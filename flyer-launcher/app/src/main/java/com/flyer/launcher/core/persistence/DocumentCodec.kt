package com.flyer.launcher.core.persistence

import androidx.compose.runtime.Immutable
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.workspace.LayoutValidator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/** What the launcher persists: the configuration plus the user's arrangement. */
@Immutable
@Serializable
data class LauncherDocument(
    val schemaVersion: Int = FlyerConfig.CURRENT_SCHEMA_VERSION,
    val config: FlyerConfig = FlyerConfig(),
    val layout: HomeLayout = HomeLayout(),
)

enum class BackupKind { FULL, THEME }

class DecodeResult<T>(val value: T, val warnings: List<String>)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Encodes and decodes launcher documents and backup files.
 *
 * Decoding is deliberately lenient: each top-level section is decoded on its own so one corrupt
 * section only resets that section to defaults, and everything is passed through the validators.
 */
object DocumentCodec {

    const val BACKUP_FORMAT = "flyer-launcher-backup"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        allowSpecialFloatingPointValues = false
        classDiscriminator = "type"
    }

    private val pretty: Json = Json(json) { prettyPrint = true }

    fun encodeDocument(doc: LauncherDocument): String = json.encodeToString(LauncherDocument.serializer(), doc)

    fun decodeDocument(text: String): DecodeResult<LauncherDocument> {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw InvalidBackupException("Not a valid JSON document", e)
        }
        return decodeDocument(root)
    }

    fun decodeDocument(root: JsonObject): DecodeResult<LauncherDocument> {
        val warnings = mutableListOf<String>()
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        val migrated = ConfigMigrations.migrate(root, version, warnings)
        val config = (migrated["config"] as? JsonObject)?.let { decodeConfig(it, warnings) } ?: FlyerConfig()
        val layout = migrated["layout"]?.let { element ->
            runCatching { json.decodeFromJsonElement<HomeLayout>(element) }
                .onFailure { warnings += "Layout could not be read and was reset (${it.message?.take(80)})" }
                .getOrNull()
        } ?: HomeLayout()
        val cleanConfig = ConfigValidator.sanitize(config)
        val cleanLayout = LayoutValidator.sanitize(layout, cleanConfig)
        return DecodeResult(LauncherDocument(FlyerConfig.CURRENT_SCHEMA_VERSION, cleanConfig, cleanLayout), warnings)
    }

    /** Decodes a config section by section; failing sections fall back to their defaults. */
    fun decodeConfig(obj: JsonObject, warnings: MutableList<String>): FlyerConfig {
        val defaults = FlyerConfig()
        fun <T> section(key: String, serializer: KSerializer<T>, fallback: T): T {
            val element = obj[key] ?: return fallback
            return try {
                json.decodeFromJsonElement(serializer, element)
            } catch (e: Exception) {
                warnings += "Section \"$key\" was invalid and has been reset"
                fallback
            }
        }
        return FlyerConfig(
            schemaVersion = FlyerConfig.CURRENT_SCHEMA_VERSION,
            home = section("home", serializer(), defaults.home),
            icons = section("icons", serializer(), defaults.icons),
            dock = section("dock", serializer(), defaults.dock),
            drawer = section("drawer", serializer(), defaults.drawer),
            folders = section("folders", serializer(), defaults.folders),
            widgets = section("widgets", serializer(), defaults.widgets),
            gestures = section("gestures", serializer(), defaults.gestures),
            animations = section("animations", serializer(), defaults.animations),
            appearance = section("appearance", serializer(), defaults.appearance),
            typography = section("typography", serializer(), defaults.typography),
            desktop = section("desktop", serializer(), defaults.desktop),
            search = section("search", serializer(), defaults.search),
            performance = section("performance", serializer(), defaults.performance),
            advanced = section("advanced", serializer(), defaults.advanced),
            style = section("style", serializer(), defaults.style),
            lock = section("lock", serializer(), defaults.lock),
        )
    }

    // ───────────── Backup files ─────────────

    fun encodeBackup(doc: LauncherDocument, kind: BackupKind, name: String, timestamp: Long): String {
        val obj = buildJsonObject {
            put("format", JsonPrimitive(BACKUP_FORMAT))
            put("kind", JsonPrimitive(kind.name))
            put("name", JsonPrimitive(name))
            put("exportedAt", JsonPrimitive(timestamp))
            put("schemaVersion", JsonPrimitive(FlyerConfig.CURRENT_SCHEMA_VERSION))
            put("config", json.encodeToJsonElement(doc.config))
            if (kind == BackupKind.FULL) put("layout", json.encodeToJsonElement(doc.layout))
        }
        return pretty.encodeToString(JsonElement.serializer(), obj)
    }

    class Backup(val kind: BackupKind, val name: String, val document: LauncherDocument, val warnings: List<String>)

    fun decodeBackup(text: String): Backup {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw InvalidBackupException("The file is not a Flyer backup (invalid JSON)", e)
        }
        val format = (root["format"] as? JsonPrimitive)?.content
        if (format != BACKUP_FORMAT) throw InvalidBackupException("The file is not a Flyer backup")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.int ?: 1
        val kind = runCatching { BackupKind.valueOf((root["kind"] as JsonPrimitive).content) }.getOrDefault(BackupKind.FULL)
        val name = (root["name"] as? JsonPrimitive)?.content ?: "Imported setup"
        if (root["config"] !is JsonObject) throw InvalidBackupException("The backup contains no configuration")
        val warnings = mutableListOf<String>()
        if (version > FlyerConfig.CURRENT_SCHEMA_VERSION) {
            warnings += "This backup was made by a newer version; unknown settings were ignored"
        }
        val decoded = decodeDocument(root)
        warnings += decoded.warnings
        return Backup(kind, name, decoded.value, warnings)
    }
}
