package com.example.kpkn.data.db

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomExerciseEntityTest {

    @Test
    fun toEntity_preserves_created_at_on_update() {
        val info = ExerciseMuscleInfo(
            id = "custom:1",
            name = "Mi Ejercicio",
            isCustom = true,
        )
        val first = info.toEntity(nowIso = "2026-08-01T10:00:00Z")
        val updated = info.toEntity(
            nowIso = "2026-08-05T10:00:00Z",
            createdAt = first.createdAt,
        )
        assertEquals(first.createdAt, updated.createdAt)
        assertEquals("2026-08-05T10:00:00Z", updated.updatedAt)
    }
}
