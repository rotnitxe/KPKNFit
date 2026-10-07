package com.example.kpkn.screens.onboarding.design.entreno.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El patrón de movimiento se deduce del NOMBRE del ejercicio (sin tildes ni mayúsculas) con reglas ordenadas: «press
 * militar» es empuje vertical aunque lleve «press», «sentadilla búlgara» es una zancada, lo desconocido lleva una barra.
 */
class ExercisePatternTest {

    private fun pattern(name: String) = exercisePatternOf(name)

    @Test
    fun theBigThreeAndTheirFamilies() {
        assertEquals(ExercisePattern.SQUAT, pattern("Sentadilla"))
        assertEquals(ExercisePattern.SQUAT, pattern("Sentadilla frontal"))
        assertEquals(ExercisePattern.SQUAT, pattern("Prensa de piernas"))
        assertEquals(ExercisePattern.PRESS_HORIZONTAL, pattern("Press banca"))
        assertEquals(ExercisePattern.PRESS_HORIZONTAL, pattern("Press banca inclinado"))
        assertEquals(ExercisePattern.HINGE, pattern("Peso muerto"))
        assertEquals(ExercisePattern.HINGE, pattern("Peso muerto rumano"))
        assertEquals(ExercisePattern.HINGE, pattern("Hip thrust"))
    }

    @Test
    fun overheadPressBeatsGenericPress() {
        assertEquals(ExercisePattern.PRESS_VERTICAL, pattern("Press militar"))
        assertEquals(ExercisePattern.PRESS_VERTICAL, pattern("Press de hombros con mancuernas"))
        assertEquals(ExercisePattern.PRESS_VERTICAL, pattern("Elevaciones laterales"))
        assertEquals(ExercisePattern.PRESS_HORIZONTAL, pattern("Press de pecho en máquina"))
    }

    @Test
    fun pullsAndRows() {
        assertEquals(ExercisePattern.PULL_VERTICAL, pattern("Dominadas"))
        assertEquals(ExercisePattern.PULL_VERTICAL, pattern("Jalón al pecho"))
        assertEquals(ExercisePattern.ROW, pattern("Remo con barra"))
        assertEquals(ExercisePattern.ROW, pattern("Remo en polea baja"))
        assertEquals(ExercisePattern.ROW, pattern("Face pull"))
    }

    @Test
    fun accentsAndCaseDoNotMatter() {
        assertEquals(ExercisePattern.PULL_VERTICAL, pattern("JALÓN AL PECHO"))
        assertEquals(ExercisePattern.ARMS, pattern("Curl de bíceps"))
        assertEquals(ExercisePattern.ARMS, pattern("EXTENSIÓN DE TRÍCEPS"))
        assertEquals("sentadilla bulgara", normalizeExerciseName("Sentadilla búlgara"))
    }

    @Test
    fun lungesCoreCarriesAndOlympicLifts() {
        assertEquals(ExercisePattern.LUNGE, pattern("Zancadas caminando"))
        assertEquals(ExercisePattern.LUNGE, pattern("Sentadilla búlgara"))
        assertEquals(ExercisePattern.CORE, pattern("Plancha"))
        assertEquals(ExercisePattern.CORE, pattern("Elevaciones de piernas colgado"))
        assertEquals(ExercisePattern.CARRY, pattern("Paseo del granjero"))
        assertEquals(ExercisePattern.OLYMPIC, pattern("Arranque"))
        assertEquals(ExercisePattern.OLYMPIC, pattern("Dos tiempos"))
    }

    @Test
    fun whatIsNotRecognizedIsGeneric() {
        assertEquals(ExercisePattern.GENERIC, pattern("Movilidad de tobillo"))
        assertEquals(ExercisePattern.GENERIC, pattern(""))
    }

    @Test
    fun everyPatternHasItsOwnGlyphAndLabel() {
        assertEquals(ExercisePattern.entries.size, ExercisePattern.entries.map { it.label }.toSet().size)
        for (pattern in ExercisePattern.entries) {
            val art = patternArt(pattern)
            assertTrue(art.width > 0f && art.height > 0f)
        }
    }
}
