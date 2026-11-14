package com.flyer.launcher.flyer.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.SurfaceMaterial
import com.flyer.launcher.core.config.UnlockMethod
import com.flyer.launcher.core.gestures.GestureHelperBridge
import com.flyer.launcher.core.lock.CredentialHasher
import com.flyer.launcher.core.lock.CredentialType
import com.flyer.launcher.core.lock.VerifyResult
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.ThemePreset
import com.flyer.launcher.core.theme.ThemePresets
import com.flyer.launcher.flyer.FlyerSession
import com.flyer.launcher.flyer.LocalFlyerPlatform
import com.flyer.launcher.flyer.components.AppPickerDialog
import com.flyer.launcher.flyer.components.FlyerCard
import com.flyer.launcher.flyer.components.InfoRow
import com.flyer.launcher.flyer.components.SectionHeader
import com.flyer.launcher.launcher.home.HomeBackground
import com.flyer.launcher.launcher.render.IconImage
import com.flyer.launcher.launcher.render.materials.BackdropLayer
import com.flyer.launcher.launcher.render.materials.Backdrops
import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.MaterialSurface
import com.flyer.launcher.launcher.render.materials.recordBackdrop
import com.flyer.launcher.launcher.render.rememberAppIcon
import kotlinx.coroutines.launch

private val materialNames = listOf(
    SurfaceMaterial.FLAT to "Flat", SurfaceMaterial.LIQUID_GLASS to "Liquid glass", SurfaceMaterial.FROSTED to "Frosted",
    SurfaceMaterial.HOLOGRAPHIC to "Holographic", SurfaceMaterial.NEUMORPHIC to "Soft UI", SurfaceMaterial.CLAY to "Clay",
    SurfaceMaterial.NEON to "Neon", SurfaceMaterial.OUTLINE to "Outline",
)

/**
 * A small live scene: a launcher background with a material "dock" on top, rendered with the real
 * material engine (including real backdrop blur/refraction) for [config].
 */
