package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
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
            r.apre != null -> if (r.appliedSetIds.isEmpty()) "APRE · S4 se conserva" else "APRE · carga de S4 aplicada"
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
        record?.apre != null -> "Aplicar a S4"
        d?.scope == CalibrationScope.NEXT_SESSIONS -> "Guardar propuesta"
        positions.isEmpty() -> "Aceptar referencia"
        positions.size > 1 && positions.zipWithNext().all { (a,b) -> b == a + 1 } -> "Aplicar a S${positions.first()}–S${positions.last()}"
        else -> "Aplicar a ${positions.joinToString(", ") { "S$it" }}"
    }
    // `resultCard` del prototipo 02: «CALIBRACIÓN · Registrada», RESULTADO con las repeticiones y la carga, la propuesta en Syne con su
    // explicación, «Mantener el plan» y «Corregir registro»; en el pie, «Aplicar propuesta» (o «Continuar» si ya está resuelta).
    val faint = Color(0xFF92908A)
    val compact = liveSessionCardHeight() < 440.dp
    val inset = if (compact) 15.dp else 18.dp
    val effort = completed.rir?.let { "RIR $it" } ?: completed.rpe?.let { "RPE ${finishNumber(it)}" }
    val proposalKg = when {
        record?.apre != null && d?.type != CalibrationDecisionType.NOT_APPLIED -> record.apre.set4LoadKg
        else -> d?.affectedSets?.firstOrNull { it.setId in compatible }?.afterKg ?: d?.capacityKg
    }
    val kicker = when { record?.apre != null -> "APRE"; d == null -> "AMRAP"; else -> "CALIBRACIÓN" }
    val status = when (record?.status) {
        CalibrationStatus.APPLIED -> "Aplicada"
        CalibrationStatus.KEPT -> "Plan conservado"
        CalibrationStatus.REVERTED -> "Revertida"
        else -> "Registrada"
    }
    val syne = { size: Int, weight: FontWeight -> androidx.compose.ui.text.TextStyle(fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display,
        fontWeight = weight, fontSize = size.sp, fontFeatureSettings = "'tnum' 1") }
    val body = { size: Int -> androidx.compose.ui.text.TextStyle(fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.body,
        fontSize = size.sp, lineHeight = (size * 1.6f).sp) }
    Column(Modifier.fillMaxSize().background(Color(0xFF111111)).testTag("calibration-result")) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = inset, end = inset, top = if (compact) 9.dp else 13.dp)) {
            Row(Modifier.fillMaxWidth().height(if (compact) 27.dp else 29.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(kicker, Modifier.weight(1f), style = body(9).copy(letterSpacing = .65.sp, lineHeight = 12.sp), color = muted)
                Text(status, style = body(9).copy(lineHeight = 12.sp), color = muted, modifier = Modifier.testTag("calibration-status"))
            }
            Column(Modifier.weight(1f).fillMaxWidth().testTag("calibration-result-body"),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp, androidx.compose.ui.Alignment.CenterVertically)) {
                state.calibrationError?.takeIf { it.decisionId == record?.id }?.let {
                    Text(it.message, color = Color(0xFFFFB5AD), style = body(10), modifier = Modifier.testTag("calibration-error"))
                }
                Text("RESULTADO", style = body(9).copy(letterSpacing = 1.sp, lineHeight = 12.sp), color = faint)
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    append(completed.reps.toString())
                    withStyle(androidx.compose.ui.text.SpanStyle(fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.body,
                        fontSize = 12.sp, fontWeight = FontWeight.Normal)) { append(" reps") }
                }, style = syne(43, FontWeight.ExtraBold), color = ink)
                Text(listOfNotNull("${finishNumber(completed.weight)} kg", effort).joinToString(" · "), Modifier.fillMaxWidth(), style = body(12), color = muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (d == null) {
                    Text(when {
                        minimum == null -> "Sin mínimo"
                        completed.reps < minimum -> "Mínimo no alcanzado · ${completed.reps}/$minimum"
                        completed.reps == minimum -> "Mínimo cumplido"
                        else -> "Mínimo superado · ${completed.reps}/$minimum"
                    }, style = body(12), color = muted)
                    if (program.message.isNotBlank()) Text(program.message, Modifier.fillMaxWidth(), style = body(12), color = muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    proposalKg?.takeIf { it.isFinite() && it > 0.0 }?.let {
                        Text("${finishNumber(it)} kg", Modifier.padding(vertical = 3.dp).testTag("apre-set4-result"), style = syne(28, FontWeight.Bold), color = ink)
                    }
                    Text(
                        resolution ?: when {
                            d.type == CalibrationDecisionType.NOT_APPLIED -> d.reason
                            d.scope == CalibrationScope.NEXT_SESSIONS -> "Propuesta para las próximas sesiones de este ejercicio."
                            positions.isEmpty() -> "Referencia nueva; las series pendientes conservan su carga."
                            else -> "Propuesta para ${positions.joinToString(", ") { "S$it" }}." + if (compact) "" else " ${d.reason}"
                        },
                        style = body(12), color = muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().then(if (resolution != null) Modifier.testTag("calibration-resolution") else Modifier),
                    )
                }
                if (record?.status == CalibrationStatus.PROPOSED) {
                    LiveSheetSecondary("Mantener el plan", { onResolve(record.id, CalibrationStatus.KEPT) }, Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                        enabled = !busy, tag = "calibration-keep")
                } else if (record?.status == CalibrationStatus.APPLIED) {
                    LiveSheetSecondary("Revertir", { onResolve(record.id, CalibrationStatus.REVERTED) }, Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                        enabled = !busy, tag = "calibration-revert")
                }
                Box(Modifier.heightIn(min = 48.dp).clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Button, onClick = onEdit)
                    .testTag("calibration-edit"), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Corregir registro", style = body(11), color = muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 51.dp else 57.dp)
            .padding(start = inset, end = inset, top = if (compact) 4.dp else 6.dp, bottom = if (compact) 9.dp else 12.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (record?.status == CalibrationStatus.PROPOSED && d?.type != CalibrationDecisionType.NOT_APPLIED) {
                RestRef02Button("Aplicar propuesta", { onResolve(record.id, CalibrationStatus.APPLIED) }, primary = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = applyLabel }, tag = "calibration-apply", enabled = !busy, height = 48.dp, radius = 16.dp)
            } else {
                RestRef02Button("Continuar", onContinue, primary = true, modifier = Modifier.fillMaxWidth(), tag = "calibration-continue", enabled = !busy, height = 48.dp, radius = 16.dp)
            }
        }
    }
}
