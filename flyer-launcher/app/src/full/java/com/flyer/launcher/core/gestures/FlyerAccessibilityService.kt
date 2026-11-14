package com.flyer.launcher.core.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper enabled by the user ("full" build only). It only performs global actions on
 * request from the launcher and ignores every accessibility event (it is restricted to the
 * launcher's own package and cannot retrieve window content).
 */
class FlyerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        GestureHelperBridge.performGlobalAction = { action -> performGlobalAction(action) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        GestureHelperBridge.performGlobalAction = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        GestureHelperBridge.performGlobalAction = null
        super.onDestroy()
    }
}
