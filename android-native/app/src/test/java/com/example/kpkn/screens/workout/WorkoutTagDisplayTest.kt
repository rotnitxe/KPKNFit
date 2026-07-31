package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTagDisplayTest {

    @Test
    fun nameOnlyUsesPersistentSetupLabel() {
        assertEquals("Banco alto", workoutTagDisplayTitle("Banco alto", null))
    }

    @Test
    fun brandOnlyBecomesTheVisibleTagName() {
        assertEquals("Technogym", workoutTagDisplayTitle(null, "Technogym"))
    }

    @Test
    fun nameAndBrandAreShownOnceWhenEqual() {
        assertEquals("Technogym", workoutTagDisplayTitle("Technogym", "Technogym"))
        assertEquals("Banco alto · Technogym", workoutTagDisplayTitle("Banco alto", "Technogym"))
    }

    @Test
    fun internalUuidIsNeverShownAsTagName() {
        assertEquals("Technogym", workoutTagDisplayTitle("550e8400-e29b-41d4-a716-446655440000", "Technogym"))
        assertEquals("", workoutTagDisplayTitle("550e8400-e29b-41d4-a716-446655440000", null))
    }
}
