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
    fun postSessionHours_halfHourFloor_allowsTauLearning() {
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
        assertNotNull("τ CNS must update with hoursSinceSession=0.5", newCns)
        assertTrue(newCns!! in 12.0..144.0)
        assertTrue("Immediate post-session should nudge τ away from default", newCns != 36.0)
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
        val now = System.currentTimeMillis()
        val wellbeing = com.example.kpkn.data.models.DailyWellbeingLog(
            id = "wb",
            date = java.time.LocalDate.now().toString(),
            manualNeuralBattery = 55,
            manualSpinalBattery = 55,
            manualBatteryAnchorMs = now,
        )
        val baseline = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            adaptiveCache = AugeAdaptiveCache(),
        )
        val calibrated = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            adaptiveCache = AugeAdaptiveCache(
                cnsLearningDelta = 12.0,
                spinalLearningDelta = -10.0,
            ),
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
    fun drainMultipliers_clampToHalfThroughOnePointSix() {
        val (cns, spinal, muscles) = AugeAdaptiveEngine.updateDrainMultipliers(
            currentCnsMult = 1.0,
            currentSpinalMult = 1.0,
            currentMuscleMults = emptyMap(),
            manualNeural = 20,
            manualSpinal = 20,
            manualMuscleBatteries = mapOf("pectorales" to 20),
            predictedNeural = 80,
            predictedSpinal = 80,
            predictedMuscleBatteries = mapOf("pectorales" to 80),
            preWorkoutNeural = 100,
            preWorkoutSpinal = 100,
            preWorkoutMuscleBatteries = mapOf("pectorales" to 100),
            totalObservations = 0,
        )
        assertTrue(cns in 0.5..1.6)
        assertTrue(spinal in 0.5..1.6)
        assertTrue(muscles.values.all { it in 0.5..1.6 })
    }

    @Test
    fun sleepQuality_nudgesImpliedTauInLearning() {
        // Mid-range implied τ so sleepAdj is not crushed by the 200h clamp
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

        assertTrue(
            "Better sleep should imply shorter learned τ ($tauGood vs $tauBad)",
            tauGood < tauBad,
        )
    }
}
