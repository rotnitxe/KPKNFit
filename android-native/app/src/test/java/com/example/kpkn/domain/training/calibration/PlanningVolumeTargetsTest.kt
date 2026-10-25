package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.programs.KpknMuscleGroup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlanningVolumeTargetsTest {
    private val chosen = PlanningVolumeTarget(KpknMuscleGroup.CHEST, 10, 26, 123L, "Elección personal")
    @Test fun explicit_range_round_trips_without_turning_it_into_a_derived_score() {
        assertEquals(chosen, Json.decodeFromString<PlanningVolumeTarget>(Json.encodeToString(chosen)))
        val bound = PlanningVolumeTargets.asPlanningBounds(listOf(chosen)).single()
        assertEquals("pectorales", bound.muscleGroup)
        assertEquals(10, bound.minEffectiveVolume)
        assertEquals(26, bound.maxRecoverableVolume)
        assertTrue(bound.maxAdaptiveVolume in 10..26)
    }
    @Test fun unknown_is_empty_and_invalid_or_duplicate_ranges_are_rejected() {
        assertTrue(PlanningVolumeTargets.asPlanningBounds(emptyList()).isEmpty())
        assertFalse(PlanningVolumeTargets.valid(listOf(chosen.copy(minSets = -1))))
        assertFalse(PlanningVolumeTargets.valid(listOf(chosen.copy(minSets = 27))))
        assertFalse(PlanningVolumeTargets.valid(listOf(chosen, chosen)))
        assertTrue(PlanningVolumeTargets.valid(listOf(chosen.copy(minSets = 0, maxSets = 0))))
    }
    @Test fun program_defaults_and_original_notes_are_backward_compatible() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; coerceInputValues = true }
        val old = json.decodeFromString<com.example.kpkn.data.models.Program>("""{"id":"p","name":"Plan"}""")
        assertTrue(old.planningVolumeTargets.isEmpty())
        assertEquals("", old.volumeCalibrationNotes)
        val updated = old.copy(planningVolumeTargets = listOf(chosen), volumeCalibrationNotes = "Nota original")
        assertEquals(updated, json.decodeFromString<com.example.kpkn.data.models.Program>(json.encodeToString(updated)))
        assertNull(updated.athleteProfileScore)
        assertTrue(updated.volumeRecommendations.isEmpty())
    }
}
