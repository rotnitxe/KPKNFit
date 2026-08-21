package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.templates.SessionTemplateEngine

data class ProgramHierarchyLocation(
    val macrocycleId: String,
    val blockId: String,
    val mesocycleId: String,
    val weekId: String,
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
    val globalMesoIndex: Int,
    val globalWeekIndex: Int,
    val macrocycle: Macrocycle,
    val block: Block,
    val mesocycle: Mesocycle,
    val week: ProgramWeek,
)

data class ProgramSessionLocation(
    val sessionId: String,
    val sessionIndex: Int,
    val hierarchy: ProgramHierarchyLocation,
    val session: Session,
)

enum class ProgramHierarchyNodeType { MACROCYCLE, BLOCK, MESOCYCLE, WEEK, SESSION }

data class DuplicateProgramHierarchyId(
    val type: ProgramHierarchyNodeType,
    val id: String,
)

/** Canonical, immutable hierarchy index for one Program snapshot. IDs are identity. */
class ProgramHierarchyIndex(program: Program) {
    private val weekLocations: List<ProgramHierarchyLocation>
    private val sessionLocations: List<ProgramSessionLocation>
    val duplicateIds: List<DuplicateProgramHierarchyId>
    private val weeksById: Map<String, ProgramHierarchyLocation>
    private val sessionsById: Map<String, ProgramSessionLocation>

    init {
        val weeks = mutableListOf<ProgramHierarchyLocation>()
        val sessions = mutableListOf<ProgramSessionLocation>()
        val seen = mutableMapOf<ProgramHierarchyNodeType, MutableMap<String, Int>>()
        var globalMesoIndex = 0
        var globalWeekIndex = 0

        fun record(type: ProgramHierarchyNodeType, id: String) {
            val counts = seen.getOrPut(type) { mutableMapOf() }
            counts[id] = (counts[id] ?: 0) + 1
        }

        program.macrocycles.forEachIndexed { macroIndex, macro ->
            record(ProgramHierarchyNodeType.MACROCYCLE, macro.id)
            macro.blocks.forEachIndexed { blockIndex, block ->
                record(ProgramHierarchyNodeType.BLOCK, block.id)
                block.mesocycles.forEachIndexed { mesoIndex, meso ->
                    record(ProgramHierarchyNodeType.MESOCYCLE, meso.id)
                    meso.weeks.forEachIndexed { weekIndex, week ->
                        record(ProgramHierarchyNodeType.WEEK, week.id)
                        val location = ProgramHierarchyLocation(
                            macrocycleId = macro.id,
                            blockId = block.id,
                            mesocycleId = meso.id,
                            weekId = week.id,
                            macroIndex = macroIndex,
                            blockIndex = blockIndex,
                            mesoIndex = mesoIndex,
                            weekIndex = weekIndex,
                            globalMesoIndex = globalMesoIndex,
                            globalWeekIndex = globalWeekIndex++,
                            macrocycle = macro,
                            block = block,
                            mesocycle = meso,
                            week = week,
                        )
                        weeks += location
                        week.sessions.forEachIndexed { sessionIndex, session ->
                            record(ProgramHierarchyNodeType.SESSION, session.id)
                            sessions += ProgramSessionLocation(session.id, sessionIndex, location, session)
                        }
                    }
                    globalMesoIndex++
                }
            }
        }

        duplicateIds = seen.flatMap { (type, counts) ->
            counts.filterValues { it > 1 }.keys.map { DuplicateProgramHierarchyId(type, it) }
        }.sortedWith(compareBy({ it.type.ordinal }, { it.id }))
        val duplicateWeeks = duplicateIds.filter { it.type == ProgramHierarchyNodeType.WEEK }.mapTo(mutableSetOf()) { it.id }
        val duplicateSessions = duplicateIds.filter { it.type == ProgramHierarchyNodeType.SESSION }.mapTo(mutableSetOf()) { it.id }
        weekLocations = weeks
        sessionLocations = sessions
        weeksById = weeks.filterNot { it.weekId in duplicateWeeks }.associateBy { it.weekId }
        sessionsById = sessions.filterNot { it.sessionId in duplicateSessions }.associateBy { it.sessionId }
    }

    val isValid: Boolean get() = duplicateIds.isEmpty()
    fun locateWeek(weekId: String): ProgramHierarchyLocation? = weeksById[weekId]
    fun locateSession(sessionId: String): ProgramSessionLocation? = sessionsById[sessionId]
    fun orderedWeeks(): List<ProgramHierarchyLocation> = weekLocations
    fun orderedSessions(): List<ProgramSessionLocation> = sessionLocations
}

