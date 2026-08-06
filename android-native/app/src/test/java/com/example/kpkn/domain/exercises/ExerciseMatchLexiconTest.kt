package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMatchLexiconTest {

    @Test
    fun normalize_folds_accents_and_punctuation() {
        assertEquals("aperturas planas", ExerciseMatchLexicon.normalize("Apertúras Planás!"))
        assertEquals("peso muerto", ExerciseMatchLexicon.normalize("Peso  Muerto,."))
    }

    @Test
    fun stem_handles_plural_spanish() {
        assertEquals("apertura", ExerciseMatchLexicon.stem("aperturas"))
        assertEquals("plana", ExerciseMatchLexicon.stem("planas"))
        assertEquals("press", ExerciseMatchLexicon.stem("press"))
        assertEquals("triceps", ExerciseMatchLexicon.stem("triceps"))
    }

    @Test
    fun synonyms_es_en_map_to_canonical_keys() {
        assertEquals("fly", ExerciseMatchLexicon.synonymKey("apertura"))
        assertEquals("fly", ExerciseMatchLexicon.synonymKey("fly"))

        assertEquals("chest", ExerciseMatchLexicon.synonymKey("pecho"))
        assertEquals("chest", ExerciseMatchLexicon.synonymKey("pectorales"))
        assertEquals("squat", ExerciseMatchLexicon.synonymKey("sentadilla"))
        assertEquals("row", ExerciseMatchLexicon.synonymKey("remo"))
    }

    @Test
    fun token_similarity_handles_typos() {
        val similarity = ExerciseMatchLexicon.tokenSimilarity(
            "Apérturas Planas",
            "aperturas planas, chest fly planas",
        )
        assertTrue("similarity=$similarity", similarity >= 0.55)
    }

    @Test
    fun token_similarity_uses_synonyms() {
        val similarity = ExerciseMatchLexicon.tokenSimilarity(
            "Chest Fly Planas",
            "aperturas planas, chest fly planas",
        )
        assertTrue("similarity=$similarity", similarity >= 0.8)
    }

    @Test
    fun fuzzy_match_tolerates_one_edit() {
        assertTrue(ExerciseMatchLexicon.fuzzyMatch("apretura", "apertura"))
        assertTrue(ExerciseMatchLexicon.fuzzyMatch("sentadilla", "sentadila"))
        assertFalse(ExerciseMatchLexicon.fuzzyMatch("curl", "peso"))
    }

    @Test
    fun known_phrases_are_detected() {
        assertTrue(ExerciseMatchLexicon.containsKnownPhrase("Peso muerto rumano", "peso muerto rumano con barra"))
        assertTrue(ExerciseMatchLexicon.containsKnownPhrase("Press banca", "press banca con barra"))
        assertFalse(ExerciseMatchLexicon.containsKnownPhrase("Curl femoral", "peso muerto"))
    }

    @Test
    fun mentioned_muscle_groups_are_resolved() {
        assertEquals(setOf("Pectorales"), ExerciseMatchLexicon.mentionedMuscleGroups("Aperturas de pecho"))
        assertEquals(setOf("Dorsales", "Trapecio", "Romboides"), ExerciseMatchLexicon.mentionedMuscleGroups("Remo espalda"))
        assertEquals(emptySet<String>(), ExerciseMatchLexicon.mentionedMuscleGroups("Press banca"))
    }

    @Test
    fun exact_match_requires_normalized_equality() {
        val custom = listOf(
            ExerciseMuscleInfo(id = "custom:1", name = "Aperturas Planas", alias = "aperturas planas, chest fly"),
        )
        assertTrue(ExerciseMatchLexicon.hasExactMatch("Apertúras Planas!", customExercises = custom))
        assertTrue(ExerciseMatchLexicon.hasExactMatch("Chest Fly", customExercises = custom))
        assertFalse(ExerciseMatchLexicon.hasExactMatch("Aperturas Planas Inclinadas", customExercises = custom))
        assertFalse(ExerciseMatchLexicon.hasExactMatch("Aperturas", customExercises = custom))
    }
}
