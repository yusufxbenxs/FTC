package com.flyer.launcher.launcher.render

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.model.AppOverride

/** How launcher surfaces are being rendered. */
enum class RenderMode {
    /** The real, interactive home screen. */
    LIVE,

    /** Inside Flyer: non-interactive preview (widgets are drawn as placeholders). */
    PREVIEW,

    /** Inside Flyer's live editor: selectable and draggable elements. */
    EDITOR,
}

@Immutable
class AppIndex(val byKey: Map<String, AppEntry>) {
    operator fun get(key: String): AppEntry? = byKey[key]

    companion object {
        val Empty = AppIndex(emptyMap())
    }
}

val LocalLauncherCore = staticCompositionLocalOf<LauncherCore> { error("LauncherCore not provided") }
val LocalAppIndex = compositionLocalOf { AppIndex.Empty }
val LocalAppOverrides = compositionLocalOf<Map<String, AppOverride>> { emptyMap() }
val LocalRenderMode = staticCompositionLocalOf { RenderMode.LIVE }

/** Ids of items currently being dragged (rendered dimmed at their origin). */
val LocalDraggingIds = compositionLocalOf<Set<String>> { emptySet() }

fun labelFor(key: String, index: AppIndex, overrides: Map<String, AppOverride>): String =
    overrides[key]?.label?.takeIf { it.isNotBlank() } ?: index[key]?.label ?: "…"
