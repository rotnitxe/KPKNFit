package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** El punto de control `<id>.pose.part`: va y vuelve entero y cualquier archivo roto, de otro vídeo o de otra versión se descarta. */
class AnalysisCheckpointTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val set = PipelineTestKit.squat(reps = 2)
    private val partial: PoseTrackV2 = set.track.copy(frames = set.track.frames.take(120), mediaId = "clip")

    private fun header(
        usesPlate: Boolean = false,
        stage: String = AnalysisCheckpoint.STAGE_NONE,
        seedKey: String? = null,
        stride: Int = 1,
    ) = AnalysisCheckpoint.Header(
        mediaId = "clip", poseVersion = 1, videoBytes = 2048L, videoModifiedMs = 77L, usesPlate = usesPlate, poseStride = stride,
        timeScale = 1.0, frameWidth = 406, frameHeight = 720, lastPtsUs = partial.frames.last().tUs, nextIndex = 120,
        plateStage = stage, seedKey = seedKey, poseErrors = 3,
    )

    private fun bar() = BarTrack(
        "clip", 406, 720, null, PlateSeed(500_000L, 100f, 200f, 40f, false),
        List(10) { BarSample(500_000L + it * 33_333L, 100f + it, 200f, 40f, 1f, 0.9f, BarSampleSource.HOUGH) },
    )

    private fun usable(
        cp: AnalysisCheckpoint,
        mediaId: String = "clip",
        bytes: Long = 2048L,
        modified: Long = 77L,
        version: Int = 1,
        plate: Boolean = false,
        width: Int = 406,
        height: Int = 720,
        scale: Double = 1.0,
        seedKey: String? = null,
    ) = AnalysisCheckpoints.isUsable(cp, mediaId, bytes, modified, version, plate, width, height, scale, seedKey)

    @Test
    fun aCheckpointRoundTripsWithItsPoseBarAndHeader() {
        val seedKey = AnalysisCheckpoint.seedKey(PlateSeed(1L, 2f, 3f, 4f, true))
        val original = AnalysisCheckpoint(header(true, AnalysisCheckpoint.STAGE_TRACKING, seedKey), partial, bar())
        val file = File(folder.root, "clip.pose.part")
        AnalysisCheckpoints.write(file, original)
        val back = checkNotNull(AnalysisCheckpoints.read(file))

        assertEquals(original.header, back.header)
        assertEquals(120, back.pose.frames.size)
        assertEquals(partial.frames.map { it.tUs }, back.pose.frames.map { it.tUs })
        assertEquals(partial.frames.map { it.hasPose }, back.pose.frames.map { it.hasPose })
        assertEquals(10, checkNotNull(back.bar).samples.size)
        assertEquals(PlateSeed(500_000L, 100f, 200f, 40f, false), back.bar?.seed)
        assertFalse("no deja el temporal", File(folder.root, "clip.pose.part.tmp").exists())
        assertTrue(usable(back, plate = true, seedKey = seedKey))
    }

    @Test
    fun aCheckpointWithoutBarHasNoBarWhenTheProfileDoesNotUseThePlate() {
        val original = AnalysisCheckpoint(header(), partial, null)
        val back = checkNotNull(AnalysisCheckpoints.decode(AnalysisCheckpoints.encode(original)))
        assertNull(back.bar)
        assertTrue(usable(back))
        // Con un perfil que sí usa el disco, un punto de control sin traza del disco no sirve.
        assertFalse(usable(back, plate = true))
    }

    @Test
    fun brokenFilesDecodeToNullAndNeverThrow() {
        val good = AnalysisCheckpoints.encode(AnalysisCheckpoint(header(), partial, null))
        assertNotNull(AnalysisCheckpoints.decode(good))
        assertNull(AnalysisCheckpoints.decode(ByteArray(0)))
        assertNull(AnalysisCheckpoints.decode(ByteArray(40) { 7 }))
        // Truncado: se pierde el CRC y parte del contenido.
        assertNull(AnalysisCheckpoints.decode(good.copyOf(good.size / 2)))
        assertNull(AnalysisCheckpoints.decode(good.copyOf(good.size - 1)))
        // Un byte cambiado en cualquier sitio rompe el CRC.
        for (position in listOf(0, 5, 12, good.size / 3, good.size / 2, good.size - 12)) {
            val flipped = good.copyOf().also { it[position] = (it[position].toInt() xor 0x55).toByte() }
            assertNull("byte $position", AnalysisCheckpoints.decode(flipped))
        }
        // Y el archivo que no existe o es una carpeta.
        assertNull(AnalysisCheckpoints.read(File(folder.root, "no-existe.pose.part")))
        assertNull(AnalysisCheckpoints.read(folder.newFolder("carpeta.pose.part")))
        // El archivo de verdad, tal cual.
        val file = File(folder.root, "ok.pose.part")
        file.writeBytes(good)
        assertNotNull(AnalysisCheckpoints.read(file))
        assertArrayEquals(good, file.readBytes())
    }

    @Test
    fun aCheckpointOnlyServesTheSameVideoVersionPlateAndFrameSize() {
        val cp = AnalysisCheckpoint(header(), partial, null)
        assertTrue(usable(cp))
        assertFalse("otro medio", usable(cp, mediaId = "otro"))
        assertFalse("otro tamaño de archivo", usable(cp, bytes = 2049L))
        assertFalse("otra fecha", usable(cp, modified = 78L))
        assertFalse("otra versión de pose", usable(cp, version = 2))
        assertFalse("otro tamaño de fotograma", usable(cp, width = 720, height = 1280))
        assertFalse("otra escala de tiempo (cámara lenta)", usable(cp, scale = 0.5))
        assertFalse("otra semilla confirmada", usable(cp, seedKey = "1:2:3:4"))
        // Un salto de pose que no es 1 ni 2, o un punto de control sin fotogramas procesados, tampoco valen.
        assertFalse(usable(AnalysisCheckpoint(header(stride = 3), partial, null)))
        assertFalse(usable(AnalysisCheckpoint(header().copy(nextIndex = 0), partial, null)))
        assertFalse(usable(AnalysisCheckpoint(header(), partial.copy(frames = emptyList()), null)))
        // Un perfil con disco necesita que el punto de control ya no esté buscándolo.
        assertFalse(usable(AnalysisCheckpoint(header(true, AnalysisCheckpoint.STAGE_NONE), partial, bar()), plate = true))
        assertTrue(usable(AnalysisCheckpoint(header(true, AnalysisCheckpoint.STAGE_GAVE_UP), partial, BarTrack("clip", 406, 720, null, null, emptyList())), plate = true))
    }
}
