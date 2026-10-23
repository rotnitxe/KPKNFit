package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.domain.calculations.estimateRepsFromPercent1RM
import com.example.kpkn.domain.calculations.percentLoadKg
import com.example.kpkn.domain.calculations.percentLoadReferenceKg
import com.example.kpkn.domain.calculations.percentOfOneRm
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.rmSetLoadKg
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType

/** B3: tres entradas para una misma prescripción; el TM nunca se trata como un 1RM. */
@Composable
internal fun RmSetFocus(
    exercise: Exercise,
    set: ExerciseSet,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onUpdateExercise: (((Exercise) -> Exercise) -> Unit)?,
    fixedUnilateralSide: String? = null,
) {
    val usesTm = set.loadBasis == LoadBasis.PERCENT_TM
    val reference = percentLoadReferenceKg(exercise, set)
    val percent = set.targetPercentageRM
    val oneRmPercent = percentOfOneRm(exercise, set)
    val estimatedReps = oneRmPercent?.let(::estimateRepsFromPercent1RM)
    val external = (set.loadModeV2 ?: LoadModeV2.LOAD) == LoadModeV2.LOAD
    val weight = if (external) reference?.let { set.weight ?: rmSetLoadKg(exercise, set) }
        else set.weight ?: calculateSuggestedLoad(exercise, set)
    var slider by remember(set.id, percent) { mutableStateOf((percent ?: 75.0).toFloat()) }
    fun commitPercent(value: Double?) {
        if (value == null || !value.isFinite() || value <= 0.0) return
        // %TM puede superar 100; la normalización existente mantiene ese contrato.
        val bounded = if (usesTm) value else value.coerceIn(40.0, 100.0)
        // La prescripción de reps/RIR de una receta se conserva aunque RM no la exponga como entrada de esfuerzo.
        onUpdate { it.copy(targetPercentageRM = bounded, targetRPE = null, intensityMode = null, isFailure = false) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetLoadTypeControl(set = set, onUpdate = onUpdate)
        if (fixedUnilateralSide != null) {
            val side = if (fixedUnilateralSide == "L") set.leftTarget else set.rightTarget
            Text("$fixedUnilateralSide · " + (side?.weight?.let { "${formatEditableNumber(it).replace('.', ',')}\u00a0kg" } ?: "Sin carga programada"),
                style = KpknType.bodyStrong, color = WizardColors.text,
                modifier = Modifier.semantics { contentDescription = "Carga del lado $fixedUnilateralSide" })
            Text("El porcentaje y la referencia se aplican a los dos lados.", style = KpknType.caption, color = WizardColors.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetValueCell(label = "Reps estimadas", value = estimatedReps?.toString().orEmpty(), stateKey = "rm-reps-${set.id}",
                listSkin = true, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                labelMinLines = 2, labelMaxLines = 2, labelOverflow = TextOverflow.Clip,
                enabled = !usesTm || (reference != null && exercise.reference1RM != null),
                onCommit = { input -> input.safeIntOrNull()?.takeIf { it in 1..30 }?.let { reps ->
                    val oneRmValue = estimatePercent1RM(reps)
                    commitPercent(if (usesTm) reference?.let { oneRmValue * exercise.reference1RM!! / it } else oneRmValue)
                } })
            SetValueCell(label = if (usesTm) "% del TM" else "%RM", value = formatEditableNumber(percent),
                stateKey = "rm-percent-${set.id}", listSkin = true, modifier = Modifier.weight(1f),
                labelMinLines = 2, labelMaxLines = 2, labelOverflow = TextOverflow.Clip,
                keyboardType = KeyboardType.Decimal, onCommit = { commitPercent(it.safeDoubleOrNull()) })
            SetValueCell(label = "Peso (kg)", value = formatEditableNumber(weight), stateKey = "rm-weight-${set.id}",
                listSkin = true, modifier = Modifier.weight(1f), labelMinLines = 2, labelMaxLines = 2,
                labelOverflow = TextOverflow.Clip, keyboardType = KeyboardType.Decimal,
                enabled = reference != null && external, onCommit = { input ->
                    input.safeDoubleOrNull()?.takeIf { it > 0.0 }?.let { kg -> commitPercent(reference?.let { kg * 100.0 / it }) }
                })
        }
        val minimumPercent = minOf(45f, (percent ?: 45.0).toFloat()).coerceAtLeast(0.01f)
        Slider(value = slider.coerceIn(minimumPercent, maxOf(100f, slider)),
            onValueChange = { slider = it }, onValueChangeFinished = { commitPercent(slider.toDouble()) },
            valueRange = minimumPercent..maxOf(100f, (percent ?: 100.0).toFloat()), enabled = reference != null,
            colors = inkSliderColors(),
            modifier = Modifier.fillMaxWidth())
        if (reference == null) Text(if (usesTm) "Falta el TM" else "Falta el 1RM",
            style = KpknType.caption, color = WizardColors.danger)
        if (usesTm && exercise.reference1RM == null) Text("Sin 1RM no se estiman las repeticiones a partir del TM.",
            style = KpknType.caption, color = WizardColors.textMuted)
        if (onUpdateExercise != null) RmReferenceControl(exercise, usesTm, onUpdateExercise)
    }
}

@Composable
internal fun SetLoadTypeControl(set: ExerciseSet, onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit) {
    val modes = listOf(LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT, LoadModeV2.LASTRE, LoadModeV2.ASSISTED)
    val labels = listOf("Externa", "Corporal", "Lastre", "Asistido")
    val selected = modes.indexOf(set.loadModeV2 ?: LoadModeV2.LOAD)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = KpknType.segment.copy(fontSize = 13.sp, lineHeight = 18.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = with(density) { labels.maxOf { measurer.measure(it, style).size.width }.toDp() }
        if (maxWidth >= (widest + 12.dp) * 4f + 8.dp) {
            KpknSegmentedControl(options = labels, selectedIndex = selected,
                onSelect = { index -> onUpdate { it.copy(loadModeV2 = modes[index]) } },
                size = KpknSegmentedSize.Compact, modifier = Modifier.fillMaxWidth())
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(0, 1), listOf(2, 3)).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index -> LoadTypeCell(text = labels[index], selected = selected == index,
                    onClick = { onUpdate { it.copy(loadModeV2 = modes[index]) } }, modifier = Modifier.weight(1f), testTag = "load-type-$index") }
            } }
        }
    }
}

