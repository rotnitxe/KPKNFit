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
    fun front_squat_resolves_barbell_and_smith_variants() {
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
    }

    @Test
    fun goblet_squat_resolves_image() {
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
    fun hack_squat_resolves_machine_image() {
        val res = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "quads_sentadilla_hack",
            exerciseDbId = "quads_sentadilla_hack__machine",
            exerciseId = "ex-hs-1",
            selectedImplementation = "machine",
        )
        assertEquals(R.drawable.exercise_sentadilla_hack_maquina, res)
    }
}
