package com.flyer.launcher.launcher.home

import android.app.WallpaperManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.GestureType
import com.flyer.launcher.core.config.PageTransition
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.PageController
import com.flyer.launcher.launcher.render.SearchBarWidget
import com.flyer.launcher.launcher.ui.homeGestures
import com.flyer.launcher.launcher.ui.onMouseWheel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INFINITE_MULTIPLIER = 400

/** The live, interactive mobile home screen. */
@Composable
fun HomeScreen(ui: LauncherUiState, config: FlyerConfig, layout: HomeLayout, modifier: Modifier = Modifier) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val realPages = layout.pages.size
    val dragging = ui.drag.isDragging
    // While dragging, an extra empty page at the end lets the user create a page by dropping there.
    val pageCount = realPages + if (dragging) 1 else 0
    val infinite = config.home.infiniteScroll && realPages > 1 && !dragging
    val virtualCount = if (infinite) realPages * INFINITE_MULTIPLIER else pageCount
    val initial = config.home.defaultPage.coerceIn(0, realPages - 1) + if (infinite) realPages * (INFINITE_MULTIPLIER / 2) else 0
    val pager = rememberPagerState(initialPage = initial) { virtualCount }
    val scope = rememberCoroutineScope()

    fun realIndex(v: Int) = if (infinite) ((v % realPages) + realPages) % realPages else v

    DisposableEffect(pager, realPages, infinite) {
        ui.pageController = object : PageController {
            override val pageCount: Int get() = realPages
            override val currentPage: Int get() = realIndex(pager.currentPage)
            override fun goTo(page: Int, animate: Boolean) {
                val target = if (infinite) {
                    val cur = pager.currentPage
                    cur - realIndex(cur) + page.coerceIn(0, realPages - 1)
                } else page.coerceIn(0, pageCount - 1)
                scope.launch {
                    if (animate && config.home.pageTransition != PageTransition.NONE) pager.animateScrollToPage(target) else pager.scrollToPage(target)
                }
            }
        }
        onDispose { ui.pageController = null }
    }

    WallpaperScrollEffect(pager, realPages, infinite, config.home.background.wallpaperScrolling)
    EdgePagingEffect(ui, pager)

    val actions = LocalLauncherActions.current
    val density = LocalDensity.current
    val threshold = with(density) { config.gestures.swipeThresholdDp.dp.toPx() }
    val onGesture: (GestureType, Offset) -> Unit = { type, pos ->
        val action = config.gestures.bindings[type]
        if (action != null && action.type != ActionType.NONE) {
            ui.lastGesturePos = pos
            actions.perform(action)
        }
    }

    HomeFrame(
        config = config,
        insets = insets,
        modifier = modifier,
        pages = { m ->
            Box(
                m.homeGestures(!dragging, threshold, config.gestures.doubleTapTimeoutMs.toLong(), ui.arbiter, onGesture)
                    .onMouseWheel { delta ->
                        val next = if (delta > 0) pager.currentPage + 1 else pager.currentPage - 1
                        if (next in 0 until virtualCount) scope.launch { pager.animateScrollToPage(next) }
                    },
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !dragging,
                    key = { v -> if (infinite) v else layout.pages.getOrNull(v)?.id ?: "new-page" },
                ) { v ->
                    val index = realIndex(v)
                    val offset = (pager.currentPage - v) + pager.currentPageOffsetFraction
                    HomePage(
                        ui = ui,
                        config = config,
                        index = index,
                        items = layout.pages.getOrNull(index)?.items.orEmpty(),
                        modifier = Modifier.pageTransition(config.home.pageTransition, offset),
                    )
                }
            }
        },
        indicator = {
            val position = realIndex(pager.currentPage) + pager.currentPageOffsetFraction
            PageIndicator(config, realPages, position)
        },
        searchBar = { SearchBarWidget(Modifier.height(config.search.barHeightDp.dp + 8.dp)) },
        dock = { vertical ->
            Box(
                Modifier.homeGestures(!dragging, threshold, 300, ui.arbiter) { type, pos ->
                    if (type == GestureType.SWIPE_UP) onGesture(GestureType.SWIPE_UP_DOCK, pos)
                },
            ) { LiveDock(ui, config, layout.dock, vertical) }
        },
    )
}

