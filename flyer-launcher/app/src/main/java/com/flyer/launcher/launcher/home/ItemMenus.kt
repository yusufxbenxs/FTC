package com.flyer.launcher.launcher.home

import android.graphics.Rect as AndroidRect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.geometry.Rect
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.launcher.ConfirmRequest
import com.flyer.launcher.launcher.LauncherActions
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.MenuEntry
import com.flyer.launcher.launcher.MenuState
import kotlin.math.roundToInt

fun Rect.toAndroid() = AndroidRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

/** Builds context menus for launcher items (long press on touch, right click with a mouse). */
object ItemMenus {

    fun forItem(ui: LauncherUiState, actions: LauncherActions, item: WorkspaceItem, surface: Surface, anchor: Rect): MenuState? = when (item) {
        is AppItem -> ui.core.resolveApp(item.appKey)?.let { forApp(ui, actions, it, item, surface, anchor) }
        is FolderItem -> forFolder(ui, item, anchor)
        is WidgetItem -> forWidget(ui, item, anchor)
        is BuiltInWidgetItem -> forBuiltIn(ui, item, anchor)
        is ShortcutItem -> MenuState(anchor, item.label, listOf(removeEntry(ui, item, "Remove shortcut")))
    }

    fun forApp(
        ui: LauncherUiState,
        actions: LauncherActions,
        entry: AppEntry,
        item: AppItem?,
        surface: Surface?,
        anchor: Rect,
    ): MenuState {
        val core = ui.core
        val entries = mutableListOf<MenuEntry>()
        val shortcuts = core.appsEngine.shortcutsFor(entry).map { info ->
            MenuEntry(info.shortLabel?.toString() ?: info.id, Icons.AutoMirrored.Outlined.OpenInNew) {
                ui.menu = null
                core.appsEngine.startShortcut(info, anchor.toAndroid(), null)
            }
        }
        if (entry.builtIn != null) {
            entries += MenuEntry("Open ${entry.label}", Icons.AutoMirrored.Outlined.OpenInNew) {
                ui.menu = null
                actions.launchApp(entry.key, anchor)
            }
        } else {
            entries += MenuEntry("App info", Icons.Outlined.Info) {
                ui.menu = null
                core.appsEngine.openAppInfo(entry, anchor.toAndroid())
            }
        }
        entries += MenuEntry("Customize icon & label", Icons.Outlined.Brush) {
            ui.menu = null
            actions.openFlyer(FlyerRoute.AppEditor(entry.key))
        }
        if (item != null && surface != null) {
            entries += MenuEntry("Edit in layout editor", Icons.Outlined.DashboardCustomize) {
                ui.menu = null
                actions.openFlyer(FlyerRoute.LiveEditor(item.id))
            }
            if (!ui.core.config.value.home.lockLayout && entry.builtIn == null) {
                entries += removeEntry(ui, item, if (surface == Surface.Dock) "Remove from dock" else "Remove")
            }
        } else {
            entries += MenuEntry("Add to home screen", Icons.Outlined.AddToHomeScreen) {
                ui.menu = null
                core.editHome("Add ${entry.label}") { doc ->
                    doc.copy(layout = WorkspaceEngine.addApp(doc.layout, entry.key, doc.config, ui.pageController?.currentPage ?: 0, desktop = ui.desktopActive))
                }
                actions.showMessage("${entry.label} added")
            }
            if (ui.desktopActive) {
                entries += MenuEntry("Pin to taskbar", Icons.Outlined.PushPin) {
                    ui.menu = null
                    core.editHome("Pin ${entry.label}") { doc ->
                        doc.copy(layout = doc.layout.copy(desktop = doc.layout.desktop.copy(taskbarPins = (doc.layout.desktop.taskbarPins + entry.key).distinct())))
                    }
                }
            }
            if (entry.builtIn == null) {
                entries += MenuEntry("Hide from drawer", Icons.Outlined.VisibilityOff) {
                    ui.menu = null
                    core.editHome("Hide ${entry.label}") { doc -> doc.copy(layout = doc.layout.copy(hiddenApps = doc.layout.hiddenApps + entry.key)) }
                    actions.showMessage("${entry.label} hidden · manage in Flyer › App Drawer")
                }
            }
        }
        if (core.appsEngine.canUninstall(entry)) {
            entries += MenuEntry("Uninstall", Icons.Outlined.Delete, destructive = true) {
                ui.menu = null
                core.appsEngine.uninstall(entry)
            }
        }
        return MenuState(anchor, entry.label, entries, shortcuts)
    }

