package com.flyer.launcher.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.flyer.FlyerRoute

/**
 * Everything launcher surfaces can ask the launcher to do. Implemented by the launcher activity;
 * previews inside Flyer use [NoOpLauncherActions] so nothing can be launched from a preview.
 */
interface LauncherActions {
    fun launchApp(key: String, bounds: Rect?)
    fun launchShortcut(item: ShortcutItem, bounds: Rect?)
    fun openFolder(folderId: String, bounds: Rect?)
    fun openDrawer()
    fun openSearch(query: String = "")
    fun openFlyer(route: FlyerRoute = FlyerRoute.Dashboard)
    fun openWidgetPicker()
    fun perform(action: GestureAction)
    fun showMessage(text: String)
}

object NoOpLauncherActions : LauncherActions {
    override fun launchApp(key: String, bounds: Rect?) = Unit
    override fun launchShortcut(item: ShortcutItem, bounds: Rect?) = Unit
    override fun openFolder(folderId: String, bounds: Rect?) = Unit
    override fun openDrawer() = Unit
    override fun openSearch(query: String) = Unit
    override fun openFlyer(route: FlyerRoute) = Unit
    override fun openWidgetPicker() = Unit
    override fun perform(action: GestureAction) = Unit
    override fun showMessage(text: String) = Unit
}

val LocalLauncherActions = staticCompositionLocalOf<LauncherActions> { NoOpLauncherActions }
