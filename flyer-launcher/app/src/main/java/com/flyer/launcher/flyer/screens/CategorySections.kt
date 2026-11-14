package com.flyer.launcher.flyer.screens

import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.apps.BuiltInApp
import com.flyer.launcher.core.apps.IconPackManager
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.FontChoice
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.DrawerGroup
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.flyer.components.AppPickerDialog
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.InfoRow
import com.flyer.launcher.flyer.components.NameDialog
import com.flyer.launcher.flyer.components.SectionHeader
import com.flyer.launcher.launcher.overlays.builtInTitle
import com.flyer.launcher.launcher.render.IconImage
import com.flyer.launcher.launcher.render.rememberAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Category-specific modules shown above the generated settings. */
@Composable
fun CategoryCustomSection(controller: FlyerController, session: FlyerSession, core: LauncherCore, apps: List<AppEntry>, category: FlyerCategory) {
    when (category) {
        FlyerCategory.HOME -> { PagesManager(session); BackgroundImageSection(session, core) }
        FlyerCategory.ICONS -> { IconPackSection(session, core); PerAppIconsSection(controller, apps) }
        FlyerCategory.DOCK -> DockContentsSection(session, apps)
        FlyerCategory.DRAWER -> { HiddenAppsSection(session, apps); DrawerGroupsSection(session, apps) }
        FlyerCategory.WIDGETS -> WidgetsSection(controller, session)
        FlyerCategory.FOLDERS -> FoldersSection(controller, session)
        FlyerCategory.GESTURES -> GestureEditor(session, apps)
        FlyerCategory.TYPOGRAPHY -> FontImportSection(session, core)
        FlyerCategory.DESKTOP -> TaskbarPinsSection(session, apps)
        FlyerCategory.THEMES -> ThemesSection(session, core)
        FlyerCategory.PERFORMANCE -> PerformanceSection(core)
        FlyerCategory.ADVANCED -> AdvancedSection(session, core)
        FlyerCategory.BACKUP -> BackupSection(session, core)
        else -> Unit
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
private fun PagesManager(session: FlyerSession) {
    val layout = session.draft.layout
    val defaultPage = session.draft.config.home.defaultPage
    SectionHeader("Pages") {
        TextButton(onClick = { session.updateLayout("Add page") { WorkspaceEngine.addPage(it) } }) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Add page")
        }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        layout.pages.forEachIndexed { i, page ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Page ${i + 1}", style = MaterialTheme.typography.bodyLarge)
                    Text("${page.items.size} items" + if (i == defaultPage) " · default page" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { session.updateConfig("Default page", null) { it.copy(home = it.home.copy(defaultPage = i)) } }) {
                    Icon(if (i == defaultPage) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Make default page")
                }
                IconButton(onClick = { session.updateLayout("Move page") { WorkspaceEngine.movePage(it, i, i - 1) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Move earlier") }
                IconButton(onClick = { session.updateLayout("Move page") { WorkspaceEngine.movePage(it, i, i + 1) } }, enabled = i < layout.pages.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move later") }
                IconButton(
                    onClick = {
                        session.update("Delete page") { doc ->
                            val newDefault = doc.config.home.defaultPage.let { d -> if (d >= i && d > 0) d - 1 else d }
                            doc.copy(layout = WorkspaceEngine.removePage(doc.layout, i), config = doc.config.copy(home = doc.config.home.copy(defaultPage = newDefault)))
                        }
                    },
                    enabled = layout.pages.size > 1,
                ) { Icon(Icons.Outlined.Delete, "Delete page") }
            }
        }
    }
}

@Composable
private fun BackgroundImageSection(session: FlyerSession, core: LauncherCore) {
    val platform = LocalFlyerPlatform.current
    val scope = rememberCoroutineScope()
    val bg = session.draft.config.home.background
    SectionHeader("Background image")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow(
            if (bg.imagePath != null) "Custom image selected" else "No image selected",
            "Pick a photo to use as a launcher-drawn background. It enables blur behind the dock and panels.",
        ) {
            Button(onClick = {
                platform.pickImage { uri ->
                    if (uri != null) scope.launch {
                        val path = core.persistence.importMedia(uri, "backgrounds", "img")
                        if (path != null) session.updateConfig("Background image", null) { c ->
                            c.copy(home = c.home.copy(background = c.home.background.copy(type = BackgroundType.IMAGE, imagePath = path)))
                        }
                    }
                }
            }) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text("Choose") }
        }
        Row(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
            TextButton(onClick = { platform.openWallpaperPicker() }) { Text("Change system wallpaper") }
        }
    }
}

// ───────────────────────── Icons ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPackSection(session: FlyerSession, core: LauncherCore) {
    val packs by produceState<List<IconPackManager.IconPackInfo>?>(null) {
        value = withContext(Dispatchers.IO) { core.iconPacks.installedPacks() }
    }
    val current = session.draft.config.icons.iconPack
    SectionHeader("Icon pack")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Supports icon packs in the common ADW/Nova format. Install one from your app store and it appears here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = current == null, onClick = { session.updateConfig("Icon pack: none", null) { it.copy(icons = it.icons.copy(iconPack = null)) } }, label = { Text("App icons") })
                packs.orEmpty().forEach { p ->
                    FilterChip(
                        selected = current == p.packageName,
                        onClick = { session.updateConfig("Icon pack: ${p.label}", null) { it.copy(icons = it.icons.copy(iconPack = p.packageName)) } },
                        label = { Text(p.label) },
                    )
                }
            }
            if (packs != null && packs!!.isEmpty()) Text("No icon packs installed.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PerAppIconsSection(controller: FlyerController, apps: List<AppEntry>) {
    var picking by remember { mutableStateOf(false) }
    SectionHeader("Individual apps")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow("Customize one app", "Rename it or give it a different icon (icon pack, image or another app's icon).", onClick = { picking = true }) {
            Icon(Icons.Outlined.Edit, null)
        }
    }
    if (picking) AppPickerDialog("Choose an app", apps, onDismiss = { picking = false }) { picking = false; controller.navigate(FlyerRoute.AppEditor(it.key)) }
}

