package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewClassifierTest {
    private fun track(
        movement: SynthMovement,
        yaw: Double,
        noise: SynthNoise = SynthNoise(),
        seed: Long = 42L,
        camera: SynthCamera = SynthCamera(yawDeg = yaw),
    ): PoseTrackV2 = SyntheticLifter.generate(
        SynthConfig(movement, camera = camera, script = repScript(3), noise = noise, seed = seed),
    ).track

    private fun classify(t: PoseTrackV2, roll: Double = 0.0): ViewEstimate =
        ViewClassifier.classify(FrameGeometry(t, rollDeg = roll), 0 until t.frames.size)

    private fun expected(psi: Int): ViewKind = when {
        psi <= 25 -> ViewKind.FRONT
        psi >= 65 -> ViewKind.SIDE
        else -> ViewKind.OBLIQUE
    }

    private fun sweep(noise: SynthNoise, seed: Long): Pair<Int, Int> {
        var counted = 0
        var correct = 0
        for (psi in 0..90 step 5) {
            val est = classify(track(SynthMovement.SQUAT, psi.toDouble(), noise, seed))
            val skip = psi in 20..30 || psi in 60..70 // ±5° alrededor de las fronteras de 25° y 65°
            val ok = est.kind == expected(psi)
            println(
                "ψ=%2d  clase=%-7s esperada=%-7s ψ̂=%5.1f  ψ2D=%5.1f  ψworld=%5.1f  fiable=%s%s".format(
                    psi, est.kind, expected(psi), est.psiDeg ?: Double.NaN, est.psi2dDeg ?: Double.NaN,
                    est.psiWorldDeg ?: Double.NaN, est.reliable, if (skip) "  (fuera de la cuenta)" else if (ok) "" else "  <-- ERROR",
                ),
            )
            if (!skip) {
                counted++
                if (ok) correct++
            }
        }
        return Pair(correct, counted)
    }

    @Test
    fun sweepOfTheViewAngleFromFrontToSideClassifiesAtLeastNinetyFivePercent() {
        println("barrido con el ruido por defecto (semilla 42):")
        val (correct, counted) = sweep(SynthNoise(), 42L)
        println("aciertos: $correct de $counted")
        assertTrue("$correct de $counted", correct >= 0.95 * counted)
        println("barrido sin ruido:")
        val (correctClean, countedClean) = sweep(SynthNoise.NONE, 42L)
        assertTrue("$correctClean de $countedClean", correctClean >= 0.95 * countedClean)
        // Con otras semillas también.
        for (seed in listOf(1L, 2L, 3L)) {
            val (c, n) = sweep(SynthNoise(), seed)
            assertTrue("semilla $seed: $c de $n", c >= 0.95 * n)
        }
    }

    @Test
    fun sideAndFrontReportTheirAnglesAndAreReliable() {
        val side = classify(track(SynthMovement.SQUAT, 90.0))
        assertEquals(ViewKind.SIDE, side.kind)
        // ψ_2D sale algo bajo con un atleta de solo el 24 % del alto (el ruido engorda |S_L − S_R|); ψ_world no.
        assertEquals(90.0, side.psi2dDeg!!, 12.0)
        assertEquals(90.0, side.psiWorldDeg!!, 5.0)
        assertEquals(90.0, side.psiDeg!!, 8.0)
        assertTrue(side.reliable)
        val front = classify(track(SynthMovement.SQUAT, 0.0))
        assertEquals(ViewKind.FRONT, front.kind)
        assertEquals(0.0, front.psi2dDeg!!, 8.0)
        assertEquals(0.0, front.psiWorldDeg!!, 6.0)
        assertTrue(front.reliable)
        // Del otro lado también (ψ = −90 es el lado derecho: el mismo ángulo).
        assertEquals(ViewKind.SIDE, classify(track(SynthMovement.SQUAT, -90.0)).kind)
        assertEquals(ViewKind.OBLIQUE, classify(track(SynthMovement.SQUAT, -45.0)).kind)
    }

    @Test
    fun theBenchIsLyingFromTheSideAndAlsoObliquely() {
        for (yaw in listOf(90.0, -90.0, 60.0)) {
            val est = classify(track(SynthMovement.BENCH, yaw))
            assertEquals("ψ=$yaw", ViewKind.LYING, est.kind)
            assertTrue(est.reliable)
        }
        // La flexión (cuerpo en tabla) también es tumbada; la sentadilla y el peso muerto no.
        assertEquals(ViewKind.LYING, classify(track(SynthMovement.PUSH_UP, 90.0)).kind)
        assertEquals(ViewKind.SIDE, classify(track(SynthMovement.DEADLIFT, 90.0)).kind)
        assertEquals(ViewKind.SIDE, classify(track(SynthMovement.RDL, 90.0)).kind)
    }

    @Test
    fun lyingNeedsMoreThanHalfOfTheFramesWithAHorizontalTorso() {
        val bench = track(SynthMovement.BENCH, 90.0, SynthNoise.NONE).frames
        val squat = track(SynthMovement.SQUAT, 90.0, SynthNoise.NONE).frames
        val base = track(SynthMovement.SQUAT, 90.0, SynthNoise.NONE)
        val sixtyPercent = base.copy(frames = bench.take(60) + squat.take(40))
        val fortyPercent = base.copy(frames = bench.take(40) + squat.take(60))
        assertEquals(ViewKind.LYING, classify(sixtyPercent).kind)
        assertEquals(ViewKind.SIDE, classify(fortyPercent).kind)
    }

    @Test
    fun anAthleteWhoseFaceIsNotVisibleIsSeenFromBehind() {
        // De espaldas al encuadre (ψ = 180) la nariz y las orejas bajan a 0,1.
        val behind = classify(track(SynthMovement.SQUAT, 180.0))
        assertEquals(ViewKind.BACK, behind.kind)
        // Y lo mismo si, de frente, se les pone visibilidad 0,1 a mano.
        val front = track(SynthMovement.SQUAT, 0.0)
        val hidden = front.mapFrames { _, image, _ ->
            for (lm in listOf(PoseLandmark.NOSE, PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR)) {
                val p = image[lm] ?: continue
                image[lm] = LandmarkSample(p.x, p.y, p.z, 0.1f, p.presence)
            }
        }
        assertEquals(ViewKind.BACK, classify(hidden).kind)
        // Con la nariz oculta pero una oreja a la vista (perfil de tres cuartos) no es de espaldas…
        val noseOnly = front.mapFrames { _, image, _ ->
            val p = image[PoseLandmark.NOSE]!!
            image[PoseLandmark.NOSE] = LandmarkSample(p.x, p.y, p.z, 0.1f, p.presence)
        }
        assertEquals(ViewKind.FRONT, classify(noseOnly).kind)
        // …ni si solo se pierden las orejas.
        val earsOnly = front.mapFrames { _, image, _ ->
            for (lm in listOf(PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR)) {
                val p = image[lm]!!
                image[lm] = LandmarkSample(p.x, p.y, p.z, 0.1f, p.presence)
            }
        }
        assertEquals(ViewKind.FRONT, classify(earsOnly).kind)
    }

    @Test
    fun whenTheImageAndTheWorldAnglesDisagreeByMoreThanTwentyFiveDegreesTheViewIsUnreliable() {
        val side = track(SynthMovement.SQUAT, 90.0)
        // «World» de un atleta de frente (sin profundidad entre hombros ni caderas) con una imagen de lado.
        val inconsistent = side.mapFrames { _, _, world ->
            for ((lm, w) in world.entries.toList()) world[lm] = WorldSample(w.x, w.y, 0f)
        }
        val est = classify(inconsistent)
        assertEquals(90.0, est.psi2dDeg!!, 12.0)
        assertEquals(0.0, est.psiWorldDeg!!, 1.0)
        assertFalse(est.reliable)
        assertEquals(ViewKind.OBLIQUE, est.kind) // la media de los dos: ≈ 45°
        // Discrepancia justo por debajo del límite: sigue siendo fiable.
        assertTrue(classify(side).reliable)
    }

    @Test
    fun withoutWorldPointsTheImageAngleAloneDecides() {
        val noWorld = track(SynthMovement.SQUAT, 90.0).mapFrames { _, _, world -> world.clear() }
        val est = classify(noWorld)
        assertNull(est.psiWorldDeg)
        assertNotNull(est.psi2dDeg)
        assertEquals(est.psi2dDeg!!, est.psiDeg!!, 1e-9)
        assertEquals(ViewKind.SIDE, est.kind)
        assertTrue(est.reliable)
    }

    @Test
    fun unknownWhenThereIsNothingToMeasure() {
        val base = track(SynthMovement.SQUAT, 90.0)
        // Aspecto desconocido (traza v1): no se mide.
        val legacy = classify(base.copy(aspectKnown = false))
        assertEquals(ViewKind.UNKNOWN, legacy.kind)
        assertNull(legacy.psiDeg)
        assertFalse(legacy.reliable)
        // Dos fotogramas no bastan.
        assertEquals(ViewKind.UNKNOWN, ViewClassifier.classify(FrameGeometry(base), 0..1).kind)
        // Ventana vacía o sin pose.
        assertEquals(ViewKind.UNKNOWN, ViewClassifier.classify(FrameGeometry(base), 5..2).kind)
        val noPose = base.copy(frames = base.frames.map { PoseFrameV2.of(it.tUs, 0, null, null) })
        assertEquals(ViewKind.UNKNOWN, classify(noPose).kind)
    }

    @Test
    fun classBoundariesAndTheFormulaForPsi2d() {
        assertEquals(ViewKind.SIDE, ViewClassifier.kindOfPsi(65.0))
        assertEquals(ViewKind.OBLIQUE, ViewClassifier.kindOfPsi(64.99))
        assertEquals(ViewKind.OBLIQUE, ViewClassifier.kindOfPsi(25.01))
        assertEquals(ViewKind.FRONT, ViewClassifier.kindOfPsi(25.0))
        assertEquals(ViewKind.FRONT, ViewClassifier.kindOfPsi(0.0))
        assertEquals(ViewKind.UNKNOWN, ViewClassifier.kindOfPsi(Double.NaN))
        // ρ = |S_L − S_R| / |S_mid − H_mid|; ψ_2D = acos(clamp(ρ/0,80, 0, 1)).
        fun psi(width: Double, trunk: Double) = ViewClassifier.psi2dDeg(
            Vec2(0.5 - width / 2, 0.3), Vec2(0.5 + width / 2, 0.3), Vec2(0.5 - 0.05, 0.3 + trunk), Vec2(0.5 + 0.05, 0.3 + trunk),
        )
        assertEquals(0.0, psi(0.8, 1.0), 1e-9)
        assertEquals(0.0, psi(1.2, 1.0), 1e-9) // se recorta a 1
        assertEquals(90.0, psi(0.0, 1.0), 1e-9)
        assertEquals(60.0, psi(0.4, 1.0), 1e-9)
        assertEquals(60.0, psi(0.2, 0.5), 1e-9) // solo importa el cociente
        assertTrue(ViewClassifier.psi2dDeg(Vec2(0.4, 0.3), Vec2(0.6, 0.3), Vec2(0.4, 0.3), Vec2(0.6, 0.3)).isNaN())
    }
}
