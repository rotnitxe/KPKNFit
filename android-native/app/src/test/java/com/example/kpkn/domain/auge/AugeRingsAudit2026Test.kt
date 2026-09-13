package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryLearningObservation
import com.example.kpkn.data.models.RingStartSnapshot
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp

class SpinalWarpContinuityTest {
    @Test
    fun spinalHoursAreContinuousAround12h() {
        val left = AugeUtils.getSpinalRecoveryHours(11.9)
        val mid = AugeUtils.getSpinalRecoveryHours(12.0)
        val right = AugeUtils.getSpinalRecoveryHours(12.1)
        assertTrue(abs(right - left) < 0.5)
        assertTrue(mid in left..right || mid in right..left)
        val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        val log = axialLog("sq", now - (12 * 3_600_000L).toLong())
        val at115 = spinalAt(log, now - 30 * 60_000L)
        val at125 = spinalAt(log, now + 30 * 60_000L)
        assertTrue("|Δ| around 12h was ${kotlin.math.abs(at125 - at115)}", kotlin.math.abs(at125 - at115) <= 1)
    }

    private fun spinalAt(log: WorkoutLog, now: Long) = AugeRecoveryEngine.calculateSpinalBattery(
        history = listOf(log), wellbeing = null, settings = Settings(), exerciseDb = AxialFixtures.db, nowOverride = now,
    )
}

class SystemTauRoundTripTest {
    @Test
    fun cnsObservationRoundTripsForwardDecay() {
        val tau = 36.0
        val hours = 24.0
        val stress = 40.0
        val remaining = exp(-AugeUtils.TAU_K_95 * hours / tau)
        val actual = (100.0 - stress * remaining).toInt()
        val obs = RecoveryLearningObservation(
            muscle = "cns",
            predictedBattery = actual,
            actualBattery = actual,
            sessionStress = stress,
            hoursSinceSession = hours,
        )
        val (newTau, _) = AugeAdaptiveEngine.updateSystemRecoveryHours(
            currentCnsTau = tau,
            currentSpinalTau = 52.0,
            cnsObservation = obs,
            spinalObservation = null,
            totalObservations = 8,
        )
        assertEquals(tau, newTau!!, tau * 0.05)
    }
}

class SpinalAxialGateTest {
    @Test
    fun benchWithAxialZero_doesNotDrainSpinal_squatDoes() {
        val bench = session("bench", AxialFixtures.db.getValue("bench"))
        val squat = session("squat", AxialFixtures.db.getValue("squat"))
        val benchDrain = AugeFatigueEngine.calculateCompletedSessionDrain(bench, AxialFixtures.db)
        val squatDrain = AugeFatigueEngine.calculateCompletedSessionDrain(squat, AxialFixtures.db)
        assertTrue("bench spinal=${benchDrain.spinal}", benchDrain.spinal <= 1)
        assertTrue("squat spinal=${squatDrain.spinal}", squatDrain.spinal > 5)
    }
}

class HardSessionSpinalFloorTest {
    @Test
    fun curlsDoNotGetSpinalFloor() {
        val curls = session("curl", AxialFixtures.db.getValue("curl"), sets = 8, rpe = 9.0)
        val drain = AugeFatigueEngine.calculateCompletedSessionDrain(curls, AxialFixtures.db)
        assertEquals(0, drain.spinal)
        assertTrue(drain.cns >= 10)
        assertTrue(drain.muscular >= 10)
    }
}

class CardioSystemicDrainTest {
    @Test
    fun hiitDrainsEnergyAndColumnRings() {
        val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        val cardio = WorkoutLog(
            id = "hiit", programId = "p", sessionId = "s", sessionName = "HIIT",
            date = Instant.ofEpochMilli(now).toString(),
            durationMinutes = 40,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "airbike",
                    exerciseName = "Air bike",
                    exerciseDbId = "airbike",
                    restTime = 0,
                    cardioDetails = CardioDetails(type = CardioType.AIR_BIKE, targetDurationSeconds = 40 * 60),
                    sets = listOf(CompletedSet(id = "c1", weight = 0.0, reps = 0, rpe = 9.0, timeSeconds = 40 * 60)),
                ),
            ),
        )
        val energy = AugeRecoveryEngine.calculateSystemicFatigue(
            history = listOf(cardio), wellbeing = null, settings = Settings(), nowOverride = now + 5 * 60_000L,
        ).first
        val spinal = AugeRecoveryEngine.calculateSpinalBattery(
            history = listOf(cardio), wellbeing = null, settings = Settings(), nowOverride = now + 5 * 60_000L,
        )
        val session = AugeFatigueEngine.calculateCompletedSessionDrain(cardio.completedExercises)
        assertTrue("session CNS ${session.cns}", session.cns > 0)
        assertTrue("energy $energy", energy < 100)
        assertTrue("spinal $spinal", spinal < 100)
    }
}

