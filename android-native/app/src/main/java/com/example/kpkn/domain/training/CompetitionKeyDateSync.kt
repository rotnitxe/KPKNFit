package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import java.util.UUID

data class CompetitionMigration(
    val program: Program,
    val recordsToUpsert: List<CompetitionRecord>,
)

/**
 * Sincroniza una fecha clave de competición hacia su [CompetitionRecord].
 *
 * La competición ya no se programa como sesión: el record es la SSoT del evento,
 * los intentos y la bitácora. El merge nunca pisa `attempts`, `bestValidWeightKg`,
 * `bestValidMark`, journal, fotos ni un status distinto de [CompetitionRecordStatus.PLANNED].
 */
object CompetitionKeyDateSync {

    fun migrate(
        program: Program,
        existingRecords: List<CompetitionRecord>,
        competitionWeekId: String? = null,
    ): CompetitionMigration {
        val byId = existingRecords.associateBy { it.id }.toMutableMap()
        val upserts = linkedMapOf<String, CompetitionRecord>()

        collectMeetSessions(program).forEach { (session, weekId) ->
            val existing = session.competitionRecordId?.let { byId[it] }
                ?: existingRecords.firstOrNull { it.plannedSessionId == session.id }
            val merged = mergeFromLegacySession(session, existing, program.id, weekId) ?: return@forEach
            byId[merged.id] = merged
            upserts[merged.id] = merged
        }

        val keyDate = program.keyDates.firstOrNull { it.type == com.example.kpkn.data.models.KeyDateType.COMPETITION }
        if (keyDate != null) {
            val eventDate = keyDate.eventDate ?: keyDate.startDate
            val existing = byId.values.firstOrNull { it.keyDateId == keyDate.id && it.plannedProgramId == program.id }
                ?: byId.values.firstOrNull { it.plannedProgramId == program.id && it.eventDate == eventDate }
                ?: existingRecords.firstOrNull { it.keyDateId == keyDate.id && it.plannedProgramId == program.id }
            val merged = mergeFromKeyDate(
                keyDate = keyDate,
                existing = existing,
                programId = program.id,
                weekId = competitionWeekId ?: existing?.plannedWeekId,
            )
            upserts[merged.id] = merged
        }

        return CompetitionMigration(
            program = stripMeetSessions(program),
            recordsToUpsert = upserts.values.toList(),
        )
    }

    fun stripMeetSessions(program: Program): Program =
        program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        week.copy(sessions = week.sessions.filterNot { it.isCompetitionMeet })
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

    fun mergeFromKeyDate(
        keyDate: ProgramKeyDate,
        existing: CompetitionRecord?,
        programId: String,
        weekId: String?,
        recordId: String = existing?.id ?: UUID.randomUUID().toString(),
    ): CompetitionRecord {
        val eventDate = keyDate.eventDate ?: keyDate.startDate
        if (existing != null && existing.status != CompetitionRecordStatus.PLANNED) {
            return existing.copy(
                plannedProgramId = programId,
                plannedWeekId = weekId ?: existing.plannedWeekId,
                keyDateId = keyDate.id,
                plannedSessionId = null,
            )
        }
        val base = existing ?: CompetitionRecord(
            id = recordId,
            title = keyDate.title.ifBlank { "Competición" },
            eventDate = eventDate,
            status = CompetitionRecordStatus.PLANNED,
            plannedProgramId = programId,
            plannedWeekId = weekId,
            keyDateId = keyDate.id,
        )
        val title = when {
            existing == null || existing.title.isBlank() -> keyDate.title.ifBlank { "Competición" }
            else -> existing.title
        }
        return base.copy(
            title = title,
            eventDate = eventDate,
            notes = existing?.notes ?: keyDate.notes ?: base.notes,
            plannedProgramId = programId,
            plannedWeekId = weekId ?: base.plannedWeekId,
            keyDateId = keyDate.id,
            plannedSessionId = null,
        )
    }

