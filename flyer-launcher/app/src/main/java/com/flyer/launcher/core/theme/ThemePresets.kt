package com.flyer.launcher.core.theme

import com.flyer.launcher.core.config.ClockStyle
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.DockAlignment
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.FolderPreview
import com.flyer.launcher.core.config.FontChoice
import com.flyer.launcher.core.config.FontWeightOption
import com.flyer.launcher.core.config.IconShape
import com.flyer.launcher.core.config.OverlayAnimation
import com.flyer.launcher.core.config.PageIndicatorStyle
import com.flyer.launcher.core.config.PageTransition
import com.flyer.launcher.core.config.ThemeMode
import com.flyer.launcher.core.config.argb

/**
 * Built-in themes. A theme only touches visual sections (appearance, icons, dock, drawer, folders,
 * typography, widgets styling, page transitions); the user's arrangement and behaviour settings
 * (grid, gestures, desktop mode, advanced) are preserved.
 */
data class ThemePreset(val id: String, val name: String, val description: String, val swatch: List<ColorValue>, val apply: (FlyerConfig) -> FlyerConfig)

object ThemePresets {

    /** Copies only the visual sections of [source] into [target]. */
    fun applyVisuals(target: FlyerConfig, source: FlyerConfig): FlyerConfig = target.copy(
        appearance = source.appearance,
        icons = source.icons.copy(iconPack = target.icons.iconPack),
        dock = source.dock.copy(slots = target.dock.slots, enabled = target.dock.enabled, position = target.dock.position),
        drawer = source.drawer.copy(columns = target.drawer.columns, sort = target.drawer.sort, layout = target.drawer.layout),
        folders = source.folders,
        typography = source.typography,
        widgets = source.widgets,
        home = target.home.copy(
            pageTransition = source.home.pageTransition,
            pageIndicator = source.home.pageIndicator,
            pageIndicatorColor = source.home.pageIndicatorColor,
            background = target.home.background.copy(dim = source.home.background.dim),
        ),
        search = target.search.copy(barBackground = source.search.barBackground, barCornerDp = source.search.barCornerDp),
        desktop = target.desktop.copy(
            taskbarBackground = source.desktop.taskbarBackground,
            panelBackground = source.desktop.panelBackground,
            panelCornerDp = source.desktop.panelCornerDp,
        ),
    )

    private val default = FlyerConfig()

