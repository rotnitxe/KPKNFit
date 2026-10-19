package com.example.kpkn.data.media.technique

import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `<id>.capture.json`: lo que el grabador escribe (gravedad, focal, fps…) y lo que el usuario decide (perfil, disco) viven en el mismo
 * archivo y ninguno pisa al otro.
 */
class CaptureMetadataTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "clip.capture.json")

    private fun obj(): JsonObject = Json.parseToJsonElement(file.readText()) as JsonObject

    @Test
    fun aMissingOrBrokenFileReadsAsNoData() {
        assertEquals(CaptureMetadata(), CaptureMetadataFile.read(file))
        file.writeText("")
        assertEquals(CaptureMetadata(), CaptureMetadataFile.read(file))
        file.writeText("{no es json")
        assertEquals(CaptureMetadata(), CaptureMetadataFile.read(file))
        file.writeText("[1, 2]")
        assertEquals(CaptureMetadata(), CaptureMetadataFile.read(file))
        // Un valor de otro tipo es «sin dato», no un fallo.
        file.writeText("""{"grantedFps":"rápido","gravityRollDeg":null,"focalLengthPx":1480.5,"lens":"","recordedAtMs":"ayer"}""")
        val read = CaptureMetadataFile.read(file)
        assertNull(read.grantedFps)
        assertNull(read.gravityRollDeg)
        assertEquals(1480.5f, read.focalLengthPx)
        assertNull(read.lens)
        assertNull(read.recordedAtMs)
    }

    @Test
    fun theRecordersMetadataRoundTrips() {
        val metadata = CaptureMetadata(
            requestedFps = 60f, grantedFps = 30f, gravityRollDeg = -1.25f, gravityPitchDeg = 84f, focalLengthPx = 1480f,
            lens = "main", deviceModel = "SM-F731B", recordedAtMs = 1_780_000_000_000L,
        )
        CaptureMetadataFile.write(file, metadata)
        assertEquals(metadata, CaptureMetadataFile.read(file))
        assertFalse(File(folder.root, "clip.capture.json.tmp").exists())
        assertEquals(1_780_000_000_000L, (obj()["recordedAtMs"] as JsonPrimitive).content.toLong())
    }

    @Test
    fun writingMetadataKeepsTheUsersChoicesAndUnknownKeys() {
        val seed = PlateSeed(1_500_000L, 512.5f, 300f, 42f, true)
        CaptureOverridesFile.update(file) { it.copy(userProfileOverride = AnalysisProfileId.SQUAT, plateSeed = seed, plateDiameterMm = 450) }
        file.writeText(file.readText().dropLast(1) + ""","versionFutura":{"x":[1,2,3]}}""")

        CaptureMetadataFile.write(file, CaptureMetadata(grantedFps = 60f, focalLengthPx = 1480f))

        assertEquals(CaptureOverrides(AnalysisProfileId.SQUAT, seed, 450), CaptureOverridesFile.read(file))
        assertTrue("clave desconocida intacta", "versionFutura" in obj())
        assertEquals(CaptureMetadata(grantedFps = 60f, focalLengthPx = 1480f), CaptureMetadataFile.read(file))
    }

    @Test
    fun theUsersChoicesKeepTheMetadata() {
        CaptureMetadataFile.write(file, CaptureMetadata(requestedFps = 60f, gravityRollDeg = 2f, lens = "wide"))
        CaptureOverridesFile.update(file) { it.copy(plateDiameterMm = 400) }
        CaptureOverridesFile.update(file) { it.copy(userProfileOverride = AnalysisProfileId.ROW) }
        assertEquals(CaptureMetadata(requestedFps = 60f, gravityRollDeg = 2f, lens = "wide"), CaptureMetadataFile.read(file))
        assertEquals(CaptureOverrides(AnalysisProfileId.ROW, null, 400), CaptureOverridesFile.read(file))
    }

    @Test
    fun updateChangesOnlyWhatTheFunctionChangesAndNullRemovesAKey() {
        CaptureMetadataFile.write(file, CaptureMetadata(requestedFps = 60f, grantedFps = 60f, lens = "main"))
        CaptureMetadataFile.update(file) { it.copy(grantedFps = 30f, lens = null, deviceModel = "Z Flip5") }
        assertEquals(CaptureMetadata(requestedFps = 60f, grantedFps = 30f, deviceModel = "Z Flip5"), CaptureMetadataFile.read(file))
        assertFalse("lens" in obj())
        // Un número que no es finito no se escribe (el JSON no admite NaN).
        CaptureMetadataFile.update(file) { it.copy(gravityPitchDeg = Float.NaN, focalLengthPx = Float.POSITIVE_INFINITY) }
        assertNull(CaptureMetadataFile.read(file).gravityPitchDeg)
        assertNull(CaptureMetadataFile.read(file).focalLengthPx)
    }

    @Test
    fun concurrentWritersDoNotLoseEachOthersKeys() {
        val t1 = Thread {
            for (i in 1..200) {
                CaptureMetadataFile.update(file) {
                    it.copy(
                        requestedFps = 60f,
                        grantedFps = (i % 60 + 1).toFloat(),
                        gravityRollDeg = 1.5f,
                        gravityPitchDeg = 85f,
                        focalLengthPx = 1450f,
                        lens = "main",
                        deviceModel = "Pixel $i",
                        recordedAtMs = 1700000000000L + i,
                    )
                }
            }
        }
        val t2 = Thread {
            for (i in 1..200) {
                CaptureOverridesFile.update(file) {
                    it.copy(
                        userProfileOverride = AnalysisProfileId.SQUAT,
                        plateDiameterMm = 450,
                        plateSeed = PlateSeed(tUs = i.toLong(), cx = 100f, cy = 100f, r = 20f, userConfirmed = true),
                    )
                }
            }
        }
        t1.start()
        t2.start()
        t1.join()
        t2.join()

        val meta = CaptureMetadataFile.read(file)
        val overrides = CaptureOverridesFile.read(file)

        assertEquals(60f, meta.requestedFps)
        assertNotNull(meta.grantedFps)
        assertEquals(1.5f, meta.gravityRollDeg)
        assertEquals(85f, meta.gravityPitchDeg)
        assertEquals(1450f, meta.focalLengthPx)
        assertEquals("main", meta.lens)
        assertNotNull(meta.deviceModel)
        assertNotNull(meta.recordedAtMs)

        assertEquals(AnalysisProfileId.SQUAT, overrides.userProfileOverride)
        assertEquals(450, overrides.plateDiameterMm)
        assertNotNull(overrides.plateSeed)
    }

    @Test
    fun theMetadataFileNameIsTheSidecarsCaptureFile() {
        val video = File(folder.root, "2026-10/abc.mp4")
        assertEquals(File(folder.root, "2026-10/abc.capture.json").absolutePath, TechniqueSidecars.captureFile(video).absolutePath)
    }
}
