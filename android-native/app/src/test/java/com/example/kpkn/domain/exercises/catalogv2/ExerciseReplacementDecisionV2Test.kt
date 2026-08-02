package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ExerciseReplacementDecisionV2Test {

    private val standing = ExerciseSelectionV2("biceps_curl", "biceps_curl__standing__dumbbells__supinated", "v2")
    private val preacher = ExerciseSelectionV2("biceps_curl", "biceps_curl__preacher__ez_bar__supinated", "v2")

    @Test
    fun exactSelectionMatchesOnlyTheExactConfiguration() {
        val decision = ReplacementDecisionV2(
            sourceMatch = ReplacementSourceMatchV2.ExactSelection(standing),
            fromSelection = standing,
            toSelection = preacher,
            scope = ReplacementScopeV2.SESSION,
        )

        assertTrue(decision.matches(standing))
        assertFalse(decision.matches(preacher))
    }

    @Test
    fun definitionMatchDoesNotEraseConfigurationIdentity() {
        val decision = ReplacementDecisionV2(
            sourceMatch = ReplacementSourceMatchV2.Definition("biceps_curl"),
            fromSelection = standing,
            toSelection = preacher,
            scope = ReplacementScopeV2.PROGRAM,
        )
        val sameParentDifferentConfig = standing.copy(configurationId = "biceps_curl__bayesian__cable__supinated")

        assertTrue(decision.matches(sameParentDifferentConfig))
        assertFalse(decision.matches(ExerciseSelectionV2("lateral_raise", "lateral_raise__standing__dumbbells__bilateral", "v2")))
    }

    @Test
    fun occurrenceMatchRequiresTheOccurrenceAndNeverTheParentAlone() {
        val decision = ReplacementDecisionV2(
            sourceMatch = ReplacementSourceMatchV2.Occurrence("session-1:exercise-2"),
            fromSelection = standing,
            toSelection = preacher,
            scope = ReplacementScopeV2.OCCURRENCE,
        )

        assertTrue(decision.matches(standing, occurrenceId = "session-1:exercise-2"))
        assertFalse(decision.matches(standing, occurrenceId = "session-1:exercise-3"))
        assertFalse(decision.matches(standing))
    }
}
