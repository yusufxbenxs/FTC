package com.flyer.launcher.flyer.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.config.TaskbarPosition
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.workspace.DropTarget
import com.flyer.launcher.flyer.preview.PreviewBackground
import com.flyer.launcher.launcher.home.GridOverlay
import com.flyer.launcher.launcher.home.HomeFrame
import com.flyer.launcher.launcher.home.PageIndicator
import com.flyer.launcher.launcher.home.StaticItemVisual
import com.flyer.launcher.launcher.home.WidgetResizeFrame
import com.flyer.launcher.launcher.home.WorkspaceSurface
import com.flyer.launcher.launcher.home.fittedIconSize
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.GridGeometry
import com.flyer.launcher.launcher.render.SearchBarWidget
import com.flyer.launcher.flyer.preview.StaticDock
import com.flyer.launcher.flyer.preview.StaticTaskbar
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.widgets.WidgetEngine
import kotlin.math.abs
import kotlin.math.roundToInt

/** What is selected in the live editor. */
@Immutable
sealed interface EditorSelection {
    val title: String

    data class Item(val surface: Surface, val id: String) : EditorSelection { override val title = "Item" }
    data object Workspace : EditorSelection { override val title = "Workspace & background" }
    data object Dock : EditorSelection { override val title = "Dock" }
    data object PageIndicator : EditorSelection { override val title = "Page indicator" }
    data object SearchBar : EditorSelection { override val title = "Search bar" }
    data object Desktop : EditorSelection { override val title = "Desktop" }
    data object Taskbar : EditorSelection { override val title = "Taskbar" }
}

/** Callbacks from the canvas to the editor screen (which owns the session). */
interface EditorCanvasCallbacks {
    fun select(selection: EditorSelection)
    fun move(surface: Surface, itemId: String, target: DropTarget)
    fun resize(surface: Surface, itemId: String, cell: Cell)
    fun widgetLimits(item: WorkspaceItem, geometry: GridGeometry): WidgetEngine.SpanLimits?
}

private data class Guides(val x: Float? = null, val y: Float? = null)

/** Snaps a free-positioned rect to edges/centers of other items and the content area. */
private fun snapToGuides(rect: Rect, others: List<Rect>, geometry: GridGeometry, threshold: Float): Pair<Offset, Guides> {
    val content = Rect(geometry.left, geometry.top, geometry.left + geometry.contentWidth, geometry.top + geometry.contentHeight)
    val xs = others.flatMap { listOf(it.left, it.right, it.center.x) } + listOf(content.left, content.right, content.center.x)
    val ys = others.flatMap { listOf(it.top, it.bottom, it.center.y) } + listOf(content.top, content.bottom, content.center.y)
    var dx = 0f
    var gx: Float? = null
    var best = threshold
    for (candidate in xs) for (edge in listOf(rect.left, rect.right, rect.center.x)) {
        val d = candidate - edge
        if (abs(d) < best) { best = abs(d); dx = d; gx = candidate }
    }
    var dy = 0f
    var gy: Float? = null
    best = threshold
    for (candidate in ys) for (edge in listOf(rect.top, rect.bottom, rect.center.y)) {
        val d = candidate - edge
        if (abs(d) < best) { best = abs(d); dy = d; gy = candidate }
    }
    return Offset(dx, dy) to Guides(gx, gy)
}

/**
 * The editable surface: identical geometry and visuals to the live launcher, with selection,
 * drag (grid snapping or free positioning with alignment guides) and resize handles.
 */
