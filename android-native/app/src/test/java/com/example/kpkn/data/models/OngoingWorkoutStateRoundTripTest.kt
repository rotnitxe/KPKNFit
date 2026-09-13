package com.example.kpkn.data.models

import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.decodeOngoingWorkout
import com.example.kpkn.data.db.OngoingDecode
import com.example.kpkn.screens.workout.FinishResumeSnapshot
import com.example.kpkn.screens.workout.GodModeUndoSnapshot
import com.example.kpkn.screens.workout.WorkoutEditingState
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OngoingWorkoutStateRoundTripTest {

    @Test
    fun newDraftFields_surviveEncodeDecode() {
        val original = OngoingWorkoutState(
            programId = "prog",
            session = Session(
                id = "sess",
                name = "Push",
                exercises = listOf(
                    Exercise(id = "ex1", name = "Press", sets = listOf(ExerciseSet(id = "s1", targetReps = 8))),
                ),
            ),
            startTime = 42L,
            postExerciseFeedbackByExerciseId = mapOf(
                "ex1" to PostExerciseFeedback(
                    exerciseId = "ex1",
                    exerciseName = "Press",
                    technicalQuality = 8,
                    discomfortIds = listOf("elbow"),
                ),
            ),
            planDeviations = listOf(
                PlanDeviation(
                    exerciseId = "ex1",
                    exerciseName = "Press",
                    setIdx = 0,
                    type = PlanDeviationType.WEIGHT_HIGH,
                    detail = "+5kg",
                ),
            ),
            showFinishSheet = true,
            finishResumeSnapshot = FinishResumeSnapshot(
                exerciseId = "ex1",
                setId = "s1",
                activeStepKey = "work:ex1:0",
                editingState = WorkoutEditingState(setKey = "ex1_0", exerciseId = "ex1", setIdx = 0),
                finishOperationId = "op-1",
            ),
            godModeUndoStack = listOf(
                GodModeUndoSnapshot(label = "Eliminar serie", currentExerciseIdx = 0, currentSetIdx = 1),
            ),
            pendingVolumeAdvances = listOf(
                MuscleAdvance(
                    muscleId = "pecs",
                    muscleName = "Pectorales",
                    currentSets = 12.0,
                    targetSets = 10.0,
                    deficitSets = -2.0,
                    targetSessionId = "next",
                    targetSessionName = "Push 2",
                ),
            ),
            showVolumeAdvanceModal = true,
            volumeAdvanceHandled = false,
            archivedCompletedExercises = listOf(
                CompletedExercise(exerciseId = "old", exerciseName = "Remo", sets = emptyList()),
            ),
            logAlreadyWrittenId = "log-1",
        )

        val json = dbJson.encodeToString(original)
        val decoded = decodeOngoingWorkout(json)
        assertTrue(decoded is OngoingDecode.Ok)
        val state = (decoded as OngoingDecode.Ok).state
        assertEquals("elbow", state.postExerciseFeedbackByExerciseId["ex1"]?.discomfortIds?.single())
        assertEquals(PlanDeviationType.WEIGHT_HIGH, state.planDeviations.single().type)
        assertTrue(state.showFinishSheet)
        assertEquals("op-1", state.finishResumeSnapshot?.finishOperationId)
        assertEquals("Eliminar serie", state.godModeUndoStack.single().label)
        assertEquals("pecs", state.pendingVolumeAdvances.single().muscleId)
        assertTrue(state.showVolumeAdvanceModal)
        assertEquals("old", state.archivedCompletedExercises.single().exerciseId)
        assertEquals("log-1", state.logAlreadyWrittenId)
    }
}
