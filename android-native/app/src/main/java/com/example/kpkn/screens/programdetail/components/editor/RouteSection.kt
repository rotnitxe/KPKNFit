package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.components.AdvancedRoadmap
import com.example.kpkn.screens.programdetail.design.programPhaseOf
import com.example.kpkn.screens.programdetail.design.structure.ProgramRouteBlock
import com.example.kpkn.screens.programdetail.design.structure.ProgramRouteKeyDate
import com.example.kpkn.screens.programdetail.design.structure.ProgramRouteModel
import com.example.kpkn.screens.programdetail.design.structure.ProgramRouteSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramRouteWeek
import com.example.kpkn.screens.programdetail.keyDateTypeLabel
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import java.time.LocalDate

/**
 * La hoja «Ruta del programa» (P3c): traduce la ruta que ya calcula el editor (`AdvancedRoadmap`) y el encaje de la competición a los datos
 * simples de la hoja. Las acciones llegan del editor: ajustar semanas y ver una semana son las de siempre.
 */
@Composable
internal fun RouteSection(
    program: Program,
    roadmap: AdvancedRoadmap,
    today: LocalDate,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onAdjustBlockWeeks: (blockId: String, deltaWeeks: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val model = remember(program, roadmap, today) { programRouteModelOf(program, roadmap, today) }
    ProgramRouteSheet(
        model = model,
        onFocusWeek = onFocusWeek,
        onAdjustBlockWeeks = onAdjustBlockWeeks,
        onDismiss = onDismiss,
    )
}

/**
 * La ruta del programa en datos de la hoja: las semanas en orden, los bloques con su fase (color), las fechas clave que no son la
 * competición y el encaje con la competición. Sin fecha de inicio, no hay semanas y la hoja muestra su estado vacío.
 */
internal fun programRouteModelOf(program: Program, roadmap: AdvancedRoadmap, today: LocalDate): ProgramRouteModel {
    val weeks = roadmap.weekSlots.mapIndexed { index, slot ->
        ProgramRouteWeek(
            index = index,
            blockId = slot.blockId,
            weekId = slot.weekId,
            start = slot.weekStart,
            end = slot.weekEnd,
            isCompetition = slot.marks.any { it.type == KeyDateType.COMPETITION },
        )
    }
    val fit = ProgramKeyDateEngine.fitBlocksToCompetitionKeyDate(program)
    return ProgramRouteModel(
        today = today,
        weeks = weeks,
        blocks = routeBlocksOf(program, roadmap, weeks),
        keyDates = routeKeyDatesOf(program, weeks),
        competitionDate = roadmap.competitionDate,
        competitionWeekIndex = weeks.indexOfFirst { it.isCompetition }.takeIf { it >= 0 },
        fitStatus = fit?.status,
        fitWeeks = fit?.actionableWeeks ?: 0,
    )
}

/** Un bloque mientras se recorren sus semanas: cuántas tiene y si lleva la competición. */
private class BlockRun(
    val blockId: String,
    val name: String,
    val firstIndex: Int,
    val start: LocalDate,
    val firstWeekId: String,
    var weeks: Int,
    var hasCompetition: Boolean,
)

/** Los bloques en orden, cada uno como tramo de la línea de tiempo y con el color de su fase. */
private fun routeBlocksOf(program: Program, roadmap: AdvancedRoadmap, weeks: List<ProgramRouteWeek>): List<ProgramRouteBlock> {
    val goals: Map<String, BlockGoal?> = program.macrocycles.flatMap { it.blocks }.associate { it.id to it.goal }
    val runs = mutableListOf<BlockRun>()
    roadmap.weekSlots.forEachIndexed { index, slot ->
        val isCompetition = weeks[index].isCompetition
        val last = runs.lastOrNull()
        if (last != null && last.blockId == slot.blockId) {
            last.weeks += 1
            last.hasCompetition = last.hasCompetition || isCompetition
        } else {
            runs += BlockRun(
                blockId = slot.blockId,
                name = slot.blockName,
                firstIndex = index,
                start = slot.weekStart,
                firstWeekId = slot.weekId,
                weeks = 1,
                hasCompetition = isCompetition,
            )
        }
    }
    return runs.map { run ->
        val phase = programPhaseOf(goals[run.blockId], run.hasCompetition)
        ProgramRouteBlock(
            id = run.blockId,
            name = run.name,
            firstWeekIndex = run.firstIndex,
            weeks = run.weeks,
            start = run.start,
            firstWeekId = run.firstWeekId,
            color = phase?.color ?: WizardColors.textFaint,
            phaseLabel = phase?.label,
        )
    }
}

/** Las fechas clave que no son la competición, con la semana que las contiene si cae dentro del programa. */
private fun routeKeyDatesOf(program: Program, weeks: List<ProgramRouteWeek>): List<ProgramRouteKeyDate> =
    program.keyDates
        .filter { it.type != KeyDateType.COMPETITION }
        .mapNotNull { keyDate ->
            val date = parseIsoDateExact(keyDate.eventDate) ?: parseIsoDateExact(keyDate.startDate) ?: return@mapNotNull null
            ProgramRouteKeyDate(
                title = keyDate.title,
                typeLabel = keyDateTypeLabel(keyDate.type),
                date = date,
                weekIndex = weeks.indexOfFirst { week -> !date.isBefore(week.start) && !date.isAfter(week.end) }
                    .takeIf { it >= 0 },
            )
        }