    fun mergeFromLegacySession(
        session: Session,
        existingRecord: CompetitionRecord?,
        programId: String,
        weekId: String,
    ): CompetitionRecord? {
        if (!session.isCompetitionMeet) return null
        if (existingRecord != null && existingRecord.status != CompetitionRecordStatus.PLANNED) {
            return existingRecord.copy(
                plannedProgramId = existingRecord.plannedProgramId ?: programId,
                plannedWeekId = existingRecord.plannedWeekId ?: weekId,
                keyDateId = existingRecord.keyDateId ?: session.competitionKeyDateId,
                plannedSessionId = null,
            )
        }
        val recordId = session.competitionRecordId ?: existingRecord?.id ?: UUID.randomUUID().toString()
        val details = session.competitionDetails

        val base = existingRecord ?: CompetitionRecord(
            id = recordId,
            title = session.name.ifBlank { "Competición" },
            eventDate = details?.competitionDate,
            sportType = session.competitionSportType ?: CompetitionTemplateType.CUSTOM,
            recordMode = session.competitionRecordMode ?: CompetitionRecordMode.HYBRID,
            status = CompetitionRecordStatus.PLANNED,
            plannedProgramId = programId,
            plannedSessionId = session.id,
            plannedWeekId = weekId,
            keyDateId = session.competitionKeyDateId,
        )

        val sessionBlocks = session.exercises.map { exercise ->
            val existingBlock = base.technicalBlocks.firstOrNull { block ->
                block.exerciseDbId == exercise.exerciseDbId ||
                    block.canonicalExerciseId == exercise.resolvedCanonicalExerciseId() ||
                    block.exerciseName.equals(exercise.name, ignoreCase = true)
            }
            CompetitionTechnicalBlock(
                id = existingBlock?.id ?: exercise.id,
                title = exercise.name.ifBlank { "Movimiento" },
                movementType = existingBlock?.movementType ?: CompetitionMovementType.CUSTOM,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                canonicalExerciseId = exercise.resolvedCanonicalExerciseId(),
                exerciseName = exercise.name,
                resultUnit = existingBlock?.resultUnit,
                attempts = existingBlock?.attempts.orEmpty(),
                bestValidWeightKg = existingBlock?.bestValidWeightKg,
                bestValidMark = existingBlock?.bestValidMark,
                notes = existingBlock?.notes,
            )
        }
        val sessionBlockIds = sessionBlocks.map { it.id }.toSet()
        val untouchedBlocks = base.technicalBlocks.filterNot { it.id in sessionBlockIds }

        return base.copy(
            title = session.name.ifBlank { base.title },
            eventDate = details?.competitionDate ?: base.eventDate,
            startTime = details?.startTime ?: base.startTime,
            location = details?.location ?: base.location,
            federation = details?.federation ?: base.federation,
            category = details?.category ?: base.category,
            bodyweightKg = details?.targetBodyweightKg ?: session.meetBodyweight ?: base.bodyweightKg,
            notes = details?.strategyNotes ?: base.notes,
            sportType = session.competitionSportType ?: base.sportType,
            recordMode = session.competitionRecordMode ?: base.recordMode,
            plannedProgramId = base.plannedProgramId ?: programId,
            plannedSessionId = null,
            plannedWeekId = base.plannedWeekId ?: weekId,
            keyDateId = base.keyDateId ?: session.competitionKeyDateId,
            reminderOneWeekEnabled = details?.reminderOneWeekEnabled ?: base.reminderOneWeekEnabled,
            reminder48hEnabled = details?.reminder48hEnabled ?: base.reminder48hEnabled,
            reminderStartEnabled = details?.reminderStartEnabled ?: base.reminderStartEnabled,
            technicalBlocks = untouchedBlocks + sessionBlocks,
        )
    }

    private fun collectMeetSessions(program: Program): List<Pair<Session, String>> {
        val out = mutableListOf<Pair<Session, String>>()
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        week.sessions.filter { it.isCompetitionMeet }.forEach { session ->
                            out += session to week.id
                        }
                    }
                }
            }
        }
        return out
    }
}
