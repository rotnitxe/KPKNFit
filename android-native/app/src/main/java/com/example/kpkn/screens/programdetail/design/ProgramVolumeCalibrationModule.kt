package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.domain.training.calibration.CalibrationEvidenceSummary
import com.example.kpkn.domain.training.calibration.PlanningVolumeTarget
import com.example.kpkn.domain.training.calibration.WeeklyCycleEvidence
import com.example.kpkn.domain.training.cycle.kpknMuscleGroupLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

@Composable
internal fun ProgramVolumeCalibrationModule(
    evidence: CalibrationEvidenceSummary?,
    targets: List<PlanningVolumeTarget>,
    cycles: List<WeeklyCycleEvidence>,
    responses: com.example.kpkn.data.models.VolumeCalibrationResponses?,
    originalNotes: String,
    onEdit: () -> Unit,
    proposals: List<com.example.kpkn.domain.training.calibration.PlanningRangeProposal> = emptyList(),
    onReviewProposals: () -> Unit = {},
) {
    KpknModule(modifier = Modifier.fillMaxWidth(), accent = SymbolPalette.energia) {
        Text("Tu volumen", style = KpknType.bodyStrong, color = WizardColors.text)
        evidence?.let { summary ->
            Text("${summary.recordedSessions} registros de sesión en ${summary.observedWeeks} semanas con registros.", style = KpknType.body, color = WizardColors.text)
        }
        Text("Objetivos de planificación", style = KpknType.bodyStrong, color = WizardColors.text)
        if (targets.isEmpty()) Text("Se usan referencias editoriales de series. Puedes elegir tus propios rangos.", style = KpknType.body, color = WizardColors.textMuted)
        targets.sortedBy { it.muscleGroup.ordinal }.take(2).forEach { target ->
            Text("${kpknMuscleGroupLabel(target.muscleGroup.name)}: ${target.minSets}–${target.maxSets} series por semana · elegido por ti", style = KpknType.body, color = WizardColors.text)
        }
        if (targets.size > 2) Text("${targets.size} rangos elegidos en total", style = KpknType.caption, color = WizardColors.textMuted)
        cycles.takeLast(1).forEach { cycle ->
            Text("Ciclo ${cycle.cycleNumber}: ${cycle.answeredWeeks} semanas respondidas y ${cycle.skippedWeeks} omitidas.", style = KpknType.caption, color = WizardColors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        if (responses != null) Text("Perfil guardado · ${responseOrigin(responses.state)}", style = KpknType.caption, color = WizardColors.textMuted)
        if (originalNotes.isNotBlank()) Text(originalNotes, style = KpknType.body, color = WizardColors.text)
        KpknCompactPill(text = "Ver registros y elegir rangos", onClick = onEdit, fullWidth = true)
        if (proposals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            KpknCompactPill(text = "Revisar propuestas del ciclo", onClick = onReviewProposals, filled = false, fullWidth = true)
        }
    }
}

@Composable
internal fun PlanningRangeProposalsSheet(
    proposals: List<com.example.kpkn.domain.training.calibration.PlanningRangeProposal>,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() }, stableHeightFraction = 0.85f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Propuestas de planificación", style = KpknType.bodyStrong, color = WizardColors.text)
            Text("Estos cambios ajustan el rango usado para preparar futuras propuestas. Se guardan sólo si los confirmas.", style = KpknType.body, color = WizardColors.textMuted)
            proposals.forEach { proposal ->
                Text("${kpknMuscleGroupLabel(proposal.before.muscleGroup.name)} · ciclo ${proposal.cycleNumber}", style = KpknType.bodyStrong, color = WizardColors.text)
                Text("Antes: ${proposal.before.minSets}–${proposal.before.maxSets} series por semana\nPropuesto: ${proposal.proposed.minSets}–${proposal.proposed.maxSets}", style = KpknType.body, color = WizardColors.text)
                Text(proposal.reason, style = KpknType.body, color = WizardColors.text)
            }
            if (!busy) {
                KpknCompactPill(text = "Confirmar rangos propuestos", onClick = onAccept, fullWidth = true)
                KpknCompactPill(text = "Más tarde", onClick = onDismiss, filled = false, fullWidth = true)
                KpknCompactPill(text = "Descartar estas propuestas", onClick = onReject, filled = false, fullWidth = true)
            } else Text("Guardando…", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramPlanningVolumeTargetSheet(
    targets: List<PlanningVolumeTarget>,
    evidence: CalibrationEvidenceSummary?,
    cycles: List<WeeklyCycleEvidence>,
    responses: com.example.kpkn.data.models.VolumeCalibrationResponses?,
    busy: Boolean,
    onSave: (PlanningVolumeTarget) -> Unit,
    onReset: (KpknMuscleGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var group by remember { mutableStateOf(KpknMuscleGroup.CHEST) }
    val current = targets.firstOrNull { it.muscleGroup == group }
    val reference = VolumeLandmarks.byGroup.getValue(group)
    var minText by remember(group, current) { mutableStateOf((current?.minSets ?: reference.mev).toString()) }
    var maxText by remember(group, current) { mutableStateOf((current?.maxSets ?: reference.mrv).toString()) }
    var note by remember(group, current) { mutableStateOf(current?.notes.orEmpty()) }
    val min = minText.toIntOrNull()
    val max = maxText.toIntOrNull()
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() }, stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Elegir un rango de series", style = KpknType.bodyStrong, color = WizardColors.text)
            Text("Este rango acota las propuestas que luego revisas y confirmas. Guardarlo conserva las sesiones programadas.", style = KpknType.body, color = WizardColors.textMuted)
            evidence?.let { summary ->
                Text("Registros del ${summary.fromDate} al ${summary.throughDate}", style = KpknType.caption, color = WizardColors.textMuted)
                summary.notes.forEach { Text(it, style = KpknType.body, color = WizardColors.text) }
            }
            responses?.let { saved ->
                Text("Perfil guardado · ${responseOrigin(saved.state)}", style = KpknType.bodyStrong, color = WizardColors.text)
                Text("Técnica ${saved.technique}/3 · constancia ${saved.consistency}/3 · fuerza ${saved.strength}/3 · movilidad ${saved.mobility}/3", style = KpknType.body, color = WizardColors.text)
            }
            cycles.forEach { cycle ->
                Text("Ciclo ${cycle.cycleNumber}: ${cycle.answeredWeeks} semanas respondidas y ${cycle.skippedWeeks} omitidas.", style = KpknType.caption, color = WizardColors.textMuted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknMuscleGroup.entries.forEach { option ->
                    FilterChip(selected = group == option, enabled = !busy, onClick = { group = option },
                        label = { Text(kpknMuscleGroupLabel(option.name)) })
                }
            }
            Text("Referencia editorial: ${reference.mev}–${reference.mrv} series semanales.", style = KpknType.caption, color = WizardColors.textMuted)
            Text("Convención de planificación: una serie para un músculo principal cuenta como una; su participación secundaria cuenta como media.", style = KpknType.caption, color = WizardColors.textMuted)
            OutlinedTextField(value = minText, onValueChange = { minText = it }, enabled = !busy, singleLine = true,
                label = { Text("Mínimo semanal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = maxText, onValueChange = { maxText = it }, enabled = !busy, singleLine = true,
                label = { Text("Máximo semanal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, enabled = !busy,
                label = { Text("Nota opcional") }, modifier = Modifier.fillMaxWidth())
            if (min != null && max != null && min >= 0 && max >= min && !busy)
                KpknCompactPill(text = "Guardar este rango", onClick = { onSave(PlanningVolumeTarget(group, min, max, notes = note)) }, fullWidth = true)
            else if (minText.isNotBlank() && maxText.isNotBlank()) Text("Elige números enteros: mínimo desde cero y máximo igual o mayor.", style = KpknType.caption, color = WizardColors.textMuted)
            if (current != null && !busy) KpknCompactPill(text = "Usar referencia editorial", onClick = { onReset(group) }, filled = false, fullWidth = true)
            if (busy) Text("Guardando…", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

private fun responseOrigin(state: com.example.kpkn.data.models.CalibrationResponseState): String = when (state) {
    com.example.kpkn.data.models.CalibrationResponseState.DECLARED -> "respuestas declaradas"
    com.example.kpkn.data.models.CalibrationResponseState.IMPORTED -> "datos importados"
    com.example.kpkn.data.models.CalibrationResponseState.DERIVED -> "cálculo histórico"
    com.example.kpkn.data.models.CalibrationResponseState.SUGGESTED -> "valores sugeridos"
    com.example.kpkn.data.models.CalibrationResponseState.UNKNOWN -> "origen sin confirmar"
}
