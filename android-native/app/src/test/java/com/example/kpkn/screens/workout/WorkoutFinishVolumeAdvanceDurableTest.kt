package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.MuscleAdvance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFinishVolumeAdvanceDurableTest {

    @Test
    fun surplusKeepsOngoingUntilAck() {
        val deltas = listOf(
            MuscleAdvance(
                muscleId = "pecs",
                muscleName = "Pectorales",
                currentSets = 12.0,
                targetSets = 10.0,
                deficitSets = -2.0,
                targetSessionId = "next",
                targetSessionName = "Push 2",
            ),
        )
        assertTrue(shouldKeepOngoingForVolumeAdvance(deltas, volumeAdvanceHandled = false))
        assertFalse(shouldKeepOngoingForVolumeAdvance(deltas, volumeAdvanceHandled = true))
        assertFalse(shouldKeepOngoingForVolumeAdvance(emptyList<MuscleAdvance>(), volumeAdvanceHandled = false))
    }
}
