package com.flyer.launcher.flyer.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.config.GestureType
import com.flyer.launcher.core.gestures.GestureCatalog
import com.flyer.launcher.core.persistence.BackupKind
import com.flyer.launcher.core.persistence.DocumentCodec
import com.flyer.launcher.core.persistence.InvalidBackupException
import com.flyer.launcher.core.persistence.LauncherDocument
import com.flyer.launcher.core.persistence.PersistenceEngine
import com.flyer.launcher.core.theme.ThemePresets
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.flyer.components.AppPickerDialog
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.InfoRow
import com.flyer.launcher.flyer.components.NameDialog
import com.flyer.launcher.flyer.components.SectionHeader
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────── Gestures ─────────────────────────

@Composable
fun GestureEditor(session: FlyerSession, apps: List<AppEntry>) {
    val platform = LocalFlyerPlatform.current
    val bindings = session.draft.config.gestures.bindings
    var editing by remember { mutableStateOf<GestureType?>(null) }
    var pickingAppFor by remember { mutableStateOf<GestureType?>(null) }
    val helperOn = remember { platform.isAccessibilityHelperEnabled() }

    SectionHeader("Gestures")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        GestureType.entries.forEach { g ->
            val action = bindings[g] ?: GestureAction()
            val target = if (action.type == ActionType.LAUNCH_APP) apps.firstOrNull { it.key == action.target }?.label?.let { " · $it" }.orEmpty() else ""
            val warn = GestureCatalog.needsAccessibility(action.type) && !helperOn
            InfoRow(
                GestureCatalog.title(g),
                GestureCatalog.title(action.type) + target + if (warn) " · needs the gesture helper" else "",
                onClick = { editing = g },
            ) { if (warn) Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.tertiary) }
        }
    }
    if (!helperOn) {
        FlyerCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            InfoRow(
                "Lock screen, recents & screenshots",
                "A launcher can't perform these by itself. Enable the optional \"Flyer Launcher gestures\" accessibility helper to use them; it only performs these actions when you trigger them.",
            ) { OutlinedButton(onClick = { platform.openAccessibilitySettings() }) { Text("Enable") } }
        }
    }

    editing?.let { g ->
        val current = bindings[g]?.type ?: ActionType.NONE
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(GestureCatalog.title(g)) },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    ActionType.entries.filter { Build.VERSION.SDK_INT >= GestureCatalog.minSdk(it) }.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                editing = null
                                if (a == ActionType.LAUNCH_APP) pickingAppFor = g
                                else session.updateConfig("${GestureCatalog.title(g)}: ${GestureCatalog.title(a)}", null) { c ->
                                    c.copy(gestures = c.gestures.copy(bindings = c.gestures.bindings + (g to GestureAction(a))))
                                }
                            }.padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(if (a == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(GestureCatalog.title(a), fontWeight = if (a == current) FontWeight.Bold else FontWeight.Normal)
                                if (GestureCatalog.needsAccessibility(a)) Text("Uses the optional gesture helper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (g == GestureType.LONG_PRESS_EMPTY && a == ActionType.NONE) Text("Falls back to the home menu so Flyer stays reachable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
        )
    }
    pickingAppFor?.let { g ->
        AppPickerDialog("App for ${GestureCatalog.title(g)}", apps, onDismiss = { pickingAppFor = null }) { app ->
            pickingAppFor = null
            session.updateConfig("${GestureCatalog.title(g)}: ${app.label}", null) { c ->
                c.copy(gestures = c.gestures.copy(bindings = c.gestures.bindings + (g to GestureAction(ActionType.LAUNCH_APP, app.key))))
            }
        }
    }
}

