package com.example.kpkn.screens.onboarding.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lógica de la escena de Recuperación: qué valor tiene cada anillo en cada instante de la mini historia del día,
 * cuándo salen las etiquetas de cambio, qué flecha, qué icono, qué momento de la línea de tiempo y qué lectura.
 * El composable solo dibuja esto, así que lo que se fija aquí es lo que ve la persona.
 */
class WelcomeRingsSceneTest {

    private val s = RingsStory

    private fun values(t: Float) = ringsValuesAt(t)

    private fun <T : Any> notNull(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    // ── La historia: los números del plan ────────────────────────────────────────────────────

    @Test
    fun `al despertar los anillos valen 92 88 y 95`() {
        val v = values(1.5f)
        assertEquals(92f, v.columna, 1e-4f)
        assertEquals(88f, v.musculo, 1e-4f)
        assertEquals(95f, v.energia, 1e-4f)
    }

    @Test
    fun `al entrenar baja el musculo 30 y la energia 21`() {
        val v = values(5.5f)
        assertEquals(58f, v.musculo, 1e-4f)
        assertEquals(74f, v.energia, 1e-4f)
        assertEquals(-30, ringDisplayPercent(v.musculo) - ringDisplayPercent(s.morning.musculo))
        assertEquals(-21, ringDisplayPercent(v.energia) - ringDisplayPercent(s.morning.energia))
        assertTrue("la columna cede un poco", v.columna < s.morning.columna && v.columna > 80f)
    }

    @Test
    fun `al dormir sube la energia a 97 y el musculo a 76 con la columna cerca de 91`() {
        val v = values(10.0f)
        assertEquals(97f, v.energia, 1e-4f)
        assertEquals(76f, v.musculo, 1e-4f)
        assertEquals(91f, v.columna, 1e-4f)
        assertEquals(23, ringDisplayPercent(v.energia) - ringDisplayPercent(s.afterTraining.energia))
        assertEquals(18, ringDisplayPercent(v.musculo) - ringDisplayPercent(s.afterTraining.musculo))
    }

    @Test
    fun `al abrir la escena los anillos parten de cero y se llenan`() {
        val v0 = values(0f)
        assertEquals(0f, v0.columna, 0f)
        assertEquals(0f, v0.musculo, 0f)
        assertEquals(0f, v0.energia, 0f)
        var previous = -1f
        var t = 0f
        while (t <= 1.3f) {
            val v = ringValueAt(RingKind.COLUMNA, t)
            assertTrue("sube en t=$t", v >= previous)
            previous = v
            t += 0.02f
        }
    }

    @Test
    fun `el entreno solo hace bajar y el descanso solo hace subir`() {
        RingKind.entries.forEach { kind ->
            val drain = s.drain.getValue(kind)
            var previous = Float.MAX_VALUE
            var t = drain.start
            while (t <= drain.end) {
                val v = ringValueAt(kind, t)
                assertTrue("$kind baja en t=$t", v <= previous + 1e-4f)
                previous = v
                t += 0.02f
            }
            val recover = s.recover.getValue(kind)
            previous = -1f
            t = recover.start
            while (t <= recover.end) {
                val v = ringValueAt(kind, t)
                assertTrue("$kind sube en t=$t", v >= previous - 1e-4f)
                previous = v
                t += 0.02f
            }
        }
    }

    @Test
    fun `los valores siempre quedan entre 0 y 100 en todo el bucle`() {
        var t = 0f
        while (t < WelcomeRingsPeriod) {
            val v = values(t)
            listOf(v.columna, v.musculo, v.energia).forEach { assertTrue("t=$t v=$it", it in 0f..100f) }
            t += 0.01f
        }
    }

    @Test
    fun `los movimientos terminan antes de que cambie el momento siguiente`() {
        s.intro.values.forEach { assertTrue(it.end <= s.TrainAt) }
        s.drain.values.forEach { assertTrue(it.start >= s.TrainAt && it.end <= s.SleepAt) }
        s.recover.values.forEach { assertTrue(it.start >= s.SleepAt && it.end <= s.NextAt) }
        assertTrue(s.NextAt < WelcomeRingsPeriod)
    }

    @Test
    fun `el porcentaje se lee como entero redondeado`() {
        assertEquals(58, ringDisplayPercent(57.5f))
        assertEquals(57, ringDisplayPercent(57.49f))
        assertEquals(0, ringDisplayPercent(-3f))
        assertEquals(100, ringDisplayPercent(140f))
    }

    // ── Etiquetas de cambio ──────────────────────────────────────────────────────────────────

    @Test
    fun `las etiquetas usan el signo menos de verdad y el espacio antes del porcentaje`() {
        assertEquals("−30 %", formatRingDelta(-30))
        assertEquals("+23 %", formatRingDelta(23))
        assertEquals("+2 %", formatRingDelta(2))
        assertEquals("−3 %", formatRingDelta(-3))
    }

    @Test
    fun `al entrenar salen las etiquetas de cambio de los tres anillos`() {
        val chips = ringChipsAt(4.0f).associateBy { it.kind }
        assertEquals("−30 %", chips.getValue(RingKind.MUSCULO).text)
        assertEquals("−21 %", chips.getValue(RingKind.ENERGIA).text)
        assertEquals("−3 %", chips.getValue(RingKind.COLUMNA).text)
    }

    @Test
    fun `al dormir salen las etiquetas de recuperacion`() {
        val chips = ringChipsAt(7.9f).associateBy { it.kind }
        assertEquals("+23 %", chips.getValue(RingKind.ENERGIA).text)
        assertEquals("+18 %", chips.getValue(RingKind.MUSCULO).text)
        assertEquals("+2 %", chips.getValue(RingKind.COLUMNA).text)
    }

    @Test
    fun `sin cambios no hay etiquetas`() {
        assertTrue(ringChipsAt(1.5f).isEmpty())
        assertTrue(ringChipsAt(6.0f).isEmpty())
        assertTrue(ringChipsAt(10.5f).isEmpty())
        assertTrue(ringChipsAt(11.9f).isEmpty())
    }

    @Test
    fun `cada etiqueta sube mientras vive y se desvanece al final`() {
        var previousRise = -1f
        var t = 3.3f
        while (t < 3.3f + RingChipLife) {
            val chip = ringChipsAt(t).firstOrNull { it.kind == RingKind.MUSCULO } ?: break
            assertTrue("alpha en t=$t", chip.alpha in 0f..1f)
            assertTrue("sube en t=$t", chip.rise >= previousRise)
            previousRise = chip.rise
            t += 0.02f
        }
        val early = ringChipsAt(3.3f + 0.05f).first { it.kind == RingKind.MUSCULO }
        val mid = ringChipsAt(3.3f + 0.8f).first { it.kind == RingKind.MUSCULO }
        val late = ringChipsAt(3.3f + RingChipLife - 0.02f).first { it.kind == RingKind.MUSCULO }
        assertTrue(early.alpha < mid.alpha)
        assertEquals(1f, mid.alpha, 1e-4f)
        assertTrue(late.alpha < 0.1f)
        assertTrue(late.rise > early.rise)
    }

    @Test
    fun `las etiquetas de entrenar se apagan antes de que empiece el descanso`() {
        val lastDrainChip = 3.6f + RingChipLife
        assertTrue(lastDrainChip < s.SleepAt + 1f)
        assertTrue(ringChipsAt(s.SleepAt).isEmpty())
    }

    // ── Flechas, momento, icono, línea de tiempo ─────────────────────────────────────────────

    @Test
    fun `la flecha va hacia abajo tras entrenar y hacia arriba al recuperar`() {
        RingKind.entries.forEach { kind ->
            assertEquals(RingTrend.NONE, ringTrendAt(kind, 1.5f).trend)
            val down = ringTrendAt(kind, 5.5f)
            assertEquals(RingTrend.DOWN, down.trend)
            assertEquals(1f, down.alpha, 1e-4f)
            val up = ringTrendAt(kind, 9.0f)
            assertEquals(RingTrend.UP, up.trend)
            assertEquals(1f, up.alpha, 1e-4f)
        }
    }

    @Test
    fun `los cuatro momentos del dia siguen el orden de la historia`() {
        assertEquals(RingsMoment.DESPIERTAS, ringsMomentAt(0f))
        assertEquals(RingsMoment.DESPIERTAS, ringsMomentAt(s.TrainAt - 0.01f))
        assertEquals(RingsMoment.ENTRENAS, ringsMomentAt(s.TrainAt))
        assertEquals(RingsMoment.DUERMES, ringsMomentAt(s.SleepAt))
        assertEquals(RingsMoment.AL_OTRO_DIA, ringsMomentAt(s.NextAt))
        assertEquals(RingsMoment.AL_OTRO_DIA, ringsMomentAt(WelcomeRingsPeriod - 0.01f))
    }

    @Test
    fun `aparece la pesa al entrenar y la cama al dormir`() {
        assertNull(badgeAt(1.0f))
        val train = notNull(badgeAt(4.0f))
        assertEquals(RingsBadge.DUMBBELL, train.badge)
        assertEquals(1f, train.scale, 1e-3f)
        val sleep = notNull(badgeAt(8.0f))
        assertEquals(RingsBadge.BED, sleep.badge)
        assertEquals(1f, sleep.scale, 1e-3f)
        assertNull(badgeAt(11.0f))
    }

    @Test
    fun `el icono nace con rebote y nunca tiene escala negativa`() {
        var t = 0f
        while (t < WelcomeRingsPeriod) {
            val b = badgeAt(t)
            if (b != null) {
                assertTrue("escala en t=$t", b.scale >= 0f && b.scale < 1.3f)
                assertTrue("onda en t=$t", b.wave in 0f..1f)
            }
            t += 0.02f
        }
    }

    @Test
    fun `la linea de tiempo enciende el sol la pesa y la luna segun el momento`() {
        val morning = timelineAt(1.5f)
        assertEquals(1f, morning.sun, 1e-4f)
        assertEquals(0f, morning.dumbbell, 1e-4f)
        assertEquals(0f, morning.moon, 1e-4f)
        assertEquals(0f, morning.progress, 1e-4f)

        val training = timelineAt(4.5f)
        assertEquals(0f, training.sun, 1e-4f)
        assertEquals(1f, training.dumbbell, 1e-4f)
        assertEquals(0.5f, training.progress, 1e-4f)

        val sleeping = timelineAt(8.5f)
        assertEquals(1f, sleeping.moon, 1e-4f)
        assertEquals(0f, sleeping.dumbbell, 1e-4f)
        assertEquals(1f, sleeping.progress, 1e-4f)

        val next = timelineAt(11f)
        assertEquals(1f, next.sun, 1e-4f)
        assertEquals(0f, next.moon, 1e-4f)
        assertEquals(0f, next.progressAlpha, 1e-4f)
    }

    // ── Textos que cambian ───────────────────────────────────────────────────────────────────

    @Test
    fun `la lectura cambia de hoy puedes entrenar fuerte a mejor un dia suave y a listo para otra sesion`() {
        val sw = RingsReading.titleSwitch
        assertEquals(TextSwap(0, 0, 1f), textSwapAt(1.5f, sw))
        assertEquals(TextSwap(1, 1, 1f), textSwapAt(6.0f, sw))
        assertEquals(TextSwap(2, 2, 1f), textSwapAt(11.0f, sw))
        assertEquals("Hoy puedes entrenar fuerte", RingsReading.titles[0])
        assertEquals("Mejor un día suave", RingsReading.titles[1])
        assertEquals("Listo para otra buena sesión", RingsReading.titles[2])
    }

    @Test
    fun `durante el fundido el texto nuevo entra con k de 0 a 1`() {
        val switch = RingsReading.titleSwitch[0]
        val start = textSwapAt(switch, RingsReading.titleSwitch)
        assertEquals(0, start.from)
        assertEquals(1, start.to)
        assertEquals(0f, start.k, 1e-6f)
        val half = textSwapAt(switch + 0.2f, RingsReading.titleSwitch, fade = 0.4f)
        assertEquals(0.5f, half.k, 1e-5f)
        val done = textSwapAt(switch + 0.5f, RingsReading.titleSwitch, fade = 0.4f)
        assertEquals(TextSwap(1, 1, 1f), done)
    }

    @Test
    fun `la lectura cambia despues de que los anillos terminan de moverse`() {
        val titleSwitch = RingsReading.titleSwitch
        assertTrue(titleSwitch[0] >= s.drain.values.maxOf { it.end })
        assertTrue(titleSwitch[1] >= s.recover.values.maxOf { it.end })
        assertEquals(RingsReading.titles.size - 1, titleSwitch.size)
        assertEquals(RingsReading.subtitles.size - 1, RingsReading.subtitleSwitch.size)
    }

    @Test
    fun `la primera parada pasa de Despiertas a Al otro dia cuando amanece`() {
        assertEquals("Despiertas", RingsReading.wakeLabels[textSwapAt(5f, RingsReading.wakeSwitch).to])
        assertEquals("Al otro día", RingsReading.wakeLabels[textSwapAt(11f, RingsReading.wakeSwitch).to])
        assertEquals(RingsStory.NextAt, RingsReading.wakeSwitch.single(), 0f)
    }

    @Test
    fun `los textos de la tarjeta caben en su caja`() {
        RingsReading.titles.forEach { assertTrue(it, it.length <= 30) }
        RingsReading.subtitles.forEach { assertTrue(it, it.length <= 34) }
    }

    @Test
    fun `el velo del bucle cubre el cierre y el arranque pero no el resto de la historia`() {
        assertEquals(1f, ringsVeilAt(0f), 0f)
        assertEquals(0f, ringsVeilAt(WelcomeLoopFadeSeconds), 1e-6f)
        listOf(1.5f, 4f, 7.5f, 10.5f).forEach { assertEquals("t=$it", 0f, ringsVeilAt(it), 0f) }
        assertEquals(0f, ringsVeilAt(WelcomeRingsPeriod - WelcomeLoopFadeSeconds), 1e-6f)
        assertTrue(ringsVeilAt(WelcomeRingsPeriod - 0.001f) > 0.99f)
    }

    @Test
    fun `la tarjeta de lectura aparece despues de que los anillos empiezan a llenarse`() {
        assertEquals(0f, RingsReading.cardAlpha(0f), 0f)
        assertEquals(1f, RingsReading.cardAlpha(1.5f), 0f)
        var previous = -1f
        var t = 0f
        while (t < 2f) {
            val a = RingsReading.cardAlpha(t)
            assertTrue(a >= previous)
            previous = a
            t += 0.05f
        }
    }
}
