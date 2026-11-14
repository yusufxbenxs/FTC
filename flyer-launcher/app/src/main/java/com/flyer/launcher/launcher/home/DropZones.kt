package com.flyer.launcher.launcher.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.workspace.DropTarget
import com.flyer.launcher.launcher.drag.DragSession
import com.flyer.launcher.launcher.drag.DropCandidate
import com.flyer.launcher.launcher.drag.DropZone
import com.flyer.launcher.launcher.drag.SpecialDrop
import com.flyer.launcher.launcher.render.GridGeometry

/** Drop zone for a grid surface (home page or desktop). Fields are refreshed on recomposition. */
class SurfaceDropZone(val surface: Surface) : DropZone {
    var rootBounds: Rect = Rect.Zero
    var geometry: GridGeometry? = null
    var items: List<WorkspaceItem> = emptyList()
    var snapPx: Float = 0f
    var allowGrouping: Boolean = true

    override val bounds: Rect get() = rootBounds

    override fun resolve(session: DragSession, pointer: Offset, visualTopLeft: Offset): DropCandidate? {
        val g = geometry ?: return null
        val origin = rootBounds.topLeft
        val local = pointer - origin
        val localTopLeft = visualTopLeft - origin
        val visual = session.visual
        val draggedId = session.draggedItemId

        if (allowGrouping && (visual is AppItem || visual is FolderItem)) {
            val target = items.firstOrNull { it.id != draggedId && (it is AppItem || it is FolderItem) && g.itemRect(it).contains(local) }
            if (target != null && !(visual is FolderItem && target is AppItem)) {
                val r = g.itemRect(target)
                val inner = Rect(r.left + r.width * 0.2f, r.top + r.height * 0.15f, r.right - r.width * 0.2f, r.bottom - r.height * 0.3f)
                if (inner.contains(local)) {
                    return DropCandidate(DropTarget.OntoItem(surface, target.id), highlight = r.translate(origin), ontoItem = true)
                }
            }
        }

        val spanX = visual.cell.spanX.coerceIn(1, g.columns)
        val spanY = visual.cell.spanY.coerceIn(1, g.rows)
        if (g.freePositioning) {
            val pos = g.freeFor(localTopLeft, spanX, spanY, snapPx)
            val rect = g.freeRect(pos, Cell(0, 0, spanX, spanY))
            val fallback = g.cellForTopLeft(localTopLeft, spanX, spanY)
            return DropCandidate(DropTarget.AtFree(surface, pos, fallback), highlight = rect.translate(origin))
        }
        val cell = g.cellForTopLeft(localTopLeft, spanX, spanY)
        return DropCandidate(DropTarget.AtCell(surface, cell.x, cell.y), highlight = g.cellRect(cell).translate(origin))
    }
}

/** Drop zone for the dock: inserts between icons or groups onto an icon. */
class DockDropZone : DropZone {
    var rootBounds: Rect = Rect.Zero
    var vertical: Boolean = false
    var slots: Int = 5
    val itemRects = LinkedHashMap<String, Rect>()
    var items: List<WorkspaceItem> = emptyList()

    override val bounds: Rect get() = rootBounds
    override val priority: Int get() = 1

    override fun resolve(session: DragSession, pointer: Offset, visualTopLeft: Offset): DropCandidate? {
        val visual = session.visual
        if (visual !is AppItem && visual !is FolderItem && visual !is com.flyer.launcher.core.model.ShortcutItem) return null
        val draggedId = session.draggedItemId
        val others = items.filter { it.id != draggedId }
        for (it in others) {
            val r = itemRects[it.id] ?: continue
            val inner = Rect(r.left + r.width * 0.22f, r.top + r.height * 0.22f, r.right - r.width * 0.22f, r.bottom - r.height * 0.22f)
            if (inner.contains(pointer) && (it is AppItem || it is FolderItem) && visual !is com.flyer.launcher.core.model.ShortcutItem &&
                !(visual is FolderItem && it is AppItem)
            ) {
                return DropCandidate(DropTarget.OntoItem(Surface.Dock, it.id), highlight = r, ontoItem = true)
            }
        }
        if (others.size >= slots) return null
        val index = others.count { other ->
            val r = itemRects[other.id] ?: return@count false
            if (vertical) r.center.y < pointer.y else r.center.x < pointer.x
        }
        return DropCandidate(DropTarget.DockIndex(index), highlight = rootBounds)
    }
}

/** The "Remove / App info / Uninstall" targets shown at the top while dragging. */
class ActionDropZone(val action: SpecialDrop) : DropZone {
    var rootBounds: Rect = Rect.Zero
    override val bounds: Rect get() = rootBounds
    override val priority: Int get() = 10
    override fun resolve(session: DragSession, pointer: Offset, visualTopLeft: Offset) =
        DropCandidate(special = action, highlight = rootBounds)
}
