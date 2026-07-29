package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVisualModelsTest {

    @Test
    fun header_group_prefers_user_session_part_name() {
        val label = resolveWorkoutHeaderGroupLabel(
            partName = "Pecho pesado",
            type = "Básico",
            category = "Fuerza",
        )

        assertEquals("Pecho pesado", label)
    }

    @Test
    fun header_group_falls_back_to_type_then_category() {
        assertEquals(
            "Básico",
            resolveWorkoutHeaderGroupLabel(partName = "Sesión", type = "Básico", category = "Fuerza"),
        )
        assertEquals(
            "Hipertrofia",
            resolveWorkoutHeaderGroupLabel(partName = "", type = "", category = "Hipertrofia"),
        )
    }

    @Test
    fun header_group_normalizes_principales_casing() {
        assertEquals(
            "PRINCIPALES",
            resolveWorkoutHeaderGroupLabel(partName = "PRINCIPALEs", type = null, category = null),
        )
        assertEquals(
            "PRINCIPALES",
            resolveWorkoutHeaderGroupLabel(partName = "PRINCIPALES", type = null, category = null),
        )
        assertEquals(
            "Principales",
            resolveWorkoutHeaderGroupLabel(partName = "principales", type = null, category = null),
        )
    }
}
