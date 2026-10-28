package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * El reloj de descanso con los RINGS (LV6, F1): lo que se llena, la forma que toca en cada descanso, el total que no se reinicia al
 * ajustar el tiempo y por dónde corre el personaje. Todo puro.
 */
class RestClockMathTest {

    @Test
    fun progressIsTheElapsedPartOfTheTotal() {
        assertEquals(0f, RestClockMath.progress(totalSeconds = 249, remainingSeconds = 249), 1e-6f)
        assertEquals(88f / 249f, RestClockMath.progress(totalSeconds = 249, remainingSeconds = 161), 1e-6f)
        assertEquals(1f, RestClockMath.progress(totalSeconds = 249, remainingSeconds = 0), 1e-6f)
    }

    @Test
    fun progressNeverLeavesZeroToOne() {
        // Un restante mayor que el total (un ajuste a medias) no da progreso negativo; uno negativo no pasa de 1; total 0 no divide por 0.
        assertEquals(0f, RestClockMath.progress(totalSeconds = 60, remainingSeconds = 75), 0f)
        assertEquals(1f, RestClockMath.progress(totalSeconds = 60, remainingSeconds = -5), 0f)
        assertEquals(1f, RestClockMath.progress(totalSeconds = 0, remainingSeconds = 0), 0f)
    }

    @Test
    fun theTowerFillsOneThirdOfTheRestPerRingFromTheBottomUp() {
        // f(i) = clamp(3p − i)
        assertEquals(1f, RestClockMath.towerFill(0.5f, 0), 1e-6f)
        assertEquals(0.5f, RestClockMath.towerFill(0.5f, 1), 1e-6f)
        assertEquals(0f, RestClockMath.towerFill(0.5f, 2), 1e-6f)
        assertEquals(1f, RestClockMath.towerFill(1f / 3f + 1e-4f, 0), 1e-6f)
        assertEquals(0f, RestClockMath.towerFill(0f, 0), 1e-6f)
        assertEquals(1f, RestClockMath.towerFill(1f, 2), 1e-6f)
        // Cada anillo se lleva un tercio: la suma de lo llenado es 3p.
        for (step in 0..20) {
            val p = step / 20f
            val total = (0..2).sumOf { RestClockMath.towerFill(p, it).toDouble() }
            assertEquals(3.0 * p, total, 1e-5)
        }
    }

    @Test
    fun theActiveRingIsTheFirstOneThatIsNotFull() {
        assertEquals(0, RestClockMath.activeRing(0f))
        assertEquals(0, RestClockMath.activeRing(0.30f))
        assertEquals(1, RestClockMath.activeRing(0.34f))
        assertEquals(1, RestClockMath.activeRing(0.60f))
        assertEquals(2, RestClockMath.activeRing(0.67f))
        assertEquals(2, RestClockMath.activeRing(1f))
    }

    @Test
    fun theTrackAndTheSpiralFillWithTheRestProgress() {
        assertEquals(0.35f, RestClockMath.singleFill(0.35f), 0f)
        assertEquals(0f, RestClockMath.singleFill(-1f), 0f)
        assertEquals(1f, RestClockMath.singleFill(3f), 0f)
    }

    @Test
    fun theShapeRotatesFromOneRestToTheNextAndTheChoiceSticks() {
        // Torre → Pista → Espiral entre los descansos de la sesión (el primero tras la primera serie).
        assertEquals(RestClockShape.TOWER, RestClockMath.shapeFor(restOrdinal = 1, chosen = null))
        assertEquals(RestClockShape.TRACK, RestClockMath.shapeFor(restOrdinal = 2, chosen = null))
        assertEquals(RestClockShape.SPIRAL, RestClockMath.shapeFor(restOrdinal = 3, chosen = null))
        assertEquals(RestClockShape.TOWER, RestClockMath.shapeFor(restOrdinal = 4, chosen = null))
        assertEquals(RestClockShape.TOWER, RestClockMath.shapeFor(restOrdinal = 7, chosen = null))
        assertEquals(RestClockShape.TOWER, RestClockMath.shapeFor(restOrdinal = 0, chosen = null))
        // Lo elegido con las pestañas vale para el resto de la sesión, sea cual sea el descanso.
        for (ordinal in 0..9) {
            assertEquals(RestClockShape.SPIRAL, RestClockMath.shapeFor(restOrdinal = ordinal, chosen = RestClockShape.SPIRAL))
        }
    }

