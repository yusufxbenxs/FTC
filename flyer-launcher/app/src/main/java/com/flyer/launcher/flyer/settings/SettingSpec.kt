package com.flyer.launcher.flyer.settings

import androidx.compose.runtime.Immutable
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.theme.DeviceTraits
import com.flyer.launcher.flyer.FlyerCategory

enum class SettingLevel { BASIC, ADVANCED }

/** Elements of the home screen that the live editor can select; settings declare which they style. */
enum class EditorElement(val title: String) {
    WORKSPACE("Workspace"), BACKGROUND("Background"), ICON("Icons"), LABEL("Labels"), DOCK("Dock"),
    PAGE_INDICATOR("Page indicator"), SEARCH_BAR("Search bar"), FOLDER("Folders"), WIDGET("Widgets"),
    CLOCK("Clock"), DESKTOP("Desktop"), TASKBAR("Taskbar"),
}

/**
 * One customizable property. A spec is a lens into [FlyerConfig] plus everything Flyer needs to
 * render a control for it, search it, reset it and explain platform limitations.
 */
@Immutable
sealed class SettingSpec(
    val id: String,
    val title: String,
    val description: String?,
    val category: FlyerCategory,
    val section: String,
    val keywords: List<String>,
    val level: SettingLevel,
    val elements: Set<EditorElement>,
    /** Hides the control when it does not apply (e.g. corner radius for circles). */
    val visibleWhen: (FlyerConfig) -> Boolean,
    /** Explains why a value has no effect on this device / configuration (shown under the control). */
    val limitation: (FlyerConfig, DeviceTraits) -> String?,
) {
    abstract fun isDefault(c: FlyerConfig): Boolean
    abstract fun reset(c: FlyerConfig): FlyerConfig

    /** Human readable current value (dashboard summaries, search results). */
    abstract fun display(c: FlyerConfig): String

    fun matches(query: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val t = title.lowercase()
        return when {
            t.startsWith(q) -> 100
            t.contains(q) -> 80
            keywords.any { it.lowercase().startsWith(q) } -> 60
            section.lowercase().contains(q) -> 40
            description?.lowercase()?.contains(q) == true -> 30
            category.title.lowercase().contains(q) -> 20
            q.split(' ').all { w -> "$t ${keywords.joinToString(" ")} ${description.orEmpty()}".lowercase().contains(w) } -> 15
            else -> 0
        }
    }

    protected companion object {
        val DEFAULT = FlyerConfig()
    }
}

class Meta(
    val id: String,
    val title: String,
    val category: FlyerCategory,
    val section: String,
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val level: SettingLevel = SettingLevel.BASIC,
    val elements: Set<EditorElement> = emptySet(),
    val visibleWhen: (FlyerConfig) -> Boolean = { true },
    val limitation: (FlyerConfig, DeviceTraits) -> String? = { _, _ -> null },
)

class ToggleSpec(
    m: Meta,
    val get: (FlyerConfig) -> Boolean,
    val set: (FlyerConfig, Boolean) -> FlyerConfig,
) : SettingSpec(m.id, m.title, m.description, m.category, m.section, m.keywords, m.level, m.elements, m.visibleWhen, m.limitation) {
    override fun isDefault(c: FlyerConfig) = get(c) == get(DEFAULT)
    override fun reset(c: FlyerConfig) = set(c, get(DEFAULT))
    override fun display(c: FlyerConfig) = if (get(c)) "On" else "Off"
}

class SliderSpec(
    m: Meta,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val unit: String,
    val get: (FlyerConfig) -> Float,
    val set: (FlyerConfig, Float) -> FlyerConfig,
    val format: (Float) -> String = { v -> if (step >= 1f) "${v.toInt()}$unit" else "%.2f".format(v).trimEnd('0').trimEnd('.') + unit },
) : SettingSpec(m.id, m.title, m.description, m.category, m.section, m.keywords, m.level, m.elements, m.visibleWhen, m.limitation) {
    override fun isDefault(c: FlyerConfig) = get(c) == get(DEFAULT)
    override fun reset(c: FlyerConfig) = set(c, get(DEFAULT))
    override fun display(c: FlyerConfig) = format(get(c))

    fun snap(v: Float): Float {
        val clamped = v.coerceIn(range.start, range.endInclusive)
        if (step <= 0f) return clamped
        val steps = ((clamped - range.start) / step + 0.5f).toInt()
        return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
    }
}

class ChoiceSpec<E>(
    m: Meta,
    val options: List<Pair<E, String>>,
    val get: (FlyerConfig) -> E,
    val set: (FlyerConfig, E) -> FlyerConfig,
) : SettingSpec(m.id, m.title, m.description, m.category, m.section, m.keywords, m.level, m.elements, m.visibleWhen, m.limitation) {
    override fun isDefault(c: FlyerConfig) = get(c) == get(DEFAULT)
    override fun reset(c: FlyerConfig) = set(c, get(DEFAULT))
    override fun display(c: FlyerConfig) = options.firstOrNull { it.first == get(c) }?.second ?: get(c).toString()

    @Suppress("UNCHECKED_CAST")
    fun setAny(c: FlyerConfig, value: Any?) = set(c, value as E)
}

class ColorSpec(
    m: Meta,
    val allowAlpha: Boolean,
    val get: (FlyerConfig) -> ColorValue,
    val set: (FlyerConfig, ColorValue) -> FlyerConfig,
) : SettingSpec(m.id, m.title, m.description, m.category, m.section, m.keywords, m.level, m.elements, m.visibleWhen, m.limitation) {
    override fun isDefault(c: FlyerConfig) = get(c) == get(DEFAULT)
    override fun reset(c: FlyerConfig) = set(c, get(DEFAULT))
    override fun display(c: FlyerConfig) = get(c).toHex()
}

class TextSpec(
    m: Meta,
    val placeholder: String,
    val validate: (String) -> String?,
    val get: (FlyerConfig) -> String,
    val set: (FlyerConfig, String) -> FlyerConfig,
) : SettingSpec(m.id, m.title, m.description, m.category, m.section, m.keywords, m.level, m.elements, m.visibleWhen, m.limitation) {
    override fun isDefault(c: FlyerConfig) = get(c) == get(DEFAULT)
    override fun reset(c: FlyerConfig) = set(c, get(DEFAULT))
    override fun display(c: FlyerConfig) = get(c)
}

/** A search hit (also used by the launcher's global search to jump into Flyer). */
@Immutable
data class SettingHit(val spec: SettingSpec, val score: Int)
