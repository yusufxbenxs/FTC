package com.flyer.launcher.core.apps

import com.flyer.launcher.core.config.DrawerSort
import java.text.Normalizer

/** Sorting and matching rules for app lists (drawer, search, pickers). */
object AppSorting {

    fun sort(apps: List<AppEntry>, sort: DrawerSort, launchCounts: Map<String, Long>, labelOf: (AppEntry) -> String): List<AppEntry> = when (sort) {
        DrawerSort.NAME_ASC -> apps.sortedBy { normalize(labelOf(it)) }
        DrawerSort.NAME_DESC -> apps.sortedByDescending { normalize(labelOf(it)) }
        DrawerSort.MOST_USED -> apps.sortedWith(compareByDescending<AppEntry> { launchCounts[it.key] ?: 0L }.thenBy { normalize(labelOf(it)) })
        DrawerSort.RECENTLY_INSTALLED -> apps.sortedByDescending { it.installTime }
        DrawerSort.RECENTLY_UPDATED -> apps.sortedByDescending { it.updateTime }
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /**
     * Relevance of [text] for [query] (0 = no match). Prefix > word start > substring > (fuzzy)
     * in-order subsequence, so "gm" finds "Gmail" and "Google Maps".
     */
    fun score(text: String, query: String, fuzzy: Boolean): Int {
        if (query.isBlank()) return 1
        val t = normalize(text)
        val q = normalize(query.trim())
        if (t == q) return 1000
        if (t.startsWith(q)) return 800 - t.length
        val words = t.split(' ', '-', '_', '.')
        if (words.any { it.startsWith(q) }) return 600 - t.length
        val initials = words.filter { it.isNotEmpty() }.joinToString("") { it.first().toString() }
        if (initials.startsWith(q)) return 500
        val idx = t.indexOf(q)
        if (idx >= 0) return 400 - idx
        if (!fuzzy) return 0
        var ti = 0
        var gaps = 0
        for (c in q) {
            val found = t.indexOf(c, ti)
            if (found < 0) return 0
            gaps += found - ti
            ti = found + 1
        }
        return (200 - gaps * 4).coerceAtLeast(1)
    }

    /** First letter bucket used by the drawer's fast scroller. */
    fun indexLetter(label: String): Char {
        val c = normalize(label).firstOrNull() ?: return '#'
        return if (c.isLetter()) c.uppercaseChar() else '#'
    }
}