    @Test
    fun theTotalDoesNotResetWhenTheRestIsAdjustedByFifteenSeconds() {
        val startedAt = 1_000_000L
        val endsAt = startedAt + 249_000L
        assertEquals(249, RestClockMath.totalSeconds(startedAt, endsAt, activeSeconds = 249))
        // +15 s a los 88 s: el orquestador deja activeSeconds en lo que quedaba más 15 y mueve el fin 15 s; el inicio se conserva.
        val total = RestClockMath.totalSeconds(startedAt, endsAt + 15_000L, activeSeconds = 176)
        assertEquals(264, total)
        // El reloj no vuelve a cero: el progreso en ese instante es el mismo (88/264 en vez de 0/176).
        assertEquals(88f / 264f, RestClockMath.progress(total, remainingSeconds = 176), 1e-6f)
        // −15 s encoge el total en 15.
        assertEquals(234, RestClockMath.totalSeconds(startedAt, endsAt - 15_000L, activeSeconds = 146))
    }

    @Test
    fun withoutAStartTheTotalIsTheActiveTime() {
        // Descansos guardados antes de este campo (o sin fin): vale activeSeconds, y nunca menos de 1.
        assertEquals(195, RestClockMath.totalSeconds(startedAtMs = 0L, endsAtMs = 0L, activeSeconds = 195))
        assertEquals(1, RestClockMath.totalSeconds(startedAtMs = 0L, endsAtMs = 0L, activeSeconds = 0))
        assertEquals(120, WorkoutRestModalState(activeSeconds = 120).totalRestSeconds())
        assertEquals(
            300,
            WorkoutRestModalState(activeSeconds = 100, startedAtMs = 10_000L, endsAtMs = 310_000L).totalRestSeconds(),
        )
    }

    @Test
    fun theRunnerStartsWhereEachStrokeStarts() {
        val tower = RestClockMath.towerPoint(0f, 0f)
        assertEquals(280f, tower.x, 1e-3f)
        assertEquals(220f, tower.y, 1e-3f)
        val track = RestClockMath.trackPoint(0f)
        assertEquals(80f, track.x, 1e-3f)
        assertEquals(70f, track.y, 1e-3f)
        val spiral = RestClockMath.spiralPoint(0f)
        assertEquals(150f, spiral.x, 1e-3f)
        assertEquals(128f, spiral.y, 1e-3f)
    }

    @Test
    fun theSpiralEndsAtItsOuterTip() {
        val tip = RestClockMath.spiralPoint(0.999999f)
        assertEquals(150f, tip.x, 0.05f)
        assertEquals(56f, tip.y, 0.05f)
    }

    @Test
    fun theRunnerStaysOnTheRingItRunsOn() {
        RestClockMath.towerRings.forEachIndexed { index, ring ->
            for (step in 0 until 40) {
                val point = RestClockMath.towerPoint(index.toFloat(), step / 40f)
                val dx = (point.x - RestClockMath.CENTER_X) / ring.rx
                val dy = (point.y - ring.cy) / ring.ry
                assertEquals("anillo $index, paso $step", 1.0, (dx * dx + dy * dy).toDouble(), 1e-4)
            }
        }
    }

    @Test
    fun theRunnerJumpsBetweenRingsInsteadOfTeleporting() {
        // A medio camino entre dos anillos está entre los dos.
        val low = RestClockMath.towerPoint(0f, 0.25f)
        val mid = RestClockMath.towerPoint(0.5f, 0.25f)
        val high = RestClockMath.towerPoint(1f, 0.25f)
        assertTrue(mid.y < low.y && mid.y > high.y)
    }

    @Test
    fun theRunnerStaysOnTheTrack() {
        for (step in 0 until 200) {
            val p = RestClockMath.trackPoint(step / 200f)
            val onTopEdge = abs(p.y - 70f) < 1e-3f && p.x in 80f..220f
            val onBottomEdge = abs(p.y - 210f) < 1e-3f && p.x in 80f..220f
            val onRightArc = abs(hypot(p.x - 220f, p.y - 140f) - 70f) < 1e-2f && p.x >= 219.9f
            val onLeftArc = abs(hypot(p.x - 80f, p.y - 140f) - 70f) < 1e-2f && p.x <= 80.1f
            assertTrue("paso $step en (${p.x}, ${p.y})", onTopEdge || onBottomEdge || onRightArc || onLeftArc)
        }
    }

