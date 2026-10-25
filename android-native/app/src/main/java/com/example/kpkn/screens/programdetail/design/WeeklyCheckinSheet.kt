package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.cycle.MuscleCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest
import com.example.kpkn.domain.training.cycle.kpknMuscleGroupLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeklyCheckinSheet(
    request: WeeklyCheckinRequest,
    previewDetail: String?,
    busy: Boolean,
    onPreview: (WeeklyCheckin) -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onOmit: (WeeklyCheckin) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = request.muscleGroups.ifEmpty { listOf("") }
    var performance by remember(request) { mutableStateOf(emptyMap<String, Int>()) }
    var soreness by remember(request) { mutableStateOf(emptyMap<String, Int>()) }
    fun answer(skipped: Boolean = false) = WeeklyCheckin(
        weekId = request.weekId, cycleNumber = request.cycleNumber,
        programRunId = request.programRunId, answeredAtMs = System.currentTimeMillis(), skipped = skipped,
        feedbackWeekId = request.feedbackWeekId, feedbackCycleNumber = request.feedbackCycleNumber,
        muscles = if (request.muscleGroups.isEmpty() || skipped) emptyList() else groups.map {
            MuscleCheckin(it, performance.getValue(it), soreness.getValue(it))
        },
        globalPerformance = if (request.muscleGroups.isEmpty() && !skipped) performance[""] else null,
        globalSoreness = if (request.muscleGroups.isEmpty() && !skipped) soreness[""] else null,
    )
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() }, stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("¿Cómo fue la semana pasada?", style = KpknType.bodyStrong, color = WizardColors.text)
            Text("Tus respuestas quedan guardadas. Puedes revisar una propuesta para los accesorios de la nueva semana antes de confirmarla.", style = KpknType.body, color = WizardColors.textMuted)
            if (previewDetail != null) {
                Text("Revisar propuesta", style = KpknType.bodyStrong, color = WizardColors.text)
                Text(previewDetail, style = KpknType.body, color = WizardColors.text)
                if (!busy) {
                    KpknCompactPill(text = "Confirmar", onClick = onConfirm, fullWidth = true)
                    KpknCompactPill(text = "Editar respuestas", onClick = onEdit, filled = false, fullWidth = true)
                }
            } else {
                groups.forEach { group ->
                    Text(if (group.isEmpty()) "En general" else kpknMuscleGroupLabel(group).replaceFirstChar { it.uppercase() }, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text("Rendimiento", style = KpknType.caption, color = WizardColors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Fácil, superé el plan", "Cumplí", "Me costó", "No igualé la semana anterior").forEachIndexed { index, text ->
                            FilterChip(selected = performance[group] == index + 1, enabled = !busy,
                                onClick = { performance = performance + (group to index + 1) }, label = { Text(text) })
                        }
                    }
                    Text("Agujetas", style = KpknType.caption, color = WizardColors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ninguna", "Se fueron antes de entrenar", "Justo a tiempo", "Aún las tengo").forEachIndexed { index, text ->
                            FilterChip(selected = soreness[group] == index + 1, enabled = !busy,
                                onClick = { soreness = soreness + (group to index + 1) }, label = { Text(text) })
                        }
                    }
                }
                if (!busy && groups.all { performance[it] != null && soreness[it] != null })
                    KpknCompactPill(text = "Revisar propuesta", onClick = { onPreview(answer()) }, fullWidth = true)
                if (!busy) KpknCompactPill(text = "Omitir esta semana", onClick = { onOmit(answer(skipped = true)) }, filled = false, fullWidth = true)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("No volver a preguntar", Modifier.weight(1f), style = KpknType.body, color = WizardColors.text)
                    Switch(checked = false, enabled = !busy, onCheckedChange = { if (it) onDisable() })
                }
            }
            if (busy) Text("Guardando…", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}
