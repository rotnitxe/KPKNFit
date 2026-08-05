package com.example.kpkn.domain.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmphasisEngineTest {

    private fun emphasis(muscleId: String, definitionId: String, pattern: String? = null, options: Map<String, String> = emptyMap()) =
        EmphasisEngine.deriveEmphasis(muscleId, definitionId, pattern, options)

    // Pectoral
    @Test
    fun incline_press_emphasizes_superior_pectoral() {
        assertEquals("superior", emphasis("pectoralis", "incline_bench_press__barbell"))
    }

    @Test
    fun decline_press_emphasizes_inferior_pectoral() {
        assertEquals("inferior", emphasis("pectoralis", "decline_bench_press__barbell"))
    }

    @Test
    fun flat_press_emphasizes_medio_pectoral() {
        assertEquals("medio", emphasis("pectoralis", "bench_press__barbell"))
        assertEquals("medio", emphasis("pectoralis", "flat_chest_fly__dumbbells"))
    }

    @Test
    fun low_pulley_emphasizes_superior_pectoral() {
        assertEquals("superior", emphasis("pectoralis", "tren_superior_cruce_poleas", options = mapOf("pulley_height" to "low")))
    }

    @Test
    fun high_pulley_emphasizes_inferior_pectoral() {
        assertEquals("inferior", emphasis("pectoralis", "tren_superior_cruce_poleas", options = mapOf("pulley_height" to "high")))
    }

    @Test
    fun feet_elevated_pushup_emphasizes_superior_pectoral() {
        assertEquals("superior", emphasis("pectoralis", "push_up", options = mapOf("support_angle" to "feet_elevated")))
    }

    // Deltoid
    @Test
    fun lateral_raise_pattern_emphasizes_lateral_deltoid() {
        assertEquals("lateral", emphasis("deltoid", "seated_lateral_raise__dumbbells", pattern = "shoulder_abduction"))
        assertEquals("lateral", emphasis("deltoid", "standing_lateral_raise__dumbbells", pattern = "shoulder_abduction_full_rom"))
    }

    @Test
    fun face_pull_emphasizes_posterior_deltoid() {
        assertEquals("posterior", emphasis("deltoid", "deltoides_face_pull", pattern = "horizontal_pull"))
    }

    @Test
    fun reverse_fly_horizontal_abduction_emphasizes_posterior_deltoid() {
        assertEquals("posterior", emphasis("deltoid", "reverse_fly", pattern = "horizontal_abduction"))
    }

    @Test
    fun presses_emphasize_anterior_deltoid() {
        assertEquals("anterior", emphasis("deltoid", "bench_press__barbell", pattern = "horizontal_push"))
        assertEquals("anterior", emphasis("deltoid", "deltoides_press_landmine_unilateral", pattern = "vertical_push"))
    }

    // Glute
    @Test
    fun hip_extension_emphasizes_mayor_glute() {
        assertEquals("mayor", emphasis("gluteus_maximus", "hip_thrust__bilateral__barbell", pattern = "hip_extension"))
    }

    @Test
    fun gluteus_medius_emphasizes_medio_glute() {
        assertEquals("medio", emphasis("gluteus_medius", "cable_abduction", pattern = "hip_abduction"))
    }

    // Non-target muscles must not produce an emphasis.
    @Test
    fun non_target_muscle_has_no_emphasis() {
        assertNull(emphasis("biceps", "curl", pattern = "elbow_flexion"))
        assertNull(emphasis("quadriceps", "squat", pattern = "knee_dominant"))
        assertNull(emphasis("triceps", "bench_press__barbell", pattern = "horizontal_push"))
    }
}
