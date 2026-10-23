package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.workout.isSetDone
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.completionKeysForSet
import com.example.kpkn.ui.design.*
import java.util.Locale

/** B4: records one actual series; the host owns persistence, side identity and advancing. */
@Composable
internal fun UltraFastRecordSheet(
    exercise: Exercise,
    completedSets: Map<String, CompletedSet>,
    currentSetIndex: Int,
    busy: Boolean,
    onTechnique: (SeriesTechnique) -> Unit,
    onRecord: (weight: Double, reps: Int, rir: Int?) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    val current = exercise.sets.getOrNull(currentSetIndex)
    val mode = current?.resolveUnitModeV2(exercise.trainingMode) ?: exercise.trainingMode.toUnitModeV2()
    val supported = current != null && !exercise.isCardio && mode == UnitModeV2.REPS
    var weightText by remember(exercise.id, currentSetIndex, current?.id) { mutableStateOf(current?.weight?.let(::ultraNumber).orEmpty()) }
    var repsText by remember(exercise.id, currentSetIndex, current?.id) {
        mutableStateOf((current?.targetRepsRange?.max ?: current?.targetReps ?: current?.plannedTargetV2?.toInt())?.toString().orEmpty())
    }
    // A planned RIR is never a report. Even a tap on the minimum must be explicit.
    var rir by remember(exercise.id, currentSetIndex, current?.id) { mutableStateOf<Int?>(null) }
    val weight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val reps = repsText.toIntOrNull()?.takeIf { it >= 0 }
    val finished = current != null && exercise.isSetDone(completedSets, currentSetIndex)
    val techniques = listOf(SeriesTechnique.NORMAL, SeriesTechnique.DROPSET, SeriesTechnique.REST_PAUSE)
    val technique = when { current?.isDropSet == true -> SeriesTechnique.DROPSET; current?.isRestPause == true -> SeriesTechnique.REST_PAUSE; else -> SeriesTechnique.NORMAL }
    val pending = exercise.sets.indices.count { !exercise.isSetDone(completedSets, it) }
    HeaderSheet(onDismiss, dismissible = !busy) { close ->
        HeaderSheetFrame("Ultrarrápido", footer = {
            if (!supported) HeaderSheetAction("Volver a la sesión", close, Modifier.fillMaxWidth(), primary = true)
            else {
                Text(error ?: if (rir == null) "Marca tu esfuerzo antes de registrar." else "RIR " + rir,
                    style = KpknType.caption, color = if (error != null) WizardColors.danger else WizardColors.textMuted)
                HeaderSheetAction(if (busy) "Registrando…" else "Registrar S" + (currentSetIndex + 1), {
                    if (!busy && !finished && weight != null && reps != null && rir != null) onRecord(weight, reps, rir)
                }, Modifier.fillMaxWidth().testTag("ultra-record-submit"), primary = true,
                    enabled = !busy && !finished && weight != null && reps != null && rir != null)
            }
        }) {
            Text(exercise.name + " · " + pending + " series pendientes", style = KpknType.caption, color = WizardColors.textMuted)
            if (!supported) {
                Text("Usa la tarjeta de serie para registrar este tipo de ejercicio.", style = KpknType.body, color = WizardColors.text)
            } else {
                LiveCompactSegments(listOf("Normal", "Drop-set", "Rest-pause"), techniques.indexOf(technique),
                    { if (!busy) onTechnique(techniques[it]) }, enabled = !busy)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF111111))) {
                    exercise.sets.forEachIndexed { index, set ->
                        val keys = exercise.completionKeysForSet(index)
                        val records = keys.mapNotNull { completedSets[it] }
                        val done = exercise.isSetDone(completedSets, index)
                        val skipped = records.isNotEmpty() && records.all { it.skipped }
                        Row(Modifier.fillMaxWidth().testTag("ultra-record-row-" + index)
                            .background(if (done && !skipped) Color(0xFF43D18C).copy(alpha = .07f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("S" + (index + 1), style = KpknType.caption, color = WizardColors.textMuted)
                            Column(Modifier.weight(1f)) {
                                val dose = if (records.isNotEmpty()) records.joinToString(" / ") { ultraNumber(it.weight) + " × " + it.reps }
                                    else ultraNumber(set.weight ?: 0.0) + " × " + (set.targetRepsRange?.format() ?: set.targetReps?.toString() ?: "—")
                                Text(dose, style = KpknType.metricM.copy(fontSize = 23.sp, lineHeight = 27.sp), color = WizardColors.text)
                                if (!done && (set.isDropSet || set.isRestPause)) Text(if (set.isDropSet) "Drop-set" else "Rest-pause", style = KpknType.caption, color = WizardColors.textMuted)
                            }
                            Text(when { skipped -> "Saltada"; done -> "Hecha"; index == currentSetIndex -> "En curso"; else -> "Pendiente" },
                                style = KpknType.caption, color = if (done && !skipped) Color(0xFF43D18C) else WizardColors.textMuted)
                        }
                    }
                }
                Text("S" + (currentSetIndex + 1) + " · AJUSTA ANTES DE REGISTRAR", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UltraRecordValue("Carga (kg)", weightText, { weightText = it }, { direction -> weightText = ultraNumber(((weight ?: 0.0) + direction * 2.5).coerceAtLeast(0.0)) }, busy, Modifier.weight(1f).testTag("ultra-record-weight"), true)
                    UltraRecordValue("Reps", repsText, { repsText = it.filter(Char::isDigit) }, { direction -> repsText = ((reps ?: 0) + direction).coerceAtLeast(0).toString() }, busy, Modifier.weight(1f).testTag("ultra-record-reps"), false)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Esfuerzo", style = KpknType.caption, color = WizardColors.textMuted)
                    UltraRecordValue("RIR", rir?.toString().orEmpty(), { text -> rir = text.toIntOrNull()?.takeIf { it >= 0 } },
                        { direction -> rir = if (rir == null) 0 else (rir!! + direction).coerceAtLeast(0) }, busy, Modifier.weight(1f).testTag("ultra-record-rir"), false)
                }
            }
        }
    }
}

private fun ultraNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

@Composable
private fun UltraRecordValue(label: String, value: String, onValue: (String) -> Unit, onStep: (Int) -> Unit, busy: Boolean, modifier: Modifier, decimal: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = KpknType.caption, color = WizardColors.textMuted)
        Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A1A)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(-1, 1).forEachIndexed { index, direction ->
                if (index == 1) BasicTextField(value, onValue, Modifier.weight(1f).semantics { contentDescription = label },
                    enabled = !busy, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                    textStyle = KpknType.bodyStrong.copy(fontSize = 17.sp, textAlign = TextAlign.Center, color = WizardColors.text),
                    cursorBrush = SolidColor(WizardColors.text), decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (value.isBlank()) Text("—", style = KpknType.bodyStrong, color = WizardColors.textMuted)
                            inner()
                        }
                    })
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF292929))
                    .semantics { contentDescription = (if (direction < 0) "Menos " else "Más ") + label }
                    .clickable(enabled = !busy, role = Role.Button) { onStep(direction) }, contentAlignment = Alignment.Center) {
                    Text(if (direction < 0) "−" else "+", style = KpknType.bodyStrong.copy(fontSize = 21.sp), color = WizardColors.text)
                }
            }
        }
    }
}
