package com.flyer.launcher.core.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.UserManager
import android.util.Log
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import org.xmlpull.v1.XmlPullParser

/**
 * "Copy original home screen layout": rebuilds the home screen of another launcher (usually the
 * phone's stock launcher).
 *
 * 1. Its *current* arrangement, read from the launcher's own "favorites" provider. Most modern
 *    launchers protect this with a signature permission, so it usually isn't readable.
 * 2. Otherwise its *original (factory) layout*, parsed from the launcher's built-in
 *    default-workspace XML (the AOSP Launcher3 format used by most stock launchers), or from a
 *    partner customization package.
 *
 * Widgets can't be bound without the user, so they come back as "tap to restore" placeholders.
 */
class LayoutImporter(private val context: Context, private val apps: AppDiscoveryEngine) {

    data class SourceLauncher(val packageName: String, val label: String, val isSystem: Boolean)

    enum class Origin { CURRENT_LAYOUT, FACTORY_LAYOUT }

    data class Imported(
        val origin: Origin,
        val source: SourceLauncher,
        val pages: List<HomePage>,
        val dock: List<WorkspaceItem>,
        val columns: Int?,
        val rows: Int?,
        val skipped: Int,
    ) {
        val appCount get() = (pages.flatMap { it.items } + dock).sumOf { if (it is FolderItem) it.apps.size else if (it is AppItem) 1 else 0 }
        val folderCount get() = (pages.flatMap { it.items } + dock).count { it is FolderItem }
        val widgetCount get() = pages.flatMap { it.items }.count { it is WidgetItem }

        /** Replaces pages and dock of [base]; desktop, hidden apps, groups and custom icons are kept. */
        fun applyTo(base: HomeLayout): HomeLayout = base.copy(pages = pages.ifEmpty { listOf(HomePage()) }, dock = dock, initialized = true)
    }

    class ImportException(message: String) : Exception(message)

    private val pm = context.packageManager

