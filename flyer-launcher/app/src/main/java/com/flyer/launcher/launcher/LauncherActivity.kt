package com.flyer.launcher.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.flyer.launcher.FlyerApp
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppDiscoveryEngine
import com.flyer.launcher.core.apps.BuiltInApp
import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.AppLaunchAnimation
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.DesktopMode
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.config.GestureType
import com.flyer.launcher.core.config.OrientationLock
import com.flyer.launcher.core.config.SystemBarStyle
import com.flyer.launcher.core.config.ThemeMode
import com.flyer.launcher.core.gestures.AccessibilityRequired
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.BuiltInWidgetKind
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.theme.ThemeEngine
import com.flyer.launcher.core.widgets.WidgetProvider
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.flyer.FlyerPlatform
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.launcher.home.toAndroid
import com.flyer.launcher.launcher.overlays.WidgetChoice
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The launcher's single HOME activity. It hosts the home screen, every launcher overlay and the
 * Flyer customization environment, and provides the Activity-bound services both need.
 */
class LauncherActivity : ComponentActivity(), FlyerPlatform, LauncherActions {

    private lateinit var core: LauncherCore
    private lateinit var ui: LauncherUiState
    private var pointerAttached by mutableStateOf(false)
    private var hasFocus = false

    // ───────────── Activity results ─────────────

