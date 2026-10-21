package com.example.kpkn.data.exercises

import com.example.kpkn.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseImagesReview20261010Test {
    @Test
    fun five_historical_corrections_resolve_to_the_current_selected_images() {
        // Historical B13 is now represented by the reviewed R02 replacement.
        val approved = listOf(
            Triple("forward_lunge", "forward_lunge__cable", R.drawable.exercise_forward_lunge_cable_batch20261008),
            Triple("quads_extension_cuadriceps", "quads_extension_cuadriceps__machine__bilateral", R.drawable.exercise_quads_extension_cuadriceps_machine_batch20261008b),
            Triple("back_hiperextension_45_zercher_espalda_baja", "back_hiperextension_45_zercher_espalda_baja__default", R.drawable.exercise_back_hiperextension_45_zercher_espalda_baja_default),
            Triple("quads_zancada_caminando_zercher_barra_recta", "quads_zancada_caminando_zercher_barra_recta__default", R.drawable.exercise_quads_zancada_caminando_zercher_barra_recta_barbell_batch20261008b),
            Triple("quads_zancada_frontal_zercher", "quads_zancada_frontal_zercher__default", R.drawable.exercise_quads_zancada_frontal_zercher_barbell_batch20261008b),
        )
        assertEquals(5, approved.size)
        for ((definition, configurationId, expected) in approved) {
            assertEquals("$definition / $configurationId", expected, resolve(configurationId))
        }
    }

    @Test
    fun later_reviewed_repairs_resolve_to_the_current_selection() {
        assertEquals(R.drawable.exercise_rack_chin_bodyweight, resolve("rack_chin__default"))
        assertEquals(R.drawable.exercise_quads_sentadilla_sumo_zercher_barbell, resolve("quads_sentadilla_sumo_zercher__default"))
        assertEquals(R.drawable.exercise_back_extension_lumbar_default, resolve("back_extension_lumbar__default"))
        assertEquals(R.drawable.exercise_back_jefferson_curl_smith_machine, resolve("back_jefferson_curl__smith_machine"))
    }

    private fun resolve(configurationId: String) =
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = configurationId.substringBefore("__"),
            exerciseDbId = null,
            exerciseId = configurationId,
            catalogConfigurationId = configurationId,
        )
}
