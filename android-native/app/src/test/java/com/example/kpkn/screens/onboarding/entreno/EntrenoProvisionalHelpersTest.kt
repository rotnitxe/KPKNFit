package com.example.kpkn.screens.onboarding.entreno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Textos y conversiones puras de los controles provisionales de Entreno v2 (la UI solo los pinta). */
class EntrenoProvisionalHelpersTest {

    @Test
    fun theSessionTimeIsReadInMinutesAndAboveAnHourAlsoInHours() {
        assertEquals("75 min", formatSessionMinutes(75))
        assertNull(formatSessionHoursMinutes(45))
        assertNull(formatSessionHoursMinutes(59))
        assertEquals("1 h", formatSessionHoursMinutes(60))
        assertEquals("1 h 15 min", formatSessionHoursMinutes(75))
        assertEquals("3 h", formatSessionHoursMinutes(180))
    }

    @Test
    fun theSessionTimeHintGoesToTheEssentialWithLittleTimeAndAddsWorkWithMuch() {
        assertNull(sessionTimeHint(null))
        assertEquals("Con poco tiempo vamos a lo esencial.", sessionTimeHint(20))
        assertEquals("Con poco tiempo vamos a lo esencial.", sessionTimeHint(30))
        assertNull(sessionTimeHint(31))
        assertNull(sessionTimeHint(60))
        assertNull(sessionTimeHint(89))
        assertEquals("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.", sessionTimeHint(90))
        assertEquals("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.", sessionTimeHint(180))
    }

    @Test
    fun theDaysCountAgreesInNumber() {
        assertEquals("Elige entre 1 y 7 días.", weekdaysCountText(0))
        assertEquals("1 día por semana", weekdaysCountText(1))
        assertEquals("2 días por semana", weekdaysCountText(2))
        assertEquals("7 días por semana", weekdaysCountText(7))
    }

    @Test
    fun marksAreShownInTheVisibleUnitWithoutExtraZeros() {
        assertEquals("100", marksDisplayText(100.0, "kg"))
        assertEquals("82,5", marksDisplayText(82.5, "kg").replace('.', ','))
        // 100 kg son 220,5 lb; 45,359237 kg son exactamente 100 lb.
        assertEquals("220.5", marksDisplayText(100.0, "lb"))
        assertEquals("100", marksDisplayText(45.359237, "lb"))
    }

    @Test
    fun marksAreWrittenBackAsKilogramsWhateverTheUnitTheyWereTypedIn() {
        assertEquals(100.0, marksKgFromText("100", "kg")!!, 0.0001)
        assertEquals(82.5, marksKgFromText("82,5", "kg")!!, 0.0001)
        assertEquals(45.359237, marksKgFromText("100", "lb")!!, 0.0001)
        // Fuera de rango o sin número no se escribe nada.
        assertNull(marksKgFromText("", "kg"))
        assertNull(marksKgFromText("abc", "kg"))
        assertNull(marksKgFromText("0", "kg"))
        assertNull(marksKgFromText("5000", "kg"))
        assertNull(marksKgFromText("5000", "lb"))
    }
}
