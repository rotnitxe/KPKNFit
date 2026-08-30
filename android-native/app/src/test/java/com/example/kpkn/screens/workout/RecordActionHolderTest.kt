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
    fun shouldShowWorkoutRecordFab_trueForStrengthSet() {
        assertTrue(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = false,
                showingPostExerciseCard = false,
                isWarmupOverlayActive = false,
                isMobilityOverlayActive = false,
                showReadinessSheet = false,
                showRestOverlayHost = false,
            ),
        )
    }

    @Test
    fun shouldShowWorkoutRecordFab_falseWhenCardio() {
        assertFalse(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = true,
                showingPostExerciseCard = false,
                isWarmupOverlayActive = false,
                isMobilityOverlayActive = false,
                showReadinessSheet = false,
                showRestOverlayHost = false,
            ),
        )
    }

    @Test
    fun shouldShowWorkoutRecordFab_falseWhenWarmupOrMobilityOrOverlays() {
        val base = shouldShowWorkoutRecordFab(
            hasCurrentExercise = true,
            hasCurrentSet = true,
            isCardio = false,
            showingPostExerciseCard = false,
            isWarmupOverlayActive = false,
            isMobilityOverlayActive = false,
            showReadinessSheet = false,
            showRestOverlayHost = false,
        )
        assertTrue(base)

        assertFalse(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = false,
                showingPostExerciseCard = false,
                isWarmupOverlayActive = true,
                isMobilityOverlayActive = false,
                showReadinessSheet = false,
                showRestOverlayHost = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = false,
                showingPostExerciseCard = false,
                isWarmupOverlayActive = false,
                isMobilityOverlayActive = true,
                showReadinessSheet = false,
                showRestOverlayHost = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = false,
                showingPostExerciseCard = true,
                isWarmupOverlayActive = false,
                isMobilityOverlayActive = false,
                showReadinessSheet = false,
                showRestOverlayHost = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = false,
                showingPostExerciseCard = false,
                isWarmupOverlayActive = false,
                isMobilityOverlayActive = false,
                showReadinessSheet = true,
                showRestOverlayHost = false,
            ),
        )
        assertFalse(
            shouldShowWorkoutRecordFab(
                hasCurrentExercise = true,
                hasCurrentSet = true,
                isCardio = false,
                showingPostExerciseCard = false,
                isWarmupOverlayActive = false,
                isMobilityOverlayActive = false,
                showReadinessSheet = false,
                showRestOverlayHost = true,
            ),
        )
    }
}
