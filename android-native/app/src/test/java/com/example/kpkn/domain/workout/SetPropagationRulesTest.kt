package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnilateralTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SetPropagationRulesTest {
    @Test
    fun copies_planned_value_but_preserves_target_identity() {
        val source = ExerciseSet(id = "source", targetReps = 8, targetRPE = 8.5)
        val target = ExerciseSet(id = "target", targetReps = 12, targetRPE = 7.0)

        val result = target.copyPlannedValueFrom(source)

        assertEquals("target", result.id)
        assertEquals(8, result.targetReps)
        assertEquals(8.5, result.targetRPE)
    }

    @Test
    fun unilateral_propagation_can_target_only_left_side() {
        val source = ExerciseSet(
            id = "source",
            leftTarget = UnilateralTarget(weight = 20.0, targetReps = 8),
            rightTarget = UnilateralTarget(weight = 18.0, targetReps = 8),
        )
        val target = ExerciseSet(
            id = "target",
            leftTarget = UnilateralTarget(weight = 10.0, targetReps = 12),
            rightTarget = UnilateralTarget(weight = 9.0, targetReps = 12),
        )

        val result = target.copyPlannedValueFrom(source, PropagationSide.LEFT)

        assertEquals(source.leftTarget, result.leftTarget)
        assertEquals(target.rightTarget, result.rightTarget)
    }
}
