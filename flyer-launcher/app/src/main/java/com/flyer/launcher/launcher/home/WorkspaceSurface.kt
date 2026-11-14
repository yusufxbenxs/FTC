package com.flyer.launcher.launcher.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.HomeConfig
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.BuiltInWidget
import com.flyer.launcher.launcher.render.FolderTile
import com.flyer.launcher.launcher.render.GridGeometry
import com.flyer.launcher.launcher.render.LocalDraggingIds
import com.flyer.launcher.launcher.render.ShortcutTile
import com.flyer.launcher.launcher.render.WidgetContainer
import com.flyer.launcher.launcher.render.WidgetPlaceholder
import com.flyer.launcher.launcher.render.labelHeightDp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays out workspace items on a surface (home page or desktop) using [GridGeometry]. Purely
 * positional: what each item looks like and how it reacts is decided by [itemContent], so the
 * same surface powers the live home screen, Flyer previews and the live editor.
 */
@Composable
fun WorkspaceSurface(
    items: List<WorkspaceItem>,
    columns: Int,
    rows: Int,
    home: HomeConfig?,
    freePositioning: Boolean,
    modifier: Modifier = Modifier,
    marginsDp: FloatArray? = null,
    underlay: @Composable BoxScope.(GridGeometry) -> Unit = {},
    overlay: @Composable BoxScope.(GridGeometry) -> Unit = {},
    itemContent: @Composable BoxScope.(item: WorkspaceItem, geometry: GridGeometry, rect: Rect) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val geometry = remember(wPx, hPx, columns, rows, home, freePositioning, density, marginsDp?.toList()) {
            GridGeometry.compute(wPx, hPx, columns, rows, home, density, freePositioning, marginsDp)
        }
        underlay(geometry)
        val dragging = LocalDraggingIds.current
        for (item in items) {
            key(item.id) {
                val rect = geometry.itemRect(item)
                val wDp = with(density) { rect.width.toDp() }
                val hDp = with(density) { rect.height.toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(wDp, hDp)
                        .let { if (item.id in dragging) it.dimmed() else it },
                    contentAlignment = Alignment.Center,
                ) {
                    itemContent(item, geometry, rect)
                }
            }
        }
        overlay(geometry)
    }
}

private fun Modifier.dimmed(): Modifier = this.alpha(0.25f)

/** Icon size that fits a cell together with its label. */
@Composable
fun fittedIconSize(config: FlyerConfig, cellWidthPx: Float, cellHeightPx: Float, showLabel: Boolean, requested: Int = config.icons.sizeDp): Dp {
    val density = LocalDensity.current
    val label = if (showLabel) labelHeightDp(config.icons.label) + config.icons.labelGapDp.dp else 0.dp
    val maxW = with(density) { (cellWidthPx * 0.94f).toDp() }
    val maxH = with(density) { cellHeightPx.toDp() } - label - 2.dp
    return min(requested.toFloat(), min(maxW.value, maxH.value)).coerceAtLeast(16f).dp
}

/**
 * Static visual of any workspace item (no interaction). Used by previews, the editor and the
 * drag visual. Widgets render as placeholders.
 */
@Composable
fun StaticItemVisual(item: WorkspaceItem, cellWidthPx: Float, cellHeightPx: Float, showLabels: Boolean = true, modifier: Modifier = Modifier) {
    val config = LocalFlyerConfig.current
    when (item) {
        is AppItem -> {
            val size = fittedIconSize(config, cellWidthPx * item.cell.spanX, cellHeightPx * item.cell.spanY, showLabels && config.icons.label.show)
            AppTile(item.appKey, size, showLabels, config.icons.label, modifier, item.style)
        }
        is FolderItem -> {
            val size = fittedIconSize(config, cellWidthPx, cellHeightPx, showLabels && config.icons.label.show)
            FolderTile(item, size, showLabels, config.icons.label, modifier)
        }
        is ShortcutItem -> {
            val size = fittedIconSize(config, cellWidthPx, cellHeightPx, showLabels && config.icons.label.show)
            ShortcutTile(item, size, showLabels, config.icons.label, modifier)
        }
        is WidgetItem -> WidgetContainer(item, modifier) { WidgetPlaceholder(item) }
        is BuiltInWidgetItem -> BuiltInWidget(item, modifier)
    }
}

/** Draws the cell grid (while dragging or in the editor). */
@Composable
fun BoxScope.GridOverlay(geometry: GridGeometry, color: Color = Color.White.copy(alpha = 0.22f), highlight: Rect? = null, highlightColor: Color = Color.White) {
    Canvas(Modifier.matchParentSize()) {
        for (y in 0 until geometry.rows) for (x in 0 until geometry.columns) {
            val r = geometry.cellRect(com.flyer.launcher.core.model.Cell(x, y))
            drawRoundRect(
                color, topLeft = Offset(r.left + 3f, r.top + 3f), size = Size(r.width - 6f, r.height - 6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(width = 1.5f),
            )
        }
        highlight?.let { r ->
            drawRoundRect(
                highlightColor.copy(alpha = 0.22f), topLeft = r.topLeft, size = r.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f),
            )
            drawRoundRect(
                highlightColor, topLeft = r.topLeft, size = r.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f), style = Stroke(width = 3f),
            )
        }
    }
}
