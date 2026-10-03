package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.CardioTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCardioPageActionTest {
    @Test
    fun staleSeriesOneCardCannotMutateAfterCursorMovesToSeriesTwo() {
        assertFalse(
            isCardioPageActionAllowed(
                pageExerciseId = "run",
                pageSetIndex = 0,
                activeExerciseId = "run",
                activeSetIndex = 1,
                activeStepKey = WorkoutStepRules.cardioStepKey("run", 1),
            ),
        )
    }

    @Test
    fun activeSeriesCardRequiresMatchingExerciseIndexAndCanonicalStepKey() {
        assertTrue(
            isCardioPageActionAllowed(
                pageExerciseId = "run",
                pageSetIndex = 1,
                activeExerciseId = "run",
                activeSetIndex = 1,
                activeStepKey = WorkoutStepRules.cardioStepKey("run", 1),
            ),
        )
        assertFalse(
            isCardioPageActionAllowed(
                pageExerciseId = "run",
                pageSetIndex = 1,
                activeExerciseId = "other-run",
                activeSetIndex = 1,
                activeStepKey = WorkoutStepRules.cardioStepKey("run", 1),
            ),
        )
        assertFalse(
            isCardioPageActionAllowed(
                pageExerciseId = "run",
                pageSetIndex = 1,
                activeExerciseId = "run",
                activeSetIndex = 1,
                activeStepKey = WorkoutStepRules.cardioStepKey("run", 0),
            ),
        )
    }

    @Test
    fun runningPausedAndAwaitingTimerStayAttachedToTheirOriginalSeries() {
        val protectedStatuses = setOf(
            CardioExecutionStatus.RUNNING,
            CardioExecutionStatus.PAUSED,
            CardioExecutionStatus.AWAITING_CONFIRMATION,
        )
        protectedStatuses.forEach { status ->
            val timer = CardioTimerState(
                exerciseId = "run",
                totalSeconds = 60,
                remainingSeconds = 30,
                status = status,
                setId = "run-set-a",
            )
            assertTrue(cardioTimerAllowsTarget(timer, "run", 0, "run-set-a"))
            assertFalse(cardioTimerAllowsTarget(timer, "run", 1, "run-set-b"))
            assertFalse(cardioTimerAllowsTarget(timer, "cycle", 0, "cycle-set-a"))
            assertTrue(cardioTimerProtectsSeries(timer))
        }
        val recorded = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 60,
            remainingSeconds = 0,
            status = CardioExecutionStatus.RECORDED,
            setId = "run-set-a",
        )
        assertTrue(cardioTimerAllowsTarget(recorded, "run", 1, "run-set-b"))
        assertFalse(cardioTimerProtectsSeries(recorded))
    }

    @Test
    fun pageIdentityIncludesSetSoCardRememberStateCannotLeakAcrossSeries() {
        assertFalse(
            cardioPageIdentity("run", 0, "run-set-a") == cardioPageIdentity("run", 1, "run-set-b"),
        )
        assertFalse(
            cardioPageIdentity("run", 1, "run-set-b") == cardioPageIdentity("cycle", 1, "cycle-set-b"),
        )
    }

    @Test
    fun legacyUnkeyedPausedTimerProtectsOnlySeriesZero() {
        val legacyPausedTimer = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 60,
            remainingSeconds = 20,
            status = CardioExecutionStatus.PAUSED,
            setId = null,
        )
        assertTrue(cardioTimerAllowsTarget(legacyPausedTimer, "run", 0, "run-set-a"))
        assertFalse(cardioTimerAllowsTarget(legacyPausedTimer, "run", 1, "run-set-b"))
        assertFalse(cardioTimerAllowsTarget(legacyPausedTimer, "run", 1, null))
        assertFalse(isTimerForCardioSet(legacyPausedTimer, "run", 1, null))
    }

    @Test
    fun protectedTimerRejectsStepsWithoutAValidLiveCardioTarget() {
        val timer = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 60,
            remainingSeconds = 30,
            status = CardioExecutionStatus.PAUSED,
            setId = "run-set-0",
        )
        val cardio = Exercise(
            id = "run",
            name = "Running",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
            sets = listOf(ExerciseSet(id = "run-set-0")),
        )
        val strength = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "press-set-0")),
        )
        val activeCardio = WorkoutStep(
            type = WorkoutStepType.CARDIO,
            exerciseId = "run",
            exerciseName = "Running",
            stepKey = WorkoutStepRules.cardioStepKey("run", 0),
            setIndex = 0,
        )

        assertTrue(cardioTimerAllowsWorkoutStep(timer, activeCardio, listOf(cardio, strength)))
        assertFalse(cardioTimerAllowsWorkoutStep(timer, activeCardio.copy(exerciseId = "missing"), listOf(cardio, strength)))
        assertFalse(cardioTimerAllowsWorkoutStep(timer, activeCardio.copy(exerciseId = "press"), listOf(cardio, strength)))
        assertFalse(
            cardioTimerAllowsWorkoutStep(
                timer,
                activeCardio.copy(type = WorkoutStepType.WORKING_SET, exerciseId = "press"),
                listOf(cardio, strength),
            ),
        )
    }

    @Test
    fun savedPendingCardioSeriesWinsOverEarlierPendingSeries() {
        val cardio = Exercise(
            id = "run",
            name = "Running",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
            sets = listOf(ExerciseSet(id = "run-set-0"), ExerciseSet(id = "run-set-1")),
        )
        assertEquals(
            1,
            preferredPendingCardioSetIndex(
                exercise = cardio,
                completedSets = emptyMap(),
                preferredSetId = "run-set-1",
            ),
        )
        assertEquals(
            0,
            preferredPendingCardioSetIndex(
                exercise = cardio,
                completedSets = mapOf("run_1" to com.example.kpkn.data.models.CompletedSet(id = "done")),
                preferredSetId = "run-set-1",
            ),
        )
    }

    @Test
    fun protectedCardioTimerPreventsVariantChangeUntilReleased() {
        val paused = CardioTimerState(
            exerciseId = "run",
            totalSeconds = 60,
            remainingSeconds = 30,
            status = CardioExecutionStatus.PAUSED,
            setId = "run-set-1",
        )
        assertFalse(cardioTimerAllowsVariantChange(paused))
        assertTrue(cardioTimerAllowsVariantChange(paused.copy(status = CardioExecutionStatus.RECORDED)))
        assertTrue(cardioTimerAllowsVariantChange(null))
    }

}
