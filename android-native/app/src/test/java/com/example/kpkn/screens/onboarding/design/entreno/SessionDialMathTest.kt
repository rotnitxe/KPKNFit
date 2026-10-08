package com.example.kpkn.screens.onboarding.design.entreno

import com.example.kpkn.domain.onboarding.EntrenoStepValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El dial sin Compose: de minutos a ángulo de reloj y de vuelta, el ajuste a la muesca, la saturación en los extremos y en el
 * hueco de abajo, y el seguimiento de un arrastre completo (que nunca da la vuelta de golpe al cruzar el hueco).
 *
 * El rango por defecto es el del paso SESSION_TIME (30 a 180 min): el arco barre 300° en 150 min, así que cada muesca de 5
 * minutos son 10° y los 30 min caen justo al inicio del arco.
 */
class SessionDialMathTest {

    private val notches = (30..180 step 5).toList()

    // ─── Ángulos ─────────────────────────────────────────────────────────────

    @Test
    fun theDialRangeIsTheOneOfTheStep() {
        // Una sola fuente: el reloj y el borrador nunca discrepan.
        assertEquals(EntrenoStepValues.SESSION_MINUTES_MIN..EntrenoStepValues.SESSION_MINUTES_MAX, SESSION_DIAL_RANGE)
        assertEquals(30..180, SESSION_DIAL_RANGE)
        assertEquals(EntrenoStepValues.SESSION_MINUTES_STEP, SESSION_DIAL_STEP)
        assertEquals(5, SESSION_DIAL_STEP)
    }

    @Test
    fun theArcRunsFromBottomLeftThroughTheTopToBottomRight() {
        assertEquals(210f, angleForMinutes(30), 1e-4f)
        assertEquals(0f, angleForMinutes(105), 1e-4f)
        assertEquals(150f, angleForMinutes(180), 1e-4f)
        assertEquals(270f, angleForMinutes(60), 1e-4f)
        assertEquals(240f, angleForMinutes(45), 1e-4f)
        assertEquals(30f, angleForMinutes(120), 1e-4f)
        assertEquals(90f, angleForMinutes(150), 1e-4f)
        // Cada muesca de 5 minutos son 10°.
        assertEquals(10f, angleForMinutes(35) - angleForMinutes(30), 1e-4f)
    }

    @Test
    fun anglesAreClampedToTheEndsOfTheArc() {
        assertEquals(210f, angleForMinutes(0), 1e-4f)
        // Los 20 y 25 min de antes ya no tienen sitio en el arco: caen en el inicio.
        assertEquals(210f, angleForMinutes(20), 1e-4f)
        assertEquals(210f, angleForMinutes(25), 1e-4f)
        assertEquals(210f, angleForMinutes(-50f), 1e-4f)
        assertEquals(150f, angleForMinutes(500), 1e-4f)
        assertEquals(210f, angleForMinutes(Float.NaN), 1e-4f)
        assertEquals(150f, angleForMinutes(Float.POSITIVE_INFINITY), 1e-4f)
    }

    @Test
    fun theFractionIsLinearAndClamped() {
        assertEquals(0f, dialFraction(30f), 0f)
        assertEquals(0.5f, dialFraction(105f), 1e-6f)
        assertEquals(1f, dialFraction(180f), 0f)
        assertEquals(0f, dialFraction(5f), 0f)
        assertEquals(0f, dialFraction(20f), 0f)
        assertEquals(1f, dialFraction(999f), 0f)
        assertEquals(0f, dialFraction(Float.NaN), 0f)
        assertEquals(0f, dialFraction(50f, 20..20), 0f)
    }

    @Test
    fun clockAnglesNormalizeToOneTurn() {
        assertEquals(350f, normalizeDialAngle(-10f), 1e-4f)
        assertEquals(10f, normalizeDialAngle(370f), 1e-4f)
        assertEquals(0f, normalizeDialAngle(360f), 0f)
        assertEquals(0f, normalizeDialAngle(-360f), 0f)
        assertEquals(0f, normalizeDialAngle(0f), 0f)
        assertEquals(0f, normalizeDialAngle(Float.NaN), 0f)
        assertEquals(0f, normalizeDialAngle(Float.POSITIVE_INFINITY), 0f)
        assertTrue(normalizeDialAngle(-1e-9f) < 360f)
    }

    @Test
    fun aTouchPointBecomesAClockAngleFromTheTwelve() {
        assertEquals(0f, dialAngleOf(0f, -1f), 1e-4f)
        assertEquals(90f, dialAngleOf(1f, 0f), 1e-4f)
        assertEquals(180f, dialAngleOf(0f, 1f), 1e-4f)
        assertEquals(270f, dialAngleOf(-1f, 0f), 1e-4f)
        assertEquals(45f, dialAngleOf(1f, -1f), 1e-4f)
        assertEquals(315f, dialAngleOf(-1f, -1f), 1e-4f)
        assertEquals(135f, dialAngleOf(3f, 3f), 1e-4f)
    }

