package com.flyer.launcher.launcher

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.config.EffectsLevel
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.OverlayAnimation
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.render.IconSpec
import com.flyer.launcher.core.theme.FlyerTheme
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.ThemeEngine
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.flyer.FlyerHost
import com.flyer.launcher.flyer.FlyerPlatform
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.launcher.desktop.DesktopScreen
import com.flyer.launcher.launcher.desktop.isDesktopActive
import com.flyer.launcher.launcher.drag.DragSession
import com.flyer.launcher.launcher.drag.DropCandidate
import com.flyer.launcher.launcher.drag.SpecialDrop
import com.flyer.launcher.launcher.drag.dragMonitor
import com.flyer.launcher.launcher.drawer.AppDrawer
import com.flyer.launcher.launcher.home.HomeBackground
import com.flyer.launcher.launcher.home.HomeScreen
import com.flyer.launcher.launcher.overlays.ConfirmDialog
import com.flyer.launcher.launcher.overlays.DragLayer
import com.flyer.launcher.launcher.overlays.FolderPopup
import com.flyer.launcher.launcher.overlays.LauncherSnackbar
import com.flyer.launcher.launcher.overlays.MenuOverlay
import com.flyer.launcher.launcher.overlays.RenameFolderDialog
import com.flyer.launcher.launcher.overlays.SearchOverlay
import com.flyer.launcher.launcher.overlays.WidgetPicker
import com.flyer.launcher.launcher.render.AppIndex
import com.flyer.launcher.launcher.render.LocalAppIndex
import com.flyer.launcher.launcher.render.LocalAppOverrides
import com.flyer.launcher.launcher.render.LocalDraggingIds
import com.flyer.launcher.launcher.render.LocalLauncherCore
import kotlinx.coroutines.delay
import com.flyer.launcher.launcher.lock.AuthPrompt
import com.flyer.launcher.launcher.lock.LockButton
import com.flyer.launcher.launcher.lock.LockOverlay
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.flyer.launcher.launcher.render.materials.BackdropLayer
import com.flyer.launcher.launcher.render.materials.Backdrops
import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.recordBackdrop

/** Blur radius (px) the activity should apply behind its window (wallpaper) right now. */
fun interface WindowBlurSink {
    fun setBlurBehind(radiusPx: Int)
}

