package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogRetiredKnowledgeTest {
    @Test
    fun runtime_asset_decodes_without_retired_note_or_tendon_keys() {
        val resource = java.io.File("src/main/assets/exercise_catalog_v2.json")
            .takeIf { it.exists() }
            ?: java.io.File("app/src/main/assets/exercise_catalog_v2.json")
        val payload = resource.readText()
        assertTrue("muscleNotes" !in payload)
        assertTrue("relevantTendons" !in payload)
        assertTrue("\"note\"" !in payload)
        val catalog = ExerciseCatalogV2Loader.decodeApproved(payload)
        val selection = catalog.families.first().definitions.first().let { definition ->
            ExerciseSelectionV2(definition.id, definition.configurations.first().id, catalog.catalogRevision)
        }
        val legacy = catalog.toLegacySelection(selection)
        assertTrue(legacy != null)
        assertTrue(legacy!!.involvedMuscles.all { it.biomechanicalReason == null })
    }
}
