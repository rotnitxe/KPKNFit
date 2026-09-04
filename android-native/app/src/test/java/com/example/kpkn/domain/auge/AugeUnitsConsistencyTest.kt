package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AugeUnitsConsistencyTest {

    @Test
    fun acwr_ignoresLegacySessionStressScore() {
        val now = Instant.parse("2026-08-29T12:00:00Z")
        val legacyOnly = listOf(
            legacyLog("l1", now.minus(20, ChronoUnit.DAYS), sessionStress = 500.0),
            legacyLog("l2", now.minus(18, ChronoUnit.DAYS), sessionStress = 500.0),
            legacyLog("l3", now.minus(16, ChronoUnit.DAYS), sessionStress = 500.0),
            legacyLog("l4", now.minus(14, ChronoUnit.DAYS), sessionStress = 500.0),
        )
        assertNull(AugeRecoveryEngine.muscularAcwrFor(legacyOnly, now.toEpochMilli()))

        val mixed = legacyOnly + logWithStress("v2-spike", now.minus(1, ChronoUnit.DAYS), 40.0)
        val acwrMixed = AugeRecoveryEngine.muscularAcwrFor(mixed, now.toEpochMilli())
        assertNull(acwrMixed)
    }

    @Test
    fun acwr_onlyUsesV2StressUnits_notInflatedByLegacy() {
        val now = Instant.parse("2026-08-29T12:00:00Z")
        val baseline = listOf(
            logWithStress("b1", now.minus(20, ChronoUnit.DAYS), 12.0),
            logWithStress("b2", now.minus(18, ChronoUnit.DAYS), 12.0),
            logWithStress("b3", now.minus(16, ChronoUnit.DAYS), 12.0),
            logWithStress("spike", now.minus(1, ChronoUnit.DAYS), 80.0),
        )
        val withLegacyNoise = baseline + legacyLog(
            id = "noise",
            instant = now.minus(2, ChronoUnit.DAYS),
            sessionStress = 10_000.0,
        )
        val acwrBaseline = AugeRecoveryEngine.muscularAcwrFor(baseline, now.toEpochMilli())
        val acwrWithNoise = AugeRecoveryEngine.muscularAcwrFor(withLegacyNoise, now.toEpochMilli())
        assertTrue(acwrBaseline != null && acwrWithNoise != null)
        assertEquals(acwrBaseline!!, acwrWithNoise!!, 0.0001)
    }

    @Test
    fun acwr_rejectsInvalidLogDateMs() {
        val now = Instant.parse("2026-08-29T12:00:00Z")
        val logs = listOf(
            logWithStress("ok-1", now.minus(20, ChronoUnit.DAYS), 12.0),
            logWithStress("ok-2", now.minus(18, ChronoUnit.DAYS), 12.0),
            logWithStress("ok-3", now.minus(16, ChronoUnit.DAYS), 12.0),
            logWithStress("bad", now.minus(15, ChronoUnit.DAYS), 12.0).copy(date = "not-a-date"),
        )
        assertNull(AugeRecoveryEngine.muscularAcwrFor(logs, now.toEpochMilli()))
    }

    @Test
    fun capacityEngine_usesStressUnits_notImmediateDrainPct() {
        val completion = "2026-08-29T12:00:00Z"
        val completionMs = Instant.parse(completion).toEpochMilli()
        val log = logWithStress("cap", Instant.parse(completion).minus(3, ChronoUnit.DAYS), stress = 200.0)
            .copy(
                muscularImpactV2 = logWithStress("cap", Instant.parse(completion).minus(3, ChronoUnit.DAYS), 200.0)
                    .muscularImpactV2!!.copy(
                        perMuscle = mapOf(
                            "Pectorales" to MuscleSessionImpactV2(
                                stressUnits = 200.0,
                                capacityAtCompletion = 260.0,
                                immediateDrainPct = 5.0,
                                directStressUnits = 200.0,
                                indirectStressUnits = 0.0,
                            ),
                        ),
                    ),
            )
        val capacity = AugeMuscleCapacityEngine.calculateUserWorkCapacity(
            muscleName = "Pectorales",
            history = listOf(log),
            completionInstantIso = completion,
        )
        val lowDrainLog = log.copy(
            muscularImpactV2 = log.muscularImpactV2!!.copy(
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = 5.0,
                        capacityAtCompletion = 260.0,
                        immediateDrainPct = 200.0,
                        directStressUnits = 5.0,
                        indirectStressUnits = 0.0,
                    ),
                ),
            ),
        )
        val lowCapacity = AugeMuscleCapacityEngine.calculateUserWorkCapacity(
            muscleName = "Pectorales",
            history = listOf(lowDrainLog),
            completionInstantIso = completion,
        )
        assertTrue(capacity > lowCapacity)
        assertTrue(capacity >= 120.0)
        assertTrue(completionMs > 0L)
    }

    @Test
    fun timeOnlySetWithoutCardioDetails_isNotEffective() {
        val timeOnly = CompletedSet(id = "t1", reps = 0, weight = 0.0, timeSeconds = 1200)
        assertFalse(AugeFatigueEngine.isSetEffective(timeOnly))
    }

    @Test
    fun cardioDetails_allowTimeOnlyDrain() {
        val steady = CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 30 * 60)
        val drain = CardioRingDrainEngine.drain(steady, 30 * 60, 6.0, Settings())
        assertTrue(drain.cns > 0.0)
        assertTrue(drain.muscular > 0.0)
    }

    private fun legacyLog(id: String, instant: Instant, sessionStress: Double): WorkoutLog =
        WorkoutLog(
            id = id,
            programId = "p",
            sessionId = id,
            sessionName = id,
            date = instant.toString(),
            durationMinutes = 50,
            sessionStressScore = sessionStress,
        )

    private fun logWithStress(id: String, instant: Instant, stress: Double): WorkoutLog {
        val iso = instant.toString()
        return WorkoutLog(
            id = id,
            programId = "p",
            sessionId = id,
            sessionName = id,
            date = iso,
            durationMinutes = 50,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 20,
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = stress,
                        capacityAtCompletion = 260.0,
                        immediateDrainPct = 20.0,
                        directStressUnits = stress,
                        indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Pectorales"),
                setInputHash = id,
                contextHash = id,
            ),
        )
    }
}
