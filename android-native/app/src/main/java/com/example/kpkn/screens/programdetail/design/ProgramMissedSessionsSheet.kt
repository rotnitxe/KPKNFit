package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.acceptsKpknAdjustments
import com.example.kpkn.domain.training.schedule.InactivityNotice
import com.example.kpkn.domain.training.schedule.MissedSessionChoice
import com.example.kpkn.domain.training.schedule.MissedSessionPolicy
import com.example.kpkn.domain.training.schedule.MissedSessionsRequest
import com.example.kpkn.ui.components.icons.KpknLineIcons

/*
 * Sesiones sin registrar y vuelta tras una pausa, con el patrón de las hojas m-* (sustituye al módulo ProgramScheduleModule de la hoja
 * intermedia de «Pendiente»): título, sub, una fila `.mi` con subtítulo por opción y, tras elegir, la revisión con su píldora. Las opciones y
 * la lógica son las de siempre: hacerlas esta semana, saltarlas o volver suave (solo si el programa acepta ajustes de KPKN).
 */

/** La opción de una sesión perdida: su icono, su título y su efecto en una línea. */
private data class MissedOption(val choice: MissedSessionChoice, val title: String, val sub: String)

private fun missedOptionOf(choice: MissedSessionChoice): MissedOption = when (choice) {
    MissedSessionChoice.DO_THIS_WEEK -> MissedOption(choice, "Hacerlas esta semana", "Pasan a esta semana; no se salta nada")
    MissedSessionChoice.SKIP -> MissedOption(choice, "Saltarlas", "Sigues con el plan; no cuentan como entrenadas")
    MissedSessionChoice.SOFT_RETURN -> MissedOption(choice, "Vuelta suave", "Las próximas sesiones empiezan con menos carga")
}

/**
 * «2 sesiones sin registrar» (o la vuelta tras días sin entrenar): la semana como eyebrow y una fila por opción, la recomendada primero. Tocar
 * una pide la revisión ([onChoice] / [onSoftReturn]); con [busy] las filas se apagan.
 */
@Composable
internal fun ProgramMissedSessionsSheet(
    program: Program,
    request: MissedSessionsRequest?,
    notice: InactivityNotice?,
    busy: Boolean,
    onChoice: (MissedSessionChoice) -> Unit,
    onSoftReturn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val adjustable = program.acceptsKpknAdjustments()
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        if (request != null) {
            val weekName = ProgramHierarchyIndex(program).locateWeek(request.weekId)?.week?.name
            val default = MissedSessionPolicy.defaultChoice(program)
            val other = if (default == MissedSessionChoice.SKIP) MissedSessionChoice.DO_THIS_WEEK else MissedSessionChoice.SKIP
            val choices = buildList {
                add(default)
                add(other)
                if (adjustable) add(MissedSessionChoice.SOFT_RETURN)
            }
            LienzoSheetTitle(
                title = missedSessionsTitle(request.sessionIds.size),
                eyebrow = weekName,
                sub = "Sin registro no quiere decir sin entrenar: elige cómo sigue el plan.",
            )
            choices.map(::missedOptionOf).forEachIndexed { index, option ->
                LienzoMenuRow(
                    icon = when (option.choice) {
                        MissedSessionChoice.DO_THIS_WEEK -> KpknLineIcons.Calendar
                        MissedSessionChoice.SKIP -> KpknLineIcons.ChevronRight
                        MissedSessionChoice.SOFT_RETURN -> KpknLineIcons.Repeat
                    },
                    title = option.title,
                    sub = option.sub,
                    onClick = { onChoice(option.choice) },
                    divider = index > 0,
                    enabled = !busy,
                )
            }
        } else if (notice != null) {
            LienzoSheetTitle(title = "Vuelta al plan", sub = notice.message)
            if (adjustable) {
                LienzoMenuRow(
                    icon = KpknLineIcons.Repeat,
                    title = "Vuelta suave",
                    sub = "Las próximas sesiones empiezan con menos carga",
                    onClick = onSoftReturn,
                    divider = false,
                    enabled = !busy,
                )
            }
        }
    }
}

/**
 * «Revisar continuidad»: lo que cambia al confirmar, en filas `.ratio` (semana: series antes → después; ejercicio: series y RIR), las sesiones
 * que se declaran saltadas y «Confirmar».
 */
@Composable
internal fun ProgramScheduleReviewSheet(original: Program, result: Program, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val addedDecisions = result.programDecisions.filter { decision -> original.programDecisions.none { it.id == decision.id } }
    val skipped = result.skippedSessionKeys.size - original.skippedSessionKeys.size
    val sub = buildList {
        addAll(addedDecisions.map { it.detail }.filter { it.isNotBlank() })
        if (skipped > 0) add("$skipped sesiones se declararán saltadas. Los registros de entrenamiento se conservan.")
    }.joinToString(" ").ifBlank { null }
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        LienzoSheetTitle(title = "Revisar continuidad", sub = sub)
        val oldIndex = ProgramHierarchyIndex(original)
        fun sets(week: com.example.kpkn.data.models.ProgramWeek) =
            week.sessions.sumOf { session -> session.allExercises().sumOf { exercise -> exercise.sets.count { !it.isIneffective } } }
        fun rir(e: com.example.kpkn.data.models.Exercise) = e.sets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "—" }
        ProgramHierarchyIndex(result).orderedWeeks().forEach weekLoop@{ location ->
            val before = oldIndex.locateWeek(location.week.id)?.week ?: return@weekLoop
            if (before == location.week) return@weekLoop
            LienzoGroup(location.week.name)
            LienzoRatioRow(label = "Series", value = "${sets(before)} → ${sets(location.week)}")
            location.week.sessions.forEach sessionLoop@{ session ->
                val previous = before.sessions.firstOrNull { it.id == session.id } ?: return@sessionLoop
                if (previous == session) return@sessionLoop
                session.allExercises().forEach exerciseLoop@{ exercise ->
                    val prev = previous.allExercises().firstOrNull { it.id == exercise.id } ?: return@exerciseLoop
                    if (prev == exercise) return@exerciseLoop
                    LienzoRatioRow(
                        label = exercise.name,
                        value = "series ${prev.sets.size} → ${exercise.sets.size} · RIR ${rir(prev)} → ${rir(exercise)}",
                    )
                }
            }
        }
        ProgramLienzoPill(
            text = "Confirmar",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !busy,
            tag = "schedule_confirm",
        )
    }
}