    @Test
    fun theRunnerStaysOnTheSpiral() {
        for (step in 0 until 300) {
            val p = RestClockMath.spiralPoint(step / 300f)
            // Debe estar a la distancia de algún arco respecto de su centro.
            val onSomeArc = (0 until RestClockMath.SPIRAL_ARCS).any { k ->
                val r = RestClockMath.SPIRAL_STEP * (k + 1)
                val cy = if (k % 2 == 0) 140f else 128f
                abs(hypot(p.x - 150f, p.y - cy) - r) < 1e-2f
            }
            assertTrue("paso $step en (${p.x}, ${p.y})", onSomeArc)
        }
    }

    @Test
    fun theRunnerFacesWhereItMoves() {
        // La vuelta va en sentido horario: por delante (abajo) hacia la izquierda y por detrás (arriba) hacia la derecha.
        assertEquals(-1f, RestClockMath.towerPoint(0f, 0.25f).facing, 0f)
        assertEquals(1f, RestClockMath.towerPoint(0f, 0.75f).facing, 0f)
        // En la Pista, por arriba hacia la derecha y por abajo hacia la izquierda.
        assertEquals(1f, RestClockMath.trackPoint(0.05f).facing, 0f)
        assertEquals(-1f, RestClockMath.trackPoint(0.55f).facing, 0f)
    }

    /** Lo que sobresale el corredor por encima de su punto (cabeza incluida) y a cada lado, en unidades del dibujo. */
    private val runnerUp = 30f
    private val runnerSide = 8f

    @Test
    fun everyShapeIsCenteredOnTheSameVerticalAxis() {
        RestClockShape.entries.forEach { shape ->
            val box = RestClockMath.viewBox(shape)
            assertEquals("$shape", RestClockMath.CENTER_X, box.left + box.width / 2f, 1e-3f)
        }
    }

    @Test
    fun theTowerBoxHoldsItsRingsItsHaloAndTheRunner() {
        val box = RestClockMath.viewBox(RestClockShape.TOWER)
        val halo = 5f
        RestClockMath.towerRings.forEach { ring ->
            assertTrue(RestClockMath.CENTER_X - ring.rx - ring.stroke / 2f - halo >= box.left)
            assertTrue(RestClockMath.CENTER_X + ring.rx + ring.stroke / 2f + halo <= box.right)
            assertTrue(ring.cy - ring.ry - ring.stroke / 2f - halo >= box.top)
            assertTrue(ring.cy + ring.ry + ring.stroke / 2f + halo <= box.bottom)
        }
        // El corredor, en cualquiera de los tres anillos y en cualquier punto de la vuelta (y en los saltos entre anillos).
        for (ringPos in listOf(0f, 0.5f, 1f, 1.5f, 2f)) {
            for (step in 0 until 60) {
                val p = RestClockMath.towerPoint(ringPos, step / 60f)
                assertTrue("anillo $ringPos, paso $step", p.y - runnerUp >= box.top && p.y <= box.bottom)
                assertTrue(p.x - runnerSide >= box.left && p.x + runnerSide <= box.right)
            }
        }
    }

    @Test
    fun theTrackBoxHoldsTheStadiumItsHaloAndTheRunner() {
        val box = RestClockMath.viewBox(RestClockShape.TRACK)
        val halo = 5f
        // Estadio: x de 10 a 290, y de 70 a 210, con el trazo de 20.
        assertTrue(10f - 10f - halo >= box.left)
        assertTrue(290f + 10f + halo <= box.right)
        assertTrue(70f - 10f - halo >= box.top)
        assertTrue(210f + 10f + halo <= box.bottom)
        for (step in 0 until 120) {
            val p = RestClockMath.trackPoint(step / 120f)
            assertTrue("paso $step", p.y - runnerUp >= box.top && p.y <= box.bottom)
            assertTrue(p.x - runnerSide >= box.left && p.x + runnerSide <= box.right)
        }
    }

    @Test
    fun theSpiralBoxHoldsTheSpiralItsHaloAndTheRunner() {
        val box = RestClockMath.viewBox(RestClockShape.SPIRAL)
        val halo = 5f
        // Espiral: x de 78 a 222, y de 56 a 200, con el trazo de 12.
        assertTrue(78f - 6f - halo >= box.left)
        assertTrue(222f + 6f + halo <= box.right)
        assertTrue(56f - 6f - halo >= box.top)
        assertTrue(200f + 6f + halo <= box.bottom)
        for (step in 0 until 200) {
            val p = RestClockMath.spiralPoint(step / 200f)
            assertTrue("paso $step", p.y - runnerUp >= box.top && p.y <= box.bottom)
            assertTrue(p.x - runnerSide >= box.left && p.x + runnerSide <= box.right)
        }
    }

