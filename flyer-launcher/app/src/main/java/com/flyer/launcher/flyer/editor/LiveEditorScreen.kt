package com.flyer.launcher.flyer.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.BuiltInWidgetKind
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.widgets.WidgetEngine
import com.flyer.launcher.core.workspace.DropTarget
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.flyer.components.AppPickerDialog
import com.flyer.launcher.flyer.preview.PreviewLocals
import com.flyer.launcher.flyer.preview.ScaledScreen
import com.flyer.launcher.flyer.preview.rememberScreenSize
import com.flyer.launcher.launcher.overlays.WidgetPicker
import com.flyer.launcher.launcher.overlays.builtInTitle
import com.flyer.launcher.launcher.render.GridGeometry
import com.flyer.launcher.launcher.render.RenderMode

private enum class EditorSurfaceMode { HOME, DESKTOP }

@Composable
fun LiveEditorScreen(
    controller: FlyerController,
    session: FlyerSession,
    core: LauncherCore,
    apps: List<AppEntry>,
    selectItemId: String?,
    padding: PaddingValues,
) {
    val initial = remember(selectItemId) { selectItemId?.let { WorkspaceEngine.find(session.draft.layout, it) } }
    var mode by rememberSaveable {
        mutableStateOf(if (initial?.first == Surface.Desktop) EditorSurfaceMode.DESKTOP else EditorSurfaceMode.HOME)
    }
    var page by rememberSaveable { mutableIntStateOf((initial?.first as? Surface.Page)?.index ?: 0) }
    var selection by remember {
        mutableStateOf<EditorSelection?>(
            initial?.let { (surface, item) -> EditorSelection.Item(surface, item.id) },
        )
    }
    var showGrid by rememberSaveable { mutableStateOf(true) }
    var showGuides by rememberSaveable { mutableStateOf(true) }
    var inspectorOpen by rememberSaveable { mutableStateOf(true) }
    var addMenu by remember { mutableStateOf(false) }
    var pickApp by remember { mutableStateOf(false) }
    var pickFolderApps by remember { mutableStateOf<Set<String>?>(null) }
    var widgetPicker by remember { mutableStateOf(false) }
    val platform = LocalFlyerPlatform.current

    val doc = session.preview
    val config = doc.config
    val pageCount = doc.layout.pages.size
    if (page > pageCount - 1) page = pageCount - 1
    val currentSurface: Surface = if (mode == EditorSurfaceMode.DESKTOP) Surface.Desktop else Surface.Page(page)

    val callbacks = remember(session) {
        object : EditorCanvasCallbacks {
            override fun select(selection: EditorSelection) { selectionSetter(selection) }
            override fun move(surface: Surface, itemId: String, target: DropTarget) {
                session.updateLayout("Move item") { l ->
                    when (val r = WorkspaceEngine.drop(l, Placement.Existing(itemId), target, session.draft.config)) {
                        is WorkspaceEngine.DropResult.Accepted -> r.layout
                        is WorkspaceEngine.DropResult.Rejected -> l
                    }
                }
            }
            override fun resize(surface: Surface, itemId: String, cell: Cell) {
                val items = WorkspaceEngine.itemsOf(session.draft.layout, surface)
                if (!WorkspaceEngine.isOccupied(items, cell, ignoreId = itemId)) {
                    session.updateLayout("Resize", coalesceKey = "resize-$itemId") { l -> WorkspaceEngine.updateItem(l, itemId) { it.withCell(cell) } }
                }
            }
            override fun widgetLimits(item: WorkspaceItem, geometry: GridGeometry): WidgetEngine.SpanLimits? {
                val w = item as? WidgetItem ?: return null
                val info = core.widgets.providerInfo(w.appWidgetId) ?: return null
                val d = core.app.resources.displayMetrics.density
                return core.widgets.spansFor(info, geometry.cellWidth / d, geometry.cellHeight / d, geometry.columns, geometry.rows)
            }

            var selectionSetter: (EditorSelection) -> Unit = {}
        }
    }
    callbacks.selectionSetter = { selection = it; inspectorOpen = true }

    // Drop selection if its item disappeared (e.g. after undo).
    (selection as? EditorSelection.Item)?.let { sel -> if (WorkspaceEngine.find(session.draft.layout, sel.id) == null) selection = null }

    fun addItem(item: WorkspaceItem, label: String) {
        session.update(label) { d ->
            d.copy(layout = WorkspaceEngine.addItem(d.layout, item, d.config, preferPage = page, desktop = mode == EditorSurfaceMode.DESKTOP))
        }
        selection = EditorSelection.Item(currentSurface, item.id)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val inspectorHeight = maxHeight * 0.42f
        val toolbar: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(mode == EditorSurfaceMode.HOME, { mode = EditorSurfaceMode.HOME; selection = null }, label = { Text("Home") }, leadingIcon = { Icon(Icons.Outlined.Home, null) })
                FilterChip(mode == EditorSurfaceMode.DESKTOP, { mode = EditorSurfaceMode.DESKTOP; selection = null }, label = { Text("Desktop") }, leadingIcon = { Icon(Icons.Outlined.DesktopWindows, null) })
                if (mode == EditorSurfaceMode.HOME) {
                    Spacer(Modifier.width(4.dp))
                    (0 until pageCount).forEach { i ->
                        FilterChip(i == page, { page = i; selection = null }, label = { Text("${i + 1}") })
                    }
                    IconButton(onClick = { session.updateLayout("Add page") { WorkspaceEngine.addPage(it) }; page = pageCount }) { Icon(Icons.Outlined.NoteAdd, "Add page") }
                    FilterChip(
                        config.home.positioning == Positioning.FREE,
                        {
                            val next = if (config.home.positioning == Positioning.FREE) Positioning.GRID else Positioning.FREE
                            session.updateConfig("Positioning: ${next.name.lowercase()}", null) { it.copy(home = it.home.copy(positioning = next)) }
                        },
                        label = { Text("Free positioning") }, leadingIcon = { Icon(Icons.Outlined.OpenWith, null) },
                    )
                }
                IconToggleButton(showGrid, { showGrid = it }) { Icon(Icons.Outlined.GridOn, "Show grid", tint = if (showGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconToggleButton(showGuides, { showGuides = it }) { Icon(Icons.Outlined.Straighten, "Alignment guides", tint = if (showGuides) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    FilterChip(false, { addMenu = true }, label = { Text("Add") }, leadingIcon = { Icon(Icons.Outlined.Add, null) })
                    DropdownMenu(addMenu, { addMenu = false }) {
                        DropdownMenuItem(text = { Text("App…") }, leadingIcon = { Icon(Icons.Outlined.Apps, null) }, onClick = { addMenu = false; pickApp = true })
                        DropdownMenuItem(text = { Text("Folder…") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { addMenu = false; pickFolderApps = emptySet() })
                        DropdownMenuItem(text = { Text("App widget…") }, leadingIcon = { Icon(Icons.Outlined.Widgets, null) }, onClick = { addMenu = false; widgetPicker = true })
                        HorizontalDivider()
                        BuiltInWidgetKind.entries.forEach { kind ->
                            DropdownMenuItem(text = { Text(builtInTitle(kind)) }, onClick = {
                                addMenu = false
                                val span = when (kind) {
                                    BuiltInWidgetKind.CLOCK -> Cell(spanX = config.home.columns.coerceAtMost(4), spanY = 2)
                                    BuiltInWidgetKind.DATE, BuiltInWidgetKind.SEARCH, BuiltInWidgetKind.FLYER_QUICK_PANEL -> Cell(spanX = config.home.columns.coerceAtMost(4), spanY = 1)
                                }
                                addItem(BuiltInWidgetItem(kind = kind, cell = span), "Add ${builtInTitle(kind)}")
                            })
                        }
                    }
                }
                val sel = selection as? EditorSelection.Item
                val selItem = sel?.let { WorkspaceEngine.find(session.draft.layout, it.id)?.second }
                if (sel != null && selItem != null && !WorkspaceEngine.isProtected(selItem)) {
                    IconButton(onClick = { session.updateLayout("Remove") { WorkspaceEngine.removeItem(it, sel.id) }; selection = null }) {
                        Icon(Icons.Outlined.Delete, "Remove selected")
                    }
                }
            }
        }

        val canvas: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m.background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                val screen = rememberScreenSize()
                val density = LocalDensity.current
                val availW = with(density) { (maxWidth - 24.dp).toPx() }
                val availH = with(density) { (maxHeight - 24.dp).toPx() }
                val scale = minOf(availW / screen.width, availH / screen.height).coerceIn(0.15f, 1f)
                val insets = WindowInsets.systemBars.asPaddingValues()
                PreviewLocals(doc, RenderMode.EDITOR) {
                    ScaledScreen(screen, scale, Modifier.clip(RoundedCornerShape(20.dp))) {
                        if (mode == EditorSurfaceMode.HOME) {
                            EditorHome(config, doc.layout, page, insets, selection, scale, showGrid, showGuides, callbacks)
                        } else {
                            EditorDesktop(config, doc.layout, insets, selection, scale, showGrid, showGuides, callbacks)
                        }
                    }
                }
            }
        }

        val inspector: @Composable (Modifier) -> Unit = { m ->
            Inspector(controller, session, core, apps, selection, m) { selection = it }
        }

        if (wide) {
            Column(Modifier.fillMaxSize()) {
                toolbar()
                Row(Modifier.weight(1f)) {
                    canvas(Modifier.weight(0.58f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                        inspector(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()))
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                toolbar()
                canvas(Modifier.weight(1f).fillMaxWidth())
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selection?.let { inspectorTitle(it, session, apps) } ?: "Tap an element to edit it",
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { inspectorOpen = !inspectorOpen }) {
                                Icon(if (inspectorOpen) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, if (inspectorOpen) "Collapse inspector" else "Expand inspector")
                            }
                        }
                        if (inspectorOpen && selection != null) {
                            inspector(Modifier.fillMaxWidth().height(inspectorHeight).padding(bottom = padding.calculateBottomPadding()))
                        } else Spacer(Modifier.height(padding.calculateBottomPadding()))
                    }
                }
            }
        }
    }

    if (pickApp) AppPickerDialog("Add app", apps, onDismiss = { pickApp = false }) { app ->
        pickApp = false
        addItem(AppItem(appKey = app.key), "Add ${app.label}")
    }
    pickFolderApps?.let { chosen ->
        AppPickerDialog("Folder apps (${chosen.size} chosen)", apps, onDismiss = {
            pickFolderApps = null
            if (chosen.size >= 2) addItem(FolderItem(name = "Folder", apps = chosen.toList()), "Add folder")
        }, multiSelect = chosen) { app ->
            pickFolderApps = if (app.key in chosen) chosen - app.key else chosen + app.key
        }
    }
    if (widgetPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { widgetPicker = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            WidgetPicker(
                onPick = { choice ->
                    widgetPicker = false
                    platform.addWidget(choice, currentSurface) { item -> addItem(item, "Add widget") }
                },
                onDismiss = { widgetPicker = false },
            )
        }
    }
}

fun inspectorTitle(selection: EditorSelection, session: FlyerSession, apps: List<AppEntry>): String = when (selection) {
    is EditorSelection.Item -> when (val item = WorkspaceEngine.find(session.draft.layout, selection.id)?.second) {
        is AppItem -> session.draft.layout.appOverrides[item.appKey]?.label ?: apps.firstOrNull { it.key == item.appKey }?.label ?: "App"
        is FolderItem -> "Folder · ${item.name}"
        is WidgetItem -> "Widget"
        is BuiltInWidgetItem -> builtInTitle(item.kind)
        is com.flyer.launcher.core.model.ShortcutItem -> "Shortcut · ${item.label}"
        null -> "Item"
    }
    else -> selection.title
}
