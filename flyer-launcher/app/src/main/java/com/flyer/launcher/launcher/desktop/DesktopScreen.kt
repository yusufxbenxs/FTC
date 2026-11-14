package com.flyer.launcher.launcher.desktop

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.apps.AppSorting
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.TaskbarPosition
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.core.workspace.desktopGrid
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.MenuEntry
import com.flyer.launcher.launcher.MenuState
import com.flyer.launcher.launcher.drag.DragSession
import com.flyer.launcher.launcher.drag.DropCandidate
import com.flyer.launcher.launcher.drag.DropZone
import com.flyer.launcher.launcher.drag.SpecialDrop
import com.flyer.launcher.launcher.home.GridOverlay
import com.flyer.launcher.launcher.home.LiveItem
import com.flyer.launcher.launcher.home.SurfaceDropZone
import com.flyer.launcher.launcher.home.WorkspaceSurface
import com.flyer.launcher.launcher.home.targetsSurface
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.IconImage
import com.flyer.launcher.launcher.render.LocalAppIndex
import com.flyer.launcher.launcher.render.LocalAppOverrides
import com.flyer.launcher.launcher.render.labelFor
import com.flyer.launcher.launcher.render.rememberAppIcon
import com.flyer.launcher.launcher.ui.CoordsHolder
import com.flyer.launcher.launcher.ui.ItemGestureCallbacks
import com.flyer.launcher.launcher.ui.clickableNoRipple
import com.flyer.launcher.launcher.ui.itemGestures
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Desktop-style environment: a free-form desktop with draggable shortcuts and widgets, a taskbar,
 * a start menu panel and right-click menus. It has its own layout (desktop items / taskbar pins)
 * so switching between mobile and desktop never loses either arrangement.
 */
@Composable
fun DesktopScreen(ui: LauncherUiState, config: FlyerConfig, layout: HomeLayout, apps: List<com.flyer.launcher.core.apps.AppEntry>) {
    val d = config.desktop
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
        if (d.taskbarPosition == TaskbarPosition.TOP) Taskbar(ui, config, layout)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DesktopSurface(ui, config, layout)
            if (ui.startMenuOpen) {
                StartMenu(ui, config, layout, apps, Modifier.align(if (d.taskbarPosition == TaskbarPosition.TOP) Alignment.TopStart else Alignment.BottomStart)) {
                    ui.startMenuOpen = false
                }
            }
        }
        if (d.taskbarPosition == TaskbarPosition.BOTTOM) Taskbar(ui, config, layout)
        Spacer(Modifier.height(insets.calculateBottomPadding()))
    }
}

