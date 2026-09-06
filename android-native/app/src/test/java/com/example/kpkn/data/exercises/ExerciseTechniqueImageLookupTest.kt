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
            R.drawable.exercise_sentadilla_sissy_barra,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "sissy_squat",
                exerciseDbId = "sissy_squat__barbell",
                exerciseId = "ex-sy-1",
                selectedImplementation = "barbell",
            ),
        )
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
}
