package com.flyer.launcher.core.apps

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AppDiscovery"

/**
 * App Discovery Engine: enumerates launchable activities for every user profile through
 * [LauncherApps], keeps the list current through package callbacks and launches apps.
 *
 * Built-in launcher apps (Flyer and friends) are part of the same list so every surface (home,
 * dock, folders, drawer, search) treats them exactly like installed apps.
 */
class AppDiscoveryEngine(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val pm = context.packageManager

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Emits component keys of apps whose package was really removed (not just made unavailable). */
    private val _removed = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val removedPackages: SharedFlow<String> = _removed

    /** Emits keys of newly installed apps (for "add new apps to home"). */
    private val _added = MutableSharedFlow<List<String>>(extraBufferCapacity = 16)
    val addedApps: SharedFlow<List<String>> = _added

    private var byKey: Map<String, AppEntry> = emptyMap()
    private var refreshJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _removed.tryEmit(packageName)
            scheduleRefresh()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleRefresh(newPackage = packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleRefresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = scheduleRefresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = scheduleRefresh()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = scheduleRefresh()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = scheduleRefresh()
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            _shortcutsVersion.value++
        }
    }

    private val _shortcutsVersion = MutableStateFlow(0)
    val shortcutsVersion: StateFlow<Int> = _shortcutsVersion

    private var registered = false
    private val pendingNew = mutableSetOf<String>()

    fun start() {
        if (!registered) {
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        }
        scheduleRefresh(immediate = true)
    }

    fun stop() {
        if (registered) {
            launcherApps.unregisterCallback(callback)
            registered = false
        }
    }

    private fun scheduleRefresh(immediate: Boolean = false, newPackage: String? = null) {
        if (newPackage != null) synchronized(pendingNew) { pendingNew += newPackage }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (!immediate) delay(250) // coalesce bursts of package events
            val before = byKey.keys
            val list = withContext(Dispatchers.IO) { loadAll() }
            byKey = list.associateBy { it.key }
            _apps.value = list
            _loaded.value = true
            val newPkgs = synchronized(pendingNew) { pendingNew.toSet().also { pendingNew.clear() } }
            if (newPkgs.isNotEmpty() && before.isNotEmpty()) {
                val added = list.filter { it.packageName in newPkgs && it.key !in before }.map { it.key }
                if (added.isNotEmpty()) _added.tryEmit(added)
            }
        }
    }

    private fun loadAll(): List<AppEntry> {
        val result = ArrayList<AppEntry>(256)
        val profiles = runCatching { userManager.userProfiles }.getOrDefault(listOf(Process.myUserHandle()))
        val me = Process.myUserHandle()
        for (user in profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities: List<LauncherActivityInfo> = runCatching { launcherApps.getActivityList(null, user) }
                .onFailure { Log.w(TAG, "Profile $serial unavailable", it) }
                .getOrDefault(emptyList())
            for (info in activities) {
                // The launcher itself is reachable through its built-in apps, not as an app tile.
                if (info.componentName.packageName == context.packageName) continue
                val appInfo = info.applicationInfo
                val pkgInfo = runCatching { pm.getPackageInfo(info.componentName.packageName, 0) }.getOrNull()
                result += AppEntry(
                    key = AppEntry.keyOf(info.componentName, serial),
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    packageName = info.componentName.packageName,
                    component = info.componentName,
                    user = user,
                    userSerial = serial,
                    isWorkProfile = user != me,
                    builtIn = null,
                    installTime = pkgInfo?.firstInstallTime ?: 0L,
                    updateTime = pkgInfo?.lastUpdateTime ?: 0L,
                    category = appInfo.category,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
        }
        BuiltInApp.entries.forEach { b ->
            result += AppEntry(
                key = b.key, label = b.label, packageName = context.packageName, component = null,
                user = me, userSerial = 0, isWorkProfile = false, builtIn = b,
                installTime = 0, updateTime = 0, category = -1, isSystemApp = true,
            )
        }
        result.sortBy { it.sortLabel }
        return result
    }

    fun resolve(key: String): AppEntry? = byKey[key] ?: BuiltInApp.fromKey(key)?.let { b -> _apps.value.firstOrNull { it.builtIn == b } }

    fun userFor(serial: Long): UserHandle? = runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()

    // ───────────── Launching ─────────────

    /**
     * Starts an installed app. Built-in apps are handled by the caller (they open launcher-internal UI).
     * Returns false if the app could not be started.
     */
    fun launch(entry: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean {
        val component = entry.component ?: return false
        val user = entry.user ?: Process.myUserHandle()
        return try {
            launcherApps.startMainActivity(component, user, sourceBounds, options)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Launch failed for ${entry.key}", e)
            false
        }
    }

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        val component = entry.component ?: return
        runCatching { launcherApps.startAppDetailsActivity(component, entry.user ?: Process.myUserHandle(), sourceBounds, null) }
    }

    fun uninstall(entry: AppEntry) {
        if (entry.builtIn != null) return // built-in launcher apps can never be uninstalled
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_USER, entry.user)
        runCatching { context.startActivity(intent) }
    }

    fun canUninstall(entry: AppEntry): Boolean = entry.builtIn == null && !entry.isSystemApp

    // ───────────── App shortcuts (requires being the default launcher) ─────────────

    val hasShortcutPermission: Boolean get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutsFor(entry: AppEntry): List<ShortcutInfo> {
        val component = entry.component ?: return emptyList()
        if (!hasShortcutPermission) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(component.packageName)
            .setActivity(component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return runCatching { launcherApps.getShortcuts(query, entry.user ?: Process.myUserHandle()).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled && (it.isDynamic || it.isDeclaredInManifest) }
            .sortedBy { it.rank }
            .take(5)
    }

    /** Every dynamic/manifest shortcut of every app (for search). Requires the default-launcher role. */
    fun allShortcuts(): List<ShortcutInfo> {
        if (!hasShortcutPermission) return emptyList()
        val profiles = runCatching { userManager.userProfiles }.getOrDefault(listOf(Process.myUserHandle()))
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
        )
        return profiles.flatMap { user -> runCatching { launcherApps.getShortcuts(query, user).orEmpty() }.getOrDefault(emptyList()) }
            .filter { it.isEnabled }
    }

    fun findShortcut(packageName: String, id: String, user: UserHandle): ShortcutInfo? {
        if (!hasShortcutPermission) return null
        val query = LauncherApps.ShortcutQuery().setPackage(packageName).setShortcutIds(listOf(id)).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        return runCatching { launcherApps.getShortcuts(query, user)?.firstOrNull() }.getOrNull()
    }

    fun startShortcut(info: ShortcutInfo, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startShortcut(info, sourceBounds, options)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Shortcut launch failed", e)
        false
    }

    fun startShortcut(packageName: String, id: String, user: UserHandle, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startShortcut(packageName, id, sourceBounds, options, user)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Shortcut launch failed", e)
        false
    }

    /** Keeps a pinned shortcut pinned for this launcher (needed when it is placed on the home screen). */
    fun pinShortcut(packageName: String, ids: List<String>, user: UserHandle) {
        if (!hasShortcutPermission) return
        runCatching { launcherApps.pinShortcuts(packageName, ids, user) }
    }

    // ───────────── Default layout helpers ─────────────

    /** Resolves well-known app roles to keys for the first-run layout. */
    fun defaultAppKeys(): Map<String, String> {
        val serial = userManager.getSerialNumberForUser(Process.myUserHandle())
        fun keyFor(intent: Intent): String? {
            val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
            val pkg = ri.activityInfo?.packageName ?: return null
            if (pkg == "android") return null // resolver/chooser, no default set
            return _apps.value.firstOrNull { it.packageName == pkg && it.builtIn == null && !it.isWorkProfile }?.key
                ?: _apps.value.firstOrNull { it.packageName == pkg }?.key
                ?: ri.activityInfo?.let { AppEntry.keyOf(ComponentName(pkg, it.name), serial) }
                    ?.takeIf { byKey.containsKey(it) }
        }
        val roles = linkedMapOf<String, String>()
        keyFor(Intent(Intent.ACTION_DIAL))?.let { roles["phone"] = it }
        keyFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))?.let { roles["messages"] = it }
        keyFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))?.let { roles["browser"] = it }
        keyFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))?.let { roles["camera"] = it }
        keyFor(Intent(Settings.ACTION_SETTINGS))?.let { roles["settings"] = it }
        keyFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))?.let { roles["gallery"] = it }
        keyFor(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.chrome")))?.let { roles["store"] = it }
        keyFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL))?.let { roles["email"] = it }
        keyFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))?.let { roles["calendar"] = it }
        keyFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS))?.let { roles["maps"] = it }
        return roles
    }

    companion object {
        fun makeLaunchOptions(
            style: com.flyer.launcher.core.config.AppLaunchAnimation,
            bounds: Rect?,
            view: android.view.View?,
        ): Bundle? {
            if (bounds == null || view == null) return null
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val x = bounds.left - loc[0]
            val y = bounds.top - loc[1]
            return when (style) {
                com.flyer.launcher.core.config.AppLaunchAnimation.SCALE_UP ->
                    ActivityOptions.makeScaleUpAnimation(view, x, y, bounds.width(), bounds.height()).toBundle()
                com.flyer.launcher.core.config.AppLaunchAnimation.CLIP_REVEAL ->
                    ActivityOptions.makeClipRevealAnimation(view, x, y, bounds.width(), bounds.height()).toBundle()
                com.flyer.launcher.core.config.AppLaunchAnimation.FADE ->
                    ActivityOptions.makeCustomAnimation(view.context, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
                com.flyer.launcher.core.config.AppLaunchAnimation.NONE ->
                    ActivityOptions.makeCustomAnimation(view.context, 0, 0).toBundle()
                com.flyer.launcher.core.config.AppLaunchAnimation.SYSTEM -> null
            }
        }

        fun isDefaultLauncher(context: Context): Boolean {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return ri?.activityInfo?.packageName == context.packageName
        }
    }
}
