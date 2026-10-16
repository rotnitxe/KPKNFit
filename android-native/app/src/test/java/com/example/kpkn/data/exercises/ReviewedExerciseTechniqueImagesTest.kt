package com.example.kpkn.data.exercises

import com.example.kpkn.R
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewedExerciseTechniqueImagesTest {
    @Test
    fun mobility_configuration_identity_precedes_legacy_or_occurrence_ids() {
        val authoritative = "mob_neck_retraction"
        val legacy = "mob_wall_sit"
        val occurrence = "occurrence-uuid"
        val resolved = ReviewedExerciseTechniqueImages.mobilityMovementId(authoritative, legacy, occurrence)
        assertEquals(authoritative, resolved)
        assertEquals(R.drawable.exercise_mob_neck_retraction,
            ReviewedExerciseTechniqueImages.mobilityImage(resolved))
        assertEquals(authoritative,
            ReviewedExerciseTechniqueImages.mobilityMovementId(authoritative, null, occurrence))
        assertEquals(legacy,
            ReviewedExerciseTechniqueImages.mobilityMovementId(" ", legacy, occurrence))
        assertEquals(legacy,
            ReviewedExerciseTechniqueImages.mobilityMovementId(null, legacy, occurrence))
        assertEquals(occurrence,
            ReviewedExerciseTechniqueImages.mobilityMovementId(null, null, occurrence))
    }

    @Test
    fun reviewed_ids_exist_in_the_shipped_catalogs() {
        val source = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.isFile }
        val catalog = ExerciseCatalogV2Loader.decodeApproved(source.readText())
        val configurations = catalog.families.flatMap { it.definitions }
            .flatMap { it.configurations }.map { it.id }.toSet()
        assertEquals(53, ReviewedExerciseTechniqueImages.strength.size)
        assertTrue(ReviewedExerciseTechniqueImages.strength.keys.all { it in configurations })
        val movements = MobilityExerciseCatalog.getAllMobilityExercises().map { it.id }.toSet()
        assertEquals(162, ReviewedExerciseTechniqueImages.mobility.size)
        assertTrue(ReviewedExerciseTechniqueImages.mobility.keys.all { it in movements })
        assertEquals(213, (ReviewedExerciseTechniqueImages.strength.values +
            ReviewedExerciseTechniqueImages.mobility.values).toSet().size)
    }

    @Test
    fun shared_reviewed_images_have_explicit_aliases() {
        assertEquals(ReviewedExerciseTechniqueImages.strengthImage("wall_sit__default"),
            ReviewedExerciseTechniqueImages.mobilityImage("mob_wall_sit"))
        assertEquals(R.drawable.exercise_wall_sit_default,
            ReviewedExerciseTechniqueImages.mobilityImage("mob_wall_sit"))
        assertEquals(R.drawable.exercise_mob_ankle_dorsiflexion_wall,
            ReviewedExerciseTechniqueImages.mobilityImage("mob_ankle_dorsiflexion_wall"))
        assertEquals(ReviewedExerciseTechniqueImages.mobilityImage("mob_ankle_dorsiflexion_wall"),
            ReviewedExerciseTechniqueImages.mobilityImage("mob_ankle_dorsiflexion"))
    }

    @Test
    fun new_art_never_falls_back_to_definition_or_similar_configuration() {
        listOf("pendlay_row", "pendlay_row__band", "t_bar_row__t_bar__medium",
            "hip_abduction__standing__machine__unilateral", "wall_sit",
            "back_jefferson_curl__barbell").forEach {
            assertNull(it, ReviewedExerciseTechniqueImages.strengthImage(it))
        }
        assertNull(ReviewedExerciseTechniqueImages.mobilityImage("Retracción cervical"))
        assertNull(ReviewedExerciseTechniqueImages.mobilityImage("mob_neck_retraction__machine"))
        assertNull(ReviewedExerciseTechniqueImages.mobilityImage(null))
    }

    @Test
    fun exact_configurations_precede_legacy_restrictions_and_preserve_authoritative_id() {
        ReviewedExerciseTechniqueImages.strength.forEach { (id, image) ->
            assertEquals(id, image, ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = id.substringBefore("__"), exerciseDbId = null,
                exerciseId = "occurrence", catalogConfigurationId = id,
            ))
        }
        assertEquals(ReviewedExerciseTechniqueImages.strengthImage("pendlay_row__dumbbells"),
            ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "pendlay_row", exerciseDbId = "pendlay_row__machine",
            exerciseId = "occurrence", catalogConfigurationId = "pendlay_row__dumbbells",
        ))
        ReviewedExerciseTechniqueImages.mobility.forEach { (id, image) ->
            assertEquals(id, image, ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = null, exerciseDbId = id, exerciseId = "occurrence",
            ))
        }
    }
}
