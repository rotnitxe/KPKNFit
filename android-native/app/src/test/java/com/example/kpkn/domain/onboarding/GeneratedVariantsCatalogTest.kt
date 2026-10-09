package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.domain.training.periodization.ProgramVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto 12 (D2b) · Las variantes «en bloques» y «periodizada» de los programas «a medida»: sus ids, su modo y su variante, y
 * su ficha en el catálogo (JVM puro). No se ofrecen todavía en PLAN (eso es D2c): existen, se resuelven por id y se construyen.
 */
class GeneratedVariantsCatalogTest {

    @Test
    fun the_thirty_ids_resolve_in_the_catalog_and_are_distinct() {
        val ids = PersonalizedPlanCatalog.GENERATED_IDS + PersonalizedPlanCatalog.GENERATED_VARIANT_IDS
        assertEquals(30, ids.size)
        assertEquals("ids repetidos", ids.size, ids.toSet().size)
        ids.forEach { id -> assertTrue("$id resuelve por find", PersonalizedPlanCatalog.find(id) != null) }
    }

    @Test
    fun every_profile_and_variant_builds_an_id_that_mode_and_variant_read_back() {
        TrainingGoalProfile.entries.forEach { profile ->
            assertEquals(
                "$profile: la semana simple es el id sin sufijo",
                GeneratedPlans.entryIdFor(profile),
                GeneratedPlans.entryIdFor(profile, ProgramVariant.SIMPLE),
            )
            ProgramVariant.entries.forEach { variant ->
                val id = GeneratedPlans.entryIdFor(profile, variant)
                assertEquals("$id: modo", GeneratedPlans.modeFor(profile), GeneratedPlans.modeOf(id))
                assertEquals("$id: variante", variant, GeneratedPlans.variantOf(id))
                assertTrue("$id es «a medida»", GeneratedPlans.isGenerated(id))
            }
        }
        assertEquals("generated:strength-muscle:blocks", GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_MUSCLE, ProgramVariant.BLOCKS))
        assertEquals("generated:powerlifting:periodized", GeneratedPlans.entryIdFor(TrainingGoalProfile.POWERLIFTING, ProgramVariant.PERIODIZED))
        assertEquals(ProgramVariant.SIMPLE, GeneratedPlans.variantOf("generated:strength-muscle"))
    }

    @Test
    fun malformed_or_unknown_ids_have_neither_a_mode_nor_a_variant() {
        listOf(
            "generated:",
            "generated:foo",
            "generated:strength-muscle:otra",
            "generated:strength-muscle:",
            "generated:strength-muscle:blocks:extra",
            "generated:strength-muscle:BLOCKS",
            "generated::blocks",
            "generated:strength-muscle:periodized:",
            "native:strength-muscle:blocks",
            "strength-muscle:blocks",
        ).forEach { id ->
            assertNull("«$id» sin modo", GeneratedPlans.modeOf(id))
            assertNull("«$id» sin variante", GeneratedPlans.variantOf(id))
            assertFalse("«$id» no es «a medida»", GeneratedPlans.isGenerated(id))
        }
        assertNull(GeneratedPlans.variantOf(null))
    }

    @Test
    fun no_variant_is_listed_and_each_is_an_unlisted_native_that_lasts_its_weeks() {
        val listed = PersonalizedPlanCatalog.listedEntries().map { it.id }.toSet()
        PersonalizedPlanCatalog.GENERATED_VARIANT_IDS.forEach { id ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id en el catálogo" }
            val weeks = if (id.endsWith(":blocks")) 6 else 12
            assertEquals(id, CatalogSource.NATIVE, entry.source)
            assertTrue("$id es «a medida»", entry.isGenerated)
            assertFalse("$id no se lista", entry.listed)
            assertFalse("$id no está en el planificador ni en la biblioteca", id in listed)
            assertEquals(id, PublicationState.PUBLISHED, entry.publication)
            assertEquals(id, CatalogDuration.FINITE_CYCLE, entry.duration)
            assertEquals(id, weeks, entry.durationWeeks)
            assertEquals("$id: todos los niveles", CatalogLevel.entries.toSet(), entry.levels)
            assertEquals("$id: 1 a 7 días", 1..7, entry.supportedFrequencies)
            assertTrue("$id: no exige material (el generador decide)", entry.requiredEquipment.isEmpty())
        }
    }

    @Test
    fun each_variant_keeps_the_references_capabilities_and_source_of_its_simple_week() {
        PersonalizedPlanCatalog.GENERATED_VARIANT_IDS.forEach { id ->
            val simple = requireNotNull(PersonalizedPlanCatalog.find(id.substringBeforeLast(':')))
            val variant = requireNotNull(PersonalizedPlanCatalog.find(id))
            assertEquals("$id: references", simple.references, variant.references)
            assertEquals("$id: capabilities", simple.capabilities, variant.capabilities)
            assertEquals("$id: misma semana base", simple.sourceId, variant.sourceId)
        }
    }

    @Test
    fun the_variant_ranks_are_unique_and_sit_between_710_and_729() {
        val ranks = PersonalizedPlanCatalog.GENERATED_VARIANT_IDS.map { requireNotNull(PersonalizedPlanCatalog.find(it)).rank }
        assertEquals("ranks repetidos: $ranks", ranks.size, ranks.toSet().size)
        assertTrue("ranks fuera de 710–729: $ranks", ranks.all { it in 710..729 })
    }
}
