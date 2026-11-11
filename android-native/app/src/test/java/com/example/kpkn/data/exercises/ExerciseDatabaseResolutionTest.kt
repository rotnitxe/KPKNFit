package com.example.kpkn.data.exercises

import com.example.kpkn.data.exercises.sharedLoadConfigurationId
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseDatabaseResolutionTest {

    private val custom = ExerciseMuscleInfo(
        id = "custom:triceps-extension",
        name = "Extensión de Tríceps",
        alias = "Jalón de tríceps, extensión cuerda",
    )
    private val index = mapOf(custom.id.lowercase() to custom)

    @Test
    fun resolves_custom_exercise_by_id() {
        val resolved = resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = null,
            exerciseDbId = custom.id,
            exerciseId = null,
            exerciseName = "Otro nombre",
        )

        assertEquals(custom.id, resolved?.id)
    }

    @Test
    fun public_resolver_reads_the_current_custom_overlay() {
        setCustomExerciseOverlay(listOf(custom))
        try {
            val resolved = resolveCatalogExerciseInfo(
                catalogConfigurationId = null,
                exerciseDbId = custom.id,
                exerciseId = null,
                exerciseName = custom.name,
            )
            assertEquals(custom.id, resolved?.id)
        } finally {
            setCustomExerciseOverlay(emptyList())
        }
    }

    @Test
    fun shared_load_identity_uses_exact_configuration_or_known_personal_id_only() {
        val personal = custom.copy(isCustom = true)
        setCustomExerciseOverlay(listOf(personal))
        try {
            assertEquals("squat__barbell", sharedLoadConfigurationId(
                Exercise(id = "occurrence-1", name = "Squat", catalogConfigurationId = "squat__barbell"),
            ))
            val planned = Exercise(
                id = "occurrence-2",
                name = personal.name,
                exerciseDbId = personal.id,
            )
            val completed = CompletedExercise(
                exerciseId = "occurrence-2",
                exerciseName = personal.name,
                exerciseDbId = personal.id,
            )
            assertEquals("personal:${personal.id}", sharedLoadConfigurationId(planned))
            assertEquals("personal:${personal.id}", sharedLoadConfigurationId(completed))
            assertNull(sharedLoadConfigurationId(planned.copy(exerciseDbId = "custom:deleted")))
        } finally {
            setCustomExerciseOverlay(emptyList())
        }
    }

    @Test
    fun does_not_resolve_by_name_when_id_is_stale() {
        val resolved = resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = null,
            exerciseDbId = "custom:old-id",
            exerciseId = "custom:old-id",
            exerciseName = "Extension de Triceps",
        )

        assertNull(resolved)
    }

    @Test
    fun does_not_resolve_by_alias_when_id_is_stale() {
        val resolved = resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = null,
            exerciseDbId = "custom:old-id",
            exerciseId = null,
            exerciseName = "Jalon de triceps",
        )

        assertNull(resolved)
    }

    @Test
    fun returns_null_when_id_and_name_do_not_match() {
        val resolved = resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = "missing-config",
            exerciseDbId = "missing-db-id",
            exerciseId = "missing-exercise-id",
            exerciseName = "Ejercicio inexistente",
        )

        assertNull(resolved)
    }
}
