package com.example.kpkn.data.repository

import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Lo que el usuario fija de un vídeo vive en `<id>.capture.json` sin pisar lo que el grabador ya escribió allí. JVM puro. */
class CaptureOverridesFileTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val seed = PlateSeed(tUs = 1_500_000L, cx = 512.5f, cy = 300f, r = 42.25f, userConfirmed = true)

    private fun captureFile(name: String = "vid-1.capture.json") = File(folder.root, "workout_media/2026-10/$name")

    private fun objectOf(file: File): JsonObject = Json.parseToJsonElement(file.readText()) as JsonObject

    @Test
    fun a_missing_or_unreadable_file_means_nothing_chosen() {
        val file = captureFile()
        assertEquals(CaptureOverrides(), CaptureOverridesFile.read(file))

        file.parentFile!!.mkdirs()
        file.writeText("")
        assertEquals(CaptureOverrides(), CaptureOverridesFile.read(file))
        file.writeText("{ esto no es json")
        assertEquals(CaptureOverrides(), CaptureOverridesFile.read(file))
        file.writeText("[1,2,3]")
        assertEquals("un JSON que no es un objeto tampoco vale", CaptureOverrides(), CaptureOverridesFile.read(file))
    }

    @Test
    fun updating_a_video_without_capture_metadata_creates_the_file_with_only_the_three_keys() {
        val file = captureFile()

        CaptureOverridesFile.update(file) {
            it.copy(userProfileOverride = AnalysisProfileId.SQUAT, plateSeed = seed, plateDiameterMm = 450)
        }

        val written = objectOf(file)
        assertEquals(setOf("userProfileOverride", "plateSeed", "plateDiameterMm"), written.keys)
        assertEquals("SQUAT", (written["userProfileOverride"] as JsonPrimitive).content)
        assertEquals(450, (written["plateDiameterMm"] as JsonPrimitive).intOrNull)
        val seedJson = written["plateSeed"] as JsonObject
        assertEquals(setOf("tUs", "cx", "cy", "r", "userConfirmed"), seedJson.keys)
        assertEquals(512.5, (seedJson["cx"] as JsonPrimitive).doubleOrNull!!, 0.0)
        assertEquals(CaptureOverrides(AnalysisProfileId.SQUAT, seed, 450), CaptureOverridesFile.read(file))
    }

    @Test
    fun the_keys_the_recorder_wrote_survive_every_update() {
        val file = captureFile()
        file.parentFile!!.mkdirs()
        file.writeText(
            """{"requestedFps":60,"grantedFps":60,"gravityRollDeg":-1.5,"lens":{"id":"0","focal":4.3},"recordedAtMs":1780000000000,"futuro":[1,2]}""",
        )

        CaptureOverridesFile.update(file) { it.copy(plateSeed = seed, plateDiameterMm = 400) }
        CaptureOverridesFile.update(file) { it.copy(userProfileOverride = AnalysisProfileId.ROW) }

        val written = objectOf(file)
        assertEquals(60, (written["requestedFps"] as JsonPrimitive).intOrNull)
        assertEquals(60, (written["grantedFps"] as JsonPrimitive).intOrNull)
        assertEquals(-1.5, (written["gravityRollDeg"] as JsonPrimitive).doubleOrNull!!, 0.0)
        assertEquals("""{"id":"0","focal":4.3}""", written["lens"].toString())
        assertEquals(1_780_000_000_000L, (written["recordedAtMs"] as JsonPrimitive).content.toLong())
        assertEquals("[1,2]", written["futuro"].toString())
        assertEquals(CaptureOverrides(AnalysisProfileId.ROW, seed, 400), CaptureOverridesFile.read(file))
    }

    @Test
    fun a_null_removes_only_its_own_key() {
        val file = captureFile()
        CaptureOverridesFile.update(file) {
            it.copy(userProfileOverride = AnalysisProfileId.CURL, plateSeed = seed, plateDiameterMm = 450)
        }

        CaptureOverridesFile.update(file) { it.copy(userProfileOverride = null) }

        val written = objectOf(file)
        assertFalse("userProfileOverride" in written)
        assertEquals(setOf("plateSeed", "plateDiameterMm"), written.keys)
        assertEquals(CaptureOverrides(null, seed, 450), CaptureOverridesFile.read(file))
    }

    @Test
    fun a_corrupt_file_is_replaced_by_a_new_one_with_the_choice() {
        val file = captureFile()
        file.parentFile!!.mkdirs()
        file.writeText("{\"requestedFps\": 6")

        CaptureOverridesFile.update(file) { it.copy(plateDiameterMm = 380) }

        assertEquals(CaptureOverrides(null, null, 380), CaptureOverridesFile.read(file))
        assertEquals(setOf("plateDiameterMm"), objectOf(file).keys)
    }

    @Test
    fun values_this_version_cannot_use_are_ignored_not_fatal() {
        val file = captureFile()
        file.parentFile!!.mkdirs()
        file.writeText(
            """{"userProfileOverride":"PERFIL_DEL_FUTURO","plateSeed":{"tUs":1,"cx":1.0,"cy":1.0,"r":-3.0,"userConfirmed":true},"plateDiameterMm":"mucho"}""",
        )

        assertEquals(CaptureOverrides(), CaptureOverridesFile.read(file))

        file.writeText("""{"plateSeed":{"tUs":9,"cx":10.0,"cy":20.0,"r":5.0}}""")
        val partial = CaptureOverridesFile.read(file)
        assertEquals("sin userConfirmed se lee como no confirmado", PlateSeed(9L, 10f, 20f, 5f, false), partial.plateSeed)
    }

    @Test
    fun the_write_is_atomic_and_leaves_no_temporary_file() {
        val file = captureFile()

        CaptureOverridesFile.update(file) { it.copy(plateDiameterMm = 450) }

        val names = file.parentFile!!.list()!!.toSet()
        assertEquals(setOf(file.name), names)
        assertTrue(file.length() > 0L)
    }

    @Test
    fun the_profile_names_are_the_enum_names() {
        val file = captureFile()
        for (profile in AnalysisProfileId.entries) {
            CaptureOverridesFile.update(file) { it.copy(userProfileOverride = profile) }
            assertEquals(profile, CaptureOverridesFile.read(file).userProfileOverride)
        }
        assertNull(CaptureOverridesFile.read(captureFile("otro.capture.json")).userProfileOverride)
    }
}
