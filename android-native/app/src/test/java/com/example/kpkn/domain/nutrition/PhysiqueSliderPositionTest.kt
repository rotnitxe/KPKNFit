package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [physiqueSliderPositionForBodyFat]: la inversa de [bodyFatForSliderPos] que usa la regla de grasa corporal del
 * alta para elegir el fotograma de la figura a partir del porcentaje.
 */
class PhysiqueSliderPositionTest {

    @Test
    fun theMidpointOfEveryGroupIsItsOwnPosition() {
        for (group in PhysiqueGroups) {
            assertEquals(
                "grupo ${group.group} (${group.midpointBodyFat} %)",
                group.group.toFloat(),
                physiqueSliderPositionForBodyFat(group.midpointBodyFat),
                1e-6f,
            )
        }
        // Los puntos medios son 10, 15… 40: la figura de arranque (25 %) es la posición 4.
        assertEquals(4f, physiqueSliderPositionForBodyFat(25.0), 1e-6f)
    }

    @Test
    fun itIsLinearBetweenTheMidpointsOfNeighbouringGroups() {
        assertEquals(1.5f, physiqueSliderPositionForBodyFat(12.5), 1e-6f)
        assertEquals(2.2f, physiqueSliderPositionForBodyFat(16.0), 1e-6f)
        assertEquals(4.5f, physiqueSliderPositionForBodyFat(27.5), 1e-6f)
        assertEquals(6.8f, physiqueSliderPositionForBodyFat(39.0), 1e-5f)
    }

    @Test
    fun itIsClampedToOneAndSevenOutsideTheIllustratedRange() {
        for (percent in listOf(0.0, 3.0, 5.0, 9.9, 10.0)) {
            assertEquals("percent=$percent", 1f, physiqueSliderPositionForBodyFat(percent), 0f)
        }
        for (percent in listOf(40.0, 40.1, 45.0, 50.0, 60.0, 100.0)) {
            assertEquals("percent=$percent", 7f, physiqueSliderPositionForBodyFat(percent), 0f)
        }
        assertEquals(1f, physiqueSliderPositionForBodyFat(-3.0), 0f)
        assertEquals(1f, physiqueSliderPositionForBodyFat(Double.NEGATIVE_INFINITY), 0f)
        assertEquals(7f, physiqueSliderPositionForBodyFat(Double.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun somethingThatIsNotANumberFallsBackToTheStartingPosition() {
        assertEquals(4f, physiqueSliderPositionForBodyFat(Double.NaN), 0f)
    }

    @Test
    fun itNeverDecreasesAsThePercentageGrows() {
        var previous = physiqueSliderPositionForBodyFat(0.0)
        var percent = 0.0
        while (percent <= 70.0) {
            val position = physiqueSliderPositionForBodyFat(percent)
            assertTrue("percent=$percent: $position < $previous", position >= previous)
            assertTrue("percent=$percent: $position fuera de [1, 7]", position in 1f..7f)
            previous = position
            percent += 0.1
        }
    }

    @Test
    fun itUndoesBodyFatForSliderPosOverTheIllustratedRange() {
        // posición → porcentaje → posición.
        var position = 1f
        while (position <= 7f) {
            val roundTrip = physiqueSliderPositionForBodyFat(bodyFatForSliderPos(position))
            assertEquals("posición $position", position, roundTrip, 1e-4f)
            position += 0.125f
        }
        // porcentaje → posición → porcentaje, de 10 % a 40 % (fuera de ahí la posición se fija y no vuelve).
        var percent = 10.0
        while (percent <= 40.0) {
            val roundTrip = bodyFatForSliderPos(physiqueSliderPositionForBodyFat(percent))
            assertEquals("porcentaje $percent", percent, roundTrip, 1e-4)
            percent += 0.5
        }
    }

    @Test
    fun everyWholePercentOfTheRulerMapsToAFrameOfTheSixtyOneDrawablesInOrder() {
        // Las figuras son 61 fotogramas (posición 1..7 en pasos de 0,1): la regla de 5 % a 50 % los recorre de punta a punta.
        var previousFrame = -1
        for (percent in 5..50) {
            val position = physiqueSliderPositionForBodyFat(percent.toDouble())
            val frame = Math.round((position - 1f) / 6f * 60f)
            assertTrue("$percent %: fotograma $frame", frame in 0..60)
            assertTrue("$percent %: el fotograma no retrocede", frame >= previousFrame)
            previousFrame = frame
        }
        assertEquals(0L, Math.round((physiqueSliderPositionForBodyFat(5.0) - 1f) / 6f * 60f).toLong())
        assertEquals(60L, Math.round((physiqueSliderPositionForBodyFat(50.0) - 1f) / 6f * 60f).toLong())
    }
}