class AdaptiveCacheSchema3MigrationTest {
    @Test
    fun schema2ResetsSystemTauAndKeepsMuscles() {
        val v2 = AugeAdaptiveCache(
            schemaVersion = 2,
            personalizedRecoveryHours = mapOf("pectorales" to 55.0),
            cnsRecoveryHours = 90.0,
            spinalRecoveryHours = 110.0,
            cnsLearningDelta = 8.0,
            spinalLearningDelta = -6.0,
            cnsDrainMultiplier = 1.2,
            spinalDrainMultiplier = 0.8,
        )
        val next = AugeAdaptiveCacheMigration.migrate(v2, storedVersion = 2)
        assertEquals(3, next.schemaVersion)
        assertEquals(55.0, next.personalizedRecoveryHours["pectorales"])
        assertNull(next.cnsRecoveryHours)
        assertNull(next.spinalRecoveryHours)
        assertEquals(0.0, next.cnsLearningDelta, 0.0)
        assertEquals(0.0, next.spinalLearningDelta, 0.0)
        assertEquals(1.0, next.cnsDrainMultiplier, 0.0)
        assertEquals(1.0, next.spinalDrainMultiplier, 0.0)
    }
}

class SpineRingSingleDefinitionTest {
    @Test
    fun previewSpinalMatchesBlendedHomeScore() {
        val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        val log = axialLog("sq", now)
        val settings = Settings()
        val preview = AugeRecoveryEngine.previewPostSessionBatteries(
            baseHistory = emptyList(),
            previewLog = log,
            wellbeing = null,
            settings = settings,
            exerciseDb = AxialFixtures.db,
            nowOverrideMs = now,
        )
        val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
            history = listOf(log), wellbeing = null, settings = settings, exerciseDb = AxialFixtures.db,
            nowOverrideMs = now,
        )
        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = listOf(log), wellbeing = null, settings = settings, exerciseDb = AxialFixtures.db,
            precomputedMuscles = muscles, nowOverrideMs = now,
        )
        val (art, guard) = AugeRecoveryEngine.structureInputs(muscles, emptyMap())
        val blended = AugeRecoveryEngine.structureRingScore(batteries.spinal, art, guard)
        assertTrue("preview=${preview.spinal} home=$blended", abs(preview.spinal - blended) <= 1)
    }
}

class ManualSpinalOverrideSticksTest {
    @Test
    fun invertThenBlendReturnsDisplayedValue() {
        val displayed = AugeRecoveryEngine.structureRingScore(90, 40, listOf(40))
        val raw = AugeRecoveryEngine.invertStructureRingScore(displayed, 40, listOf(40))
        val again = AugeRecoveryEngine.structureRingScore(raw, 40, listOf(40))
        assertEquals(displayed, again)
        val wellbeing = DailyWellbeingLog(
            id = "w", date = "2026-09-12",
            sleepQuality = 3, stressLevel = 3, doms = 1, motivation = 3,
            manualSpinalBattery = raw,
            manualBatteryAnchorMs = Instant.parse("2026-09-12T10:00:00Z").toEpochMilli(),
        )
        val spinal = AugeRecoveryEngine.calculateSpinalBattery(
            history = emptyList(), wellbeing = wellbeing, settings = Settings(),
            nowOverride = Instant.parse("2026-09-12T10:05:00Z").toEpochMilli(),
        )
        assertTrue("anchored raw $spinal vs $raw", abs(spinal - raw) <= 3)
    }
}

class MuscleColdStartParityTest {
    @Test
    fun firstSessionFinishAndHomeShareCapacityUnits() {
        val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        val iso = Instant.ofEpochMilli(now).toString()
        val log = WorkoutLog(
            id = "c1", programId = "p", sessionId = "s", sessionName = "Pecho",
            date = iso, durationMinutes = 50,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 28,
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = 750.0, capacityAtCompletion = 2600.0, immediateDrainPct = 28.0,
                        directStressUnits = 750.0, indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Pectorales"),
                setInputHash = "c1",
                contextHash = "c1",
            ),
            completedExercises = session("bench", AxialFixtures.db.getValue("bench")),
        )
        val preview = AugeRecoveryEngine.previewPostSessionBatteries(
            baseHistory = emptyList(), previewLog = log, wellbeing = null, settings = Settings(),
            exerciseDb = AxialFixtures.db, nowOverrideMs = now,
        )
        val home = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = listOf(log), wellbeing = null, settings = Settings(),
            exerciseDb = AxialFixtures.db, nowOverride = now + 60_000L,
        )
        assertTrue("finish=${preview.perMuscle["Pectorales"]?.recoveryScore} home=${home.recoveryScore}",
            abs((preview.perMuscle["Pectorales"]?.recoveryScore ?: 0) - home.recoveryScore) <= 2)
    }
}

