package com.example.kpkn.data.models

import com.example.kpkn.domain.training.EquipmentKeys
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

    @Test
    fun presence_of_a_key_is_tri_state_and_absence_beats_presence() {
        val availability = EquipmentAvailability(
            categories = setOf(EquipmentCategory.MACHINES),
            apparatus = mapOf(
                EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT,
                EquipmentKeys.HACK_SQUAT to ApparatusPresence.ABSENT,
                EquipmentKeys.LEG_EXTENSION to ApparatusPresence.UNKNOWN,
            ),
            supports = mapOf(
                EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
                // Contradicción entre mapas: la ausencia gana siempre.
                EquipmentKeys.HACK_SQUAT to ApparatusPresence.PRESENT,
            ),
        )

        assertEquals(ApparatusPresence.PRESENT, availability.presenceOf(EquipmentKeys.LEG_PRESS))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf(EquipmentKeys.HACK_SQUAT))
        assertEquals(ApparatusPresence.UNKNOWN, availability.presenceOf(EquipmentKeys.LEG_EXTENSION))
        assertEquals("Clave ausente = sin confirmar", ApparatusPresence.UNKNOWN, availability.presenceOf(EquipmentKeys.DUAL_CABLE))

        assertTrue(availability.hasExplicitPresence)
        assertFalse(EquipmentAvailability(setOf(EquipmentCategory.MACHINES)).hasExplicitPresence)
        assertFalse(EquipmentAvailability().hasExplicitPresence)
    }

    @Test
    fun presence_fields_round_trip_and_absent_fields_decode_to_empty_maps() {
        val json = Json { encodeDefaults = true }
        val declared = json.decodeFromString<EquipmentAvailability>(
            """
            {"categories":["MACHINES"],"apparatus":{"leg_press":"PRESENT","hack_squat":"ABSENT"},
             "supports":{"bench_flat":"PRESENT"}}
            """.trimIndent(),
        )
        assertEquals(setOf(EquipmentCategory.MACHINES), declared.categories)
        assertEquals(ApparatusPresence.PRESENT, declared.presenceOf(EquipmentKeys.LEG_PRESS))
        assertEquals(ApparatusPresence.ABSENT, declared.presenceOf(EquipmentKeys.HACK_SQUAT))
        assertEquals(ApparatusPresence.PRESENT, declared.presenceOf(EquipmentKeys.BENCH_FLAT))

        // Persistencia previa (§13.1): sin los campos nuevos decodifican `{}`.
        val legacy = json.decodeFromString<EquipmentAvailability>("""{"categories":["SUPPORT"]}""")
        assertEquals(emptyMap<String, ApparatusPresence>(), legacy.apparatus)
        assertEquals(emptyMap<String, ApparatusPresence>(), legacy.supports)
        assertEquals(ApparatusPresence.UNKNOWN, legacy.presenceOf(EquipmentKeys.BENCH_FLAT))
        assertFalse(legacy.hasExplicitPresence)

        assertEquals(declared, json.decodeFromString<EquipmentAvailability>(json.encodeToString(declared)))
        assertTrue(json.encodeToString(declared).contains("\"apparatus\""))
        assertTrue(json.encodeToString(declared).contains("\"supports\""))
    }
}
