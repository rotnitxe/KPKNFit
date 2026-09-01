package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostExerciseFeedbackUnresolvedTest {

    @Test
    fun legacy_feedback_without_still_present_keeps_reported_ids() {
        val fb = PostExerciseFeedback(
            exerciseId = "ex1",
            exerciseName = "Press",
            technicalQuality = 8,
            discomfortIds = listOf("shoulder_front"),
        )
        assertEquals(listOf("shoulder_front"), fb.unresolvedDiscomfortIds())
    }

    @Test
    fun none_sentinel_clears_unresolved() {
        val fb = PostExerciseFeedback(
            exerciseId = "ex1",
            exerciseName = "Press",
            technicalQuality = 8,
            discomfortIds = listOf("shoulder_front"),
            stillPresentDiscomfortIds = listOf("none"),
        )
        assertTrue(fb.unresolvedDiscomfortIds().isEmpty())
    }

    @Test
    fun still_present_subset_is_what_later_series_see() {
        val previous = PostExerciseFeedback(
            exerciseId = "ex1",
            exerciseName = "Press",
            technicalQuality = 8,
            discomfortIds = listOf("shoulder_front", "elbow"),
            stillPresentDiscomfortIds = listOf("elbow"),
        )
        val current = PostExerciseFeedback(
            exerciseId = "ex2",
            exerciseName = "Curl",
            technicalQuality = 8,
            discomfortIds = listOf("none"),
            stillPresentDiscomfortIds = listOf("none"),
        )
        assertEquals(listOf("elbow"), unresolvedDiscomfortIdsFrom(listOf(previous, current)))
    }
}
