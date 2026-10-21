package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.AugeParameterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln

/**
 * Modelo de observación de juguete cerrado:
 * h(ln τ) = 100 · exp(−rc · exp(−dt / exp(ln τ)))
 */
class ToyObservationModel(
    val rc: Double,
    val dt: Double,
    val atFloor: Boolean = false,
    val blocked: Boolean = false,
) : AugeObservationModel {
    override fun predict(parameterKey: String, lnTau: Double): AugePrediction {
        val tau = exp(lnTau)
        val battery = 100.0 * exp(-rc * exp(-dt / tau))
        return AugePrediction(battery = battery, atFloor = atFloor, blocked = blocked)
    }
}

/**
 * Modelo de juguete con h creciente (no monótona) para vector V7:
 * h(ln τ) = 100 · exp(−rc · exp(−dt · exp(ln τ) / 1000))
 */
class ToyIncreasingObservationModel(
    val rc: Double = 0.5,
    val dt: Double = 24.0,
) : AugeObservationModel {
    override fun predict(parameterKey: String, lnTau: Double): AugePrediction {
        val tau = exp(lnTau)
        val battery = 100.0 * exp(-rc * exp(-dt * tau / 1000.0))
        return AugePrediction(battery = battery)
    }
}

/**
 * AugeLearnerOracleVectorsTest — Reproduce a 1e-9 los 12 vectores V1..V12 y la cadena V13 (pasos 1..4)
 * generados por el oráculo independiente ekf_oracle.py (WP 3.2b).
 */
class AugeLearnerOracleVectorsTest {

    private val TOL = 1e-9
    private val BASE_MS = 1_700_000_000_000L

    private data class OracleVector(
        val id: String,
        val m0: Double,
        val p0: Double,
        val y: Double,
        val sigma: Double,
        val dDias: Double,
        val model: AugeObservationModel,
        val expectedStatus: AugeObservationStatus,
        val expectedM1: Double,
        val expectedP1: Double,
        val expectedNu: Double,
        val expectedH: Double?,
        val expectedS: Double?,
        val expectedK: Double?,
        val expectedPred: Double,
    )

