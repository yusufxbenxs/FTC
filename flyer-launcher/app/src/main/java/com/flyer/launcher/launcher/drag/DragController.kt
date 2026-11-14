package com.flyer.launcher.launcher.drag

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.workspace.DropTarget
import com.flyer.launcher.core.workspace.Placement

enum class SpecialDrop { REMOVE, APP_INFO, UNINSTALL, HIDE, PIN_TASKBAR }

@Immutable
data class DropCandidate(
    val target: DropTarget? = null,
    val special: SpecialDrop? = null,
    /** Rectangle (root coordinates) to highlight as the landing spot. */
    val highlight: Rect? = null,
    /** True when dropping onto another item (folder creation / add to folder). */
    val ontoItem: Boolean = false,
)

@Immutable
data class DragSession(
    val placement: Placement,
    /** What is drawn under the finger. */
    val visual: WorkspaceItem,
    val source: Surface?,
    val sizePx: Size,
    val touchOffset: Offset,
    val start: Offset,
    val started: Boolean = false,
) {
    val draggedItemId: String? get() = (placement as? Placement.Existing)?.itemId
}

/** A region that accepts drops. Registered by pages, the dock and the drag action bar. */
interface DropZone {
    val bounds: Rect
    /** Higher wins when zones overlap (the action bar sits above pages). */
    val priority: Int get() = 0
    fun resolve(session: DragSession, pointer: Offset, visualTopLeft: Offset): DropCandidate?
}

/**
 * Coordinates drag & drop across every launcher surface. Items only *start* a drag (on long
 * press); from then on a root-level pointer monitor feeds this controller, so a drag survives the
 * source surface disappearing (drawer or folder closing).
 */
@Stable
class DragController {
    var session by mutableStateOf<DragSession?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var candidate by mutableStateOf<DropCandidate?>(null)
        private set

    val isDragging: Boolean get() = session?.started == true

    private val zones = LinkedHashMap<String, DropZone>()

    /** Called once the finger actually moves after a long press (close menus, drawers…). */
    var onDragStarted: ((DragSession) -> Unit)? = null

    /** Called on release with the resolved candidate (null = cancelled / nowhere). */
    var onDrop: ((DragSession, DropCandidate?) -> Unit)? = null

    /** Called when a long press is released without moving (menu stays open). */
    var onPendingReleased: ((DragSession) -> Unit)? = null

    fun register(id: String, zone: DropZone) {
        zones[id] = zone
    }

    fun unregister(id: String) {
        zones.remove(id)
    }

    /** Arms a drag at long-press time; it starts when the pointer moves beyond the slop. */
    fun prepare(placement: Placement, visual: WorkspaceItem, source: Surface?, itemBounds: Rect, pointerRoot: Offset) {
        session = DragSession(
            placement = placement,
            visual = visual,
            source = source,
            sizePx = itemBounds.size,
            touchOffset = pointerRoot - itemBounds.topLeft,
            start = pointerRoot,
        )
        pointer = pointerRoot
        candidate = null
    }

    fun cancel() {
        session = null
        candidate = null
    }

    fun visualTopLeft(): Offset {
        val s = session ?: return Offset.Zero
        return pointer - s.touchOffset
    }

    internal fun move(pos: Offset, slop: Float) {
        val s = session ?: return
        pointer = pos
        if (!s.started) {
            if ((pos - s.start).getDistance() <= slop) return
            val started = s.copy(started = true)
            session = started
            onDragStarted?.invoke(started)
        }
        candidate = resolve(pos)
    }

    internal fun release(pos: Offset) {
        val s = session ?: return
        pointer = pos
        if (!s.started) {
            session = null
            onPendingReleased?.invoke(s)
            return
        }
        val c = resolve(pos)
        session = null
        candidate = null
        onDrop?.invoke(s, c)
    }

    private fun resolve(pos: Offset): DropCandidate? {
        val s = session ?: return null
        val topLeft = pos - s.touchOffset
        return zones.values
            .filter { it.bounds.contains(pos) }
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { it.resolve(s, pos, topLeft) }
    }
}

/**
 * Root-level pointer monitor. While a drag is armed it observes (and consumes) every pointer
 * change in the Initial pass so the pager and items don't react to the dragging finger.
 */
fun Modifier.dragMonitor(controller: DragController): Modifier = pointerInput(controller) {
    val slop = viewConfiguration.touchSlop * 1.5f
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (controller.session == null) continue
            val change = ev.changes.firstOrNull() ?: continue
            change.consume()
            ev.changes.forEach { it.consume() }
            if (change.changedToUp() || !change.pressed) controller.release(change.position)
            else controller.move(change.position, slop)
        }
    }
}
