package com.example.kpkn.domain.workout

import kotlinx.serialization.Serializable

/**
 * Contrato del historial de Modo Voz.
 *
 * - Una entrada existe solo cuando el usuario confirma una interpretación de voz;
 *   nunca se guarda audio, solo la transcripción y los valores aplicados al borrador.
 * - [VoiceHistoryStatus.REVIEW] mientras la serie no se haya registrado;
 *   [VoiceHistoryStatus.REGISTERED] cuando el registro real ocurrió.
 * - Cada corrección incrementa [VoiceHistoryEntry.revision] y conserva [VoiceHistoryEntry.audit]
 *   con los valores anterior y nuevo para la vista «Antes: … · Ver cambios».
 * - Pausar o desactivar Modo Voz no borra el historial; solo la retención lo depura.
 */
@Serializable
enum class VoiceHistoryStatus { REVIEW, REGISTERED }

@Serializable
data class VoiceHistoryValues(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distanceKm: Double? = null,
    val averageHeartRate: Int? = null,
    val intensityValue: Double? = null,
    val intensityKind: String? = null,
    val reachedFailure: Boolean = false,
    val isFailedSet: Boolean = false,
    val helpedReps: Int? = null,
)

@Serializable
data class VoiceHistoryAudit(
    val previous: VoiceHistoryValues,
    val next: VoiceHistoryValues,
)

@Serializable
data class VoiceHistoryEntry(
    val id: String,
    val sessionKey: String,
    val exerciseId: String,
    val exerciseName: String,
    val setIdx: Int,
    val side: String? = null,
    val transcript: String,
    val values: VoiceHistoryValues = VoiceHistoryValues(),
    val status: VoiceHistoryStatus = VoiceHistoryStatus.REVIEW,
    val revision: Int = 1,
    val audit: VoiceHistoryAudit? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** Retención acordada: 30 días y como máximo 500 capturas por sesión, sin audio. */
object VoiceHistoryRetention {
    const val RETENTION_DAYS: Long = 30L
    const val MAX_ENTRIES_PER_SESSION: Int = 500
    const val RETENTION_MS: Long = RETENTION_DAYS * 24L * 60L * 60L * 1000L
}
