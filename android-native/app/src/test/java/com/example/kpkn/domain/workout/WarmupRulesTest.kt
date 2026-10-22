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
    fun live_add_advisory_warns_beyond_the_recommended_max_with_the_editor_text() {
        val four = (1..4).map { WarmupSetDefinition("w$it", 0.5, 5) }
        assertEquals(4, warmupMaxRecommended(1))
        assertEquals(6, warmupMaxRecommended(3))
        assertNull(warmupLiveAddAdvisory(four, effectiveSetCount = 1))
        assertEquals(
            "Demasiadas aproximaciones: máximo recomendado 4 para 1 series efectivas.",
            warmupLiveAddAdvisory(four + WarmupSetDefinition("w5", 0.5, 5), effectiveSetCount = 1),
        )
    }

    @Test
    fun live_add_advisory_only_looks_at_the_newly_added_last_warmup_for_the_85_rule() {
        val plan = listOf(WarmupSetDefinition("a", 0.85, 3), WarmupSetDefinition("b", 0.5, 6))
        assertNull(warmupLiveAddAdvisory(plan, effectiveSetCount = 3))
        val added = plan + WarmupSetDefinition("c", 0.9, 1)
        assertTrue(warmupLiveAddAdvisory(added, effectiveSetCount = 3)!!.startsWith("Una aproximación al 85% o más"))
        assertNull(warmupLiveAddAdvisory(emptyList(), effectiveSetCount = 3))
    }
}
