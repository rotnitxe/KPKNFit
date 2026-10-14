package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Las piezas de la visión del disco por separado: gradientes, ajustes, Kalman, Hough y NCC. */
class PlateVisionPartsTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.3f", x)

    // ------------------------------------------------------------------------------------------------------------
    // CircleFit
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun kasaRecoversACircleExactlyAndIgnoresDeadPoints() {
        val n = 90
        val x = DoubleArray(n + 5)
        val y = DoubleArray(n + 5)
        for (i in 0 until n) {
            val t = 2.0 * PI * i / n
            x[i] = 123.4 + 37.5 * cos(t)
            y[i] = 456.7 + 37.5 * sin(t)
        }
        // Cinco puntos basura marcados como muertos.
        for (i in n until n + 5) {
            x[i] = 900.0
            y[i] = -50.0
        }
        val alive = BooleanArray(n + 5) { it < n }
        val out = DoubleArray(3)
        assertTrue(CircleFit.kasa(x, y, alive, n + 5, out))
        assertEquals(123.4, out[0], 1e-9)
        assertEquals(456.7, out[1], 1e-9)
        assertEquals(37.5, out[2], 1e-9)
        // Tres puntos alineados no definen un círculo.
        assertFalse(CircleFit.kasa(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 1.0, 2.0), null, 3, out))
    }

    @Test
    fun theAxisAlignedEllipseGivesTheSemiAxesWithAFixedCentreAndTheCentreWithAFreeOne() {
        val cx = 200.0
        val cy = 300.0
        val a = 42.5
        val b = 50.0
        val n = 120
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        for (i in 0 until n) {
            val t = 2.0 * PI * i / n
            x[i] = cx + a * cos(t)
            y[i] = cy + b * sin(t)
        }
        val out = DoubleArray(4)
        assertTrue(CircleFit.ellipseFixedCenter(x, y, null, n, cx, cy, out))
        assertEquals(a, out[0], 1e-6)
        assertEquals(b, out[1], 1e-6)

        // Con el centro libre y solo la mitad de los arcos (dos oclusores): el centro y los semiejes salen igual.
        val alive = BooleanArray(n) { i -> val t = 360.0 * i / n; !(t in 20.0..110.0 || t in 200.0..290.0) }
        assertTrue(CircleFit.ellipseFit(x, y, alive, n, cx + 3.0, cy - 2.0, 6, out))
        println("[elipse] centro libre con arcos sueltos: centro (${fmt(out[0])}, ${fmt(out[1])}) a=${fmt(out[2])} b=${fmt(out[3])}")
        assertEquals(cx, out[0], 0.05)
        assertEquals(cy, out[1], 0.05)
        assertEquals(a, out[2], 0.05)
        assertEquals(b, out[3], 0.05)
        // Y la distancia con signo: dentro negativa, fuera positiva; con a = b es |p − c| − r.
        assertEquals(10.0, CircleFit.ellipseDistance(cx + a + 10.0, cy, cx, cy, a, b), 0.2)
        assertEquals(-7.0, CircleFit.ellipseDistance(cx, cy - b + 7.0, cx, cy, a, b), 0.2)
        assertEquals(4.0, CircleFit.ellipseDistance(104.0, 0.0, 0.0, 0.0, 100.0, 100.0), 1e-9)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Kalman
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun theKalmanConvergesToAConstantVelocityAndItsGateRejectsOutliers() {
        val kf = ConstantVelocityKalman()
        val rng = Random(5L)
        kf.reset(100.0, 200.0, 0.0, 0.0, 5.0, 300.0)
        val dt = 1.0 / 30.0
        val vx = 90.0
        val vy = -240.0
        for (k in 1..60) {
            kf.predict(dt, 100.0)
            val zx = 100.0 + vx * dt * k + rng.nextGaussian() * 0.8
            val zy = 200.0 + vy * dt * k + rng.nextGaussian() * 0.8
            kf.update(zx, zy, 0.8)
        }
        println("[kalman] v = (${fmt(kf.vx)}, ${fmt(kf.vy)}) esperada ($vx, $vy), σpos ${fmt(kf.positionSigma)}")
        assertEquals(vx, kf.vx, 8.0)
        assertEquals(vy, kf.vy, 8.0)
        kf.predict(dt, 100.0)
        // Una medida a 3 px de la predicción pasa la compuerta de 9,21; otra a 40 px, no.
        assertTrue(kf.mahalanobis2(kf.x + 2.0, kf.y - 2.0, 0.8) < 9.21)
        assertTrue(kf.mahalanobis2(kf.x + 40.0, kf.y, 0.8) > 9.21)
        // Sin medidas la incertidumbre crece con el tiempo; hacia atrás también.
        val before = kf.positionSigma
        repeat(10) { kf.predict(dt, 100.0) }
        assertTrue(kf.positionSigma > before)
        val back = ConstantVelocityKalman()
        back.reset(0.0, 0.0, 50.0, 0.0, 1.0, 10.0)
        back.predict(-0.5, 100.0)
        assertEquals(-25.0, back.x, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gradientes
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun aVerticalStepGivesAHorizontalGradientOfAboutTwiceItsHeightAtEveryReduction() {
        val w = 120
        val h = 80
        val data = ByteArray(w * h) { i -> if (i % w < 60) 50 else (50 + 80).toByte() }
        val frame = LumaFrame(w, h, data)
        val g = Gradients()
        for (scale in listOf(1, 2, 3, 4)) {
            assertTrue(g.compute(frame, 0, 0, w, h, scale))
            val rw = g.width
            val rh = g.height
            var peak = 0f
            var peakCol = -1
            val row = rh / 2
            for (i in 1 until rw - 1) {
                val m = g.mag[row * rw + i]
                if (m > peak) {
                    peak = m
                    peakCol = i
                }
            }
            // El borde está en x = 60 del fotograma: la columna reducida que lo contiene.
            val expectedCol = 60 / scale
            println("[gradientes] reducción $scale: ${rw}x$rh, pico ${fmt(peak.toDouble())} en la columna $peakCol (borde en ≈ $expectedCol); gy en el borde ${fmt(g.gy[row * rw + peakCol].toDouble())}")
            assertTrue("pico fuera de sitio", abs(peakCol - expectedCol) <= 1)
            assertTrue("pico demasiado bajo: $peak", peak > 120f && peak < 400f)
            assertEquals(0.0, g.gy[row * rw + peakCol].toDouble(), 1e-3)
            // Con un solo borde la mediana del módulo es 0 (casi todo es plano) y el percentil 99 alcanza la mitad del pico.
            assertEquals(0f, g.percentile(50.0), 0f)
            assertTrue("P99 ${g.percentile(99.0)}", g.percentile(99.0) > 0.5f * peak)
        }
        // Una ventana demasiado pequeña no da gradientes.
        assertFalse(g.compute(frame, 0, 0, 10, 10, 4))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hough sobre una escena limpia
    // ------------------------------------------------------------------------------------------------------------

    private fun cleanScene(radius: Double, cx: Double, cy: Double, axisRatio: Double = 1.0): PlateScene {
        val options = SceneOptions(
            radiusPx = radius, axisRatio = axisRatio, contrast = 60.0, polarity = 1, noiseSigma = 2.0, flicker = 0.0,
            rackBars = false, distractor = false,
        )
        val params = SyntheticPlateFrames.params(77L, options)
        return SyntheticPlateFrames.scene(params, StaticPath(cx, cy))
    }

    @Test
    fun theHoughFindsTheCentreAndTheRadiusOfACleanPlateToAFractionOfAPixel() {
        val fails = RepTestSupport.Failures()
        val hough = CircleHough(PlateVisionConfig())
        val out = Array(3) { HoughCandidate() }
        for (radius in listOf(40.0, 64.0, 91.0, 130.0)) {
            val cx = 361.37
            val cy = 640.81
            val scene = cleanScene(radius, cx, cy)
            val frame = scene.frame(0.0, 0)
            val x0 = (cx - 4 * radius).toInt()
            val y0 = (cy - 4 * radius).toInt()
            val n = hough.detect(frame, x0, y0, (cx + 4 * radius).toInt(), (cy + 4 * radius).toInt(), 0.75 * radius, 1.7 * radius, RadiusChoice.OUTERMOST, radius, 1.0, out, 3)
            val c = out[0]
            val err = hypot(c.cx - cx, c.cy - cy)
            println(
                "[hough] R=$radius: $n candidatos; el primero en (${fmt(c.cx)}, ${fmt(c.cy)}) r=${fmt(c.r)} error ${fmt(err)} px, q=${fmt(c.quality)} cobertura ${fmt(c.coverage)} " +
                    "cos ${fmt(c.meanCos)} refinado=${c.refined} a/b ${fmt(c.ratio)}",
            )
            fails.check(n >= 1) { "R=$radius: sin candidatos" }
            fails.check(err <= 0.3) { "R=$radius: error del centro ${fmt(err)} px (≤ 0,3)" }
            fails.check(abs(c.r - radius) <= 0.4) { "R=$radius: radio ${fmt(c.r)}" }
            fails.check(c.quality >= 0.8) { "R=$radius: calidad ${fmt(c.quality)} (≥ 0,8)" }
            fails.check(c.refined) { "R=$radius: sin refinar" }
        }
        fails.assertNone()
    }

    @Test
    fun theHoughMeasuresTheAxisRatioOfATiltedPlateWhenItsExpectedRatioIsWithinThreePercent() {
        val fails = RepTestSupport.Failures()
        val hough = CircleHough(PlateVisionConfig())
        val out = Array(3) { HoughCandidate() }
        // (a/b real, a/b previsto): el exacto y otros a ±3 % (el residuo que deja la rejilla de hipótesis del detector y del rastreador).
        for ((ratio, expected) in listOf(0.94 to 0.94, 0.94 to 0.97, 0.94 to 0.91, 0.85 to 0.85, 0.85 to 0.88, 0.85 to 0.82)) {
            val radius = 80.0
            val cx = 300.0
            val cy = 500.0
            val scene = cleanScene(radius, cx, cy, ratio)
            val frame = scene.frame(0.0, 0)
            val req = scene.params.equivalentRadiusPx
            val n = hough.detect(frame, (cx - 4 * req).toInt(), (cy - 4 * req).toInt(), (cx + 4 * req).toInt(), (cy + 4 * req).toInt(), 0.75 * req, 1.7 * req, RadiusChoice.OUTERMOST, req, expected, out, 3)
            val c = out[0]
            println(
                "[hough] a/b real $ratio, previsto $expected: $n candidatos; a/b medido ${fmt(c.ratio)} (medido=${c.ratioMeasured}), r=${fmt(c.r)} (equivalente ${fmt(req)}), " +
                    "centro (${fmt(c.cx)}, ${fmt(c.cy)}) error ${fmt(hypot(c.cx - cx, c.cy - cy))} px, q=${fmt(c.quality)}",
            )
            val label = "a/b real $ratio previsto $expected"
            fails.check(n >= 1 && c.ratioMeasured) { "$label: no se midió la elipse" }
            fails.check(abs(c.ratio - ratio) <= 0.02) { "$label: medido ${fmt(c.ratio)}" }
            fails.check(abs(c.r - req) / req <= 0.01) { "$label: radio equivalente ${fmt(c.r)} (verdad ${fmt(req)})" }
            fails.check(hypot(c.cx - cx, c.cy - cy) <= 0.4) { "$label: centro (${fmt(c.cx)}, ${fmt(c.cy)})" }
        }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // NCC
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun theNccFindsTheTemplateInAShiftedFrameAndRefusesWhenItIsNotThere() {
        val config = PlateTrackerConfig(plateDiameterMm = 450)
        val radius = 70.0
        val first = cleanScene(radius, 300.0, 600.0)
        val shifted = cleanScene(radius, 300.0 + 23.6, 600.0 - 17.2)
        val ncc = Ncc(config)
        ncc.capture(first.frame(0.0, 0), 300.0, 600.0, radius)
        assertTrue(ncc.hasTemplate)
        assertTrue(ncc.match(shifted.frame(0.0, 1), 300.0, 600.0, 60.0, radius))
        println("[ncc] pico ${fmt(ncc.peak)} segundo ${fmt(ncc.second)} centro (${fmt(ncc.centerX)}, ${fmt(ncc.centerY)}) verdad (323.6, 582.8)")
        assertTrue("pico ${ncc.peak}", ncc.peak >= 0.9)
        assertTrue("segundo ${ncc.second} frente a ${ncc.peak}", ncc.second < 0.9 * ncc.peak)
        assertEquals(323.6, ncc.centerX, 2.0)
        assertEquals(582.8, ncc.centerY, 2.0)
        // Un fotograma sin disco: la correlación no llega a 0,6 (o el segundo pico no queda por debajo de 0,9·pico).
        val empty = SyntheticPlateFrames.scene(
            SyntheticPlateFrames.params(78L, SceneOptions(radiusPx = radius, rackBars = true, distractor = true, noiseSigma = 3.0)),
            StaticPath(-500.0, -500.0),
        ).frame(0.0, 0)
        val found = ncc.match(empty, 300.0, 600.0, 60.0, radius)
        println("[ncc] sin disco: encontrado=$found pico ${fmt(ncc.peak)} segundo ${fmt(ncc.second)}")
        assertTrue(!found || ncc.peak < 0.6 || ncc.second >= 0.9 * ncc.peak)
        ncc.clear()
        assertFalse(ncc.hasTemplate)
    }
}
