package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import com.example.kpkn.data.programs.PlanEditorialTable
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.periodization.ProgramVariant
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La tabla de identidades de las portadas: los 30 programas «a medida», sus reglas y la identidad de respaldo de Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoverIdentitiesTest {

    /** Los 30 ids «a medida» tal como los lista la tabla editorial (ver `PlanEditorialTable`). */
    private val generatedIds = PlanEditorialTable.byId.keys.filter { it.startsWith("generated:") }.sorted()

    @Test
    fun everyGeneratedProgramHasItsOwnIdentity() {
        assertEquals(30, generatedIds.size)
        assertEquals(generatedIds.toSet(), assignedCoverProgramIds)
        for (id in generatedIds) assertNotNull(id, assignedCoverIdentity(id))
    }

    @Test
    fun noTwoProgramsShareStylePaletteAndPrimary() {
        val keys = generatedIds.map { assignedCoverIdentity(it)!! }.map { Triple(it.style, it.palette, it.primary) }
        assertEquals("identidades repetidas: ${keys.groupingBy { it }.eachCount().filterValues { it > 1 }}", keys.size, keys.toSet().size)
    }

    @Test
    fun eachProfileUsesThreeStylesAndThreePalettesAndItsAccent() {
        for (profile in TrainingGoalProfile.entries) {
            val variants = ProgramVariant.entries.map { assignedCoverIdentity(GeneratedPlans.entryIdFor(profile, it))!! }
            assertEquals("estilos de ${profile.name}", 3, variants.map { it.style }.toSet().size)
            assertEquals("fondos de ${profile.name}", 3, variants.map { it.palette }.toSet().size)
            for (identity in variants) assertEquals("primario de ${profile.name}", goalAccent(profile), identity.primary)
        }
    }

    @Test
    fun theSecondaryAccentIsAnotherAccentOfTheSymbolPalette() {
        val accents = listOf(SymbolPalette.musculo, SymbolPalette.energia, SymbolPalette.ok, SymbolPalette.columna, SymbolPalette.mente)
        for (id in generatedIds) {
            val identity = assignedCoverIdentity(id)!!
            assertNotEquals("secundario de $id", identity.primary, identity.secondary)
            assertTrue("acento de $id", identity.secondary in accents && identity.primary in accents)
        }
    }

    @Test
    fun motifsStayInTheirRangesForEveryStyle() {
        for (r in 0 until 60) {
            assertTrue(motifFor(CoverStudioStyle.NUMERAL, r) in listOf(1, 6, 12))
            assertTrue(motifFor(CoverStudioStyle.TOPO, r) in 14..18)
            assertTrue(motifFor(CoverStudioStyle.PLATES, r) in 4..6)
            assertTrue(motifFor(CoverStudioStyle.GRID, r) in listOf(1, 6))
            assertTrue(motifFor(CoverStudioStyle.WAVES, r) in 9..12)
        }
    }

    @Test
    fun aTableRowWinsOverTheProgramIdInHome() {
        val row = assignedCoverIdentity("generated:hybrid:blocks")!!
        assertEquals(row, coverIdentityFor("un-programa-cualquiera", "generated:hybrid:blocks"))
        assertEquals(row, coverIdentityFor("generated:hybrid:blocks", null))
    }

    @Test
    fun aProgramOutsideTheTableGetsAStableValidFallback() {
        val id = "propio:mi-programa"
        val first = coverIdentityFor(id, null)
        assertEquals(first, coverIdentityFor(id, null))
        assertEquals(first.seed, stableHash(id))
        assertNotEquals(first.primary, first.secondary)
        assertNull(assignedCoverIdentity(id))
        // Un templateId sin fila cae al programa: la identidad de respaldo sigue saliendo del programa.
        assertEquals(first, coverIdentityFor(id, "ni-una-fila-en-la-tabla"))
    }

    @Test
    fun theFallbackUsesEveryStyleAndValidAccentsAcrossIds() {
        val identities = (0 until 300).map { coverIdentityFor("importado-$it", null) }
        assertEquals(CoverStudioStyle.entries.toSet(), identities.map { it.style }.toSet())
        for (identity in identities) assertNotEquals(identity.primary, identity.secondary)
    }

    @Test
    fun theStableHashIsTheSameForTheSameText() {
        assertEquals(stableHash("generated:powerlifting"), stableHash("generated:powerlifting"))
        assertNotEquals(stableHash("generated:powerlifting"), stableHash("generated:powerbuilding"))
    }

    @Test
    fun aPhotoOfTheProgramWinsOverTheStudio() {
        val identity = assignedCoverIdentity("generated:strongman")!!
        assertEquals(CoverArtMode.PHOTO, coverArtModeOf(hasPhoto = true, identity = identity))
        assertEquals(CoverArtMode.STUDIO, coverArtModeOf(hasPhoto = false, identity = identity))
        assertEquals(CoverArtMode.PROCEDURAL, coverArtModeOf(hasPhoto = false, identity = null))
    }
}
