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
}
