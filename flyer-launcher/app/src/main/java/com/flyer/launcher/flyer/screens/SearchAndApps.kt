package com.flyer.launcher.flyer.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.apps.IconPackManager
import com.flyer.launcher.core.model.AppOverride
import com.flyer.launcher.core.model.IconSource
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.flyer.components.AppPickerDialog
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.InfoRow
import com.flyer.launcher.flyer.components.SectionHeader
import com.flyer.launcher.flyer.preview.PreviewLocals
import com.flyer.launcher.flyer.settings.SettingsRegistry
import com.flyer.launcher.launcher.render.AppTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsSearchScreen(controller: FlyerController, session: FlyerSession, padding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val config = session.draft.config
    val hits = remember(query, config) { if (query.isBlank()) emptyList() else SettingsRegistry.search(query, config) }
    val categories = remember(query) {
        if (query.isBlank()) emptyList() else FlyerCategory.entries.filter { it.title.contains(query, true) || it.summary.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search ${SettingsRegistry.all.size} settings, e.g. \"blur\" or \"label\"") },
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + padding.calculateBottomPadding())) {
            if (categories.isNotEmpty()) {
                item { SectionHeader("Modules") }
                items(categories, key = { "c-" + it.name }) { c ->
                    InfoRow(c.title, c.summary, onClick = { controller.navigate(FlyerRoute.of(c)) }) { Icon(c.icon, null) }
                }
            }
            if (hits.isNotEmpty()) item { SectionHeader("Settings") }
            items(hits, key = { it.spec.id }) { hit ->
                val s = hit.spec
                InfoRow(
                    s.title,
                    "${s.category.title} › ${s.section} · ${s.display(config)}",
                    onClick = { controller.navigate(FlyerRoute.Category(s.category, highlight = s.id)) },
                ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
            }
            if (query.isNotBlank() && hits.isEmpty() && categories.isEmpty()) {
                item { Text("Nothing matches \"$query\".", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Everything about one app: label, icon source, visibility, placement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppEditorScreen(session: FlyerSession, core: LauncherCore, apps: List<AppEntry>, appKey: String, padding: PaddingValues) {
    val entry = apps.firstOrNull { it.key == appKey }
    if (entry == null) {
        Text("This app is no longer installed.", modifier = Modifier.padding(24.dp))
        return
    }
    val platform = LocalFlyerPlatform.current
    val scope = rememberCoroutineScope()
    val override = session.draft.layout.appOverrides[appKey] ?: AppOverride()
    var label by remember(appKey) { mutableStateOf(override.label.orEmpty()) }
    var pickingOther by remember { mutableStateOf(false) }
    var packPicker by remember { mutableStateOf(false) }

    fun setOverride(name: String, transform: (AppOverride) -> AppOverride) {
        session.updateLayout(name, coalesceKey = "override-$appKey-$name") { l ->
            val next = transform(l.appOverrides[appKey] ?: AppOverride())
            l.copy(appOverrides = if (next.label == null && next.icon == null) l.appOverrides - appKey else l.appOverrides + (appKey to next))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp + padding.calculateBottomPadding())) {
        FlyerCard(Modifier.padding(16.dp)) {
            PreviewLocals(session.preview) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    val cfg = session.preview.config
                    listOf(40, 56, 80).forEach { size -> AppTile(appKey, size.dp, true, cfg.icons.label.copy(shadow = false), labelColor = MaterialTheme.colorScheme.onSurface) }
                }
            }
            Text(
                "${entry.label} · ${entry.packageName}" + if (entry.isWorkProfile) " · work profile" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
        }

        SectionHeader("Label")
        FlyerCard(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                label,
                { label = it.take(40); setOverride("Rename ${entry.label}") { o -> o.copy(label = label.ifBlank { null }) } },
                singleLine = true, placeholder = { Text(entry.label) }, label = { Text("Shown name") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }

        SectionHeader("Icon")
        FlyerCard(Modifier.padding(horizontal = 16.dp)) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = override.icon == null, onClick = { setOverride("Default icon") { it.copy(icon = null) } }, label = { Text("App's icon") })
                FilterChip(selected = override.icon is IconSource.Pack, onClick = { packPicker = true }, label = { Text("From icon pack…") })
                FilterChip(selected = override.icon is IconSource.Image, onClick = {
                    platform.pickImage { uri ->
                        if (uri != null) scope.launch {
                            core.persistence.importMedia(uri, "icons", "img")?.let { path -> setOverride("Custom image icon") { it.copy(icon = IconSource.Image(path)) } }
                        }
                    }
                }, label = { Text("Image…") })
                FilterChip(selected = override.icon is IconSource.OtherApp, onClick = { pickingOther = true }, label = { Text("Another app's icon…") })
            }
            Text(
                "Custom icons still follow your icon shape, size and effects.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }

        SectionHeader("Visibility & placement")
        FlyerCard(Modifier.padding(horizontal = 16.dp)) {
            val hidden = appKey in session.draft.layout.hiddenApps
            InfoRow("Hide from app drawer", "The app stays installed.") {
                Switch(checked = hidden, onCheckedChange = { h ->
                    session.updateLayout(if (h) "Hide ${entry.label}" else "Show ${entry.label}") { l -> l.copy(hiddenApps = if (h) l.hiddenApps + appKey else l.hiddenApps - appKey) }
                })
            }
            val placed = appKey in WorkspaceEngine.placedAppKeys(session.draft.layout)
            InfoRow(if (placed) "On your home screen" else "Not on your home screen", null) {
                Button(onClick = { session.update("Add ${entry.label}") { d -> d.copy(layout = WorkspaceEngine.addApp(d.layout, appKey, d.config)) } }) { Text("Add") }
            }
            if (override.label != null || override.icon != null) {
                Row(Modifier.padding(12.dp)) {
                    OutlinedButton(onClick = { label = ""; setOverride("Reset ${entry.label}") { AppOverride() } }) { Text("Reset this app's customizations") }
                }
            }
        }
    }

    if (pickingOther) AppPickerDialog("Use the icon of…", apps.filter { it.key != appKey }, onDismiss = { pickingOther = false }) { other ->
        pickingOther = false
        setOverride("Icon from ${other.label}") { it.copy(icon = IconSource.OtherApp(other.key)) }
    }
    if (packPicker) IconPackPickerDialog(core, onDismiss = { packPicker = false }) { pack, name ->
        packPicker = false
        setOverride("Icon pack icon") { it.copy(icon = IconSource.Pack(pack, name)) }
    }
}

@Composable
private fun IconPackPickerDialog(core: LauncherCore, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val packs by produceState<List<IconPackManager.IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { core.iconPacks.installedPacks() } }
    var pack by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val names by produceState<List<String>>(emptyList(), pack) {
        value = pack?.let { p -> withContext(Dispatchers.IO) { core.iconPacks.load(p)?.allDrawables().orEmpty() } }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pack == null) "Choose an icon pack" else "Choose an icon") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = { if (pack != null) TextButton(onClick = { pack = null }) { Text("Back") } },
        text = {
            if (pack == null) {
                Column {
                    if (packs?.isEmpty() == true) Text("No icon packs are installed.")
                    packs.orEmpty().forEach { p ->
                        Text(p.label, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pack = p.packageName }.padding(12.dp))
                    }
                }
            } else {
                Column {
                    OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Filter icons") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val filtered = remember(names, query) { names.filter { query.isBlank() || it.contains(query, true) }.take(600) }
                    LazyVerticalGrid(GridCells.Adaptive(56.dp), modifier = Modifier.height(380.dp)) {
                        items(filtered, key = { it }) { name ->
                            val bmp by produceState<ImageBitmap?>(null, pack, name) { value = core.icons.packDrawable(pack!!, name, 112) }
                            Box(Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(pack!!, name) }, contentAlignment = Alignment.Center) {
                                bmp?.let { Image(it, name, Modifier.size(44.dp)) }
                            }
                        }
                    }
                    if (filtered.isEmpty()) Text("This pack lists no icons.", fontWeight = FontWeight.Medium)
                }
            }
        },
    )
}
