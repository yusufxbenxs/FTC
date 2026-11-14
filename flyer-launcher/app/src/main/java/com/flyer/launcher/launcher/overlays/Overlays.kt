package com.flyer.launcher.launcher.overlays

import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.MaterialSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.BuiltInWidgetItem
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.withOpacity
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.MenuEntry
import com.flyer.launcher.launcher.MenuState
import com.flyer.launcher.launcher.drag.SpecialDrop
import com.flyer.launcher.launcher.home.ActionDropZone
import com.flyer.launcher.launcher.home.StaticItemVisual
import com.flyer.launcher.launcher.ui.clickableNoRipple
import kotlin.math.roundToInt

/** Context menu positioned next to its anchor, flipping above it when there is no room below. */
@Composable
fun MenuOverlay(menu: MenuState, onDismiss: () -> Unit) {
    val config = LocalFlyerConfig.current
    val bg = config.appearance.menuBackground.withOpacity(config.appearance.menuOpacity)
    val fg = if (config.appearance.menuBackground.color.luminance() > 0.5f) Color(0xFF15151F) else Color.White
    val radius = (18 * config.appearance.cornerScale).dp
    Box(Modifier.fillMaxSize().clickableNoRipple(onDismiss)) {
        Layout(
            content = {
                MaterialSurface(
                    tint = bg,
                    cornerRadius = radius,
                    modifier = Modifier.widthIn(min = 200.dp, max = 300.dp).shadow(12.dp, RoundedCornerShape(radius)),
                    enabled = config.style.applyToMenus,
                    backdrop = LocalBackdrops.current.home,
                ) {
                Column(
                    Modifier.clickableNoRipple { }.padding(vertical = 6.dp),
                ) {
                    menu.title?.let {
                        Text(it, color = fg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), maxLines = 1)
                    }
                    if (menu.shortcuts.isNotEmpty()) {
                        menu.shortcuts.forEach { MenuRow(it, fg) }
                        HorizontalDivider(color = fg.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    }
                    menu.entries.forEach { MenuRow(it, fg) }
                }
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val a = menu.anchor
            val margin = 8.dp.roundToPx()
            val belowY = a.bottom.roundToInt() + margin
            val aboveY = a.top.roundToInt() - margin - placeable.height
            val y = when {
                belowY + placeable.height <= constraints.maxHeight - margin -> belowY
                aboveY >= margin -> aboveY
                else -> (constraints.maxHeight - placeable.height) / 2
            }
            val x = (a.center.x.roundToInt() - placeable.width / 2).coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, fg: Color) {
    val color = when {
        !entry.enabled -> fg.copy(alpha = 0.35f)
        entry.destructive -> Color(0xFFFF6B6B)
        else -> fg
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = entry.enabled, onClick = entry.onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)) }
        Text(entry.label, color = color, fontSize = 15.sp, modifier = Modifier.weight(1f))
        entry.shortcutHint?.let { Text(it, color = fg.copy(alpha = 0.5f), fontSize = 12.sp) }
    }
}

/**
 * Drag visual + the action targets (remove / app info / uninstall / hide) at the top of the screen.
 */
@Composable
fun DragLayer(ui: LauncherUiState) {
    val drag = ui.drag
    val session = drag.session ?: return
    if (!session.started) return
    val density = LocalDensity.current
    val visual = session.visual
    val isApp = visual is AppItem
    val entry = (visual as? AppItem)?.let { ui.core.resolveApp(it.appKey) }
    val fromSurface = session.source != null
    val actions = buildList {
        val protected = entry?.builtIn != null
        if ((fromSurface || session.placement is com.flyer.launcher.core.workspace.Placement.FromFolder) && !protected) add(SpecialDrop.REMOVE)
        if (isApp && entry?.builtIn == null) add(SpecialDrop.APP_INFO)
        if (isApp && !fromSurface && entry?.builtIn == null) add(SpecialDrop.HIDE)
        if (entry != null && ui.core.appsEngine.canUninstall(entry)) add(SpecialDrop.UNINSTALL)
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        Modifier.fillMaxWidth().padding(top = top + 8.dp, start = 12.dp, end = 12.dp).height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEach { action ->
            ActionTarget(ui, action, Modifier.weight(1f), highlighted = drag.candidate?.special == action)
        }
    }

    val topLeft = drag.visualTopLeft()
    val w = with(density) { session.sizePx.width.toDp() }
    val h = with(density) { session.sizePx.height.toDp() }
    Box(
        Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(w, h)
            .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = if (drag.candidate?.special != null) 0.6f else 0.95f },
        contentAlignment = Alignment.Center,
    ) {
        when (visual) {
            is WidgetItem, is BuiltInWidgetItem -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.25f)))
            else -> StaticItemVisual(visual, session.sizePx.width, session.sizePx.height, showLabels = false)
        }
    }
}

@Composable
private fun ActionTarget(ui: LauncherUiState, action: SpecialDrop, modifier: Modifier, highlighted: Boolean) {
    val zone = remember(action) { ActionDropZone(action) }
    val id = "action-${action.name}"
    DisposableEffect(id) {
        ui.drag.register(id, zone)
        onDispose { ui.drag.unregister(id) }
    }
    val (label, icon) = when (action) {
        SpecialDrop.REMOVE -> "Remove" to Icons.Outlined.Delete
        SpecialDrop.APP_INFO -> "App info" to Icons.Outlined.Info
        SpecialDrop.UNINSTALL -> "Uninstall" to Icons.Outlined.Delete
        SpecialDrop.HIDE -> "Hide" to Icons.Outlined.VisibilityOff
        SpecialDrop.PIN_TASKBAR -> "Pin" to Icons.Outlined.Info
    }
    ActionChip(label, icon, highlighted, action == SpecialDrop.UNINSTALL, modifier.onGloballyPositioned { zone.rootBounds = it.boundsInRoot() })
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, highlighted: Boolean, destructive: Boolean, modifier: Modifier) {
    val base = if (destructive) Color(0xFFE5484D) else Color(0xFF2A2A3A)
    Row(
        modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(if (highlighted) base else base.copy(alpha = 0.75f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun RenameFolderDialog(initial: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename folder") },
        text = { OutlinedTextField(text, { text = it.take(40) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onRename(text.trim().ifEmpty { "Folder" }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Thin bottom message with an optional action (used for "Removed · Undo"). */
@Composable
fun LauncherSnackbar(text: String, actionLabel: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier) {
    val config = LocalFlyerConfig.current
    val light = config.appearance.menuBackground.color.luminance() > 0.5f
    val fg = if (light) Color(0xFF15151F) else Color.White
    val accent = config.appearance.accent.color
    // Keep the action readable when the accent is too close to the snackbar colour.
    val action = if (light == (accent.luminance() > 0.45f)) fg else accent
    Row(
        modifier.padding(16.dp).clip(RoundedCornerShape(14.dp)).background(config.appearance.menuBackground.withOpacity(0.96f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(16.dp))
            Text(actionLabel, color = action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}
