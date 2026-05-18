package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PrReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLoadModeSuggestionTest {

    @Test
    fun assistedLoadIncreasesAssistanceForHigherRepTargets() {
        val exercise = Exercise(
            id = "pullup-assisted",
            name = "Dominada asistida",
            prFor1RM = PrReference(weight = 20.0, reps = 5),
        )

        val heavierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-1", targetReps = 1, loadModeV2 = LoadModeV2.ASSISTED),
        )
        val easierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-2", targetReps = 10, loadModeV2 = LoadModeV2.ASSISTED),
        )

        assertTrue((heavierTarget ?: 0.0) < 20.0)
        assertTrue((easierTarget ?: 0.0) > 20.0)
    }

    @Test
    fun bodyweightLoadSuggestionIsZero() {
        val exercise = Exercise(
            id = "pullup-bodyweight",
            name = "Dominada",
            prFor1RM = PrReference(weight = 20.0, reps = 5),
        )

        val suggested = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-1", targetReps = 5, loadModeV2 = LoadModeV2.BODYWEIGHT),
        )

        assertEquals(0.0, suggested ?: -1.0, 0.0)
    }

    @Test
    fun ballastLoadKeepsPositiveProgression() {
        val exercise = Exercise(
            id = "pullup-weighted",
            name = "Dominada con lastre",
            prFor1RM = PrReference(weight = 20.0, reps = 5),
        )

        val heavierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-1", targetReps = 1, loadModeV2 = LoadModeV2.LASTRE),
        )
        val easierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-2", targetReps = 10, loadModeV2 = LoadModeV2.LASTRE),
        )

        assertTrue((heavierTarget ?: 0.0) > 20.0)
        assertTrue((easierTarget ?: 0.0) < 20.0)
    }
}
