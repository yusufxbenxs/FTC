package com.flyer.launcher.core.config

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The single source of truth for every visual and behavioural property of the launcher.
 *
 * Launcher components never hardcode visual values: they read them from this snapshot
 * (provided through the composition) and Flyer edits it through [com.flyer.launcher.flyer.FlyerSession].
 *
 * The schema is versioned ([schemaVersion]); see [ConfigMigrations] for upgrades of older files and
 * [ConfigValidator] for the clamping rules that keep an imported or hand-edited file from breaking
 * the launcher.
 */
@Immutable
@Serializable
data class FlyerConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val home: HomeConfig = HomeConfig(),
    val icons: IconConfig = IconConfig(),
    val dock: DockConfig = DockConfig(),
    val drawer: DrawerConfig = DrawerConfig(),
    val folders: FolderConfig = FolderConfig(),
    val widgets: WidgetConfig = WidgetConfig(),
    val gestures: GestureConfig = GestureConfig(),
    val animations: AnimationConfig = AnimationConfig(),
    val appearance: AppearanceConfig = AppearanceConfig(),
    val typography: TypographyConfig = TypographyConfig(),
    val desktop: DesktopConfig = DesktopConfig(),
    val search: SearchConfig = SearchConfig(),
    val performance: PerformanceConfig = PerformanceConfig(),
    val advanced: AdvancedConfig = AdvancedConfig(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

// ───────────────────────────── Home screen ─────────────────────────────

@Serializable enum class Positioning { GRID, FREE }
@Serializable enum class PageTransition { SLIDE, FADE, DEPTH, CUBE, STACK, ZOOM, ROTATE, NONE }
@Serializable enum class PageIndicatorStyle { DOTS, LINE, NUMBERS, NONE }
@Serializable enum class VerticalPlacement { TOP, CENTER, BOTTOM }
@Serializable enum class CellSizing { FILL, FIXED }
@Serializable enum class BackgroundType { SYSTEM_WALLPAPER, SOLID, GRADIENT, IMAGE }
@Serializable enum class SystemBarStyle { AUTO, LIGHT_ICONS, DARK_ICONS }

@Immutable
@Serializable
data class BackgroundConfig(
    val type: BackgroundType = BackgroundType.SYSTEM_WALLPAPER,
    val color: ColorValue = argb(0xFF101018),
    val gradientEnd: ColorValue = argb(0xFF3B2C8C),
    val gradientAngle: Int = 135,
    /** Absolute path inside the app's private storage (copied from the user's pick). */
    val imagePath: String? = null,
    val imageBlurDp: Int = 0,
    /** 0..1 black scrim drawn over the wallpaper / background to improve contrast. */
    val dim: Float = 0f,
    /** Parallax: report page offsets to the system wallpaper so it scrolls with the pages. */
    val wallpaperScrolling: Boolean = true,
)

@Immutable
@Serializable
data class HomeConfig(
    val columns: Int = 4,
    val rows: Int = 5,
    val positioning: Positioning = Positioning.GRID,
    /** In free positioning, snap dropped items to this step (dp). 0 = no snapping. */
    val freeSnapDp: Int = 8,
    val marginStartDp: Int = 12,
    val marginEndDp: Int = 12,
    val marginTopDp: Int = 16,
    val marginBottomDp: Int = 8,
    val cellSpacingHDp: Int = 0,
    val cellSpacingVDp: Int = 0,
    val cellSizing: CellSizing = CellSizing.FILL,
    val fixedCellHeightDp: Int = 96,
    val gridPlacement: VerticalPlacement = VerticalPlacement.TOP,
    val defaultPage: Int = 0,
    val infiniteScroll: Boolean = false,
    val pageTransition: PageTransition = PageTransition.SLIDE,
    val pageIndicator: PageIndicatorStyle = PageIndicatorStyle.DOTS,
    val pageIndicatorColor: ColorValue = ColorValue.White,
    val pageIndicatorAtTop: Boolean = false,
    val background: BackgroundConfig = BackgroundConfig(),
    val showStatusBar: Boolean = true,
    val showNavigationBar: Boolean = true,
    val systemBarIcons: SystemBarStyle = SystemBarStyle.AUTO,
    val lockLayout: Boolean = false,
    val showGridWhileDragging: Boolean = true,
    /** Allow overlapping items when positioning freely. */
    val allowOverlap: Boolean = false,
)

// ───────────────────────────── Icons ─────────────────────────────

@Serializable enum class IconShape { SYSTEM, CIRCLE, SQUIRCLE, ROUNDED_SQUARE, SQUARE, TEARDROP, HEXAGON, PEBBLE, CLOVER }
@Serializable enum class PressEffect { SCALE, DIM, NONE }
@Serializable enum class FontWeightOption { LIGHT, NORMAL, MEDIUM, SEMIBOLD, BOLD }

@Immutable
@Serializable
data class LabelStyle(
    val show: Boolean = true,
    val color: ColorValue = ColorValue.White,
    val sizeSp: Float = 12f,
    val weight: FontWeightOption = FontWeightOption.NORMAL,
    val maxLines: Int = 1,
    val shadow: Boolean = true,
    val shadowColor: ColorValue = argb(0xAA000000),
    val uppercase: Boolean = false,
)

@Immutable
@Serializable
data class IconConfig(
    val sizeDp: Int = 56,
    val shape: IconShape = IconShape.SQUIRCLE,
    /** Used by ROUNDED_SQUARE / TEARDROP / PEBBLE (percentage of the half size). */
    val cornerRadiusPercent: Int = 30,
    /** Put non-adaptive (legacy) icons onto the shape. */
    val shapeLegacyIcons: Boolean = true,
    val legacyBackground: ColorValue = ColorValue.White,
    /** Android 13+: tint monochrome layers with the theme colors. */
    val themedIcons: Boolean = false,
    val themedForeground: ColorValue = argb(0xFFD7DBFF),
    val themedBackground: ColorValue = argb(0xFF262A40),
    /** Package name of an installed ADW/Nova compatible icon pack. */
    val iconPack: String? = null,
    val label: LabelStyle = LabelStyle(),
    val labelGapDp: Int = 6,
    val shadow: Boolean = false,
    val shadowElevationDp: Int = 4,
    val borderWidthDp: Float = 0f,
    val borderColor: ColorValue = argb(0x66FFFFFF),
    val opacity: Float = 1f,
    /** 0 = grayscale, 1 = original colors. */
    val saturation: Float = 1f,
    val pressEffect: PressEffect = PressEffect.SCALE,
    /** Inner inset applied to the drawable inside its shape, in percent. */
    val insetPercent: Int = 0,
    val badgeWorkApps: Boolean = true,
)

// ───────────────────────────── Dock ─────────────────────────────

@Serializable enum class DockPosition { BOTTOM, TOP, LEFT, RIGHT }
@Serializable enum class DockAlignment { CENTER, START, END, SPACE_EVENLY, SPACE_BETWEEN }

@Immutable
@Serializable
data class DockConfig(
    val enabled: Boolean = true,
    val position: DockPosition = DockPosition.BOTTOM,
    val slots: Int = 5,
    val followIconSize: Boolean = true,
    val iconSizeDp: Int = 52,
    val thicknessDp: Int = 88,
    /** Length of the dock as a fraction of the screen edge it is attached to. */
    val lengthFraction: Float = 1f,
    val marginDp: Int = 8,
    val edgeMarginDp: Int = 8,
    val background: ColorValue = argb(0x33FFFFFF),
    val opacity: Float = 1f,
    val blurDp: Int = 0,
    val cornerRadiusDp: Int = 28,
    val borderWidthDp: Float = 0f,
    val borderColor: ColorValue = argb(0x33FFFFFF),
    val iconSpacingDp: Int = 8,
    val alignment: DockAlignment = DockAlignment.SPACE_EVENLY,
    val showLabels: Boolean = false,
    val showSearchInDock: Boolean = false,
)

// ───────────────────────────── App drawer ─────────────────────────────

@Serializable enum class DrawerLayout { GRID, LIST }
@Serializable enum class DrawerSort { NAME_ASC, NAME_DESC, MOST_USED, RECENTLY_INSTALLED, RECENTLY_UPDATED }
@Serializable enum class OverlayAnimation { SLIDE_UP, FADE, SCALE, SLIDE_SIDE, NONE }

@Immutable
@Serializable
data class DrawerConfig(
    val layout: DrawerLayout = DrawerLayout.GRID,
    val columns: Int = 4,
    val followIconSize: Boolean = true,
    val iconSizeDp: Int = 52,
    val rowSpacingDp: Int = 12,
    val sort: DrawerSort = DrawerSort.NAME_ASC,
    val showSearch: Boolean = true,
    val searchAtBottom: Boolean = false,
    val autoShowKeyboard: Boolean = false,
    val background: ColorValue = argb(0xFF101018),
    val opacity: Float = 0.86f,
    val blurDp: Int = 0,
    val cornerRadiusDp: Int = 28,
    val showCategories: Boolean = true,
    val showUserGroups: Boolean = true,
    val showLabels: Boolean = true,
    val labelColor: ColorValue = ColorValue.White,
    val animation: OverlayAnimation = OverlayAnimation.SLIDE_UP,
    val fastScroller: Boolean = true,
    val showRecentRow: Boolean = true,
    val separateWorkApps: Boolean = true,
    val horizontalPaddingDp: Int = 12,
)

// ───────────────────────────── Folders ─────────────────────────────

@Serializable enum class FolderPreview { GRID_2X2, GRID_3X3, STACK, FIRST_ICON }
@Serializable enum class FolderOpenStyle { POPUP, FULLSCREEN }

@Immutable
@Serializable
data class FolderConfig(
    val preview: FolderPreview = FolderPreview.GRID_2X2,
    val shape: IconShape = IconShape.SQUIRCLE,
    val previewBackground: ColorValue = argb(0x55FFFFFF),
    val previewOpacity: Float = 1f,
    val columns: Int = 4,
    val maxVisibleRows: Int = 4,
    val popupBackground: ColorValue = argb(0xFF1C1C28),
    val popupOpacity: Float = 0.94f,
    val popupCornerRadiusDp: Int = 28,
    val popupBlurDp: Int = 0,
    val openStyle: FolderOpenStyle = FolderOpenStyle.POPUP,
    val animation: OverlayAnimation = OverlayAnimation.SCALE,
    val showLabels: Boolean = true,
    val showTitle: Boolean = true,
    val titleColor: ColorValue = ColorValue.White,
    val titleSizeSp: Float = 20f,
    val labelColor: ColorValue = ColorValue.White,
)

// ───────────────────────────── Widgets ─────────────────────────────

@Serializable enum class ClockStyle { DIGITAL, ANALOG, STACKED }

@Immutable
@Serializable
data class ClockConfig(
    val style: ClockStyle = ClockStyle.DIGITAL,
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val datePattern: String = "EEEE, d MMMM",
    val color: ColorValue = ColorValue.White,
    val sizeSp: Float = 56f,
    val weight: FontWeightOption = FontWeightOption.LIGHT,
    val alignStart: Boolean = false,
)

@Immutable
@Serializable
data class WidgetConfig(
    val paddingDp: Int = 4,
    val cornerRadiusDp: Int = 20,
    val clipToCorners: Boolean = true,
    val containerBackground: ColorValue = argb(0x00000000),
    val containerOpacity: Float = 1f,
    /** Honor the provider's min-resize limits when resizing. */
    val respectProviderLimits: Boolean = true,
    val clock: ClockConfig = ClockConfig(),
)

// ───────────────────────────── Gestures ─────────────────────────────

@Serializable
enum class GestureType {
    SWIPE_UP, SWIPE_DOWN, DOUBLE_TAP, LONG_PRESS_EMPTY, PINCH_IN, PINCH_OUT,
    TWO_FINGER_SWIPE_UP, TWO_FINGER_SWIPE_DOWN, HOME_BUTTON, SWIPE_UP_DOCK,
}

@Serializable
enum class ActionType {
    NONE, OPEN_DRAWER, OPEN_SEARCH, OPEN_FLYER, OPEN_LIVE_EDITOR, HOME_MENU,
    EXPAND_NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, RECENT_APPS, SCREENSHOT,
    LAUNCH_APP, TOGGLE_DESKTOP_MODE, TOGGLE_DARK_MODE, DEFAULT_PAGE, NEXT_PAGE, PREVIOUS_PAGE, OPEN_WIDGETS,
}

@Immutable
@Serializable
data class GestureAction(
    val type: ActionType = ActionType.NONE,
    /** Component key for [ActionType.LAUNCH_APP]. */
    val target: String? = null,
)

@Immutable
@Serializable
data class GestureConfig(
    val bindings: Map<GestureType, GestureAction> = defaultGestureBindings(),
    val swipeThresholdDp: Int = 64,
    val doubleTapTimeoutMs: Int = 300,
    val hapticFeedback: Boolean = true,
)

fun defaultGestureBindings(): Map<GestureType, GestureAction> = mapOf(
    GestureType.SWIPE_UP to GestureAction(ActionType.OPEN_DRAWER),
    GestureType.SWIPE_DOWN to GestureAction(ActionType.EXPAND_NOTIFICATIONS),
    GestureType.DOUBLE_TAP to GestureAction(ActionType.NONE),
    GestureType.LONG_PRESS_EMPTY to GestureAction(ActionType.HOME_MENU),
    GestureType.PINCH_IN to GestureAction(ActionType.OPEN_LIVE_EDITOR),
    GestureType.PINCH_OUT to GestureAction(ActionType.NONE),
    GestureType.TWO_FINGER_SWIPE_UP to GestureAction(ActionType.OPEN_FLYER),
    GestureType.TWO_FINGER_SWIPE_DOWN to GestureAction(ActionType.QUICK_SETTINGS),
    GestureType.HOME_BUTTON to GestureAction(ActionType.DEFAULT_PAGE),
    GestureType.SWIPE_UP_DOCK to GestureAction(ActionType.OPEN_DRAWER),
)

// ───────────────────────────── Animations ─────────────────────────────

@Serializable enum class Easing { STANDARD, EMPHASIZED, LINEAR, DECELERATE, SPRING, BOUNCY }
@Serializable enum class AppLaunchAnimation { SYSTEM, SCALE_UP, CLIP_REVEAL, FADE, NONE }

@Immutable
@Serializable
data class AnimationConfig(
    val enabled: Boolean = true,
    val durationScale: Float = 1f,
    val easing: Easing = Easing.EMPHASIZED,
    val appLaunch: AppLaunchAnimation = AppLaunchAnimation.SCALE_UP,
    val pressScale: Float = 0.9f,
    /** Respect the system "remove animations" setting. */
    val followSystem: Boolean = true,
    /** Zoom/fade the home screen in when returning from an app. */
    val homeEntrance: Boolean = true,
)

// ───────────────────────────── Appearance ─────────────────────────────

@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Immutable
@Serializable
data class AppearanceConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Android 12+: derive colors from the wallpaper (Material You). */
    val dynamicColor: Boolean = true,
    val accent: ColorValue = argb(0xFF6A5BFF),
    /** Corner radius scale applied to Flyer surfaces and launcher menus. */
    val cornerScale: Float = 1f,
    val menuBackground: ColorValue = argb(0xFF1E1E2A),
    val menuOpacity: Float = 0.97f,
    val highContrastText: Boolean = false,
)

