package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Modelo de observación real (3.2c-1): h(θ) reevalúa el motor con el caché candidato y la lectura continua.
 *
 * Sin reloj real ni ruido: el corpus del máster dorado fija instantes y zona. Las cifras visibles del máster
 * no dependen de esta clase, que además comprueba monotonía, aislamiento, sensibilidad y convergencia.
 */
class AugeLearnerObservationModelTest {

    private val casos: List<GoldenCase> by lazy { AugeGoldenCorpus.build(AugeGoldenCorpus.BASE_MS) }

    private val instanteCarga: Long = AugeGoldenCorpus.BASE_MS + 12L * AugeGoldenCorpus.HORA_MS

    private fun inputs(
        caso: GoldenCase,
        cache: AugeAdaptiveCache = AugeAdaptiveCache(),
        history: List<WorkoutLog> = caso.history,
        nowMs: Long = caso.origin,
    ) = AugeSnapshotInputs(
        history = history,
        wellbeing = caso.wellbeing,
        settings = caso.settings,
        exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
        nowMs = nowMs,
        zone = AugeGoldenCorpus.ZONE,
        adaptiveCache = cache,
    )

    private fun modelo(
        caso: GoldenCase,
        instante: Long = instanteCarga,
        escala: ObservationScale = ObservationScale.SHOWN,
        cache: AugeAdaptiveCache = AugeAdaptiveCache(),
        history: List<WorkoutLog> = caso.history,
    ) = SnapshotObservationModel(inputs(caso, cache, history, instante), instante, escala)

    /** Malla de 8 τ (horas) entre 24 y 120, en pasos geométricos. */
    private val malla: List<Double> = List(8) { i -> 24.0 * Math.pow(120.0 / 24.0, i / 7.0) }

    private data class CasoConMusculo(val caso: GoldenCase, val musculo: String)

    private fun casosConCarga(): List<CasoConMusculo> = listOf(
        CasoConMusculo(casos.first { it.id == "A01" }, "Cuádriceps"),
        CasoConMusculo(casos.first { it.id == "A02" }, "Pectorales"),
        CasoConMusculo(casos.first { it.id == "A03" }, "Dorsales"),
    )

    private fun lecturasEstenCargas(
        caso: GoldenCase,
        clave: String,
        escala: ObservationScale,
    ): List<Double> = malla.map { tau ->
        modelo(caso, escala = escala).predict(clave, ln(tau)).battery
    }

    private fun assertEstrictamenteDecreciente(serie: List<Double>, etiqueta: String) {
        for (i in 1 until serie.size) {
            assertTrue(
                "$etiqueta debe ser estrictamente decreciente en τ: " +
                    "h(τ=${serie[i - 1]}) = ${serie[i - 1]} no es mayor que h(τ=${serie[i]}) = ${serie[i]}",
                serie[i] < serie[i - 1] - 1e-12,
            )
        }
    }

    // ─── (a) Monotonía estricta en τ sobre el motor real ─────────────────────

    @Test
    fun predictEsEstrictamenteDecrecienteEnTauParaCnsSpinalYMusculo() {
        for ((caso, musculo) in casosConCarga()) {
            listOf(ObservationScale.HISTORICAL_RAW, ObservationScale.SHOWN).forEach { escala ->
                assertEstrictamenteDecreciente(
                    lecturasEstenCargas(caso, "cns", escala),
                    "${caso.id} cns ($escala)",
                )
                assertEstrictamenteDecreciente(
                    lecturasEstenCargas(caso, "spinal", escala),
                    "${caso.id} spinal ($escala)",
                )
            }
            assertEstrictamenteDecreciente(
                lecturasEstenCargas(caso, musculo, ObservationScale.SHOWN),
                "${caso.id} $musculo",
            )
        }
    }

    // ─── (b) Aislamiento entre parámetros ────────────────────────────────────

