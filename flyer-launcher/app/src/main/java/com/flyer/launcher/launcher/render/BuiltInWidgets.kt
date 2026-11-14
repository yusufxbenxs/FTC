package com.flyer.launcher.launcher.render

import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.MaterialSurface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.ClockStyle
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.BuiltInWidgetKind
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.LocalLauncherFont
import com.flyer.launcher.core.theme.ThemeEngine
import com.flyer.launcher.core.theme.withOpacity
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.launcher.LocalLauncherActions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Launcher-owned widgets: completely styled by the configuration. */
@Composable
fun BuiltInWidget(item: BuiltInWidgetItem, modifier: Modifier = Modifier) {
    when (item.kind) {
        BuiltInWidgetKind.CLOCK -> ClockWidget(modifier, showTime = true)
        BuiltInWidgetKind.DATE -> ClockWidget(modifier, showTime = false)
        BuiltInWidgetKind.SEARCH -> SearchBarWidget(modifier)
        BuiltInWidgetKind.FLYER_QUICK_PANEL -> FlyerQuickPanel(modifier)
    }
}

@Composable
private fun rememberNow(precisionSeconds: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(precisionSeconds) {
        while (true) {
            val t = System.currentTimeMillis()
            now = t
            val step = if (precisionSeconds) 1000L else 60_000L
            delay(step - t % step + 5)
        }
    }
    return now
}

@Composable
fun ClockWidget(modifier: Modifier, showTime: Boolean) {
    val config = LocalFlyerConfig.current
    val clock = config.widgets.clock
    val font = LocalLauncherFont.current
    val now = rememberNow(clock.showSeconds && showTime)
    val use24 = clock.use24h
    val timePattern = buildString {
        append(if (use24) "HH:mm" else "h:mm")
        if (clock.showSeconds) append(":ss")
    }
    val date = remember(now / 60_000, clock.datePattern) {
        runCatching { SimpleDateFormat(clock.datePattern, Locale.getDefault()).format(Date(now)) }.getOrDefault("")
    }
    val time = remember(now / 1000, timePattern) { SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(now)) }
    val shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f)
    val align = if (clock.alignStart) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (clock.alignStart) TextAlign.Start else TextAlign.Center
    val scale = config.typography.textScale

    Box(modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = if (clock.alignStart) Alignment.CenterStart else Alignment.Center) {
        if (!showTime) {
            Text(date, style = TextStyle(color = clock.color.color, fontSize = (clock.sizeSp * 0.4f * scale).sp, fontFamily = font, shadow = shadow, textAlign = textAlign))
            return@Box
        }
        when (clock.style) {
            ClockStyle.DIGITAL -> Column(horizontalAlignment = align) {
                Text(
                    time,
                    style = TextStyle(
                        color = clock.color.color, fontSize = (clock.sizeSp * scale).sp, fontFamily = font,
                        fontWeight = ThemeEngine.weight(clock.weight), shadow = shadow, textAlign = textAlign,
                        lineHeight = (clock.sizeSp * scale * 1.05f).sp,
                    ),
                    maxLines = 1,
                )
                if (clock.showDate) {
                    Text(date, style = TextStyle(color = clock.color.color.copy(alpha = 0.85f), fontSize = (14 * scale).sp, fontFamily = font, shadow = shadow))
                }
            }
            ClockStyle.STACKED -> {
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                val h = if (use24) cal.get(Calendar.HOUR_OF_DAY) else (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it })
                val style = TextStyle(
                    color = clock.color.color, fontSize = (clock.sizeSp * 0.9f * scale).sp, fontFamily = font,
                    fontWeight = ThemeEngine.weight(clock.weight), shadow = shadow, lineHeight = (clock.sizeSp * 0.9f * scale).sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%02d".format(h), style = style)
                        Text("%02d".format(cal.get(Calendar.MINUTE)), style = style.copy(color = clock.color.color.copy(alpha = 0.6f)))
                    }
                    if (clock.showDate) {
                        Spacer(Modifier.width(14.dp))
                        Text(date.replace(", ", "\n"), style = TextStyle(color = clock.color.color, fontSize = (15 * scale).sp, fontFamily = font, shadow = shadow))
                    }
                }
            }
            ClockStyle.ANALOG -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                AnalogClock(now, clock.color.color, clock.showSeconds, Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp))
                if (clock.showDate) {
                    Spacer(Modifier.width(12.dp))
                    Text(date, style = TextStyle(color = clock.color.color, fontSize = (15 * scale).sp, fontFamily = font, shadow = shadow))
                }
            }
        }
    }
}

