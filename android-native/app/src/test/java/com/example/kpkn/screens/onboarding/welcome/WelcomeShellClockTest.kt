package com.example.kpkn.screens.onboarding.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reloj de las escenas: lo que decide qué cuadro se ve es aritmética pura. Aquí se fija que el tiempo de escena
 * siempre cae en [0, periodo), que el bucle vuelve a 0 al cumplir el periodo, que lo que no corre muestra su cuadro
 * representativo (periodo × 0,7) y que el fundido entre ciclos esconde el salto del cuadro final al inicial.
 */
class WelcomeShellClockTest {

    private val period = 14f

    @Test
    fun `sceneTimeAt arranca en cero y avanza con los segundos transcurridos`() {
        assertEquals(0f, sceneTimeAt(0f, period), 0f)
        assertEquals(3.5f, sceneTimeAt(3.5f, period), 1e-6f)
        assertEquals(13.9f, sceneTimeAt(13.9f, period), 1e-4f)
    }

    @Test
    fun `sceneTimeAt vuelve a cero al cumplir el periodo y sigue en bucle`() {
        assertEquals(0f, sceneTimeAt(period, period), 0f)
        assertEquals(0.5f, sceneTimeAt(period + 0.5f, period), 1e-5f)
        assertEquals(13f, sceneTimeAt(period * 2f + 13f, period), 1e-4f)
        assertEquals(0f, sceneTimeAt(period * 7f, period), 0f)
    }

    @Test
    fun `sceneTimeAt siempre devuelve un tiempo valido dentro del periodo`() {
        var elapsed = 0f
        while (elapsed < 400f) {
            val t = sceneTimeAt(elapsed, period)
            assertTrue("t=$t para elapsed=$elapsed", t >= 0f && t < period)
            elapsed += 0.37f
        }
    }

    @Test
    fun `sceneTimeAt tolera entradas invalidas`() {
        assertEquals(0f, sceneTimeAt(-1f, period), 0f)
        assertEquals(0f, sceneTimeAt(Float.NaN, period), 0f)
        assertEquals(0f, sceneTimeAt(Float.POSITIVE_INFINITY, period), 0f)
        assertEquals(0f, sceneTimeAt(5f, 0f), 0f)
        assertEquals(0f, sceneTimeAt(5f, -3f), 0f)
        assertEquals(0f, sceneTimeAt(5f, Float.NaN), 0f)
    }

    @Test
    fun `el cuadro representativo es el 70 por ciento del periodo`() {
        assertEquals(period * 0.7f, representativeSceneTime(period), 1e-5f)
        assertEquals(8.4f, representativeSceneTime(12f), 1e-5f)
        assertEquals(0.7f, WelcomeRepresentativeFraction, 0f)
    }

    @Test
    fun `el fundido del bucle vale cero en los extremos y uno entre los dos fundidos`() {
        assertEquals(0f, loopAlpha(0f, 12f), 0f)
        assertEquals(1f, loopAlpha(WelcomeLoopFadeSeconds, 12f), 1e-6f)
        assertEquals(1f, loopAlpha(6f, 12f), 0f)
        assertEquals(1f, loopAlpha(12f - WelcomeLoopFadeSeconds, 12f), 1e-6f)
        assertTrue(loopAlpha(12f - 0.001f, 12f) < 0.01f)
    }

    @Test
    fun `el fundido sube y baja de forma monotona`() {
        var previous = -1f
        var t = 0f
        while (t <= WelcomeLoopFadeSeconds) {
            val a = loopAlpha(t, 12f)
            assertTrue("sube en t=$t", a >= previous)
            previous = a
            t += 0.01f
        }
        previous = 2f
        t = 12f - WelcomeLoopFadeSeconds
        while (t < 12f) {
            val a = loopAlpha(t, 12f)
            assertTrue("baja en t=$t", a <= previous)
            previous = a
            t += 0.01f
        }
    }

    @Test
    fun `el corte entre ciclos dura medio segundo`() {
        // 0,25 s de salida + 0,25 s de entrada.
        assertEquals(0.5f, WelcomeLoopFadeSeconds * 2f, 0f)
    }

    @Test
    fun `una escena que no corre muestra su cuadro representativo a plena opacidad`() {
        val frame = welcomeSceneFrame(elapsedSeconds = 3f, period = period, playing = false)
        assertEquals(representativeSceneTime(period), frame.t, 0f)
        assertEquals(1f, frame.alpha, 0f)
    }

    @Test
    fun `una pagina recien llegada apaga el cuadro fijo y luego arranca desde cero`() {
        val rep = representativeSceneTime(period)
        val start = welcomeSceneFrame(-WelcomeArrivalLeadSeconds, period, playing = true)
        assertEquals(rep, start.t, 0f)
        assertEquals(1f, start.alpha, 1e-6f)

        val middle = welcomeSceneFrame(-WelcomeArrivalLeadSeconds / 2f, period, playing = true)
        assertEquals(rep, middle.t, 0f)
        assertEquals(0.5f, middle.alpha, 1e-6f)

        val zero = welcomeSceneFrame(0f, period, playing = true)
        assertEquals(0f, zero.t, 0f)
        assertEquals(0f, zero.alpha, 0f)
    }

    @Test
    fun `una escena que corre usa el tiempo de escena y su fundido`() {
        val frame = welcomeSceneFrame(5f, period, playing = true)
        assertEquals(5f, frame.t, 1e-6f)
        assertEquals(1f, frame.alpha, 0f)

        // Un ciclo completo despues: vuelve a 0 y a opacidad 0 (cortina del bucle).
        val loop = welcomeSceneFrame(period, period, playing = true)
        assertEquals(0f, loop.t, 0f)
        assertEquals(0f, loop.alpha, 0f)
    }

    @Test
    fun `una escena que cierra sola no se funde otra vez en el marco`() {
        val noFade = welcomeSceneFrame(period, period, playing = true, fadeLoop = false)
        assertEquals(0f, noFade.t, 0f)
        assertEquals(1f, noFade.alpha, 0f)
        // La despedida del cuadro fijo al llegar a la pagina si se mantiene.
        val lead = welcomeSceneFrame(-WelcomeArrivalLeadSeconds / 2f, period, playing = true, fadeLoop = false)
        assertEquals(0.5f, lead.alpha, 1e-6f)
        val clock = WelcomeSceneClock()
        clock.page = 0
        clock.elapsed = period + 0.1f
        clock.playing = true
        assertEquals(1f, clock.frameOf(0, period, fadeLoop = false).alpha, 0f)
        assertTrue(clock.frameOf(0, period, fadeLoop = true).alpha < 1f)
    }

    @Test
    fun `el reloj solo corre para la pagina que lo posee`() {
        val clock = WelcomeSceneClock()
        // Sin dueno ni marcha: todas las paginas en su cuadro fijo.
        assertEquals(representativeSceneTime(period), clock.frameOf(0, period).t, 0f)

        clock.page = 1
        clock.elapsed = 4f
        clock.playing = true
        assertEquals(4f, clock.frameOf(1, period).t, 1e-6f)
        assertEquals(representativeSceneTime(period), clock.frameOf(0, period).t, 0f)
        assertEquals(representativeSceneTime(period), clock.frameOf(2, period).t, 0f)

        clock.playing = false
        assertEquals(representativeSceneTime(period), clock.frameOf(1, period).t, 0f)
    }
}
