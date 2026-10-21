package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationOutcome
import com.example.kpkn.data.models.AugeObservationRecord
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.AugeParameterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * AugeLearnerEkfTest — Pruebas del actualizador bayesiano puro AugeLearner (WP 3.2b).
 *
 * Cubre los criterios (c), (d), (e), (f), bordes numéricos del brief y reglas de versionado/idempotencia.
 */
class AugeLearnerEkfTest {

    private val BASE_MS = 1_700_000_000_000L

    /**
     * Criterio (c) Signo:
     * Para y < h en Energía, Columna y músculo: 0 acortamientos de τ (τ' >= τ).
     * Comprobado sobre 300 casos con semilla fija (incluyendo interior, pisos y bordes).
     */
    @Test
    fun criterioCSignoNuncaAcortaTauParaYMenorQueH() {
        val rng = Random(42)
        val channels = listOf("cns", "spinal", "pectorales")
        var acortamientos = 0
        var totalCasos = 0

        for (i in 1..300) {
            val key = channels[i % channels.size]
            val rc = 0.2 + rng.nextDouble() * 0.8
            val dt = 8.0 + rng.nextDouble() * (336.0 - 8.0)
            val atFloor = rng.nextDouble() < 0.1
            val blocked = rng.nextDouble() < 0.1

            // Rango de m entre bordes de τ: [ln 18, ln 144]
            val m = when (i % 5) {
                0 -> ln(18.0)
                1 -> ln(144.0)
                2 -> ln(18.05)
                3 -> ln(143.95)
                else -> ln(18.0) + rng.nextDouble() * (ln(144.0) - ln(18.0))
            }
            val model = ToyObservationModel(rc = rc, dt = dt, atFloor = atFloor, blocked = blocked)
            val pred = model.predict(key, m)

            // Forzar y < pred.battery
            val h = pred.battery
            val y = if (h > 5.0) 1.0 + rng.nextDouble() * (h - 2.0) else maxOf(0.1, h - 0.5)

            val state = AugeParameterState(
                mean = m,
                variance = AugeParameters.LEARNER_P0,
                n = i,
                lastUpdatedAtMs = BASE_MS,
                paramsVersion = AugeParameters.VERSION,
            )
            val cache = AugeAdaptiveCache(parameterStates = mapOf(key to state))
            val obs = AugeLearningObservation(
                parameterKey = key,
                y = y,
                sigma = 5.0,
                source = "manual",
                anchorMs = BASE_MS + i * 1000L,
                observedAtMs = BASE_MS + i * 1000L,
                relevantLogId = "log_$i",
                hoursSinceRelevantLoad = dt,
            )

            val res = AugeLearner.update(cache, obs, model)
            val tauBefore = exp(m)
            val finalMean = res.cache.parameterStates[key]?.mean ?: m
            val tauAfter = exp(finalMean)

            if (tauAfter < tauBefore - 1e-12) {
                acortamientos++
            }
            totalCasos++
        }

        assertEquals("0 acortamientos de τ para y < h sobre $totalCasos casos", 0, acortamientos)
    }

