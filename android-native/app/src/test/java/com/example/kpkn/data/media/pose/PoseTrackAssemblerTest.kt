package com.example.kpkn.data.media.pose

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseTrackAssemblerTest {
    private val w = 720
    private val h = 1280
    private val engine = PoseEngineInfo("mediapipe-pose", "full", "CPU")

    private fun assembler(
        stride: Int = 1,
        rotation: Int = 0,
        captureFps: Float? = null,
        focalLengthPx: Float? = null,
        roll: Float? = null,
        pitch: Float? = null,
        fallbackFps: Float? = null,
    ) = PoseTrackAssembler(
        mediaId = "clip",
        frameWidth = w,
        frameHeight = h,
        rotationApplied = rotation,
        poseStride = stride,
        captureFps = captureFps,
        focalLengthPx = focalLengthPx,
        gravityRollDeg = roll,
        gravityPitchDeg = pitch,
        fallbackFps = fallbackFps,
    )

    /** Desplazamiento (x normalizada, y en fracción de [size]) de cada punto de una persona de pie. */
    private fun offset(landmark: PoseLandmark, half: Float): Pair<Float, Float> {
        val side = when {
            landmark.name.startsWith("LEFT_") -> 1f
            landmark.name.startsWith("RIGHT_") -> -1f
            else -> 0f
        }
        return when (landmark.name.removePrefix("LEFT_").removePrefix("RIGHT_")) {
            "SHOULDER" -> side * half to -0.12f
            "ELBOW" -> side * half * 1.2f to 0f
            "WRIST", "PINKY", "INDEX", "THUMB" -> side * half * 1.3f to 0.05f
            "HIP" -> side * half * 0.8f to 0.12f
            "KNEE" -> side * half * 0.8f to 0.30f
            "ANKLE" -> side * half * 0.8f to 0.48f
            "HEEL", "FOOT_INDEX" -> side * half * 0.8f to 0.50f
            else -> side * 0.01f to -0.20f // nariz, ojos, orejas y boca
        }
    }

    /** Una persona de pie con el centro del torso en (cx, cy) y altura [size] (fracción del alto del fotograma). */
    private fun person(cx: Float, cy: Float, size: Float, vis: Float = 0.95f): DetectedPose {
        val half = 0.07f * size * h / w
        val image = PoseLandmark.entries.map { landmark ->
            val (dx, dy) = offset(landmark, half)
            LandmarkSample(cx + dx, cy + dy * size, 0.02f, vis, 0.99f)
        }
        // «world» en metros, centrado en la cadera, siempre distinto de cero.
        val world = PoseLandmark.entries.map { landmark ->
            val (dx, dy) = offset(landmark, 0.2f)
            WorldSample(dx, dy * 1.7f, 0.03f)
        }
        return DetectedPose(image, world)
    }

    private fun us(frame: Int, stepUs: Long = 33_333L) = frame * stepUs

    @Test
    fun followsTheAthleteEvenWhenABiggerPersonWalksIn() {
        val a = assembler()
        for (k in 0..90) {
            val t = us(k)
            val athlete = person(0.30f, 0.50f + 0.01f * sin(k * 0.3f), 0.50f)
            val poses = if (t >= 1_200_000L) listOf(person(0.75f, 0.50f, 0.80f), athlete) else listOf(athlete)
            a.add(t, poses)
        }
        val track = a.build(engine)
        assertEquals(91, track.frames.size)
        for ((k, frame) in track.frames.withIndex()) {
            assertTrue("fotograma $k sin atleta", frame.hasPose)
            val nose = frame.point(PoseLandmark.NOSE)!!
            assertEquals("fotograma $k: sigue a otra persona", 0.30f, nose.x, 0.05f)
            assertEquals("fotograma $k: poses vistas", if (us(k) >= 1_200_000L) 2 else 1, frame.posesDetected)
        }
        assertEquals(a.frameCount, a.framesWithAthlete)
    }

    @Test
    fun theBiggestBoxWinsDuringTheFirstSecondAndContinuityTakesOverAfterwards() {
        val a = assembler()
        // Las dos desde el principio; la de la derecha es más grande y gana el primer segundo.
        // A partir de ahí manda la continuidad: sigue a la que ya seguía, aunque la otra se haga más grande.
        for (k in 0..90) {
            val t = us(k)
            val small = person(0.30f, 0.50f, 0.50f)
            val big = person(0.75f, 0.50f, if (t < 2_000_000L) 0.80f else 0.40f)
            a.add(t, listOf(small, big))
        }
        val track = a.build(engine)
        for ((k, frame) in track.frames.withIndex()) {
            assertEquals("fotograma $k", 0.75f, frame.point(PoseLandmark.NOSE)!!.x, 0.05f)
        }
    }

    @Test
    fun keepsOnlyTheStoredLandmarks() {
        val a = assembler()
        a.add(0L, listOf(person(0.5f, 0.5f, 0.5f)))
        val frame = a.build(engine).frames.single()
        // Imagen: los 27 de KEPT_IMAGE; los ojos (1..6) no se guardan.
        assertNotNull(frame.point(PoseLandmark.NOSE))
        assertNotNull(frame.point(PoseLandmark.LEFT_EAR))
        assertNotNull(frame.point(PoseLandmark.RIGHT_FOOT_INDEX))
        assertNull(frame.point(PoseLandmark.LEFT_EYE))
        assertNull(frame.point(PoseLandmark.RIGHT_EYE_OUTER))
        assertEquals(27, PoseLandmark.entries.count { frame.point(it) != null })
        // World: los 12 de KEPT_WORLD (hombros, codos, muñecas, caderas, rodillas y tobillos).
        assertNotNull(frame.world(PoseLandmark.LEFT_SHOULDER))
        assertNotNull(frame.world(PoseLandmark.RIGHT_ANKLE))
        assertNull(frame.world(PoseLandmark.NOSE))
        assertNull(frame.world(PoseLandmark.LEFT_HEEL))
        assertEquals(12, PoseLandmark.entries.count { frame.world(it) != null })
        // Los valores pasan tal cual: visibilidad, presencia y z.
        val hip = frame.point(PoseLandmark.LEFT_HIP)!!
        assertEquals(0.95f, hip.visibility, 1e-6f)
        assertEquals(0.99f, hip.presence, 1e-6f)
        assertEquals(0.02f, hip.z, 1e-6f)
    }

    @Test
    fun aWorldOfZerosIsStoredAsAbsent() {
        val image = person(0.5f, 0.5f, 0.5f).image
        val zeros = List(33) { WorldSample(0f, 0f, 0f) }
        val a = assembler()
        a.add(0L, listOf(DetectedPose(image, zeros)))
        a.add(33_333L, listOf(DetectedPose(image, emptyList())))
        val frames = a.build(engine).frames
        for (frame in frames) {
            assertTrue(frame.hasPose)
            assertNull(frame.world(PoseLandmark.LEFT_HIP))
            assertNotNull(frame.point(PoseLandmark.LEFT_HIP))
        }
    }

    @Test
    fun missingLandmarksAreIgnored() {
        val full = person(0.5f, 0.5f, 0.5f)
        val image = full.image.toMutableList()
        image[PoseLandmark.LEFT_KNEE.mp] = LandmarkSample(Float.NaN, Float.NaN, 0f, 0f, 0f)
        val a = assembler()
        a.add(0L, listOf(DetectedPose(image, full.world)))
        val frame = a.build(engine).frames.single()
        assertNull(frame.point(PoseLandmark.LEFT_KNEE))
        assertNotNull(frame.point(PoseLandmark.RIGHT_KNEE))
    }

    @Test
    fun framesWithoutPosesOrWithoutUsablePointsHaveNoAthlete() {
        val a = assembler()
        a.add(0L, emptyList())
        a.add(33_333L, listOf(DetectedPose(List(33) { LandmarkSample(Float.NaN, Float.NaN, 0f, 0f, 0f) }, emptyList())))
        a.add(66_666L, listOf(person(0.5f, 0.5f, 0.5f)))
        val track = a.build(engine)
        assertFalse(track.frames[0].hasPose)
        assertEquals(0, track.frames[0].posesDetected)
        assertFalse(track.frames[1].hasPose)
        assertEquals(1, track.frames[1].posesDetected)
        assertTrue(track.frames[2].hasPose)
        assertEquals(3, a.frameCount)
        assertEquals(1, a.framesWithAthlete)
    }

    @Test
    fun nominalFpsIsTheInverseOfTheMedianPtsStep() {
        val a = assembler()
        for (k in 0 until 60) a.add(us(k), listOf(person(0.5f, 0.5f, 0.5f)))
        val track = a.build(engine)
        assertEquals(30f, track.nominalFps, 0.01f)
        assertFalse(track.vfr)
        assertEquals(1, track.poseStride)
    }

    @Test
    fun withStrideTwoTheNominalFpsIsThatOfTheFramesWithPose() {
        // Un vídeo de 60 fps con pose en uno de cada dos fotogramas: los fotogramas añadidos van a 30 Hz.
        val a = assembler(stride = 2)
        for (k in 0 until 60) a.add(k * 2 * 16_667L, listOf(person(0.5f, 0.5f, 0.5f)))
        val track = a.build(engine)
        assertEquals(30f, track.nominalFps, 0.05f)
        assertEquals(2, track.poseStride)
        assertFalse(track.vfr)
    }

    @Test
    fun aVariableCadenceIsFlaggedAsVfr() {
        val a = assembler()
        var t = 0L
        for (k in 0 until 60) {
            a.add(t, listOf(person(0.5f, 0.5f, 0.5f)))
            t += if (k % 2 == 0) 25_000L else 41_000L
        }
        assertTrue(a.build(engine).vfr)
    }

    @Test
    fun timesThatDoNotGrowAreDropped() {
        val a = assembler()
        a.add(100_000L, listOf(person(0.5f, 0.5f, 0.5f)))
        a.add(100_000L, listOf(person(0.5f, 0.5f, 0.5f)))
        a.add(50_000L, listOf(person(0.5f, 0.5f, 0.5f)))
        a.add(133_333L, listOf(person(0.5f, 0.5f, 0.5f)))
        assertEquals(2, a.frameCount)
        assertEquals(2, a.droppedFrames)
        assertEquals(listOf(100_000L, 133_333L), a.build(engine).frames.map { it.tUs })
    }

    @Test
    fun multiPersonNeedsMoreThanTenPercentOfTheFrames() {
        fun fraction(frames: Int, withTwo: Int): PoseTrackAssembler {
            val a = assembler()
            for (k in 0 until frames) {
                val poses = if (k < withTwo) listOf(person(0.3f, 0.5f, 0.5f), person(0.7f, 0.5f, 0.4f)) else listOf(person(0.3f, 0.5f, 0.5f))
                a.add(us(k), poses)
            }
            return a
        }
        assertEquals(0.0, assembler().multiPersonFraction, 0.0)
        assertEquals(0.15, fraction(20, 3).multiPersonFraction, 1e-9)
        assertTrue(fraction(20, 3).isMultiPerson)
        // Justo el 10 % no es más del 10 %.
        assertEquals(0.10, fraction(20, 2).multiPersonFraction, 1e-9)
        assertFalse(fraction(20, 2).isMultiPerson)
        assertFalse(fraction(20, 1).isMultiPerson)
    }

    @Test
    fun theMetadataGoesIntoTheTrack() {
        val a = assembler(stride = 2, rotation = 90, captureFps = 240f, focalLengthPx = 1500f, roll = 1.5f, pitch = -3f)
        a.add(0L, listOf(person(0.5f, 0.5f, 0.5f)))
        val track = a.build(PoseEngineInfo("mediapipe-pose", "heavy", "GPU"))
        assertEquals("clip", track.mediaId)
        assertEquals(PoseEngineInfo("mediapipe-pose", "heavy", "GPU"), track.engine)
        assertEquals(w, track.frameWidth)
        assertEquals(h, track.frameHeight)
        assertEquals(90, track.rotationApplied)
        assertEquals(2, track.poseStride)
        assertEquals(240f, track.captureFps)
        assertEquals(1500f, track.focalLengthPx)
        assertEquals(1.5f, track.gravityRollDeg)
        assertEquals(-3f, track.gravityPitchDeg)
        assertTrue(track.aspectKnown)
        assertEquals(2, track.version)
    }

    @Test
    fun anEmptyAssemblerBuildsAnEmptyTrackWithTheFallbackFps() {
        assertEquals(24f, assembler(fallbackFps = 24f).build(engine).nominalFps, 0f)
        assertEquals(0f, assembler().build(engine).nominalFps, 0f)
        assertTrue(assembler().build(engine).frames.isEmpty())
        val one = assembler(fallbackFps = 30f)
        one.add(0L, listOf(person(0.5f, 0.5f, 0.5f)))
        assertEquals(30f, one.build(engine).nominalFps, 0f)
    }

    @Test
    fun resumingFromAPartialTrackGivesTheSameTrackAsGoingStraightThrough() {
        fun posesAt(k: Int): List<DetectedPose> {
            val athlete = person(0.30f, 0.50f, 0.50f)
            return if (us(k) >= 1_200_000L) listOf(person(0.75f, 0.50f, 0.80f), athlete) else listOf(athlete)
        }
        val straight = assembler()
        for (k in 0..90) straight.add(us(k), posesAt(k))

        // Un punto de control a mitad de camino: el atleta y el espectador, más grande, ya están en cuadro.
        val firstHalf = assembler()
        for (k in 0..59) firstHalf.add(us(k), posesAt(k))
        val resumed = assembler()
        resumed.resumeFrom(firstHalf.build(engine).frames)
        for (k in 60..90) resumed.add(us(k), posesAt(k))

        val expected = straight.build(engine)
        val actual = resumed.build(engine)
        assertEquals(expected.frames, actual.frames)
        assertEquals(expected.nominalFps, actual.nominalFps, 1e-6f)
        assertEquals(straight.frameCount, resumed.frameCount)
        assertEquals(straight.framesWithAthlete, resumed.framesWithAthlete)
        assertEquals(straight.multiPersonFraction, resumed.multiPersonFraction, 1e-12)
        // Y sigue al atleta, no al espectador, después de reanudar.
        assertEquals(0.30f, actual.frames.last().point(PoseLandmark.NOSE)!!.x, 0.05f)
    }

    @Test(expected = IllegalStateException::class)
    fun onlyAnEmptyAssemblerCanBeResumed() {
        val a = assembler()
        a.add(0L, listOf(person(0.5f, 0.5f, 0.5f)))
        a.resumeFrom(emptyList())
    }

    @Test
    fun theTrackSurvivesTheCodec() {
        val a = assembler(stride = 1, rotation = 270, captureFps = 120f, roll = 2f)
        for (k in 0 until 40) {
            val poses = when {
                k < 5 -> emptyList()
                k < 10 -> listOf(person(0.3f, 0.5f, 0.5f), person(0.7f, 0.5f, 0.4f))
                else -> listOf(person(0.3f, 0.5f + 0.02f * sin(k * 0.5f), 0.5f))
            }
            a.add(us(k), poses)
        }
        val track = a.build(PoseEngineInfo("mediapipe-pose", "full", "GPU"))
        val back = PoseTrackCodec.decode(PoseTrackCodec.encode(track)) ?: error("El códec no decodifica la traza")
        assertEquals(track.frames.size, back.frames.size)
        assertEquals(track.engine, back.engine)
        assertEquals(track.frameWidth, back.frameWidth)
        assertEquals(track.frameHeight, back.frameHeight)
        assertEquals(270, back.rotationApplied)
        assertEquals(120f, back.captureFps)
        assertEquals(2f, back.gravityRollDeg)
        assertEquals(track.nominalFps, back.nominalFps, 1e-3f)
        for (k in track.frames.indices) {
            assertEquals("tiempo $k", track.frames[k].tUs, back.frames[k].tUs)
            assertEquals("pose $k", track.frames[k].hasPose, back.frames[k].hasPose)
            assertEquals("poses vistas $k", track.frames[k].posesDetected, back.frames[k].posesDetected)
        }
        val before = track.frames[20].point(PoseLandmark.LEFT_HIP)!!
        val after = back.frames[20].point(PoseLandmark.LEFT_HIP)!!
        assertEquals(before.x, after.x, 2e-4f)
        assertEquals(before.y, after.y, 2e-4f)
        assertNotNull(back.frames[20].world(PoseLandmark.LEFT_HIP))
    }
}
