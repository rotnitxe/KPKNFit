package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.domain.training.captureApreXrm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutApreAndSupersetNavigationTest {

    private fun testNavigator(
        getState: () -> WorkoutUiState,
        updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
        onStopRest: () -> Unit = {},
    ): WorkoutStepNavigator = WorkoutStepNavigator(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        getState = getState,
        updateState = updateState,
        ports = object : WorkoutStepNavigator.Ports {
            override fun visibleExercises(state: WorkoutUiState): List<Exercise> = state.session?.exercises.orEmpty()
            override fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = base
            override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                completedSets.containsKey("${exerciseId}_$setIdx")
            override fun buildEditingStateForPosition(completedSets: Map<String, CompletedSet>, exercise: Exercise?, setIdx: Int, preferredSide: String?) = null
            override fun stopRestTimer() = onStopRest()
            override fun persistOngoingState(immediate: Boolean) = Unit
            override suspend fun persistOngoingStateAndAwait() = WorkoutPersistResult.Ok
            override fun refreshLoadSuggestions(state: WorkoutUiState) = Unit
            override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = Unit
            override fun computeImbalanceNotice(exercise: Exercise, setIdx: Int, completedSets: Map<String, CompletedSet>) = null
            override fun openFinishSheet() = Unit
            override fun speakCurrentStepAnnouncementIfEnabled() = Unit
            override fun isRecordingBusy() = false
            override fun onRecordingBusyBlocked(message: String) = Unit
            override fun announcePostExerciseFeedback(exerciseIds: List<String>) = Unit
            override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
        },
    )

    @Test
    fun feedbackLivesOnCardIsFalseWhenPagerIsHiddenDuringPostExerciseTransition() {
        // #14.4 deadlock prevention: when pagerVisible == false, feedback must render in overlay host
        assertFalse(
            feedbackLivesOnCard(
                feedbackShowing = true,
                isRestTimerRunning = true,
                isRestMinimized = true,
                restKind = RestTimerKind.STANDARD,
                pagerVisible = false,
            ),
        )
        assertTrue(
            feedbackLivesOnCard(
                feedbackShowing = true,
                isRestTimerRunning = true,
                isRestMinimized = true,
                restKind = RestTimerKind.STANDARD,
                pagerVisible = true,
            ),
        )
    }

    @Test
    fun apreCalibrationAndXrmCaptureSupportPerformedRirAndRpeEffectiveReps() {
        val baseSets = listOf(
            ExerciseSet(id = "s1", weight = 50.0, targetReps = 10),
            ExerciseSet(id = "s2", weight = 75.0, targetReps = 6),
            ExerciseSet(
                id = "s3",
                weight = 100.0,
                targetReps = 6,
                targetRIR = 2,
                intensityMode = IntensityMode.RIR,
                purpose = SetPurpose.AMRAP,
                amrapMinimumReps = 6,
            ),
            ExerciseSet(
                id = "s4",
                weight = 100.0,
                targetReps = 6,
                targetRIR = 2,
                intensityMode = IntensityMode.RIR,
                purpose = SetPurpose.AMRAP,
                amrapMinimumReps = 6,
            ),
        )
        val exercise = Exercise(
            id = "bench",
            name = "Press banca",
            sets = baseSets,
            apreBlock = ApreBlockReference(
                xrmReps = 6,
                setIds = listOf("s1", "s2", "s3", "s4"),
            ),
        )
        val completedS3 = CompletedSet(
            id = "c3",
            weight = 100.0,
            reps = 5,
            rir = 2,
            purpose = SetPurpose.AMRAP,
            amrapMinimumReps = 6,
        )
        val calibration = buildRecordedApreCalibration(
            state = WorkoutUiState(),
            exercise = exercise,
            index = 2,
            completed = completedS3,
            now = 1_000L,
        )
        // 5 valid reps + RIR 2 = 7 effective reps on APRE 6 -> HOLD (100 kg)
        assertNotNull(calibration)
        assertEquals(100.0, calibration!!.decision.affectedSets.single().afterKg, 0.001)

        // S4 XRM capture with performed RPE 8.0 (2 RIR) on 4 reps -> 4 + 2 = 6 effective reps -> HOLD (100 kg)
        val completedS4Rpe = CompletedSet(
            id = "c4",
            weight = 100.0,
            reps = 4,
            rpe = 8.0,
            purpose = SetPurpose.AMRAP,
            amrapMinimumReps = 6,
        )
        val xrmHold = captureApreXrm(exercise = exercise, index = 3, completed = completedS4Rpe, incrementKg = 2.5)
        assertNotNull(xrmHold)
        assertEquals(100.0, xrmHold!!.reference.capturedLoadKg!!, 0.001)

        // S4 XRM capture with performed RIR 0 and 8 reps -> RAISE (+2.5..5.0 kg)
        val completedS4Raise = completedS4Rpe.copy(id = "c4b", reps = 8, rpe = null, rir = 0)
        val xrmRaise = captureApreXrm(exercise = exercise, index = 3, completed = completedS4Raise, incrementKg = 2.5)
        assertNotNull(xrmRaise)
        assertTrue(xrmRaise!!.reference.capturedLoadKg!! > 100.0)
    }

    @Test
    fun navigatorSelectsFirstPendingStepInSupersetClearsPostSheetAndStopsRestOnCardioTransition() {
        val ex1 = Exercise(
            id = "e1",
            name = "Press",
            supersetGroupRef = "g1",
            sets = listOf(
                ExerciseSet(id = "s11", targetReps = 8),
                ExerciseSet(id = "s12", targetReps = 8),
            ),
        )
        val ex2 = Exercise(
            id = "e2",
            name = "Remo",
            supersetGroupRef = "g1",
            sets = listOf(
                ExerciseSet(id = "s21", targetReps = 8),
                ExerciseSet(id = "s22", targetReps = 8),
            ),
        )
        val cardio = Exercise(
            id = "c1",
            name = "Bici",
            cardioDetails = com.example.kpkn.data.models.CardioDetails(
                type = com.example.kpkn.data.models.CardioType.BIKE_STATIONARY,
            ),
            sets = listOf(ExerciseSet(id = "sc1", targetDuration = 600)),
        )
        val session = Session(id = "ses", name = "Torso + Cardio", exercises = listOf(ex1, ex2, cardio))
        val groups = liveRouteGroups(session.exercises, session.supersetGroups)
        assertTrue(groups.any { it.id == "g1" && it.members.size == 2 })

        var state = WorkoutUiState(
            session = session,
            currentExerciseIdx = 0,
            currentSetIdx = 0,
            completedSets = mapOf(
                "e1_0" to CompletedSet(id = "cs1", weight = 60.0, reps = 8),
            ),
        )
        var restStoppedCount = 0
        val navigator = testNavigator(
            getState = { state },
            updateState = { transform -> state = transform(state) },
            onStopRest = { restStoppedCount++ },
        )

        // Superset g1 should jump to e2_0 (first pending step in round 1 of g1)
        navigator.selectSupersetGroup("g1")
        assertEquals(1, state.currentExerciseIdx)
        assertEquals(0, state.currentSetIdx)

        // Tapping current step while post-exercise sheet is open must close the sheet instead of early-returning
        val currentStepKey = state.activeStepKey!!
        state = state.copy(showPostExerciseSheet = true)
        navigator.selectWorkoutStep(currentStepKey)
        assertFalse(state.showPostExerciseSheet)

        // Advancing directly to Cardio with stopRest = false must still stop running strength rest timer
        val e2SecondStepKey = navigator.workoutStepPositions(state)
            .first { it.exerciseId == "e2" && it.setIndex == 1 }
            .stepKey
        state = state.copy(
            currentExerciseIdx = 1,
            currentSetIdx = 1,
            activeStepKey = e2SecondStepKey,
            completedSets = mapOf(
                "e1_0" to CompletedSet(id = "c1", weight = 60.0, reps = 8),
                "e2_0" to CompletedSet(id = "c2", weight = 60.0, reps = 8),
                "e1_1" to CompletedSet(id = "c3", weight = 60.0, reps = 8),
            ),
            isRestTimerRunning = true,
        )
        restStoppedCount = 0
        navigator.nextSet(stopRest = false)
        assertEquals(2, state.currentExerciseIdx)
        assertTrue("Rest timer must stop when transitioning directly into Cardio", restStoppedCount >= 1)
    }
}
