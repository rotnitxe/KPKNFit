package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionExerciseTypeaheadTest {

    private val squat = ExerciseMuscleInfo(id = "sq-barbell", name = "Sentadilla barra", alias = "Back squat")
    private val bench = ExerciseMuscleInfo(id = "bp-barbell", name = "Press banca", alias = "Bench press")
    private val index = listOf(squat, bench).associateBy { it.id.lowercase() }

    @Test
    fun suggest_returns_catalog_ids_never_invented() {
        val hits = CompetitionExerciseTypeahead.suggest("sentadilla", index)
        assertEquals(1, hits.size)
        assertEquals("sq-barbell", hits.first().exercise.id)
        assertFalse(CompetitionExerciseTypeahead.isCustomId(hits.first().exercise.id))
        assertTrue(hits.all { it.exercise.id in index.keys || index.containsKey(it.exercise.id.lowercase()) })
    }

    @Test
    fun unmatched_name_uses_custom_prefix_via_smart_creator() {
        val created = PowerliftingWizardDraft.createCustomExercise("Press de trineo lunar", index.values.toList())
        assertTrue(created.id.startsWith(CompetitionExerciseTypeahead.CUSTOM_PREFIX))
        assertTrue(created.isCustom)
        assertFalse(index.containsKey(created.id.lowercase()))
    }

    @Test
    fun empty_query_returns_no_suggestions() {
        assertTrue(CompetitionExerciseTypeahead.suggest("", index).isEmpty())
    }
}
