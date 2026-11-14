package com.flyer.launcher.flyer.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.FolderPreview
import com.flyer.launcher.core.config.IconShape
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.AppOverride
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.BuiltInWidgetKind
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.FreePos
import com.flyer.launcher.core.model.ItemStyle
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.core.workspace.DropTarget
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.core.workspace.gridFor
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.components.ColorPickerDialog
import com.flyer.launcher.flyer.components.ColorSwatch
import com.flyer.launcher.flyer.components.SectionHeader
import com.flyer.launcher.flyer.components.SettingRow
import com.flyer.launcher.flyer.settings.EditorElement
import com.flyer.launcher.flyer.settings.SettingsRegistry

/** Properties panel for the element selected in the live editor. */
@Composable
fun Inspector(
    controller: FlyerController,
    session: FlyerSession,
    core: LauncherCore,
    apps: List<AppEntry>,
    selection: EditorSelection?,
    modifier: Modifier,
    onSelect: (EditorSelection?) -> Unit,
) {
    val config = session.draft.config
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        when (selection) {
            null -> item {
                Text(
                    "Select any element in the preview: icons, folders, widgets, the dock, the page indicator, the search bar or empty space for the workspace. Drag items to move them.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
                )
            }
            EditorSelection.Workspace -> specs(session, setOf(EditorElement.WORKSPACE, EditorElement.BACKGROUND))
            EditorSelection.Dock -> {
                item { DockItemsRow(session, apps, onSelect) }
                specs(session, setOf(EditorElement.DOCK))
            }
            EditorSelection.PageIndicator -> specs(session, setOf(EditorElement.PAGE_INDICATOR))
            EditorSelection.SearchBar -> specs(session, setOf(EditorElement.SEARCH_BAR))
            EditorSelection.Desktop -> specs(session, setOf(EditorElement.DESKTOP))
            EditorSelection.Taskbar -> specs(session, setOf(EditorElement.TASKBAR))
            is EditorSelection.Item -> {
                val found = WorkspaceEngine.find(session.draft.layout, selection.id)
                if (found == null) {
                    item { Text("This item no longer exists.", modifier = Modifier.padding(20.dp)) }
                } else {
                    val (surface, it) = found
                    item { ItemActions(controller, session, surface, it, onSelect) }
                    item { ItemProperties(session, core, surface, it, config) }
                    when (it) {
                        is AppItem, is ShortcutItem -> specsCollapsible(session, "All icons", setOf(EditorElement.ICON, EditorElement.LABEL))
                        is FolderItem -> specsCollapsible(session, "All folders", setOf(EditorElement.FOLDER))
                        is WidgetItem -> specsCollapsible(session, "All widgets", setOf(EditorElement.WIDGET))
                        is BuiltInWidgetItem -> when (it.kind) {
                            BuiltInWidgetKind.CLOCK, BuiltInWidgetKind.DATE -> specs(session, setOf(EditorElement.CLOCK))
                            BuiltInWidgetKind.SEARCH -> specs(session, setOf(EditorElement.SEARCH_BAR))
                            BuiltInWidgetKind.FLYER_QUICK_PANEL -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.specs(session: FlyerSession, elements: Set<EditorElement>) {
    val config = session.draft.config
    val list = SettingsRegistry.all.filter { s -> s.elements.any { it in elements } && s.visibleWhen(config) }
    items(list, key = { it.id }) { spec ->
        val traits = LocalDeviceTraits.current
        SettingRow(spec, session.draft.config, traits, highlighted = false, onEdit = { next, label, key -> session.updateConfig(label, key) { next } })
    }
}

private fun LazyListScope.specsCollapsible(session: FlyerSession, title: String, elements: Set<EditorElement>) {
    item(key = "collapse-$title") {
        var open by remember { mutableStateOf(false) }
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$title (applies to every item)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (open) {
                val config = session.draft.config
                val traits = LocalDeviceTraits.current
                SettingsRegistry.all.filter { s -> s.elements.any { it in elements } && s.visibleWhen(config) }.forEach { spec ->
                    SettingRow(spec, config, traits, highlighted = false, onEdit = { next, label, key -> session.updateConfig(label, key) { next } })
                }
            }
        }
    }
}

@Composable
private fun DockItemsRow(session: FlyerSession, apps: List<AppEntry>, onSelect: (EditorSelection?) -> Unit) {
    val dock = session.draft.layout.dock.sortedBy { it.cell.x }
    if (dock.isEmpty()) return
    SectionHeader("Dock items")
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dock.forEach { item ->
            val name = when (item) {
                is AppItem -> apps.firstOrNull { it.key == item.appKey }?.label ?: "App"
                is FolderItem -> item.name
                else -> "Item"
            }
            FilterChip(false, { onSelect(EditorSelection.Item(Surface.Dock, item.id)) }, label = { Text(name) })
        }
    }
}

@Composable
private fun ItemActions(
    controller: FlyerController,
    session: FlyerSession,
    surface: Surface,
    item: WorkspaceItem,
    onSelect: (EditorSelection?) -> Unit,
) {
    val layout = session.draft.layout
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                session.updateLayout("Reset element") { l ->
                    WorkspaceEngine.updateItem(l, item.id) { w ->
                        when (val r = w.withStyle(ItemStyle())) {
                            is FolderItem -> r.copy(color = null, shape = null, preview = null, columns = null)
                            is WidgetItem -> r.copy(paddingDp = null, background = null, cornerRadiusDp = null)
                            else -> r
                        }
                    }
                }
            }) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Reset element") }
            if (item is AppItem) {
                OutlinedButton(onClick = { controller.navigate(FlyerRoute.AppEditor(item.appKey)) }) {
                    Icon(Icons.Outlined.Brush, null); Spacer(Modifier.width(6.dp)); Text("Icon & label…")
                }
            }
            OutlinedButton(onClick = { session.updateLayout("Remove") { WorkspaceEngine.removeItem(it, item.id) }; onSelect(null) }) {
                Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Remove")
            }
        }
        // Moving between surfaces.
        val canDock = item is AppItem || item is FolderItem || item is ShortcutItem
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Move to", style = MaterialTheme.typography.labelMedium)
            layout.pages.indices.forEach { i ->
                FilterChip(surface == Surface.Page(i), {
                    if (surface != Surface.Page(i)) moveTo(session, item, Surface.Page(i), onSelect)
                }, label = { Text("Page ${i + 1}") })
            }
            if (canDock) FilterChip(surface == Surface.Dock, { if (surface != Surface.Dock) moveTo(session, item, Surface.Dock, onSelect) }, label = { Text("Dock") })
            FilterChip(surface == Surface.Desktop, { if (surface != Surface.Desktop) moveTo(session, item, Surface.Desktop, onSelect) }, label = { Text("Desktop") })
        }
    }
}