    /**
     * Criterio (d) Idempotencia:
     * Repetir inmediatamente, tras serialización/deserialización JSON, y después de más de 200 registros:
     * 0 cambios adicionales y devolución de la misma instancia o rechazo por duplicado.
     */
    @Test
    fun criterioDIdempotenciaInmediataJsonYTrasDoscientosRegistros() {
        val key = "cns"
        val model = ToyObservationModel(rc = 0.5, dt = 24.0)
        val initialCache = AugeAdaptiveCache()

        val obs = AugeLearningObservation(
            parameterKey = key,
            y = 70.0,
            sigma = 5.0,
            source = "manual",
            anchorMs = BASE_MS + 1000L,
            observedAtMs = BASE_MS + 1000L,
            relevantLogId = "log_idem_1",
            hoursSinceRelevantLoad = 24.0,
        )

        // 1. Primera actualización admitida
        val res1 = AugeLearner.update(initialCache, obs, model)
        assertEquals(AugeObservationStatus.ADMITTED, res1.outcome.status)
        val cache1 = res1.cache
        assertEquals(1, cache1.observations.size)

        // 2. Repetición inmediata: devuelve EXACTAMENTE la misma instancia
        val res2 = AugeLearner.update(cache1, obs, model)
        assertEquals(AugeObservationStatus.REJECTED, res2.outcome.status)
        assertEquals("duplicado", res2.outcome.reason)
        assertSame(cache1, res2.cache)
        assertNull(res2.detail)

        // 3. Tras ida y vuelta JSON
        val encoded = AugeAdaptiveCacheMigration.encodeAdaptiveCache(cache1)
        val decoded = AugeAdaptiveCacheMigration.toAdaptiveCache(encoded)
        val res3 = AugeLearner.update(decoded, obs, model)
        assertEquals(AugeObservationStatus.REJECTED, res3.outcome.status)
        assertEquals("duplicado", res3.outcome.reason)
        assertEquals(decoded, res3.cache)

        // 4. Tras más de 200 registros de otros canales que expulsan el registro del anillo
        var cacheHeavy = cache1
        for (i in 1..210) {
            val dummyRecord = AugeObservationRecord(
                id = "dummy_$i",
                parameterKey = "spinal",
                y = 60.0,
                predicted = 65.0,
                deltaHours = 24.0,
                source = "manual",
                tauUsedHours = 52.0,
                observedAtMs = BASE_MS + 2000L + i,
                paramsVersion = AugeParameters.VERSION,
                anchorMs = BASE_MS + 2000L + i,
            )
            cacheHeavy = AugeAdaptiveCacheMigration.record(cacheHeavy, dummyRecord)
        }
        assertEquals(200, cacheHeavy.observations.size)
        // La observación original ya no está en la lista de observaciones
        val obsId = AugeAdaptiveCacheMigration.observationId(key, obs.anchorMs, obs.y, obs.relevantLogId)
        assertTrue(cacheHeavy.observations.none { it.id == obsId })
        // Pero la marca de agua del parámetro sigue protegiéndolo
        assertTrue(cacheHeavy.parameterStates.getValue(key).replayWatermarkMs >= obs.anchorMs)

        val res4 = AugeLearner.update(cacheHeavy, obs, model)
        assertEquals(AugeObservationStatus.REJECTED, res4.outcome.status)
        assertEquals("duplicado", res4.outcome.reason)
        assertEquals(cacheHeavy, res4.cache)
    }

