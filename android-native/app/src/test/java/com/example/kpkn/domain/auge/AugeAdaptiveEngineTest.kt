package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.RecoveryLearningObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeAdaptiveEngineTest {

    @Test
    fun updateSystemLearningDeltas_zeroObservations_usesMaxAlpha() {
        val (newCns, newSpinal) = AugeAdaptiveEngine.updateSystemLearningDeltas(
            currentCnsDelta = 0.0,
            currentSpinalDelta = 0.0,
            systemAdjustment = 20,
            structureAdjustment = -10,
            totalObservations = 0,
        )
        assertEquals(10.0, newCns, 0.01)  // 0*(1-0.5) + 20*0.5 = 10
        assertEquals(-5.0, newSpinal, 0.01) // 0*(1-0.5) + (-10)*0.5 = -5
    }

    @Test
    fun updateSystemLearningDeltas_manyObservations_usesMinAlpha() {
        val (newCns, _) = AugeAdaptiveEngine.updateSystemLearningDeltas(
            currentCnsDelta = 10.0,
            currentSpinalDelta = 5.0,
            systemAdjustment = 40,
            structureAdjustment = 0,
            totalObservations = 50,
        )
        // alpha = max(0.05, min(0.5, 1.5/51)) = max(0.05, 0.0294) = 0.05
        // newCns = 10*0.95 + 40*0.05 = 9.5 + 2.0 = 11.5
        assertEquals(11.5, newCns, 0.01)
    }

    @Test
    fun updateSystemLearningDeltas_clampsAt50() {
        val (newCns, _) = AugeAdaptiveEngine.updateSystemLearningDeltas(
            currentCnsDelta = 0.0,
            currentSpinalDelta = 0.0,
            systemAdjustment = 100,
            structureAdjustment = 0,
            totalObservations = 0,
        )
        assertEquals(50.0, newCns, 0.01)
    }

    @Test
    fun updatePersonalizedRecoveryHours_newMuscle_appliesDefault() {
        val obs = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 50,
            actualBattery = 70,
            sessionStress = 30.0,
            hoursSinceSession = 48.0,
            sleepQuality = 3,
            stressLevel = 3,
        )
        val result = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
            current = emptyMap(),
            observation = obs,
            totalObservations = 0,
        )
        assertTrue(result.containsKey("pectorales"))
        val tau = result["pectorales"]!!
        assertTrue(tau in 12.0..144.0)
    }

    @Test
    fun updatePersonalizedRecoveryHours_existingMuscle_blendsTowardImplied() {
        val obs = RecoveryLearningObservation(
            muscle = "Cuádriceps",
            predictedBattery = 30,
            actualBattery = 30,
            sessionStress = 50.0,
            hoursSinceSession = 72.0,
            sleepQuality = 3,
            stressLevel = 3,
        )
        val result = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
            current = mapOf("cuádriceps" to 72.0),
            observation = obs,
            totalObservations = 5,
        )
        val tau = result["cuádriceps"]!!
        assertTrue(tau in 12.0..144.0)
    }

    @Test
    fun updateMuscleDeltas_correctDelta() {
        val result = AugeAdaptiveEngine.updateMuscleDeltas(
            current = emptyMap(),
            manualMuscleBatteries = mapOf("Pectorales" to 80),
            predictedMuscleBatteries = mapOf("Pectorales" to 60),
            totalObservations = 0,
        )
        val delta = result["pectorales"]!!
        assertEquals(10.0, delta, 0.01) // (80-60)*0.5 = 10
    }

    @Test
    fun updateMuscleDeltas_clampsAt50() {
        val result = AugeAdaptiveEngine.updateMuscleDeltas(
            current = emptyMap(),
            manualMuscleBatteries = mapOf("Pectorales" to 100),
            predictedMuscleBatteries = mapOf("Pectorales" to 0),
            totalObservations = 0,
        )
        val delta = result["pectorales"]!!
        assertEquals(50.0, delta, 0.01)
    }

    @Test
    fun updateMuscleDeltas_existingDelta_blendsCorrectly() {
        val result = AugeAdaptiveEngine.updateMuscleDeltas(
            current = mapOf("pectorales" to 10.0),
            manualMuscleBatteries = mapOf("Pectorales" to 70),
            predictedMuscleBatteries = mapOf("Pectorales" to 60),
            totalObservations = 3,
        )
        // alpha = max(0.15, min(0.5, 1/4)) = max(0.15, 0.25) = 0.25
        // newDelta = 10*0.75 + 10*0.25 = 7.5 + 2.5 = 10
        val delta = result["pectorales"]!!
        assertEquals(10.0, delta, 0.01)
    }

    @Test
    fun updatePersonalizedRecoveryHours_moreRecovered_shortensTauVsStillFatigued() {
        val recovered = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 70,
            actualBattery = 99,
            sessionStress = 30.0,
            hoursSinceSession = 48.0,
        )
        val fatigued = recovered.copy(actualBattery = 55)
        val start = mapOf("pectorales" to 48.0)
        val afterRecovered = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(start, recovered, 0)
        val afterFatigued = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(start, fatigued, 0)
        assertTrue(afterRecovered["pectorales"]!! < afterFatigued["pectorales"]!!)
        assertTrue(afterRecovered["pectorales"]!! < 48.0)
    }

    @Test
    fun deriveImpliedRecoveryTime_hoursSinceZero_returnsNull() {
        val obs = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 50,
            actualBattery = 60,
            sessionStress = 30.0,
            hoursSinceSession = 0.0,
            sleepQuality = 3,
            stressLevel = 3,
        )
        val result = invokeDeriveImpliedRecoveryTime(obs)
        assertTrue(result == null)
    }

    @Test
    fun deriveImpliedRecoveryTime_normalCase_returnsReasonableTau() {
        val obs = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 50,
            actualBattery = 70,
            sessionStress = 30.0,
            hoursSinceSession = 48.0,
            sleepQuality = 3,
            stressLevel = 3,
        )
        val result = invokeDeriveImpliedRecoveryTime(obs)
        assertTrue(result != null)
        assertTrue(result!! in 6.0..200.0)
    }

    private fun invokeDeriveImpliedRecoveryTime(obs: RecoveryLearningObservation): Double? {
        return AugeAdaptiveEngine::class.java
            .getDeclaredMethod("deriveImpliedRecoveryTime", RecoveryLearningObservation::class.java)
            .apply { isAccessible = true }
            .invoke(AugeAdaptiveEngine, obs) as? Double
    }
}
