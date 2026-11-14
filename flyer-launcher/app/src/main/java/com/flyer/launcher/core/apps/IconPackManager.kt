package com.flyer.launcher.core.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Support for third-party icon packs using the widely adopted ADW / Nova "appfilter" format:
 * an installed package declares one of the theme intent actions and ships an appfilter.xml that
 * maps ComponentInfo{package/class} to drawable names.
 */
class IconPackManager(private val context: Context) {

    data class IconPackInfo(val packageName: String, val label: String)

    class LoadedPack(
        val packageName: String,
        private val res: Resources,
        private val map: Map<String, String>,
        val backs: List<String>,
        val mask: String?,
        val upon: String?,
        val scale: Float,
        private val declaredDrawables: List<String>,
    ) {
        private val idCache = ConcurrentHashMap<String, Int>()

        @SuppressLint("DiscouragedApi")
        private fun idOf(name: String): Int = idCache.getOrPut(name) {
            res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
                ?: res.getIdentifier(name, "mipmap", packageName)
        }

        fun drawable(name: String, density: Int): Drawable? {
            val id = idOf(name)
            if (id == 0) return null
            return runCatching { res.getDrawableForDensity(id, density, null) }.getOrNull()
        }

        fun drawableNameFor(component: ComponentName): String? =
            map[component.flattenToString()] ?: map[component.packageName + "/" + component.className]

        fun hasMapping(component: ComponentName) = drawableNameFor(component) != null

        /** Every icon the pack offers, for the manual icon picker. */
        fun allDrawables(): List<String> = (declaredDrawables.ifEmpty { map.values.toList() }).distinct().sorted()
    }

    private val loaded = ConcurrentHashMap<String, LoadedPack>()

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
        return actions.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            IconPackInfo(pkg, ri.loadLabel(pm).toString())
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun invalidate(packageName: String) {
        loaded.remove(packageName)
    }

    /** Loads and caches a pack. Must be called off the main thread. */
    fun load(packageName: String): LoadedPack? {
        loaded[packageName]?.let { return it }
        return try {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val map = HashMap<String, String>(512)
            val backs = mutableListOf<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            openXml(res, packageName, "appfilter")?.let { parser ->
                parser.use { p ->
                    var event = p.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG) {
                            when (p.name) {
                                "item" -> {
                                    val comp = p.getAttributeValue(null, "component")
                                    val drawable = p.getAttributeValue(null, "drawable")
                                    if (comp != null && drawable != null) normalize(comp)?.let { map[it] = drawable }
                                }
                                "iconback" -> for (i in 0 until p.attributeCount) backs += p.getAttributeValue(i)
                                "iconmask" -> mask = p.getAttributeValue(null, "img1")
                                "iconupon" -> upon = p.getAttributeValue(null, "img1")
                                "scale" -> scale = p.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                            }
                        }
                        event = p.next()
                    }
                }
            }
            val declared = mutableListOf<String>()
            openXml(res, packageName, "drawable")?.use { p ->
                var event = p.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && p.name == "item") {
                        p.getAttributeValue(null, "drawable")?.let { declared += it }
                    }
                    event = p.next()
                }
            }
            LoadedPack(packageName, res, map, backs, mask, upon, scale.coerceIn(0.2f, 1.5f), declared)
                .also { loaded[packageName] = it }
        } catch (e: Exception) {
            Log.w("IconPack", "Could not load icon pack $packageName", e)
            null
        }
    }

    private class Parser(val parser: XmlPullParser, private val close: () -> Unit) {
        fun <R> use(block: (XmlPullParser) -> R): R = try { block(parser) } finally { close() }
    }

    @SuppressLint("DiscouragedApi")
    private fun openXml(res: Resources, pkg: String, name: String): Parser? {
        val id = res.getIdentifier(name, "xml", pkg)
        if (id != 0) {
            val xml = res.getXml(id)
            return Parser(xml) { xml.close() }
        }
        return runCatching {
            val stream = res.assets.open("$name.xml")
            val parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
            Parser(parser) { stream.close() }
        }.getOrNull()
    }

    private fun normalize(component: String): String? {
        val inner = component.substringAfter("ComponentInfo{", "").substringBefore("}")
        if (inner.isEmpty() || !inner.contains('/')) return null
        val pkg = inner.substringBefore('/')
        var cls = inner.substringAfter('/')
        if (cls.startsWith(".")) cls = pkg + cls
        return "$pkg/$cls"
    }
}
