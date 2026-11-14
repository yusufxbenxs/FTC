package com.flyer.launcher.core.workspace

import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.FreePos
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import kotlin.math.abs

/** Grid dimensions of a surface. */
data class GridDims(val columns: Int, val rows: Int)

fun FlyerConfig.homeGrid() = GridDims(home.columns, home.rows)
fun FlyerConfig.desktopGrid() = GridDims(desktop.columns, desktop.rows)
fun FlyerConfig.gridFor(surface: Surface) = when (surface) {
    Surface.Desktop -> desktopGrid()
    Surface.Dock -> GridDims(dock.slots, 1)
    else -> homeGrid()
}

/** Where a dragged item should land. */
sealed interface DropTarget {
    /** A grid cell (top-left of the item) on a page or the desktop. */
    data class AtCell(val surface: Surface, val x: Int, val y: Int) : DropTarget

    /** A free position (fraction of the content area) on a page or the desktop. */
    data class AtFree(val surface: Surface, val pos: FreePos, val fallbackCell: Cell) : DropTarget

    /** On top of another item: creates a folder (app onto app) or adds to a folder. */
    data class OntoItem(val surface: Surface, val targetId: String) : DropTarget

    /** A dock position (insertion index). */
    data class DockIndex(val index: Int) : DropTarget
}

/** Something that is being placed: either an existing item or a new app coming from the drawer/folder. */
sealed interface Placement {
    data class Existing(val itemId: String) : Placement
    data class NewApp(val appKey: String) : Placement
    data class NewItem(val item: WorkspaceItem) : Placement

    /** An app dragged out of a folder. */
    data class FromFolder(val folderId: String, val appKey: String) : Placement
}

/**
 * Pure, side-effect free operations on [HomeLayout]. Every launcher surface (home pages, dock,
 * desktop, folders) and the Flyer layout editor go through these functions, which keeps the rules
 * for collisions, folders and pages in one tested place.
 */
object WorkspaceEngine {

    // ───────────── Queries ─────────────

    fun itemsOf(layout: HomeLayout, surface: Surface): List<WorkspaceItem> = when (surface) {
        is Surface.Page -> layout.pages.getOrNull(surface.index)?.items.orEmpty()
        Surface.Dock -> layout.dock
        Surface.Desktop -> layout.desktop.items
        is Surface.Folder -> emptyList()
    }

    fun find(layout: HomeLayout, id: String): Pair<Surface, WorkspaceItem>? {
        layout.pages.forEachIndexed { index, page ->
            page.items.firstOrNull { it.id == id }?.let { return Surface.Page(index) to it }
        }
        layout.dock.firstOrNull { it.id == id }?.let { return Surface.Dock to it }
        layout.desktop.items.firstOrNull { it.id == id }?.let { return Surface.Desktop to it }
        return null
    }

    fun findFolder(layout: HomeLayout, id: String): FolderItem? = find(layout, id)?.second as? FolderItem

    /** All app keys placed somewhere on the launcher (including folders). */
    fun placedAppKeys(layout: HomeLayout): Set<String> = buildSet {
        fun visit(item: WorkspaceItem) {
            when (item) {
                is AppItem -> add(item.appKey)
                is FolderItem -> addAll(item.apps)
                else -> Unit
            }
        }
        layout.pages.forEach { p -> p.items.forEach(::visit) }
        layout.dock.forEach(::visit)
        layout.desktop.items.forEach(::visit)
    }

    fun isOccupied(items: List<WorkspaceItem>, cell: Cell, ignoreId: String? = null): Boolean =
        items.any { it.id != ignoreId && it.cell.overlaps(cell) }

    fun itemAt(items: List<WorkspaceItem>, x: Int, y: Int, ignoreId: String? = null): WorkspaceItem? =
        items.firstOrNull { it.id != ignoreId && it.cell.overlaps(Cell(x, y, 1, 1)) }

