package com.example.kpkn.domain.relator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorMemoryTest {
    @Test
    fun concept_is_blocked_for_seven_days() {
        val memory = RelatorLongTermMemory().recordConcept("brace", epochDay = 100)
        assertTrue(memory.conceptWasShownRecently("brace", epochDay = 106))
        assertFalse(memory.conceptWasShownRecently("brace", epochDay = 107))
    }

    @Test
    fun encode_round_trip() {
        val memory = RelatorLongTermMemory()
            .recordConcept("brace", 50)
            .recordOpener("Ahora")
            .bumpExercise("sq")
        val decoded = RelatorLongTermMemory.decode(memory.encode())
        assertTrue(decoded.conceptWasShownRecently("brace", 50))
        assertTrue("Ahora" in decoded.openersUsed)
        assertTrue(decoded.exerciseCounts["sq"] == 1)
    }

    @Test
    fun selector_skips_recent_concept() {
        val ctx = relatorContext(
            conceptId = "brace",
            conceptLines = listOf("Aprieta el brace antes de {ex}."),
            executionCues = listOf("Pecho alto."),
        )
        val memory = RelatorLongTermMemory().recordConcept("brace", epochDay = 10)
        val technique = TechniqueObserver.observe(ctx).first()
        val other = HistoryObserver.observe(ctx).first()
        val picked = RelatorSelector.select(
            candidates = listOf(technique, other),
            state = RelatorSelectorState(),
            context = ctx,
            longTerm = memory,
            nowEpochDay = 12,
        )
        assertTrue(picked?.topic != RelatorTopic.TECHNIQUE)
    }
}
