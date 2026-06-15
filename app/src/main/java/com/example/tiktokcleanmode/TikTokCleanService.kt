package com.example.tiktokcleanmode

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast
import kotlin.math.abs

/**
 * TikTokCleanService
 * ==================
 * An [AccessibilityService] that injects a floating trigger button on top of
 * TikTok. When the user taps the button the service:
 *
 *  1. Dispatches a synthetic long-press gesture at the centre of the screen,
 *     which causes TikTok to display its video-options context menu.
 *  2. Waits (via [AccessibilityEvent] callbacks) for the menu to render.
 *  3. Traverses the [AccessibilityNodeInfo] tree to find the node whose text
 *     or content-description matches a "Clear display" keyword.
 *  4. Performs ACTION_CLICK on that node, enabling TikTok's clean full-screen mode.
 *
 * Required capabilities (declared in res/xml/accessibility_service_config.xml):
 *   • canRetrieveWindowContent = true
 *   • canPerformGestures       = true
 */
class TikTokCleanService : AccessibilityService() {

    // ── Companion / constants ─────────────────────────────────────────────────

    companion object {
        private const val TAG = "TikTokCleanService"

        /** Duration of the synthetic long-press in milliseconds. */
        private const val LONG_PRESS_MS = 800L

        /**
         * Maximum time to wait (ms) for TikTok's context menu to appear
         * after the gesture finishes before giving up.
         */
        private const val SEARCH_TIMEOUT_MS = 4_000L

        /**
         * Known TikTok package names across different regions / app variants.
         */
        val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",   // TikTok – global
            "com.ss.android.ugc.trill",   // TikTok – some markets
            "com.ss.android.ugc.aweme"    // Douyin  – China
        )

        /**
         * Localised text / content-description strings that represent TikTok's
         * "Clear display" (full-screen clean mode) button.
         *
         * Add more translations here as needed.
         */
        val CLEAR_DISPLAY_KEYWORDS = listOf(
            "clear display",        // English
            "чистый режим",         // Russian
            "清屏",                  // Chinese Simplified
            "清螢幕",                // Chinese Traditional
            "화면 지우기",            // Korean
            "écran vide",           // French
            "limpiar pantalla",     // Spanish
            "bildschirm leeren",    // German
            "cancella schermo",     // Italian
            "limpar tela"           // Portuguese
        )

