package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El estado del arrastre sin dedos ni pantalla: levantar, seguir al dedo (con sus límites), saber sobre qué día está,
 * soltar o cancelar, y el resorte de cada ficha. Es lo mismo que ejecuta el gesto, así que fija el comportamiento del
 * tablero sin necesitar Compose.
 */
class WeekLayoutDragStateTest {

    // Siete columnas de 100 con 10 de hueco, cabecera de 40 y cuerpo de 120.
    private val geometry = WeekStripGeometry(
        order = slotOrder(1),
        colWidth = 100f,
        gap = 10f,
        headerHeight = 40f,
        bodyHeight = 120f,
        dragRange = 16f,
        cancelDistance = 50f,
    )

    private fun newState(placed: Map<Int, String> = mapOf(1 to "a", 3 to "b", 5 to "c")): WeekLayoutDragState {
        val state = WeekLayoutDragState()
        state.geometry = geometry
        state.occupants = placed
        for ((day, id) in placed) state.motionFor(id, geometry.home(day)).target = geometry.home(day)
        return state
    }

    /** El dedo sobre el centro del glifo de la ficha en su ranura. */
    private fun grabPoint(day: Int) = geometry.home(day) + Offset(50f, 60f)

    // ------------------------------------------------------------ geometría

    @Test
    fun theGeometryPlacesEachSlotOnItsColumn() {
        assertEquals(110f, geometry.pitch, 0f)
        assertEquals(760f, geometry.contentWidth, 0f)
        assertEquals(160f, geometry.height, 0f)
        assertEquals(Offset(0f, 40f), geometry.home(1))
        assertEquals(Offset(220f, 40f), geometry.home(3))
        assertEquals(Offset(660f, 40f), geometry.home(7))
        assertEquals(7, geometry.slotRects.size)
        assertEquals(100f, geometry.slotRects.getValue(2).left - geometry.slotRects.getValue(1).left - 10f, 0f)
    }

    @Test
    fun theGeometryFollowsTheWeekStart() {
        val thursday = WeekStripGeometry(slotOrder(4), 100f, 10f, 40f, 120f, 16f, 50f)
        assertEquals(Offset(0f, 40f), thursday.home(4))
        assertEquals(Offset(440f, 40f), thursday.home(1))
        assertEquals(Offset(660f, 40f), thursday.home(3))
        // Un día desconocido cae en la primera ranura (nunca fuera de la tira).
        assertEquals(Offset(0f, 40f), thursday.home(99))
    }

    @Test
    fun anEmptyGeometryHasNothing() {
        assertEquals(0f, WeekStripGeometry.Empty.contentWidth, 0f)
        assertTrue(WeekStripGeometry.Empty.slotRects.isEmpty())
    }

    // ------------------------------------------------------------ tocar

    @Test
    fun dayAtAndSessionAtDayTellWhoIsUnderTheFinger() {
        val state = newState()
        assertEquals(3, state.dayAt(Offset(250f, 80f)))
        assertNull(state.dayAt(Offset(105f, 80f)))
        assertEquals("b", state.sessionAtDay(state.dayAt(Offset(250f, 80f))))
        assertNull(state.sessionAtDay(2))
        assertNull(state.sessionAtDay(null))
    }

    // ------------------------------------------------------------ levantar

    @Test
    fun liftingMarksTheSessionAndItsOrigin() {
        val state = newState()
        state.selectedId = "c"
        assertTrue(state.lift("b", grabPoint(3)))
        assertEquals("b", state.liftedId)
        assertEquals(3, state.originDay)
        assertEquals(3, state.hoverDay)
        assertNull("levantar deshace la selección", state.selectedId)
        assertTrue(state.motionFor("b", Offset.Zero).dragging)
        assertTrue(state.motionFor("b", Offset.Zero).raised)
    }

    @Test
    fun liftingEndsThePressFeedback() {
        val state = newState()
        state.pressedId = "a"
        assertTrue(state.lift("a", grabPoint(1)))
        assertNull(state.pressedId)
    }

    @Test
    fun onlyOneSessionCanBeLiftedAndOnlyAPlacedOne() {
        val state = newState()
        assertFalse("sesión que no está colocada", state.lift("zzz", Offset(10f, 10f)))
        assertFalse("sin ficha conocida", state.lift("d", Offset(10f, 10f)))
        assertTrue(state.lift("a", grabPoint(1)))
        assertFalse("ya hay otra levantada", state.lift("b", grabPoint(3)))
        assertEquals("a", state.liftedId)
    }

    // ------------------------------------------------------------ arrastrar

    @Test
    fun theFichaFollowsTheFingerKeepingTheGrabPoint() {
        val state = newState()
        state.lift("a", grabPoint(1))
        val motion = state.motionFor("a", Offset.Zero)
        // Un desplazamiento de 220 a la derecha lleva la ficha a la ranura 3.
        state.dragTo(grabPoint(1) + Offset(220f, 0f))
        assertEquals(geometry.home(3), motion.pos)
        assertEquals(grabPoint(1) + Offset(220f, 0f), state.finger)
    }

