package com.example.tiktokcleanmode

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * PermissionHelper
 * ================
 * Stateless utility object that centralises all permission-status checks
 * required by TikTok Clean Mode.
 *
 * Two distinct permissions are needed:
 *
 * 1. **SYSTEM_ALERT_WINDOW** ("Draw over other apps")
 *    Required to show the floating trigger button on top of TikTok.
 *    Checked via [Settings.canDrawOverlays].
 *
 * 2. **Accessibility Service** enabled status
 *    Required for [TikTokCleanService] to receive events and dispatch gestures.
 *    Checked by reading [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES].
 */
object PermissionHelper {

    /**
     * Returns `true` if the "Draw over other apps" (SYSTEM_ALERT_WINDOW)
     * permission has been granted by the user.
     */
    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * Returns `true` if [TikTokCleanService] is listed in the system's set of
     * enabled accessibility services.
     *
     * The enabled-services setting is a colon-delimited list of flattened
     * [ComponentName] strings, e.g.:
     *   `com.example.app/.MyService:com.other.app/.OtherService`
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val target = ComponentName(context, TikTokCleanService::class.java)

        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServicesSetting
            .split(":")
            .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
            .any { it == target }
    }

    /**
     * Convenience check: returns `true` only when **both** permissions are in
     * the granted state.
     */
    fun hasAllPermissions(context: Context): Boolean =
        hasOverlayPermission(context) && isAccessibilityServiceEnabled(context)
}