// ───────────────────────── Themes ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesSection(session: FlyerSession, core: LauncherCore) {
    val platform = LocalFlyerPlatform.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<List<PersistenceEngine.Snapshot>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) { saved = core.persistence.listSnapshots().filter { it.kind == BackupKind.THEME } }

    SectionHeader("Built-in themes")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePresets.all.forEach { preset ->
            Card(
                onClick = { session.updateConfig("Theme: ${preset.name}", null) { preset.apply(it) } },
                modifier = Modifier.width(160.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        preset.swatch.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(com.flyer.launcher.flyer.components.Brushes.checker()).background(c.color)) }
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Text(
        "Themes change the look only — your pages, dock, gestures and behaviour settings stay as they are. Tap a theme to preview it, then Apply.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )

    SectionHeader("Your themes") {
        TextButton(onClick = { naming = true }) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(4.dp)); Text("Save current") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (saved.isEmpty()) InfoRow("No saved themes", "Save the current look to switch back to it anytime.")
        saved.forEach { snap ->
            InfoRow(snap.name, "Saved ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snap.savedAt))}") {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { core.persistence.readSnapshot(snap) }.onSuccess { b ->
                                session.updateConfig("Theme: ${snap.name}", null) { ThemePresets.applyVisuals(it, b.document.config) }
                            }.onFailure { message = it.message }
                        }
                    }) { Text("Use") }
                    IconButton(onClick = { scope.launch { core.persistence.deleteSnapshot(snap); refresh++ } }) { Icon(Icons.Outlined.Delete, "Delete") }
                }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                platform.createDocument("flyer-theme.json") { uri ->
                    if (uri != null) {
                        val ok = platform.writeText(uri, core.encodeBackup(session.preview, BackupKind.THEME, "Exported theme"))
                        message = if (ok) "Theme exported" else "Could not write the file"
                    }
                }
            }) { Icon(Icons.Outlined.Upload, null); Spacer(Modifier.width(6.dp)); Text("Export theme") }
            OutlinedButton(onClick = {
                platform.openDocument { uri ->
                    val text = uri?.let { platform.readText(it) } ?: return@openDocument
                    message = try {
                        val b = core.decodeBackup(text)
                        session.updateConfig("Import theme ${b.name}", null) { ThemePresets.applyVisuals(it, b.document.config) }
                        "Theme \"${b.name}\" loaded — press Apply to keep it"
                    } catch (e: InvalidBackupException) { e.message }
                }
            }) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(6.dp)); Text("Import theme") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) }
    }
    if (naming) NameDialog("Save theme", "My theme", onDismiss = { naming = false }) { name ->
        naming = false
        scope.launch { core.persistence.saveSnapshot(session.preview, name, BackupKind.THEME); refresh++ }
    }
}

// ───────────────────────── Backup & restore ─────────────────────────

@Composable
fun BackupSection(session: FlyerSession, core: LauncherCore) {
    val platform = LocalFlyerPlatform.current
    val scope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf<List<PersistenceEngine.Snapshot>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<DocumentCodec.Backup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) { snapshots = core.persistence.listSnapshots().filter { it.kind == BackupKind.FULL } }

    SectionHeader("Export & import")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow("Export a backup file", "Your whole setup (settings, pages, dock, desktop, groups, custom labels) as a JSON file you can keep or move to another device.") {
            Button(onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                platform.createDocument("flyer-backup-$stamp.json") { uri ->
                    if (uri != null) {
                        val ok = platform.writeText(uri, core.encodeBackup(session.preview, BackupKind.FULL, "Backup $stamp"))
                        message = if (ok) "Backup saved" else "Could not write the file"
                    }
                }
            }) { Text("Export") }
        }
        InfoRow("Import a backup file", "Loads it into Flyer so you can review it in the previews before applying.") {
            OutlinedButton(onClick = {
                platform.openDocument { uri ->
                    val text = uri?.let { platform.readText(it) }
                    if (text == null) { if (uri != null) message = "Could not read the file"; return@openDocument }
                    try { pending = core.decodeBackup(text) } catch (e: InvalidBackupException) { message = e.message }
                }
            }) { Text("Import") }
        }
        Text(
            "Widgets are tied to this device; after importing on another device they show as placeholders you can tap to restore.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) }
    }

    SectionHeader("Snapshots on this device") {
        TextButton(onClick = { naming = true }) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(4.dp)); Text("Save snapshot") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (snapshots.isEmpty()) InfoRow("No snapshots", "Save a snapshot before big changes so you can go back.")
        snapshots.forEach { snap ->
            InfoRow(snap.name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snap.savedAt))) {
                Row {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { core.persistence.readSnapshot(snap) }.onSuccess { pending = it }.onFailure { message = it.message }
                        }
                    }) { Icon(Icons.Outlined.Restore, "Restore") }
                    IconButton(onClick = { scope.launch { core.persistence.deleteSnapshot(snap); refresh++ } }) { Icon(Icons.Outlined.Delete, "Delete") }
                }
            }
        }
    }

    if (naming) NameDialog("Save snapshot", "Snapshot", onDismiss = { naming = false }) { name ->
        naming = false
        scope.launch { core.persistence.saveSnapshot(session.preview, name, BackupKind.FULL); refresh++; message = "Snapshot \"$name\" saved" }
    }
    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Load \"${b.name}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (b.kind == BackupKind.FULL) "Full setup: ${b.document.layout.pages.size} pages, ${b.document.layout.dock.size} dock items." else "Theme only (looks).")
                    b.warnings.forEach { w -> Text("• $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                    Text("It is loaded as unapplied changes; review the previews, then press Apply.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    if (b.kind == BackupKind.FULL) {
                        session.replace(LauncherDocument(config = b.document.config, layout = b.document.layout.copy(initialized = true)), "Load ${b.name}")
                    } else {
                        session.updateConfig("Load theme ${b.name}", null) { ThemePresets.applyVisuals(it, b.document.config) }
                    }
                    message = "\"${b.name}\" loaded — press Apply to make it live"
                }) { Text("Load") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
