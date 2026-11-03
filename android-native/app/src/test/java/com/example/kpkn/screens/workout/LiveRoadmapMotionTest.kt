package com.example.kpkn.screens.workout

import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las animaciones del roadmap contra los `@keyframes` de `lv.css` (`lv2-*`), en el 0, 25, 50, 75 y 100 % de su duración: la curva
 * (.2,.8,.2,1) rige cada intervalo, el retardo y el relleno son los de la regla. Valores esperados calculados a mano desde el CSS.
 */
class LiveRoadmapMotionTest {
    private val e = { x: Float -> KpknMotion.Estandar.transform(x) }
    private fun pose(a: KpknAnimation, t: Float): KpknPose = KpknPose().also { a.poseAt(t, it) }
    private val fractions = listOf(0f, .25f, .5f, .75f, 1f)

    @Test fun slidesEnterFromTheSideOfTheMovementIn220Ms() {
        listOf(
            Triple(LiveRoadmapMotion.SlideLeft, -14f, 0f), Triple(LiveRoadmapMotion.SlideRight, 14f, 0f), Triple(LiveRoadmapMotion.SlideUp, 0f, 8f),
        ).forEach { (anim, dx, dy) ->
            assertEquals(220, anim.duration.ms)
            for (f in fractions) {
                val t = 220f * f
                val p = pose(anim, t)
                val k = e(f)
                assertEquals("alfa a $f", k, p.alpha, 1e-3f)
                assertEquals("x a $f", dx * (1f - k), p.translateXDp, 1e-3f)
                assertEquals("y a $f", dy * (1f - k), p.translateYDp, 1e-3f)
            }
        }
    }

    @Test fun theNumberRollsFrom60PercentOfItsHeightIn260Ms() {
        assertEquals(260, LiveRoadmapMotion.Roll.duration.ms)
        for (f in fractions.dropLast(1)) {
            val p = pose(LiveRoadmapMotion.Roll, 260f * f)
            assertEquals(e(f), p.alpha, 1e-3f)
            assertEquals(.6f * (1f - e(f)), p.translateYFraction, 1e-3f)
        }
    }

    @Test fun theCheckIsDrawnIn360MsAfter60MsOfDelayAndStaysHiddenBeforeIt() {
        val anim = LiveRoadmapMotion.Check
        assertEquals(360, anim.duration.ms); assertEquals(60, anim.delay.ms)
        assertEquals(1f, pose(anim, 0f)[KpknChannel.DashOffset], 1e-4f)   // relleno `backwards`: oculto durante el retardo
        assertEquals(1f, pose(anim, 59f)[KpknChannel.DashOffset], 1e-3f)
        for (f in fractions.drop(1).dropLast(1)) {
            assertEquals(1f - e(f), pose(anim, 60f + 360f * f)[KpknChannel.DashOffset], 1e-3f)
        }
        assertFalse(pose(anim, 421f).active)
        assertEquals(0f, pose(anim, 421f)[KpknChannel.DashOffset], 0f)
    }

    @Test fun theWipeFillsTheButtonIn70PercentAndFadesOutInTheRest() {
        val anim = LiveRoadmapMotion.Wipe
        assertEquals(550, anim.duration.ms)
        val start = pose(anim, 0f)
        assertEquals(0f, start[KpknChannel.WidthFraction], 1e-4f); assertEquals(.9f, start.alpha, 1e-4f)
        val half = pose(anim, 550f * .35f)  // mitad del primer tramo (0 → 70 %)
        assertEquals(e(.5f), half[KpknChannel.WidthFraction], 1e-3f); assertEquals(.9f, half.alpha, 1e-4f)
        val full = pose(anim, 550f * .7f)
        assertEquals(1f, full[KpknChannel.WidthFraction], 1e-3f); assertEquals(.9f, full.alpha, 1e-3f)
        val fade = pose(anim, 550f * .85f)  // mitad del segundo tramo (70 → 100 %)
        assertEquals(1f, fade[KpknChannel.WidthFraction], 1e-4f); assertEquals(.9f * (1f - e(.5f)), fade.alpha, 1e-3f)
        assertEquals(0f, pose(anim, 549.9f).alpha, 2e-3f)
        assertFalse(pose(anim, 551f).active)
    }

    @Test fun theProvisionalSegmentPulsesInEightyMs() {
        val anim = LiveRoadmapMotion.Tick
        assertEquals(80, anim.duration.ms)
        val start = pose(anim, 0f)
        assertEquals(1.12f, start.scaleX, 1e-4f); assertEquals(1.3f, start.scaleY, 1e-4f)
        val mid = pose(anim, 40f)
        assertEquals(1.12f + (1f - 1.12f) * e(.5f), mid.scaleX, 1e-3f); assertEquals(1.3f + (1f - 1.3f) * e(.5f), mid.scaleY, 1e-3f)
        assertFalse(pose(anim, 81f).active)
    }

    @Test fun routeChipsEnterStaggeredBy40MsFrom160Ms() {
        for (index in 0..3) {
            val anim = LiveRoadmapMotion.chip(index)
            val delay = 160 + 40 * index
            assertEquals(delay, anim.delay.ms); assertEquals(220, anim.duration.ms)
            val before = pose(anim, delay - 1f)           // antes del retardo: el estilo del elemento (opacity 0, translateY 6)
            assertEquals(0f, before.alpha, 0f); assertEquals(6f, before.translateYDp, 0f)
            val mid = pose(anim, delay + 110f)
            assertEquals(e(.5f), mid.alpha, 1e-3f); assertEquals(6f * (1f - e(.5f)), mid.translateYDp, 1e-3f)
            val after = pose(anim, delay + 500f)          // relleno `forwards`
            assertEquals(1f, after.alpha, 0f); assertEquals(0f, after.translateYDp, 0f)
            assertTrue(after.active)
        }
    }

    @Test fun reducedMotionJumpsToTheFinalStateWithoutAnimating() {
        val p = KpknPose()
        LiveRoadmapMotion.SlideLeft.poseAt(0f, p, reducedMotion = true)
        assertEquals(1f, p.alpha, 0f); assertEquals(0f, p.translateXDp, 0f)
    }

    @Test fun curvesAndDurationsAreTheOnesOfTheCanvas() {
        assertEquals(700, LiveRoadmapMotion.RingMs.ms); assertEquals(250, LiveRoadmapMotion.DrainMs.ms)
        assertEquals(420, LiveRoadmapMotion.FillMs.ms); assertEquals(280, LiveRoadmapMotion.SegmentMs.ms)
        assertEquals(120, LiveRoadmapMotion.SegmentScrubMs.ms); assertEquals(90, LiveRoadmapMotion.PressMs.ms)
    }
}
