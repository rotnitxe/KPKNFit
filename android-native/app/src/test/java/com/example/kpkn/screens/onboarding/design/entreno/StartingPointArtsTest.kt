package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.CardioBase
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los símbolos nuevos del punto de partida: ninguno se sale de su lienzo de 64 × 64 en las 8 fases de su bucle, las poses de los
 * estilos de mesa se distinguen sin leer la etiqueta y las bases de cardio van de menos a más.
 */
class StartingPointArtsTest {

    private val shapes: List<StartingPointShape> = listOf<StartingPointShape>(
        WALK_ART, JOG_ART, RUN_ART, SPRINT_ART, RUNNING_ART, HipThrustArt, JumpsArt, NoneArt,
    ) + ArmStyle.entries.map { armArtOf(it) as ArmStyleArt }

    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

    @Test
    fun everyPointOfEveryLoopStaysInsideTheCanvasInEightPhases() {
        for (shape in shapes) {
            val art = shape as SymbolArt
            assertEquals(64f, art.width, 0f)
            assertEquals(64f, art.height, 0f)
            for (k in 0 until 8) {
                val t = art.period * k / 8f
                for (p in shape.pointsAt(t)) {
                    assertTrue("${art::class.simpleName} en $t → $p", p.x in 0f..64f && p.y in 0f..64f)
                }
            }
        }
    }

    @Test
    fun everyStaticFrameIsInsideItsLoop() {
        for (shape in shapes) {
            val art = shape as SymbolArt
            assertTrue("${art::class.simpleName} restT ${art.restT}", art.restT >= 0f && art.restT < art.period)
        }
    }

    @Test
    fun theArmStylesReadDifferentlyAtRest() {
        val top = armPoseAt(ArmStyle.TOP_ROLL, 0f)
        val hook = armPoseAt(ArmStyle.HOOK, 0f)
        val press = armPoseAt(ArmStyle.PRESS, armArtOf(ArmStyle.PRESS).restT)
        val unsure = armPoseAt(ArmStyle.UNSURE, 0f)
        // Top roll: la mano propia encima de la rival; hook: debajo, en copa; press: el torso y la mano rival bajada a la mesa;
        // aún no lo sé: las dos manos juntas y sin torso.
        assertTrue("top roll: la propia encima", top.handOwn.y < top.handRival.y - 2f)
        assertTrue("hook: la propia debajo", hook.handOwn.y > hook.handRival.y + 2f)
        assertTrue("press: torso propio", press.torso.size == 3)
        assertTrue("press: la mano rival bajada a la mesa", press.handRival.y > 24f)
        assertTrue("aún no lo sé: agarradas", unsure.torso.isEmpty() && dist(unsure.handOwn, unsure.handRival) < 4f)
    }

    @Test
    fun theTopRollHandStaysAboveTheRivalHandAlongTheLoop() {
        val period = armArtOf(ArmStyle.TOP_ROLL).period
        for (k in 0 until 8) {
            val t = period * k / 8f
            val pose = armPoseAt(ArmStyle.TOP_ROLL, t)
            assertTrue("en $t la mano propia queda por encima de la rival", pose.handOwn.y < pose.handRival.y)
        }
    }

    @Test
    fun theCardioBasesGetFasterFromWalkingToSprinting() {
        val periods = CardioBase.entries.map { cardioArtOf(it).period }
        for (i in 0 until periods.size - 1) {
            assertTrue("${CardioBase.entries[i]} más lento que ${CardioBase.entries[i + 1]}", periods[i] > periods[i + 1])
        }
    }

    @Test
    fun everyAvoidGroupHasAPictogramOnTheSixtyFourCanvas() {
        for (group in AvoidGroup.entries) {
            assertEquals("$group", 64f, avoidArtOf(group).width, 0f)
        }
    }

    @Test
    fun theJumpImpactMarksAppearOnlyWhenTheFootLands() {
        val period = JumpsArt.period
        assertEquals(0f, JumpsArt.impact(period * 0.5f), 1e-4f)
        assertTrue(JumpsArt.impact(period * 0.72f) > 0.5f)
    }

    @Test
    fun theHipThrustRaisesTheHipAndLowersItAgain() {
        assertTrue(HipThrustArt.lift(0f) < 0.1f)
        assertTrue(HipThrustArt.lift(1.1f) > 0.9f)
    }

}