@Composable
fun EditorSurface(
    surface: Surface,
    items: List<WorkspaceItem>,
    config: FlyerConfig,
    columns: Int,
    rows: Int,
    freePositioning: Boolean,
    marginsDp: FloatArray?,
    selection: EditorSelection?,
    uiScale: Float,
    showGrid: Boolean,
    showGuides: Boolean,
    callbacks: EditorCanvasCallbacks,
    labelColor: Color? = null,
    iconSizeDp: Int? = null,
    emptySelection: EditorSelection,
) {
    val density = LocalDensity.current
    var dragRect by remember { mutableStateOf<Rect?>(null) }
    var guides by remember { mutableStateOf(Guides()) }
    val stroke = (2f / uiScale).dp
    val selectedId = (selection as? EditorSelection.Item)?.takeIf { it.surface == surface }?.id
    val cb = rememberUpdatedState(callbacks)

    WorkspaceSurface(
        items = items, columns = columns, rows = rows,
        home = if (surface is Surface.Page) config.home else null,
        freePositioning = freePositioning, marginsDp = marginsDp,
        modifier = Modifier.pointerInput(emptySelection) { detectTapGestures { cb.value.select(emptySelection) } },
        underlay = { geometry ->
            if (showGrid) {
                GridOverlay(geometry, color = Color.White.copy(alpha = 0.28f), highlight = dragRect)
            } else if (dragRect != null) {
                GridOverlay(geometry.copy(columns = 0, rows = 0), highlight = dragRect)
            }
        },
        overlay = { geometry ->
            if (showGuides) GuideLines(guides, uiScale)
            val selected = items.firstOrNull { it.id == selectedId }
            if (selected != null && (selected is WidgetItem || selected is BuiltInWidgetItem) && dragRect == null && !(freePositioning && selected.free != null)) {
                WidgetResizeFrame(
                    selected, geometry, cb.value.widgetLimits(selected, geometry),
                    respectLimits = config.widgets.respectProviderLimits,
                    onResize = { cell -> cb.value.resize(surface, selected.id, cell) },
                    onDone = { cb.value.select(emptySelection) },
                )
            }
        },
    ) { item, geometry, rect ->
        var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
        val isSelected = item.id == selectedId
        val itemState = rememberUpdatedState(item)
        val rectState = rememberUpdatedState(rect)
        val othersState = rememberUpdatedState(items.filter { it.id != item.id }.map { geometry.itemRect(it) })
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .let { if (isSelected) it.border(stroke, Color(0xFF7AE7FF), RoundedCornerShape((12f / uiScale).dp)) else it }
                .pointerInput(item.id) { detectTapGestures { cb.value.select(EditorSelection.Item(surface, itemState.value.id)) } }
                .pointerInput(item.id, geometry, freePositioning) {
                    detectDragGestures(
                        onDragStart = { cb.value.select(EditorSelection.Item(surface, itemState.value.id)) },
                        onDragCancel = { offset = Offset.Zero; dragRect = null; guides = Guides() },
                        onDragEnd = {
                            val r = rectState.value
                            val it = itemState.value
                            val topLeft = r.topLeft + offset
                            val target = if (freePositioning) {
                                val snap = with(density) { config.home.freeSnapDp.dp.toPx() }
                                DropTarget.AtFree(
                                    surface,
                                    geometry.freeFor(topLeft, it.cell.spanX, it.cell.spanY, if (guides.x != null || guides.y != null) 0f else snap),
                                    geometry.cellForTopLeft(topLeft, it.cell.spanX, it.cell.spanY),
                                )
                            } else {
                                val c = geometry.cellForTopLeft(topLeft, it.cell.spanX, it.cell.spanY)
                                DropTarget.AtCell(surface, c.x, c.y)
                            }
                            cb.value.move(surface, it.id, target)
                            offset = Offset.Zero
                            dragRect = null
                            guides = Guides()
                        },
                    ) { change, amount ->
                        change.consume()
                        offset += amount
                        val r = rectState.value
                        val moved = r.translate(offset)
                        if (freePositioning) {
                            if (showGuides) {
                                val (adjust, g) = snapToGuides(moved, othersState.value, geometry, with(density) { 8.dp.toPx() } / uiScale)
                                guides = g
                                dragRect = moved.translate(adjust)
                            } else dragRect = moved
                        } else {
                            val it = itemState.value
                            dragRect = geometry.cellRect(geometry.cellForTopLeft(moved.topLeft, it.cell.spanX, it.cell.spanY))
                        }
                    }
                },
        ) {
            if (item is AppItem && iconSizeDp != null) {
                val size = fittedIconSize(config, geometry.cellWidth, geometry.cellHeight, true, iconSizeDp)
                AppTile(item.appKey, size, true, config.icons.label, style = item.style, labelColor = labelColor)
            } else StaticItemVisual(item, geometry.cellWidth, geometry.cellHeight)
        }
    }
}