private fun moveTo(session: FlyerSession, item: WorkspaceItem, target: Surface, onSelect: (EditorSelection?) -> Unit) {
    var accepted = false
    session.updateLayout("Move item") { l ->
        val config = session.draft.config
        val dims = config.gridFor(target)
        val dropTarget = when (target) {
            Surface.Dock -> DropTarget.DockIndex(l.dock.size)
            else -> {
                val free = WorkspaceEngine.findFreeCell(WorkspaceEngine.itemsOf(l, target), dims, item.cell.spanX, item.cell.spanY) ?: Cell(0, 0)
                DropTarget.AtCell(target, free.x, free.y)
            }
        }
        when (val r = WorkspaceEngine.drop(l, Placement.Existing(item.id), dropTarget, config)) {
            is WorkspaceEngine.DropResult.Accepted -> { accepted = true; r.layout }
            is WorkspaceEngine.DropResult.Rejected -> l
        }
    }
    if (accepted) onSelect(EditorSelection.Item(target, item.id))
}

@Composable
private fun ItemProperties(session: FlyerSession, core: LauncherCore, surface: Surface, item: WorkspaceItem, config: FlyerConfig) {
    fun update(label: String, key: String? = null, transform: (WorkspaceItem) -> WorkspaceItem) =
        session.updateLayout(label, key) { l -> WorkspaceEngine.updateItem(l, item.id, transform) }

    Column {
        when (item) {
            is AppItem -> {
                SectionHeader("App")
                val override = session.draft.layout.appOverrides[item.appKey] ?: AppOverride()
                var text by remember(item.appKey) { mutableStateOf(override.label.orEmpty()) }
                OutlinedTextField(
                    text,
                    { v ->
                        text = v.take(40)
                        session.updateLayout("Rename", "rename-${item.appKey}") { l ->
                            val next = (l.appOverrides[item.appKey] ?: AppOverride()).copy(label = text.ifBlank { null })
                            l.copy(appOverrides = if (next.label == null && next.icon == null) l.appOverrides - item.appKey else l.appOverrides + (item.appKey to next))
                        }
                    },
                    label = { Text("Label (everywhere)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            is FolderItem -> FolderProperties(session, item)
            is WidgetItem -> {
                SectionHeader("Widget container")
                PropSlider("Padding", (item.paddingDp ?: config.widgets.paddingDp).toFloat(), 0f..48f, 1f, "dp", item.paddingDp != null) { v ->
                    update("Widget padding", "wpad-${item.id}") { (it as WidgetItem).copy(paddingDp = v?.toInt()) }
                }
                PropSlider("Corner radius", (item.cornerRadiusDp ?: config.widgets.cornerRadiusDp).toFloat(), 0f..64f, 1f, "dp", item.cornerRadiusDp != null) { v ->
                    update("Widget corners", "wcorner-${item.id}") { (it as WidgetItem).copy(cornerRadiusDp = v?.toInt()) }
                }
                PropColor("Background", item.background ?: config.widgets.containerBackground, item.background != null) { c ->
                    update("Widget background", "wbg-${item.id}") { (it as WidgetItem).copy(background = c) }
                }
                core.widgets.providerInfo(item.appWidgetId)?.let { info ->
                    val label = info.loadLabel(core.app.packageManager)
                    Text("$label · ${info.provider.packageName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            else -> Unit
        }

        if (item !is WidgetItem && item !is BuiltInWidgetItem) {
            SectionHeader("This item's style")
            PropToggle("Hide label", item.style.hideLabel == true) { v -> update("Hide label") { it.withStyle(it.style.copy(hideLabel = if (v) true else null)) } }
            PropSlider("Icon scale", (item.style.iconScale ?: 1f), 0.4f..2.5f, 0.05f, "×", item.style.iconScale != null) { v ->
                update("Icon scale", "scale-${item.id}") { it.withStyle(it.style.copy(iconScale = v)) }
            }
            PropSlider("Rotation", (item.style.rotationDeg ?: 0f), -180f..180f, 1f, "°", item.style.rotationDeg != null) { v ->
                update("Rotation", "rot-${item.id}") { it.withStyle(it.style.copy(rotationDeg = v)) }
            }
            PropSlider("Opacity", (item.style.opacity ?: 1f), 0.1f..1f, 0.01f, "", item.style.opacity != null) { v ->
                update("Opacity", "op-${item.id}") { it.withStyle(it.style.copy(opacity = v)) }
            }
            PropColor("Label color", item.style.labelColor ?: config.icons.label.color, item.style.labelColor != null) { c ->
                update("Label color", "lc-${item.id}") { it.withStyle(it.style.copy(labelColor = c)) }
            }
        }

        if (surface != Surface.Dock) PositionProperties(session, surface, item, config)
    }
}

@Composable
private fun FolderProperties(session: FlyerSession, folder: FolderItem) {
    val config = session.draft.config
    fun update(label: String, key: String? = null, transform: (FolderItem) -> FolderItem) =
        session.updateLayout(label, key) { l -> WorkspaceEngine.updateItem(l, folder.id) { transform(it as FolderItem) } }
    SectionHeader("Folder")
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    OutlinedTextField(name, { v -> name = v.take(40); update("Rename folder", "fname-${folder.id}") { it.copy(name = name.ifBlank { "Folder" }) } },
        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    PropColor("Folder color", folder.color ?: config.folders.previewBackground, folder.color != null) { c -> update("Folder color", "fcolor-${folder.id}") { it.copy(color = c) } }
    PropChoice("Shape", listOf<Pair<IconShape?, String>>(null to "Default") + IconShape.entries.map { it to it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }, folder.shape) { v ->
        update("Folder shape") { it.copy(shape = v) }
    }
    PropChoice("Preview", listOf<Pair<FolderPreview?, String>>(null to "Default", FolderPreview.GRID_2X2 to "2×2", FolderPreview.GRID_3X3 to "3×3", FolderPreview.STACK to "Stack", FolderPreview.FIRST_ICON to "First app"), folder.preview) { v ->
        update("Folder preview") { it.copy(preview = v) }
    }
    PropSlider("Columns when open", (folder.columns ?: config.folders.columns).toFloat(), 2f..8f, 1f, "", folder.columns != null) { v ->
        update("Folder columns", "fcols-${folder.id}") { it.copy(columns = v?.toInt()) }
    }
    SectionHeader("Apps in this folder")
    folder.apps.forEachIndexed { i, key ->
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(session.draft.layout.appOverrides[key]?.label ?: key.substringAfter('/').substringBefore('@').substringAfterLast('.'), modifier = Modifier.weight(1f))
            IconButton(onClick = { session.updateLayout("Reorder folder") { WorkspaceEngine.reorderInFolder(it, folder.id, i, i - 1) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
            IconButton(onClick = { session.updateLayout("Reorder folder") { WorkspaceEngine.reorderInFolder(it, folder.id, i, i + 1) } }, enabled = i < folder.apps.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
            IconButton(onClick = { session.updateLayout("Remove from folder") { WorkspaceEngine.updateFolderApps(it, folder.id, folder.apps - key) } }) { Icon(Icons.Outlined.Remove, "Remove from folder") }
        }
    }
}

@Composable
private fun PositionProperties(session: FlyerSession, surface: Surface, item: WorkspaceItem, config: FlyerConfig) {
    val dims = config.gridFor(surface)
    val free = (surface == Surface.Desktop && !config.desktop.snapToGrid) || (surface is Surface.Page && config.home.positioning == Positioning.FREE)
    val items = WorkspaceEngine.itemsOf(session.draft.layout, surface)
    fun setCell(cell: Cell, label: String) {
        if (cell.fitsIn(dims.columns, dims.rows) && !WorkspaceEngine.isOccupied(items, cell, ignoreId = item.id)) {
            session.updateLayout(label, "pos-${item.id}") { l -> WorkspaceEngine.updateItem(l, item.id) { it.withCell(cell).withFree(if (free) it.free else null) } }
        }
    }
    SectionHeader(if (free) "Position (free)" else "Position (grid)")
    if (free) {
        val pos = item.free ?: WorkspaceEngine.cellToFree(item.cell, dims)
        PropSlider("X", pos.x, 0f..1f, 0.005f, "", true, showReset = false) { v ->
            session.updateLayout("Move X", "fx-${item.id}") { l -> WorkspaceEngine.updateItem(l, item.id) { it.withFree(FreePos(v ?: 0f, pos.y)) } }
        }
        PropSlider("Y", pos.y, 0f..1f, 0.005f, "", true, showReset = false) { v ->
            session.updateLayout("Move Y", "fy-${item.id}") { l -> WorkspaceEngine.updateItem(l, item.id) { it.withFree(FreePos(pos.x, v ?: 0f)) } }
        }
        if (item.free != null) TextButton(onClick = {
            session.updateLayout("Snap to grid") { l -> WorkspaceEngine.updateItem(l, item.id) { it.withCell(WorkspaceEngine.freeToCell(pos, it.cell, dims)).withFree(null) } }
        }, modifier = Modifier.padding(start = 12.dp)) { Text("Snap back to its grid cell") }
    } else {
        PropStepper("Column", item.cell.x + 1, 1, dims.columns - item.cell.spanX + 1) { setCell(item.cell.copy(x = it - 1), "Move") }
        PropStepper("Row", item.cell.y + 1, 1, dims.rows - item.cell.spanY + 1) { setCell(item.cell.copy(y = it - 1), "Move") }
    }
    if (item is WidgetItem || item is BuiltInWidgetItem) {
        PropStepper("Width (cells)", item.cell.spanX, 1, dims.columns - item.cell.x) { setCell(item.cell.copy(spanX = it), "Resize") }
        PropStepper("Height (cells)", item.cell.spanY, 1, dims.rows - item.cell.y) { setCell(item.cell.copy(spanY = it), "Resize") }
    }
}

// ───────────── Item property controls (nullable = "use the global value") ─────────────

@Composable
private fun PropSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    overridden: Boolean,
    showReset: Boolean = true,
    onChange: (Float?) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (overridden) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Text(if (step >= 1f) "${value.toInt()}$unit" else "%.2f$unit".format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (showReset && overridden) IconButton(onClick = { onChange(null) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Clear, "Use default", Modifier.size(16.dp)) }
        }
        Slider(value.coerceIn(range.start, range.endInclusive), { v ->
            val snapped = if (step > 0f) (range.start + ((v - range.start) / step + 0.5f).toInt() * step).coerceIn(range.start, range.endInclusive) else v
            onChange(snapped)
        }, valueRange = range)
    }
}

@Composable
private fun PropToggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun PropColor(title: String, value: ColorValue, overridden: Boolean, onChange: (ColorValue?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (overridden) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (overridden) IconButton(onClick = { onChange(null) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Clear, "Use default", Modifier.size(16.dp)) }
        ColorSwatch(value, 30)
    }
    if (open) ColorPickerDialog(title, value, allowAlpha = true, onPreview = { onChange(it) }, onDismiss = { open = false })
}

@Composable
private fun <T> PropChoice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (v, label) -> FilterChip(v == selected, { onSelect(v) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun PropStepper(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) { Icon(Icons.Outlined.Remove, "Decrease") }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
        IconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) { Icon(Icons.Outlined.Add, "Increase") }
    }
}
