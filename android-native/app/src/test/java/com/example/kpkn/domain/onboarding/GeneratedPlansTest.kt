package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.domain.training.generator.RoutineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · los programas «a medida» del generador como entradas del catálogo y de dónde salen los programas de
 * cada perfil de objetivo ([GeneratedPlans]); el material de cada lugar ([PlaceMaterial]).
 */
class GeneratedPlansTest {

    @Test
    fun every_profile_has_its_tailored_entry_and_mode_and_both_ways_agree() {
        val ids = TrainingGoalProfile.entries.map { GeneratedPlans.entryIdFor(it) }
        assertEquals("un programa «a medida» por perfil", PersonalizedPlanCatalog.GENERATED_IDS.toSet(), ids.toSet())
        assertEquals(ids.size, ids.distinct().size)
        TrainingGoalProfile.entries.forEach { profile ->
            val id = GeneratedPlans.entryIdFor(profile)
            assertEquals("$profile: el id lleva su modo", GeneratedPlans.modeFor(profile), GeneratedPlans.modeOf(id))
            assertTrue(GeneratedPlans.isGenerated(id))
        }
        assertEquals(RoutineMode.entries.toSet(), TrainingGoalProfile.entries.map { GeneratedPlans.modeFor(it) }.toSet())
        listOf(null, "", "native:muscle-foundation-v2", "generated:", "generated:no-existe").forEach { id ->
            assertNull("«$id» no es un programa a medida", GeneratedPlans.modeOf(id))
            assertFalse(GeneratedPlans.isGenerated(id))
        }
    }

    @Test
    fun the_profiles_split_into_tailored_only_tailored_plus_authors_and_initial_versions() {
        val general = TrainingGoalProfile.general.map { GeneratedPlans.sourcesFor(it) }.toSet()
        assertEquals(setOf(PlanCandidateSources.GENERATED), general)
        assertEquals(
            setOf(TrainingGoalProfile.POWERLIFTING, TrainingGoalProfile.POWERBUILDING, TrainingGoalProfile.BODYBUILDING),
            TrainingGoalProfile.entries.filter { GeneratedPlans.sourcesFor(it) == PlanCandidateSources.GENERATED_AND_CATALOG }.toSet(),
        )
        assertEquals(
            setOf(
                TrainingGoalProfile.CALISTHENICS, TrainingGoalProfile.ARMWRESTLING,
                TrainingGoalProfile.STRONGMAN, TrainingGoalProfile.WEIGHTLIFTING,
            ),
            TrainingGoalProfile.entries.filter { GeneratedPlans.sourcesFor(it) == PlanCandidateSources.GENERATED_DISCIPLINE }.toSet(),
        )
        assertEquals("sin perfil: el planificador de siempre", PlanCandidateSources.CATALOG, GeneratedPlans.sourcesFor(null))
    }

    @Test
    fun the_tailored_entries_are_unlisted_natives_for_any_week_and_resolve_by_id() {
        val listed = PersonalizedPlanCatalog.listedEntries().map { it.id }.toSet()
        PersonalizedPlanCatalog.GENERATED_IDS.forEach { id ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id en el catálogo" }
            assertEquals(id, CatalogSource.NATIVE, entry.source)
            assertTrue("$id es «a medida»", entry.isGenerated)
            assertFalse("$id no se lista", entry.listed)
            assertFalse("$id no está en la biblioteca ni en el planificador", id in listed)
            assertEquals(id, PublicationState.PUBLISHED, entry.publication)
            assertEquals("$id: 1 a 7 días", 1..7, entry.supportedFrequencies)
            assertEquals(id, CatalogDuration.REPEATING_WEEK, entry.duration)
            assertEquals(id, 1, entry.durationWeeks)
            assertEquals("$id: todos los niveles", CatalogLevel.entries.toSet(), entry.levels)
            assertTrue("$id: no exige material (el generador decide)", entry.requiredEquipment.isEmpty())
            assertEquals(id, PersonalizedPlanCatalog.lookup(id)?.entry?.id)
        }
        // Nada que no sea «a medida» se marca como tal.
        PersonalizedPlanCatalog.entries().filterNot { it.id in PersonalizedPlanCatalog.GENERATED_IDS }.forEach { entry ->
            assertFalse("${entry.id} no es «a medida»", entry.isGenerated)
        }
    }

    // ─── Material de cada lugar ──────────────────────────────────────────────────────────────────

    @Test
    fun with_a_single_place_there_is_no_per_place_material() {
        val gym = setOf(TrainingPlace.GYM)
        assertTrue(PlaceMaterial.byPlace(EquipmentSymbols.seedFor(gym), gym).isEmpty())
        assertTrue(PlaceMaterial.byPlace(emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun gym_and_home_keep_the_gym_seed_at_the_gym_and_only_the_extras_at_home() {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)
        val selected = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) + EquipmentSymbolId.RINGS
        val byPlace = PlaceMaterial.byPlace(selected, places)
        assertEquals(places, byPlace.keys)
        val gym = EquipmentSymbols.selectedFrom(byPlace.getValue(TrainingPlace.GYM))
        val home = EquipmentSymbols.selectedFrom(byPlace.getValue(TrainingPlace.HOME))
        assertTrue("el gimnasio tiene barra, rack y banco", gym.containsAll(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH)))
        assertTrue("las anillas se llevan encima", EquipmentSymbolId.RINGS in gym)
        assertEquals("en casa solo lo que la persona añadió", setOf(EquipmentSymbolId.RINGS), home)
        assertFalse("en casa nunca se supone la barra del gimnasio", EquipmentCategory.BARBELL in byPlace.getValue(TrainingPlace.HOME).categories)
    }

    @Test
    fun home_and_park_send_the_park_structure_to_the_park_and_the_rest_home() {
        val places = setOf(TrainingPlace.HOME, TrainingPlace.PUBLIC)
        val selected = setOf(
            EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS,
            EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS,
        )
        val byPlace = PlaceMaterial.byPlace(selected, places)
        val home = EquipmentSymbols.selectedFrom(byPlace.getValue(TrainingPlace.HOME))
        val park = EquipmentSymbols.selectedFrom(byPlace.getValue(TrainingPlace.PUBLIC))
        assertEquals(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS), home)
        assertEquals(
            "al parque van su estructura y las bandas (las mancuernas se quedan en casa)",
            setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BANDS),
            park,
        )
    }

    @Test
    fun only_bodyweight_gives_bodyweight_everywhere() {
        val places = setOf(TrainingPlace.HOME, TrainingPlace.PUBLIC)
        val byPlace = PlaceMaterial.byPlace(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), places)
        byPlace.values.forEach { availability ->
            assertTrue(availability.categories.isEmpty())
            assertTrue(availability.apparatus.values.none { it == ApparatusPresence.PRESENT })
        }
    }
}
