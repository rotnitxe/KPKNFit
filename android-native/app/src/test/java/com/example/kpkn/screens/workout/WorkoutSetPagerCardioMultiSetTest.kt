package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSetPagerCardioMultiSetTest {
    @Test
    fun restPage_followsTheCardioSeriesThatWasRecorded() {
        val pages = listOf(
            WorkoutSetSwipePage(LivePageType.CARDIO, setIndex = 0, exerciseId = "run"),
            WorkoutSetSwipePage(LivePageType.CARDIO, setIndex = 1, exerciseId = "run"),
            WorkoutSetSwipePage(LivePageType.CARDIO, setIndex = 2, exerciseId = "run"),
        )

        assertEquals(1, restPageInsertIndex(pages, loggedKey = "run_0", anchorExerciseId = "run"))
        assertEquals(2, restPageInsertIndex(pages, loggedKey = "run_1", anchorExerciseId = "run"))
    }
}
