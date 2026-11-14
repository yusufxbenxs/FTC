package com.flyer.launcher.core.history

/**
 * Bounded undo/redo history of immutable snapshots.
 *
 * Consecutive changes with the same [coalesceKey] inside [coalesceWindowMs] are merged into one
 * entry so dragging a slider produces a single undo step instead of hundreds.
 */
class History<T>(initial: T, private var capacity: Int = 100, private val clock: () -> Long = System::currentTimeMillis) {

    data class Entry<T>(val state: T, val label: String)

    private val undoStack = ArrayDeque<Entry<T>>()
    private val redoStack = ArrayDeque<Entry<T>>()
    private var lastKey: String? = null
    private var lastTime = 0L

    var current: T = initial
        private set
    var currentLabel: String = "Start"
        private set

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val undoLabel get() = if (canUndo) currentLabel else null
    val redoLabel get() = redoStack.lastOrNull()?.label

    /** Labels of the applied changes, most recent first. */
    fun timeline(): List<String> = buildList {
        if (undoStack.isNotEmpty()) add(currentLabel)
        undoStack.reversed().drop(1).forEach { add(it.label) }
    }.take(50)

    fun setCapacity(value: Int) {
        capacity = value.coerceAtLeast(1)
        while (undoStack.size > capacity) undoStack.removeFirst()
    }

    fun push(state: T, label: String, coalesceKey: String? = null, coalesceWindowMs: Long = 800) {
        if (state == current) return
        val now = clock()
        val merge = coalesceKey != null && coalesceKey == lastKey && now - lastTime < coalesceWindowMs && undoStack.isNotEmpty()
        if (!merge) {
            undoStack.addLast(Entry(current, currentLabel))
            while (undoStack.size > capacity) undoStack.removeFirst()
        }
        redoStack.clear()
        current = state
        currentLabel = label
        lastKey = coalesceKey
        lastTime = now
    }

    fun undo(): T? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(Entry(current, currentLabel))
        current = prev.state
        currentLabel = prev.label
        lastKey = null
        return current
    }

    fun redo(): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(Entry(current, currentLabel))
        current = next.state
        currentLabel = next.label
        lastKey = null
        return current
    }

    /** Replaces the baseline (e.g. after apply) and clears history. */
    fun reset(state: T, label: String = "Start") {
        undoStack.clear()
        redoStack.clear()
        current = state
        currentLabel = label
        lastKey = null
    }
}
