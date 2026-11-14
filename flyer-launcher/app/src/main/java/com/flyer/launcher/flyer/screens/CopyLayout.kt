package com.flyer.launcher.flyer.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.LayoutImporter
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.InfoRow
import com.flyer.launcher.flyer.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home Screen module card. */
@Composable
fun CopyOriginalLayoutCard(session: FlyerSession, core: LauncherCore) {
    var open by remember { mutableStateOf(false) }
    SectionHeader("Copy original home screen")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow(
            "Copy original home screen layout",
            "Rebuilds the pages, dock, folders and widgets from your phone's original launcher. You review it before applying.",
            onClick = { open = true },
        ) { Icon(Icons.Outlined.ContentCopy, null) }
    }
    if (open) CopyOriginalLayoutDialog(session, core) { open = false }
}

/** Pick a launcher, import its layout into the Flyer draft. */
@Composable
fun CopyOriginalLayoutDialog(session: FlyerSession, core: LauncherCore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val launchers by produceState<List<LayoutImporter.SourceLauncher>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { core.importer.launchers() }.getOrDefault(emptyList()) }
    }
    var chosen by remember { mutableStateOf<LayoutImporter.SourceLauncher?>(null) }
    var useGrid by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<LayoutImporter.Imported?>(null) }
    val selected = chosen ?: launchers?.firstOrNull()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (result == null) "Copy original home screen" else "Layout copied") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = result
                when {
                    r != null -> {
                        Text(
                            when (r.origin) {
                                LayoutImporter.Origin.CURRENT_LAYOUT -> "Copied your current home screen from ${r.source.label}."
                                LayoutImporter.Origin.FACTORY_LAYOUT ->
                                    "${r.source.label} doesn't let other launchers read your current arrangement, so Flyer rebuilt its original (factory) home screen instead."
                            },
                        )
                        Text(
                            "${r.pages.size} pages · ${r.appCount} apps · ${r.folderCount} folders · ${r.dock.size} in the dock" +
                                if (r.widgetCount > 0) " · ${r.widgetCount} widgets (tap each once to restore it)" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (r.skipped > 0) Text("${r.skipped} items were skipped because their apps aren't installed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("It's loaded as unapplied changes. Check the preview, then press Apply.", style = MaterialTheme.typography.bodySmall)
                    }
                    launchers == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Looking for launchers…")
                    }
                    launchers!!.isEmpty() -> Text("No other launcher is installed on this phone.")
                    else -> {
                        Text("Copy from:", style = MaterialTheme.typography.labelLarge)
                        launchers!!.forEach { l ->
                            Row(Modifier.fillMaxWidth().clickable { chosen = l }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = l == selected, onClick = { chosen = l })
                                Column {
                                    Text(l.label)
                                    Text(if (l.isSystem) "Built into your phone" else l.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable { useGrid = !useGrid }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(useGrid, { useGrid = it })
                            Text("Also use its grid size")
                        }
                        Text(
                            "Replaces your pages and dock in the draft. Your desktop-mode layout, hidden apps, groups and custom icons stay. Flyer stays on your home screen.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Copying…")
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            if (result != null) Button(onClick = onDismiss) { Text("Done") }
            else Button(
                enabled = selected != null && !busy,
                onClick = {
                    val source = selected ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) { runCatching { core.importer.import(source) } }
                        busy = false
                        imported.onSuccess { r ->
                            session.update("Copy home screen from ${r.source.label}") { doc ->
                                val cols = r.columns?.takeIf { useGrid }?.coerceIn(ConfigValidator.Ranges.columns)
                                val rows = r.rows?.takeIf { useGrid }?.coerceIn(ConfigValidator.Ranges.rows)
                                doc.copy(
                                    layout = r.applyTo(doc.layout),
                                    config = doc.config.copy(home = doc.config.home.copy(columns = cols ?: doc.config.home.columns, rows = rows ?: doc.config.home.rows)),
                                )
                            }
                            result = r
                        }.onFailure { error = it.message ?: "Couldn't copy the layout" }
                    }
                },
            ) { Text("Copy") }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
