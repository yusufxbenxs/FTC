package com.flyer.launcher.launcher.drawer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.apps.AppCategory
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.apps.AppSorting
import com.flyer.launcher.core.config.DrawerLayout
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.LocalLauncherFont
import com.flyer.launcher.core.theme.withOpacity
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.home.ItemMenus
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.IconImage
import com.flyer.launcher.launcher.render.labelFor
import com.flyer.launcher.launcher.render.LocalAppIndex
import com.flyer.launcher.launcher.render.LocalAppOverrides
import com.flyer.launcher.launcher.render.rememberAppIcon
import com.flyer.launcher.launcher.ui.CoordsHolder
import com.flyer.launcher.launcher.ui.ItemGestureCallbacks
import com.flyer.launcher.launcher.ui.itemGestures
import kotlinx.coroutines.launch

/** Tabs of the drawer. */
sealed interface DrawerTab {
    val title: String

    data object All : DrawerTab { override val title = "All" }
    data object Work : DrawerTab { override val title = "Work" }
    data class Category(val category: AppCategory) : DrawerTab { override val title = category.title }
    data class Group(val id: String, override val title: String) : DrawerTab
}

/** Pure list computation for the drawer (also used by Flyer's drawer preview). */
fun drawerApps(
    apps: List<AppEntry>,
    layout: HomeLayout,
    config: FlyerConfig,
    tab: DrawerTab,
    query: String,
    launchCounts: Map<String, Long>,
    labelOf: (AppEntry) -> String,
): List<AppEntry> {
    val visible = apps.filter { it.key !in layout.hiddenApps || (query.isNotBlank() && config.advanced.showHiddenAppsInSearch) }
    val base = when (tab) {
        DrawerTab.All -> if (config.drawer.separateWorkApps) visible.filter { !it.isWorkProfile } else visible
        DrawerTab.Work -> visible.filter { it.isWorkProfile }
        is DrawerTab.Category -> visible.filter { it.category == tab.category.id }
        is DrawerTab.Group -> {
            val keys = layout.drawerGroups.firstOrNull { it.id == tab.id }?.apps.orEmpty()
            keys.mapNotNull { k -> visible.firstOrNull { it.key == k } }
        }
    }
    if (query.isNotBlank()) {
        return base.map { it to AppSorting.score(labelOf(it), query, config.search.fuzzyMatching) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    return if (tab is DrawerTab.Group) base else AppSorting.sort(base, config.drawer.sort, launchCounts, labelOf)
}

fun drawerTabs(apps: List<AppEntry>, layout: HomeLayout, config: FlyerConfig): List<DrawerTab> = buildList {
    add(DrawerTab.All)
    if (config.drawer.separateWorkApps && apps.any { it.isWorkProfile }) add(DrawerTab.Work)
    if (config.drawer.showUserGroups) layout.drawerGroups.forEach { add(DrawerTab.Group(it.id, it.name)) }
    if (config.drawer.showCategories) {
        val present = apps.map { it.category }.toSet()
        AppCategory.entries.filter { it.id in present }.forEach { add(DrawerTab.Category(it)) }
    }
}

/**
 * The app drawer. In [interactive] mode apps launch and can be dragged to the home screen; Flyer
 * renders the same composable non-interactively as its drawer preview.
 */
@Composable
fun AppDrawer(
    ui: LauncherUiState?,
    apps: List<AppEntry>,
    layout: HomeLayout,
    launchCounts: Map<String, Long>,
    lastLaunched: Map<String, Long>,
    interactive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    insets: PaddingValues = WindowInsets.systemBars.asPaddingValues(),
) {
    val config = LocalFlyerConfig.current
    val d = config.drawer
    val index = LocalAppIndex.current
    val overrides = LocalAppOverrides.current
    val labelOf: (AppEntry) -> String = { labelFor(it.key, index, overrides) }
    var query by rememberSaveable { mutableStateOf("") }
    var tabState by remember { mutableStateOf<DrawerTab>(DrawerTab.All) }
    val tabs = remember(apps, layout.drawerGroups, config.drawer) { drawerTabs(apps, layout, config) }
    val tab = tabs.firstOrNull { it == tabState } ?: DrawerTab.All
    val list = remember(apps, layout.hiddenApps, layout.drawerGroups, overrides, config.drawer, config.search, tab, query, launchCounts) {
        drawerApps(apps, layout, config, tab, query, launchCounts, labelOf)
    }
    val recents = remember(lastLaunched, apps, d.columns, layout.hiddenApps) {
        if (!d.showRecentRow) emptyList()
        else lastLaunched.entries.sortedByDescending { it.value }.mapNotNull { (k, _) -> index[k] }
            .filter { it.key !in layout.hiddenApps }.take(d.columns)
    }
    val iconSize: Dp = (if (d.followIconSize) config.icons.sizeDp else d.iconSizeDp).dp
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = d.cornerRadiusDp.dp, topEnd = d.cornerRadiusDp.dp)
    val focus = remember { FocusRequester() }
    val actions = LocalLauncherActions.current

    // Overscroll at the top of the list closes the drawer.
    val density = LocalDensity.current
    val closeThreshold = with(density) { 96.dp.toPx() }
    val onCloseState = rememberUpdatedState(onClose)
    val nested = remember(gridState) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
                    pulled += available.y
                    if (pulled > closeThreshold) { pulled = 0f; onCloseState.value() }
                } else if (available.y <= 0f) pulled = 0f
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(interactive, d.autoShowKeyboard) {
        if (interactive && d.autoShowKeyboard && d.showSearch) runCatching { focus.requestFocus() }
    }

    val searchField: @Composable () -> Unit = {
        if (d.showSearch) {
            TextField(
                value = query,
                onValueChange = { query = it },
                enabled = interactive,
                singleLine = true,
                placeholder = { Text("Search ${list.size.takeIf { query.isBlank() } ?: ""} apps".replace("  ", " ")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(config.search.barCornerDp.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = d.labelColor.color, unfocusedTextColor = d.labelColor.color,
                    focusedPlaceholderColor = d.labelColor.color.copy(alpha = 0.6f), unfocusedPlaceholderColor = d.labelColor.color.copy(alpha = 0.6f),
                    disabledPlaceholderColor = d.labelColor.color.copy(alpha = 0.6f),
                    focusedLeadingIconColor = d.labelColor.color, unfocusedLeadingIconColor = d.labelColor.color.copy(alpha = 0.7f),
                    disabledLeadingIconColor = d.labelColor.color.copy(alpha = 0.7f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { list.firstOrNull()?.let { actions.launchApp(it.key, null) } }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
        }
    }

    Box(
        modifier.fillMaxSize().clip(shape).background(d.background.withOpacity(d.opacity))
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        Column(Modifier.fillMaxSize().let { if (interactive) it.imePadding() else it }) {
            if (!d.searchAtBottom) searchField()
            if (tabs.size > 1 && query.isBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabs.forEach { t ->
                        FilterChip(
                            selected = t == tab, onClick = { tabState = t; scope.launch { gridState.scrollToItem(0) } },
                            enabled = interactive,
                            label = { Text(t.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = d.labelColor.color, selectedLabelColor = Color.Black,
                                selectedContainerColor = d.labelColor.color.copy(alpha = 0.9f),
                                disabledLabelColor = d.labelColor.color, disabledSelectedContainerColor = d.labelColor.color.copy(alpha = 0.9f),
                            ),
                        )
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if (d.layout == DrawerLayout.LIST) 1 else d.columns
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    userScrollEnabled = interactive,
                    contentPadding = PaddingValues(horizontal = d.horizontalPaddingDp.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(d.rowSpacingDp.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().nestedScroll(nested),
                ) {
                    if (recents.isNotEmpty() && query.isBlank() && tab == DrawerTab.All && d.layout == DrawerLayout.GRID) {
                        items(recents, key = { "recent-" + it.key }) { entry ->
                            DrawerAppItem(ui, entry, iconSize, d.layout, interactive)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "recent-divider") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(d.labelColor.color.copy(alpha = 0.15f)))
                        }
                    }
                    items(list, key = { it.key }) { entry ->
                        DrawerAppItem(ui, entry, iconSize, d.layout, interactive)
                    }
                    if (list.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (query.isBlank()) "No apps here" else "No apps match \"$query\"",
                                color = d.labelColor.color.copy(alpha = 0.7f),
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
                if (d.fastScroller && query.isBlank() && list.size > 20 && interactive) {
                    FastScroller(list, labelOf, gridState, recents.size + if (recents.isNotEmpty()) 1 else 0, d.labelColor.color)
                }
            }
            if (d.searchAtBottom) searchField()
        }
    }
}

@Composable
private fun FastScroller(list: List<AppEntry>, labelOf: (AppEntry) -> String, state: LazyGridState, offset: Int, color: Color) {
    val letters = remember(list) { list.map { AppSorting.indexLetter(labelOf(it)) }.distinct() }
    val firstIndex = remember(list) {
        val m = HashMap<Char, Int>()
        list.forEachIndexed { i, e -> m.putIfAbsent(AppSorting.indexLetter(labelOf(e)), i) }
        m
    }
    var active by remember { mutableStateOf<Char?>(null) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var heightPx by remember { mutableStateOf(1f) }
    fun pick(y: Float) {
        val i = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            firstIndex[letter]?.let { scope.launch { state.scrollToItem(it + offset) } }
        }
    }
    Box {
        Column(
            Modifier.fillMaxHeight().width(22.dp)
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ -> pick(change.position.y) }
                }
                .pointerInput(letters) { detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); active = null }) },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(l.toString(), color = if (l == active) color else color.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        active?.let { l ->
            Box(
                Modifier.align(Alignment.Center).padding(end = 48.dp).size(56.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) { Text(l.toString(), color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun DrawerAppItem(ui: LauncherUiState?, entry: AppEntry, iconSize: Dp, layout: DrawerLayout, interactive: Boolean) {
    val config = LocalFlyerConfig.current
    val actions = LocalLauncherActions.current
    val view = LocalView.current
    val holder = remember { CoordsHolder() }
    val callbacks = remember(entry.key, ui) {
        object : ItemGestureCallbacks {
            override fun onTap(rootPos: Offset) = actions.launchApp(entry.key, holder.boundsInRoot())
            override fun onLongPress(rootPos: Offset) {
                val u = ui ?: return
                val bounds = holder.boundsInRoot()
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                u.menu = ItemMenus.forApp(u, actions, entry, null, null, bounds)
                u.drag.prepare(Placement.NewApp(entry.key), AppItem(appKey = entry.key), null, bounds, rootPos)
            }
            override fun onSecondaryClick(rootPos: Offset) {
                val u = ui ?: return
                u.menu = ItemMenus.forApp(u, actions, entry, null, null, holder.boundsInRoot())
            }
        }
    }
    val m = Modifier.itemGestures(holder, ui?.arbiter, enabled = interactive && ui != null, callbacks = callbacks)
    val d = config.drawer
    if (layout == DrawerLayout.LIST) {
        Row(
            m.fillMaxWidth().clip(RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconImage(rememberAppIcon(entry.key, iconSize * 0.8f), iconSize * 0.8f)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    labelFor(entry.key, LocalAppIndex.current, LocalAppOverrides.current),
                    color = d.labelColor.color, fontSize = (15 * config.typography.textScale).sp,
                    fontFamily = LocalLauncherFont.current, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (entry.isWorkProfile) Text("Work", color = d.labelColor.color.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    } else {
        Box(m.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            AppTile(entry.key, iconSize, d.showLabels, config.icons.label, labelColor = d.labelColor.color)
        }
    }
}
