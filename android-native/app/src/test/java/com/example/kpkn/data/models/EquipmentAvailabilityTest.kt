package com.example.kpkn.data.models

import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentAvailabilityTest {
    @Test
    fun categorical_projection_is_closed_exhaustive_and_never_invents_inventory_tokens() {
        val expected = mapOf(
            EquipmentCategory.BARBELL to "barbell",
            EquipmentCategory.DUMBBELLS to "dumbbells",
            EquipmentCategory.KETTLEBELL to "kettlebell",
            EquipmentCategory.MACHINES to "machine",
            EquipmentCategory.CABLE to "cable",
            EquipmentCategory.SMITH_MACHINE to "smith_machine",
            EquipmentCategory.BAND to "band",
            EquipmentCategory.SUPPORT to "support",
            EquipmentCategory.PULL_UP_BAR to "pull_up_bar",
            EquipmentCategory.BALL to "ball",
            EquipmentCategory.CARDIO to "cardio",
        )
        assertEquals(expected.keys, EquipmentCategory.entries.toSet())
        expected.forEach { (category, token) ->
            assertEquals(
                setOf("bodyweight", token),
                TrainingOptions(availability = EquipmentAvailability(setOf(category)))
                    .effectiveEquipment(setOf("general_gym", "free_weights")),
            )
        }

        val allTokens = expected.values.toSet()
        val all = TrainingOptions(
            availability = EquipmentAvailability(EquipmentCategory.entries.toSet()),
        ).effectiveEquipment(setOf("general_gym", "free_weights", "machine_config:invented"))
        assertEquals(setOf("bodyweight") + allTokens, all)
        assertFalse("No blanket gym token", "general_gym" in all)
        assertFalse("No free-weight umbrella token", "free_weights" in all)
        assertFalse("No inferred machine configuration", all.any { it.startsWith("machine_config:") })
    }

    @Test
    fun null_and_explicit_empty_settings_availability_are_distinct_in_json() {
        val json = Json { encodeDefaults = true }
        val oldSettings = json.decodeFromString<Settings>("{}")
        val explicitEmpty = Settings(equipmentAvailability = EquipmentAvailability())
        val roundTrip = json.decodeFromString<Settings>(json.encodeToString(explicitEmpty))

        assertNull(oldSettings.equipmentAvailability)
        assertNotEquals(oldSettings.equipmentAvailability, roundTrip.equipmentAvailability)
        assertEquals(EquipmentAvailability(emptySet()), roundTrip.equipmentAvailability)
        assertTrue(json.encodeToString(explicitEmpty).contains("\"categories\":[]"))
    }
}