    @Test
    fun reproduceDoceVectoresIndependientesDelOraculo() {
        val vectors = listOf(
            OracleVector(
                id = "V1_peor_que_lo_previsto",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 70.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 4.001722797829607,
                expectedP1 = 0.07587754134749873,
                expectedNu = -3.840314997473101,
                expectedH = -11.198063379111401,
                expectedS = 40.36108637171799,
                expectedK = -0.033987260682417475,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V2_mejor_que_lo_previsto",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 78.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 3.729824712370267,
                expectedP1 = 0.07587754134749873,
                expectedNu = 4.159685002526899,
                expectedH = -11.198063379111401,
                expectedS = 40.36108637171799,
                expectedK = -0.033987260682417475,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V3_recorte_2_5_sqrtS",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 40.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 4.41100687098921,
                expectedP1 = 0.07587754134749873,
                expectedNu = -33.8403149974731,
                expectedH = -11.198063379111401,
                expectedS = 40.36108637171799,
                expectedK = -0.033987260682417475,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V4_zona_muerta",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 75.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.CENSORED,
                expectedM1 = 3.871201010907891,
                expectedP1 = 0.12249999999999998,
                expectedNu = 1.1596850025268992,
                expectedH = -11.198063379111401,
                expectedS = null,
                expectedK = null,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V4b_zona_muerta_borde_3",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 70.8403149974731,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 3.9731627929551436,
                expectedP1 = 0.07587754134749873,
                expectedNu = -3.0,
                expectedH = -11.198063379111401,
                expectedS = 40.36108637171799,
                expectedK = -0.033987260682417475,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V5_lectura_saturada",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 99.6,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.CENSORED,
                expectedM1 = 3.871201010907891,
                expectedP1 = 0.12249999999999998,
                expectedNu = 25.759685002526894,
                expectedH = -11.198063379111401,
                expectedS = null,
                expectedK = null,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V6_q_por_dias",
                m0 = 3.871201010907891,
                p0 = 0.05,
                y = 70.0,
                sigma = 5.0,
                dDias = 10.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 3.9399639193174902,
                expectedP1 = 0.043974632198017796,
                expectedNu = -3.840314997473101,
                expectedH = -11.198063379111401,
                expectedS = 31.26983117212979,
                expectedK = -0.017905538596402825,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V7_H_no_negativa",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 70.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyIncreasingObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.CENSORED,
                expectedM1 = 3.871201010907891,
                expectedP1 = 0.12249999999999998,
                expectedNu = -15.384801932897787,
                expectedH = 15.540288179755926,
                expectedS = null,
                expectedK = null,
                expectedPred = 85.38480193289779,
            ),
            OracleVector(
                id = "V8_sin_sensibilidad",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 50.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 0.01),
                expectedStatus = AugeObservationStatus.CENSORED,
                expectedM1 = 3.871201010907891,
                expectedP1 = 0.12249999999999998,
                expectedNu = -10.659383669893316,
                expectedH = -0.006319054495737727,
                expectedS = null,
                expectedK = null,
                expectedPred = 60.659383669893316,
            ),
            OracleVector(
                id = "V9_borde_inferior_18h",
                m0 = 2.9014215940827497,
                p0 = 0.12249999999999998,
                y = 60.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.6, 30.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 3.571734453671362,
                expectedP1 = 0.05060890724322188,
                expectedNu = -29.099181196078874,
                expectedH = -17.026525080964937,
                expectedS = 60.51306315075919,
                expectedK = -0.034467753139877814,
                expectedPred = 89.09918119607887,
            ),
            OracleVector(
                id = "V10_borde_superior_144h",
                m0 = 4.962844630259907,
                p0 = 0.12249999999999998,
                y = 60.0,
                sigma = 5.0,
                dDias = 0.0,
                model = ToyObservationModel(0.6, 60.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 4.969813299576001,
                expectedP1 = 0.075666459607491,
                expectedNu = -7.408872519150577,
                expectedH = -11.239027860037064,
                expectedS = 40.47367903673944,
                expectedK = -0.034016697903958414,
                expectedPred = 67.40887251915058,
            ),
            OracleVector(
                id = "V11_sigma_e1rm_12",
                m0 = 3.871201010907891,
                p0 = 0.12249999999999998,
                y = 70.0,
                sigma = 12.0,
                dDias = 0.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 3.9042580214697447,
                expectedP1 = 0.11069201648672114,
                expectedNu = -3.840314997473101,
                expectedH = -11.198063379111401,
                expectedS = 159.361086371718,
                expectedK = -0.008607890390138524,
                expectedPred = 73.8403149974731,
            ),
            OracleVector(
                id = "V12_varianza_chica",
                m0 = 4.0943445622221,
                p0 = 0.01,
                y = 62.0,
                sigma = 5.0,
                dDias = 3.0,
                model = ToyObservationModel(0.5, 24.0),
                expectedStatus = AugeObservationStatus.ADMITTED,
                expectedM1 = 4.129576686712789,
                expectedP1 = 0.010845171667353205,
                expectedNu = -9.522362495169418,
                expectedH = -9.590122809404637,
                expectedS = 25.91970455499463,
                expectedK = -0.0036999352323082925,
                expectedPred = 71.52236249516942,
            ),
        )

        for (v in vectors) {
            val key = "cns"
            val lastUpdateMs = if (v.dDias > 0.0) BASE_MS else 0L
            val observedMs = if (v.dDias > 0.0) BASE_MS + (v.dDias * 86_400_000.0).toLong() else BASE_MS
            val state = AugeParameterState(
                mean = v.m0,
                variance = v.p0,
                n = if (v.dDias > 0.0) 1 else 0,
                lastUpdatedAtMs = lastUpdateMs,
                replayWatermarkMs = 0L,
                paramsVersion = AugeParameters.VERSION,
            )
            val cache = AugeAdaptiveCache(parameterStates = mapOf(key to state))
            val obs = AugeLearningObservation(
                parameterKey = key,
                y = v.y,
                sigma = v.sigma,
                source = "manual",
                anchorMs = observedMs,
                observedAtMs = observedMs,
                relevantLogId = "log_${v.id}",
                hoursSinceRelevantLoad = 24.0, // Dentro de [8, 336] h
            )

            val res = AugeLearner.update(cache, obs, v.model)
            assertEquals("${v.id} status", v.expectedStatus, res.outcome.status)

            val detail = requireNotNull(res.detail) { "${v.id} detail no debe ser null" }
            assertEquals("${v.id} nu", v.expectedNu, detail.nu, TOL)
            assertEquals("${v.id} pred", v.expectedPred, detail.h, TOL)

            if (v.expectedH != null) {
                assertEquals("${v.id} H", v.expectedH, detail.H!!, TOL)
            } else {
                assertNull("${v.id} H debe ser null", detail.H)
            }

            if (v.expectedS != null) {
                assertEquals("${v.id} S", v.expectedS, detail.S!!, TOL)
            } else {
                assertNull("${v.id} S debe ser null", detail.S)
            }

            if (v.expectedK != null) {
                assertEquals("${v.id} K", v.expectedK, detail.K!!, TOL)
            } else {
                assertNull("${v.id} K debe ser null", detail.K)
            }

            assertEquals("${v.id} m1", v.expectedM1, detail.meanAfter, TOL)
            assertEquals("${v.id} P1", v.expectedP1, detail.varianceAfter, TOL)

            // Comprobación sobre el estado en el caché resultante
            val finalState = res.cache.parameterStates.getValue(key)
            assertEquals("${v.id} cache m", v.expectedM1, finalState.mean, TOL)
            assertEquals("${v.id} cache P", v.expectedP1, finalState.variance, TOL)
        }
    }

