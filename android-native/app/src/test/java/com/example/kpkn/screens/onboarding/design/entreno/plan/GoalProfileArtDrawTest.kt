package com.example.kpkn.screens.onboarding.design.entreno.plan

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Humo del dibujo: los diez símbolos de objetivo (en su versión de lista y en la de portada) se pintan sobre un lienzo real
 * en muchos instantes de su bucle y con la selección a medias, sin lanzar excepciones. No mira píxeles (eso son las
 * capturas del emulador): garantiza que ningún cuadro del bucle revienta con una geometría degenerada, y fija las
 * propiedades que el resto del código da por hechas (lienzo de 64 × 64, bucle > 0, cuadro estático dentro del bucle).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GoalProfileArtDrawTest {

    private fun newCanvas(side: Int): Canvas =
        Canvas(android.graphics.Canvas(Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)))

    private val arts: List<GoalArt> =
        TrainingGoalProfile.entries.map { goalArt(it) } + TrainingGoalProfile.entries.map { goalCoverArt(it) }

    @Test
    fun everySymbolDrawsAtEveryInstantAndSelection() {
        val side = 200
        val canvas = newCanvas(side)
        val scope = CanvasDrawScope()
        val pen = SymbolPen()
        var frames = 0
        for (art in arts) {
            for (sel in listOf(0f, 0.4f, 1f)) {
                for (i in 0..40) {
                    val t = art.period * i / 41f
                    scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(side.toFloat(), side.toFloat())) {
                        pen.begin(this, sel, art.accent)
                        val k = side / maxOf(art.width, art.height)
                        withTransform({ scale(k, k, Offset.Zero) }) {
                            art.drawStatic(pen)
                            art.drawDynamic(pen, t)
                            // El fundido de soltar dibuja la pose congelada y la estática con opacidad de grupo.
                            pen.ga = 0.5f
                            art.drawDynamic(pen, art.restT)
                            pen.ga = 1f
                        }
                    }
                    frames++
                }
            }
        }
        assertEquals(arts.size * 3 * 41, frames)
    }

    @Test
    fun theCanvasIs64SquareAndTheLoopIsPositive() {
        for (art in arts) {
            assertEquals(64f, art.width, 0f)
            assertEquals(64f, art.height, 0f)
            assertTrue("bucle de ${art.javaClass.simpleName}", art.period > 0f)
            assertTrue("cuadro estático dentro del bucle de ${art.javaClass.simpleName}", art.restT >= 0f && art.restT < art.period)
        }
    }

    @Test
    fun theTenProfilesHaveTheirOwnSymbolAndTheCoverUsesAThinnerLine() {
        val listArts = TrainingGoalProfile.entries.map { goalArt(it) }
        assertEquals(10, listArts.map { it.javaClass }.toSet().size)
        for (profile in TrainingGoalProfile.entries) {
            assertEquals(goalArt(profile).javaClass, goalCoverArt(profile).javaClass)
            // La portada es otra instancia con la línea más fina, no la misma del símbolo.
            assertTrue(goalArt(profile) !== goalCoverArt(profile))
        }
        assertEquals(goalCoverArt(TrainingGoalProfile.STRENGTH_MUSCLE), goalCoverArt(null))
    }

    @Test
    fun theSymbolsKeepTheirDisciplineAccent() {
        for (profile in TrainingGoalProfile.entries) {
            assertEquals(goalAccent(profile), goalArt(profile).accent)
        }
    }

    @Test
    fun theWeightliftingPoseKeepsTheBarOverheadAtTheLockout() {
        val art = goalArt(TrainingGoalProfile.WEIGHTLIFTING) as WeightliftingArt
        assertTrue(art.barY(art.restT) < 15f)
        assertTrue(art.barY(0f) > 30f)
        assertTrue("sentadilla de recepción", art.shoulderY(1.15f) > art.shoulderY(0f))
    }

    @Test
    fun thePowerliftingBarRisesSlowlyAndStaysInsideTheCanvas() {
        val art = goalArt(TrainingGoalProfile.POWERLIFTING) as PowerliftingArt
        var maxY = 0f
        var minY = 99f
        for (i in 0..200) {
            val y = art.barY(art.period * i / 200f)
            maxY = maxOf(maxY, y)
            minY = minOf(minY, y)
        }
        // Los discos miden ±25 alrededor de la barra: no se salen del lienzo de 64.
        assertTrue("arriba $minY", minY - 25f >= 0f)
        assertTrue("abajo $maxY", maxY + 25f <= 64f)
    }

    @Test
    fun theHeartBeatsFourTimesPerLoopAndTheTraceCrossesTheCanvas() {
        val art = goalArt(TrainingGoalProfile.FUNCTIONAL_HEALTH) as FunctionalArt
        assertEquals(4f, art.period / 0.7f, 0.0001f)
        assertEquals(4f, art.headX(0f), 0.001f)
        assertEquals(60f, art.headX(art.period), 0.001f)
        // El latido sube y baja: hay instantes con y sin pulso.
        val beats = (0..140).map { art.beat(it * 0.02f) }
        assertTrue(beats.max() > 0.8f)
        assertTrue(beats.min() < 0.15f)
    }

    @Test
    fun theArmwrestlingPulseNeverLeavesTheCanvas() {
        val art = goalArt(TrainingGoalProfile.ARMWRESTLING) as ArmwrestlingArt
        var left = 0f
        var right = 0f
        for (i in 0..240) {
            val s = art.sway(art.period * i / 240f)
            assertTrue("el puño se desplaza $s", kotlin.math.abs(s) <= 5.4f)
            left = minOf(left, s)
            right = maxOf(right, s)
        }
        // Se inclina de verdad a los dos lados.
        assertTrue(left < -3f && right > 3f)
    }

    @Test
    fun theStrongmanCarrierAlternatesItsSteps() {
        val art = goalArt(TrainingGoalProfile.STRONGMAN) as StrongmanArt
        var positive = 0
        var negative = 0
        var prev = art.step(0.0001f)
        var crossings = 0
        for (i in 1..600) {
            val s = art.step(art.period * i / 600f)
            assertTrue(s in -1.0001f..1.0001f)
            if (s > 0f) positive++ else if (s < 0f) negative++
            if ((s > 0f) != (prev > 0f)) crossings++
            prev = s
        }
        assertTrue(positive > 250 && negative > 250)
        // Tres pasos por bucle: seis cambios de pie.
        assertTrue("cambios de pie: $crossings", crossings in 5..7)
        // La piedra sube y baja dentro del bucle.
        assertEquals(0f, art.lift(0f), 0.0001f)
        assertEquals(1f, art.lift(1.5f), 0.0001f)
    }

    @Test
    fun theSagCurveMatchesItsEndpointsAndItsCentre() {
        assertEquals(10f, sagY(2f, 2f, 62f, 8f, 2f), 0.001f)
        assertEquals(10f, sagY(62f, 2f, 62f, 8f, 2f), 0.001f)
        assertEquals(8f, sagY(32f, 2f, 62f, 8f, 2f), 0.001f)
        // Con la barra curvada hacia abajo en los extremos, el extremo izquierdo sube hacia el centro (pendiente negativa).
        assertTrue(sagDeg(10f, 2f, 62f, 2f) < 0f)
        assertTrue(sagDeg(54f, 2f, 62f, 2f) > 0f)
        assertEquals(0f, sagDeg(32f, 2f, 62f, 2f), 0.001f)
    }
}
