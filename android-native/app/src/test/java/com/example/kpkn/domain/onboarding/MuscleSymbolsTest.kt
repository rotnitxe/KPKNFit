package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.orderPointsFromBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleSymbolsTest {

    @Test
    fun everySymbolMapsToAnEngineCanonicalMuscleAndBack() {
        val canonicalNames = ORDER_MUSCLE_OPTIONS.map { it.value }.toSet()
        for (symbol in MuscleSymbol.entries) {
            val canonical = MuscleSymbols.canonical(symbol)
            assertTrue("«$canonical» no está en la lista canónica del motor de orden", canonical in canonicalNames)
            assertEquals(symbol, MuscleSymbols.fromCanonical(canonical))
        }
        // Un nombre canónico distinto por símbolo.
        assertEquals(MuscleSymbol.entries.size, MuscleSymbol.entries.map(MuscleSymbols::canonical).toSet().size)
    }

    @Test
    fun theMappingUsesTheNamesTheOrderEngineAlreadyKnows() {
        assertEquals("Pectorales", MuscleSymbols.canonical(MuscleSymbol.CHEST))
        assertEquals("Dorsales", MuscleSymbols.canonical(MuscleSymbol.BACK))
        assertEquals("Deltoides", MuscleSymbols.canonical(MuscleSymbol.SHOULDERS))
        assertEquals("Trapecio", MuscleSymbols.canonical(MuscleSymbol.TRAPS))
        assertEquals("Abdomen", MuscleSymbols.canonical(MuscleSymbol.ABS))
        assertEquals("Isquiosurales", MuscleSymbols.canonical(MuscleSymbol.HAMSTRINGS))
        assertEquals("Pantorrillas", MuscleSymbols.canonical(MuscleSymbol.CALVES))
    }

    @Test
    fun forearmsCountForRealInTheOrderBag() {
        // El catálogo y el motor lo llaman «Antebrazo» (singular), no «Antebrazos».
        assertEquals("Antebrazo", MuscleSymbols.canonical(MuscleSymbol.FOREARMS))
        val bag = MuscleSymbols.orderBagOf(setOf(MuscleSymbol.FOREARMS, MuscleSymbol.BICEPS))
        assertEquals(mapOf("Bíceps" to 1, "Antebrazo" to 1), bag)
        // El motor la normaliza sin perder el punto del antebrazo, también con el plural coloquial.
        assertEquals(bag, orderPointsFromBag(bag))
        assertEquals(mapOf("Antebrazo" to 1), orderPointsFromBag(mapOf("Antebrazos" to 1)))
        assertEquals(MuscleSymbol.FOREARMS, MuscleSymbols.fromCanonical("Antebrazos"))
    }

    @Test
    fun synonymsTheEngineNormalizesResolveToTheirSymbol() {
        assertEquals(MuscleSymbol.CHEST, MuscleSymbols.fromCanonical("pecho"))
        assertEquals(MuscleSymbol.BACK, MuscleSymbols.fromCanonical("espalda"))
        assertEquals(MuscleSymbol.HAMSTRINGS, MuscleSymbols.fromCanonical("isquios"))
        // «Erectores Espinales» existe en el motor pero no tiene símbolo: se ignora, no rompe.
        assertNull(MuscleSymbols.fromCanonical("Erectores Espinales"))
        assertEquals(setOf(MuscleSymbol.CHEST), MuscleSymbols.symbolsOf(mapOf("Pectorales" to 2, "Erectores Espinales" to 1)))
    }

    @Test
    fun theBagHasOnePointPerMuscleAndNeverExceedsTheEngineBudget() {
        val all = MuscleSymbol.entries.toSet()
        val bag = MuscleSymbols.orderBagOf(all)
        assertEquals(MuscleSymbols.MAX_SELECTION, bag.size)
        assertTrue(bag.values.all { it == 1 })
        assertNotNull("la bolsa cumple el contrato del motor", orderPointsFromBag(bag))
        assertTrue(MuscleSymbols.orderBagOf(emptySet()).isEmpty())
        assertEquals(setOf("Pectorales", "Dorsales"), MuscleSymbols.canonicalSet(setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK)))
    }

    @Test
    fun suggestionsFollowTheProfile() {
        assertEquals(
            setOf(MuscleSymbol.CHEST, MuscleSymbol.GLUTES, MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS, MuscleSymbol.ABS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.POWERLIFTING),
        )
        assertEquals(
            setOf(MuscleSymbol.FOREARMS, MuscleSymbol.BICEPS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.ARMWRESTLING),
        )
        assertEquals(
            setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.POWERBUILDING),
        )
        assertEquals(
            setOf(MuscleSymbol.BACK, MuscleSymbol.CHEST, MuscleSymbol.ABS, MuscleSymbol.SHOULDERS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.CALISTHENICS),
        )
        assertEquals(
            setOf(MuscleSymbol.SHOULDERS, MuscleSymbol.QUADS, MuscleSymbol.GLUTES, MuscleSymbol.TRAPS, MuscleSymbol.ABS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.WEIGHTLIFTING),
        )
        assertEquals(
            setOf(MuscleSymbol.BACK, MuscleSymbol.TRAPS, MuscleSymbol.ABS, MuscleSymbol.QUADS, MuscleSymbol.FOREARMS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.STRONGMAN),
        )
    }

    @Test
    fun bodybuildingAndTheGeneralProfilesSuggestNothingAndEverySuggestionFitsTheCap() {
        for (profile in listOf(TrainingGoalProfile.BODYBUILDING) + TrainingGoalProfile.general) {
            assertTrue("$profile", MuscleSuggestions.forProfile(profile).isEmpty())
        }
        assertTrue(MuscleSuggestions.forProfile(null).isEmpty())
        for (profile in TrainingGoalProfile.entries) {
            val suggestion = MuscleSuggestions.forProfile(profile)
            assertTrue("$profile", suggestion.size <= MuscleSymbols.MAX_SELECTION)
            assertNotNull(orderPointsFromBag(MuscleSymbols.orderBagOf(suggestion)))
        }
    }
}
