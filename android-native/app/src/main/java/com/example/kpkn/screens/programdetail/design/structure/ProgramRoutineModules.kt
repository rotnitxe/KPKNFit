package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.ui.components.icons.KpknLineIcons

/*
 * e-cycles: la Estructura de una rutina (programa que se repite). El módulo «Semana base» y las filas del grupo «Ciclos». Los textos con
 * cifras son funciones puras, para fijarlas con pruebas de JVM.
 */

/** «4 sesiones · ≈ 3 h 10»: las sesiones de la semana base y su duración estimada. Sin sesiones, «Sin sesiones». Pura. */
internal fun baseWeekHeadline(sessions: Int, minutes: Int): String {
    if (sessions <= 0) return "Sin sesiones"
    val count = if (sessions == 1) "1 sesión" else "$sessions sesiones"
    return "$count · ${approximateDurationLabel(minutes)}"
}

/** «≈ 45 min», «≈ 3 h» o «≈ 3 h 10» (como el lienzo, sin «min» tras las horas). Pura. */
internal fun approximateDurationLabel(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    if (safe < 60) return "≈ $safe min"
    val hours = safe / 60
    val rest = safe % 60
    return if (rest == 0) "≈ $hours h" else "≈ $hours h ${rest.toString().padStart(2, '0')}"
}

/**
 * e-cycles, módulo «Semana base»: la cabecera con «Editar» (lápiz, en `--muted`), el titular (Syne 700 17) y los días L–D con los de entreno
 * rellenos de tinta (10 dp por encima). Si el programa guarda su ciclo, «Ciclo N» en `.cap` debajo.
 */
@Composable
internal fun ProgramBaseWeekModule(
    headline: String,
    trainingDays: Set<Int>,
    cycleLabel: String?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier.testTag("estructura_semana_base")) {
        StructureModuleHeader(
            label = "Semana base",
            action = { StructureAction(text = "Editar", onClick = onEdit, leadingIcon = KpknLineIcons.Edit, faint = true) },
        )
        Text(
            text = headline,
            style = StructureHeadlineStyle.tabular(),
            color = StructureInk,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        StructureWeekdayRow(trainingDays = trainingDays)
        cycleLabel?.let {
            Spacer(Modifier.height(8.dp))
            StructureCaption(text = it)
        }
    }
}

/** Una fila del grupo «Ciclos»: el ciclo, su icono, el título con su cadencia en semanas y la próxima vez. */
internal data class ProgramCycleRowUi(
    val loopId: String,
    val icon: ImageVector,
    val title: String,
    val detail: String?,
)

/** «Descarga cada 4 semanas»: el nombre del ciclo y su cadencia pasada a semanas ([cycleWeeks] semanas por vuelta). Pura. */
internal fun cycleRowTitle(title: String, type: LoopType, cadence: Int, cycleWeeks: Int): String {
    val name = title.ifBlank { LoopEngine.getLoopTypeLabel(type) }
    val weeks = cadence.coerceAtLeast(1) * cycleWeeks.coerceAtLeast(1)
    return if (weeks == 1) "$name cada semana" else "$name cada $weeks semanas"
}

/** «Próxima: semana 8» (descarga, competición) o «Próximo: semana 8» (test, otro); «Cancelado» si está cancelado. Pura. */
internal fun cycleRowDetail(type: LoopType, nextWeek: Int?, cancelled: Boolean): String? {
    if (cancelled) return "Cancelado"
    nextWeek ?: return null
    val next = when (type) {
        LoopType.DELOAD, LoopType.COMPETITION -> "Próxima"
        LoopType.ONE_RM_TEST, LoopType.CUSTOM -> "Próximo"
    }
    return "$next: semana $nextWeek"
}

/** Las filas de «Ciclos» de [program], en el orden de sus ciclos, con la próxima vez que toca cada uno. */
internal fun programCycleRows(program: Program): List<ProgramCycleRowUi> {
    val cycleWeeks = LoopEngine.getCycleLength(program)
    val cancelled = (program.loopState?.cancelled ?: emptyList()).toSet()
    val projections = LoopEngine.projectLoops(program, fromCycle = LoopEngine.getCurrentCycle(program).coerceAtLeast(0), lookAheadCycles = 24)
    return program.loops.map { loop ->
        val next = projections.firstOrNull { it.loop.id == loop.id && !it.isCancelled }?.cycle
        ProgramCycleRowUi(
            loopId = loop.id,
            icon = loopTypeIcon(loop.type),
            title = cycleRowTitle(loop.title, loop.type, loop.repeatEveryXLoops, cycleWeeks),
            detail = cycleRowDetail(loop.type, next?.let { it * cycleWeeks }, cancelled = loop.id in cancelled),
        )
    }
}
