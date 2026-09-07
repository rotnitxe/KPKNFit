package com.example.kpkn.data.exercises

import com.example.kpkn.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseTechniqueImageLookupTest {

    @Test
    fun bench_press_resolves_barbell_variant() {
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bench_press",
            exerciseDbId = "bench_press__barbell",
            exerciseId = "ex-1",
            catalogConfigurationId = "bench_press__barbell",
            selectedImplementation = "barbell",
        )
        assertEquals(R.drawable.exercise_press_de_banca_plano, res)
    }

    @Test
    fun squat_resolves_from_configuration_prefix() {
        val definition = ExerciseTechniqueImageLookup.resolveDefinitionId(
            catalogDefinitionId = null,
            exerciseDbId = null,
            exerciseId = "x",
            catalogConfigurationId = "high_bar_back_squat__smith_machine",
        )
        assertEquals("high_bar_back_squat", definition)
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = null,
            exerciseDbId = null,
            exerciseId = "x",
            catalogConfigurationId = "high_bar_back_squat__smith_machine",
        )
        assertEquals(R.drawable.exercise_sentadilla_trasera_barra_alta_smith, res)
    }

    @Test
    fun unknown_exercise_has_no_technique_photo() {
        assertTrue(ExerciseTechniqueImageLookup.variants("unknown_move").isEmpty())
        assertNull(
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "unknown_move",
                exerciseDbId = "unknown_move",
                exerciseId = "unknown_move",
            ),
        )
    }

    @Test
    fun front_squat_resolves_all_implement_variants() {
        val barbellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "front_squat",
            exerciseDbId = "front_squat__barbell",
            exerciseId = "ex-fs-1",
            selectedImplementation = "barbell",
        )
        assertEquals(R.drawable.exercise_sentadilla_frontal, barbellRes)

        val smithRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "front_squat",
            exerciseDbId = "front_squat__smith_machine",
            exerciseId = "ex-fs-2",
            selectedImplementation = "smith_machine",
        )
        assertEquals(R.drawable.exercise_sentadilla_frontal_smith, smithRes)

        val dumbbellsRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "front_squat",
            exerciseDbId = "front_squat__dumbbells",
            exerciseId = "ex-fs-3",
            selectedImplementation = "dumbbells",
        )
        assertEquals(R.drawable.exercise_sentadilla_frontal_mancuernas, dumbbellsRes)

        val kettlebellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "front_squat",
            exerciseDbId = "front_squat__kettlebell",
            exerciseId = "ex-fs-4",
            selectedImplementation = "kettlebell",
        )
        assertEquals(R.drawable.exercise_sentadilla_frontal_kettlebell, kettlebellRes)

        val cableRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "front_squat",
            exerciseDbId = "front_squat__cable",
            exerciseId = "ex-fs-5",
            selectedImplementation = "cable",
        )
        assertEquals(R.drawable.exercise_sentadilla_frontal_polea, cableRes)
    }

    @Test
    fun goblet_squat_has_exactly_one_variant_and_resolves_image() {
        val variants = ExerciseTechniqueImageLookup.variants("quads_sentadilla_copa")
        assertEquals(1, variants.size)
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "quads_sentadilla_copa",
            exerciseDbId = "quads_sentadilla_copa__default",
            exerciseId = "ex-gs-1",
        )
        assertEquals(R.drawable.exercise_sentadilla_copa, res)
    }

    @Test
    fun bulgarian_split_squat_resolves_image() {
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__dumbbells",
            exerciseId = "ex-bss-1",
            selectedImplementation = "dumbbells",
        )
        assertEquals(R.drawable.exercise_sentadilla_bulgara_mancuernas, res)
    }

    @Test
    fun sumo_squat_resolves_image() {
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "sumo_squat",
            exerciseDbId = "sumo_squat__barbell",
            exerciseId = "ex-ss-1",
            selectedImplementation = "barbell",
        )
        assertEquals(R.drawable.exercise_sentadilla_sumo, res)
    }

    @Test
    fun hack_squat_resolves_machine_and_barbell_images() {
        val machineRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "quads_sentadilla_hack",
            exerciseDbId = "quads_sentadilla_hack__machine",
            exerciseId = "ex-hs-1",
            selectedImplementation = "machine",
        )
        assertEquals(R.drawable.exercise_sentadilla_hack_maquina, machineRes)

        val barbellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "quads_sentadilla_hack",
            exerciseDbId = "quads_sentadilla_hack__barbell",
            exerciseId = "ex-hs-2",
            selectedImplementation = "barbell",
        )
        assertEquals(R.drawable.exercise_sentadilla_hack_barra, barbellRes)
    }

    @Test
    fun pendulum_squat_has_exactly_one_variant_and_resolves_image() {
        val variants = ExerciseTechniqueImageLookup.variants("pendulum_squat")
        assertEquals(1, variants.size)
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "pendulum_squat",
            exerciseDbId = "pendulum_squat__bilateral",
            exerciseId = "ex-ps-1",
        )
        assertEquals(R.drawable.exercise_sentadilla_pendulo, res)
    }

    @Test
    fun belt_squat_has_exactly_one_variant_and_resolves_image() {
        val variants = ExerciseTechniqueImageLookup.variants("belt_squat")
        assertEquals(1, variants.size)
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "belt_squat",
            exerciseDbId = "belt_squat__bilateral",
            exerciseId = "ex-bs-1",
        )
        assertEquals(R.drawable.exercise_sentadilla_belt_squat, res)
    }

    @Test
    fun sumo_squat_resolves_distinct_implement_images() {
        val barbellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "sumo_squat",
            exerciseDbId = "sumo_squat__barbell",
            exerciseId = "ex-ss-1",
            selectedImplementation = "barbell",
        )
        val dumbbellsRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "sumo_squat",
            exerciseDbId = "sumo_squat__dumbbells",
            exerciseId = "ex-ss-2",
            selectedImplementation = "dumbbells",
        )
        val kettlebellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "sumo_squat",
            exerciseDbId = "sumo_squat__kettlebell",
            exerciseId = "ex-ss-3",
            selectedImplementation = "kettlebell",
        )
        assertEquals(R.drawable.exercise_sentadilla_sumo, barbellRes)
        assertEquals(R.drawable.exercise_sentadilla_sumo_mancuernas, dumbbellsRes)
        assertEquals(R.drawable.exercise_sentadilla_sumo_kettlebell, kettlebellRes)
    }

    @Test
    fun bulgarian_split_squat_resolves_distinct_implement_images() {
        val dumbbellsRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__dumbbells",
            exerciseId = "ex-bss-1",
            selectedImplementation = "dumbbells",
        )
        val barbellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__barbell",
            exerciseId = "ex-bss-2",
            selectedImplementation = "barbell",
        )
        val smithRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__smith_machine",
            exerciseId = "ex-bss-3",
            selectedImplementation = "smith_machine",
        )
        val machineRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__machine",
            exerciseId = "ex-bss-4",
            selectedImplementation = "machine",
        )
        val cableRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__cable",
            exerciseId = "ex-bss-5",
            selectedImplementation = "cable",
        )
        val kettlebellRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "bulgarian_split_squat",
            exerciseDbId = "bulgarian_split_squat__kettlebell",
            exerciseId = "ex-bss-6",
            selectedImplementation = "kettlebell",
        )
        assertEquals(R.drawable.exercise_sentadilla_bulgara_mancuernas, dumbbellsRes)
        assertEquals(R.drawable.exercise_sentadilla_bulgara_barra, barbellRes)
        assertEquals(R.drawable.exercise_sentadilla_bulgara_smith, smithRes)
        assertEquals(R.drawable.exercise_sentadilla_bulgara_maquina, machineRes)
        assertEquals(R.drawable.exercise_sentadilla_bulgara_polea, cableRes)
        assertEquals(R.drawable.exercise_sentadilla_bulgara_kettlebell, kettlebellRes)
    }

    @Test
    fun hack_squat_resolves_distinct_smith_image() {
        val smithRes = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "quads_sentadilla_hack",
            exerciseDbId = "quads_sentadilla_hack__smith_machine",
            exerciseId = "ex-hs-3",
            selectedImplementation = "smith_machine",
        )
        assertEquals(R.drawable.exercise_sentadilla_hack_smith, smithRes)
    }

    @Test
    fun new_squat_definitions_resolve_their_drawables() {
        assertEquals(
            R.drawable.exercise_sentadilla_hack_invertida,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_hack_invertida_maquina",
                exerciseDbId = "quads_sentadilla_hack_invertida_maquina__default",
                exerciseId = "ex-hi-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_anderson,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_anderson",
                exerciseDbId = "quads_sentadilla_anderson__default",
                exerciseId = "ex-an-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_anderson_frontal,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_anderson_frontal_barra_recta",
                exerciseDbId = "quads_sentadilla_anderson_frontal_barra_recta__default",
                exerciseId = "ex-anf-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_bazuca,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_bazuca",
                exerciseDbId = "quads_sentadilla_bazuca__default",
                exerciseId = "ex-bz-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_somersault,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_somersault",
                exerciseDbId = "quads_sentadilla_somersault__default",
                exerciseId = "ex-sm-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_v_squat,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_v_squat",
                exerciseDbId = "quads_sentadilla_v_squat__default",
                exerciseId = "ex-vs-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_v_squat_invertida,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "quads_sentadilla_v_squat_invertida_maquina",
                exerciseDbId = "quads_sentadilla_v_squat_invertida_maquina__default",
                exerciseId = "ex-vsi-1",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_bulgara_zercher,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "bulgarian_zercher",
                exerciseDbId = "bulgarian_zercher__barbell__zercher",
                exerciseId = "ex-bzr-1",
                catalogConfigurationId = "bulgarian_zercher__barbell__zercher",
            ),
        )
    }

    @Test
    fun sissy_squat_resolves_all_implement_variants() {
        assertEquals(
            R.drawable.exercise_sentadilla_sissy_maquina,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "sissy_squat",
                exerciseDbId = "sissy_squat__machine",
                exerciseId = "ex-sy-2",
                selectedImplementation = "machine",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_sissy_smith,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "sissy_squat",
                exerciseDbId = "sissy_squat__smith_machine",
                exerciseId = "ex-sy-3",
                selectedImplementation = "smith_machine",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_sissy_mancuernas,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "sissy_squat",
                exerciseDbId = "sissy_squat__dumbbells",
                exerciseId = "ex-sy-4",
                selectedImplementation = "dumbbells",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_sissy_disco,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "sissy_squat",
                exerciseDbId = "sissy_squat__plate",
                exerciseId = "ex-sy-5",
                selectedImplementation = "plate",
            ),
        )
    }

    @Test
    fun belt_and_pendulum_unilateral_reuse_same_drawable() {
        assertEquals(
            R.drawable.exercise_sentadilla_belt_squat,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "belt_squat",
                exerciseDbId = "belt_squat__unilateral",
                exerciseId = "ex-bs-u",
            ),
        )
        assertEquals(
            R.drawable.exercise_sentadilla_pendulo,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "pendulum_squat",
                exerciseDbId = "pendulum_squat__unilateral",
                exerciseId = "ex-ps-u",
            ),
        )
    }

    @Test
    fun conventional_deadlift_catalog_implements_exclude_kettlebell() {
        val keys = ExerciseTechniqueImageLookup.variants("conventional_deadlift").map { it.implementation }
        assertEquals(listOf("barbell", "smith_machine", "hex_bar", "dumbbells"), keys)
        assertEquals(
            R.drawable.exercise_peso_muerto_convencional_mancuernas,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "conventional_deadlift",
                exerciseDbId = "conventional_deadlift__bilateral__dumbbells",
                exerciseId = "ex-cdl-db",
                catalogConfigurationId = "conventional_deadlift__bilateral__dumbbells",
            ),
        )
        assertEquals(
            R.drawable.exercise_peso_muerto_convencional,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "conventional_deadlift",
                exerciseDbId = "conventional_deadlift__unilateral__barbell",
                exerciseId = "ex-cdl-u",
                catalogConfigurationId = "conventional_deadlift__unilateral__barbell",
            ),
        )
    }

    @Test
    fun hinge_lote_maps_each_catalog_implement_to_one_drawable() {
        fun res(
            definitionId: String,
            configurationId: String,
            selected: String? = null,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
            selectedImplementation = selected,
        )

        assertEquals(R.drawable.exercise_peso_muerto_sumo, res("sumo_deadlift", "sumo_deadlift__barbell", "barbell"))
        assertEquals(R.drawable.exercise_peso_muerto_sumo_mancuernas, res("sumo_deadlift", "sumo_deadlift__dumbbells", "dumbbells"))

        assertEquals(R.drawable.exercise_peso_muerto_rumano, res("romanian_deadlift", "romanian_deadlift__bilateral__barbell"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano, res("romanian_deadlift", "romanian_deadlift__unilateral__barbell"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_mancuernas, res("romanian_deadlift", "romanian_deadlift__bilateral__dumbbells"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_smith, res("romanian_deadlift", "romanian_deadlift__smith_machine", "smith_machine"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_hex_bar, res("romanian_deadlift", "romanian_deadlift__hex_bar", "hex_bar"))

        assertEquals(R.drawable.exercise_peso_muerto_rumano_sumo, res("romanian_sumo_deadlift", "romanian_sumo_deadlift__bilateral__barbell"))
        assertEquals(R.drawable.exercise_peso_muerto_piernas_rigidas, res("stiff_leg_deadlift", "stiff_leg_deadlift__bilateral__barbell"))

        assertEquals(R.drawable.exercise_buenos_dias, res("good_morning", "good_morning__bilateral__barbell"))
        assertEquals(R.drawable.exercise_buenos_dias_safety_bar, res("good_morning", "good_morning__unilateral__safety_bar"))
        assertEquals(R.drawable.exercise_buenos_dias_sentado, res("good_morning_seated", "good_morning_seated__barbell", "barbell"))
        assertEquals(R.drawable.exercise_buenos_dias_zercher, res("good_morning_zercher", "good_morning_zercher__default"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_zercher, res("hams_peso_muerto_rumano_zercher", "hams_peso_muerto_rumano_zercher__default"))
        assertEquals(R.drawable.exercise_pull_through, res("hams_pull_through", "hams_pull_through__default"))

        assertEquals(R.drawable.exercise_hip_thrust, res("hip_thrust", "hip_thrust__bilateral__barbell"))
        assertEquals(R.drawable.exercise_hip_thrust_banda, res("hip_thrust", "hip_thrust__unilateral__band"))

        assertEquals(R.drawable.exercise_peso_muerto_convencional_deficit, res("hams_peso_muerto_convencional_deficit", "hams_peso_muerto_convencional_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_sumo_deficit, res("hams_peso_muerto_sumo_deficit", "hams_peso_muerto_sumo_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_piernas_rigidas_deficit, res("hams_peso_muerto_piernas_rigidas_deficit", "hams_peso_muerto_piernas_rigidas_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_deficit, res("hams_peso_muerto_rumano_deficit", "hams_peso_muerto_rumano_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_sumo_deficit, res("hams_peso_muerto_rumano_sumo_deficit", "hams_peso_muerto_rumano_sumo_deficit__default"))

        assertEquals(R.drawable.exercise_swing_kettlebell_dos_manos, res("hams_swing_kettlebell_dos_manos", "hams_swing_kettlebell_dos_manos__default"))
        assertEquals(R.drawable.exercise_swing_kettlebell_unilateral, res("hams_swing_kettlebell_unilateral", "hams_swing_kettlebell_unilateral__default"))
    }

    @Test
    fun arm_lote_a_biceps_maps_catalog_implement_and_ignores_grip() {
        fun res(
            definitionId: String,
            configurationId: String,
            selected: String? = null,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
            selectedImplementation = selected,
        )

        assertEquals(R.drawable.exercise_curl_biceps_pie_barra, res("standing_biceps_curl", "standing_biceps_curl__barbell"))
        assertEquals(R.drawable.exercise_curl_biceps_pie_ez, res("standing_biceps_curl", "standing_biceps_curl__ez_bar"))
        assertEquals(R.drawable.exercise_curl_biceps_pie_mancuernas, res("standing_biceps_curl", "standing_biceps_curl__dumbbells"))
        assertEquals(R.drawable.exercise_curl_biceps_pie_polea, res("standing_biceps_curl", "standing_biceps_curl__cable"))

        assertEquals(R.drawable.exercise_curl_biceps_sentado_mancuernas, res("biceps_curl_sentado_banco_plano", "biceps_curl_sentado_banco_plano__dumbbells"))
        assertEquals(R.drawable.exercise_curl_biceps_sentado_polea, res("biceps_curl_sentado_banco_plano", "biceps_curl_sentado_banco_plano__cable"))

        assertEquals(R.drawable.exercise_curl_predicador_barra, res("preacher_curl", "preacher_curl__barbell"))
        assertEquals(R.drawable.exercise_curl_predicador_maquina, res("preacher_curl", "preacher_curl__machine"))

        assertEquals(R.drawable.exercise_curl_arana_mancuernas, res("spider_curl", "spider_curl__dumbbells__supinated"))
        assertEquals(R.drawable.exercise_curl_arana_mancuernas, res("spider_curl", "spider_curl__dumbbells__pronated"))
        assertEquals(R.drawable.exercise_curl_arana_polea, res("spider_curl", "spider_curl__cable__neutral"))
        assertEquals(R.drawable.exercise_curl_arana_barra, res("spider_curl", "spider_curl__barbell__supinated"))

        assertEquals(R.drawable.exercise_curl_concentrado_mancuernas, res("concentration_curl", "concentration_curl__dumbbells"))
        assertEquals(R.drawable.exercise_curl_bayesian_mancuernas, res("biceps_curl_bayesian", "biceps_curl_bayesian__dumbbells__supinated"))
        assertEquals(R.drawable.exercise_curl_bayesian_polea, res("biceps_curl_bayesian", "biceps_curl_bayesian__cable__neutral"))

        assertEquals(R.drawable.exercise_curl_martillo_h_bar, res("hammer_curl", "hammer_curl__h_bar"))
        assertEquals(R.drawable.exercise_curl_martillo_kettlebell, res("hammer_curl", "hammer_curl__kettlebell"))
        assertEquals(R.drawable.exercise_curl_invertido_h_bar, res("reverse_curl", "reverse_curl__h_bar"))
        assertEquals(R.drawable.exercise_curl_invertido_mancuernas, res("reverse_curl", "reverse_curl__dumbbells"))

        assertEquals(R.drawable.exercise_curl_drag_barra, res("biceps_curl_drag", "biceps_curl_drag__barbell__supinated"))
        assertEquals(R.drawable.exercise_curl_zottman_mancuernas, res("biceps_curl_zottman", "biceps_curl_zottman__dumbbells"))
        assertEquals(R.drawable.exercise_curl_waiter_disco, res("biceps_curl_waiter", "biceps_curl_waiter__plate"))
        assertEquals(R.drawable.exercise_curl_crucifijo, res("biceps_curl_crucifijo", "biceps_curl_crucifijo__default"))
        assertEquals(R.drawable.exercise_curl_superman, res("biceps_curl_superman", "biceps_curl_superman__default"))
        assertEquals(R.drawable.exercise_curl_biceps_trx, res("biceps_curl_trx", "biceps_curl_trx__supinated"))
    }

    @Test
    fun arm_lote_b_triceps_maps_catalog_implement_and_ignores_laterality() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(R.drawable.exercise_triceps_pushdown_polea, res("triceps_pushdown", "triceps_pushdown__bilateral__cable"))
        assertEquals(R.drawable.exercise_triceps_pushdown_polea, res("triceps_pushdown", "triceps_pushdown__unilateral__cable"))
        assertEquals(R.drawable.exercise_triceps_pushdown_maquina, res("triceps_pushdown", "triceps_pushdown__bilateral__machine"))
        assertEquals(R.drawable.exercise_triceps_pushdown_banda, res("triceps_pushdown", "triceps_pushdown__unilateral__band"))

        assertEquals(R.drawable.exercise_triceps_overhead_barra, res("overhead_triceps_extension", "overhead_triceps__barbell"))
        assertEquals(R.drawable.exercise_triceps_overhead_maquina, res("overhead_triceps_extension", "overhead_triceps__machine"))
        assertEquals(R.drawable.exercise_triceps_overhead_mancuernas, res("overhead_triceps_extension", "overhead_triceps__dumbbells"))
        assertEquals(R.drawable.exercise_triceps_overhead_polea, res("overhead_triceps_extension", "overhead_triceps__cable"))

        assertEquals(R.drawable.exercise_press_frances_mancuernas, res("triceps_press_frances", "triceps_press_frances__dumbbells"))
        assertEquals(R.drawable.exercise_press_frances_barra, res("triceps_press_frances", "triceps_press_frances__barbell"))
        assertEquals(R.drawable.exercise_press_frances_ez, res("triceps_press_frances", "triceps_press_frances__ez_bar"))
        assertEquals(R.drawable.exercise_press_frances_polea, res("triceps_press_frances", "triceps_press_frances__cable"))
        assertEquals(R.drawable.exercise_press_frances_kettlebell, res("triceps_press_frances", "triceps_press_frances__kettlebell"))

        assertEquals(R.drawable.exercise_jm_press_barra, res("jm_press", "jm_press__barbell"))
        assertEquals(R.drawable.exercise_jm_press_ez, res("jm_press", "jm_press__ez_bar"))
        assertEquals(R.drawable.exercise_jm_press_mancuernas, res("jm_press", "jm_press__dumbbells"))
        assertEquals(R.drawable.exercise_jm_press_smith, res("jm_press", "jm_press__smith_machine"))
        assertEquals(R.drawable.exercise_jm_press_polea, res("jm_press", "jm_press__cable"))

        assertEquals(R.drawable.exercise_press_california_barra, res("california_press", "california_press__barbell"))
        assertEquals(R.drawable.exercise_press_california_ez, res("california_press", "california_press__ez_bar"))
        assertEquals(R.drawable.exercise_press_california_mancuernas, res("california_press", "california_press__dumbbells"))

        assertEquals(R.drawable.exercise_tate_press_mancuernas, res("tate_press", "tate_press__dumbbells"))
        assertEquals(R.drawable.exercise_tate_press_polea, res("tate_press", "tate_press__cable"))

        assertEquals(R.drawable.exercise_triceps_patada_mancuernas, res("triceps_patada", "triceps_patada__dumbbells__bilateral"))
        assertEquals(R.drawable.exercise_triceps_patada_mancuernas, res("triceps_patada", "triceps_patada__dumbbells__unilateral"))
        assertEquals(R.drawable.exercise_triceps_patada_polea, res("triceps_patada", "triceps_patada__cable__bilateral"))

        assertEquals(R.drawable.exercise_triceps_katana_polea, res("katana_extension", "katana_extension__cable__unilateral"))
        assertEquals(R.drawable.exercise_triceps_katana_banda, res("katana_extension", "katana_extension__band__bilateral"))

        assertEquals(R.drawable.exercise_triceps_cruzada_polea, res("crossbody_triceps_extension", "crossbody_triceps__cable__bilateral"))
        assertEquals(R.drawable.exercise_triceps_cruzada_polea, res("crossbody_triceps_extension", "crossbody_triceps__cable__unilateral"))

        assertEquals(R.drawable.exercise_triceps_extension_trx, res("triceps_extension", "triceps_extension__default"))
        assertEquals(R.drawable.exercise_triceps_pjr_mancuerna, res("triceps_extension_pjr_mancuerna", "triceps_extension_pjr_mancuerna__default"))
        assertEquals(R.drawable.exercise_triceps_flexion_esfinge, res("triceps_flexiones_esfinge", "triceps_flexiones_esfinge__default"))
        assertEquals(R.drawable.exercise_triceps_fondos_bancos, res("triceps_fondos_entre_bancos", "triceps_fondos_entre_bancos__default"))
        assertEquals(R.drawable.exercise_triceps_press_maquina, res("triceps_press_maquina", "triceps_press_maquina__default"))
        assertEquals(R.drawable.exercise_triceps_rolling_extension, res("triceps_rolling_extension", "triceps_rolling_extension__default"))
    }

    @Test
    fun laterality_token_is_not_treated_as_implement() {
        val barbell = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "hip_thrust",
            exerciseDbId = "hip_thrust__bilateral__barbell",
            exerciseId = "ex-ht",
            catalogConfigurationId = "hip_thrust__bilateral__barbell",
            selectedImplementation = "bilateral",
        )
        assertEquals(R.drawable.exercise_hip_thrust, barbell)
    }
}
