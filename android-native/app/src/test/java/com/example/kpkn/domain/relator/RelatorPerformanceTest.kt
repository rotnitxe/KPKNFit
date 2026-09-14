package com.example.kpkn.domain.relator

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class RelatorPerformanceTest {
    @Test
    fun resolve_median_under_one_millisecond() {
        val ctx = relatorContext()
        var state = RelatorSelectorState()
        var memory = RelatorLongTermMemory()
        repeat(40) {
            val result = RelatorEngine.resolve(ctx, selectorState = state, longTermMemory = memory)
            state = result.selectorState
            memory = result.longTermMemory
        }
        val samples = LongArray(120)
        state = RelatorSelectorState()
        memory = RelatorLongTermMemory()
        for (i in samples.indices) {
            samples[i] = measureNanoTime {
                val result = RelatorEngine.resolve(
                    relatorContext(setKey = "perf_$i", setIndex = i % 5, idleCycle = i % 3),
                    selectorState = state,
                    longTermMemory = memory,
                )
                state = result.selectorState
                memory = result.longTermMemory
            }
        }
        val median = samples.sorted()[samples.size / 2]
        assertTrue("median resolve ${median}ns >= 1ms", median < 1_000_000L)
    }
}
