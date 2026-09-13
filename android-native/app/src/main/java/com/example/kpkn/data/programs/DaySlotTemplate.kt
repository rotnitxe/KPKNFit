package com.example.kpkn.data.programs

import kotlinx.serialization.Serializable

@Serializable
enum class DaySlotTemplate {
    PL_SQUAT,
    PL_BENCH_HEAVY,
    PL_DEADLIFT,
    PL_BENCH_VOLUME,
    BB_PUSH,
    BB_PULL,
    BB_LEGS,
    BB_TORSO,
}
