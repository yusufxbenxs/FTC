package com.flyer.launcher.flyer.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.SectionHeader
import com.flyer.launcher.flyer.components.SettingRow
import com.flyer.launcher.flyer.preview.PreviewKind
import com.flyer.launcher.flyer.preview.SurfacePreview
import com.flyer.launcher.flyer.preview.rememberScreenSize
import com.flyer.launcher.flyer.settings.SettingLevel
import com.flyer.launcher.flyer.settings.SettingSpec
import com.flyer.launcher.flyer.settings.SettingsRegistry

fun previewKindFor(category: FlyerCategory) = when (category) {
    FlyerCategory.DRAWER -> PreviewKind.DRAWER
    FlyerCategory.FOLDERS -> PreviewKind.FOLDER
    FlyerCategory.DESKTOP -> PreviewKind.DESKTOP
    FlyerCategory.LOCK -> PreviewKind.LOCK
    FlyerCategory.GESTURES, FlyerCategory.PERFORMANCE, FlyerCategory.ADVANCED, FlyerCategory.BACKUP -> PreviewKind.NONE
    else -> PreviewKind.HOME
}

private sealed interface Row {
    data object Preview : Row
    data object Custom : Row
    data class Header(val title: String) : Row
    data class Setting(val spec: SettingSpec) : Row
    data object Footer : Row
}

@Composable
fun CategoryScreen(
    controller: FlyerController,
    session: FlyerSession,
    core: LauncherCore,
    apps: List<AppEntry>,
    category: FlyerCategory,
    highlight: String?,
    padding: PaddingValues,
) {
    val config = session.draft.config
    val traits = LocalDeviceTraits.current
    val showAdvanced = config.advanced.showAdvancedSettings
    val specs = SettingsRegistry.forCategory(category).filter {
        it.id == highlight || (it.visibleWhen(config) && (showAdvanced || it.level == SettingLevel.BASIC))
    }
    val hiddenAdvanced = if (showAdvanced) 0 else SettingsRegistry.forCategory(category).count { it.level == SettingLevel.ADVANCED }
    val kind = previewKindFor(category)
    val listState = rememberLazyListState()
    val onEdit = { next: com.flyer.launcher.core.config.FlyerConfig, label: String, key: String? -> session.updateConfig(label, key) { next } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && kind != PreviewKind.NONE
        val paneHeight = maxHeight - 48.dp
        val rows = buildList {
            if (!wide && kind != PreviewKind.NONE) add(Row.Preview)
            add(Row.Custom)
            var section: String? = null
            specs.forEach { s ->
                if (s.section != section) { section = s.section; add(Row.Header(s.section)) }
                add(Row.Setting(s))
            }
            if (specs.isNotEmpty()) add(Row.Footer)
        }
        LaunchedEffect(highlight, rows.size) {
            val i = rows.indexOfFirst { it is Row.Setting && it.spec.id == highlight }
            if (i >= 0) listState.animateScrollToItem(i)
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            LazyColumn(m, state = listState, contentPadding = PaddingValues(bottom = 32.dp + padding.calculateBottomPadding())) {
                items(rows, key = { r ->
                    when (r) {
                        Row.Preview -> "preview"; Row.Custom -> "custom"; Row.Footer -> "footer"
                        is Row.Header -> "h:" + r.title
                        is Row.Setting -> r.spec.id
                    }
                }) { r ->
                    when (r) {
                        Row.Preview -> PreviewPane(kind, session, apps, collapsible = true, height = 320.dp)
                        Row.Custom -> CategoryCustomSection(controller, session, core, apps, category)
                        is Row.Header -> SectionHeader(r.title)
                        is Row.Setting -> SettingRow(r.spec, config, traits, highlighted = r.spec.id == highlight, onEdit = onEdit, modifier = Modifier.padding(horizontal = 8.dp))
                        Row.Footer -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (hiddenAdvanced > 0) {
                                Text(
                                    "$hiddenAdvanced advanced settings hidden · enable them from the ⋮ menu",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            OutlinedButton(onClick = {
                                val all = SettingsRegistry.forCategory(category)
                                session.updateConfig("Reset ${category.title}", null) { c -> all.fold(c) { acc, s -> s.reset(acc) } }
                            }, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Outlined.RestartAlt, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reset ${category.title} to defaults")
                            }
                        }
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    PreviewPane(kind, session, apps, collapsible = false, height = paneHeight)
                }
                list(Modifier.weight(0.58f).fillMaxHeight())
            }
        } else list(Modifier.fillMaxSize())
    }
}

/** Live preview of the draft for a category, with page switching for the home screen. */
@Composable
fun PreviewPane(kind: PreviewKind, session: FlyerSession, apps: List<AppEntry>, collapsible: Boolean, height: Dp) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    val doc = session.preview
    val screen = rememberScreenSize()
    val density = LocalDensity.current
    val scale = with(density) { (height.toPx() / screen.height).coerceIn(0.12f, 0.6f) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    FlyerCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (session.isDirty) "Live preview · not applied yet" else "Live preview", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (kind == PreviewKind.HOME && doc.layout.pages.size > 1) {
                IconButton(onClick = { page = (page - 1 + doc.layout.pages.size) % doc.layout.pages.size }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page") }
                Text("${page + 1}/${doc.layout.pages.size}", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { page = (page + 1) % doc.layout.pages.size }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page") }
            }
            if (collapsible) IconButton(onClick = { collapsed = !collapsed }) {
                Icon(if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, if (collapsed) "Show preview" else "Hide preview")
            }
        }
        if (!collapsed) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                SurfacePreview(kind, doc, apps, page.coerceIn(0, doc.layout.pages.lastIndex), insets, scale)
            }
        }
    }
}
