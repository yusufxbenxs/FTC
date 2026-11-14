package com.flyer.launcher.launcher.lock

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flyer.launcher.core.config.LockConfig
import com.flyer.launcher.core.config.LockCorner
import com.flyer.launcher.core.config.LockIcon
import com.flyer.launcher.core.config.UnlockMethod
import com.flyer.launcher.core.lock.CredentialType
import com.flyer.launcher.core.lock.LockState
import com.flyer.launcher.core.lock.VerifyResult
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.LocalLauncherFont
import com.flyer.launcher.launcher.LauncherUiState
import com.flyer.launcher.launcher.render.materials.LocalBackdrops
import com.flyer.launcher.launcher.render.materials.MaterialSurface
import com.flyer.launcher.launcher.ui.clickableNoRipple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun LockIcon.vector(): ImageVector = when (this) {
    LockIcon.PADLOCK -> Icons.Outlined.Lock
    LockIcon.SHIELD -> Icons.Outlined.Shield
    LockIcon.MOON -> Icons.Outlined.DarkMode
    LockIcon.KEY -> Icons.Outlined.Key
}

/** What actually protects the launcher right now (falls back when no launcher credential is set). */
fun effectiveMethod(lock: LockConfig, state: LockState): UnlockMethod = when (lock.unlockMethod) {
    UnlockMethod.LAUNCHER_PIN -> if (state.credential == CredentialType.PIN) UnlockMethod.LAUNCHER_PIN else UnlockMethod.PHONE
    UnlockMethod.LAUNCHER_PASSWORD -> if (state.credential == CredentialType.PASSWORD) UnlockMethod.LAUNCHER_PASSWORD else UnlockMethod.PHONE
    else -> lock.unlockMethod
}

/**
 * Corner lock button. Tap: lock the home screen. Hold (with a filling ring): lock the phone.
 */