/** The whole launcher: background, home (mobile or desktop), overlays and Flyer. */
@Composable
fun LauncherRoot(ui: LauncherUiState, actions: LauncherActions, platform: FlyerPlatform, blur: WindowBlurSink, pointerDeviceAttached: Boolean) {
    val core = ui.core
    val doc by core.document.collectAsStateWithLifecycle()
    val apps by core.apps.collectAsStateWithLifecycle()
    val counts by core.persistence.launchCounts.collectAsStateWithLifecycle()
    val last by core.persistence.lastLaunched.collectAsStateWithLifecycle()
    val homeEdit by core.lastHomeEdit.collectAsStateWithLifecycle()
    val lockState by core.lock.state.collectAsStateWithLifecycle()
    val config = doc.config
    val layout = doc.layout
    val index = remember(apps) { AppIndex(apps.associateBy { it.key }) }
    val dragging = ui.drag.session?.takeIf { it.started }?.draggedItemId?.let { setOf(it) } ?: emptySet()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val bgLayer = rememberGraphicsLayer()
    val homeLayer = rememberGraphicsLayer()
    val backdrops = remember(bgLayer, homeLayer) { Backdrops(BackdropLayer(bgLayer), BackdropLayer(homeLayer)) }

    SideEffect { ui.desktopActive = isDesktopActive(config, pointerDeviceAttached, widthDp) }
    WireDrag(ui, actions)
    PrefetchIcons(core, apps, config)

    CompositionLocalProvider(
        LocalLauncherCore provides core,
        LocalAppIndex provides index,
        LocalAppOverrides provides layout.appOverrides,
        LocalFlyerConfig provides config,
        LocalDeviceTraits provides core.traits,
        LocalLauncherActions provides actions,
        LocalFlyerPlatform provides platform,
        LocalDraggingIds provides dragging,
        LocalBackdrops provides backdrops,
    ) {
        FlyerTheme(config) {
            val traits = core.traits
            val animate = ThemeEngine.animationsActive(config.animations, traits, config.performance.effects)
            val blurAllowed = traits.blurSupported && config.performance.blurEffects && !config.performance.lowMemoryMode &&
                config.performance.effects != EffectsLevel.MINIMAL
            val cap = if (config.performance.effects == EffectsLevel.BALANCED) 16 else 64
            val folder = ui.openFolder?.let { WorkspaceEngine.findFolder(layout, it.folderId) }
            val overlayBlurDp = when {
                !blurAllowed || ui.flyer.isOpen -> 0
                folder != null -> config.folders.popupBlurDp
                ui.drawerOpen || ui.searchOpen -> config.drawer.blurDp
                else -> 0
            }.coerceAtMost(cap)
            val homeBlurDp = if (lockState.locked && blurAllowed) config.lock.blurDp.coerceAtMost(cap) else overlayBlurDp / 2
            val density = LocalDensity.current
            LaunchedEffect(overlayBlurDp) { blur.setBlurBehind(with(density) { overlayBlurDp.dp.roundToPx() }) }

            // Home "entrance" when returning from an app.
            val entrance = remember { Animatable(1f) }
            LaunchedEffect(ui.resumed) {
                if (ui.resumed && animate && config.animations.homeEntrance && !ui.anyOverlayOpen) {
                    entrance.snapTo(0f)
                    entrance.animateTo(1f, tween(ThemeEngine.duration(config.animations, 320)))
                }
            }

            val flyerState = remember { MutableTransitionState(false) }
            // While locked, Flyer is hidden (its draft is kept) so the home screen shows behind the lock.
            flyerState.targetState = ui.flyer.isOpen && !lockState.locked
            val flyerCovering = flyerState.currentState && flyerState.isIdle && ui.flyer.isOpen && !lockState.locked

            Box(Modifier.fillMaxSize().dragMonitor(ui.drag)) {
                if (!flyerCovering) {
                    // Recorded so glass materials can blur and refract what is really behind them.
                    Box(Modifier.fillMaxSize().recordBackdrop(backdrops.home)) {
                        HomeBackground(config.home.background, Modifier.recordBackdrop(backdrops.background))
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val e = entrance.value
                                    // Locked without blur (older Android / effects off): fade the home so the lock reads clearly.
                                    alpha = e * if (lockState.locked && homeBlurDp == 0) 0.3f else 1f
                                    scaleX = 0.94f + 0.06f * e
                                    scaleY = 0.94f + 0.06f * e
                                }
                                .let { if (homeBlurDp > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(homeBlurDp.dp) else it },
                        ) {
                            if (ui.desktopActive) DesktopScreen(ui, config, layout, apps) else HomeScreen(ui, config, layout)
                        }
                    }

                    if (config.lock.showButton && !lockState.locked && !ui.drawerOpen && ui.openFolder == null && !ui.searchOpen && !ui.drag.isDragging) {
                        LockButton(ui, config.lock)
                    }

                    AnimatedVisibility(ui.drawerOpen, enter = enterFor(config.drawer.animation, config, animate), exit = exitFor(config.drawer.animation, config, animate)) {
                        AppDrawer(ui, apps, layout, counts, last, interactive = true, onClose = { ui.drawerOpen = false })
                    }
                    if (folder != null) {
                        FolderPopup(
                            ui, folder, ui.openFolder, interactive = true,
                            asWindow = ui.desktopActive && config.desktop.foldersAsWindows,
                            onDismiss = { ui.openFolder = null },
                        )
                    }
                    AnimatedVisibility(ui.searchOpen, enter = fadeIn(), exit = fadeOut()) {
                        SearchOverlay(ui, apps, layout) { ui.closeSearch() }
                    }
                    ui.widgetPicker?.let { surface ->
                        WidgetPicker(
                            onPick = { choice ->
                                ui.widgetPicker = null
                                platform.addWidget(choice, surface) { item ->
                                    core.editHome("Add widget") { d ->
                                        d.copy(layout = WorkspaceEngine.addItem(d.layout, item, d.config, ui.pageController?.currentPage ?: 0, desktop = surface == Surface.Desktop))
                                    }
                                }
                            },
                            onDismiss = { ui.widgetPicker = null },
                        )
                    }
                    DragLayer(ui)
                    ui.menu?.let { MenuOverlay(it) { ui.menu = null; ui.drag.cancel() } }

                    homeEdit?.takeIf { !lockState.locked }?.let { (label, _) ->
                        LaunchedEffect(homeEdit) { delay(4500); core.clearHomeUndo() }
                        LauncherSnackbar(
                            label, "Undo", { core.undoHomeEdit() },
                            Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visibleState = flyerState,
                    enter = if (animate) fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.94f) else EnterTransition.None,
                    exit = if (animate) fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.96f) else ExitTransition.None,
                ) {
                    FlyerHost(ui.flyer, core, apps)
                }

                if (lockState.locked) LockOverlay(ui, lockState)
                ui.authRequest?.let { AuthPrompt(ui, it, lockState) }

                ui.message?.let { msg ->
                    LaunchedEffect(msg) { delay(2600); ui.message = null }
                    LauncherSnackbar(
                        msg, null, null,
                        Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
                    )
                }
                ui.renameFolderId?.let { id ->
                    val f = WorkspaceEngine.findFolder(layout, id)
                    if (f == null) ui.renameFolderId = null
                    else RenameFolderDialog(f.name, onDismiss = { ui.renameFolderId = null }) { name ->
                        ui.renameFolderId = null
                        core.editHome("Rename folder") { d -> d.copy(layout = WorkspaceEngine.renameFolder(d.layout, id, name)) }
                    }
                }
                ui.confirm?.let { c ->
                    ConfirmDialog(c.title, c.message, c.confirmLabel, onDismiss = { ui.confirm = null }) { ui.confirm = null; c.onConfirm() }
                }
            }

            BackHandler(enabled = true) {
                when {
                    ui.authRequest != null -> ui.authRequest = null
                    lockState.locked -> Unit // the lock can only be left by unlocking
                    else -> ui.back()
                }
            }
        }
    }
}

