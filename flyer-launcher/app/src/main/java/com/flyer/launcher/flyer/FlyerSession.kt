package com.flyer.launcher.flyer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyer.launcher.core.LauncherApi
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.history.History
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.persistence.LauncherDocument
import com.flyer.launcher.core.workspace.LayoutValidator

/** Config sections that can be reset independently. */
enum class ConfigSection(val title: String) {
    HOME("Home screen"), ICONS("Icons"), DOCK("Dock"), DRAWER("App drawer"), FOLDERS("Folders"),
    WIDGETS("Widgets"), GESTURES("Gestures"), ANIMATIONS("Animations"), APPEARANCE("Appearance"),
    TYPOGRAPHY("Typography"), DESKTOP("Desktop mode"), SEARCH("Search"), PERFORMANCE("Performance"), ADVANCED("Advanced"),
    STYLE("Materials & effects"), LOCK("Lock");

    fun reset(c: FlyerConfig): FlyerConfig {
        val d = FlyerConfig()
        return when (this) {
            HOME -> c.copy(home = d.home)
            ICONS -> c.copy(icons = d.icons)
            DOCK -> c.copy(dock = d.dock)
            DRAWER -> c.copy(drawer = d.drawer)
            FOLDERS -> c.copy(folders = d.folders)
            WIDGETS -> c.copy(widgets = d.widgets)
            GESTURES -> c.copy(gestures = d.gestures)
            ANIMATIONS -> c.copy(animations = d.animations)
            APPEARANCE -> c.copy(appearance = d.appearance)
            TYPOGRAPHY -> c.copy(typography = d.typography)
            DESKTOP -> c.copy(desktop = d.desktop)
            SEARCH -> c.copy(search = d.search)
            PERFORMANCE -> c.copy(performance = d.performance)
            ADVANCED -> c.copy(advanced = d.advanced)
            STYLE -> c.copy(style = d.style)
            LOCK -> c.copy(lock = d.lock)
        }
    }

    fun isDefault(c: FlyerConfig): Boolean = reset(c) == c
}

/**
 * An editing session inside Flyer. Every control edits a *draft* document; previews render the
 * draft; [apply] validates and commits it to the launcher through [LauncherApi]. With
 * "apply changes instantly" every edit is committed as it happens (history still works).
 */
@Stable
class FlyerSession(private val api: LauncherApi, initial: LauncherDocument) {

    private val history = History(initial, initial.config.advanced.undoHistorySize)

    var draft by mutableStateOf(initial)
        private set
    var baseline by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var undoLabel by mutableStateOf<String?>(null)
        private set
    var redoLabel by mutableStateOf<String?>(null)
        private set
    var timeline by mutableStateOf<List<String>>(emptyList())
        private set

    val isDirty: Boolean get() = draft != baseline

    /** What previews render: the draft passed through the same validators the launcher uses. */
    val preview: LauncherDocument by derivedStateOf {
        val c = ConfigValidator.sanitize(draft.config)
        LauncherDocument(config = c, layout = LayoutValidator.sanitize(draft.layout, c))
    }

    val config: FlyerConfig get() = draft.config
    val layout: HomeLayout get() = draft.layout

    private val autoApply: Boolean get() = draft.config.advanced.autoApplyFlyerChanges

    fun update(label: String, coalesceKey: String? = null, transform: (LauncherDocument) -> LauncherDocument) {
        val next = transform(draft)
        if (next == draft) return
        history.push(next, label, coalesceKey)
        draft = next
        syncFlags()
        if (autoApply) commit(label)
    }

    fun updateConfig(label: String, coalesceKey: String? = label, transform: (FlyerConfig) -> FlyerConfig) =
        update(label, coalesceKey) { it.copy(config = transform(it.config)) }

    fun updateLayout(label: String, coalesceKey: String? = null, transform: (HomeLayout) -> HomeLayout) =
        update(label, coalesceKey) { it.copy(layout = transform(it.layout)) }

    fun undo() {
        history.undo()?.let { draft = it; syncFlags(); if (autoApply) commit("Undo") }
    }

    fun redo() {
        history.redo()?.let { draft = it; syncFlags(); if (autoApply) commit("Redo") }
    }

    fun apply() {
        commit("Applied from Flyer")
    }

    private fun commit(label: String) {
        api.commit(draft, label)
        // The launcher may have repaired the document (validators); adopt its version as baseline.
        val committed = api.document.value
        baseline = committed
        if (committed != draft) {
            draft = committed
            history.reset(committed, label)
        }
        syncFlags()
    }

    fun discard() {
        draft = baseline
        history.reset(baseline)
        syncFlags()
    }

    fun resetSection(section: ConfigSection) = updateConfig("Reset ${section.title}", null) { section.reset(it) }

    fun resetAllSettings() = updateConfig("Reset all settings", null) { FlyerConfig() }

    /** Replaces the draft entirely (import / snapshot restore / theme). */
    fun replace(doc: LauncherDocument, label: String) = update(label) { doc }

    /** Follow launcher-side changes (e.g. an app uninstalled) while nothing is being edited. */
    fun onLauncherChanged(doc: LauncherDocument) {
        if (!isDirty && doc != baseline) {
            baseline = doc
            draft = doc
            history.reset(doc)
            syncFlags()
        }
    }

    private fun syncFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
        undoLabel = history.undoLabel
        redoLabel = history.redoLabel
        timeline = history.timeline()
        history.setCapacity(draft.config.advanced.undoHistorySize)
    }
}

/** Open/close state and navigation of Flyer inside the launcher activity. */
@Stable
class FlyerController(private val core: LauncherCore) {
    var isOpen by mutableStateOf(false)
        private set
    var backStack by mutableStateOf<List<FlyerRoute>>(listOf(FlyerRoute.Dashboard))
        private set
    var session by mutableStateOf<FlyerSession?>(null)
        private set
    var confirmClose by mutableStateOf(false)

    val route: FlyerRoute get() = backStack.last()

    fun open(route: FlyerRoute = FlyerRoute.Dashboard) {
        if (!isOpen) {
            session = FlyerSession(core, core.document.value)
            isOpen = true
            backStack = if (route == FlyerRoute.Dashboard) listOf(FlyerRoute.Dashboard) else listOf(FlyerRoute.Dashboard, route)
        } else navigate(route)
    }

    fun navigate(route: FlyerRoute) {
        if (route == FlyerRoute.Dashboard) {
            backStack = listOf(FlyerRoute.Dashboard)
            return
        }
        if (backStack.last() == route) return
        // Sidebar navigation replaces the current module instead of stacking modules endlessly.
        val stack = backStack.toMutableList()
        if (stack.size > 1 && stack.last() is FlyerRoute.Category && route is FlyerRoute.Category) stack.removeAt(stack.lastIndex)
        stack += route
        backStack = stack
    }

    fun back() {
        if (backStack.size > 1) backStack = backStack.dropLast(1) else requestClose()
    }

    fun requestClose() {
        val s = session
        if (s != null && s.isDirty) confirmClose = true else close()
    }

    fun close(applyChanges: Boolean? = null) {
        val s = session
        if (s != null && applyChanges == true) s.apply()
        confirmClose = false
        isOpen = false
        session = null
        backStack = listOf(FlyerRoute.Dashboard)
    }
}
