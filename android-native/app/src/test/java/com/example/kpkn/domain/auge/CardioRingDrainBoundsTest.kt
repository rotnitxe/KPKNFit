package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.cardio.CardioHiitProgramBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioRingDrainBoundsTest {
    private val settings = Settings()

    @Test
    fun rpeAndProtocolMonotonicityArePreserved() {
        val steady = CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 30 * 60)
        val easy = CardioRingDrainEngine.drain(steady, 30 * 60, 3.0, settings)
        val medium = CardioRingDrainEngine.drain(steady, 30 * 60, 6.0, settings)
        val hard = CardioRingDrainEngine.drain(steady, 30 * 60, 9.0, settings)
        assertTrue(hard.cns > medium.cns && medium.cns > easy.cns)
        assertTrue(hard.muscular > medium.muscular && medium.muscular > easy.muscular)
        assertTrue(hard.spinal > medium.spinal && medium.spinal > easy.spinal)

        val hiitConfig = CardioHiitConfig(warmupSeconds = 0, workSeconds = 30, restSeconds = 30, rounds = 8, targetRpe = 9.0, protocol = HiitProtocol.HIIT)
        val sitConfig = hiitConfig.copy(protocol = HiitProtocol.SIT, targetRpe = 10.0)
        val hiit = CardioHiitProgramBuilder.buildDetails(hiitConfig, CardioType.AIR_BIKE)
        val sit = CardioHiitProgramBuilder.buildDetails(sitConfig, CardioType.AIR_BIKE)
        // Compare equal minutes of WORK, as the contract states; HIIT includes
        // its recovery/cooldown time in the scheduled duration.
        val continuous = CardioDetails(type = CardioType.AIR_BIKE, targetDurationSeconds = hiitConfig.workSeconds * hiitConfig.rounds)
        val c = CardioRingDrainEngine.drain(continuous, continuous.effectiveDurationSeconds(), 9.0, settings)
        val h = CardioRingDrainEngine.drain(hiit, hiit.effectiveDurationSeconds(), 9.0, settings)
        val s = CardioRingDrainEngine.drain(sit, sit.effectiveDurationSeconds(), 10.0, settings)
        assertTrue(s.cns > h.cns && h.cns > c.cns)
    }

    @Test
    fun modalityMapAndOutputsStayBounded() {
        val run = CardioDetails(type = CardioType.RUN_OUTDOOR, targetDurationSeconds = 45 * 60)
        val drain = CardioRingDrainEngine.drain(run, 45 * 60, 7.0, settings)
        listOf(drain.cns, drain.muscular, drain.spinal).forEach { assertTrue(it in 0.0..100.0) }
        assertTrue(drain.muscleDrains.isNotEmpty())
        assertTrue(drain.spinal > CardioRingDrainEngine.drain(CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 45 * 60), 45 * 60, 7.0, settings).spinal)
    }
}
