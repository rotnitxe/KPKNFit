package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WarmupSetDefinition
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
}