@Composable
private fun RmReferenceControl(exercise: Exercise, usesTm: Boolean, onUpdate: ((Exercise) -> Exercise) -> Unit) {
    var fromPr by remember(exercise.id) { mutableStateOf(exercise.prFor1RM != null) }
    var prKg by remember(exercise.id, exercise.prFor1RM) { mutableStateOf(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prReps by remember(exercise.id, exercise.prFor1RM) { mutableStateOf(exercise.prFor1RM?.reps?.toString().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (usesTm) "Referencia · TM" else "Referencia · 1RM o PR", style = KpknType.bodyStrong, color = WizardColors.text)
        if (usesTm) {
            EditorKpknField(label = "TM (kg)", value = formatEditableNumber(exercise.referenceTrainingMax), keyboardType = KeyboardType.Decimal,
                onCommit = { input -> onUpdate { it.copy(referenceTrainingMax = input.safeDoubleOrNull()?.takeIf { kg -> kg.isFinite() && kg > 0.0 }) } })
        } else {
            KpknSegmentedControl(options = listOf("1RM", "PR"), selectedIndex = if (fromPr) 1 else 0,
                onSelect = { fromPr = it == 1 }, size = KpknSegmentedSize.Compact)
            if (!fromPr) EditorKpknField(label = "1RM (kg)", value = formatEditableNumber(exercise.reference1RM), keyboardType = KeyboardType.Decimal,
                onCommit = { input -> onUpdate { it.copy(reference1RM = input.safeDoubleOrNull()?.takeIf { kg -> kg.isFinite() && kg > 0.0 }, prFor1RM = null) } })
            else {
                fun commitPr(kg: Double?, reps: Int?) {
                    if (kg == null || !kg.isFinite() || kg <= 0.0 || reps == null || reps !in 1..50) return
                    onUpdate { it.copy(prFor1RM = PrReference(kg, reps), reference1RM = calculateHybrid1RM(kg, reps)) }
                }
                EditorKpknField(label = "PR kg", value = prKg, keyboardType = KeyboardType.Decimal,
                    onCommit = { prKg = it; commitPr(prKg.safeDoubleOrNull(), prReps.safeIntOrNull()) })
                EditorKpknField(label = "PR reps", value = prReps, keyboardType = KeyboardType.Number,
                    onCommit = { prReps = it; commitPr(prKg.safeDoubleOrNull(), prReps.safeIntOrNull()) })
            }
        }
    }
}
