package com.example.kpkn.screens.workout

import kotlinx.serialization.Serializable

/*
 * STUBS DEL ARNÉS JVM: tipos de estado de pantalla que data/models/WorkoutLog.kt referencia en sus campos persistidos.
 * Sin campos: el dominio no los lee; solo necesitan existir y ser serializables (como objetos vacíos).
 */

@Serializable
class WorkoutSetDraft()

@Serializable
class PreparationReport()

@Serializable
class WorkoutRestModalState()

@Serializable
class FinishResumeSnapshot()

@Serializable
class GodModeUndoSnapshot()

/*
 * Copia del cuerpo puro de technicalQuality10ToPenaltyScale (screens/workout/WorkoutFeedbackModels.kt, archivo de UI que
 * no entra en el arnés). Mapea una calidad técnica 1–10 a la escala 1–5 de calculateTechniquePenalty.
 */
fun technicalQuality10ToPenaltyScale(technicalQuality: Int): Int {
    val q = technicalQuality.coerceIn(1, 10)
    return (1 + ((q - 1) * 4) / 9).coerceIn(1, 5)
}
