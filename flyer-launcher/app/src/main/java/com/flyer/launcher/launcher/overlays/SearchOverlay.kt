package com.flyer.launcher.launcher.overlays

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ShortcutInfo
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.apps.AppSorting
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.withOpacity
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.flyer.settings.SettingsRegistry
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.LocalAppIndex
import com.flyer.launcher.launcher.render.LocalAppOverrides
import com.flyer.launcher.launcher.render.labelFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Global launcher search: apps, app shortcuts, Flyer settings and the web. */
@Composable
fun SearchOverlay(ui: LauncherUiState, apps: List<AppEntry>, layout: HomeLayout, onClose: () -> Unit) {
    val config = LocalFlyerConfig.current
    val s = config.search
    val actions = LocalLauncherActions.current
    val context = LocalContext.current
    val index = LocalAppIndex.current
    val overrides = LocalAppOverrides.current
    val query = ui.searchQuery
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val appHits = remember(query, apps, layout.hiddenApps, overrides, s) {
        if (!s.searchApps) emptyList() else apps
            .filter { it.key !in layout.hiddenApps || config.advanced.showHiddenAppsInSearch }
            .map { it to AppSorting.score(labelFor(it.key, index, overrides), query, s.fuzzyMatching) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(s.maxResults)
            .map { it.first }
    }
    val shortcuts by produceState<List<ShortcutInfo>>(emptyList(), s.searchShortcuts) {
        value = if (s.searchShortcuts) withContext(Dispatchers.IO) { ui.core.appsEngine.allShortcuts() } else emptyList()
    }
    val shortcutHits = remember(query, shortcuts) {
        if (query.isBlank()) emptyList() else shortcuts
            .map { it to AppSorting.score(it.shortLabel?.toString().orEmpty(), query, s.fuzzyMatching) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(6).map { it.first }
    }
    val settingHits = remember(query, config) {
        if (!s.searchFlyerSettings || query.isBlank()) emptyList() else SettingsRegistry.search(query, config, limit = 5)
    }

    fun webSearch() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onClose()
        }.onFailure { actions.showMessage("No app can handle web searches") }
    }

    val fg = Color.White
    Column(
        Modifier.fillMaxSize().background(config.drawer.background.withOpacity(0.96f))
            .windowInsetsPadding(WindowInsets.systemBars).imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search", tint = fg) }
            TextField(
                value = query,
                onValueChange = { ui.searchQuery = it },
                singleLine = true,
                placeholder = { Text("Search apps, shortcuts, settings…") },
                shape = RoundedCornerShape(s.barCornerDp.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f), unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = fg, unfocusedTextColor = fg,
                    focusedPlaceholderColor = fg.copy(alpha = 0.6f), unfocusedPlaceholderColor = fg.copy(alpha = 0.6f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val first = appHits.firstOrNull()
                    if (first != null && query.isNotBlank()) { actions.launchApp(first.key, null); onClose() } else if (s.webFallback && query.isNotBlank()) webSearch()
                }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (appHits.isNotEmpty()) {
                item { SearchHeader("Apps") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(appHits, key = { it.key }) { app ->
                            Box(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { actions.launchApp(app.key, null); onClose() }.padding(4.dp), contentAlignment = Alignment.Center) {
                                AppTile(app.key, 52.dp, true, config.icons.label.copy(sizeSp = 11f, maxLines = 2), labelColor = fg)
                            }
                        }
                    }
                }
            }
            if (shortcutHits.isNotEmpty()) {
                item { SearchHeader("Shortcuts") }
                items(shortcutHits, key = { it.`package` + "/" + it.id }) { sc ->
                    SearchRow(Icons.AutoMirrored.Outlined.OpenInNew, sc.shortLabel?.toString() ?: sc.id, labelFor(apps.firstOrNull { it.packageName == sc.`package` }?.key ?: "", index, overrides)) {
                        ui.core.appsEngine.startShortcut(sc, null, null); onClose()
                    }
                }
            }
            if (settingHits.isNotEmpty()) {
                item { SearchHeader("Flyer settings") }
                items(settingHits, key = { it.spec.id }) { hit ->
                    SearchRow(Icons.Outlined.Tune, hit.spec.title, "${hit.spec.category.title} · ${hit.spec.display(config)}") {
                        onClose()
                        actions.openFlyer(FlyerRoute.Category(hit.spec.category, highlight = hit.spec.id))
                    }
                }
            }
            if (s.webFallback && query.isNotBlank()) {
                item { SearchRow(Icons.Outlined.Language, "Search the web for \"$query\"", null) { webSearch() } }
            }
            if (query.isNotBlank() && appHits.isEmpty() && shortcutHits.isEmpty() && settingHits.isEmpty() && !s.webFallback) {
                item { Text("No results", color = fg.copy(alpha = 0.7f)) }
            }
        }
    }
}

@Composable
private fun SearchHeader(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SearchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}
