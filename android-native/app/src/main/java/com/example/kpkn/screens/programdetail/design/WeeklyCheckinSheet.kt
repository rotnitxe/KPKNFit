package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.cycle.MuscleCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest
import com.example.kpkn.domain.training.cycle.kpknMuscleGroupLabel
import com.example.kpkn.screens.onboarding.design.WizardColors

/** Las cuatro respuestas de rendimiento, con su texto literal (m-checkin). */
internal val CheckinPerformanceOptions = listOf("Fácil, superé el plan", "Cumplí", "Me costó", "No igualé la anterior")

/** Las cuatro respuestas de agujetas, con su texto literal (m-checkin). */
internal val CheckinSorenessOptions = listOf("Ninguna", "Se fueron antes de entrenar", "Justo a tiempo", "Aún las tengo")

/** «Espalda · Hombros · Pierna: 3 grupos más»; con uno, «Espalda: 1 grupo más». */
internal fun checkinFoldedLine(labels: List<String>): String =
    "${labels.joinToString(" · ")}: ${labels.size} ${if (labels.size == 1) "grupo más" else "grupos más"}"

/**
 * Check-in semanal (lienzo v6, m-checkin): «Semana N» arriba, la pregunta y, por grupo muscular, «Pecho · rendimiento» y «Pecho · agujetas»
 * con chips KPKN. El primer grupo va abierto y el resto plegado en una línea («Espalda · Hombros · Pierna: 3 grupos más») que los abre.
 * «Revisar propuesta» se activa con todo respondido; debajo, «Omitir esta semana» y «No volver a preguntar» como acciones de texto (esta
 * pide confirmación). Con la propuesta preparada, «Confirmar» o «Editar respuestas».
 *
 * [weekLabel] es «Semana 3» (la semana por la que se pregunta); null si no se sabe.
 */
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
    weekLabel: String? = null,
) {
    val groups = request.muscleGroups.ifEmpty { listOf("") }
    var performance by remember(request) { mutableStateOf(emptyMap<String, Int>()) }
    var soreness by remember(request) { mutableStateOf(emptyMap<String, Int>()) }
    var expanded by remember(request) { mutableStateOf(false) }
    var confirmingDisable by remember(request) { mutableStateOf(false) }
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
    fun label(group: String) = if (group.isEmpty()) "" else kpknMuscleGroupLabel(group).replaceFirstChar { it.uppercase() }
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        when {
            confirmingDisable -> {
                LienzoSheetTitle(
                    title = "¿No volver a preguntar?",
                    sub = "Dejarás de ver el check-in semanal y las propuestas de accesorios que salen de él.",
                )
                ProgramLienzoPill(text = "No volver a preguntar", onClick = onDisable, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                LienzoTextAction(text = "Cancelar", onClick = { confirmingDisable = false }, centered = true, modifier = Modifier.padding(top = 6.dp))
            }
            previewDetail != null -> {
                LienzoSheetTitle(title = "Revisar propuesta", eyebrow = weekLabel, sub = previewDetail)
                ProgramLienzoPill(text = "Confirmar", onClick = onConfirm, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                ProgramLienzoPill(
                    text = "Editar respuestas",
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    filled = false,
                    enabled = !busy,
                )
            }
            else -> {
                LienzoSheetTitle(
                    title = "¿Cómo fue la semana pasada?",
                    eyebrow = weekLabel,
                    sub = "Tus respuestas quedan guardadas. Después revisas una propuesta para los accesorios.",
                )
                val shown = if (expanded) groups else groups.take(1)
                shown.forEach { group ->
                    val name = label(group)
                    LienzoGroup(if (name.isEmpty()) "Rendimiento" else "$name · rendimiento")
                    LienzoChipsWrap(
                        options = CheckinPerformanceOptions,
                        selected = { performance[group] == it + 1 },
                        onSelect = { performance = performance + (group to it + 1) },
                        enabled = !busy,
                    )
                    LienzoGroup(if (name.isEmpty()) "Agujetas" else "$name · agujetas")
                    LienzoChipsWrap(
                        options = CheckinSorenessOptions,
                        selected = { soreness[group] == it + 1 },
                        onSelect = { soreness = soreness + (group to it + 1) },
                        enabled = !busy,
                    )
                }
                val folded = groups.drop(shown.size).map(::label)
                if (folded.isNotEmpty()) {
                    LienzoDivider(Modifier.padding(top = 12.dp))
                    Text(
                        text = checkinFoldedLine(folded),
                        style = LienzoCapStyle,
                        color = WizardColors.textFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                expanded = true
                            },
                    )
                }
                val complete = groups.all { performance[it] != null && soreness[it] != null }
                ProgramLienzoPill(
                    text = "Revisar propuesta",
                    onClick = { onPreview(answer()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    enabled = complete && !busy,
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LienzoTextAction(text = "Omitir esta semana", onClick = { onOmit(answer(skipped = true)) }, enabled = !busy)
                    LienzoTextAction(text = "No volver a preguntar", onClick = { confirmingDisable = true }, enabled = !busy)
                }
            }
        }
    }
}