private fun enterFor(a: OverlayAnimation, config: FlyerConfig, animate: Boolean): EnterTransition {
    if (!animate || a == OverlayAnimation.NONE) return EnterTransition.None
    val d = ThemeEngine.duration(config.animations, 280)
    return when (a) {
        OverlayAnimation.SLIDE_UP -> slideInVertically(tween(d)) { it / 3 } + fadeIn(tween(d))
        OverlayAnimation.FADE -> fadeIn(tween(d))
        OverlayAnimation.SCALE -> scaleIn(tween(d), initialScale = 0.9f) + fadeIn(tween(d))
        OverlayAnimation.SLIDE_SIDE -> slideInHorizontally(tween(d)) { it / 3 } + fadeIn(tween(d))
        OverlayAnimation.NONE -> EnterTransition.None
    }
}

private fun exitFor(a: OverlayAnimation, config: FlyerConfig, animate: Boolean): ExitTransition {
    if (!animate || a == OverlayAnimation.NONE) return ExitTransition.None
    val d = ThemeEngine.duration(config.animations, 220)
    return when (a) {
        OverlayAnimation.SLIDE_UP -> slideOutVertically(tween(d)) { it / 3 } + fadeOut(tween(d))
        OverlayAnimation.FADE -> fadeOut(tween(d))
        OverlayAnimation.SCALE -> scaleOut(tween(d), targetScale = 0.9f) + fadeOut(tween(d))
        OverlayAnimation.SLIDE_SIDE -> slideOutHorizontally(tween(d)) { it / 3 } + fadeOut(tween(d))
        OverlayAnimation.NONE -> ExitTransition.None
    }
}

