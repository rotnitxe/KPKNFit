package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.RecoveryLearningObservation
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeAdaptiveEngineTest {

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
