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
    fun theThreeLiftsWeighAddUpAndTheJudgesLightUpAtTheRest() {
        val art = goalArt(TrainingGoalProfile.POWERLIFTING) as PowerliftingArt
        val w = FloatArray(3)
        for (i in 0..120) {
            art.weights(art.period * i / 120f, w)
            assertEquals(1f, w[0] + w[1] + w[2], 0.0001f)
        }
        // En reposo (sentadilla arriba, 1,5 s) las luces de los jueces ya están encendidas; antes de la primera repetición, apagadas.
        assertTrue("luces en reposo ${art.judgeLit(art.restT)}", art.judgeLit(art.restT) > 0.9f)
        assertEquals(0f, art.judgeLit(0.5f), 0.0001f)
    }

    @Test
    fun theBarsGrowOneByOneAndTheDumbbellLiftsWithThem() {
        val art = goalArt(TrainingGoalProfile.STRENGTH_MUSCLE) as StrengthMuscleArt
        assertEquals(0f, art.barGrow(0.1f, 0), 0.0001f)
        assertEquals(1f, art.barGrow(0.6f, 0), 0.0001f)
        assertEquals(0f, art.barGrow(0.6f, 2), 0.0001f)
        assertEquals(1f, art.barGrow(1.25f, 2), 0.0001f)
        // La mancuerna sube a −25° con las barras y se queda; al final del bucle vuelve a su cuadro inicial.
        assertEquals(-25f, art.tilt(2f), 0.01f)
        assertEquals(0f, art.tilt(art.period), 0.01f)
        // En reposo (2,0 s) está todo visible: la flecha y el rótulo ya terminaron.
        assertEquals(1f, art.arrow(art.restT), 0.0001f)
        assertEquals(1f, art.label(art.restT), 0.0001f)
    }

    @Test
    fun theHeartBeatsTwiceAndTheDumbbellRisesOnEachBeat() {
        val art = goalArt(TrainingGoalProfile.STRENGTH_CARDIO) as StrengthCardioArt
        // Dos latidos por bucle: el pico de cada uno cae a 0,35 y a 0,75 del período.
        assertTrue(art.beat(art.period * 0.35f) > 0.99f)
        assertTrue(art.beat(art.period * 0.75f) > 0.99f)
        // Entre latidos y en reposo, el corazón está quieto.
        assertEquals(0f, art.beat(art.period * 0.55f), 0.0001f)
        assertEquals(0f, art.beat(art.restT), 0.0001f)
    }

    @Test
    fun theJumperCrouchesJumpsLandsAndTheLoopCloses() {
        val art = goalArt(TrainingGoalProfile.FUNCTIONAL_HEALTH) as FunctionalArt
        assertEquals(0f, art.airOf(0f), 0.0001f)
        assertEquals(0f, art.airOf(0.3f), 0.0001f)
        assertEquals(1f, art.airOf(0.75f), 0.0001f)
        assertTrue("en reposo casi arriba: ${art.airOf(art.restT)}", art.airOf(art.restT) > 0.85f)
        // En el aire los pies están más altos que de pie.
        assertTrue(art.pose(0.75f).ftL.y < art.pose(0f).ftL.y)
        // El bucle cierra en la pose de pie y ninguna articulación se sale del lienzo.
        assertEquals(art.pose(0f).ftL.y, art.pose(art.period).ftL.y, 0.001f)
        for (i in 0..180) {
            val p = art.pose(art.period * i / 180f)
            for (o in listOf(p.head, p.shL, p.shR, p.elL, p.haL, p.elR, p.haR, p.hip, p.knL, p.ftL, p.knR, p.ftR)) {
                assertTrue("fuera del lienzo: $o", o.x in 0f..64f && o.y in 0f..64f)
            }
        }
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
    fun theStrongmanStoneRisesToTheChestAndStaysInsideTheCanvas() {
        val art = goalArt(TrainingGoalProfile.STRONGMAN) as StrongmanArt
        var highest = 99f
        var lowest = 0f
        for (i in 0..320) {
            val c = art.stoneCenter(art.period * i / 320f)
            // La roca mide ~11 de radio: ninguna parte se sale del lienzo de 64.
            assertTrue("izquierda ${c.x}", c.x - 12.5f >= 0f)
            assertTrue("derecha ${c.x}", c.x + 12.5f <= 64f)
            assertTrue("arriba ${c.y}", c.y - 12.5f >= 0f)
            assertTrue("abajo ${c.y}", c.y + 12.5f <= 64f)
            highest = minOf(highest, c.y)
            lowest = maxOf(lowest, c.y)
        }
        // Sube al menos 15 unidades desde el suelo.
        assertTrue("recorrido ${lowest - highest}", lowest - highest > 15f)
        // En el suelo al empezar y sobre la plataforma al terminar el empuje (2,6 s); en reposo se ve arriba (lo más reconocible).
        assertEquals(0f, art.lift(0f), 0.0001f)
        assertEquals(1f, art.lift(2.6f), 0.0001f)
        assertTrue("reposo con la piedra arriba", art.lift(art.restT) > 0.95f)
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
