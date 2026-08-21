package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class SquatVariant { LOW_BAR, HIGH_BAR, FRONT_SQUAT }

@Serializable
enum class BenchVariant { BARBELL, DUMBBELL, MACHINE }

@Serializable
enum class DeadliftVariant { CONVENTIONAL, SUMO, HEX_BAR }

@Serializable
enum class PowerliftingModality { RAW, RAW_CLASSIC, EQUIPPED }

@Serializable
data class PowerliftingProfile(
    val squatVariant: SquatVariant = SquatVariant.LOW_BAR,
    val benchVariant: BenchVariant = BenchVariant.BARBELL,
    val deadliftVariant: DeadliftVariant = DeadliftVariant.CONVENTIONAL,
    val modality: PowerliftingModality = PowerliftingModality.RAW_CLASSIC,
    val squat1RM: Double? = null,
    val bench1RM: Double? = null,
    val deadlift1RM: Double? = null,
    val squatE1RM: Double? = null,
    val benchE1RM: Double? = null,
    val deadliftE1RM: Double? = null,
    val squatTM: Double? = null,
    val benchTM: Double? = null,
    val deadliftTM: Double? = null,
)
