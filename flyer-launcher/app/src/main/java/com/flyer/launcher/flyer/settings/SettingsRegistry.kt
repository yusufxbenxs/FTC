package com.flyer.launcher.flyer.settings

import android.os.Build
import com.flyer.launcher.core.config.AppLaunchAnimation
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.CellSizing
import com.flyer.launcher.core.config.ClockStyle
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.core.config.DesktopMode
import com.flyer.launcher.core.config.DockAlignment
import com.flyer.launcher.core.config.DockPosition
import com.flyer.launcher.core.config.DrawerLayout
import com.flyer.launcher.core.config.DrawerSort
import com.flyer.launcher.core.config.Easing
import com.flyer.launcher.core.config.EffectsLevel
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.FolderOpenStyle
import com.flyer.launcher.core.config.FolderPreview
import com.flyer.launcher.core.config.FontChoice
import com.flyer.launcher.core.config.FontWeightOption
import com.flyer.launcher.core.config.IconShape
import com.flyer.launcher.core.config.OrientationLock
import com.flyer.launcher.core.config.OverlayAnimation
import com.flyer.launcher.core.config.PageIndicatorStyle
import com.flyer.launcher.core.config.PageTransition
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.config.PressEffect
import com.flyer.launcher.core.config.SearchBarPosition
import com.flyer.launcher.core.config.SystemBarStyle
import com.flyer.launcher.core.config.TaskbarPosition
import com.flyer.launcher.core.config.ThemeMode
import com.flyer.launcher.core.config.VerticalPlacement
import com.flyer.launcher.core.theme.DeviceTraits
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.settings.EditorElement as E
import com.flyer.launcher.flyer.settings.SettingLevel.ADVANCED

/**
 * Every setting Flyer exposes. Category screens, the search index, the live editor inspector
 * and resets are all generated from this list, and every entry reads/writes a real field of
 * [FlyerConfig] that launcher components render from.
 */
object SettingsRegistry {

    private class Builder(val category: FlyerCategory) {
        var section = ""
        val out = mutableListOf<SettingSpec>()

        private fun meta(
            id: String, title: String, description: String?, keywords: List<String>, level: SettingLevel,
            elements: Set<E>, visible: (FlyerConfig) -> Boolean, limitation: (FlyerConfig, DeviceTraits) -> String?,
        ) = Meta("${category.name.lowercase()}.$id", title, category, section, description, keywords, level, elements, visible, limitation)

        fun toggle(
            id: String, title: String, description: String? = null, keywords: List<String> = emptyList(),
            level: SettingLevel = SettingLevel.BASIC, el: Set<E> = emptySet(),
            visible: (FlyerConfig) -> Boolean = { true }, limitation: (FlyerConfig, DeviceTraits) -> String? = { _, _ -> null },
            get: (FlyerConfig) -> Boolean, set: (FlyerConfig, Boolean) -> FlyerConfig,
        ) { out += ToggleSpec(meta(id, title, description, keywords, level, el, visible, limitation), get, set) }

        fun slider(
            id: String, title: String, range: ClosedFloatingPointRange<Float>, step: Float, unit: String = "",
            description: String? = null, keywords: List<String> = emptyList(), level: SettingLevel = SettingLevel.BASIC,
            el: Set<E> = emptySet(), visible: (FlyerConfig) -> Boolean = { true },
            limitation: (FlyerConfig, DeviceTraits) -> String? = { _, _ -> null },
            format: ((Float) -> String)? = null,
            get: (FlyerConfig) -> Float, set: (FlyerConfig, Float) -> FlyerConfig,
        ) {
            val m = meta(id, title, description, keywords, level, el, visible, limitation)
            out += if (format != null) SliderSpec(m, range, step, unit, get, set, format) else SliderSpec(m, range, step, unit, get, set)
        }

        fun <T> choice(
            id: String, title: String, options: List<Pair<T, String>>, description: String? = null,
            keywords: List<String> = emptyList(), level: SettingLevel = SettingLevel.BASIC, el: Set<E> = emptySet(),
            visible: (FlyerConfig) -> Boolean = { true }, limitation: (FlyerConfig, DeviceTraits) -> String? = { _, _ -> null },
            get: (FlyerConfig) -> T, set: (FlyerConfig, T) -> FlyerConfig,
        ) { out += ChoiceSpec(meta(id, title, description, keywords, level, el, visible, limitation), options, get, set) }

        fun color(
            id: String, title: String, alpha: Boolean = true, description: String? = null,
            keywords: List<String> = emptyList(), level: SettingLevel = SettingLevel.BASIC, el: Set<E> = emptySet(),
            visible: (FlyerConfig) -> Boolean = { true },
            get: (FlyerConfig) -> ColorValue, set: (FlyerConfig, ColorValue) -> FlyerConfig,
        ) { out += ColorSpec(meta(id, title, description, keywords, level, el, visible) { _, _ -> null }, alpha, get, set) }

        fun text(
            id: String, title: String, placeholder: String, validate: (String) -> String?, description: String? = null,
            keywords: List<String> = emptyList(), level: SettingLevel = SettingLevel.BASIC, el: Set<E> = emptySet(),
            get: (FlyerConfig) -> String, set: (FlyerConfig, String) -> FlyerConfig,
        ) { out += TextSpec(meta(id, title, description, keywords, level, el, { true }) { _, _ -> null }, placeholder, validate, get, set) }
    }

    private fun build(category: FlyerCategory, block: Builder.() -> Unit) = Builder(category).apply(block).out

    private val R = ConfigValidator.Ranges
    private fun IntRange.f() = first.toFloat()..last.toFloat()
    private fun pct(v: Float) = "${(v * 100).toInt()}%"
    private val shapeOptions = listOf(
        IconShape.SYSTEM to "System", IconShape.CIRCLE to "Circle", IconShape.SQUIRCLE to "Squircle",
        IconShape.ROUNDED_SQUARE to "Rounded square", IconShape.SQUARE to "Square", IconShape.TEARDROP to "Teardrop",
        IconShape.HEXAGON to "Hexagon", IconShape.PEBBLE to "Pebble", IconShape.CLOVER to "Clover",
    )
    private val weightOptions = listOf(
        FontWeightOption.LIGHT to "Light", FontWeightOption.NORMAL to "Regular", FontWeightOption.MEDIUM to "Medium",
        FontWeightOption.SEMIBOLD to "Semibold", FontWeightOption.BOLD to "Bold",
    )
    private val overlayAnimations = listOf(
        OverlayAnimation.SLIDE_UP to "Slide up", OverlayAnimation.FADE to "Fade", OverlayAnimation.SCALE to "Zoom",
        OverlayAnimation.SLIDE_SIDE to "Slide sideways", OverlayAnimation.NONE to "None",
    )
    private val blurLimitation: (FlyerConfig, DeviceTraits) -> String? = { c, t ->
        when {
            !t.blurSupported -> "Blur needs Android 12 or newer."
            !c.performance.blurEffects -> "Blur effects are turned off in Performance."
            else -> null
        }
    }

