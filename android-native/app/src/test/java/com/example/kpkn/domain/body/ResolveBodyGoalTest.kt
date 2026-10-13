package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.TypedBodyGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveBodyGoalTest {

    private fun plan(
        typed: TypedBodyGoal? = null,
        goalType: GoalMetric = GoalMetric.WEIGHT,
        goalValue: Double = 0.0,
    ) = NutritionPlan(id = "plan-1", name = "Plan", goalType = goalType, goalValue = goalValue, typedBodyGoal = typed)

    private fun bodyGoal(
        metric: BodyMetric,
        target: Double,
        updatedAt: Long,
        linkedPlanId: String? = null,
        createdAt: Long = 1_000L,
    ) = BodyGoal(
        id = "goal-${metric.name}-$updatedAt",
        metric = metric,
        targetValueSi = target,
        unitSi = if (metric == BodyMetric.WEIGHT) "kg" else "%",
        origin = CalculationOrigin.MANUAL,
        linkedPlanId = linkedPlanId,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun `plan con meta tipada usa su metrica y su valor`() {
        val resolved = resolveBodyGoal(plan(typed = TypedBodyGoal(GoalMetric.WEIGHT, 75.0)), emptyList())

        assertEquals(ResolvedBodyGoal(GoalMetric.WEIGHT, 75.0, null), resolved)
    }

    @Test
    fun `la meta del plan toma la fecha de su fila derivada`() {
        val goals = listOf(bodyGoal(BodyMetric.WEIGHT, 75.0, updatedAt = 5L, linkedPlanId = "plan-1", createdAt = 42L))

        val resolved = resolveBodyGoal(plan(typed = TypedBodyGoal(GoalMetric.WEIGHT, 75.0)), goals)

        assertEquals(ResolvedBodyGoal(GoalMetric.WEIGHT, 75.0, 42L), resolved)
    }

    @Test
    fun `meta de tipo y valor del plan cuando no hay meta tipada`() {
        val resolved = resolveBodyGoal(plan(goalType = GoalMetric.BODY_FAT, goalValue = 14.0), emptyList())

        assertEquals(ResolvedBodyGoal(GoalMetric.BODY_FAT, 14.0, null), resolved)
    }

    @Test
    fun `sin meta en el plan, el objetivo propio de grasa conserva su metrica y no pasa a peso`() {
        val goals = listOf(bodyGoal(BodyMetric.BODY_FAT_PERCENT, 15.0, updatedAt = 10L))

        val resolved = resolveBodyGoal(plan(goalType = GoalMetric.WEIGHT, goalValue = 0.0), goals)

        assertEquals(GoalMetric.BODY_FAT, resolved?.metric)
        assertEquals(15.0, resolved!!.targetValueSi, 1e-9)
    }

    @Test
    fun `entre objetivos propios gana el actualizado mas recientemente`() {
        val goals = listOf(
            bodyGoal(BodyMetric.WEIGHT, 80.0, updatedAt = 5L),
            bodyGoal(BodyMetric.MUSCLE_MASS_PERCENT, 40.0, updatedAt = 10L),
        )

        val resolved = resolveBodyGoal(null, goals)

        assertEquals(ResolvedBodyGoal(GoalMetric.MUSCLE_MASS, 40.0, 1_000L), resolved)
    }

    @Test
    fun `sin nada, o solo con metas de otro plan, no hay objetivo`() {
        assertNull(resolveBodyGoal(null, emptyList()))
        assertNull(resolveBodyGoal(plan(), listOf(bodyGoal(BodyMetric.WEIGHT, 80.0, updatedAt = 1L, linkedPlanId = "otro"))))
    }
}
