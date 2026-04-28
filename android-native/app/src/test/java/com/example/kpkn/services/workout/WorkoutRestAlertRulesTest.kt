package com.example.kpkn.services.workout

import com.example.kpkn.data.models.HapticIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestAlertRulesTest {

    @Test
    fun prealert_is_only_scheduled_when_rest_is_long_enough() {
        assertFalse(shouldScheduleWorkoutRestPrealert(3))
        assertFalse(shouldScheduleWorkoutRestPrealert(2))
        assertTrue(shouldScheduleWorkoutRestPrealert(8))
    }

    @Test
    fun prealert_trigger_is_three_seconds_before_end() {
        assertEquals(27_000L, workoutRestPrealertTriggerAt(endAtMs = 30_000L))
    }

    @Test
    fun recent_audio_failure_only_counts_inside_window() {
        assertTrue(isRecentWorkoutRestAudioFailure(lastFailureAtMs = 1_000L, nowMs = 5_000L, windowMs = 10_000L))
        assertFalse(isRecentWorkoutRestAudioFailure(lastFailureAtMs = 1_000L, nowMs = 15_500L, windowMs = 10_000L))
        assertFalse(isRecentWorkoutRestAudioFailure(lastFailureAtMs = 0L, nowMs = 5_000L, windowMs = 10_000L))
    }

    @Test
    fun completion_vibration_grows_with_intensity() {
        val light = workoutCompletionVibrationPattern(HapticIntensity.LIGHT)
        val strong = workoutCompletionVibrationPattern(HapticIntensity.STRONG)

        assertEquals(6, light.size)
        assertEquals(6, strong.size)
        assertTrue(strong.last() > light.last())
    }

    @Test
    fun completion_tone_plan_uses_three_beeps() {
        val plan = workoutCompletionTonePlan()

        assertEquals(3, plan.size)
        assertTrue(plan[2].durationMs > plan[0].durationMs)
    }
}
