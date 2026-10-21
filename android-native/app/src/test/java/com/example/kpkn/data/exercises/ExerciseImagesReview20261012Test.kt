package com.example.kpkn.data.exercises

import com.example.kpkn.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseImagesReview20261012Test {
    @Test
    fun twenty_six_explicitly_approved_pairs_resolve_to_their_exact_assets() {
        val approved = listOf(
            Triple("conventional_row", "conventional_row__smith_machine", R.drawable.exercise_conventional_row_smith_machine),
            Triple("conventional_row", "conventional_row__kettlebell", R.drawable.exercise_conventional_row_kettlebell),
            Triple("good_morning", "good_morning__bilateral__bodyweight", R.drawable.exercise_good_morning_bodyweight),
            Triple("romanian_deadlift", "romanian_deadlift__unilateral__bodyweight", R.drawable.exercise_romanian_deadlift_bodyweight),
            Triple("neck_flexion_lateral_cuello", "neck_flexion_lateral_cuello__default", R.drawable.exercise_neck_flexion_lateral_cuello_plate),
            Triple("glutes_patada_gluteo_lateral", "glutes_patada_gluteo_lateral__cable", R.drawable.exercise_glutes_patada_gluteo_lateral_cable),
            Triple("glutes_patada_gluteo_lateral", "glutes_patada_gluteo_lateral__band", R.drawable.exercise_glutes_patada_gluteo_lateral_band),
            Triple("glutes_puente_gluteos", "glutes_puente_gluteos__bilateral__bodyweight", R.drawable.exercise_glutes_puente_gluteos_bodyweight),
            Triple("sumo_squat", "sumo_squat__bodyweight", R.drawable.exercise_sumo_squat_bodyweight),
            Triple("calf_raise", "calf_raise__bilateral__bodyweight", R.drawable.exercise_calf_raise_bodyweight),
            Triple("calf_raise", "calf_raise__bilateral__leg_press_machine", R.drawable.exercise_calf_raise_leg_press_machine),
            Triple("reverse_lunge", "reverse_lunge__bodyweight", R.drawable.exercise_reverse_lunge_bodyweight),
            Triple("sissy_squat", "sissy_squat__bodyweight", R.drawable.exercise_sissy_squat_bodyweight),
            Triple("step_up", "step_up__bodyweight", R.drawable.exercise_step_up_bodyweight),
            Triple("quads_zancada_inversa_frontal", "quads_zancada_inversa_frontal__default", R.drawable.exercise_quads_zancada_inversa_frontal_barbell),
            Triple("neck_extension_cuello", "neck_extension_cuello__plate", R.drawable.exercise_neck_extension_cuello_plate),
            Triple("neck_flexion_cuello", "neck_flexion_cuello__plate", R.drawable.exercise_neck_flexion_cuello_plate),
            Triple("quads_zancada_inversa_zercher", "quads_zancada_inversa_zercher__default", R.drawable.exercise_quads_zancada_inversa_zercher_barbell),
            Triple("lateral_raise_super_rom", "lateral_raise_super_rom__dumbbells", R.drawable.exercise_lateral_raise_super_rom_dumbbells),
            Triple("bulgarian_split_squat", "bulgarian_split_squat__bodyweight", R.drawable.exercise_bulgarian_split_squat_bodyweight),
            Triple("overhead_triceps_extension", "overhead_triceps__ez_bar", R.drawable.exercise_overhead_triceps_extension_ez_bar),
            Triple("back_remo_gorilla_mancuernas", "back_remo_gorilla_mancuernas__cable", R.drawable.exercise_back_remo_gorilla_mancuernas_cable),
            Triple("back_encogimientos_kelso", "back_encogimientos_kelso__barbell", R.drawable.exercise_back_encogimientos_kelso_barbell),
            Triple("deltoides_lu_raises", "deltoides_lu_raises__default", R.drawable.exercise_deltoides_lu_raises_dumbbells),
            Triple("rack_chin", "rack_chin__default", R.drawable.exercise_rack_chin_bodyweight),
            Triple("quads_sentadilla_sumo_zercher", "quads_sentadilla_sumo_zercher__default", R.drawable.exercise_quads_sentadilla_sumo_zercher_barbell),
        )
        assertEquals(26, approved.size)
        for ((definition, configurationId, expected) in approved) {
            assertEquals("$definition / $configurationId", expected, resolve(configurationId, definition))
        }
    }

    @Test
    fun later_reviewed_lumbar_extension_and_smith_jefferson_resolve() {
        assertEquals(R.drawable.exercise_back_extension_lumbar_default, resolve("back_extension_lumbar__default"))
        assertEquals(R.drawable.exercise_back_jefferson_curl_smith_machine, resolve("back_jefferson_curl__smith_machine"))
    }

    @Test
    fun later_reviewed_corrections_resolve_for_their_exact_published_configurations() {
        // These were pending on October 12; the later review selected these exact configurations.
        val reviewed = listOf(
            "chest_supported_row__kettlebell__close" to R.drawable.exercise_chest_supported_row_kettlebell_close,
            "conventional_row__machine" to R.drawable.exercise_conventional_row_machine,
            "pendlay_row__dumbbells" to R.drawable.exercise_pendlay_row_dumbbells,
            "pendlay_row__kettlebell" to R.drawable.exercise_pendlay_row_kettlebell,
            "seal_row__dumbbells" to R.drawable.exercise_seal_row_dumbbells,
            "glutes_hiperextension_45__dumbbells" to R.drawable.exercise_glutes_hiperextension_45_dumbbells,
            "glutes_hiperextension_45__barbell" to R.drawable.exercise_glutes_hiperextension_45_barbell,
            "glutes_hiperextension_45__plate" to R.drawable.exercise_glutes_hiperextension_45_plate,
            "glutes_hiperextension_45__smith_machine" to R.drawable.exercise_glutes_hiperextension_45_smith_machine,
            "glutes_patada_gluteo__cable" to R.drawable.exercise_glutes_patada_gluteo_cable,
            "glutes_patada_gluteo__dumbbells" to R.drawable.exercise_glutes_patada_gluteo_dumbbells,
            "glutes_patada_gluteo__band" to R.drawable.exercise_glutes_patada_gluteo_band,
            "reverse_hyper__machine" to R.drawable.exercise_reverse_hyper_machine,
            "glutes_hiperextension_45_zercher__default" to R.drawable.exercise_glutes_hiperextension_45_zercher_default,
            "glutes_patada_gluteo_polea_diagonal__default" to R.drawable.exercise_glutes_patada_gluteo_polea_diagonal_default,
            "calf_raise__bilateral__seated_machine" to R.drawable.exercise_calf_raise_bilateral_seated_machine,
            "calf_raise__bilateral__donkey_machine" to R.drawable.exercise_calf_raise_bilateral_donkey_machine,
            "lateral_raise_super_rom__cable" to R.drawable.exercise_lateral_raise_super_rom_cable,
            "skullcrusher__default" to R.drawable.exercise_skullcrusher_default,
            "back_encogimientos_kelso__machine" to R.drawable.exercise_back_encogimientos_kelso_machine,
            "back_encogimientos_kelso__cable" to R.drawable.exercise_back_encogimientos_kelso_cable,
            "back_encogimientos_kelso__kettlebell" to R.drawable.exercise_back_encogimientos_kelso_kettlebell,
            "back_encogimientos_kelso__smith_machine" to R.drawable.exercise_back_encogimientos_kelso_smith_machine,
            "lat_pulldown__bilateral__band" to R.drawable.exercise_lat_pulldown_bilateral_band,
            "close_grip_lat_pulldown__cable" to R.drawable.exercise_close_grip_lat_pulldown_cable,
        )
        assertEquals(25, reviewed.size)
        for ((configurationId, expected) in reviewed) {
            assertEquals(configurationId, expected, resolve(configurationId))
        }
    }

    @Test
    fun configurations_without_reviewed_images_do_not_inherit_a_different_setup() {
        assertNull(resolve("chest_supported_row__kettlebell__wide"))
        assertNull(resolve("t_bar_row__t_bar__wide"))
        assertEquals(
            R.drawable.exercise_calf_raise_machine_batch9,
            resolve("calf_raise__bilateral__machine"),
        )
    }

    private fun resolve(configurationId: String, definitionId: String = configurationId.substringBefore("__")) =
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = null,
            exerciseId = configurationId,
            catalogConfigurationId = configurationId,
        )
}
