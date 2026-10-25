package com.example.kpkn.domain.training.insights

import com.example.kpkn.domain.multisport.SportId
import org.junit.Assert.*
import org.junit.Test

class TrainingInsightsEngineTest {
    private val now = 200L * 86_400_000L
    private fun fact(id: String, day: Long, value: Double = 0.0, sport: SportId = SportId.RUNNING) =
        TrainingFact(id, id, day * 86_400_000L, sport, "distance", value, "m", FactOrigin.OBSERVED,
            "flat_5k", "manual", "distance_v1",context=mapOf("surface" to "track"))

    @Test fun `every sport keeps its own comparisons and a real zero`() {
        for (sport in SportId.entries) {
            val current = fact("current", 200, 0.0, sport)
            val previous = (194L..199L).map { fact("$sport-$it", it, (it - 194).toDouble(), sport) }
            val result = TrainingInsightsEngine.compare(current, previous, now)
            assertEquals(6, result.previous.size)
            assertEquals(2.5, result.median!!, 0.0)
            assertEquals(-2.5, result.differenceFromMedian!!, 0.0)
        }
    }
    @Test fun `future old duplicate and incompatible data cannot enter reference`() {
        val current = fact("current", 200)
        val base = fact("prior", 199, 10.0)
        val result = TrainingInsightsEngine.compare(current, listOf(base, base, fact("future", 201),
            fact("too_old", 1), base.copy(sourceId="unit", unit="km"),
            base.copy(sourceId="unknown", method=null), base.copy(sourceId="estimated", origin=FactOrigin.ESTIMATED)), now)
        assertEquals(listOf(base), result.previous)
        assertNull(result.median)
    }
    @Test fun `unknown context never equals known or unknown and history is bounded`() {
        val unknown = fact("unknown", 200).copy(protocol=null)
        assertFalse(TrainingInsightsEngine.comparable(unknown, unknown))
        assertEquals(1, TrainingInsightsEngine.visible(listOf(fact("today",200),fact("future",201),fact("old",170)), now, 4).size)
        assertEquals(0, TrainingInsightsEngine.coverage(emptyList(), now).activities)
    }
}
