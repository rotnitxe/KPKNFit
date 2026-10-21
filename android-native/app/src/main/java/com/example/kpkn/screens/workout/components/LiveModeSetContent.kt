package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** The footer keeps its 44dp targets while additional content scrolls inside the 425dp frame. */
@Composable
internal fun LiveModeSetContent(
    mode: LiveSetCardMode,
    setNumber: Int,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().testTag("live-mode-${mode.name}")) {
        Column(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("live-mode-body")
            .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${mode.label} · S$setNumber", style = KpknType.eyebrow.copy(fontSize = 10.sp, lineHeight = 14.sp), color = WizardColors.textMuted)
            content()
        }
        Box(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) { actions() }
    }
}

@Composable
private fun ModeLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = KpknType.eyebrow.copy(fontSize = 10.sp, lineHeight = 14.sp), color = WizardColors.textMuted)
}

@Composable
internal fun LiveModeNumberField(
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    description: String,
    modifier: Modifier = Modifier,
    time: Boolean = false,
    enabled: Boolean = true,
    placeholder: String = "0",
    sizeSp: Int = 28,
    preserveEntryWhileFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (!focused) entry = value }
    BasicTextField(if (focused && preserveEntryWhileFocused) entry else value, { entry = it; onChange(it) }, modifier.heightIn(min = 44.dp).testTag(tag)
        .onFocusChanged { focused = it.isFocused; if (!focused) entry = value }.semantics { contentDescription = description },
        enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (time) KeyboardType.Ascii else KeyboardType.Decimal),
        textStyle = KpknType.metricM.copy(fontSize = (if (value.length > 7) 20 else sizeSp).sp, lineHeight = 40.sp,
            color = WizardColors.text, textAlign = TextAlign.Center, fontFeatureSettings = "'tnum' 1"),
        cursorBrush = SolidColor(WizardColors.text),
        decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            if (value.isBlank()) Text(placeholder, style = KpknType.metricM.copy(fontSize = sizeSp.sp), color = WizardColors.textFaint)
            inner()
        } })
}

@Composable
internal fun LiveQuantityInput(
    value: String,
    onChange: (String) -> Unit,
    unit: UnitModeV2,
    customUnit: String?,
    target: Double?,
    enabled: Boolean = true,
) {
    val suffix = when (unit) {
        UnitModeV2.TIME -> "min:s"
        UnitModeV2.DISTANCE -> "m"
        UnitModeV2.CUSTOM -> customUnit?.takeIf { it.isNotBlank() } ?: "cantidad"
        UnitModeV2.REPS -> "reps"
    }
    val isTime = unit == UnitModeV2.TIME
    fun display(raw: String) = if (isTime && raw.toIntOrNull() != null) formatLiveDuration(raw.toInt()) else raw
    var entry by remember(unit) { mutableStateOf(display(value)) }
    LaunchedEffect(value, unit) {
        if (liveQuantityNumber(entry, unit) != liveQuantityNumber(value, unit) || entry.isBlank() != value.isBlank()) entry = display(value)
    }
    val actual = liveQuantityNumber(value, unit) ?: 0.0
    Column(Modifier.fillMaxWidth().testTag("live-quantity")) {
        if (target != null && target > 0 && !isTime) {
            Text("Objetivo ${target.toLiveNumberString()} $suffix", style = KpknType.caption, color = WizardColors.textMuted)
            Spacer(Modifier.height(6.dp))
        }
        ModeLabel(when (unit) {
            UnitModeV2.TIME -> "TIEMPO REALIZADO · min:s"
            UnitModeV2.DISTANCE -> "METROS RECORRIDOS"
            UnitModeV2.CUSTOM -> "CANTIDAD REALIZADA"
            UnitModeV2.REPS -> "REPS REALIZADAS"
        })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((actual - 1).coerceAtLeast(0.0).toLiveNumberString()) }, enabled = enabled, modifier = Modifier.size(44.dp).semantics { contentDescription = "Restar $suffix" }, contentPadding = PaddingValues(0.dp)) { Text("−", color = WizardColors.text) }
            LiveModeNumberField(entry, { entry = it; onChange(it) }, "live-quantity-input", "Cantidad realizada ($suffix)", Modifier.weight(1f), time = isTime,
                placeholder = if (isTime) "0:00" else "0", enabled = enabled)
            if (unit == UnitModeV2.DISTANCE) Text(suffix, style = KpknType.caption, color = WizardColors.textMuted)
            TextButton(onClick = { onChange((actual + 1).toLiveNumberString()) }, enabled = enabled, modifier = Modifier.size(44.dp).semantics { contentDescription = "Añadir $suffix" }, contentPadding = PaddingValues(0.dp)) { Text("+", color = WizardColors.text) }
        }
        if (unit == UnitModeV2.CUSTOM) Text(suffix, style = KpknType.caption, color = WizardColors.textMuted)
        if (target != null && target > 0 && !isTime) {
            val fraction = (actual / target).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(2.dp).background(KpknSurface.inset)
                .testTag("live-quantity-progress").semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(WizardColors.text))
            }
        }
    }
}

