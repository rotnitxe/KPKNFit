package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.TrainingPlace
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La parte pura de los símbolos de Entreno (sin dibujar): tiempo de bucle, curvas, cinemática inversa y las poses
 * de las figuras. Lo que no puede salirse del lienzo ni estirarse al moverse se comprueba muestreando cada bucle.
 */
class EntrenoSymbolMathTest {

    private val allArts: List<SymbolArt> =
        TrainingPlace.entries.map(::placeArt) + EquipmentSymbolId.entries.map(::equipmentArt)

    private fun samples(period: Float, n: Int = 120): List<Float> = (0 until n).map { period * it / n }

    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

    // ------------------------------------------------------------ tiempo

    @Test
    fun wrapLoopKeepsTimeInsideOnePeriod() {
        assertEquals(0.5f, wrapLoop(0.5f, 4f), 1e-6f)
        assertEquals(0.5f, wrapLoop(4.5f, 4f), 1e-5f)
        assertEquals(3.5f, wrapLoop(-0.5f, 4f), 1e-5f)
        assertEquals(0f, wrapLoop(4f, 4f), 0f)
        for (t in listOf(-123.4f, -1e-9f, 0f, 3.9999f, 4f, 1e6f)) {
            val w = wrapLoop(t, 4f)
            assertTrue("$t -> $w", w >= 0f && w < 4f)
        }
    }

    @Test
    fun invalidTimesAndPeriodsGiveZero() {
        assertEquals(0f, wrapLoop(Float.NaN, 4f), 0f)
        assertEquals(0f, wrapLoop(Float.POSITIVE_INFINITY, 4f), 0f)
        assertEquals(0f, wrapLoop(1f, 0f), 0f)
        assertEquals(0f, wrapLoop(1f, -2f), 0f)
        assertEquals(0f, wrapLoop(1f, Float.NaN), 0f)
        assertEquals(1.2f, loopTime(1.2f, Float.NaN, 4f), 1e-6f)
    }

    @Test
    fun aSymbolThatStartsRunningContinuesFromItsStaticFrame() {
        for (art in allArts) {
            assertEquals(art.restT, loopTime(art.restT, 0f, art.period), 1e-5f)
            // Y un instante después sigue de ahí, sin saltos de pose.
            val next = loopTime(art.restT, 0.016f, art.period)
            assertEquals(art.restT + 0.016f, if (next < art.restT) next + art.period else next, 1e-4f)
        }
    }

    @Test
    fun everyStaticFrameLivesInsideItsLoop() {
        for (art in allArts) {
            assertTrue("${art::class.simpleName}: periodo", art.period > 0f)
            assertTrue("${art::class.simpleName}: restT=${art.restT}", art.restT >= 0f && art.restT < art.period)
        }
    }

    @Test
    fun theCanvasesAreTheContractSizes() {
        TrainingPlace.entries.forEach {
            assertEquals(120f, placeArt(it).width, 0f)
            assertEquals(170f, placeArt(it).height, 0f)
        }
        EquipmentSymbolId.entries.forEach {
            assertEquals(64f, equipmentArt(it).width, 0f)
            assertEquals(64f, equipmentArt(it).height, 0f)
        }
    }

    @Test
    fun equipmentAccentsRotateThroughTheFiveModuleColours() {
        val accents = EquipmentSymbolId.entries.map { equipmentArt(it).accent }.toSet()
        assertEquals(
            setOf(
                SymbolPalette.musculo, SymbolPalette.energia, SymbolPalette.ok, SymbolPalette.columna, SymbolPalette.mente,
            ),
            accents,
        )
        // Y una fila de tres símbolos vecinos nunca repite acento.
        val ordered = EquipmentSymbolId.entries.map { equipmentArt(it).accent }
        ordered.windowed(3).forEach { assertEquals(it.toString(), 3, it.toSet().size) }
    }

    @Test
    fun theScenesUseTheirModuleAccent() {
        assertEquals(SymbolPalette.musculo, placeArt(TrainingPlace.GYM).accent)
        assertEquals(SymbolPalette.energia, placeArt(TrainingPlace.HOME).accent)
        assertEquals(SymbolPalette.ok, placeArt(TrainingPlace.PUBLIC).accent)
    }

    // ------------------------------------------------------------ curvas

