package com.flyer.launcher.launcher.render

import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.FolderPreview
import com.flyer.launcher.core.config.IconShape
import com.flyer.launcher.core.config.LabelStyle
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.ItemStyle
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.render.IconShapes
import com.flyer.launcher.core.render.IconSpec
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.LocalLauncherFont
import com.flyer.launcher.core.theme.ThemeEngine
import com.flyer.launcher.core.theme.withOpacity

/** Loads (and caches) the rendered icon bitmap of an app off the main thread. */
@Composable
fun rememberAppIcon(appKey: String, sizeDp: Dp): ImageBitmap? {
    val core = LocalLauncherCore.current
    val config = LocalFlyerConfig.current
    val index = LocalAppIndex.current
    val overrides = LocalAppOverrides.current
    val density = LocalDensity.current
    val px = with(density) { (sizeDp.toPx() * config.performance.iconQuality).toInt() }
    val spec = remember(config.icons, px) { IconSpec.from(config.icons, px) }
    val entry = index[appKey]
    val source = overrides[appKey]?.icon
    val cacheKey = core.icons.cacheKey(appKey, spec, source)
    val bitmap by produceState(core.icons.peek(cacheKey), cacheKey, entry) {
        if (entry != null) value = core.icons.appIcon(entry, spec, source)
    }
    return bitmap
}

@Composable
fun rememberShortcutIcon(item: ShortcutItem, sizeDp: Dp): ImageBitmap? {
    val core = LocalLauncherCore.current
    val config = LocalFlyerConfig.current
    val px = with(LocalDensity.current) { sizeDp.toPx().toInt() }
    val spec = remember(config.icons, px) { IconSpec.from(config.icons, px) }
    val bitmap by produceState<ImageBitmap?>(null, item.packageName, item.shortcutId, spec) {
        val user = core.appsEngine.userFor(item.userSerial) ?: Process.myUserHandle()
        val info = core.appsEngine.findShortcut(item.packageName, item.shortcutId, user)
        value = core.icons.shortcutIcon(info, "${item.packageName}/${item.shortcutId}", spec)
    }
    return bitmap
}

/** An icon bitmap with the configured shape effects (opacity, saturation, border, shadow). */
@Composable
fun IconImage(bitmap: ImageBitmap?, size: Dp, modifier: Modifier = Modifier, style: ItemStyle = ItemStyle()) {
    val icons = LocalFlyerConfig.current.icons
    val shape = remember(icons.shape, icons.cornerRadiusPercent) {
        IconShapes.composeShape(if (icons.shape == IconShape.SYSTEM) IconShape.CIRCLE else icons.shape, icons.cornerRadiusPercent)
    }
    val filter = remember(icons.saturation) {
        if (icons.saturation >= 0.999f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(icons.saturation) })
    }
    val scale = style.iconScale ?: 1f
    val finalSize = size * scale
    var m = modifier.size(finalSize)
    style.rotationDeg?.let { if (it != 0f) m = m.rotate(it) }
    val effectsAllowed = LocalFlyerConfig.current.performance.effects != com.flyer.launcher.core.config.EffectsLevel.MINIMAL
    if (icons.shadow && effectsAllowed && icons.shape != IconShape.SYSTEM) {
        m = m.shadow(icons.shadowElevationDp.dp, shape, clip = false)
    }
    if (icons.borderWidthDp > 0f && icons.shape != IconShape.SYSTEM) {
        m = m.border(icons.borderWidthDp.dp, icons.borderColor.color, shape)
    }
    val alpha = icons.opacity * (style.opacity ?: 1f)
    Box(m.alpha(alpha), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, colorFilter = filter, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.12f)))
        }
    }
}

@Composable
fun labelTextStyle(label: LabelStyle, colorOverride: Color? = null): TextStyle {
    val config = LocalFlyerConfig.current
    val font = LocalLauncherFont.current
    return remember(label, colorOverride, config.typography, font) {
        TextStyle(
            color = colorOverride ?: label.color.color,
            fontSize = (label.sizeSp * config.typography.textScale).sp,
            fontWeight = ThemeEngine.weight(label.weight),
            fontFamily = font,
            letterSpacing = if (config.typography.letterSpacingEm != 0f) (config.typography.letterSpacingEm * label.sizeSp).sp else TextStyle.Default.letterSpacing,
            textAlign = TextAlign.Center,
            shadow = if (label.shadow) Shadow(label.shadowColor.color, Offset(0f, 1.5f), 4f) else null,
        )
    }
}

