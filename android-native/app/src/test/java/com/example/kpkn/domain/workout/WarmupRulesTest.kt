package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupRulesTest {
    @Test
    fun legacy_percentage_value_is_validated_as_a_percentage() {
        val messages = warmupValidationMessages(
            warmupSets = listOf(WarmupSetDefinition("legacy", 50.0, 6)),
            effectiveSetCount = 3,
        )

        assertTrue(messages.isEmpty())
    }

    @Test
    fun percentage_below_editor_floor_keeps_warning() {
        val messages = warmupValidationMessages(
            warmupSets = listOf(WarmupSetDefinition("invalid", 0.05, 6)),
            effectiveSetCount = 3,
        )

        assertTrue(messages.any { it.contains("10%") })
    }

    @Test
    fun percentage_near_working_load_warns_about_effective_series() {
        val messages = warmupValidationMessages(
            warmupSets = listOf(WarmupSetDefinition("near-working", 0.85, 5)),
            effectiveSetCount = 3,
        )

        assertTrue(messages.any { it.contains("serie efectiva") })
    }

    @Test
    fun recommended_max_is_twice_the_effective_sets_with_a_floor_of_four() {
        assertEquals(4, recommendedWarmupMax(1))
        assertEquals(6, recommendedWarmupMax(3))
        assertEquals(10, recommendedWarmupMax(5))
    }

    @Test
    fun warmup_load_applies_the_percentage_to_the_working_weight_not_the_capacity() {
        assertEquals(50.0, warmupLoadKg(0.5, workingWeightKg = 100.0, capacityKg = 130.0)!!, 0.0001)
        assertEquals(85.0, warmupLoadKg(0.85, workingWeightKg = 100.0, capacityKg = 200.0)!!, 0.0001)
    }

    @Test
    fun warmup_load_falls_back_to_capacity_when_the_working_set_has_no_weight() {
        assertEquals(65.0, warmupLoadKg(0.5, workingWeightKg = null, capacityKg = 130.0)!!, 0.0001)
        assertEquals(65.0, warmupLoadKg(0.5, workingWeightKg = 0.0, capacityKg = 130.0)!!, 0.0001)
    }

    @Test
    fun warmup_load_is_unknown_without_any_reference_weight() {
        assertNull(warmupLoadKg(0.5, workingWeightKg = null, capacityKg = null))
    }

    @Test
    fun warmup_load_accepts_the_legacy_percentage_representation() {
        assertEquals(50.0, warmupLoadKg(50.0, workingWeightKg = 100.0, capacityKg = null)!!, 0.0001)
    }

    @Test
    fun exceeding_the_recommended_max_is_reported_with_the_new_rule() {
        val six = (1..7).map { WarmupSetDefinition("w$it", 0.5, 8) }
        val messages = warmupValidationMessages(warmupSets = six, effectiveSetCount = 3)
        assertTrue(messages.any { it.contains("máximo recomendado 6") })
    }
}