@Composable
internal fun LiveLoadModeButton(mode: LoadModeV2, expanded: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, onSelect: (LoadModeV2) -> Unit,
    quickLoads: List<QuickLoadChipOption> = emptyList(), onQuickLoad: (QuickLoadChipOption) -> Unit = {}) {
    Box {
        TextButton(onClick = onOpen, modifier = Modifier.height(44.dp).semantics { contentDescription = "Cambiar tipo de carga" }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(when (mode) { LoadModeV2.LOAD -> "Externa"; LoadModeV2.BODYWEIGHT -> "Corporal"; LoadModeV2.LASTRE -> "Lastre"; LoadModeV2.ASSISTED -> "Asistida" },
                style = KpknType.caption.copy(fontSize = 10.sp), color = WizardColors.textMuted)
        }
        KpknDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            listOf(LoadModeV2.LOAD to "Carga", LoadModeV2.BODYWEIGHT to "Peso corporal", LoadModeV2.LASTRE to "Lastre", LoadModeV2.ASSISTED to "Asistido").forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value) })
            }
            if (quickLoads.isNotEmpty()) {
                HorizontalDivider()
                ModeLabel("CARGAS RÁPIDAS", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                quickLoads.forEachIndexed { index, option ->
                    val known = option.weight.isFinite() && (option.weight > 0 || option.targetLoadMode == LoadModeV2.BODYWEIGHT)
                    val label = if (option.isAuge) "Sugerida" else option.label
                    val value = if (known) "${option.weight.toLiveNumberString()} kg" else "—"
                    DropdownMenuItem(text = { Text("$label · $value") }, enabled = known,
                        modifier = Modifier.testTag("quick_suggestion_menu_$index"), onClick = { onQuickLoad(option) })
                }
            }
        }
    }
}

@Composable
internal fun LiveCompactLoadInput(value: String, onChange: (String) -> Unit, label: String, placeholder: String?, mode: LoadModeV2, modeButton: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ModeLabel(if (mode == LoadModeV2.BODYWEIGHT) "PESO CORPORAL" else label.uppercase(), Modifier.weight(1f))
        if (mode != LoadModeV2.BODYWEIGHT) LiveModeNumberField(value, onChange, "live-actual-load", "Carga utilizada en kg", Modifier.width(80.dp), placeholder = placeholder ?: "0", sizeSp = 23)
        modeButton()
    }
}

@Composable
internal fun LiveSetClockHero(elapsed: Int, remaining: Int, target: Int?, running: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("live-set-clock")) {
        ModeLabel("CRONÓMETRO DE LA SERIE")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatLiveDuration(if (target != null && elapsed == 0) target else if (running && target != null) remaining else elapsed),
                Modifier.weight(1f).testTag("live-set-clock-value"), style = KpknType.metricM.copy(fontSize = 44.sp, lineHeight = 50.sp), color = WizardColors.text)
            IconButton(onClick = onToggle, modifier = Modifier.size(52.dp).background(WizardColors.text, CircleShape)) {
                Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, if (running) "Detener" else "Iniciar", tint = KpknSurface.canvas)
            }
        }
        Text(if (target != null) "${if (running) "Restan" else "Objetivo"} ${formatLiveDuration(if (running) remaining else target)}" else "Tiempo libre",
            style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = WizardColors.textMuted)
    }
}

