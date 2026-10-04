package com.example.kpkn.data

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionValidationV2
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
        // Drift de conteos reconciliado con honestidad (T-002b, paquete E):
        // esta prueba esperaba 197 definiciones/510 configuraciones bajo
        // `v2-approved-2026-08-12-a`, pero el asset de HEAD (e520812e6) ya
        // publicaba 199/512 — drift PREEXISTENTE a este trabajo, no causado por
        // él. Las altas curadas de cuerpo (T-041/T-043, revisión 2026-09-28) lo
        // llevaron a 200/522 y la alta curada `skullcrusher` de §13.5 (paquete E)
        // lo lleva a 201/523 bajo `v2-approved-2026-09-29-a`. La expectativa es
        // el conteo REAL aprobado tras las altas autorizadas; el resto de
        // aserciones de este test se conservan intactas. El retiro de
        // `sissy_squat__barbell` (decisión del usuario, 2026-10-02) lo deja en 201/522. Las
        // cinco altas M1-M5 del 2026-10-03 (`close_grip_bench_press`, `paused_back_squat`,
        // `deadlift_to_knees`, `close_grip_lat_pulldown` e `incline_biceps_curl`) lo llevan
        // a 206/527 con la misma revisión. El usuario retira las cuatro
        // configuraciones unilaterales de `romanian_sumo_deadlift` el
        // 2026-10-04: quedan 206 definiciones y 523 configuraciones.
        assertEquals(2, catalog.schemaVersion)
        assertEquals("v2-approved-2026-09-29-a", catalog.catalogRevision)
        assertEquals(catalog.families.size, catalog.families.map { it.id }.distinct().size)
        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertEquals(configurations.size, configurations.map { it.id }.distinct().size)
        assertEquals(206, definitions.size)
        assertEquals(523, configurations.size)
    }

    @Test
    fun retired_configuration_selection_resolves_to_its_documented_replacement() {
        assertTrue(configurations.none { it.id == "sissy_squat__barbell" })
        val resolver = ExerciseCatalogV2Resolver(catalog)
        val saved = ExerciseSelectionV2(
            definitionId = "sissy_squat",
            configurationId = "sissy_squat__barbell",
            catalogRevision = catalog.catalogRevision,
        )
        val validation = resolver.validate(saved)
        assertTrue(validation is ExerciseSelectionValidationV2.Valid)
        assertEquals(
            "sissy_squat__smith_machine",
            (validation as ExerciseSelectionValidationV2.Valid).selection.configurationId,
        )
        assertEquals(
            configurations.single { it.id == "sissy_squat__smith_machine" }.profile,
            resolver.resolve(saved),
        )
    }

    @Test
    fun retired_single_leg_sumo_rdl_selections_resolve_to_same_implement_conventional_rdl() {
        val resolver = ExerciseCatalogV2Resolver(catalog)
        val sumo = definitions.single { it.id == "romanian_sumo_deadlift" }
        assertEquals("romanian_sumo_deadlift__bilateral__barbell", sumo.defaultConfigurationId)
        assertEquals(listOf("implement"), sumo.optionAxes)
        listOf("barbell", "smith_machine", "dumbbells", "hex_bar").forEach { implement ->
            val retiredId = "romanian_sumo_deadlift__unilateral__$implement"
            val replacementId = "romanian_deadlift__unilateral__$implement"
            assertTrue(configurations.none { it.id == retiredId })
            assertTrue(sumo.configurations.any { it.id == "romanian_sumo_deadlift__bilateral__$implement" })
            val saved = ExerciseSelectionV2("romanian_sumo_deadlift", retiredId, catalog.catalogRevision)
            val validation = resolver.validate(saved)
            assertTrue(validation is ExerciseSelectionValidationV2.Valid)
            val migrated = (validation as ExerciseSelectionValidationV2.Valid).selection
            assertEquals("romanian_deadlift", migrated.definitionId)
            assertEquals(replacementId, migrated.configurationId)
            val target = configurations.single { it.id == replacementId }.profile
            assertEquals(implement, target.equipmentId)
            assertEquals("UNILATERAL", target.laterality.name)
            assertEquals(target, resolver.resolve(saved))
        }
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
            assertTrue(metadata.anatomy.jointActions.isNotEmpty())
            assertTrue(metadata.anatomy.jointInvolvement.isNotEmpty())
            assertTrue(metadata.biomechanics.relevantJoints.isNotEmpty())
            assertEquals(
                metadata.biomechanics.relevantJoints.toSet(),
                metadata.anatomy.jointInvolvement.map { it.jointId }.toSet(),
            )
            assertTrue(configuration.profile.description.length >= 40)
            assertTrue(configuration.profile.setupCues.isNotEmpty())
            assertTrue(configuration.profile.executionCues.isNotEmpty())
            // Derived anatomy mirrors: the ficha authors muscles/joints, the rest follows.
            assertEquals(
                configuration.profile.jointInvolvement.flatMap { it.actions }.distinct(),
                metadata.anatomy.jointActions,
            )
            assertEquals(
                configuration.profile.primaryMuscles.map { "${configuration.profile.movementPatternId}:$it" },
                metadata.replacement.preservesIntent,
            )
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
        assertNotEquals(wideDumbbells.profile.executionCues, wideCable.profile.executionCues)
        assertTrue(wideDumbbells.profile.jointInvolvement.any { it.jointId == "glenohumeral" })
        assertEquals(
            wideCable.profile.jointInvolvement.map { it.jointId }.toSet(),
            wideCable.profile.richMetadata!!.biomechanics.relevantJoints.toSet(),
        )
    }
}
