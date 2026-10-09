package com.example.kpkn.screens.onboarding.design.entreno

import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometría de los símbolos de material rediseñados, sin dibujar: el rack no tiene parte dinámica y su barra descansa
 * en los ganchos; el asa de la kettlebell mide lo que debe frente a su campana; la figura del cajón de pie mide tres
 * veces el frente de la caja y ninguna parte de ella sale del lienzo.
 */
class EquipmentSymbolGeometryTest {

    @Test
    fun theRackHasNoDynamicPartAndItsBarRestsOnTheHooks() {
        assertTrue("el rack no se mueve", !RackArt.MOVES)
        for (i in 0 until 40) assertEquals(0f, RackArt.lift(RackArt.period * i / 40f), 0f)
        // La barra descansa sobre la punta de los ganchos (y = 28,6), con medio grosor de línea por debajo de ella.
        assertEquals(27f, RackArt.BAR_Y, 0.5f)
        assertTrue(RackArt.BAR_Y + SymbolStroke.LINE / 2f <= 28.6f)
    }

    @Test
    fun theKettlebellHandleMeasuresBetweenSixAndSevenTenthsOfTheBell() {
        val ratio = 2f * KettlebellArt.HANDLE_HALF / (2f * KettlebellArt.BELL_R)
        assertTrue("asa / campana = $ratio", ratio in 0.6f..0.75f)
    }

    @Test
    fun theStandingJumperIsAtLeastThreeTimesTheBoxFront() {
        val stand = BoxArt.jumperPose(BoxArt.topRoot, crouch = 0f, tuck = 0f, arms = 0f)
        val feet = max(stand.footN.y, stand.footF.y)
        val top = stand.head.y - BoxArt.dims.head
        val height = feet - top
        assertTrue("alto de pie $height frente al frente de ${BoxArt.FRONT_H}", height >= 3f * BoxArt.FRONT_H)
    }

    @Test
    fun noPointOfTheBoxFigureLeavesItsCanvasInFortyFrames() {
        val r = BoxArt.dims.head
        for (i in 0 until 40) {
            val t = BoxArt.period * i / 40f
            BoxArt.eachFigureAt(t) { root, crouch, tuck, arms, _ ->
                val p = BoxArt.jumperPose(root, crouch, tuck, arms)
                val points = listOf(p.hip, p.kneeN, p.footN, p.kneeF, p.footF, p.sh, p.elbowN, p.handN, p.elbowF, p.handF, p.head)
                for (q in points) {
                    assertTrue("t=$t: $q fuera del lienzo", q.x in 0f..64f && q.y in 0f..64f)
                }
                assertTrue("t=$t: la cabeza sube por encima de y = 2", p.head.y - r >= 2f)
                assertTrue("t=$t: la cabeza se sale del lienzo", p.head.x - r >= 0f && p.head.x + r <= 64f)
            }
        }
    }
}
