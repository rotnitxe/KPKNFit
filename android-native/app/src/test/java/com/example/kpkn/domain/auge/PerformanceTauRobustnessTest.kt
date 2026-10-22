package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.AugePerformanceContext
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Pruebas de robustez numérica, bilateralidad, exclusiones e idempotencia del aprendizaje de rendimiento (paquete 3.4).
 */
class PerformanceTauRobustnessTest {

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
        rpe: Double? = 8.0,
        setsCount: Int = 2,
        reps: Int = 1,
        context: AugePerformanceContext? = AugePerformanceContext(sessionStartTimeMs = ms, isDeload = false),
        discomforts: List<String> = emptyList(),
        stillPresentDiscomforts: List<String> = emptyList(),
        postExerciseReports: List<ExerciseDiscomfortReport> = emptyList(),
    ): WorkoutLog {
        val iso = Instant.ofEpochMilli(ms).toString()
        val sets = (1..setsCount).map { i ->
            CompletedSet(id = "$id-s$i", weight = weight, reps = reps, rpe = rpe)
        }
        return WorkoutLog(
            id = id,
            programId = "prog",
            sessionId = id,
            sessionName = "Sesión $id",
            date = iso,
            durationMinutes = 60,
            discomforts = discomforts,
            stillPresentDiscomfortIds = stillPresentDiscomforts,
            postExerciseReports = postExerciseReports,
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

    // ─── Robustez de la mediana de 3 sesiones ───────────────────────────────────────────────────

    @Test
    fun robustezDeLaReferencia_sesionesCienCienYCientoSesentaDanMedianaCienYNoCientoVeinte() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 160.0)
        val today = log("today", originMs, weight = 100.0)

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
        assertEquals("La mediana de 100, 100, 160 es 100 (la media aritmética sería 120)", 100.0, diag.baseline ?: 0.0, 1e-9)
        assertEquals("Ratio 100/100 = 1.0", 1.0, diag.ratio ?: 0.0, 1e-9)
    }

    @Test
    fun duplicarSeriesDeUnaReferenciaNoDuplicaSuPeso() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0, setsCount = 10) // 10 series de 100
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0, setsCount = 2)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 160.0, setsCount = 2)
        val today = log("today", originMs, weight = 100.0)

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
        assertEquals("Sigue siendo la mediana de las 3 sesiones = 100", 100.0, diag.baseline ?: 0.0, 1e-9)
    }

    @Test
    fun menosDeTresSesionesEligibles_ceroObservacionesConMotivo() {
        val h1 = log("h1", originMs - 48L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 24L * 3600_000L, weight = 100.0)
        val today = log("today", originMs, weight = 100.0)

        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2),
                today = today,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        assertTrue("0 observaciones con menos de 3 referencias", result.observations.none { it.parameterKey == "pectorales" })
        assertEquals("insufficient_snapshots", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    // ─── Bilateralidad (ν = -10 y ν = +10) ──────────────────────────────────────────────────────

    @Test
    fun bilateralidad_peorYMejorAportanObservacionesConPesosUnoYMedioRespectivamente() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)

        // Caso Peor: baseline = 100, today = 92.5 (ratio = 0.925 -> implied ≈ 70, predicted = 80 -> ν = -10)
        val todayPeor = log("today-peor", originMs, weight = 92.5)
        val resPeor = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = todayPeor,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 80),
            ),
        )
        val obsPeor = resPeor.observations.single { it.parameterKey == "pectorales" }
        val diagPeor = resPeor.diagnostics.single { it.channel == "Pectorales" }
        assertEquals(1.0, diagPeor.weight ?: 0.0, 1e-9)
        assertEquals("σ base para e1RM = 12.0", 12.0, obsPeor.sigma, 1e-9)
        assertEquals(144.0, obsPeor.sigma * obsPeor.sigma, 1e-6)

        // Caso Mejor: baseline = 100, today = 100 (ratio = 1.0 -> implied = 98, predicted = 88 -> ν = +10)
        val todayMejor = log("today-mejor", originMs, weight = 100.0)
        val resMejor = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = todayMejor,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 88),
            ),
        )
        val obsMejor = resMejor.observations.single { it.parameterKey == "pectorales" }
        val diagMejor = resMejor.diagnostics.single { it.channel == "Pectorales" }
        assertEquals("Progreso aporta peso 0.5", 0.5, diagMejor.weight ?: 0.0, 1e-9)
        val sigmaEsperada = 12.0 / sqrt(0.5)
        assertEquals(sigmaEsperada, obsMejor.sigma, 1e-9)
        assertEquals("R = 144 / 0.5 = 288.0", 288.0, obsMejor.sigma * obsMejor.sigma, 1e-6)

        // Actualización EKF: peor => τ no baja (se alarga o mantiene), mejor => τ no sube (se acorta o mantiene)
        // Modelo simple monotono: battery = 75 - 10 * (lnTau - ln(48)) => dh/d(lnTau) = -10 < 0
        val model = AugeObservationModel { _, lnTau ->
            val b = 75.0 - 10.0 * (lnTau - ln(48.0))
            AugePrediction(battery = b.coerceIn(0.0, 100.0))
        }

        val cacheBase = AugeAdaptiveCache()
        val cPeor = AugeLearner.update(cacheBase, obsPeor, model)
        assertEquals(AugeObservationStatus.ADMITTED, cPeor.outcome.status)
        val tauPeor = exp(cPeor.cache.parameterStates.getValue("pectorales").mean)
        assertTrue("Con rendimiento peor (ν < 0), τ se alarga: $tauPeor >= 48.0", tauPeor >= 48.0)

        val cMejor = AugeLearner.update(cacheBase, obsMejor, model)
        assertEquals(AugeObservationStatus.ADMITTED, cMejor.outcome.status)
        val tauMejor = exp(cMejor.cache.parameterStates.getValue("pectorales").mean)
        assertTrue("Con rendimiento mejor (ν > 0), τ se acorta: $tauMejor <= 48.0", tauMejor <= 48.0)

        // Verificación de ganancia K con R efectiva: K = P * H / (H^2 * P + R)
        // Con R = 288 frente a R = 144, |K_mejor| < |K_peor|
        val kPeor = abs(requireNotNull(cPeor.detail?.K))
        val kMejor = abs(requireNotNull(cMejor.detail?.K))
        assertTrue("|K_mejor| ($kMejor) < |K_peor| ($kPeor) por el descuento de peso 0.5", kMejor < kPeor)
    }

    // ─── Molestias y Descargas ──────────────────────────────────────────────────────────────────

    @Test
    fun molestiaEnSesionMedida_excluyeConMotivo() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)

        val todayConMolestia = log(
            "today", originMs, weight = 80.0,
            discomforts = listOf("hombro_izquierdo"),
        )

        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = todayConMolestia,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        assertTrue("Molestia en today excluye todas las observaciones", result.observations.isEmpty())
        assertEquals("discomfort", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun molestiaEnReferencia_noCuentaComoSesionElegible() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        // h2 tiene molestia: debe quedar fuera de las referencias
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0, discomforts = listOf("codo"))
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)
        val today = log("today", originMs, weight = 80.0)

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

        // Solo hay 2 referencias elegibles (h1 y h3) -> insuficiente
        assertTrue(result.observations.isEmpty())
        assertEquals("insufficient_snapshots", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun molestiaConSentinelNoneOListaVacia_noExcluye() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)

        // Sesión con lista vacía y "none" / espacios en blanco
        val todayLimpio = log(
            "today", originMs, weight = 72.0,
            discomforts = listOf("none", "   "),
            stillPresentDiscomforts = listOf("none"),
        )

        assertFalse(PerformanceTauLearner.hasReportedDiscomfort(todayLimpio))

        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = todayLimpio,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        assertTrue("No debe excluirse por molestia sentinel", result.observations.any { it.parameterKey == "pectorales" })
    }

    @Test
    fun descargaEnSesionMedida_excluyeInclusoConRpeAlto() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)

        val todayDeload = log(
            "today", originMs, weight = 70.0, rpe = 9.0, // RPE alto pero en deload
            context = AugePerformanceContext(sessionStartTimeMs = originMs, isDeload = true, deloadOrigin = "week_execution_kind"),
        )

        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = todayDeload,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        assertTrue(result.observations.isEmpty())
        assertEquals("deload", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun sesionMedidaSinContexto_seExcluyeConMotivoNoContext() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)

        // Sesión legacy sin contexto nuevo
        val todayLegacy = log("today", originMs, weight = 70.0, context = null)

        val result = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(h1, h2, h3),
                today = todayLegacy,
                nowMs = originMs + 3600_000L,
                exerciseDb = testDb,
                predictedEnergy = 85,
                predictedStructure = 90,
                predictedMuscles = mapOf("Pectorales" to 70),
            ),
        )

        assertTrue(result.observations.isEmpty())
        assertEquals("no_context", result.diagnostics.first { it.channel == "Pectorales" }.skipReason)
    }

    @Test
    fun sesionesDeReferenciaHeredadasSinContexto_siguenSiendoEligibles() {
        // Referencias legacy sin contexto
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0, context = null)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0, context = null)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0, context = null)

        // Sesión medida con contexto
        val today = log("today", originMs, weight = 72.0)

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

        assertTrue("Referencias legacy sin contexto siguen siendo elegibles", result.observations.any { it.parameterKey == "pectorales" })
    }

    // ─── Ausencia de esfuerzo y ΔRPE ────────────────────────────────────────────────────────────

    @Test
    fun rpeCompletamenteAusente_ceroObservacionesDeltaRpe() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0, rpe = null)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0, rpe = null)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0, rpe = null)
        val today = log("today", originMs, weight = 100.0, rpe = null)

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

        // ΔRPE no se construye (no aparece el 7 de respaldo)
        val deltaRpeObs = result.observations.firstOrNull { it.source == "delta_rpe" }
        assertNull("Sin RPE explícito no se emite delta_rpe", deltaRpeObs)
    }

    // ─── Idempotencia ───────────────────────────────────────────────────────────────────────────

    @Test
    fun reprocesarMismoLogTresVeces_generaComoMaximoUnaActualizacionYUnSoloRegistro() {
        val h1 = log("h1", originMs - 72L * 3600_000L, weight = 100.0)
        val h2 = log("h2", originMs - 48L * 3600_000L, weight = 100.0)
        val h3 = log("h3", originMs - 24L * 3600_000L, weight = 100.0)
        val today = log("today", originMs, weight = 72.0)

        val input = PerformanceTauInput(
            historyWithoutToday = listOf(h1, h2, h3),
            today = today,
            nowMs = originMs + 3600_000L,
            exerciseDb = testDb,
            predictedEnergy = 85,
            predictedStructure = 90,
            predictedMuscles = mapOf("Pectorales" to 70),
        )

        val result = PerformanceTauLearner.observations(input)
        val model = AugeObservationModel { _, lnTau ->
            val b = 75.0 - 10.0 * (lnTau - ln(48.0))
            AugePrediction(battery = b.coerceIn(0.0, 100.0))
        }

        var cache = AugeAdaptiveCache()
        cache = PerformanceTauLearner.applyToCache(cache, result, today.id, originMs + 3600_000L, model)
        val nAfterFirst = cache.parameterStates.getValue("pectorales").n
        val obsCountFirst = cache.observations.size

        assertEquals(1, nAfterFirst)
        assertEquals(result.observations.size, obsCountFirst)

        // Segundo intento
        cache = PerformanceTauLearner.applyToCache(cache, result, today.id, originMs + 3600_000L, model)
        assertEquals("n no sube en segundo intento", nAfterFirst, cache.parameterStates.getValue("pectorales").n)
        assertEquals("observaciones no aumentan", obsCountFirst, cache.observations.size)

        // Tercer intento
        cache = PerformanceTauLearner.applyToCache(cache, result, today.id, originMs + 3600_000L, model)
        assertEquals("n no sube en tercer intento", nAfterFirst, cache.parameterStates.getValue("pectorales").n)
        assertEquals("observaciones no aumentan", obsCountFirst, cache.observations.size)
    }

    // ─── Simulación Adversa / Alternante ─────────────────────────────────────────────────────────

    @Test
    fun simulacionAdversaAlternante_ceroAcortamientosConNuNegativoYSinNan() {
        val model = AugeObservationModel { _, lnTau ->
            // Ley estándar: mayor τ => menor recuperación en tiempo fijo
            val b = 70.0 - 8.0 * (lnTau - ln(48.0))
            AugePrediction(battery = b.coerceIn(0.0, 100.0))
        }

        var cache = AugeAdaptiveCache()
        var currentTau = 48.0
        val baseMs = originMs

        for (step in 1..20) {
            val isWorse = step % 2 == 1
            val impliedBattery = if (isWorse) 55.0 else 85.0
            val sigma = if (isWorse) 12.0 else 12.0 / sqrt(0.5)

            val obs = AugeLearningObservation(
                parameterKey = "pectorales",
                y = impliedBattery,
                sigma = sigma,
                source = "e1rm",
                anchorMs = baseMs + step * 24L * 3600_000L,
                observedAtMs = baseMs + step * 24L * 3600_000L,
                relevantLogId = "load-$step",
                hoursSinceRelevantLoad = 24.0,
            )

            val update = AugeLearner.update(cache, obs, model)
            if (update.outcome.status == AugeObservationStatus.ADMITTED) {
                val nextTau = exp(update.cache.parameterStates.getValue("pectorales").mean)
                assertFalse("nextTau no debe ser NaN", nextTau.isNaN())
                assertTrue("nextTau dentro de límites [18, 144]", nextTau in 18.0..144.0)

                val nu = obs.y - requireNotNull(update.detail?.h)
                if (nu < 0) {
                    assertTrue(
                        "Con ν < 0, τ posterior ($nextTau) no debe acortarse respecto al anterior ($currentTau)",
                        nextTau >= currentTau - 1e-9,
                    )
                } else if (nu > 0) {
                    assertTrue(
                        "Con ν > 0, τ posterior ($nextTau) no debe alargarse respecto al anterior ($currentTau)",
                        nextTau <= currentTau + 1e-9,
                    )
                }
                currentTau = nextTau
                cache = update.cache
            }
        }
    }
}
