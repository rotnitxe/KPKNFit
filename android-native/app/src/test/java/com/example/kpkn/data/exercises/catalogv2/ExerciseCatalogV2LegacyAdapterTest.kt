package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.domain.exercises.catalogv2.CatalogConfidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogEvidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionKindV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseFamilyV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogV2LegacyAdapterTest {
    @Test
    fun adapter_materializes_one_default_row_and_carries_v2_identity() {
        val evidence = CatalogEvidenceV2(
            reviewStatus = CatalogReviewStatusV2.APPROVED,
            confidence = CatalogConfidenceV2.HIGH,
            evidenceRefs = listOf("test"),
        )
        val profile = ResolvedExerciseProfileV2(
            movementPatternId = "elbow_flexion",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = "dumbbells",
            loadMode = "free_external_load",
            primaryMuscles = listOf("biceps"),
            secondaryMuscles = listOf("forearm"),
            stabilizerMuscles = emptyList(),
            efc = 2.0,
            cnc = 1.0,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            technicalDifficulty = 3.0,
            resistanceProfile = "gravity_arc",
            setupCues = listOf("Estable."),
            executionCues = listOf("Controla."),
            commonMistakes = listOf("Balancear."),
            performanceProfileId = "curl_free",
        )
        val catalog = ExerciseCatalogV2(
            schemaVersion = 2,
            catalogRevision = "test",
            ontologyRevision = "test",
            families = listOf(
                ExerciseFamilyV2(
                    id = "family",
                    canonicalName = "Familia",
                    description = "Familia de prueba para adaptar un perfil v2 a una card heredada.",
                    definitions = listOf(
                        ExerciseDefinitionV2(
                            id = "curl",
                            familyId = "family",
                            kind = ExerciseDefinitionKindV2.PARENT,
                            canonicalName = "Curl de bíceps",
                            description = "Flexión de codo con una configuración explícita y estable.",
                            optionAxes = listOf("implement"),
                            configurations = listOf(
                                ExerciseConfigurationV2(
                                    id = "curl__dumbbells",
                                    selectedOptions = mapOf("implement" to "dumbbells"),
                                    displaySummary = "Mancuernas",
                                    profile = profile,
                                    evidence = evidence,
                                ),
                            ),
                            defaultConfigurationId = "curl__dumbbells",
                            evidence = evidence,
                        ),
                    ),
                    evidence = evidence,
                ),
            ),
        )

        val row = catalog.toLegacyDefaultCatalog().single()
        assertEquals("curl", row.catalogDefinitionId)
        assertEquals("curl__dumbbells", row.catalogConfigurationId)
        assertEquals("test", row.catalogRevision)
        assertEquals("curl_free", row.performanceProfileId)
        assertEquals("Bíceps", row.involvedMuscles.first().muscle)
        assertTrue(row.catalogOptionAxes.isNullOrEmpty())
    }
}
