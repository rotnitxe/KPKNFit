package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryLearningObservation
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class PerformanceTauObservationTest {

    private val now = Instant.parse("2026-09-03T18:00:00Z")
    private val benchDb = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench",
            name = "Press banca",
            equipment = "barbell",
            efc = 3.5,
            cnc = 4.0,
            ssc = 1.2,
            axialLoadFactor = 0.2,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat",
            name = "Sentadilla",
            equipment = "barbell",
            efc = 4.0,
            cnc = 4.5,
            ssc = 1.8,
            axialLoadFactor = 0.9,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
    )

    @Test
    fun pecs48hAfterHardSession_ratio095_vsPredicted70_emitsObservation() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 3),
        )
        val pecs = result.observations.single { it.muscle == "Pectorales" }
        assertEquals(70, pecs.predictedBattery)
        assertEquals(80, pecs.actualBattery)
        assertTrue(pecs.hoursSinceSession in 47.0..49.0)
        assertTrue(pecs.sessionStress > 0.0)
        assertTrue(result.diagnostics.none { it.channel == "Pectorales" && it.skipReason != null })
    }

    @Test
    fun skipsWhenFewerThanThreeSnapshots() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 2),
        )
        assertTrue(result.observations.none { it.muscle == "Pectorales" })
        assertEquals(
            "insufficient_snapshots",
            result.diagnostics.first { it.channel == "Pectorales" }.skipReason,
        )
    }

    @Test
    fun skipsTechnicalInvalid() {
        val base = pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 3)
        val today = base.today.copy(
            postExerciseReports = listOf(
                ExerciseDiscomfortReport(
                    exerciseId = "ex-today",
                    canonicalExerciseId = "bench",
                    exerciseName = "Press banca",
                    technicalQuality = 2,
                ),
            ),
        )
        val result = PerformanceTauLearner.observations(base.copy(today = today))
        assertTrue(result.observations.none { it.muscle == "Pectorales" })
        assertEquals("technical_invalid", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun skipsDeloadRpe() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 3, todayRpe = 6.0),
        )
        assertTrue(result.observations.none { it.muscle == "Pectorales" })
        assertEquals("rpe_not_comparable", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun skipsHoursBelowEight() {
        val result = PerformanceTauLearner.observations(
            pecsInput(
                todayWeight = 76.0,
                predictedPecs = 70,
                historyCount = 3,
                latestHistoryHoursAgo = 4,
            ),
        )
        assertTrue(result.observations.none { it.muscle == "Pectorales" })
        assertEquals("hours_below_min", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun skipsBelowNoiseThreshold() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 78, historyCount = 3),
        )
        assertTrue(result.observations.none { it.muscle == "Pectorales" })
        assertEquals("below_noise_threshold", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun skipsWhenMuscleWasCalibratedManually() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 3).copy(
                manual = PerformanceTauManualTouches(muscles = setOf("Pectorales")),
            ),
        )
        assertTrue(result.observations.none { it.muscle == "Pectorales" })
        assertEquals("manual_touch", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun energyMatchedRpe_emitsCnsObservation() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 80.0, predictedPecs = 80, historyCount = 3, todayRpe = 9.0).copy(
                predictedEnergy = 70,
                manual = PerformanceTauManualTouches(muscles = setOf("Pectorales")),
            ),
        )
        val energy = result.observations.single { it.muscle == "cns" }
        assertEquals(70, energy.predictedBattery)
        assertEquals(62, energy.actualBattery)
    }

    @Test
    fun structureAxialErm_emitsSpinalObservation() {
        val history = (3 downTo 1).map { index ->
            squatLog(
                id = "h$index",
                instant = now.minus((index * 6 + 2).toLong(), ChronoUnit.DAYS),
                weight = 100.0,
            )
        }
        val today = squatLog(
            id = "today",
            instant = now,
            weight = 95.0,
        )
        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = history,
                today = today,
                nowMs = now.toEpochMilli(),
                exerciseDb = benchDb,
                predictedEnergy = 90,
                predictedStructure = 70,
                predictedMuscles = mapOf("Cuádriceps" to 90),
                manual = PerformanceTauManualTouches(muscles = setOf("Cuádriceps"), energy = true),
            ),
        )
        val spinal = result.observations.single { it.muscle == "spinal" }
        assertEquals(70, spinal.predictedBattery)
        assertEquals(80, spinal.actualBattery)
    }

    @Test
    fun applyToCache_betterThanPredicted_shortensPecsTau() {
        val obs = RecoveryLearningObservation(
            muscle = "Pectorales",
            predictedBattery = 70,
            actualBattery = 99,
            sessionStress = 30.0,
            hoursSinceSession = 48.0,
        )
        val result = PerformanceTauResult(
            observations = listOf(obs),
            diagnostics = emptyList(),
        )
        val updated = PerformanceTauLearner.applyToCache(
            cache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf("pectorales" to 48.0)),
            result = result,
            finishedLogId = "today",
            nowMs = now.toEpochMilli(),
        )
        val tau = updated.personalizedRecoveryHours["pectorales"]!!
        assertTrue(tau < 48.0)
        assertEquals("today", updated.lastPerformanceLearnLogId)
        assertEquals(1, updated.totalObservations)
    }

    private fun pecsInput(
        todayWeight: Double,
        predictedPecs: Int,
        historyCount: Int,
        todayRpe: Double = 8.0,
        latestHistoryHoursAgo: Long = 48,
    ): PerformanceTauInput {
        val history = (historyCount downTo 1).map { index ->
            val instant = if (index == 1) {
                now.minus(latestHistoryHoursAgo, ChronoUnit.HOURS)
            } else {
                now.minus((index * 6).toLong(), ChronoUnit.DAYS)
            }
            benchLog(id = "h$index", instant = instant, weight = 80.0, rpe = 8.0)
        }
        return PerformanceTauInput(
            historyWithoutToday = history,
            today = benchLog(id = "today", instant = now, weight = todayWeight, rpe = todayRpe),
            nowMs = now.toEpochMilli(),
            exerciseDb = benchDb,
            predictedEnergy = 85,
            predictedStructure = 90,
            predictedMuscles = mapOf("Pectorales" to predictedPecs),
        )
    }

    private fun benchLog(id: String, instant: Instant, weight: Double, rpe: Double): WorkoutLog {
        val iso = instant.toString()
        val exercise = CompletedExercise(
            exerciseId = if (id == "today") "ex-today" else "ex-$id",
            exerciseName = "Press banca",
            exerciseDbId = "bench",
            canonicalExerciseId = "bench",
            sets = listOf(
                CompletedSet(id = "$id-s1", weight = weight, reps = 5, rpe = rpe),
                CompletedSet(id = "$id-s2", weight = weight, reps = 5, rpe = rpe),
            ),
        )
        return WorkoutLog(
            id = id,
            programId = "p",
            sessionId = id,
            sessionName = id,
            date = iso,
            durationMinutes = 50,
            completedExercises = listOf(exercise),
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 20,
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = 40.0,
                        capacityAtCompletion = 260.0,
                        immediateDrainPct = 30.0,
                        directStressUnits = 40.0,
                        indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Pectorales"),
                setInputHash = id,
                contextHash = id,
            ),
        )
    }

    private fun squatLog(id: String, instant: Instant, weight: Double): WorkoutLog {
        val iso = instant.toString()
        return WorkoutLog(
            id = id,
            programId = "p",
            sessionId = id,
            sessionName = id,
            date = iso,
            durationMinutes = 50,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "sq-$id",
                    exerciseName = "Sentadilla",
                    exerciseDbId = "squat",
                    canonicalExerciseId = "squat",
                    sets = listOf(
                        CompletedSet(id = "$id-s1", weight = weight, reps = 5, rpe = 8.0),
                        CompletedSet(id = "$id-s2", weight = weight, reps = 5, rpe = 8.0),
                    ),
                ),
            ),
        )
    }
}