    private var onImage: ((Uri?) -> Unit)? = null
    private var onFont: ((Uri?) -> Unit)? = null
    private var onCreateDoc: ((Uri?) -> Unit)? = null
    private var onOpenDoc: ((Uri?) -> Unit)? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { onImage?.invoke(it); onImage = null }
    private val pickFontDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { onFont?.invoke(it); onFont = null }
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { onCreateDoc?.invoke(it); onCreateDoc = null }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { onOpenDoc?.invoke(it); onOpenDoc = null }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrFinish(pending) else cancelPendingWidget()
    }

    private class PendingWidget(val id: Int, val provider: WidgetProvider, val surface: Surface, val onAdded: (WorkspaceItem) -> Unit)

    private var pendingWidget: PendingWidget? = null

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshPointerDevices()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshPointerDevices()
        override fun onInputDeviceChanged(deviceId: Int) = refreshPointerDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        core = FlyerApp.core(this)
        ui = LauncherUiState(core)
        ui.reconfigureWidget = { id -> core.widgets.startConfigure(this, id, REQUEST_RECONFIGURE) }
        ui.openWallpaperPicker = { openWallpaperPicker() }
        ui.restoreWidget = { item -> restoreWidget(item) }

        getSystemService(InputManager::class.java)?.registerInputDeviceListener(inputListener, null)
        refreshPointerDevices()

        setContent {
            LauncherRoot(ui, this, this, WindowBlurSink { setBlurBehind(it) }, pointerAttached)
        }

        lifecycleScope.launch {
            core.config.collectLatest { applyWindowConfig(it) }
        }
        lifecycleScope.launch {
            // Flyer open/closed changes system bar appearance.
            androidx.compose.runtime.snapshotFlow { ui.flyer.isOpen }.collectLatest { applyWindowConfig(core.config.value) }
        }
        handleIntent(intent, fromNewIntent = false)
    }

    override fun onDestroy() {
        getSystemService(InputManager::class.java)?.unregisterInputDeviceListener(inputListener)
        core.widgets.releaseViews()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        core.widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        if (!core.config.value.performance.keepWidgetsListening) core.widgets.stopListening()
    }

    override fun onResume() {
        super.onResume()
        ui.resumed = true
        refreshPointerDevices()
    }

    override fun onPause() {
        super.onPause()
        ui.resumed = false
        ui.menu = null
        ui.drag.cancel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        this.hasFocus = hasFocus
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, fromNewIntent = true)
    }

    private fun handleIntent(intent: Intent?, fromNewIntent: Boolean) {
        if (intent == null || !fromNewIntent) return
        if (intent.action != Intent.ACTION_MAIN || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        val alreadyOnHome = hasFocus && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        val config = core.config.value
        if (!alreadyOnHome) {
            // Returning from an app: clear transient overlays, keep Flyer where the user left it.
            ui.closeAll(includeFlyer = false)
            return
        }
        if (ui.anyOverlayOpen) {
            val keepFlyer = ui.flyer.isOpen && !config.advanced.homeButtonClosesFlyer
            ui.closeAll(includeFlyer = !keepFlyer)
        } else {
            perform(config.gestures.bindings[GestureType.HOME_BUTTON] ?: GestureAction(ActionType.DEFAULT_PAGE))
        }
    }

    // ───────────── Window configuration ─────────────

    private fun applyWindowConfig(config: FlyerConfig) {
        requestedOrientation = when (config.advanced.orientation) {
            OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val flyerOpen = ::ui.isInitialized && ui.flyer.isOpen
        if (config.home.showStatusBar || flyerOpen) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
        if (config.home.showNavigationBar || flyerOpen) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        val darkIcons = if (flyerOpen) {
            !isDarkTheme(config)
        } else when (config.home.systemBarIcons) {
            SystemBarStyle.LIGHT_ICONS -> false
            SystemBarStyle.DARK_ICONS -> true
            SystemBarStyle.AUTO -> {
                val bg = config.home.background
                (bg.type == BackgroundType.SOLID || bg.type == BackgroundType.GRADIENT) && bg.dim < 0.3f && bg.color.color.luminance() > 0.6f
            }
        }
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
    }

    private fun isDarkTheme(config: FlyerConfig): Boolean = when (config.appearance.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Blurs the wallpaper behind the window (Android 12+, when the device allows cross-window blur). */
    private fun setBlurBehind(radiusPx: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wm = getSystemService(WindowManager::class.java)
        if (wm?.isCrossWindowBlurEnabled != true) return
        val attrs = window.attributes
        if (radiusPx > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attrs.blurBehindRadius = radiusPx
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attrs.blurBehindRadius = 0
        }
        window.attributes = attrs
    }

    private fun refreshPointerDevices() {
        val im = getSystemService(InputManager::class.java) ?: return
        pointerAttached = im.inputDeviceIds.any { id ->
            val d = im.getInputDevice(id) ?: return@any false
            val external = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) d.isExternal else !d.isVirtual
            external && (d.supportsSource(InputDevice.SOURCE_MOUSE) || d.supportsSource(InputDevice.SOURCE_TOUCHPAD))
        }
    }

    // ───────────── Keyboard shortcuts ─────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && core.config.value.desktop.keyboardShortcuts && handleShortcut(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun handleShortcut(e: KeyEvent): Boolean {
        val ctrl = e.isCtrlPressed
        val session = ui.flyer.session
        when {
            e.keyCode == KeyEvent.KEYCODE_ESCAPE -> return ui.back()
            ctrl && e.keyCode == KeyEvent.KEYCODE_SPACE -> { if (ui.desktopActive) ui.startMenuOpen = !ui.startMenuOpen else openSearch(); return true }
            ctrl && e.keyCode == KeyEvent.KEYCODE_F -> { openSearch(); return true }
            ctrl && e.keyCode == KeyEvent.KEYCODE_COMMA -> { openFlyer(FlyerRoute.Dashboard); return true }
            ctrl && e.keyCode == KeyEvent.KEYCODE_E -> { openFlyer(FlyerRoute.LiveEditor()); return true }
            ctrl && e.keyCode == KeyEvent.KEYCODE_A && !ui.flyer.isOpen -> { if (ui.desktopActive) ui.startMenuOpen = true else ui.drawerOpen = true; return true }
            ctrl && e.keyCode == KeyEvent.KEYCODE_Z && !e.isShiftPressed -> { if (session != null) session.undo() else core.undoHomeEdit(); return true }
            ctrl && (e.keyCode == KeyEvent.KEYCODE_Y || (e.keyCode == KeyEvent.KEYCODE_Z && e.isShiftPressed)) -> { session?.redo(); return session != null }
            ctrl && e.keyCode == KeyEvent.KEYCODE_S && session != null -> { session.apply(); return true }
            !ui.anyOverlayOpen && (e.keyCode == KeyEvent.KEYCODE_PAGE_DOWN || (ctrl && e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) -> {
                ui.pageController?.let { it.goTo(it.currentPage + 1) }; return true
            }
            !ui.anyOverlayOpen && (e.keyCode == KeyEvent.KEYCODE_PAGE_UP || (ctrl && e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) -> {
                ui.pageController?.let { it.goTo(it.currentPage - 1) }; return true
            }
        }
        return false
    }

    // ───────────── LauncherActions ─────────────

    override fun launchApp(key: String, bounds: Rect?) {
        val entry = core.resolveApp(key) ?: run { showMessage("App not found"); return }
        entry.builtIn?.let { builtIn ->
            core.recordLaunch(key)
            when (builtIn) {
                BuiltInApp.FLYER -> openFlyer(FlyerRoute.Dashboard)
                BuiltInApp.LAYOUT_EDITOR -> openFlyer(FlyerRoute.LiveEditor())
                BuiltInApp.WIDGETS -> openWidgetPicker()
            }
            ui.drawerOpen = false
            ui.closeSearch()
            return
        }
        val config = core.config.value
        val screenRect = bounds?.let { toScreen(it) }
        val style = if (ThemeEngine.animationsActive(config.animations, core.traits, config.performance.effects)) config.animations.appLaunch else AppLaunchAnimation.NONE
        val options = AppDiscoveryEngine.makeLaunchOptions(style, screenRect, window.decorView)
        if (core.appsEngine.launch(entry, screenRect, options)) {
            core.recordLaunch(key)
            ui.openFolder = null
            ui.startMenuOpen = false
            window.decorView.postDelayed({ ui.drawerOpen = false; ui.closeSearch() }, 350)
        } else showMessage("Couldn't open ${entry.label}")
    }

    private fun toScreen(r: Rect): android.graphics.Rect {
        val loc = IntArray(2)
        window.decorView.getLocationOnScreen(loc)
        val a = r.toAndroid()
        a.offset(loc[0], loc[1])
        return a
    }

    override fun launchShortcut(item: ShortcutItem, bounds: Rect?) {
        val user = core.appsEngine.userFor(item.userSerial) ?: android.os.Process.myUserHandle()
        if (!core.appsEngine.startShortcut(item.packageName, item.shortcutId, user, bounds?.let { toScreen(it) }, null)) {
            showMessage(if (core.appsEngine.hasShortcutPermission) "Shortcut unavailable" else "Make Flyer Launcher your home app to use shortcuts")
        }
    }

    override fun openFolder(folderId: String, bounds: Rect?) {
        ui.openFolder = OpenFolder(folderId, bounds)
    }

    override fun openDrawer() {
        if (ui.desktopActive) ui.startMenuOpen = true else ui.drawerOpen = true
    }

    override fun openSearch(query: String) {
        ui.searchQuery = query
        ui.searchOpen = true
    }

    override fun openFlyer(route: FlyerRoute) {
        ui.menu = null
        ui.drawerOpen = false
        ui.openFolder = null
        ui.closeSearch()
        ui.flyer.open(route)
    }

    override fun openWidgetPicker() {
        ui.widgetPicker = if (ui.desktopActive) Surface.Desktop else Surface.Page(ui.pageController?.currentPage ?: 0)
    }

    override fun showMessage(text: String) = ui.toast(text)

    override fun perform(action: GestureAction) {
        val config = core.config.value
        when (action.type) {
            ActionType.NONE -> Unit
            ActionType.OPEN_DRAWER -> openDrawer()
            ActionType.OPEN_SEARCH -> openSearch()
            ActionType.OPEN_FLYER -> openFlyer(FlyerRoute.Dashboard)
            ActionType.OPEN_LIVE_EDITOR -> openFlyer(FlyerRoute.LiveEditor())
            ActionType.OPEN_WIDGETS -> openWidgetPicker()
            ActionType.HOME_MENU -> {
                val p = ui.lastGesturePos
                ui.menu = com.flyer.launcher.launcher.home.ItemMenus.homeMenu(ui, this, Rect(p, p))
            }
            ActionType.LAUNCH_APP -> action.target?.let { launchApp(it, null) }
            ActionType.TOGGLE_DESKTOP_MODE -> {
                val next = when (config.desktop.mode) {
                    DesktopMode.AUTO -> if (ui.desktopActive) DesktopMode.OFF else DesktopMode.ON
                    DesktopMode.ON -> DesktopMode.OFF
                    DesktopMode.OFF -> DesktopMode.ON
                }
                core.editHome("Desktop mode ${if (next == DesktopMode.ON) "on" else "off"}") { d -> d.copy(config = d.config.copy(desktop = d.config.desktop.copy(mode = next))) }
            }
            ActionType.TOGGLE_DARK_MODE -> {
                val next = if (isDarkTheme(config)) ThemeMode.LIGHT else ThemeMode.DARK
                core.editHome("Theme ${next.name.lowercase()}") { d -> d.copy(config = d.config.copy(appearance = d.config.appearance.copy(themeMode = next))) }
            }
            ActionType.DEFAULT_PAGE -> ui.pageController?.goTo(config.home.defaultPage)
            ActionType.NEXT_PAGE -> ui.pageController?.let { it.goTo(it.currentPage + 1) }
            ActionType.PREVIOUS_PAGE -> ui.pageController?.let { it.goTo(it.currentPage - 1) }
            ActionType.EXPAND_NOTIFICATIONS, ActionType.QUICK_SETTINGS, ActionType.LOCK_SCREEN,
            ActionType.RECENT_APPS, ActionType.SCREENSHOT,
            -> core.gestures.performSystemAction(action.type).onFailure { e ->
                showMessage(if (e is AccessibilityRequired) "Enable the gesture helper in Flyer › Gestures to use this" else e.message ?: "Not supported on this device")
            }
        }
    }

    // ───────────── FlyerPlatform ─────────────

    override fun pickImage(onResult: (Uri?) -> Unit) {
        onImage = onResult
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun pickFont(onResult: (Uri?) -> Unit) {
        onFont = onResult
        pickFontDoc.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "font/*"))
    }

    override fun createDocument(fileName: String, onResult: (Uri?) -> Unit) {
        onCreateDoc = onResult
        createDoc.launch(fileName)
    }

    override fun openDocument(onResult: (Uri?) -> Unit) {
        onOpenDoc = onResult
        openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    override fun writeText(uri: Uri, text: String): Boolean = runCatching {
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } != null
    }.getOrDefault(false)

    override fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > 8 * 1024 * 1024) null else bytes.decodeToString()
        }
    }.getOrNull()

    override fun isDefaultLauncher(): Boolean = AppDiscoveryEngine.isDefaultLauncher(this)

    override fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.onSuccess { return }
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    override fun openAccessibilitySettings() {
        runCatching { startActivity(core.gestures.accessibilitySettingsIntent()) }
    }

    override fun isAccessibilityHelperEnabled(): Boolean = core.gestures.isAccessibilityEnabled()

    override fun openWallpaperPicker() {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) }
            .onFailure { showMessage("No wallpaper picker available") }
    }

    override fun addWidget(choice: WidgetChoice, surface: Surface, onAdded: (WorkspaceItem) -> Unit) {
        when (choice) {
            is WidgetChoice.BuiltIn -> {
                val config = core.config.value
                val cols = if (surface == Surface.Desktop) config.desktop.columns else config.home.columns
                val span = when (choice.kind) {
                    BuiltInWidgetKind.CLOCK -> Cell(spanX = minOf(4, cols), spanY = 2)
                    else -> Cell(spanX = minOf(4, cols), spanY = 1)
                }
                onAdded(BuiltInWidgetItem(kind = choice.kind, cell = span))
            }
            is WidgetChoice.Native -> {
                cancelPendingWidget()
                val id = core.widgets.allocateId()
                val pending = PendingWidget(id, choice.provider, surface, onAdded)
                pendingWidget = pending
                if (core.widgets.bindIfAllowed(id, choice.provider)) configureOrFinish(pending)
                else runCatching { bindWidget.launch(core.widgets.bindIntent(id, choice.provider)) }.onFailure { cancelPendingWidget() }
            }
        }
    }

    private fun restoreWidget(item: WidgetItem) {
        val provider = core.widgets.findProvider(item.provider, item.providerUser)
        if (provider == null) {
            showMessage("The app that provides this widget is not installed")
            return
        }
        addWidget(WidgetChoice.Native(provider), Surface.Page(0)) { restored ->
            val newId = (restored as? WidgetItem)?.appWidgetId ?: return@addWidget
            core.editHome("Restore widget") { d ->
                d.copy(layout = WorkspaceEngine.updateItem(d.layout, item.id) { (it as WidgetItem).copy(appWidgetId = newId) })
            }
        }
    }

    private fun configureOrFinish(pending: PendingWidget) {
        val info = core.widgets.providerInfo(pending.id)
        if (info != null && core.widgets.needsConfiguration(info)) {
            if (!core.widgets.startConfigure(this, pending.id, REQUEST_CONFIGURE)) cancelPendingWidget()
        } else finishPendingWidget(pending)
    }

    private fun finishPendingWidget(pending: PendingWidget) {
        pendingWidget = null
        val config = core.config.value
        val dm = resources.displayMetrics
        val desktop = pending.surface == Surface.Desktop
        val columns = if (desktop) config.desktop.columns else config.home.columns
        val rows = if (desktop) config.desktop.rows else config.home.rows
        val widthDp = dm.widthPixels / dm.density - config.home.marginStartDp - config.home.marginEndDp
        val heightDp = dm.heightPixels / dm.density * 0.7f
        val info = core.widgets.providerInfo(pending.id) ?: pending.provider.info
        val spans = core.widgets.spansFor(info, widthDp / columns, heightDp / rows, columns, rows)
        pending.onAdded(
            WidgetItem(
                appWidgetId = pending.id,
                provider = pending.provider.info.provider.flattenToShortString(),
                providerUser = pending.provider.userSerial,
                cell = Cell(spanX = spans.defaultX, spanY = spans.defaultY),
            ),
        )
    }

    private fun cancelPendingWidget() {
        pendingWidget?.let { core.widgets.deleteId(it.id) }
        pendingWidget = null
    }

    @Deprecated("AppWidgetHost configuration still reports through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CONFIGURE -> {
                val pending = pendingWidget ?: return
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id) ?: pending.id
                if (resultCode == RESULT_OK && id == pending.id) finishPendingWidget(pending) else cancelPendingWidget()
            }
            REQUEST_RECONFIGURE -> Unit
        }
    }

    companion object {
        private const val REQUEST_CONFIGURE = 4101
        private const val REQUEST_RECONFIGURE = 4102
    }
}