    /** Finds the free cell rectangle closest to [near] (or the first one in reading order). */
    fun findFreeCell(
        items: List<WorkspaceItem>,
        dims: GridDims,
        spanX: Int,
        spanY: Int,
        near: Cell? = null,
        ignoreId: String? = null,
    ): Cell? {
        val sx = spanX.coerceIn(1, dims.columns)
        val sy = spanY.coerceIn(1, dims.rows)
        var best: Cell? = null
        var bestDist = Int.MAX_VALUE
        for (y in 0..dims.rows - sy) {
            for (x in 0..dims.columns - sx) {
                val candidate = Cell(x, y, sx, sy)
                if (isOccupied(items, candidate, ignoreId)) continue
                if (near == null) return candidate
                val d = abs(near.x - x) * abs(near.x - x) + abs(near.y - y) * abs(near.y - y)
                if (d < bestDist) {
                    bestDist = d
                    best = candidate
                }
            }
        }
        return best
    }

    // ───────────── Basic mutations ─────────────

    fun mapSurface(layout: HomeLayout, surface: Surface, transform: (List<WorkspaceItem>) -> List<WorkspaceItem>): HomeLayout =
        when (surface) {
            is Surface.Page -> {
                if (surface.index !in layout.pages.indices) layout
                else layout.copy(pages = layout.pages.mapIndexed { i, p -> if (i == surface.index) p.copy(items = transform(p.items)) else p })
            }
            Surface.Dock -> layout.copy(dock = renumberDock(transform(layout.dock)))
            Surface.Desktop -> layout.copy(desktop = layout.desktop.copy(items = transform(layout.desktop.items)))
            is Surface.Folder -> layout
        }

    fun updateItem(layout: HomeLayout, id: String, transform: (WorkspaceItem) -> WorkspaceItem): HomeLayout {
        val (surface, _) = find(layout, id) ?: return layout
        return mapSurface(layout, surface) { items -> items.map { if (it.id == id) transform(it) else it } }
    }

    // ───────────── Built-in (Flyer) apps are never removable ─────────────

    fun isProtectedKey(key: String) = key.startsWith("builtin:")

    fun isProtected(item: WorkspaceItem) = item is AppItem && isProtectedKey(item.appKey)

    /** Takes an item off its surface so it can be placed elsewhere (moves; not a user removal). */
    private fun detach(layout: HomeLayout, id: String): HomeLayout {
        val (surface, _) = find(layout, id) ?: return layout
        return mapSurface(layout, surface) { items -> items.filterNot { it.id == id } }
    }

    /** Removing a built-in app is refused; removing a folder keeps the built-in apps it contained. */
    fun removeItem(layout: HomeLayout, id: String): HomeLayout {
        val (surface, item) = find(layout, id) ?: return layout
        if (isProtected(item)) return layout
        if (item is FolderItem && item.apps.any(::isProtectedKey)) {
            val kept = normalizeFolder(item.copy(apps = item.apps.filter(::isProtectedKey)))
            return mapSurface(layout, surface) { items -> items.mapNotNull { if (it.id == id) kept else it } }
        }
        return mapSurface(layout, surface) { items -> items.filterNot { it.id == id } }
    }

    /** Removes every occurrence of an app (used when a package is uninstalled; never for built-ins). */
    fun removeApp(layout: HomeLayout, appKey: String): HomeLayout {
        if (isProtectedKey(appKey)) return layout
        fun clean(items: List<WorkspaceItem>): List<WorkspaceItem> = items.mapNotNull { item ->
            when (item) {
                is AppItem -> item.takeIf { it.appKey != appKey }
                is FolderItem -> normalizeFolder(item.copy(apps = item.apps - appKey))
                else -> item
            }
        }
        return layout.copy(
            pages = layout.pages.map { it.copy(items = clean(it.items)) },
            dock = renumberDock(clean(layout.dock)),
            desktop = layout.desktop.copy(
                items = clean(layout.desktop.items),
                taskbarPins = layout.desktop.taskbarPins - appKey,
            ),
            drawerGroups = layout.drawerGroups.map { it.copy(apps = it.apps - appKey) },
        )
    }

