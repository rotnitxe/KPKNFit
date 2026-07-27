package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionDetails
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionSessionSyncTest {

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
        competitionDetails = CompetitionDetails(competitionDate = "2026-08-15"),
        competitionRecordMode = CompetitionRecordMode.HYBRID,
    )

    @Test
    fun merge_returns_null_for_non_competition_session() {
        val session = Session(id = "s1", name = "Push day")
        val result = CompetitionSessionSync.merge(session, existingRecord = null, programId = "p1", weekId = "w1")
        assertNull(result)
    }

    @Test
    fun merge_returns_null_without_competitionRecordId() {
        val session = competitionSession(recordId = null)
        val result = CompetitionSessionSync.merge(session, existingRecord = null, programId = "p1", weekId = "w1")
        assertNull(result)
    }

    @Test
    fun merge_creates_new_record_from_session_when_none_exists() {
        val squat = Exercise(id = "e1", name = "Sentadilla", exerciseDbId = "squat_barbell")
        val session = competitionSession(exercises = listOf(squat))

        val result = CompetitionSessionSync.merge(session, existingRecord = null, programId = "p1", weekId = "w1")

        requireNotNull(result)
        assertEquals("rec-1", result.id)
        assertEquals("Meet day", result.title)
        assertEquals("2026-08-15", result.eventDate)
        assertEquals("p1", result.plannedProgramId)
        assertEquals("w1", result.plannedWeekId)
        assertEquals(CompetitionRecordStatus.PLANNED, result.status)
        assertEquals(1, result.technicalBlocks.size)
        assertEquals("Sentadilla", result.technicalBlocks[0].exerciseName)
        assertTrue(result.technicalBlocks[0].attempts.isEmpty())
    }

    @Test
    fun merge_never_overwrites_attempts_already_logged_in_record() {
        val squat = Exercise(id = "e1", name = "Sentadilla", exerciseDbId = "squat_barbell")
        val session = competitionSession(exercises = listOf(squat))

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

        // User re-opens the planning session editor (e.g. tweaks the movement name) and saves.
        val editedSession = session.copy(
            exercises = listOf(squat.copy(name = "Sentadilla libre")),
        )
        val result = CompetitionSessionSync.merge(editedSession, existing, programId = "p1", weekId = "w1")

        requireNotNull(result)
        assertEquals(1, result.technicalBlocks.size)
        val block = result.technicalBlocks[0]
        assertEquals("Sentadilla libre", block.exerciseName)
        assertEquals(loggedAttempts, block.attempts)
        assertEquals(150.0, block.bestValidWeightKg)
    }

    @Test
    fun merge_preserves_blocks_without_matching_session_exercise() {
        // Powerlifting auto-generates SBD blocks in CompetitionScreen that are not tied to any
        // session exercise. Saving the planning session must not wipe them out.
        val benchExercise = Exercise(id = "e-bench", name = "Press banca", exerciseDbId = "bench_barbell")
        val session = competitionSession(exercises = listOf(benchExercise))

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
                    attempts = listOf(CompetitionAttempt(id = "a1", attemptNumber = 1, weightKg = 180.0, resultType = CompetitionAttemptResult.GOOD_LIFT)),
                    bestValidWeightKg = 180.0,
                ),
                CompetitionTechnicalBlock(
                    id = "deadlift-block",
                    title = "Peso muerto",
                    movementType = CompetitionMovementType.DEADLIFT,
                ),
            ),
        )

        val result = CompetitionSessionSync.merge(session, existing, programId = "p1", weekId = "w1")

        requireNotNull(result)
        assertEquals(3, result.technicalBlocks.size)
        val squatBlock = result.technicalBlocks.first { it.id == "squat-block" }
        assertEquals(180.0, squatBlock.bestValidWeightKg)
        assertEquals(1, squatBlock.attempts.size)
        assertTrue(result.technicalBlocks.any { it.id == "deadlift-block" })
        assertTrue(result.technicalBlocks.any { it.exerciseDbId == "bench_barbell" })
    }

    @Test
    fun merge_updates_event_metadata_from_session_details() {
        val session = competitionSession().copy(
            competitionDetails = CompetitionDetails(
                competitionDate = "2026-09-01",
                startTime = "10:00",
                location = "Gimnasio Central",
                federation = "IPF",
            ),
        )
        val existing = CompetitionRecord(
            id = "rec-1",
            title = "Meet day",
            eventDate = "2026-08-15",
        )

        val result = CompetitionSessionSync.merge(session, existing, programId = "p1", weekId = "w1")

        requireNotNull(result)
        assertEquals("2026-09-01", result.eventDate)
        assertEquals("10:00", result.startTime)
        assertEquals("Gimnasio Central", result.location)
        assertEquals("IPF", result.federation)
    }
}