@Composable
private fun AnalogClock(now: Long, color: Color, seconds: Boolean, modifier: Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
    val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    val s = cal.get(Calendar.SECOND).toFloat()
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(color.copy(alpha = 0.12f), r)
        drawCircle(color.copy(alpha = 0.5f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.03f))
        for (i in 0 until 12) {
            rotate(i * 30f, c) {
                drawLine(color.copy(alpha = 0.7f), Offset(c.x, c.y - r * 0.92f), Offset(c.x, c.y - r * 0.8f), strokeWidth = r * 0.04f, cap = StrokeCap.Round)
            }
        }
        rotate(h * 30f, c) { drawLine(color, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = r * 0.08f, cap = StrokeCap.Round) }
        rotate(m * 6f, c) { drawLine(color, c, Offset(c.x, c.y - r * 0.75f), strokeWidth = r * 0.05f, cap = StrokeCap.Round) }
        if (seconds) rotate(s * 6f, c) { drawLine(Color(0xFFFF6B6B), c, Offset(c.x, c.y - r * 0.82f), strokeWidth = r * 0.02f, cap = StrokeCap.Round) }
        drawCircle(color, r * 0.05f, c)
    }
}

@Composable
fun SearchBarWidget(modifier: Modifier) {
    val config = LocalFlyerConfig.current
    val actions = LocalLauncherActions.current
    val shape = RoundedCornerShape(config.search.barCornerDp.dp)
    Box(modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        MaterialSurface(
            tint = config.search.barBackground.color,
            cornerRadius = config.search.barCornerDp.dp,
            modifier = Modifier.fillMaxWidth(),
            enabled = config.style.applyToSearch,
            backdrop = LocalBackdrops.current.background,
        ) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .clickable { actions.openSearch() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = config.icons.label.color.color)
            Spacer(Modifier.width(12.dp))
            Text("Search apps, settings, web", color = config.icons.label.color.color.copy(alpha = 0.8f), fontSize = 15.sp, fontFamily = LocalLauncherFont.current)
        }
        }
    }
}

/** A launcher-owned widget exposing a few Flyer controls right on the home screen. */
@Composable
fun FlyerQuickPanel(modifier: Modifier) {
    val config = LocalFlyerConfig.current
    val actions = LocalLauncherActions.current
    val shape = RoundedCornerShape((20 * config.appearance.cornerScale).dp)
    MaterialSurface(
        tint = config.appearance.menuBackground.withOpacity(0.75f),
        cornerRadius = (20 * config.appearance.cornerScale).dp,
        modifier = modifier.fillMaxSize().padding(4.dp),
        enabled = config.style.applyToMenus,
        backdrop = LocalBackdrops.current.background,
    ) {
    Row(
        Modifier.fillMaxSize().clip(shape).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickButton(Icons.Outlined.Tune, "Flyer") { actions.openFlyer(FlyerRoute.Dashboard) }
        QuickButton(Icons.Outlined.DashboardCustomize, "Edit") { actions.openFlyer(FlyerRoute.LiveEditor()) }
        QuickButton(Icons.Outlined.Palette, "Themes") { actions.openFlyer(FlyerRoute.Category(FlyerCategory.THEMES)) }
        QuickButton(Icons.Outlined.DarkMode, "Dark") { actions.perform(GestureAction(ActionType.TOGGLE_DARK_MODE)) }
        QuickButton(Icons.Outlined.DesktopWindows, "Desktop") { actions.perform(GestureAction(ActionType.TOGGLE_DESKTOP_MODE)) }
    }
    }
}

@Composable
private fun QuickButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
