package com.flyer.launcher.core

import android.app.ActivityManager
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.flyer.launcher.core.apps.AppDiscoveryEngine
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.apps.BuiltInApp
import com.flyer.launcher.core.apps.IconPackManager
import com.flyer.launcher.core.config.EffectsLevel
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.gestures.GestureEngine
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.BuiltInWidgetKind
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.persistence.BackupKind
import com.flyer.launcher.core.persistence.DocumentCodec
import com.flyer.launcher.core.persistence.LauncherDocument
import com.flyer.launcher.core.persistence.PersistenceEngine
import com.flyer.launcher.core.render.IconEngine
import com.flyer.launcher.core.theme.DeviceTraits
import com.flyer.launcher.core.widgets.WidgetEngine
import com.flyer.launcher.core.workspace.WorkspaceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The internal API through which Flyer (and any future customization module) talks to the
 * launcher. Flyer never reaches into launcher UI code; it reads and commits documents through this
 * contract, and the launcher renders whatever is committed.
 */
interface LauncherApi {
    val document: StateFlow<LauncherDocument>
    val apps: StateFlow<List<AppEntry>>
    val traits: DeviceTraits

    /** Validates and persists a complete document (config + layout). */
    fun commit(doc: LauncherDocument, label: String)

    fun resolveApp(key: String): AppEntry?

    fun encodeBackup(doc: LauncherDocument, kind: BackupKind, name: String): String

    fun decodeBackup(text: String): DocumentCodec.Backup
}

/**
 * Launcher Core: composition root for every engine. One instance per process (owned by the
 * Application) so the launcher activity and Flyer share the same state.
 */
class LauncherCore(val app: Application) : LauncherApi {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val persistence = PersistenceEngine(app)
    val appsEngine = AppDiscoveryEngine(app, scope)
    val iconPacks = IconPackManager(app)
    val icons = IconEngine(app, appsEngine, iconPacks)
    val widgets = WidgetEngine(app)
    val gestures = GestureEngine(app)
    val lock = com.flyer.launcher.core.lock.LockEngine(app, scope)
    val importer = com.flyer.launcher.core.apps.LayoutImporter(app, appsEngine)

