package com.flyer.launcher.launcher.overlays

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.flyer.launcher.core.model.BuiltInWidgetKind
import com.flyer.launcher.core.widgets.WidgetProvider
import com.flyer.launcher.launcher.render.LocalLauncherCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface WidgetChoice {
    data class Native(val provider: WidgetProvider) : WidgetChoice
    data class BuiltIn(val kind: BuiltInWidgetKind) : WidgetChoice
}

fun builtInTitle(kind: BuiltInWidgetKind) = when (kind) {
    BuiltInWidgetKind.CLOCK -> "Clock"
    BuiltInWidgetKind.DATE -> "Date"
    BuiltInWidgetKind.SEARCH -> "Search bar"
    BuiltInWidgetKind.FLYER_QUICK_PANEL -> "Flyer quick panel"
}

fun builtInDescription(kind: BuiltInWidgetKind) = when (kind) {
    BuiltInWidgetKind.CLOCK -> "Digital, analog or stacked · styled in Flyer › Widgets"
    BuiltInWidgetKind.DATE -> "Today's date in your format"
    BuiltInWidgetKind.SEARCH -> "Opens launcher search"
    BuiltInWidgetKind.FLYER_QUICK_PANEL -> "Shortcuts into Flyer right on the home screen"
}

/** Lists every widget provider of every profile, plus the launcher's own built-in widgets. */
@Composable
fun WidgetPicker(onPick: (WidgetChoice) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val core = LocalLauncherCore.current
    var query by remember { mutableStateOf("") }
    val providers by produceState<List<WidgetProvider>?>(null) {
        value = withContext(Dispatchers.IO) { core.widgets.providers() }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text("Widgets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search widgets") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val list = providers
            val grouped = remember(list, query) {
                list.orEmpty()
                    .filter { query.isBlank() || it.label.contains(query, true) || it.appLabel.contains(query, true) }
                    .groupBy { it.appLabel + if (it.userSerial != 0L) " (work)" else "" }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Flyer Launcher", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(BuiltInWidgetKind.entries.filter { query.isBlank() || builtInTitle(it).contains(query, true) }) { kind ->
                            WidgetCard(builtInTitle(kind), builtInDescription(kind), null) { onPick(WidgetChoice.BuiltIn(kind)) }
                        }
                    }
                }
                if (list == null) item { Text("Loading widgets…") }
                grouped.forEach { (app, items) ->
                    item(key = app) {
                        Text(app, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items, key = { it.key }) { p ->
                                val preview = rememberProviderPreview(p)
                                val dm = LocalContext.current.resources.displayMetrics
                                val sizeLabel = "${(p.info.minWidth / dm.density).toInt()} × ${(p.info.minHeight / dm.density).toInt()} dp"
                                WidgetCard(p.label, sizeLabel, preview) { onPick(WidgetChoice.Native(p)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberProviderPreview(p: WidgetProvider): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, p.key) {
        value = withContext(Dispatchers.IO) {
            val density = context.resources.displayMetrics.densityDpi
            val d = runCatching { p.info.loadPreviewImage(context, density) }.getOrNull()
                ?: runCatching { p.info.loadIcon(context, density) }.getOrNull()
            d?.let { runCatching { it.toBitmap(it.intrinsicWidth.coerceIn(1, 600), it.intrinsicHeight.coerceIn(1, 600)).asImageBitmap() }.getOrNull() }
        }
    }
    return bitmap
}

@Composable
private fun WidgetCard(title: String, subtitle: String, preview: ImageBitmap?, onClick: () -> Unit) {
    Column(
        Modifier.width(168.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) Image(preview, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
            else Icon(Icons.Outlined.Widgets, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
