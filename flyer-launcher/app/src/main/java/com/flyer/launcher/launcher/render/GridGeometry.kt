package com.flyer.launcher.launcher.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import com.flyer.launcher.core.config.CellSizing
import com.flyer.launcher.core.config.HomeConfig
import com.flyer.launcher.core.config.VerticalPlacement
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FreePos
import com.flyer.launcher.core.model.WorkspaceItem
import kotlin.math.floor

/**
 * Pixel geometry of a workspace surface. Computed from the configuration and the measured size;
 * used for rendering, hit testing, drag & drop and the editor's guides — so all of them agree.
 */
@Immutable
data class GridGeometry(
    val width: Float,
    val height: Float,
    val columns: Int,
    val rows: Int,
    val left: Float,
    val top: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val spacingH: Float,
    val spacingV: Float,
    val cellWidth: Float,
    val cellHeight: Float,
    val freePositioning: Boolean,
) {
    fun cellRect(cell: Cell): Rect {
        val x = left + cell.x * (cellWidth + spacingH)
        val y = top + cell.y * (cellHeight + spacingV)
        val w = cell.spanX * cellWidth + (cell.spanX - 1) * spacingH
        val h = cell.spanY * cellHeight + (cell.spanY - 1) * spacingV
        return Rect(x, y, x + w, y + h)
    }

    fun spanSize(spanX: Int, spanY: Int) = Pair(
        spanX * cellWidth + (spanX - 1) * spacingH,
        spanY * cellHeight + (spanY - 1) * spacingV,
    )

    fun freeRect(free: FreePos, cell: Cell): Rect {
        val (w, h) = spanSize(cell.spanX, cell.spanY)
        val x = left + free.x * contentWidth
        val y = top + free.y * contentHeight
        return Rect(x, y, x + w, y + h)
    }

    fun itemRect(item: WorkspaceItem): Rect {
        val free = item.free
        return if (freePositioning && free != null) freeRect(free, item.cell) else cellRect(item.cell)
    }

    /** Cell under a point (clamped to the grid). */
    fun cellAt(p: Offset): Pair<Int, Int> {
        val cx = floor((p.x - left) / (cellWidth + spacingH)).toInt().coerceIn(0, columns - 1)
        val cy = floor((p.y - top) / (cellHeight + spacingV)).toInt().coerceIn(0, rows - 1)
        return cx to cy
    }

    /** Top-left cell for an item whose visual top-left is at [topLeft]. */
    fun cellForTopLeft(topLeft: Offset, spanX: Int, spanY: Int): Cell {
        val cx = ((topLeft.x - left) / (cellWidth + spacingH) + 0.5f).toInt().coerceIn(0, (columns - spanX).coerceAtLeast(0))
        val cy = ((topLeft.y - top) / (cellHeight + spacingV) + 0.5f).toInt().coerceIn(0, (rows - spanY).coerceAtLeast(0))
        return Cell(cx, cy, spanX, spanY)
    }

    /** Free fraction for a visual top-left point, optionally snapped to [snapPx]. */
    fun freeFor(topLeft: Offset, spanX: Int, spanY: Int, snapPx: Float): FreePos {
        val (w, h) = spanSize(spanX, spanY)
        var x = (topLeft.x - left).coerceIn(0f, (contentWidth - w).coerceAtLeast(0f))
        var y = (topLeft.y - top).coerceIn(0f, (contentHeight - h).coerceAtLeast(0f))
        if (snapPx >= 1f) {
            x = (x / snapPx + 0.5f).toInt() * snapPx
            y = (y / snapPx + 0.5f).toInt() * snapPx
        }
        return FreePos(if (contentWidth > 0) x / contentWidth else 0f, if (contentHeight > 0) y / contentHeight else 0f)
    }

    companion object {
        fun compute(
            widthPx: Float,
            heightPx: Float,
            columns: Int,
            rows: Int,
            home: HomeConfig?,
            density: Density,
            freePositioning: Boolean,
            marginsDp: FloatArray? = null,
        ): GridGeometry {
            with(density) {
                val m = marginsDp ?: floatArrayOf(
                    (home?.marginStartDp ?: 0).toFloat(), (home?.marginTopDp ?: 0).toFloat(),
                    (home?.marginEndDp ?: 0).toFloat(), (home?.marginBottomDp ?: 0).toFloat(),
                )
                val left = m[0] * this.density
                val top = m[1] * this.density
                val right = m[2] * this.density
                val bottom = m[3] * this.density
                val sh = (home?.cellSpacingHDp ?: 0) * this.density
                val sv = (home?.cellSpacingVDp ?: 0) * this.density
                val cw = (widthPx - left - right).coerceAtLeast(1f)
                val ch = (heightPx - top - bottom).coerceAtLeast(1f)
                val cellW = ((cw - sh * (columns - 1)) / columns).coerceAtLeast(1f)
                var cellH = ((ch - sv * (rows - 1)) / rows).coerceAtLeast(1f)
                var gridTop = top
                var gridHeight = ch
                if (home != null && home.cellSizing == CellSizing.FIXED) {
                    val fixed = home.fixedCellHeightDp * this.density
                    cellH = minOf(fixed, cellH)
                    gridHeight = cellH * rows + sv * (rows - 1)
                    gridTop = when (home.gridPlacement) {
                        VerticalPlacement.TOP -> top
                        VerticalPlacement.CENTER -> top + (ch - gridHeight) / 2f
                        VerticalPlacement.BOTTOM -> top + ch - gridHeight
                    }
                }
                return GridGeometry(
                    width = widthPx, height = heightPx, columns = columns, rows = rows,
                    left = left, top = gridTop, contentWidth = cw, contentHeight = gridHeight,
                    spacingH = sh, spacingV = sv, cellWidth = cellW, cellHeight = cellH,
                    freePositioning = freePositioning,
                )
            }
        }
    }
}
