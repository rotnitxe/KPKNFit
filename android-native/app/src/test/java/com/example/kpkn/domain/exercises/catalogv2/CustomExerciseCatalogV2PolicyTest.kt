package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExerciseCatalogV2PolicyTest {
    @Test
    fun custom_ids_are_namespaced_and_normalized() {
        assertEquals("custom:my_press", CustomExerciseCatalogV2Policy.normalizeId(" Custom:My_Press "))
        assertThrows(IllegalArgumentException::class.java) {
            CustomExerciseCatalogV2Policy.normalizeId("my_press")
        }
    }

    @Test
    fun automation_requires_minimum_metadata_and_static_collisions_are_rejected() {
        assertTrue(CustomExerciseCatalogV2Policy.canUseForAutomation(hasMinimumMetadata = true))
        assertFalse(CustomExerciseCatalogV2Policy.canUseForAutomation(hasMinimumMetadata = false))
        assertThrows(IllegalArgumentException::class.java) {
            CustomExerciseCatalogV2Policy.validateCollision("custom:press", setOf("custom:press"))
        }
    }
}
