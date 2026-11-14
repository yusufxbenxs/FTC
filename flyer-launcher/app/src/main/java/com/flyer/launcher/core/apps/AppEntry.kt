package com.flyer.launcher.core.apps

import android.content.ComponentName
import android.os.UserHandle
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.flyer.launcher.R

/** Launcher-owned applications. They are entries of the app list like any other app, but launch internally. */
enum class BuiltInApp(val key: String, val label: String, @DrawableRes val icon: Int, val description: String) {
    FLYER(
        "builtin:flyer", "Flyer", R.drawable.ic_flyer,
        "The launcher's customization environment",
    ),
    LAYOUT_EDITOR(
        "builtin:editor", "Layout Editor", R.drawable.ic_builtin_editor,
        "Opens Flyer's live home screen editor directly",
    ),
    WIDGETS(
        "builtin:widgets", "Widgets", R.drawable.ic_builtin_widgets,
        "Browse and place native widgets",
    );

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

@Immutable
data class AppEntry(
    /** Stable identity: "package/class@userSerial" or a [BuiltInApp.key]. */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName?,
    val user: UserHandle?,
    val userSerial: Long,
    val isWorkProfile: Boolean,
    val builtIn: BuiltInApp?,
    val installTime: Long,
    val updateTime: Long,
    /** android.content.pm.ApplicationInfo.category (API 26), or -1. */
    val category: Int,
    val isSystemApp: Boolean,
) {
    val sortLabel: String = label.lowercase()

    companion object {
        fun keyOf(component: ComponentName, userSerial: Long) = "${component.flattenToShortString()}@$userSerial"

        fun parseKey(key: String): Pair<ComponentName, Long>? {
            if (key.startsWith("builtin:")) return null
            val at = key.lastIndexOf('@')
            val comp = if (at > 0) key.substring(0, at) else key
            val serial = if (at > 0) key.substring(at + 1).toLongOrNull() ?: 0L else 0L
            return ComponentName.unflattenFromString(comp)?.let { it to serial }
        }

        fun packageOf(key: String): String? = parseKey(key)?.first?.packageName
    }
}

/** Drawer categories derived from the platform's app category metadata. */
enum class AppCategory(val id: Int, val title: String) {
    GAME(0, "Games"), AUDIO(1, "Audio"), VIDEO(2, "Video"), IMAGE(3, "Photos"),
    SOCIAL(4, "Social"), NEWS(5, "News"), MAPS(6, "Maps"), PRODUCTIVITY(7, "Productivity"),
    ACCESSIBILITY(8, "Accessibility");

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id }
    }
}
