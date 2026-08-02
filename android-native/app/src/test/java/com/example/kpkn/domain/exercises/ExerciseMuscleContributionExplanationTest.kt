package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMuscleContributionExplanationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun exercise(
        name: String,
        movementPattern: String,
        force: String,
        muscle: String,
        role: MuscleRole = MuscleRole.SECONDARY,
    ): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = name.lowercase().replace(" ", "-"),
        name = name,
        description = "Descripción de prueba",
        movementPattern = movementPattern,
        force = force,
        involvedMuscles = listOf(InvolvedMuscle(muscle = muscle, role = role, volumeContribution = 0.5)),
    )

    @Test
    fun explanations_differ_between_press_row_squat_hinge_and_curl() {
        val press = explainMuscleContribution(
            exercise("Press de banca", "Empuje Horizontal", "Empuje", "Deltoides anterior"),
            InvolvedMuscle(
                muscle = "Deltoides anterior",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
            ),
        )
        val row = explainMuscleContribution(
            exercise("Remo con barra", "Tirón Horizontal", "Tirón", "Bíceps"),
            InvolvedMuscle(
                muscle = "Bíceps",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
            ),
        )
        val squat = explainMuscleContribution(
            exercise("Sentadilla", "Dominante de Rodilla", "Empuje", "Glúteos"),
            InvolvedMuscle(
                muscle = "Glúteos",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
            ),
        )
        val hinge = explainMuscleContribution(
            exercise("Peso muerto rumano", "Bisagra de Cadera", "Tirón", "Erectores espinales"),
            InvolvedMuscle(
                muscle = "Erectores espinales",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
            ),
        )
        val curl = explainMuscleContribution(
            exercise("Curl de bíceps", "Flexión de Codo", "Tirón", "Bíceps", MuscleRole.PRIMARY),
            InvolvedMuscle(
                muscle = "Bíceps",
                role = MuscleRole.PRIMARY,
                volumeContribution = 1.0,
            ),
        )

        assertTrue(press.contains("hombro", ignoreCase = true))
        assertTrue(press.contains("secundario", ignoreCase = true))
        assertTrue(row.contains("codo", ignoreCase = true))
        assertTrue(squat.contains("cadera", ignoreCase = true))
        assertTrue(hinge.contains("columna", ignoreCase = true))
        assertTrue(curl.contains("codo", ignoreCase = true))
        assertNotEquals(press, row)
        assertNotEquals(row, squat)
        assertNotEquals(squat, hinge)
        assertNotEquals(hinge, curl)
    }

    @Test
    fun manual_editorial_override_has_priority() {
        val exercise = exercise("Press de banca", "Empuje Horizontal", "Empuje", "Tríceps")
        val override = "La posición del codo cambia la demanda de extensión de codo."
        val reason = explainMuscleContribution(
            exercise,
            InvolvedMuscle(
                muscle = "Tríceps",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
                biomechanicalReason = override,
            ),
        )

        assertEquals(override, reason)
    }

    @Test
    fun every_catalog_involvement_has_specific_non_generic_explanation() {
        val catalog = listOf(
            File("../../catalog/exercises/v2/curation/evidence/legacy/exercise_database.json"),
            File("../catalog/exercises/v2/curation/evidence/legacy/exercise_database.json"),
            File("catalog/exercises/v2/curation/evidence/legacy/exercise_database.json"),
        ).first { it.exists() }
            .let { json.decodeFromString<List<ExerciseMuscleInfo>>(it.readText()) }

        val genericPhrases = listOf(
            "Aporta volumen parcial",
            "Fatiga local sin",
            "Trabajo de control; no cuenta",
            "Recibe la mayor parte del estímulo",
        )
        val reasons = catalog.flatMap { exercise ->
            exercise.involvedMuscles.map { involvement ->
                explainMuscleContribution(exercise, involvement)
            }
        }

        assertTrue(reasons.isNotEmpty())
        assertTrue(reasons.all { it.isNotBlank() && it.length >= 30 })
        assertFalse(reasons.any { reason ->
            genericPhrases.any { generic -> reason.contains(generic, ignoreCase = true) }
        })
    }
}