    @Test
    fun cambiarUnTauNoAlteraLaLecturaDeLosDemasParametros() {
        val caso = casos.first { it.id == "A01" }
        val musculos = AugeParameters.PILLARS
        val lnFijo = ln(52.0)

        // Energía: dos cachés que solo difieren en el τ de Columna y músculos; el modelo fija el de Energía.
        val cnsBajo = modelo(caso, cache = AugeAdaptiveCache(cnsRecoveryHours = 24.0))
        val cnsAlto = modelo(caso, cache = AugeAdaptiveCache(cnsRecoveryHours = 120.0))
        // Columna: lo mismo con el τ de Energía.
        val spinalBajo = modelo(caso, cache = AugeAdaptiveCache(spinalRecoveryHours = 24.0))
        val spinalAlto = modelo(caso, cache = AugeAdaptiveCache(spinalRecoveryHours = 120.0))
        // Músculo: cambiar el τ de un músculo no toca a los canales del sistema.
        val musculoBajo = modelo(
            caso,
            cache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf("cuádriceps" to 24.0)),
        )
        val musculoAlto = modelo(
            caso,
            cache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf("cuádriceps" to 120.0)),
        )

        // Cambiar el τ de Energía no toca la Columna ni ningún músculo.
        assertEquals("cambiar τ de Energía no altera la Columna", cnsBajo.predict("spinal", lnFijo), cnsAlto.predict("spinal", lnFijo))
        for (musculo in musculos) {
            assertEquals(
                "cambiar τ de Energía no altera $musculo",
                cnsBajo.predict(musculo, lnFijo),
                cnsAlto.predict(musculo, lnFijo),
            )
        }

        // Cambiar el τ de Columna no toca la Energía ni ningún músculo.
        assertEquals("cambiar τ de Columna no altera la Energía", spinalBajo.predict("cns", lnFijo), spinalAlto.predict("cns", lnFijo))
        for (musculo in musculos) {
            assertEquals(
                "cambiar τ de Columna no altera $musculo",
                spinalBajo.predict(musculo, lnFijo),
                spinalAlto.predict(musculo, lnFijo),
            )
        }

        // Y el τ de un músculo tampoco mueve los canales del sistema ni otros músculos.
        assertEquals("cambiar τ de Cuádriceps no altera la Energía", musculoBajo.predict("cns", lnFijo), musculoAlto.predict("cns", lnFijo))
        assertEquals("cambiar τ de Cuádriceps no altera la Columna", musculoBajo.predict("spinal", lnFijo), musculoAlto.predict("spinal", lnFijo))
        for (musculo in musculos.filter { it != "Cuádriceps" }) {
            assertEquals(
                "cambiar τ de Cuádriceps no altera $musculo",
                musculoBajo.predict(musculo, lnFijo),
                musculoAlto.predict(musculo, lnFijo),
            )
        }
    }

    // ─── (c) La lectura continua conserva sensibilidad tras ±2 % ─────────────

    @Test
    fun diferenciaFinitaDelDosPorCientoDeEnergiaTieneSensibilidadSuficiente() {
        for ((caso, _) in casosConCarga()) {
            listOf(ObservationScale.HISTORICAL_RAW, ObservationScale.SHOWN).forEach { escala ->
                val modelo = modelo(caso, escala = escala)
                val tau = AugeParameters.DEFAULT_TAU_ENERGY_H
                val bajo = modelo.predict("cns", ln(tau * 0.98)).battery
                val alto = modelo.predict("cns", ln(tau * 1.02)).battery
                val h = (alto - bajo) / (ln(tau * 1.02) - ln(tau * 0.98))
                println("[OBS-H] ${caso.id} $escala: h(0,98τ)=$bajo h(1,02τ)=$alto H=$h")
                assertTrue("${caso.id} $escala: H debe ser negativo (Δ=$h)", h < 0.0)
                assertTrue("${caso.id} $escala: |H| >= 0,1 (H=$h)", abs(h) >= 0.1)
            }
        }
    }

    // ─── (d) Humo de convergencia con el motor real ──────────────────────────

    @Test
    fun ekfConvergeHaciaUnTauVerdaderoUnTreintaPorCientoMayorQueElPrior() {
        // C09 deja una carga grande y reciente (sesión ENORME al final): a diferencia de A01, la lectura sigue
        // en la zona sensible durante varios días, así que cada observación separa τ verdadero del posterior.
        val caso = casos.first { it.id == "C09" }
        val clave = "Cuádriceps"
        val claveCache = AugeAdaptiveCacheMigration.parameterKey(clave)
        val tauVerdadero = AugeAdaptiveCacheMigration.prior(claveCache).let { exp(it.mean) } * 1.3

        // Observaciones cada 24 h sobre el mismo historial, desde 72 h: la carga enorme mantiene la lectura en la
        // zona sensible (ni suelo ni saturación) durante toda la ventana de aprendizaje.
        val instantes = (0 until 6).map { i -> caso.origin + (3 + i) * 24L * AugeGoldenCorpus.HORA_MS }

        val lnVerdadero = ln(tauVerdadero)
        var cache = AugeAdaptiveCache()
        val errores = mutableListOf(abs(AugeAdaptiveCacheMigration.prior(claveCache).mean - lnVerdadero))
        var admitidas = 0
        var censuradas = 0

        instantes.forEachIndexed { i, instante ->
            val horasObservacion = (3 + i) * 24.0
            val modelo = modelo(caso, instante = instante, escala = ObservationScale.SHOWN)
            val y = modelo.predict(clave, lnVerdadero).battery
            val obs = AugeLearningObservation(
                parameterKey = clave,
                y = y,
                sigma = AugeParameters.LEARNER_SIGMA_MANUAL,
                source = "manual",
                anchorMs = instante,
                observedAtMs = instante,
                relevantLogId = "c09-final",
                hoursSinceRelevantLoad = horasObservacion,
            )
            val antes = cache.parameterStates[claveCache]?.mean ?: AugeAdaptiveCacheMigration.prior(claveCache).mean
            val res = AugeLearner.update(cache, obs, modelo)
            when (res.outcome.status) {
                AugeObservationStatus.ADMITTED -> admitidas++
                AugeObservationStatus.CENSORED -> {
                    censuradas++
                    // Al converger solo caben las censuras que expresan «ya no hay información útil»:
                    // zona muerta (|ν| < 3) o lectura saturada (≥ 99,5) cuando el músculo ya está recuperado.
                    assertTrue(
                        "Censura inesperada al converger: ${res.outcome.reason}",
                        res.outcome.reason == "zona_muerta" || res.outcome.reason == "saturada",
                    )
                }
                AugeObservationStatus.REJECTED -> error("observación de convergencia rechazada: ${res.outcome.reason}")
            }
            cache = res.cache
            val despues = cache.parameterStates[claveCache]?.mean ?: antes
            errores += abs(despues - lnVerdadero)
            println(
                "[OBS-CONV] obs=${i + 1} h=$horasObservacion y=${"%.4f".format(y)} " +
                    "pred=${"%.4f".format(modelo.predict(clave, antes).battery)} " +
                    "τ̂=${exp(despues)} error=${errores.last()} estado=${res.outcome.status}" +
                    (res.outcome.reason?.let { " ($it)" } ?: ""),
            )
        }

        // La zona muerta (3 puntos) censura las repeticiones posteriores en cuanto el posterior queda a menos de
        // 3 puntos de la lectura: una sola actualización basta para acercarse, y el resto no debe mover la media.
        assertTrue("Debe haber al menos una actualización admitida (admitidas=$admitidas censuradas=$censuradas)", admitidas >= 1)
        for (i in 1 until errores.size) {
            assertTrue(
                "El error debe acercarse de forma monótona: ${errores[i - 1]} → ${errores[i]}",
                errores[i] <= errores[i - 1] + 1e-12,
            )
        }
        assertTrue("El error final (${errores.last()}) debe ser menor que el inicial (${errores.first()})", errores.last() < errores.first())
        assertTrue("La convergencia debe quedar cerca de τ verdadero (error=${errores.last()})", errores.last() <= 0.10)
        val mediaFinal = cache.parameterStates.getValue(claveCache).mean
        assertTrue(
            "La media posterior (τ=${exp(mediaFinal)}) debe acercarse a τ verdadero ($tauVerdadero)",
            abs(mediaFinal - lnVerdadero) < abs(AugeAdaptiveCacheMigration.prior(claveCache).mean - lnVerdadero),
        )
    }
}
