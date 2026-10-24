package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.SessionDurationEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSessionDurationPolicyTest {
    @Test
    fun absentBudgetUsesTheEstimatorForExecutableBaseSessionContent() {
        val source = resistanceSession(targetDurationMinutes = null)
        val expectedEstimate = SessionDurationEstimator.estimate(source).totalMinutes

        val result = HybridSessionDurationPolicy.resolve(source, bindingBudgetMinutes = null)

        assertTrue(result is HybridSessionDurationPolicyResult.Ready)
        val resolution = (result as HybridSessionDurationPolicyResult.Ready).resolution
        assertEquals(expectedEstimate, resolution.placementMinutes)
        assertEquals(expectedEstimate, resolution.estimatedMinutes)
        assertEquals(HybridSessionDurationProvenance.ESTIMATE, resolution.provenance)
        assertTrue(resolution.budgetMinutes == null)
        assertTrue(resolution.budgetSource == null)
    }

    @Test
    fun sessionTargetIsUsedAsABudgetWithoutBeingReportedAsAnEstimate() {
        val source = resistanceSession(targetDurationMinutes = 10)

        val result = HybridSessionDurationPolicy.resolve(source, bindingBudgetMinutes = null)

        assertTrue(result is HybridSessionDurationPolicyResult.Ready)
        val resolution = (result as HybridSessionDurationPolicyResult.Ready).resolution
        assertEquals(10, resolution.placementMinutes)
        assertTrue(checkNotNull(resolution.estimatedMinutes) < resolution.placementMinutes)
        assertEquals(HybridSessionDurationProvenance.BUDGET, resolution.provenance)
        assertEquals(HybridSessionDurationBudgetSource.SESSION_TARGET, resolution.budgetSource)
    }

    @Test
    fun bindingBudgetTakesPriorityOverLegacyTargetWhenItContainsTheEstimate() {
        val source = resistanceSession(targetDurationMinutes = 60)

        val result = HybridSessionDurationPolicy.resolve(source, bindingBudgetMinutes = 6)

        assertTrue(result is HybridSessionDurationPolicyResult.Ready)
        val resolution = (result as HybridSessionDurationPolicyResult.Ready).resolution
        assertEquals(6, resolution.placementMinutes)
        assertEquals(HybridSessionDurationBudgetSource.BINDING, resolution.budgetSource)
    }

    @Test
    fun mixedResistanceAndCardioDoseCannotBePlacedInsideAShortBudget() {
        val source = Session(
            id = "mixed",
            name = "Mixed session",
            exercises = listOf(
                resistanceExercise("lift"),
                Exercise(
                    id = "run",
                    name = "Run",
                    cardioDetails = CardioDetails(
                        type = CardioType.RUN_OUTDOOR,
                        targetDurationSeconds = 10 * 60,
                    ),
                ),
            ),
        )

        val result = HybridSessionDurationPolicy.resolve(source, bindingBudgetMinutes = 10)

        assertTrue(result is HybridSessionDurationPolicyResult.Invalid)
        val error = (result as HybridSessionDurationPolicyResult.Invalid).error
        assertTrue(error is HybridSessionDurationPolicyError.BudgetTooShort)
        val budgetError = error as HybridSessionDurationPolicyError.BudgetTooShort
        assertTrue(budgetError.estimatedMinutes > 10)
        assertEquals(10, budgetError.budgetMinutes)
        assertEquals(HybridSessionDurationBudgetSource.BINDING, budgetError.source)
    }

    @Test
    fun anExplicitBudgetCannotMakeAnEmptySessionLookTrainable() {
        val empty = Session(id = "empty", name = "Empty", targetDurationMinutes = 30)

        val result = HybridSessionDurationPolicy.resolve(empty, bindingBudgetMinutes = null)

        assertTrue(result is HybridSessionDurationPolicyResult.Invalid)
        assertEquals(
            HybridSessionDurationPolicyError.NoExecutableContent(30, HybridSessionDurationBudgetSource.SESSION_TARGET),
            (result as HybridSessionDurationPolicyResult.Invalid).error,
        )
    }

    @Test
    fun cardioContentWithoutDurationIsNotAssignedAnInventedOneMinuteEstimate() {
        val source = Session(
            id = "distance-only",
            name = "Distance only",
            exercises = listOf(
                Exercise(
                    id = "run",
                    name = "Run",
                    cardioDetails = CardioDetails(
                        type = CardioType.RUN_OUTDOOR,
                        targetDurationSeconds = 0,
                        targetDistanceKm = 5.0,
                    ),
                ),
            ),
            targetDurationMinutes = 30,
        )

        val result = HybridSessionDurationPolicy.resolve(source, bindingBudgetMinutes = null)

        assertTrue(result is HybridSessionDurationPolicyResult.Invalid)
        assertEquals(
            HybridSessionDurationPolicyError.EstimateUnavailable,
            (result as HybridSessionDurationPolicyResult.Invalid).error,
        )
    }

    @Test
    fun variantsAreAlternativesAndDoNotIncreaseTheBaseSessionEstimate() {
        val source = resistanceSession(targetDurationMinutes = null).copy(
            sessionB = resistanceSession("variant-b", targetDurationMinutes = 90, setCount = 12),
            sessionC = resistanceSession("variant-c", targetDurationMinutes = 90, setCount = 12),
            sessionD = resistanceSession("variant-d", targetDurationMinutes = 90, setCount = 12),
        )
        val expectedEstimate = SessionDurationEstimator.estimate(source).totalMinutes
        val variantEstimate = SessionDurationEstimator.estimate(source.sessionB!!).totalMinutes

        val result = HybridSessionDurationPolicy.resolve(source, bindingBudgetMinutes = null)

        assertTrue(result is HybridSessionDurationPolicyResult.Ready)
        val resolution = (result as HybridSessionDurationPolicyResult.Ready).resolution
        assertEquals(expectedEstimate, resolution.placementMinutes)
        assertTrue(resolution.placementMinutes < variantEstimate)
    }

    private fun resistanceSession(
        id: String = "lift-session",
        targetDurationMinutes: Int?,
        setCount: Int = 1,
    ) = Session(
        id = id,
        name = id,
        exercises = listOf(resistanceExercise("$id-exercise", setCount)),
        targetDurationMinutes = targetDurationMinutes,
    )

    private fun resistanceExercise(id: String, setCount: Int = 1) = Exercise(
        id = id,
        name = "Resistance movement",
        sets = (1..setCount).map { index -> ExerciseSet(id = "$id-set-$index", targetReps = 5) },
    )
}
