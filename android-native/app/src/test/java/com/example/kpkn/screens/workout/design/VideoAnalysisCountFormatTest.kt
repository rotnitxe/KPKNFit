package com.example.kpkn.screens.workout.design

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAnalysisCountFormatTest {
    @Test fun estimate_uses_exactly_one_non_breaking_space() {
        listOf(0, 7, 8, 100).forEach { count ->
            assertEquals("≈\u00A0$count", approxCount(count))
        }
    }

    @Test fun chip_visual_phrase_and_menu_description_share_the_estimate() {
        listOf(
            SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.MEDIA),
            SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.MEDIA),
        ).forEach { state ->
            val chip = workoutVideoChipContent(state)!!.text
            assertTrue(chip.startsWith("≈\u00A0"))
            assertTrue(visualModeAnalysisLine(state, 0).text.contains(chip))
            assertTrue(workoutVideoRepsText(state).contains(chip))
        }
    }
}
