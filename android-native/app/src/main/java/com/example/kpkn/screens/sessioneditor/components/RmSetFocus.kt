package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimateRepsFromPercent1RM
import com.example.kpkn.domain.calculations.percentLoadReferenceKg
import com.example.kpkn.domain.calculations.percentOfOneRm
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.rmSetLoadKg
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.ui.design.KpknType

/**
 * `ed-serie-rm` · B3: la carga es un porcentaje de tu máximo. El campo «% del RM» y el deslizador (45–100 %) mueven lo mismo y la cifra de
 * carga y las reps máximas estimadas se recalculan. El TM nunca se trata como un 1RM: con «% del TM» el propio TM se edita debajo.
 */
@Composable
internal fun RmSetFocus(
    exercise: Exercise,
    set: ExerciseSet,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onUpdateExercise: (((Exercise) -> Exercise) -> Unit)?,
    fixedUnilateralSide: String? = null,
    /** Abre la hoja de la carga inteligente (la cifra de carga es su botón). */
    onOpenSmartLoad: (() -> Unit)? = null,
) {
    val usesTm = set.loadBasis == LoadBasis.PERCENT_TM
    val reference = percentLoadReferenceKg(exercise, set)
    val percent = set.targetPercentageRM
    val oneRmPercent = percentOfOneRm(exercise, set)
    val estimatedReps = oneRmPercent?.let(::estimateRepsFromPercent1RM)
    val external = (set.loadModeV2 ?: LoadModeV2.LOAD) == LoadModeV2.LOAD
    val weight = if (external) reference?.let { set.weight ?: rmSetLoadKg(exercise, set) }
        else set.weight ?: calculateSuggestedLoad(exercise, set)
    val shownWeight = if (fixedUnilateralSide != null) {
        val side = if (fixedUnilateralSide == "L") set.leftTarget else set.rightTarget
        side?.weight ?: weight
    } else weight
    val minimumPercent = minOf(45f, (percent ?: 45.0).toFloat()).coerceAtLeast(1f)
    val maximumPercent = maxOf(100f, (percent ?: 100.0).toFloat())
    var slider by remember(set.id, percent) { mutableStateOf((percent ?: 75.0).toFloat()) }
    fun commitPercent(value: Double?) {
        if (value == null || !value.isFinite() || value <= 0.0) return
        // %TM puede superar 100; la normalización existente mantiene ese contrato.
        val bounded = if (usesTm) value else value.coerceIn(40.0, 100.0)
        // La prescripción de reps/RIR de una receta se conserva aunque RM no la exponga como entrada de esfuerzo.
        onUpdate { it.copy(targetPercentageRM = bounded, targetRPE = null, intensityMode = null, isFailure = false) }
    }
    val label = if (usesTm) "% del TM" else "% del RM"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EdLoadSection(
            mode = set.loadModeV2 ?: LoadModeV2.LOAD,
            weightKg = shownWeight,
            onChangeMode = { mode -> onUpdate { it.copy(loadModeV2 = mode) } },
            onOpenSmartLoad = onOpenSmartLoad,
            percentSuffix = percent?.let { "${edNumber(it)} % ${if (usesTm) "TM" else "RM"}" },
        )
        EdSection(
            label = label,
            hint = estimatedReps?.let { reps -> "≈ $reps ${if (reps == 1) "rep" else "reps"} máx. al ${edNumber(percent ?: slider.toDouble())} %" },
        ) {
            EdStepperField(
                text = percent?.let { edNumber(it) } ?: "—",
                label = label,
                unit = "%",
                onStep = { direction -> commitPercent(((percent ?: 75.0) + direction).coerceIn(minimumPercent.toDouble(), maximumPercent.toDouble())) },
                canDecrement = (percent ?: 75.0) > minimumPercent,
                canIncrement = (percent ?: 75.0) < maximumPercent,
                keyboard = KeyboardType.Decimal,
                onCommitText = { input -> commitPercent(input.safeDoubleOrNull()) },
                minusDescription = "Menos porcentaje",
                plusDescription = "Más porcentaje",
            )
            EdSlider(
                value = slider,
                onValueChange = { slider = kotlin.math.round(it) },
                onValueChangeFinished = { commitPercent(slider.toDouble()) },
                valueRange = minimumPercent..maximumPercent,
                steps = (maximumPercent - minimumPercent).toInt().coerceAtLeast(1) - 1,
                description = label,
                startLabel = "${minimumPercent.toInt()} %",
                endLabel = "${maximumPercent.toInt()} %",
                enabled = reference != null,
            )
            if (reference == null) {
                Text(if (usesTm) "Falta el TM" else "Falta el 1RM", style = KpknType.caption, color = WizardColors.danger)
            }
        }
        if (usesTm && onUpdateExercise != null) {
            EdSection(label = "Referencia · TM", hint = "kg") {
                EdStepperField(
                    text = exercise.referenceTrainingMax?.let { edNumber(it) } ?: "—",
                    label = "TM (kg)",
                    unit = "kg",
                    onStep = { direction ->
                        val next = ((exercise.referenceTrainingMax ?: 0.0) + direction * 2.5).coerceAtLeast(0.0)
                        onUpdateExercise { it.copy(referenceTrainingMax = next.takeIf { kg -> kg > 0.0 }) }
                    },
                    canDecrement = (exercise.referenceTrainingMax ?: 0.0) > 0.0,
                    keyboard = KeyboardType.Decimal,
                    onCommitText = { input ->
                        onUpdateExercise { it.copy(referenceTrainingMax = input.safeDoubleOrNull()?.takeIf { kg -> kg.isFinite() && kg > 0.0 }) }
                    },
                    minusDescription = "Menos TM",
                    plusDescription = "Más TM",
                )
                if (exercise.reference1RM == null) {
                    Text("Sin 1RM no se estiman las repeticiones a partir del TM.", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }
    }
}
