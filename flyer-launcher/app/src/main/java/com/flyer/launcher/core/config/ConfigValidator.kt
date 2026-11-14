package com.flyer.launcher.core.config

/**
 * Keeps every configuration value inside the range the launcher can render safely.
 *
 * Applied whenever a configuration enters the system: on load, on import, on apply from Flyer and
 * after migrations. A hand-edited file with absurd values therefore degrades to the nearest valid
 * value instead of crashing or producing an unusable home screen.
 */
object ConfigValidator {

    object Ranges {
        val columns = 2..12
        val rows = 2..14
        val margin = 0..96
        val cellSpacing = 0..64
        val fixedCellHeight = 48..240
        val iconSize = 24..128
        val cornerPercent = 0..100
        val labelSize = 6f..24f
        val labelMaxLines = 1..3
        val dockSlots = 2..10
        val dockThickness = 40..200
        val dockIconSize = 24..112
        val dockLength = 0.2f..1f
        val drawerColumns = 2..10
        val folderColumns = 2..8
        val folderRows = 1..8
        val corner = 0..64
        val blur = 0..64
        val textScale = 0.6f..1.8f
        val durationScale = 0.1f..4f
        val desktopColumns = 4..24
        val desktopRows = 3..16
        val taskbarHeight = 36..96
        val iconCacheMb = 2..96
        val history = 10..500
        val swipeThreshold = 24..200
        val doubleTapTimeout = 150..600
    }

    fun sanitize(c: FlyerConfig): FlyerConfig = c.copy(
        schemaVersion = FlyerConfig.CURRENT_SCHEMA_VERSION,
        home = sanitize(c.home),
        icons = sanitize(c.icons),
        dock = sanitize(c.dock),
        drawer = sanitize(c.drawer),
        folders = sanitize(c.folders),
        widgets = sanitize(c.widgets),
        gestures = sanitize(c.gestures),
        animations = c.animations.copy(
            durationScale = c.animations.durationScale.finite(1f).coerceIn(Ranges.durationScale),
            pressScale = c.animations.pressScale.finite(0.9f).coerceIn(0.5f, 1.2f),
        ),
        appearance = c.appearance.copy(
            cornerScale = c.appearance.cornerScale.finite(1f).coerceIn(0f, 2f),
            menuOpacity = c.appearance.menuOpacity.unit(0.97f).coerceAtLeast(0.3f),
        ),
        typography = c.typography.copy(
            textScale = c.typography.textScale.finite(1f).coerceIn(Ranges.textScale),
            letterSpacingEm = c.typography.letterSpacingEm.finite(0f).coerceIn(-0.1f, 0.5f),
            font = if (c.typography.font == FontChoice.CUSTOM && c.typography.customFontPath.isNullOrBlank())
                FontChoice.SYSTEM else c.typography.font,
        ),
        desktop = sanitize(c.desktop),
        search = c.search.copy(
            barCornerDp = c.search.barCornerDp.coerceIn(Ranges.corner),
            barHeightDp = c.search.barHeightDp.coerceIn(36, 80),
            maxResults = c.search.maxResults.coerceIn(4, 100),
        ),
        performance = c.performance.copy(
            iconCacheMb = c.performance.iconCacheMb.coerceIn(Ranges.iconCacheMb),
            iconQuality = c.performance.iconQuality.finite(1f).coerceIn(0.5f, 2f),
        ),
        advanced = c.advanced.copy(
            undoHistorySize = c.advanced.undoHistorySize.coerceIn(Ranges.history),
        ),
    )

    private fun sanitize(h: HomeConfig): HomeConfig = h.copy(
        columns = h.columns.coerceIn(Ranges.columns),
        rows = h.rows.coerceIn(Ranges.rows),
        freeSnapDp = h.freeSnapDp.coerceIn(0, 64),
        marginStartDp = h.marginStartDp.coerceIn(Ranges.margin),
        marginEndDp = h.marginEndDp.coerceIn(Ranges.margin),
        marginTopDp = h.marginTopDp.coerceIn(Ranges.margin),
        marginBottomDp = h.marginBottomDp.coerceIn(Ranges.margin),
        cellSpacingHDp = h.cellSpacingHDp.coerceIn(Ranges.cellSpacing),
        cellSpacingVDp = h.cellSpacingVDp.coerceIn(Ranges.cellSpacing),
        fixedCellHeightDp = h.fixedCellHeightDp.coerceIn(Ranges.fixedCellHeight),
        defaultPage = h.defaultPage.coerceAtLeast(0),
        background = h.background.copy(
            gradientAngle = ((h.background.gradientAngle % 360) + 360) % 360,
            imageBlurDp = h.background.imageBlurDp.coerceIn(Ranges.blur),
            dim = h.background.dim.unit(0f),
            type = if (h.background.type == BackgroundType.IMAGE && h.background.imagePath.isNullOrBlank())
                BackgroundType.SYSTEM_WALLPAPER else h.background.type,
        ),
    )

    private fun sanitize(i: IconConfig): IconConfig = i.copy(
        sizeDp = i.sizeDp.coerceIn(Ranges.iconSize),
        cornerRadiusPercent = i.cornerRadiusPercent.coerceIn(Ranges.cornerPercent),
        label = sanitize(i.label),
        labelGapDp = i.labelGapDp.coerceIn(0, 32),
        shadowElevationDp = i.shadowElevationDp.coerceIn(0, 24),
        borderWidthDp = i.borderWidthDp.finite(0f).coerceIn(0f, 8f),
        opacity = i.opacity.unit(1f).coerceAtLeast(0.1f),
        saturation = i.saturation.unit(1f),
        insetPercent = i.insetPercent.coerceIn(0, 40),
        iconPack = i.iconPack?.takeIf { it.isNotBlank() },
    )