    val home = build(FlyerCategory.HOME) {
        section = "Grid"
        slider("columns", "Columns", R.columns.f(), 1f, keywords = listOf("grid", "width"), el = setOf(E.WORKSPACE),
            get = { it.home.columns.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(columns = v.toInt())) })
        slider("rows", "Rows", R.rows.f(), 1f, keywords = listOf("grid", "height"), el = setOf(E.WORKSPACE),
            get = { it.home.rows.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(rows = v.toInt())) })
        choice("positioning", "Positioning", listOf(Positioning.GRID to "Grid", Positioning.FREE to "Free"),
            description = "Free positioning lets you place items anywhere; your grid positions are kept.",
            keywords = listOf("free", "anywhere", "snap"), el = setOf(E.WORKSPACE),
            get = { it.home.positioning }, set = { c, v -> c.copy(home = c.home.copy(positioning = v)) })
        slider("freeSnap", "Free positioning snap step", 0f..64f, 1f, "dp", el = setOf(E.WORKSPACE), level = ADVANCED,
            description = "0 disables snapping.", visible = { it.home.positioning == Positioning.FREE },
            get = { it.home.freeSnapDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(freeSnapDp = v.toInt())) })
        toggle("overlap", "Allow overlapping items", el = setOf(E.WORKSPACE), level = ADVANCED, visible = { it.home.positioning == Positioning.FREE },
            get = { it.home.allowOverlap }, set = { c, v -> c.copy(home = c.home.copy(allowOverlap = v)) })
        choice("cellSizing", "Cell height", listOf(CellSizing.FILL to "Fill screen", CellSizing.FIXED to "Fixed"), el = setOf(E.WORKSPACE),
            get = { it.home.cellSizing }, set = { c, v -> c.copy(home = c.home.copy(cellSizing = v)) })
        slider("fixedHeight", "Fixed cell height", R.fixedCellHeight.f(), 1f, "dp", el = setOf(E.WORKSPACE), visible = { it.home.cellSizing == CellSizing.FIXED },
            get = { it.home.fixedCellHeightDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(fixedCellHeightDp = v.toInt())) })
        choice("placement", "Grid alignment", listOf(VerticalPlacement.TOP to "Top", VerticalPlacement.CENTER to "Center", VerticalPlacement.BOTTOM to "Bottom"),
            el = setOf(E.WORKSPACE), visible = { it.home.cellSizing == CellSizing.FIXED },
            get = { it.home.gridPlacement }, set = { c, v -> c.copy(home = c.home.copy(gridPlacement = v)) })

        section = "Margins & spacing"
        slider("marginStart", "Left margin", R.margin.f(), 1f, "dp", keywords = listOf("padding"), el = setOf(E.WORKSPACE),
            get = { it.home.marginStartDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(marginStartDp = v.toInt())) })
        slider("marginEnd", "Right margin", R.margin.f(), 1f, "dp", keywords = listOf("padding"), el = setOf(E.WORKSPACE),
            get = { it.home.marginEndDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(marginEndDp = v.toInt())) })
        slider("marginTop", "Top margin", R.margin.f(), 1f, "dp", keywords = listOf("padding"), el = setOf(E.WORKSPACE),
            get = { it.home.marginTopDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(marginTopDp = v.toInt())) })
        slider("marginBottom", "Bottom margin", R.margin.f(), 1f, "dp", keywords = listOf("padding"), el = setOf(E.WORKSPACE),
            get = { it.home.marginBottomDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(marginBottomDp = v.toInt())) })
        slider("spacingH", "Horizontal spacing", R.cellSpacing.f(), 1f, "dp", keywords = listOf("gap"), el = setOf(E.WORKSPACE),
            get = { it.home.cellSpacingHDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(cellSpacingHDp = v.toInt())) })
        slider("spacingV", "Vertical spacing", R.cellSpacing.f(), 1f, "dp", keywords = listOf("gap"), el = setOf(E.WORKSPACE),
            get = { it.home.cellSpacingVDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(cellSpacingVDp = v.toInt())) })

        section = "Pages"
        toggle("infinite", "Infinite scrolling", "Swiping past the last page returns to the first.", keywords = listOf("loop", "wrap"),
            get = { it.home.infiniteScroll }, set = { c, v -> c.copy(home = c.home.copy(infiniteScroll = v)) })
        choice("indicator", "Page indicator", listOf(PageIndicatorStyle.DOTS to "Dots", PageIndicatorStyle.LINE to "Line", PageIndicatorStyle.NUMBERS to "Numbers", PageIndicatorStyle.NONE to "Hidden"),
            el = setOf(E.PAGE_INDICATOR), get = { it.home.pageIndicator }, set = { c, v -> c.copy(home = c.home.copy(pageIndicator = v)) })
        color("indicatorColor", "Indicator color", el = setOf(E.PAGE_INDICATOR), visible = { it.home.pageIndicator != PageIndicatorStyle.NONE },
            get = { it.home.pageIndicatorColor }, set = { c, v -> c.copy(home = c.home.copy(pageIndicatorColor = v)) })
        toggle("indicatorTop", "Indicator at the top", el = setOf(E.PAGE_INDICATOR), visible = { it.home.pageIndicator != PageIndicatorStyle.NONE },
            get = { it.home.pageIndicatorAtTop }, set = { c, v -> c.copy(home = c.home.copy(pageIndicatorAtTop = v)) })

        section = "Background"
        choice("bgType", "Background", listOf(BackgroundType.SYSTEM_WALLPAPER to "System wallpaper", BackgroundType.SOLID to "Solid color", BackgroundType.GRADIENT to "Gradient", BackgroundType.IMAGE to "Image"),
            keywords = listOf("wallpaper", "image", "gradient"), el = setOf(E.BACKGROUND),
            get = { it.home.background.type }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(type = v))) })
        color("bgColor", "Background color", alpha = false, el = setOf(E.BACKGROUND), visible = { it.home.background.type != BackgroundType.SYSTEM_WALLPAPER },
            get = { it.home.background.color }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(color = v))) })
        color("bgColor2", "Gradient end color", alpha = false, el = setOf(E.BACKGROUND), visible = { it.home.background.type == BackgroundType.GRADIENT },
            get = { it.home.background.gradientEnd }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(gradientEnd = v))) })
        slider("bgAngle", "Gradient angle", 0f..359f, 1f, "°", el = setOf(E.BACKGROUND), visible = { it.home.background.type == BackgroundType.GRADIENT },
            get = { it.home.background.gradientAngle.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(gradientAngle = v.toInt()))) })
        slider("bgBlur", "Image blur", R.blur.f(), 1f, "dp", el = setOf(E.BACKGROUND), visible = { it.home.background.type == BackgroundType.IMAGE },
            limitation = { _, t -> if (!t.blurSupported) "Blur needs Android 12 or newer." else null },
            get = { it.home.background.imageBlurDp.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(imageBlurDp = v.toInt()))) })
        slider("dim", "Darken background", 0f..1f, 0.01f, format = ::pct, keywords = listOf("dim", "scrim", "contrast"), el = setOf(E.BACKGROUND),
            get = { it.home.background.dim }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(dim = v))) })
        toggle("parallax", "Wallpaper scrolls with pages", keywords = listOf("parallax"), el = setOf(E.BACKGROUND),
            visible = { it.home.background.type == BackgroundType.SYSTEM_WALLPAPER },
            limitation = { _, _ -> "Some wallpapers (and Android versions) ignore scrolling offsets." },
            get = { it.home.background.wallpaperScrolling }, set = { c, v -> c.copy(home = c.home.copy(background = c.home.background.copy(wallpaperScrolling = v))) })

        section = "System bars"
        toggle("statusBar", "Show status bar", keywords = listOf("immersive", "fullscreen"),
            get = { it.home.showStatusBar }, set = { c, v -> c.copy(home = c.home.copy(showStatusBar = v)) })
        toggle("navBar", "Show navigation bar", keywords = listOf("immersive", "fullscreen", "gesture bar"),
            description = "When hidden, swipe from the screen edge to reveal it temporarily.",
            get = { it.home.showNavigationBar }, set = { c, v -> c.copy(home = c.home.copy(showNavigationBar = v)) })
        choice("barIcons", "Status bar icons", listOf(SystemBarStyle.AUTO to "Automatic", SystemBarStyle.LIGHT_ICONS to "Light", SystemBarStyle.DARK_ICONS to "Dark"),
            get = { it.home.systemBarIcons }, set = { c, v -> c.copy(home = c.home.copy(systemBarIcons = v)) })

        section = "Behaviour"
        toggle("lock", "Lock home screen layout", "Prevents moving or removing items by accident.", keywords = listOf("lock", "protect"),
            get = { it.home.lockLayout }, set = { c, v -> c.copy(home = c.home.copy(lockLayout = v)) })
        toggle("gridWhileDragging", "Show grid while dragging", level = ADVANCED,
            get = { it.home.showGridWhileDragging }, set = { c, v -> c.copy(home = c.home.copy(showGridWhileDragging = v)) })
    }

    val icons = build(FlyerCategory.ICONS) {
        section = "Size & shape"
        slider("size", "Icon size", R.iconSize.f(), 1f, "dp", el = setOf(E.ICON),
            get = { it.icons.sizeDp.toFloat() }, set = { c, v -> c.copy(icons = c.icons.copy(sizeDp = v.toInt())) })
        choice("shape", "Icon shape", shapeOptions, keywords = listOf("mask", "round", "squircle"), el = setOf(E.ICON),
            get = { it.icons.shape }, set = { c, v -> c.copy(icons = c.icons.copy(shape = v)) })
        slider("corner", "Corner roundness", R.cornerPercent.f(), 1f, "%", el = setOf(E.ICON),
            visible = { it.icons.shape in setOf(IconShape.ROUNDED_SQUARE, IconShape.TEARDROP, IconShape.PEBBLE, IconShape.HEXAGON) },
            get = { it.icons.cornerRadiusPercent.toFloat() }, set = { c, v -> c.copy(icons = c.icons.copy(cornerRadiusPercent = v.toInt())) })
        slider("inset", "Icon inset", 0f..40f, 1f, "%", "Shrinks the artwork inside its shape.", level = ADVANCED, el = setOf(E.ICON),
            get = { it.icons.insetPercent.toFloat() }, set = { c, v -> c.copy(icons = c.icons.copy(insetPercent = v.toInt())) })

        section = "Legacy & themed icons"
        toggle("shapeLegacy", "Reshape legacy icons", "Puts non-adaptive icons onto your icon shape.", el = setOf(E.ICON),
            get = { it.icons.shapeLegacyIcons }, set = { c, v -> c.copy(icons = c.icons.copy(shapeLegacyIcons = v)) })
        color("legacyBg", "Legacy icon background", alpha = false, el = setOf(E.ICON), visible = { it.icons.shapeLegacyIcons },
            get = { it.icons.legacyBackground }, set = { c, v -> c.copy(icons = c.icons.copy(legacyBackground = v)) })
        toggle("themed", "Themed icons", "Uses apps' monochrome icons tinted with the colors below.", keywords = listOf("monochrome", "material you"),
            el = setOf(E.ICON), limitation = { _, t -> if (!t.themedIconsSupported) "Themed icons need Android 13 or newer." else "Apps without a monochrome icon keep their normal icon." },
            get = { it.icons.themedIcons }, set = { c, v -> c.copy(icons = c.icons.copy(themedIcons = v)) })
        color("themedFg", "Themed icon color", alpha = false, el = setOf(E.ICON), visible = { it.icons.themedIcons },
            get = { it.icons.themedForeground }, set = { c, v -> c.copy(icons = c.icons.copy(themedForeground = v)) })
        color("themedBg", "Themed icon background", alpha = false, el = setOf(E.ICON), visible = { it.icons.themedIcons },
            get = { it.icons.themedBackground }, set = { c, v -> c.copy(icons = c.icons.copy(themedBackground = v)) })
        toggle("workBadge", "Badge work profile apps", level = ADVANCED, el = setOf(E.ICON),
            get = { it.icons.badgeWorkApps }, set = { c, v -> c.copy(icons = c.icons.copy(badgeWorkApps = v)) })

        section = "Effects"
        slider("opacity", "Icon opacity", 0.1f..1f, 0.01f, format = ::pct, keywords = listOf("transparency", "alpha"), el = setOf(E.ICON),
            get = { it.icons.opacity }, set = { c, v -> c.copy(icons = c.icons.copy(opacity = v)) })
        slider("saturation", "Icon saturation", 0f..1f, 0.01f, format = ::pct, description = "0% shows icons in grayscale.", keywords = listOf("grayscale", "monochrome", "color"),
            el = setOf(E.ICON), get = { it.icons.saturation }, set = { c, v -> c.copy(icons = c.icons.copy(saturation = v)) })
        toggle("shadow", "Icon shadow", el = setOf(E.ICON), visible = { it.icons.shape != IconShape.SYSTEM },
            get = { it.icons.shadow }, set = { c, v -> c.copy(icons = c.icons.copy(shadow = v)) })
        slider("shadowSize", "Shadow size", 0f..24f, 1f, "dp", el = setOf(E.ICON), visible = { it.icons.shadow && it.icons.shape != IconShape.SYSTEM },
            get = { it.icons.shadowElevationDp.toFloat() }, set = { c, v -> c.copy(icons = c.icons.copy(shadowElevationDp = v.toInt())) })
        slider("border", "Border width", 0f..8f, 0.5f, "dp", el = setOf(E.ICON), visible = { it.icons.shape != IconShape.SYSTEM },
            get = { it.icons.borderWidthDp }, set = { c, v -> c.copy(icons = c.icons.copy(borderWidthDp = v)) })
        color("borderColor", "Border color", el = setOf(E.ICON), visible = { it.icons.borderWidthDp > 0f },
            get = { it.icons.borderColor }, set = { c, v -> c.copy(icons = c.icons.copy(borderColor = v)) })
        choice("press", "Press effect", listOf(PressEffect.SCALE to "Shrink", PressEffect.DIM to "Dim", PressEffect.NONE to "None"), el = setOf(E.ICON),
            get = { it.icons.pressEffect }, set = { c, v -> c.copy(icons = c.icons.copy(pressEffect = v)) })

        section = "Labels"
        toggle("labels", "Show labels", keywords = listOf("names", "text"), el = setOf(E.LABEL),
            get = { it.icons.label.show }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(show = v))) })
        color("labelColor", "Label color", el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.label.color }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(color = v))) })
        slider("labelSize", "Label size", R.labelSize, 0.5f, "sp", el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.label.sizeSp }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(sizeSp = v))) })
        choice("labelWeight", "Label weight", weightOptions, el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.label.weight }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(weight = v))) })
        slider("labelLines", "Label lines", R.labelMaxLines.f(), 1f, el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.label.maxLines.toFloat() }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(maxLines = v.toInt()))) })
        toggle("labelShadow", "Label shadow", el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.label.shadow }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(shadow = v))) })
        color("labelShadowColor", "Label shadow color", level = ADVANCED, el = setOf(E.LABEL), visible = { it.icons.label.show && it.icons.label.shadow },
            get = { it.icons.label.shadowColor }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(shadowColor = v))) })
        toggle("labelUpper", "Uppercase labels", el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.label.uppercase }, set = { c, v -> c.copy(icons = c.icons.copy(label = c.icons.label.copy(uppercase = v))) })
        slider("labelGap", "Gap between icon and label", 0f..32f, 1f, "dp", el = setOf(E.LABEL), visible = { it.icons.label.show },
            get = { it.icons.labelGapDp.toFloat() }, set = { c, v -> c.copy(icons = c.icons.copy(labelGapDp = v.toInt())) })
    }

    val dock = build(FlyerCategory.DOCK) {
        section = "Dock"
        toggle("enabled", "Show dock", keywords = listOf("hotseat", "visibility"), el = setOf(E.DOCK),
            get = { it.dock.enabled }, set = { c, v -> c.copy(dock = c.dock.copy(enabled = v)) })
        choice("position", "Position", listOf(DockPosition.BOTTOM to "Bottom", DockPosition.TOP to "Top", DockPosition.LEFT to "Left", DockPosition.RIGHT to "Right"),
            el = setOf(E.DOCK), get = { it.dock.position }, set = { c, v -> c.copy(dock = c.dock.copy(position = v)) })
        slider("slots", "Number of icons", R.dockSlots.f(), 1f, description = "Items beyond the new limit move to your home pages.", el = setOf(E.DOCK),
            get = { it.dock.slots.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(slots = v.toInt())) })
        choice("alignment", "Icon alignment", listOf(DockAlignment.SPACE_EVENLY to "Spread evenly", DockAlignment.SPACE_BETWEEN to "Spread to edges", DockAlignment.CENTER to "Center", DockAlignment.START to "Start", DockAlignment.END to "End"),
            el = setOf(E.DOCK), get = { it.dock.alignment }, set = { c, v -> c.copy(dock = c.dock.copy(alignment = v)) })
        slider("spacing", "Icon spacing", 0f..64f, 1f, "dp", el = setOf(E.DOCK),
            visible = { it.dock.alignment in setOf(DockAlignment.CENTER, DockAlignment.START, DockAlignment.END) },
            get = { it.dock.iconSpacingDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(iconSpacingDp = v.toInt())) })
        toggle("followIcon", "Same icon size as home", el = setOf(E.DOCK),
            get = { it.dock.followIconSize }, set = { c, v -> c.copy(dock = c.dock.copy(followIconSize = v)) })
        slider("iconSize", "Dock icon size", R.dockIconSize.f(), 1f, "dp", el = setOf(E.DOCK), visible = { !it.dock.followIconSize },
            get = { it.dock.iconSizeDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(iconSizeDp = v.toInt())) })
        toggle("labels", "Show labels in dock", el = setOf(E.DOCK),
            get = { it.dock.showLabels }, set = { c, v -> c.copy(dock = c.dock.copy(showLabels = v)) })
        toggle("search", "Search button in dock", level = ADVANCED, el = setOf(E.DOCK),
            get = { it.dock.showSearchInDock }, set = { c, v -> c.copy(dock = c.dock.copy(showSearchInDock = v)) })

        section = "Size & placement"
        slider("thickness", "Height", R.dockThickness.f(), 1f, "dp", "Thickness of the dock (width when it is on a side).", el = setOf(E.DOCK),
            get = { it.dock.thicknessDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(thicknessDp = v.toInt())) })
        slider("length", "Width", R.dockLength, 0.01f, format = ::pct, description = "Length along the screen edge; below 100% the dock floats.", keywords = listOf("floating"),
            el = setOf(E.DOCK), get = { it.dock.lengthFraction }, set = { c, v -> c.copy(dock = c.dock.copy(lengthFraction = v)) })
        slider("margin", "Distance from edge", 0f..96f, 1f, "dp", el = setOf(E.DOCK),
            get = { it.dock.marginDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(marginDp = v.toInt())) })
        slider("edgeMargin", "Side margins", 0f..96f, 1f, "dp", el = setOf(E.DOCK),
            get = { it.dock.edgeMarginDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(edgeMarginDp = v.toInt())) })

        section = "Background"
        color("bg", "Background color", el = setOf(E.DOCK), get = { it.dock.background }, set = { c, v -> c.copy(dock = c.dock.copy(background = v)) })
        slider("opacity", "Background opacity", 0f..1f, 0.01f, format = ::pct, keywords = listOf("transparency"), el = setOf(E.DOCK),
            get = { it.dock.opacity }, set = { c, v -> c.copy(dock = c.dock.copy(opacity = v)) })
        slider("corner", "Corner radius", 0f..100f, 1f, "dp", el = setOf(E.DOCK),
            get = { it.dock.cornerRadiusDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(cornerRadiusDp = v.toInt())) })
        slider("blur", "Background blur", R.blur.f(), 1f, "dp", el = setOf(E.DOCK), keywords = listOf("frosted", "glass"),
            limitation = { c, t ->
                blurLimitation(c, t) ?: if (c.home.background.type == BackgroundType.SYSTEM_WALLPAPER)
                    "Android doesn't let apps blur part of the system wallpaper. Choose a launcher background (Home Screen › Background) to blur behind the dock." else null
            },
            get = { it.dock.blurDp.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(blurDp = v.toInt())) })
        slider("border", "Border width", 0f..8f, 0.5f, "dp", el = setOf(E.DOCK),
            get = { it.dock.borderWidthDp }, set = { c, v -> c.copy(dock = c.dock.copy(borderWidthDp = v)) })
        color("borderColor", "Border color", el = setOf(E.DOCK), visible = { it.dock.borderWidthDp > 0f },
            get = { it.dock.borderColor }, set = { c, v -> c.copy(dock = c.dock.copy(borderColor = v)) })
    }

    val drawer = build(FlyerCategory.DRAWER) {
        section = "Layout"
        choice("layout", "Layout", listOf(DrawerLayout.GRID to "Grid", DrawerLayout.LIST to "List"),
            get = { it.drawer.layout }, set = { c, v -> c.copy(drawer = c.drawer.copy(layout = v)) })
        slider("columns", "Columns", R.drawerColumns.f(), 1f, visible = { it.drawer.layout == DrawerLayout.GRID },
            get = { it.drawer.columns.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(columns = v.toInt())) })
        toggle("followIcon", "Same icon size as home",
            get = { it.drawer.followIconSize }, set = { c, v -> c.copy(drawer = c.drawer.copy(followIconSize = v)) })
        slider("iconSize", "Drawer icon size", R.iconSize.f(), 1f, "dp", visible = { !it.drawer.followIconSize },
            get = { it.drawer.iconSizeDp.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(iconSizeDp = v.toInt())) })
        slider("rowSpacing", "Row spacing", 0f..64f, 1f, "dp",
            get = { it.drawer.rowSpacingDp.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(rowSpacingDp = v.toInt())) })
        slider("hPadding", "Side padding", 0f..64f, 1f, "dp", level = ADVANCED,
            get = { it.drawer.horizontalPaddingDp.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(horizontalPaddingDp = v.toInt())) })
        toggle("labels", "Show labels", get = { it.drawer.showLabels }, set = { c, v -> c.copy(drawer = c.drawer.copy(showLabels = v)) })
        color("labelColor", "Text color", get = { it.drawer.labelColor }, set = { c, v -> c.copy(drawer = c.drawer.copy(labelColor = v)) })

        section = "Sorting & sections"
        choice("sort", "Sort apps by", listOf(DrawerSort.NAME_ASC to "Name (A–Z)", DrawerSort.NAME_DESC to "Name (Z–A)", DrawerSort.MOST_USED to "Most used", DrawerSort.RECENTLY_INSTALLED to "Recently installed", DrawerSort.RECENTLY_UPDATED to "Recently updated"),
            keywords = listOf("order", "alphabetical"), get = { it.drawer.sort }, set = { c, v -> c.copy(drawer = c.drawer.copy(sort = v)) })
        toggle("recent", "Recently used row", get = { it.drawer.showRecentRow }, set = { c, v -> c.copy(drawer = c.drawer.copy(showRecentRow = v)) })
        toggle("categories", "Category tabs", "Games, Social, Photos… from each app's category.", keywords = listOf("tabs"),
            get = { it.drawer.showCategories }, set = { c, v -> c.copy(drawer = c.drawer.copy(showCategories = v)) })
        toggle("groups", "My groups as tabs", get = { it.drawer.showUserGroups }, set = { c, v -> c.copy(drawer = c.drawer.copy(showUserGroups = v)) })
        toggle("work", "Separate work apps", keywords = listOf("profile"), get = { it.drawer.separateWorkApps }, set = { c, v -> c.copy(drawer = c.drawer.copy(separateWorkApps = v)) })
        toggle("fastScroll", "Alphabet fast scroller", get = { it.drawer.fastScroller }, set = { c, v -> c.copy(drawer = c.drawer.copy(fastScroller = v)) })

        section = "Search"
        toggle("search", "Search field", get = { it.drawer.showSearch }, set = { c, v -> c.copy(drawer = c.drawer.copy(showSearch = v)) })
        toggle("searchBottom", "Search at the bottom", visible = { it.drawer.showSearch },
            get = { it.drawer.searchAtBottom }, set = { c, v -> c.copy(drawer = c.drawer.copy(searchAtBottom = v)) })
        toggle("keyboard", "Open keyboard automatically", visible = { it.drawer.showSearch },
            get = { it.drawer.autoShowKeyboard }, set = { c, v -> c.copy(drawer = c.drawer.copy(autoShowKeyboard = v)) })

        section = "Background & motion"
        color("bg", "Background color", alpha = false, get = { it.drawer.background }, set = { c, v -> c.copy(drawer = c.drawer.copy(background = v)) })
        slider("opacity", "Background opacity", 0f..1f, 0.01f, format = ::pct, keywords = listOf("transparency"),
            get = { it.drawer.opacity }, set = { c, v -> c.copy(drawer = c.drawer.copy(opacity = v)) })
        slider("blur", "Blur behind drawer", R.blur.f(), 1f, "dp", keywords = listOf("frosted", "glass"),
            limitation = { c, t -> blurLimitation(c, t) ?: "Some devices disable cross-window blur (e.g. in battery saver)." },
            get = { it.drawer.blurDp.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(blurDp = v.toInt())) })
        slider("corner", "Top corner radius", R.corner.f(), 1f, "dp",
            get = { it.drawer.cornerRadiusDp.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(cornerRadiusDp = v.toInt())) })
        choice("animation", "Open animation", overlayAnimations, get = { it.drawer.animation }, set = { c, v -> c.copy(drawer = c.drawer.copy(animation = v)) })
    }

    val widgets = build(FlyerCategory.WIDGETS) {
        section = "Widget containers"
        slider("padding", "Padding", 0f..48f, 1f, "dp", el = setOf(E.WIDGET),
            get = { it.widgets.paddingDp.toFloat() }, set = { c, v -> c.copy(widgets = c.widgets.copy(paddingDp = v.toInt())) })
        slider("corner", "Corner radius", R.corner.f(), 1f, "dp", el = setOf(E.WIDGET),
            get = { it.widgets.cornerRadiusDp.toFloat() }, set = { c, v -> c.copy(widgets = c.widgets.copy(cornerRadiusDp = v.toInt())) })
        toggle("clip", "Clip widgets to corners", el = setOf(E.WIDGET),
            get = { it.widgets.clipToCorners }, set = { c, v -> c.copy(widgets = c.widgets.copy(clipToCorners = v)) })
        color("bg", "Container background", el = setOf(E.WIDGET), description = "Drawn behind widgets; transparent widgets show it.",
            get = { it.widgets.containerBackground }, set = { c, v -> c.copy(widgets = c.widgets.copy(containerBackground = v)) })
        slider("opacity", "Container opacity", 0f..1f, 0.01f, format = ::pct, el = setOf(E.WIDGET),
            get = { it.widgets.containerOpacity }, set = { c, v -> c.copy(widgets = c.widgets.copy(containerOpacity = v)) })
        toggle("limits", "Respect widget size limits", "Turn off to resize beyond the sizes an app declares (may look broken).", level = ADVANCED, el = setOf(E.WIDGET),
            get = { it.widgets.respectProviderLimits }, set = { c, v -> c.copy(widgets = c.widgets.copy(respectProviderLimits = v)) })

        section = "Built-in clock"
        choice("clockStyle", "Clock style", listOf(ClockStyle.DIGITAL to "Digital", ClockStyle.STACKED to "Stacked", ClockStyle.ANALOG to "Analog"), el = setOf(E.CLOCK),
            get = { it.widgets.clock.style }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(style = v))) })
        toggle("clock24", "24-hour time", el = setOf(E.CLOCK),
            get = { it.widgets.clock.use24h }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(use24h = v))) })
        toggle("clockSeconds", "Show seconds", el = setOf(E.CLOCK),
            get = { it.widgets.clock.showSeconds }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(showSeconds = v))) })
        toggle("clockDate", "Show date", el = setOf(E.CLOCK),
            get = { it.widgets.clock.showDate }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(showDate = v))) })
        text("datePattern", "Date format", "EEEE, d MMMM", validate = { if (ConfigValidator.isValidDatePattern(it)) null else "Not a valid date pattern" },
            description = "Java date pattern, e.g. EEE d MMM or dd/MM/yyyy.", el = setOf(E.CLOCK),
            get = { it.widgets.clock.datePattern }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(datePattern = v))) })
        color("clockColor", "Clock color", el = setOf(E.CLOCK),
            get = { it.widgets.clock.color }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(color = v))) })
        slider("clockSize", "Clock size", 12f..160f, 1f, "sp", el = setOf(E.CLOCK),
            get = { it.widgets.clock.sizeSp }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(sizeSp = v))) })
        choice("clockWeight", "Clock weight", weightOptions, el = setOf(E.CLOCK),
            get = { it.widgets.clock.weight }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(weight = v))) })
        toggle("clockStart", "Align to the left", el = setOf(E.CLOCK),
            get = { it.widgets.clock.alignStart }, set = { c, v -> c.copy(widgets = c.widgets.copy(clock = c.widgets.clock.copy(alignStart = v))) })
    }

    val folders = build(FlyerCategory.FOLDERS) {
        section = "Folder icon"
        choice("preview", "Preview style", listOf(FolderPreview.GRID_2X2 to "2 × 2 grid", FolderPreview.GRID_3X3 to "3 × 3 grid", FolderPreview.STACK to "Stack", FolderPreview.FIRST_ICON to "First app"),
            el = setOf(E.FOLDER), get = { it.folders.preview }, set = { c, v -> c.copy(folders = c.folders.copy(preview = v)) })
        choice("shape", "Folder shape", shapeOptions, el = setOf(E.FOLDER), get = { it.folders.shape }, set = { c, v -> c.copy(folders = c.folders.copy(shape = v)) })
        color("previewBg", "Folder icon color", el = setOf(E.FOLDER), get = { it.folders.previewBackground }, set = { c, v -> c.copy(folders = c.folders.copy(previewBackground = v)) })
        slider("previewOpacity", "Folder icon opacity", 0f..1f, 0.01f, format = ::pct, el = setOf(E.FOLDER),
            get = { it.folders.previewOpacity }, set = { c, v -> c.copy(folders = c.folders.copy(previewOpacity = v)) })

        section = "Open folder"
        choice("style", "Open as", listOf(FolderOpenStyle.POPUP to "Popup", FolderOpenStyle.FULLSCREEN to "Full screen"), el = setOf(E.FOLDER),
            get = { it.folders.openStyle }, set = { c, v -> c.copy(folders = c.folders.copy(openStyle = v)) })
        slider("columns", "Columns", R.folderColumns.f(), 1f, el = setOf(E.FOLDER),
            get = { it.folders.columns.toFloat() }, set = { c, v -> c.copy(folders = c.folders.copy(columns = v.toInt())) })
        slider("rows", "Visible rows", R.folderRows.f(), 1f, el = setOf(E.FOLDER), description = "More apps scroll.",
            get = { it.folders.maxVisibleRows.toFloat() }, set = { c, v -> c.copy(folders = c.folders.copy(maxVisibleRows = v.toInt())) })
        color("popupBg", "Background color", alpha = false, el = setOf(E.FOLDER), get = { it.folders.popupBackground }, set = { c, v -> c.copy(folders = c.folders.copy(popupBackground = v)) })
        slider("popupOpacity", "Background opacity", 0.2f..1f, 0.01f, format = ::pct, el = setOf(E.FOLDER),
            get = { it.folders.popupOpacity }, set = { c, v -> c.copy(folders = c.folders.copy(popupOpacity = v)) })
        slider("popupCorner", "Corner radius", R.corner.f(), 1f, "dp", el = setOf(E.FOLDER),
            get = { it.folders.popupCornerRadiusDp.toFloat() }, set = { c, v -> c.copy(folders = c.folders.copy(popupCornerRadiusDp = v.toInt())) })
        slider("popupBlur", "Blur behind folder", R.blur.f(), 1f, "dp", el = setOf(E.FOLDER), limitation = blurLimitation,
            get = { it.folders.popupBlurDp.toFloat() }, set = { c, v -> c.copy(folders = c.folders.copy(popupBlurDp = v.toInt())) })
        choice("animation", "Open animation", overlayAnimations, el = setOf(E.FOLDER),
            get = { it.folders.animation }, set = { c, v -> c.copy(folders = c.folders.copy(animation = v)) })

        section = "Text"
        toggle("title", "Show folder name", el = setOf(E.FOLDER), get = { it.folders.showTitle }, set = { c, v -> c.copy(folders = c.folders.copy(showTitle = v)) })
        color("titleColor", "Name color", el = setOf(E.FOLDER), visible = { it.folders.showTitle },
            get = { it.folders.titleColor }, set = { c, v -> c.copy(folders = c.folders.copy(titleColor = v)) })
        slider("titleSize", "Name size", 10f..40f, 1f, "sp", el = setOf(E.FOLDER), visible = { it.folders.showTitle },
            get = { it.folders.titleSizeSp }, set = { c, v -> c.copy(folders = c.folders.copy(titleSizeSp = v)) })
        toggle("labels", "App labels", el = setOf(E.FOLDER), get = { it.folders.showLabels }, set = { c, v -> c.copy(folders = c.folders.copy(showLabels = v)) })
        color("labelColor", "App label color", el = setOf(E.FOLDER), visible = { it.folders.showLabels },
            get = { it.folders.labelColor }, set = { c, v -> c.copy(folders = c.folders.copy(labelColor = v)) })
    }

    val gestures = build(FlyerCategory.GESTURES) {
        section = "Sensitivity"
        slider("threshold", "Swipe distance", R.swipeThreshold.f(), 1f, "dp", "How far you swipe before a gesture triggers.",
            get = { it.gestures.swipeThresholdDp.toFloat() }, set = { c, v -> c.copy(gestures = c.gestures.copy(swipeThresholdDp = v.toInt())) })
        slider("doubleTap", "Double-tap window", R.doubleTapTimeout.f(), 10f, " ms",
            get = { it.gestures.doubleTapTimeoutMs.toFloat() }, set = { c, v -> c.copy(gestures = c.gestures.copy(doubleTapTimeoutMs = v.toInt())) })
        toggle("haptics", "Haptic feedback", keywords = listOf("vibration"),
            get = { it.gestures.hapticFeedback }, set = { c, v -> c.copy(gestures = c.gestures.copy(hapticFeedback = v)) })
    }

    val animations = build(FlyerCategory.ANIMATIONS) {
        section = "Motion"
        toggle("enabled", "Animations", get = { it.animations.enabled }, set = { c, v -> c.copy(animations = c.animations.copy(enabled = v)) })
        toggle("system", "Follow system animation setting", "Turns launcher motion off when \"Remove animations\" is on.",
            get = { it.animations.followSystem }, set = { c, v -> c.copy(animations = c.animations.copy(followSystem = v)) })
        slider("speed", "Animation duration", R.durationScale, 0.05f, "×", keywords = listOf("speed", "slow", "fast"),
            get = { it.animations.durationScale }, set = { c, v -> c.copy(animations = c.animations.copy(durationScale = v)) })
        choice("easing", "Easing", listOf(Easing.EMPHASIZED to "Emphasized", Easing.STANDARD to "Standard", Easing.DECELERATE to "Decelerate", Easing.LINEAR to "Linear", Easing.SPRING to "Spring", Easing.BOUNCY to "Bouncy"),
            get = { it.animations.easing }, set = { c, v -> c.copy(animations = c.animations.copy(easing = v)) })
        toggle("entrance", "Animate home when returning", get = { it.animations.homeEntrance }, set = { c, v -> c.copy(animations = c.animations.copy(homeEntrance = v)) })

        section = "Transitions"
        choice("pageTransition", "Page transition", listOf(PageTransition.SLIDE to "Slide", PageTransition.FADE to "Fade", PageTransition.DEPTH to "Depth", PageTransition.CUBE to "Cube", PageTransition.STACK to "Stack", PageTransition.ZOOM to "Zoom", PageTransition.ROTATE to "Rotate", PageTransition.NONE to "None"),
            keywords = listOf("page", "swipe", "effect"), el = setOf(E.WORKSPACE),
            get = { it.home.pageTransition }, set = { c, v -> c.copy(home = c.home.copy(pageTransition = v)) })
        choice("appLaunch", "App opening animation", listOf(AppLaunchAnimation.SCALE_UP to "Grow from icon", AppLaunchAnimation.CLIP_REVEAL to "Reveal from icon", AppLaunchAnimation.FADE to "Fade", AppLaunchAnimation.SYSTEM to "System default", AppLaunchAnimation.NONE to "None"),
            limitation = { _, _ -> "Android 12+ may replace this with the system's own app-launch animation." },
            get = { it.animations.appLaunch }, set = { c, v -> c.copy(animations = c.animations.copy(appLaunch = v)) })
        slider("pressScale", "Press shrink amount", 0.5f..1.2f, 0.01f, format = ::pct, visible = { it.icons.pressEffect == PressEffect.SCALE },
            get = { it.animations.pressScale }, set = { c, v -> c.copy(animations = c.animations.copy(pressScale = v)) })
    }

    val appearance = build(FlyerCategory.APPEARANCE) {
        section = "Theme"
        choice("mode", "Theme", listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), keywords = listOf("dark mode", "light"),
            get = { it.appearance.themeMode }, set = { c, v -> c.copy(appearance = c.appearance.copy(themeMode = v)) })
        toggle("dynamic", "Wallpaper colors (Material You)", keywords = listOf("monet", "dynamic"),
            limitation = { _, _ -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "Needs Android 12 or newer; the accent color is used instead." else null },
            get = { it.appearance.dynamicColor }, set = { c, v -> c.copy(appearance = c.appearance.copy(dynamicColor = v)) })
        color("accent", "Accent color", alpha = false, visible = { !it.appearance.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S },
            get = { it.appearance.accent }, set = { c, v -> c.copy(appearance = c.appearance.copy(accent = v)) })
        toggle("contrast", "High-contrast text", get = { it.appearance.highContrastText }, set = { c, v -> c.copy(appearance = c.appearance.copy(highContrastText = v)) })

        section = "Menus & panels"
        color("menuBg", "Menu background", alpha = false, get = { it.appearance.menuBackground }, set = { c, v -> c.copy(appearance = c.appearance.copy(menuBackground = v)) })
        slider("menuOpacity", "Menu opacity", 0.3f..1f, 0.01f, format = ::pct, get = { it.appearance.menuOpacity }, set = { c, v -> c.copy(appearance = c.appearance.copy(menuOpacity = v)) })
        slider("corners", "Corner roundness", 0f..2f, 0.05f, "×", get = { it.appearance.cornerScale }, set = { c, v -> c.copy(appearance = c.appearance.copy(cornerScale = v)) })
    }

    val typography = build(FlyerCategory.TYPOGRAPHY) {
        section = "Font"
        choice("font", "Launcher font", listOf(FontChoice.SYSTEM to "System", FontChoice.SANS to "Sans serif", FontChoice.SERIF to "Serif", FontChoice.MONOSPACE to "Monospace", FontChoice.CONDENSED to "Condensed", FontChoice.ROUNDED to "Medium", FontChoice.CUSTOM to "Custom file"),
            keywords = listOf("typeface"), el = setOf(E.LABEL, E.CLOCK),
            limitation = { c, _ -> if (c.typography.font == FontChoice.CUSTOM && c.typography.customFontPath == null) "Import a font file below." else null },
            get = { it.typography.font }, set = { c, v -> c.copy(typography = c.typography.copy(font = v)) })
        slider("scale", "Text size", R.textScale, 0.05f, "×", el = setOf(E.LABEL, E.CLOCK),
            get = { it.typography.textScale }, set = { c, v -> c.copy(typography = c.typography.copy(textScale = v)) })
        slider("spacing", "Letter spacing", -0.1f..0.5f, 0.01f, " em", el = setOf(E.LABEL),
            get = { it.typography.letterSpacingEm }, set = { c, v -> c.copy(typography = c.typography.copy(letterSpacingEm = v)) })
        toggle("flyerFont", "Use launcher font in Flyer", get = { it.typography.flyerUsesLauncherFont }, set = { c, v -> c.copy(typography = c.typography.copy(flyerUsesLauncherFont = v)) })
    }

    val desktop = build(FlyerCategory.DESKTOP) {
        section = "Mode"
        choice("mode", "Desktop mode", listOf(DesktopMode.AUTO to "Automatic", DesktopMode.ON to "Always", DesktopMode.OFF to "Never"),
            description = "Automatic switches to the desktop layout when a mouse or trackpad is connected and the screen is at least 600 dp wide.",
            keywords = listOf("mouse", "keyboard", "pc", "dex"), el = setOf(E.DESKTOP),
            get = { it.desktop.mode }, set = { c, v -> c.copy(desktop = c.desktop.copy(mode = v)) })
        toggle("shortcuts", "Keyboard shortcuts", "Ctrl+Space search, Ctrl+, Flyer, Ctrl+E editor, Ctrl+A apps, Esc close, Ctrl+Z / Ctrl+Y in Flyer.",
            get = { it.desktop.keyboardShortcuts }, set = { c, v -> c.copy(desktop = c.desktop.copy(keyboardShortcuts = v)) })
        toggle("hover", "Hover highlights", get = { it.desktop.hoverEffects }, set = { c, v -> c.copy(desktop = c.desktop.copy(hoverEffects = v)) })
        toggle("doubleClick", "Double-click to open desktop icons", get = { it.desktop.doubleClickToOpen }, set = { c, v -> c.copy(desktop = c.desktop.copy(doubleClickToOpen = v)) })
        toggle("folderWindows", "Open folders as windows", get = { it.desktop.foldersAsWindows }, set = { c, v -> c.copy(desktop = c.desktop.copy(foldersAsWindows = v)) })

        section = "Desktop"
        slider("iconSize", "Desktop icon size", 24f..96f, 1f, "dp", el = setOf(E.DESKTOP),
            get = { it.desktop.iconSizeDp.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(iconSizeDp = v.toInt())) })
        slider("columns", "Desktop grid columns", R.desktopColumns.f(), 1f, el = setOf(E.DESKTOP),
            get = { it.desktop.columns.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(columns = v.toInt())) })
        slider("rows", "Desktop grid rows", R.desktopRows.f(), 1f, el = setOf(E.DESKTOP),
            get = { it.desktop.rows.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(rows = v.toInt())) })
        toggle("snap", "Snap icons to grid", el = setOf(E.DESKTOP), get = { it.desktop.snapToGrid }, set = { c, v -> c.copy(desktop = c.desktop.copy(snapToGrid = v)) })
        color("labelColor", "Icon label color", el = setOf(E.DESKTOP), get = { it.desktop.labelColor }, set = { c, v -> c.copy(desktop = c.desktop.copy(labelColor = v)) })

        section = "Taskbar"
        choice("taskbarPos", "Taskbar position", listOf(TaskbarPosition.BOTTOM to "Bottom", TaskbarPosition.TOP to "Top"), el = setOf(E.TASKBAR),
            get = { it.desktop.taskbarPosition }, set = { c, v -> c.copy(desktop = c.desktop.copy(taskbarPosition = v)) })
        slider("taskbarHeight", "Taskbar height", R.taskbarHeight.f(), 1f, "dp", el = setOf(E.TASKBAR),
            get = { it.desktop.taskbarHeightDp.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(taskbarHeightDp = v.toInt())) })
        color("taskbarBg", "Taskbar color", el = setOf(E.TASKBAR), get = { it.desktop.taskbarBackground }, set = { c, v -> c.copy(desktop = c.desktop.copy(taskbarBackground = v)) })
        toggle("taskbarCenter", "Center pinned apps", el = setOf(E.TASKBAR), get = { it.desktop.taskbarCentered }, set = { c, v -> c.copy(desktop = c.desktop.copy(taskbarCentered = v)) })
        toggle("taskbarClock", "Show clock", el = setOf(E.TASKBAR), get = { it.desktop.taskbarShowClock }, set = { c, v -> c.copy(desktop = c.desktop.copy(taskbarShowClock = v)) })
        toggle("taskbarLabels", "Show app names", el = setOf(E.TASKBAR), get = { it.desktop.taskbarShowLabels }, set = { c, v -> c.copy(desktop = c.desktop.copy(taskbarShowLabels = v)) })

        section = "Start menu & panels"
        slider("startColumns", "Start menu columns", 3f..10f, 1f, get = { it.desktop.startMenuColumns.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(startMenuColumns = v.toInt())) })
        slider("startWidth", "Start menu width", 280f..1200f, 10f, "dp", get = { it.desktop.startMenuWidthDp.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(startMenuWidthDp = v.toInt())) })
        slider("startHeight", "Start menu height", 240f..1000f, 10f, "dp", get = { it.desktop.startMenuHeightDp.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(startMenuHeightDp = v.toInt())) })
        color("panelBg", "Panel color", get = { it.desktop.panelBackground }, set = { c, v -> c.copy(desktop = c.desktop.copy(panelBackground = v)) })
        slider("panelCorner", "Panel corner radius", R.corner.f(), 1f, "dp", get = { it.desktop.panelCornerDp.toFloat() }, set = { c, v -> c.copy(desktop = c.desktop.copy(panelCornerDp = v.toInt())) })
    }

    val search = build(FlyerCategory.SEARCH) {
        section = "Search bar"
        choice("bar", "Home search bar", listOf(SearchBarPosition.HIDDEN to "Hidden", SearchBarPosition.TOP to "Top of screen", SearchBarPosition.ABOVE_DOCK to "Above the dock"),
            el = setOf(E.SEARCH_BAR), get = { it.search.homeBar }, set = { c, v -> c.copy(search = c.search.copy(homeBar = v)) })
        color("barBg", "Bar color", el = setOf(E.SEARCH_BAR), get = { it.search.barBackground }, set = { c, v -> c.copy(search = c.search.copy(barBackground = v)) })
        slider("barCorner", "Bar corner radius", R.corner.f(), 1f, "dp", el = setOf(E.SEARCH_BAR),
            get = { it.search.barCornerDp.toFloat() }, set = { c, v -> c.copy(search = c.search.copy(barCornerDp = v.toInt())) })
        slider("barHeight", "Bar height", 36f..80f, 1f, "dp", el = setOf(E.SEARCH_BAR),
            get = { it.search.barHeightDp.toFloat() }, set = { c, v -> c.copy(search = c.search.copy(barHeightDp = v.toInt())) })

        section = "Sources"
        toggle("apps", "Apps", get = { it.search.searchApps }, set = { c, v -> c.copy(search = c.search.copy(searchApps = v)) })
        toggle("shortcuts", "App shortcuts", limitation = { _, _ -> "Available while Flyer Launcher is the default home app." },
            get = { it.search.searchShortcuts }, set = { c, v -> c.copy(search = c.search.copy(searchShortcuts = v)) })
        toggle("flyer", "Flyer settings", "Jump straight to any customization option.",
            get = { it.search.searchFlyerSettings }, set = { c, v -> c.copy(search = c.search.copy(searchFlyerSettings = v)) })
        toggle("web", "Web search", get = { it.search.webFallback }, set = { c, v -> c.copy(search = c.search.copy(webFallback = v)) })
        toggle("fuzzy", "Fuzzy matching", "\"gm\" finds Gmail and Google Maps.", get = { it.search.fuzzyMatching }, set = { c, v -> c.copy(search = c.search.copy(fuzzyMatching = v)) })
        slider("max", "Maximum results", 4f..100f, 1f, level = ADVANCED, get = { it.search.maxResults.toFloat() }, set = { c, v -> c.copy(search = c.search.copy(maxResults = v.toInt())) })
    }

    val performance = build(FlyerCategory.PERFORMANCE) {
        section = "Effects"
        choice("effects", "Visual effects", listOf(EffectsLevel.FULL to "Full", EffectsLevel.BALANCED to "Balanced", EffectsLevel.MINIMAL to "Minimal"),
            description = "Minimal turns off animations, shadows and blur. Balanced caps blur strength.",
            get = { it.performance.effects }, set = { c, v -> c.copy(performance = c.performance.copy(effects = v)) })
        toggle("blur", "Blur effects", limitation = { _, t -> if (!t.blurSupported) "Blur needs Android 12 or newer." else null },
            get = { it.performance.blurEffects }, set = { c, v -> c.copy(performance = c.performance.copy(blurEffects = v)) })
        toggle("lowMemory", "Low-memory mode", "Smaller caches, no blur, fewer pages kept in memory.",
            get = { it.performance.lowMemoryMode }, set = { c, v -> c.copy(performance = c.performance.copy(lowMemoryMode = v)) })

        section = "Caches"
        slider("cache", "Icon cache size", R.iconCacheMb.f(), 1f, " MB", get = { it.performance.iconCacheMb.toFloat() }, set = { c, v -> c.copy(performance = c.performance.copy(iconCacheMb = v.toInt())) })
        slider("quality", "Icon render resolution", 0.5f..2f, 0.25f, "×", "Higher looks sharper on zoom but uses more memory.", level = ADVANCED,
            get = { it.performance.iconQuality }, set = { c, v -> c.copy(performance = c.performance.copy(iconQuality = v)) })
        toggle("prefetch", "Prefetch drawer icons", "Renders all app icons in the background at start-up so the drawer opens instantly.",
            get = { it.performance.prefetchIcons }, set = { c, v -> c.copy(performance = c.performance.copy(prefetchIcons = v)) })
        toggle("widgetsListening", "Update widgets in the background", "Widgets keep receiving updates while an app is open (uses more battery).", level = ADVANCED,
            get = { it.performance.keepWidgetsListening }, set = { c, v -> c.copy(performance = c.performance.copy(keepWidgetsListening = v)) })
    }

    val advanced = build(FlyerCategory.ADVANCED) {
        section = "Behaviour"
        choice("orientation", "Screen orientation", listOf(OrientationLock.AUTO to "Automatic", OrientationLock.PORTRAIT to "Portrait", OrientationLock.LANDSCAPE to "Landscape"), keywords = listOf("rotation"),
            get = { it.advanced.orientation }, set = { c, v -> c.copy(advanced = c.advanced.copy(orientation = v)) })
        toggle("homeClosesFlyer", "Home button closes Flyer",
            get = { it.advanced.homeButtonClosesFlyer }, set = { c, v -> c.copy(advanced = c.advanced.copy(homeButtonClosesFlyer = v)) })
        toggle("autoApply", "Apply changes instantly", "Every Flyer change goes live immediately (undo still works).",
            get = { it.advanced.autoApplyFlyerChanges }, set = { c, v -> c.copy(advanced = c.advanced.copy(autoApplyFlyerChanges = v)) })
        toggle("advancedSettings", "Show advanced settings", get = { it.advanced.showAdvancedSettings }, set = { c, v -> c.copy(advanced = c.advanced.copy(showAdvancedSettings = v)) })
        toggle("confirm", "Confirm before removing folders and widgets",
            get = { it.advanced.confirmRemovals }, set = { c, v -> c.copy(advanced = c.advanced.copy(confirmRemovals = v)) })
        toggle("hiddenInSearch", "Show hidden apps in search",
            get = { it.advanced.showHiddenAppsInSearch }, set = { c, v -> c.copy(advanced = c.advanced.copy(showHiddenAppsInSearch = v)) })
        toggle("newApps", "Add new apps to home screen",
            get = { it.advanced.addNewAppsToHome }, set = { c, v -> c.copy(advanced = c.advanced.copy(addNewAppsToHome = v)) })
        slider("history", "Undo history length", R.history.f(), 10f, " steps", level = ADVANCED,
            get = { it.advanced.undoHistorySize.toFloat() }, set = { c, v -> c.copy(advanced = c.advanced.copy(undoHistorySize = v.toInt())) })

        section = "Developer"
        toggle("bounds", "Show layout bounds", "Outlines the grid area and every item.", level = ADVANCED,
            get = { it.advanced.showLayoutBounds }, set = { c, v -> c.copy(advanced = c.advanced.copy(showLayoutBounds = v)) })
    }

    val all: List<SettingSpec> = home + icons + dock + drawer + widgets + folders + gestures + animations + appearance +
        typography + desktop + search + performance + advanced

    fun forCategory(category: FlyerCategory): List<SettingSpec> = all.filter { it.category == category }

    fun forElement(element: E): List<SettingSpec> = all.filter { element in it.elements }

    fun byId(id: String): SettingSpec? = all.firstOrNull { it.id == id }

    fun search(query: String, config: FlyerConfig, includeAdvanced: Boolean = true, limit: Int = 40): List<SettingHit> =
        all.asSequence()
            .filter { includeAdvanced || it.level == SettingLevel.BASIC || config.advanced.showAdvancedSettings }
            .map { SettingHit(it, it.matches(query)) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()

    /** How many settings differ from the defaults in a category (dashboard badges). */
    fun modifiedCount(category: FlyerCategory, config: FlyerConfig): Int = forCategory(category).count { !it.isDefault(config) }
}