@Composable
private fun MaterialScene(config: FlyerConfig, modifier: Modifier = Modifier, showIcons: Boolean = true) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { BackdropLayer(layer) }
    CompositionLocalProvider(LocalFlyerConfig provides config, LocalBackdrops provides Backdrops(background = backdrop, home = backdrop)) {
        Box(modifier.clip(RoundedCornerShape(16.dp))) {
            val bg = config.home.background
            val sceneBg = if (bg.type == BackgroundType.SYSTEM_WALLPAPER) bg.copy(type = BackgroundType.MESH) else bg
            HomeBackground(sceneBg, Modifier.recordBackdrop(backdrop))
            Text(
                "12:30", color = config.widgets.clock.color.color, fontSize = 22.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
            MaterialSurface(
                tint = config.dock.background.color.copy(alpha = config.dock.background.alpha * config.dock.opacity),
                cornerRadius = 18.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(46.dp),
                backdrop = backdrop,
            ) {
                if (showIcons) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf(0xFFFF7A59, 0xFF4FC3F7, 0xFF8BC34A, 0xFFFFCA28).forEach { c ->
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(c)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialGallery(session: FlyerSession) {
    val config = session.draft.config
    SectionHeader("Pick a material")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        materialNames.forEach { (m, name) ->
            val selected = config.style.material == m
            val sample = config.copy(style = config.style.copy(material = m))
            Column(
                Modifier.width(150.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable { session.updateConfig("Material: $name", null) { it.copy(style = it.style.copy(material = m)) } }
                    .padding(8.dp),
            ) {
                MaterialScene(sample, Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(6.dp))
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
    Text(
        "Glass materials blur and bend what the launcher draws behind them (refraction needs Android 13+, blur Android 12+). " +
            "Android never lets apps read the system wallpaper, so for the full effect pick a launcher background such as Mesh or Aurora in Home Screen › Background.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** Live mini renders of the advanced themes (background + material + accent). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedThemesGallery(session: FlyerSession) {
    SectionHeader("Advanced themes")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePresets.advanced.forEach { preset -> AdvancedThemeCard(session, preset) }
    }
    Text(
        "Advanced themes set the material, effects and a launcher-drawn background, then restyle icons, dock, folders and type to match. Your pages, gestures and behaviour settings are untouched.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun AdvancedThemeCard(session: FlyerSession, preset: ThemePreset) {
    val themed = remember(preset.id) { preset.apply(FlyerConfig()) }
    Column(
        Modifier.width(160.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { session.updateConfig("Theme: ${preset.name}", null) { preset.apply(it) } }
            .padding(8.dp),
    ) {
        MaterialScene(themed, Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(6.dp))
        Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────────── Lock ─────────────────────────

private enum class CredentialAction { SET_PIN, SET_PASSWORD, REMOVE }

@Composable
fun LockSection(session: FlyerSession, core: LauncherCore, apps: List<AppEntry>) {
    val platform = LocalFlyerPlatform.current
    val scope = rememberCoroutineScope()
    val state by core.lock.state.collectAsStateWithLifecycle()
    val config = session.draft.config
    val lock = config.lock
    var action by remember { mutableStateOf<CredentialAction?>(null) }
    var pickApps by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val adminActive = remember(refresh) { core.lock.isAdminActive() }
    val deviceSecure = remember(refresh) { core.lock.isDeviceSecure() }

    SectionHeader("Lock")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow("Lock the home screen now", "The home screen stays visible but can't be used until you unlock it.") {
            Button(onClick = { platform.lockLauncher() }) { Icon(Icons.Outlined.Lock, null); Spacer(Modifier.width(6.dp)); Text("Lock") }
        }
        val helper = GestureHelperBridge.performGlobalAction != null
        InfoRow(
            when {
                helper -> "Phone lock: using the gesture helper"
                adminActive -> "Phone lock: allowed"
                else -> "Phone lock: not allowed yet"
            },
            "Holding the lock button turns the screen off and locks the phone. Android needs your permission once (device admin, \"lock screen\" only). " +
                if (adminActive) "To uninstall Flyer Launcher later, turn this off first." else "",
        ) {
            if (adminActive) OutlinedButton(onClick = { core.lock.removeAdmin(); refresh++ }) { Text("Turn off") }
            else if (!helper) Button(onClick = { platform.requestDeviceAdmin(); refresh++ }) { Text("Allow") }
        }
    }

    SectionHeader("Launcher PIN / password")
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        InfoRow(
            when (state.credential) {
                CredentialType.NONE -> "No launcher PIN or password"
                CredentialType.PIN -> "Launcher PIN is set"
                CredentialType.PASSWORD -> "Launcher password is set"
            },
            "Only for this launcher: separate from your phone's lock and never included in backups.",
        )
        Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { action = CredentialAction.SET_PIN }) { Text(if (state.credential == CredentialType.PIN) "Change PIN" else "Set PIN") }
            OutlinedButton(onClick = { action = CredentialAction.SET_PASSWORD }) { Text(if (state.credential == CredentialType.PASSWORD) "Change password" else "Set password") }
            if (state.credential != CredentialType.NONE) TextButton(onClick = { action = CredentialAction.REMOVE }) { Text("Remove") }
        }
        val warning = when {
            lock.unlockMethod == UnlockMethod.LAUNCHER_PIN && state.credential != CredentialType.PIN ->
                "\"Unlock with launcher PIN\" is selected but no PIN is set, so your phone's screen lock is used until you set one."
            lock.unlockMethod == UnlockMethod.LAUNCHER_PASSWORD && state.credential != CredentialType.PASSWORD ->
                "\"Unlock with launcher password\" is selected but no password is set, so your phone's screen lock is used until you set one."
            lock.unlockMethod == UnlockMethod.PHONE && !deviceSecure ->
                "Your phone has no screen lock, so \"Phone's screen lock\" can't protect anything. Set a launcher PIN or a phone screen lock."
            lock.unlockMethod == UnlockMethod.TAP -> "\"Just tap\" hides the home screen but anyone can unlock it."
            else -> null
        }
        if (warning != null) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(warning, style = MaterialTheme.typography.bodySmall)
        }
    }

    SectionHeader("Protected apps (${lock.protectedApps.size})") {
        TextButton(onClick = { pickApps = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Choose") }
    }
    FlyerCard(Modifier.padding(horizontal = 16.dp)) {
        if (lock.protectedApps.isEmpty()) {
            InfoRow("No protected apps", "Protected apps ask for the launcher unlock when opened from this launcher (not from notifications or other apps).")
        }
        lock.protectedApps.mapNotNull { k -> apps.firstOrNull { it.key == k } }.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconImage(rememberAppIcon(app.key, 32.dp), 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(app.label, modifier = Modifier.weight(1f))
                IconButton(onClick = { session.updateConfig("Unprotect ${app.label}", null) { c -> c.copy(lock = c.lock.copy(protectedApps = c.lock.protectedApps - app.key)) } }) {
                    Icon(Icons.Outlined.Delete, "Remove")
                }
            }
        }
    }

    if (pickApps) AppPickerDialog("Protected apps", apps.filter { it.builtIn == null }, onDismiss = { pickApps = false }, multiSelect = lock.protectedApps) { app ->
        session.updateConfig("Protect ${app.label}", null) { c ->
            val set = c.lock.protectedApps
            c.copy(lock = c.lock.copy(protectedApps = if (app.key in set) set - app.key else set + app.key))
        }
    }

    action?.let { a ->
        CredentialDialog(
            action = a,
            hasCurrent = state.credential != CredentialType.NONE,
            currentIsPin = state.credential == CredentialType.PIN,
            onDismiss = { action = null },
        ) { current, new, onError ->
            scope.launch {
                if (state.credential != CredentialType.NONE) {
                    when (val r = core.lock.verify(current, lock.maxAttempts)) {
                        VerifyResult.Ok -> Unit
                        is VerifyResult.Wrong -> { onError("Current ${if (state.credential == CredentialType.PIN) "PIN" else "password"} is wrong (${r.attemptsLeft} tries left)"); return@launch }
                        is VerifyResult.LockedOut -> { onError("Too many tries, wait a moment"); return@launch }
                    }
                }
                when (a) {
                    CredentialAction.SET_PIN -> {
                        core.lock.setCredential(CredentialType.PIN, new)
                        session.updateConfig("Unlock with launcher PIN", null) { it.copy(lock = it.lock.copy(unlockMethod = UnlockMethod.LAUNCHER_PIN)) }
                    }
                    CredentialAction.SET_PASSWORD -> {
                        core.lock.setCredential(CredentialType.PASSWORD, new)
                        session.updateConfig("Unlock with launcher password", null) { it.copy(lock = it.lock.copy(unlockMethod = UnlockMethod.LAUNCHER_PASSWORD)) }
                    }
                    CredentialAction.REMOVE -> core.lock.clearCredential()
                }
                action = null
            }
        }
    }
}

@Composable
private fun CredentialDialog(
    action: CredentialAction,
    hasCurrent: Boolean,
    currentIsPin: Boolean,
    onDismiss: () -> Unit,
    onSave: (current: String, new: String, onError: (String) -> Unit) -> Unit,
) {
    val pin = action == CredentialAction.SET_PIN
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    val pwKeyboard = KeyboardOptions(keyboardType = KeyboardType.Password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (action) {
                    CredentialAction.SET_PIN -> "Launcher PIN"
                    CredentialAction.SET_PASSWORD -> "Launcher password"
                    CredentialAction.REMOVE -> "Remove launcher ${if (currentIsPin) "PIN" else "password"}"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasCurrent) OutlinedTextField(
                    current, { current = it }, singleLine = true, label = { Text("Current ${if (currentIsPin) "PIN" else "password"}") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = if (currentIsPin) pinKeyboard else pwKeyboard,
                )
                if (action != CredentialAction.REMOVE) {
                    OutlinedTextField(
                        new, { new = it }, singleLine = true, label = { Text(if (pin) "New PIN" else "New password") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = if (pin) pinKeyboard else pwKeyboard,
                    )
                    OutlinedTextField(
                        confirm, { confirm = it }, singleLine = true, label = { Text("Repeat") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = if (pin) pinKeyboard else pwKeyboard,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    hasCurrent && current.isEmpty() -> "Enter the current one first"
                    action == CredentialAction.REMOVE -> null
                    CredentialHasher.validate(new, pin) != null -> CredentialHasher.validate(new, pin)
                    new != confirm -> "They don't match"
                    else -> null
                }
                if (error == null) onSave(current, new) { error = it }
            }) { Text(if (action == CredentialAction.REMOVE) "Remove" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
