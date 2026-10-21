package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.RecoveryLearningObservation
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveCalibrationAppliesTest {

    @Test
    fun finishSheetHalfHour_doesNotLearnTau() {
        val obs = RecoveryLearningObservation(
            muscle = "cns",
            predictedBattery = 55,
            actualBattery = 70,
            sessionStress = 40.0,
            hoursSinceSession = 0.5,
            sleepQuality = 4,
            nutritionMultiplier = 1.0,
        )
        val (newCns, _) = AugeAdaptiveEngine.updateSystemRecoveryHours(
            currentCnsTau = 36.0,
            currentSpinalTau = 52.0,
            cnsObservation = obs,
            spinalObservation = null,
            totalObservations = 0,
        )
        assertEquals("Finish-near-zero must not invert τ", 36.0, newCns)
    }

    @Test
    fun readinessAfterEightHours_learnsTau() {
        val obs = RecoveryLearningObservation(
            muscle = "cns",
            predictedBattery = 55,
            actualBattery = 70,
            sessionStress = 40.0,
            hoursSinceSession = 8.0,
            sleepQuality = 4,
            nutritionMultiplier = 1.0,
        )
        val (newCns, _) = AugeAdaptiveEngine.updateSystemRecoveryHours(
            currentCnsTau = 36.0,
            currentSpinalTau = 52.0,
            cnsObservation = obs,
            spinalObservation = null,
            totalObservations = 0,
        )
        assertNotNull(newCns)
        assertTrue(newCns!! in 12.0..144.0)
        assertTrue("A delayed sensation should nudge τ", newCns != 36.0)
    }

    @Test
    fun blockedQuarterHour_doesNotLearnTau() {
        val obs = RecoveryLearningObservation(
            muscle = "cns",
            predictedBattery = 55,
            actualBattery = 70,
            sessionStress = 40.0,
            hoursSinceSession = 0.25,
            sleepQuality = 4,
        )
        val (newCns, _) = AugeAdaptiveEngine.updateSystemRecoveryHours(
            currentCnsTau = 36.0,
            currentSpinalTau = null,
            cnsObservation = obs,
            spinalObservation = null,
            totalObservations = 0,
        )
        assertEquals(36.0, newCns)
    }

    @Test
    fun cnsAndSpinalLearningDeltas_moveGlobalBatteries() {
        // Reloj explícito (WP 1.7): lunes 2025-03-03 10:00 UTC; ningún dato depende del reloj real.
        val now = java.time.Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()
        val wellbeing = com.example.kpkn.data.models.DailyWellbeingLog(
            id = "wb",
            date = "2025-03-03",
            manualNeuralBattery = 55,
            manualSpinalBattery = 55,
            manualBatteryAnchorMs = now,
        )
        val baseline = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            adaptiveCache = AugeAdaptiveCache(),
            nowOverrideMs = now,
        )
        val calibrated = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            adaptiveCache = AugeAdaptiveCache(
                cnsLearningDelta = 12.0,
                spinalLearningDelta = -10.0,
            ),
            nowOverrideMs = now,
        )
        assertTrue(
            "Positive CNS delta should raise SYSTEM ring (${baseline.cnc} → ${calibrated.cnc})",
            calibrated.cnc > baseline.cnc,
        )
        assertTrue(
            "Negative spinal delta should lower STRUCTURE ring (${baseline.spinal} → ${calibrated.spinal})",
            calibrated.spinal < baseline.spinal,
        )
    }

    @Test
    fun sleepQuality_doesNotChangeImpliedTau() {
        val base = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 40,
            actualBattery = 70,
            sessionStress = 50.0,
            hoursSinceSession = 24.0,
            sleepQuality = 3,
            nutritionMultiplier = 1.0,
        )
        val goodSleep = base.copy(sleepQuality = 5)
        val badSleep = base.copy(sleepQuality = 1)

        val tauGood = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
            current = mapOf("pectorales" to 48.0),
            observation = goodSleep,
            totalObservations = 0,
        )["pectorales"]!!
        val tauBad = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
            current = mapOf("pectorales" to 48.0),
            observation = badSleep,
            totalObservations = 0,
        )["pectorales"]!!

        assertEquals(tauGood, tauBad, 0.001)
    }
}
