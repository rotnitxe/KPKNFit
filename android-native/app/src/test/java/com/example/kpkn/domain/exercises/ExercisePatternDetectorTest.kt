package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.exercises.ExercisePatternDetector.PatternConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExercisePatternDetectorTest {

    @Test
    fun detects_hinge_for_deadlift_variants() {
        val result = ExercisePatternDetector.detect("Peso muerto rumano con mancuernas")
        assertNotNull(result)
        assertEquals("hip_hinge", result?.patternId)
        assertEquals(PatternConfidence.HIGH, result?.confidence)
    }

    @Test
    fun detects_chest_fly_for_aperturas() {
        val result = ExercisePatternDetector.detect("Aperturas Planas")
        assertNotNull(result)
        assertEquals("horizontal_abduction", result?.patternId)
        assertEquals("Aperturas / Abducción horizontal", result?.label)
        assertEquals(PatternConfidence.HIGH, result?.confidence)
    }

    @Test
    fun detects_horizontal_push_for_bench_press() {
        val result = ExercisePatternDetector.detect("Press banca con barra")
        assertNotNull(result)
        assertEquals("horizontal_push", result?.patternId)
        assertEquals(PatternConfidence.HIGH, result?.confidence)
    }

    @Test
    fun detects_horizontal_pull_for_rows() {
        val result = ExercisePatternDetector.detect("Remo con barra")
        assertNotNull(result)
        assertEquals("horizontal_pull", result?.patternId)
    }

    @Test
    fun detects_knee_dominant_for_bulgarian_split_squat() {
        val result = ExercisePatternDetector.detect("Sentadilla búlgara")
        assertNotNull(result)
        assertEquals("knee_dominant", result?.patternId)
    }

    @Test
    fun detects_elbow_flexion_for_curls() {
        val result = ExercisePatternDetector.detect("Curl martillo con mancuernas")
        assertNotNull(result)
        assertEquals("elbow_flexion", result?.patternId)
    }

    @Test
    fun returns_null_for_unknown_custom_names() {
        assertNull(ExercisePatternDetector.detect("Kárate kick alfa"))
        assertNull(ExercisePatternDetector.detect(""))
    }
}
