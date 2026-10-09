package com.example.kpkn.screens.onboarding.design.entreno.plan

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.entreno.FigPose
import com.example.kpkn.screens.onboarding.design.entreno.LiftPoses
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Poses de los símbolos de disciplina: en 40 cuadros de cada período, los huesos mantienen su largo (±0,5 u), los pies no se
 * hunden bajo el suelo y ninguna articulación sale del lienzo de 64. Además, los cuatro símbolos nuevos se dibujan en muchos
 * instantes (lista y portada) para que ningún cuadro reviente.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DisciplinePosesTest {

    private val fig = FigPose()

    /** Cuadros de un levantamiento: `u(t)` da la fase en cada uno de los [n] instantes del período [period]. */
    private fun liftFrames(lift: LiftMark, period: Float, u: (Float) -> Float, n: Int = 40): List<ProfileJoints> =
        (0 until n).map { i -> ProfileJoints().also { solveLiftCanvas(lift, u(period * i / n), fig, it) } }

    /** Ningún hueso cambia de largo más de 1 u entre cuadros (±0,5 u alrededor de su media). */
    private fun assertBonesKeepTheirLength(name: String, frames: List<ProfileJoints>) {
        val count = frames.first().bones().size
        for (b in 0 until count) {
            val lengths = frames.map { f -> f.bones()[b].let { (a, c) -> (c - a).getDistance() } }
            val spread = lengths.maxOrNull()!! - lengths.minOrNull()!!
            assertTrue("$name: el hueso $b cambia $spread u", spread <= 1f)
        }
    }

    private fun assertInsideCanvas(name: String, frames: List<ProfileJoints>) {
        for (f in frames) for (o in f.joints()) {
            assertTrue("$name: $o fuera del lienzo", o.x in 0f..64f && o.y in 0f..64f)
        }
    }

    @Test
    fun theLiftFiguresKeepTheirBonesAndTheirFeetOnTheFloor() {
        val pl = PowerliftingArt(1f)
        val pb = PowerbuildingFlexArt(1f)
        val groundY = liftToCanvas(Offset(0f, LiftPoses.GROUND)).y
        assertEquals(60f, groundY, 0.001f)
        val sets = listOf(
            "sentadilla" to liftFrames(LiftMark.SQUAT, pl.period, pl::squatU),
            "banca" to liftFrames(LiftMark.BENCH, pl.period, pl::benchU),
            "peso muerto" to liftFrames(LiftMark.DEADLIFT, pl.period, pl::deadliftU),
            "peso muerto del powerbuilding" to liftFrames(LiftMark.DEADLIFT, pb.period, pb::deadliftU),
        )
        for ((name, frames) in sets) {
            assertBonesKeepTheirLength(name, frames)
            assertInsideCanvas(name, frames)
            for (f in frames) {
                assertEquals("$name: pie cercano en el suelo", groundY, f.footN.y, 0.01f)
                assertEquals("$name: pie lejano en el suelo", groundY, f.footF.y, 0.01f)
            }
        }
    }

    @Test
    fun theLiftDiscsStayInsideTheCanvasAtEveryPhase() {
        val pl = PowerliftingArt(1f)
        for (lift in listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT)) {
            for (i in 0..200) {
                val u = i / 200f
                val f = ProfileJoints().also { solveLiftCanvas(lift, u, fig, it) }
                // El disco mide el radio de la barra (≈ 5,9 u en el lienzo de 64) alrededor de su centro.
                for (v in listOf(f.bar.x - LIFT_PLATE_R, f.bar.x + LIFT_PLATE_R, f.bar.y - LIFT_PLATE_R, f.bar.y + LIFT_PLATE_R)) {
                    assertTrue("$lift u=$u: disco fuera del lienzo ($v)", v in 0f..64f)
                }
            }
        }
        assertTrue(pl.period > 0f)
    }

    @Test
    fun theStrongmanKeepsItsBonesItsFeetAndItsStoneInside() {
        val art = StrongmanArt(1f)
        val p = FloatArray(11)
        val frames = (0 until 40).map { i -> ProfileJoints().also { strongPose(art.period * i / 40f, p, it) } }
        assertBonesKeepTheirLength("strongman", frames)
        assertInsideCanvas("strongman", frames)
        for (f in frames) {
            assertTrue("pie cercano bajo el suelo: ${f.footN.y}", f.footN.y <= SM_GROUND + 0.01f)
            assertTrue("pie lejano bajo el suelo: ${f.footF.y}", f.footF.y <= SM_GROUND + 0.01f)
        }
        for (i in 0 until 40) {
            val c = art.stoneCenter(art.period * i / 40f)
            assertTrue("piedra fuera del lienzo: $c", c.x - STONE_RADIUS >= 0f && c.x + STONE_RADIUS <= 64f)
            assertTrue("piedra fuera del lienzo: $c", c.y - STONE_RADIUS >= 0f && c.y + STONE_RADIUS <= 64f)
        }
    }

    @Test
    fun theBodybuilderArmsKeepTheirLengthAsTheyChangePose() {
        val art = BodybuildingArt(1f)
        val out = FloatArray(8)
        val shL = Offset(22f, 20f)
        val shR = Offset(42f, 20f)
        for (i in 0 until 40) {
            art.armsAt(art.period * i / 40f, out)
            val eL = Offset(out[0], out[1])
            val hL = Offset(out[2], out[3])
            val eR = Offset(out[4], out[5])
            val hR = Offset(out[6], out[7])
            assertEquals("brazo superior izquierdo", 8.5f, (eL - shL).getDistance(), 0.5f)
            assertEquals("antebrazo izquierdo", 9.5f, (hL - eL).getDistance(), 0.5f)
            assertEquals("brazo superior derecho", 8.5f, (eR - shR).getDistance(), 0.5f)
            assertEquals("antebrazo derecho", 9.5f, (hR - eR).getDistance(), 0.5f)
            for (o in listOf(eL, hL, eR, hR)) assertTrue("brazo fuera del lienzo: $o", o.x in 0f..64f && o.y in 0f..64f)
        }
    }

    @Test
    fun theFourDisciplinesDrawAtEveryInstantInListAndCover() {
        val arts: List<GoalArt> = listOf(
            PowerbuildingFlexArt(1f), BodybuildingArt(1f), StrongmanArt(1f), PowerliftingArt(1f),
            PowerbuildingFlexArt(COVER_LINE_SCALE), BodybuildingArt(COVER_LINE_SCALE),
            StrongmanArt(COVER_LINE_SCALE), PowerliftingArt(COVER_LINE_SCALE),
        )
        val side = 200
        val canvas = Canvas(android.graphics.Canvas(Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)))
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
    fun theLiftMappingKeepsTheCanvasScaleAndTheFloorAt60() {
        // Los puntos de LiftPoses entran en el lienzo de 64: la escala y el traslado dejan el suelo en y = 60.
        assertEquals(0.8f, liftToCanvas(Offset.Zero).x, 0.001f)
        assertEquals(60f, liftToCanvas(Offset(0f, LiftPoses.GROUND)).y, 0.001f)
        assertTrue(liftToCanvas(Offset(LiftPoses.CANVAS_W, 0f)).x <= 64f)
    }
}
