package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
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
        )

        val breakdown = calculateSessionTimeBreakdown(
            exercises = Session("s1", "Cardio", exercises = listOf(cardio)).allExercises(),
            supersetGroups = emptyList(),
        )

        assertEquals(1_200, breakdown.executionSeconds)
    }
}
