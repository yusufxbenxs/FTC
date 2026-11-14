package com.flyer.launcher.core.theme

import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.flyer.launcher.core.config.AnimationConfig
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.Easing
import com.flyer.launcher.core.config.EffectsLevel
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.FontChoice
import com.flyer.launcher.core.config.FontWeightOption
import com.flyer.launcher.core.config.ThemeMode
import com.flyer.launcher.core.config.TypographyConfig
import java.io.File

/** The active configuration, available to every launcher-owned composable. */
val LocalFlyerConfig = compositionLocalOf { FlyerConfig() }

/** Resolved runtime capabilities that depend on the device rather than the configuration. */
data class DeviceTraits(
    val blurSupported: Boolean,
    val lowRam: Boolean,
    val systemAnimationsOff: Boolean,
    val themedIconsSupported: Boolean = Build.VERSION.SDK_INT >= 33,
)

val LocalDeviceTraits = staticCompositionLocalOf { DeviceTraits(blurSupported = false, lowRam = false, systemAnimationsOff = false) }

/** Font family resolved from the typography config (a custom font file is loaded once). */
val LocalLauncherFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * Theme Engine: turns configuration into Compose theme objects (color scheme, typography, motion).
 */
object ThemeEngine {

    @Composable
    fun isDark(config: FlyerConfig): Boolean = when (config.appearance.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    @Composable
    fun colorScheme(config: FlyerConfig, dark: Boolean): ColorScheme {
        val context = LocalContext.current
        val accent = config.appearance.accent.color
        val base = if (config.appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            fromAccent(accent, dark)
        }
        return if (config.appearance.highContrastText) {
            base.copy(onSurface = if (dark) Color.White else Color.Black, onBackground = if (dark) Color.White else Color.Black)
        } else base
    }

    fun fromAccent(accent: Color, dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = lerp(accent, Color.White, 0.25f),
            onPrimary = Color(0xFF14142B),
            primaryContainer = lerp(accent, Color.Black, 0.45f),
            onPrimaryContainer = lerp(accent, Color.White, 0.8f),
            secondary = lerp(accent, Color(0xFF9AA0B8), 0.6f),
            secondaryContainer = lerp(accent, Color(0xFF22243A), 0.8f),
            onSecondaryContainer = Color(0xFFE2E3F5),
            tertiary = lerp(accent, Color(0xFFFF7AD9), 0.5f),
            background = Color(0xFF101018),
            surface = Color(0xFF13131D),
            surfaceVariant = Color(0xFF23243A),
            surfaceContainer = Color(0xFF1A1B28),
            surfaceContainerHigh = Color(0xFF212234),
            surfaceContainerHighest = Color(0xFF292A3E),
            surfaceContainerLow = Color(0xFF161722),
            surfaceContainerLowest = Color(0xFF0D0D14),
        )
    } else {
        lightColorScheme(
            primary = lerp(accent, Color.Black, 0.1f),
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.8f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
            secondary = lerp(accent, Color(0xFF5C5F75), 0.6f),
            secondaryContainer = lerp(accent, Color.White, 0.88f),
            tertiary = lerp(accent, Color(0xFFB0307F), 0.5f),
            background = Color(0xFFFBFAFF),
            surface = Color(0xFFFBFAFF),
            surfaceVariant = Color(0xFFE4E3F2),
            surfaceContainer = Color(0xFFF0EFF8),
            surfaceContainerHigh = Color(0xFFEAE9F4),
            surfaceContainerHighest = Color(0xFFE4E3EF),
            surfaceContainerLow = Color(0xFFF5F4FC),
            surfaceContainerLowest = Color.White,
        )
    }

    fun fontFamily(t: TypographyConfig): FontFamily = when (t.font) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.SANS -> FontFamily.SansSerif
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONOSPACE -> FontFamily.Monospace
        FontChoice.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        FontChoice.ROUNDED -> FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        FontChoice.CUSTOM -> t.customFontPath?.let { path ->
            runCatching { FontFamily(Typeface.createFromFile(File(path))) }.getOrNull()
        } ?: FontFamily.Default
    }

    fun weight(w: FontWeightOption) = when (w) {
        FontWeightOption.LIGHT -> FontWeight.Light
        FontWeightOption.NORMAL -> FontWeight.Normal
        FontWeightOption.MEDIUM -> FontWeight.Medium
        FontWeightOption.SEMIBOLD -> FontWeight.SemiBold
        FontWeightOption.BOLD -> FontWeight.Bold
    }

    fun typography(font: FontFamily, t: TypographyConfig): Typography {
        val base = Typography()
        val spacing: TextUnit = t.letterSpacingEm.em
        fun TextStyle.f() = copy(fontFamily = font, letterSpacing = if (t.letterSpacingEm != 0f) spacing else letterSpacing)
        return base.copy(
            displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
            headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
            titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
            bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
            labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
        )
    }

    // ───────────── Motion ─────────────

    fun animationsActive(a: AnimationConfig, traits: DeviceTraits, effects: EffectsLevel): Boolean =
        a.enabled && !(a.followSystem && traits.systemAnimationsOff) && effects != EffectsLevel.MINIMAL

    fun duration(a: AnimationConfig, baseMs: Int): Int = (baseMs * a.durationScale).toInt().coerceIn(0, 4000)

    fun <T> spec(a: AnimationConfig, traits: DeviceTraits, effects: EffectsLevel, baseMs: Int = 300): AnimationSpec<T> {
        if (!animationsActive(a, traits, effects)) return snap()
        val d = duration(a, baseMs)
        return when (a.easing) {
            Easing.STANDARD -> tween(d, easing = FastOutSlowInEasing)
            Easing.EMPHASIZED -> tween(d, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
            Easing.LINEAR -> tween(d, easing = LinearEasing)
            Easing.DECELERATE -> tween(d, easing = LinearOutSlowInEasing)
            Easing.SPRING -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow / a.durationScale.coerceAtLeast(0.1f))
            Easing.BOUNCY -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow / a.durationScale.coerceAtLeast(0.1f))
        }
    }
}

fun ColorValue.withOpacity(opacity: Float): Color = color.copy(alpha = color.alpha * opacity.coerceIn(0f, 1f))

/**
 * Applies the theme engine to a subtree. Used both by the launcher surfaces and by Flyer
 * (Flyer can optionally use the launcher font).
 */
@Composable
fun FlyerTheme(config: FlyerConfig, useLauncherFont: Boolean = true, content: @Composable () -> Unit) {
    val dark = ThemeEngine.isDark(config)
    val scheme = ThemeEngine.colorScheme(config, dark)
    val font = remember(config.typography.font, config.typography.customFontPath) { ThemeEngine.fontFamily(config.typography) }
    val typography = remember(font, config.typography, useLauncherFont) {
        ThemeEngine.typography(if (useLauncherFont) font else FontFamily.Default, config.typography)
    }
    CompositionLocalProvider(LocalLauncherFont provides font) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
