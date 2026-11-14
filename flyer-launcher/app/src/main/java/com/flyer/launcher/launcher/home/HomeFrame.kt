package com.flyer.launcher.launcher.home

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flyer.launcher.core.config.BackgroundConfig
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.DockAlignment
import com.flyer.launcher.core.config.DockConfig
import com.flyer.launcher.core.config.DockPosition
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.PageIndicatorStyle
import com.flyer.launcher.core.config.PageTransition
import com.flyer.launcher.core.config.SearchBarPosition
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.core.config.SurfaceMaterial
import com.flyer.launcher.launcher.render.materials.AnimatedBackground
import com.flyer.launcher.launcher.render.materials.BackdropBlur
import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.MaterialSurface
import androidx.compose.ui.platform.LocalDensity
import com.flyer.launcher.core.theme.withOpacity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The launcher-rendered background (solid, gradient or image) plus the contrast scrim. */
@Composable
fun HomeBackground(bg: BackgroundConfig, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (bg.type) {
            BackgroundType.SYSTEM_WALLPAPER -> Unit
            BackgroundType.SOLID -> Box(Modifier.fillMaxSize().background(bg.color.color))
            BackgroundType.GRADIENT -> {
                val angle = Math.toRadians(bg.gradientAngle.toDouble())
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = maxOf(size.width, size.height) / 2f
                    val dx = (cos(angle) * r).toFloat()
                    val dy = (sin(angle) * r).toFloat()
                    drawRect(Brush.linearGradient(listOf(bg.color.color, bg.gradientEnd.color), Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy)))
                }
            }
            BackgroundType.IMAGE -> {
                val image = rememberBackgroundImage(bg.imagePath)
                if (image != null) {
                    val blurOk = bg.imageBlurDp > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    Image(
                        image, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().let { if (blurOk) it.blur(bg.imageBlurDp.dp) else it },
                    )
                } else Box(Modifier.fillMaxSize().background(bg.color.color))
            }
            else -> AnimatedBackground(bg)
        }
        if (bg.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bg.dim)))
    }
}

@Composable
fun rememberBackgroundImage(path: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                var sample = 1
                while (opts.outWidth / (sample * 2) >= 1440 && opts.outHeight / (sample * 2) >= 1440) sample *= 2
                File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return image
}

/**
 * The arrangement of home elements: search bar, pages, page indicator and dock in every dock
 * position. Shared by the live home screen and every Flyer preview.
 */
@Composable
fun HomeFrame(
    config: FlyerConfig,
    insets: PaddingValues,
    modifier: Modifier = Modifier,
    pages: @Composable (Modifier) -> Unit,
    indicator: @Composable () -> Unit,
    searchBar: @Composable () -> Unit,
    dock: @Composable (vertical: Boolean) -> Unit,
) {
    val dockCfg = config.dock
    val showDock = dockCfg.enabled
    val searchPos = config.search.homeBar
    val indicatorTop = config.home.pageIndicatorAtTop

    val inner: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            if (indicatorTop) indicator()
            if (searchPos == SearchBarPosition.TOP) searchBar()
            pages(Modifier.weight(1f).fillMaxWidth())
            if (!indicatorTop) indicator()
            if (searchPos == SearchBarPosition.ABOVE_DOCK) searchBar()
        }
    }

    Box(modifier.fillMaxSize().padding(insets)) {
        when {
            !showDock -> inner(Modifier.fillMaxSize())
            dockCfg.position == DockPosition.BOTTOM -> Column(Modifier.fillMaxSize()) {
                inner(Modifier.weight(1f).fillMaxWidth())
                dock(false)
            }
            dockCfg.position == DockPosition.TOP -> Column(Modifier.fillMaxSize()) {
                dock(false)
                inner(Modifier.weight(1f).fillMaxWidth())
            }
            dockCfg.position == DockPosition.LEFT -> Row(Modifier.fillMaxSize()) {
                dock(true)
                inner(Modifier.weight(1f).fillMaxHeight())
            }
            else -> Row(Modifier.fillMaxSize()) {
                inner(Modifier.weight(1f).fillMaxHeight())
                dock(true)
            }
        }
    }
}

/** Dock background + arrangement. Content is laid out according to [DockConfig.alignment]. */
@Composable
fun DockContainer(
    config: FlyerConfig,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val d = config.dock
    val traits = LocalDeviceTraits.current
    val backdrop = LocalBackdrops.current.background
    val outer = if (vertical) {
        Modifier.fillMaxHeight(d.lengthFraction).width(d.thicknessDp.dp).padding(vertical = d.edgeMarginDp.dp, horizontal = d.marginDp.dp)
    } else {
        Modifier.fillMaxWidth(d.lengthFraction).height(d.thicknessDp.dp).padding(horizontal = d.edgeMarginDp.dp, vertical = d.marginDp.dp)
    }
    val materialOn = config.style.applyToDock && config.style.material != SurfaceMaterial.FLAT
    Box(
        if (vertical) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSurface(
            tint = d.background.withOpacity(d.opacity),
            cornerRadius = d.cornerRadiusDp.dp,
            modifier = modifier.then(outer),
            enabled = config.style.applyToDock,
            backdrop = backdrop,
            borderWidth = d.borderWidthDp.dp,
            borderColor = d.borderColor.color,
        ) {
            // The dock's own blur setting also works with the flat material.
            val blurActive = !materialOn && d.blurDp > 0 && traits.blurSupported && config.performance.blurEffects && backdrop != null
            if (blurActive) {
                val cap = if (config.performance.effects == com.flyer.launcher.core.config.EffectsLevel.BALANCED) 16 else 64
                BackdropBlur(backdrop!!, with(LocalDensity.current) { d.blurDp.coerceAtMost(cap).dp.toPx() })
                Box(Modifier.matchParentSize().background(d.background.withOpacity(d.opacity)))
            }
            val arrangementH = dockArrangementH(d)
            val arrangementV = dockArrangementV(d)
            if (vertical) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = arrangementV, horizontalAlignment = Alignment.CenterHorizontally) { content() }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalArrangement = arrangementH, verticalAlignment = Alignment.CenterVertically) { content() }
            }
        }
    }
}

