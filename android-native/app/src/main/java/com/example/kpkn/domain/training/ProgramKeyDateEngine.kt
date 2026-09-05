package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.data.repository.CompetitionRepository
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object ProgramKeyDateEngine {

    enum class KeyDateDeleteMode {
        UNLINK_SESSION,
        ARCHIVE_SESSION_AND_RECORD,
    }

    fun competitionKeyDate(program: Program): ProgramKeyDate? =
        program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }

    fun linkedCompetitionSessionCount(program: Program, keyDateId: String): Int =
        program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
            .count { it.competitionKeyDateId == keyDateId }

    fun hasLinkedCompetitionEntities(
        program: Program,
        keyDateId: String,
        competitionRepository: CompetitionRepository? = null,
    ): Boolean {
        if (linkedCompetitionSessionCount(program, keyDateId) > 0) return true
        return competitionRepository?.records?.value?.any {
            it.keyDateId == keyDateId && it.plannedProgramId == program.id
        } == true
    }

    data class CalendarSaveResult(
        val program: Program,
        val competitionKeyDate: ProgramKeyDate?,
        val competitionMoved: Boolean = false,
    )

    enum class KeyDateFitStatus {
        /** El programa termina exactamente en la semana de la fecha clave. */
        FITS,
        /** Faltan semanas para alcanzar la fecha clave: hay que agregar bloques/semanas. */
        NEEDS_MORE_WEEKS,
        /** El programa se extiende más allá de la fecha clave: hay semanas sobrantes. */
        HAS_SURPLUS_WEEKS,
        /** La fecha clave cae antes del inicio del programa. */
        BEFORE_PROGRAM_START,
        /** Falta la fecha de inicio del programa o la fecha del evento para poder calcular. */
        MISSING_DATA,
    }

    /**
     * Reporte de calce entre la estructura actual del programa (semanas totales) y las
     * semanas necesarias para llegar a [keyDateId] (típicamente el día de competición/meet day).
     */
    data class KeyDateFitReport(
        val keyDateId: String,
        val status: KeyDateFitStatus,
        val currentTotalWeeks: Int,
        val requiredWeeks: Int?,
        /** Positivo = faltan N semanas (déficit). Negativo = sobran N semanas (superávit). */
        val weeksGap: Int,
    ) {
        val needsMoreWeeks: Boolean get() = status == KeyDateFitStatus.NEEDS_MORE_WEEKS
        val hasSurplusWeeks: Boolean get() = status == KeyDateFitStatus.HAS_SURPLUS_WEEKS
        val fitsExactly: Boolean get() = status == KeyDateFitStatus.FITS

        /** Semanas a agregar/quitar para calzar; siempre positivo o cero. */
        val actionableWeeks: Int get() = kotlin.math.abs(weeksGap)
    }

    /**
     * Compara las semanas totales del programa contra las semanas necesarias para que
     * [keyDate] (fecha de evento o inicio) caiga dentro del programa, asumiendo semanas de 7 días
     * contadas desde [Program.timelineStartDate].
     */
    fun fitBlocksToKeyDate(program: Program, keyDate: ProgramKeyDate): KeyDateFitReport {
        val currentTotalWeeks = program.totalProgramWeeks
        val startDate = parseDate(program.resolvedSchedulePlan().anchorDate)
        val eventDate = parseDate(keyDate.eventDate ?: keyDate.startDate)
        if (startDate == null || eventDate == null) {
            return KeyDateFitReport(
                keyDateId = keyDate.id,
                status = KeyDateFitStatus.MISSING_DATA,
                currentTotalWeeks = currentTotalWeeks,
                requiredWeeks = null,
                weeksGap = 0,
            )
        }
        val daysBetween = ChronoUnit.DAYS.between(startDate, eventDate)
        if (daysBetween < 0) {
            return KeyDateFitReport(
                keyDateId = keyDate.id,
                status = KeyDateFitStatus.BEFORE_PROGRAM_START,
                currentTotalWeeks = currentTotalWeeks,
                requiredWeeks = 0,
                weeksGap = -currentTotalWeeks,
            )
        }
        val requiredWeeks = requiredWeeksFromAnchor(program, startDate, eventDate)
        val gap = requiredWeeks - currentTotalWeeks
        val status = when {
            gap > 0 -> KeyDateFitStatus.NEEDS_MORE_WEEKS
            gap < 0 -> KeyDateFitStatus.HAS_SURPLUS_WEEKS
            else -> KeyDateFitStatus.FITS
        }
        return KeyDateFitReport(
            keyDateId = keyDate.id,
            status = status,
            currentTotalWeeks = currentTotalWeeks,
            requiredWeeks = requiredWeeks,
            weeksGap = gap,
        )
    }

    /** Atajo para el caso más común: calzar contra la fecha de competición del programa. */
    fun fitBlocksToCompetitionKeyDate(program: Program): KeyDateFitReport? =
        competitionKeyDate(program)?.let { fitBlocksToKeyDate(program, it) }

    /**
     * Cuenta ventanas de 7 días desde el ancla, igual que [ProgramCalendarEngine.project].
     * Si el programa está calendarizado, usa la proyección real (salta semanas loop).
     */
    private fun requiredWeeksFromAnchor(program: Program, startDate: LocalDate, eventDate: LocalDate): Int {
        val projection = ProgramCalendarEngine.project(program)
        if (projection.enabled && projection.weeks.isNotEmpty()) {
            val containing = projection.weeks.indexOfFirst { it.contains(eventDate) }
            if (containing >= 0) return containing + 1
            val lastEnd = projection.weeks.last().endDate
            if (eventDate.isAfter(lastEnd)) {
                val extraDays = ChronoUnit.DAYS.between(lastEnd.plusDays(1), eventDate).coerceAtLeast(0)
                return projection.weeks.size + ((extraDays / 7) + 1).toInt()
            }
        }
        var cursor = startDate
        var count = 0
        while (count < 520) {
            count++
            val end = cursor.plusDays(6)
            if (!eventDate.isBefore(cursor) && !eventDate.isAfter(end)) return count
            cursor = end.plusDays(1)
        }
        return count
    }

    /** Agrega [count] semanas nuevas al final del bloque [blockId]. No-op si el bloque no existe. */
    fun addWeeksToBlock(program: Program, blockId: String, count: Int, idProvider: IdProvider = UuidIdProvider): Program {
        if (count <= 0) return program
        val location = locateBlock(program, blockId) ?: return program
        val (macroIndex, blockIndex) = location
        return program.copy(
            macrocycles = program.macrocycles.mapIndexed { mi, macro ->
                if (mi != macroIndex) macro
                else macro.copy(
                    blocks = macro.blocks.mapIndexed { bi, block ->
                        if (bi != blockIndex) block else block.appendWeeks(count, idProvider)
                    },
                )
            },
        )
    }

    /**
     * Quita hasta [count] semanas desde el final del bloque [blockId], nunca dejándolo vacío.
     * No-op si el bloque no existe o ya tiene una sola semana.
     */
    fun removeWeeksFromBlock(program: Program, blockId: String, count: Int): Program {
        if (count <= 0) return program
        val location = locateBlock(program, blockId) ?: return program
        val (macroIndex, blockIndex) = location
        val block = program.macrocycles[macroIndex].blocks[blockIndex]
        val totalBlockWeeks = block.mesocycles.sumOf { it.weeks.size }
        val safeCount = count.coerceAtMost((totalBlockWeeks - 1).coerceAtLeast(0))
        if (safeCount <= 0) return program
        return program.copy(
            macrocycles = program.macrocycles.mapIndexed { mi, macro ->
                if (mi != macroIndex) macro
                else macro.copy(
                    blocks = macro.blocks.mapIndexed { bi, currentBlock ->
                        if (bi != blockIndex) currentBlock else currentBlock.trimTrailingWeeks(safeCount)
                    },
                )
            },
        )
    }

    private fun locateBlock(program: Program, blockId: String): Pair<Int, Int>? {
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            val blockIndex = macro.blocks.indexOfFirst { it.id == blockId }
            if (blockIndex >= 0) return macroIndex to blockIndex
        }
        return null
    }

    private fun Block.appendWeeks(count: Int, idProvider: IdProvider): Block {
        val existingWeeks = mesocycles.sumOf { it.weeks.size }
        val newWeeks = (1..count).map { offset ->
            ProgramWeek(id = idProvider.newId(), name = "Semana ${existingWeeks + offset}")
        }
        if (mesocycles.isEmpty()) {
            return copy(mesocycles = listOf(Mesocycle(id = idProvider.newId(), name = "Mesociclo 1", weeks = newWeeks)))
        }
        val lastMesoIndex = mesocycles.lastIndex
        return copy(
            mesocycles = mesocycles.mapIndexed { index, meso ->
                if (index == lastMesoIndex) meso.copy(weeks = meso.weeks + newWeeks) else meso
            },
        )
    }

    private fun Block.trimTrailingWeeks(count: Int): Block {
        var remaining = count
        val trimmed = mesocycles.asReversed().map { meso ->
            if (remaining <= 0) {
                meso
            } else {
                val keep = (meso.weeks.size - remaining).coerceAtLeast(0)
                remaining -= (meso.weeks.size - keep)
                meso.copy(weeks = meso.weeks.take(keep))
            }
        }.asReversed()
        return copy(mesocycles = trimmed.filter { it.weeks.isNotEmpty() })
    }

    fun validate(keyDate: ProgramKeyDate): String? {
        val start = parseDate(keyDate.startDate)
        if (start == null) return "La fecha de inicio no es válida."
        val end = keyDate.endDate?.let { parseDate(it) }
        if (keyDate.endDate != null && end == null) return "La fecha de fin no es válida."
        if (end != null && end.isBefore(start)) return "La fecha de fin no puede ser anterior al inicio."
        if (keyDate.type == KeyDateType.COMPETITION) {
            val event = keyDate.eventDate?.let { parseDate(it) }
            if (event == null) return "La competición requiere un día de evento válido."
        }
        return null
    }

    fun upsertKeyDate(program: Program, keyDate: ProgramKeyDate): Program {
        val normalized = normalizeKeyDate(keyDate)
        val next = program.keyDates.filterNot { it.id == normalized.id } + normalized
        return program.copy(keyDates = next.sortedBy { it.startDate })
    }

    fun deleteKeyDate(
        program: Program,
        keyDateId: String,
        mode: KeyDateDeleteMode = KeyDateDeleteMode.UNLINK_SESSION,
        competitionRepository: CompetitionRepository? = null,
    ): Program {
        val keyDate = program.keyDates.firstOrNull { it.id == keyDateId } ?: return program
        var updated = program.copy(keyDates = program.keyDates.filterNot { it.id == keyDateId })
        if (keyDate.type == KeyDateType.COMPETITION) {
            updated = unlinkCompetitionEntities(updated, keyDate, mode, competitionRepository)
            if (updated.calendarization?.mode == ProgramCalendarizationMode.ADVANCED_COMPETITION &&
                updated.keyDates.none { it.type == KeyDateType.COMPETITION }
            ) {
                updated = updated.copy(calendarization = updated.calendarization?.copy(activatedByCompetition = false))
            }
        }
        return updated
    }

    fun applyAdvancedCalendarSave(
        program: Program,
        timelineStartDate: String?,
        competitionDate: String?,
        manualEndDate: String?,
        competitionRepository: CompetitionRepository? = null,
        idProvider: IdProvider = UuidIdProvider,
    ): CalendarSaveResult {
        val startRaw = timelineStartDate?.trim().orEmpty()
        val competitionRaw = competitionDate?.trim().orEmpty()
        val manualRaw = manualEndDate?.trim().orEmpty()

        val competitionKeyDate = competitionRaw.takeIf { it.isNotBlank() }?.let { date ->
            val calendarProgram = program.copy(
                timelineStartDate = startRaw.ifBlank { null },
                calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization().copy(
                    manualEndDate = manualRaw.ifBlank { null },
                ),
                keyDates = program.keyDates.filterNot { it.type == KeyDateType.COMPETITION },
            )
            val competitionDay = parseDate(date)
            val assignedWeek = competitionDay?.let { findProgramWeekRange(calendarProgram, it) }
            ProgramKeyDate(
                id = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }?.id
                    ?: idProvider.newId(),
                title = "Competición",
                type = KeyDateType.COMPETITION,
                startDate = assignedWeek?.first?.toString() ?: date,
                endDate = assignedWeek?.second?.toString(),
                eventDate = date,
            )
        }

        val calendarization = when {
            competitionKeyDate != null -> ProgramCalendarEngine.defaultCompetitionCalendarization().copy(
                manualEndDate = manualRaw.ifBlank { null },
            )
            startRaw.isNotBlank() -> {
                val base = program.calendarization
                    ?: ProgramCalendarEngine.defaultAdvancedDatedCalendarization()
                base.copy(
                    manualEndDate = manualRaw.ifBlank { null },
                    activatedByCompetition = false,
                )
            }
            program.calendarization != null -> program.calendarization.copy(
                manualEndDate = manualRaw.ifBlank { null },
            )
            else -> null
        }

        var updated = program.copy(
            timelineStartDate = startRaw.ifBlank { null },
            calendarization = calendarization,
            keyDates = program.keyDates.filterNot { it.type == KeyDateType.COMPETITION } +
                listOfNotNull(competitionKeyDate),
            schedulePlan = program.schedulePlan?.copy(
                anchorDate = startRaw.ifBlank { null },
                targetEndDate = manualRaw.ifBlank { null },
                mode = if (startRaw.isNotBlank()) ScheduleMode.DATED else ScheduleMode.FLOATING,
            ) ?: program.resolvedSchedulePlan().copy(
                anchorDate = startRaw.ifBlank { null },
                targetEndDate = manualRaw.ifBlank { null },
                mode = if (startRaw.isNotBlank()) ScheduleMode.DATED else ScheduleMode.FLOATING,
            ),
        )

        val previousCompetition = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }
        val competitionMoved = competitionKeyDate != null &&
            previousCompetition != null &&
            previousCompetition.eventDate != competitionKeyDate.eventDate

        // Materialize dated weeks first so competition placement uses the new timeline.
        updated = ProgramCalendarEngine.materializeWeekDates(updated)

        if (competitionKeyDate != null) {
            updated = syncCompetitionLinkedEntities(
                program = updated,
                keyDate = competitionKeyDate,
                competitionRepository = competitionRepository,
            )
        } else {
            updated = CompetitionKeyDateSync.stripMeetSessions(updated)
        }

        return CalendarSaveResult(
            program = updated,
            competitionKeyDate = competitionKeyDate,
            competitionMoved = competitionMoved,
        )
    }

    fun syncCompetitionLinkedEntities(
        program: Program,
        keyDate: ProgramKeyDate,
        competitionRepository: CompetitionRepository?,
    ): Program {
        if (keyDate.type != KeyDateType.COMPETITION) return CompetitionKeyDateSync.stripMeetSessions(program)
        val weekDay = locateCompetitionWeekDay(program, keyDate)
        val targetWeekId = weekDay?.first
        val migration = CompetitionKeyDateSync.migrate(
            program = program,
            existingRecords = competitionRepository?.records?.value.orEmpty(),
            competitionWeekId = targetWeekId,
        )
        competitionRepository?.let { repo ->
            migration.recordsToUpsert.forEach(repo::upsert)
            val existing = repo.records.value.firstOrNull {
                it.keyDateId == keyDate.id && it.plannedProgramId == program.id
            } ?: migration.recordsToUpsert.firstOrNull { it.keyDateId == keyDate.id }
            repo.upsert(
                CompetitionKeyDateSync.mergeFromKeyDate(
                    keyDate = keyDate,
                    existing = existing,
                    programId = program.id,
                    weekId = targetWeekId,
                ),
            )
        }
        return migration.program
    }

    private fun unlinkCompetitionEntities(
        program: Program,
        keyDate: ProgramKeyDate,
        mode: KeyDateDeleteMode,
        competitionRepository: CompetitionRepository?,
    ): Program {
        val withoutLinkedSessions = program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        week.copy(
                                            sessions = week.sessions.filterNot { session ->
                                                session.competitionKeyDateId == keyDate.id || session.isCompetitionMeet
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        competitionRepository?.let { repo ->
            repo.records.value
                .filter { it.keyDateId == keyDate.id && it.plannedProgramId == program.id }
                .forEach { record ->
                    when (mode) {
                        KeyDateDeleteMode.UNLINK_SESSION -> repo.upsert(
                            record.copy(keyDateId = null, plannedSessionId = null, plannedWeekId = null),
                        )
                        KeyDateDeleteMode.ARCHIVE_SESSION_AND_RECORD -> repo.delete(record.id)
                    }
                }
        }

        return CompetitionKeyDateSync.stripMeetSessions(withoutLinkedSessions)
    }

    private fun normalizeKeyDate(keyDate: ProgramKeyDate): ProgramKeyDate {
        val start = parseDate(keyDate.startDate) ?: return keyDate
        val end = keyDate.endDate?.let { parseDate(it) } ?: start
        val event = when (keyDate.type) {
            KeyDateType.COMPETITION -> keyDate.eventDate?.let { parseDate(it) } ?: start
            else -> keyDate.eventDate?.let { parseDate(it) }
        }
        return keyDate.copy(
            startDate = start.toString(),
            endDate = if (keyDate.endDate.isNullOrBlank()) null else end.toString(),
            eventDate = event?.toString(),
        )
    }

    fun locateCompetitionWeekDay(program: Program, keyDate: ProgramKeyDate): Pair<String, Int>? {
        val eventDate = parseDate(keyDate.eventDate ?: keyDate.startDate) ?: return null
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        val dayByTrainingDate = week.trainingDayDates.entries.firstOrNull { (_, date) ->
                            parseDate(date) == eventDate
                        }?.key
                        if (dayByTrainingDate != null) return week.id to dayByTrainingDate
                        val start = parseDate(week.startDate)
                        val end = parseDate(week.endDate)
                        if (start != null && end != null &&
                            !eventDate.isBefore(start) && !eventDate.isAfter(end)
                        ) {
                            return week.id to eventDate.dayOfWeek.value
                        }
                    }
                }
            }
        }
        return null
    }

    fun findProgramWeekRange(program: Program, date: LocalDate): Pair<LocalDate, LocalDate>? {
        val projection = ProgramCalendarEngine.project(program)
        val week = projection.weekForDate(date)
        return week?.let { it.startDate to it.endDate }
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw.trim())
        } catch (_: DateTimeParseException) {
            ProgramCalendarEngine.parseIsoDate(raw)
        }
    }
}
