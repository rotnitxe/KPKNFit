package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S3 (B3): la decisión de importar el catálogo global es pura y barata. No lee assets: el SHA-256 de ~72 MB que
 * antes se calculaba en cada arranque en frío, antes de publicar las comidas del usuario, ya no participa.
 *
 * WP-S10: la huella que espera la compuerta trae, además de la versión de datos, el SHA-256 de los CSV que resume el manifiesto del
 * build ("v<DATA_VERSION>+<sha256>"). Aquí solo se prueba la compuerta; la lectura del manifiesto está en FoodDataManifestTest.
 */
class FoodImporterGateTest {

    /** La huella de un build sin manifiesto: solo la versión de datos. */
    private val fingerprint = FoodImporter.versionFingerprint()

    /** Checksum que guardaban las instalaciones anteriores a WP-S3: SHA-256 hexadecimal de los CSV. */
    private val legacySha256 = "ab12".repeat(16)

    private fun meta(
        version: Int = FoodImporter.DATA_VERSION,
        checksum: String = fingerprint,
    ) = FoodImporter.ImportMetadata(version = version, checksum = checksum, importedAt = "2026-10-02T00:00:00Z")

    // ─── Compuerta ────────────────────────────────────────────────────────

    @Test
    fun `the fingerprint of a build without a manifest is the data version alone`() {
        assertEquals("v${FoodImporter.DATA_VERSION}", fingerprint)
        assertEquals(fingerprint, FoodImporter.composeFingerprint(null))
    }

    @Test
    fun `skips the import when the catalog exists and version and fingerprint match`() {
        assertFalse(FoodImporter.shouldImport(alreadyImported = true, meta = meta(), expectedFingerprint = fingerprint))
    }

    @Test
    fun `imports when the stored version differs from the data version`() {
        assertTrue(FoodImporter.shouldImport(true, meta(version = FoodImporter.DATA_VERSION - 1), fingerprint))
        assertTrue(FoodImporter.shouldImport(true, meta(version = FoodImporter.DATA_VERSION + 1), fingerprint))
    }

    @Test
    fun `imports when there is no stored meta`() {
        assertTrue(FoodImporter.shouldImport(alreadyImported = true, meta = null, expectedFingerprint = fingerprint))
    }

    @Test
    fun `imports when the global catalog is empty even if the meta matches`() {
        assertTrue(FoodImporter.shouldImport(alreadyImported = false, meta = meta(), expectedFingerprint = fingerprint))
        assertTrue(FoodImporter.shouldImport(alreadyImported = false, meta = null, expectedFingerprint = fingerprint))
    }

    @Test
    fun `imports when the fingerprint changed`() {
        assertTrue(FoodImporter.shouldImport(true, meta(checksum = "v0"), fingerprint))
        assertTrue(FoodImporter.shouldImport(true, meta(), "manifest-abc123"))
    }

    // ─── Instalaciones previas: el checksum guardado era el SHA-256 de los CSV ─────────────────

    @Test
    fun `a legacy sha256 checksum at the same data version is adopted instead of re-importing`() {
        val adopted = FoodImporter.adoptLegacyChecksum(meta(checksum = legacySha256), fingerprint)

        assertEquals(fingerprint, adopted?.checksum)
        assertFalse(FoodImporter.shouldImport(alreadyImported = true, meta = adopted, expectedFingerprint = fingerprint))
    }

    @Test
    fun `a legacy sha256 checksum at another data version still imports`() {
        val old = meta(version = FoodImporter.DATA_VERSION - 1, checksum = legacySha256)

        val adopted = FoodImporter.adoptLegacyChecksum(old, fingerprint)

        assertEquals(old, adopted)
        assertTrue(FoodImporter.shouldImport(alreadyImported = true, meta = adopted, expectedFingerprint = fingerprint))
    }

    @Test
    fun `only a full lowercase sha256 is treated as legacy`() {
        listOf("something-else", "ab12".repeat(15), "AB12".repeat(16), "").forEach { checksum ->
            val unknown = meta(checksum = checksum)
            val adopted = FoodImporter.adoptLegacyChecksum(unknown, fingerprint)
            assertEquals("checksum '$checksum' must be kept as is", unknown, adopted)
            assertTrue(FoodImporter.shouldImport(alreadyImported = true, meta = adopted, expectedFingerprint = fingerprint))
        }
    }

    @Test
    fun `adoption only applies while the fingerprint is the version scheme`() {
        val legacy = meta(checksum = legacySha256)

        val adopted = FoodImporter.adoptLegacyChecksum(legacy, "manifest-abc123")

        assertEquals(legacy, adopted)
        assertTrue(FoodImporter.shouldImport(alreadyImported = true, meta = adopted, expectedFingerprint = "manifest-abc123"))
    }

    @Test
    fun `a missing meta stays missing`() {
        assertNull(FoodImporter.adoptLegacyChecksum(null, fingerprint))
    }

    // ─── WP-S10: la huella trae el manifiesto de los CSV ──────────────────

    private val manifestA = "a".repeat(64)
    private val manifestB = "b".repeat(64)

    @Test
    fun `a changed manifest imports even when the data version is the same`() {
        val withA = FoodImporter.composeFingerprint(manifestA)
        val withB = FoodImporter.composeFingerprint(manifestB)

        assertTrue(FoodImporter.shouldImport(alreadyImported = true, meta = meta(checksum = withA), expectedFingerprint = withB))
        assertFalse(FoodImporter.shouldImport(alreadyImported = true, meta = meta(checksum = withA), expectedFingerprint = withA))
    }

    @Test
    fun `an install holding the version only fingerprint imports once when the build ships a manifest`() {
        val shipped = FoodImporter.composeFingerprint(manifestA)

        // Las instalaciones sin manifiesto guardaron solo la versión ("v<DATA_VERSION>"); tras ese único import guardan la huella del manifiesto y se callan.
        assertTrue(FoodImporter.shouldImport(true, meta(checksum = fingerprint), shipped))
        assertFalse(FoodImporter.shouldImport(true, meta(checksum = shipped), shipped))
    }

    @Test
    fun `a legacy sha256 is never adopted under a manifest fingerprint`() {
        val shipped = FoodImporter.composeFingerprint(manifestA)
        val legacy = meta(checksum = legacySha256)

        assertEquals(legacy, FoodImporter.adoptLegacyChecksum(legacy, shipped))
        assertTrue(FoodImporter.shouldImport(alreadyImported = true, meta = legacy, expectedFingerprint = shipped))
    }

    @Test
    fun `a build that loses its manifest falls back to the version and then stays quiet`() {
        val shipped = FoodImporter.composeFingerprint(manifestA)

        // Una vez (lo guardado era la huella del manifiesto), y como la huella de respaldo es estable no hay bucle de importaciones.
        assertTrue(FoodImporter.shouldImport(true, meta(checksum = shipped), fingerprint))
        assertFalse(FoodImporter.shouldImport(true, meta(checksum = fingerprint), fingerprint))
    }
}
