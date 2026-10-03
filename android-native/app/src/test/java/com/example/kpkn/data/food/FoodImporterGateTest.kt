package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S3 (B3): la decisión de importar el catálogo global es pura y barata. No lee assets: el SHA-256 de ~72 MB que
 * antes se calculaba en cada arranque en frío, antes de publicar las comidas del usuario, ya no participa.
 */
class FoodImporterGateTest {

    private val fingerprint = FoodImporter.datasetFingerprint()

    /** Checksum que guardaban las instalaciones anteriores a WP-S3: SHA-256 hexadecimal de los CSV. */
    private val legacySha256 = "ab12".repeat(16)

    private fun meta(
        version: Int = FoodImporter.DATA_VERSION,
        checksum: String = fingerprint,
    ) = FoodImporter.ImportMetadata(version = version, checksum = checksum, importedAt = "2026-10-02T00:00:00Z")

    // ─── Compuerta ────────────────────────────────────────────────────────

    @Test
    fun `the fingerprint is derived from the data version alone`() {
        assertEquals("v${FoodImporter.DATA_VERSION}", fingerprint)
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
}