    /**
     * Criterio (e) Censura:
     * 0 cambio de m, P, n, fecha en saturada (y o pred >= 99,5), piso/bloqueo, y |ν| < 3.
     */
    @Test
    fun criterioECensuraSaturadaPisoBloqueoYZonaMuertaSinCambiosDeEstado() {
        val key = "pectorales"
        val m0 = ln(48.0)
        val p0 = AugeParameters.LEARNER_P0
        val lastUpdate = BASE_MS
        val state0 = AugeParameterState(
            mean = m0,
            variance = p0,
            n = 5,
            lastUpdatedAtMs = lastUpdate,
            paramsVersion = AugeParameters.VERSION,
        )
        val initialCache = AugeAdaptiveCache(parameterStates = mapOf(key to state0))

        // Caso 1: Lectura saturada y >= 99.5
        val obsSatY = AugeLearningObservation(
            parameterKey = key, y = 99.6, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 10_000L, observedAtMs = BASE_MS + 10_000L, relevantLogId = "log_sat_y",
            hoursSinceRelevantLoad = 24.0,
        )
        val resSatY = AugeLearner.update(initialCache, obsSatY, ToyObservationModel(0.5, 24.0))
        assertEquals(AugeObservationStatus.CENSORED, resSatY.outcome.status)
        assertEquals("saturada", resSatY.outcome.reason)
        val stateSatY = resSatY.cache.parameterStates.getValue(key)
        assertEquals(m0, stateSatY.mean, 0.0)
        assertEquals(p0, stateSatY.variance, 0.0)
        assertEquals(5, stateSatY.n)
        assertEquals(lastUpdate, stateSatY.lastUpdatedAtMs)

        // Caso 2: Predicción saturada pred >= 99.5
        val obsSatPred = AugeLearningObservation(
            parameterKey = key, y = 90.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 20_000L, observedAtMs = BASE_MS + 20_000L, relevantLogId = "log_sat_pred",
            hoursSinceRelevantLoad = 24.0,
        )
        // Con rc = 0.001, 100 · exp(−0.001 · exp(−24/48)) ≈ 99.94 >= 99.5
        val resSatPred = AugeLearner.update(initialCache, obsSatPred, ToyObservationModel(0.001, 24.0))
        assertEquals(AugeObservationStatus.CENSORED, resSatPred.outcome.status)
        assertEquals("saturada", resSatPred.outcome.reason)
        val stateSatPred = resSatPred.cache.parameterStates.getValue(key)
        assertEquals(m0, stateSatPred.mean, 0.0)
        assertEquals(p0, stateSatPred.variance, 0.0)
        assertEquals(5, stateSatPred.n)
        assertEquals(lastUpdate, stateSatPred.lastUpdatedAtMs)

        // Caso 3: Piso fisiológico (atFloor = true)
        val obsFloor = AugeLearningObservation(
            parameterKey = key, y = 70.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 30_000L, observedAtMs = BASE_MS + 30_000L, relevantLogId = "log_floor",
            hoursSinceRelevantLoad = 24.0,
        )
        val resFloor = AugeLearner.update(initialCache, obsFloor, ToyObservationModel(0.5, 24.0, atFloor = true))
        assertEquals(AugeObservationStatus.CENSORED, resFloor.outcome.status)
        assertEquals("piso_o_bloqueo", resFloor.outcome.reason)
        val stateFloor = resFloor.cache.parameterStates.getValue(key)
        assertEquals(m0, stateFloor.mean, 0.0)
        assertEquals(p0, stateFloor.variance, 0.0)
        assertEquals(5, stateFloor.n)
        assertEquals(lastUpdate, stateFloor.lastUpdatedAtMs)

        // Caso 4: Bloqueo por DOMS o articular (blocked = true)
        val obsBlocked = AugeLearningObservation(
            parameterKey = key, y = 70.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 40_000L, observedAtMs = BASE_MS + 40_000L, relevantLogId = "log_blocked",
            hoursSinceRelevantLoad = 24.0,
        )
        val resBlocked = AugeLearner.update(initialCache, obsBlocked, ToyObservationModel(0.5, 24.0, blocked = true))
        assertEquals(AugeObservationStatus.CENSORED, resBlocked.outcome.status)
        assertEquals("piso_o_bloqueo", resBlocked.outcome.reason)
        val stateBlocked = resBlocked.cache.parameterStates.getValue(key)
        assertEquals(m0, stateBlocked.mean, 0.0)
        assertEquals(p0, stateBlocked.variance, 0.0)
        assertEquals(5, stateBlocked.n)
        assertEquals(lastUpdate, stateBlocked.lastUpdatedAtMs)

        // Caso 5: Zona muerta |ν| < 3
        val hNominal = ToyObservationModel(0.5, 24.0).predict(key, m0).battery
        val obsDeadZone = AugeLearningObservation(
            parameterKey = key, y = hNominal + 1.5, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 50_000L, observedAtMs = BASE_MS + 50_000L, relevantLogId = "log_dz",
            hoursSinceRelevantLoad = 24.0,
        )
        val resDeadZone = AugeLearner.update(initialCache, obsDeadZone, ToyObservationModel(0.5, 24.0))
        assertEquals(AugeObservationStatus.CENSORED, resDeadZone.outcome.status)
        assertEquals("zona_muerta", resDeadZone.outcome.reason)
        val stateDeadZone = resDeadZone.cache.parameterStates.getValue(key)
        assertEquals(m0, stateDeadZone.mean, 0.0)
        assertEquals(p0, stateDeadZone.variance, 0.0)
        assertEquals(5, stateDeadZone.n)
        assertEquals(lastUpdate, stateDeadZone.lastUpdatedAtMs)
    }