    /** A folder with one app becomes that app; an empty folder disappears. */
    fun normalizeFolder(folder: FolderItem): WorkspaceItem? = when (folder.apps.size) {
        0 -> null
        1 -> AppItem(id = folder.id, appKey = folder.apps.first(), cell = folder.cell, free = folder.free)
        else -> folder
    }

    fun renumberDock(items: List<WorkspaceItem>): List<WorkspaceItem> =
        items.sortedBy { it.cell.x }.mapIndexed { i, item -> item.withCell(Cell(i, 0, 1, 1)) }

    // ───────────── Adding ─────────────

    /** Adds an app to the first page (or desktop) with room, creating a page if every page is full. */
    fun addApp(layout: HomeLayout, appKey: String, config: FlyerConfig, preferPage: Int = 0, desktop: Boolean = false): HomeLayout =
        addItem(layout, AppItem(appKey = appKey), config, preferPage, desktop)

    fun addItem(layout: HomeLayout, item: WorkspaceItem, config: FlyerConfig, preferPage: Int = 0, desktop: Boolean = false): HomeLayout {
        if (desktop) {
            val dims = config.desktopGrid()
            val cell = findFreeCell(layout.desktop.items, dims, item.cell.spanX, item.cell.spanY)
                ?: Cell(0, 0, item.cell.spanX.coerceAtMost(dims.columns), item.cell.spanY.coerceAtMost(dims.rows))
            return mapSurface(layout, Surface.Desktop) { it + item.withCell(cell).withFree(null) }
        }
        val dims = config.homeGrid()
        val order = (listOf(preferPage) + layout.pages.indices.filter { it != preferPage })
            .filter { it in layout.pages.indices }
        for (index in order) {
            val cell = findFreeCell(layout.pages[index].items, dims, item.cell.spanX, item.cell.spanY)
            if (cell != null) return mapSurface(layout, Surface.Page(index)) { it + item.withCell(cell).withFree(null) }
        }
        val cell = Cell(0, 0, item.cell.spanX.coerceAtMost(dims.columns), item.cell.spanY.coerceAtMost(dims.rows))
        return layout.copy(pages = layout.pages + HomePage(items = listOf(item.withCell(cell).withFree(null))))
    }

    // ───────────── Drag & drop ─────────────

    sealed interface DropResult {
        data class Accepted(val layout: HomeLayout, val message: String? = null) : DropResult
        data class Rejected(val reason: String) : DropResult
    }

    /**
     * Places [placement] at [target]. Handles moving between surfaces, collisions (nearest free
     * cell), folder creation (app dropped on app), adding to folders and dock insertion.
     */
    fun drop(layout: HomeLayout, placement: Placement, target: DropTarget, config: FlyerConfig): DropResult {
        // 1. Resolve the item being placed and remove it from its origin.
        val (item, without) = when (placement) {
            is Placement.Existing -> {
                val found = find(layout, placement.itemId) ?: return DropResult.Rejected("Item no longer exists")
                found.second to detach(layout, placement.itemId)
            }
            is Placement.NewApp -> AppItem(appKey = placement.appKey) to layout
            is Placement.NewItem -> placement.item to layout
            is Placement.FromFolder -> {
                val folder = findFolder(layout, placement.folderId) ?: return DropResult.Rejected("Folder no longer exists")
                val updated = updateFolderApps(layout, folder.id, folder.apps - placement.appKey)
                AppItem(appKey = placement.appKey) to updated
            }
        }

        return when (target) {
            is DropTarget.OntoItem -> dropOnto(without, item, target, config)
            is DropTarget.DockIndex -> dropInDock(without, item, target.index, config)
            is DropTarget.AtCell -> dropAtCell(without, item, target, config)
            is DropTarget.AtFree -> {
                val items = itemsOf(without, target.surface)
                val dims = config.gridFor(target.surface)
                val span = clampSpan(item.cell, dims)
                val cell = target.fallbackCell.copy(spanX = span.spanX, spanY = span.spanY)
                val overlapAllowed = config.home.allowOverlap || target.surface == Surface.Desktop
                val placed = item.withCell(cell).withFree(clampFree(target.pos))
                if (!overlapAllowed && items.any { it.cell.overlaps(cell) && it.free == null }) {
                    // In free mode on a page without overlap, fall back to grid collision rules.
                    dropAtCell(without, item, DropTarget.AtCell(target.surface, cell.x, cell.y), config)
                } else {
                    DropResult.Accepted(ensurePage(without, target.surface).let { l -> mapSurface(l, target.surface) { it + placed } })
                }
            }
        }
    }