sealed interface ProgramStructureIssue {
    data class DuplicateId(val duplicate: DuplicateProgramHierarchyId) : ProgramStructureIssue
    data object SimpleRequiresOneMacrocycle : ProgramStructureIssue
    data object SimpleRequiresOneBlock : ProgramStructureIssue
    data object AdvancedRequiresOneMacrocycle : ProgramStructureIssue
}

object ProgramStructureContract {
    fun validate(program: Program): List<ProgramStructureIssue> = buildList {
        ProgramHierarchyIndex(program).duplicateIds.forEach { add(ProgramStructureIssue.DuplicateId(it)) }
        when (program.structure) {
            ProgramStructure.SIMPLE -> {
                if (program.macrocycles.size != 1) add(ProgramStructureIssue.SimpleRequiresOneMacrocycle)
                if (program.macrocycles.sumOf { it.blocks.size } != 1) add(ProgramStructureIssue.SimpleRequiresOneBlock)
            }
            ProgramStructure.COMPLEX -> if (program.macrocycles.size != 1) add(ProgramStructureIssue.AdvancedRequiresOneMacrocycle)
        }
    }
}

/**
 * Executable-plan gate used before a generated structure is persisted or
 * applied.  The hierarchy contract above intentionally stays permissive for
 * editor drafts; this second gate is strict about a plan that claims to be
 * runnable while still allowing an explicit REST week.
 */
sealed interface ProgramExecutionIssue {
    val message: String

    data class Structure(override val message: String) : ProgramExecutionIssue
    data class EmptyNode(override val message: String) : ProgramExecutionIssue
    data class EmptyTrainingWeek(override val message: String) : ProgramExecutionIssue
    data class PhaseOrder(override val message: String) : ProgramExecutionIssue
    data class PendingMaterialization(override val message: String) : ProgramExecutionIssue
    data class StaleCursor(override val message: String) : ProgramExecutionIssue
}

object ProgramExecutionContract {
    fun validate(program: Program): List<ProgramExecutionIssue> = buildList {
        ProgramStructureContract.validate(program).forEach { issue ->
            add(ProgramExecutionIssue.Structure(issue.toString()))
        }
        if (program.macrocycles.isEmpty()) {
            add(ProgramExecutionIssue.EmptyNode("El programa no tiene macrociclos."))
        }
        val blocks = program.macrocycles.flatMap { it.blocks }
        if (blocks.isEmpty()) {
            add(ProgramExecutionIssue.EmptyNode("El programa no tiene bloques materializables."))
        }

        fun phaseOf(block: com.example.kpkn.data.models.Block): Int? = when (block.goal) {
            com.example.kpkn.data.models.BlockGoal.ACCUMULATION -> 0
            com.example.kpkn.data.models.BlockGoal.INTENSIFICATION -> 1
            com.example.kpkn.data.models.BlockGoal.SPECIFICITY -> 2
            com.example.kpkn.data.models.BlockGoal.REALIZATION,
            com.example.kpkn.data.models.BlockGoal.PEAK -> 3
            com.example.kpkn.data.models.BlockGoal.DELOAD,
            com.example.kpkn.data.models.BlockGoal.TAPER -> 4
            else -> null
        }

        // Phase order is scoped to each macrocycle.  A new macrocycle may
        // intentionally restart at ACCUMULATION after the previous one tapered.
        program.macrocycles.forEach { macrocycle ->
            var previousPhase = -1
            macrocycle.blocks.forEach { block ->
            if (block.mesocycles.isEmpty()) {
                add(ProgramExecutionIssue.EmptyNode("El bloque '${block.name}' no tiene mesociclos."))
            }
            if (block.materializationPending) {
                add(ProgramExecutionIssue.PendingMaterialization("El bloque '${block.name}' requiere materializar su nueva prescripción."))
            }
            val phase = phaseOf(block)
            if (phase != null && previousPhase > phase) {
                add(ProgramExecutionIssue.PhaseOrder("El bloque '${block.name}' retrocede de fase ($previousPhase→$phase)."))
            }
            if (phase != null) previousPhase = phase

            block.mesocycles.forEach { meso ->
                if (meso.weeks.isEmpty()) {
                    add(ProgramExecutionIssue.EmptyNode("El mesociclo '${meso.name}' no tiene semanas."))
                }
                meso.weeks.forEach { week ->
                    if (week.executionKind != com.example.kpkn.data.models.WeekExecutionKind.REST) {
                        val requiredSessions = week.sessions.filter {
                            it.requirement == com.example.kpkn.data.models.SessionRequirement.REQUIRED
                        }
                        if (requiredSessions.any { !SessionTemplateEngine.sessionHasExecutableContent(it) }) {
                            add(ProgramExecutionIssue.EmptyTrainingWeek(
                                "La semana '${week.name}' contiene una sesión REQUIRED sin receta ejecutable.",
                            ))
                        } else if (week.sessions.none(SessionTemplateEngine::sessionHasExecutableContent)) {
                            add(ProgramExecutionIssue.EmptyTrainingWeek("La semana '${week.name}' está vacía y no es REST."))
                        }
                    }
                }
            }
            }
        }

        program.runState?.let { run ->
            val index = ProgramHierarchyIndex(program)
            if (run.weekId != null && index.locateWeek(run.weekId) == null) {
                add(ProgramExecutionIssue.StaleCursor("El cursor apunta a una semana inexistente (${run.weekId})."))
            }
            if (run.blockId != null && blocks.none { it.id == run.blockId }) {
                add(ProgramExecutionIssue.StaleCursor("El cursor apunta a un bloque inexistente (${run.blockId})."))
            }
            if (run.mesocycleId != null && blocks.flatMap { it.mesocycles }.none { it.id == run.mesocycleId }) {
                add(ProgramExecutionIssue.StaleCursor("El cursor apunta a un mesociclo inexistente (${run.mesocycleId})."))
            }
            if (run.pendingAction != null && run.status == com.example.kpkn.data.models.ProgramRunStatus.COMPLETED) {
                add(ProgramExecutionIssue.StaleCursor("Un programa completado conserva una acción pendiente."))
            }
        }
    }