    /**
     * Criterio (f) Parámetro nuevo:
     * Tras 40 observaciones de otros canales, su primera actualización coincide exactamente
     * con la del primer músculo de un usuario fresco: |Δm|, |ΔP| <= 1e-12 y mismo n = 1.
     */
    @Test
    fun criterioFParametroNuevoTrasCuarentaObservacionesDeOtrosCanales() {
        val model = ToyObservationModel(0.5, 24.0)
        var cache = AugeAdaptiveCache()

        // 40 observaciones repartidas en cns y spinal
        for (i in 1..40) {
            val ch = if (i % 2 == 0) "cns" else "spinal"
            val obsOther = AugeLearningObservation(
                parameterKey = ch,
                y = 70.0 + (i % 5),
                sigma = 5.0,
                source = "manual",
                anchorMs = BASE_MS + i * 86_400_000L,
                observedAtMs = BASE_MS + i * 86_400_000L,
                relevantLogId = "log_other_$i",
                hoursSinceRelevantLoad = 24.0,
            )
            cache = AugeLearner.update(cache, obsOther, model).cache
        }

        // Nuevo músculo: "cuadriceps" (perfil slow, prior tau = 72 h). La clave canónica es
        // la del pilar en minúsculas (con tilde), no el texto crudo de entrada.
        val newMuscleKey = AugeAdaptiveCacheMigration.parameterKey("cuadriceps")
        val obsNew = AugeLearningObservation(
            parameterKey = newMuscleKey,
            y = 65.0,
            sigma = 5.0,
            source = "manual",
            anchorMs = BASE_MS + 50 * 86_400_000L,
            observedAtMs = BASE_MS + 50 * 86_400_000L,
            relevantLogId = "log_cuad_1",
            hoursSinceRelevantLoad = 24.0,
        )

        // Actualizar en el caché con 40 observaciones previas
        val resTrained = AugeLearner.update(cache, obsNew, model)
        val stateTrained = resTrained.cache.parameterStates.getValue(newMuscleKey)

        // Actualizar en un caché fresco sin historial
        val resFresh = AugeLearner.update(AugeAdaptiveCache(), obsNew, model)
        val stateFresh = resFresh.cache.parameterStates.getValue(newMuscleKey)

        assertEquals("n debe ser 1", 1, stateTrained.n)
        assertEquals("n debe ser 1 en fresh", 1, stateFresh.n)
        assertEquals("|Δm| <= 1e-12", stateFresh.mean, stateTrained.mean, 1e-12)
        assertEquals("|ΔP| <= 1e-12", stateFresh.variance, stateTrained.variance, 1e-12)
        assertEquals("Horas de recuperación personalizadas idénticas",
            resFresh.cache.personalizedRecoveryHours[newMuscleKey]!!,
            resTrained.cache.personalizedRecoveryHours[newMuscleKey]!!,
            1e-12,
        )
    }