@Composable
fun BoxScope.LockButton(ui: LauncherUiState, lock: LockConfig) {
    val view = LocalView.current
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val lockState = rememberUpdatedState(lock)
    val align = when (lock.corner) {
        LockCorner.TOP_START -> Alignment.TopStart
        LockCorner.TOP_END -> Alignment.TopEnd
        LockCorner.BOTTOM_START -> Alignment.BottomStart
        LockCorner.BOTTOM_END -> Alignment.BottomEnd
    }
    val size = lock.buttonSizeDp.dp
    Box(
        Modifier.align(align)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(lock.buttonMarginDp.dp)
            .size(size)
            .alpha(lock.buttonOpacity)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val l = lockState.value
                    var firedPhone = false
                    val job = if (l.holdToLockPhone) scope.launch {
                        delay(180)
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween((l.holdDurationMs - 180).coerceAtLeast(200), easing = LinearEasing))
                        firedPhone = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        ui.lockPhone?.invoke()
                    } else null
                    waitForUpOrCancellation()?.consume()
                    job?.cancel()
                    scope.launch { progress.animateTo(0f, tween(150)) }
                    if (!firedPhone) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        ui.lockLauncher?.invoke()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MaterialSurface(
            tint = Color.Black.copy(alpha = 0.35f),
            cornerRadius = size / 2,
            modifier = Modifier.matchParentSize(),
            enabled = LocalFlyerConfig.current.style.applyToLock,
            backdrop = LocalBackdrops.current.home,
        )
        Icon(lock.icon.vector(), "Lock (hold to lock the phone)", tint = Color.White, modifier = Modifier.size(size * 0.48f))
        Canvas(Modifier.matchParentSize()) {
            val p = progress.value
            if (p > 0f) {
                drawArc(Color.White, -90f, 360f * p, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** Battery level/charging, updated live while the lock screen is shown. */
@Composable
private fun rememberBattery(): Pair<Int, Boolean>? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0 && scale > 0) state = (level * 100 / scale) to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read(i)
        }
        read(ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

/**
 * The launcher lock screen (in the spirit of Linux Mint's screen locker): the home screen stays
 * visible behind a blur, with the time, date, battery, next alarm and an away message, and an
 * Unlock button. Nothing underneath can be touched.
 */
@Composable
fun LockOverlay(ui: LauncherUiState?, lockState: LockState) {
    val config = LocalFlyerConfig.current
    val lock = config.lock
    val font = LocalLauncherFont.current
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var unlocking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val battery = if (lock.showBattery) rememberBattery() else null
    val alarm = remember(now / 60_000) {
        if (!lock.showNextAlarm) null
        else context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let { SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(it)) }
    }
    val shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 10f)
    val method = effectiveMethod(lock, lockState)

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = lock.dim))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume(); waitForUpOrCancellation()?.consume() } }
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            // Above centre, clear of a home-screen clock at the top (Mint-style).
            Modifier.align(BiasAlignment(0f, -0.4f)).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (lock.showClock) {
                Text(
                    SimpleDateFormat(if (config.widgets.clock.use24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)),
                    style = TextStyle(color = Color.White, fontSize = lock.clockSizeSp.sp, fontWeight = FontWeight.Light, fontFamily = font, shadow = shadow),
                )
            }
            if (lock.showDate) {
                Text(
                    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
                    style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontFamily = font, shadow = shadow),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                battery?.let { (level, charging) ->
                    InfoChip(if (charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd, "$level%" + if (charging) " · charging" else "")
                }
                alarm?.let { InfoChip(Icons.Outlined.Alarm, it) }
            }
            if (lock.awayMessage.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                MaterialSurface(
                    tint = Color.Black.copy(alpha = 0.35f), cornerRadius = 20.dp,
                    modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 24.dp),
                    enabled = config.style.applyToLock, backdrop = LocalBackdrops.current.home,
                ) {
                    Text(lock.awayMessage, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, fontFamily = font, modifier = Modifier.padding(16.dp))
                }
            }
        }

        AnimatedVisibility(!unlocking, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MaterialSurface(
                    tint = Color.White.copy(alpha = 0.22f), cornerRadius = 30.dp,
                    modifier = Modifier.clickableNoRipple {
                        if (method == UnlockMethod.PHONE || method == UnlockMethod.TAP) ui?.unlockWithPhoneOrTap?.invoke(method) else if (ui != null) unlocking = true
                    },
                    enabled = config.style.applyToLock, backdrop = LocalBackdrops.current.home,
                ) {
                    Row(Modifier.padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LockOpen, null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("Unlock", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = font)
                    }
                }
                if (ui?.canLockPhone?.invoke() == true) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.clickableNoRipple { ui?.lockPhone?.invoke() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PowerSettingsNew, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lock the phone", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
        }

        AnimatedVisibility(
            unlocking && ui != null, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            val glass = config.style.applyToLock && config.style.material.usesBackdrop
            MaterialSurface(
                tint = Color.Black.copy(alpha = if (glass) 0.5f else 0.78f), cornerRadius = 28.dp,
                modifier = Modifier.padding(16.dp).widthIn(max = 380.dp).imePadding(),
                enabled = config.style.applyToLock, backdrop = LocalBackdrops.current.home,
            ) {
                if (ui != null) CredentialEntry(
                    ui = ui,
                    pin = method == UnlockMethod.LAUNCHER_PIN,
                    lockState = lockState,
                    maxAttempts = lock.maxAttempts,
                    onCancel = { unlocking = false },
                    onSuccess = { unlocking = false; ui.finishUnlock?.invoke() },
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
    }
}

/** PIN pad or password field with lockout handling. Used by the lock screen and auth prompts. */
@Composable
fun CredentialEntry(
    ui: LauncherUiState,
    pin: Boolean,
    lockState: LockState,
    maxAttempts: Int,
    onCancel: () -> Unit,
    onSuccess: () -> Unit,
    title: String = if (pin) "Enter launcher PIN" else "Enter launcher password",
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var secret by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val lockedOutFor = ((lockState.lockoutUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    LaunchedEffect(lockState.lockoutUntil, tick) {
        if (lockState.lockoutUntil > System.currentTimeMillis()) { delay(1000); tick++ }
    }

    fun submit() {
        if (busy || secret.isEmpty() || lockedOutFor > 0) return
        busy = true
        scope.launch {
            when (val r = ui.core.lock.verify(secret, maxAttempts)) {
                VerifyResult.Ok -> { message = null; onSuccess() }
                is VerifyResult.Wrong -> {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    message = if (pin) "Wrong PIN · ${r.attemptsLeft} tries left" else "Wrong password · ${r.attemptsLeft} tries left"
                }
                is VerifyResult.LockedOut -> message = "Too many tries"
            }
            secret = ""
            busy = false
        }
    }

    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        val status = when {
            lockedOutFor > 0 -> "Try again in %d:%02d".format(lockedOutFor / 60, lockedOutFor % 60)
            else -> message
        }
        Text(status ?: " ", color = if (status != null) Color(0xFFFFB4AB) else Color.Transparent, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (pin) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(16.dp)) {
                repeat(secret.length.coerceAtMost(12)) { Box(Modifier.size(12.dp).background(Color.White, CircleShape)) }
            }
            Spacer(Modifier.height(16.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.padding(vertical = 6.dp).size(64.dp).background(Color.White.copy(alpha = 0.12f), CircleShape)
                                .clickable(enabled = lockedOutFor == 0L && !busy) {
                                    when (k) {
                                        "⌫" -> secret = secret.dropLast(1)
                                        "✓" -> submit()
                                        else -> if (secret.length < 12) secret += k
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "⌫" -> Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = Color.White)
                                "✓" -> Icon(Icons.Outlined.Check, "Unlock", tint = Color.White)
                                else -> Text(k, color = Color.White, fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            TextField(
                secret, { secret = it.take(64) }, singleLine = true, enabled = lockedOutFor == 0L,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                placeholder = { Text("Password") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f), unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.6f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Cancel", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.clickableNoRipple(onCancel).padding(8.dp))
            if (ui.core.lock.isDeviceSecure()) {
                Text(
                    "Forgot? Use phone lock", color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.clickableNoRipple {
                        ui.requestPhoneCredential?.invoke("Unlock Flyer Launcher") { ok -> if (ok) onSuccess() }
                    }.padding(8.dp),
                )
            }
        }
    }
}

/** "Unlock to open …" prompt for Protect Flyer and protected apps. */
@Composable
fun AuthPrompt(ui: LauncherUiState, request: com.flyer.launcher.launcher.AuthRequest, lockState: LockState) {
    val config = LocalFlyerConfig.current
    val method = effectiveMethod(config.lock, lockState)
    LaunchedEffect(request) {
        when (method) {
            UnlockMethod.TAP -> { ui.authRequest = null; request.onSuccess() }
            UnlockMethod.PHONE -> ui.requestPhoneCredential?.invoke(request.title) { ok ->
                ui.authRequest = null
                if (ok) request.onSuccess()
            }
            else -> Unit
        }
    }
    if (method == UnlockMethod.LAUNCHER_PIN || method == UnlockMethod.LAUNCHER_PASSWORD) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickableNoRipple { ui.authRequest = null },
            contentAlignment = Alignment.Center,
        ) {
            MaterialSurface(
                tint = Color(0xFF1E1E2A).copy(alpha = 0.9f), cornerRadius = 28.dp,
                modifier = Modifier.padding(16.dp).widthIn(max = 380.dp).imePadding().clickableNoRipple { },
                enabled = config.style.applyToLock, backdrop = LocalBackdrops.current.home,
            ) {
                CredentialEntry(
                    ui = ui, pin = method == UnlockMethod.LAUNCHER_PIN, lockState = lockState, maxAttempts = config.lock.maxAttempts,
                    title = request.title,
                    onCancel = { ui.authRequest = null },
                    onSuccess = {
                        ui.authRequest = null
                        ui.core.lock.markAuthorized()
                        request.onSuccess()
                    },
                )
            }
        }
    }
}
