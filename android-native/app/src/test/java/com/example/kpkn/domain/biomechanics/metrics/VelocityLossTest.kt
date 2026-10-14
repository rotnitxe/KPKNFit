package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.vbt.VbtTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * La pérdida de velocidad de la sentadilla, sin disco y con disco (VT3b). La sentadilla sin disco es el caso difícil: el punto de
 * barra es el hombro, y la duración de una sola concéntrica no se conoce mejor que lo que dejan el ruido de los puntos y el
 * tamaño del atleta en el cuadro. Por eso se mide con 9 semillas y se mira la mediana, en una escena que cumple lo que pide el
 * encuadre (el atleta ocupa ≥ 40 % del alto: cámara a 1,7 m) y con un ruido de 0,002 FH (≈ 2,6 px a 1280 px de alto).
 */
class VelocityLossTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.1f", x)

    private val seeds = (1L..9L).toList()

    /** Cámara a 1,7 m: el atleta ocupa el 41 % del alto del fotograma. */
    private val close = SynthCamera(distanceM = 1.7)

    private fun noise(sigmaFh: Double) = SynthNoise(sigmaFh = sigmaFh, outlierRate = 0.01, dropRate = 0.01, ptsJitterMs = 3.0, farSideVisibility = 0.3f)

    private fun squatScript(conSeconds: List<Double>): List<SynthSegment> =
        listOf<SynthSegment>(SynthSegment.Idle(2.0)) + conSeconds.map { SynthSegment.Rep(1.5, 0.5, it, 0.5, 1.0) } + SynthSegment.Idle(2.0)

    private class Outcome(val report: SetTechniqueReport, val durationError: Double?) {
        val loss: Double? get() = report.velocityLossPct
    }

    private fun durationErrorOf(o: Outcome): Double? = o.durationError

    private fun analyze(conSeconds: List<Double>, seed: Long, withPlate: Boolean, camera: SynthCamera, sigmaFh: Double): Outcome {
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, squatScript(conSeconds), camera = camera, noise = noise(sigmaFh), fps = 30.0, seed = seed)
        val bar = if (withPlate) {
            val depth = VbtTestSupport.leftWristDepthM(camera)
            VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, 450, VbtTestSupport.radiusPx(450, depth, camera), noisePx = 0.5, seed = seed)
        } else {
            null
        }
        val profile = RepTestSupport.profile(AnalysisProfileId.SQUAT, plate = withPlate)
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, bar, profile, null, null, emptySet()))
        val error = if (withPlate) null else MetricsTestSupport.scene(r, profile)?.let { RelativeVelocity.compute(it.ctx).durationError }
        return Outcome(report, error)
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else 0.5 * (s[s.size / 2 - 1] + s[s.size / 2])
    }

    private val zeroLoss = List(6) { 1.5 }

    /** CON de 1,0 a 1,5 s: la velocidad pasa de 1/1,0 a 1/1,5, es decir, una pérdida real del 33,3 %. */
    private val thirdLoss = listOf(1.0, 1.1, 1.2, 1.3, 1.4, 1.5)

    @Test
    fun aSquatWithoutAPlateAndWithoutRealLossReadsAboutZero() {
        val fails = RepTestSupport.Failures()
        val outcomes = seeds.map { analyze(zeroLoss, it, withPlate = false, camera = close, sigmaFh = 0.002) }
        val losses = outcomes.map { it.loss }
        println(
            "[pérdida] sentadilla sin disco, pérdida real 0 %: ${losses.map { fmt(it) }} % | conteos ${outcomes.map { it.report.repCount }} | " +
                "unidad ${outcomes.first().report.velocityUnit} | error esperado de la duración ${outcomes.map { fmt(it.durationError?.times(100.0)) }} %",
        )
        fails.check(outcomes.all { it.report.repCount == 6 }) { "conteos ${outcomes.map { it.report.repCount }}" }
        val given = losses.filterNotNull()
        fails.check(given.size >= 7) { "la pérdida debería darse en la mayoría de las semillas: ${losses.map { fmt(it) }}" }
        if (given.isNotEmpty()) {
            val med = median(given.map { abs(it) })
            fails.check(med <= 3.0) { "mediana de |VL| = $med % (esperada ≤ 3)" }
            fails.check(given.all { abs(it) <= 7.0 }) { "alguna semilla se va de ±7 %: ${given.map { fmt(it) }}" }
        }
        // Con ruido de sobra para las barras, siguen estando aunque la pérdida no se dé.
        for (o in outcomes) {
            fails.check(MetricKey.VELOCITY_RELATIVE in o.report.available) { "VELOCITY_RELATIVE ${MetricKey.VELOCITY_RELATIVE in o.report.available} con VL ${o.loss}" }
        }
        fails.assertNone()
    }

    @Test
    fun aSquatWithoutAPlateThatLosesAThirdOfItsVelocityReadsAboutAThird() {
        val fails = RepTestSupport.Failures()
        val outcomes = seeds.map { analyze(thirdLoss, it, withPlate = false, camera = close, sigmaFh = 0.002) }
        val losses = outcomes.map { it.loss }
        println(
            "[pérdida] sentadilla sin disco, pérdida real 33,3 %: ${losses.map { fmt(it) }} % | conteos ${outcomes.map { it.report.repCount }} | " +
                "error esperado de la duración ${outcomes.map { fmt(it.durationError?.times(100.0)) }} %",
        )
        fails.check(outcomes.all { it.report.repCount == 6 }) { "conteos ${outcomes.map { it.report.repCount }}" }
        val given = losses.filterNotNull()
        fails.check(given.size >= 7) { "la pérdida debería darse en la mayoría de las semillas: ${losses.map { fmt(it) }}" }
        if (given.isNotEmpty()) {
            val med = median(given)
            fails.check(abs(med - 100.0 / 3.0) <= 4.0) { "mediana de la VL = $med % (esperada 33 ± 4)" }
            fails.check(given.all { abs(it - 100.0 / 3.0) <= 9.0 }) { "alguna semilla se va de 33 ± 9 %: ${given.map { fmt(it) }}" }
        }
        fails.assertNone()
    }

    @Test
    fun aSquatWithACalibratedPlateAndWithoutRealLossReadsZeroOnEverySeed() {
        val fails = RepTestSupport.Failures()
        val outcomes = seeds.map { analyze(zeroLoss, it, withPlate = true, camera = close, sigmaFh = 0.002) }
        val losses = outcomes.map { it.loss }
        println("[pérdida] sentadilla con disco, pérdida real 0 %: ${losses.map { fmt(it) }} % | conteos ${outcomes.map { it.report.repCount }} unidad ${outcomes.first().report.velocityUnit}")
        fails.check(outcomes.all { it.report.repCount == 6 && it.report.velocityUnit == VelocityUnit.MPS }) { "conteos ${outcomes.map { it.report.repCount }}, unidad ${outcomes.map { it.report.velocityUnit }}" }
        for ((i, loss) in losses.withIndex()) {
            fails.check(loss != null && abs(loss) <= 3.0) { "semilla ${seeds[i]}: VL $loss % (esperada |VL| ≤ 3)" }
        }
        fails.assertNone()
    }

    @Test
    fun aSquatWithACalibratedPlateThatLosesAThirdOfItsVelocityReadsAThirdOnEverySeed() {
        val fails = RepTestSupport.Failures()
        val outcomes = seeds.map { analyze(thirdLoss, it, withPlate = true, camera = close, sigmaFh = 0.002) }
        val losses = outcomes.map { it.loss }
        println("[pérdida] sentadilla con disco, pérdida real 33,3 %: ${losses.map { fmt(it) }} % | conteos ${outcomes.map { it.report.repCount }} MCV de la primera ${outcomes.first().report.reps.map { r -> fmt(r.mcv?.times(100.0)) }}")
        fails.check(outcomes.all { it.report.repCount == 6 }) { "conteos ${outcomes.map { it.report.repCount }}" }
        for ((i, loss) in losses.withIndex()) {
            fails.check(loss != null && abs(loss - 100.0 / 3.0) <= 4.0) { "semilla ${seeds[i]}: VL $loss % (esperada 33 ± 4)" }
        }
        fails.assertNone()
    }

    @Test
    fun withTheDefaultNoiseOfTheGeneratorTheLossOfASingleRepIsNotKnownBetterThanAFewPercent() {
        // El generador por defecto (atleta al 23 % del alto, 0,004 FH ≈ 5 px de ruido por punto) deja la duración de una concéntrica con
        // un error de ~5 % (cota de Cramér–Rao: 4,3 %), y la «mejor» de seis repeticiones sube con ese ruido: la pérdida real 0 sale
        // entre 0 y 20 %. La guarda tiene que verlo (el ruido se mide en la propia concéntrica) y no dar la pérdida.
        val defaults = seeds.map { analyze(zeroLoss, it, withPlate = false, camera = SynthCamera(), sigmaFh = 0.004) }
        val errors = defaults.map { o -> durationErrorOf(o) }
        println("[pérdida] ruido por defecto (3 m, 0,004 FH), pérdida real 0 %: ${defaults.map { fmt(it.loss) }} % | error esperado de la duración ${errors.map { fmt(it?.times(100.0)) }} % | VELOCITY_RELATIVE ${defaults.map { MetricKey.VELOCITY_RELATIVE in it.report.available }}")
        val withheld = defaults.count { it.loss == null }
        println("[pérdida] retenidas por ruido: $withheld de ${defaults.size}")
        // Con el atleta al 23 % del cuadro y 5 px de ruido por punto, el error de la duración de una concéntrica (≈ 3-4 %) pasa del 2,5 %:
        // la pérdida no se da en la mayoría de las semillas, y la que se da nunca es absurda. Las velocidades relativas (±5 %) sí valen.
        assertTrue("retenidas $withheld de ${defaults.size}", withheld >= 6)
        for (o in defaults) assertTrue("VL ${o.loss}", o.loss == null || abs(o.loss!!) <= 15.0)
        for (o in defaults) assertTrue("el error de la duración ${o.durationError} no pasa del 10 %: hay velocidad relativa", MetricKey.VELOCITY_RELATIVE in o.report.available)
    }

    @Test
    fun theLossNeedsLessNoiseThanTheBarsAndTheFirstRepsToAgree() {
        val tuning = AnalysisTuning.DEFAULT
        val decline = listOf(1.00, 0.95, 0.90, 0.84, 0.77)
        // Una señal limpia: la pérdida se da (1,00 → 0,77 = 23 %), también cuando el cambio es brusco y real (cuatro repeticiones medidas
        // con un error del 0,5 %, aunque la tercera ya sea un 17 % más lenta que la primera).
        val clean = VelocityLoss.of(decline, 0.005, tuning)
        assertTrue(clean.reliable)
        assertEquals(23.0, clean.lossPct!!, 1e-9)
        val abrupt = VelocityLoss.of(listOf(1.0, 1.0, 0.833, 0.714), 0.005, tuning)
        assertTrue("un cambio brusco pero bien medido se da: ${abrupt.lossPct}", abrupt.reliable && abrupt.lossPct != null)
        // Una señal ruidosa: con un error esperado de la duración del 5,5 % (más del 2,5 %) la pérdida no se da, sea cual sea la bajada,
        // pero las velocidades relativas siguen valiendo (hasta el 10 %).
        val noisy = VelocityLoss.of(decline, 0.055, tuning)
        assertTrue(noisy.reliable)
        assertNull(noisy.lossPct)
        assertEquals(0.055, noisy.durationError!!, 1e-12)
        // Con más del 10 % no vale ni una cosa ni la otra.
        val hopeless = VelocityLoss.of(decline, 0.12, tuning)
        assertTrue(!hopeless.reliable)
        assertNull(hopeless.lossPct)
        // Con menos de 3 repeticiones tampoco hay pérdida, pero un ruido enorme sigue quitando las velocidades relativas.
        assertTrue(!VelocityLoss.of(listOf(1.0, 0.9), 0.12, tuning).reliable)
        // Sin estimación del ruido no se bloquea (solo cuentan las primeras repeticiones).
        assertTrue(VelocityLoss.of(decline, null, tuning).reliable)
        // Las tres primeras repeticiones no deben diferir más de un 25 % (CV): 1,0, 0,5 y 1,0 delatan una medida rota.
        val broken = VelocityLoss.of(listOf(1.0, 0.5, 1.0, 0.9, 0.8), 0.005, tuning)
        assertTrue(!broken.reliable)
        assertNull(broken.lossPct)
        // Con menos de 3 repeticiones no hay pérdida, pero tampoco hay nada que juzgar: no es «no fiable».
        val two = VelocityLoss.of(listOf(1.0, 0.8), 0.005, tuning)
        assertTrue(two.reliable)
        assertNull(two.lossPct)
    }

    @Test
    fun theExpectedDurationErrorFollowsTheCramerRaoBoundOfTheMinimumJerkFit() {
        // (La parte del ruido medido en la concéntrica está en la prueba siguiente.)
        // Con el atleta al 23 % del cuadro, 0,004 FH de ruido (0,017 BH), un recorrido de 0,33 BH, 1,5 s a 30 fps: ≈ 5,1 %
        // (la cota de Cramér–Rao es 4,3 %; el ajuste real se queda un 20 % por encima, y κ = 6,6 lo recoge).
        val tuning = AnalysisTuning.DEFAULT
        val noisy = VelocityLoss.durationError(0.017, 0.33, 30.0, 1.5, tuning)!!
        assertEquals(0.0507, noisy, 0.002)
        // Cerca (41 %): 0,002 FH = 0,0048 BH → ≈ 1,4 %.
        val close = VelocityLoss.durationError(0.0048, 0.33, 30.0, 1.5, tuning)!!
        assertEquals(0.0143, close, 0.001)
        assertNull(VelocityLoss.durationError(0.01, 0.0, 30.0, 1.5, tuning))
        assertNull(VelocityLoss.durationError(Double.NaN, 0.33, 30.0, 1.5, tuning))
    }

    @Test
    fun theFitNoiseRecoversTheWhiteNoiseOfAMinimumJerkRiseFromItsOwnResidual() {
        // Una subida de 0,33 que sale en el índice 60 y dura 45 fotogramas (1,5 s a 30 fps), con ruido blanco de 0,017 (el del
        // generador por defecto en alturas corporales): 40 semillas, la media del ruido medido cae a menos de un 8 % de la verdad.
        val sigma = 0.017
        val estimates = (1L..40L).map { seed ->
            val rng = java.util.Random(seed)
            val x = DoubleArray(200) { i -> 0.10 + 0.33 * VelocityWindow.profile((i - 60.0) / 45.0) + sigma * rng.nextGaussian() }
            VelocityLoss.fitNoise(x, 60.0, 105.0, 30.0, 0.20)
        }
        val mean = estimates.average()
        println("[pérdida] ruido recuperado del ajuste: media $mean (verdad $sigma), mínimo ${estimates.min()}, máximo ${estimates.max()}")
        assertEquals(sigma, mean, 0.08 * sigma)
        // Con la forma exacta y sin ruido no queda residuo, y un tramo de una muestra no se puede juzgar.
        val clean = DoubleArray(200) { 0.10 + 0.33 * VelocityWindow.profile((it - 60.0) / 45.0) }
        assertEquals(0.0, VelocityLoss.fitNoise(clean, 60.0, 105.0, 30.0, 0.20), 1e-9)
        assertTrue(VelocityLoss.fitNoise(clean, 60.0, 61.0, 30.0, 0.20).isNaN())
        // Un ruido de baja frecuencia (el blanco promediado de tres en tres) también se ve: el ajuste mide lo que le afecta, no solo
        // lo agudo. La razón entre el ruido medido y la desviación real de ese ruido sale cerca de 1 (20 semillas).
        val ratios = (1L..20L).map { seed ->
            val rng = java.util.Random(seed)
            val white = DoubleArray(200) { sigma * rng.nextGaussian() }
            val slow = DoubleArray(200) { i -> (white[maxOf(0, i - 1)] + white[i] + white[minOf(199, i + 1)]) / 3.0 * 1.7 }
            val x = DoubleArray(200) { 0.10 + 0.33 * VelocityWindow.profile((it - 60.0) / 45.0) + slow[it] }
            VelocityLoss.fitNoise(x, 60.0, 105.0, 30.0, 0.20) / sqrtOf(slow)
        }
        println("[pérdida] ruido de baja frecuencia: razón medida/real ${ratios.average()}")
        assertTrue("el ruido de baja frecuencia debería medirse: ${ratios.average()}", ratios.average() > 0.75)
    }

    private fun sqrtOf(v: DoubleArray): Double {
        val mean = v.average()
        return Math.sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
    }

    @Test
    fun theWindowSpeedOfAMinimumJerkRiseIsOnePointFiveEightTimesItsMean() {
        // 0,8·ΔL/(t90 − t10) con τ10 = 0,2466 y τ90 = 0,7534: 0,8/0,5067 = 1,5788.
        val factor = VelocityWindow.speed(1.0, 1.0, 0.10, 0.90)
        assertEquals(1.5788, factor, 0.001)
        // Proporcional al desplazamiento e inversa a la duración.
        assertEquals(2.0 * factor, VelocityWindow.speed(2.0, 1.0, 0.10, 0.90), 1e-9)
        assertEquals(0.5 * factor, VelocityWindow.speed(1.0, 2.0, 0.10, 0.90), 1e-9)
    }
}
