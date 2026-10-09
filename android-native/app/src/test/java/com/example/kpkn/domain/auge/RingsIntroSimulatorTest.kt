package com.example.kpkn.domain.auge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** La demostración de RINGS usa la ley de E3a: sin sesión todo vale 100 y la sesión baja y se recupera con sus curvas. */
class RingsIntroSimulatorTest {

    private val session = RingsIntroSimulator.toggleSession(RingsIntroState())

    private fun valuesAt(state: RingsIntroState, hours: Float): RingsIntroValues =
        RingsIntroSimulator.valuesAt(RingsIntroSimulator.withHours(state, hours))

    @Test
    fun sinSesionNiAjustesTodoVale100() {
        for (hours in listOf(0f, 12f, 36f, 72f)) {
            assertEquals(RingsIntroValues(columna = 100, musculos = 100, energia = 100), valuesAt(RingsIntroState(), hours))
        }
    }

    @Test
    fun sesionRecienTerminadaDaMusculos45ColumnaCerca55EnergiaCerca70() {
        val values = valuesAt(session, 0f)
        assertTrue("Músculos ${values.musculos}", abs(values.musculos - 45) <= 2)
        assertTrue("Columna ${values.columna}", abs(values.columna - 55) <= 2)
        assertTrue("Energía ${values.energia}", abs(values.energia - 70) <= 2)
    }

    @Test
    fun sesionSubeSinBajarConLasHoras() {
        var previous = valuesAt(session, 0f)
        for (hour in 1..72) {
            val now = valuesAt(session, hour.toFloat())
            assertTrue("Músculos baja a las $hour h", now.musculos >= previous.musculos)
            assertTrue("Columna baja a las $hour h", now.columna >= previous.columna)
            assertTrue("Energía baja a las $hour h", now.energia >= previous.energia)
            previous = now
        }
    }

    @Test
    fun aLas72HorasTodosLosCanalesSuperan90() {
        val values = valuesAt(session, 72f)
        assertTrue(values.musculos >= 90 && values.columna >= 90 && values.energia >= 90)
    }

    @Test
    fun horasFueraDeRangoSeRecortanA0Y72() {
        assertEquals(valuesAt(session, 0f), valuesAt(session, -10f))
        assertEquals(valuesAt(session, 72f), valuesAt(session, 100f))
    }

    @Test
    fun ajusteManualFijaElValorEnCeroYLuegoSube() {
        val adjusted = RingsIntroSimulator.withAdjustment(RingsIntroState(), RingChannelId.MUSCULOS, 60)
        assertEquals(60, valuesAt(adjusted, 0f).musculos)
        assertTrue(valuesAt(adjusted, 24f).musculos > 60)
        assertTrue(valuesAt(adjusted, 72f).musculos > valuesAt(adjusted, 24f).musculos)
    }

    @Test
    fun ajusteHechoAUnaHoraSaltaAlValorYLaCurvaSigueDesdeAhi() {
        val atHour36 = RingsIntroSimulator.withHours(session, 36f)
        val adjusted = RingsIntroSimulator.withAdjustment(atHour36, RingChannelId.COLUMNA, 30)
        // A la hora del ajuste el anillo marca exactamente lo que dice el deslizador.
        assertEquals(30, RingsIntroSimulator.valuesAt(adjusted).columna)
        // Después la curva sube desde ese valor.
        assertTrue(valuesAt(adjusted, 60f).columna > 30)
        // Antes de esa hora vale la sesión, no el ajuste.
        assertEquals(valuesAt(session, 12f).columna, valuesAt(adjusted, 12f).columna)
    }

    @Test
    fun laHoraDeLaLineaSeRedondeaAEnteraYNoSaleDelRango() {
        assertEquals(36f, RingsIntroSimulator.withHours(session, 35.6f).hoursAfter)
        assertEquals(RingsIntroSimulator.MAX_HOURS, RingsIntroSimulator.withHours(session, 200f).hoursAfter)
    }

    @Test
    fun quitarElEntrenoDevuelveLaDemostracionAsuEstadoInicial() {
        val adjusted = RingsIntroSimulator.withAdjustment(
            RingsIntroSimulator.withHours(session, 24f),
            RingChannelId.ENERGIA,
            40,
        )
        assertEquals(RingsIntroState(), RingsIntroSimulator.toggleSession(adjusted))
    }

    @Test
    fun esDeterminista() {
        val state = RingsIntroSimulator.withAdjustment(
            RingsIntroSimulator.withHours(session, 18f),
            RingChannelId.MUSCULOS,
            52,
        )
        assertEquals(RingsIntroSimulator.valuesAt(state), RingsIntroSimulator.valuesAt(state.copy()))
        assertEquals(RingsIntroSimulator.valuesAt(state), RingsIntroSimulator.valuesAt(state))
    }

    @Test
    fun losValoresSeLeenPorCanal() {
        val values = RingsIntroValues(columna = 1, musculos = 2, energia = 3)
        assertEquals(1, values[RingChannelId.COLUMNA])
        assertEquals(2, values[RingChannelId.MUSCULOS])
        assertEquals(3, values[RingChannelId.ENERGIA])
    }
}
