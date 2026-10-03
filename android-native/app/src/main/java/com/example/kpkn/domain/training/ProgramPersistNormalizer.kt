package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.domain.exercises.normalizedIdentityFields

/**
 * Single persist path for program blobs: temporal metadata, loop occurrences,
 * stale run cursors, then calendar materialization.
 */
object ProgramPersistNormalizer {
    fun normalize(program: Program): Program {
        val aligned = program.alignTemporalMetadata()
        val synced = LoopEngine.syncOccurrences(aligned)
        val repaired = repairStaleRunCursor(synced)
        val dated = ProgramCalendarEngine.materializeWeekDates(repaired)
        val stripped = CompetitionKeyDateSync.stripMeetSessions(dated)
        val competition = ProgramKeyDateEngine.competitionKeyDate(stripped) ?: return stripped
        return ProgramKeyDateEngine.syncCompetitionLinkedEntities(stripped, competition, null)
    }

    fun repairStaleRunCursor(program: Program): Program {
        val run = program.runState ?: return program
        val index = ProgramHierarchyIndex(program)
        val locations = index.orderedWeeks()
        if (locations.isEmpty()) {
            return program.copy(
                runState = run.copy(
                    weekId = null,
                    weekInstanceId = null,
                    blockId = null,
                    mesocycleId = null,
                    macrocycleId = null,
                ),
            )
        }

        val requestedWeekId = run.weekId?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
        val weekLocation = requestedWeekId?.let(index::locateWeek)
        val blocks = program.macrocycles.flatMap { it.blocks }
        val blockOk = run.blockId == null || blocks.any { it.id == run.blockId }
        val mesoOk = run.mesocycleId == null || blocks.flatMap { it.mesocycles }.any { it.id == run.mesocycleId }
        if (weekLocation != null && blockOk && mesoOk) return program

        val fallback = weekLocation
            ?: locations.firstOrNull { it.blockId == run.blockId }
            ?: locations.firstOrNull { it.mesocycleId == run.mesocycleId }
            ?: locations.first()
        return program.copy(
            runState = run.copy(
                weekId = fallback.week.id,
                weekInstanceId = run.weekInstanceId?.takeIf { weekLocation != null } ?: fallback.week.id,
                blockId = fallback.blockId,
                mesocycleId = fallback.mesocycleId,
                macrocycleId = fallback.macrocycleId,
            ),
        )
    }

    fun withRunStatus(program: Program, run: ProgramRunState, status: com.example.kpkn.data.models.ProgramRunStatus): Program =
        program.copy(runState = run.copy(status = status))

    /** Forma canónica del JSON en Room: misma ruta que init/repository. */
    fun forRoomStorage(program: Program): Program =
        normalize(ProgramMigrationEngine.migrateIfNeeded(program).program)
            .let(::repairNativeRunWeekCursor)
            .normalizedIdentityFields()

    /** Asegura que el run cursor nativo use ids de instancia de ciclo, no plantillas sueltas. */
    fun repairNativeRunWeekCursor(program: Program): Program {
        if (!program.requiresNativeWeekInstances()) return program
        val run = program.runState ?: return program
        val templateWeekId = run.weekId?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
            ?: return program
        val coerced = coerceNativeWeekInstanceId(run.cycleNumber, templateWeekId, run.weekInstanceId)
        if (coerced == run.weekInstanceId && run.weekId == templateWeekId) return program
        return program.copy(
            runState = run.copy(
                weekId = templateWeekId,
                weekInstanceId = coerced,
            ),
        )
    }
}