/** One interactive home page with its drop zone. */
@Composable
fun HomePage(ui: LauncherUiState, config: FlyerConfig, index: Int, items: List<WorkspaceItem>, modifier: Modifier = Modifier) {
    val zone = remember(index) { SurfaceDropZone(Surface.Page(index)) }
    val zoneId = "page-$index"
    DisposableEffect(zoneId) {
        ui.drag.register(zoneId, zone)
        onDispose { ui.drag.unregister(zoneId) }
    }
    val density = LocalDensity.current
    val free = config.home.positioning == Positioning.FREE
    WorkspaceSurface(
        items = items,
        columns = config.home.columns,
        rows = config.home.rows,
        home = config.home,
        freePositioning = free,
        modifier = modifier.onGloballyPositioned { zone.rootBounds = it.boundsInRoot() },
        underlay = { geometry ->
            SideEffect {
                zone.geometry = geometry
                zone.items = items
                zone.snapPx = with(density) { config.home.freeSnapDp.dp.toPx() }
            }
            val candidate = ui.drag.candidate
            if (ui.drag.isDragging && config.home.showGridWhileDragging && !free) {
                val highlight = candidate?.highlight?.takeIf { candidate.target?.let { t -> targetsSurface(t, Surface.Page(index)) } == true }
                    ?.translate(-zone.rootBounds.topLeft)
                GridOverlay(geometry, highlight = highlight)
            } else if (ui.drag.isDragging && free) {
                val highlight = candidate?.highlight?.takeIf { candidate.target?.let { t -> targetsSurface(t, Surface.Page(index)) } == true }
                    ?.translate(-zone.rootBounds.topLeft)
                if (highlight != null) GridOverlay(geometry.copy(columns = 0, rows = 0), highlight = highlight)
            }
            if (config.advanced.showLayoutBounds) LayoutBoundsOverlay(geometry, items)
        },
        overlay = { geometry ->
            val resizing = ui.resizingWidgetId?.let { id -> items.firstOrNull { it.id == id } }
            if (resizing != null) {
                val limits = (resizing as? com.flyer.launcher.core.model.WidgetItem)?.let { w ->
                    ui.core.widgets.providerInfo(w.appWidgetId)?.let { info ->
                        ui.core.widgets.spansFor(
                            info, geometry.cellWidth / density.density, geometry.cellHeight / density.density,
                            geometry.columns, geometry.rows,
                        )
                    }
                }
                WidgetResizeFrame(
                    resizing, geometry, limits, config.widgets.respectProviderLimits,
                    onResize = { cell ->
                        if (!com.flyer.launcher.core.workspace.WorkspaceEngine.isOccupied(items, cell, ignoreId = resizing.id)) {
                            ui.core.editHome("Resize widget") { doc ->
                                doc.copy(layout = com.flyer.launcher.core.workspace.WorkspaceEngine.updateItem(doc.layout, resizing.id) { it.withCell(cell).withFree(null) })
                            }
                        }
                    },
                    onDone = { ui.resizingWidgetId = null },
                )
            }
        },
    ) { item, geometry, _ ->
        LiveItem(item, Surface.Page(index), geometry.cellWidth, geometry.cellHeight, ui)
    }
}

fun targetsSurface(t: com.flyer.launcher.core.workspace.DropTarget, s: Surface): Boolean = when (t) {
    is com.flyer.launcher.core.workspace.DropTarget.AtCell -> t.surface == s
    is com.flyer.launcher.core.workspace.DropTarget.AtFree -> t.surface == s
    is com.flyer.launcher.core.workspace.DropTarget.OntoItem -> t.surface == s
    is com.flyer.launcher.core.workspace.DropTarget.DockIndex -> s == Surface.Dock
}