    /** Other installed home apps, stock launchers first. */
    fun launchers(): List<SourceLauncher> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName || pkg == "android" || pkg == "com.android.settings") return@mapNotNull null
            val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            SourceLauncher(pkg, ri.loadLabel(pm).toString(), isSystem)
        }.distinctBy { it.packageName }.sortedWith(compareByDescending<SourceLauncher> { it.isSystem }.thenBy { it.label })
    }

    /** Must be called off the main thread. */
    fun import(source: SourceLauncher): Imported {
        readCurrentLayout(source)?.let { return it }
        readFactoryLayout(source)?.let { return it }
        throw ImportException("${source.label} doesn't share its layout and has no built-in default layout Flyer can read.")
    }

    // ───────────── 1. Current layout via the launcher's favorites provider ─────────────

    private fun readCurrentLayout(source: SourceLauncher): Imported? {
        val authorities = listOf("${source.packageName}.settings", "${source.packageName}.provider.settings")
        for (authority in authorities) {
            val uri = Uri.parse("content://$authority/favorites")
            val rows = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    fun col(name: String) = c.getColumnIndex(name)
                    val list = mutableListOf<Row>()
                    while (c.moveToNext()) {
                        list += Row(
                            id = c.getLongOr(col("_id"), -1),
                            title = c.getStringOr(col("title")),
                            intent = c.getStringOr(col("intent")),
                            container = c.getLongOr(col("container"), -100),
                            screen = c.getLongOr(col("screen"), 0),
                            x = c.getIntOr(col("cellX"), 0),
                            y = c.getIntOr(col("cellY"), 0),
                            spanX = c.getIntOr(col("spanX"), 1),
                            spanY = c.getIntOr(col("spanY"), 1),
                            type = c.getIntOr(col("itemType"), 0),
                            provider = c.getStringOr(col("appWidgetProvider")),
                            profile = c.getLongOr(col("profileId"), 0),
                            rank = c.getIntOr(col("rank"), 0),
                        )
                    }
                    list
                }
            } catch (e: Exception) {
                Log.i(TAG, "Favorites of ${source.packageName} not readable ($authority): ${e.javaClass.simpleName}")
                null
            }
            if (!rows.isNullOrEmpty()) return fromRows(source, rows)
        }
        return null
    }

    private data class Row(
        val id: Long, val title: String?, val intent: String?, val container: Long, val screen: Long,
        val x: Int, val y: Int, val spanX: Int, val spanY: Int, val type: Int, val provider: String?, val profile: Long, val rank: Int,
    )

    private fun fromRows(source: SourceLauncher, rows: List<Row>): Imported {
        var skipped = 0
        fun appKey(r: Row): String? = r.intent?.let { keyForIntentUri(it, r.profile) } ?: run { skipped++; null }
        val folders = rows.filter { it.type == TYPE_FOLDER }.associateBy { it.id }
        val folderChildren = rows.filter { it.container in folders.keys }.groupBy { it.container }
        fun toItem(r: Row): WorkspaceItem? = when (r.type) {
            TYPE_APP, TYPE_SHORTCUT, TYPE_DEEP_SHORTCUT -> appKey(r)?.let { AppItem(appKey = it, cell = Cell(r.x, r.y)) }
            TYPE_FOLDER -> {
                val children = folderChildren[r.id].orEmpty().sortedWith(compareBy({ it.rank }, { it.y }, { it.x })).mapNotNull { appKey(it) }.distinct()
                when (children.size) {
                    0 -> null
                    1 -> AppItem(appKey = children.first(), cell = Cell(r.x, r.y))
                    else -> FolderItem(name = r.title?.takeIf { it.isNotBlank() } ?: "Folder", apps = children, cell = Cell(r.x, r.y))
                }
            }
            TYPE_WIDGET, TYPE_CUSTOM_WIDGET -> r.provider?.let { WidgetItem(appWidgetId = -1, provider = it, providerUser = r.profile, cell = Cell(r.x, r.y, r.spanX.coerceAtLeast(1), r.spanY.coerceAtLeast(1))) }
            else -> { skipped++; null }
        }
        val desktop = rows.filter { it.container == CONTAINER_DESKTOP }
        val screenOrder = desktop.map { it.screen }.distinct().sorted()
        val pages = screenOrder.map { screen -> HomePage(items = desktop.filter { it.screen == screen }.mapNotNull(::toItem)) }
        val dock = rows.filter { it.container == CONTAINER_HOTSEAT }.sortedWith(compareBy({ it.screen }, { it.x }))
            .mapNotNull(::toItem).mapIndexed { i, it -> it.withCell(Cell(i, 0)) }
        val cols = (desktop.maxOfOrNull { it.x + it.spanX } ?: 0).takeIf { it in 3..12 }
        val rowsCount = (desktop.maxOfOrNull { it.y + it.spanY } ?: 0).takeIf { it in 3..14 }
        return Imported(Origin.CURRENT_LAYOUT, source, pages, dock, cols, rowsCount, skipped)
    }

    // ───────────── 2. Factory layout from the launcher's default-workspace XML ─────────────

    private class Parsed {
        val pages = sortedMapOf<Int, MutableList<WorkspaceItem>>()
        val dock = mutableListOf<Pair<Int, WorkspaceItem>>()
        var skipped = 0
    }

    @SuppressLint("DiscouragedApi")
    private fun readFactoryLayout(source: SourceLauncher): Imported? {
        val candidates = mutableListOf<Pair<Resources, String>>()
        // Partner customization packages (OEM / carrier layouts) take precedence, like in Launcher3.
        pm.queryBroadcastReceivers(Intent("com.android.launcher3.action.PARTNER_CUSTOMIZATION"), 0).forEach { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@forEach
            runCatching { pm.getResourcesForApplication(pkg) }.getOrNull()?.let { candidates += it to pkg }
        }
        runCatching { pm.getResourcesForApplication(source.packageName) }.getOrNull()?.let { candidates += it to source.packageName }
        val grids = listOf(5 to 5, 4 to 5, 4 to 4, 5 to 6, 6 to 5, 4 to 6, 6 to 6, 3 to 3, 3 to 4, 7 to 7)
        for ((res, pkg) in candidates) {
            val names = grids.map { (c, r) -> "default_workspace_${c}x$r" to (c to r) } +
                listOf("partner_default_layout" to null, "default_workspace" to null, "default_layout" to null)
            for ((name, grid) in names) {
                val id = res.getIdentifier(name, "xml", pkg)
                if (id == 0) continue
                val parsed = Parsed()
                runCatching { parseWorkspace(res, pkg, id, parsed) }.onFailure { Log.w(TAG, "Couldn't parse $pkg/$name", it) }.getOrNull() ?: continue
                if (parsed.pages.isEmpty() && parsed.dock.isEmpty()) continue
                val pages = (0..(parsed.pages.keys.maxOrNull() ?: 0)).map { HomePage(items = parsed.pages[it].orEmpty()) }
                val dock = parsed.dock.sortedBy { it.first }.mapIndexed { i, (_, item) -> item.withCell(Cell(i, 0)) }
                val all = pages.flatMap { it.items }
                val cols = grid?.first ?: all.maxOfOrNull { it.cell.x + it.cell.spanX }?.takeIf { it in 3..12 }
                val rows = grid?.second ?: all.maxOfOrNull { it.cell.y + it.cell.spanY }?.takeIf { it in 3..14 }
                return Imported(Origin.FACTORY_LAYOUT, source, pages, dock, cols, rows, parsed.skipped)
            }
        }
        return null
    }

    private fun parseWorkspace(res: Resources, pkg: String, xmlId: Int, out: Parsed, depth: Int = 0) {
        if (depth > 4) return
        res.getXml(xmlId).use { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.depth == 2) {
                    handleTag(res, pkg, parser, out, depth)
                }
                event = parser.next()
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun handleTag(res: Resources, pkg: String, p: XmlPullParser, out: Parsed, depth: Int) {
        val tag = p.name
        if (tag == "include") {
            attrRes(p, "workspace")?.let { parseWorkspace(res, pkg, it, out, depth + 1) }
            return
        }
        val container = attr(p, "container")?.toLongOrNull() ?: CONTAINER_DESKTOP
        val screen = attr(p, "screen")?.toIntOrNull() ?: 0
        val x = attr(p, "x")?.toIntOrNull() ?: 0
        val y = attr(p, "y")?.toIntOrNull() ?: 0
        val item: WorkspaceItem? = when (tag) {
            "favorite", "shortcut" -> keyForFavorite(p)?.let { AppItem(appKey = it, cell = Cell(x, y)) } ?: run { out.skipped++; null }
            "resolve" -> firstResolvable(p)?.let { AppItem(appKey = it, cell = Cell(x, y)) } ?: run { out.skipped++; null }
            "folder" -> {
                val title = attrString(res, p, "title") ?: "Folder"
                val children = folderChildren(p).distinct()
                when (children.size) {
                    0 -> null
                    1 -> AppItem(appKey = children.first(), cell = Cell(x, y))
                    else -> FolderItem(name = title, apps = children, cell = Cell(x, y))
                }
            }
            "appwidget" -> {
                val wPkg = attr(p, "packageName")
                val wCls = attr(p, "className")
                if (wPkg != null && wCls != null) WidgetItem(
                    appWidgetId = -1, provider = ComponentName(wPkg, wCls).flattenToShortString(),
                    cell = Cell(x, y, attr(p, "spanX")?.toIntOrNull() ?: 2, attr(p, "spanY")?.toIntOrNull() ?: 2),
                ) else null
            }
            else -> null
        }
        item ?: return
        if (container == CONTAINER_HOTSEAT) out.dock += screen to item
        else out.pages.getOrPut(screen) { mutableListOf() } += item
    }

    /** Reads `<favorite>` children of the current `<resolve>` and returns the first that is installed. */
    private fun firstResolvable(p: XmlPullParser): String? {
        val startDepth = p.depth
        var found: String? = null
        var event = p.next()
        while (!(event == XmlPullParser.END_TAG && p.depth == startDepth) && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && found == null && p.name == "favorite") found = keyForFavorite(p)
            event = p.next()
        }
        return found
    }

    private fun folderChildren(p: XmlPullParser): List<String> {
        val startDepth = p.depth
        val keys = mutableListOf<String>()
        var event = p.next()
        while (!(event == XmlPullParser.END_TAG && p.depth == startDepth) && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.depth == startDepth + 1) {
                when (p.name) {
                    "favorite", "shortcut" -> keyForFavorite(p)?.let { keys += it }
                    "resolve" -> firstResolvable(p)?.let { keys += it }
                }
            }
            event = p.next()
        }
        return keys
    }

    private fun keyForFavorite(p: XmlPullParser): String? {
        val pkg = attr(p, "packageName")
        val cls = attr(p, "className")
        if (pkg != null && cls != null) {
            val serial = myUserSerial()
            val exact = AppEntry.keyOf(ComponentName(pkg, if (cls.startsWith(".")) pkg + cls else cls), serial)
            apps.resolve(exact)?.let { return it.key }
            return mainActivityKey(pkg)
        }
        return attr(p, "uri")?.let { keyForIntentUri(it, myUserSerial()) }
    }

    /** Resolves an intent (as stored by launchers) to an installed, launchable app. */
    private fun keyForIntentUri(uri: String, userSerial: Long): String? {
        val intent = runCatching { Intent.parseUri(uri, 0) }.getOrNull() ?: return null
        intent.component?.let { cn ->
            apps.resolve(AppEntry.keyOf(cn, userSerial))?.let { return it.key }
            return mainActivityKey(cn.packageName, userSerial)
        }
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        if (pkg == "android") return null // chooser: no default app for this role
        return mainActivityKey(pkg, userSerial)
    }

    private fun mainActivityKey(pkg: String, userSerial: Long = myUserSerial()): String? =
        apps.apps.value.firstOrNull { it.packageName == pkg && it.userSerial == userSerial && it.builtIn == null }?.key
            ?: apps.apps.value.firstOrNull { it.packageName == pkg && it.builtIn == null }?.key

    private fun myUserSerial(): Long = context.getSystemService(UserManager::class.java).getSerialNumberForUser(android.os.Process.myUserHandle())

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) if (p.getAttributeName(i) == name) return p.getAttributeValue(i)
        return null
    }

    private fun attrRes(p: XmlPullParser, name: String): Int? = attr(p, name)?.removePrefix("@")?.toIntOrNull()

    private fun attrString(res: Resources, p: XmlPullParser, name: String): String? {
        val v = attr(p, name) ?: return null
        return if (v.startsWith("@")) v.removePrefix("@").toIntOrNull()?.let { runCatching { res.getString(it) }.getOrNull() } else v
    }

    private fun android.database.Cursor.getStringOr(i: Int): String? = if (i >= 0 && !isNull(i)) getString(i) else null
    private fun android.database.Cursor.getIntOr(i: Int, d: Int): Int = if (i >= 0 && !isNull(i)) getInt(i) else d
    private fun android.database.Cursor.getLongOr(i: Int, d: Long): Long = if (i >= 0 && !isNull(i)) getLong(i) else d

    companion object {
        private const val TAG = "LayoutImporter"
        const val CONTAINER_DESKTOP = -100L
        const val CONTAINER_HOTSEAT = -101L
        private const val TYPE_APP = 0
        private const val TYPE_SHORTCUT = 1
        private const val TYPE_FOLDER = 2
        private const val TYPE_WIDGET = 4
        private const val TYPE_CUSTOM_WIDGET = 5
        private const val TYPE_DEEP_SHORTCUT = 6
    }
}
