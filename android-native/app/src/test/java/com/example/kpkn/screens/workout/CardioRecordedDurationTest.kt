package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class CardioRecordedDurationTest {
    @Test fun pausedGpsConfirmationUsesTheSameClockAsTheSavedTrack() {
        // Physical pause/resume: timer rounded to 152, GPS active segments total 151.
        assertEquals(151, cardioRecordedDurationSeconds(true, 151L, 152))
    }

    @Test fun manualCardioKeepsItsOwnTimer() {
        assertEquals(152, cardioRecordedDurationSeconds(false, 151L, 152))
    }

    @Test fun unavailableGpsFallsBackToActualTimerWithoutUsingPrescription() {
        assertEquals(152, cardioRecordedDurationSeconds(true, null, 152))
        assertEquals(0, cardioRecordedDurationSeconds(true, 0L, 0))
    }
}
