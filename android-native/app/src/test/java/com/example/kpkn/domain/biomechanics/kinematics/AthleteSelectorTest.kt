package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AthleteSelectorTest {
    private val w = 720
    private val h = 1280

    /** Una persona de pie con el centro del torso en (cx, cy) y altura total [size] (en fracción del alto del fotograma). */
    private fun person(cx: Float, cy: Float, size: Float, vis: Float = 0.95f): Map<PoseLandmark, LandmarkSample> {
        fun p(dx: Float, dy: Float) = LandmarkSample(cx + dx, cy + dy * size, 0f, vis, 0.99f)
        val half = 0.07f * size * h / w // hombros: 0,14 de la altura de ancho, en x normalizada
        return mapOf(
            PoseLandmark.NOSE to p(0f, -0.20f),
            PoseLandmark.LEFT_SHOULDER to p(half, -0.12f),
            PoseLandmark.RIGHT_SHOULDER to p(-half, -0.12f),
            PoseLandmark.LEFT_HIP to p(half * 0.8f, 0.12f),
            PoseLandmark.RIGHT_HIP to p(-half * 0.8f, 0.12f),
            PoseLandmark.LEFT_KNEE to p(half * 0.8f, 0.30f),
            PoseLandmark.RIGHT_KNEE to p(-half * 0.8f, 0.30f),
            PoseLandmark.LEFT_ANKLE to p(half * 0.8f, 0.48f),
            PoseLandmark.RIGHT_ANKLE to p(-half * 0.8f, 0.48f),
        )
    }

    @Test
    fun followsTheContinuityEvenIfTheOtherPersonIsBigger() {
        val athlete = person(0.30f, 0.50f, 0.50f)
        val bystander = person(0.75f, 0.50f, 0.80f) // más grande
        val previous = AthleteSelector.torsoCenter(athlete, w, h)!!
        assertEquals(0, AthleteSelector.select(listOf(athlete, bystander), w, h, previous))
        // El mismo caso con el orden cambiado: sigue al que mantiene la continuidad.
        assertEquals(1, AthleteSelector.select(listOf(bystander, athlete), w, h, previous))
        // Un poco desplazado respecto del centro anterior (se movió entre fotogramas) sigue ganando.
        val moved = Vec2(previous.x + 0.01, previous.y + 0.02)
        assertEquals(1, AthleteSelector.select(listOf(bystander, athlete), w, h, moved))
    }

    @Test
    fun withoutAPreviousCenterTheBiggestBoxWins() {
        val small = person(0.30f, 0.50f, 0.50f)
        val big = person(0.75f, 0.50f, 0.80f)
        assertEquals(1, AthleteSelector.select(listOf(small, big), w, h, null))
        assertEquals(0, AthleteSelector.select(listOf(big, small), w, h, null))
        // Un empate lo gana el índice menor.
        assertEquals(0, AthleteSelector.select(listOf(small, small), w, h, null))
    }

    @Test
    fun theTorsoCenterIsInFrameHeightsWithTheAspectCorrected() {
        val c = AthleteSelector.torsoCenter(person(0.30f, 0.50f, 0.50f), w, h)!!
        // La media de hombros y caderas: x = 0,30 (simétrica), y = 0,50 + (−0,12 −0,12 +0,12 +0,12)/4·size = 0,50.
        assertEquals(0.30 * w / h, c.x, 1e-5)
        assertEquals(0.50, c.y, 1e-5)
        // Con un solo hombro visible, ese solo.
        val onlyOne = mapOf(PoseLandmark.LEFT_SHOULDER to LandmarkSample(0.4f, 0.3f, 0f, 0.9f, 0.9f))
        val one = AthleteSelector.torsoCenter(onlyOne, w, h)!!
        assertEquals(0.4 * w / h, one.x, 1e-6)
        assertEquals(0.3, one.y, 1e-6)
        // Sin torso: null.
        assertNull(AthleteSelector.torsoCenter(mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.1f, 0f, 0.9f, 0.9f)), w, h))
        assertNull(AthleteSelector.torsoCenter(emptyMap(), w, h))
        // Los puntos poco visibles pesan menos que ninguno: se ignoran si hay al menos dos visibles.
        val mixed = mapOf(
            PoseLandmark.LEFT_SHOULDER to LandmarkSample(0.4f, 0.3f, 0f, 0.9f, 0.9f),
            PoseLandmark.RIGHT_SHOULDER to LandmarkSample(0.6f, 0.3f, 0f, 0.9f, 0.9f),
            PoseLandmark.LEFT_HIP to LandmarkSample(0.9f, 0.9f, 0f, 0.1f, 0.9f),
        )
        val m = AthleteSelector.torsoCenter(mixed, w, h)!!
        assertEquals(0.5 * w / h, m.x, 1e-6)
        assertEquals(0.3, m.y, 1e-6)
    }

    @Test
    fun edgeCases() {
        assertEquals(-1, AthleteSelector.select(emptyList(), w, h, null))
        assertEquals(0, AthleteSelector.select(listOf(person(0.5f, 0.5f, 0.5f)), w, h, Vec2(0.0, 0.0)))
        // Si ninguna pose tiene torso medible, se usa la caja más grande aunque haya un centro anterior.
        val noTorsoSmall = mapOf(
            PoseLandmark.NOSE to LandmarkSample(0.2f, 0.2f, 0f, 0.9f, 0.9f),
            PoseLandmark.LEFT_ANKLE to LandmarkSample(0.2f, 0.4f, 0f, 0.9f, 0.9f),
        )
        val noTorsoBig = mapOf(
            PoseLandmark.NOSE to LandmarkSample(0.6f, 0.1f, 0f, 0.9f, 0.9f),
            PoseLandmark.LEFT_ANKLE to LandmarkSample(0.7f, 0.9f, 0f, 0.9f, 0.9f),
        )
        assertEquals(1, AthleteSelector.select(listOf(noTorsoSmall, noTorsoBig), w, h, Vec2(0.1, 0.1)))
        // Una con torso y otra sin: gana la que tiene torso aunque la otra esté más cerca en caja.
        val torso = person(0.3f, 0.5f, 0.3f)
        val chosen = AthleteSelector.select(listOf(noTorsoBig, torso), w, h, Vec2(0.3 * w / h, 0.5))
        assertEquals(1, chosen)
        assertNotNull(AthleteSelector.torsoCenter(torso, w, h))
    }
}
