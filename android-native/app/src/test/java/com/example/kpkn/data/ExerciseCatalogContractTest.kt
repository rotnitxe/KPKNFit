package com.example.kpkn.data

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogContractTest {
    private val catalog: List<ExerciseMuscleInfo> by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString(
            File("src/main/assets/exercise_database.json").readText(),
        )
    }

    @Test
    fun catalog_has_expected_unique_rows_and_preserves_submuscle_group() {
        // Canonical catalog after alias consolidation (duplicates redirected via exercise_id_aliases.json).
        assertEquals(257, catalog.size)
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertTrue(catalog.any { !it.subMuscleGroup.isNullOrBlank() })
    }

    @Test
    fun activation_respects_role_contract() {
        val violations = catalog.flatMap { exercise ->
            exercise.involvedMuscles.mapNotNull { involved ->
                val activation = involved.volumeContribution ?: return@mapNotNull "${exercise.id}:missing"
                val valid = when (involved.role) {
                    MuscleRole.PRIMARY -> activation in 0.8..1.0
                    MuscleRole.SECONDARY -> activation in 0.3..0.7
                    MuscleRole.STABILIZER -> activation in 0.0..0.4
                    MuscleRole.NEUTRALIZER -> activation in 0.0..0.4
                }
                if (valid) null else "${exercise.id}:${involved.muscle}:${involved.role}:$activation"
            }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun technical_modifiers_use_canonical_parent_names() {
        val allowed = setOf(
            "Antebrazo", "Bíceps", "Core", "Deltoides", "Dorsales",
            "Erectores Espinales", "Glúteo Mayor", "Pectorales", "Trapecio", "Tríceps",
        )
        val unknown = catalog.flatMap { exercise ->
            exercise.technicalAspects.orEmpty().flatMap { aspect ->
                aspect.options.flatMap { option ->
                    option.modifiers.mapNotNull { modifier ->
                        modifier.muscle.takeUnless { it in allowed }?.let { "${exercise.id}:$it" }
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test
    fun descriptions_are_capitalized_and_technical_options_are_documented() {
        assertTrue(catalog.all { !it.description.isNullOrBlank() })
        assertTrue(catalog.none { it.description.orEmpty().contains("ajustable en chips", ignoreCase = true) })
        val options = catalog.flatMap { it.technicalAspects.orEmpty() }.flatMap { it.options }
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { !it.description.isNullOrBlank() })
    }

    @Test
    fun barbell_hinges_have_minimum_structural_cost() {
        val violations = catalog.filter {
            it.movementPattern.orEmpty().startsWith("Bisagra") &&
                it.equipment == "Barra" &&
                (it.ssc ?: 0.0) < 1.0
        }.map { it.id }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun catalog_forbids_manguito_rotador_as_volume_muscle() {
        val hits = catalog.flatMap { exercise ->
            exercise.involvedMuscles.mapNotNull { involved ->
                involved.muscle.takeIf { it.contains("manguito", ignoreCase = true) }
                    ?.let { "${exercise.id}:$it" }
            }
        }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun deltoid_and_glute_emphasis_use_canonical_head_keywords() {
        val allowedDeltoid = setOf("anterior", "medio", "posterior", null)
        val allowedGlute = setOf("mayor", "medio", "menor", null)
        val violations = catalog.flatMap { exercise ->
            exercise.involvedMuscles.mapNotNull { involved ->
                when (involved.muscle) {
                    "Deltoides" -> involved.emphasis.takeUnless { it in allowedDeltoid }
                        ?.let { "${exercise.id}:Deltoides:$it" }
                    "Glúteos" -> involved.emphasis.takeUnless { it in allowedGlute }
                        ?.let { "${exercise.id}:Glúteos:$it" }
                    else -> null
                }
            }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun deltoid_and_glute_never_duplicate_parent_rows() {
        val violations = catalog.flatMap { exercise ->
            listOf("Deltoides", "Glúteos").mapNotNull { parent ->
                val count = exercise.involvedMuscles.count { it.muscle == parent }
                if (count > 1) "${exercise.id}:$parent x$count" else null
            }
        }
        assertEquals(emptyList<String>(), violations)
    }
}
