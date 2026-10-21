package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.generator.ItemRole
import com.example.kpkn.domain.training.generator.Muscles
import com.example.kpkn.domain.training.generator.PriorityTargets
import com.example.kpkn.domain.training.generator.RoutinePattern
import com.example.kpkn.domain.training.orderPointsFromBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleSymbolsTest {

    @Test
    fun everySymbolMapsToEngineCanonicalMusclesAndBack() {
        val canonicalNames = ORDER_MUSCLE_OPTIONS.map { it.value }.toSet()
        for (symbol in MuscleSymbol.entries) {
            val canonicals = MuscleSymbols.canonicals(symbol)
            assertTrue("$symbol sin músculos canónicos", canonicals.isNotEmpty())
            for (canonical in canonicals) {
                assertTrue("«$canonical» no está en la lista canónica del motor de orden", canonical in canonicalNames)
                assertEquals(symbol, MuscleSymbols.fromCanonical(canonical))
            }
        }
        // Ningún músculo canónico pertenece a dos símbolos.
        val all = MuscleSymbol.entries.flatMap { MuscleSymbols.canonicals(it) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun theMappingUsesTheNamesTheOrderEngineAlreadyKnows() {
        assertEquals(listOf("Pectorales"), MuscleSymbols.canonicals(MuscleSymbol.CHEST))
        assertEquals(listOf("Dorsales"), MuscleSymbols.canonicals(MuscleSymbol.BACK))
        assertEquals(listOf("Deltoides"), MuscleSymbols.canonicals(MuscleSymbol.SHOULDERS))
        assertEquals(listOf("Abdomen"), MuscleSymbols.canonicals(MuscleSymbol.ABS))
        assertEquals(listOf("Isquiosurales"), MuscleSymbols.canonicals(MuscleSymbol.HAMSTRINGS))
        assertEquals(listOf("Pantorrillas"), MuscleSymbols.canonicals(MuscleSymbol.CALVES))
    }

    @Test
    fun brazosIsBicepsAndTricepsTogetherAndReadsBackFromEither() {
        assertEquals(listOf("Bíceps", "Tríceps"), MuscleSymbols.canonicals(MuscleSymbol.ARMS))
        assertEquals(MuscleSymbol.ARMS, MuscleSymbols.fromCanonical("Bíceps"))
        assertEquals(MuscleSymbol.ARMS, MuscleSymbols.fromCanonical("Tríceps"))
        assertEquals(MuscleSymbol.ARMS, MuscleSymbols.fromCanonical("brazos"))
        // Un punto en cada uno al escribirlo, y se lee de vuelta como Brazos desde cualquiera de los dos.
        assertEquals(mapOf("Bíceps" to 1, "Tríceps" to 1), MuscleSymbols.orderBagOf(setOf(MuscleSymbol.ARMS)))
        assertEquals(setOf(MuscleSymbol.ARMS), MuscleSymbols.symbolsOf(mapOf("Bíceps" to 1)))
        assertEquals(setOf(MuscleSymbol.ARMS), MuscleSymbols.symbolsOf(mapOf("Tríceps" to 1)))
        assertEquals(setOf(MuscleSymbol.ARMS), MuscleSymbols.symbolsOf(mapOf("Bíceps" to 1, "Tríceps" to 1)))
        assertEquals(
            setOf(MuscleSymbol.ARMS),
            MuscleSymbols.symbolsOf(MuscleSymbols.orderBagOf(setOf(MuscleSymbol.ARMS))),
        )
    }

    @Test
    fun forearmsCountForRealInTheOrderBag() {
        // El catálogo y el motor lo llaman «Antebrazo» (singular), no «Antebrazos».
        assertEquals(listOf("Antebrazo"), MuscleSymbols.canonicals(MuscleSymbol.FOREARMS))
        val bag = MuscleSymbols.orderBagOf(setOf(MuscleSymbol.FOREARMS, MuscleSymbol.ARMS))
        assertEquals(mapOf("Bíceps" to 1, "Tríceps" to 1, "Antebrazo" to 1), bag)
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
    fun theBagHasOnePointPerCanonicalMuscleAndNeverExceedsTheEngineBudget() {
        // Los diez símbolos: la bolsa toma los cinco primeros en orden (Pecho, Espalda, Hombros, Brazos y Antebrazos).
        // Brazos escribe dos puntos, así que la bolsa llega a seis.
        val all = MuscleSymbol.entries.toSet()
        val bag = MuscleSymbols.orderBagOf(all)
        assertEquals(6, bag.size)
        assertEquals(6, bag.values.sum())
        assertTrue(bag.values.all { it == 1 })
        assertNotNull("la bolsa cumple el contrato del motor", orderPointsFromBag(bag))
        assertTrue(MuscleSymbols.orderBagOf(emptySet()).isEmpty())
        assertEquals(setOf("Pectorales", "Dorsales"), MuscleSymbols.canonicalSet(setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK)))
        assertEquals(
            setOf("Pectorales", "Dorsales", "Bíceps", "Tríceps"),
            MuscleSymbols.canonicalSet(setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.ARMS)),
        )
    }

    @Test
    fun fiveSymbolsWithBrazosGiveAValidSixPointBag() {
        // El tope de símbolos sigue en cinco; con Brazos dentro, la bolsa tiene seis puntos y el motor la acepta.
        val five = setOf(
            MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.ARMS, MuscleSymbol.FOREARMS,
        )
        val bag = MuscleSymbols.orderBagOf(five)
        assertEquals(MuscleSymbols.MAX_SELECTION, five.size)
        assertEquals(6, bag.values.sum())
        assertEquals(bag, orderPointsFromBag(bag))
        // Un séptimo punto ya no cabe: el motor lo rechaza.
        assertNull(orderPointsFromBag(bag + ("Abdomen" to 1)))
    }

    @Test
    fun suggestionsFollowTheProfile() {
        assertEquals(
            setOf(MuscleSymbol.CHEST, MuscleSymbol.GLUTES, MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS, MuscleSymbol.ABS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.POWERLIFTING),
        )
        assertEquals(
            setOf(MuscleSymbol.FOREARMS, MuscleSymbol.ARMS),
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
        // El trapecio que sugerían Halterofilia y Strongman va en Espalda (sin duplicarla en Strongman).
        assertEquals(
            setOf(MuscleSymbol.SHOULDERS, MuscleSymbol.QUADS, MuscleSymbol.GLUTES, MuscleSymbol.BACK, MuscleSymbol.ABS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.WEIGHTLIFTING),
        )
        assertEquals(
            setOf(MuscleSymbol.BACK, MuscleSymbol.ABS, MuscleSymbol.QUADS, MuscleSymbol.FOREARMS),
            MuscleSuggestions.forProfile(TrainingGoalProfile.STRONGMAN),
        )
    }

    @Test
    fun theRetiredTrapeziusIsNoLongerASymbolButStaysAnEngineMuscleForOldDrafts() {
        assertEquals(10, MuscleSymbol.entries.size)
        assertEquals("Trapecio", MuscleSymbols.RETIRED_TRAPS_KEY)
        // El motor sigue conociendo el músculo (ORDER_MUSCLE_OPTIONS), pero ningún símbolo lo escribe ni lo lee.
        assertTrue(MuscleSymbols.RETIRED_TRAPS_KEY in ORDER_MUSCLE_OPTIONS.map { it.value })
        assertNull(MuscleSymbols.fromCanonical(MuscleSymbols.RETIRED_TRAPS_KEY))
        assertEquals(setOf(MuscleSymbol.BACK), MuscleSymbols.symbolsOf(MuscleSymbols.orderBagOf(setOf(MuscleSymbol.BACK))))
    }

    @Test
    fun theEngineTargetOfBrazosIsTheUnionOfBicepsAndTriceps() {
        val brazos = PriorityTargets.of(MuscleSymbol.ARMS)
        assertTrue(Muscles.BICEPS in brazos.muscles)
        assertTrue(Muscles.TRICEPS in brazos.muscles)
        assertTrue(RoutinePattern.BICEPS in brazos.patterns)
        assertTrue(RoutinePattern.TRICEPS in brazos.patterns)
        assertEquals(
            setOf(ItemRole.ISOLATION),
            brazos.extra.map { it.second }.toSet(),
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
