package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.screens.programdetail.design.ProgramPhaseWeekUi
import com.example.kpkn.screens.programdetail.design.programPhaseOf

/** Un mesociclo para su fila del módulo de bloque: nombre y objetivo ya en español. */
internal data class ProgramMesoUi(
    val name: String,
    val goalLabel: String,
)

/** El único nombre de la semana de un programa simple: lo lleva el título del bloque, sin repetirlo en el eyebrow. */
internal const val SIMPLE_WEEK_NAME = "Semana que se repite"

/**
 * Un bloque para su tarjeta (e-blocks). [phaseLabel] es la fase («Intensificación») o, si no hay fase, el objetivo; null si no se sabe.
 * [schemeLabel] es su método de progresión («Carga lineal»); null si no tiene. [phase] da el color de la franja. [macroIndex] y
 * [blockIndex] sitúan el bloque en el programa, para sus cambios.
 */
internal data class ProgramBlockUi(
    val id: String,
    val name: String,
    val macroIndex: Int,
    val blockIndex: Int,
    val phaseLabel: String?,
    val phase: ProgramPhase?,
    val mesocycles: List<ProgramMesoUi>,
    val weeks: List<ProgramPhaseWeekUi>,
    val schemeLabel: String? = null,
    /** «Ciclo 5» de un programa simple, si el programa tiene ciclo guardado. */
    val cycleLabel: String? = null,
)

/** «Intensificación · 2 semanas · Top set + back-off»: la línea bajo el nombre del bloque. Lo que falta no se pinta. Pura. */
internal fun blockSummaryLine(block: ProgramBlockUi): String =
    listOfNotNull(
        block.phaseLabel,
        if (block.weeks.size == 1) "1 semana" else "${block.weeks.size} semanas",
        block.schemeLabel,
    ).joinToString(" · ")

/**
 * Los bloques del programa en su orden, con cada semana numerada en orden global (como el módulo «Fases» de Semana). Pura: [currentWeekId]
 * es la semana en curso y [cycle], el ciclo del programa simple; sin ciclo guardado no hay «Ciclo».
 */
internal fun programBlockUis(program: Program, currentWeekId: String?, cycle: Int?): List<ProgramBlockUi> {
    val simple = program.isSimpleProgram
    val roadmapBlocks = ProgramDetailHelpers.buildRoadmapBlocks(program)
    val allWeeks = ProgramDetailHelpers.getAllWeeks(roadmapBlocks, program)
    val globalIndex = allWeeks.withIndex().associate { (index, week) -> week.id to index }
    val currentIndex = currentWeekId?.let { globalIndex[it] } ?: -1
    return roadmapBlocks.mapNotNull { roadmapBlock ->
        val block = program.macrocycles.getOrNull(roadmapBlock.macroIndex)?.blocks?.getOrNull(roadmapBlock.blockIndex)
            ?: return@mapNotNull null
        val weeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmapBlocks, program)
        val phase = if (simple) null else programPhaseOf(block.goal, weeks.any { it.keyDateType == KeyDateType.COMPETITION })
        val goal: BlockGoal? = block.goal
        ProgramBlockUi(
            id = block.id,
            // En un programa simple, la semana se llama «Semana que se repite», no «Semana cíclica».
            name = if (simple) SIMPLE_WEEK_NAME else block.name,
            macroIndex = roadmapBlock.macroIndex,
            blockIndex = roadmapBlock.blockIndex,
            phaseLabel = when {
                simple -> if (program.simpleProgramKind == SimpleProgramKind.CALENDARIZED) "Calendarizado" else null
                phase != null -> phase.label
                goal != null && goal != BlockGoal.CUSTOM -> blockGoalLabel(goal)
                else -> null
            },
            phase = phase,
            mesocycles = block.mesocycles.map { meso ->
                ProgramMesoUi(name = meso.name, goalLabel = mesocycleGoalLabel(meso.goal))
            },
            weeks = weeks.map { week ->
                val index = globalIndex[week.id] ?: 0
                ProgramPhaseWeekUi(
                    id = week.id,
                    number = index + 1,
                    isCurrent = week.id == currentWeekId,
                    isDone = currentIndex >= 0 && index < currentIndex,
                    isLoop = week.isLoopWeek,
                    isCompetition = week.keyDateType == KeyDateType.COMPETITION,
                )
            },
            schemeLabel = block.progressionScheme?.takeIf { it != BlockProgressionScheme.NONE }?.let(::blockProgressionLabel),
            cycleLabel = if (simple) cycle?.let { "Ciclo $it" } else null,
        )
    }
}