// ───────────────────────────── Typography ─────────────────────────────

@Serializable enum class FontChoice { SYSTEM, SANS, SERIF, MONOSPACE, CONDENSED, ROUNDED, CUSTOM }

@Immutable
@Serializable
data class TypographyConfig(
    val font: FontChoice = FontChoice.SYSTEM,
    /** Private path to a user-imported .ttf/.otf used when [font] is CUSTOM. */
    val customFontPath: String? = null,
    val textScale: Float = 1f,
    val letterSpacingEm: Float = 0f,
    val flyerUsesLauncherFont: Boolean = false,
)

// ───────────────────────────── Desktop mode ─────────────────────────────

@Serializable enum class DesktopMode { OFF, ON, AUTO }
@Serializable enum class TaskbarPosition { BOTTOM, TOP }

@Immutable
@Serializable
data class DesktopConfig(
    val mode: DesktopMode = DesktopMode.AUTO,
    val iconSizeDp: Int = 48,
    val columns: Int = 12,
    val rows: Int = 7,
    val snapToGrid: Boolean = true,
    val labelColor: ColorValue = ColorValue.White,
    val doubleClickToOpen: Boolean = false,
    val taskbarPosition: TaskbarPosition = TaskbarPosition.BOTTOM,
    val taskbarHeightDp: Int = 56,
    val taskbarBackground: ColorValue = argb(0xE6181822),
    val taskbarCentered: Boolean = true,
    val taskbarShowClock: Boolean = true,
    val taskbarShowLabels: Boolean = false,
    val startMenuColumns: Int = 6,
    val startMenuWidthDp: Int = 560,
    val startMenuHeightDp: Int = 520,
    val panelBackground: ColorValue = argb(0xF0202030),
    val panelCornerDp: Int = 16,
    val keyboardShortcuts: Boolean = true,
    val hoverEffects: Boolean = true,
    val foldersAsWindows: Boolean = true,
)

