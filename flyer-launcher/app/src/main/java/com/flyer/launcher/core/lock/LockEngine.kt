package com.flyer.launcher.core.lock

import android.app.KeyguardManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flyer.launcher.core.gestures.GestureHelperBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

enum class CredentialType { NONE, PIN, PASSWORD }

@Immutable
data class LockState(
    val locked: Boolean = false,
    val credential: CredentialType = CredentialType.NONE,
    val failedAttempts: Int = 0,
    /** Wall-clock time until which attempts are refused. */
    val lockoutUntil: Long = 0L,
)

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Wrong(val attemptsLeft: Int) : VerifyResult
    data class LockedOut(val untilMillis: Long) : VerifyResult
}

/** Minimal device admin: the only policy it declares is "force lock" (lock the screen now). */
class FlyerDeviceAdminReceiver : DeviceAdminReceiver()

/**
 * Lock Engine: the launcher lock state, the launcher-only credential and locking the real phone.
 *
 * Stored in its own DataStore, which is never exported with Flyer backups. The launcher lock
 * protects the launcher (home, drawer, widgets, Flyer); it is not a replacement for the system
 * lock screen — the long-press action locks the actual device for that.
 */
class LockEngine(private val context: Context, scope: CoroutineScope) {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("security") },
    )

    val state: StateFlow<LockState> = store.data.map { p ->
        LockState(
            locked = p[LOCKED] ?: false,
            credential = runCatching { CredentialType.valueOf(p[TYPE] ?: "NONE") }.getOrDefault(CredentialType.NONE),
            failedAttempts = p[FAILED] ?: 0,
            lockoutUntil = p[LOCKOUT] ?: 0L,
        )
    }.stateIn(scope, SharingStarted.Eagerly, LockState())

    /** Time of the last successful unlock (elapsed realtime), for "Protect Flyer" grace. */
    @Volatile
    var lastAuthorized: Long = 0L
        private set

    fun markAuthorized() {
        lastAuthorized = SystemClock.elapsedRealtime()
    }

    fun clearAuthorization() {
        lastAuthorized = 0L
    }

    fun recentlyAuthorized(windowMs: Long = 5 * 60_000L) =
        lastAuthorized != 0L && SystemClock.elapsedRealtime() - lastAuthorized < windowMs

    suspend fun lock() {
        store.edit { it[LOCKED] = true }
        clearAuthorization()
    }

    suspend fun unlock() {
        store.edit {
            it[LOCKED] = false
            it[FAILED] = 0
            it[LOCKOUT] = 0L
        }
        markAuthorized()
    }

    suspend fun setCredential(type: CredentialType, secret: String) {
        require(type != CredentialType.NONE)
        val hashed = withContext(Dispatchers.Default) { CredentialHasher.hash(secret) }
        store.edit {
            it[TYPE] = type.name
            it[SALT] = hashed.salt
            it[HASH] = hashed.hash
            it[ITER] = hashed.iterations
            it[FAILED] = 0
            it[LOCKOUT] = 0L
        }
    }

    suspend fun clearCredential() {
        store.edit {
            it[TYPE] = CredentialType.NONE.name
            it.remove(SALT); it.remove(HASH); it.remove(ITER)
        }
    }

    /** Checks the launcher credential with exponential lockout after [maxAttempts] failures. */
    suspend fun verify(secret: String, maxAttempts: Int, now: Long = System.currentTimeMillis()): VerifyResult {
        val p = store.data.first()
        val lockout = p[LOCKOUT] ?: 0L
        if (now < lockout) return VerifyResult.LockedOut(lockout)
        val stored = CredentialHasher.Hashed(p[SALT] ?: return VerifyResult.Wrong(0), p[HASH] ?: return VerifyResult.Wrong(0), p[ITER] ?: CredentialHasher.ITERATIONS)
        val ok = withContext(Dispatchers.Default) { CredentialHasher.verify(secret, stored) }
        if (ok) {
            store.edit { it[FAILED] = 0; it[LOCKOUT] = 0L }
            return VerifyResult.Ok
        }
        var result: VerifyResult = VerifyResult.Wrong(0)
        store.edit {
            val failed = (it[FAILED] ?: 0) + 1
            it[FAILED] = failed
            if (failed >= maxAttempts) {
                val over = failed - maxAttempts
                val until = now + lockoutDuration(over)
                it[LOCKOUT] = until
                result = VerifyResult.LockedOut(until)
            } else result = VerifyResult.Wrong(maxAttempts - failed)
        }
        return result
    }

    // ───────────── Phone lock / phone credential ─────────────

    private val dpm get() = context.getSystemService(DevicePolicyManager::class.java)
    private val keyguard get() = context.getSystemService(KeyguardManager::class.java)
    val adminComponent = ComponentName(context, FlyerDeviceAdminReceiver::class.java)

    /** Whether the phone itself has a PIN/pattern/password set. */
    fun isDeviceSecure(): Boolean = keyguard?.isDeviceSecure == true

    fun isAdminActive(): Boolean = runCatching { dpm?.isAdminActive(adminComponent) == true }.getOrDefault(false)

    fun canLockDevice(): Boolean = isAdminActive() || (GestureHelperBridge.performGlobalAction != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)

    /** Locks the actual phone (screen off + system lock screen). */
    fun lockDevice(): Result<Unit> {
        val helper = GestureHelperBridge.performGlobalAction
        if (helper != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            helper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        ) return Result.success(Unit)
        if (isAdminActive()) return runCatching { dpm!!.lockNow() }
        return Result.failure(IllegalStateException("Allow Flyer Launcher to lock the phone in Flyer › Lock"))
    }

    fun adminActivationIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Lets Flyer Launcher turn the screen off and lock the phone when you hold the lock button. It cannot read or change anything else.",
        )

    fun removeAdmin() {
        runCatching { dpm?.removeActiveAdmin(adminComponent) }
    }

    companion object {
        private val LOCKED = booleanPreferencesKey("locked")
        private val TYPE = stringPreferencesKey("credential_type")
        private val SALT = stringPreferencesKey("salt")
        private val HASH = stringPreferencesKey("hash")
        private val ITER = intPreferencesKey("iterations")
        private val FAILED = intPreferencesKey("failed")
        private val LOCKOUT = longPreferencesKey("lockout_until")

        /** 30 s, 1 min, 2 min, 4 min … capped at 30 min. */
        fun lockoutDuration(overLimit: Int): Long = (30_000L shl overLimit.coerceIn(0, 6)).coerceAtMost(30 * 60_000L)
    }
}