    /**
     * Casos de borde del brief:
     * - 7,99 h (censura ventana) vs 8,0 h (admitida)
     * - 336,0 h (admitida) vs 336,01 h (censura ventana)
     * - |ν| = 2,99 puntos (censura zona muerta) vs 3,0 puntos exactos (admitida)
     * - y = 99,49 (no saturada) vs 99,5 (censura saturada)
     * - H ≈ 0 (censura sin sensibilidad)
     * - NaN / ∞ en y o sigma (rechazada inválida)
     * - σ <= 0 (rechazada inválida)
     * - Límites de τ: 18 h (unilateral adelante) y 144 h (unilateral atrás)
     */
    @Test
    fun casosDeBordeDelBrief() {
        val key = "cns"
        val model = ToyObservationModel(0.5, 24.0)

        // 1. Borde de ventana inferior: 7.99 h vs 8.0 h
        val obs799 = AugeLearningObservation(
            parameterKey = key, y = 70.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 1000L, observedAtMs = BASE_MS + 1000L, relevantLogId = null,
            hoursSinceRelevantLoad = 7.99,
        )
        val res799 = AugeLearner.update(AugeAdaptiveCache(), obs799, model)
        assertEquals(AugeObservationStatus.CENSORED, res799.outcome.status)
        assertEquals("ventana", res799.outcome.reason)

        val obs800 = AugeLearningObservation(
            parameterKey = key, y = 70.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 2000L, observedAtMs = BASE_MS + 2000L, relevantLogId = null,
            hoursSinceRelevantLoad = 8.0,
        )
        val res800 = AugeLearner.update(AugeAdaptiveCache(), obs800, model)
        assertEquals(AugeObservationStatus.ADMITTED, res800.outcome.status)

        // 2. Borde de ventana superior: 336.0 h vs 336.01 h
        val obs33600 = AugeLearningObservation(
            parameterKey = key, y = 70.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 3000L, observedAtMs = BASE_MS + 3000L, relevantLogId = null,
            hoursSinceRelevantLoad = 336.0,
        )
        val res33600 = AugeLearner.update(AugeAdaptiveCache(), obs33600, model)
        assertEquals(AugeObservationStatus.ADMITTED, res33600.outcome.status)

        val obs33601 = AugeLearningObservation(
            parameterKey = key, y = 70.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 4000L, observedAtMs = BASE_MS + 4000L, relevantLogId = null,
            hoursSinceRelevantLoad = 336.01,
        )
        val res33601 = AugeLearner.update(AugeAdaptiveCache(), obs33601, model)
        assertEquals(AugeObservationStatus.CENSORED, res33601.outcome.status)
        assertEquals("ventana", res33601.outcome.reason)

        // 3. Zona muerta: 2.99 vs 3.0 exacto.
        // Modelo lineal local con h(m_prior) = 70 exacto y H = −10: h(θ) = 70 − 10·(θ − m_prior).
        // Así |ν| = 3.0 es exactamente representable y no depende de redondeos de h(m).
        val mPrior = AugeAdaptiveCacheMigration.prior(key).mean
        val linearModel = AugeObservationModel { _, lnTau ->
            AugePrediction(battery = 70.0 - 10.0 * (lnTau - mPrior))
        }
        val obsNu299 = AugeLearningObservation(
            parameterKey = key, y = 67.01, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 5000L, observedAtMs = BASE_MS + 5000L, relevantLogId = null,
            hoursSinceRelevantLoad = 24.0,
        )
        val resNu299 = AugeLearner.update(AugeAdaptiveCache(), obsNu299, linearModel)
        assertEquals(AugeObservationStatus.CENSORED, resNu299.outcome.status)
        assertEquals("zona_muerta", resNu299.outcome.reason)

        val obsNu300 = AugeLearningObservation(
            parameterKey = key, y = 67.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 6000L, observedAtMs = BASE_MS + 6000L, relevantLogId = null,
            hoursSinceRelevantLoad = 24.0,
        )
        val resNu300 = AugeLearner.update(AugeAdaptiveCache(), obsNu300, linearModel)
        assertEquals(AugeObservationStatus.ADMITTED, resNu300.outcome.status)

        // 4. Saturación: 99.49 vs 99.5
        val obsSat9949 = AugeLearningObservation(
            parameterKey = key, y = 99.49, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 7000L, observedAtMs = BASE_MS + 7000L, relevantLogId = null,
            hoursSinceRelevantLoad = 24.0,
        )
        val resSat9949 = AugeLearner.update(AugeAdaptiveCache(), obsSat9949, model)
        assertEquals(AugeObservationStatus.ADMITTED, resSat9949.outcome.status)

        val obsSat9950 = AugeLearningObservation(
            parameterKey = key, y = 99.5, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 8000L, observedAtMs = BASE_MS + 8000L, relevantLogId = null,
            hoursSinceRelevantLoad = 24.0,
        )
        val resSat9950 = AugeLearner.update(AugeAdaptiveCache(), obsSat9950, model)
        assertEquals(AugeObservationStatus.CENSORED, resSat9950.outcome.status)
        assertEquals("saturada", resSat9950.outcome.reason)

        // 5. Sensibilidad H ≈ 0
        val modelZeroH = AugeObservationModel { _, _ -> AugePrediction(battery = 50.0) }
        val obsZeroH = AugeLearningObservation(
            parameterKey = key, y = 60.0, sigma = 5.0, source = "manual",
            anchorMs = BASE_MS + 9000L, observedAtMs = BASE_MS + 9000L, relevantLogId = null,
            hoursSinceRelevantLoad = 24.0,
        )
        val resZeroH = AugeLearner.update(AugeAdaptiveCache(), obsZeroH, modelZeroH)
        assertEquals(AugeObservationStatus.CENSORED, resZeroH.outcome.status)
        assertEquals("sin_sensibilidad", resZeroH.outcome.reason)

        // 6. Entradas no finitas o σ <= 0 -> RECHAZADA «invalida»
        listOf(
            obs800.copy(y = Double.NaN),
            obs800.copy(y = Double.POSITIVE_INFINITY),
            obs800.copy(sigma = 0.0),
            obs800.copy(sigma = -1.0),
            obs800.copy(sigma = Double.NaN),
            obs800.copy(hoursSinceRelevantLoad = Double.NaN),
        ).forEach { invalidObs ->
            val res = AugeLearner.update(AugeAdaptiveCache(), invalidObs, model)
            assertEquals(AugeObservationStatus.REJECTED, res.outcome.status)
            assertEquals("invalida", res.outcome.reason)
        }

        // 7. τ en bordes: 18 h (unilateral hacia delante) y 144 h (unilateral hacia atrás)
        val state18 = AugeParameterState(mean = ln(18.0), variance = AugeParameters.LEARNER_P0, paramsVersion = AugeParameters.VERSION)
        val res18 = AugeLearner.update(AugeAdaptiveCache(parameterStates = mapOf(key to state18)), obs800, model)
        assertEquals(AugeObservationStatus.ADMITTED, res18.outcome.status)
        assertTrue(res18.detail!!.meanAfter >= ln(18.0))

        val state144 = AugeParameterState(mean = ln(144.0), variance = AugeParameters.LEARNER_P0, paramsVersion = AugeParameters.VERSION)
        val res144 = AugeLearner.update(AugeAdaptiveCache(parameterStates = mapOf(key to state144)), obs800, model)
        assertEquals(AugeObservationStatus.ADMITTED, res144.outcome.status)
        assertTrue(res144.detail!!.meanAfter <= ln(144.0))
    }

