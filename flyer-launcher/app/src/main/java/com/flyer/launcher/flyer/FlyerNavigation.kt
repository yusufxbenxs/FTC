package com.flyer.launcher.flyer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** Flyer's customization modules. New modules are added here and in [com.flyer.launcher.flyer.settings.SettingsRegistry]. */
enum class FlyerCategory(val title: String, val summary: String, val icon: ImageVector) {
    HOME("Home Screen", "Grid, pages, margins, background, system bars", Icons.Outlined.Home),
    LAYOUT_EDITOR("Layout Editor", "Visually arrange and style every element", Icons.Outlined.DashboardCustomize),
    ICONS("Icons", "Size, shape, packs, labels, effects", Icons.Outlined.Category),
    DOCK("Dock", "Position, size, background, blur, slots", Icons.Outlined.ViewDay),
    DRAWER("App Drawer", "Layout, sorting, search, groups, hidden apps", Icons.Outlined.Apps),
    WIDGETS("Widgets", "Native widgets, containers, built-in clock", Icons.Outlined.Widgets),
    FOLDERS("Folders", "Previews, shapes, popup style, animations", Icons.Outlined.Folder),
    GESTURES("Gestures", "Swipes, taps, pinches and their actions", Icons.Outlined.Gesture),
    ANIMATIONS("Animations", "Motion, easing, page transitions, app launch", Icons.Outlined.Animation),
    APPEARANCE("Appearance", "Theme mode, colors, Material You, menus", Icons.Outlined.Palette),
    TYPOGRAPHY("Typography", "Fonts, custom font files, text scale", Icons.Outlined.TextFields),
    DESKTOP("Desktop Mode", "Mouse, keyboard, taskbar, windows", Icons.Outlined.DesktopWindows),
    SEARCH("Search", "Search bar and search sources", Icons.Outlined.Search),
    THEMES("Themes", "Advanced themes, presets and your saved themes", Icons.Outlined.Brush),
    MATERIALS("Materials & Effects", "Liquid glass, frosted, neon, clay, animated backgrounds", Icons.Outlined.AutoAwesome),
    LOCK("Lock", "Lock button, unlock methods, protected apps", Icons.Outlined.Lock),
    PERFORMANCE("Performance", "Effects, caches, low-memory mode", Icons.Outlined.Speed),
    ADVANCED("Advanced", "Built-in apps, behaviour, orientation, debug", Icons.Outlined.Tune),
    BACKUP("Backup & Restore", "Export, import and snapshots", Icons.Outlined.Backup),
}

@Immutable
sealed interface FlyerRoute {
    data object Dashboard : FlyerRoute
    data class Category(val category: FlyerCategory, val highlight: String? = null) : FlyerRoute
    data class LiveEditor(val selectItemId: String? = null) : FlyerRoute
    data object Search : FlyerRoute
    data class AppEditor(val appKey: String) : FlyerRoute
    data object WidgetPicker : FlyerRoute

    val title: String
        get() = when (this) {
            Dashboard -> "Flyer"
            is Category -> category.title
            is LiveEditor -> "Layout Editor"
            Search -> "Search settings"
            is AppEditor -> "App"
            WidgetPicker -> "Add widget"
        }

    companion object {
        fun of(category: FlyerCategory): FlyerRoute =
            if (category == FlyerCategory.LAYOUT_EDITOR) LiveEditor() else Category(category)
    }
}

/** Grid icon re-export for callers that need a neutral icon. */
val GridIcon: ImageVector get() = Icons.Outlined.GridView