    private fun forFolder(ui: LauncherUiState, item: FolderItem, anchor: Rect) = MenuState(
        anchor, item.name,
        listOf(
            MenuEntry("Rename", Icons.Outlined.DriveFileRenameOutline) { ui.menu = null; ui.renameFolderId = item.id },
            MenuEntry("Style in Flyer", Icons.Outlined.Brush) { ui.menu = null; ui.flyer.open(FlyerRoute.LiveEditor(item.id)) },
            removeEntry(ui, item, "Remove folder", confirm = "The folder and its shortcuts are removed from the home screen. Apps stay installed."),
        ),
    )

    private fun forWidget(ui: LauncherUiState, item: WidgetItem, anchor: Rect): MenuState {
        val info = ui.core.widgets.providerInfo(item.appWidgetId)
        val entries = mutableListOf<MenuEntry>()
        entries += MenuEntry("Resize", Icons.Outlined.AspectRatio) { ui.menu = null; ui.resizingWidgetId = item.id }
        if (info != null && ui.core.widgets.isReconfigurable(info)) {
            entries += MenuEntry("Widget settings", Icons.Outlined.Settings) {
                ui.menu = null
                ui.reconfigureWidget?.invoke(item.appWidgetId)
            }
        }
        entries += MenuEntry("Style in Flyer", Icons.Outlined.Brush) { ui.menu = null; ui.flyer.open(FlyerRoute.LiveEditor(item.id)) }
        entries += removeEntry(ui, item, "Remove widget", confirm = "Remove this widget from the home screen?")
        return MenuState(anchor, info?.loadLabel(ui.core.app.packageManager) ?: "Widget", entries)
    }

    private fun forBuiltIn(ui: LauncherUiState, item: BuiltInWidgetItem, anchor: Rect) = MenuState(
        anchor, WorkspaceEngine.labelFor(item),
        listOf(
            MenuEntry("Style in Flyer", Icons.Outlined.Brush) { ui.menu = null; ui.flyer.open(FlyerRoute.Category(FlyerCategory.WIDGETS)) },
            MenuEntry("Edit in layout editor", Icons.Outlined.DashboardCustomize) { ui.menu = null; ui.flyer.open(FlyerRoute.LiveEditor(item.id)) },
            removeEntry(ui, item, "Remove"),
        ),
    )

    private fun removeEntry(ui: LauncherUiState, item: WorkspaceItem, label: String, confirm: String? = null): MenuEntry =
        MenuEntry(label, Icons.Outlined.Delete, destructive = true) {
            ui.menu = null
            val doRemove = {
                ui.core.editHome("Remove") { doc -> doc.copy(layout = WorkspaceEngine.removeItem(doc.layout, item.id)) }
                ui.toast("Removed")
            }
            if (confirm != null && ui.core.config.value.advanced.confirmRemovals) {
                ui.confirm = ConfirmRequest(label, confirm, onConfirm = doRemove)
            } else doRemove()
        }

    /** Long press on empty home space. Always contains Flyer, so it can never be unreachable. */
    fun homeMenu(ui: LauncherUiState, actions: LauncherActions, anchor: Rect): MenuState = MenuState(
        anchor, null,
        listOf(
            MenuEntry("Flyer", Icons.Outlined.Settings) { ui.menu = null; actions.openFlyer(FlyerRoute.Dashboard) },
            MenuEntry("Edit layout", Icons.Outlined.DashboardCustomize) { ui.menu = null; actions.openFlyer(FlyerRoute.LiveEditor()) },
            MenuEntry("Widgets", Icons.Outlined.Widgets) { ui.menu = null; actions.openWidgetPicker() },
            MenuEntry("Themes", Icons.Outlined.Brush) { ui.menu = null; actions.openFlyer(FlyerRoute.Category(FlyerCategory.THEMES)) },
            MenuEntry("Wallpaper", Icons.Outlined.Wallpaper) { ui.menu = null; ui.openWallpaperPicker?.invoke() },
        ),
    )
}
