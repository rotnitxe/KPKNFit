package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorSelectorTest {
    @Test
    fun seed_is_deterministic() {
        val a = RelatorSelector.deterministicSeed("s", "ex_1", 2)
        val b = RelatorSelector.deterministicSeed("s", "ex_1", 2)
        val c = RelatorSelector.deterministicSeed("s", "ex_1", 3)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun does_not_repeat_topic_unless_reaction() {
        val ctx = relatorContext(setKey = "ex_2")
        val plan = PlanObserver.observe(ctx).first()
        val history = HistoryObserver.observe(ctx).first()
        val state = RelatorSelectorState(lastTopic = RelatorTopic.PLAN, setsSeen = 3)
        val picked = RelatorSelector.select(listOf(plan, history), state, ctx, RelatorLongTermMemory())
        assertEquals(RelatorTopic.HISTORY, picked?.topic)
    }

    @Test
    fun reaction_can_repeat_topic() {
        val ctx = relatorContext(userReacted = true)
        val reaction = RelatorCandidate(
            topic = RelatorTopic.REACTION,
            priority = 90,
            relevance = 1.0,
            lines = listOf("reaccion"),
            fingerprintBase = "R",
            isReaction = true,
        )
        val state = RelatorSelectorState(lastTopic = RelatorTopic.REACTION, setsSeen = 1)
        val picked = RelatorSelector.select(listOf(reaction), state, ctx, RelatorLongTermMemory())
        assertEquals(RelatorTopic.REACTION, picked?.topic)
    }
}
