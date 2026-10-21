package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAugeInferenceNoCoreTest {

    @Test
    @Suppress("DEPRECATION")
    fun unknown_exercise_does_not_invent_muscles_and_defaults_body_part_to_full() {
        val inferred = inferExerciseMetrics(
            type = "Accesorio",
            force = "",
            equipment = "Mancuerna",
            category = "Fuerza",
            isAxialLoaded = false,
            exerciseName = "Kárate kick alfa",
        )
        assertTrue(
            "suggestedMuscles should be empty but was: ${inferred.suggestedMuscles}",
            inferred.suggestedMuscles.isEmpty(),
        )
        assertEquals("full", inferred.suggestedBodyPart)
    }

    @Test
    @Suppress("DEPRECATION")
    fun known_rules_preserve_primary_muscles_without_inventing_core_as_primary() {
        val squat = inferExerciseMetrics(
            type = "Básico",
            force = "Sentadilla",
            equipment = "Barra",
            category = "Fuerza",
            isAxialLoaded = true,
            exerciseName = "Sentadilla trasera",
        )
        val squatPrimary = squat.suggestedMuscles.filter { it.role == MuscleRole.PRIMARY }
        assertEquals(1, squatPrimary.size)
        assertEquals("Cuádriceps", squatPrimary.first().muscle)
        assertEquals("lower", squat.suggestedBodyPart)

        val curl = inferExerciseMetrics(
            type = "Aislamiento",
            force = "",
            equipment = "Mancuerna",
            category = "Hipertrofia",
            isAxialLoaded = false,
            exerciseName = "Curl de bíceps",
        )
        val curlPrimary = curl.suggestedMuscles.filter { it.role == MuscleRole.PRIMARY }
        assertEquals(1, curlPrimary.size)
        assertEquals("Bíceps", curlPrimary.first().muscle)
        assertEquals("upper", curl.suggestedBodyPart)

        val rules = listOf(
            inferExerciseMetrics("Básico", "Sentadilla", "Barra", "Fuerza", true, "Sentadilla"),
            inferExerciseMetrics("Básico", "Bisagra", "Barra", "Fuerza", true, "Peso muerto"),
            inferExerciseMetrics("Básico", "Empuje", "Barra", "Fuerza", false, "Press banca"),
            inferExerciseMetrics("Básico", "Tirón", "Barra", "Fuerza", false, "Remo con barra"),
            inferExerciseMetrics("Aislamiento", "", "Mancuerna", "Hipertrofia", false, "Curl martillo"),
            inferExerciseMetrics("Aislamiento", "", "Polea", "Hipertrofia", false, "Extensiones de tríceps"),
        )
        for (rule in rules) {
            val primaryCore = rule.suggestedMuscles.filter {
                it.muscle.equals("Core", ignoreCase = true) && it.role == MuscleRole.PRIMARY
            }
            assertTrue("Core should never be PRIMARY in rule: ${rule.suggestedMuscles}", primaryCore.isEmpty())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun smart_exercise_creator_create_automatic_with_no_matches_produces_empty_involved_muscles() {
        val created = SmartExerciseCreator.createAutomatic("Kárate kick alfa", emptyList<ExerciseMuscleInfo>())
        assertTrue(
            "involvedMuscles should be empty for unmatched exercise but was: ${created.involvedMuscles}",
            created.involvedMuscles.isEmpty(),
        )
        assertEquals("full", created.bodyPart)
    }
}
