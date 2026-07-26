package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CalendarBreak
import com.example.kpkn.data.models.LoopOccurrence
import com.example.kpkn.data.models.LoopStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TemporalStructureIssue
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.restorePausedCyclicProgram
import com.example.kpkn.data.models.suggestCalendarTrainingDays
import com.example.kpkn.data.models.totalBlockCount
import com.example.kpkn.data.models.validateTemporalStructure
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Migración segura de programas legacy al contrato Simple/Avanzado explícito.
 * Aísla programas corruptos y crea respaldo lógico antes de transformar.
 */
object ProgramMigrationEngine {

    data class MigrationResult(
        val program: Program,
        val issues: List<TemporalStructureIssue> = emptyList(),
        val migrated: Boolean = false,
    )

    data class LoadResult(
        val programs: List<Program>,
        val corruptedIds: List<String> = emptyList(),
    )

    fun migrateIfNeeded(
        program: Program,
        clock: AppClock = SystemAppClock,
    ): MigrationResult {
        val issues = program.validateTemporalStructure()
        var migrated = false
        var current = program

        if (current.schedulePlan == null) {
            current = current.copy(schedulePlan = buildSchedulePlanFromLegacy(current))
            migrated = true
        }

        if (shouldPromoteToComplex(current)) {
            if (current.structure != ProgramStructure.COMPLEX) {
                current = current.copy(structure = ProgramStructure.COMPLEX)
                migrated = true
            }
        }

        if (current.structure == ProgramStructure.COMPLEX && current.macrocycles.size > 1) {
            current = flattenToSingleMacrocycle(current)
            migrated = true
        }

        if (current.isSimpleProgram && current.simpleProgramKind == SimpleProgramKind.CALENDARIZED) {
            val breakFromSnapshot = migrateExpiredSimpleDatedIfNeeded(current, clock)
            if (breakFromSnapshot != null) {
                current = breakFromSnapshot
                migrated = true
            }
        }

        val loopOccurrences = buildLoopOccurrencesFromLegacy(current)
        if (loopOccurrences.isNotEmpty() && current.loopOccurrences.isEmpty()) {
            current = LoopEngine.syncOccurrences(current.copy(loopOccurrences = loopOccurrences))
            migrated = true
        } else if (current.loops.isNotEmpty() && current.loopOccurrences.isEmpty()) {
            current = LoopEngine.syncOccurrences(current)
            migrated = true
        }

        if (current.runState == null && current.isSimpleProgram && current.simpleProgramKind == SimpleProgramKind.CYCLIC) {
            current = current.copy(
                runState = ProgramRunState(
                    runId = "run_${current.id}",
                    cycleNumber = (current.loopState?.currentCycle ?: 0).coerceAtLeast(1),
                ),
            )
            migrated = true
        }

        val aligned = current.alignTemporalMetadata()
        if (aligned != current) {
            current = aligned
            migrated = true
        }

        return MigrationResult(program = current, issues = issues, migrated = migrated)
    }

    fun loadProgramsSafely(rawPrograms: List<Pair<String, Program?>>): LoadResult {
        val valid = mutableListOf<Program>()
        val corrupted = mutableListOf<String>()
        rawPrograms.forEach { (id, program) ->
            if (program == null) {
                corrupted += id
            } else {
                valid += migrateIfNeeded(program).program
            }
        }
        return LoadResult(programs = valid, corruptedIds = corrupted)
    }

    /** Flatten legacy multi-macrocycle advanced programs into a single sequential macrocycle. */
    fun flattenToSingleMacrocycle(program: Program): Program {
        if (program.macrocycles.size <= 1) return program
        val allBlocks = program.macrocycles.flatMap { it.blocks }
        val first = program.macrocycles.first()
        return program.copy(
            macrocycles = listOf(
                first.copy(
                    id = first.id.ifBlank { "macro_${program.id}" },
                    name = first.name.ifBlank { "Macrociclo" },
                    blocks = allBlocks,
                ),
            ),
        )
    }

    private fun shouldPromoteToComplex(program: Program): Boolean {
        if (program.structure == ProgramStructure.COMPLEX) return false
        if (program.totalBlockCount > 1) return true
        if (program.macrocycles.size > 1) return true
        if (program.calendarization?.mode == ProgramCalendarizationMode.ADVANCED_COMPETITION) return true
        if (program.keyDates.isNotEmpty()) return true
        if (!program.structureTemplateId.isNullOrBlank() && program.structureTemplateId!!.startsWith("power-")) return true
        return false
    }

    private fun buildSchedulePlanFromLegacy(program: Program): ProgramSchedulePlan {
        val mode = when {
            program.calendarization != null && !program.timelineStartDate.isNullOrBlank() -> ScheduleMode.DATED
            else -> ScheduleMode.FLOATING
        }
        val trainingDays = program.suggestCalendarTrainingDays()
        return ProgramSchedulePlan(
            anchorDate = program.timelineStartDate,
            weekStartDay = program.startDay,
            trainingDays = trainingDays,
            targetEndDate = program.calendarization?.manualEndDate,
            mode = mode,
        )
    }