class ReadinessSheetHomeParityTest {
    @Test
    fun dashboardMuscularChannelMatchesGlobalBattery() {
        val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        val log = axialLog("sq", now)
        val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
            history = listOf(log), wellbeing = null, settings = Settings(), exerciseDb = AxialFixtures.db,
            nowOverrideMs = now,
        )
        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = listOf(log), wellbeing = null, settings = Settings(), exerciseDb = AxialFixtures.db,
            precomputedMuscles = muscles, nowOverrideMs = now,
        )
        val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = batteries,
            perMuscle = muscles,
            articularBatteries = emptyMap(),
            wellbeing = null,
            sleepLogs = emptyList(),
            recentSessionCount = 0,
        )
        assertEquals(batteries.muscular, dashboard.channels.first { it.id == RecoveryChannelId.MUSCULAR }.score)
        assertEquals(batteries.cnc, dashboard.channels.first { it.id == RecoveryChannelId.SYSTEM }.score)
    }
}

class TauObservationCapTest {
    @Test
    fun oneObservationCannotMoveTauMoreThan25Percent() {
        val start = 48.0
        val obs = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 70,
            actualBattery = 22,
            sessionStress = 40.0,
            hoursSinceSession = 48.0,
        )
        val next = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(mapOf("pectorales" to start), obs, 0)
        val tau = next.getValue("pectorales")
        assertTrue(tau <= start * 1.25 + 0.01)
        assertTrue(tau >= start * 0.75 - 0.01)
        val capped = AugeAdaptiveEngine.capObservationTau(200.0, start)
        assertEquals(start * 1.25, capped, 0.01)
    }
}

class PerformanceTauProgressionBiasTest {
    @Test
    fun impliedBetterThanPredictedIsSkipped() {
        assertEquals("progression_not_learned", runSkip(impliedBetter = true))
    }

    private fun runSkip(impliedBetter: Boolean): String {
        // Use the public observations path with a progressing squat vs a weak prediction.
        val now = Instant.parse("2026-09-12T18:00:00Z")
        val history = (1..4).map { i ->
            axialLog("sq-$i", now.minusSeconds((i * 7L) * 24 * 3600).toEpochMilli(), weight = 100.0)
        }
        val today = axialLog("today", now.toEpochMilli(), weight = 130.0)
        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = history,
                today = today,
                nowMs = now.toEpochMilli(),
                exerciseDb = AxialFixtures.db,
                settings = Settings(),
                predictedEnergy = if (impliedBetter) 60 else 90,
                predictedStructure = if (impliedBetter) 60 else 90,
                predictedMuscles = mapOf("Cuádriceps" to if (impliedBetter) 60 else 90),
            ),
        )
        return result.diagnostics.firstOrNull { it.skipReason == "progression_not_learned" }?.skipReason
            ?: result.diagnostics.firstOrNull()?.skipReason
            ?: if (result.observations.isEmpty()) "none" else "learned"
    }
}

class ManualSpinalLearningAxialGateTest {
    @Test
    fun curlsDoNotQualifyForSpinalLearning_squatDoes() {
        val curls = WorkoutLog(
            id = "c", programId = "p", sessionId = "s", sessionName = "curl",
            date = Instant.parse("2026-09-12T18:00:00Z").toString(), durationMinutes = 40,
            completedExercises = session("curl", AxialFixtures.db.getValue("curl"), sets = 8, rpe = 9.0),
        )
        val squat = axialLog("sq", Instant.parse("2026-09-12T18:00:00Z").toEpochMilli())
        assertFalse(PerformanceTauLearner.sessionHasAxialStimulus(curls.completedExercises, AxialFixtures.db))
        assertTrue(PerformanceTauLearner.sessionHasAxialStimulus(squat.completedExercises, AxialFixtures.db))
    }
}

private object AxialFixtures {
    val db = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Press Banca", equipment = "barra",
            efc = 3.2, cnc = 3.5, ssc = 1.2, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barra",
            efc = 4.2, cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
        "curl" to ExerciseMuscleInfo(
            id = "curl", name = "Curl", equipment = "barra",
            efc = 2.0, cnc = 1.5, ssc = 0.1, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY)),
        ),
    )
}

private fun session(
    id: String,
    info: ExerciseMuscleInfo,
    sets: Int = 5,
    rpe: Double = 8.5,
    weight: Double = 100.0,
): List<CompletedExercise> = listOf(
    CompletedExercise(
        exerciseId = id, exerciseName = info.name, exerciseDbId = info.id, restTime = 120,
        sets = List(sets) { i -> CompletedSet(id = "$id-$i", weight = weight, reps = 6, rpe = rpe) },
    ),
)

private fun axialLog(id: String, at: Long, weight: Double = 140.0): WorkoutLog {
    val iso = Instant.ofEpochMilli(at).toString()
    return WorkoutLog(
        id = id, programId = "p", sessionId = id, sessionName = "Sentadilla",
        date = iso, durationMinutes = 55,
        ringStartSnapshot = RingStartSnapshot(iso, muscular = 80, energy = 75, structure = 70),
        completedExercises = session("squat", AxialFixtures.db.getValue("squat"), weight = weight),
    )
}
