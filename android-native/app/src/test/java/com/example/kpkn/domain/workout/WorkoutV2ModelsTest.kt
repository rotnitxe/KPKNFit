package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.buildWorkoutContextKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutV2ModelsTest {

    @Test
    fun context_key_contains_expected_segments() {
        val key = buildWorkoutContextKey(
            exerciseId = "bench-press",
            machineBrand = "Matrix",
            tagId = "inclinado",
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
        )

        assertEquals("bench-press|Matrix|inclinado|na|LOAD|REPS", key)
    }

    @Test
    fun context_key_uses_safe_defaults() {
        val key = buildWorkoutContextKey(
            exerciseId = "",
            machineBrand = null,
            tagId = null,
            loadMode = LoadModeV2.ASSISTED,
            unitMode = UnitModeV2.TIME,
        )

        assertEquals("unknown|na|na|na|ASSISTED|TIME", key)
    }
}
