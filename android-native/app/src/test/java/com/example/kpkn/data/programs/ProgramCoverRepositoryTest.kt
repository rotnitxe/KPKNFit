package com.example.kpkn.data.programs

import com.example.kpkn.data.models.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ProgramCoverRepositoryTest {

    /** Assets falsos: [files] son rutas completas bajo `assets/`. */
    private class FakeCoverSource(private val files: Set<String>) : CoverAssetSource {
        override fun listFiles(dir: String): Set<String> =
            files.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }.toSet()

        override fun open(path: String): InputStream = ByteArrayInputStream(ByteArray(0))
    }

    @Test
    fun sanitizeCoverIdLowercasesAndTurnsSeparatorsIntoUnderscores() {
        assertEquals("native_strength_foundation_v2", sanitizeCoverId("native:strength-foundation-v2"))
        assertEquals("generated_strength_muscle_blocks", sanitizeCoverId("generated:strength-muscle:blocks"))
        assertEquals("template_power_12_3", sanitizeCoverId("template:power-12-3"))
    }

    @Test
    fun sanitizeCoverIdReplacesEverythingElseThatIsNotAsciiAlphanumeric() {
        assertEquals("a_b_c_9", sanitizeCoverId("A/B c.9"))
        assertEquals("_", sanitizeCoverId("ñ"))
    }

    @Test
    fun coverPathForFindsTheAssetOnlyWhenTheFileExists() {
        val repository = ProgramCoverRepository(
            FakeCoverSource(setOf("program_covers/cover_native_strength_foundation_v2.webp")),
        )

        assertEquals(
            "program_covers/cover_native_strength_foundation_v2.webp",
            repository.coverPathFor("native:strength-foundation-v2"),
        )
        assertNull(repository.coverPathFor("native:strength-foundation-v3"))
        assertNull(repository.coverPathFor(""))
    }

    @Test
    fun coverPathForIgnoresFilesOutsideTheCoversFolder() {
        val repository = ProgramCoverRepository(FakeCoverSource(setOf("cover_native_bodyweight.webp")))

        assertNull(repository.coverPathFor("native:bodyweight"))
    }

    @Test
    fun coverPathForIsNullWhenTheCoversFolderIsMissing() {
        val repository = ProgramCoverRepository(FakeCoverSource(emptySet()))

        assertNull(repository.coverPathFor("native:bodyweight"))
    }

    @Test
    fun kpknCoverSchemeResolvesToItsCatalogEntry() {
        assertEquals("kpkn-cover://native:bodyweight", programCoverValueOf("native:bodyweight"))
        assertEquals("native:bodyweight", programCoverEntryIdOf("kpkn-cover://native:bodyweight"))
    }

    @Test
    fun unknownSchemesAndEmptyValuesResolveToNoEntry() {
        assertNull(programCoverEntryIdOf(null))
        assertNull(programCoverEntryIdOf(""))
        assertNull(programCoverEntryIdOf("gradient://ember"))
        assertNull(programCoverEntryIdOf("foo://native:bodyweight"))
        assertNull(programCoverEntryIdOf("kpkn-cover://"))
        assertNull(programCoverEntryIdOf("kpkn-cover://  "))
    }

    @Test
    fun catalogCoverOnlyFillsAProgramThatHasNoCoverYet() {
        val fresh = Program(id = "p1", name = "Plan")
        assertEquals("kpkn-cover://native:bodyweight", fresh.withCatalogCover("native:bodyweight").coverImage)

        val chosen = fresh.copy(coverImage = "gradient://lagoon")
        assertSame(chosen, chosen.withCatalogCover("native:bodyweight"))
    }

    @Test
    fun sampleSizeKeepsTheDecodedWidthAtLeastTheRequestedOne() {
        assertEquals(1, sampleSizeFor(1600, 1600))
        assertEquals(2, sampleSizeFor(1600, 412))
        assertEquals(16, sampleSizeFor(1600, 100))
        assertEquals(1, sampleSizeFor(0, 100))
        assertEquals(1, sampleSizeFor(1600, 0))
    }
}