    fun requireExecutable(program: Program): Program {
        val issues = validate(program)
        require(issues.isEmpty()) {
            issues.joinToString("; ") { it.message }
        }
        return program
    }
}

sealed interface ProgramStructureMutationResult {
    data class Success(val program: Program) : ProgramStructureMutationResult
    data class NotFound(val type: ProgramHierarchyNodeType, val id: String) : ProgramStructureMutationResult
    data class InvalidStructure(val issues: List<ProgramStructureIssue>) : ProgramStructureMutationResult
}

object ProgramStructureMutator {
    fun updateWeek(program: Program, weekId: String, transform: (ProgramWeek) -> ProgramWeek): ProgramStructureMutationResult {
        val index = ProgramHierarchyIndex(program)
        if (!index.isValid) return ProgramStructureMutationResult.InvalidStructure(ProgramStructureContract.validate(program))
        val target = index.locateWeek(weekId) ?: return ProgramStructureMutationResult.NotFound(ProgramHierarchyNodeType.WEEK, weekId)
        val updated = program.copy(macrocycles = program.macrocycles.map { macro ->
            if (macro.id != target.macrocycleId) macro else macro.copy(blocks = macro.blocks.map { block ->
                if (block.id != target.blockId) block else block.copy(mesocycles = block.mesocycles.map { meso ->
                    if (meso.id != target.mesocycleId) meso else meso.copy(weeks = meso.weeks.map { week ->
                        if (week.id == weekId) transform(week) else week
                    })
                })
            })
        })
        return ProgramStructureMutationResult.Success(updated)
    }

    fun updateSession(program: Program, sessionId: String, transform: (Session) -> Session): ProgramStructureMutationResult {
        val index = ProgramHierarchyIndex(program)
        if (!index.isValid) return ProgramStructureMutationResult.InvalidStructure(ProgramStructureContract.validate(program))
        val target = index.locateSession(sessionId) ?: return ProgramStructureMutationResult.NotFound(ProgramHierarchyNodeType.SESSION, sessionId)
        return updateWeek(program, target.hierarchy.weekId) { week ->
            week.copy(sessions = week.sessions.map { if (it.id == sessionId) transform(it) else it })
        }
    }

    fun removeMesocycle(program: Program, mesocycleId: String): ProgramStructureMutationResult {
        val index = ProgramHierarchyIndex(program)
        if (!index.isValid) return ProgramStructureMutationResult.InvalidStructure(ProgramStructureContract.validate(program))
        val exists = program.macrocycles.any { macro -> macro.blocks.any { block -> block.mesocycles.any { it.id == mesocycleId } } }
        if (!exists) return ProgramStructureMutationResult.NotFound(ProgramHierarchyNodeType.MESOCYCLE, mesocycleId)
        return ProgramStructureMutationResult.Success(program.copy(macrocycles = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block -> block.copy(mesocycles = block.mesocycles.filterNot { it.id == mesocycleId }) })
        }))
    }
}
