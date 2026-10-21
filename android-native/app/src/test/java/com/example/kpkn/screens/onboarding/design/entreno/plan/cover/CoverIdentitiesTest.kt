package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanEditorialTable
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.periodization.ProgramVariant
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La tabla de identidades de las portadas: los 30 «a medida», los 47 del catálogo que se listan, sus reglas (vecinos, fondos, estilos y
 * acentos) y la identidad de respaldo de Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoverIdentitiesTest {

    /** Los 30 ids «a medida» tal como los lista la tabla editorial (ver `PlanEditorialTable`). */
    private val generatedIds = PlanEditorialTable.byId.keys.filter { it.startsWith("generated:") }.sorted()

    /** Los programas del catálogo que se listan (la biblioteca y el planificador): 47. */
    private val listedIds = PersonalizedPlanCatalog.listedEntries().map { it.id }.sorted()

    /** Las 77 portadas con identidad propia: los «a medida» y el catálogo. */
    private val allIds = generatedIds + listedIds

    private fun identityOf(id: String): CoverIdentity = assignedCoverIdentity(id)!!

    /** Lo que se ve de una portada: estilo, fondo, acentos y motivo, y los días de GRID y el rótulo de NUMERAL cuando cuentan. */
    private fun lookOf(identity: CoverIdentity): List<Any> = listOf(
        identity.style,
        identity.palette,
        identity.primary,
        identity.secondary,
        identity.motif,
        if (identity.style == CoverStudioStyle.GRID) identity.days else 0,
        if (identity.style == CoverStudioStyle.NUMERAL) identity.numeralIsWeeks else true,
    )

    @Test
    fun everyGeneratedProgramHasItsOwnIdentity() {
        assertEquals(30, generatedIds.size)
        for (id in generatedIds) assertNotNull(id, assignedCoverIdentity(id))
    }

    @Test
    fun everyListedProgramHasItsOwnIdentity() {
        assertEquals(47, listedIds.size)
        for (id in listedIds) assertNotNull(id, assignedCoverIdentity(id))
        assertEquals(allIds.toSet(), assignedCoverProgramIds)
    }

    @Test
    fun noTwoProgramsShareStylePaletteAndPrimary() {
        val keys = generatedIds.map { identityOf(it) }.map { Triple(it.style, it.palette, it.primary) }
        assertEquals("identidades repetidas: ${keys.groupingBy { it }.eachCount().filterValues { it > 1 }}", keys.size, keys.toSet().size)
    }

    @Test
    fun noTwoCoversLookAlike() {
        val looks = allIds.map { lookOf(identityOf(it)) }
        assertEquals("portadas iguales: ${looks.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}", looks.size, looks.toSet().size)
    }

    @Test
    fun eachProfileUsesThreeStylesAndThreePalettesAndItsAccent() {
        for (profile in TrainingGoalProfile.entries) {
            val variants = ProgramVariant.entries.map { identityOf(GeneratedPlans.entryIdFor(profile, it)) }
            assertEquals("estilos de ${profile.name}", 3, variants.map { it.style }.toSet().size)
            assertEquals("fondos de ${profile.name}", 3, variants.map { it.palette }.toSet().size)
            for (identity in variants) assertEquals("primario de ${profile.name}", goalAccent(profile), identity.primary)
        }
    }

    @Test
    fun theSecondaryAccentIsAnotherAccentOfTheSymbolPalette() {
        val accents = listOf(SymbolPalette.musculo, SymbolPalette.energia, SymbolPalette.ok, SymbolPalette.columna, SymbolPalette.mente)
        for (id in allIds) {
            val identity = identityOf(id)
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
            assertEquals(0, motifFor(CoverStudioStyle.HALFTONE, r))
            assertEquals(0, motifFor(CoverStudioStyle.TRACK, r))
            assertTrue(motifFor(CoverStudioStyle.TRAIL, r) in 3..5)
            assertTrue(motifFor(CoverStudioStyle.ENGRAVING, r) in 0..3)
            assertEquals(0, motifFor(CoverStudioStyle.BAUHAUS, r))
        }
    }

    @Test
    fun theCatalogMotifsStayInTheirRanges() {
        // En el catálogo, el grabado es siempre disco; las ondas y las rutas tienen su número de ondas y de hitos.
        for (id in listedIds) {
            val identity = identityOf(id)
            when (identity.style) {
                CoverStudioStyle.ENGRAVING -> assertEquals("$id", 0, identity.motif)
                CoverStudioStyle.WAVES -> assertTrue("$id", identity.motif in 9..12)
                CoverStudioStyle.TRAIL -> assertTrue("$id", identity.motif in 3..5)
                else -> Unit
            }
        }
    }

    @Test
    fun aMethodNumberIsNotAWeekCount() {
        assertFalse(identityOf("protocol:wendler-531-bbb").numeralIsWeeks)
        assertFalse(identityOf("protocol:nsuns-531-lp-4d").numeralIsWeeks)
        assertTrue(identityOf("protocol:smolov-jr").numeralIsWeeks)
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

    @Test
    fun paperIsAtMostAThirdOfAllCovers() {
        val papel = allIds.count { identityOf(it).palette == CoverPaletteKind.PAPEL }
        assertTrue("PAPEL $papel de ${allIds.size}", papel * 10 <= allIds.size * 3)
    }

    @Test
    fun everyStyleAppearsAtLeastThreeTimes() {
        val counts = allIds.groupingBy { identityOf(it).style }.eachCount()
        for (style in CoverStudioStyle.entries) assertTrue("$style: ${counts[style]}", (counts[style] ?: 0) >= 3)
    }

    @Test
    fun neighboursInEachDisciplineDifferInStyleAndPalette() {
        val disciplines = listOf(
            TrainingGoalProfile.POWERLIFTING to TrainingReference.POWERLIFTING,
            TrainingGoalProfile.POWERBUILDING to TrainingReference.POWERBUILDING,
            TrainingGoalProfile.BODYBUILDING to TrainingReference.HYPERTROPHY,
        )
        for ((profile, reference) in disciplines) {
            // La lista de PLAN de la disciplina: primero sus tres «a medida» y luego los de autor, en orden editorial.
            val generated = ProgramVariant.entries.map { GeneratedPlans.entryIdFor(profile, it) }
            val authored = PersonalizedPlanCatalog.listedEntries()
                .filter { reference in it.references }
                .sortedBy { it.rank }
                .map { it.id }
            val list = (generated + authored).map { identityOf(it) }
            for ((a, b) in list.zipWithNext()) {
                assertTrue("estilo repetido en ${profile.name}: ${a.style}", a.style != b.style)
                assertTrue("fondo repetido en ${profile.name}: ${a.palette}", a.palette != b.palette)
            }
        }
    }
}