    private fun clampFree(p: FreePos) = FreePos(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))

    private fun clampSpan(cell: Cell, dims: GridDims) =
        cell.copy(spanX = cell.spanX.coerceIn(1, dims.columns), spanY = cell.spanY.coerceIn(1, dims.rows))

    private fun ensurePage(layout: HomeLayout, surface: Surface): HomeLayout =
        if (surface is Surface.Page && surface.index >= layout.pages.size) {
            layout.copy(pages = layout.pages + List(surface.index - layout.pages.size + 1) { HomePage() })
        } else layout

    private fun dropAtCell(layout: HomeLayout, item: WorkspaceItem, target: DropTarget.AtCell, config: FlyerConfig): DropResult {
        val l = ensurePage(layout, target.surface)
        val dims = config.gridFor(target.surface)
        val items = itemsOf(l, target.surface)
        val span = clampSpan(item.cell, dims)
        val desired = Cell(
            target.x.coerceIn(0, dims.columns - span.spanX),
            target.y.coerceIn(0, dims.rows - span.spanY),
            span.spanX, span.spanY,
        )
        val cell = if (!isOccupied(items, desired)) desired
        else findFreeCell(items, dims, span.spanX, span.spanY, near = desired)
            ?: return DropResult.Rejected("No room on this page")
        val placed = item.withCell(cell).withFree(null)
        return DropResult.Accepted(mapSurface(l, target.surface) { it + placed })
    }

    private fun dropOnto(layout: HomeLayout, item: WorkspaceItem, target: DropTarget.OntoItem, config: FlyerConfig): DropResult {
        val targetItem = itemsOf(layout, target.surface).firstOrNull { it.id == target.targetId }
            ?: return DropResult.Rejected("Target moved")
        val draggedApps: List<String> = when (item) {
            is AppItem -> listOf(item.appKey)
            is FolderItem -> item.apps
            else -> return DropResult.Rejected("Only apps and folders can be grouped")
        }
        return when (targetItem) {
            is FolderItem -> {
                val merged = (targetItem.apps + draggedApps).distinct()
                DropResult.Accepted(updateFolderApps(layout, targetItem.id, merged), "Added to ${targetItem.name}")
            }
            is AppItem -> {
                val apps = (listOf(targetItem.appKey) + draggedApps).distinct()
                if (apps.size < 2) return DropResult.Rejected("Already here")
                val folder = FolderItem(name = "Folder", apps = apps, cell = targetItem.cell, free = targetItem.free)
                DropResult.Accepted(
                    mapSurface(layout, target.surface) { items -> items.map { if (it.id == targetItem.id) folder else it } },
                    "Folder created",
                )
            }
            else -> DropResult.Rejected("Can't group with this item")
        }
    }

    private fun dropInDock(layout: HomeLayout, item: WorkspaceItem, index: Int, config: FlyerConfig): DropResult {
        if (item is WidgetItem || item is BuiltInWidgetItem) return DropResult.Rejected("Widgets can't go in the dock")
        val dock = layout.dock.sortedBy { it.cell.x }
        if (dock.size >= config.dock.slots) return DropResult.Rejected("The dock is full")
        val i = index.coerceIn(0, dock.size)
        val newDock = dock.toMutableList().apply { add(i, item.withFree(null)) }
        return DropResult.Accepted(layout.copy(dock = renumberDock(newDock.mapIndexed { n, it -> it.withCell(Cell(n, 0)) })))
    }

    // ───────────── Folders ─────────────

    fun updateFolderApps(layout: HomeLayout, folderId: String, apps: List<String>): HomeLayout {
        val (surface, folder) = find(layout, folderId) ?: return layout
        if (folder !is FolderItem) return layout
        val normalized = normalizeFolder(folder.copy(apps = apps.distinct()))
        return mapSurface(layout, surface) { items ->
            items.mapNotNull { if (it.id == folderId) normalized else it }
        }
    }

    fun renameFolder(layout: HomeLayout, folderId: String, name: String): HomeLayout =
        updateItem(layout, folderId) { if (it is FolderItem) it.copy(name = name.take(40)) else it }

    fun reorderInFolder(layout: HomeLayout, folderId: String, from: Int, to: Int): HomeLayout {
        val folder = findFolder(layout, folderId) ?: return layout
        if (from !in folder.apps.indices) return layout
        val list = folder.apps.toMutableList()
        val app = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), app)
        return updateFolderApps(layout, folderId, list)
    }

    // ───────────── Pages ─────────────

    fun addPage(layout: HomeLayout, at: Int = layout.pages.size): HomeLayout =
        layout.copy(pages = layout.pages.toMutableList().apply { add(at.coerceIn(0, size), HomePage()) })

    /** Deletes a page; built-in apps on it move to another page (refused without [config] to place them). */
    fun removePage(layout: HomeLayout, index: Int, config: FlyerConfig? = null): HomeLayout {
        if (layout.pages.size <= 1 || index !in layout.pages.indices) return layout
        val rescued = layout.pages[index].items.flatMap { item ->
            when (item) {
                is AppItem -> listOf(item.appKey).filter(::isProtectedKey)
                is FolderItem -> item.apps.filter(::isProtectedKey)
                else -> emptyList()
            }
        }
        if (rescued.isNotEmpty() && config == null) return layout
        var out = layout.copy(pages = layout.pages.filterIndexed { i, _ -> i != index })
        rescued.forEach { key -> out = addApp(out, key, config!!) }
        return out
    }

    fun movePage(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        if (from !in layout.pages.indices) return layout
        val list = layout.pages.toMutableList()
        val page = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), page)
        return layout.copy(pages = list)
    }

    /** Removes empty trailing pages (keeps at least one). */
    fun trimEmptyPages(layout: HomeLayout, keep: Int = 1): HomeLayout {
        val pages = layout.pages.toMutableList()
        while (pages.size > keep && pages.last().items.isEmpty()) pages.removeAt(pages.lastIndex)
        return layout.copy(pages = pages)
    }

    // ───────────── Free positioning helpers ─────────────

    /** Converts every item on home pages to free positions derived from their cells (non destructive). */
    fun cellToFree(cell: Cell, dims: GridDims) = FreePos(cell.x / dims.columns.toFloat(), cell.y / dims.rows.toFloat())

    fun freeToCell(pos: FreePos, span: Cell, dims: GridDims): Cell {
        val x = (pos.x * dims.columns + 0.5f).toInt().coerceIn(0, (dims.columns - span.spanX).coerceAtLeast(0))
        val y = (pos.y * dims.rows + 0.5f).toInt().coerceIn(0, (dims.rows - span.spanY).coerceAtLeast(0))
        return Cell(x, y, span.spanX, span.spanY)
    }

    /** Lays items in reading order on a grid (used by "auto arrange" on the desktop). */
    fun autoArrange(items: List<WorkspaceItem>, dims: GridDims): List<WorkspaceItem> {
        val placed = mutableListOf<WorkspaceItem>()
        val sorted = items.sortedWith(compareBy({ it.cell.y }, { it.cell.x }))
        for (item in sorted) {
            val cell = findFreeCell(placed, dims, item.cell.spanX, item.cell.spanY) ?: item.cell
            placed += item.withCell(cell).withFree(null)
        }
        return placed
    }

    fun labelFor(item: WorkspaceItem): String = when (item) {
        is AppItem -> "App"
        is FolderItem -> "Folder ${item.name}"
        is WidgetItem -> "Widget"
        is BuiltInWidgetItem -> item.kind.name.lowercase().replaceFirstChar { it.uppercase() }
        is ShortcutItem -> "Shortcut ${item.label}"
    }
}
