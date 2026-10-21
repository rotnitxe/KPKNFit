package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calibration.CalibrationDecisionType
import com.example.kpkn.screens.workout.*

/** V7 occupies the existing card, with a scrollable body and fixed, accessible actions. */
@Composable
internal fun LiveCalibrationResult(
    state: WorkoutUiState, exercise: Exercise, completed: CompletedSet,
    record: CalibrationDecisionRecord?, program: LiveAmrapProgramPreview,
    onResolve: (String, CalibrationStatus) -> Unit, onContinue: () -> Unit, onEdit: () -> Unit,
) {
    val ink = Color(0xFFF2EEE6)
    val muted = ink.copy(alpha = .65f)
    val accent = Color(0xFFF49A6E)
    val d = record?.decision
    val resolution = record?.let { r -> when (r.status) {
        CalibrationStatus.APPLIED -> when {
            r.decision.scope == CalibrationScope.NEXT_SESSIONS -> "Propuesta guardada para la próxima sesión de este ejercicio"
            r.appliedSetIds.isEmpty() -> "Referencia aceptada · sin cambios de carga"
            else -> "Cargas aplicadas · calibrada"
        }
        CalibrationStatus.KEPT -> "Conservaste las cargas del plan"
        CalibrationStatus.REVERTED -> "Revertida · cargas pendientes restauradas"
        else -> null
    } }
    val busy = state.calibrationActionId != null || state.recordingSetKey != null
    val minimum = completed.amrapMinimumReps
    val sourceIndex = completed.setIndex ?: 0
    val pending = d?.let { pendingCalibrationSets(state, exercise, sourceIndex, it.context) }.orEmpty()
    val compatible = pending.filter { !it.isCompleted && !it.isManuallyFixed && it.context == d?.context }
        .filter { set -> record?.pendingPrescriptions?.get(set.setId)?.let {
            it.targetReps == set.targetReps && it.targetRir == set.targetRir
        } ?: true }.map { it.setId }.toSet()
    val positions = d?.affectedSets.orEmpty().filter { it.setId in compatible }.mapNotNull { change ->
        exercise.sets.indexOfFirst { calibrationSetId(it, completed.side) == change.setId }.takeIf { it >= 0 }?.plus(1)
    }
    val applyLabel = when {
        d?.scope == CalibrationScope.NEXT_SESSIONS -> "Guardar propuesta"
        positions.isEmpty() -> "Aceptar referencia"
        positions.size > 1 && positions.zipWithNext().all { (a,b) -> b == a + 1 } -> "Aplicar a S${positions.first()}–S${positions.last()}"
        else -> "Aplicar a ${positions.joinToString(", ") { "S$it" }}"
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF111111)).padding(horizontal = 16.dp, vertical = 10.dp)
        .testTag("calibration-result"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (d == null) "AMRAP · registrada" else "Calibrador · S${sourceIndex + 1}",
                Modifier.weight(1f).padding(top = 12.dp), color = muted, fontSize = 12.sp)
            TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 44.dp).testTag("calibration-edit")) {
                Text("Editar", color = ink)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("calibration-result-body"),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            state.calibrationError?.takeIf { it.decisionId == record?.id }?.let {
                Text(it.message, color = Color(0xFFEF6F64), fontSize = 12.sp, modifier = Modifier.testTag("calibration-error"))
            }
            resolution?.let { Text(it, color = ink, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("calibration-resolution")) }
            if (d == null) {
                Text(when {
                    minimum == null -> "${completed.reps} reps · sin mínimo"
                    completed.reps < minimum -> "Mínimo no alcanzado · ${completed.reps}/$minimum reps"
                    completed.reps == minimum -> "Mínimo cumplido · ${completed.reps} reps"
                    else -> "Mínimo superado · ${completed.reps}/$minimum reps"
                }, color = ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text("Registrado en tu historial y récords", color = muted, fontSize = 12.sp)
                Text(program.message, color = accent, fontSize = 13.sp, lineHeight = 18.sp)
                program.proposals.forEach { Text(it.explanation, color = muted, fontSize = 12.sp, lineHeight = 16.sp) }
            } else {
                Text(when {
                    d.type == CalibrationDecisionType.NOT_APPLIED -> "Calibración no aplicada"
                    d.referenceKg == null && d.capacityKg != null -> "Primera referencia"
                    d.type == CalibrationDecisionType.HOLD -> "Mantener las cargas"
                    d.type == CalibrationDecisionType.LOWER || d.type == CalibrationDecisionType.LOW_REPS_DROP -> "Bajar las cargas"
                    d.scope == CalibrationScope.NEXT_SESSIONS -> "Referencia para próximas sesiones"
                    else -> "Subir las cargas"
                }, color = ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                d.capacityKg?.let { Text("Capacidad ≈ ${finishNumber(it)} kg", color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                Text(d.referenceKg?.let { "Referencia ${finishNumber(it)} kg · ${when(d.referenceSource.name) {
                    "ACCEPTED_CALIBRATION" -> "calibración aceptada"; "HISTORY" -> "historial"; "DECLARED_PR" -> "PR declarado"; else -> "1RM declarado" }}" }
                    ?: "Sin referencia previa", color = muted, fontSize = 12.sp)
                Text(d.reason, color = accent, fontSize = 12.sp, lineHeight = 16.sp)
                d.improvementPercent?.let { Text("Cambio medido ${if(it >= 0) "+" else ""}${finishNumber(it)} % · ajuste ${finishNumber((d.factor - 1) * 100)} %",
                    color = muted, fontSize = 11.sp, lineHeight = 15.sp) }
                d.note?.let { Text(it, color = muted, fontSize = 11.sp, lineHeight = 15.sp) }
                d.affectedSets.forEach { change ->
                    val index = exercise.sets.indexOfFirst { calibrationSetId(it, completed.side) == change.setId }
                    if (index >= 0) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val set = pending.firstOrNull { it.setId == change.setId }
                        val key = workoutSetKey(exercise.id, index, completed.side)
                        val actual = state.completedSets[key]?.let { it.originalLoad ?: it.weight }
                            ?: state.manualLoadOverrides[key] ?: state.manualLoadOverrides[workoutSetKey(exercise.id, index)]
                            ?: state.setDrafts[key]?.takeIf { set?.isManuallyFixed == true }?.weightText?.replace(',', '.')?.toDoubleOrNull()
                        val proposed = when (record.status) {
                            CalibrationStatus.PROPOSED -> if (change.setId in compatible) change.afterKg else change.beforeKg
                            CalibrationStatus.APPLIED -> if (change.setId in record.appliedSetIds) change.afterKg else change.beforeKg
                            else -> change.beforeKg
                        }
                        val before = if (record.status == CalibrationStatus.REVERTED && change.setId in record.appliedSetIds) change.afterKg else change.beforeKg
                        Column {
                            Text("S${index + 1}", color = ink, fontSize = 13.sp)
                            if (change.setId !in compatible) {
                                Text(when { set?.isCompleted == true -> "Hecha · se conserva"
                                    set?.isManuallyFixed == true -> "Fijada · se conserva"
                                    else -> "Receta editada · se conserva" }, color = muted, fontSize = 10.sp)
                            }
                        }
                        Text("${finishNumber(before)} → ${finishNumber(actual ?: proposed)} kg",
                            color = ink, fontSize = 13.sp)
                    }
                }
                if (d.scope == CalibrationScope.NEXT_SESSIONS && program.hasRule) Text(program.message, color = muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        if (record?.status == CalibrationStatus.PROPOSED) {
            if (d?.type != CalibrationDecisionType.NOT_APPLIED) Button(onClick = { onResolve(record.id, CalibrationStatus.APPLIED) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("calibration-apply"),
                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = Color.Black), shape = RoundedCornerShape(24.dp)) {
                Text(applyLabel, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onResolve(record.id, CalibrationStatus.KEPT) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("calibration-keep")) { Text("Conservar", color = ink) }
        } else {
            if (record?.status == CalibrationStatus.APPLIED) OutlinedButton(onClick = { onResolve(record.id, CalibrationStatus.REVERTED) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("calibration-revert")) { Text("Revertir", color = ink) }
            Button(onClick = onContinue, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("calibration-continue"),
                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = Color.Black)) { Text("Continuar") }
        }
    }
}
