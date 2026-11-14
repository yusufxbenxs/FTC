package com.flyer.launcher.core.persistence

import com.flyer.launcher.core.config.FlyerConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Upgrades persisted documents and backups from older schema versions.
 *
 * A migration receives the raw JSON of version N and must return JSON of version N + 1. Working on
 * JSON (instead of the typed model) means old field names can be read even after the Kotlin model
 * dropped them. Register a step here whenever a change to the config or layout model is not purely
 * additive (renames, unit changes, restructuring).
 */
object ConfigMigrations {

    fun interface Migration {
        fun migrate(root: JsonObject): JsonObject
    }

    /** fromVersion -> migration to fromVersion + 1. Schema v1 is the first released format. */
    private val steps: Map<Int, Migration> = emptyMap()

    internal var testSteps: Map<Int, Migration>? = null

    fun migrate(root: JsonObject, fromVersion: Int, warnings: MutableList<String>): JsonObject {
        val registry = testSteps ?: steps
        val target = testSteps?.keys?.maxOrNull()?.plus(1) ?: FlyerConfig.CURRENT_SCHEMA_VERSION
        var version = fromVersion.coerceAtLeast(1)
        var current = root
        while (version < target) {
            val step = registry[version]
            if (step == null) {
                warnings += "No migration from schema $version; continuing with best effort"
                break
            }
            current = step.migrate(current)
            version++
        }
        return JsonObject(current + ("schemaVersion" to JsonPrimitive(version)))
    }
}
