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
    fun zercher_squat_has_exactly_one_variant_and_resolves_image() {
        val variants = ExerciseTechniqueImageLookup.variants("quads_sentadilla_zercher_barra_recta")
        assertEquals(1, variants.size)
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "quads_sentadilla_zercher_barra_recta",
            exerciseDbId = "quads_sentadilla_zercher_barra_recta__default",
            exerciseId = "ex-zs-1",
        )
        assertEquals(R.drawable.exercise_sentadilla_zercher, res)
    }
}