@Composable
private fun BoxScope.GuideLines(guides: Guides, uiScale: Float) {
    if (guides.x == null && guides.y == null) return
    Canvas(Modifier.matchParentSize()) {
        val effect = PathEffect.dashPathEffect(floatArrayOf(12f / uiScale, 8f / uiScale))
        val color = Color(0xFFFF7AD9)
        guides.x?.let { drawLine(color, Offset(it, 0f), Offset(it, size.height), strokeWidth = 2f / uiScale, pathEffect = effect) }
        guides.y?.let { drawLine(color, Offset(0f, it), Offset(size.width, it), strokeWidth = 2f / uiScale, pathEffect = effect) }
    }
}

/** The full home screen in editor mode (workspace, indicator, search bar and dock are selectable). */
@Composable
fun EditorHome(
    config: FlyerConfig,
    layout: HomeLayout,
    page: Int,
    insets: PaddingValues,
    selection: EditorSelection?,
    uiScale: Float,
    showGrid: Boolean,
    showGuides: Boolean,
    callbacks: EditorCanvasCallbacks,
) {
    val stroke = (2f / uiScale).dp
    val highlight = Color(0xFF7AE7FF)
    fun Modifier.selectable(sel: EditorSelection) = this
        .let { if (selection == sel) it.border(stroke, highlight, RoundedCornerShape((14f / uiScale).dp)) else it }
        .pointerInput(sel) { detectTapGestures { callbacks.select(sel) } }

    Box(Modifier.fillMaxSize()) {
        PreviewBackground(config)
        HomeFrame(
            config = config,
            insets = insets,
            pages = { m ->
                Box(m.let { if (selection == EditorSelection.Workspace) it.border(stroke, highlight.copy(alpha = 0.6f)) else it }) {
                    EditorSurface(
                        surface = Surface.Page(page),
                        items = layout.pages.getOrNull(page)?.items.orEmpty(),
                        config = config, columns = config.home.columns, rows = config.home.rows,
                        freePositioning = config.home.positioning == Positioning.FREE, marginsDp = null,
                        selection = selection, uiScale = uiScale, showGrid = showGrid, showGuides = showGuides,
                        callbacks = callbacks, emptySelection = EditorSelection.Workspace,
                    )
                }
            },
            indicator = { Box(Modifier.fillMaxWidth().selectable(EditorSelection.PageIndicator)) { PageIndicator(config, layout.pages.size, page.toFloat()) } },
            searchBar = {
                Box(Modifier.fillMaxWidth().selectable(EditorSelection.SearchBar)) { SearchBarWidget(Modifier.height(config.search.barHeightDp.dp + 8.dp)) }
            },
            dock = { vertical ->
                Box(Modifier.selectable(EditorSelection.Dock)) { StaticDock(config, layout, vertical) }
            },
        )
    }
}

/** The desktop in editor mode. */
@Composable
fun EditorDesktop(
    config: FlyerConfig,
    layout: HomeLayout,
    insets: PaddingValues,
    selection: EditorSelection?,
    uiScale: Float,
    showGrid: Boolean,
    showGuides: Boolean,
    callbacks: EditorCanvasCallbacks,
) {
    val d = config.desktop
    val stroke = (2f / uiScale).dp
    Box(Modifier.fillMaxSize()) {
        PreviewBackground(config)
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(insets.calculateTopPadding()))
            val taskbar: @Composable () -> Unit = {
                Box(
                    Modifier.fillMaxWidth()
                        .let { if (selection == EditorSelection.Taskbar) it.border(stroke, Color(0xFF7AE7FF)) else it }
                        .pointerInput(Unit) { detectTapGestures { callbacks.select(EditorSelection.Taskbar) } },
                ) { StaticTaskbar(config, layout) }
            }
            if (d.taskbarPosition == TaskbarPosition.TOP) taskbar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EditorSurface(
                    surface = Surface.Desktop, items = layout.desktop.items, config = config,
                    columns = d.columns, rows = d.rows, freePositioning = !d.snapToGrid,
                    marginsDp = floatArrayOf(12f, 12f, 12f, 12f), selection = selection, uiScale = uiScale,
                    showGrid = showGrid, showGuides = showGuides, callbacks = callbacks,
                    labelColor = d.labelColor.color, iconSizeDp = d.iconSizeDp, emptySelection = EditorSelection.Desktop,
                )
            }
            if (d.taskbarPosition == TaskbarPosition.BOTTOM) taskbar()
            Spacer(Modifier.height(insets.calculateBottomPadding()))
        }
    }
}
