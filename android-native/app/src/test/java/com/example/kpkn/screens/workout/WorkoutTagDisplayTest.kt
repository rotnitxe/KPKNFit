package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutContextProfile
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
        assertEquals("Banco alto · Technogym", workoutTagDisplayTitle("Banco alto · Technogym", "Technogym"))
    }

    @Test
    fun internalUuidIsNeverShownAsTagName() {
        assertEquals("Technogym", workoutTagDisplayTitle("550e8400-e29b-41d4-a716-446655440000", "Technogym"))
        assertEquals("", workoutTagDisplayTitle("550e8400-e29b-41d4-a716-446655440000", null))
    }

    @Test
    fun profilePersistsSetupLabelAndDisplaysBrandSeparately() {
        val profile = WorkoutContextProfile(
            id = "profile-1",
            exerciseKey = "exercise-1",
            tagId = "tag-1",
            setupLabel = "Banco alto",
            machineBrand = "Technogym",
        )
        assertEquals("Banco alto", profile.persistentTagName())
        assertEquals("Banco alto · Technogym", profile.tagDisplayTitle())
        assertEquals("Banco alto", profile.legacyTagName())
    }
}
