package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExerciseCatalogV2PersistenceTest {
    private val selection = ExerciseSelectionV2("curl", "curl__standing", "v2")
    private val profile = ResolvedExerciseProfileV2(
        movementPatternId = "elbow_flexion",
        bodyRegion = ExerciseBodyRegionV2.UPPER,
        kineticChain = ExerciseKineticChainV2.ANTERIOR,
        laterality = ExerciseLateralityV2.BILATERAL,
        equipmentId = "dumbbells",
        loadMode = "free_external_load",
        primaryMuscles = listOf("biceps"),
        secondaryMuscles = emptyList(),
        stabilizerMuscles = emptyList(),
        efc = 2.0,
        cnc = 1.0,
        ssc = 0.0,
        ttc = 1.0,
        axialLoadFactor = 0.0,
        technicalDifficulty = 3.0,
        resistanceProfile = "gravity_arc",
        setupCues = listOf("Setup."),
        executionCues = listOf("Execute."),
        commonMistakes = listOf("Error."),
        performanceProfileId = "curl_free",
    )

    @Test
    fun planned_exercise_carries_exact_selection_and_occurrence() {
        val planned = Exercise(id = "occ-1", name = "Curl de bíceps")
            .withCatalogSelection(selection, profile, occurrenceId = "occ-1")

        val identity = planned.catalogSelectionOrNull()
        assertNotNull(identity)
        assertEquals(selection, identity!!.selection)
        assertEquals("curl_free", identity.performanceProfileId)
        assertEquals("occ-1", identity.occurrenceId)
    }

    @Test
    fun completed_exercise_keeps_an_immutable_profile_snapshot() {
        val completed = CompletedExercise(
            exerciseId = "occ-1",
            exerciseName = "Curl de bíceps",
        ).withResolvedCatalogSnapshot(selection, profile, occurrenceId = "occ-1", capturedAtEpochMs = 10L)

        val snapshot = completed.decodeResolvedCatalogSnapshot()
        assertNotNull(snapshot)
        assertEquals(selection, snapshot!!.selection)
        assertEquals("curl_free", snapshot.resolvedProfile.performanceProfileId)
        assertEquals(10L, snapshot.capturedAtEpochMs)
    }
}
