package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceInputTest {

    @Test
    fun parses_reps_weight_and_rir_in_natural_spoken_order() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "5 repeticiones 50 kilos rir 1",
            isTimeMode = false,
            isUnilateral = false,
        )

        assertEquals(5, result?.metricValue)
        assertEquals(50.0, result?.weightKg ?: 0.0, 0.0)
        assertEquals(1.0, result?.intensityValue ?: 0.0, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RIR, result?.intensityKind)
    }
    @Test
    fun parses_connected_weight_metric_and_rpe() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "80 por 8 rpe 9",
            isTimeMode = false,
            isUnilateral = false,
        )

        assertEquals(80.0, result?.weightKg ?: 0.0, 0.0)
        assertEquals(8, result?.metricValue)
        assertEquals(9.0, result?.intensityValue ?: 0.0, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RPE, result?.intensityKind)
        assertTrue(result?.fields?.contains(WorkoutVoiceField.WEIGHT) == true)
        assertTrue(result?.fields?.contains(WorkoutVoiceField.VALUE) == true)
        assertTrue(result?.fields?.contains(WorkoutVoiceField.INTENSITY) == true)
    }

    @Test
    fun parses_time_side_and_failure() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "45 segundos izquierda fallo",
            isTimeMode = true,
            isUnilateral = true,
        )

        assertEquals(45, result?.metricValue)
        assertEquals("left", result?.side)
        assertEquals(true, result?.reachedFailure)
        assertTrue(result?.fields?.contains(WorkoutVoiceField.VALUE) == true)
        assertTrue(result?.fields?.contains(WorkoutVoiceField.SIDE) == true)
        assertTrue(result?.fields?.contains(WorkoutVoiceField.FAILURE) == true)
    }

    @Test
    fun parses_decimal_weight_with_keywords() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "20.5 kilos 6 reps",
            isTimeMode = false,
            isUnilateral = false,
        )

        assertEquals(20.5, result?.weightKg ?: 0.0, 0.0)
        assertEquals(6, result?.metricValue)
    }

    @Test
    fun ignores_plain_transcript_without_safe_fields() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "me senti fuerte",
            isTimeMode = false,
            isUnilateral = false,
        )

        assertNull(result)
    }

    @Test
    fun converts_rir_to_rpe_text_for_draft() {
        val interpretation = WorkoutVoiceInterpretation(
            transcript = "rir 2",
            intensityValue = 2.0,
            intensityKind = WorkoutVoiceIntensityKind.RIR,
            fields = setOf(WorkoutVoiceField.INTENSITY),
        )

        assertEquals("8", workoutVoiceIntensityText(interpretation, IntensityMode.RPE))
        assertEquals("2", workoutVoiceIntensityText(interpretation, IntensityMode.RIR))
    }

    @Test
    fun parses_weight_before_keyword_and_unilateral_side() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "12 kilos derecha 10 reps",
            isTimeMode = false,
            isUnilateral = true,
        )

        assertEquals(12.0, result?.weightKg ?: 0.0, 0.0)
        assertEquals(10, result?.metricValue)
        assertEquals("right", result?.side)
    }

    @Test
    fun parses_rir_synonym_from_reserve_language() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "100 por 5 con 2 reservas",
            isTimeMode = false,
            isUnilateral = false,
        )

        assertEquals(100.0, result?.weightKg ?: 0.0, 0.0)
        assertEquals(5, result?.metricValue)
        assertEquals(2.0, result?.intensityValue ?: 0.0, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RIR, result?.intensityKind)
    }

    @Test
    fun parses_minutes_into_seconds() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "2 minutos",
            isTimeMode = true,
            isUnilateral = false,
        )

        assertEquals(120, result?.metricValue)
    }

    @Test
    fun time_mode_connector_does_not_force_weight() {
        val result = parseWorkoutVoiceTranscript(
            transcript = "30 por 15",
            isTimeMode = true,
            isUnilateral = false,
        )

        assertEquals(15, result?.metricValue)
        assertFalse(result?.fields?.contains(WorkoutVoiceField.WEIGHT) == true)
    }

    @Test
    fun parses_vosk_intensity_aliases() {
        val rpe = parseWorkoutVoiceTranscript("80 por 8 esfuerzo 9", false, false)
        val rir = parseWorkoutVoiceTranscript("80 por 8 reservas 2", false, false)
        assertEquals(WorkoutVoiceIntensityKind.RPE, rpe?.intensityKind)
        assertEquals(9.0, rpe?.intensityValue ?: 0.0, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RIR, rir?.intensityKind)
        assertEquals(2.0, rir?.intensityValue ?: 0.0, 0.0)
    }

    @Test
    fun parses_decimal_distance_and_custom_unit() {
        val distance = parseWorkoutVoiceTranscript("2.5 kilometros", false, false, UnitModeV2.DISTANCE)
        val custom = parseWorkoutVoiceTranscript("20 calorias", false, false, UnitModeV2.CUSTOM, "calorias")
        assertEquals(2.5, distance?.resolvedMetricValue ?: 0.0, 0.0)
        assertEquals(20.0, custom?.resolvedMetricValue ?: 0.0, 0.0)
    }

    @Test
    fun rom_is_only_accepted_when_exercise_tracks_it() {
        val enabled = parseWorkoutVoiceTranscript("80 por 8 rango 75", false, false, trackRom = true)
        val disabled = parseWorkoutVoiceTranscript("80 por 8 rango 75", false, false, trackRom = false)
        assertEquals(75, enabled?.romPercent)
        assertTrue(enabled?.fields?.contains(WorkoutVoiceField.ROM) == true)
        assertNull(disabled?.romPercent)
    }
}