    @Test
    fun pingPongGoesUpAndBackOnce() {
        assertEquals(0f, pingPong(0f), 1e-6f)
        assertEquals(1f, pingPong(0.5f), 1e-6f)
        assertEquals(0f, pingPong(1f), 1e-5f)
    }

    @Test
    fun riseHoldFallRisesHoldsAndFalls() {
        assertEquals(0f, riseHoldFall(0f, 1f, 2f, 3f, 4f), 0f)
        assertEquals(0.5f, riseHoldFall(1.5f, 1f, 2f, 3f, 4f), 1e-6f)
        assertEquals(1f, riseHoldFall(2.5f, 1f, 2f, 3f, 4f), 0f)
        assertEquals(0.5f, riseHoldFall(3.5f, 1f, 2f, 3f, 4f), 1e-6f)
        assertEquals(0f, riseHoldFall(5f, 1f, 2f, 3f, 4f), 0f)
    }

    /** Cada bucle debe cerrar donde empieza: nada salta al volver a `t = 0`. */
    @Test
    fun loopsCloseWhereTheyStart() {
        val eps = 1e-3f
        fun closes(name: String, period: Float, f: (Float) -> Float) {
            assertEquals(name, f(0f), f(period - eps), 0.05f)
        }
        closes("gym", GymScene.period) { GymScene.lift(it) }
        closes("home", HomeScene.period) { HomeScene.down(it) }
        closes("park", ParkScene.period) { ParkScene.pull(it) }
        closes("barbell", BarbellArt.period) { BarbellArt.lift(it) }
        closes("rack", RackArt.period) { RackArt.lift(it) }
        closes("bench", BenchArt.period) { BenchArt.press(it) }
        closes("dumbbells", DumbbellsArt.period) { DumbbellsArt.curl(it) }
        closes("kettlebell", KettlebellArt.period) { KettlebellArt.swingDeg(it) / 100f }
        closes("cable", CableArt.period) { CableArt.pull(it) }
        closes("machines", MachinesArt.period) { MachinesArt.rise(it) }
        closes("smith", SmithArt.period) { SmithArt.lift(it) }
        closes("pullup", PullUpBarArt.period) { PullUpBarArt.pull(it) }
        closes("parallel", ParallelBarsArt.period) { ParallelBarsArt.dip(it) }
        closes("rings", RingsArt.period) { RingsArt.swingDeg(it, 0f) / 13f }
        closes("rings lagged", RingsArt.period) { RingsArt.swingDeg(it, 0.3f) / 13f }
        closes("bands", BandsArt.period) { BandsArt.stretch(it) }
        closes("ball", BallArt.period) { BallArt.height(it) / 20f }
        closes("box ground", BoxArt.period) { BoxArt.groundAlpha(it) }
        closes("box top", BoxArt.period) { BoxArt.topAlpha(it) }
        closes("bodyweight open", BodyweightArt.period) { BodyweightArt.spread(it) }
        closes("bodyweight hop", BodyweightArt.period) { BodyweightArt.hop(it) / 5f }
    }

    // ------------------------------------------------------------ cinemática inversa

    @Test
    fun ikKeepsBothSegmentLengths() {
        val a = Offset(10f, 20f)
        for (target in listOf(Offset(30f, 40f), Offset(12f, 45f), Offset(-5f, 25f), Offset(25f, 18f))) {
            for (sign in listOf(-1f, 1f)) {
                val mid = ik(a, target, 21f, 21f, sign)
                assertEquals(21f, dist(a, mid), 1e-3f)
                assertEquals(21f, dist(mid, target), 1e-2f)
            }
        }
    }

    @Test
    fun ikStretchesTowardAnUnreachableTargetWithoutBreaking() {
        val a = Offset(0f, 0f)
        val mid = ik(a, Offset(100f, 0f), 10f, 10f, 1f)
        assertTrue(mid.x.isFinite() && mid.y.isFinite())
        assertEquals(10f, dist(a, mid), 1e-3f)
        // Sobre el mismo punto no hay NaN.
        val onTop = ik(a, a, 10f, 10f, -1f)
        assertTrue(onTop.x.isFinite() && onTop.y.isFinite())
    }

