package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * WP-S10 (B3): the start gate reads a manifest of under 1 KB, never the ~72 MB of CSV. Gradle writes it at build time (task
 * `generateFoodDataManifest`): the SHA-256 of every catalog CSV and, over those, one fingerprint. These tests are the contract
 * between that task and [FoodImporter.expectedFingerprint]: what the manifest looks like, how it is read, and that a manifest that is
 * missing or broken degrades to the data version instead of crashing the start or looping the import.
 */
class FoodDataManifestTest {

    private val fingerprint = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    private val fileHash = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"

    /** The shape the Gradle task writes: compact JSON, files sorted by name, the fingerprint last. */
    private val sample =
        """{"version":1,"files":{"food.csv":{"sha256":"$fileHash","bytes":6542148},"off_chile.csv":{"sha256":"$fileHash","bytes":53745763}},""" +
            """"fingerprint":"$fingerprint"}"""

    private fun opener(text: String): () -> InputStream = { ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) }

    // ─── Reading the manifest ─────────────────────────────────────────────

    @Test
    fun `the fingerprint of a manifest is read from its fingerprint field`() {
        assertEquals(fingerprint, FoodImporter.parseManifestFingerprint(sample))
        // It is JSON: whitespace and the order of the keys do not matter.
        assertEquals(fingerprint, FoodImporter.parseManifestFingerprint("""  { "fingerprint" : "$fingerprint" , "version":1 }  """))
    }

    @Test
    fun `a manifest that is not JSON or has no valid fingerprint reads as null`() {
        listOf(
            "", "   ", "{", "not json", "[]", "null", "42", "\"$fingerprint\"",
            """{"version":1,"files":{"food.csv":{"sha256":"ab""",
            """{"version":1,"files":{}}""",
            """{"fingerprint":null}""",
            """{"fingerprint":""}""",
            """{"fingerprint":42}""",
            """{"fingerprint":{"sha256":"$fingerprint"}}""",
            """{"fingerprint":["$fingerprint"]}""",
            """{"fingerprint":"${fingerprint.uppercase()}"}""",
            """{"fingerprint":"${fingerprint.dropLast(1)}"}""",
            """{"fingerprint":"${fingerprint}0"}""",
            """{"fingerprint":"${"g".repeat(64)}"}""",
        ).forEach { json -> assertNull("'$json'", FoodImporter.parseManifestFingerprint(json)) }
    }

    // ─── The fingerprint the gate compares ────────────────────────────────

    @Test
    fun `the fingerprint is the data version alone without a manifest and the version plus the manifest with one`() {
        val version = "v${FoodImporter.DATA_VERSION}"

        assertEquals(version, FoodImporter.versionFingerprint())
        assertEquals(version, FoodImporter.composeFingerprint(null))
        assertEquals(version, FoodImporter.composeFingerprint(""))
        assertEquals(version, FoodImporter.composeFingerprint("   "))
        assertEquals("$version+$fingerprint", FoodImporter.composeFingerprint(fingerprint))
    }

    @Test
    fun `the expected fingerprint reads the manifest asset`() {
        assertEquals("v${FoodImporter.DATA_VERSION}+$fingerprint", FoodImporter.expectedFingerprint(opener(sample)))
    }

    @Test
    fun `a missing or unreadable manifest falls back to the data version without throwing and stays stable`() {
        val fallback = FoodImporter.versionFingerprint()
        val missing: () -> InputStream = { throw FileNotFoundException(FoodImporter.MANIFEST_ASSET) }
        val noAssets: () -> InputStream = { throw IllegalStateException("the assets are gone") }
        val broken: () -> InputStream = {
            object : InputStream() {
                override fun read(): Int = throw IOException("disk")
            }
        }

        // A stable value is what keeps a build without manifest from importing the catalog again at every start.
        repeat(3) {
            assertEquals(fallback, FoodImporter.expectedFingerprint(missing))
            assertEquals(fallback, FoodImporter.expectedFingerprint(noAssets))
            assertEquals(fallback, FoodImporter.expectedFingerprint(broken))
            assertEquals(fallback, FoodImporter.expectedFingerprint(opener("")))
            assertEquals(fallback, FoodImporter.expectedFingerprint(opener("{not json")))
            assertEquals(fallback, FoodImporter.expectedFingerprint(opener("""{"fingerprint":"zz"}""")))
        }
    }

    // ─── The build side ───────────────────────────────────────────────────

    @Test
    fun `the manifest is not one of the assets the importer reads as data`() {
        assertEquals("food_data/manifest.json", FoodImporter.MANIFEST_ASSET)
        assertFalse(FoodImporter.MANIFEST_ASSET in FoodImporter.IMPORT_ASSETS)
    }

    @Test
    fun `the build script declares the task that writes the manifest the app reads`() {
        // The fallback is silent on purpose (a missing manifest must never crash the start), so nothing else would notice the task
        // going away: the script must still name the task, what it hashes, where it writes and what it hangs from.
        val script = File("build.gradle.kts")
        assertTrue("${script.absolutePath} is not the app build script", script.isFile)
        val text = script.readText()

        assertTrue("task name", "generateFoodDataManifest" in text)
        assertTrue("input: the catalog CSV", "src/main/assets/food_data" in text)
        assertTrue("output: the asset the app reads", FoodImporter.MANIFEST_ASSET in text)
        assertTrue("registered as an assets source", "assets.srcDir" in text && "foodDataManifest" in text)
        assertTrue("hung from preBuild", "\"preBuild\"" in text)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `a generated manifest is what an independent hashing of the CSV gives`() {
        // Gradle writes it in preBuild, so it is there in every build; a run outside Gradle has nothing to compare and skips.
        val generated = File("build/generated/foodDataManifest/${FoodImporter.MANIFEST_ASSET}")
        assumeTrue("not generated (run through Gradle): ${generated.absolutePath}", generated.isFile)

        val csv = File("src/main/assets/food_data").listFiles { file -> file.isFile && file.extension == "csv" }.orEmpty().sortedBy { it.name }
        // It covers exactly what the importer reads, in file-name order.
        assertEquals(FoodImporter.IMPORT_ASSETS.map { it.removePrefix("food_data/") }.sorted(), csv.map { it.name })
        val hashes = csv.associate { it.name to sha256(it) }
        val fingerprint = sha256(csv.joinToString("\n") { "${it.name}:${hashes.getValue(it.name)}" }.toByteArray(Charsets.UTF_8))
        val expected = "{\"version\":1,\"files\":{" +
            csv.joinToString(",") { "\"${it.name}\":{\"sha256\":\"${hashes.getValue(it.name)}\",\"bytes\":${it.length()}}" } +
            "},\"fingerprint\":\"$fingerprint\"}"

        assertEquals(expected, generated.readText(Charsets.UTF_8))
        assertEquals(fingerprint, FoodImporter.parseManifestFingerprint(generated.readText(Charsets.UTF_8)))
    }
}
