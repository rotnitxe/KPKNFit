package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PrEffortMode
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.domain.calculations.calculateSuggestedLoadForTarget
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionEditorUnilateralSmartLoadTest {

    private fun baseUnilateralExercise(): Exercise = Exercise(
        id = "uni-row",
        name = "Remo unilateral",
        trainingMode = TrainingMode.REPS,
        isUnilateral = true,
        unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
        unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
        unilateralIntensityMode = UnilateralIntensityMode.SHARED,
        prFor1RM = PrReference(weight = 60.0, reps = 5, effortMode = PrEffortMode.FAILURE),
        sets = listOf(
            ExerciseSet(
                id = "s1",
                targetReps = 5,
                intensityMode = IntensityMode.FAILURE,
                isFailure = true,
                leftTarget = UnilateralTarget(targetReps = 5, intensityMode = IntensityMode.FAILURE, weight = 60.0),
                rightTarget = UnilateralTarget(targetReps = 5, intensityMode = IntensityMode.FAILURE, weight = 60.0),
            ),
            ExerciseSet(
                id = "s2",
                targetReps = 5,
                intensityMode = IntensityMode.FAILURE,
                isFailure = true,
                leftTarget = UnilateralTarget(targetReps = 5, intensityMode = IntensityMode.FAILURE, weight = 60.0),
                rightTarget = UnilateralTarget(targetReps = 5, intensityMode = IntensityMode.FAILURE, weight = 60.0),
            ),
        ),
    ).normalizeExercise()

    @Test
    fun pr60x5FailureProduces67_5E1rmAndChangingRepsTo8RecalculatesAllSidesTo54_5() {
        val initial = baseUnilateralExercise()
        assertEquals(67.5, resolveReferenceCapacity(initial)!!, 0.01)
        assertEquals(60.0, initial.sets[0].leftTarget!!.weight!!, 0.01)
        assertEquals(60.0, initial.sets[0].rightTarget!!.weight!!, 0.01)

        // Cambiar reps de 5 a 8 en R1 y aplicar a todas en modo SHARED recalcula R1/L1/R2/L2 (~54.5 kg)
        val editedS1 = initial.sets[0].copy(
            targetReps = 8,
            rightTarget = initial.sets[0].rightTarget!!.copy(targetReps = 8, weight = null),
            leftTarget = initial.sets[0].leftTarget!!.copy(targetReps = 8, weight = null),
        )
        val appliedS2 = initial.sets[1].withSideConfigOf(editedS1, side = "R", sharedSides = true)
        val after = initial.copy(sets = listOf(editedS1, appliedS2))
            .withExplicitRmLoads(initial)
            .normalizeExercise()

        after.sets.forEach { set ->
            assertEquals(8, set.targetReps)
            assertEquals(8, set.leftTarget!!.targetReps)
            assertEquals(8, set.rightTarget!!.targetReps)
            assertEquals(54.5, set.leftTarget!!.weight!!, 0.25)
            assertEquals(54.5, set.rightTarget!!.weight!!, 0.25)
        }
        assertEquals("2 × 8 · hasta el fallo · 1:30", sessionExerciseDose(after.sets, after.trainingMode, 90))
        assertEquals("2 × 8 @ 54.5kg", formatExerciseCollapsedSummary(after))
    }

    @Test
    fun editingPrUpdatesSuggestedLoadsAcrossAllUnilateralSidesImmediately() {
        val initial = baseUnilateralExercise()
        val updatedPr = initial.copy(
            prFor1RM = PrReference(weight = 70.0, reps = 5, effortMode = PrEffortMode.FAILURE),
        ).withExplicitRmLoads(initial).normalizeExercise()

        assertEquals(78.75, updatedPr.reference1RM!!, 0.05)
        updatedPr.sets.forEach { set ->
            assertEquals(70.0, set.leftTarget!!.weight!!, 0.25)
            assertEquals(70.0, set.rightTarget!!.weight!!, 0.25)
        }
    }

    @Test
    fun sharedModeMirrorsExistingSidesWithoutResurrectingDeletedNullSide() {
        val initial = baseUnilateralExercise()
        val s2WithoutLeft = initial.sets[1].copy(leftTarget = null)
        val updatedS1 = initial.sets[0].copy(
            rightTarget = initial.sets[0].rightTarget!!.copy(
                targetReps = 10,
                intensityMode = IntensityMode.RIR,
                targetRIR = 2,
                weight = null,
            ),
        )
        val syncedS2 = s2WithoutLeft.withSideConfigOf(updatedS1, side = "R", sharedSides = true)
        assertNull("No debe resucitar un lado eliminado (null)", syncedS2.leftTarget)
        assertEquals(10, syncedS2.rightTarget!!.targetReps)
        assertEquals(IntensityMode.RIR, syncedS2.rightTarget!!.intensityMode)
        assertEquals(2, syncedS2.rightTarget!!.targetRIR)
        assertEquals(10, syncedS2.targetReps)
        assertEquals(2, syncedS2.targetRIR)
    }

    @Test
    fun clearingPrReferenceClearsSuggestedWeightsOnSetAndBothSides() {
        val initial = baseUnilateralExercise()
        val cleared = initial.copy(prFor1RM = null, reference1RM = null)
            .withExplicitRmLoads(initial)
            .normalizeExercise()

        assertNull(cleared.reference1RM)
        cleared.sets.forEach { set ->
            assertNull(set.weight)
            assertNull(set.leftTarget!!.weight)
            assertNull(set.rightTarget!!.weight)
            assertNull(calculateSuggestedLoadForTarget(cleared, set, set.leftTarget))
            assertNull(calculateSuggestedLoadForTarget(cleared, set, set.rightTarget))
        }
    }
}
