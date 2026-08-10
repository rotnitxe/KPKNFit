package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioCalorieTargetTest {
    @Test
    fun targetUsesProgrammedDuration() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intensity = CardioIntensity.MEDIA,
            targetDurationSeconds = 1_800,
        )

        val target = CardioCalorieTargetEngine.estimate(details, bodyWeightKg = 80.0)
        val halfDuration = com.example.kpkn.domain.calculations.CardioCalorieEngine.estimate(
            com.example.kpkn.domain.calculations.CardioCalorieInput(details, 80.0, 900),
        )

        assertTrue(target != null)
        assertEquals(halfDuration * 2.0, target!!, 0.0001)
    }
}
