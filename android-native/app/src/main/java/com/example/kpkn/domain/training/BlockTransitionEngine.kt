package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.OvertrainingDetector
import com.example.kpkn.domain.templates.SessionClonePurpose
import com.example.kpkn.domain.templates.SessionTemplateEngine
import kotlin.math.ceil
import java.util.UUID

/**
 * Evalúa la transición al completar la última semana de un bloque COMPLEX.
 */
object BlockTransitionEngine {

    enum class DecisionKind {
        ADVANCE_NEXT_BLOCK,
        INSERT_DELOAD,
        PROPOSE_1RM_TEST,
        HOLD_INCOMPLETE,
    }

    data class TransitionContext(
        /** Null means no real AUGE snapshot was supplied; never invent 0/70. */
        val cumulativeFatigue: Double? = null,
        val readinessScore: Int? = null,
        val settings: Settings = Settings(),
        val mesocycleStressEma: Double = 0.0,
        val overtrainedMuscles: List<String> = emptyList(),
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
                instanceId = week.id,
                cycleNumber = 1,
                programRunId = programRunId,
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

        val hasAugeSnapshot = context.cumulativeFatigue != null && context.readinessScore != null
        val suggestDeload = (hasAugeSnapshot && AugeFatigueEngine.shouldSuggestAutoDeload(
            cumulativeFatigue = context.cumulativeFatigue!!,
            readinessScore = context.readinessScore!!,
            settings = context.settings,
        )) || context.mesocycleStressEma >= 75.0 || context.overtrainedMuscles.isNotEmpty()

        val blocks = orderedBlocks(program)
        val blockPos = blocks.indexOfFirst { it.id == completedBlockId }
        val next = blocks.getOrNull(blockPos + 1)

        val goal = block.goal ?: block.mesocycles.firstOrNull()?.goal?.toBlockGoal()
        if (goal == BlockGoal.REALIZATION || goal == BlockGoal.PEAK) {
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
        if ((goal == BlockGoal.TAPER || goal == BlockGoal.DELOAD) &&
            (previousGoal == BlockGoal.REALIZATION || previousGoal == BlockGoal.PEAK)
        ) {
            return TransitionDecision(
                kind = DecisionKind.PROPOSE_1RM_TEST,
                message = "Descarga/taper post-pico completado. Propón el test final de 1RM antes de continuar.",
                nextBlockId = next?.id,
            )
        }

        if (suggestDeload && next?.goal != BlockGoal.DELOAD && next?.goal != BlockGoal.TAPER) {
            val withDeload = insertDeloadBlockAfter(program, completedBlockId)
            if (withDeload == null) {
                return TransitionDecision(
                    kind = DecisionKind.HOLD_INCOMPLETE,
                    message = "El gate de descarga se activó, pero el bloque previo no tiene sesiones ejecutables para construir una descarga segura.",
                )
            }
            return TransitionDecision(
                kind = DecisionKind.INSERT_DELOAD,
                message = "Gate AUGE / estrés alto: se inserta un bloque de descarga antes de continuar.",
                nextBlockId = withDeload.second,
                updatedProgram = withDeload.first,
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
    fun insertDeloadBlockAfter(program: Program, afterBlockId: String): Pair<Program, String>? {
        val sourceBlock = locateBlock(program, afterBlockId)?.block ?: return null
        val sourceSessions = sourceBlock.mesocycles.flatMap { it.weeks }
                .lastOrNull { week -> week.sessions.any(SessionTemplateEngine::sessionHasExecutableContent) }
                ?.sessions
                ?.filter(SessionTemplateEngine::sessionHasExecutableContent)
            .orEmpty()
        if (sourceSessions.isEmpty()) return null

        val deloadId = UUID.randomUUID().toString()
        val deloadWeekId = UUID.randomUUID().toString()
        val deload = Block(
            id = deloadId,
            name = "Descarga (auto)",
            description = "Insertado por gate AUGE / sobreentrenamiento al cerrar el bloque anterior.",
            goal = BlockGoal.DELOAD,
            progressionScheme = BlockProgressionScheme.PERCENT_RM,
            mesocycles = listOf(
                Mesocycle(
                    id = UUID.randomUUID().toString(),
                    name = "Descarga",
                    goal = MesocycleGoal.DELOAD,
                    weeks = listOf(
                        ProgramWeek(
                            id = deloadWeekId,
                            name = "Semana 1",
                            progressionIndex = 1,
                            executionKind = com.example.kpkn.data.models.WeekExecutionKind.DELOAD,
                            sessions = sourceSessions.map(::scaleSessionForDeload),
                        ),
                    ),
                ),
            ),
        )
        val macros = program.macrocycles.map { macro ->
            val idx = macro.blocks.indexOfFirst { it.id == afterBlockId }
            if (idx < 0) macro
            else macro.copy(blocks = macro.blocks.take(idx + 1) + deload + macro.blocks.drop(idx + 1))
        }
        return program.copy(macrocycles = macros) to deloadId
    }

    private fun Block.hasExecutableSessions(): Boolean = mesocycles
        .flatMap { it.weeks }
        .any { week -> week.sessions.any(SessionTemplateEngine::sessionHasExecutableContent) }

    private fun scaleSessionForDeload(source: com.example.kpkn.data.models.Session): com.example.kpkn.data.models.Session {
        val cloned = SessionTemplateEngine.cloneSessionContent(source, SessionClonePurpose.PROGRESSION_SEED)
        fun scale(exercise: com.example.kpkn.data.models.Exercise): com.example.kpkn.data.models.Exercise {
            if (exercise.sets.isEmpty()) return exercise
            val keepSets = ceil(exercise.sets.size * 0.6).toInt().coerceAtLeast(1)
            return exercise.copy(
                sets = exercise.sets.take(keepSets).map { set ->
                    val percentage = set.targetPercentageRM?.times(0.85)?.coerceAtMost(65.0)
                    set.copy(
                        targetPercentageRM = percentage,
                        targetRPE = (set.targetRPE ?: 6.0).coerceAtMost(6.0),
                        intensityMode = if (percentage != null) {
                            com.example.kpkn.data.models.IntensityMode.SOLO_RM
                        } else {
                            com.example.kpkn.data.models.IntensityMode.RPE
                        },
                        isAmrap = false,
                        isCalibrator = false,
                    )
                },
            )
        }
        fun scaleVariant(session: com.example.kpkn.data.models.Session): com.example.kpkn.data.models.Session {
            return session.copy(
                exercises = session.exercises.map(::scale),
                parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::scale)) },
                // Variants are executable alternatives of the same weekly
                // prescription.  Leaving B/C/D at the original intensity
                // would let a deload silently turn back into the prior block.
                sessionB = session.sessionB?.let(::scaleVariant),
                sessionC = session.sessionC?.let(::scaleVariant),
                sessionD = session.sessionD?.let(::scaleVariant),
            )
        }
        return scaleVariant(cloned)
    }

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

    /** Helper para callers que ya tienen logs/feedbacks y quieren rellenar overtraining. */
    fun detectOvertrained(
        program: Program,
        logs: List<WorkoutLog>,
    ): List<String> = OvertrainingDetector.detectOvertrainedMuscles(
        program = program,
        historyLogs = logs,
        feedbacks = emptyList(),
    )
}
