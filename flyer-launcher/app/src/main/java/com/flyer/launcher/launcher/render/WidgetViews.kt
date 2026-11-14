package com.flyer.launcher.launcher.render

import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.MaterialSurface
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.withOpacity
import com.flyer.launcher.core.widgets.FlyerWidgetHostView
import com.flyer.launcher.launcher.ui.clickableNoRipple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Container styling shared by live widgets and their placeholders. */
@Composable
fun WidgetContainer(item: WidgetItem, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val w = LocalFlyerConfig.current.widgets
    val radius = (item.cornerRadiusDp ?: w.cornerRadiusDp).dp
    val shape = RoundedCornerShape(radius)
    val bg = (item.background ?: w.containerBackground).withOpacity(w.containerOpacity)
    val config = LocalFlyerConfig.current
    if (config.style.applyToWidgets && config.style.material != com.flyer.launcher.core.config.SurfaceMaterial.FLAT) {
        MaterialSurface(
            tint = bg, cornerRadius = radius,
            modifier = modifier.fillMaxSize().padding((item.paddingDp ?: w.paddingDp).dp),
            backdrop = LocalBackdrops.current.background,
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
        return
    }
    var m = modifier.fillMaxSize().padding((item.paddingDp ?: w.paddingDp).dp)
    if (w.clipToCorners) m = m.clip(shape)
    if (bg.alpha > 0f) m = m.background(bg, shape)
    Box(m, contentAlignment = Alignment.Center) { content() }
}

/** A real, running app widget (AppWidgetHostView) embedded in Compose. */
@Composable
fun LiveWidget(
    item: WidgetItem,
    widthDp: Dp,
    heightDp: Dp,
    listener: FlyerWidgetHostView.Listener?,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val core = LocalLauncherCore.current
    val context = LocalContext.current
    val config = LocalFlyerConfig.current.widgets
    val radiusPx = with(LocalDensity.current) { (item.cornerRadiusDp ?: config.cornerRadiusDp).dp.toPx() }
    val view = if (item.appWidgetId >= 0) core.widgets.viewFor(context, item.appWidgetId) else null
    WidgetContainer(item, modifier) {
        if (view == null) {
            WidgetPlaceholder(item, broken = true, onClick = onRestore)
        } else {
            AndroidView(
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                update = { v ->
                    v.listener = listener
                    v.longPressEnabled = listener != null
                    if (config.clipToCorners) {
                        v.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                            }
                        }
                        v.clipToOutline = true
                    } else {
                        v.clipToOutline = false
                    }
                    core.widgets.updateSize(v, item.appWidgetId, widthDp.value, heightDp.value)
                },
                onRelease = { v -> v.listener = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Preview / editor rendering of a widget (live views can only be attached once). */
@Composable
fun WidgetPlaceholder(item: WidgetItem, broken: Boolean = false, onClick: (() -> Unit)? = null) {
    val core = LocalLauncherCore.current
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val data by produceState<Pair<String, ImageBitmap?>>("Widget" to null, item.appWidgetId, item.provider) {
        value = withContext(Dispatchers.IO) {
            val info = core.widgets.providerInfo(item.appWidgetId)
                ?: core.widgets.findProvider(item.provider, item.providerUser)?.info
            if (info == null) "Widget" to null
            else {
                val label = info.loadLabel(context.packageManager) ?: "Widget"
                val drawable = runCatching { info.loadPreviewImage(context, density) }.getOrNull()
                    ?: runCatching { info.loadIcon(context, density) }.getOrNull()
                label to drawable?.let { d ->
                    val w = d.intrinsicWidth.coerceIn(1, 512)
                    val h = d.intrinsicHeight.coerceIn(1, 512)
                    runCatching { d.toBitmap(w, h).asImageBitmap() }.getOrNull()
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.10f)).let { m ->
            if (onClick != null) m.clickableNoRipple(onClick) else m
        },
        contentAlignment = Alignment.Center,
    ) {
        val (label, preview) = data
        if (preview != null && !broken) {
            Image(preview, null, Modifier.fillMaxSize().padding(6.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit, alpha = 0.9f)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Widgets, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (broken) "$label\nTap to restore" else label,
                    color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 3,
                )
            }
        }
    }
}
