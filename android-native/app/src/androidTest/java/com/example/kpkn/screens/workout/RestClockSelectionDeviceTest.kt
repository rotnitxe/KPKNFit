package com.example.kpkn.screens.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestClockSelectionDeviceTest {
    private val start = 1_792_170_000_123L

    @Test fun modernIdentitySurvivesElapsedTimeAndBothAdjustments() {
        val initial = WorkoutRestModalState(startedAtMs = start, endsAtMs = start + 90_000L, activeSeconds = 90)
        val afterThirtySecondsPlus15 = initial.copy(endsAtMs = start + 105_000L, activeSeconds = 75, isManualOverride = true)
        val laterMinus15 = afterThirtySecondsPlus15.copy(endsAtMs = start + 90_000L, activeSeconds = 50)
        val shape = restClockShapeForEvent(initial)
        assertEquals(shape, restClockShapeForEvent(afterThirtySecondsPlus15))
        assertEquals(shape, restClockShapeForEvent(laterMinus15))
        assertEquals(shape, restClockShapeForEvent(laterMinus15.copy(exerciseName = "Siguiente ejercicio")))
    }

    @Test fun endFallbackMatchesTheEquivalentExplicitStart() {
        val legacy = WorkoutRestModalState(endsAtMs = start + 90_000L, activeSeconds = 90)
        assertEquals(start, legacy.restEventStartedAtMs())
        assertEquals(restClockShapeForEvent(legacy.copy(startedAtMs = start)), restClockShapeForEvent(legacy))
    }

    @Test fun legacyIdentityIsNormalizedBeforeRemainingTimeChanges() {
        val legacy = WorkoutRestModalState(endsAtMs = start + 90_000L, activeSeconds = 90, plannedSeconds = 90)
        val normalized = legacy.withRestEventIdentity()
        // This is the real addTime contract: activeSeconds becomes remaining + delta, not original duration + delta.
        val plus = normalized.copy(endsAtMs = start + 105_000L, activeSeconds = 75)
        val minus = plus.copy(endsAtMs = start + 90_000L, activeSeconds = 50)
        assertEquals(legacy.copy(startedAtMs = start), normalized)
        assertEquals(start, plus.restEventStartedAtMs())
        assertEquals(start, minus.restEventStartedAtMs())
        assertEquals(restClockShapeForEvent(legacy), restClockShapeForEvent(plus))
        assertEquals(restClockShapeForEvent(legacy), restClockShapeForEvent(minus))
    }

    @Test fun naturalOverrunKeepsSubsecondIdentityWithoutChangingDuration() {
        val rest = WorkoutRestModalState(startedAtMs = start, endsAtMs = start + 90_499L, activeSeconds = 60)
        val overrun = RestOverrunPolicy.captureOrNull(WorkoutUiState(restModalState = rest), start + 92_000L)
        assertNotNull(overrun)
        requireNotNull(overrun)
        assertEquals(start, overrun.startedAtMs)
        assertEquals(rest.endsAtMs, overrun.endedAtMs)
        assertEquals(90, overrun.totalSeconds)
        assertEquals(restClockShapeForEvent(rest), restClockShapeForEvent(null, overrun))
        assertEquals(restClockShapeForEvent(rest), restClockShapeForEvent(null, overrun.copy(exerciseIdx = 4, setIdx = 2)))
    }

    @Test fun legacyOverrunCanStillInferEndMinusTotal() {
        val legacy = WorkoutRestModalState(endsAtMs = start + 90_000L, activeSeconds = 90)
        val overrun = RestOverrun(endedAtMs = legacy.endsAtMs, totalSeconds = 90)
        assertEquals(restClockShapeForEvent(legacy), restClockShapeForEvent(null, overrun))
        assertEquals(restClockShapeForEvent(null), restClockShapeForEvent(null))
    }

    @Test fun distinctEventsUseTheAvailableShapesWithoutRequiringAlternation() {
        val choices = (0 until 128).map { index ->
            restClockShapeForEvent(WorkoutRestModalState(startedAtMs = start + index * 91_137L))
        }.toSet()
        assertEquals(RestClockShape.entries.toSet(), choices)
    }
}
