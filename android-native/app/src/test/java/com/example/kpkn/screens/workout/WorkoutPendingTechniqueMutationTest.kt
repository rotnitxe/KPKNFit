package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.sessionassistant.applyMarkedSeriesTechnique
import com.example.kpkn.domain.sessionassistant.withTechnique
import com.example.kpkn.domain.workout.resolveScheduledTechniqueExecution
import org.junit.Assert.*
import org.junit.Test

class WorkoutPendingTechniqueMutationTest {
    @Test fun assistedDropsIncreaseHelpInBothMarkedAndScheduledFlows() {
        val sets = listOf(ExerciseSet("base", weight = 30.0, loadModeV2 = LoadModeV2.ASSISTED),
            ExerciseSet("drop", weight = 30.0, loadModeV2 = LoadModeV2.ASSISTED))
        assertEquals(35.0, applyMarkedSeriesTechnique(sets, setOf(1), SeriesTechnique.DROPSET)[1].weight!!, 0.0)
        val scheduled = sets[0].copy(isDropSet = true, plannedIntensityTechniques = listOf(
            PlannedTechnique("scheduled", TechniqueType.DROP_SET, params = mapOf("betweenMarked" to "true", "weightPcts" to "-5", "count" to "1"))))
        val next = scheduled.resolveScheduledTechniqueExecution(
            currentLoad = 30.0, normalRestSeconds = 90, loadMode = LoadModeV2.ASSISTED)
        assertEquals(35.0, next!!.nextLoad!!, 0.0)
    }
    @Test fun aBatchChangesBothExercisesAndProtectsAnOutOfOrderOrHalfRecordedSet() {
        val a = Exercise("a", "A", sets = listOf(ExerciseSet("a0"), ExerciseSet("a1")))
        val b = Exercise("b", "B", isUnilateral = true, sets = listOf(ExerciseSet("b0"), ExerciseSet("b1")))
        val state = WorkoutUiState(session = Session("s", "S", exercises = listOf(a, b)), currentSetIdx = 1,
            completedSets = mapOf("a_1" to CompletedSet("done"), "b_0_L" to CompletedSet("left", side = "left")))
        val changed = pendingTechniqueMutation(state, setOf("a", "b"), SeriesTechnique.REST_PAUSE)!!
        val changedA = changed.allExercises().first { it.id == "a" }
        val changedB = changed.allExercises().first { it.id == "b" }
        assertTrue(changedA.sets.first { it.id == "a0" }.isRestPause)
        assertEquals(a.sets[1], changedA.sets.first { it.id == "a1" })
        assertEquals(b.sets[0], changedB.sets.first { it.id == "b0" })
        assertTrue(changedB.sets.first { it.id == "b1" }.isRestPause)
    }
}
