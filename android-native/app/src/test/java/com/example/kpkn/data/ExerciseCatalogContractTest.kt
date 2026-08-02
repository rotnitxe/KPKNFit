package com.example.kpkn.data

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogContractTest {
    private val catalog: ExerciseCatalogV2 by lazy {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }

    private val definitions
        get() = catalog.families.flatMap { it.definitions }

    private val configurations
        get() = definitions.flatMap { it.configurations }

    @Test
    fun approved_catalog_has_stable_schema_and_unique_exact_identities() {
        assertEquals(2, catalog.schemaVersion)
        assertEquals("v2-approved-2026-08-02", catalog.catalogRevision)
        assertEquals(catalog.families.size, catalog.families.map { it.id }.distinct().size)
        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertEquals(configurations.size, configurations.map { it.id }.distinct().size)
        assertTrue(definitions.size >= 200)
        assertTrue(configurations.size >= 250)
    }

    @Test
    fun every_definition_materializes_compatible_configurations_without_singleton_chips() {
        definitions.forEach { definition ->
            assertNotNull(definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId })
            definition.optionAxes.forEach { axis ->
                assertTrue(
                    "singleton axis ${definition.id}:$axis",
                    definition.configurations.mapNotNull { it.selectedOptions[axis] }.distinct().size > 1,
                )
            }
            definition.configurations.forEach { configuration ->
                assertEquals(definition.optionAxes.toSet(), configuration.selectedOptions.keys)
                assertFalse(configuration.profile.richMetadata == null)
                assertEquals("APPROVED", configuration.evidence.reviewStatus.name)
                assertTrue(configuration.profile.automationEligible)
            }
        }
    }

    @Test
    fun rich_metadata_is_identity_consistent_and_non_empty() {
        configurations.forEach { configuration ->
            val metadata = configuration.profile.richMetadata!!
            assertTrue(metadata.anatomy.targetRegions.isNotEmpty())
            assertTrue(metadata.anatomy.jointActions.isNotEmpty())
            assertTrue(metadata.biomechanics.relevantJoints.isNotEmpty())
            assertTrue(metadata.coaching.cues.isNotEmpty())
            assertTrue(metadata.programming.objectives.isNotEmpty())
            assertTrue(metadata.replacement.preservesIntent.isNotEmpty())
            assertEquals(configuration.profile.efc, metadata.fatigue.efc, 0.0)
            assertEquals(configuration.profile.performanceProfileId, metadata.identity.performanceProfileId)
        }
    }
}
