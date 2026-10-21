package com.example.kpkn.data.exercises

import com.example.kpkn.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseImagesReview20261009Test {
    @Test
    fun all_twenty_six_historically_approved_images_resolve_exactly() {
        val approved = listOf(
            Triple("dead_bug", "dead_bug__default", R.drawable.exercise_dead_bug_bodyweight_batch20261008),
            Triple("close_grip_bench_press", "close_grip_bench_press__barbell", R.drawable.exercise_close_grip_bench_press_barbell_batch20261008),
            Triple("incline_biceps_curl", "incline_biceps_curl__dumbbells", R.drawable.exercise_incline_biceps_curl_dumbbells_batch20261008),
            Triple("quads_prensa_piernas", "quads_prensa_piernas__bilateral", R.drawable.exercise_quads_prensa_piernas_machine_batch20261008b),
            Triple("quads_sentadilla_sin_carga", "quads_sentadilla_sin_carga__default", R.drawable.exercise_quads_sentadilla_sin_carga_bodyweight_batch20261008b),
            Triple("paused_back_squat", "paused_back_squat__barbell", R.drawable.exercise_paused_back_squat_barbell_batch20261008b),
            Triple("overhead_squat", "overhead_squat__barbell", R.drawable.exercise_overhead_squat_barbell_batch20261008b),
            Triple("quads_sentadilla_jefferson", "quads_sentadilla_jefferson__default", R.drawable.exercise_quads_sentadilla_jefferson_barbell_batch20261008b),
            Triple("quads_extension_cuadriceps_pie_polea", "quads_extension_cuadriceps_pie_polea__bilateral", R.drawable.exercise_quads_extension_cuadriceps_pie_polea_cable_batch20261008b),
            Triple("quads_sentadilla_cajon", "quads_sentadilla_cajon__default", R.drawable.exercise_quads_sentadilla_cajon_barbell_batch20261008b),
            Triple("quads_sentadilla_sumo_frontal", "quads_sentadilla_sumo_frontal__default", R.drawable.exercise_quads_sentadilla_sumo_frontal_barbell_batch20261008b),
            Triple("glute_ham_raise", "glute_ham_raise__default", R.drawable.exercise_glute_ham_raise_ghd_batch20261008b),
            Triple("zercher_carry", "zercher_carry__barbell", R.drawable.exercise_zercher_carry_barbell_batch20261008b),
            Triple("back_jefferson_curl", "back_jefferson_curl__barbell", R.drawable.exercise_back_jefferson_curl_barbell_batch20261008b),
            Triple("back_jefferson_curl", "back_jefferson_curl__dumbbells", R.drawable.exercise_back_jefferson_curl_dumbbells_batch20261008b),
            Triple("back_jefferson_curl", "back_jefferson_curl__cable", R.drawable.exercise_back_jefferson_curl_cable_batch20261008b),
            Triple("glutes_zancada_cruzada", "glutes_zancada_cruzada__default", R.drawable.exercise_glutes_zancada_cruzada_dumbbells_batch20261008b),
            Triple("deadlift_to_knees", "deadlift_to_knees__barbell", R.drawable.exercise_deadlift_to_knees_barbell_batch20261008b),
            Triple("quads_sentadilla_pistola_asistida_trx", "quads_sentadilla_pistola_asistida_trx__default", R.drawable.exercise_quads_sentadilla_pistola_asistida_trx_trx_batch20261008b),
            Triple("quads_step_up_cajon_frontal", "quads_step_up_cajon_frontal__default", R.drawable.exercise_quads_step_up_cajon_frontal_barbell_batch20261008b),
            Triple("quads_step_up_cajon_zercher", "quads_step_up_cajon_zercher__default", R.drawable.exercise_quads_step_up_cajon_zercher_barbell_batch20261008b),
            Triple("quads_zancada_caminando_frontal_barra_recta", "quads_zancada_caminando_frontal_barra_recta__default", R.drawable.exercise_quads_zancada_caminando_frontal_barra_recta_barbell_batch20261008b),
            Triple("walking_lunge", "walking_lunge__barbell", R.drawable.exercise_walking_lunge_barbell_batch20261008b),
            Triple("walking_lunge", "walking_lunge__dumbbells", R.drawable.exercise_walking_lunge_dumbbells_batch20261008b),
            Triple("walking_lunge", "walking_lunge__kettlebell", R.drawable.exercise_walking_lunge_kettlebell_batch20261008b),
            Triple("walking_lunge", "walking_lunge__bodyweight", R.drawable.exercise_walking_lunge_bodyweight_batch20261008b),
        )
        assertEquals(26, approved.size)
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
