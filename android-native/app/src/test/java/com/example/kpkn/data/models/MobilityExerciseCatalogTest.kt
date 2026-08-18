package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityExerciseCatalogTest {
    @Test
    fun catalog_has_broad_anatomical_coverage_and_unique_ids() {
        val exercises = MobilityExerciseCatalog.getAllMobilityExercises()

        assertTrue("La suite debe superar el catálogo mínimo", exercises.size >= 150)
        assertEquals(exercises.size, exercises.map { it.id }.toSet().size)
        assertTrue(exercises.any { it.bodyRegion == "neck" })
        assertTrue(exercises.any { it.bodyRegion == "shoulder" })
        assertTrue(exercises.any { it.bodyRegion == "wrist" })
        assertTrue(exercises.any { it.bodyRegion == "hip" })
        assertTrue(exercises.any { it.bodyRegion == "knee" })
        assertTrue(exercises.any { it.bodyRegion == "ankle" })
        assertTrue(exercises.any { it.bodyRegion == "foot" })
    }

    @Test
    fun every_entry_has_editorial_fields_and_known_discomfort_links() {
        MobilityExerciseCatalog.getAllMobilityExercises().forEach { exercise ->
            assertTrue(exercise.id.startsWith("mob_"))
            assertTrue(exercise.name.isNotBlank())
            assertTrue(exercise.description.length >= 40)
            assertTrue(exercise.instructions.orEmpty().length >= 30)
            assertTrue(exercise.objective.isNotBlank())
            assertTrue(exercise.joints.isNotEmpty())
            assertTrue(exercise.targetMuscles.isNotEmpty())
            assertTrue(exercise.precautions.isNotBlank())
            assertTrue(exercise.discomfortIds.all { it in DISCOMFORT_CATALOG_BY_ID })
        }
    }

    @Test
    fun search_normalizes_accents_and_keeps_international_aliases_searchable() {
        val withoutAccent = MobilityExerciseCatalog.searchMobility("dorsiflexion")
        val withAccent = MobilityExerciseCatalog.searchMobility("dorsiflexión")
        val alias = MobilityExerciseCatalog.searchMobility("wall slides")

        assertFalse(withoutAccent.isEmpty())
        assertEquals(withoutAccent.map { it.id }.toSet(), withAccent.map { it.id }.toSet())
        assertTrue(alias.any { it.id == "mob_wall_slides" })
        assertTrue(alias.first { it.id == "mob_wall_slides" }.name == "Deslizamientos de brazos en pared")
    }

    @Test
    fun discomfort_recommendations_are_deduplicated_and_span_multiple_regions() {
        val recommendations = MobilityExerciseCatalog.getMobilityForDiscomforts(
            listOf("shoulder_anterior", "ankle", "shoulder_anterior"),
        )

        assertEquals(recommendations.size, recommendations.map { it.id }.toSet().size)
        assertTrue(recommendations.any { it.bodyRegion == "shoulder" })
        assertTrue(recommendations.any { it.bodyRegion == "ankle" })
    }
}