    val all: List<ThemePreset> = listOf(
        ThemePreset("flyer", "Flyer", "The default: soft glass dock, squircle icons", listOf(argb(0xFF6A5BFF), argb(0x33FFFFFF), argb(0xFF101018))) { c ->
            applyVisuals(c, default)
        },
        ThemePreset("minimal", "Minimal", "No dock background, circles, thin type", listOf(argb(0xFFFFFFFF), argb(0xFF000000), argb(0xFF888888))) { c ->
            applyVisuals(c, default).let { t ->
                t.copy(
                    icons = t.icons.copy(shape = IconShape.CIRCLE, sizeDp = 50, label = t.icons.label.copy(weight = FontWeightOption.LIGHT, sizeSp = 11f)),
                    dock = t.dock.copy(background = ColorValue.Transparent, cornerRadiusDp = 0, borderWidthDp = 0f, showLabels = false),
                    drawer = t.drawer.copy(background = argb(0xFF000000), opacity = 0.92f, showCategories = false),
                    folders = t.folders.copy(preview = FolderPreview.STACK, previewBackground = argb(0x22FFFFFF)),
                    home = t.home.copy(pageIndicator = PageIndicatorStyle.LINE),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(weight = FontWeightOption.LIGHT, sizeSp = 64f)),
                )
            }
        },
        ThemePreset("glass", "Glass", "Frosted panels, rounded squares, blur", listOf(argb(0x66FFFFFF), argb(0xFF7AE7FF), argb(0xFF1E2A44))) { c ->
            applyVisuals(c, default).let { t ->
                t.copy(
                    appearance = t.appearance.copy(accent = argb(0xFF4FC3F7), dynamicColor = false),
                    icons = t.icons.copy(shape = IconShape.ROUNDED_SQUARE, cornerRadiusPercent = 38, borderWidthDp = 1f, borderColor = argb(0x55FFFFFF)),
                    dock = t.dock.copy(background = argb(0x40FFFFFF), blurDp = 24, cornerRadiusDp = 32, borderWidthDp = 1f, borderColor = argb(0x66FFFFFF), lengthFraction = 0.92f, edgeMarginDp = 14),
                    drawer = t.drawer.copy(background = argb(0xFF1E2A44), opacity = 0.7f, blurDp = 30),
                    folders = t.folders.copy(popupBackground = argb(0xFF24324F), popupOpacity = 0.75f, popupBlurDp = 30, previewBackground = argb(0x40FFFFFF)),
                    search = t.search.copy(barBackground = argb(0x40FFFFFF)),
                )
            }
        },
        ThemePreset("neon", "Neon", "Dark, hexagon icons, glowing accents", listOf(argb(0xFFFF2BD6), argb(0xFF00F0FF), argb(0xFF07070D))) { c ->
            applyVisuals(c, default).let { t ->
                t.copy(
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFFFF2BD6), menuBackground = argb(0xFF0B0B14)),
                    icons = t.icons.copy(
                        shape = IconShape.HEXAGON, cornerRadiusPercent = 30, borderWidthDp = 1.5f, borderColor = argb(0xFF00F0FF),
                        shadow = true, shadowElevationDp = 8,
                        label = t.icons.label.copy(color = argb(0xFF00F0FF), weight = FontWeightOption.MEDIUM, uppercase = true, sizeSp = 10f),
                    ),
                    dock = t.dock.copy(background = argb(0xCC07070D), borderWidthDp = 1.5f, borderColor = argb(0xFFFF2BD6), cornerRadiusDp = 12),
                    drawer = t.drawer.copy(background = argb(0xFF07070D), opacity = 0.95f, labelColor = argb(0xFF00F0FF)),
                    folders = t.folders.copy(popupBackground = argb(0xFF0B0B14), titleColor = argb(0xFFFF2BD6), previewBackground = argb(0x3300F0FF), shape = IconShape.HEXAGON),
                    typography = t.typography.copy(font = FontChoice.MONOSPACE),
                    home = t.home.copy(pageIndicatorColor = argb(0xFFFF2BD6), pageTransition = PageTransition.CUBE),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(color = argb(0xFF00F0FF), weight = FontWeightOption.BOLD)),
                )
            }
        },
        ThemePreset("paper", "Paper", "Light, serif type, white icon plates", listOf(argb(0xFFF5F1E8), argb(0xFF3D3A35), argb(0xFFB5523B))) { c ->
            applyVisuals(c, default).let { t ->
                t.copy(
                    appearance = t.appearance.copy(themeMode = ThemeMode.LIGHT, dynamicColor = false, accent = argb(0xFFB5523B), menuBackground = argb(0xFFF5F1E8)),
                    icons = t.icons.copy(shape = IconShape.SQUARE, legacyBackground = argb(0xFFF5F1E8), label = t.icons.label.copy(color = argb(0xFF3D3A35), shadow = false)),
                    dock = t.dock.copy(background = argb(0xF0F5F1E8), cornerRadiusDp = 4, borderWidthDp = 1f, borderColor = argb(0x333D3A35)),
                    drawer = t.drawer.copy(background = argb(0xFFF5F1E8), opacity = 0.98f, labelColor = argb(0xFF3D3A35)),
                    folders = t.folders.copy(popupBackground = argb(0xFFF5F1E8), titleColor = argb(0xFF3D3A35), labelColor = argb(0xFF3D3A35), previewBackground = argb(0xCCF5F1E8), shape = IconShape.SQUARE),
                    typography = t.typography.copy(font = FontChoice.SERIF),
                    home = t.home.copy(pageIndicatorColor = argb(0xFF3D3A35), background = t.home.background.copy(dim = 0f)),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(color = argb(0xFF3D3A35), style = ClockStyle.STACKED)),
                    search = t.search.copy(barBackground = argb(0xF0F5F1E8), barCornerDp = 4),
                )
            }
        },
        ThemePreset("terminal", "Terminal", "Monospace, green on black, square everything", listOf(argb(0xFF00FF66), argb(0xFF000000), argb(0xFF0A3D1E))) { c ->
            applyVisuals(c, default).let { t ->
                t.copy(
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFF00FF66), menuBackground = argb(0xFF000000), cornerScale = 0f),
                    icons = t.icons.copy(
                        shape = IconShape.SQUARE, saturation = 0f, borderWidthDp = 1f, borderColor = argb(0xFF00FF66),
                        label = t.icons.label.copy(color = argb(0xFF00FF66), shadow = false, sizeSp = 11f),
                    ),
                    dock = t.dock.copy(background = argb(0xFF000000), cornerRadiusDp = 0, borderWidthDp = 1f, borderColor = argb(0xFF00FF66), alignment = DockAlignment.START),
                    drawer = t.drawer.copy(background = argb(0xFF000000), opacity = 1f, labelColor = argb(0xFF00FF66), cornerRadiusDp = 0, animation = OverlayAnimation.FADE),
                    folders = t.folders.copy(popupBackground = argb(0xFF000000), popupCornerRadiusDp = 0, titleColor = argb(0xFF00FF66), labelColor = argb(0xFF00FF66), shape = IconShape.SQUARE, previewBackground = argb(0x2200FF66)),
                    typography = t.typography.copy(font = FontChoice.MONOSPACE),
                    home = t.home.copy(pageIndicator = PageIndicatorStyle.NUMBERS, pageIndicatorColor = argb(0xFF00FF66), pageTransition = PageTransition.NONE, background = t.home.background.copy(dim = 0.6f)),
                    widgets = t.widgets.copy(cornerRadiusDp = 0, clock = t.widgets.clock.copy(color = argb(0xFF00FF66), weight = FontWeightOption.NORMAL)),
                    search = t.search.copy(barBackground = argb(0xFF000000), barCornerDp = 0),
                    desktop = t.desktop.copy(panelCornerDp = 0, taskbarBackground = argb(0xFF000000), panelBackground = argb(0xFF000000)),
                )
            }
        },
        ThemePreset("pebble", "Pebble", "Warm pastel pebbles and teardrop folders", listOf(argb(0xFFFFB38A), argb(0xFF8AD1C2), argb(0xFF3A2F45))) { c ->
            applyVisuals(c, default).let { t ->
                t.copy(
                    appearance = t.appearance.copy(accent = argb(0xFFFF8A65), dynamicColor = false),
                    icons = t.icons.copy(shape = IconShape.PEBBLE, cornerRadiusPercent = 60, legacyBackground = argb(0xFFFFE9DC)),
                    dock = t.dock.copy(background = argb(0x55FFB38A), cornerRadiusDp = 40, lengthFraction = 0.85f, edgeMarginDp = 16),
                    folders = t.folders.copy(shape = IconShape.TEARDROP, previewBackground = argb(0x668AD1C2), popupBackground = argb(0xFF3A2F45)),
                    drawer = t.drawer.copy(background = argb(0xFF3A2F45), opacity = 0.9f),
                    home = t.home.copy(pageTransition = PageTransition.DEPTH),
                )
            }
        },
    )
}
