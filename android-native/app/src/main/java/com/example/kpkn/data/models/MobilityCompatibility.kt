package com.example.kpkn.data.models

/**
 * Applies the mobility contract at a boundary where a session is loaded or
 * edited. Keeping this as an explicit normalization step lets old JSON keep
 * decoding with kotlinx.serialization's normal defaults.
 */
fun MobilitySeries.normalizeForCompatibility(): MobilitySeries {
    val normalizedReps = reps?.takeIf { it.isNotBlank() }
    val normalizedDuration = durationSeconds?.coerceAtLeast(1)
    val effectiveUnit = unit ?: if (normalizedDuration != null) MobilityUnit.SECONDS else MobilityUnit.REPS
    return if (effectiveUnit == MobilityUnit.SECONDS && normalizedDuration != null) {
        copy(
            unit = MobilityUnit.SECONDS,
            reps = null,
            durationSeconds = normalizedDuration,
            restBetweenSeconds = 0,
        )
    } else {
        copy(
            unit = MobilityUnit.REPS,
            reps = normalizedReps,
            durationSeconds = null,
            restBetweenSeconds = 0,
        )
    }
}

fun MobilitySeries.catalogIdentityKey(): String =
    catalogConfigurationId?.takeIf { it.isNotBlank() } ?: id

fun MobilityConfig.normalizedForCompatibility(hasSeries: Boolean): MobilityConfig? {
    if (!hasSeries) return this
    return copy(
        // Surtido was a temporary editor mode. Legacy payloads are executed as
        // the current focused checklist while retaining the configured duration.
        mode = MobilityMode.ENFOCADO,
        totalMinutes = totalMinutes.coerceAtLeast(0),
    )
}

fun Session.normalizeMobilityCompatibility(): Session {
    fun normalizeExercise(exercise: Exercise): Exercise {
        val series = exercise.mobilitySeries.map(MobilitySeries::normalizeForCompatibility)
        return exercise.copy(
            mobilitySeries = series,
            mobilityConfig = exercise.mobilityConfig
                ?.normalizedForCompatibility(series.isNotEmpty())
                ?: series.takeIf { it.isNotEmpty() }?.let { MobilityConfig() },
        )
    }

    fun normalizePart(part: SessionPart): SessionPart {
        val series = part.mobilitySeries.map(MobilitySeries::normalizeForCompatibility)
        return part.copy(
            exercises = part.exercises.map(::normalizeExercise),
            mobilitySeries = series,
            mobilityConfig = part.mobilityConfig
                ?.normalizedForCompatibility(series.isNotEmpty())
                ?: series.takeIf { it.isNotEmpty() }?.let { MobilityConfig() },
        )
    }

    return copy(
        exercises = exercises.map(::normalizeExercise),
        parts = parts.map(::normalizePart),
        sessionB = sessionB?.normalizeMobilityCompatibility(),
        sessionC = sessionC?.normalizeMobilityCompatibility(),
        sessionD = sessionD?.normalizeMobilityCompatibility(),
        trainingBackup = trainingBackup?.copy(
            exercises = trainingBackup.exercises.map(::normalizeExercise),
            parts = trainingBackup.parts.map(::normalizePart),
        ),
    )
}
