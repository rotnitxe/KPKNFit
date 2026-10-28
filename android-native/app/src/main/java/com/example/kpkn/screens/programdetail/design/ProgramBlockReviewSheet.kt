package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

@Composable
internal fun ProgramBlockReviewSheet(
    preview: ProgramRepository.BlockReviewPreview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val review = preview.original.pendingBlockReview?.review ?: return
    val previous = ProgramHierarchyIndex(preview.original).orderedWeeks().flatMap { it.week.sessions }.associateBy { it.id }
    val changed = ProgramHierarchyIndex(preview.result).orderedWeeks().flatMap { it.week.sessions }.filter { previous[it.id] != it }
    fun number(value: Double) = String.format(java.util.Locale.forLanguageTag("es-CL"), "%.1f", value)
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() }, stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = "Revisión de bloque", eyebrow = "Bloque terminado", subtitle = review.summary)
            Text("${review.feedbackWeeks} semanas con respuestas originales atribuibles al bloque.", style = KpknType.caption, color = WizardColors.textMuted)
            review.muscleSets.forEach { muscle ->
                Text("${muscle.muscleName}: ${number(muscle.plannedPerWeek)} series previstas y ${number(muscle.recordedPerWeek)} registradas por semana.",
                    style = KpknType.body, color = WizardColors.text)
            }
            Text(preview.result.programDecisions.lastOrNull()?.detail.orEmpty(), style = KpknType.body, color = WizardColors.text)
            Text(if (changed.isEmpty()) "Esta elección conserva la prescripción vigente." else
                "${changed.size} sesiones futuras tienen cambios propuestos. Se guardarán al confirmar.", style = KpknType.bodyStrong, color = WizardColors.text)
            changed.forEach { session ->
                val beforeSets = previous[session.id]?.allExercises().orEmpty().flatMap { it.sets }.filterNot { it.isIneffective }
                val afterSets = session.allExercises().flatMap { it.sets }.filterNot { it.isIneffective }
                val beforeRir = beforeSets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "sin RIR declarado" }
                val afterRir = afterSets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "sin RIR declarado" }
                Text("${session.name}: series ${beforeSets.size} → ${afterSets.size}; RIR $beforeRir → $afterRir.",
                    style = KpknType.body, color = WizardColors.text)
                val beforePercents = beforeSets.mapNotNull { it.targetPercentageRM }.distinct().sorted()
                val afterPercents = afterSets.mapNotNull { it.targetPercentageRM }.distinct().sorted()
                if (beforePercents != afterPercents) Text("Porcentajes de referencia: ${beforePercents.joinToString("/") { number(it) }} → ${afterPercents.joinToString("/") { number(it) }}.",
                    style = KpknType.body, color = WizardColors.text)
            }
            if (!busy) {
                KpknPrimaryPill(text = "Confirmar", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                KpknSecondaryPill(text = "Volver", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
        }
    }
}