        /** Set to true while the service is alive (used by MainActivity). */
        @Volatile
        var isRunning = false
            private set
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    /** True while we are actively waiting for TikTok's menu to appear. */
    @Volatile
    private var isWaitingForMenu = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Cancels the search-timeout when the button is found or service stops. */
    private var timeoutRunnable: Runnable? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "Service connected — injecting floating button")
        addFloatingButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopWaitingForMenu()
        removeFloatingButton()
        Log.d(TAG, "Service destroyed")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    // ── Accessibility event handling ──────────────────────────────────────────

    /**
     * Called by the system whenever an accessibility event fires for the
     * targeted packages (defined in accessibility_service_config.xml).
     *
     * We only act when [isWaitingForMenu] is true, meaning the user tapped
     * the floating button and we are waiting for TikTok's menu to appear.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isWaitingForMenu) return

        // Ignore events that are not from a known TikTok package.
        if (!TIKTOK_PACKAGES.contains(event.packageName?.toString())) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                Log.d(TAG, "Content changed – scanning node tree for clear-display button")
                if (findAndClickClearDisplayNode()) {
                    stopWaitingForMenu()
                }
            }
        }
    }

    // ── Floating button ───────────────────────────────────────────────────────

    /**
     * Inflates [R.layout.layout_fab] and attaches it to [WindowManager] with an
     * always-on-top overlay type. The button is draggable and snaps to the nearest
     * screen edge when released.
     */
    private fun addFloatingButton() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_fab, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_APPLICATION_OVERLAY is required on API 26+.
            // It draws above all apps but below the status bar.
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE: touch events outside the button reach TikTok.
            // FLAG_NOT_TOUCH_MODAL: the button doesn't intercept the global touch stream.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0       // Start flush against the left edge …
            y = 400     // … 400px from the top
        }

        val fabButton = floatingView!!.findViewById<ImageButton>(R.id.fab_clean)
        setupDragAndClick(fabButton, params)

        windowManager?.addView(floatingView, params)
        Log.d(TAG, "Floating button added to WindowManager")
    }

    /**
     * Attaches a [View.OnTouchListener] that handles both:
     *   • **Drag** — moves the overlay window via [WindowManager.updateViewLayout].
     *   • **Tap**  — calls [triggerCleanMode] when the finger lifts without dragging.
     *
     * After a drag, the button snaps to the nearest screen edge.
     */
    private fun setupDragAndClick(button: ImageButton, params: WindowManager.LayoutParams) {
        var startLayoutX = 0
        var startLayoutY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var dragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Record anchor positions for delta calculation.
                    startLayoutX = params.x
                    startLayoutY = params.y
                    startTouchX  = event.rawX
                    startTouchY  = event.rawY
                    dragging     = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchX).toInt()
                    val dy = (event.rawY - startTouchY).toInt()

                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }

                    if (dragging) {
                        val screenW = getScreenWidth()
                        val screenH = getScreenHeight()

                        // Clamp so the button can't be dragged off-screen.
                        params.x = (startLayoutX + dx).coerceIn(0, screenW - view.width)
                        params.y = (startLayoutY + dy).coerceIn(0, screenH - view.height)
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge(params)
                    } else {
                        // Short tap with no movement → trigger the main action.
                        view.performClick()
                    }
                    true
                }

                else -> false
            }
        }

        // performClick() above routes here via the standard OnClickListener contract.
        button.setOnClickListener {
            triggerCleanMode()
        }
    }

    /**
     * Animates (immediately repositions) the floating button so that it sits
     * flush against whichever horizontal screen edge is closest.
     */
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenWidth   = getScreenWidth()
        val buttonWidth   = floatingView?.width ?: 0
        val buttonCenterX = params.x + buttonWidth / 2

        params.x = if (buttonCenterX < screenWidth / 2) 0 else screenWidth - buttonWidth
        windowManager?.updateViewLayout(floatingView, params)
        Log.d(TAG, "Button snapped to ${if (params.x == 0) "left" else "right"} edge")
    }

    /** Detaches the floating button from [WindowManager]. */
    private fun removeFloatingButton() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
            Log.d(TAG, "Floating button removed from WindowManager")
        }
    }

    // ── Clean mode trigger ────────────────────────────────────────────────────

    /**
     * Entry point when the user taps the floating button.
     *
     * Steps:
     *  1. Verify TikTok is the active window.
     *  2. Build a [GestureDescription] that mimics a long-press at screen centre.
     *  3. Dispatch the gesture; on completion, start scanning the node tree.
     *  4. [onAccessibilityEvent] will also scan on every content-change event,
     *     providing a faster path if the menu appears while the gesture callback
     *     is still pending.
     */
    private fun triggerCleanMode() {
        // Guard: refuse to act if TikTok is not in the foreground.
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in TIKTOK_PACKAGES) {
            showToast(getString(R.string.toast_not_tiktok))
            Log.w(TAG, "Aborting: TikTok is not the active window")
            return
        }

        val centerX = getScreenWidth()  / 2f
        val centerY = getScreenHeight() / 2f

        // A zero-radius Path at (centerX, centerY) held for LONG_PRESS_MS
        // is how dispatchGesture simulates a long-press.
        val longPressPath = Path().also { it.moveTo(centerX, centerY) }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    longPressPath,
                    /* startTime = */ 0,
                    /* duration  = */ LONG_PRESS_MS
                )
            )
            .build()

        // Arm the waiting state BEFORE dispatching so we don't miss events.
        startWaitingForMenu()
        showToast(getString(R.string.toast_clean_mode_triggered))

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Long-press gesture completed")
                    // The menu may not have rendered yet; do a final scan after a
                    // short grace period in case onAccessibilityEvent hasn't fired.
                    mainHandler.postDelayed({
                        if (isWaitingForMenu) {
                            Log.d(TAG, "Post-gesture delayed scan triggered")
                            if (!findAndClickClearDisplayNode()) {
                                Log.w(TAG, "Clear display button not found in delayed scan")
                            }
                        }
                    }, 600L)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Long-press gesture cancelled")
                    stopWaitingForMenu()
                    showToast(getString(R.string.toast_gesture_failed))
                }
            },
            mainHandler
        )

        if (!dispatched) {
            stopWaitingForMenu()
            showToast(getString(R.string.toast_gesture_failed))
            Log.e(TAG, "dispatchGesture returned false — canPerformGestures may be false")
        }
    }

    // ── Node tree search ──────────────────────────────────────────────────────

    /**
     * Walks the entire [AccessibilityNodeInfo] tree rooted at [rootInActiveWindow]
     * and attempts to find a node matching any [CLEAR_DISPLAY_KEYWORDS].
     *
     * @return `true` if a matching node was found **and** clicked.
     */
    private fun findAndClickClearDisplayNode(): Boolean {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "rootInActiveWindow is null — cannot scan")
            return false
        }

        return try {
            searchNodeTree(root)
        } finally {
            root.recycle()
        }
    }

    /**
     * Recursively searches [node] and its children for a "clear display" match.
     *
     * Matching strategy (in order):
     *  1. [AccessibilityNodeInfo.getText] — visible label text.
     *  2. [AccessibilityNodeInfo.getContentDescription] — accessibility label (icon buttons).
     *  3. [AccessibilityNodeInfo.getViewIdResourceName] — as a last resort, by view-id
     *     (e.g., `com.zhiliaoapp.musically:id/clear_screen` seen in some APK versions).
     *
     * @return `true` if the node (or any descendant) was clicked.
     */
    private fun searchNodeTree(node: AccessibilityNodeInfo): Boolean {
        val text   = node.text?.toString()?.lowercase().orEmpty()
        val desc   = node.contentDescription?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()

        for (keyword in CLEAR_DISPLAY_KEYWORDS) {
            if (text.contains(keyword) || desc.contains(keyword)) {
                Log.i(TAG, "Found clear-display node via text/desc: '$text' / '$desc'")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        // Resource-ID heuristic: some TikTok builds use an id ending with
        // "clear_screen", "clean_mode", "clear_display", or "clean_screen".
        val idKeywords = listOf("clear_screen", "clean_mode", "clear_display", "clean_screen")
        if (idKeywords.any { viewId.endsWith(it) }) {
            Log.i(TAG, "Found clear-display node via resource ID: '$viewId'")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }

        // Depth-first traversal of children.
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchNodeTree(child)
            child.recycle()
            if (found) return true
        }

        return false
    }

    // ── Wait-state management ─────────────────────────────────────────────────

    /**
     * Arms the "menu search" state and schedules a timeout after which the
     * search is abandoned and the user is notified.
     */
    private fun startWaitingForMenu() {
        isWaitingForMenu = true

        // Cancel any pre-existing timeout.
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }

        timeoutRunnable = Runnable {
            if (isWaitingForMenu) {
                isWaitingForMenu = false
                showToast(getString(R.string.toast_button_not_found))
                Log.w(TAG, "Search timed out after ${SEARCH_TIMEOUT_MS}ms")
            }
        }.also { mainHandler.postDelayed(it, SEARCH_TIMEOUT_MS) }
    }

    /** Disarms the waiting state and cancels the timeout runnable. */
    private fun stopWaitingForMenu() {
        isWaitingForMenu = false
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    // ── Screen dimension helpers ──────────────────────────────────────────────

    /**
     * Returns the screen width in pixels, compatible with API 26–34.
     * Uses the non-deprecated [WindowManager.currentWindowMetrics] on API 30+.
     */
    private fun getScreenWidth(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager?.currentWindowMetrics?.bounds?.width() ?: resources.displayMetrics.widthPixels
    } else {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(dm)
        dm.widthPixels
    }

    /** Returns the screen height in pixels, compatible with API 26–34. */
    private fun getScreenHeight(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager?.currentWindowMetrics?.bounds?.height() ?: resources.displayMetrics.heightPixels
    } else {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(dm)
        dm.heightPixels
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Shows a [Toast] safely on the main thread from any calling thread. */
    private fun showToast(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}
