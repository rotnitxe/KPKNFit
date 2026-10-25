package com.example.kpkn.domain.training.cycle

import kotlinx.serialization.Serializable

/** Respuestas originales, ambas de 1 a 4; no representan una medida de recuperación. */
@Serializable
data class MuscleCheckin(val muscleGroup: String, val performance: Int, val soreness: Int)

/** Opinión sobre la semana cerrada; [weekId] identifica la semana siguiente que se propone ajustar. */
@Serializable
data class WeeklyCheckin(
    val weekId: String,
    val cycleNumber: Int = 1,
    val answeredAtMs: Long = 0L,
    val muscles: List<MuscleCheckin> = emptyList(),
    val globalPerformance: Int? = null,
    val globalSoreness: Int? = null,
    val skipped: Boolean = false,
    val programRunId: String? = null,
    val feedbackWeekId: String? = null,
    val feedbackCycleNumber: Int? = null,
)

/** Petición para una ocurrencia concreta; no se reutiliza entre ejecuciones del programa. */
@Serializable
data class WeeklyCheckinRequest(
    val weekId: String,
    val cycleNumber: Int = 1,
    val muscleGroups: List<String> = emptyList(),
    val createdAtMs: Long = 0L,
    val programRunId: String? = null,
    val feedbackWeekId: String? = null,
    val feedbackCycleNumber: Int? = null,
)