@Composable
private fun DesktopSurface(ui: LauncherUiState, config: FlyerConfig, layout: HomeLayout) {
    val d = config.desktop
    val zone = remember { SurfaceDropZone(Surface.Desktop) }
    DisposableEffect(Unit) {
        ui.drag.register("desktop", zone)
        onDispose { ui.drag.unregister("desktop") }
    }
    val actions = LocalLauncherActions.current
    val view = LocalView.current
    val items = layout.desktop.items
    var surfaceBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    fun desktopMenu(at: Offset) {
        ui.menu = MenuState(
            Rect(at, at), "Desktop",
            listOf(
                MenuEntry("Flyer", Icons.Outlined.Tune, shortcutHint = "Ctrl+,") { ui.menu = null; actions.openFlyer() },
                MenuEntry("Edit layout", Icons.Outlined.DashboardCustomize) { ui.menu = null; actions.openFlyer(FlyerRoute.LiveEditor()) },
                MenuEntry("Add widget", Icons.Outlined.Widgets) { ui.menu = null; ui.widgetPicker = Surface.Desktop },
                MenuEntry("Arrange icons", Icons.Outlined.AutoAwesomeMosaic) {
                    ui.menu = null
                    ui.core.editHome("Arrange desktop") { doc ->
                        doc.copy(layout = doc.layout.copy(desktop = doc.layout.desktop.copy(items = WorkspaceEngine.autoArrange(doc.layout.desktop.items, doc.config.desktopGrid()))))
                    }
                },
                MenuEntry("Wallpaper", Icons.Outlined.Wallpaper) { ui.menu = null; ui.openWallpaperPicker?.invoke() },
                MenuEntry("Switch to mobile layout", Icons.Outlined.PhoneAndroid) {
                    ui.menu = null
                    actions.perform(com.flyer.launcher.core.config.GestureAction(com.flyer.launcher.core.config.ActionType.TOGGLE_DESKTOP_MODE))
                },
            ),
        )
    }

    WorkspaceSurface(
        items = items,
        columns = d.columns,
        rows = d.rows,
        home = null,
        freePositioning = !d.snapToGrid,
        marginsDp = floatArrayOf(12f, 12f, 12f, 12f),
        modifier = Modifier
            .onGloballyPositioned { zone.rootBounds = it.boundsInRoot(); surfaceBounds = zone.rootBounds }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val secondary = currentEvent.buttons.isSecondaryPressed
                    if (secondary) {
                        down.consume()
                        desktopMenu(down.position + surfaceBounds.topLeft)
                        return@awaitEachGesture
                    }
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.first()
                            if (!c.pressed) return@withTimeoutOrNull true
                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    if (up == null) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        desktopMenu(down.position + surfaceBounds.topLeft)
                    } else if (up) {
                        ui.startMenuOpen = false
                    }
                }
            },
        underlay = { geometry ->
            SideEffect {
                zone.geometry = geometry
                zone.items = items
                zone.snapPx = with(density) { 8.dp.toPx() }
                zone.allowGrouping = true
            }
            if (ui.drag.isDragging) {
                val c = ui.drag.candidate
                val highlight = c?.highlight?.takeIf { c.target?.let { t -> targetsSurface(t, Surface.Desktop) } == true }?.translate(-zone.rootBounds.topLeft)
                GridOverlay(geometry, color = Color.White.copy(alpha = if (d.snapToGrid) 0.12f else 0f), highlight = highlight)
            }
        },
    ) { item, geometry, _ ->
        LiveItem(
            item, Surface.Desktop, geometry.cellWidth, geometry.cellHeight, ui,
            iconSizeOverride = d.iconSizeDp.dp, labelColor = d.labelColor.color,
        )
    }
}

/** Taskbar: start button, pinned apps (drop an app here to pin it), Flyer, search and clock. */
@Composable
private fun Taskbar(ui: LauncherUiState, config: FlyerConfig, layout: HomeLayout) {
    val d = config.desktop
    val actions = LocalLauncherActions.current
    val pinZone = remember { TaskbarPinZone() }
    DisposableEffect(Unit) {
        ui.drag.register("taskbar", pinZone)
        onDispose { ui.drag.unregister("taskbar") }
    }
    Row(
        Modifier.fillMaxWidth().height(d.taskbarHeightDp.dp).background(d.taskbarBackground.color)
            .onGloballyPositioned { pinZone.rootBounds = it.boundsInRoot() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskbarButton(Icons.Outlined.Apps, "Start", highlighted = ui.startMenuOpen) { ui.startMenuOpen = !ui.startMenuOpen }
        TaskbarButton(Icons.Outlined.Search, "Search") { actions.openSearch() }
        Row(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = if (d.taskbarCentered) Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            layout.desktop.taskbarPins.forEach { key ->
                key(key) { TaskbarApp(ui, key, d.taskbarShowLabels) }
            }
            if (ui.drag.isDragging && ui.drag.candidate?.special == SpecialDrop.PIN_TASKBAR) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.3f)))
            }
        }
        TaskbarButton(Icons.Outlined.Tune, "Flyer") { actions.openFlyer() }
        if (d.taskbarShowClock) TaskbarClock()
    }
}

private class TaskbarPinZone : DropZone {
    var rootBounds: Rect = Rect.Zero
    override val bounds: Rect get() = rootBounds
    override val priority: Int get() = 2
    override fun resolve(session: DragSession, pointer: Offset, visualTopLeft: Offset): DropCandidate? =
        if (session.visual is com.flyer.launcher.core.model.AppItem) DropCandidate(special = SpecialDrop.PIN_TASKBAR, highlight = rootBounds) else null
}

