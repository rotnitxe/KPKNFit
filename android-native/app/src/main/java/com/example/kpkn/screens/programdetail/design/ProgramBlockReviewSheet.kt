package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramHierarchyIndex

/** Una cifra con un decimal como mucho, en español: 12 → «12», 9,5 → «9,5». */
internal fun blockReviewNumber(value: Double): String =
    String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", value).removeSuffix(",0")

/** «12 previstas · 9 registradas» en la primera fila de «Series por semana»; en el resto, «16 · 15». */
internal fun blockReviewSetsLine(planned: Double, recorded: Double, first: Boolean): String =
    if (first) "${blockReviewNumber(planned)} previstas · ${blockReviewNumber(recorded)} registradas"
    else "${blockReviewNumber(planned)} · ${blockReviewNumber(recorded)}"

/** «Cambios propuestos · 4 sesiones»; con una, «· 1 sesión». */
internal fun blockReviewChangesGroup(count: Int): String =
    "Cambios propuestos · $count ${if (count == 1) "sesión" else "sesiones"}"

/**
 * Revisión de bloque (lienzo v6, m-block): «Bloque N terminado», «Revisión de bloque», las semanas con respuestas y el resumen; «Series por
 * semana» (previstas frente a registradas por músculo) y «Cambios propuestos · N sesiones» (series y RIR, antes → después). «Confirmar»
 * guarda la propuesta y «Conservar el plan» la deja como está. Si la revisión ya no está pendiente, lo dice y deja cerrar.
 */
@Composable
internal fun ProgramBlockReviewSheet(
    preview: ProgramRepository.BlockReviewPreview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onKeepPlan: () -> Unit = onDismiss,
) {
    val review = preview.original.pendingBlockReview?.review ?: run {
        ProgramUnavailableSheet("Bloque terminado", "Esta revisión ya no está pendiente. Vuelve a abrirla desde «Pendiente».", onDismiss)
        return
    }
    val previous = ProgramHierarchyIndex(preview.original).orderedWeeks().flatMap { it.week.sessions }.associateBy { it.id }
    val changed = ProgramHierarchyIndex(preview.result).orderedWeeks().flatMap { it.week.sessions }.filter { previous[it.id] != it }
    val weeksLine = "${review.feedbackWeeks} ${if (review.feedbackWeeks == 1) "semana" else "semanas"} con respuestas."
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        LienzoSheetTitle(
            title = "Revisión de bloque",
            eyebrow = "${review.blockName} terminado",
            sub = listOf(weeksLine, review.summary).filter { it.isNotBlank() }.joinToString(" "),
        )
        if (review.muscleSets.isNotEmpty()) {
            LienzoGroup("Series por semana")
            review.muscleSets.forEachIndexed { index, muscle ->
                LienzoRatioRow(
                    label = muscle.muscleName,
                    value = blockReviewSetsLine(muscle.plannedPerWeek, muscle.recordedPerWeek, first = index == 0),
                )
            }
        }
        if (changed.isNotEmpty()) {
            LienzoGroup(blockReviewChangesGroup(changed.size))
            changed.forEach { session ->
                val beforeSets = previous[session.id]?.allExercises().orEmpty().flatMap { it.sets }.filterNot { it.isIneffective }
                val afterSets = session.allExercises().flatMap { it.sets }.filterNot { it.isIneffective }
                val beforeRir = beforeSets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "—" }
                val afterRir = afterSets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "—" }
                LienzoRatioRow(label = session.name, value = "series ${beforeSets.size} → ${afterSets.size} · RIR $beforeRir → $afterRir")
            }
        }
        ProgramLienzoPill(
            text = "Confirmar",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !busy,
            tag = "block_review_confirm",
        )
        ProgramLienzoPill(
            text = "Conservar el plan",
            onClick = onKeepPlan,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            filled = false,
            enabled = !busy,
            tag = "block_review_keep",
        )
    }
}
