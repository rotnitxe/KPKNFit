package com.example.kpkn.domain.relator

import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorComposerTest {
    @Test
    fun has_at_least_six_openers() {
        assertTrue(RelatorComposer.OPENERS.size >= 6)
    }

    @Test
    fun clips_to_140_chars_when_possible() {
        val candidate = RelatorCandidate(
            topic = RelatorTopic.PLAN,
            priority = 50,
            relevance = 1.0,
            lines = listOf("x".repeat(200)),
            fingerprintBase = "PLAN",
        )
        val (line, _) = RelatorComposer.compose(
            candidate,
            relatorContext(),
            RelatorSelectorState(),
            RelatorLongTermMemory(),
        )
        assertTrue((line.text?.length ?: 0) <= RELATOR_LINE_MAX_CHARS)
    }

    @Test
    fun applies_feminine_voice() {
        val candidate = RelatorCandidate(
            topic = RelatorTopic.SITUATE,
            priority = 10,
            relevance = 1.0,
            lines = listOf("Queda {ready} para {ex}."),
            fingerprintBase = "S",
            isReaction = true,
        )
        val (line, _) = RelatorComposer.compose(
            candidate,
            relatorContext(feminine = true, idleCycle = 0),
            RelatorSelectorState(),
            RelatorLongTermMemory(),
        )
        assertTrue(line.text!!.contains("lista"))
    }

    @Test
    fun observers_pad_to_at_least_six_variants() {
        val ctx = relatorContext(
            isAmrap = true,
            isTopSet = true,
            restActive = true,
            phase = RelatorSessionPhase.REST,
            dailyScore = 40,
            warmupRemaining = 2,
            prJustNow = true,
            mediaCount = 2,
            coachBody = "Baja un punto.",
            conceptId = "brace",
            conceptLines = listOf("Brace antes de empujar."),
            executionCues = listOf("Rodillas afuera."),
        )
        RelatorEngine.defaultObservers().forEach { observer ->
            observer.observe(ctx).forEach { candidate ->
                assertTrue(
                    "${candidate.topic} has ${candidate.lines.size} lines",
                    candidate.lines.size >= 6,
                )
            }
        }
    }
}