@Composable
private fun TaskbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.size(44.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (hovered || highlighted) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun TaskbarApp(ui: LauncherUiState, key: String, showLabel: Boolean) {
    val actions = LocalLauncherActions.current
    val holder = remember { CoordsHolder() }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val label = labelFor(key, LocalAppIndex.current, LocalAppOverrides.current)
    val callbacks = remember(key) {
        object : ItemGestureCallbacks {
            override fun onTap(rootPos: Offset) = actions.launchApp(key, holder.boundsInRoot())
            override fun onLongPress(rootPos: Offset) = onSecondaryClick(rootPos)
            override fun onSecondaryClick(rootPos: Offset) {
                ui.menu = MenuState(
                    holder.boundsInRoot(), label,
                    listOf(
                        MenuEntry("Open", Icons.Outlined.Apps) { ui.menu = null; actions.launchApp(key, holder.boundsInRoot()) },
                        MenuEntry("Unpin from taskbar", Icons.Outlined.PushPin) {
                            ui.menu = null
                            ui.core.editHome("Unpin") { doc -> doc.copy(layout = doc.layout.copy(desktop = doc.layout.desktop.copy(taskbarPins = doc.layout.desktop.taskbarPins - key))) }
                        },
                        MenuEntry("App settings", Icons.Outlined.Settings) {
                            ui.menu = null
                            ui.core.resolveApp(key)?.let { ui.core.appsEngine.openAppInfo(it, null) }
                        },
                    ),
                )
            }
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (hovered) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .itemGestures(holder, ui.arbiter, callbacks = callbacks).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconImage(rememberAppIcon(key, 32.dp), 32.dp)
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TaskbarClock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.End) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(now)), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

/** Floating start menu: type to search, Enter launches the first match. */
@Composable
fun StartMenu(
    ui: LauncherUiState,
    config: FlyerConfig,
    layout: HomeLayout,
    apps: List<com.flyer.launcher.core.apps.AppEntry>,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val d = config.desktop
    val actions = LocalLauncherActions.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val index = LocalAppIndex.current
    val overrides = LocalAppOverrides.current
    val visible = remember(apps, layout.hiddenApps, query, overrides) {
        apps.filter { it.key !in layout.hiddenApps }
            .map { it to AppSorting.score(labelFor(it.key, index, overrides), query, config.search.fuzzyMatching) }
            .filter { it.second > 0 }
            .let { list -> if (query.isBlank()) list.sortedBy { AppSorting.normalize(labelFor(it.first.key, index, overrides)) } else list.sortedByDescending { it.second } }
            .map { it.first }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        modifier.padding(12.dp).width(d.startMenuWidthDp.dp).height(d.startMenuHeightDp.dp)
            .clip(RoundedCornerShape(d.panelCornerDp.dp)).background(d.panelBackground.color)
            .clickableNoRipple { }.padding(14.dp),
    ) {
        TextField(
            query, { query = it }, singleLine = true,
            placeholder = { Text("Type to search apps") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { visible.firstOrNull()?.let { actions.launchApp(it.key, null); onClose() } }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.1f), unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.6f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                focusedLeadingIconColor = Color.White, unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            GridCells.Fixed(d.startMenuColumns),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(visible, key = { it.key }) { entry ->
                val source = remember { MutableInteractionSource() }
                val hovered by source.collectIsHoveredAsState()
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (hovered) Color.White.copy(0.12f) else Color.Transparent)
                        .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
                        .clickableNoRipple { actions.launchApp(entry.key, null); onClose() }.padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppTile(entry.key, 40.dp, true, config.icons.label.copy(sizeSp = 11f, shadow = false), labelColor = Color.White)
                }
            }
        }
    }
}

/** Is desktop mode active for this configuration and the attached input devices / screen? */
fun isDesktopActive(config: FlyerConfig, pointerDeviceAttached: Boolean, widthDp: Int): Boolean = when (config.desktop.mode) {
    com.flyer.launcher.core.config.DesktopMode.ON -> true
    com.flyer.launcher.core.config.DesktopMode.OFF -> false
    com.flyer.launcher.core.config.DesktopMode.AUTO -> pointerDeviceAttached && widthDp >= 600
}
