package com.flyer.launcher.launcher.overlays

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.config.FolderOpenStyle
import com.flyer.launcher.core.config.OverlayAnimation
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.LocalLauncherFont
import com.flyer.launcher.core.theme.ThemeEngine
import com.flyer.launcher.core.theme.withOpacity
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.OpenFolder
import com.flyer.launcher.launcher.home.ItemMenus
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.ui.CoordsHolder
import com.flyer.launcher.launcher.ui.ItemGestureCallbacks
import com.flyer.launcher.launcher.ui.clickableNoRipple
import com.flyer.launcher.launcher.ui.itemGestures
import kotlin.math.roundToInt

/**
 * An open folder: a popup card, a full-screen panel, or (desktop mode) a draggable window.
 * [interactive] = false renders the same visual for Flyer's folder preview.
 */
@Composable
fun FolderPopup(
    ui: LauncherUiState?,
    folder: FolderItem,
    open: OpenFolder?,
    interactive: Boolean,
    asWindow: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = LocalFlyerConfig.current
    val f = config.folders
    val traits = LocalDeviceTraits.current
    val columns = (folder.columns ?: f.columns).coerceIn(2, 8)
    val iconSize = config.icons.sizeDp.dp
    val labelExtra = if (f.showLabels) (config.icons.label.sizeSp * 1.4f + config.icons.labelGapDp).dp else 0.dp
    val rowHeight = iconSize + labelExtra + 16.dp
    val fullscreen = f.openStyle == FolderOpenStyle.FULLSCREEN && !asWindow
    val shape = RoundedCornerShape(if (fullscreen) 0.dp else f.popupCornerRadiusDp.dp)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(folder.id) {
        progress.animateTo(1f, ThemeEngine.spec(config.animations, traits, config.performance.effects, 260))
    }
    val p = progress.value
    val anchor = open?.anchor

    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (asWindow) 0f else 0.35f * p))
            .let { if (interactive && !asWindow) it.clickableNoRipple(onDismiss) else it },
    ) {
        val density = LocalDensity.current
        val maxW = maxWidth
        val cardWidth = if (fullscreen) maxW else minOf(maxW - 32.dp, (iconSize + 28.dp) * columns + 32.dp)
        var windowOffset by remember(folder.id) { mutableStateOf(Offset.Zero) }

        val origin = if (anchor != null && !fullscreen) {
            with(density) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                TransformOrigin((anchor.center.x / w).coerceIn(0f, 1f), (anchor.center.y / h).coerceIn(0f, 1f))
            }
        } else TransformOrigin.Center

        val anim = Modifier.graphicsLayer {
            alpha = if (f.animation == OverlayAnimation.NONE) 1f else p
            when (f.animation) {
                OverlayAnimation.SCALE -> { scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p; transformOrigin = origin }
                OverlayAnimation.SLIDE_UP -> translationY = (1f - p) * 120.dp.toPx()
                OverlayAnimation.SLIDE_SIDE -> translationX = (1f - p) * 120.dp.toPx()
                else -> Unit
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(windowOffset.x.roundToInt(), windowOffset.y.roundToInt()) }
                .then(anim)
                .width(cardWidth)
                .let { if (fullscreen) it.fillMaxSize() else it }
                .windowInsetsPadding(WindowInsets.systemBars)
                .clip(shape)
                .background(f.popupBackground.withOpacity(f.popupOpacity))
                .clickableNoRipple { }
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().let { m ->
                    if (asWindow && interactive) m.pointerInput(folder.id) {
                        detectDragGestures { change, drag -> change.consume(); windowOffset += drag }
                    } else m
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (f.showTitle) {
                    Text(
                        folder.name,
                        style = TextStyle(color = f.titleColor.color, fontSize = f.titleSizeSp.sp, fontWeight = FontWeight.Medium, fontFamily = LocalLauncherFont.current),
                        modifier = Modifier.weight(1f).let { if (interactive && ui != null) it.clickableNoRipple { ui.renameFolderId = folder.id } else it }.padding(4.dp),
                    )
                } else Spacer(Modifier.weight(1f))
                if (asWindow) {
                    IconButton(onClick = onDismiss, enabled = interactive) { Icon(Icons.Outlined.Close, "Close", tint = f.titleColor.color) }
                }
            }
            Spacer(Modifier.size(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                userScrollEnabled = interactive,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
                modifier = Modifier.fillMaxWidth().let { if (fullscreen) it.weight(1f) else it.heightIn(max = rowHeight * f.maxVisibleRows) },
            ) {
                items(folder.apps, key = { it }) { key ->
                    FolderAppItem(ui, folder, key, interactive)
                }
            }
        }
    }
}

@Composable
private fun FolderAppItem(ui: LauncherUiState?, folder: FolderItem, appKey: String, interactive: Boolean) {
    val config = LocalFlyerConfig.current
    val actions = LocalLauncherActions.current
    val view = LocalView.current
    val holder = remember { CoordsHolder() }
    val callbacks = remember(appKey, folder.id) {
        object : ItemGestureCallbacks {
            override fun onTap(rootPos: Offset) = actions.launchApp(appKey, holder.boundsInRoot())
            override fun onLongPress(rootPos: Offset) {
                val u = ui ?: return
                val entry = u.core.resolveApp(appKey) ?: return
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val bounds = holder.boundsInRoot()
                u.menu = ItemMenus.forApp(u, actions, entry, null, null, bounds)
                if (!u.core.config.value.home.lockLayout) {
                    u.drag.prepare(Placement.FromFolder(folder.id, appKey), AppItem(appKey = appKey), null, bounds, rootPos)
                }
            }
            override fun onSecondaryClick(rootPos: Offset) {
                val u = ui ?: return
                val entry = u.core.resolveApp(appKey) ?: return
                u.menu = ItemMenus.forApp(u, actions, entry, null, null, holder.boundsInRoot())
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().itemGestures(holder, ui?.arbiter, enabled = interactive && ui != null, callbacks = callbacks).padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppTile(appKey, config.icons.sizeDp.dp, config.folders.showLabels, config.icons.label, labelColor = config.folders.labelColor.color)
    }
}
