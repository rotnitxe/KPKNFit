package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class ExerciseCatalogV2LoaderTest {
    @Test
    fun draft_catalog_cannot_be_loaded_as_runtime_catalog() {
        val draft = Json.encodeToString(
            ExerciseCatalogV2.serializer(),
            ExerciseCatalogV2(
                schemaVersion = 2,
                catalogRevision = "draft",
                ontologyRevision = "draft",
                families = listOf(
                    ExerciseFamilyV2(
                        id = "test_family",
                        canonicalName = "Familia de prueba",
                        description = "Familia de prueba para verificar el gate de aprobación.",
                        definitions = emptyList(),
                        evidence = CatalogEvidenceV2(
                            reviewStatus = CatalogReviewStatusV2.DRAFT,
                            confidence = CatalogConfidenceV2.LOW,
                            evidenceRefs = listOf("test"),
                        ),
                    ),
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ExerciseCatalogV2Loader.decodeApproved(draft)
        }
    }
}