    @Test
    fun theAngularDistanceTakesTheShortWayRound() {
        assertEquals(20f, dialAngularDistance(10f, 350f), 1e-4f)
        assertEquals(20f, dialAngularDistance(350f, 10f), 1e-4f)
        assertEquals(180f, dialAngularDistance(0f, 180f), 1e-4f)
        assertEquals(0f, dialAngularDistance(90f, 90f), 0f)
    }

    // ─── Minutos ─────────────────────────────────────────────────────────────

    @Test
    fun everyNotchSurvivesTheRoundTrip() {
        for (m in notches) assertEquals("$m min", m, minutesForAngle(angleForMinutes(m)))
    }

    @Test
    fun aNotchOwnsTheAnglesAroundItUpToHalfAStep() {
        // También las de los extremos: más allá del arco, el hueco satura en ellas.
        for (m in notches) {
            val angle = angleForMinutes(m)
            assertEquals("$m +", m, minutesForAngle(angle + 4.5f))
            assertEquals("$m −", m, minutesForAngle(angle - 4.5f))
        }
        // Pasada la mitad de la muesca (5°) manda la vecina.
        for (m in 35..175 step 5) {
            val angle = angleForMinutes(m)
            assertEquals("$m +", m + 5, minutesForAngle(angle + 5.1f))
            assertEquals("$m −", m - 5, minutesForAngle(angle - 5.1f))
        }
    }

    @Test
    fun theGapSaturatesAtTheNearestEndInsteadOfWrappingOrOvershooting() {
        // Mitad derecha del hueco (más cerca del final del arco): el máximo.
        assertEquals(180, minutesForAngle(150f))
        assertEquals(180, minutesForAngle(151f))
        assertEquals(180, minutesForAngle(165f))
        assertEquals(180, minutesForAngle(179.9f))
        // Centro exacto del hueco y mitad izquierda: el mínimo.
        assertEquals(30, minutesForAngle(180f))
        assertEquals(30, minutesForAngle(180.1f))
        assertEquals(30, minutesForAngle(195f))
        assertEquals(30, minutesForAngle(209f))
        assertEquals(30, minutesForAngle(210f))
    }

    @Test
    fun anAngleOutsideTheNormalTurnMeansTheSameAsItsNormalizedOne() {
        for (angle in listOf(0f, 37f, 93.75f, 150f, 170f, 205f, 285f, 341f)) {
            assertEquals(minutesForAngle(angle), minutesForAngle(angle + 360f))
            assertEquals(minutesForAngle(angle), minutesForAngle(angle - 720f))
        }
    }

