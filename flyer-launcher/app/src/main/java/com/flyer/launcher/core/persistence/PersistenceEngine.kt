package com.flyer.launcher.core.persistence

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.workspace.LayoutValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "FlyerPersistence"

/** DataStore serializer for the launcher document (JSON, validated on read). */
internal object LauncherDocumentSerializer : Serializer<LauncherDocument> {
    override val defaultValue: LauncherDocument = LauncherDocument()

    override suspend fun readFrom(input: InputStream): LauncherDocument {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            val result = DocumentCodec.decodeDocument(text)
            result.warnings.forEach { Log.w(TAG, it) }
            result.value
        } catch (e: InvalidBackupException) {
            throw CorruptionException("Launcher document unreadable", e)
        }
    }

    override suspend fun writeTo(t: LauncherDocument, output: OutputStream) {
        output.write(DocumentCodec.encodeDocument(t).encodeToByteArray())
    }
}

/**
 * Persistence Engine: owns the on-disk launcher document, launch statistics and saved snapshots.
 * All reads are exposed as flows; writes are atomic DataStore transactions.
 */
class PersistenceEngine(private val context: Context, private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {

    private val documentStore: DataStore<LauncherDocument> = DataStoreFactory.create(
        serializer = LauncherDocumentSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            Log.e(TAG, "Launcher document corrupt; keeping a copy and starting fresh", e)
            runCatching {
                val src = context.dataStoreFile(DOCUMENT_FILE)
                src.copyTo(File(src.parentFile, "$DOCUMENT_FILE.corrupt-${System.currentTimeMillis()}"), overwrite = true)
            }
            LauncherDocument()
        },
        scope = scope,
        produceFile = { context.dataStoreFile(DOCUMENT_FILE) },
    )

    private val usageStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("usage") },
    )

    val document: Flow<LauncherDocument> = documentStore.data

    suspend fun load(): LauncherDocument = documentStore.data.first()

    suspend fun update(transform: (LauncherDocument) -> LauncherDocument): LauncherDocument =
        documentStore.updateData { current ->
            val next = transform(current)
            if (next === current) current else {
                val config = ConfigValidator.sanitize(next.config)
                next.copy(config = config, layout = LayoutValidator.sanitize(next.layout, config))
            }
        }

    suspend fun updateLayout(transform: (HomeLayout) -> HomeLayout) =
        update { doc -> doc.copy(layout = transform(doc.layout)) }

    // ───────────── Usage statistics (for "most used" sorting and the recent row) ─────────────

    val launchCounts: StateFlow<Map<String, Long>> = usageStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(COUNT_PREFIX) }
            .associate { it.key.name.removePrefix(COUNT_PREFIX) to (it.value as? Long ?: 0L) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val lastLaunched: StateFlow<Map<String, Long>> = usageStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(TIME_PREFIX) }
            .associate { it.key.name.removePrefix(TIME_PREFIX) to (it.value as? Long ?: 0L) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    suspend fun recordLaunch(appKey: String) {
        usageStore.edit { prefs ->
            val countKey = longPreferencesKey(COUNT_PREFIX + appKey)
            prefs[countKey] = (prefs[countKey] ?: 0L) + 1
            prefs[longPreferencesKey(TIME_PREFIX + appKey)] = System.currentTimeMillis()
        }
    }

    suspend fun clearUsage() {
        usageStore.edit { it.clear() }
    }

    // ───────────── Snapshots (saved themes / full setups) ─────────────

    data class Snapshot(val file: File, val name: String, val kind: BackupKind, val savedAt: Long)

    private val snapshotDir: File get() = File(context.filesDir, "snapshots").apply { mkdirs() }

    suspend fun listSnapshots(): List<Snapshot> = withContext(Dispatchers.IO) {
        snapshotDir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            runCatching {
                val backup = DocumentCodec.decodeBackup(file.readText())
                Snapshot(file, backup.name, backup.kind, file.lastModified())
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    suspend fun saveSnapshot(doc: LauncherDocument, name: String, kind: BackupKind): Snapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "snapshot" }.take(40)
        val file = File(snapshotDir, "${kind.name.lowercase()}-$now-${safe.replace(' ', '_')}.json")
        file.writeText(DocumentCodec.encodeBackup(doc, kind, name, now))
        Snapshot(file, name, kind, now)
    }

    suspend fun readSnapshot(snapshot: Snapshot): DocumentCodec.Backup = withContext(Dispatchers.IO) {
        DocumentCodec.decodeBackup(snapshot.file.readText())
    }

    suspend fun deleteSnapshot(snapshot: Snapshot) = withContext(Dispatchers.IO) { snapshot.file.delete() }

    // ───────────── Private media (custom icons, background images, fonts) ─────────────

    fun mediaDir(kind: String): File = File(context.filesDir, "media/$kind").apply { mkdirs() }

    /** Copies a user picked content Uri into private storage and returns the absolute path. */
    suspend fun importMedia(uri: android.net.Uri, kind: String, extension: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(mediaDir(kind), "${System.currentTimeMillis()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out ->
                    val limit = 20L * 1024 * 1024
                    var copied = 0L
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > limit) error("File too large")
                        out.write(buffer, 0, n)
                    }
                }
            } ?: return@runCatching null
            target.absolutePath
        }.onFailure { Log.w(TAG, "Media import failed", it) }.getOrNull()
    }

    companion object {
        const val DOCUMENT_FILE = "launcher_document.json"
        private const val COUNT_PREFIX = "count:"
        private const val TIME_PREFIX = "time:"
    }
}
