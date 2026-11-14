package com.flyer.launcher.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.flyer.launcher.core.config.GestureType
import kotlin.math.abs
import kotlin.math.hypot

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Remembers the latest layout coordinates of an element (for root-space conversions). */
@Stable
class CoordsHolder {
    var coords: LayoutCoordinates? = null

    fun toRoot(local: Offset): Offset = coords?.takeIf { it.isAttached }?.localToRoot(local) ?: local
    fun boundsInRoot(): Rect = coords?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
}

fun Modifier.trackCoords(holder: CoordsHolder): Modifier = onGloballyPositioned { holder.coords = it }

/**
 * Lets the home-level gesture detector know that a gesture started on an item, so long-press and
 * double-tap on *empty space* are not fired for it.
 */
@Stable
class GestureArbiter {
    var lastItemDownTime: Long = -1
}

interface ItemGestureCallbacks {
    fun onTap(rootPos: Offset)
    fun onLongPress(rootPos: Offset)
    fun onSecondaryClick(rootPos: Offset)
    fun onPressChanged(pressed: Boolean) = Unit
}

/**
 * Tap / long-press / right-click detection for launcher items. After a long press the gesture is
 * handed over to the root drag monitor ([com.flyer.launcher.launcher.drag.DragController]), which
 * keeps tracking the pointer even if this item leaves the composition (e.g. the drawer closes).
 */
fun Modifier.itemGestures(
    holder: CoordsHolder,
    arbiter: GestureArbiter?,
    enabled: Boolean = true,
    doubleClickToOpen: Boolean = false,
    callbacks: ItemGestureCallbacks,
): Modifier = if (!enabled) this else this.trackCoords(holder).pointerInput(callbacks, doubleClickToOpen) {
    var lastTapTime = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        arbiter?.lastItemDownTime = down.uptimeMillis
        if (currentEvent.buttons.isSecondaryPressed) {
            down.consume()
            callbacks.onSecondaryClick(holder.toRoot(down.position))
            return@awaitEachGesture
        }
        callbacks.onPressChanged(true)
        val slop = viewConfiguration.touchSlop
        var outcome = 0 // 0 = timeout (long press), 1 = tap, 2 = cancelled
        val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.size > 1) return@withTimeoutOrNull 2
                val change = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull 2
                if (change.changedToUp()) {
                    if (change.isConsumed) return@withTimeoutOrNull 2
                    change.consume()
                    return@withTimeoutOrNull 1
                }
                if (change.isConsumed || (change.position - down.position).getDistance() > slop) return@withTimeoutOrNull 2
            }
            @Suppress("UNREACHABLE_CODE") 2
        }
        outcome = result ?: 0
        callbacks.onPressChanged(false)
        when (outcome) {
            1 -> {
                val now = down.uptimeMillis
                if (doubleClickToOpen) {
                    if (now - lastTapTime < viewConfiguration.doubleTapTimeoutMillis) {
                        lastTapTime = 0
                        callbacks.onTap(holder.toRoot(down.position))
                    } else lastTapTime = now
                } else callbacks.onTap(holder.toRoot(down.position))
            }
            0 -> callbacks.onLongPress(holder.toRoot(down.position))
            else -> Unit
        }
    }
}

/** Home-screen (empty space) gestures: swipes, double tap, long press, pinch, two-finger swipes. */
fun Modifier.homeGestures(
    enabled: Boolean,
    swipeThresholdPx: Float,
    doubleTapTimeoutMs: Long,
    arbiter: GestureArbiter,
    onGesture: (GestureType, Offset) -> Unit,
): Modifier = if (!enabled) this else pointerInput(swipeThresholdPx, doubleTapTimeoutMs) {
    var lastTapUp = 0L
    var lastTapPos = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        val onItem = arbiter.lastItemDownTime == down.uptimeMillis
        val start = down.position
        var fired = false
        var maxPointers = 1
        var initialSpan = 0f
        var initialCentroid = Offset.Zero
        var moved = false
        var consumedByChild = false

        val firstPhase = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val pressed = ev.changes.filter { it.pressed }
                if (ev.changes.any { it.isConsumed }) consumedByChild = true
                if (pressed.size >= 2) return@withTimeoutOrNull "multi"
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "end"
                if (c.changedToUp()) return@withTimeoutOrNull "up"
                if ((c.position - start).getDistance() > viewConfiguration.touchSlop) { moved = true; return@withTimeoutOrNull "move" }
            }
            @Suppress("UNREACHABLE_CODE") "end"
        }

        if (firstPhase == null) {
            // Long press without movement.
            if (!onItem && !consumedByChild) {
                onGesture(GestureType.LONG_PRESS_EMPTY, start)
                fired = true
            }
        } else if (firstPhase == "up") {
            if (!onItem && !consumedByChild) {
                val t = currentEvent.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                if (t - lastTapUp < doubleTapTimeoutMs && (start - lastTapPos).getDistance() < swipeThresholdPx) {
                    onGesture(GestureType.DOUBLE_TAP, start)
                    lastTapUp = 0
                } else {
                    lastTapUp = t
                    lastTapPos = start
                }
            }
            return@awaitEachGesture
        }

        // Track the rest of the gesture for swipes / pinches.
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val pressed = ev.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (fired) continue
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val span = hypot(a.x - b.x, a.y - b.y)
                val centroid = (a + b) / 2f
                if (maxPointers < 2) {
                    maxPointers = 2
                    initialSpan = span
                    initialCentroid = centroid
                    continue
                }
                if (initialSpan > 0f) {
                    val ratio = span / initialSpan
                    val dy = centroid.y - initialCentroid.y
                    when {
                        ratio < 0.65f -> { onGesture(GestureType.PINCH_IN, centroid); fired = true }
                        ratio > 1.5f -> { onGesture(GestureType.PINCH_OUT, centroid); fired = true }
                        abs(dy) > swipeThresholdPx && abs(ratio - 1f) < 0.25f -> {
                            onGesture(if (dy < 0) GestureType.TWO_FINGER_SWIPE_UP else GestureType.TWO_FINGER_SWIPE_DOWN, centroid)
                            fired = true
                        }
                    }
                }
                if (fired) ev.changes.forEach { it.consume() }
                continue
            }
            if (maxPointers >= 2) continue
            val c = pressed.first()
            if (c.isConsumed) { consumedByChild = true; continue }
            val d = c.position - start
            if (!consumedByChild && abs(d.y) > swipeThresholdPx && abs(d.y) > abs(d.x) * 1.6f) {
                onGesture(if (d.y < 0) GestureType.SWIPE_UP else GestureType.SWIPE_DOWN, start)
                fired = true
            }
        }
    }
}

/** Mouse wheel / touchpad scrolling, used for page switching in desktop-style interaction. */
fun Modifier.onMouseWheel(onScroll: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var accumulated = 0f
        var lastTime = 0L
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.type != PointerEventType.Scroll) continue
            val change = ev.changes.firstOrNull() ?: continue
            val delta = change.scrollDelta.y + change.scrollDelta.x
            if (change.uptimeMillis - lastTime > 350) accumulated = 0f
            accumulated += delta
            if (abs(accumulated) >= 1f) {
                onScroll(accumulated)
                accumulated = 0f
                lastTime = change.uptimeMillis
            }
            ev.changes.forEach { it.consume() }
        }
    }
}