    @Test
    fun nonsenseAnglesFallBackToTheMinimum() {
        assertEquals(30, minutesForAngle(Float.NaN))
        assertEquals(30, minutesForAngle(Float.POSITIVE_INFINITY))
        assertEquals(30, minutesForAngle(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun otherRangesAndStepsWorkTheSameWay() {
        assertEquals(60, minutesForAngle(0f, 30..90, 10))
        assertEquals(30, minutesForAngle(210f, 30..90, 10))
        assertEquals(90, minutesForAngle(150f, 30..90, 10))
        for (m in 30..90 step 10) {
            val angle = angleForMinutes(m, 30..90)
            assertEquals(m, minutesForAngle(angle, 30..90, 10))
        }
    }

    // ─── Ajuste a la muesca ──────────────────────────────────────────────────

    @Test
    fun minutesSnapToTheNearestNotchAndHalfWayGoesUp() {
        assertEquals(30, snapMinutes(32.4f))
        assertEquals(35, snapMinutes(32.5f))
        assertEquals(35, snapMinutes(32.6f))
        assertEquals(180, snapMinutes(179.9f))
        assertEquals(75, snapMinutes(75f))
        assertEquals(30, snapMinutes(-5f))
        // Por debajo del mínimo del reloj (los 20 y 25 min de antes) se acota al mínimo.
        assertEquals(30, snapMinutes(20f))
        assertEquals(30, snapMinutes(25f))
        assertEquals(180, snapMinutes(500f))
        assertEquals(30, snapMinutes(Float.NaN))
    }

    @Test
    fun theNotchesCountFromTheMinimumAndNeverPassTheMaximum() {
        assertEquals(22, snapMinutes(24f, 22..180, 5))
        assertEquals(27, snapMinutes(25f, 22..180, 5))
        assertEquals(180, snapMinutes(180f, 22..180, 5))
        // Un paso menor que 1 se trata como 1 y no divide por cero.
        assertEquals(23, snapMinutes(22.6f, 20..30, 0))
    }

    @Test
    fun theAccessibilityStepsMoveOneNotchAndStopAtTheEnds() {
        assertEquals(65, stepMinutes(60, +1))
        assertEquals(55, stepMinutes(60, -1))
        assertEquals(180, stepMinutes(180, +1))
        assertEquals(30, stepMinutes(30, -1))
        // Fuera de una muesca salta a la contigua en esa dirección.
        assertEquals(65, stepMinutes(62, +1))
        assertEquals(60, stepMinutes(62, -1))
        // Por debajo del rango (los 20 y 25 min de antes) se cuenta desde el mínimo y nunca baja de él.
        assertEquals(35, stepMinutes(17, +1))
        assertEquals(35, stepMinutes(25, +1))
        assertEquals(30, stepMinutes(25, -1))
        assertEquals(60, stepMinutes(62, 0))
    }

    // ─── Arrastre ────────────────────────────────────────────────────────────

    @Test
    fun theFirstTouchIsAbsoluteAndGivesTheNotchUnderTheFinger() {
        for (m in notches) {
            assertEquals("$m min", m, DialDragTracker().begin(angleForMinutes(m)))
        }
        // En el hueco, el extremo más cercano.
        assertEquals(180, DialDragTracker().begin(160f))
        assertEquals(30, DialDragTracker().begin(200f))
    }

    @Test
    fun draggingClockwiseAllTheWayRoundNeverJumpsAndStaysAtTheMaximum() {
        val tracker = DialDragTracker()
        var previous = tracker.begin(210f)
        assertEquals(30, previous)
        for (k in 1..360) {
            val value = tracker.move((210f + k) % 360f)
            assertTrue("k=$k: $previous → $value", value >= previous)
            assertTrue("k=$k saltó ${value - previous}", value - previous <= 5)
            previous = value
            if (k >= 300) assertEquals("k=$k", 180, value)
        }
        assertEquals(180, previous)
    }

    @Test
    fun draggingCounterClockwiseAllTheWayRoundNeverJumpsAndStaysAtTheMinimum() {
        val tracker = DialDragTracker()
        var previous = tracker.begin(150f)
        assertEquals(180, previous)
        for (k in 1..360) {
            val angle = ((150f - k) % 360f + 360f) % 360f
            val value = tracker.move(angle)
            assertTrue("k=$k: $previous → $value", value <= previous)
            assertTrue("k=$k saltó ${previous - value}", previous - value <= 5)
            previous = value
            if (k >= 300) assertEquals("k=$k", 30, value)
        }
        assertEquals(30, previous)
    }

    @Test
    fun theKnobWaitsAtTheEndWhileTheFingerGoesAroundTheGapAndRejoinsWhenItComesBack() {
        val tracker = DialDragTracker()
        assertEquals(180, tracker.begin(150f))
        // El dedo cruza el hueco hasta el otro lado: el pomo no se va a 30.
        for (angle in listOf(160f, 175f, 185f, 200f, 210f, 225f, 240f)) {
            assertEquals("ida a $angle", 180, tracker.move(angle))
        }
        // Y vuelve por donde vino: el pomo sigue en 180 hasta que el dedo vuelve al final del arco…
        for (angle in listOf(225f, 210f, 200f, 185f, 175f, 160f, 150f)) {
            assertEquals("vuelta a $angle", 180, tracker.move(angle))
        }
        // …y entonces lo acompaña de nuevo.
        assertTrue(tracker.move(140f) < 180)
        assertEquals(170, tracker.move(130f))
    }

    @Test
    fun startingInTheLeftHalfOfTheGapKeepsTheMinimumUntilTheFingerReachesTheArc() {
        val tracker = DialDragTracker()
        assertEquals(30, tracker.begin(200f))
        assertEquals(30, tracker.move(211f))
        assertEquals(35, tracker.move(220f))
    }

    @Test
    fun aSmallStepAcrossNorthWrapsTheShortWay() {
        val tracker = DialDragTracker()
        assertEquals(100, tracker.begin(350f))
        val before = tracker.rawPosition()
        tracker.move(10f)
        // Del 350° al 10° son 20° en sentido horario, no −340°.
        assertEquals(before + 20f, tracker.rawPosition(), 1e-3f)
    }

    @Test
    fun aFullCircleInThreeDegreeStepsFromAnyStartNeverMovesMoreThanOneNotchPerStep() {
        for (startAngle in listOf(0f, 37f, 93.75f, 150f, 170f, 205f, 285f)) {
            for (direction in listOf(1f, -1f)) {
                val tracker = DialDragTracker()
                var previous = tracker.begin(startAngle)
                for (k in 1..240) {
                    val value = tracker.move(((startAngle + direction * 3f * k) % 360f + 360f) % 360f)
                    assertTrue("inicio $startAngle dirección $direction k=$k: $previous → $value", kotlin.math.abs(value - previous) <= 5)
                    previous = value
                }
            }
        }
    }
}
