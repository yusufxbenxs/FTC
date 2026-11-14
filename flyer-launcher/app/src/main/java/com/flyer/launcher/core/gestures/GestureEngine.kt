package com.flyer.launcher.core.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.config.GestureType

/** Human readable metadata for gestures and actions (used by Flyer's gesture editor). */
object GestureCatalog {
    fun title(g: GestureType) = when (g) {
        GestureType.SWIPE_UP -> "Swipe up"
        GestureType.SWIPE_DOWN -> "Swipe down"
        GestureType.DOUBLE_TAP -> "Double tap"
        GestureType.LONG_PRESS_EMPTY -> "Long press on empty space"
        GestureType.PINCH_IN -> "Pinch in"
        GestureType.PINCH_OUT -> "Pinch out"
        GestureType.TWO_FINGER_SWIPE_UP -> "Two-finger swipe up"
        GestureType.TWO_FINGER_SWIPE_DOWN -> "Two-finger swipe down"
        GestureType.HOME_BUTTON -> "Home button (while on home)"
        GestureType.SWIPE_UP_DOCK -> "Swipe up on dock"
    }

    fun title(a: ActionType) = when (a) {
        ActionType.NONE -> "Do nothing"
        ActionType.OPEN_DRAWER -> "Open app drawer"
        ActionType.OPEN_SEARCH -> "Open search"
        ActionType.OPEN_FLYER -> "Open Flyer"
        ActionType.OPEN_LIVE_EDITOR -> "Open live editor"
        ActionType.HOME_MENU -> "Show home menu"
        ActionType.EXPAND_NOTIFICATIONS -> "Notifications"
        ActionType.QUICK_SETTINGS -> "Quick settings"
        ActionType.LOCK_SCREEN -> "Lock screen"
        ActionType.RECENT_APPS -> "Recent apps"
        ActionType.SCREENSHOT -> "Take screenshot"
        ActionType.LAUNCH_APP -> "Launch an app"
        ActionType.TOGGLE_DESKTOP_MODE -> "Toggle desktop mode"
        ActionType.TOGGLE_DARK_MODE -> "Toggle dark / light theme"
        ActionType.DEFAULT_PAGE -> "Go to default page"
        ActionType.NEXT_PAGE -> "Next page"
        ActionType.PREVIOUS_PAGE -> "Previous page"
        ActionType.OPEN_WIDGETS -> "Add widget"
    }

    /** Actions that need the optional accessibility helper because launchers cannot do them directly. */
    fun needsAccessibility(a: ActionType) = a in setOf(ActionType.LOCK_SCREEN, ActionType.RECENT_APPS, ActionType.SCREENSHOT)

    fun minSdk(a: ActionType) = when (a) {
        ActionType.LOCK_SCREEN, ActionType.SCREENSHOT -> Build.VERSION_CODES.P
        else -> Build.VERSION_CODES.O
    }
}

/**
 * Gesture Engine: resolves gestures to actions according to the configuration and performs the
 * system-level actions. Launcher-level actions (drawer, Flyer, pages…) are dispatched by the
 * launcher UI, which owns that state.
 */
class GestureEngine(private val context: Context) {

    fun actionFor(config: FlyerConfig, gesture: GestureType): GestureAction =
        config.gestures.bindings[gesture] ?: GestureAction(ActionType.NONE)

    /** Returns false with a reason when the action can't run on this device/config. */
    fun performSystemAction(type: ActionType): Result<Unit> = when (type) {
        ActionType.EXPAND_NOTIFICATIONS -> {
            if (expandStatusBar("expandNotificationsPanel")) Result.success(Unit)
            else accessibility(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
        ActionType.QUICK_SETTINGS -> {
            if (expandStatusBar("expandSettingsPanel")) Result.success(Unit)
            else accessibility(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
        ActionType.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            accessibility(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else Result.failure(UnsupportedOperationException("Requires Android 9"))
        ActionType.RECENT_APPS -> accessibility(AccessibilityService.GLOBAL_ACTION_RECENTS)
        ActionType.SCREENSHOT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            accessibility(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) else Result.failure(UnsupportedOperationException("Requires Android 9"))
        else -> Result.failure(IllegalArgumentException("Not a system action"))
    }

    /**
     * Uses StatusBarManager's expand methods. They are not part of the public SDK but are the
     * long-standing way launchers open the shade (guarded by the normal EXPAND_STATUS_BAR permission).
     */
    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandStatusBar(method: String): Boolean = try {
        val service = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod(method).invoke(service)
        true
    } catch (e: Throwable) {
        Log.i("GestureEngine", "StatusBarManager.$method unavailable: ${e.javaClass.simpleName}")
        false
    }

    private fun accessibility(action: Int): Result<Unit> {
        val service = FlyerAccessibilityService.instance
            ?: return Result.failure(AccessibilityRequired())
        return if (service.performGlobalAction(action)) Result.success(Unit)
        else Result.failure(UnsupportedOperationException("The system refused the action"))
    }

    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, FlyerAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

class AccessibilityRequired : Exception("Enable \"Flyer Launcher gestures\" in Accessibility settings to use this action")

/**
 * Optional helper enabled by the user. It only performs global actions on request from the launcher
 * and ignores every accessibility event (it is restricted to the launcher's own package).
 */
class FlyerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: FlyerAccessibilityService? = null
            private set
    }
}