/** Main view mirrors V6; the linked editor expands inside the scrollable card body. */
@Composable
internal fun LiveRmFocus(
    values: LiveRmValues,
    actualWeight: String,
    onWeight: (String) -> Unit,
    onPercent: (Double) -> Unit,
    onEstimatedReps: (Int) -> Unit,
    onReference: (Double) -> Unit,
    prLabel: String?,
    fallbackWeight: String?,
    modeButton: @Composable () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val referenceLabel = values.oneRm?.let { "Tu referencia: ${it.toLiveNumberString()} kg de 1RM" } ?: "Sin referencia de 1RM o PR"
    if (editing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prescripción ${if (values.usesTrainingMax) "%TM" else "%RM"}", Modifier.weight(1f), style = KpknType.bodyStrong, color = WizardColors.text)
            TextButton(onClick = { editing = false }) { Text("Volver", color = WizardColors.text) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                ModeLabel("REPS EST.")
                LiveModeNumberField(values.estimatedReps?.toString().orEmpty(), { it.toIntOrNull()?.takeIf { n -> n > 0 }?.let(onEstimatedReps) }, "live-rm-reps", "Repeticiones estimadas", Modifier.fillMaxWidth(), enabled = values.oneRm != null, placeholder = "—", sizeSp = 22, preserveEntryWhileFocused = true)
            }
            Column(Modifier.weight(1f)) {
                ModeLabel(if (values.usesTrainingMax) "%TM" else "%RM")
                LiveModeNumberField(values.percent.toLivePercentString(), { it.replace(',', '.').toDoubleOrNull()?.takeIf { n -> n.isFinite() && n > 0 }?.let(onPercent) }, "live-rm-percent", "Porcentaje de referencia", Modifier.fillMaxWidth(), sizeSp = 22, preserveEntryWhileFocused = true)
            }
            Column(Modifier.weight(1f)) {
                ModeLabel("PESO · kg")
                LiveModeNumberField(actualWeight, onWeight, "live-rm-weight", "Peso enlazado en kg", Modifier.fillMaxWidth(), placeholder = values.weight?.toLiveNumberString() ?: fallbackWeight ?: "—", sizeSp = 22)
            }
        }
        Slider(value = values.percent.toFloat(), onValueChange = { onPercent(it.toDouble()) }, valueRange = minOf(40f, values.percent.toFloat())..maxOf(100f, values.percent.toFloat()),
            colors = SliderDefaults.colors(thumbColor = SymbolPalette.energia, activeTrackColor = SymbolPalette.energia,
                inactiveTrackColor = SymbolPalette.energia.copy(alpha = .16f), activeTickColor = KpknSurface.canvas,
                inactiveTickColor = SymbolPalette.energia.copy(alpha = .4f)),
            modifier = Modifier.height(44.dp).testTag("live-rm-slider"))
        ModeLabel("REFERENCIA 1RM · kg")
        LiveModeNumberField(values.oneRm?.toLiveNumberString().orEmpty(), { it.replace(',', '.').toDoubleOrNull()?.takeIf { n -> n.isFinite() && n > 0 }?.let(onReference) }, "live-rm-reference", "Referencia de 1RM en kg", Modifier.fillMaxWidth(), preserveEntryWhileFocused = true)
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ModeLabel("CARGA UTILIZADA · kg")
                LiveModeNumberField(actualWeight, onWeight, "live-rm-weight", "Peso enlazado en kg", Modifier.fillMaxWidth(), placeholder = values.weight?.toLiveNumberString() ?: fallbackWeight ?: "—", sizeSp = 38)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { editing = true }, modifier = Modifier.testTag("live-rm-edit"), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("${values.percent.toLivePercentString()} %", style = KpknType.metricM.copy(fontSize = 23.sp), color = Color(0xFFF49A6E))
                }
                Text(if (values.usesTrainingMax) "del TM" else "del 1RM", style = KpknType.caption.copy(fontSize = 10.sp), color = WizardColors.textMuted)
            }
        }
        Text("Calculado: ${values.weight?.toLiveNumberString() ?: "—"} kg · ${values.estimatedReps ?: "—"} reps est.",
            Modifier.fillMaxWidth().clickable { editing = true }, style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = WizardColors.textMuted)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(referenceLabel, Modifier.clickable { editing = true }, style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 14.sp), color = WizardColors.textMuted)
            prLabel?.let { Text(it, style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 14.sp), color = WizardColors.textMuted) }
            values.trainingMax?.let { Text("TM: ${it.toLiveNumberString()} kg", style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 14.sp), color = WizardColors.textMuted) }
        }
        if (editing) modeButton()
    }
}
