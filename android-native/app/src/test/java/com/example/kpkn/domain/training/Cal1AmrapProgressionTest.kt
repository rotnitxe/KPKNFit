package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class Cal1AmrapProgressionTest {
    private val planned = ExerciseSet("a", targetReps = 6, targetRepsRange = RepRange(4, 6),
        purpose = SetPurpose.AMRAP, amrapMinimumReps = 4)
    private val session = Session("s", "Sentadilla", exercises = listOf(Exercise("e", "Sentadilla", sets = listOf(planned))))
    private val week = ProgramWeek("w", "Semana", sessions = listOf(session))
    private fun log(set: CompletedSet, date: String = "2026-10-20T12:00:00Z") = WorkoutLog(
        "log-$date", "p", session.id, session.name, date, 30, weekId = week.id,
        completedExercises = listOf(CompletedExercise("e", "Sentadilla", sets = listOf(set))),
    )
    private fun hits(set: CompletedSet) = ProgramAutoregulationEngine.collectAmrapHits(week, listOf(log(set)))

    @Test fun performedMinimumFourWinsOverLegacyMaximumSix() {
        val hit = hits(CompletedSet("done", reps = 5, purpose = SetPurpose.AMRAP,
            amrapPerformed = true, amrapMinimumReps = 4, setIndex = 0)).single()
        assertEquals(4, hit.prescribedReps)
        assertEquals(5, hit.actualReps)
        assertTrue(hit.actualReps >= hit.prescribedReps)
        assertFalse(ProgramAutoregulationEngine.isShortAmrap(hit))
        val legacy = hits(CompletedSet("old", reps = 5, amrapPerformed = true, amrapMinimumReps = 4)).single()
        assertEquals(4, legacy.prescribedReps)
    }

    @Test fun explicitNoneCannotReviveByPositionOrOlderLog() {
        val none = CompletedSet("done", reps = 5, purpose = SetPurpose.NONE, setIndex = 0)
        assertTrue(hits(none).isEmpty())
        val oldAmrap = CompletedSet("old", reps = 7, amrapPerformed = true)
        assertTrue(ProgramAutoregulationEngine.collectAmrapHits(week, listOf(
            log(none), log(oldAmrap, "2026-10-19T12:00:00Z"),
        )).isEmpty())
    }

    @Test fun oldModernPayloadWithAmrapDisabledAlsoPreventsPositionalFallback() {
        val done = CompletedSet("done", reps = 5, recordedPayloadV3 = RecordedSetPayload(exerciseId = "e", amrapPerformed = false))
        assertTrue(hits(done).isEmpty())
    }

    @Test fun explicitLegacyFalseAndRecordedMinimumCannotReviveByPosition() {
        val disabled = kotlinx.serialization.json.Json.decodeFromString<CompletedSet>(
            """{"id":"old","reps":5,"amrapPerformed":false}""",
        )
        assertTrue(hits(disabled).isEmpty())
        assertTrue(hits(CompletedSet("old", reps = 5, amrapMinimumReps = 4)).isEmpty())
        assertTrue(hits(CompletedSet("old", reps = 5, setIndex = 0)).isEmpty())
    }

    @Test fun fallbackIsStillAvailableForOldLogsWithoutEvidence() {
        val hit = hits(CompletedSet("old", reps = 5)).single()
        assertEquals(4, hit.prescribedReps)
    }

    @Test fun explicitlyNoMinimumIsNeverReplacedByPlan() {
        val hit = hits(CompletedSet("done", reps = 1, purpose = SetPurpose.AMRAP, amrapPerformed = true)).single()
        assertEquals(0, hit.prescribedReps)
    }

    @Test fun liveActivationOfAnOrdinarySetIsReadFromPerformedPurpose() {
        val normalWeek = week.copy(sessions = listOf(session.copy(exercises = listOf(
            session.exercises.single().copy(sets = listOf(planned.copy(purpose = SetPurpose.NONE, amrapMinimumReps = null))),
        ))))
        val done = CompletedSet("done", reps = 6, purpose = SetPurpose.CALIBRATOR, amrapMinimumReps = 4, setIndex = 0)
        assertEquals(6, ProgramAutoregulationEngine.collectAmrapHits(normalWeek, listOf(log(done))).single().actualReps)
    }
}
