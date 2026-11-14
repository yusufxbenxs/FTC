package com.flyer.launcher.core.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.config.GestureType
import com.flyer.launcher.BuildConfig

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
        ActionType.LOCK_SCREEN -> "Lock the phone"
        ActionType.LOCK_LAUNCHER -> "Lock the home screen"
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
    fun needsAccessibility(a: ActionType) = a in setOf(ActionType.RECENT_APPS, ActionType.SCREENSHOT)

    /** False for helper-only actions in builds that don't include the helper. */
    fun availableInBuild(a: ActionType) = !needsAccessibility(a) || GestureHelperBridge.includedInBuild

    fun minSdk(a: ActionType) = when (a) {
        ActionType.SCREENSHOT -> Build.VERSION_CODES.P
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
            else accessibility(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Opening notifications isn't supported on this device")
        }
        ActionType.QUICK_SETTINGS -> {
            if (expandStatusBar("expandSettingsPanel")) Result.success(Unit)
            else accessibility(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Opening quick settings isn't supported on this device")
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

    private fun accessibility(action: Int, unavailableMessage: String = HELPER_MISSING): Result<Unit> {
        if (!GestureHelperBridge.includedInBuild) return Result.failure(UnsupportedOperationException(unavailableMessage))
        val perform = GestureHelperBridge.performGlobalAction ?: return Result.failure(AccessibilityRequired())
        return if (perform(action)) Result.success(Unit)
        else Result.failure(UnsupportedOperationException("The system refused the action"))
    }

    fun isAccessibilityEnabled(): Boolean {
        if (!GestureHelperBridge.includedInBuild) return false
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context.packageName, GestureHelperBridge.SERVICE_CLASS).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private companion object {
        const val HELPER_MISSING = "Lock screen, recents and screenshot gestures need the \"full\" build of Flyer Launcher"
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

class AccessibilityRequired : Exception("Enable \"Flyer Launcher gestures\" in Accessibility settings to use this action")

/**
 * Connection to the optional accessibility helper. The helper itself only exists in the "full"
 * build flavor (see src/full); the default "standard" build ships without any accessibility
 * service so sideloading isn't blocked by Play Protect.
 */
object GestureHelperBridge {
    const val SERVICE_CLASS = "com.flyer.launcher.core.gestures.FlyerAccessibilityService"

    /** Set by the helper while it is connected. */
    @Volatile
    var performGlobalAction: ((Int) -> Boolean)? = null

    val includedInBuild: Boolean get() = BuildConfig.GESTURE_HELPER
}
