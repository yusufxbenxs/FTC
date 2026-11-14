package com.flyer.launcher.core.workspace

import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.FreePos
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.model.newId

/**
 * Repairs a layout so that it can always be rendered for a given configuration:
 * unique ids, items inside the grid, no overlaps in grid mode, normalized folders, and a dock that
 * fits its slot count. Items that no longer fit are moved to a free spot or a new page — never
 * silently dropped.
 */
object LayoutValidator {

    fun sanitize(layout: HomeLayout, config: FlyerConfig): HomeLayout {
        val seen = HashSet<String>()
        fun uniq(item: WorkspaceItem): WorkspaceItem {
            val fixed = if (item.id.isBlank() || !seen.add(item.id)) {
                val id = newId().also { seen.add(it) }
                when (item) {
                    is AppItem -> item.copy(id = id)
                    is FolderItem -> item.copy(id = id)
                    is com.flyer.launcher.core.model.WidgetItem -> item.copy(id = id)
                    is com.flyer.launcher.core.model.BuiltInWidgetItem -> item.copy(id = id)
                    is com.flyer.launcher.core.model.ShortcutItem -> item.copy(id = id)
                }
            } else item
            val free = fixed.free?.let { FreePos(it.x.safeUnit(), it.y.safeUnit()) }
            return fixed.withFree(free)
        }

        fun normalize(items: List<WorkspaceItem>): List<WorkspaceItem> = items.mapNotNull { item ->
            val u = uniq(item)
            if (u is FolderItem) WorkspaceEngine.normalizeFolder(u.copy(apps = u.apps.distinct())) else u
        }

        val homeDims = config.homeGrid()
        val freeMode = config.home.positioning == Positioning.FREE
        val overflow = mutableListOf<WorkspaceItem>()

        val pages = (if (layout.pages.isEmpty()) listOf(HomePage()) else layout.pages).map { page ->
            page.copy(
                id = page.id.ifBlank { newId() },
                items = fitGrid(normalize(page.items), homeDims, allowOverlap = freeMode && config.home.allowOverlap, freeMode = freeMode, overflow = overflow),
            )
        }.toMutableList()

        // Dock: sorted, renumbered, extra items beyond the slot count go to the home pages.
        val dockItems = WorkspaceEngine.renumberDock(normalize(layout.dock))
        val keptDock = dockItems.take(config.dock.slots)
        overflow += dockItems.drop(config.dock.slots)

        var result = layout.copy(pages = pages, dock = keptDock)
        for (item in overflow) result = WorkspaceEngine.addItem(result, item, config)

        val desktopDims = config.desktopGrid()
        val desktopOverflow = mutableListOf<WorkspaceItem>()
        val desktopItems = fitGrid(normalize(layout.desktop.items), desktopDims, allowOverlap = true, freeMode = true, overflow = desktopOverflow)
        result = result.copy(
            desktop = result.desktop.copy(
                items = desktopItems + desktopOverflow.map { it.withCell(it.cell.copy(x = 0, y = 0)) },
                taskbarPins = layout.desktop.taskbarPins.distinct(),
            ),
            drawerGroups = layout.drawerGroups.map { it.copy(id = it.id.ifBlank { newId() }, apps = it.apps.distinct()) },
        )
        return result
    }

    /** Clamps spans/positions to the grid and resolves overlaps by relocating later items. */
    private fun fitGrid(
        items: List<WorkspaceItem>,
        dims: GridDims,
        allowOverlap: Boolean,
        freeMode: Boolean,
        overflow: MutableList<WorkspaceItem>,
    ): List<WorkspaceItem> {
        val placed = mutableListOf<WorkspaceItem>()
        for (item in items) {
            val sx = item.cell.spanX.coerceIn(1, dims.columns)
            val sy = item.cell.spanY.coerceIn(1, dims.rows)
            val cell = Cell(
                item.cell.x.coerceIn(0, dims.columns - sx),
                item.cell.y.coerceIn(0, dims.rows - sy),
                sx, sy,
            )
            if (allowOverlap || (freeMode && item.free != null) || !WorkspaceEngine.isOccupied(placed, cell)) {
                placed += item.withCell(cell)
                continue
            }
            val alt = WorkspaceEngine.findFreeCell(placed, dims, sx, sy, near = cell)
            if (alt != null) placed += item.withCell(alt) else overflow += item.withCell(cell)
        }
        return placed
    }

    private fun Float.safeUnit() = if (isNaN() || isInfinite()) 0f else coerceIn(0f, 1f)
}
