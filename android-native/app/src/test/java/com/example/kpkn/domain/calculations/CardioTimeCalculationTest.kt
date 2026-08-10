package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioTimeCalculationTest {
    @Test
    fun embeddedCardioTargetIsCountedOnceWithoutExerciseSets() {
        val cardio = Exercise(
            id = "cardio-1",
            name = "Cinta de correr",
            cardioDetails = CardioDetails(
                type = CardioType.TREADMILL,
                targetDurationSeconds = 1_200,
            ),
            // Legacy sessions can contain strength-shaped rows; cardio must
            // remain one continuous block regardless of that stale payload.
            sets = listOf(
                ExerciseSet(id = "legacy-cardio-set-1", targetDuration = 30),
                ExerciseSet(id = "legacy-cardio-set-2", targetDuration = 30),
            ),
        )

        val breakdown = calculateSessionTimeBreakdown(
            exercises = Session("s1", "Cardio", exercises = listOf(cardio)).allExercises(),
            supersetGroups = emptyList(),
        )

        assertEquals(1_200, breakdown.executionSeconds)
        assertEquals(0, breakdown.restSeconds)
        assertEquals(0, breakdown.totalSetCount)
    }

    @Test
    fun mobilitySeriesCountsDurationWithoutRestBetweenConfiguredSets() {
        val exercise = Exercise(
            id = "squat-1",
            name = "Sentadilla",
            mobilitySeries = listOf(
                MobilitySeries(
                    id = "ankle-1",
                    name = "Movilidad de tobillo",
                    sets = 3,
                    durationSeconds = 20,
                    restBetweenSeconds = 15,
                ),
            ),
        )

        val breakdown = calculateSessionTimeBreakdown(
            exercises = Session("s1", "Movilidad", exercises = listOf(exercise)).allExercises(),
            supersetGroups = emptyList(),
        )

        assertEquals(60, breakdown.warmupSeconds)
    }
}