    /** Los puntos por los que pasa el corredor en cada forma: toda la vuelta (y los saltos entre anillos de la Torre). */
    private fun runnerPoints(shape: RestClockShape): List<RestRunnerPoint> = when (shape) {
        RestClockShape.TOWER -> listOf(0f, 0.5f, 1f, 1.5f, 2f).flatMap { ring ->
            (0 until 60).map { RestClockMath.towerPoint(ring, it / 60f) }
        }
        RestClockShape.TRACK -> (0 until 120).map { RestClockMath.trackPoint(it / 120f) }
        RestClockShape.SPIRAL -> (0 until 200).map { RestClockMath.spiralPoint(it / 200f) }
    }

    @Test
    fun aBiggerRunnerNeverGetsItsHeadClippedOnAnyShape() {
        // La tarjeta dibuja al corredor a 1,4: la caja crece lo que él sobresale de más y sigue centrada.
        val scale = 1.4f
        RestClockShape.entries.forEach { shape ->
            val box = RestClockMath.viewBox(shape, scale)
            runnerPoints(shape).forEach { p ->
                assertTrue("$shape: la cabeza se corta por arriba", p.y - runnerUp * scale >= box.top)
                assertTrue(
                    "$shape: el corredor se corta a un lado",
                    p.x - runnerSide * scale >= box.left && p.x + runnerSide * scale <= box.right,
                )
            }
            assertEquals("$shape sigue centrada", RestClockMath.CENTER_X, box.left + box.width / 2f, 1e-3f)
        }
    }

    @Test
    fun theBoxOfTheRegularRunnerIsTheOneItAlwaysWas() {
        RestClockShape.entries.forEach { shape ->
            assertEquals(RestClockMath.viewBox(shape), RestClockMath.viewBox(shape, 1f))
            // Sin corredor (escala 0, la forma pequeña del feedback) tampoco encoge: la caja de siempre.
            assertEquals(RestClockMath.viewBox(shape), RestClockMath.viewBox(shape, 0f))
        }
    }

    @Test
    fun theLengthsAreTheOnesTheFillIsMeasuredWith() {
        assertEquals(2.0 * 140.0 + 2.0 * PI * 70.0, RestClockMath.trackLength.toDouble(), 1e-2)
        assertEquals(PI * 252.0, RestClockMath.spiralLength.toDouble(), 1e-2)
        // Ramanujan para una elipse casi circular se parece al círculo.
        assertEquals(2.0 * PI * 50.0, RestClockMath.ellipsePerimeter(50f, 50f).toDouble(), 1e-2)
    }

    @Test
    fun talkBackReadsTheRemainingTimeAsASentence() {
        assertEquals("Descanso: quedan 2 minutos y 41 segundos", RestClockMath.spokenRemaining(161))
        assertEquals("Descanso: quedan 1 minuto y 1 segundo", RestClockMath.spokenRemaining(61))
        assertEquals("Descanso: quedan 3 minutos", RestClockMath.spokenRemaining(180))
        assertEquals("Descanso: quedan 45 segundos", RestClockMath.spokenRemaining(45))
        assertEquals("Descanso: quedan 0 segundos", RestClockMath.spokenRemaining(-3))
    }

    @Test
    fun applyingTheSuggestedRestKeepsTheElapsedTimeInsteadOfRestartingIt() {
        // 2:00 de descanso con 1:28 transcurridos y una sugerencia de 3:00: quedan 1:32, no 3:00.
        assertEquals(92, RestClockMath.remainingAfterSuggestion(totalSeconds = 120, remainingSeconds = 32, suggestedSeconds = 180))
        // Aplicar una sugerencia más corta que lo ya transcurrido deja el descanso en 0, sin negativos.
        assertEquals(0, RestClockMath.remainingAfterSuggestion(totalSeconds = 120, remainingSeconds = 20, suggestedSeconds = 60))
        // El caso del prototipo: el total pasa a ser el sugerido y el reloj conserva lo ya hecho.
        assertEquals(92, RestClockMath.remainingAfterSuggestion(totalSeconds = 120, remainingSeconds = 32, suggestedSeconds = 180))
    }
}
