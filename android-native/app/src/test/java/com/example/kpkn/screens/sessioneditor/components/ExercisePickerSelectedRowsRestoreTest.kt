package com.example.kpkn.screens.sessioneditor.components

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExercisePickerSelectedRowsRestoreTest {

    private fun evidence() = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("test"),
    )

    private fun config(id: String, implement: String, grip: String) = ExerciseConfigurationV2(
        id = id,
        selectedOptions = mapOf("implement" to implement, "grip_width" to grip),
        displaySummary = "$implement · $grip",
        profile = ResolvedExerciseProfileV2(
            movementPatternId = "horizontal_pull",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.POSTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = implement,
            loadMode = "external",
            primaryMuscles = listOf("lats"),
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

    private val tBarRow = ExerciseDefinitionV2(
        id = "t_bar_row",
        familyId = "horizontal_pull",
        kind = ExerciseDefinitionKindV2.PARENT,
        canonicalName = "Remo en Barra T",
        description = "Remo con barra T.",
        optionAxes = listOf("implement", "grip_width"),
        configurations = listOf(
            config("t_bar_row__machine__wide", "machine", "wide"),
            config("t_bar_row__machine__medium", "machine", "medium"),
            config("t_bar_row__t_bar__medium", "t_bar", "medium"),
        ),
        defaultConfigurationId = "t_bar_row__t_bar__medium",
        evidence = evidence(),
    )

    private val catalog = ExerciseCatalogV2(
        schemaVersion = 2,
        catalogRevision = "test-revision",
        ontologyRevision = "test-ontology",
        families = listOf(
            ExerciseFamilyV2(
                id = "horizontal_pull",
                canonicalName = "Remo",
                description = "Familia de remo.",
                definitions = listOf(tBarRow),
                evidence = evidence(),
            ),
        ),
    )

    private val definitionsById = mapOf(tBarRow.id to tBarRow)
    private val firstCatalogId = tBarRow.configurations.first().id

    @Test
    fun resolve_never_uses_first_catalog_row_as_default() {
        assertEquals("t_bar_row__machine__wide", firstCatalogId)
        val resolved = resolveSelectedCatalogConfigurationId(
            definition = tBarRow,
            draftOptions = emptyMap(),
            previousConfigurationId = null,
            initialConfigurationId = null,
        )
        assertEquals("t_bar_row__t_bar__medium", resolved)
        assertNotEquals(firstCatalogId, resolved)
    }

    @Test
    fun draft_machine_medium_resolves_exact_config_not_wide() {
        val resolved = resolveSelectedCatalogConfigurationId(
            definition = tBarRow,
            draftOptions = mapOf("implement" to "machine", "grip_width" to "medium"),
            previousConfigurationId = null,
            initialConfigurationId = null,
        )
        assertEquals("t_bar_row__machine__medium", resolved)
    }

    @Test
    fun previous_choice_survives_parent_that_only_keeps_definition_ids() {
        val chosen = restoreSelectedCatalogRows(
            catalog = catalog,
            definitionsById = definitionsById,
            selectedIds = listOf("t_bar_row"),
            previous = mapOf(
                "t_bar_row" to ExerciseMuscleInfo(
                    id = "t_bar_row",
                    name = "Remo en Barra T",
                    catalogConfigurationId = "t_bar_row__machine__medium",
                ),
            ),
            draftByDefinition = emptyMap(),
        )
        assertEquals("t_bar_row__machine__medium", chosen.getValue("t_bar_row").catalogConfigurationId)
        assertNotEquals(firstCatalogId, chosen.getValue("t_bar_row").catalogConfigurationId)
    }

    @Test
    fun restore_without_previous_uses_default_not_first_row() {
        val restored = restoreSelectedCatalogRows(
            catalog = catalog,
            definitionsById = definitionsById,
            selectedIds = listOf("t_bar_row"),
            previous = emptyMap(),
            draftByDefinition = emptyMap(),
        )
        assertEquals("t_bar_row__t_bar__medium", restored.getValue("t_bar_row").catalogConfigurationId)
    }

    @Test
    fun restore_honors_initial_configuration_for_replacement() {
        val restored = restoreSelectedCatalogRows(
            catalog = catalog,
            definitionsById = definitionsById,
            selectedIds = listOf("t_bar_row"),
            previous = emptyMap(),
            draftByDefinition = emptyMap(),
            initialCatalogDefinitionId = "t_bar_row",
            initialCatalogConfigurationId = "t_bar_row__machine__medium",
        )
        assertEquals("t_bar_row__machine__medium", restored.getValue("t_bar_row").catalogConfigurationId)
    }
}
