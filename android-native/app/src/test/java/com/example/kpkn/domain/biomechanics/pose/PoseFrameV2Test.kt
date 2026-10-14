package com.example.kpkn.domain.biomechanics.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseFrameV2Test {
    @Test
    fun landmarkIndicesMatchTheModel() {
        assertEquals(33, PoseLandmark.entries.size)
        PoseLandmark.entries.forEachIndexed { i, lm -> assertEquals(lm.name, i, lm.mp) }
        assertEquals(PoseLandmark.LEFT_SHOULDER, PoseLandmark.fromMp(11))
        assertNull(PoseLandmark.fromMp(33))
        assertNull(PoseLandmark.fromMp(-1))
    }

    @Test
    fun keptSetsAreTheOnesInTheContract() {
        assertEquals(27, PoseLandmark.KEPT_IMAGE.size)
        assertEquals(setOf(0) + (7..32).toSet(), PoseLandmark.KEPT_IMAGE.map { it.mp }.toSet())
        assertFalse(PoseLandmark.LEFT_EYE in PoseLandmark.KEPT_IMAGE)
        assertTrue(PoseLandmark.NOSE in PoseLandmark.KEPT_IMAGE)
        assertEquals(12, PoseLandmark.KEPT_WORLD.size)
        assertEquals((11..16).toSet() + (23..28).toSet(), PoseLandmark.KEPT_WORLD.map { it.mp }.toSet())
        assertFalse(PoseLandmark.LEFT_HEEL in PoseLandmark.KEPT_WORLD)
        // El orden de las listas es el de los canales del códec: ascendente por índice del modelo.
        assertEquals(PoseLandmark.KEPT_IMAGE.map { it.mp }.sorted(), PoseLandmark.KEPT_IMAGE.map { it.mp })
    }

    @Test
    fun pointsComeBackExactlyAndOnlyForKeptLandmarks() {
        val nose = LandmarkSample(0.5f, 0.1f, -0.2f, 0.9f, 0.95f)
        val hip = LandmarkSample(0.45f, 0.55f, 0.01f, 0.8f, 0.85f)
        val eye = LandmarkSample(0.51f, 0.09f, -0.2f, 0.9f, 0.9f)
        val frame = PoseFrameV2.of(
            tUs = 1_000L,
            posesDetected = 2,
            image = mapOf(PoseLandmark.NOSE to nose, PoseLandmark.LEFT_HIP to hip, PoseLandmark.LEFT_EYE to eye),
            world = mapOf(
                PoseLandmark.LEFT_HIP to WorldSample(0.09f, 0f, 0f),
                PoseLandmark.LEFT_HEEL to WorldSample(1f, 1f, 1f),
            ),
        )
        assertTrue(frame.hasPose)
        assertEquals(1_000L, frame.tUs)
        assertEquals(2, frame.posesDetected)
        assertEquals(nose, frame.point(PoseLandmark.NOSE))
        assertEquals(hip, frame.point(PoseLandmark.LEFT_HIP))
        assertNull("los ojos no se guardan", frame.point(PoseLandmark.LEFT_EYE))
        assertNull("falta el dato", frame.point(PoseLandmark.RIGHT_HIP))
        assertEquals(WorldSample(0.09f, 0f, 0f), frame.world(PoseLandmark.LEFT_HIP))
        assertNull("el talón no está en KEPT_WORLD", frame.world(PoseLandmark.LEFT_HEEL))
        assertNull("falta el dato world", frame.world(PoseLandmark.RIGHT_HIP))
    }

    @Test
    fun noAthleteMeansNoPointsAndNoWorld() {
        val none = PoseFrameV2.of(5L, 0, null, null)
        assertFalse(none.hasPose)
        for (lm in PoseLandmark.entries) {
            assertNull(none.point(lm))
            assertNull(none.world(lm))
        }
        // Con atleta pero sin puntos «world» (traza v1, por ejemplo).
        val noWorld = PoseFrameV2.of(5L, 1, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.5f, 0f, 1f, 1f)), null)
        assertTrue(noWorld.hasPose)
        assertNotNull(noWorld.point(PoseLandmark.NOSE))
        assertNull(noWorld.world(PoseLandmark.LEFT_HIP))
    }

    @Test
    fun publicConstructorBehavesLikeTheFactory() {
        val image = mapOf(PoseLandmark.LEFT_KNEE to LandmarkSample(0.3f, 0.7f, 0f, 0.7f, 0.8f))
        assertEquals(PoseFrameV2.of(7L, 1, image, null), PoseFrameV2(7L, 1, image, null))
        assertFalse(PoseFrameV2(7L, 0, null, null).hasPose)
    }

    @Test
    fun framesCompareByContent() {
        val a = PoseFrameV2.of(1L, 1, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.5f, 0f, 1f, 1f)), null)
        val b = PoseFrameV2.of(1L, 1, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.5f, 0f, 1f, 1f)), null)
        val c = PoseFrameV2.of(1L, 1, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.6f, 0f, 1f, 1f)), null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotEquals(a, PoseFrameV2.of(2L, 1, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.5f, 0f, 1f, 1f)), null))
        assertNotEquals(a, PoseFrameV2.of(1L, 0, null, null))
    }
}
