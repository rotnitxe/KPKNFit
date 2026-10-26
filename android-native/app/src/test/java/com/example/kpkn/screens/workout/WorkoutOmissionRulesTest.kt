package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutOmissionRulesTest {
    private val exercise = Exercise("e", "Press", sets = listOf(ExerciseSet("s0"), ExerciseSet("s1")))
    private fun state(unilateral: Boolean = false) = WorkoutUiState(
        session = Session("s", "S", exercises = listOf(exercise.copy(isUnilateral = unilateral))))

    @Test fun skippingAnExerciseNamesOnlyItsUnperformedSetsAndRepairsLegacySkipFlags() {
        val state = state().copy(completedSets = mapOf("e_0" to CompletedSet("done")), skippedExerciseIds = setOf("e"))
        assertEquals(setOf("e_1"), expandedOmissionKeys(state))
        assertEquals("done", state.completedSets.getValue("e_0").id)
    }

    @Test fun skippingAHalfPerformedPairRetainsTheObservedSideAndIdentifiesItsOmission() {
        val state = state(true).copy(completedSets = mapOf("e_0_L" to CompletedSet("left", side = "left")))
        assertEquals(setOf("e_0", "e_1"), expandedOmissionKeys(state, exerciseIds = setOf("e")))
        assertEquals("left", state.completedSets.getValue("e_0_L").id)
    }

    @Test fun aCompletedSetCannotBecomeANewOmission() {
        val state = state().copy(completedSets = mapOf("e_0" to CompletedSet("done")))
        assertTrue(expandedOmissionKeys(state, setKeys = setOf("e_0", "missing_4")).isEmpty())
        assertEquals(setOf("e_1"), expandedOmissionKeys(state, setKeys = setOf("e_1")))
    }

    @Test fun aDifferentVariantsObservationDoesNotTurnPendingWorkIntoCompletedWork() {
        val base = state()
        val state = base.copy(
            activeMode = WeekVariant.B,
            session = base.session!!.copy(sessionB = Session("b", "B", exercises = listOf(exercise))),
            completedSets = mapOf("e_0" to CompletedSet("a", sourceRef = WorkoutSetExecutionRef(1, WeekVariant.A, "e", "s0"))),
        )
        assertEquals(setOf("e_0", "e_1"), expandedOmissionKeys(state, exerciseIds = setOf("e")))
    }
}
