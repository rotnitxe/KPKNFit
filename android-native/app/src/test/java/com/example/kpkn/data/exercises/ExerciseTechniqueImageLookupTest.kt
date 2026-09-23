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
        assertEquals(R.drawable.exercise_quads_sentadilla_copa_batch8, res)
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
        assertEquals(
            listOf("barbell", "safety_bar", "smith_machine", "machine"),
            ExerciseTechniqueImageLookup.variants("good_morning").map { it.implementation },
        )
        assertEquals(R.drawable.exercise_buenos_dias_sentado, res("good_morning_seated", "good_morning_seated__barbell", "barbell"))
        assertEquals(R.drawable.exercise_buenos_dias_zercher, res("good_morning_zercher", "good_morning_zercher__default"))
        assertEquals(R.drawable.exercise_buenos_dias_zercher, res("hams_peso_muerto_rumano_zercher", "hams_peso_muerto_rumano_zercher__default"))
        assertEquals(R.drawable.exercise_pull_through, res("hams_pull_through", "hams_pull_through__default"))

        assertEquals(R.drawable.exercise_hip_thrust, res("hip_thrust", "hip_thrust__bilateral__barbell"))
        assertEquals(R.drawable.exercise_hip_thrust_banda, res("hip_thrust", "hip_thrust__unilateral__band"))

        assertEquals(R.drawable.exercise_peso_muerto_convencional_deficit, res("hams_peso_muerto_convencional_deficit", "hams_peso_muerto_convencional_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_sumo_deficit, res("hams_peso_muerto_sumo_deficit", "hams_peso_muerto_sumo_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_piernas_rigidas_deficit, res("hams_peso_muerto_piernas_rigidas_deficit", "hams_peso_muerto_piernas_rigidas_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_deficit, res("hams_peso_muerto_rumano_deficit", "hams_peso_muerto_rumano_deficit__default"))
        assertEquals(R.drawable.exercise_peso_muerto_rumano_sumo_deficit, res("hams_peso_muerto_rumano_sumo_deficit", "hams_peso_muerto_rumano_sumo_deficit__default"))

        assertEquals(R.drawable.exercise_hams_swing_kettlebell_dos_manos_batch8, res("hams_swing_kettlebell_dos_manos", "hams_swing_kettlebell_dos_manos__default"))
        assertEquals(R.drawable.exercise_hams_swing_kettlebell_unilateral_batch8, res("hams_swing_kettlebell_unilateral", "hams_swing_kettlebell_unilateral__default"))
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

        assertEquals(R.drawable.exercise_triceps_patada_mancuernas, res("triceps_patada", "triceps_patada__dumbbells__bilateral"))
        assertEquals(R.drawable.exercise_triceps_patada_mancuernas, res("triceps_patada", "triceps_patada__dumbbells__unilateral"))
        assertEquals(R.drawable.exercise_triceps_patada_polea, res("triceps_patada", "triceps_patada__cable__bilateral"))

        assertEquals(R.drawable.exercise_triceps_katana_polea, res("katana_extension", "katana_extension__cable__unilateral"))

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
    fun arm_lote_c_forearms_maps_catalog_implement() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(R.drawable.exercise_curl_muneca_mancuernas, res("forearms_curl_muneca_sentado", "forearms_curl_muneca_sentado__dumbbells"))
        assertEquals(R.drawable.exercise_curl_muneca_barra, res("forearms_curl_muneca_sentado", "forearms_curl_muneca_sentado__barbell"))
        assertEquals(R.drawable.exercise_curl_muneca_ez, res("forearms_curl_muneca_sentado", "forearms_curl_muneca_sentado__ez_bar"))
        assertEquals(R.drawable.exercise_curl_muneca_polea, res("forearms_curl_muneca_sentado", "forearms_curl_muneca_sentado__cable"))

        assertEquals(R.drawable.exercise_extension_muneca_polea, res("forearms_curl_muneca_inverso_sentado", "forearms_curl_muneca_inverso_sentado__cable"))
        assertEquals(R.drawable.exercise_extension_muneca_mancuernas, res("forearms_curl_muneca_inverso_sentado", "forearms_curl_muneca_inverso_sentado__dumbbells"))
        assertEquals(R.drawable.exercise_extension_muneca_barra, res("forearms_curl_muneca_inverso_sentado", "forearms_curl_muneca_inverso_sentado__barbell"))
        assertEquals(R.drawable.exercise_extension_muneca_ez, res("forearms_curl_muneca_inverso_sentado", "forearms_curl_muneca_inverso_sentado__ez_bar"))

        assertEquals(R.drawable.exercise_curl_muneca_tras_espalda, res("forearms_curl_muneca_de_pie_tras_espalda_barra", "forearms_curl_muneca_de_pie_tras_espalda_barra__default"))
        assertEquals(R.drawable.exercise_enrollamiento_muneca_rodillo, res("forearms_enrollamiento_muneca_rodillo", "forearms_enrollamiento_muneca_rodillo__default"))

        assertEquals(R.drawable.exercise_supinacion_mancuernas, res("supination", "supination__dumbbells"))
        assertEquals(R.drawable.exercise_supinacion_polea, res("supination", "supination__cable"))
        assertEquals(R.drawable.exercise_pronacion_mancuernas, res("pronation", "pronation__dumbbells"))
        assertEquals(R.drawable.exercise_pronacion_polea, res("pronation", "pronation__cable"))

        assertEquals(R.drawable.exercise_paseo_granjero_mancuernas, res("forearms_paseo_del_granjero", "forearms_paseo_del_granjero__dumbbells"))
        assertEquals(R.drawable.exercise_paseo_granjero_kettlebell, res("forearms_paseo_del_granjero", "forearms_paseo_del_granjero__kettlebell"))
        assertEquals(R.drawable.exercise_paseo_granjero_discos, res("forearms_paseo_del_granjero", "forearms_paseo_del_granjero__plate"))
        assertEquals(R.drawable.exercise_paseo_granjero_hex, res("forearms_paseo_del_granjero", "forearms_paseo_del_granjero__hex_bar"))

        assertEquals(R.drawable.exercise_dead_hang, res("forearms_suspension_isometrica_barra_fija", "forearms_suspension_isometrica_barra_fija__default"))
        assertEquals(R.drawable.exercise_pinza_discos, res("forearms_pinza_de_discos", "forearms_pinza_de_discos__default"))
    }

    @Test
    fun approved_chest_and_back_batch_images_map_to_their_catalog_implement() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(R.drawable.exercise_incline_bench_press_barbell, res("incline_bench_press", "incline_bench_press__barbell"))
        assertEquals(R.drawable.exercise_incline_bench_press_dumbbells, res("incline_bench_press", "incline_bench_press__dumbbells"))
        assertEquals(R.drawable.exercise_incline_bench_press_smith_machine, res("incline_bench_press", "incline_bench_press__smith_machine"))
        assertEquals(R.drawable.exercise_incline_bench_press_machine, res("incline_bench_press", "incline_bench_press__machine"))
        assertEquals(R.drawable.exercise_incline_bench_press_cable, res("incline_bench_press", "incline_bench_press__cable"))
        assertEquals(R.drawable.exercise_incline_bench_press_kettlebell, res("incline_bench_press", "incline_bench_press__kettlebell"))

        assertEquals(R.drawable.exercise_decline_bench_press_barbell, res("decline_bench_press", "decline_bench_press__barbell"))
        assertEquals(R.drawable.exercise_decline_bench_press_dumbbells, res("decline_bench_press", "decline_bench_press__dumbbells"))
        assertEquals(R.drawable.exercise_decline_bench_press_smith_machine, res("decline_bench_press", "decline_bench_press__smith_machine"))
        assertEquals(R.drawable.exercise_decline_bench_press_kettlebell, res("decline_bench_press", "decline_bench_press__kettlebell"))

        assertEquals(R.drawable.exercise_flat_chest_fly_dumbbells, res("flat_chest_fly", "flat_chest_fly__dumbbells"))
        assertEquals(R.drawable.exercise_flat_chest_fly_cable, res("flat_chest_fly", "flat_chest_fly__cable"))
        assertEquals(R.drawable.exercise_flat_chest_fly_machine, res("flat_chest_fly", "flat_chest_fly__machine"))
        assertEquals(R.drawable.exercise_decline_chest_fly_dumbbells, res("decline_chest_fly", "decline_chest_fly__dumbbells"))
        assertEquals(R.drawable.exercise_incline_chest_fly_dumbbells, res("incline_chest_fly", "incline_chest_fly__dumbbells"))
        assertEquals(R.drawable.exercise_incline_chest_fly_cable, res("incline_chest_fly", "incline_chest_fly__cable"))
        assertEquals(R.drawable.exercise_incline_chest_fly_machine, res("incline_chest_fly", "incline_chest_fly__machine"))
        assertEquals(R.drawable.exercise_floor_press_barbell, res("floor_press", "floor_press__barbell"))

        assertEquals(R.drawable.exercise_conventional_row_barbell, res("conventional_row", "conventional_row__barbell"))
        assertEquals(R.drawable.exercise_conventional_row_dumbbells, res("conventional_row", "conventional_row__dumbbells"))
        assertEquals(R.drawable.exercise_chest_supported_row_dumbbells_medium, res("chest_supported_row", "chest_supported_row__dumbbells__medium"))
        assertEquals(R.drawable.exercise_chest_supported_row_machine_medium, res("chest_supported_row", "chest_supported_row__machine__medium"))
        assertEquals(R.drawable.exercise_chest_supported_row_cable_mid_medium, res("chest_supported_row", "chest_supported_row__cable__mid__medium"))
        assertEquals(R.drawable.exercise_lat_pulldown_cable, res("lat_pulldown", "lat_pulldown__bilateral__cable"))
        assertEquals(R.drawable.exercise_lat_pulldown_machine, res("lat_pulldown", "lat_pulldown__bilateral__machine"))
        assertEquals(R.drawable.exercise_seal_row_barbell, res("seal_row", "seal_row__barbell"))
    }

    @Test
    fun approved_chest_batch_three_images_map_to_catalog_implement() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(R.drawable.exercise_floor_press_dumbbells, res("floor_press", "floor_press__dumbbells"))
        assertEquals(R.drawable.exercise_lying_pullover_dumbbells, res("lying_pullover", "lying_pullover__dumbbells"))
        assertEquals(R.drawable.exercise_lying_pullover_barbell, res("lying_pullover", "lying_pullover__barbell"))
        assertEquals(R.drawable.exercise_lying_pullover_kettlebell, res("lying_pullover", "lying_pullover__kettlebell"))
        assertEquals(R.drawable.exercise_lying_pullover_cable, res("lying_pullover", "lying_pullover__cable"))
        assertEquals(R.drawable.exercise_seated_machine_pullover, res("seated_machine_pullover", "seated_machine_pullover__machine"))
        assertEquals(R.drawable.exercise_push_up_bodyweight, res("push_up", "push_up__flat"))
        assertEquals(R.drawable.exercise_cable_crossover_mid, res("tren_superior_cruce_poleas", "tren_superior_cruce_poleas__cable__mid"))
        assertEquals(R.drawable.exercise_parallel_bar_dip_bodyweight, res("tren_superior_fondos", "tren_superior_fondos__default"))
        assertEquals(R.drawable.exercise_resistance_band_chest_press, res("tren_superior_press_banda_resistencia", "tren_superior_press_banda_resistencia__default"))
        assertEquals(R.drawable.exercise_flat_converging_chest_press_machine, res("tren_superior_press_pecho_maquina_convergente", "tren_superior_press_pecho_maquina_convergente__default"))
        assertEquals(R.drawable.exercise_spoto_press_barbell, res("tren_superior_press_spoto_barra", "tren_superior_press_spoto_barra__default"))
        assertEquals(R.drawable.exercise_unilateral_cable_chest_press, res("tren_superior_press_unilateral_polea", "tren_superior_press_unilateral_polea__default"))
        assertEquals(R.drawable.exercise_squeeze_press_dumbbells, res("tren_superior_squeeze_press_mancuernas", "tren_superior_squeeze_press_mancuernas__default"))
        assertEquals(R.drawable.exercise_incline_converging_chest_press_machine, res("tren_superior_press_inclinado_maquina_convergente", "tren_superior_press_inclinado_maquina_convergente__default"))
    }

    @Test
    fun approved_back_batch_four_images_map_only_to_the_correct_implement() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(
            R.drawable.exercise_standing_cable_pullover,
            res("pullover", "pullover__bilateral__cable"),
        )
        assertEquals(
            R.drawable.exercise_standing_cable_pullover,
            res("pullover", "pullover__unilateral__cable"),
        )
        assertEquals(
            R.drawable.exercise_t_bar_row_machine,
            res("t_bar_row", "t_bar_row__machine__medium"),
        )
        assertNull(res("t_bar_row", "t_bar_row__t_bar__medium"))
    }

    @Test
    fun approved_back_batch_four_remainder_maps_without_substituting_missing_implements() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(R.drawable.exercise_pull_up_pronated_medium, res("pull_up", "pull_up__pronated__medium"))
        assertNull(res("pull_up", "pull_up__supinated__medium"))
        assertEquals(R.drawable.exercise_gironda_row_cable, res("gironda_row", "gironda_row__medium"))
        assertEquals(R.drawable.exercise_band_pull_apart, res("back_band_pull_apart", "back_band_pull_apart__default"))
        assertEquals(R.drawable.exercise_gorilla_row_dumbbells, res("back_remo_gorilla_mancuernas", "back_remo_gorilla_mancuernas__dumbbells"))
        assertEquals(R.drawable.exercise_gorilla_row_kettlebell, res("back_remo_gorilla_mancuernas", "back_remo_gorilla_mancuernas__kettlebell"))
        assertNull(res("back_remo_gorilla_mancuernas", "back_remo_gorilla_mancuernas__cable"))
        assertEquals(R.drawable.exercise_inverted_row_bodyweight, res("back_remo_invertido", "back_remo_invertido__default"))
        assertEquals(R.drawable.exercise_renegade_row_dumbbells, res("back_remo_renegado_mancuernas", "back_remo_renegado_mancuernas__dumbbells"))
        assertEquals(R.drawable.exercise_renegade_row_kettlebell, res("back_remo_renegado_mancuernas", "back_remo_renegado_mancuernas__kettlebell"))
        assertEquals(R.drawable.exercise_pendlay_row_barbell, res("pendlay_row", "pendlay_row__barbell"))
        assertEquals(R.drawable.exercise_pendlay_row_smith_machine, res("pendlay_row", "pendlay_row__smith_machine"))
        assertNull(res("pendlay_row", "pendlay_row__dumbbells"))
        assertNull(res("pendlay_row", "pendlay_row__machine"))
        assertNull(res("pendlay_row", "pendlay_row__cable"))
        assertNull(res("pendlay_row", "pendlay_row__kettlebell"))
        assertEquals(R.drawable.exercise_band_row, res("back_remo_banda", "back_remo_banda__default"))
        assertEquals(R.drawable.exercise_face_pull_cable, res("deltoides_face_pull", "deltoides_face_pull__default"))
        assertEquals(R.drawable.exercise_bench_press_chains, res("tren_superior_press_banca_cadenas", "tren_superior_press_banca_cadenas__default"))
    }

    @Test
    fun approved_shoulder_batch_five_images_map_to_their_own_implement() {
        fun res(
            definitionId: String,
            configurationId: String,
        ) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = configurationId,
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = configurationId,
        )

        assertEquals(R.drawable.exercise_arnold_press_dumbbells, res("arnold_press", "arnold_press__dumbbells"))
        assertEquals(R.drawable.exercise_arnold_press_kettlebell, res("arnold_press", "arnold_press__kettlebell"))
        assertEquals(R.drawable.exercise_arnold_press_cable, res("arnold_press", "arnold_press__cable"))

        assertEquals(R.drawable.exercise_military_press_barbell, res("military_press", "military_press__barbell"))
        assertEquals(R.drawable.exercise_military_press_dumbbells, res("military_press", "military_press__dumbbells"))
        assertEquals(R.drawable.exercise_military_press_smith_machine, res("military_press", "military_press__smith_machine"))
        assertEquals(R.drawable.exercise_military_press_machine, res("military_press", "military_press__machine"))
        assertEquals(R.drawable.exercise_military_press_cable, res("military_press", "military_press__cable"))
        assertEquals(R.drawable.exercise_military_press_kettlebell, res("military_press", "military_press__kettlebell"))

        assertEquals(R.drawable.exercise_seated_shoulder_press_barbell, res("seated_shoulder_press", "seated_shoulder_press__barbell"))
        assertEquals(R.drawable.exercise_seated_shoulder_press_dumbbells, res("seated_shoulder_press", "seated_shoulder_press__dumbbells"))
        assertEquals(R.drawable.exercise_seated_shoulder_press_smith_machine, res("seated_shoulder_press", "seated_shoulder_press__smith_machine"))
        assertEquals(R.drawable.exercise_seated_shoulder_press_machine, res("seated_shoulder_press", "seated_shoulder_press__machine"))
        assertEquals(R.drawable.exercise_seated_shoulder_press_cable, res("seated_shoulder_press", "seated_shoulder_press__cable"))
        assertEquals(R.drawable.exercise_seated_shoulder_press_kettlebell, res("seated_shoulder_press", "seated_shoulder_press__kettlebell"))
    }

    @Test
    fun approved_shoulder_batch_six_images_map_to_their_own_implement() {
        fun res(definitionId: String, implement: String) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = "${definitionId}__${implement}",
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = "${definitionId}__${implement}",
            selectedImplementation = implement,
        )

        val front = "deltoides_elevaciones_frontales"
        assertEquals(R.drawable.exercise_front_raise_cable, res(front, "cable"))
        assertEquals(R.drawable.exercise_front_raise_barbell, res(front, "barbell"))
        assertEquals(R.drawable.exercise_front_raise_dumbbells, res(front, "dumbbells"))
        assertEquals(R.drawable.exercise_front_raise_kettlebell, res(front, "kettlebell"))

        val seated = "seated_lateral_raise"
        assertEquals(R.drawable.exercise_seated_lateral_raise_dumbbells, res(seated, "dumbbells"))
        assertEquals(R.drawable.exercise_seated_lateral_raise_cable, res(seated, "cable"))
        assertEquals(R.drawable.exercise_seated_lateral_raise_machine, res(seated, "machine"))
        assertEquals(R.drawable.exercise_seated_lateral_raise_kettlebell, res(seated, "kettlebell"))

        val standing = "standing_lateral_raise"
        assertEquals(R.drawable.exercise_standing_lateral_raise_dumbbells, res(standing, "dumbbells"))
        assertEquals(R.drawable.exercise_standing_lateral_raise_cable, res(standing, "cable"))
        assertEquals(R.drawable.exercise_standing_lateral_raise_machine, res(standing, "machine"))
        assertEquals(R.drawable.exercise_standing_lateral_raise_kettlebell, res(standing, "kettlebell"))

        val rear = "rear_delt_raise"
        assertEquals(R.drawable.exercise_rear_delt_raise_dumbbells, res(rear, "dumbbells"))
        assertEquals(R.drawable.exercise_rear_delt_raise_cable, res(rear, "cable"))
        assertEquals(R.drawable.exercise_rear_delt_raise_machine, res(rear, "machine"))
    }

    @Test
    fun approved_batch_seven_images_map_to_catalog_definitions_and_implements() {
        fun res(definitionId: String, configurationId: String, implement: String) =
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = definitionId,
                exerciseDbId = configurationId,
                exerciseId = "ex-$definitionId",
                catalogConfigurationId = configurationId,
                selectedImplementation = implement,
            )

        assertEquals(R.drawable.exercise_z_press_barbell, res("z_press", "z_press__barbell", "barbell"))
        assertEquals(R.drawable.exercise_z_press_dumbbells, res("z_press", "z_press__dumbbells", "dumbbells"))
        assertEquals(R.drawable.exercise_z_press_ez_bar, res("z_press", "z_press__ez_bar", "ez_bar"))
        assertEquals(R.drawable.exercise_z_press_kettlebell, res("z_press", "z_press__kettlebell", "kettlebell"))

        val shrugs = "back_encogimientos"
        assertEquals(R.drawable.exercise_back_encogimientos_barbell, res(shrugs, "${shrugs}__barbell", "barbell"))
        assertEquals(R.drawable.exercise_back_encogimientos_dumbbells, res(shrugs, "${shrugs}__dumbbells", "dumbbells"))
        assertEquals(R.drawable.exercise_back_encogimientos_kettlebell, res(shrugs, "${shrugs}__kettlebell", "kettlebell"))
        assertEquals(R.drawable.exercise_back_encogimientos_smith_machine, res(shrugs, "${shrugs}__smith_machine", "smith_machine"))
        assertNull(res(shrugs, "${shrugs}__cable", "cable"))

        assertEquals(R.drawable.exercise_back_y_raises_dumbbells, res("back_y_raises", "back_y_raises__default", "dumbbells"))
        assertEquals(R.drawable.exercise_deltoides_y_raises_sentado_banco_inclinado_dumbbells, res("deltoides_y_raises_sentado_banco_inclinado", "deltoides_y_raises_sentado_banco_inclinado__default", "dumbbells"))
        assertEquals(R.drawable.exercise_back_dominadas_escapulares_bodyweight, res("back_dominadas_escapulares", "back_dominadas_escapulares__default", "bodyweight"))
        assertEquals(R.drawable.exercise_deltoides_push_press_barbell, res("deltoides_push_press", "deltoides_push_press__default", "barbell"))
        assertEquals(R.drawable.exercise_deltoides_press_landmine_unilateral_barbell, res("deltoides_press_landmine_unilateral", "deltoides_press_landmine_unilateral__default", "barbell"))
        assertEquals(R.drawable.exercise_deltoides_remo_menton_barbell, res("deltoides_remo_menton", "deltoides_remo_menton__default", "barbell"))
        assertEquals(R.drawable.exercise_core_plancha_bodyweight, res("core_plancha", "core_plancha__default", "bodyweight"))
    }

    @Test
    fun missing_catalog_implements_do_not_inherit_another_implement_image() {
        fun res(definitionId: String, implement: String) = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = definitionId,
            exerciseDbId = "${definitionId}__${implement}",
            exerciseId = "ex-$definitionId",
            catalogConfigurationId = "${definitionId}__${implement}",
            selectedImplementation = implement,
        )

        assertNull(res("chest_supported_row", "kettlebell"))
        assertNull(res("conventional_row", "machine"))
        assertNull(res("seal_row", "dumbbells"))
        assertNull(res("lat_pulldown", "band"))
        assertNull(res("sissy_squat", "barbell"))
        assertEquals(R.drawable.exercise_conventional_row_barbell, res("conventional_row", "barbell"))
    }

    @Test
    fun batch8_approved_images_resolve_only_for_their_catalog_implement() {
        val approved = listOf(
            Triple("core_crunch_suelo_peso_corporal", "bodyweight", R.drawable.exercise_core_crunch_suelo_peso_corporal_batch8),
            Triple("core_elevacion_piernas", "bodyweight", R.drawable.exercise_core_elevacion_piernas_batch8),
            Triple("core_inclinacion_lateral", "dumbbells", R.drawable.exercise_core_inclinacion_lateral_batch8),
            Triple("core_rueda_abdominal", "ab_wheel", R.drawable.exercise_core_rueda_abdominal_batch8),
            Triple("glutes_clamshells_banda", "band", R.drawable.exercise_glutes_clamshells_banda_batch8),
            Triple("glutes_monster_walk_banda", "band", R.drawable.exercise_glutes_monster_walk_banda_batch8),
            Triple("glutes_step_up_gluteo", "dumbbells", R.drawable.exercise_glutes_step_up_gluteo_batch8),
            Triple("quads_sentadilla_cosaca", "bodyweight", R.drawable.exercise_quads_sentadilla_cosaca_batch8),
            Triple("quads_sentadilla_pistola", "bodyweight", R.drawable.exercise_quads_sentadilla_pistola_batch8),
        )
        for ((definitionId, equipmentId, imageResId) in approved) {
            assertEquals(
                imageResId,
                ExerciseTechniqueImageLookup.resolveImageResId(
                    catalogDefinitionId = definitionId,
                    exerciseDbId = "${definitionId}__default",
                    exerciseId = "ex-$definitionId",
                    selectedImplementation = equipmentId,
                ),
            )
        }
        assertNull(
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "core_inclinacion_lateral",
                exerciseDbId = "core_inclinacion_lateral__default",
                exerciseId = "ex-core-inclinacion-lateral",
                selectedImplementation = "cable",
            ),
        )
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
