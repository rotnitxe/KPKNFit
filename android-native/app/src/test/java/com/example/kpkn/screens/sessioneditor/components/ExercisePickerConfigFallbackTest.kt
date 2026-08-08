package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.exercises.catalogv2.CatalogConfidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogEvidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionKindV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression para el hardening del picker (Fix 1): cuando el draft del usuario
 * selecciona opciones que no colapsan a una configuración exacta, el fallback
 * debe resolver la mejor configuración compatible en lugar de caer en silencio
 * a la configuración por defecto.
 */
class ExercisePickerConfigFallbackTest {

    private fun config(id: String, setup: String, implement: String) = ExerciseConfigurationV2(
        id = id,
        selectedOptions = mapOf("setup" to setup, "implement" to implement),
        displaySummary = "$setup · $implement",
        profile = ResolvedExerciseProfileV2(
            movementPatternId = "test_pattern",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = implement,
            loadMode = "external",
            primaryMuscles = listOf("test_muscle"),
            efc = 1.0,
            cnc = 1.0,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            technicalDifficulty = 2.0,
            resistanceProfile = "test",
            setupCues = listOf("Setup."),
            executionCues = listOf("Execute."),
            commonMistakes = listOf("Error."),
            performanceProfileId = id,
        ),
        evidence = evidence(),
    )

    private fun evidence() = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("test"),
    )

    private val definition = ExerciseDefinitionV2(
        id = "parent",
        familyId = "family",
        kind = ExerciseDefinitionKindV2.PARENT,
        canonicalName = "Ejercicio padre",
        description = "Ejercicio de prueba.",
        optionAxes = listOf("setup", "implement"),
        configurations = listOf(
            config("parent__standing__barbell", "standing", "barbell"),
            config("parent__standing__cable", "standing", "cable"),
            config("parent__seated__dumbbells", "seated", "dumbbells"),
        ),
        defaultConfigurationId = "parent__standing__barbell",
        evidence = evidence(),
    )

    private val default = definition.configurations.first { it.id == definition.defaultConfigurationId }

    @Test
    fun non_default_option_resolves_to_matching_config_not_default() {
        val resolved = bestMatchingConfigurationId(
            definition = definition,
            selectedOptions = mapOf("implement" to "cable"),
            default = default,
        )
        assertEquals("parent__standing__cable", resolved)
    }

    @Test
    fun partial_draft_prefers_default_config_when_compatible() {
        val resolved = bestMatchingConfigurationId(
            definition = definition,
            selectedOptions = mapOf("setup" to "standing"),
            default = default,
        )
        assertEquals("parent__standing__barbell", resolved)
    }

    @Test
    fun incompatible_draft_returns_null() {
        val resolved = bestMatchingConfigurationId(
            definition = definition,
            selectedOptions = mapOf("setup" to "missing"),
            default = default,
        )
        assertNull(resolved)
    }
}