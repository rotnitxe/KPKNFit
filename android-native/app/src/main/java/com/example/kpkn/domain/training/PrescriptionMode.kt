package com.example.kpkn.domain.training

import kotlinx.serialization.Serializable

/**
 * Modo de prescripción explícito (Fase 4 — plan maestro).
 * Nunca inferir el modo desde la etiqueta del día; siempre viene de la receta.
 */
@Serializable
enum class PrescriptionMode {
    PERCENT_1RM,
    PERCENT_TRAINING_MAX,
    RPE,
    RIR,
    DAILY_MAX,
    VELOCITY,
    FIXED_SET_REP,
}

fun PrescriptionMode.isPercentBased(): Boolean = this == PrescriptionMode.PERCENT_1RM || this == PrescriptionMode.PERCENT_TRAINING_MAX