private fun dockArrangementH(d: DockConfig): Arrangement.Horizontal = when (d.alignment) {
    DockAlignment.CENTER -> Arrangement.spacedBy(d.iconSpacingDp.dp, Alignment.CenterHorizontally)
    DockAlignment.START -> Arrangement.spacedBy(d.iconSpacingDp.dp, Alignment.Start)
    DockAlignment.END -> Arrangement.spacedBy(d.iconSpacingDp.dp, Alignment.End)
    DockAlignment.SPACE_EVENLY -> Arrangement.SpaceEvenly
    DockAlignment.SPACE_BETWEEN -> Arrangement.SpaceBetween
}

private fun dockArrangementV(d: DockConfig): Arrangement.Vertical = when (d.alignment) {
    DockAlignment.CENTER -> Arrangement.spacedBy(d.iconSpacingDp.dp, Alignment.CenterVertically)
    DockAlignment.START -> Arrangement.spacedBy(d.iconSpacingDp.dp, Alignment.Top)
    DockAlignment.END -> Arrangement.spacedBy(d.iconSpacingDp.dp, Alignment.Bottom)
    DockAlignment.SPACE_EVENLY -> Arrangement.SpaceEvenly
    DockAlignment.SPACE_BETWEEN -> Arrangement.SpaceBetween
}

@Composable
fun PageIndicator(config: FlyerConfig, count: Int, position: Float, modifier: Modifier = Modifier) {
    val style = config.home.pageIndicator
    if (style == PageIndicatorStyle.NONE || count <= 1) {
        Spacer(modifier.height(4.dp))
        return
    }
    val color = config.home.pageIndicatorColor.color
    Box(modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
        when (style) {
            PageIndicatorStyle.DOTS -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(count) { i ->
                    val d = abs(position - i).coerceAtMost(1f)
                    val s = 6f + 3f * (1f - d)
                    Box(Modifier.size(s.dp).clip(CircleShape).background(color.copy(alpha = 0.35f + 0.65f * (1f - d))))
                }
            }
            PageIndicatorStyle.LINE -> Box(Modifier.width((24 * count).dp).height(3.dp).clip(CircleShape).background(color.copy(alpha = 0.25f))) {
                Box(
                    Modifier.width(24.dp).height(3.dp)
                        .graphicsLayer { translationX = position * 24.dp.toPx() }
                        .clip(CircleShape).background(color),
                )
            }
            PageIndicatorStyle.NUMBERS -> Text(
                "${(position + 0.5f).toInt().coerceIn(0, count - 1) + 1} / $count",
                color = color, fontSize = 12.sp,
            )
            PageIndicatorStyle.NONE -> Unit
        }
    }
}

/** Applies the configured page transition to a pager page. [offset] = (current - page) + fraction. */
fun Modifier.pageTransition(transition: PageTransition, offset: Float): Modifier = when (transition) {
    PageTransition.SLIDE, PageTransition.NONE -> this
    PageTransition.FADE -> this.graphicsLayer {
        translationX = offset * size.width
        alpha = (1f - abs(offset)).coerceIn(0f, 1f)
    }
    PageTransition.DEPTH -> this.graphicsLayer {
        if (offset > 0f) {
            translationX = offset * size.width
            val s = 1f - 0.25f * offset.coerceAtMost(1f)
            scaleX = s; scaleY = s
            alpha = (1f - offset).coerceIn(0f, 1f)
        }
    }
    PageTransition.ZOOM -> this.graphicsLayer {
        val a = abs(offset).coerceAtMost(1f)
        val s = 1f - 0.18f * a
        scaleX = s; scaleY = s
        alpha = 1f - 0.5f * a
    }
    PageTransition.CUBE -> this.graphicsLayer {
        cameraDistance = 12f * density
        transformOrigin = if (offset > 0f) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
        rotationY = -90f * offset.coerceIn(-1f, 1f)
    }
    PageTransition.STACK -> this.zIndex(if (offset < 0f) 1f else 0f).graphicsLayer {
        if (offset > 0f) {
            translationX = offset * size.width
            alpha = 1f - 0.5f * offset.coerceAtMost(1f)
        }
    }
    PageTransition.ROTATE -> this.graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1.2f)
        rotationZ = -18f * offset.coerceIn(-1f, 1f)
    }
}

/** Convenience: should this config show any search bar on the home screen? */
fun FlyerConfig.hasHomeSearch() = search.homeBar != SearchBarPosition.HIDDEN

@Composable
fun rememberSearchBarHeight(config: FlyerConfig) = remember(config.search.barHeightDp) { config.search.barHeightDp.dp }