    @Test
    fun theFichaStaysInsideTheStripSideways() {
        val state = newState()
        state.lift("a", grabPoint(1))
        val motion = state.motionFor("a", Offset.Zero)
        state.dragTo(Offset(5000f, 100f))
        assertEquals(660f, motion.pos.x, 0f)
        state.dragTo(Offset(-5000f, 100f))
        assertEquals(0f, motion.pos.x, 0f)
    }

    @Test
    fun theFichaOnlyLeavesItsRowByTheDragRange() {
        val state = newState()
        state.lift("a", grabPoint(1))
        val motion = state.motionFor("a", Offset.Zero)
        state.dragTo(grabPoint(1) + Offset(0f, -500f))
        assertEquals(40f - 16f, motion.pos.y, 0f)
        state.dragTo(grabPoint(1) + Offset(0f, 500f))
        assertEquals(40f + 16f, motion.pos.y, 0f)
    }

    @Test
    fun theFichaCanGoUpLessThanDownToKeepTheHeaderClear() {
        val tight = WeekStripGeometry(slotOrder(1), 100f, 10f, 40f, 120f, dragRange = 18f, cancelDistance = 50f, dragUp = 6f)
        val state = WeekLayoutDragState()
        state.geometry = tight
        state.occupants = mapOf(1 to "a")
        val motion = state.motionFor("a", tight.home(1))
        motion.target = tight.home(1)
        assertTrue(state.lift("a", tight.home(1) + Offset(50f, 60f)))
        state.dragTo(tight.home(1) + Offset(50f, -500f))
        assertEquals(40f - 6f, motion.pos.y, 0f)
        state.dragTo(tight.home(1) + Offset(50f, 500f))
        assertEquals(40f + 18f, motion.pos.y, 0f)
    }

