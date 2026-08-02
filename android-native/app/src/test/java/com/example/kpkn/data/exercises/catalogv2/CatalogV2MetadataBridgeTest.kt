package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
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
import com.example.kpkn.domain.exercises.catalogv2.toRichMetadata
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogV2MetadataBridgeTest {
    @Test
    fun completed_snapshot_requires_exact_selection_and_keeps_typed_metadata() {
        val catalog = fixtureCatalog()
        val family = catalog.families.single()
        val definition = family.definitions.single()
        val configuration = definition.configurations.single()
        val metadata = configuration.profile.toRichMetadata(catalog.catalogRevision, family, definition, configuration)
        val info = ExerciseMuscleInfo(
            id = configuration.id,
            name = definition.canonicalName,
            catalogRevision = catalog.catalogRevision,
            catalogDefinitionId = definition.id,
            catalogConfigurationId = configuration.id,
            performanceProfileId = configuration.profile.performanceProfileId,
            catalogReviewStatus = "APPROVED",
            catalogRichMetadataJson = Json.encodeToString(
                com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2.serializer(),
                metadata,
            ),
        )
        val exercise = Exercise(
            id = "occ-1",
            name = definition.canonicalName,
            exerciseDbId = configuration.id,
            catalogRevision = catalog.catalogRevision,
            catalogDefinitionId = definition.id,
            catalogConfigurationId = configuration.id,
            performanceProfileId = configuration.profile.performanceProfileId,
            occurrenceId = "occ-1",
        )

        val encoded = exercise.toResolvedCatalogSnapshotJson(info, capturedAtEpochMs = 42L)
        val snapshot = encoded?.let {
            kotlinx.serialization.json.Json.decodeFromString(
                com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseSnapshotV2.serializer(),
                it,
            )
        }

        assertNotNull(snapshot)
        assertEquals("config", snapshot!!.selection.configurationId)
        assertEquals("profile", snapshot.resolvedProfile.performanceProfileId)
        assertEquals(42L, snapshot.capturedAtEpochMs)
        assertNull(exercise.copy(catalogConfigurationId = "wrong").toResolvedCatalogSnapshotJson(info, 42L))
    }

    private fun fixtureCatalog(): ExerciseCatalogV2 {
        val evidence = CatalogEvidenceV2(
            reviewStatus = CatalogReviewStatusV2.APPROVED,
            confidence = CatalogConfidenceV2.HIGH,
            evidenceRefs = listOf("fixture"),
        )
        val profile = ResolvedExerciseProfileV2(
            movementPatternId = "elbow_flexion",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = "barbell",
            loadMode = "free_external_load",
            primaryMuscles = listOf("biceps"),
            efc = 2.0,
            cnc = 1.0,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            technicalDifficulty = 2.0,
            resistanceProfile = "gravity_arc",
            setupCues = listOf("Setup."),
            executionCues = listOf("Execute."),
            commonMistakes = listOf("Error."),
            performanceProfileId = "profile",
        )
        val definition = ExerciseDefinitionV2(
            id = "parent",
            familyId = "family",
            kind = ExerciseDefinitionKindV2.PARENT,
            canonicalName = "Padre",
            description = "Fixture de prueba suficientemente descriptivo.",
            configurations = listOf(
                ExerciseConfigurationV2(
                    id = "config",
                    selectedOptions = mapOf("implement" to "barbell"),
                    displaySummary = "Barra",
                    profile = profile,
                    evidence = evidence,
                ),
            ),
            defaultConfigurationId = "config",
            evidence = evidence,
        )
        return ExerciseCatalogV2(
            schemaVersion = 2,
            catalogRevision = "v2",
            ontologyRevision = "ontology",
            families = listOf(
                ExerciseFamilyV2(
                    id = "family",
                    canonicalName = "Familia",
                    description = "Familia de prueba suficientemente descriptiva.",
                    definitions = listOf(definition),
                    evidence = evidence,
                ),
            ),
        )
    }
}
