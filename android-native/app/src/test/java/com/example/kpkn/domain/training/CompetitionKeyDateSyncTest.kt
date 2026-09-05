package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionDetails
import com.example.kpkn.data.models.CompetitionJournal
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCompetitionMeet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionKeyDateSyncTest {

    private fun competitionSession(
        exercises: List<Exercise> = emptyList(),
        recordId: String? = "rec-1",
    ) = Session(
        id = "s1",
        name = "Meet day",
        isMeetDay = true,
        isCompetitionSession = true,
        exercises = exercises,
        competitionRecordId = recordId,
        competitionKeyDateId = "kd-1",
        competitionDetails = CompetitionDetails(competitionDate = "2026-08-15"),
        competitionRecordMode = CompetitionRecordMode.HYBRID,
    )

    private fun keyDate(
        id: String = "kd-1",
        eventDate: String = "2026-08-15",
        title: String = "Open local",
    ) = ProgramKeyDate(
        id = id,
        title = title,
        type = KeyDateType.COMPETITION,
        startDate = eventDate,
        eventDate = eventDate,
    )

    private fun programWith(
        sessions: List<Session>,
        keyDate: ProgramKeyDate? = keyDate(),
    ) = Program(
        id = "p1",
        name = "Plan",
        structure = ProgramStructure.COMPLEX,
        keyDates = listOfNotNull(keyDate),
        macrocycles = listOf(
            Macrocycle(
                id = "mc",
                name = "M",
                blocks = listOf(
                    Block(
                        id = "b",
                        name = "B",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "m",
                                name = "M",
                                weeks = listOf(ProgramWeek(id = "w1", name = "W", sessions = sessions)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun mergeFromLegacySession_returns_null_for_training_session() {
        val session = Session(id = "s1", name = "Push day")
        val result = CompetitionKeyDateSync.mergeFromLegacySession(session, null, "p1", "w1")
        assertNull(result)
    }

    @Test
    fun mergeFromLegacySession_creates_record_without_existing_id() {
        val squat = Exercise(id = "e1", name = "Sentadilla", exerciseDbId = "squat_barbell")
        val result = CompetitionKeyDateSync.mergeFromLegacySession(
            competitionSession(exercises = listOf(squat), recordId = null),
            existingRecord = null,
            programId = "p1",
            weekId = "w1",
        )

        requireNotNull(result)
        assertEquals("Meet day", result.title)
        assertEquals("2026-08-15", result.eventDate)
        assertEquals("p1", result.plannedProgramId)
        assertEquals("w1", result.plannedWeekId)
        assertNull(result.plannedSessionId)
        assertEquals(CompetitionRecordStatus.PLANNED, result.status)
        assertEquals(1, result.technicalBlocks.size)
        assertEquals("Sentadilla", result.technicalBlocks[0].exerciseName)
        assertTrue(result.technicalBlocks[0].attempts.isEmpty())
    }

    @Test
    fun mergeFromLegacySession_never_overwrites_logged_attempts() {
        val squat = Exercise(id = "e1", name = "Sentadilla", exerciseDbId = "squat_barbell")
        val loggedAttempts = listOf(
            CompetitionAttempt(id = "a1", attemptNumber = 1, weightKg = 150.0, resultType = CompetitionAttemptResult.GOOD_LIFT),
            CompetitionAttempt(id = "a2", attemptNumber = 2, weightKg = 160.0, resultType = CompetitionAttemptResult.NO_LIFT),
        )
        val existing = CompetitionRecord(
            id = "rec-1",
            title = "Meet day",
            sportType = CompetitionTemplateType.POWERLIFTING,
            recordMode = CompetitionRecordMode.HYBRID,
            plannedProgramId = "p1",
            plannedSessionId = "s1",
            plannedWeekId = "w1",
            technicalBlocks = listOf(
                CompetitionTechnicalBlock(
                    id = "e1",
                    title = "Sentadilla",
                    exerciseDbId = "squat_barbell",
                    exerciseName = "Sentadilla",
                    attempts = loggedAttempts,
                    bestValidWeightKg = 150.0,
                ),
            ),
        )
        val editedSession = competitionSession(exercises = listOf(squat.copy(name = "Sentadilla libre")))
        val result = CompetitionKeyDateSync.mergeFromLegacySession(editedSession, existing, "p1", "w1")

        requireNotNull(result)
        assertEquals(loggedAttempts, result.technicalBlocks[0].attempts)
        assertEquals(150.0, result.technicalBlocks[0].bestValidWeightKg)
        assertEquals("Sentadilla libre", result.technicalBlocks[0].exerciseName)
    }

    @Test
    fun mergeFromLegacySession_preserves_blocks_without_matching_session_exercise() {
        val benchExercise = Exercise(id = "e-bench", name = "Press banca", exerciseDbId = "bench_barbell")
        val existing = CompetitionRecord(
            id = "rec-1",
            title = "Meet day",
            sportType = CompetitionTemplateType.POWERLIFTING,
            recordMode = CompetitionRecordMode.HYBRID,
            technicalBlocks = listOf(
                CompetitionTechnicalBlock(
                    id = "squat-block",
                    title = "Sentadilla",
                    movementType = CompetitionMovementType.SQUAT,
                    attempts = listOf(
                        CompetitionAttempt(id = "a1", attemptNumber = 1, weightKg = 180.0, resultType = CompetitionAttemptResult.GOOD_LIFT),
                    ),
                    bestValidWeightKg = 180.0,
                ),
                CompetitionTechnicalBlock(
                    id = "deadlift-block",
                    title = "Peso muerto",
                    movementType = CompetitionMovementType.DEADLIFT,
                ),
            ),
        )

        val result = CompetitionKeyDateSync.mergeFromLegacySession(
            competitionSession(exercises = listOf(benchExercise)),
            existing,
            "p1",
            "w1",
        )

        requireNotNull(result)
        assertEquals(3, result.technicalBlocks.size)
        assertEquals(180.0, result.technicalBlocks.first { it.id == "squat-block" }.bestValidWeightKg)
        assertTrue(result.technicalBlocks.any { it.id == "deadlift-block" })
        assertTrue(result.technicalBlocks.any { it.exerciseDbId == "bench_barbell" })
    }

    @Test
    fun mergeFromKeyDate_creates_planned_record() {
        val result = CompetitionKeyDateSync.mergeFromKeyDate(keyDate(), existing = null, programId = "p1", weekId = "w1")
        assertEquals("Open local", result.title)
        assertEquals("2026-08-15", result.eventDate)
        assertEquals("kd-1", result.keyDateId)
        assertEquals("p1", result.plannedProgramId)
        assertEquals("w1", result.plannedWeekId)
        assertNull(result.plannedSessionId)
        assertEquals(CompetitionRecordStatus.PLANNED, result.status)
    }

    @Test
    fun mergeFromKeyDate_does_not_overwrite_completed_attempts_or_journal() {
        val attempts = listOf(
            CompetitionAttempt(id = "a1", attemptNumber = 1, weightKg = 200.0, resultType = CompetitionAttemptResult.GOOD_LIFT),
        )
        val existing = CompetitionRecord(
            id = "rec-1",
            title = "Open local",
            eventDate = "2026-08-15",
            status = CompetitionRecordStatus.COMPLETED,
            journal = CompetitionJournal(overallFeeling = "Buena sensación"),
            technicalBlocks = listOf(
                CompetitionTechnicalBlock(
                    id = "e1",
                    title = "Sentadilla",
                    attempts = attempts,
                    bestValidWeightKg = 200.0,
                ),
            ),
        )
        val moved = keyDate(eventDate = "2026-09-01", title = "Fecha nueva")
        val result = CompetitionKeyDateSync.mergeFromKeyDate(moved, existing, "p1", "w2")

        assertEquals(CompetitionRecordStatus.COMPLETED, result.status)
        assertEquals("Buena sensación", result.journal?.overallFeeling)
        assertEquals(attempts, result.technicalBlocks.single().attempts)
        assertEquals(200.0, result.technicalBlocks.single().bestValidWeightKg)
        assertEquals("2026-08-15", result.eventDate)
        assertEquals("kd-1", result.keyDateId)
        assertEquals("w2", result.plannedWeekId)
        assertNull(result.plannedSessionId)
    }

    @Test
    fun migrate_strips_meet_sessions_and_upserts_record() {
        val training = Session(id = "mon", name = "Fuerza", dayOfWeek = 1)
        val meet = competitionSession()
        val existing = CompetitionRecord(
            id = "rec-1",
            title = "Meet day",
            status = CompetitionRecordStatus.PLANNED,
            plannedSessionId = "s1",
            technicalBlocks = listOf(
                CompetitionTechnicalBlock(
                    id = "e1",
                    title = "Sentadilla",
                    attempts = listOf(
                        CompetitionAttempt(id = "a1", attemptNumber = 1, weightKg = 150.0, resultType = CompetitionAttemptResult.GOOD_LIFT),
                    ),
                    bestValidWeightKg = 150.0,
                ),
            ),
        )

        val result = CompetitionKeyDateSync.migrate(
            program = programWith(sessions = listOf(training, meet)),
            existingRecords = listOf(existing),
            competitionWeekId = "w1",
        )

        val remaining = result.program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
        assertEquals(listOf("mon"), remaining.map { it.id })
        assertFalse(remaining.any { it.isCompetitionMeet })
        val upserted = result.recordsToUpsert.single { it.id == "rec-1" }
        assertEquals(150.0, upserted.technicalBlocks.single().bestValidWeightKg)
        assertNull(upserted.plannedSessionId)
        assertEquals("kd-1", upserted.keyDateId)
    }
}
