package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugePerformanceContext
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/**
 * Pruebas de observaciones de rendimiento v4 (paquete 3.4): adaptadas hacia el EKF único.
 *
 * Re-ancladas respecto a 3.3:
 * - Las observaciones ahora son [AugeLearningObservation] con fuente, σ efectiva según bilateralidad, y instante causal.
 * - En lugar de descartar la progresión («progression_not_learned» o «below_noise_threshold»), ahora la mejora
 *   aporta observación con peso 0,5 (σ_efectiva = 12 / √0,5).
 * - `applyToCache` ahora aplica la observación en el EKF v4 en lugar de ser un puente neutro.
 */
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
    fun pecs48hAfterHardSession_underperformance_emitsObservation() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 72.0, predictedPecs = 70, historyCount = 3),
        )
        val pecs = result.observations.single { it.parameterKey == "pectorales" }
        val diag = result.diagnostics.single { it.channel == "Pectorales" }
        assertEquals(70, diag.predicted)
        assertEquals(60.0, pecs.y, 1e-9)
        assertEquals(12.0, pecs.sigma, 1e-9)
        assertEquals("e1rm", pecs.source)
        assertTrue(pecs.hoursSinceRelevantLoad in 47.0..49.0)
        assertTrue((diag.sessionStress ?: 0.0) > 0.0)
        assertTrue(result.diagnostics.none { it.channel == "Pectorales" && it.skipReason != null })
    }

    @Test
    fun skipsWhenFewerThanThreeSnapshots() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 2),
        )
        assertTrue(result.observations.none { it.parameterKey == "pectorales" })
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
        assertTrue(result.observations.none { it.parameterKey == "pectorales" })
        assertEquals("technical_invalid", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun skipsDeloadRpe() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 3, todayRpe = 6.0),
        )
        assertTrue(result.observations.none { it.parameterKey == "pectorales" })
        assertEquals("insufficient_snapshots", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun skipsHoursBelowEight() {
        val result = PerformanceTauLearner.observations(
            pecsInput(
                todayWeight = 72.0,
                predictedPecs = 70,
                historyCount = 3,
                latestHistoryHoursAgo = 4,
            ),
        )
        assertTrue(result.observations.none { it.parameterKey == "pectorales" })
        assertEquals("hours_below_min", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun progression_emitsObservationWithReducedWeight() {
        // Antes: se descartaba con «progression_not_learned» o «below_noise_threshold».
        // Después: bilateralidad -> la progresión (ν > 0) aporta observación con peso 0,5 y σ efectiva = 12 / √0,5.
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 80.0, predictedPecs = 70, historyCount = 3),
        )
        val obs = result.observations.single { it.parameterKey == "pectorales" }
        val diag = result.diagnostics.single { it.channel == "Pectorales" }
        assertEquals(98.0, obs.y, 1e-9) // ratio 1.0 -> implied 98
        assertEquals(0.5, diag.weight ?: 0.0, 1e-9)
        assertEquals(12.0 / sqrt(0.5), obs.sigma, 1e-9)
    }

    @Test
    fun skipsWhenMuscleWasCalibratedManually() {
        val result = PerformanceTauLearner.observations(
            pecsInput(todayWeight = 76.0, predictedPecs = 70, historyCount = 3).copy(
                manual = PerformanceTauManualTouches(muscles = setOf("Pectorales")),
            ),
        )
        assertTrue(result.observations.none { it.parameterKey == "pectorales" })
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
        val energy = result.observations.single { it.parameterKey == "cns" }
        assertEquals(62.0, energy.y, 1e-9)
        assertEquals("delta_rpe", energy.source)
        assertEquals(10.0, energy.sigma, 1e-9)
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
            weight = 90.0,
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
        val spinal = result.observations.single { it.parameterKey == "spinal" }
        assertEquals(60.0, spinal.y, 1e-9)
        assertEquals("e1rm", spinal.source)
        assertEquals(12.0, spinal.sigma, 1e-9)
    }

    @Test
    fun applyToCache_updatesAdaptiveCacheWithEkf() {
        // 3.3: el puente era neutro.
        // 3.4: el puente aplica en el EKF v4, actualiza el estado de parámetros y sella lastPerformanceLearnLogId.
        val obs = AugeLearningObservation(
            parameterKey = "pectorales",
            y = 60.0,
            sigma = 12.0,
            source = "e1rm",
            anchorMs = now.toEpochMilli(),
            observedAtMs = now.toEpochMilli(),
            relevantLogId = "h1",
            hoursSinceRelevantLoad = 48.0,
        )
        val result = PerformanceTauResult(
            observations = listOf(obs),
            diagnostics = emptyList(),
        )
        val start = AugeAdaptiveCache()
        val model = AugeObservationModel { _, _ -> AugePrediction(battery = 75.0) }
        val updated = PerformanceTauLearner.applyToCache(
            cache = start,
            result = result,
            finishedLogId = "today",
            nowMs = now.toEpochMilli(),
            model = model,
        )
        assertEquals("today", updated.lastPerformanceLearnLogId)
        assertEquals(1, updated.observations.size)
        val state = updated.parameterStates["pectorales"]
        assertNotNull(state)
        assertEquals(1, state?.n)
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
            performanceContext = AugePerformanceContext(
                sessionStartTimeMs = instant.toEpochMilli(),
                isDeload = false,
                deloadOrigin = "hierarchy_normal",
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
            performanceContext = AugePerformanceContext(
                sessionStartTimeMs = instant.toEpochMilli(),
                isDeload = false,
                deloadOrigin = "hierarchy_normal",
            ),
        )
    }
}