    @Test
    fun ikBendsTheKneeForwardAndTheElbowBackwardInAProfileFacingRight() {
        val hip = Offset(50f, 100f)
        val foot = Offset(50f, 140f)
        assertTrue("rodilla hacia delante", ik(hip, foot, 21f, 21f, -1f).x > 50f)
        assertTrue("codo hacia atrás", ik(hip, foot, 21f, 21f, 1f).x < 50f)
    }

    // ------------------------------------------------------------ poses

    private fun joints(p: Pose) = listOf(p.hip, p.kneeN, p.footN, p.kneeF, p.footF, p.sh, p.head, p.elbowN, p.handN, p.elbowF, p.handF)

    private fun assertInside(name: String, points: List<Offset>, w: Float, h: Float) {
        for (p in points) {
            assertTrue("$name: $p no es finito", p.x.isFinite() && p.y.isFinite())
            assertTrue("$name: $p fuera del lienzo", p.x in -1f..(w + 1f) && p.y in -1f..(h + 1f))
        }
    }

    @Test
    fun theLifterKeepsItsLimbLengthsAndItsFeetOnTheFloor() {
        val d = GymScene.dims
        for (t in samples(GymScene.period)) {
            val p = GymScene.pose(GymScene.lift(t))
            assertInside("gym@$t", joints(p) + Offset(GymScene.BAR_X, GymScene.barY(GymScene.lift(t))), 120f, 170f)
            assertEquals("muslo @$t", d.thigh, dist(p.hip, p.kneeN), 0.05f)
            assertEquals("espinilla @$t", d.shin, dist(p.kneeN, p.footN), 0.8f)
            assertEquals("tronco @$t", d.torso, dist(p.hip, p.sh), 0.05f)
            assertEquals("brazo @$t", d.uarm, dist(p.sh, p.elbowN), 0.05f)
            assertEquals("pie en la tarima", 145.8f, p.footN.y, 1e-4f)
        }
    }

    @Test
    fun theDeadliftGoesFromAHingeToStandingTall() {
        val bottom = GymScene.pose(0f)
        val top = GymScene.pose(1f)
        assertTrue("arranca con las caderas atrás", bottom.hip.x < bottom.footN.x - 15f)
        assertTrue("termina erguido", abs(top.hip.x - top.footN.x) < 6f)
        assertTrue("los hombros suben", top.sh.y < bottom.sh.y - 25f)
        assertTrue("la barra sube", GymScene.barY(1f) < GymScene.barY(0f) - 25f)
    }

    @Test
    fun thePushUpPivotsOnTheToesAndTheHandsStayPut() {
        val d = HomeScene.dims
        val body = d.thigh + d.shin + d.torso
        var handX: Float? = null
        for (t in samples(HomeScene.period)) {
            val p = HomeScene.pose(HomeScene.down(t))
            assertInside("home@$t", joints(p), 120f, 170f)
            assertEquals("cuerpo recto @$t", body, dist(p.footN, p.sh), 0.05f)
            handX = handX ?: p.handN.x
            assertEquals("manos fijas @$t", handX, p.handN.x, 0.001f)
            assertEquals("manos en la esterilla", 146.5f, p.handN.y, 0f)
        }
    }

    @Test
    fun thePullUpHangsFromTheBarAndEndsWithTheChinOverIt() {
        val hang = ParkScene.pose(0f, 0f)
        val top = ParkScene.pose(1f, 0f)
        assertEquals(34f, hang.haL.y, 0f)
        assertEquals(34f, top.haR.y, 0f)
        assertTrue("sube la cabeza", top.head.y < hang.head.y - 15f)
        assertTrue("la barbilla pasa de la barra", top.head.y + ParkScene.dims.head <= 36f)
        for (t in samples(ParkScene.period)) {
            val p = ParkScene.pose(ParkScene.pull(t), 1.6f)
            val all = listOf(p.head, p.shL, p.shR, p.elL, p.haL, p.elR, p.haR, p.hip, p.knL, p.ftL, p.knR, p.ftR)
            assertInside("park@$t", all, 120f, 170f)
        }
    }

