package com.flyer.launcher.flyer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.theme.FlyerTheme
import com.flyer.launcher.flyer.editor.LiveEditorScreen
import com.flyer.launcher.flyer.screens.AppEditorScreen
import com.flyer.launcher.flyer.screens.CategoryScreen
import com.flyer.launcher.flyer.screens.DashboardScreen
import com.flyer.launcher.flyer.screens.SettingsSearchScreen
import com.flyer.launcher.flyer.settings.SettingsRegistry
import com.flyer.launcher.launcher.overlays.WidgetPicker
import kotlinx.coroutines.launch

/**
 * Flyer: the launcher's built-in customization environment. Rendered inside the launcher
 * activity, editing a draft of the launcher document through [FlyerSession].
 */
@Composable
fun FlyerHost(controller: FlyerController, core: LauncherCore, apps: List<AppEntry>, modifier: Modifier = Modifier) {
    val session = controller.session ?: return
    val config = session.preview.config
    FlyerTheme(config, useLauncherFont = config.typography.flyerUsesLauncherFont) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val content: @Composable () -> Unit = {
                    FlyerScaffold(controller, session, core, apps, showMenuButton = !wide) { scope.launch { drawerState.open() } }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(264.dp).fillMaxHeight()) {
                            FlyerSidebar(controller, session) { }
                        }
                        Box(Modifier.weight(1f)) { content() }
                    }
                } else {
                    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                        ModalDrawerSheet(Modifier.width(300.dp)) { FlyerSidebar(controller, session) { scope.launch { drawerState.close() } } }
                    }) { content() }
                }
            }
        }
        if (controller.confirmClose) {
            AlertDialog(
                onDismissRequest = { controller.confirmClose = false },
                title = { Text("Apply your changes?") },
                text = { Text("You have changes that are only visible in Flyer's previews. Apply them to the home screen?") },
                confirmButton = { Button(onClick = { controller.close(applyChanges = true) }) { Text("Apply") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { controller.confirmClose = false }) { Text("Keep editing") }
                        TextButton(onClick = { session.discard(); controller.close(applyChanges = false) }) { Text("Discard") }
                    }
                },
            )
        }
    }
    LaunchedEffect(session) {
        core.document.collect { session.onLauncherChanged(it) }
    }
}

@Composable
private fun FlyerSidebar(controller: FlyerController, session: FlyerSession, onNavigated: () -> Unit) {
    val route = controller.route
    val config = session.draft.config
    Column(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(FlyerCategory.ADVANCED.icon, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Flyer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Customization environment", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SidebarItem(Icons.Outlined.Dashboard, "Dashboard", route == FlyerRoute.Dashboard, null) {
            controller.navigate(FlyerRoute.Dashboard); onNavigated()
        }
        SidebarItem(Icons.Outlined.Search, "Search settings", route == FlyerRoute.Search, null) {
            controller.navigate(FlyerRoute.Search); onNavigated()
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        FlyerCategory.entries.forEach { c ->
            val selected = (route is FlyerRoute.Category && route.category == c) || (c == FlyerCategory.LAYOUT_EDITOR && route is FlyerRoute.LiveEditor)
            val modified = SettingsRegistry.modifiedCount(c, config)
            SidebarItem(c.icon, c.title, selected, modified.takeIf { it > 0 }) {
                controller.navigate(FlyerRoute.of(c)); onNavigated()
            }
        }
    }
}

@Composable
private fun SidebarItem(icon: ImageVector, title: String, selected: Boolean, badge: Int?, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) Badge { Text("$badge") }
    }
}

@Composable
private fun FlyerScaffold(
    controller: FlyerController,
    session: FlyerSession,
    core: LauncherCore,
    apps: List<AppEntry>,
    showMenuButton: Boolean,
    openMenu: () -> Unit,
) {
    val route = controller.route
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
        FlyerTopBar(controller, session, route, showMenuButton, openMenu)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(targetState = route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "flyer-route") { r ->
                val bottom = PaddingValues(bottom = insets.calculateBottomPadding())
                when (r) {
                    FlyerRoute.Dashboard -> DashboardScreen(controller, session, apps, bottom)
                    is FlyerRoute.Category -> CategoryScreen(controller, session, core, apps, r.category, r.highlight, bottom)
                    is FlyerRoute.LiveEditor -> LiveEditorScreen(controller, session, core, apps, r.selectItemId, bottom)
                    FlyerRoute.Search -> SettingsSearchScreen(controller, session, bottom)
                    is FlyerRoute.AppEditor -> AppEditorScreen(session, core, apps, r.appKey, bottom)
                    FlyerRoute.WidgetPicker -> {
                        val platform = LocalFlyerPlatform.current
                        WidgetPicker(
                            onPick = { choice ->
                                platform.addWidget(choice, com.flyer.launcher.core.model.Surface.Page(0)) { item ->
                                    session.updateLayout("Add widget") { l -> com.flyer.launcher.core.workspace.WorkspaceEngine.addItem(l, item, session.draft.config) }
                                    controller.back()
                                }
                            },
                            onDismiss = { controller.back() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlyerTopBar(controller: FlyerController, session: FlyerSession, route: FlyerRoute, showMenuButton: Boolean, openMenu: () -> Unit) {
    var overflow by remember { mutableStateOf(false) }
    val auto = session.draft.config.advanced.autoApplyFlyerChanges
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Phones put the title on its own line (Material "medium" top bar) so it never truncates.
        val narrow = maxWidth < 600.dp
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (controller.backStack.size > 1) {
                    IconButton(onClick = { controller.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                } else {
                    IconButton(onClick = { controller.requestClose() }) { Icon(Icons.Outlined.Close, "Close Flyer") }
                }
                if (showMenuButton) IconButton(onClick = openMenu) { Icon(Icons.Outlined.Menu, "Modules") }
                if (narrow) Spacer(Modifier.weight(1f))
                else Text(route.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (route != FlyerRoute.Search) IconButton(onClick = { controller.navigate(FlyerRoute.Search) }) { Icon(Icons.Outlined.Search, "Search settings") }
                IconButton(onClick = { session.undo() }, enabled = session.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo ${session.undoLabel.orEmpty()}") }
                IconButton(onClick = { session.redo() }, enabled = session.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo") }
                if (auto) {
                    Icon(Icons.Outlined.Check, "Changes apply instantly", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                    Button(onClick = { session.apply() }, enabled = session.isDirty, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Apply") }
                }
                Box {
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text("Show advanced settings") },
                            trailingIcon = { Switch(checked = session.draft.config.advanced.showAdvancedSettings, onCheckedChange = null) },
                            onClick = { session.updateConfig("Advanced settings", null) { it.copy(advanced = it.advanced.copy(showAdvancedSettings = !it.advanced.showAdvancedSettings)) } },
                        )
                        DropdownMenuItem(text = { Text("Discard unapplied changes") }, enabled = session.isDirty, onClick = { overflow = false; session.discard() })
                        DropdownMenuItem(text = { Text("Reset all settings") }, onClick = { overflow = false; session.resetAllSettings() })
                        DropdownMenuItem(text = { Text("Close Flyer") }, onClick = { overflow = false; controller.requestClose() })
                    }
                }
            }
            if (narrow) {
                Text(
                    route.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}
