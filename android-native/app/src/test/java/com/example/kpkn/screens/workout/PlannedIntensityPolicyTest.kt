package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.PlanDeviationType
import com.example.kpkn.data.models.effectiveRepEquivalent
import com.example.kpkn.domain.auge.AugeFatigueEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedIntensityPolicyTest {
    @Test
    fun repRangeEvaluation_acceptsValueInsideInclusiveRange() {
        val evaluation = evaluateRepRange(actual = 5.0, range = RepRange(4, 6))

        assertTrue(evaluation?.isInRange == true)
        assertEquals(0.0, evaluation?.delta ?: -1.0, 0.001)
        assertEquals(0.0, evaluation?.debt ?: -1.0, 0.001)
    }

    @Test
    fun repRangeEvaluation_usesMinimumForDeficitAndMaximumForExcess() {
        val below = evaluateRepRange(actual = 3.0, range = RepRange(4, 6))
        val above = evaluateRepRange(actual = 7.0, range = RepRange(4, 6))

        assertEquals(-1.0, below?.delta ?: 0.0, 0.001)
        assertEquals(1.0, below?.debt ?: 0.0, 0.001)
        assertEquals(1.0, above?.delta ?: 0.0, 0.001)
        assertEquals(0.0, above?.debt ?: -1.0, 0.001)
    }

    @Test
    fun repRangeEvaluation_amrap_hasNoUpperPenalty() {
        val evaluation = evaluateRepRange(
            actual = 12.0,
            range = RepRange(4, 6),
            amrapActive = true,
            amrapMinimum = 4,
        )

        assertTrue(evaluation?.isInRange == true)
        assertEquals(0.0, evaluation?.debt ?: -1.0, 0.001)
    }

    @Test
    fun repRangeDeviation_usesRangeEdges_withoutThreeRepTolerance() {
        val planned = ExerciseSet(
            id = "range",
            targetReps = 6,
            targetRepsRange = RepRange(4, 6),
        )

        val below = WorkoutPlanDeviationSupport.detect(
            exerciseId = "exercise",
            exerciseName = "Press",
            setIdx = 0,
            plannedSet = planned,
            actualWeight = 0.0,
            actualReps = 3,
            advanced = SetAdvancedFeedback(),
            suggestedWeight = null,
        )
        val above = WorkoutPlanDeviationSupport.detect(
            exerciseId = "exercise",
            exerciseName = "Press",
            setIdx = 0,
            plannedSet = planned,
            actualWeight = 0.0,
            actualReps = 7,
            advanced = SetAdvancedFeedback(),
            suggestedWeight = null,
        )

        assertEquals(PlanDeviationType.REPS_LOW, below.single().type)
        assertEquals(PlanDeviationType.REPS_HIGH, above.single().type)
    }

    @Test
    fun repRange_survives_live_normalization_and_usesUpperAnchor() {
        val normalized = WorkoutEditingRules.normalizeLiveEditedSet(
            mode = com.example.kpkn.data.models.TrainingMode.REPS,
            set = ExerciseSet(id = "range", targetReps = 6, targetRepsRange = RepRange(4, 6)),
        )

        assertEquals(RepRange(4, 6), normalized.targetRepsRange)
        assertEquals(6, normalized.targetReps)
    }

    @Test
    fun amrap_normalization_keepsMinimumAndRecordsBelowMinimumDeviation() {
        val planned = ExerciseSet(
            id = "amrap",
            targetReps = 6,
            targetRepsRange = RepRange(4, 6),
            isAmrap = true,
            intensityMode = IntensityMode.AMRAP,
        )
        val normalized = WorkoutEditingRules.normalizeLiveEditedSet(
            mode = com.example.kpkn.data.models.TrainingMode.AMRAP,
            set = planned,
        )
        val deviations = WorkoutPlanDeviationSupport.detect(
            exerciseId = "exercise",
            exerciseName = "Press",
            setIdx = 0,
            plannedSet = normalized,
            actualWeight = 0.0,
            actualReps = 3,
            advanced = SetAdvancedFeedback(amrapMinimumReps = 4),
            suggestedWeight = null,
        )

        assertEquals(4, normalized.targetRepsRange?.min)
        assertEquals(IntensityMode.AMRAP, normalized.intensityMode)
        assertEquals(PlanDeviationType.AMRAP_BELOW_MINIMUM, deviations.single().type)
    }

    @Test
    fun omitted_intensity_stays_omitted_after_live_normalization() {
        val normalized = WorkoutEditingRules.normalizeLiveEditedSet(
            mode = com.example.kpkn.data.models.TrainingMode.REPS,
            set = ExerciseSet(id = "set", targetReps = 8),
        )

        assertFalse(exerciseHasPlannedIntensity(Exercise(id = "exercise", name = "Press", sets = listOf(normalized))))
        assertTrue(normalized.intensityMode == null)
    }

    @Test
    fun unilateral_intensity_is_detected_without_inventing_a_mode() {
        val set = ExerciseSet(
            id = "set",
            targetReps = 8,
            leftTarget = UnilateralTarget(targetReps = 8, targetRIR = 2),
        )

        assertTrue(exerciseHasPlannedIntensity(Exercise(id = "exercise", name = "Split squat", isUnilateral = true, sets = listOf(set))))
        assertFalse(exerciseHasPlannedIntensity(Exercise(id = "plain", name = "Press", sets = listOf(ExerciseSet(id = "plain-set", targetReps = 8)))))
    }

    @Test
    fun load_and_amrap_modes_do_not_require_an_rpe_input() {
        val load = Exercise(id = "load", name = "Load", sets = listOf(ExerciseSet(id = "load-set", intensityMode = IntensityMode.LOAD)))
        val amrap = Exercise(id = "amrap", name = "AMRAP", sets = listOf(ExerciseSet(id = "amrap-set", intensityMode = IntensityMode.AMRAP, isAmrap = true)))

        assertFalse(exerciseHasPlannedIntensity(load))
        assertFalse(exerciseHasPlannedIntensity(amrap))
    }

    @Test
    fun explicit_live_amrap_override_can_disable_a_planned_amrap() {
        val planned = ExerciseSet(
            id = "planned-amrap",
            targetReps = 8,
            isAmrap = true,
            intensityMode = IntensityMode.AMRAP,
        )

        assertFalse(resolveAmrapActive(planned, requestedOverride = false, explicitOverride = false))
    }

    @Test
    fun explicit_live_amrap_override_can_enable_a_plain_set() {
        val planned = ExerciseSet(id = "plain", targetReps = 8)

        assertTrue(resolveAmrapActive(planned, requestedOverride = false, explicitOverride = true))
    }

    @Test
    fun omitted_live_amrap_override_follows_the_plan() {
        val planned = ExerciseSet(id = "planned-amrap", isAmrap = true, intensityMode = IntensityMode.AMRAP)

        assertTrue(resolveAmrapActive(planned, requestedOverride = false, explicitOverride = null))
    }

    @Test
    fun amrap_deviation_uses_the_live_override_not_only_the_plan() {
        val plain = ExerciseSet(id = "plain", targetReps = 6)
        val deviations = WorkoutPlanDeviationSupport.detect(
            exerciseId = "exercise",
            exerciseName = "Press",
            setIdx = 0,
            plannedSet = plain,
            actualWeight = 0.0,
            actualReps = 3,
            advanced = SetAdvancedFeedback(
                amrapOverride = true,
                amrapMinimumReps = 4,
            ),
            suggestedWeight = null,
        )

        assertEquals(PlanDeviationType.AMRAP_BELOW_MINIMUM, deviations.single().type)
    }

    @Test
    fun help_and_partials_survive_record_mapping_for_history_and_auge() {
        val recorded = applyAdvancedFeedback(
            base = CompletedSet(
                id = "set",
                reps = 8,
                actualIntensityMode = IntensityMode.RPE,
                actualIntensityValue = 8.0,
            ),
            advanced = SetAdvancedFeedback(
                isPartial = true,
                partialReps = 2,
                assistedReps = 1,
            ),
        )

        assertTrue(recorded.isPartial)
        assertEquals(2, recorded.partialReps)
        assertEquals(1, recorded.assistedReps)
        assertEquals(9.0, recorded.effectiveRepEquivalent(), 0.001)
        // AUGE reads the same persisted flags that the history surface shows.
        assertEquals(10.0, AugeFatigueEngine.getEffectiveRPE(recorded), 0.001)
    }
}
