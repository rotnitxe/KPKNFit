package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompetitionDetails
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.repository.CompetitionRepository
import java.time.LocalDate
import java.time.format.DateTimeParseException

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

        if (competitionKeyDate != null && (competitionMoved || previousCompetition == null)) {
            updated = syncCompetitionLinkedEntities(
                program = updated,
                keyDate = competitionKeyDate,
                competitionRepository = competitionRepository,
            )
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
        if (keyDate.type != KeyDateType.COMPETITION) return program
        val eventDate = keyDate.eventDate ?: keyDate.startDate
        val eventLocal = parseDate(eventDate) ?: return program
        val weekDay = locateCompetitionWeekDay(program, keyDate)
        val targetWeekId = weekDay?.first
        val targetDay = weekDay?.second ?: eventLocal.dayOfWeek.value

        // Collect linked sessions, then place them into the target week (move if needed).
        data class LinkedSession(val session: com.example.kpkn.data.models.Session, val fromWeekId: String)
        val linked = mutableListOf<LinkedSession>()
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        week.sessions.forEach { session ->
                            if (session.competitionKeyDateId == keyDate.id) {
                                linked += LinkedSession(session, week.id)
                            }
                        }
                    }
                }
            }
        }

        var updatedProgram = program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        val withoutLinked = week.sessions.filterNot { it.competitionKeyDateId == keyDate.id }
                                        val toAdd = if (targetWeekId != null && week.id == targetWeekId) {
                                            linked.map { linkedSession ->
                                                linkedSession.session.copy(
                                                    dayOfWeek = targetDay,
                                                    assignedDays = listOf(targetDay),
                                                    name = keyDate.title.ifBlank { linkedSession.session.name },
                                                    description = keyDate.notes ?: linkedSession.session.description,
                                                    competitionDetails = linkedSession.session.competitionDetails?.copy(
                                                        competitionDate = eventDate,
                                                    ) ?: CompetitionDetails(competitionDate = eventDate),
                                                )
                                            }
                                        } else {
                                            emptyList()
                                        }
                                        val merged = withoutLinked + toAdd
                                        week.copy(
                                            sessions = merged.sortedWith(
                                                compareBy(
                                                    { it.dayOfWeek ?: it.assignedDays.firstOrNull() ?: Int.MAX_VALUE },
                                                    { if (it.competitionKeyDateId == keyDate.id) 0 else 1 },
                                                    { it.name },
                                                ),
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        // If no linked session exists yet but we have a target week, leave structure alone
        // (session creation is handled by the UI flow).
        if (linked.isEmpty()) {
            updatedProgram = program
        }

        competitionRepository?.let { repo ->
            updatedProgram.macrocycles
                .flatMap { it.blocks }
                .flatMap { it.mesocycles }
                .flatMap { it.weeks }
                .flatMap { it.sessions }
                .filter { it.competitionKeyDateId == keyDate.id && !it.competitionRecordId.isNullOrBlank() }
                .forEach { session ->
                    val record = repo.getById(session.competitionRecordId!!)
                        ?: repo.getByPlannedSessionId(session.id)
                    if (record != null) {
                        repo.upsert(
                            record.copy(
                                title = keyDate.title.ifBlank { record.title },
                                eventDate = eventDate,
                                notes = keyDate.notes ?: record.notes,
                                plannedWeekId = targetWeekId ?: record.plannedWeekId,
                                keyDateId = keyDate.id,
                            ),
                        )
                    }
                }
            repo.records.value
                .filter { it.keyDateId == keyDate.id && it.plannedProgramId == program.id }
                .forEach { record ->
                    if (record.plannedSessionId.isNullOrBlank()) {
                        repo.upsert(
                            record.copy(
                                eventDate = eventDate,
                                title = keyDate.title.ifBlank { record.title },
                                plannedWeekId = targetWeekId ?: record.plannedWeekId,
                            ),
                        )
                    }
                }
        }

        return updatedProgram
    }

    private fun unlinkCompetitionEntities(
        program: Program,
        keyDate: ProgramKeyDate,
        mode: KeyDateDeleteMode,
        competitionRepository: CompetitionRepository?,
    ): Program {
        val updatedSessions = program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        week.copy(
                                            sessions = week.sessions.mapNotNull { session ->
                                                if (session.competitionKeyDateId != keyDate.id) session
                                                else when (mode) {
                                                    KeyDateDeleteMode.UNLINK_SESSION -> session.copy(
                                                        isCompetitionSession = false,
                                                        isMeetDay = false,
                                                        competitionKeyDateId = null,
                                                        competitionRecordId = null,
                                                        competitionDetails = null,
                                                    )
                                                    KeyDateDeleteMode.ARCHIVE_SESSION_AND_RECORD -> null
                                                }
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

        return updatedSessions
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
