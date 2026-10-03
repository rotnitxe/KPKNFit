package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionHydratorMissingTemplateTest {

    @Test
    fun snapshotHydratesWhenTemplateMissing() {
        val snapshotSession = Session(
            id = "sess",
            name = "Vivo",
            exercises = listOf(Exercise(id = "ex", name = "Press", sets = listOf(ExerciseSet(id = "s0")))),
        )
        val snapshot = OngoingWorkoutState(programId = "prog", session = snapshotSession, startTime = 1L)
        val resolved = resolveHydrationSession(
            foundInProgram = null,
            snapshot = snapshot,
            programId = "prog",
            sessionId = "sess",
        )
        assertNotNull(resolved)
        assertEquals("Vivo", resolved!!.first.name)
        assertTrue(resolved.second)
    }

    @Test
    fun otherSessionSnapshotDoesNotHydrate() {
        val snapshot = OngoingWorkoutState(
            programId = "prog",
            session = Session(id = "other", name = "Otra"),
            startTime = 1L,
        )
        assertNull(
            resolveHydrationSession(
                foundInProgram = null,
                snapshot = snapshot,
                programId = "prog",
                sessionId = "sess",
            ),
        )
    }

    @Test
    fun programTemplateWinsOverSnapshot() {
        val template = Session(id = "sess", name = "Plantilla")
        val snapshot = OngoingWorkoutState(
            programId = "prog",
            session = Session(id = "sess", name = "Vivo"),
            startTime = 1L,
        )
        val resolved = resolveHydrationSession(template, snapshot, "prog", "sess")
        assertEquals("Plantilla", resolved!!.first.name)
        assertFalse(resolved.second)
    }

    @Test
    fun protectedS2TimerRestoresItsSeriesWhileS1IsStillPending() {
        val cardio = Exercise(
            id = "run",
            name = "Running",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
            sets = listOf(ExerciseSet(id = "run-set-0"), ExerciseSet(id = "run-set-1")),
        )
        val strength = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "press-set-0")),
        )
        val session = Session(id = "sess", name = "Vivo", exercises = listOf(cardio, strength))
        val availableSteps = WorkoutStepRules.buildSteps(session, visibleExercises = session.exercises)
        val timer = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 300,
            remainingSeconds = 120,
            status = CardioExecutionStatus.PAUSED,
            setId = "run-set-1",
        )
        val completed = emptyMap<String, CompletedSet>()

        assertEquals(0, availableSteps.first { it.type == WorkoutStepType.CARDIO }.setIndex)
        assertEquals(
            1,
            preferredProtectedCardioResumeStep(
                timer = timer,
                exercises = session.exercises,
                completedSets = completed,
                availableSteps = availableSteps,
            )?.setIndex,
        )
        assertNull(
            preferredProtectedCardioResumeStep(
                timer = timer.copy(setId = "missing-set"),
                exercises = session.exercises,
                completedSets = completed,
                availableSteps = availableSteps,
            ),
        )
        assertEquals(
            0,
            preferredProtectedCardioResumeStep(
                timer = timer.copy(setId = null),
                exercises = session.exercises,
                completedSets = completed,
                availableSteps = availableSteps,
            )?.setIndex,
        )
    }

}