@Composable
fun ItemLabel(text: String, label: LabelStyle, modifier: Modifier = Modifier, color: Color? = null, maxLines: Int = label.maxLines) {
    Text(
        text = if (label.uppercase) text.uppercase() else text,
        style = labelTextStyle(label, color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Height reserved for labels below icons (used to fit icons into small cells). */
@Composable
fun labelHeightDp(label: LabelStyle): Dp {
    val scale = LocalFlyerConfig.current.typography.textScale
    return if (!label.show) 0.dp else (label.sizeSp * scale * 1.35f * label.maxLines + 2f).dp
}

/** Icon + label tile used on pages, dock, folders, drawer and desktop. */
@Composable
fun AppTile(
    appKey: String,
    iconSize: Dp,
    showLabel: Boolean,
    label: LabelStyle,
    modifier: Modifier = Modifier,
    style: ItemStyle = ItemStyle(),
    labelColor: Color? = null,
) {
    val index = LocalAppIndex.current
    val overrides = LocalAppOverrides.current
    val config = LocalFlyerConfig.current
    val bitmap = rememberAppIcon(appKey, iconSize)
    val text = labelFor(appKey, index, overrides)
    TileLayout(modifier, iconSize, showLabel && style.hideLabel != true, text, label, style.labelColor?.color ?: labelColor, config.icons.labelGapDp.dp) {
        IconImage(bitmap, iconSize, style = style)
    }
}

@Composable
fun ShortcutTile(item: ShortcutItem, iconSize: Dp, showLabel: Boolean, label: LabelStyle, modifier: Modifier = Modifier) {
    val bitmap = rememberShortcutIcon(item, iconSize)
    val config = LocalFlyerConfig.current
    TileLayout(modifier, iconSize, showLabel && item.style.hideLabel != true, item.label, label, item.style.labelColor?.color, config.icons.labelGapDp.dp) {
        IconImage(bitmap, iconSize, style = item.style)
    }
}

@Composable
fun FolderTile(folder: FolderItem, iconSize: Dp, showLabel: Boolean, label: LabelStyle, modifier: Modifier = Modifier, labelColor: Color? = null) {
    val config = LocalFlyerConfig.current
    TileLayout(modifier, iconSize, showLabel && folder.style.hideLabel != true, folder.name, label, folder.style.labelColor?.color ?: labelColor, config.icons.labelGapDp.dp) {
        FolderIcon(folder, iconSize * (folder.style.iconScale ?: 1f), config)
    }
}

@Composable
private fun TileLayout(
    modifier: Modifier,
    iconSize: Dp,
    showLabel: Boolean,
    text: String,
    label: LabelStyle,
    labelColor: Color?,
    gap: Dp,
    icon: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        icon()
        if (showLabel && label.show) {
            Spacer(Modifier.height(gap))
            ItemLabel(text, label, color = labelColor, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

/** Folder preview icon built from its first apps according to the folder settings. */
@Composable
fun FolderIcon(folder: FolderItem, size: Dp, config: FlyerConfig) {
    val f = config.folders
    val shapeType = folder.shape ?: f.shape
    val shape = remember(shapeType, config.icons.cornerRadiusPercent) {
        IconShapes.composeShape(if (shapeType == IconShape.SYSTEM) IconShape.CIRCLE else shapeType, config.icons.cornerRadiusPercent)
    }
    val bg = (folder.color ?: f.previewBackground).withOpacity(f.previewOpacity)
    val preview = folder.preview ?: f.preview
    Box(
        Modifier.size(size).clip(shape).background(bg).let {
            if (config.icons.borderWidthDp > 0f) it.border(config.icons.borderWidthDp.dp, config.icons.borderColor.color, shape) else it
        },
        contentAlignment = Alignment.Center,
    ) {
        when (preview) {
            FolderPreview.GRID_2X2, FolderPreview.GRID_3X3 -> {
                val n = if (preview == FolderPreview.GRID_2X2) 2 else 3
                val pad = size * 0.14f
                val gap = size * 0.06f
                val cell = (size - pad * 2 - gap * (n - 1)) / n
                folder.apps.take(n * n).forEachIndexed { i, key ->
                    val bmp = rememberAppIcon(key, cell)
                    val x = pad + (cell + gap) * (i % n)
                    val y = pad + (cell + gap) * (i / n)
                    Box(Modifier.align(Alignment.TopStart).offset(x, y)) { MiniIcon(bmp, cell) }
                }
            }
            FolderPreview.STACK -> {
                val cell = size * 0.56f
                folder.apps.take(3).reversed().forEachIndexed { i, key ->
                    val bmp = rememberAppIcon(key, cell)
                    val shift = size * 0.09f * (2 - i - (3 - minOf(3, folder.apps.size)))
                    Box(Modifier.offset(shift, -shift)) { MiniIcon(bmp, cell) }
                }
            }
            FolderPreview.FIRST_ICON -> {
                val bmp = rememberAppIcon(folder.apps.firstOrNull() ?: "", size * 0.72f)
                MiniIcon(bmp, size * 0.72f)
            }
        }
    }
}

@Composable
private fun MiniIcon(bitmap: ImageBitmap?, size: Dp) {
    if (bitmap != null) Image(bitmap, null, Modifier.size(size)) else Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(0.15f)))
}
