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
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.SurfaceMaterial

/**
 * Built-in themes. A theme only touches visual sections (appearance, icons, dock, drawer, folders,
 * typography, widgets styling, page transitions); the user's arrangement and behaviour settings
 * (grid, gestures, desktop mode, advanced) are preserved.
 */
data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val swatch: List<ColorValue>,
    /** Advanced themes also set the material, effects and a launcher-drawn background. */
    val advanced: Boolean = false,
    val apply: (FlyerConfig) -> FlyerConfig,
)

object ThemePresets {

    /** Copies only the visual sections of [source] into [target] (and its background if [withBackground]). */
    fun applyVisuals(target: FlyerConfig, source: FlyerConfig, withBackground: Boolean = false): FlyerConfig = target.copy(
        style = source.style,
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
            background = if (withBackground) source.home.background.copy(imagePath = target.home.background.imagePath)
            else target.home.background.copy(dim = source.home.background.dim),
        ),
        search = target.search.copy(barBackground = source.search.barBackground, barCornerDp = source.search.barCornerDp),
        desktop = target.desktop.copy(
            taskbarBackground = source.desktop.taskbarBackground,
            panelBackground = source.desktop.panelBackground,
            panelCornerDp = source.desktop.panelCornerDp,
        ),
    )

    private val default = FlyerConfig()

    val all: List<ThemePreset> get() = advanced + classic

    val classic: List<ThemePreset> = listOf(
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

    private fun look(c: FlyerConfig, block: (FlyerConfig) -> FlyerConfig) = applyVisuals(c, block(default), withBackground = true)

    private fun bg(type: BackgroundType, vararg colors: Long, speed: Float = 1f, density: Float = 0.5f, dim: Float = 0f) =
        default.home.background.copy(type = type, palette = colors.map { argb(it) }, animSpeed = speed, animDensity = density, dim = dim)

    /** Advanced themes: materials, animated backgrounds and coordinated styling. */
    val advanced: List<ThemePreset> = listOf(
        ThemePreset("liquid-glass", "Liquid Glass", "Refractive glass dock, folders and menus over a soft mesh", listOf(argb(0x66FFFFFF), argb(0xFF8EC5FF), argb(0xFFE0C3FC)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.LIQUID_GLASS, refraction = 0.75f, chromatic = 0.35f, highlight = 0.9f, frostDp = 12, tintStrength = 0.3f),
                    home = t.home.copy(background = bg(BackgroundType.MESH, 0xFF8EC5FF, 0xFFE0C3FC, 0xFFFFC3A0, 0xFF1D2B53), pageTransition = PageTransition.DEPTH),
                    appearance = t.appearance.copy(accent = argb(0xFF8EC5FF), dynamicColor = false, menuBackground = argb(0xFFFFFFFF), menuOpacity = 0.35f),
                    dock = t.dock.copy(background = argb(0x55FFFFFF), cornerRadiusDp = 34, lengthFraction = 0.92f, edgeMarginDp = 12, marginDp = 10),
                    folders = t.folders.copy(popupBackground = argb(0xFFFFFFFF), popupOpacity = 0.35f, previewBackground = argb(0x44FFFFFF), popupCornerRadiusDp = 32),
                    drawer = t.drawer.copy(background = argb(0xFF1D2B53), opacity = 0.35f, cornerRadiusDp = 32),
                    search = t.search.copy(barBackground = argb(0x55FFFFFF)),
                    icons = t.icons.copy(shape = IconShape.SQUIRCLE, sizeDp = 58),
                )
            }
        },
        ThemePreset("clear-glass", "Clear Glass", "Clear glyph icons on glass plates, aurora behind", listOf(argb(0x55FFFFFF), argb(0xFF5EEAD4), argb(0xFF0B1026)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.LIQUID_GLASS, iconPlates = true, clearIcons = true, refraction = 0.6f, highlight = 0.85f, tintStrength = 0.25f),
                    home = t.home.copy(background = bg(BackgroundType.AURORA, 0xFF5EEAD4, 0xFF6366F1, 0xFFF472B6, 0xFF0B1026, speed = 0.6f)),
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFF5EEAD4), menuBackground = argb(0xFFFFFFFF), menuOpacity = 0.3f),
                    dock = t.dock.copy(background = argb(0x40FFFFFF), cornerRadiusDp = 36, lengthFraction = 0.9f, edgeMarginDp = 14, marginDp = 10),
                    folders = t.folders.copy(popupBackground = argb(0xFFFFFFFF), popupOpacity = 0.25f, previewBackground = argb(0x33FFFFFF)),
                    drawer = t.drawer.copy(background = argb(0xFF0B1026), opacity = 0.4f),
                    search = t.search.copy(barBackground = argb(0x40FFFFFF)),
                    icons = t.icons.copy(shape = IconShape.SQUIRCLE, sizeDp = 60),
                )
            }
        },
        ThemePreset("aurora", "Aurora", "Living northern-lights background with frosted panels", listOf(argb(0xFF34D399), argb(0xFF8B5CF6), argb(0xFF0F172A)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.FROSTED, frostDp = 24, grain = 0.35f, tintStrength = 0.4f),
                    home = t.home.copy(background = bg(BackgroundType.AURORA, 0xFF34D399, 0xFF22D3EE, 0xFF8B5CF6, 0xFF0F172A)),
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFF34D399), menuBackground = argb(0xFF0F172A), menuOpacity = 0.6f),
                    dock = t.dock.copy(background = argb(0x660F172A), cornerRadiusDp = 28),
                    folders = t.folders.copy(popupBackground = argb(0xFF0F172A), popupOpacity = 0.55f),
                    drawer = t.drawer.copy(background = argb(0xFF0F172A), opacity = 0.55f),
                    search = t.search.copy(barBackground = argb(0x660F172A)),
                )
            }
        },
        ThemePreset("holo", "Holo", "Iridescent holographic glass over rolling waves", listOf(argb(0xFFFF7AD9), argb(0xFF7AE7FF), argb(0xFF1E1B4B)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.HOLOGRAPHIC, holoSpeed = 1f, highlight = 0.8f),
                    home = t.home.copy(background = bg(BackgroundType.WAVES, 0xFFFF7AD9, 0xFF7AE7FF, 0xFFB388FF, 0xFF1E1B4B, density = 0.6f), pageTransition = PageTransition.ZOOM),
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFFB388FF), menuBackground = argb(0xFF1E1B4B), menuOpacity = 0.5f),
                    dock = t.dock.copy(background = argb(0x55FFFFFF), cornerRadiusDp = 30, lengthFraction = 0.9f, edgeMarginDp = 14),
                    folders = t.folders.copy(popupBackground = argb(0xFF1E1B4B), popupOpacity = 0.45f),
                    drawer = t.drawer.copy(background = argb(0xFF1E1B4B), opacity = 0.5f),
                    icons = t.icons.copy(shape = IconShape.PEBBLE, cornerRadiusPercent = 60),
                )
            }
        },
        ThemePreset("soft-ui", "Soft UI", "Neumorphic, extruded surfaces in soft grey", listOf(argb(0xFFE0E5EC), argb(0xFFFFFFFF), argb(0xFFA3B1C6)), advanced = true) { c ->
            val base = 0xFFE0E5EC
            val ink = argb(0xFF4A5568)
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.NEUMORPHIC, depth = 0.7f, iconPlates = true, lightAngle = 225),
                    home = t.home.copy(background = default.home.background.copy(type = BackgroundType.SOLID, color = argb(base)), pageIndicatorColor = ink),
                    appearance = t.appearance.copy(themeMode = ThemeMode.LIGHT, dynamicColor = false, accent = argb(0xFF6C7A96), menuBackground = argb(base), menuOpacity = 1f),
                    dock = t.dock.copy(background = argb(base), cornerRadiusDp = 30, lengthFraction = 0.9f, edgeMarginDp = 18, marginDp = 12),
                    folders = t.folders.copy(popupBackground = argb(base), popupOpacity = 1f, previewBackground = argb(0xFFD1D9E6), titleColor = ink, labelColor = ink),
                    drawer = t.drawer.copy(background = argb(base), opacity = 1f, labelColor = ink),
                    search = t.search.copy(barBackground = argb(base)),
                    icons = t.icons.copy(label = t.icons.label.copy(color = ink, shadow = false)),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(color = ink)),
                )
            }
        },
        ThemePreset("clay", "Clay", "Puffy, playful clay surfaces in pastel tones", listOf(argb(0xFFFFB38A), argb(0xFF8AD1C2), argb(0xFFFBE7DD)), advanced = true) { c ->
            val ink = argb(0xFF5B3A29)
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.CLAY, depth = 0.75f, lightAngle = 225),
                    home = t.home.copy(background = default.home.background.copy(type = BackgroundType.SOLID, color = argb(0xFFFBE7DD)), pageIndicatorColor = ink),
                    appearance = t.appearance.copy(themeMode = ThemeMode.LIGHT, dynamicColor = false, accent = argb(0xFFFF8A65), menuBackground = argb(0xFFFFD6A5), menuOpacity = 1f, cornerScale = 1.4f),
                    dock = t.dock.copy(background = argb(0xFFFFB38A), cornerRadiusDp = 40, lengthFraction = 0.88f, edgeMarginDp = 18, marginDp = 12),
                    folders = t.folders.copy(popupBackground = argb(0xFF8AD1C2), popupOpacity = 1f, shape = IconShape.PEBBLE, previewBackground = argb(0xFF8AD1C2), titleColor = ink, labelColor = ink),
                    drawer = t.drawer.copy(background = argb(0xFFFFE9DC), opacity = 1f, labelColor = ink),
                    search = t.search.copy(barBackground = argb(0xFFFFD6A5)),
                    icons = t.icons.copy(shape = IconShape.PEBBLE, cornerRadiusPercent = 70, label = t.icons.label.copy(color = ink, shadow = false, weight = com.flyer.launcher.core.config.FontWeightOption.MEDIUM)),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(color = ink, weight = com.flyer.launcher.core.config.FontWeightOption.BOLD)),
                    typography = t.typography.copy(font = FontChoice.ROUNDED),
                )
            }
        },
        ThemePreset("cyber-neon", "Cyber Neon", "Glowing neon outlines over drifting particles", listOf(argb(0xFFFF2BD6), argb(0xFF00F0FF), argb(0xFF07070D)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.NEON, glowColor = argb(0xFFFF2BD6), glowRadiusDp = 14, glowPulse = true),
                    home = t.home.copy(background = bg(BackgroundType.PARTICLES, 0xFFFF2BD6, 0xFF00F0FF, 0xFF7C3AED, 0xFF07070D, density = 0.6f), pageIndicatorColor = argb(0xFF00F0FF), pageTransition = PageTransition.CUBE),
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFFFF2BD6), menuBackground = argb(0xFF0B0B14), menuOpacity = 0.9f),
                    icons = t.icons.copy(shape = IconShape.HEXAGON, borderWidthDp = 1.5f, borderColor = argb(0xFF00F0FF), label = t.icons.label.copy(color = argb(0xFF00F0FF), uppercase = true, sizeSp = 10f)),
                    dock = t.dock.copy(background = argb(0xFF0B0B14), cornerRadiusDp = 16),
                    folders = t.folders.copy(popupBackground = argb(0xFF0B0B14), shape = IconShape.HEXAGON, titleColor = argb(0xFFFF2BD6), labelColor = argb(0xFF00F0FF)),
                    drawer = t.drawer.copy(background = argb(0xFF07070D), opacity = 0.9f, labelColor = argb(0xFF00F0FF)),
                    typography = t.typography.copy(font = FontChoice.MONOSPACE),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(color = argb(0xFF00F0FF))),
                )
            }
        },
        ThemePreset("lava", "Lava Lamp", "Warm liquid blobs behind frosted glass", listOf(argb(0xFFFF6B35), argb(0xFFF72585), argb(0xFF3A0CA3)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.FROSTED, frostDp = 28, grain = 0.3f, tintStrength = 0.3f),
                    home = t.home.copy(background = bg(BackgroundType.LIQUID, 0xFFFF6B35, 0xFFF72585, 0xFFFFBE0B, 0xFF3A0CA3, speed = 0.8f, density = 0.5f)),
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFFFF6B35), menuBackground = argb(0xFF3A0CA3), menuOpacity = 0.55f),
                    dock = t.dock.copy(background = argb(0x55FFFFFF), cornerRadiusDp = 32, lengthFraction = 0.9f, edgeMarginDp = 14),
                    folders = t.folders.copy(popupBackground = argb(0xFF3A0CA3), popupOpacity = 0.45f),
                    drawer = t.drawer.copy(background = argb(0xFF3A0CA3), opacity = 0.5f),
                    icons = t.icons.copy(shape = IconShape.CIRCLE),
                )
            }
        },
        ThemePreset("starfield", "Starfield", "Wireframe outlines in deep space", listOf(argb(0xFFFFFFFF), argb(0xFF94A3B8), argb(0xFF020617)), advanced = true) { c ->
            look(c) { t ->
                t.copy(
                    style = t.style.copy(material = SurfaceMaterial.OUTLINE),
                    home = t.home.copy(background = bg(BackgroundType.PARTICLES, 0xFFFFFFFF, 0xFF94A3B8, 0xFFBFDBFE, 0xFF020617, speed = 0.4f, density = 0.9f), pageIndicator = PageIndicatorStyle.LINE),
                    appearance = t.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false, accent = argb(0xFFBFDBFE), menuBackground = argb(0xFF020617), menuOpacity = 0.85f),
                    dock = t.dock.copy(background = argb(0xCCFFFFFF), cornerRadiusDp = 22),
                    folders = t.folders.copy(popupBackground = argb(0xFF020617), popupOpacity = 0.85f, previewBackground = argb(0x22FFFFFF)),
                    drawer = t.drawer.copy(background = argb(0xFF020617), opacity = 0.9f),
                    icons = t.icons.copy(shape = IconShape.CIRCLE, saturation = 0.3f, label = t.icons.label.copy(weight = com.flyer.launcher.core.config.FontWeightOption.LIGHT)),
                    typography = t.typography.copy(letterSpacingEm = 0.05f),
                    widgets = t.widgets.copy(clock = t.widgets.clock.copy(weight = com.flyer.launcher.core.config.FontWeightOption.LIGHT)),
                )
            }
        },
    )
}
