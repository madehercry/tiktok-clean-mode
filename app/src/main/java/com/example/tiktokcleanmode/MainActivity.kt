package com.example.tiktokcleanmode

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.tiktokcleanmode.databinding.ActivityMainBinding

/**
 * MainActivity
 * ============
 * The sole Activity in this app. Its only job is to guide the user through
 * granting the two permissions that [TikTokCleanService] requires:
 *
 *  1. **Draw over other apps** (SYSTEM_ALERT_WINDOW) — for the floating button.
 *  2. **Accessibility Service** — for gesture dispatch and node-tree access.
 *
 * Status indicators update every time the Activity resumes (e.g., when the
 * user returns from Android's Settings screens), providing instant feedback.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupButtonListeners()
    }

    /**
     * Re-check permissions every time the user returns to this screen.
     * This ensures status chips update immediately after the user grants a
     * permission in Android Settings and navigates back.
     */
    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    private fun setupButtonListeners() {
        // Step 1 — "Draw over other apps"
        binding.btnGrantOverlay.setOnClickListener {
            openOverlaySettings()
        }

        // Step 2 — Accessibility Service
        binding.btnGrantAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    /**
     * Reads the current permission state and updates every UI element to match:
     *  • Status icon  — green check or red error indicator.
     *  • Status text  — "Granted" / "Not granted".
     *  • Grant button — disabled (and re-labelled) once permission is granted.
     *  • "All set" banner — shown only when both permissions are granted.
     */
    private fun updatePermissionUI() {
        val hasOverlay       = PermissionHelper.hasOverlayPermission(this)
        val hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(this)

        // ── Overlay permission card ───────────────────────────────────────────
        applyPermissionState(
            isGranted   = hasOverlay,
            statusIcon  = binding.imgOverlayStatus,
            grantButton = binding.btnGrantOverlay
        )

        // ── Accessibility permission card ─────────────────────────────────────
        applyPermissionState(
            isGranted   = hasAccessibility,
            statusIcon  = binding.imgAccessibilityStatus,
            grantButton = binding.btnGrantAccessibility
        )

        // ── "All set" banner ──────────────────────────────────────────────────
        val allGranted = hasOverlay && hasAccessibility
        binding.cardAllSet.visibility = if (allGranted) View.VISIBLE else View.GONE
    }

    /**
     * Applies the visual granted/not-granted state to a single permission card.
     *
     * @param isGranted   Whether the permission is currently active.
     * @param statusIcon  The [android.widget.ImageView] showing the check/error icon.
     * @param grantButton The [com.google.android.material.button.MaterialButton]
     *                    that opens the relevant Settings screen.
     */
    private fun applyPermissionState(
        isGranted: Boolean,
        statusIcon: android.widget.ImageView,
        grantButton: com.google.android.material.button.MaterialButton
    ) {
        if (isGranted) {
            statusIcon.setImageResource(R.drawable.ic_check)
            statusIcon.contentDescription = getString(R.string.status_granted)
            grantButton.text    = getString(R.string.btn_granted)
            grantButton.isEnabled = false
        } else {
            statusIcon.setImageResource(R.drawable.ic_error)
            statusIcon.contentDescription = getString(R.string.status_not_granted)
            grantButton.text    = getString(R.string.btn_grant_permission)
            grantButton.isEnabled = true
        }
    }

    // ── Navigation to Settings ────────────────────────────────────────────────

    /**
     * Opens the system "Appear on top" settings screen pre-filtered to this app.
     * On some OEM ROMs the package-specific deep-link may not be supported;
     * we fall back to the general overlay settings screen in that case.
     */
    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", packageName, null)
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open the generic overlay settings without a package filter.
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    /**
     * Opens the system Accessibility Settings screen where the user can find
     * and enable "TikTok Clean Mode Service".
     */
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