    private fun sanitize(l: LabelStyle): LabelStyle = l.copy(
        sizeSp = l.sizeSp.finite(12f).coerceIn(Ranges.labelSize),
        maxLines = l.maxLines.coerceIn(Ranges.labelMaxLines),
    )

    private fun sanitize(d: DockConfig): DockConfig = d.copy(
        slots = d.slots.coerceIn(Ranges.dockSlots),
        iconSizeDp = d.iconSizeDp.coerceIn(Ranges.dockIconSize),
        thicknessDp = d.thicknessDp.coerceIn(Ranges.dockThickness),
        lengthFraction = d.lengthFraction.finite(1f).coerceIn(Ranges.dockLength),
        marginDp = d.marginDp.coerceIn(0, 96),
        edgeMarginDp = d.edgeMarginDp.coerceIn(0, 96),
        opacity = d.opacity.unit(1f),
        blurDp = d.blurDp.coerceIn(Ranges.blur),
        cornerRadiusDp = d.cornerRadiusDp.coerceIn(0, 100),
        borderWidthDp = d.borderWidthDp.finite(0f).coerceIn(0f, 8f),
        iconSpacingDp = d.iconSpacingDp.coerceIn(0, 64),
    )

    private fun sanitize(d: DrawerConfig): DrawerConfig = d.copy(
        columns = d.columns.coerceIn(Ranges.drawerColumns),
        iconSizeDp = d.iconSizeDp.coerceIn(Ranges.iconSize),
        rowSpacingDp = d.rowSpacingDp.coerceIn(0, 64),
        opacity = d.opacity.unit(0.86f),
        blurDp = d.blurDp.coerceIn(Ranges.blur),
        cornerRadiusDp = d.cornerRadiusDp.coerceIn(Ranges.corner),
        horizontalPaddingDp = d.horizontalPaddingDp.coerceIn(0, 64),
    )

    private fun sanitize(f: FolderConfig): FolderConfig = f.copy(
        columns = f.columns.coerceIn(Ranges.folderColumns),
        maxVisibleRows = f.maxVisibleRows.coerceIn(Ranges.folderRows),
        previewOpacity = f.previewOpacity.unit(1f),
        popupOpacity = f.popupOpacity.unit(0.94f).coerceAtLeast(0.2f),
        popupCornerRadiusDp = f.popupCornerRadiusDp.coerceIn(Ranges.corner),
        popupBlurDp = f.popupBlurDp.coerceIn(Ranges.blur),
        titleSizeSp = f.titleSizeSp.finite(20f).coerceIn(10f, 40f),
    )

    private fun sanitize(w: WidgetConfig): WidgetConfig = w.copy(
        paddingDp = w.paddingDp.coerceIn(0, 48),
        cornerRadiusDp = w.cornerRadiusDp.coerceIn(Ranges.corner),
        containerOpacity = w.containerOpacity.unit(1f),
        clock = w.clock.copy(
            sizeSp = w.clock.sizeSp.finite(56f).coerceIn(12f, 160f),
            datePattern = w.clock.datePattern.takeIf { isValidDatePattern(it) } ?: ClockConfig().datePattern,
        ),
    )

    private fun sanitize(g: GestureConfig): GestureConfig {
        // Never let the user lose every route into Flyer: the empty-space long press always
        // falls back to the home menu (which contains Flyer) if it was set to "nothing".
        val bindings = defaultGestureBindings() + g.bindings
        val fixed = bindings.mapValues { (_, a) ->
            if (a.type == ActionType.LAUNCH_APP && a.target.isNullOrBlank()) GestureAction(ActionType.NONE) else a
        }.toMutableMap()
        if (fixed[GestureType.LONG_PRESS_EMPTY]?.type == ActionType.NONE) {
            fixed[GestureType.LONG_PRESS_EMPTY] = GestureAction(ActionType.HOME_MENU)
        }
        return g.copy(
            bindings = fixed,
            swipeThresholdDp = g.swipeThresholdDp.coerceIn(Ranges.swipeThreshold),
            doubleTapTimeoutMs = g.doubleTapTimeoutMs.coerceIn(Ranges.doubleTapTimeout),
        )
    }

    private fun sanitize(d: DesktopConfig): DesktopConfig = d.copy(
        iconSizeDp = d.iconSizeDp.coerceIn(24, 96),
        columns = d.columns.coerceIn(Ranges.desktopColumns),
        rows = d.rows.coerceIn(Ranges.desktopRows),
        taskbarHeightDp = d.taskbarHeightDp.coerceIn(Ranges.taskbarHeight),
        startMenuColumns = d.startMenuColumns.coerceIn(3, 10),
        startMenuWidthDp = d.startMenuWidthDp.coerceIn(280, 1200),
        startMenuHeightDp = d.startMenuHeightDp.coerceIn(240, 1000),
        panelCornerDp = d.panelCornerDp.coerceIn(Ranges.corner),
    )

    fun isValidDatePattern(pattern: String): Boolean = try {
        pattern.isNotBlank() && pattern.length <= 64 &&
            java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date()).isNotEmpty()
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun Float.finite(fallback: Float) = if (isNaN() || isInfinite()) fallback else this
    private fun Float.unit(fallback: Float) = finite(fallback).coerceIn(0f, 1f)
}
