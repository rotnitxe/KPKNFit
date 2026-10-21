package com.example.kpkn.data.media

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.UnilateralMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMediaSidesTest {

    private fun sampleExercise(unilateral: Boolean): Exercise = Exercise(
        id = "ex-1",
        name = "Ejercicio de prueba",
        unilateralMode = if (unilateral) UnilateralMode.UNILATERAL_PAIRED else UnilateralMode.BILATERAL,
        isUnilateral = unilateral,
    )

    @Test
    fun bilateralNormalizesToNull() {
        val bilateral = sampleExercise(unilateral = false)
        assertNull(WorkoutMediaSides.normalize("L", bilateral))
        assertNull(WorkoutMediaSides.normalize("left", bilateral))
        assertNull(WorkoutMediaSides.normalize("R", bilateral))
        assertNull(WorkoutMediaSides.normalize("right", bilateral))
        assertNull(WorkoutMediaSides.normalize("anything", bilateral))
        assertNull(WorkoutMediaSides.normalize(null, bilateral))

        assertNull(WorkoutMediaSides.normalize("L", isUnilateral = false))
        assertNull(WorkoutMediaSides.normalize("left", isUnilateral = false))
    }

    @Test
    fun unilateralNormalizesLeftAndRight() {
        val unilateral = sampleExercise(unilateral = true)
        assertEquals("left", WorkoutMediaSides.normalize("L", unilateral))
        assertEquals("left", WorkoutMediaSides.normalize("l", unilateral))
        assertEquals("left", WorkoutMediaSides.normalize("left", unilateral))
        assertEquals("left", WorkoutMediaSides.normalize(" LEFT ", unilateral))

        assertEquals("right", WorkoutMediaSides.normalize("R", unilateral))
        assertEquals("right", WorkoutMediaSides.normalize("r", unilateral))
        assertEquals("right", WorkoutMediaSides.normalize("right", unilateral))
        assertEquals("right", WorkoutMediaSides.normalize(" RIGHT ", unilateral))

        assertNull(WorkoutMediaSides.normalize(null, unilateral))
        assertNull(WorkoutMediaSides.normalize("", unilateral))
        assertNull(WorkoutMediaSides.normalize("   ", unilateral))

        assertEquals("left", WorkoutMediaSides.normalize("L", isUnilateral = true))
        assertEquals("right", WorkoutMediaSides.normalize("right", isUnilateral = true))
    }

    @Test
    fun tokenReturnsUppercaseInitialOrNull() {
        assertEquals("L", WorkoutMediaSides.token("L"))
        assertEquals("L", WorkoutMediaSides.token("l"))
        assertEquals("L", WorkoutMediaSides.token("left"))
        assertEquals("L", WorkoutMediaSides.token("LEFT"))

        assertEquals("R", WorkoutMediaSides.token("R"))
        assertEquals("R", WorkoutMediaSides.token("r"))
        assertEquals("R", WorkoutMediaSides.token("right"))
        assertEquals("R", WorkoutMediaSides.token("RIGHT"))

        assertEquals("C", WorkoutMediaSides.token("custom"))
        assertNull(WorkoutMediaSides.token(null))
        assertNull(WorkoutMediaSides.token(""))
        assertNull(WorkoutMediaSides.token("  "))
    }
}
