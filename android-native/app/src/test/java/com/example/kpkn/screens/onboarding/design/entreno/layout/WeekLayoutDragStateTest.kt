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

    // Siete filas de 64 en una lista de 300 de ancho; la ficha empieza en 40 y su asa son los últimos 44.
    private val geometry = WeekListGeometry(
        order = slotOrder(1),
        width = 300f,
        rowHeight = 64f,
        fichaLeft = 40f,
        handleWidth = 44f,
        dragRange = 12f,
        cancelDistance = 50f,
    )

    private fun newState(placed: Map<Int, String> = mapOf(1 to "a", 3 to "b", 5 to "c")): WeekLayoutDragState {
        val state = WeekLayoutDragState()
        state.geometry = geometry
        state.occupants = placed
        for ((day, id) in placed) state.motionFor(id, geometry.home(day)).target = geometry.home(day)
        return state
    }

    /** El dedo sobre el glifo de la ficha en su fila (24 a la derecha del inicio de la ficha y a media altura). */
    private fun grabPoint(day: Int) = geometry.home(day) + Offset(24f, 32f)

    // ------------------------------------------------------------ geometría

    @Test
    fun theGeometryPlacesEachRowBelowThePreviousOne() {
        assertEquals(448f, geometry.height, 0f)
        assertEquals(260f, geometry.fichaWidth, 0f)
        assertEquals(Offset(40f, 0f), geometry.home(1))
        assertEquals(Offset(40f, 128f), geometry.home(3))
        assertEquals(Offset(40f, 384f), geometry.home(7))
        assertEquals(7, geometry.slotRects.size)
        assertEquals(64f, geometry.slotRects.getValue(2).top, 0f)
        assertEquals(300f, geometry.slotRects.getValue(2).width, 0f)
        assertEquals(64f, geometry.slotRects.getValue(2).height, 0f)
    }

    @Test
    fun theGeometryFollowsTheWeekStart() {
        val thursday = WeekListGeometry(slotOrder(4), 300f, 64f, 40f, 44f, 12f, 50f)
        assertEquals(Offset(40f, 0f), thursday.home(4))
        assertEquals(Offset(40f, 256f), thursday.home(1))
        assertEquals(Offset(40f, 384f), thursday.home(3))
        // Un día desconocido cae en la primera fila (nunca fuera de la lista).
        assertEquals(Offset(40f, 0f), thursday.home(99))
    }

    @Test
    fun anEmptyGeometryHasNothing() {
        assertEquals(0f, WeekListGeometry.Empty.height, 0f)
        assertTrue(WeekListGeometry.Empty.slotRects.isEmpty())
        assertFalse(WeekListGeometry.Empty.isHandle(0f))
    }

    @Test
    fun theHandleIsTheEndOfTheRow() {
        assertTrue(geometry.isHandle(299f))
        assertTrue(geometry.isHandle(256f))
        assertFalse(geometry.isHandle(255f))
        assertFalse(geometry.isHandle(0f))
    }

    // ------------------------------------------------------------ tocar

    @Test
    fun dayAtAndSessionAtDayTellWhoIsUnderTheFinger() {
        val state = newState()
        // El miércoles es la tercera fila (128..192).
        assertEquals(3, state.dayAt(Offset(150f, 140f)))
        assertEquals("b", state.sessionAtDay(state.dayAt(Offset(150f, 140f))))
        assertNull("por debajo de la lista no hay día", state.dayAt(Offset(150f, 449f)))
        assertNull("a la derecha de la lista tampoco", state.dayAt(Offset(301f, 10f)))
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
    fun theFichaFollowsTheFingerDownTheListKeepingTheGrabPoint() {
        val state = newState()
        state.lift("a", grabPoint(1))
        val motion = state.motionFor("a", Offset.Zero)
        // Un desplazamiento de 128 hacia abajo lleva la ficha a la fila 3.
        state.dragTo(grabPoint(1) + Offset(0f, 128f))
        assertEquals(geometry.home(3), motion.pos)
        assertEquals(grabPoint(1) + Offset(0f, 128f), state.finger)
    }

    @Test
    fun theFichaOnlyMovesUpAndDownNeverSideways() {
        val state = newState()
        state.lift("a", grabPoint(1))
        val motion = state.motionFor("a", Offset.Zero)
        state.dragTo(grabPoint(1) + Offset(150f, 64f))
        assertEquals(40f, motion.pos.x, 0f)
        assertEquals(64f, motion.pos.y, 0f)
        state.dragTo(grabPoint(1) + Offset(-500f, 64f))
        assertEquals(40f, motion.pos.x, 0f)
    }

    @Test
    fun theFichaOnlyLeavesTheListByTheDragRange() {
        val state = newState()
        state.lift("a", grabPoint(1))
        val motion = state.motionFor("a", Offset.Zero)
        state.dragTo(grabPoint(1) + Offset(0f, -500f))
        assertEquals(-12f, motion.pos.y, 0f)
        state.dragTo(grabPoint(1) + Offset(0f, 5000f))
        assertEquals(448f - 64f + 12f, motion.pos.y, 0f)
    }

    @Test
    fun theDayUnderTheFichaIsWhereItsCenterIs() {
        val state = newState()
        state.lift("a", grabPoint(1))
        // El centro de la ficha queda a 100 + 32 = 132 → tercera fila (128..192).
        state.dragTo(grabPoint(1) + Offset(0f, 100f))
        assertEquals(3, state.hoverDay)
        // A 90 + 32 = 122 → segunda fila (64..128).
        state.dragTo(grabPoint(1) + Offset(0f, 90f))
        assertEquals(2, state.hoverDay)
        // Cerca del límite entre dos filas (128) gana la de centro más cercano.
        state.dragTo(grabPoint(1) + Offset(0f, 95f))
        assertEquals(2, state.hoverDay)
        state.dragTo(grabPoint(1) + Offset(0f, 97f))
        assertEquals(3, state.hoverDay)
    }

    @Test
    fun theFirstAndLastRowsAreReachableAtTheEndsOfTheList() {
        val state = newState(mapOf(1 to "a", 7 to "c"))
        state.lift("c", grabPoint(7))
        state.dragTo(Offset(64f, -10f))
        assertEquals(1, state.hoverDay)
        state.dragTo(Offset(64f, 460f))
        assertEquals(7, state.hoverDay)
    }

    @Test
    fun pullingAwayFromTheListLeavesNoDestination() {
        val state = newState()
        state.lift("a", grabPoint(1))
        // El dedo (que lleva la ficha por su centro) a más de la distancia de cancelación por encima de la lista.
        state.dragTo(Offset(64f, -51f))
        assertNull("más allá de la distancia de cancelación", state.hoverDay)
        state.dragTo(Offset(64f, -49f))
        assertEquals(1, state.hoverDay)
        // Y por debajo.
        state.dragTo(Offset(64f, 448f + 51f))
        assertNull(state.hoverDay)
        state.dragTo(Offset(64f, 448f + 49f))
        assertEquals(7, state.hoverDay)
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
        // La página se desplazó 128 hacia arriba bajo el dedo quieto: el dedo está 128 más abajo en la lista.
        state.nudge(128f)
        assertEquals(grabPoint(1) + Offset(0f, 128f), state.finger)
        assertEquals(3, state.hoverDay)
        assertEquals(geometry.home(3), state.motionFor("a", Offset.Zero).pos)
        // Sin ficha o sin desplazamiento no hace nada.
        state.cancel()
        state.nudge(500f)
        assertEquals(grabPoint(1) + Offset(0f, 128f), state.finger)
    }

    // ------------------------------------------------------------ soltar y cancelar

    @Test
    fun dropOnAnotherDayReportsIt() {
        val state = newState()
        state.lift("a", grabPoint(1))
        state.dragTo(grabPoint(1) + Offset(0f, 128f))
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
        state.dragTo(grabPoint(1) + Offset(5f, 8f))
        assertNull(state.drop()!!.toDay)

        state.lift("a", grabPoint(1))
        state.dragTo(Offset(64f, -90f))
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
        state.dragTo(grabPoint(1) + Offset(0f, 128f))
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
        val motion = FichaMotion(Offset(40f, 0f))
        motion.target = Offset(40f, 384f)
        var t = 0f
        var maxY = 0f
        while (!motion.atRest && t < 3f) {
            motion.step(1f / 60f)
            maxY = maxOf(maxY, motion.pos.y)
            t += 1f / 60f
            assertTrue(motion.pos.x.isFinite() && motion.pos.y.isFinite())
        }
        assertTrue("llegó en $t s", t < 1.2f)
        assertEquals(motion.target, motion.pos)
        assertTrue("rebote de $maxY", maxY < 384f * 1.12f)
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
        motion.target = Offset(0f, 300f)
        motion.beginDrag()
        motion.dragTo(Offset(5f, 40f))
        assertTrue("llevada por el dedo cuenta como en reposo", motion.atRest)
        motion.step(0.016f)
        assertEquals(Offset(5f, 40f), motion.pos)
        motion.snapToTarget()
        assertEquals("no se coloca mientras la lleva el dedo", Offset(5f, 40f), motion.pos)
        motion.endDrag()
        assertFalse(motion.atRest)
        while (!motion.atRest) motion.step(1f / 60f)
        assertEquals(Offset(0f, 300f), motion.pos)
    }

    @Test
    fun snapToTargetPlacesTheFichaAtOnce() {
        val motion = FichaMotion(Offset(0f, 0f))
        motion.target = Offset(40f, 128f)
        motion.snapToTarget()
        assertEquals(Offset(40f, 128f), motion.pos)
        assertTrue(motion.atRest)
    }

    @Test
    fun aFichaStaysRaisedUntilItSettles() {
        val motion = FichaMotion(Offset(40f, 0f))
        motion.beginDrag()
        motion.dragTo(Offset(40f, 100f))
        motion.target = Offset(40f, 128f)
        motion.endDrag()
        assertTrue(motion.raised)
        motion.step(1f / 60f)
        assertTrue("sigue de camino", motion.raised)
        while (!motion.atRest) motion.step(1f / 60f)
        motion.settleIfIdle()
        assertFalse(motion.raised)
        assertTrue(abs(motion.pos.y - 128f) < 0.01f)
    }
}