/** Connects the drag controller to the layout: closes overlays when a drag starts and applies drops. */
@Composable
private fun WireDrag(ui: LauncherUiState, actions: LauncherActions) {
    SideEffect {
        ui.drag.onDragStarted = {
            ui.menu = null
            ui.drawerOpen = false
            ui.openFolder = null
            ui.closeSearch()
            ui.startMenuOpen = false
        }
        ui.drag.onPendingReleased = { }
        ui.drag.onDrop = { session, candidate -> handleDrop(ui, actions, session, candidate) }
    }
}

private fun handleDrop(ui: LauncherUiState, actions: LauncherActions, session: DragSession, candidate: DropCandidate?) {
    val core = ui.core
    candidate ?: return
    val appKey = (session.visual as? AppItem)?.appKey
    when (candidate.special) {
        SpecialDrop.REMOVE -> {
            if (appKey != null && WorkspaceEngine.isProtectedKey(appKey)) {
                actions.showMessage("Flyer apps are part of the launcher and can't be removed")
                return
            }
            when (val p = session.placement) {
                is Placement.Existing -> core.editHome("Removed") { d -> d.copy(layout = WorkspaceEngine.removeItem(d.layout, p.itemId)) }
                is Placement.FromFolder -> core.editHome("Removed from folder") { d ->
                    val folder = WorkspaceEngine.findFolder(d.layout, p.folderId)
                    if (folder == null) d else d.copy(layout = WorkspaceEngine.updateFolderApps(d.layout, p.folderId, folder.apps - p.appKey))
                }
                else -> Unit
            }
            return
        }
        SpecialDrop.APP_INFO -> { appKey?.let { core.resolveApp(it) }?.let { core.appsEngine.openAppInfo(it, null) }; return }
        SpecialDrop.UNINSTALL -> { appKey?.let { core.resolveApp(it) }?.let { core.appsEngine.uninstall(it) }; return }
        SpecialDrop.HIDE -> {
            appKey?.let { k -> core.editHome("Hidden from drawer") { d -> d.copy(layout = d.layout.copy(hiddenApps = d.layout.hiddenApps + k)) } }
            return
        }
        SpecialDrop.PIN_TASKBAR -> {
            appKey?.let { k ->
                core.editHome("Pinned to taskbar") { d -> d.copy(layout = d.layout.copy(desktop = d.layout.desktop.copy(taskbarPins = (d.layout.desktop.taskbarPins + k).distinct()))) }
            }
            return
        }
        null -> Unit
    }
    val target = candidate.target ?: return
    val doc = core.document.value
    when (val r = WorkspaceEngine.drop(doc.layout, session.placement, target, doc.config)) {
        is WorkspaceEngine.DropResult.Accepted -> core.editHome(r.message ?: if (session.visual is FolderItem) "Folder moved" else "Moved") { d -> d.copy(layout = r.layout) }
        is WorkspaceEngine.DropResult.Rejected -> actions.showMessage(r.reason)
    }
}

/** Renders every drawer icon in the background once so the drawer opens instantly. */
@Composable
private fun PrefetchIcons(core: com.flyer.launcher.core.LauncherCore, apps: List<AppEntry>, config: FlyerConfig) {
    val density = LocalDensity.current
    val sizeDp = if (config.drawer.followIconSize) config.icons.sizeDp else config.drawer.iconSizeDp
    val px = with(density) { (sizeDp.dp.toPx() * config.performance.iconQuality).toInt() }
    LaunchedEffect(apps, config.icons, px, config.performance.prefetchIcons, config.performance.lowMemoryMode) {
        if (!config.performance.prefetchIcons || config.performance.lowMemoryMode || apps.isEmpty()) return@LaunchedEffect
        delay(1200) // let the first frame settle
        val spec = IconSpec.from(config.icons, px)
        val overrides = core.layout.value.appOverrides
        apps.forEach { entry -> runCatching { core.icons.appIcon(entry, spec, overrides[entry.key]?.icon) } }
    }
}
