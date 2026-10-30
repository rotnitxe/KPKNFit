package com.example.kpkn.ui.design.motion

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Motor de fotogramas clave de `ui/design/motion` sin Android: la semántica de CSS (curva en CADA intervalo, retardo, relleno,
 * repeticiones, dirección), el catálogo y el algoritmo del campo de ondas contra valores sacados de `cvWaveD` del lienzo con Node.
 */
class KpknMotionEngineTest {

    /** Cubic-bezier de CSS resuelta por bisección, independiente de la de Compose. */
    private fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(80) {
            val mid = (lo + hi) / 2
            val x = 3 * (1 - mid) * (1 - mid) * mid * x1 + 3 * (1 - mid) * mid * mid * x2 + mid * mid * mid
            if (x < t) lo = mid else hi = mid
        }
        val s = (lo + hi) / 2
        return 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s
    }

    private fun estandar(t: Double) = bezier(0.2, 0.8, 0.2, 1.0, t)

    private fun pose(animation: KpknAnimation, timeMs: Float, reduced: Boolean = false): KpknPose =
        KpknPose().also { animation.poseAt(timeMs, it, reduced) }

    @Test
    fun catalogHasTheFixedCounts() {
        assertEquals(14, KpknMotion.curves.size)
        assertEquals(23, KpknMotion.durations.size)
        assertEquals(11, KpknMotion.delays.size)
        assertEquals(33, KpknFamilies.all.size)
        assertEquals(33, KpknFamilies.all.map { it.name }.toSet().size)
    }

    @Test
    fun curvesMatchAnIndependentCssSolver() {
        val beziers = mapOf(
            "estandar" to doubleArrayOf(0.2, 0.8, 0.2, 1.0),
            "suave" to doubleArrayOf(0.25, 0.1, 0.25, 1.0),
            "trazo" to doubleArrayOf(0.3, 0.7, 0.2, 1.0),
            "salida" to doubleArrayOf(0.0, 0.0, 0.58, 1.0),
            "resorte" to doubleArrayOf(0.3, 1.5, 0.5, 1.0),
            "vaiven" to doubleArrayOf(0.42, 0.0, 0.58, 1.0),
            "cae" to doubleArrayOf(0.5, 0.0, 0.9, 0.5),
            "acelera" to doubleArrayOf(0.4, 0.0, 1.0, 1.0),
            "material" to doubleArrayOf(0.4, 0.0, 0.2, 1.0),
            "resorte-fuerte" to doubleArrayOf(0.3, 1.6, 0.5, 1.0),
            "ruta" to doubleArrayOf(0.5, 0.0, 0.2, 1.0),
            "resorte-suave" to doubleArrayOf(0.22, 1.12, 0.36, 1.0),
        )
        for ((name, b) in beziers) {
            val easing = KpknMotion.curve(name).easing
            for (i in 1..19) {
                val t = i / 20.0
                assertEquals("$name en $t", bezier(b[0], b[1], b[2], b[3], t), easing.transform(t.toFloat()).toDouble(), 2e-3)
            }
        }
        // Los muelles rebasan de verdad: el máximo supera 1.
        assertTrue((1..99).maxOf { KpknMotion.Resorte.transform(it / 100f) } > 1.07f)
        assertTrue((1..99).maxOf { KpknMotion.ResorteFuerte.transform(it / 100f) } > 1.10f)
        assertTrue((1..99).maxOf { KpknMotion.ResorteSuave.transform(it / 100f) } > 1.003f)
        // steps(1): todo o nada.
        assertEquals(0f, KpknMotion.Parpadeo.transform(0.999f), 0f)
        assertEquals(1f, KpknMotion.Parpadeo.transform(1f), 0f)
    }

    @Test
    fun cssAppliesTheCurveToEveryIntervalNotToTheWholeAnimation() {
        // lv-pop: 0 % scale .88 · 60 % scale 1.14 · 100 % scale 1, curva `estandar` en CADA intervalo.
        val anim = KpknFamilies.Rebote.demo
        val firstHalf = estandar(0.5)
        // t = 60 ms de 200 = 30 % = mitad del primer intervalo.
        assertEquals(0.88 + (1.14 - 0.88) * firstHalf, pose(anim, 60f).scaleX.toDouble(), 2e-3)
        // t = 160 ms = 80 % = mitad del segundo intervalo.
        assertEquals(1.14 + (1.0 - 1.14) * firstHalf, pose(anim, 160f).scaleX.toDouble(), 2e-3)
        // En el fotograma clave del 60 % vale exactamente 1,14.
        assertEquals(1.14f, pose(anim, 120f).scaleX, 1e-4f)
        // Una sola curva sobre todo el recorrido daría otro valor en el 30 %: la prueba distingue las dos lecturas.
        val wholeCurve = 0.88 + (1.14 - 0.88) * (estandar(0.3) / 1.0) // aproximación errónea
        assertNotEquals(wholeCurve, pose(anim, 60f).scaleX.toDouble(), 1e-3)
    }

    @Test
    fun keyframeLevelTimingFunctionRulesTheIntervalThatStartsThere() {
        // La carrera de la hoja de curvas: ida con curva, espera lineal, vuelta con la misma curva.
        val frames = kpknKeyframes("ride", "") {
            at(0f, KpknMotion.Estandar) { translateX(0f) }
            at(0.4f, KpknMotion.Lineal) { translateX(200f) }
            at(0.6f, KpknMotion.Estandar) { translateX(200f) }
            at(0.8f) { translateX(0f) }
            at(1f) { translateX(0f) }
        }
        val anim = KpknAnimation(frames, KpknDuration(1000), KpknMotion.Lineal, iterations = KpknAnimation.INFINITE)
        assertEquals(200 * estandar(0.5), pose(anim, 200f).translateXDp.toDouble(), 0.2)
        assertEquals(200f, pose(anim, 500f).translateXDp, 1e-3f) // espera
        assertEquals(200 - 200 * estandar(0.5), pose(anim, 700f).translateXDp.toDouble(), 0.2) // vuelta, misma curva
        assertEquals(0f, pose(anim, 900f).translateXDp, 1e-3f)
        assertEquals(200 * estandar(0.5), pose(anim, 1200f).translateXDp.toDouble(), 0.2) // segunda vuelta
    }

    @Test
    fun delayAndFillModesFollowCss() {
        // lxrise2: el estado inicial lo escribe el elemento (alfa 0, 12 dp abajo) y el relleno es `forwards`, con retardo de 700 ms.
        val implicita = KpknFamilies.EntradaImplicita.demo
        assertEquals(0f, pose(implicita, 0f).alpha, 0f)
        assertEquals(12f, pose(implicita, 600f).translateYDp, 0f)
        assertFalse(pose(implicita, 600f).active)
        assertEquals(0f, pose(implicita, 700f).alpha, 1e-4f)
        assertEquals(1f, pose(implicita, 1200f).alpha, 1e-4f)
        assertEquals(1f, pose(implicita, 9000f).alpha, 0f)
        assertEquals(0f, pose(implicita, 9000f).translateYDp, 0f)

        // cv-rise: `both`: ya aplica el 0 % durante el retardo y se queda en el 100 %.
        val sube = KpknFamilies.EntradaSube.demo.copy(delay = KpknDuration(300))
        assertEquals(0f, pose(sube, 100f).alpha, 0f)
        assertEquals(14f, pose(sube, 100f).translateYDp, 0f)
        assertTrue(pose(sube, 100f).active)
        assertEquals(1f, pose(sube, 5000f).alpha, 0f)

        // lv-pop con `none`: fuera de su tiempo vuelve al estado base (escala 1, sin efecto).
        val rebote = KpknFamilies.Rebote.demo
        assertEquals(1f, pose(rebote, 5000f).scaleX, 0f)
        assertFalse(pose(rebote, 5000f).active)

        // cv-ignite: ignición con 1100 ms de retardo y `both`: sombra transparente antes, anillo de 10 dp al 40 %.
        val ignicion = KpknFamilies.Ignicion.demo
        assertEquals(0f, pose(ignicion, 0f)[KpknChannel.ShadowAlpha], 0f)
        assertEquals(10f, pose(ignicion, 1100f + 0.4f * 800f)[KpknChannel.ShadowSpreadDp], 1e-3f)
        assertEquals(0.3f, pose(ignicion, 1100f + 0.4f * 800f)[KpknChannel.ShadowAlpha], 1e-4f)
        assertEquals(22f, pose(ignicion, 9000f)[KpknChannel.ShadowBlurDp], 1e-3f)
    }

    @Test
    fun infiniteLoopsRepeatWithTheExactPeriod() {
        val latido = KpknFamilies.Latido.demo
        assertEquals(1100f, latido.periodMs, 0f)
        for (t in listOf(0f, 120f, 495f, 700f, 1099f)) {
            val a = pose(latido, t)
            val b = pose(latido, t + 1100f * 7)
            assertEquals(a.alpha, b.alpha, 1e-3f)
            assertEquals(a.scaleX, b.scaleX, 1e-3f)
        }
        assertEquals(0.34f, pose(latido, 0f).alpha, 1e-4f)
        assertEquals(0.18f, pose(latido, 1100f * 0.45f).alpha, 1e-4f)

        // blink (`steps(1)`): opaco hasta el 50 %, transparente hasta el final de la vuelta, opaco otra vez al empezar la siguiente.
        val pulso = KpknFamilies.Pulso.demo
        assertEquals(1f, pose(pulso, 499f).alpha, 0f)
        assertEquals(0f, pose(pulso, 500f).alpha, 0f)
        assertEquals(0f, pose(pulso, 999f).alpha, 0f)
        assertEquals(1f, pose(pulso, 1000f).alpha, 0f)
    }

    @Test
    fun directionAlternateReversesOddIterations() {
        val anim = KpknFamilies.Rebote.demo.copy(iterations = 2f, direction = KpknDirection.Alternate)
        val forward = pose(anim, 60f).scaleX
        val backward = pose(anim, 200f + (200f - 60f)).scaleX
        assertEquals(forward, backward, 1e-4f)
        val reverse = KpknFamilies.EntradaSube.demo.copy(direction = KpknDirection.Reverse)
        assertEquals(1f, pose(reverse, 0f).alpha, 0f)
    }

    @Test
    fun reducedMotionShowsTheFinalStateOrTheBaseState() {
        assertEquals(1f, pose(KpknFamilies.EntradaSube.demo, 0f, reduced = true).alpha, 0f)
        assertEquals(0f, pose(KpknFamilies.EntradaSube.demo, 0f, reduced = true).translateYDp, 0f)
        val latido = pose(KpknFamilies.Latido.demo, 300f, reduced = true)
        assertFalse(latido.active)
        assertEquals(1f, latido.alpha, 0f)
        assertEquals(0f, pose(KpknFamilies.FundidoSalida.demo, 0f, reduced = true).alpha, 0f)
    }

    @Test
    fun implicitKeyframesUseTheBaseValue() {
        // cv-pop: la opacidad se escribe en 0 % y 60 %; en el 100 % vuelve al valor base (1) con la curva `suave`.
        val anim = KpknFamilies.ReboteAparece.demo
        assertEquals(1f, pose(anim, 350f).alpha, 1e-4f)
        assertEquals(1f, pose(anim, 350f * 0.8f).alpha, 1e-3f)
        assertEquals(0f, pose(anim, 0f).alpha, 0f)
        // drawln: el trazo parte de dashoffset 1 (100 de 100) y llega a 0.
        val trazo = KpknFamilies.DibujoTrazo.demo
        assertEquals(1f, pose(trazo, 0f)[KpknChannel.DashOffset], 0f)
        assertEquals(0f, pose(trazo, 1600f)[KpknChannel.DashOffset], 1e-5f)
    }

    @Test
    fun countUpFormatsAndEasesLikeTheCanvas() {
        assertEquals("18.420", kpknFormatCount(18420.0))
        assertEquals("1.247", kpknFormatCount(1247.0))
        assertEquals("82,5", kpknFormatCount(82.5, 1))
        assertEquals("164,8", kpknFormatCount(164.8, 1))
        assertEquals("0", kpknFormatCount(0.0))
        assertEquals(87.5, kpknCountValue(100.0, 550f), 1e-9)
        assertEquals(100.0, kpknCountValue(100.0, 1100f), 1e-9)
        assertEquals(100.0, kpknCountValue(100.0, 9000f), 1e-9)
        assertEquals(0.0, kpknCountValue(100.0, 0f), 1e-9)
    }

    @Test
    fun cssColorMixIsPremultipliedSrgb() {
        val mid = kpknCssColorLerp(Color.Transparent, Color(0xFFF2EEE6), 0.5f)
        assertEquals(0.5f, mid.alpha, 5e-3f)
        // El color no se oscurece al mezclar con transparente: sigue siendo el crema.
        assertEquals(0xF2 / 255f, mid.red, 1e-3f)
        val a = kpknCssColorLerp(Color(0xFFF49A6E), Color(0xFFC4BFB6), 0.5f)
        assertEquals((0xF4 + 0xC4) / 2f / 255f, a.red, 1e-3f)
    }

    // Valores de `cvWaveD` del lienzo (src/scenes-cv.js) evaluados con Node: estado, líneas, alto, instante, pulso, línea, punto, y.
    private data class Golden(val state: KpknWaveState, val lines: Int, val h: Float, val t: Double, val hr: Double, val k: Int, val i: Int, val y: Double)

    @Test
    fun waveFieldReproducesTheCanvasAlgorithm() {
        val golden = listOf(
            Golden(KpknWaveState.Work, 12, 112f, 2.0, 0.0, 0, 0, 41.24118792800676),
            Golden(KpknWaveState.Work, 12, 112f, 2.0, 0.0, 3, 20, 47.534698816162276),
            Golden(KpknWaveState.Work, 12, 112f, 2.0, 0.0, 11, 36, 94.34551701917624),
            Golden(KpknWaveState.Work, 12, 112f, 2.0, 0.0, 8, 50, 69.53464921335606),
            Golden(KpknWaveState.Work, 12, 112f, 2.0, 0.0, 5, 72, 72.13823391012582),
            Golden(KpknWaveState.Above, 18, 292f, 3.37, 142.0, 0, 0, 103.6240804346555),
            Golden(KpknWaveState.Above, 18, 292f, 3.37, 142.0, 3, 20, 123.01458504039196),
            Golden(KpknWaveState.Above, 18, 292f, 3.37, 142.0, 17, 36, 180.128255558849),
            Golden(KpknWaveState.Above, 18, 292f, 3.37, 142.0, 12, 50, 206.77980468389373),
            Golden(KpknWaveState.Above, 18, 292f, 3.37, 142.0, 5, 72, 153.92536736553527),
            Golden(KpknWaveState.Idle, 12, 112f, 2.4, 0.0, 0, 0, 40.31207498171663),
            Golden(KpknWaveState.Idle, 12, 112f, 2.4, 0.0, 8, 50, 87.1426942794345),
            Golden(KpknWaveState.Warm, 24, 560f, 0.77, 96.0, 3, 20, 241.50042605986778),
            Golden(KpknWaveState.Warm, 24, 560f, 0.77, 96.0, 23, 36, 534.3841322540608),
            Golden(KpknWaveState.Warm, 24, 560f, 0.77, 96.0, 15, 50, 407.2357718645284),
        )
        for (g in golden) {
            val ys = kpknWaveLines(g.h, g.state.params, g.lines, g.t, g.hr)
            assertEquals(g.lines, ys.size)
            assertEquals(73, ys[g.k].size)
            assertEquals("${g.state} k=${g.k} i=${g.i}", g.y, ys[g.k][g.i].toDouble(), 0.01)
        }
    }

    @Test
    fun waveDynamicsApproachTheTargetAndAccumulatePhaseOnlyWhenMoving() {
        val still = KpknWaveDynamics(KpknWaveState.Idle.params)
        repeat(60) { still.step(1 / 60.0, KpknWaveState.Idle.params) }
        assertEquals(0.0, still.tAcc, 0.0)
        val moving = KpknWaveDynamics(KpknWaveState.Work.params)
        repeat(60) { moving.step(1 / 60.0, KpknWaveState.Work.params) }
        assertEquals(1.0, moving.tAcc, 1e-6)
        // El salto grande de dt se recorta a 50 ms.
        val clipped = KpknWaveDynamics(KpknWaveState.Work.params)
        clipped.step(5.0, KpknWaveState.Work.params)
        assertEquals(0.05, clipped.tAcc, 1e-9)
        // De Trabajo a Pausa: la velocidad baja hacia 0 y la amplitud hacia la de pausa.
        val toPause = KpknWaveDynamics(KpknWaveState.Work.params)
        repeat(240) { toPause.step(1 / 60.0, KpknWaveState.Pause.params) }
        assertEquals(KpknWaveState.Pause.params.amp, toPause.current().amp, 0.02)
    }
}
