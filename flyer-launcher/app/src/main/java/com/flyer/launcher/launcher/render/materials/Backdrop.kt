package com.flyer.launcher.launcher.render.materials

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent

/**
 * A recorded picture of part of the launcher (the background, or the whole home screen) that
 * glass-like materials draw, blur and refract behind themselves.
 *
 * Android does not let apps read the system wallpaper, so a backdrop only contains what the
 * launcher itself draws (launcher backgrounds, icons, widgets).
 */
@Stable
class BackdropLayer(val layer: GraphicsLayer) {
    internal var coords: LayoutCoordinates? = null
    internal var recorded = false
}

@Immutable
data class Backdrops(
    /** Launcher-drawn background only (safe for surfaces that are part of the home screen, like the dock). */
    val background: BackdropLayer? = null,
    /** Background + home screen (for overlays above it: folders, menus, drawer, lock). */
    val home: BackdropLayer? = null,
)

val LocalBackdrops = compositionLocalOf { Backdrops() }

/** Records this element's drawing into [backdrop] while still drawing it normally. */
fun Modifier.recordBackdrop(backdrop: BackdropLayer?): Modifier =
    if (backdrop == null) this else this
        .onGloballyPositioned { backdrop.coords = it }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            backdrop.recorded = true
            drawLayer(backdrop.layer)
        }

/** Draws [backdrop] aligned so that it lines up with what is really behind [self]. */
fun DrawScope.drawBackdrop(backdrop: BackdropLayer, self: LayoutCoordinates?) {
    val host = backdrop.coords ?: return
    val me = self ?: return
    if (!backdrop.recorded || !host.isAttached || !me.isAttached) return
    val offset: Offset = runCatching { host.localPositionOf(me, Offset.Zero) }.getOrNull() ?: return
    translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
}
