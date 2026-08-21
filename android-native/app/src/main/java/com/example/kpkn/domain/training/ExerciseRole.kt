package com.example.kpkn.domain.training

import kotlinx.serialization.Serializable

/**
 * Roles de ejercicio generalizados (Fase 4 — plan maestro).
 * Diferencia competencia, variante técnica, suplementario y accesorio.
 */
@Serializable
enum class ExerciseRole {
    COMPETITION_SQUAT,
    COMPETITION_BENCH,
    COMPETITION_DEADLIFT,
    PRIMARY_COMPOUND,
    TECHNIQUE_VARIANT,
    SUPPLEMENTAL,
    ACCESSORY,
    ISOLATION,
    CARDIO,
    MOBILITY,
}

fun ExerciseRole.isCompetition(): Boolean = this in setOf(
    ExerciseRole.COMPETITION_SQUAT,
    ExerciseRole.COMPETITION_BENCH,
    ExerciseRole.COMPETITION_DEADLIFT,
)
