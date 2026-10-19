package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueCatalogContractsTest {
    private val exercise = ExerciseMuscleInfo("bench_press", "Press de banca", catalogDefinitionId = "bench_press", catalogConfigurationId = "bench_press__barbell")
    @Test fun techniqueAcceptsOneExactSelectionAndCancellation() {
        val request = CatalogLaunchRequest(origin = CatalogLaunchOrigin.TECHNIQUE, selectionMode = CatalogSelectionMode.SINGLE)
        assertTrue(CatalogResult.success(request, listOf(exercise)).isValidFor(request))
        assertTrue(CatalogResult.cancel(request).isValidFor(request))
        assertFalse(CatalogResult.success(request, listOf(exercise, exercise)).isValidFor(request))
        assertFalse(CatalogResult.success(request, listOf(exercise), CatalogCommitAction.CREATE_SUPERSET).isValidFor(request))
    }
    @Test fun techniqueRejectsOtherModesAndReplacementTargets() {
        val multiple = CatalogLaunchRequest(origin = CatalogLaunchOrigin.TECHNIQUE, selectionMode = CatalogSelectionMode.MULTIPLE)
        assertFalse(CatalogResult.success(multiple, listOf(exercise)).isValidFor(multiple))
        val targeted = multiple.copy(selectionMode = CatalogSelectionMode.SINGLE, targetExerciseId = "live-slot")
        assertFalse(CatalogResult.success(targeted, listOf(exercise)).isValidFor(targeted))
    }
}