/** The live dock with its drop zone. */
@Composable
fun LiveDock(ui: LauncherUiState, config: FlyerConfig, items: List<WorkspaceItem>, vertical: Boolean) {
    val zone = remember { DockDropZone() }
    DisposableEffect(Unit) {
        ui.drag.register("dock", zone)
        onDispose { ui.drag.unregister("dock") }
    }
    SideEffect {
        zone.items = items
        zone.vertical = vertical
        zone.slots = config.dock.slots
    }
    val iconSize: Dp = (if (config.dock.followIconSize) config.icons.sizeDp else config.dock.iconSizeDp).dp
        .coerceAtMost((config.dock.thicknessDp - 2 * config.dock.marginDp - if (config.dock.showLabels) 20 else 0).coerceAtLeast(20).dp)
    val density = LocalDensity.current
    val cellPx = with(density) { (iconSize + 12.dp).toPx() }
    val cellHeightPx = with(density) { (config.dock.thicknessDp - 2 * config.dock.marginDp).dp.toPx() }
    val backdrop = rememberDockBackdrop(config)
    DockContainer(
        config, vertical,
        modifier = Modifier.onGloballyPositioned { zone.rootBounds = it.boundsInRoot() },
        backdrop = backdrop,
    ) {
        for (item in items.sortedBy { it.cell.x }) {
            key(item.id) {
                Box(
                    Modifier.size(with(density) { cellPx.toDp() }, with(density) { cellHeightPx.toDp() })
                        .onGloballyPositioned { zone.itemRects[item.id] = it.boundsInRoot() },
                ) {
                    LiveItem(
                        item, Surface.Dock, cellPx, cellHeightPx, ui,
                        showLabels = config.dock.showLabels, iconSizeOverride = iconSize,
                    )
                }
            }
        }
        if (config.dock.showSearchInDock) {
            SearchBarWidget(Modifier.size(with(density) { cellPx.toDp() } * 2, with(density) { cellHeightPx.toDp() }))
        }
    }
}

@Composable
private fun WallpaperScrollEffect(pager: PagerState, pages: Int, infinite: Boolean, enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val wm = remember { WallpaperManager.getInstance(context) }
    LaunchedEffect(pager, pages, enabled, infinite) {
        if (!enabled || pages <= 1) {
            view.windowToken?.let { runCatching { wm.setWallpaperOffsets(it, 0.5f, 0.5f) } }
            return@LaunchedEffect
        }
        runCatching { wm.setWallpaperOffsetSteps(1f / (pages - 1), 0f) }
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { pos ->
            val real = if (infinite) ((pos % pages) + pages) % pages else pos
            val x = (real / (pages - 1)).coerceIn(0f, 1f)
            view.windowToken?.let { token -> runCatching { wm.setWallpaperOffsets(token, x, 0.5f) } }
        }
    }
}

/** While dragging, holding the item near the left/right edge flips pages. */
@Composable
private fun EdgePagingEffect(ui: LauncherUiState, pager: PagerState) {
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    val dragging = ui.drag.isDragging
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        var since = 0L
        var dir = 0
        while (ui.drag.isDragging) {
            val x = ui.drag.pointer.x
            val d = when {
                x < edge -> -1
                x > widthPx - edge -> 1
                else -> 0
            }
            val now = System.currentTimeMillis()
            if (d != dir) { dir = d; since = now }
            if (d != 0 && now - since > 550) {
                val target = pager.currentPage + d
                if (target in 0 until pager.pageCount) pager.animateScrollToPage(target)
                since = System.currentTimeMillis()
            }
            delay(80)
        }
    }
}

/** Debug overlay (Advanced → Show layout bounds). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.LayoutBoundsOverlay(geometry: com.flyer.launcher.launcher.render.GridGeometry, items: List<WorkspaceItem>) {
    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        drawRect(androidx.compose.ui.graphics.Color.Cyan, topLeft = Offset(geometry.left, geometry.top),
            size = androidx.compose.ui.geometry.Size(geometry.contentWidth, geometry.contentHeight), style = stroke)
        items.forEach { item ->
            val r: Rect = geometry.itemRect(item)
            drawRect(androidx.compose.ui.graphics.Color.Magenta, topLeft = r.topLeft, size = r.size, style = stroke)
        }
    }
}
