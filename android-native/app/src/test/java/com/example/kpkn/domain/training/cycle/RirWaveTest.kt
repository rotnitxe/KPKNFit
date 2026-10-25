package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.protocols.definitions.NativeDoseLevel
import com.example.kpkn.data.protocols.definitions.NativeWeekBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class RirWaveTest {
    @Test fun levels_prescribe_the_wave_and_deload() {
        NativeDoseLevel.entries.forEach { level ->
            val expected = if (level == NativeDoseLevel.BEGINNER) listOf(3, 3, 3, 2, 2, 4)
                else listOf(3, 2, 2, 1, 1, 4)
            assertEquals(expected, (1..6).map { RirWave.targetRir(level, it) })
            assertEquals(expected, (1..6).map { NativeWeekBuilder.trainingRir(level, it) })
        }
    }

    @Test fun boundaries_are_deterministic() {
        assertEquals(3, RirWave.targetRir(NativeDoseLevel.BEGINNER, 0))
        assertEquals(3, RirWave.targetRir(NativeDoseLevel.ADVANCED, -2))
        assertEquals(3, RirWave.targetRir(NativeDoseLevel.ADVANCED, Int.MIN_VALUE))
        assertEquals(4, RirWave.targetRir(NativeDoseLevel.ADVANCED, 9))
        assertEquals(4, RirWave.targetRir(NativeDoseLevel.ADVANCED, 4, cycleWeeks = 4))
    }
}
