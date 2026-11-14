package com.flyer.launcher.flyer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.theme.DeviceTraits
import com.flyer.launcher.flyer.settings.ChoiceSpec
import com.flyer.launcher.flyer.settings.ColorSpec
import com.flyer.launcher.flyer.settings.SettingLevel
import com.flyer.launcher.flyer.settings.SettingSpec
import com.flyer.launcher.flyer.settings.SliderSpec
import com.flyer.launcher.flyer.settings.TextSpec
import com.flyer.launcher.flyer.settings.ToggleSpec
import kotlinx.coroutines.delay

/** Callback used by every control: the new config, a history label and a coalescing key. */
typealias ConfigEdit = (next: FlyerConfig, label: String, coalesceKey: String?) -> Unit

@Composable
fun SettingRow(spec: SettingSpec, config: FlyerConfig, traits: DeviceTraits, highlighted: Boolean, onEdit: ConfigEdit, modifier: Modifier = Modifier) {
    var flash by remember(highlighted) { mutableStateOf(highlighted) }
    LaunchedEffect(highlighted) { if (highlighted) { delay(1600); flash = false } }
    val bg by animateColorAsState(if (flash) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "highlight")
    val modified = !spec.isDefault(config)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        when (spec) {
            is ToggleSpec -> ToggleControl(spec, config, modified, onEdit)
            is SliderSpec -> SliderControl(spec, config, modified, onEdit)
            is ChoiceSpec<*> -> ChoiceControl(spec, config, modified, onEdit)
            is ColorSpec -> ColorControl(spec, config, modified, onEdit)
            is TextSpec -> TextControl(spec, config, modified, onEdit)
        }
        spec.limitation(config, traits)?.let { note ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, null, Modifier.size(14.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TitleBlock(spec: SettingSpec, modified: Boolean, modifier: Modifier = Modifier, value: String? = null) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(spec.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (spec.level == SettingLevel.ADVANCED) {
                    Spacer(Modifier.width(6.dp))
                    Text("ADV", fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
                if (modified) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        spec.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ResetButton(visible: Boolean, onReset: () -> Unit) {
    if (visible) IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Outlined.RestartAlt, "Reset to default", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    } else Spacer(Modifier.size(36.dp))
}

@Composable
private fun ToggleControl(spec: ToggleSpec, config: FlyerConfig, modified: Boolean, onEdit: ConfigEdit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onEdit(spec.set(config, !spec.get(config)), spec.title, null) }) {
        TitleBlock(spec, modified, Modifier.weight(1f))
        ResetButton(modified) { onEdit(spec.reset(config), "Reset ${spec.title}", null) }
        Switch(checked = spec.get(config), onCheckedChange = { onEdit(spec.set(config, it), spec.title, null) })
    }
}

@Composable
private fun SliderControl(spec: SliderSpec, config: FlyerConfig, modified: Boolean, onEdit: ConfigEdit) {
    val value = spec.get(config)
    var dragValue by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragValue.isNaN()) value else dragValue
    Row(verticalAlignment = Alignment.CenterVertically) {
        TitleBlock(spec, modified, Modifier.weight(1f), value = spec.format(shown))
        ResetButton(modified) { onEdit(spec.reset(config), "Reset ${spec.title}", null) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onEdit(spec.set(config, spec.snap(value - spec.step)), spec.title, spec.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Remove, "Decrease", Modifier.size(16.dp))
        }
        val steps = if (spec.step > 0f) (((spec.range.endInclusive - spec.range.start) / spec.step).toInt() - 1).coerceIn(0, 200) else 0
        Slider(
            value = shown.coerceIn(spec.range.start, spec.range.endInclusive),
            onValueChange = { v ->
                val snapped = spec.snap(v)
                dragValue = snapped
                onEdit(spec.set(config, snapped), spec.title, spec.id)
            },
            onValueChangeFinished = { dragValue = Float.NaN },
            valueRange = spec.range,
            steps = if (steps in 1..60) steps else 0,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onEdit(spec.set(config, spec.snap(value + spec.step)), spec.title, spec.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Add, "Increase", Modifier.size(16.dp))
        }
    }
}

@Composable
private fun <T> ChoiceControl(spec: ChoiceSpec<T>, config: FlyerConfig, modified: Boolean, onEdit: ConfigEdit) {
    val current = spec.get(config)
    if (spec.options.size <= 4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleBlock(spec, modified, Modifier.weight(1f))
            ResetButton(modified) { onEdit(spec.reset(config), "Reset ${spec.title}", null) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.options.forEach { (value, label) ->
                FilterChip(selected = value == current, onClick = { onEdit(spec.set(config, value), "${spec.title}: $label", null) }, label = { Text(label) })
            }
        }
    } else {
        var open by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = true }) {
            TitleBlock(spec, modified, Modifier.weight(1f))
            ResetButton(modified) { onEdit(spec.reset(config), "Reset ${spec.title}", null) }
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(spec.display(config), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    spec.options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (value == current) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { open = false; onEdit(spec.set(config, value), "${spec.title}: $label", null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorControl(spec: ColorSpec, config: FlyerConfig, modified: Boolean, onEdit: ConfigEdit) {
    var open by remember { mutableStateOf(false) }
    val value = spec.get(config)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = true }) {
        TitleBlock(spec, modified, Modifier.weight(1f))
        ResetButton(modified) { onEdit(spec.reset(config), "Reset ${spec.title}", null) }
        ColorSwatch(value, 36)
    }
    if (open) {
        ColorPickerDialog(
            title = spec.title, initial = value, allowAlpha = spec.allowAlpha,
            onPreview = { onEdit(spec.set(config, it), spec.title, spec.id) },
            onDismiss = { open = false },
        )
    }
}

@Composable
fun ColorSwatch(value: ColorValue, sizeDp: Int, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = CircleShape
    Box(
        Modifier.size(sizeDp.dp).clip(shape)
            .background(Brushes.checker())
            .background(value.color)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

@Composable
private fun TextControl(spec: TextSpec, config: FlyerConfig, modified: Boolean, onEdit: ConfigEdit) {
    var text by remember(spec.get(config)) { mutableStateOf(spec.get(config)) }
    val error = spec.validate(text)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TitleBlock(spec, modified, Modifier.weight(1f))
        ResetButton(modified) { onEdit(spec.reset(config), "Reset ${spec.title}", null) }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (spec.validate(it) == null) onEdit(spec.set(config, it), spec.title, spec.id)
        },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        placeholder = { Text(spec.placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