    @Test
    fun theSmallFiguresOfTheEquipmentStayInsideTheirCanvas() {
        for (t in samples(BoxArt.period, 200)) {
            val crouch = (1f - (t / BoxArt.period)).coerceIn(0f, 1f)
            val p = BoxArt.jumperPose(Offset(20f, 40f), crouch, tuck = 0.5f, arms = 0.5f)
            assertInside("jumper@$t", joints(p), 64f, 64f)
        }
        // El salto empieza en el suelo y acaba sobre el cajón.
        assertEquals(8f, BoxArt.flightRoot(0f).x, 1e-4f)
        assertEquals(58f, BoxArt.flightRoot(0f).y, 1e-4f)
        assertEquals(37f, BoxArt.flightRoot(1f).x, 1e-4f)
        assertEquals(28.4f, BoxArt.flightRoot(1f).y, 1e-4f)
        // En el vuelo hay una figura a la vez: ni la del suelo ni la de arriba; fuera de él, siempre hay una a la vista.
        for (t in samples(BoxArt.period, 340)) {
            if (t > 0.76f && t < 1.44f) {
                assertEquals(0f, BoxArt.groundAlpha(t), 0f)
                assertEquals(0f, BoxArt.topAlpha(t), 0f)
            } else if (t < 0.74f || t > 1.46f) {
                assertTrue("alguna figura visible @$t", BoxArt.groundAlpha(t) + BoxArt.topAlpha(t) > 0.5f)
            }
        }
    }

    @Test
    fun theRunnerKeepsItsFeetOnTheBelt() {
        val dims = FigureDims(7.4f, 7.4f, 10.5f, 5.2f, 5f, 3f)
        for (t in samples(CardioArt.period)) {
            val p = runPose(dims, TAU * t / CardioArt.period, 24f, 46f)
            assertInside("cardio@$t", joints(p), 64f, 64f)
            // La planta más baja toca la cinta (con el pequeño rebote de la zancada) y ninguna la atraviesa.
            val lowest = maxOf(p.footN.y, p.footF.y)
            assertTrue("pie @$t: $lowest", lowest <= 46.001f && lowest >= 45.4f)
        }
    }

    @Test
    fun theBouncingBallOnlyTouchesTheGroundAtTheEndOfEachBounce() {
        assertEquals(0f, BallArt.height(0f), 1e-6f)
        assertEquals(20f, BallArt.height(BallArt.period / 2f), 1e-4f)
        for (t in samples(BallArt.period)) assertTrue(BallArt.height(t) in 0f..20f)
    }

    // ------------------------------------------------------------ pincel y reloj

    @Test
    fun theBrushTonesFollowTheSelection() {
        val pen = SymbolPen()
        val scope = CanvasDrawScope()
        pen.begin(scope, 0f, SymbolPalette.musculo)
        // Sin seleccionar el acento es tinta (el símbolo entero se atenúa como capa) y no hay ayudas de movimiento.
        assertEquals(SymbolPalette.ink, pen.accent)
        assertEquals(0f, pen.motion.alpha, 0f)
        assertEquals(1f, pen.ink.alpha, 0f)
        assertEquals(SymbolPalette.SOFT, pen.soft.alpha, 0.01f)
        pen.begin(scope, 1f, SymbolPalette.musculo)
        assertEquals(SymbolPalette.musculo, pen.accent)
        assertEquals(SymbolPalette.MOTION, pen.motion.alpha, 0.01f)
        // A medias, el acento está entre la tinta y el color de módulo.
        pen.begin(scope, 0.5f, SymbolPalette.musculo)
        assertTrue(pen.accent != SymbolPalette.ink && pen.accent != SymbolPalette.musculo)
    }

    @Test
    fun aSymbolRunRemembersWhereItStartedAndWhereItWasLeft() {
        val run = SymbolRun()
        run.update(selected = true, now = 3f)
        assertEquals(3f, run.startAt, 0f)
        run.update(selected = true, now = 4f)
        assertEquals("seguir seleccionado no reinicia el bucle", 3f, run.startAt, 0f)
        run.update(selected = false, now = 5.5f)
        assertEquals(5.5f, run.freezeAt, 0f)
        run.update(selected = false, now = 6f)
        assertEquals("seguir sin seleccionar no mueve el punto de congelado", 5.5f, run.freezeAt, 0f)
        run.update(selected = true, now = 9f)
        assertEquals("al volver a seleccionar arranca de nuevo", 9f, run.startAt, 0f)
    }

    @Test
    fun descriptionsAreSpokenInSpanish() {
        assertEquals("Gimnasio, seleccionado", symbolDescription("Gimnasio", true))
        assertEquals("Gimnasio, sin seleccionar", symbolDescription("Gimnasio", false))
    }
}