    /**
     * Reaplica migraciones temporales (p. ej. calendarización expirada) sin reiniciar el proceso.
     * Idempotente: solo persiste cuando el programa cambia.
     */
    fun reconcileExpiredCalendarization(
        program: Program,
        clock: AppClock = SystemAppClock,
    ): MigrationResult = migrateIfNeeded(program, clock)

    /**
     * Solo archiva calendarizaciones **expiradas**.
     * Las calendarizaciones activas (fecha final >= hoy) permanecen CALENDARIZED
     * tras reiniciar; no se confunden con legacy.
     */
    internal fun migrateExpiredSimpleDatedIfNeeded(
        program: Program,
        clock: AppClock,
    ): Program? {
        val snapshot = program.pausedCyclicSnapshot ?: return null
        if (program.calendarization?.mode != ProgramCalendarizationMode.SIMPLE_DATED) return null
        val start = program.timelineStartDate ?: return null
        val endRaw = resolveCalendarizedEndDate(program) ?: return null
        val endDate = parseIsoDate(endRaw) ?: return null
        val today = clock.today(ZoneId.systemDefault())

        // Active or same-day calendarization must survive process death.
        if (!today.isAfter(endDate)) return null

        val alreadyArchived = program.calendarBreaks.any { breakEntry ->
            breakEntry.startDate == start &&
                (breakEntry.endDate == endRaw || parseIsoDate(breakEntry.endDate)?.isEqual(endDate) == true)
        }
        if (alreadyArchived) {
            return program.restorePausedCyclicProgram()
        }

        val breakWeeks = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }

        val breakEntry = CalendarBreak(
            id = "break_${program.id}_${start}",
            title = "Break calendarizado",
            startDate = start,
            endDate = endRaw,
            weeks = breakWeeks,
            pausedRunState = snapshot.runState ?: program.runState?.copy(status = ProgramRunStatus.ACTIVE),
            pausedCyclicSnapshot = snapshot,
        )

        return program.copy(
            calendarBreaks = program.calendarBreaks + breakEntry,
            macrocycles = snapshot.macrocycles,
            loops = snapshot.loops,
            loopState = snapshot.loopState,
            events = snapshot.events,
            selectedSplitId = snapshot.selectedSplitId,
            customSplitPattern = snapshot.customSplitPattern,
            customSplitName = snapshot.customSplitName,
            customSplitDescription = snapshot.customSplitDescription,
            blockSplitSelections = snapshot.blockSplitSelections,
            weekSplitSelections = snapshot.weekSplitSelections,
            runState = (snapshot.runState ?: program.runState)?.copy(status = ProgramRunStatus.ACTIVE),
            schedulePlan = snapshot.schedulePlan?.copy(
                mode = ScheduleMode.FLOATING,
                anchorDate = null,
                targetEndDate = null,
            ) ?: program.schedulePlan?.copy(
                mode = ScheduleMode.FLOATING,
                anchorDate = null,
                targetEndDate = null,
            ),
            loopOccurrences = snapshot.loopOccurrences,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            calendarization = null,
            pausedCyclicSnapshot = null,
            timelineStartDate = null,
        )
    }

    private fun resolveCalendarizedEndDate(program: Program): String? {
        program.calendarization?.manualEndDate?.takeIf { it.isNotBlank() }?.let { return it }
        program.schedulePlan?.targetEndDate?.takeIf { it.isNotBlank() }?.let { return it }
        return program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .mapNotNull { it.endDate }
            .maxOrNull()
            ?: program.timelineStartDate
    }

    private fun parseIsoDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    private fun buildLoopOccurrencesFromLegacy(program: Program): List<LoopOccurrence> {
        if (!program.isSimpleProgram || program.loops.isEmpty()) return emptyList()
        val currentCycle = program.loopState?.currentCycle ?: 0
        val projections = LoopEngine.projectLoops(program, fromCycle = currentCycle, lookAheadCycles = 24)
        return projections.map { projection ->
            LoopOccurrence(
                id = "occ_${projection.loop.id}_${projection.cycle}",
                loopId = projection.loop.id,
                cycleNumber = projection.cycle,
                scheduledCycle = projection.cycle,
                status = when {
                    projection.isCancelled -> LoopStatus.CANCELLED
                    projection.isPostponed -> LoopStatus.POSTPONED
                    projection.daysUntil <= 0 -> LoopStatus.ACTIVE
                    else -> LoopStatus.SCHEDULED
                },
                weekInstanceId = "loop_week_${projection.loop.id}",
            )
        }
    }
}
