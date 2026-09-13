package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.domain.workout.WorkoutTagResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkoutTagDuplicateTest {

    @Test
    fun duplicateDetectionIsCaseAndAccentInsensitive() {
        val existing = listOf(
            WorkoutTag(id = "1", name = "Máquina A", exerciseKey = "bench"),
            WorkoutTag(id = "2", name = "Default", exerciseKey = "bench", isDefault = true),
        )
        val clash = existing.firstOrNull { WorkoutTagResolver.namesMatch(it.name, "maquina  a") }
        assertNotNull(clash)
        assertEquals("Máquina A", clash!!.name)
        assertNotNull(existing.firstOrNull { WorkoutTagResolver.namesMatch(it.name, "default") })
    }
}