    /**
     * Versión:
     * Si state.paramsVersion != AugeParameters.VERSION, el posterior se trata como prior (n = 0)
     * y se retiran sus horas derivadas; las observaciones históricas se conservan con su versión.
     *
     * Nota: se usa VERSION + 1 como versión incompatible porque el registro histórico debe tener
     * paramsVersion > 0 para sobrevivir a la normalización (con VERSION = 1, VERSION − 1 = 0 sería
     * descartado como registro corrupto, no conservado).
     */
    @Test
    fun versionIncompatibleReseteaAlPriorYRetiraHorasDerivadas() {
        val key = "pectorales"
        val versionIncompatible = AugeParameters.VERSION + 1
        val oldState = AugeParameterState(
            mean = ln(80.0),
            variance = 0.05,
            n = 15,
            lastUpdatedAtMs = BASE_MS,
            paramsVersion = versionIncompatible,
        )
        val oldRecord = AugeObservationRecord(
            id = "old_rec_1",
            parameterKey = key,
            y = 70.0,
            predicted = 75.0,
            deltaHours = 24.0,
            source = "manual",
            tauUsedHours = 80.0,
            observedAtMs = BASE_MS,
            paramsVersion = versionIncompatible,
        )
        val oldCache = AugeAdaptiveCache(
            personalizedRecoveryHours = mapOf(key to 80.0),
            parameterStates = mapOf(key to oldState),
            observations = listOf(oldRecord),
        )

        val model = ToyObservationModel(0.5, 24.0)
        val obs = AugeLearningObservation(
            parameterKey = key,
            y = 70.0,
            sigma = 5.0,
            source = "manual",
            anchorMs = BASE_MS + 86_400_000L,
            observedAtMs = BASE_MS + 86_400_000L,
            relevantLogId = "log_version_test",
            hoursSinceRelevantLoad = 24.0,
        )

        val res = AugeLearner.update(oldCache, obs, model)
        assertEquals(AugeObservationStatus.ADMITTED, res.outcome.status)

        val newState = res.cache.parameterStates.getValue(key)
        assertEquals("n debe arrancar en 1 (prior tratado con n = 0)", 1, newState.n)
        assertEquals("paramsVersion debe actualizarse a la versión vigente", AugeParameters.VERSION, newState.paramsVersion)

        // Las observaciones históricas se conservan con su versión original
        assertEquals(2, res.cache.observations.size)
        assertEquals(versionIncompatible, res.cache.observations.first().paramsVersion)
        assertEquals(AugeParameters.VERSION, res.cache.observations.last().paramsVersion)

        // Las horas se proyectaron a partir del nuevo posterior
        assertEquals(exp(newState.mean), res.cache.personalizedRecoveryHours.getValue(key), 1e-9)
    }
}