// ───────────────────────── Dock ─────────────────────────

@Composable
private fun DockContentsSection(session: FlyerSession, apps: List<AppEntry>) {
    val dock = session.draft.layout.dock.sortedBy { it.cell.x }
    val slots = session.draft.config.dock.slots
    var picking by remember { mutableStateOf(false) }
    SectionHeader("Dock contents (${dock.size}/$slots)") {
        TextButton(onClick = { picking = true }, enabled = dock.size < slots) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Add app") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (dock.isEmpty()) InfoRow("The dock is empty", "Add apps here or drag them onto the dock.")
        dock.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val key = (item as? AppItem)?.appKey
                if (key != null) IconImage(rememberAppIcon(key, 32.dp), 32.dp) else Icon(Icons.Outlined.Edit, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    when (item) {
                        is AppItem -> apps.firstOrNull { it.key == item.appKey }?.label ?: "App"
                        is FolderItem -> "Folder · ${item.name}"
                        else -> WorkspaceEngine.labelFor(item)
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { session.updateLayout("Move in dock") { l -> l.copy(dock = swapDock(l.dock, i, i - 1)) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Move left") }
                IconButton(onClick = { session.updateLayout("Move in dock") { l -> l.copy(dock = swapDock(l.dock, i, i + 1)) } }, enabled = i < dock.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move right") }
                IconButton(onClick = { session.updateLayout("Remove from dock") { WorkspaceEngine.removeItem(it, item.id) } }) { Icon(Icons.Outlined.Delete, "Remove") }
            }
        }
    }
    if (picking) AppPickerDialog("Add to dock", apps, onDismiss = { picking = false }) { app ->
        picking = false
        session.updateLayout("Add ${app.label} to dock") { l ->
            l.copy(dock = WorkspaceEngine.renumberDock(l.dock + AppItem(appKey = app.key, cell = Cell(l.dock.size, 0))))
        }
    }
}

private fun swapDock(dock: List<com.flyer.launcher.core.model.WorkspaceItem>, a: Int, b: Int): List<com.flyer.launcher.core.model.WorkspaceItem> {
    val sorted = dock.sortedBy { it.cell.x }.toMutableList()
    if (a !in sorted.indices || b !in sorted.indices) return dock
    val tmp = sorted[a]; sorted[a] = sorted[b]; sorted[b] = tmp
    return sorted.mapIndexed { i, it -> it.withCell(Cell(i, 0)) }
}

// ───────────────────────── Drawer ─────────────────────────

@Composable
private fun HiddenAppsSection(session: FlyerSession, apps: List<AppEntry>) {
    val hidden = session.draft.layout.hiddenApps
    var picking by remember { mutableStateOf(false) }
    SectionHeader("Hidden apps (${hidden.size})") {
        TextButton(onClick = { picking = true }) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(4.dp)); Text("Choose") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (hidden.isEmpty()) InfoRow("No hidden apps", "Hidden apps stay installed and searchable if you allow it in Advanced.")
        hidden.mapNotNull { k -> apps.firstOrNull { it.key == k } }.forEach { app ->
            InfoRow(app.label, null) {
                IconButton(onClick = { session.updateLayout("Unhide ${app.label}") { it.copy(hiddenApps = it.hiddenApps - app.key) } }) { Icon(Icons.Outlined.Visibility, "Show again") }
            }
        }
    }
    if (picking) AppPickerDialog("Hide apps", apps.filter { it.builtIn != BuiltInApp.FLYER }, onDismiss = { picking = false }, multiSelect = hidden) { app ->
        session.updateLayout("Toggle hidden ${app.label}") { l -> l.copy(hiddenApps = if (app.key in l.hiddenApps) l.hiddenApps - app.key else l.hiddenApps + app.key) }
    }
}

@Composable
private fun DrawerGroupsSection(session: FlyerSession, apps: List<AppEntry>) {
    val groups = session.draft.layout.drawerGroups
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DrawerGroup?>(null) }
    var renaming by remember { mutableStateOf<DrawerGroup?>(null) }
    SectionHeader("My drawer groups") {
        TextButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("New group") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (groups.isEmpty()) InfoRow("No groups yet", "Groups appear as tabs in the app drawer.")
        groups.forEachIndexed { i, g ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(g.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${g.apps.size} apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = g }) { Icon(Icons.Outlined.Add, "Choose apps") }
                IconButton(onClick = { renaming = g }) { Icon(Icons.Outlined.Edit, "Rename") }
                IconButton(onClick = { session.updateLayout("Move group") { l -> l.copy(drawerGroups = l.drawerGroups.toMutableList().apply { if (i > 0) add(i - 1, removeAt(i)) }) } }, enabled = i > 0) {
                    Icon(Icons.Outlined.ArrowUpward, "Move up")
                }
                IconButton(onClick = { session.updateLayout("Delete group") { l -> l.copy(drawerGroups = l.drawerGroups.filterNot { it.id == g.id }) } }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
            if (i < groups.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
    if (creating) NameDialog("New group", "", "Create", onDismiss = { creating = false }) { name ->
        creating = false
        session.updateLayout("Create group $name") { it.copy(drawerGroups = it.drawerGroups + DrawerGroup(name = name)) }
    }
    renaming?.let { g ->
        NameDialog("Rename group", g.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            session.updateLayout("Rename group") { l -> l.copy(drawerGroups = l.drawerGroups.map { if (it.id == g.id) it.copy(name = name) else it }) }
        }
    }
    editing?.let { g ->
        val current = session.draft.layout.drawerGroups.firstOrNull { it.id == g.id } ?: return@let
        AppPickerDialog("Apps in ${g.name}", apps, onDismiss = { editing = null }, multiSelect = current.apps.toSet()) { app ->
            session.updateLayout("Edit group ${g.name}") { l ->
                l.copy(drawerGroups = l.drawerGroups.map { if (it.id == g.id) it.copy(apps = if (app.key in it.apps) it.apps - app.key else it.apps + app.key) else it })
            }
        }
    }
}

// ───────────────────────── Widgets & folders ─────────────────────────

@Composable
private fun WidgetsSection(controller: FlyerController, session: FlyerSession) {
    val placed = session.draft.layout.pages.flatMapIndexed { i, p -> p.items.filter { it is WidgetItem || it is BuiltInWidgetItem }.map { i to it } }
    SectionHeader("Widgets on your home screen") {
        TextButton(onClick = { controller.navigate(FlyerRoute.WidgetPicker) }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Add widget") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (placed.isEmpty()) InfoRow("No widgets yet", "Add native app widgets or the launcher's own clock, date, search and Flyer widgets.")
        placed.forEach { (page, item) ->
            val name = when (item) {
                is BuiltInWidgetItem -> builtInTitle(item.kind)
                is WidgetItem -> item.provider.substringAfterLast('.').substringAfterLast('/')
                else -> "Widget"
            }
            InfoRow("$name · ${item.cell.spanX}×${item.cell.spanY}", "Page ${page + 1}", onClick = { controller.navigate(FlyerRoute.LiveEditor(item.id)) }) {
                IconButton(onClick = { session.updateLayout("Remove widget") { WorkspaceEngine.removeItem(it, item.id) } }) { Icon(Icons.Outlined.Delete, "Remove") }
            }
        }
    }
}

@Composable
private fun FoldersSection(controller: FlyerController, session: FlyerSession) {
    val l = session.draft.layout
    val folders = (l.pages.flatMap { it.items } + l.dock + l.desktop.items).filterIsInstance<FolderItem>()
    SectionHeader("Your folders")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (folders.isEmpty()) InfoRow("No folders yet", "Drop one app onto another on the home screen to create a folder.")
        folders.forEach { f ->
            InfoRow(f.name, "${f.apps.size} apps · tap to style this folder", onClick = { controller.navigate(FlyerRoute.LiveEditor(f.id)) }) { Icon(Icons.Outlined.Edit, null) }
        }
    }
}

// ───────────────────────── Typography ─────────────────────────

@Composable
private fun FontImportSection(session: FlyerSession, core: LauncherCore) {
    val platform = LocalFlyerPlatform.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val path = session.draft.config.typography.customFontPath
    SectionHeader("Custom font")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow(if (path != null) "Custom font imported" else "Import a .ttf or .otf file", error ?: "The font is copied into the launcher; the original file can be deleted.") {
            Button(onClick = {
                platform.pickFont { uri ->
                    if (uri != null) scope.launch {
                        val imported = core.persistence.importMedia(uri, "fonts", "ttf")
                        val ok = imported != null && withContext(Dispatchers.IO) { runCatching { Typeface.createFromFile(File(imported)) != Typeface.DEFAULT }.getOrDefault(false) }
                        if (ok) {
                            error = null
                            session.updateConfig("Custom font", null) { it.copy(typography = it.typography.copy(font = FontChoice.CUSTOM, customFontPath = imported)) }
                        } else error = "That file is not a font Android can read."
                    }
                }
            }) { Text("Import") }
        }
        if (path != null) {
            val family = remember(path) { runCatching { FontFamily(Typeface.createFromFile(File(path))) }.getOrNull() }
            Text("The quick brown fox jumps over the lazy dog", fontFamily = family, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
        }
    }
}

// ───────────────────────── Desktop ─────────────────────────

@Composable
private fun TaskbarPinsSection(session: FlyerSession, apps: List<AppEntry>) {
    val pins = session.draft.layout.desktop.taskbarPins
    var picking by remember { mutableStateOf(false) }
    SectionHeader("Taskbar apps") {
        TextButton(onClick = { picking = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Pin apps") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (pins.isEmpty()) InfoRow("Nothing pinned", "Drag an app onto the taskbar or pin it here.")
        pins.forEachIndexed { i, key ->
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconImage(rememberAppIcon(key, 32.dp), 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(apps.firstOrNull { it.key == key }?.label ?: key, modifier = Modifier.weight(1f))
                IconButton(onClick = { session.updateLayout("Move pin") { l -> l.copy(desktop = l.desktop.copy(taskbarPins = l.desktop.taskbarPins.toMutableList().apply { add(i - 1, removeAt(i)) })) } }, enabled = i > 0) {
                    Icon(Icons.Outlined.ArrowUpward, "Move left")
                }
                IconButton(onClick = { session.updateLayout("Unpin") { l -> l.copy(desktop = l.desktop.copy(taskbarPins = l.desktop.taskbarPins - key)) } }) { Icon(Icons.Outlined.Delete, "Unpin") }
            }
        }
    }
    if (picking) AppPickerDialog("Taskbar apps", apps, onDismiss = { picking = false }, multiSelect = pins.toSet()) { app ->
        session.updateLayout("Pin ${app.label}") { l ->
            val p = l.desktop.taskbarPins
            l.copy(desktop = l.desktop.copy(taskbarPins = if (app.key in p) p - app.key else p + app.key))
        }
    }
}

// ───────────────────────── Performance ─────────────────────────

@Composable
private fun PerformanceSection(core: LauncherCore) {
    var cleared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SectionHeader("Maintenance")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow(
            "Device",
            buildString {
                append(if (core.traits.lowRam) "Low-RAM device · " else "")
                append(if (core.traits.blurSupported) "blur supported" else "no blur support")
                append(if (core.traits.systemAnimationsOff) " · system animations off" else "")
            },
        )
        InfoRow("Clear icon cache", if (cleared) "Cleared. Icons will be re-rendered as needed." else "Frees memory used by rendered icons.") {
            OutlinedButton(onClick = { core.icons.clear(); cleared = true }) { Text("Clear") }
        }
        InfoRow("Reset usage statistics", "Used for \"Most used\" sorting and the recent row.") {
            OutlinedButton(onClick = { scope.launch { core.persistence.clearUsage() } }) { Text("Reset") }
        }
    }
}

// ───────────────────────── Advanced ─────────────────────────

@Composable
private fun AdvancedSection(session: FlyerSession, core: LauncherCore) {
    val platform = LocalFlyerPlatform.current
    val isDefault = remember { platform.isDefaultLauncher() }
    val accessibility = remember { platform.isAccessibilityHelperEnabled() }
    var confirmReset by remember { mutableStateOf(false) }
    SectionHeader("Home app")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow(
            if (isDefault) "Flyer Launcher is your default home app" else "Make Flyer Launcher your home app",
            "Needed for the home button, app shortcuts and pinned shortcuts.",
        ) { if (!isDefault) Button(onClick = { platform.requestDefaultLauncher() }) { Text("Set default") } }
        if (com.flyer.launcher.core.gestures.GestureHelperBridge.includedInBuild) {
            InfoRow(
                if (accessibility) "Gesture helper enabled" else "Gesture helper (optional)",
                "Android only lets an accessibility service lock the screen, open recents or take screenshots. Flyer's helper does nothing else and reads no screen content.",
            ) { OutlinedButton(onClick = { platform.openAccessibilitySettings() }) { Text(if (accessibility) "Manage" else "Enable") } }
        }
    }

    SectionHeader("Built-in apps")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Built-in apps are part of the launcher. Removing their shortcut never removes the app: Flyer is always in the app drawer and in the home screen's long-press menu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
        )
        BuiltInApp.entries.forEach { b ->
            val placed = b.key in WorkspaceEngine.placedAppKeys(session.draft.layout)
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconImage(rememberAppIcon(b.key, 36.dp), 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.label, style = MaterialTheme.typography.bodyLarge)
                    Text(if (placed) "On your home screen" else b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (placed) {
                    OutlinedButton(onClick = {
                        session.updateLayout("Remove ${b.label} shortcut") { l -> WorkspaceEngine.removeApp(l, b.key) }
                    }) { Text("Remove") }
                } else {
                    Button(onClick = {
                        session.update("Restore ${b.label} shortcut") { doc -> doc.copy(layout = WorkspaceEngine.addApp(doc.layout, b.key, doc.config)) }
                    }) { Text("Restore") }
                }
            }
        }
    }

    SectionHeader("Start over")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow("Rebuild default layout", "Replaces pages, dock and desktop with a fresh arrangement of your apps. Settings are kept.") {
            OutlinedButton(onClick = { session.updateLayout("Rebuild layout") { core.buildDefaultLayout(session.draft.config) } }) { Text("Rebuild") }
        }
        InfoRow("Reset everything", "All settings and the layout go back to a fresh install.") {
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset") }
        }
    }
    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset everything?") },
            text = { Text("This resets the draft. Nothing changes until you press Apply, and you can undo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    val fresh = com.flyer.launcher.core.config.FlyerConfig()
                    session.replace(com.flyer.launcher.core.persistence.LauncherDocument(config = fresh, layout = core.buildDefaultLayout(fresh)), "Reset everything")
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
