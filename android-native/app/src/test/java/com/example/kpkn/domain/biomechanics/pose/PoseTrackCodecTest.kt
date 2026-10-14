package com.example.kpkn.domain.biomechanics.pose

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.sin

class PoseTrackCodecTest {
    @Test
    fun roundTripKeepsHeaderTimesAndQuantizedPoints() {
        val track = syntheticTrack(frameCount = 300)
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(track))
        assertNotNull(decoded)
        decoded!!

        // Cabecera idéntica (todos los campos salvo los fotogramas).
        assertEquals(track.copy(frames = emptyList()), decoded.copy(frames = emptyList()))
        assertEquals(2, decoded.version)
        assertEquals(track.frames.size, decoded.frames.size)

        var withPose = 0
        var withTwoPoses = 0
        var withoutPose = 0
        var comparedImage = 0
        var comparedWorld = 0
        for (i in track.frames.indices) {
            val a = track.frames[i]
            val b = decoded.frames[i]
            assertEquals("t[$i]", a.tUs, b.tUs)
            assertEquals("hasPose[$i]", a.hasPose, b.hasPose)
            if (!a.hasPose) {
                withoutPose++
                assertEquals("n[$i]", 0, b.posesDetected)
                for (lm in PoseLandmark.entries) {
                    assertNull(b.point(lm))
                    assertNull(b.world(lm))
                }
                continue
            }
            withPose++
            if (a.posesDetected == 2) withTwoPoses++
            assertEquals("n[$i]", a.posesDetected, b.posesDetected)
            for (lm in PoseLandmark.KEPT_IMAGE) {
                val p = a.point(lm)
                val q = b.point(lm)
                if (p == null) {
                    assertNull("$lm[$i]", q)
                    continue
                }
                assertNotNull("$lm[$i]", q)
                q!!
                assertTrue("x $lm[$i]", abs(p.x - q.x) <= 1e-4f)
                assertTrue("y $lm[$i]", abs(p.y - q.y) <= 1e-4f)
                assertTrue("z $lm[$i]", abs(p.z - q.z) <= 1e-3f)
                assertTrue("vis $lm[$i]", abs(p.visibility - q.visibility) <= 0.01f)
                assertTrue("pres $lm[$i]", abs(p.presence - q.presence) <= 0.01f)
                comparedImage++
            }
            for (lm in PoseLandmark.KEPT_WORLD) {
                val p = a.world(lm)
                val q = b.world(lm)
                if (p == null) {
                    assertNull("world $lm[$i]", q)
                    continue
                }
                assertNotNull("world $lm[$i]", q)
                q!!
                // Los «world» se guardan en milímetros: error ≤ 1 mm.
                assertTrue("wx $lm[$i]", abs(p.x - q.x) <= 1e-3f)
                assertTrue("wy $lm[$i]", abs(p.y - q.y) <= 1e-3f)
                assertTrue("wz $lm[$i]", abs(p.z - q.z) <= 1e-3f)
                comparedWorld++
            }
        }
        assertTrue("hay fotogramas sin pose", withoutPose > 10)
        assertTrue("hay fotogramas con 2 poses", withTwoPoses > 10)
        assertTrue(withPose + withoutPose == 300)
        assertTrue(comparedImage > 5000)
        assertTrue(comparedWorld > 2000)
    }

    @Test
    fun timesFollowTheVariableCadence() {
        val track = syntheticTrack(frameCount = 120)
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(track))!!
        val dts = (1 until decoded.frames.size).map { decoded.frames[it].tUs - decoded.frames[it - 1].tUs }
        assertTrue("la traza es VFR", dts.toSet().size > 1)
        assertEquals((1 until track.frames.size).map { track.frames[it].tUs - track.frames[it - 1].tUs }, dts)
        assertTrue(decoded.vfr)
    }

    @Test
    fun optionalHeaderFieldsRoundTripAsNull() {
        val track = PoseTrackV2(
            mediaId = "ñandú/éxito \"1\"",
            engine = PoseEngineInfo("mlkit-pose", "base", "CPU"),
            frameWidth = 1,
            frameHeight = 1,
            rotationApplied = 0,
            nominalFps = 10f,
            poseStride = 1,
            vfr = false,
            aspectKnown = false,
            frames = listOf(PoseFrameV2.of(0L, 1, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.5f, 0f, 1f, 1f)), null)),
        )
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(track))!!
        assertNull(decoded.captureFps)
        assertNull(decoded.focalLengthPx)
        assertNull(decoded.gravityRollDeg)
        assertNull(decoded.gravityPitchDeg)
        assertFalse(decoded.aspectKnown)
        assertEquals(track.copy(frames = emptyList()), decoded.copy(frames = emptyList()))
        assertNull(decoded.frames[0].world(PoseLandmark.LEFT_HIP))
    }

    @Test
    fun emptyTrackRoundTrips() {
        val track = syntheticTrack(frameCount = 0)
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(track))!!
        assertTrue(decoded.frames.isEmpty())
        assertEquals(track, decoded)
    }

    @Test
    fun coordinatesOutsideTheStoredRangeAreClamped() {
        val frame = PoseFrameV2.of(
            0L, 1,
            mapOf(
                PoseLandmark.NOSE to LandmarkSample(-2f, 3f, 0.2f, 1.5f, -0.3f),
                PoseLandmark.LEFT_HIP to LandmarkSample(1.5f, -0.5f, 0f, 1f, 1f),
            ),
            null,
        )
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(trackOf(listOf(frame))))!!
        val nose = decoded.frames[0].point(PoseLandmark.NOSE)!!
        assertEquals(-0.5f, nose.x, 1e-6f)
        assertEquals(1.5f, nose.y, 1e-6f)
        assertEquals(1f, nose.visibility, 1e-6f)
        assertEquals(0f, nose.presence, 1e-6f)
        val hip = decoded.frames[0].point(PoseLandmark.LEFT_HIP)!!
        assertEquals(1.5f, hip.x, 1e-6f)
        assertEquals(-0.5f, hip.y, 1e-6f)
    }

    @Test
    fun frameWithPoseButNoPointsAndInconsistentCountSurvive() {
        // Con atleta y sin ningún punto: vuelve con atleta y sin puntos.
        val empty = PoseFrameV2.of(0L, 1, emptyMap(), null)
        // posesDetected = 0 con pose: el formato usa n = 0 para «sin atleta», así que vuelve como 1 pose.
        val odd = PoseFrameV2.of(33_333L, 0, mapOf(PoseLandmark.NOSE to LandmarkSample(0.5f, 0.5f, 0f, 1f, 1f)), null)
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(trackOf(listOf(empty, odd))))!!
        assertTrue(decoded.frames[0].hasPose)
        assertEquals(1, decoded.frames[0].posesDetected)
        assertNull(decoded.frames[0].point(PoseLandmark.NOSE))
        assertTrue(decoded.frames[1].hasPose)
        assertEquals(1, decoded.frames[1].posesDetected)
        assertNotNull(decoded.frames[1].point(PoseLandmark.NOSE))
    }

    @Test
    fun encodingIsDeterministicAndCompact() {
        // 1 min a 30 Hz: movimiento suave, sin ruido y con el ruido típico del modelo (σ ≈ 3 píxeles en 1000).
        val clean = syntheticTrack(frameCount = 1800, smooth = true)
        val noisy = syntheticTrack(frameCount = 1800, smooth = true, sigma = 0.003)
        val a = PoseTrackCodec.encode(clean)
        assertArrayEquals(a, PoseTrackCodec.encode(clean))
        val b = PoseTrackCodec.encode(noisy)
        println("pose2.json.gz de 1 min a 30 Hz: ${a.size} bytes sin ruido, ${b.size} bytes con ruido")
        assertTrue("tamaño sin ruido ${a.size}", a.size < 300_000)
        assertTrue("tamaño con ruido ${b.size}", b.size < 700_000)
        assertEquals(1800, PoseTrackCodec.decode(b)!!.frames.size)
    }

    @Test
    fun garbageDecodesToNull() {
        assertNull(PoseTrackCodec.decode(ByteArray(0)))
        assertNull(PoseTrackCodec.decode("esto no es gzip".toByteArray()))
        val noise = ByteArray(512).also { Random(7).nextBytes(it) }
        assertNull(PoseTrackCodec.decode(noise))
        // gzip válido pero el contenido no es JSON.
        assertNull(PoseTrackCodec.decode(gzip("hola, esto no es JSON")))
        // JSON válido pero de otra forma.
        assertNull(PoseTrackCodec.decode(gzip("{}")))
        assertNull(PoseTrackCodec.decode(gzip("[1,2,3]")))
        assertNull(PoseTrackCodec.decode(gzip("""{"v":2}""")))
        // gzip truncado.
        val good = PoseTrackCodec.encode(syntheticTrack(frameCount = 30))
        assertNull(PoseTrackCodec.decode(good.copyOf(good.size / 2)))
    }

    @Test
    fun wrongVersionDecodesToNull() {
        val json = gunzip(PoseTrackCodec.encode(syntheticTrack(frameCount = 20)))
        assertTrue(json.startsWith("{\"v\":2,"))
        assertNotNull(PoseTrackCodec.decode(gzip(json)))
        assertNull("v = 1", PoseTrackCodec.decode(gzip(json.replaceFirst("\"v\":2", "\"v\":1"))))
        assertNull("v = 3", PoseTrackCodec.decode(gzip(json.replaceFirst("\"v\":2", "\"v\":3"))))
    }

    @Test
    fun inconsistentColumnsDecodeToNull() {
        val json = gunzip(PoseTrackCodec.encode(syntheticTrack(frameCount = 20)))
        // Una columna con un valor de menos.
        val broken = json.replaceFirst("\"n\":[", "\"n\":[1,")
        assertNull(PoseTrackCodec.decode(gzip(broken)))
    }

    @Test
    fun columnsWrittenByAnotherWriterWithFewerPointsStillDecode() {
        // La cabecera manda: si un escritor guarda solo nariz y cadera izquierda, el resto vuelve vacío.
        val json = """
            {"v":2,"mediaId":"x","engine":{"id":"e","model":"m","delegate":"d"},"w":10,"h":20,"rot":0,"fps":30.0,"stride":1,
             "vfr":false,"aspectKnown":true,"img":[0,23],"wld":[],"t0":1000,"dt":[33333],"n":[1,0],
             "x":[5000,6000,0,0],"y":[2500,7500,0,0],"z":[0,0,0,0],"vis":[90,80,0,0],"pres":[100,100,0,0]}
        """.trimIndent()
        val decoded = PoseTrackCodec.decode(gzip(json))
        assertNotNull(decoded)
        decoded!!
        assertEquals(2, decoded.frames.size)
        assertEquals(1000L, decoded.frames[0].tUs)
        assertEquals(34_333L, decoded.frames[1].tUs)
        val nose = decoded.frames[0].point(PoseLandmark.NOSE)!!
        assertEquals(0.5f, nose.x, 1e-6f)
        assertEquals(0.25f, nose.y, 1e-6f)
        assertEquals(0.9f, nose.visibility, 1e-6f)
        val hip = decoded.frames[0].point(PoseLandmark.LEFT_HIP)!!
        assertEquals(0.6f, hip.x, 1e-6f)
        assertEquals(0.75f, hip.y, 1e-6f)
        assertNull(decoded.frames[0].point(PoseLandmark.RIGHT_HIP))
        assertFalse(decoded.frames[1].hasPose)
    }

    // ---- utilidades ----

    private fun trackOf(frames: List<PoseFrameV2>) = PoseTrackV2(
        mediaId = "m",
        engine = PoseEngineInfo("mediapipe-pose", "full", "GPU"),
        frameWidth = 720,
        frameHeight = 1280,
        rotationApplied = 0,
        nominalFps = 30f,
        poseStride = 1,
        vfr = false,
        frames = frames,
    )

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { String(it.readBytes(), Charsets.UTF_8) }

    /**
     * Traza sintética VFR: algunos fotogramas sin atleta, otros con 2 poses, un punto que falta de vez en cuando y
     * «world» ausente en uno de cada cinco. Con [smooth] el movimiento es suave (como el de una persona real), y
     * [sigma] le añade ruido gaussiano en x e y (más ruido proporcional en z, visibilidad y «world»).
     */
    private fun syntheticTrack(frameCount: Int, smooth: Boolean = false, sigma: Double = 0.0): PoseTrackV2 {
        val rnd = Random(1234)
        val frames = ArrayList<PoseFrameV2>(frameCount)
        var t = 5_000_000L
        for (i in 0 until frameCount) {
            // Cadencia variable: 30 fps con alguna pausa y algún intervalo corto.
            t += when {
                i % 23 == 5 -> 50_000L
                i % 31 == 7 -> 16_667L
                else -> 33_333L
            }
            if (!smooth && i % 17 == 3) {
                frames += PoseFrameV2.of(t, 0, null, null)
                continue
            }
            val phase = i / 30.0 * 2 * Math.PI * 0.4
            val image = HashMap<PoseLandmark, LandmarkSample>()
            for ((k, lm) in PoseLandmark.KEPT_IMAGE.withIndex()) {
                if (lm == PoseLandmark.LEFT_ANKLE && i % 13 == 0) continue // punto que falta
                val jitter = if (smooth) 0.0 else (rnd.nextDouble() - 0.5) * 0.4
                val x = (0.5 + 0.25 * sin(phase + k * 0.3) + jitter * 0.3 + rnd.nextGaussian() * sigma).toFloat()
                val y = (0.1 + 0.8 * k / 27.0 + 0.05 * sin(phase + k * 0.1) + jitter * 0.2 + rnd.nextGaussian() * sigma).toFloat()
                val z = (0.2 * sin(phase * 0.5 + k) + rnd.nextGaussian() * sigma * 5).toFloat()
                val vis = if (smooth) (0.9 + rnd.nextGaussian() * sigma * 5).toFloat().coerceIn(0f, 1f) else rnd.nextFloat()
                val pres = if (smooth) (0.95 + rnd.nextGaussian() * sigma * 5).toFloat().coerceIn(0f, 1f) else rnd.nextFloat()
                image[lm] = LandmarkSample(x, y, z, vis, pres)
            }
            val world = if (i % 5 == 4) {
                null
            } else {
                PoseLandmark.KEPT_WORLD.withIndex().associate { (k, lm) ->
                    lm to WorldSample(
                        (0.4 * sin(phase + k) + rnd.nextGaussian() * sigma * 3).toFloat(),
                        (0.9 * sin(phase * 0.7 + k * 0.5) + rnd.nextGaussian() * sigma * 3).toFloat(),
                        (0.1 * sin(phase * 0.3 + k * 2.0) + (if (smooth) 0.0 else (rnd.nextDouble() - 0.5) * 0.02) +
                            rnd.nextGaussian() * sigma * 3).toFloat(),
                    )
                }
            }
            frames += PoseFrameV2.of(t, if (!smooth && i % 11 == 0) 2 else 1, image, world)
        }
        return PoseTrackV2(
            mediaId = "media-123",
            engine = PoseEngineInfo("mediapipe-pose", "full", "GPU"),
            frameWidth = 720,
            frameHeight = 1280,
            rotationApplied = 90,
            nominalFps = 29.97f,
            poseStride = 1,
            vfr = true,
            captureFps = 120f,
            focalLengthPx = 1234.5f,
            gravityRollDeg = 1.25f,
            gravityPitchDeg = -80.5f,
            aspectKnown = true,
            frames = frames,
        )
    }
}
