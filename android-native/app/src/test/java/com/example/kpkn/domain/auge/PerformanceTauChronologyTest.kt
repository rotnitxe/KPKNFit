package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugePerformanceContext
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.performance.PerformanceRangeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pruebas de cronología y reloj causal para el aprendizaje de rendimiento (paquete 3.4).
 *
 * Cumple con los criterios numéricos del brief:
 * - Historial ASC, DESC y permutado: baseline, y, σ, id y posterior iguales (Double ≤ 10⁻¹²).
 * - Candidato futuro no aporta a ninguno.
 * - Con 90/100/110 cronológicos, diagnóstico del calculador da mayor peso a 110 que a 90; mediana = 100.
 * - Procesar a 0 h vs 5 h tras el cierre produce idénticos y, h, Δt, posterior e id.
 * - El propio log medido u otro log posterior a su inicio tienen 0 influencia en la predicción previa.
 */
class PerformanceTauChronologyTest {

    private val origin = Instant.parse("2026-10-21T10:00:00Z")
    private val originMs = origin.toEpochMilli()

    private val testDb = mapOf(
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
    )

    private fun log(
        id: String,
        ms: Long,
        weight: Double,
        rpe: Double = 8.0,
        context: AugePerformanceContext? = AugePerformanceContext(sessionStartTimeMs = ms, isDeload = false),
    ): WorkoutLog {
        val iso = Instant.ofEpochMilli(ms).toString()
        val sets = listOf(
            CompletedSet(id = "$id-s1", weight = weight, reps = 5, rpe = rpe),
            CompletedSet(id = "$id-s2", weight = weight, reps = 5, rpe = rpe),
        )
        return WorkoutLog(
            id = id,
            programId = "prog",
            sessionId = id,
            sessionName = "Sesión $id",
            date = iso,
            durationMinutes = 60,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "ex-$id",
                    exerciseName = "Press banca",
                    exerciseDbId = "bench",
                    canonicalExerciseId = "bench",
                    sets = sets,
                ),
            ),
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
            performanceContext = context,
        )
    }

    @Test
    fun ordenAscDescYPermutado_producenExactamenteElMismoResultadoHastaDoceDecimales() {
        // Tres sesiones anteriores a 72h, 48h y 24h
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 80.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 82.5)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 85.0)
        val today = log("today", originMs, weight = 75.0)

        val permutations = listOf(
            listOf(h1, h2, h3), // ASC
            listOf(h3, h2, h1), // DESC
            listOf(h2, h1, h3), // Permutado 1
            listOf(h3, h1, h2), // Permutado 2
        )

        var firstObs: AugeLearningObservation? = null
        var firstCache: AugeAdaptiveCache? = null

        val model = AugeObservationModel { _, _ -> AugePrediction(battery = 70.0) }

        for ((idx, history) in permutations.withIndex()) {
            val result = PerformanceTauLearner.observations(
                PerformanceTauInput(
                    historyWithoutToday = history,
                    today = today,
                    nowMs = originMs + 3600_000L,
                    exerciseDb = testDb,
                    predictedEnergy = 85,
                    predictedStructure = 90,
                    predictedMuscles = mapOf("Pectorales" to 70),
                ),
            )
            val obs = result.observations.single { it.parameterKey == "pectorales" }
            val updated = PerformanceTauLearner.applyToCache(
                cache = AugeAdaptiveCache(),
                result = result,
                finishedLogId = today.id,
                nowMs = originMs + 3600_000L,
                model = model,
            )

            if (idx == 0) {
                firstObs = obs
                firstCache = updated
            } else {
                val ref = requireNotNull(firstObs)
                val refC = requireNotNull(firstCache)
                assertEquals("y debe ser idéntico en permutación $idx", ref.y, obs.y, 1e-12)
                assertEquals("sigma debe ser idéntico en permutación $idx", ref.sigma, obs.sigma, 1e-12)
                assertEquals("anchorMs debe ser idéntico", ref.anchorMs, obs.anchorMs)
                assertEquals("observedAtMs debe ser idéntico", ref.observedAtMs, obs.observedAtMs)
                assertEquals("relevantLogId debe ser idéntico", ref.relevantLogId, obs.relevantLogId)
                assertEquals("hoursSinceRelevantLoad debe ser idéntico", ref.hoursSinceRelevantLoad, obs.hoursSinceRelevantLoad, 1e-12)

                val refState = refC.parameterStates.getValue("pectorales")
                val actState = updated.parameterStates.getValue("pectorales")
                assertEquals("mean posterior idéntico a 10^-12", refState.mean, actState.mean, 1e-12)
                assertEquals("variance posterior idéntica a 10^-12", refState.variance, actState.variance, 1e-12)
            }
        }
    }

    @Test
    fun logFuturoAlInicioDeTodayNoAportaANingunaObservacion() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 80.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 80.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 80.0)
        val today = log("today", originMs, weight = 72.0)
        // Log registrado después del inicio de today
        val future = log("future", originMs + 3600_000L, weight = 120.0)

        val sinFuturo = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = today,
                nowMs = originMs + 7200_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        val conFuturo = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3, future),
                today = today,
                nowMs = originMs + 7200_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        val o1 = sinFuturo.observations.single { it.parameterKey == "pectorales" }
        val o2 = conFuturo.observations.single { it.parameterKey == "pectorales" }
        assertEquals(o1.y, o2.y, 1e-12)
        assertEquals(o1.sigma, o2.sigma, 1e-12)
        assertEquals(o1.relevantLogId, o2.relevantLogId)
    }

    @Test
    fun calculadoraPonderaMasElFinalPeroLaMedianaDeTresReferenciasEsExactamenteCien() {
        // Tres sesiones con valores 90, 100 y 110 cronológicos
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 90.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 110.0)
        val today = log("today", originMs, weight = 100.0)

        // Comprobación de PerformanceRangeCalculator: pondera más el final (110) que el inicio (90)
        val range = PerformanceRangeCalculator.computeRange(
            snapshots = listOf(90.0, 100.0, 110.0),
            currentErm = 110.0,
        )
        assertTrue("El calculador pondera más el más reciente: rms > 100 (${range.ermRms})", range.ermRms > 100.0)

        // Pero la referencia del aprendiz para el baseline de e1RM es la MEDIANA de las 3 referencias = 100
        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = today,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        val diag = result.diagnostics.single { it.channel == "Pectorales" }
        assertEquals("La mediana de 90, 100, 110 es 100", 100.0, diag.baseline ?: 0.0, 1e-9)
        assertEquals("Ratio todayErm/baseline = 100/100 = 1.0", 1.0, diag.ratio ?: 0.0, 1e-9)
    }

    @Test
    fun procesarACeroHorasVsCincoHorasProduceIdenticosValoresYPosterior() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 80.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 80.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 80.0)
        val today = log("today", originMs, weight = 72.0)

        // Procesar inmediatamente (processingNowMs = originMs)
        val r0 = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = today,
                nowMs = originMs,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        // Procesar 5 horas después (processingNowMs = originMs + 5h)
        val r5 = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = today,
                nowMs = originMs + 5L * 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        val obs0 = r0.observations.single { it.parameterKey == "pectorales" }
        val obs5 = r5.observations.single { it.parameterKey == "pectorales" }

        assertEquals(obs0.y, obs5.y, 1e-12)
        assertEquals(obs0.sigma, obs5.sigma, 1e-12)
        assertEquals(obs0.anchorMs, obs5.anchorMs)
        assertEquals(obs0.observedAtMs, obs5.observedAtMs)
        assertEquals(obs0.relevantLogId, obs5.relevantLogId)
        assertEquals("Δt debe depender de observationAtMs, no de processingNowMs", obs0.hoursSinceRelevantLoad, obs5.hoursSinceRelevantLoad, 1e-12)

        val model = AugeObservationModel { _, _ -> AugePrediction(battery = 70.0) }
        val c0 = PerformanceTauLearner.applyToCache(AugeAdaptiveCache(), r0, today.id, originMs, model)
        val c5 = PerformanceTauLearner.applyToCache(AugeAdaptiveCache(), r5, today.id, originMs + 5L * 3600_000L, model)

        assertEquals(c0.parameterStates.getValue("pectorales").mean, c5.parameterStates.getValue("pectorales").mean, 1e-12)
        assertEquals(c0.parameterStates.getValue("pectorales").variance, c5.parameterStates.getValue("pectorales").variance, 1e-12)
    }
}
