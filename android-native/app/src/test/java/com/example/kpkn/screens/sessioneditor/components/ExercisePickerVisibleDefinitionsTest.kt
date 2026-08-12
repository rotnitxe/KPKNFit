package com.example.kpkn.screens.sessioneditor.components

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
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchHitV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ExercisePickerVisibleDefinitionsTest {

    private fun definition(id: String, name: String, region: ExerciseBodyRegionV2 = ExerciseBodyRegionV2.LOWER) =
        ExerciseDefinitionV2(
            id = id,
            familyId = "family",
            kind = ExerciseDefinitionKindV2.PARENT,
            canonicalName = name,
            description = "Descripción de prueba para la definición del catálogo.",
            optionAxes = emptyList(),
            configurations = listOf(
                ExerciseConfigurationV2(
                    id = "${id}_config",
                    selectedOptions = emptyMap(),
                    displaySummary = id,
                    profile = ResolvedExerciseProfileV2(
                        movementPatternId = "pattern",
                        bodyRegion = region,
                        kineticChain = ExerciseKineticChainV2.ANTERIOR,
                        laterality = ExerciseLateralityV2.BILATERAL,
                        equipmentId = "test",
                        loadMode = "external",
                        primaryMuscles = listOf("muscle"),
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
                        performanceProfileId = "${id}_profile",
                    ),
                    evidence = CatalogEvidenceV2(
                        reviewStatus = CatalogReviewStatusV2.APPROVED,
                        confidence = CatalogConfidenceV2.HIGH,
                        evidenceRefs = listOf("test"),
                    ),
                ),
            ),
            defaultConfigurationId = "${id}_config",
            evidence = CatalogEvidenceV2(
                reviewStatus = CatalogReviewStatusV2.APPROVED,
                confidence = CatalogConfidenceV2.HIGH,
                evidenceRefs = listOf("test"),
            ),
        )

    private fun catalog() = ExerciseCatalogV2(
        schemaVersion = 2,
        catalogRevision = "test",
        ontologyRevision = "test",
        families = listOf(
            ExerciseFamilyV2(
                id = "family",
                canonicalName = "Familia",
                description = "Familia de prueba.",
                definitions = listOf(
                    definition("zebra", "Zebra"),
                    definition("alpha", "Alpha"),
                ),
                evidence = CatalogEvidenceV2(
                    reviewStatus = CatalogReviewStatusV2.APPROVED,
                    confidence = CatalogConfidenceV2.HIGH,
                    evidenceRefs = listOf("test"),
                ),
            ),
        ),
    )

    @Test
    fun unsettled_query_keeps_previous_stable_list_instead_of_emptying() {
        val catalog = catalog()
        val previous = catalog.families.flatMap { it.definitions }

        val visible = visibleDefinitionsForQuery(
            catalog = catalog,
            query = "sent",
            searchSettled = false,
            searchHits = emptyList(),
            filterRegion = null,
            filterMuscle = null,
            definitionsById = previous.associateBy { it.id },
            previousStable = previous,
        )

        assertSame(previous, visible)
    }

    @Test
    fun settled_query_uses_hits_in_relevance_order() {
        val catalog = catalog()
        val byId = catalog.families.flatMap { it.definitions }.associateBy { it.id }

        val visible = visibleDefinitionsForQuery(
            catalog = catalog,
            query = "alpha",
            searchSettled = true,
            searchHits = listOf(
                ExerciseSearchHitV2(definitionId = "alpha", score = 10),
                ExerciseSearchHitV2(definitionId = "zebra", score = 2),
            ),
            filterRegion = null,
            filterMuscle = null,
            definitionsById = byId,
            previousStable = emptyList(),
        )

        assertEquals(listOf("alpha", "zebra"), visible.map { it.id })
    }

    @Test
    fun blank_query_returns_full_alphabetical_list() {
        val catalog = catalog()
        val byId = catalog.families.flatMap { it.definitions }.associateBy { it.id }

        val visible = visibleDefinitionsForQuery(
            catalog = catalog,
            query = "",
            searchSettled = false,
            searchHits = emptyList(),
            filterRegion = null,
            filterMuscle = null,
            definitionsById = byId,
            previousStable = emptyList(),
        )

        assertEquals(listOf("alpha", "zebra"), visible.map { it.id })
    }
}
