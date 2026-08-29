package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class CarouselToneLogicTest {

    @Test
    fun repTone_inRange_isOnPlan() {
        val tone = carouselToneForRepEvaluation(
            RepRangeEvaluation(delta = 0.0, debt = 0.0, isInRange = true),
        )
        assertEquals(CarouselValueTone.OnPlan, tone)
    }

    @Test
    fun repTone_belowMin_isBelowPlan() {
        val tone = carouselToneForRepEvaluation(
            RepRangeEvaluation(delta = -2.0, debt = 2.0, isInRange = false),
        )
        assertEquals(CarouselValueTone.BelowPlan, tone)
    }

    @Test
    fun repTone_aboveMax_isAbovePlan() {
        val tone = carouselToneForRepEvaluation(
            RepRangeEvaluation(delta = 3.0, debt = 0.0, isInRange = false),
        )
        assertEquals(CarouselValueTone.AbovePlan, tone)
    }

    @Test
    fun intensityTone_equal_isOnPlan() {
        val tone = carouselToneForIntensityFeedback(
            isExecutionError = false,
            reachedFailure = false,
            difficultyLabel = "Igual",
            intensityDelta = 0.0,
        )
        assertEquals(CarouselValueTone.OnPlan, tone)
    }

    @Test
    fun intensityTone_easier_isBelowPlan() {
        val tone = carouselToneForIntensityFeedback(
            isExecutionError = false,
            reachedFailure = false,
            difficultyLabel = "Más fácil",
            intensityDelta = -1.0,
        )
        assertEquals(CarouselValueTone.BelowPlan, tone)
    }

    @Test
    fun intensityTone_harder_isAbovePlan() {
        val tone = carouselToneForIntensityFeedback(
            isExecutionError = false,
            reachedFailure = false,
            difficultyLabel = "Más difícil",
            intensityDelta = 1.0,
        )
        assertEquals(CarouselValueTone.AbovePlan, tone)
    }

    @Test
    fun intensityTone_failure_isBelowPlan() {
        val tone = carouselToneForIntensityFeedback(
            isExecutionError = false,
            reachedFailure = true,
            difficultyLabel = "Fallo alcanzado",
            intensityDelta = null,
        )
        assertEquals(CarouselValueTone.BelowPlan, tone)
    }

    @Test
    fun intensityTone_matchesPlanned_isOnPlan() {
        val tone = carouselToneForIntensityFeedback(
            isExecutionError = false,
            reachedFailure = false,
            difficultyLabel = null,
            intensityDelta = null,
            matchesPlanned = true,
        )
        assertEquals(CarouselValueTone.OnPlan, tone)
    }

    @Test
    fun intensityTone_executionError_isNeutral() {
        val tone = carouselToneForIntensityFeedback(
            isExecutionError = true,
            reachedFailure = false,
            difficultyLabel = null,
            intensityDelta = null,
        )
        assertEquals(CarouselValueTone.Neutral, tone)
    }
}
