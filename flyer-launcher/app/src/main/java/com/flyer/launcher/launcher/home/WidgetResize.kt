package com.flyer.launcher.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.widgets.WidgetEngine
import com.flyer.launcher.launcher.render.GridGeometry
import com.flyer.launcher.launcher.ui.clickableNoRipple
import kotlin.math.roundToInt

enum class ResizeEdge { LEFT, TOP, RIGHT, BOTTOM }

/** Computes a new cell when an edge is dragged by [deltaCells] (clamped to limits and grid). */
fun resizeCell(cell: Cell, edge: ResizeEdge, deltaCells: Int, limits: WidgetEngine.SpanLimits?, columns: Int, rows: Int, respectLimits: Boolean): Cell {
    val minX = if (respectLimits && limits != null) limits.minX else 1
    val minY = if (respectLimits && limits != null) limits.minY else 1
    val maxX = if (respectLimits && limits != null) limits.maxX else columns
    val maxY = if (respectLimits && limits != null) limits.maxY else rows
    return when (edge) {
        ResizeEdge.RIGHT -> cell.copy(spanX = (cell.spanX + deltaCells).coerceIn(minX, minOf(maxX, columns - cell.x)))
        ResizeEdge.BOTTOM -> cell.copy(spanY = (cell.spanY + deltaCells).coerceIn(minY, minOf(maxY, rows - cell.y)))
        ResizeEdge.LEFT -> {
            val right = cell.x + cell.spanX
            val span = (cell.spanX - deltaCells).coerceIn(minX, minOf(maxX, right))
            cell.copy(x = right - span, spanX = span)
        }
        ResizeEdge.TOP -> {
            val bottom = cell.y + cell.spanY
            val span = (cell.spanY - deltaCells).coerceIn(minY, minOf(maxY, bottom))
            cell.copy(y = bottom - span, spanY = span)
        }
    }
}

/** Resize handles drawn over a widget on its page. */
@Composable
fun BoxScope.WidgetResizeFrame(
    item: WorkspaceItem,
    geometry: GridGeometry,
    limits: WidgetEngine.SpanLimits?,
    respectLimits: Boolean,
    onResize: (Cell) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val rect = geometry.cellRect(item.cell)
    val current = rememberUpdatedState(item.cell)
    Box(Modifier.matchParentSize().clickableNoRipple(onDone))
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .border(2.dp, Color.White, RoundedCornerShape(16.dp)),
    ) {
        ResizeEdge.entries.forEach { edge ->
            var acc by remember(edge) { mutableFloatStateOf(0f) }
            val align = when (edge) {
                ResizeEdge.LEFT -> Alignment.CenterStart
                ResizeEdge.RIGHT -> Alignment.CenterEnd
                ResizeEdge.TOP -> Alignment.TopCenter
                ResizeEdge.BOTTOM -> Alignment.BottomCenter
            }
            val step = if (edge == ResizeEdge.LEFT || edge == ResizeEdge.RIGHT) geometry.cellWidth + geometry.spacingH else geometry.cellHeight + geometry.spacingV
            Box(
                Modifier.align(align)
                    .offset {
                        when (edge) {
                            ResizeEdge.LEFT -> IntOffset(-12.dp.roundToPx(), 0)
                            ResizeEdge.RIGHT -> IntOffset(12.dp.roundToPx(), 0)
                            ResizeEdge.TOP -> IntOffset(0, -12.dp.roundToPx())
                            ResizeEdge.BOTTOM -> IntOffset(0, 12.dp.roundToPx())
                        }
                    }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(edge, step) {
                        detectDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, drag ->
                            change.consume()
                            acc += if (edge == ResizeEdge.LEFT || edge == ResizeEdge.RIGHT) drag.x else drag.y
                            val cells = (acc / step).roundToInt()
                            if (cells != 0) {
                                val next = resizeCell(current.value, edge, cells, limits, geometry.columns, geometry.rows, respectLimits)
                                if (next != current.value) onResize(next)
                                acc -= cells * step
                            }
                        }
                    },
            )
        }
    }
}
