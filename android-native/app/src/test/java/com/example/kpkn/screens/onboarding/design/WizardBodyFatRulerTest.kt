package com.example.kpkn.screens.onboarding.design

import com.example.kpkn.screens.onboarding.design.WizardBodyFatScale.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matemática de la regla de grasa corporal y del escenario de la figura, sin Compose: qué marca es cada
 * porcentaje, cómo se pasa de una altura táctil a un entero (y a la inversa), el fotograma de la figura, el alto del
 * escenario según la pantalla y cómo se escribe el número.
 */
class WizardBodyFatRulerTest {

    private val range = WizardBodyFatScale.DEFAULT_RANGE

    // ─── Rango y marcas ──────────────────────────────────────────────────────

    @Test
    fun theRulerRunsFromFiveAtTheTopToFiftyAtTheBottom() {
        assertEquals(5, WizardBodyFatScale.MIN_PERCENT)
        assertEquals(50, WizardBodyFatScale.MAX_PERCENT)
        assertEquals(5..50, range)
        assertEquals(0f, WizardBodyFatScale.fractionOf(5.0), 0f)
        assertEquals(1f, WizardBodyFatScale.fractionOf(50.0), 0f)
    }

    @Test
    fun thereIsOneTickPerPercentWithAMidTickEveryFiveAndALabelledOneEveryTen() {
        val byKind = range.groupBy { WizardBodyFatScale.tickAt(it) }
        assertEquals(46, range.count())
        assertEquals(listOf(10, 20, 30, 40, 50), byKind.getValue(Tick.MAJOR))
        assertEquals(listOf(5, 15, 25, 35, 45), byKind.getValue(Tick.MID))
        assertEquals(36, byKind.getValue(Tick.MINOR).size)
        assertTrue(byKind.getValue(Tick.MINOR).none { it % WizardBodyFatScale.MID_EVERY == 0 })
    }

    // ─── Fracción de la pista ────────────────────────────────────────────────

    @Test
    fun theFractionGrowsDownTheRulerAndIsClampedAtBothEnds() {
        assertEquals(0.5f, WizardBodyFatScale.fractionOf(27.5), 1e-6f)
        assertEquals(0f, WizardBodyFatScale.fractionOf(0.0), 0f)
        assertEquals(0f, WizardBodyFatScale.fractionOf(3.0), 0f)
        assertEquals(1f, WizardBodyFatScale.fractionOf(58.0), 0f)
        assertEquals(1f, WizardBodyFatScale.fractionOf(Double.POSITIVE_INFINITY), 0f)
        assertEquals(0f, WizardBodyFatScale.fractionOf(Double.NaN), 0f)
        // Un rango de un solo valor no divide por cero.
        assertEquals(0f, WizardBodyFatScale.fractionOf(5.0, 5..5), 0f)
    }

    @Test
    fun aFractionOfTheTrackSnapsToTheNearestWholePercent() {
        assertEquals(5, WizardBodyFatScale.percentAt(0f))
        assertEquals(50, WizardBodyFatScale.percentAt(1f))
        assertEquals(28, WizardBodyFatScale.percentAt(0.5f))
        assertEquals(5, WizardBodyFatScale.percentAt(-1f))
        assertEquals(50, WizardBodyFatScale.percentAt(7f))
        assertEquals(5, WizardBodyFatScale.percentAt(Float.NaN))
        // Un entero está en el centro de su franja: ±0,49 pasos todavía es el mismo valor.
        val step = 1f / (range.last - range.first)
        for (value in range) {
            val center = (value - range.first) * step
            assertEquals(value, WizardBodyFatScale.percentAt(center))
            assertEquals(value, WizardBodyFatScale.percentAt(center + 0.49f * step))
            assertEquals(value, WizardBodyFatScale.percentAt(center - 0.49f * step))
        }
    }

    // ─── De altura táctil a entero ───────────────────────────────────────────

    @Test
    fun everyTickMapsToItsOwnHeightAndBackWithOnePixelOfSlack() {
        // Escenario de 380 dp a 2,75 px/dp con 11 dp de aire arriba y abajo: la pista mide (380 - 22) dp.
        val density = 2.75f
        val top = 11f * density
        val height = (380f - 22f) * density
        for (value in range) {
            val y = WizardBodyFatScale.yOf(value.toDouble(), range, top, height)
            assertEquals("$value %", value, WizardBodyFatScale.percentAtY(y, range, top, height))
            assertEquals("$value % (1 px antes)", value, WizardBodyFatScale.percentAtY(y - 1f, range, top, height))
            assertEquals("$value % (1 px después)", value, WizardBodyFatScale.percentAtY(y + 1f, range, top, height))
        }
        assertEquals(top, WizardBodyFatScale.yOf(5.0, range, top, height), 1e-3f)
        assertEquals(top + height, WizardBodyFatScale.yOf(50.0, range, top, height), 1e-3f)
    }

