package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AprendeOntologyTest {
    @Test
    fun every_catalog_axis_and_anatomy_id_has_an_explicit_bridge() {
        assertEquals(21, AprendeOntology.catalogMuscleToWikiLab.size)
        assertEquals(64, AprendeOntology.catalogPatternToWikiLab.size)
        assertTrue(AprendeOntology.catalogMuscleToWikiLab.values.all { it in APRENDE_MUSCLE_IDS })
        assertTrue(AprendeOntology.catalogPatternToWikiLab.values.all { it in APRENDE_PATTERN_IDS })
        assertTrue(AprendeOntology.wikiLabJointIds.isNotEmpty())
    }

    @Test
    fun legacy_ids_are_decisions_and_never_name_fallbacks() {
        assertEquals(66, AprendeOntology.legacyExerciseDecisions.size)
        assertEquals(19, AprendeOntology.legacyExerciseNameDecisions.size)
        assertEquals(85, AprendeOntology.allLegacyExerciseDecisions.size)
        assertNotNull(AprendeOntology.legacyExerciseId("db_bench_press_tng"))
        assertNull(AprendeOntology.legacyExerciseId("Press de banca libre no auditado"))
        assertNull(AprendeOntology.legacyExerciseId("tren_inferior_salto_cajon"))
        assertFalse(AprendeOntology.allLegacyExerciseDecisions.containsKey("nombre inventado"))
    }

    @Test
    fun aggregate_atlas_entities_have_explicit_reverse_muscle_membership() {
        assertEquals(
            setOf("latissimus_dorsi", "trapezius", "rhomboids", "erector_spinae"),
            AprendeOntology.catalogMuscleIdsForWikiLabEntity("espalda"),
        )
        assertTrue(
            AprendeOntology.catalogMuscleIdsForWikiLabEntity("glúteos")
                .containsAll(setOf("gluteus_maximus", "gluteus_medius")),
        )
    }

    private companion object {
        val APRENDE_PATTERN_IDS = setOf(
            "horizontal-push", "horizontal-pull", "vertical-push", "vertical-pull", "squat",
            "hinge", "lunge", "extension", "anti-extension", "anti-rotation", "rotation", "carry", "jump",
        )
        val APRENDE_MUSCLE_IDS = setOf(
            "recto-abdominal", "aductores", "bíceps", "pantorrillas", "deltoides",
            "erectores-espinales", "antebrazo", "glúteo-mayor", "glúteo-medio",
            "isquiosurales", "recto-femoral", "dorsal-ancho", "cuello", "pectoral",
            "cuádriceps", "romboides", "tibial-anterior", "trapecio", "tríceps", "core",
        )
    }
}