    override val traits: DeviceTraits = run {
        val am = app.getSystemService(ActivityManager::class.java)
        val scale = runCatching { Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        DeviceTraits(
            blurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
            lowRam = am?.isLowRamDevice == true,
            systemAnimationsOff = scale == 0f,
        )
    }

    private val _document = MutableStateFlow(LauncherDocument())
    override val document: StateFlow<LauncherDocument> = _document.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val config: StateFlow<FlyerConfig> = _document.map { it.config }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, FlyerConfig())
    val layout: StateFlow<HomeLayout> = _document.map { it.layout }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, HomeLayout())

    override val apps: StateFlow<List<AppEntry>> = appsEngine.apps

    private val pendingWidgetDeletes = mutableSetOf<Int>()

    /** One-step undo for direct manipulation on the home screen (drag & drop, removals). */
    private val _lastHomeEdit = MutableStateFlow<Pair<String, LauncherDocument>?>(null)
    val lastHomeEdit: StateFlow<Pair<String, LauncherDocument>?> = _lastHomeEdit.asStateFlow()

    /** First run on a low-RAM device: start with lighter effects. */
    private fun tuneForDevice(doc: LauncherDocument): LauncherDocument {
        if (!traits.lowRam) return doc
        val c = doc.config
        return doc.copy(
            config = c.copy(
                performance = c.performance.copy(effects = EffectsLevel.MINIMAL, blurEffects = false, lowMemoryMode = true, iconCacheMb = 6),
            ),
        )
    }

    private fun onPackageRemoved(pkg: String) {
        // A package "removed" callback is also sent during updates; only clean up if it's really gone.
        val stillInstalled = runCatching { app.packageManager.getPackageInfo(pkg, 0); true }.getOrElse { it !is PackageManager.NameNotFoundException }
        if (stillInstalled) return
        val doc = _document.value
        var layout = doc.layout
        val keys = WorkspaceEngine.placedAppKeys(layout).filter { AppEntry.packageOf(it) == pkg }
        keys.forEach { layout = WorkspaceEngine.removeApp(layout, it) }
        layout = layout.copy(
            hiddenApps = layout.hiddenApps.filterNot { AppEntry.packageOf(it) == pkg }.toSet(),
            appOverrides = layout.appOverrides.filterKeys { AppEntry.packageOf(it) != pkg },
        )
        if (layout != doc.layout) commit(doc.copy(layout = layout), "App uninstalled")
        icons.clear()
    }

    override fun commit(doc: LauncherDocument, label: String) = commit(doc, label, deferWidgetCleanup = false)

    private fun commit(doc: LauncherDocument, label: String, deferWidgetCleanup: Boolean) {
        val previous = _document.value
        val config = com.flyer.launcher.core.config.ConfigValidator.sanitize(doc.config)
        val clean = doc.copy(config = config, layout = com.flyer.launcher.core.workspace.LayoutValidator.sanitize(doc.layout, config))
        // Free widget ids that are no longer referenced anywhere. Home-screen edits keep them
        // until their undo window closes so "Undo" can bring a removed widget back intact.
        val removed = widgetIds(previous.layout) - widgetIds(clean.layout)
        pendingWidgetDeletes += removed
        _document.value = clean
        if (!deferWidgetCleanup) flushWidgetDeletes()
        scope.launch {
            runCatching { persistence.update { clean } }.onFailure { Log.e("LauncherCore", "persist", it) }
        }
    }

    /** Applies an edit made directly on the home screen and remembers it for a one-step undo. */
    fun editHome(label: String, transform: (LauncherDocument) -> LauncherDocument) {
        val before = _document.value
        val after = transform(before)
        if (after == before) return
        flushWidgetDeletes()
        commit(after, label, deferWidgetCleanup = true)
        _lastHomeEdit.value = label to before
    }

    fun undoHomeEdit() {
        val (label, before) = _lastHomeEdit.value ?: return
        _lastHomeEdit.value = null
        commit(before, "Undo $label")
    }

    fun clearHomeUndo() {
        _lastHomeEdit.value = null
        flushWidgetDeletes()
    }

    private fun flushWidgetDeletes() {
        val live = widgetIds(_document.value.layout)
        pendingWidgetDeletes.filter { it !in live }.forEach { widgets.deleteId(it) }
        pendingWidgetDeletes.clear()
    }

    override fun resolveApp(key: String): AppEntry? = appsEngine.resolve(key)

    override fun encodeBackup(doc: LauncherDocument, kind: BackupKind, name: String): String =
        DocumentCodec.encodeBackup(doc, kind, name, System.currentTimeMillis())

    override fun decodeBackup(text: String): DocumentCodec.Backup = DocumentCodec.decodeBackup(text)

    fun recordLaunch(key: String) {
        scope.launch { persistence.recordLaunch(key) }
    }

    /** Lists the sorted drawer apps, honoring hidden apps and overrides. */
    fun visibleApps(): StateFlow<List<AppEntry>> = combine(apps, layout) { list, l ->
        list.filter { it.key !in l.hiddenApps }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun widgetIds(layout: HomeLayout): Set<Int> = buildSet {
        fun visit(items: List<WorkspaceItem>) = items.forEach { if (it is WidgetItem && it.appWidgetId >= 0) add(it.appWidgetId) }
        layout.pages.forEach { visit(it.items) }
        visit(layout.desktop.items)
    }

    /** First-run arrangement built from the device's default apps. */
    fun buildDefaultLayout(config: FlyerConfig): HomeLayout {
        val roles = appsEngine.defaultAppKeys()
        val cols = config.home.columns
        val rows = config.home.rows
        val items = mutableListOf<WorkspaceItem>()
        items += BuiltInWidgetItem(kind = BuiltInWidgetKind.CLOCK, cell = Cell(0, 0, cols, 1))
        val bottomRow = listOfNotNull(BuiltInApp.FLYER.key, roles["settings"], roles["gallery"], roles["store"], roles["maps"]).distinct().take(cols)
        bottomRow.forEachIndexed { i, key -> items += AppItem(appKey = key, cell = Cell(i, rows - 1)) }
        val dockKeys = listOfNotNull(roles["phone"], roles["messages"], roles["browser"], roles["camera"], roles["email"], roles["calendar"])
            .distinct().filter { it !in bottomRow }.take(config.dock.slots)
        val dock = dockKeys.mapIndexed { i, key -> AppItem(appKey = key, cell = Cell(i, 0)) }
        return HomeLayout(
            pages = listOf(HomePage(items = items), HomePage()),
            dock = dock,
            desktop = com.flyer.launcher.core.model.DesktopLayout(
                items = listOfNotNull(BuiltInApp.FLYER.key, roles["browser"], roles["settings"], roles["gallery"]).distinct()
                    .mapIndexed { i, key -> AppItem(appKey = key, cell = Cell(0, i)) },
                taskbarPins = listOfNotNull(roles["browser"], roles["email"], roles["gallery"], roles["settings"]).distinct(),
            ),
            initialized = true,
        )
    }

    // Initialization runs last so every property above is ready before the first commit.
    init {
        // Screen off: forget recent unlocks and, if enabled, lock the launcher.
        val screenOff = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                lock.clearAuthorization()
                if (config.value.lock.lockOnScreenOff) scope.launch { lock.lock() }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            app, screenOff, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            val loaded = runCatching { persistence.load() }.onFailure { Log.e("LauncherCore", "load", it) }.getOrDefault(LauncherDocument())
            val tuned = if (!loaded.layout.initialized) tuneForDevice(loaded) else loaded
            _document.value = tuned
            _ready.value = true
            appsEngine.start()
            appsEngine.loaded.filter { it }.first()
            if (!_document.value.layout.initialized) {
                commit(_document.value.copy(layout = buildDefaultLayout(_document.value.config)), "Initial layout")
            }
        }
        scope.launch {
            appsEngine.removedPackages.collect { pkg -> onPackageRemoved(pkg) }
        }
        scope.launch {
            appsEngine.addedApps.collect { keys ->
                val doc = _document.value
                if (doc.config.advanced.addNewAppsToHome) {
                    var layout = doc.layout
                    keys.filter { it !in WorkspaceEngine.placedAppKeys(layout) }.forEach { layout = WorkspaceEngine.addApp(layout, it, doc.config) }
                    commit(doc.copy(layout = layout), "New app added")
                }
            }
        }
        scope.launch {
            config.collect { c ->
                icons.setCacheSizeMb(if (c.performance.lowMemoryMode) minOf(c.performance.iconCacheMb, 6) else c.performance.iconCacheMb)
            }
        }
    }
}
