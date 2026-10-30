package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.design.KpknType

/**
 * `ed-serie-amrap` · B3b/CAL4. El propósito vive en cada serie, también en recetas cuyo modo de ejercicio es RM. El campo es «Reps mín.»
 * y a su lado va la etiqueta AMRAP, que al tocarla alterna «AMRAP calibrador» (como en la serie real). Sin intensidad: el esfuerzo es
 * llegar al límite. Con calibrador salen debajo su alcance, lo que ajustará y sus requisitos.
 */
@Composable
internal fun AmrapSetFocus(
    exercise: Exercise,
    set: ExerciseSet,
    index: Int,
    fixedUnilateralSide: String? = null,
    /** Abre la hoja de la carga inteligente (la cifra de carga es su botón). */
    onOpenSmartLoad: (() -> Unit)? = null,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
) {
    val previewExercise = if (fixedUnilateralSide == null) exercise else exercise.copy(sets = exercise.sets.map { current ->
        val side = if (fixedUnilateralSide == "L") current.leftTarget else current.rightTarget
        current.copy(weight = side?.weight)
    })
    val previewSet = previewExercise.sets.getOrNull(index)?.takeIf { it.id == set.id } ?: set
    val calibrator = set.purpose == SetPurpose.CALIBRATOR
    var previousMinimum by rememberSaveable(set.id) { mutableStateOf(set.amrapMinimumReps ?: set.targetReps) }
    val minimum = set.amrapMinimumReps
    val shownWeight = previewSet.weight?.takeIf { it > 0.0 } ?: calculateSuggestedLoad(previewExercise, previewSet)
    fun setMinimum(value: Int?) {
        if (minimum != null) previousMinimum = minimum
        onUpdate { it.copy(amrapMinimumReps = value) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EdLoadSection(
            mode = set.loadModeV2 ?: LoadModeV2.LOAD,
            weightKg = shownWeight,
            onChangeMode = { mode -> onUpdate { it.copy(loadModeV2 = mode) } },
            onOpenSmartLoad = onOpenSmartLoad,
        )
        EdSection(label = "Reps mín.", hint = "tantas como puedas") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdStepperField(
                    text = minimum?.toString() ?: "—",
                    label = "Repeticiones mínimas",
                    onStep = { direction ->
                        when {
                            minimum == null && direction > 0 -> setMinimum((previousMinimum ?: 1).coerceAtLeast(1))
                            minimum == null -> Unit
                            minimum + direction < 1 -> setMinimum(null)
                            else -> setMinimum((minimum + direction).coerceAtMost(100))
                        }
                    },
                    canDecrement = minimum != null,
                    canIncrement = (minimum ?: 0) < 100,
                    keyboard = KeyboardType.Number,
                    onCommitText = { input -> input.safeIntOrNull()?.takeIf { it in 1..100 }?.let { setMinimum(it) } },
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                    minusDescription = "Reducir mínimo AMRAP",
                    plusDescription = "Aumentar mínimo AMRAP",
                )
                val shape = RoundedCornerShape(26.dp)
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(shape)
                        .background(EdColors.field, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.122f), shape)
                        .clickable(role = Role.Switch) {
                            onUpdate {
                                it.copy(
                                    purpose = if (calibrator) SetPurpose.AMRAP else SetPurpose.CALIBRATOR,
                                    calibrationScope = if (calibrator) null else it.calibrationScope ?: CalibrationScope.SESSION,
                                )
                            }
                        }
                        .semantics { stateDescription = if (calibrator) "Calibrador" else "AMRAP" }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (calibrator) "AMRAP calibrador" else "AMRAP", style = edText(13f, FontWeight.SemiBold, EdColors.ink, 16f), maxLines = 1)
                }
            }
        }
        if (calibrator) {
            val future = set.calibrationScope == CalibrationScope.NEXT_SESSIONS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Dos decisiones completas: si no caben en una línea, la segunda pasa a la siguiente (nunca se cortan).
                EdFlexRow(modifier = Modifier.fillMaxWidth(), gap = 8.dp, lineGap = 8.dp) {
                    EdNumberPill(
                        text = "Esta sesión",
                        on = !future,
                        onClick = { onUpdate { it.copy(calibrationScope = CalibrationScope.SESSION) } },
                    )
                    EdNumberPill(
                        text = "Próximas sesiones",
                        on = future,
                        onClick = { onUpdate { it.copy(calibrationScope = CalibrationScope.NEXT_SESSIONS) } },
                    )
                }
                EdHelpText(calibrationPreview(previewExercise, previewSet, index), color = EdColors.ink)
                EdHelpText("Para calibrar: carga externa mayor que 0, repeticiones completas y RIR 0–1 o fallo declarado.")
                if (set.loadModeV2 == LoadModeV2.BODYWEIGHT || set.loadModeV2 == LoadModeV2.ASSISTED) {
                    EdHelpText("Con peso corporal o asistencia se registra, pero no se calibra en kg.", color = WizardColors.danger)
                }
            }
        }
    }
}

internal fun calibrationPreview(exercise: Exercise, set: ExerciseSet, index: Int): String {
    val sourceMode = set.loadModeV2 ?: LoadModeV2.LOAD
    if (sourceMode == LoadModeV2.BODYWEIGHT || sourceMode == LoadModeV2.ASSISTED || (set.weight ?: 0.0) <= 0.0) return "Sin propuesta en kg para esta serie."
    if (set.calibrationScope == CalibrationScope.NEXT_SESSIONS) return "Propondrá una referencia para próximas sesiones de este ejercicio."
    val targets = exercise.sets.withIndex().filter { (i, candidate) -> i > index && !candidate.isEmptySlot && candidate.completedReps == null && candidate.completedDuration == null &&
        (candidate.loadModeV2 ?: LoadModeV2.LOAD) == sourceMode && (candidate.weight ?: 0.0) > 0.0 &&
        candidate.machineBrand == set.machineBrand && candidate.unitModeV2 == set.unitModeV2 &&
        candidate.setupId == set.setupId && candidate.defaultSetupProfileIdV3 == set.defaultSetupProfileIdV3 &&
        candidate.loadQuantityConvention == set.loadQuantityConvention }
    if (targets.isEmpty()) return "No hay series siguientes compatibles."
    val consecutive = targets.zipWithNext().all { (a, b) -> b.index == a.index + 1 }
    val names = if (targets.size > 1 && consecutive) "S${targets.first().index + 1}–S${targets.last().index + 1}"
        else targets.joinToString(" · ") { "S${it.index + 1}" }
    val weights = targets.mapNotNull { it.value.weight }.distinct().joinToString(" / ") { formatEditableNumber(it).replace('.', ',') }
    return "Ajustará $names · hoy $weights kg"
}