    @Test
    fun theDayUnderTheFichaIsWhereItsCenterIs() {
        val state = newState()
        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(220f, 0f))
        assertEquals(3, state.hoverDay)
        // A medio camino entre dos columnas decide dónde queda el centro de la ficha.
        state.dragTo(grabPoint(1) + Offset(110f * 3f - 40f, 0f))   // centro en 50 + 290 = 340 → columna 4 (330..430)
        assertEquals(4, state.hoverDay)
        state.dragTo(grabPoint(1) + Offset(110f * 3f - 70f, 0f))   // centro en 310 → columna 3 (220..320)
        assertEquals(3, state.hoverDay)
    }

    @Test
    fun theGapBetweenColumnsStillHasADestination() {
        val state = newState()
        state.lift("a", grabPoint(1))
        // Centro en 104 (el hueco entre la 1, 0..100, y la 2, 110..210): gana la columna más cercana.
        state.dragTo(grabPoint(1) + Offset(54f, 0f))
        assertEquals(1, state.hoverDay)
        state.dragTo(grabPoint(1) + Offset(59f, 0f))
        assertEquals(2, state.hoverDay)
    }

    @Test
    fun pullingAwayFromTheRowLeavesNoDestination() {
        val state = newState()
        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(110f, 40f))
        assertEquals(2, state.hoverDay)
        state.dragTo(grabPoint(1) + Offset(110f, 51f))
        assertNull("más allá de la distancia de cancelación", state.hoverDay)
        state.dragTo(grabPoint(1) + Offset(110f, -51f))
        assertNull(state.hoverDay)
        state.dragTo(grabPoint(1) + Offset(110f, -49f))
        assertEquals(2, state.hoverDay)
    }

    @Test
    fun dragWithoutALiftedFichaDoesNothing() {
        val state = newState()
        state.dragTo(Offset(300f, 100f))
        assertNull(state.liftedId)
        assertNull(state.hoverDay)
        assertEquals(geometry.home(1), state.motionFor("a", Offset.Zero).pos)
    }

    @Test
    fun scrollingUnderAStillFingerKeepsTheFichaOnTheFinger() {
        val state = newState()
        state.lift("a", grabPoint(1))
        // El contenido se desplazó 110 hacia la izquierda bajo el dedo quieto: el dedo está 110 más a la derecha en el contenido.
        state.nudge(110f)
        assertEquals(grabPoint(1) + Offset(110f, 0f), state.finger)
        assertEquals(2, state.hoverDay)
        assertEquals(geometry.home(2), state.motionFor("a", Offset.Zero).pos)
        // Sin ficha o sin desplazamiento no hace nada.
        state.cancel()
        state.nudge(500f)
        assertEquals(grabPoint(1) + Offset(110f, 0f), state.finger)
    }

    // ------------------------------------------------------------ soltar y cancelar

    @Test
    fun dropOnAnotherDayReportsIt() {
        val state = newState()
        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(220f, 0f))
        val result = state.drop()
        assertNotNull(result)
        assertEquals("a", result!!.sessionId)
        assertEquals(3, result.toDay)
        assertNull(state.liftedId)
        assertNull(state.hoverDay)
        val motion = state.motionFor("a", Offset.Zero)
        assertFalse(motion.dragging)
        // Sigue donde se soltó (el resorte la lleva luego): no da un salto.
        assertEquals(geometry.home(3), motion.pos)
    }

    @Test
    fun dropOnTheOwnDayOrOutsideReportsNoDestination() {
        val state = newState()
        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(10f, 5f))
        assertNull(state.drop()!!.toDay)

        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(220f, 90f))
        assertNull(state.hoverDay)
        val outside = state.drop()
        assertEquals("a", outside!!.sessionId)
        assertNull(outside.toDay)
    }

    @Test
    fun dropWithNothingLiftedIsNothing() {
        assertNull(newState().drop())
    }

    @Test
    fun cancelLetsGoOfTheFichaAndClearsTheDestination() {
        val state = newState()
        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(220f, 0f))
        state.cancel()
        assertNull(state.liftedId)
        assertNull(state.hoverDay)
        assertFalse(state.motionFor("a", Offset.Zero).dragging)
        // Cancelar dos veces, o sin nada levantado, no rompe nada.
        state.cancel()
        assertNull(state.liftedId)
    }

    @Test
    fun everyLiftAndDropWakesTheSpringLoop() {
        val state = newState()
        val start = state.epoch
        state.lift("a", grabPoint(1))
        val lifted = state.epoch
        state.drop()
        assertTrue(lifted > start)
        assertTrue(state.epoch > lifted)
    }

    @Test
    fun afterDroppingYouCanLiftAgain() {
        val state = newState()
        assertTrue(state.lift("a", grabPoint(1)))
        state.drop()
        assertTrue(state.lift("c", grabPoint(5)))
        assertEquals(5, state.originDay)
    }

    @Test
    fun forgettingFichasDropsTheirMotion() {
        val state = newState()
        state.retainMotions(setOf("a"))
        assertEquals(1, state.allMotions.size)
        // Una ficha olvidada vuelve a nacer en el sitio que se le dé.
        assertEquals(Offset(7f, 7f), state.motionFor("b", Offset(7f, 7f)).pos)
    }

    // ------------------------------------------------------------ resorte

    @Test
    fun theSpringArrivesAtItsTargetWithoutWildOvershoot() {
        val motion = FichaMotion(Offset(0f, 40f))
        motion.target = Offset(440f, 40f)
        var t = 0f
        var maxX = 0f
        while (!motion.atRest && t < 3f) {
            motion.step(1f / 60f)
            maxX = maxOf(maxX, motion.pos.x)
            t += 1f / 60f
            assertTrue(motion.pos.x.isFinite() && motion.pos.y.isFinite())
        }
        assertTrue("llegó en $t s", t < 1.2f)
        assertEquals(motion.target, motion.pos)
        assertTrue("rebote de $maxX", maxX < 440f * 1.12f)
        assertFalse(motion.raised)
    }

    @Test
    fun theSpringIsStableWithLongFramesAndHugeJumps() {
        val motion = FichaMotion(Offset.Zero)
        motion.target = Offset(100000f, -100000f)
        repeat(600) { motion.step(0.5f) }
        assertTrue(motion.pos.x.isFinite() && motion.pos.y.isFinite())
        assertEquals(motion.target, motion.pos)
    }

    @Test
    fun aFichaAtItsTargetIsAtRestAndDoesNotMove() {
        val motion = FichaMotion(Offset(10f, 20f))
        assertTrue(motion.atRest)
        motion.step(0.016f)
        assertEquals(Offset(10f, 20f), motion.pos)
    }

    @Test
    fun aDraggedFichaIsNotMovedByTheSpring() {
        val motion = FichaMotion(Offset(0f, 0f))
        motion.target = Offset(300f, 0f)
        motion.beginDrag()
        motion.dragTo(Offset(40f, 5f))
        assertTrue("llevada por el dedo cuenta como en reposo", motion.atRest)
        motion.step(0.016f)
        assertEquals(Offset(40f, 5f), motion.pos)
        motion.snapToTarget()
        assertEquals("no se coloca mientras la lleva el dedo", Offset(40f, 5f), motion.pos)
        motion.endDrag()
        assertFalse(motion.atRest)
        while (!motion.atRest) motion.step(1f / 60f)
        assertEquals(Offset(300f, 0f), motion.pos)
    }

    @Test
    fun snapToTargetPlacesTheFichaAtOnce() {
        val motion = FichaMotion(Offset(0f, 0f))
        motion.target = Offset(220f, 40f)
        motion.snapToTarget()
        assertEquals(Offset(220f, 40f), motion.pos)
        assertTrue(motion.atRest)
    }

    @Test
    fun aFichaStaysRaisedUntilItSettles() {
        val motion = FichaMotion(Offset(0f, 40f))
        motion.beginDrag()
        motion.dragTo(Offset(150f, 50f))
        motion.target = Offset(220f, 40f)
        motion.endDrag()
        assertTrue(motion.raised)
        motion.step(1f / 60f)
        assertTrue("sigue de camino", motion.raised)
        while (!motion.atRest) motion.step(1f / 60f)
        motion.settleIfIdle()
        assertFalse(motion.raised)
        assertTrue(abs(motion.pos.x - 220f) < 0.01f)
    }
}
