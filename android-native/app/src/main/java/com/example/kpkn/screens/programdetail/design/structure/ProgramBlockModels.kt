package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.BlockGoal
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

/**
 * Un bloque para su módulo (P3b). [eyebrow] es la fase («Intensificación») o, en un programa simple, «Semana que se repite» o
 * «Calendarizado»; null si no se sabe, y entonces el eyebrow no se pinta. [phase] da su color y la bandera de competición. [macroIndex]
 * y [blockIndex] sitúan el bloque en el programa, para sus cambios.
 */
internal data class ProgramBlockUi(
    val id: String,
    val name: String,
    val macroIndex: Int,
    val blockIndex: Int,
    val eyebrow: String?,
    val phase: ProgramPhase?,
    val mesocycles: List<ProgramMesoUi>,
    val weeks: List<ProgramPhaseWeekUi>,
    val sessionCount: Int,
    /** «Ciclo 5» bajo las semanas de un programa simple, si el programa tiene ciclo guardado. */
    val cycleLabel: String? = null,
)

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
            name = block.name,
            macroIndex = roadmapBlock.macroIndex,
            blockIndex = roadmapBlock.blockIndex,
            eyebrow = when {
                simple -> if (program.simpleProgramKind == SimpleProgramKind.CALENDARIZED) "Calendarizado" else "Semana que se repite"
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
            sessionCount = weeks.sumOf { it.sessions.size },
            cycleLabel = if (simple) cycle?.let { "Ciclo $it" } else null,
        )
    }
}
