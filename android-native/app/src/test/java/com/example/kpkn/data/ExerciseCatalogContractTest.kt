package com.example.kpkn.data

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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
        assertEquals("v2-approved-2026-08-08-c", catalog.catalogRevision)
        assertEquals(catalog.families.size, catalog.families.map { it.id }.distinct().size)
        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertEquals(configurations.size, configurations.map { it.id }.distinct().size)
        assertEquals(196, definitions.size)
        assertEquals(518, configurations.size)
    }

    @Test
    fun every_definition_materializes_compatible_configurations_without_singleton_chips() {
        definitions.forEach { definition ->
            assertNotNull(definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId })
            definition.optionAxes.forEach { axis ->
                if (axis == "pulley_height") return@forEach
                if (axis == "implement" && "pulley_height" in definition.optionAxes) {
                    // Cable-fixed definition: implement is implicitly cable.
                    return@forEach
                }
                assertTrue(
                    "singleton axis ${definition.id}:$axis",
                    definition.configurations.mapNotNull { it.selectedOptions[axis] }.distinct().size > 1,
                )
            }
            definition.configurations.forEach { configuration ->
                val expected = definition.optionAxes.toMutableSet()
                if ("pulley_height" in expected) {
                    if ("implement" in expected) {
                        if (configuration.selectedOptions["implement"] == "cable") {
                            assertTrue(
                                "missing pulley_height ${configuration.id}",
                                "pulley_height" in configuration.selectedOptions,
                            )
                        } else {
                            assertFalse(
                                "forbidden pulley_height ${configuration.id}",
                                "pulley_height" in configuration.selectedOptions,
                            )
                            expected.remove("pulley_height")
                        }
                    } else {
                        assertTrue(
                            "missing pulley_height ${configuration.id}",
                            "pulley_height" in configuration.selectedOptions,
                        )
                    }
                }
                assertEquals(expected, configuration.selectedOptions.keys)
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
            assertTrue(metadata.anatomy.jointInvolvement.isNotEmpty())
            assertTrue(metadata.biomechanics.relevantJoints.isNotEmpty())
            assertEquals(
                metadata.biomechanics.relevantJoints.toSet(),
                metadata.anatomy.jointInvolvement.map { it.jointId }.toSet(),
            )
            assertTrue(configuration.profile.benefits.size >= 2)
            assertTrue(configuration.profile.techniqueSummary.length >= 40)
            assertEquals(configuration.profile.description, metadata.editorial.description)
            assertTrue(metadata.coaching.cues.isNotEmpty())
            assertTrue(metadata.programming.objectives.isNotEmpty())
            assertTrue(metadata.replacement.preservesIntent.isNotEmpty())
            assertEquals(configuration.profile.efc, metadata.fatigue.efc, 0.0)
            assertEquals(configuration.profile.performanceProfileId, metadata.identity.performanceProfileId)
        }
    }

    @Test
    fun exact_configuration_copy_and_joint_profile_change_with_variant_axes() {
        val definition = definitions.single { it.id == "chest_supported_row" }
        val wideDumbbells = definition.configurations.single { it.id.endsWith("__dumbbells__wide") }
        val closeDumbbells = definition.configurations.single { it.id.endsWith("__dumbbells__close") }
        val wideCable = definition.configurations.single { it.id.endsWith("__cable__high__wide") }

        assertNotEquals(wideDumbbells.profile.description, closeDumbbells.profile.description)
        assertNotEquals(wideDumbbells.profile.techniqueSummary, wideCable.profile.techniqueSummary)
        assertTrue(wideDumbbells.profile.muscleNotes.any { it.muscleId == "trapezius" })
        assertTrue(closeDumbbells.profile.muscleNotes.any { it.muscleId == "biceps" })
        assertTrue(wideDumbbells.profile.jointInvolvement.any { it.jointId == "glenohumeral" })
        assertEquals(
            wideCable.profile.jointInvolvement.map { it.jointId }.toSet(),
            wideCable.profile.richMetadata!!.biomechanics.relevantJoints.toSet(),
        )
    }
}
