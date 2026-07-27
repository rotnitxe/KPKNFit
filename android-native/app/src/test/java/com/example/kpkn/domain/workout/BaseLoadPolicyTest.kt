package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.WorkoutContextProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseLoadPolicyTest {

    @Test
    fun legacyBarWeightResolvesAsBase() {
        val profile = WorkoutContextProfile(
            id = "p1",
            exerciseKey = "bench",
            tagId = "smith",
            barWeightKg = 20.0,
        )
        assertEquals(20.0, BaseLoadPolicy.resolvedFromProfile(profile)!!, 0.001)
    }

    @Test
    fun baseLoadKgTakesPriorityOverLegacyBarWeight() {
        val profile = WorkoutContextProfile(
            id = "p1",
            exerciseKey = "bench",
            tagId = "smith",
            baseLoadKg = 25.0,
            barWeightKg = 20.0,
            setupDetails = ExerciseSetupDetails(barWeightKg = 15.0, baseLoadKg = null),
        )
        assertEquals(25.0, BaseLoadPolicy.resolvedFromProfile(profile)!!, 0.001)
    }

    @Test
    fun withoutTagFloorDoesNotApplyEvenIfExerciseHasSessionBase() {
        val exercise = Exercise(
            id = "ex",
            name = "Leg press",
            setupDetails = ExerciseSetupDetails(baseLoadKg = 20.0),
        )
        val display = BaseLoadPolicy.resolvedForDisplay(profile = null, exercise = exercise)
        assertEquals(20.0, display!!, 0.001)

        val floor = BaseLoadPolicy.floorForLoadSuggestion(
            loadMode = LoadModeV2.LOAD,
            activeTagId = null,
            engineSuggestedKg = 5.0,
            taggedProfileBaseLoadKg = display,
            tagDisplayName = null,
        )
        assertNull(floor)
    }

    @Test
    fun withTagAndBaseFloorsEngineSuggestion() {
        val floor = BaseLoadPolicy.floorForLoadSuggestion(
            loadMode = LoadModeV2.LOAD,
            activeTagId = "máquina-A",
            engineSuggestedKg = 5.0,
            taggedProfileBaseLoadKg = 20.0,
            tagDisplayName = "Máquina A",
        )
        assertTrue(floor != null)
        assertEquals(20.0, floor!!.suggestedWeight, 0.001)
        assertEquals("Carga base · Máquina A", floor.reason)
    }

    @Test
    fun floorIgnoredForLastreAndAssisted() {
        assertNull(
            BaseLoadPolicy.floorForLoadSuggestion(
                loadMode = LoadModeV2.LASTRE,
                activeTagId = "tag",
                engineSuggestedKg = 2.5,
                taggedProfileBaseLoadKg = 20.0,
            ),
        )
        assertNull(
            BaseLoadPolicy.floorForLoadSuggestion(
                loadMode = LoadModeV2.ASSISTED,
                activeTagId = "tag",
                engineSuggestedKg = 5.0,
                taggedProfileBaseLoadKg = 20.0,
            ),
        )
    }

    @Test
    fun floorDoesNotRaiseWhenEngineAlreadyAboveBase() {
        assertNull(
            BaseLoadPolicy.floorForLoadSuggestion(
                loadMode = LoadModeV2.LOAD,
                activeTagId = "tag",
                engineSuggestedKg = 40.0,
                taggedProfileBaseLoadKg = 20.0,
            ),
        )
    }

    @Test
    fun withMirroredBaseLoadWritesBothFields() {
        val setup = BaseLoadPolicy.withMirroredBaseLoad(ExerciseSetupDetails(), 22.5)
        assertEquals(22.5, setup.baseLoadKg!!, 0.001)
        assertEquals(22.5, setup.barWeightKg!!, 0.001)
    }
}
