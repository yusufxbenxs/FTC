package com.flyer.launcher.launcher.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flyer.launcher.core.config.PressEffect
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.model.WorkspaceItem
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.widgets.FlyerWidgetHostView
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.BuiltInWidget
import com.flyer.launcher.launcher.render.FolderTile
import com.flyer.launcher.launcher.render.LiveWidget
import com.flyer.launcher.launcher.render.ShortcutTile
import com.flyer.launcher.launcher.ui.CoordsHolder
import com.flyer.launcher.launcher.ui.ItemGestureCallbacks
import com.flyer.launcher.launcher.ui.itemGestures

/**
 * An interactive launcher item: tap launches, long press opens its menu and arms drag & drop,
 * right click opens the menu, hover highlights (desktop). Visuals come from the shared renderers.
 */
@Composable
fun LiveItem(
    item: WorkspaceItem,
    surface: Surface,
    cellWidthPx: Float,
    cellHeightPx: Float,
    ui: LauncherUiState,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    iconSizeOverride: Dp? = null,
    labelColor: Color? = null,
) {
    val config = LocalFlyerConfig.current
    val actions = LocalLauncherActions.current
    val view = LocalView.current
    val holder = remember { CoordsHolder() }
    var pressed by remember { mutableStateOf(false) }
    val itemState = rememberUpdatedState(item)
    val configState = rememberUpdatedState(config)
    val actionsState = rememberUpdatedState(actions)

    fun openMenuAndArm(rootPos: Offset, armDrag: Boolean) {
        val bounds = holder.boundsInRoot()
        val current = itemState.value
        val cfg = configState.value
        if (cfg.gestures.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        ui.menu = ItemMenus.forItem(ui, actionsState.value, current, surface, bounds)
        if (armDrag && !cfg.home.lockLayout) {
            ui.drag.prepare(Placement.Existing(current.id), current, surface, bounds, rootPos)
        }
    }
    val setPressed: (Boolean) -> Unit = { pressed = it }

    val callbacks = remember(surface) {
        object : ItemGestureCallbacks {
            override fun onTap(rootPos: Offset) {
                val bounds = holder.boundsInRoot()
                val a = actionsState.value
                when (val it = itemState.value) {
                    is AppItem -> a.launchApp(it.appKey, bounds)
                    is FolderItem -> a.openFolder(it.id, bounds)
                    is ShortcutItem -> a.launchShortcut(it, bounds)
                    else -> Unit
                }
            }

            override fun onLongPress(rootPos: Offset) = openMenuAndArm(rootPos, armDrag = true)
            override fun onSecondaryClick(rootPos: Offset) = openMenuAndArm(rootPos, armDrag = false)
            override fun onPressChanged(pressed: Boolean) = setPressed(pressed)
        }
    }

    val anim = config.animations
    val target = if (pressed && config.icons.pressEffect == PressEffect.SCALE) anim.pressScale else 1f
    val scale by animateFloatAsState(target, label = "press")
    val dim = if (pressed && config.icons.pressEffect == PressEffect.DIM) 0.6f else 1f

    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val hoverOn = ui.desktopActive && config.desktop.hoverEffects

    val base = modifier
        .let { if (hoverOn) it.hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand) else it }
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = dim }

    val doubleClick = surface == Surface.Desktop && config.desktop.doubleClickToOpen

    when (item) {
        is WidgetItem -> {
            val density = LocalDensity.current
            val w = with(density) { (cellWidthPx * item.cell.spanX).toDp() }
            val h = with(density) { (cellHeightPx * item.cell.spanY).toDp() }
            val listener = remember(item.id) {
                object : FlyerWidgetHostView.Listener {
                    override fun onLongPress(view: FlyerWidgetHostView, rawX: Float, rawY: Float) {
                        val loc = IntArray(2)
                        view.rootView.getLocationOnScreen(loc)
                        openMenuAndArm(Offset(rawX - loc[0], rawY - loc[1]), armDrag = true)
                    }

                    // Movement after the long press is tracked by the root drag monitor.
                    override fun onDragMove(rawX: Float, rawY: Float) = Unit
                    override fun onDragEnd(rawX: Float, rawY: Float, cancelled: Boolean) = Unit
                }
            }
            Box(base.fillMaxSize().itemGestures(holder, ui.arbiter, enabled = true, callbacks = callbacks)) {
                LiveWidget(item, w, h, listener, onRestore = { ui.restoreWidget?.invoke(item) })
            }
        }
        is BuiltInWidgetItem -> Box(base.fillMaxSize().itemGestures(holder, ui.arbiter, callbacks = callbacks)) {
            BuiltInWidget(item)
        }
        else -> {
            val showLabel = showLabels && config.icons.label.show
            val iconSize = iconSizeOverride ?: fittedIconSize(config, cellWidthPx * item.cell.spanX, cellHeightPx * item.cell.spanY, showLabel)
            Box(
                base.fillMaxSize()
                    .let { if (hoverOn && hovered) it.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)) else it }
                    .itemGestures(holder, ui.arbiter, doubleClickToOpen = doubleClick, callbacks = callbacks),
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is AppItem -> AppTile(item.appKey, iconSize, showLabels, config.icons.label, style = item.style, labelColor = labelColor)
                    is FolderItem -> FolderTile(item, iconSize, showLabels, config.icons.label, labelColor = labelColor)
                    is ShortcutItem -> ShortcutTile(item, iconSize, showLabels, config.icons.label)
                    else -> Unit
                }
            }
        }
    }
}
