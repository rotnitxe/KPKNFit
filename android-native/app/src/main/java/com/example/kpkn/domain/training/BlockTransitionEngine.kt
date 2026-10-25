package com.example.kpkn.domain.training

import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.templates.SessionClonePurpose
import com.example.kpkn.domain.templates.SessionTemplateEngine
import kotlin.math.ceil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Evalúa la transición al completar la última semana de un bloque COMPLEX.
 */
object BlockTransitionEngine {

    enum class DecisionKind {
        ADVANCE_NEXT_BLOCK,
        PROPOSE_1RM_TEST,
        HOLD_INCOMPLETE,
    }

    data class TransitionContext(
        /** Null means no real AUGE snapshot was supplied; never invent 0/70. */
        val settings: Settings = Settings(),
        /** Día de referencia: una competición futura o de esta semana quita el test de 1RM tras el pico y tras el taper. */
        val today: LocalDate = LocalDate.now(),
    )

    data class TransitionDecision(
        val kind: DecisionKind,
        val message: String,
        val nextBlockId: String? = null,
        val updatedProgram: Program? = null,
    )

    data class BlockLocation(
        val macroIndex: Int,
        val blockIndex: Int,
        val block: Block,
        val weeks: List<ProgramWeek>,
    )

    fun locateBlock(program: Program, blockId: String): BlockLocation? {
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            macro.blocks.forEachIndexed { blockIndex, block ->
                if (block.id == blockId) {
                    val weeks = block.mesocycles.flatMap { it.weeks }
                    return BlockLocation(macroIndex, blockIndex, block, weeks)
                }
            }
        }
        return null
    }

    fun orderedBlocks(program: Program): List<Block> =
        program.macrocycles.flatMap { it.blocks }

    fun isLastWeekOfBlock(block: Block, weekId: String): Boolean {
        val weeks = block.mesocycles.flatMap { it.weeks }
        if (weeks.isEmpty()) return false
        return weeks.last().id == weekId
    }

    fun areBlockSessionsComplete(
        program: Program,
        block: Block,
        logs: List<WorkoutLog>,
        programRunId: String? = null,
    ): Boolean {
        val weeks = block.mesocycles.flatMap { it.weeks }
        if (weeks.isEmpty()) return false
        val cycle = if (program.requiresNativeWeekInstances()) program.runState?.cycleNumber ?: 1 else 1
        return weeks.all { week ->
            if (week.executionKind != com.example.kpkn.data.models.WeekExecutionKind.REST &&
                week.sessions.none(SessionTemplateEngine::sessionHasExecutableContent)
            ) {
                return@all false
            }
            ProgramProgressEngine.isWeekInstanceComplete(
                week = week,
                logs = logs,
                programId = program.id,
                instanceId = ProgramProgressEngine.instanceIdFor(cycle,week.id),
                cycleNumber = cycle,
                programRunId = programRunId,
                skippedSessionKeys = program.skippedSessionKeys,
            )
        }
    }

    fun evaluate(
        program: Program,
        completedBlockId: String,
        logs: List<WorkoutLog>,
        context: TransitionContext = TransitionContext(),
        activeState: ActiveProgramState? = null,
    ): TransitionDecision {
        if (program.structure != ProgramStructure.COMPLEX) {
            return TransitionDecision(
                kind = DecisionKind.HOLD_INCOMPLETE,
                message = "La transición por bloques solo aplica a programas COMPLEX.",
            )
        }
        val location = locateBlock(program, completedBlockId)
            ?: return TransitionDecision(DecisionKind.HOLD_INCOMPLETE, "Bloque no encontrado.")
        val block = location.block
        val complete = areBlockSessionsComplete(
            program,
            block,
            logs,
            program.runState?.runId ?: activeState?.programRunId,
        )
        if (!complete) {
            return TransitionDecision(
                kind = DecisionKind.HOLD_INCOMPLETE,
                message = "Hay sesiones incompletas en el bloque «${block.name}». Completa el bloque antes de avanzar.",
            )
        }

        val blocks = orderedBlocks(program)
        val blockPos = blocks.indexOfFirst { it.id == completedBlockId }
        val next = blocks.getOrNull(blockPos + 1)

        val goal = block.goal ?: block.mesocycles.firstOrNull()?.goal?.toBlockGoal()
        // Con competición futura o de esta semana, el pico y el taper llevan a ella: no hay test de 1RM tras ellos.
        val competitionAhead = hasCompetitionAhead(program, context.today)
        if (!competitionAhead && (goal == BlockGoal.REALIZATION || goal == BlockGoal.PEAK)) {
            // A taper/deload is the recovery runway for a peak/realization
            // block.  Do not gate before it: the final 1RM decision belongs
            // after that unload has actually been completed.
            val nextGoal = next?.goal ?: next?.mesocycles?.firstOrNull()?.goal?.toBlockGoal()
            if (next == null || (nextGoal != BlockGoal.TAPER && nextGoal != BlockGoal.DELOAD)) {
                return TransitionDecision(
                    kind = DecisionKind.PROPOSE_1RM_TEST,
                    message = "Bloque de realización/pico completado. Propón un test de 1RM antes del siguiente bloque.",
                    nextBlockId = next?.id,
                )
            }
        }

        // When a taper/deload immediately follows peak/realization, propose
        // the final test only after its reduced-volume work is complete.  The
        // previous block check prevents unrelated deloads from creating a
        // surprise 1RM gate.
        val previous = blocks.getOrNull(blockPos - 1)
        val previousGoal = previous?.goal ?: previous?.mesocycles?.firstOrNull()?.goal?.toBlockGoal()
        if (!competitionAhead && (goal == BlockGoal.TAPER || goal == BlockGoal.DELOAD) &&
            (previousGoal == BlockGoal.REALIZATION || previousGoal == BlockGoal.PEAK)
        ) {
            return TransitionDecision(
                kind = DecisionKind.PROPOSE_1RM_TEST,
                message = "Descarga/taper post-pico completado. Propón el test final de 1RM antes de continuar.",
                nextBlockId = next?.id,
            )
        }

        if (next == null) {
            return TransitionDecision(
                kind = DecisionKind.ADVANCE_NEXT_BLOCK,
                message = "Último bloque completado. Macrociclo finalizado.",
                nextBlockId = null,
            )
        }

        // A fixed/verified protocol is already materialized at day/set level.
        // Re-running a generic progression would overwrite its prescription.
        val progressed = if (next.hasExecutableSessions()) {
            next
        } else {
            val seed = block.mesocycles.flatMap { it.weeks }
                .lastOrNull { week -> week.sessions.any(SessionTemplateEngine::sessionHasExecutableContent) }
                ?.sessions
            if (seed.isNullOrEmpty()) next else BlockProgressionEngine.applyProgression(next, seedSessions = seed).block
        }
        val updated = replaceBlock(program, progressed)
        return TransitionDecision(
            kind = DecisionKind.ADVANCE_NEXT_BLOCK,
            message = "Avanzar a «${next.name}» con nueva prescripción de bloque.",
            nextBlockId = next.id,
            updatedProgram = updated,
        )
    }

    /**
     * Builds a real deload from the final executable week.  We intentionally do
     * not fabricate a blank generic week: an automatic transition must remain
     * trainable or it remains blocked for the editor to resolve.
     */


    /**
     * Hay una fecha clave COMPETITION futura o de esta semana (desde el lunes de [today]). Una competición pasada
     * no cuenta: el test de 1RM vuelve a proponerse como hasta ahora.
     */
    private fun hasCompetitionAhead(program: Program, today: LocalDate): Boolean {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return program.keyDates
            .filter { it.type == KeyDateType.COMPETITION }
            .any { keyDate ->
                val meet = runCatching { LocalDate.parse(keyDate.eventDate ?: keyDate.startDate) }.getOrNull()
                meet != null && !meet.isBefore(weekStart)
            }
    }

    private fun Block.hasExecutableSessions(): Boolean = mesocycles
        .flatMap { it.weeks }
        .any { week -> week.sessions.any(SessionTemplateEngine::sessionHasExecutableContent) }



    fun replaceBlock(program: Program, block: Block): Program {
        val macros = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { if (it.id == block.id) block else it })
        }
        return program.copy(macrocycles = macros)
    }

    private fun MesocycleGoal.toBlockGoal(): BlockGoal = when (this) {
        MesocycleGoal.ACCUMULATION -> BlockGoal.ACCUMULATION
        MesocycleGoal.INTENSIFICATION -> BlockGoal.INTENSIFICATION
        MesocycleGoal.REALIZATION -> BlockGoal.REALIZATION
        MesocycleGoal.DELOAD -> BlockGoal.DELOAD
        MesocycleGoal.CUSTOM -> BlockGoal.CUSTOM
    }

}
