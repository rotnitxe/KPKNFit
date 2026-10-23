package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** B3b/CAL4. El propósito vive en cada serie, también en recetas cuyo modo de ejercicio es RM. */
@Composable
internal fun AmrapSetFocus(exercise: Exercise, set: ExerciseSet, index: Int, fixedUnilateralSide: String? = null, onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit) {
    val previewExercise = if (fixedUnilateralSide == null) exercise else exercise.copy(sets = exercise.sets.map { current ->
        val side = if (fixedUnilateralSide == "L") current.leftTarget else current.rightTarget
        current.copy(weight = side?.weight)
    })
    val previewSet = previewExercise.sets.getOrNull(index)?.takeIf { it.id == set.id } ?: set
    val calibrator = set.purpose == SetPurpose.CALIBRATOR
    var showHelp by remember { mutableStateOf(false) }
    var previousMinimum by rememberSaveable(set.id) { mutableStateOf(set.amrapMinimumReps ?: set.targetReps) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetLoadTypeControl(set, onUpdate)
        if (fixedUnilateralSide != null) {
            Text("Lado $fixedUnilateralSide", style = KpknType.caption, color = WizardColors.textMuted)
        }
        Text(previewSet.weight?.let { "${formatEditableNumber(it).replace('.', ',')}\u00a0kg" } ?: "Sin carga programada",
            style = KpknType.metricM, color = WizardColors.text)
        KpknSegmentedControl(options = listOf("AMRAP", "Calibrador"), selectedIndex = if (calibrator) 1 else 0,
            onSelect = { selected -> onUpdate { it.copy(purpose = if (selected == 1) SetPurpose.CALIBRATOR else SetPurpose.AMRAP,
                calibrationScope = if (selected == 1) it.calibrationScope ?: CalibrationScope.SESSION else null) } },
            size = KpknSegmentedSize.Compact, modifier = Modifier.fillMaxWidth())
        Text(if (calibrator) "Mide tu capacidad de hoy y propone cargas para lo que sigue."
            else "Registra tu rendimiento y alimenta la regla de progresión del programa.",
            style = KpknType.caption, color = WizardColors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mínimo", style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
            KpknIconButton(icon = KpknLineIcons.Minus, contentDescription = "Reducir mínimo AMRAP",
                onClick = { set.amrapMinimumReps?.let { minimum -> onUpdate { it.copy(amrapMinimumReps = (minimum - 1).coerceAtLeast(1)) } } })
            Text(set.amrapMinimumReps?.toString() ?: "—", style = KpknType.metricM, color = WizardColors.text)
            KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Aumentar mínimo AMRAP",
                onClick = { onUpdate { it.copy(amrapMinimumReps = (it.amrapMinimumReps ?: 0) + 1) } })
        }
        KpknChip(text = "Sin mínimo", selected = set.amrapMinimumReps == null, onClick = {
            if (set.amrapMinimumReps != null) previousMinimum = set.amrapMinimumReps
            onUpdate { it.copy(amrapMinimumReps = if (it.amrapMinimumReps == null) previousMinimum ?: 1 else null) }
        })
        if (calibrator) {
            val future = set.calibrationScope == CalibrationScope.NEXT_SESSIONS
            AmrapScopeControl(future = future, onSelect = { selected ->
                onUpdate { it.copy(calibrationScope = if (selected == 0) CalibrationScope.SESSION else CalibrationScope.NEXT_SESSIONS) }
            })
            Text(calibrationPreview(previewExercise, previewSet, index), style = KpknType.bodyStrong, color = WizardColors.text)
            Text("Para calibrar: carga externa mayor que 0, repeticiones completas y RIR 0–1 o fallo declarado.",
                style = KpknType.caption, color = WizardColors.textMuted)
            if (set.loadModeV2 == LoadModeV2.BODYWEIGHT || set.loadModeV2 == LoadModeV2.ASSISTED) Text(
                "Con peso corporal o asistencia se registra, pero no se calibra en kg.",
                style = KpknType.caption, color = WizardColors.danger)
        }
        KpknTextAction(text = "¿Qué hace cada uno?", onClick = { showHelp = true })
    }
    if (showHelp) KpknSheet(onDismissRequest = { showHelp = false }) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("¿Qué hace cada uno?", style = KpknType.dialogTitle, color = WizardColors.text)
            Text("AMRAP registra las repeticiones logradas para la regla del programa. Calibrador mide la capacidad del mismo ejercicio y propone ajustar sus cargas compatibles, con opción de conservarlas o revertirlas.",
                style = KpknType.body, color = WizardColors.textMuted)
            KpknTextAction(text = "Listo", onClick = { showHelp = false })
        }
    }
}

/** Conserva el segmentado normal; si ni su letra mínima cabe, muestra las mismas dos decisiones completas. */
@Composable
private fun AmrapScopeControl(future: Boolean, onSelect: (Int) -> Unit) {
    val labels = listOf("Esta sesión", "Próximas sesiones")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // El segmentado compacto reserva 4 dp de pista y 6 dp de texto a cada lado; su mínimo es 11 sp.
    val minimumStyle = KpknType.segment.copy(fontSize = 11.sp, lineHeight = 18.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availablePx = with(density) { ((maxWidth - 8.dp) / 2 - 12.dp).toPx() }
        val widestPx = labels.maxOf { label ->
            measurer.measure(label, minimumStyle, maxLines = 1, softWrap = false).size.width
        }
        if (widestPx <= availablePx) {
            KpknSegmentedControl(options = labels, selectedIndex = if (future) 1 else 0,
                onSelect = onSelect, size = KpknSegmentedSize.Compact, modifier = Modifier.fillMaxWidth())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    LoadTypeCell(text = label, selected = if (future) index == 1 else index == 0,
                        onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth(), testTag = "amrap-scope-$index")
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
    return "Ajustará $names · hoy $weights\u00a0kg"
}
