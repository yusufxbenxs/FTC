package com.flyer.launcher.flyer.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.SectionHeader
import com.flyer.launcher.flyer.preview.PreviewKind
import com.flyer.launcher.flyer.preview.SurfacePreview
import com.flyer.launcher.flyer.settings.SettingsRegistry
import com.flyer.launcher.launcher.ui.clickableNoRipple

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(controller: FlyerController, session: FlyerSession, apps: List<AppEntry>, padding: PaddingValues) {
    val doc = session.preview
    val platform = LocalFlyerPlatform.current
    val isDefault = remember { platform.isDefaultLauncher() }
    val insets = WindowInsets.systemBars.asPaddingValues()
    var page by remember { mutableIntStateOf(0) }
    val items = doc.layout.pages.flatMap { it.items } + doc.layout.dock
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + padding.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlyerCard {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SurfacePreview(
                        PreviewKind.HOME, doc, apps, page, insets, scale = 0.26f,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your home screen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${doc.layout.pages.size} pages · ${items.size} items · ${items.count { it is FolderItem }} folders · " +
                                "${items.count { it is WidgetItem || it is BuiltInWidgetItem }} widgets · ${apps.count { it.builtIn == null }} apps",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusLine(
                            if (session.isDirty) "Unapplied changes in preview" else "Everything is applied",
                            ok = !session.isDirty,
                        )
                        StatusLine(if (isDefault) "Flyer Launcher is your home app" else "Not your default home app yet", ok = isDefault)
                        if (doc.layout.pages.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                doc.layout.pages.indices.forEach { i ->
                                    Box(
                                        Modifier.size(if (i == page) 10.dp else 8.dp).clip(CircleShape)
                                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    )
                                }
                            }
                            Text("Page ${page + 1}: tap to see next", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 2.dp).clickableNoRipple { page = (page + 1) % doc.layout.pages.size })
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { controller.navigate(FlyerRoute.LiveEditor()) }) {
                                Icon(Icons.Outlined.DashboardCustomize, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Live editor")
                            }
                            if (!isDefault) FilledTonalButton(onClick = { platform.requestDefaultLauncher() }) {
                                Icon(Icons.Outlined.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Set as default")
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Outlined.Brush, "Themes") { controller.navigate(FlyerRoute.Category(FlyerCategory.THEMES)) }
                QuickAction(Icons.Outlined.Widgets, "Add widget") { controller.navigate(FlyerRoute.WidgetPicker) }
                QuickAction(Icons.Outlined.Backup, "Backup") { controller.navigate(FlyerRoute.Category(FlyerCategory.BACKUP)) }
                QuickAction(Icons.Outlined.Search, "Find a setting") { controller.navigate(FlyerRoute.Search) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Customize", Modifier.padding(start = 0.dp)) }
        items(FlyerCategory.entries.toList(), key = { it.name }) { c ->
            val modified = SettingsRegistry.modifiedCount(c, doc.config)
            FlyerCard(onClick = { controller.navigate(FlyerRoute.of(c)) }) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(c.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.weight(1f))
                        if (modified > 0) Badge { Text("$modified") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(c.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (session.timeline.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Recent changes in this session", Modifier.padding(start = 0.dp)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlyerCard {
                    session.timeline.take(8).forEachIndexed { i, label ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
}