// ───────────────────────────── Search ─────────────────────────────

@Serializable enum class SearchBarPosition { TOP, ABOVE_DOCK, HIDDEN }

@Immutable
@Serializable
data class SearchConfig(
    val homeBar: SearchBarPosition = SearchBarPosition.HIDDEN,
    val barBackground: ColorValue = argb(0x33FFFFFF),
    val barCornerDp: Int = 28,
    val barHeightDp: Int = 48,
    val searchApps: Boolean = true,
    val searchShortcuts: Boolean = true,
    val searchFlyerSettings: Boolean = true,
    val webFallback: Boolean = true,
    val fuzzyMatching: Boolean = true,
    val maxResults: Int = 24,
)

// ───────────────────────────── Performance ─────────────────────────────

@Serializable enum class EffectsLevel { FULL, BALANCED, MINIMAL }

@Immutable
@Serializable
data class PerformanceConfig(
    val effects: EffectsLevel = EffectsLevel.BALANCED,
    val blurEffects: Boolean = true,
    val iconCacheMb: Int = 16,
    val iconQuality: Float = 1f,
    /** Render all drawer icons in the background at start so the drawer opens instantly. */
    val prefetchIcons: Boolean = true,
    val lowMemoryMode: Boolean = false,
    val keepWidgetsListening: Boolean = false,
)

// ───────────────────────────── Advanced ─────────────────────────────

@Serializable enum class OrientationLock { AUTO, PORTRAIT, LANDSCAPE }

@Immutable
@Serializable
data class AdvancedConfig(
    val orientation: OrientationLock = OrientationLock.AUTO,
    val homeButtonClosesFlyer: Boolean = true,
    val autoApplyFlyerChanges: Boolean = false,
    val showAdvancedSettings: Boolean = false,
    val showLayoutBounds: Boolean = false,
    val confirmRemovals: Boolean = true,
    val showHiddenAppsInSearch: Boolean = false,
    val addNewAppsToHome: Boolean = false,
    val undoHistorySize: Int = 100,
)
