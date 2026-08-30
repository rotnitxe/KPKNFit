package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordActionHolderTest {
    @Test
    fun isArmedTracksActionAssignment() {
        val holder = RecordActionHolder()
        assertFalse(holder.isArmed)

        holder.action = {}
        assertTrue(holder.isArmed)

        holder.action = null
        assertFalse(holder.isArmed)
    }

    @Test
    fun shouldShowWorkoutRecordFab_trueOnWorkingWarmupAndMobilityPages() {
        listOf(
            LivePageType.NORMAL,
            LivePageType.WARMUP,
            LivePageType.MOBILITY,
        ).forEach { pageType ->
            assertTrue(
                shouldShowWorkoutRecordFab(
                    pageType = pageType,
                    showingPostExerciseCard = false,
                    workingRestActive = false,
                    isCardio = false,
                ),
            )
        }
    }

    @Test
    fun shouldShowWorkoutRecordFab_falseOnRestCardioOrOverlays() {
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.REST,
                showingPostExerciseCard = false,
                workingRestActive = false,
                isCardio = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.CARDIO,
                showingPostExerciseCard = false,
                workingRestActive = false,
                isCardio = true,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.NORMAL,
                showingPostExerciseCard = true,
                workingRestActive = false,
                isCardio = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                pageType = LivePageType.NORMAL,
                showingPostExerciseCard = false,
                workingRestActive = true,
                isCardio = false,
            ),
        )
    }
}
