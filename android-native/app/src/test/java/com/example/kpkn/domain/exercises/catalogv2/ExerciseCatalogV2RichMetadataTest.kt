package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class ExerciseCatalogV2RichMetadataTest {

    @Test
    fun adapter_carries_identity_and_resolved_metrics_together() {
        val evidence = CatalogEvidenceV2(
            reviewStatus = CatalogReviewStatusV2.DRAFT,
            confidence = CatalogConfidenceV2.MEDIUM,
            evidenceRefs = listOf("fixture"),
        )
        val family = ExerciseFamilyV2(
            id = "family",
            canonicalName = "Familia",
            description = "Descripción de familia suficientemente explícita para el fixture.",
            definitions = emptyList(),
            evidence = evidence,
        )
        val definition = ExerciseDefinitionV2(
            id = "parent",
            familyId = family.id,
            kind = ExerciseDefinitionKindV2.PARENT,
            canonicalName = "Padre",
            description = "Descripción de ejercicio suficientemente explícita para el fixture.",
            configurations = emptyList(),
            defaultConfigurationId = "config",
            evidence = evidence,
        )
        val configuration = ExerciseConfigurationV2(
            id = "config",
            selectedOptions = mapOf("implement" to "barbell"),
            displaySummary = "Barra",
            profile = fixtureProfile(),
            evidence = evidence,
        )

        val metadata = configuration.profile.toRichMetadata("v2", family, definition, configuration)

        assertEquals("family", metadata.identity.familyId)
        assertEquals("parent", metadata.identity.definitionId)
        assertEquals("config", metadata.identity.configurationId)
        assertEquals("barbell", metadata.biomechanics.equipmentId)
        assertEquals(2.0, metadata.fatigue.efc, 0.0)
        assertEquals(listOf("hamstrings"), metadata.anatomy.primaryMuscles)
        assertNotNull(metadata.coaching.setup.single())
    }

    private fun fixtureProfile() = ResolvedExerciseProfileV2(
        movementPatternId = "hip_hinge",
        bodyRegion = ExerciseBodyRegionV2.LOWER,
        kineticChain = ExerciseKineticChainV2.POSTERIOR,
        laterality = ExerciseLateralityV2.BILATERAL,
        equipmentId = "barbell",
        loadMode = "free_external_load",
        primaryMuscles = listOf("hamstrings"),
        efc = 2.0,
        cnc = 1.0,
        ssc = 0.5,
        ttc = 1.5,
        axialLoadFactor = 1.0,
        technicalDifficulty = 5.0,
        resistanceProfile = "lengthened_hip_extensor",
        setupCues = listOf("Ajusta la barra."),
        executionCues = listOf("Mueve la cadera."),
        commonMistakes = listOf("Redondear la espalda."),
        performanceProfileId = "hip_hinge_barbell",
    )
}
