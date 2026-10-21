package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LeftRightSwapRepairTest {
    private val legPairs = listOf(
        PoseLandmark.LEFT_KNEE to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL to PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX to PoseLandmark.RIGHT_FOOT_INDEX,
    )
    private val armPairs = listOf(
        PoseLandmark.LEFT_ELBOW to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_INDEX to PoseLandmark.RIGHT_INDEX,
        PoseLandmark.LEFT_PINKY to PoseLandmark.RIGHT_PINKY,
        PoseLandmark.LEFT_THUMB to PoseLandmark.RIGHT_THUMB,
    )

    /** Zancada de lado en horizontal: las piernas quedan separadas ~0,7 m, así que un cruce se ve (y es reparable). */
    private fun lunge(noise: SynthNoise, seed: Long = 42L, reps: Int = 6): SynthResult = SyntheticLifter.generate(
        SynthConfig(
            SynthMovement.LUNGE,
            camera = SynthTestSupport.LANDSCAPE_FAR.copy(distanceM = 3.0, hfovDeg = 60.0),
            script = repScript(reps),
            noise = noise,
            seed = seed,
        ),
    )

    private fun legError(a: PoseFrameV2, b: PoseFrameV2): Double {
        var worst = 0.0
        for ((l, r) in legPairs) {
            for (lm in listOf(l, r)) {
                val pa = a.point(lm)
                val pb = b.point(lm)
                if (pa == null || pb == null) continue
                worst = maxOf(worst, abs(pa.x - pb.x).toDouble(), abs(pa.y - pb.y).toDouble())
            }
        }
        return worst
    }

    @Test
    fun fiveInjectedLegCrossingsAreFixedInAtLeastNinetyFivePercentOfTheirFrames() {
        val noise = SynthNoise(swapGlitches = 5, dropRate = 0.0)
        val result = lunge(noise)
        val glitched = result.swapGlitchFrames.indices.filter { result.swapGlitchFrames[it] }
        assertTrue("fotogramas cruzados ${glitched.size}", glitched.size in 10..25)
        // Antes de reparar, esos fotogramas están claramente mal respecto de la traza limpia…
        for (k in glitched) assertTrue("fotograma $k", legError(result.track.frames[k], result.cleanTrack.frames[k]) > 0.1)
        val repaired = LeftRightSwapRepair.repair(result.track, result.bodyHeightFh)
        // …y después casi todos coinciden con ella (el ruido σ = 0,004 FH y los atípicos dejan un error pequeño).
        val fixed = glitched.count { legError(repaired.frames[it], result.cleanTrack.frames[it]) < 0.06 }
        println("cruces: ${glitched.size} fotogramas, corregidos $fixed")
        assertTrue("corregidos $fixed de ${glitched.size}", fixed >= 0.95 * glitched.size)
        // Sin cruces nuevos: ningún fotograma que no estaba cruzado cambia.
        for (k in repaired.frames.indices) {
            if (k !in glitched) assertTrue("fotograma $k cambiado", repaired.frames[k] == result.track.frames[k])
        }
    }

    @Test
    fun aCleanTrackIsLeftExactlyAsItWas() {
        for (noise in listOf(SynthNoise.NONE, SynthNoise())) {
            val result = lunge(noise, reps = 4)
            val repaired = LeftRightSwapRepair.repair(result.track, result.bodyHeightFh)
            assertNotSame(result.track, repaired)
            assertEquals(result.track.frames.size, repaired.frames.size)
            for (k in repaired.frames.indices) assertSame("fotograma $k", result.track.frames[k], repaired.frames[k])
            // La cabecera es la misma.
            assertEquals(result.track.copy(frames = emptyList()), repaired.copy(frames = emptyList()))
        }
    }

    @Test
    fun differentSeedsAndMovementsKeepFixingTheirCrossings() {
        for (seed in listOf(1L, 2L, 3L, 4L)) {
            val noise = SynthNoise(swapGlitches = 4, dropRate = 0.01)
            val result = lunge(noise, seed)
            val repaired = LeftRightSwapRepair.repair(result.track, result.bodyHeightFh)
            val glitched = result.swapGlitchFrames.indices.filter { result.swapGlitchFrames[it] }
            val fixed = glitched.count { result.track.frames[it].hasPose && legError(repaired.frames[it], result.cleanTrack.frames[it]) < 0.06 }
            val total = glitched.count { result.track.frames[it].hasPose }
            assertTrue("semilla $seed: corregidos $fixed de $total", fixed >= 0.95 * total)
        }
    }

    @Test
    fun armCrossingsOfAnAlternatingCurlAreFixedToo() {
        // Curl alterno de frente: un brazo sube mientras el otro baja, así que cruzarlos se ve.
        val result = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.CURL,
                camera = SynthTestSupport.LANDSCAPE_FAR.copy(yawDeg = 0.0, distanceM = 3.0, hfovDeg = 60.0),
                script = repScript(8, eccS = 1.5, p1S = 0.0, conS = 1.5, p2S = 0.0),
                noise = SynthNoise.NONE,
                alternating = true,
            ),
        )
        val crossed = setOf(50, 51, 52, 130, 131, 132, 133, 210, 211)
        val broken = result.track.mapFrames { i, image, world ->
            if (i in crossed) SynthTestSupport.swapPairs(image, world, armPairs)
        }
        for (k in crossed) assertTrue(armError(broken.frames[k], result.cleanTrack.frames[k]) > 0.05)
        val repaired = LeftRightSwapRepair.repair(broken, result.bodyHeightFh)
        for (k in crossed) assertTrue("fotograma $k", armError(repaired.frames[k], result.cleanTrack.frames[k]) < 1e-4)
        for (k in repaired.frames.indices) if (k !in crossed) assertSame(broken.frames[k], repaired.frames[k])
        // Las manos viajan con la muñeca: el índice y el meñique también vuelven a su sitio.
        assertEquals(result.cleanTrack.frames[51].x(PoseLandmark.LEFT_INDEX), repaired.frames[51].x(PoseLandmark.LEFT_INDEX), 1e-5f)
    }

    private fun armError(a: PoseFrameV2, b: PoseFrameV2): Double {
        var worst = 0.0
        for ((l, r) in armPairs) for (lm in listOf(l, r)) {
            val pa = a.point(lm) ?: continue
            val pb = b.point(lm) ?: continue
            worst = maxOf(worst, abs(pa.x - pb.x).toDouble(), abs(pa.y - pb.y).toDouble())
        }
        return worst
    }

    @Test
    fun worldPointsAndVisibilityTravelWithTheSwap() {
        val result = lunge(SynthNoise.NONE, reps = 3)
        val k = 120
        val broken = result.track.mapFrames { i, image, world ->
            if (i in k..k + 3) SynthTestSupport.swapPairs(image, world, legPairs)
        }
        val repaired = LeftRightSwapRepair.repair(broken, result.bodyHeightFh)
        for (i in k..k + 3) {
            val clean = result.cleanTrack.frames[i]
            val fixed = repaired.frames[i]
            assertEquals(clean.worldX(PoseLandmark.LEFT_KNEE), fixed.worldX(PoseLandmark.LEFT_KNEE), 1e-5f)
            assertEquals(clean.worldZ(PoseLandmark.RIGHT_ANKLE), fixed.worldZ(PoseLandmark.RIGHT_ANKLE), 1e-5f)
            assertEquals(clean.vis(PoseLandmark.LEFT_HEEL), fixed.vis(PoseLandmark.LEFT_HEEL), 1e-6f)
            assertEquals(clean.tUs, fixed.tUs)
        }
    }

    @Test
    fun aSwapNeedsAGainOfMoreThanTheSwitchPenaltyAndAnInvalidScaleChangesNothing() {
        // Con las piernas casi juntas (sentadilla de lado) cruzarlas no se nota ni importa: no se «repara» nada.
        val squat = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.SQUAT,
                camera = SynthTestSupport.LANDSCAPE_FAR,
                script = repScript(3),
                noise = SynthNoise.NONE,
            ),
        )
        val repaired = LeftRightSwapRepair.repair(squat.track, squat.bodyHeightFh)
        for (k in repaired.frames.indices) assertSame(squat.track.frames[k], repaired.frames[k])
        // Escala no válida: se devuelve la traza tal cual.
        val noScale = LeftRightSwapRepair.repair(squat.track, 0.0)
        assertEquals(squat.track, noScale)
        val nanScale = LeftRightSwapRepair.repair(squat.track, Double.NaN)
        assertEquals(squat.track, nanScale)
        // Una traza vacía o de un fotograma no falla.
        val one = squat.track.copy(frames = squat.track.frames.take(1))
        assertEquals(one, LeftRightSwapRepair.repair(one, 0.5))
        val empty: PoseTrackV2 = squat.track.copy(frames = emptyList())
        assertEquals(empty, LeftRightSwapRepair.repair(empty, 0.5))
    }

    @Test
    fun longCrossingsAndOneThatStartsRightAtTheBeginningAreFixedToo() {
        val result = lunge(SynthNoise(dropRate = 0.0, outlierRate = 0.0), reps = 4)
        val crossed = (0..29).toSet() + (200..259).toSet()
        val broken = result.track.mapFrames { i, image, world ->
            if (i in crossed) SynthTestSupport.swapPairs(image, world, legPairs)
        }
        val repaired = LeftRightSwapRepair.repair(broken, result.bodyHeightFh)
        for (k in crossed) assertTrue("fotograma $k", legError(repaired.frames[k], result.cleanTrack.frames[k]) < 0.05)
        for (k in repaired.frames.indices) if (k !in crossed) assertSame(broken.frames[k], repaired.frames[k])
    }
}