    @Test
    fun touchingAboveOrBelowTheTrackStaysAtTheEnds() {
        val top = 30f
        val height = 900f
        assertEquals(5, WizardBodyFatScale.percentAtY(-200f, range, top, height))
        assertEquals(5, WizardBodyFatScale.percentAtY(top, range, top, height))
        assertEquals(50, WizardBodyFatScale.percentAtY(top + height, range, top, height))
        assertEquals(50, WizardBodyFatScale.percentAtY(5_000f, range, top, height))
        // Una pista sin alto (todavía sin medir) no divide por cero.
        assertEquals(5, WizardBodyFatScale.percentAtY(100f, range, top, 0f))
    }

    @Test
    fun oneStepOfTheFingerMovesTheValueByExactlyOnePercent() {
        val top = 20f
        val height = 450f
        val stepPx = height / (range.last - range.first)
        var previous = WizardBodyFatScale.percentAtY(top, range, top, height)
        for (index in 1..(range.last - range.first)) {
            val value = WizardBodyFatScale.percentAtY(top + index * stepPx, range, top, height)
            assertEquals(previous + 1, value)
            previous = value
        }
    }

    // ─── Número ──────────────────────────────────────────────────────────────

    @Test
    fun theNumberIsWrittenWithoutATrailingZeroAndWithACommaForDecimals() {
        assertEquals("25", wizardBodyFatNumber(25.0))
        assertEquals("5", wizardBodyFatNumber(5.0))
        assertEquals("17,5", wizardBodyFatNumber(17.5))
        assertEquals("21,6", wizardBodyFatNumber(21.6))
        // Redondea a un decimal sin dejar «22,0».
        assertEquals("22", wizardBodyFatNumber(21.96))
        assertEquals("22,1", wizardBodyFatNumber(22.06))
        assertEquals("0", wizardBodyFatNumber(Double.NaN))
        assertEquals("0", wizardBodyFatNumber(Double.POSITIVE_INFINITY))
    }

    // ─── Fotograma de la figura ──────────────────────────────────────────────

    @Test
    fun theFigureFrameComesFromThePercentageOverTheSixtyOneFrames() {
        assertEquals(30, bodyFatFrameIndex(25.0, 61))
        assertEquals(0, bodyFatFrameIndex(10.0, 61))
        assertEquals(60, bodyFatFrameIndex(40.0, 61))
        // Por debajo de 10 % y por encima de 40 % la figura se queda en la primera y en la última.
        assertEquals(0, bodyFatFrameIndex(5.0, 61))
        assertEquals(60, bodyFatFrameIndex(50.0, 61))
        // 12,5 % es la posición 1,5: la mitad del primer tramo (de 10 fotogramas).
        assertEquals(5, bodyFatFrameIndex(12.5, 61))
    }

    @Test
    fun theFigureFrameNeverGoesBackwardsAndStaysInsideTheArray() {
        var previous = -1
        for (tenths in 0..700) {
            val frame = bodyFatFrameIndex(tenths / 10.0, 61)
            assertTrue("${tenths / 10.0} %: fotograma $frame", frame in 0..60)
            assertTrue("${tenths / 10.0} %: retrocede de $previous a $frame", frame >= previous)
            previous = frame
        }
        assertEquals(0, bodyFatFrameIndex(25.0, 1))
        assertEquals(0, bodyFatFrameIndex(25.0, 0))
        // Algo que no es un número cae en la figura de arranque (posición 4, el fotograma 30).
        assertEquals(30, bodyFatFrameIndex(Double.NaN, 61))
    }

    // ─── Escenario ───────────────────────────────────────────────────────────

    @Test
    fun theStageIs380DpOnATypicalPhoneAndShrinksOnShortScreens() {
        assertEquals(380f, bodyFatStageHeightDp(844f), 0f)
        assertEquals(380f, bodyFatStageHeightDp(915f), 0f)
        assertEquals(380f, bodyFatStageHeightDp(826f), 0.5f)
        assertEquals(358.8f, bodyFatStageHeightDp(780f), 0.01f)
        assertEquals(294.4f, bodyFatStageHeightDp(640f), 0.01f)
        // Nunca por debajo del mínimo, aunque la pantalla sea diminuta (o apaisada).
        assertEquals(220f, bodyFatStageHeightDp(320f), 0f)
        assertEquals(220f, bodyFatStageHeightDp(0f), 0f)
        // Una medida que no llega no encoge la figura.
        assertEquals(380f, bodyFatStageHeightDp(Float.NaN), 0f)
    }
}
