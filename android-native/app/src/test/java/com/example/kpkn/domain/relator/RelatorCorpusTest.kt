package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorCorpusTest {
    @Test
    fun thirty_sets_unique_fingerprints_and_no_consecutive_topic() {
        var state = RelatorSelectorState()
        var memory = RelatorLongTermMemory()
        val fingerprints = mutableListOf<String>()
        val topics = mutableListOf<RelatorTopic>()
        val texts = mutableListOf<String>()
        repeat(30) { index ->
            val ctx = corpusContext(index)
            val result = RelatorEngine.resolve(ctx, selectorState = state, longTermMemory = memory)
            state = result.selectorState
            memory = result.longTermMemory
            val fp = result.line.fingerprint
            assertFalse("blank fingerprint at $index", fp.isNullOrBlank())
            assertFalse("repeated fingerprint $fp at $index", fp in fingerprints)
            fingerprints += fp!!
            val topic = result.line.topic
            assertNotNull("missing topic at $index", topic)
            if (topics.isNotEmpty() && !ctx.userReacted && topic != RelatorTopic.REACTION) {
                assertTrue(
                    "consecutive topic ${topics.last()} -> $topic at $index",
                    topic != topics.last() || result.line.holdPrevious,
                )
            }
            topics += topic!!
            val text = result.line.text
            assertFalse("blank text at $index", text.isNullOrBlank())
            texts += text!!
        }
        assertEquals(30, fingerprints.size)
        assertEquals(30, fingerprints.toSet().size)
        assertTrue("need topic variety, got ${topics.toSet()}", topics.toSet().size >= 4)
        assertEquals(30, texts.size)
    }
}

private fun corpusContext(index: Int): RelatorContext = relatorContext(
    setKey = "ex_$index",
    setIndex = index % 5,
    setCount = 5,
    idleCycle = 1 + index % 3,
    completed = index,
    total = 30,
    isAmrap = index % 7 == 0,
    isTopSet = index % 11 == 0,
    dailyScore = if (index % 5 == 0) 48 else 78,
    restActive = index % 6 == 5,
    phase = if (index % 6 == 5) RelatorSessionPhase.REST else RelatorSessionPhase.WORKING,
    prJustNow = index == 17,
    mediaCount = if (index == 21) 2 else 0,
    warmupRemaining = if (index == 2) 2 else 0,
    weekIndex = (index % 4) + 1,
)