    @Test
    fun reproduceCadenaV13CuatroPasosConsecutivos() {
        val key = "cns"
        val m0 = ln(48.0)
        val p0 = AugeParameters.LEARNER_P0
        var cache = AugeAdaptiveCache(
            parameterStates = mapOf(
                key to AugeParameterState(
                    mean = m0,
                    variance = p0,
                    n = 0,
                    lastUpdatedAtMs = 0L,
                    paramsVersion = AugeParameters.VERSION,
                ),
            ),
        )

        // Cadena V13: (y, dt, dDias, expectedM, expectedP, expectedNu, expectedH)
        data class ChainStep(
            val y: Double,
            val dt: Double,
            val dDias: Double,
            val expectedM: Double,
            val expectedP: Double,
            val expectedNu: Double,
            val expectedH: Double,
        )

        val steps = listOf(
            ChainStep(66.0, 24.0, 0.0, 4.1376718405592765, 0.07587754134749873, -7.840314997473101, -11.198063379111401),
            ChainStep(80.5, 36.0, 4.0, 4.009271849974192, 0.053850010643833414, 5.033142906313969, -12.20616835690559),
            ChainStep(88.0, 18.0, 6.0, 3.802589153308712, 0.04941517270768703, 18.27924229640577, -8.215276790387726),
            ChainStep(91.0, 30.0, 12.0, 3.5382563667330142, 0.041460062465639866, 13.586653158129693, -13.267385659960349),
        )

        var currentTimeMs = BASE_MS
        steps.forEachIndexed { i, step ->
            val stepNum = i + 1
            if (step.dDias > 0.0) {
                currentTimeMs += (step.dDias * 86_400_000.0).toLong()
            }
            val model = ToyObservationModel(0.5, step.dt)
            val obs = AugeLearningObservation(
                parameterKey = key,
                y = step.y,
                sigma = 5.0,
                source = "manual",
                anchorMs = currentTimeMs,
                observedAtMs = currentTimeMs,
                relevantLogId = "log_v13_step$stepNum",
                hoursSinceRelevantLoad = step.dt,
            )

            val res = AugeLearner.update(cache, obs, model)
            assertEquals("V13 paso $stepNum status", AugeObservationStatus.ADMITTED, res.outcome.status)

            val detail = requireNotNull(res.detail) { "V13 paso $stepNum detail no debe ser null" }
            assertEquals("V13 paso $stepNum nu", step.expectedNu, detail.nu, TOL)
            assertEquals("V13 paso $stepNum H", step.expectedH, detail.H!!, TOL)
            assertEquals("V13 paso $stepNum m", step.expectedM, detail.meanAfter, TOL)
            assertEquals("V13 paso $stepNum P", step.expectedP, detail.varianceAfter, TOL)

            val state = res.cache.parameterStates.getValue(key)
            assertEquals("V13 paso $stepNum state m", step.expectedM, state.mean, TOL)
            assertEquals("V13 paso $stepNum state P", step.expectedP, state.variance, TOL)
            assertEquals("V13 paso $stepNum state n", stepNum, state.n)
            assertEquals("V13 paso $stepNum state lastUpdatedAtMs", currentTimeMs, state.lastUpdatedAtMs)

            cache = res.cache
        }
    }
}
