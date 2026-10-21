package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Identidad estable de la ficha, sin filtros por etiqueta ni búsquedas aproximadas por nombre. */
fun exerciseFichaIdentityKeys(exercise: Exercise): Set<String> = listOfNotNull(
    exercise.canonicalExerciseId, exercise.exerciseDbId, exercise.catalogDefinitionId,
    exercise.catalogConfigurationId, exercise.exerciseId, exercise.id, exercise.resolvedCanonicalExerciseId(),
).map { it.trim().lowercase() }.filter { it.isNotBlank() && it != "unknown" }.toSet()

private fun CompletedExercise.fichaKeys(): Set<String> = listOfNotNull(
    canonicalExerciseId, exerciseDbId, catalogDefinitionId, catalogConfigurationId, exerciseId, resolvedCanonicalExerciseId(),
).map { it.trim().lowercase() }.filter { it.isNotBlank() && it != "unknown" }.toSet()

data class ExerciseHistorySummary(val bestSet: CompletedSet?, val estimatedRmKg: Double?, val lastDate: LocalDate)

/** La mejor serie se compara con el mismo e1RM homologado/híbrido que el rendimiento compartido del editor. */
fun exerciseHistorySummary(history: List<WorkoutLog>, identityKeys: Set<String>, zone: ZoneId = ZoneId.systemDefault()): ExerciseHistorySummary? {
    val keys = identityKeys.map { it.trim().lowercase() }.filter { it.isNotBlank() && it != "unknown" }.toSet()
    if (keys.isEmpty()) return null
    fun date(raw: String): LocalDate? = runCatching { Instant.parse(raw).atZone(zone).toLocalDate() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw) }.getOrNull()
    val matching = history.flatMap { log ->
        val day = date(log.actualDate ?: log.date) ?: return@flatMap emptyList()
        log.completedExercises.filter { it.fichaKeys().any(keys::contains) }
            .flatMap { ex -> ex.sets.filter { !it.isWarmup && !it.skipped && (it.reps > 0 || (it.timeSeconds ?: 0) > 0) }.map { day to it } }
    }
    val last = matching.maxOfOrNull { it.first } ?: return null
    fun estimate(set: CompletedSet): Double? = if (set.reps > 0 && set.weight > 0) StrengthModel.estimate1RM(set).takeIf { it > 0 }
        else set.homologatedResultV3?.estimatedRm?.takeIf { it.isFinite() && it > 0 }
    val best = matching.mapNotNull { (_, set) -> estimate(set)?.let { set to it } }.maxByOrNull { it.second }
    return ExerciseHistorySummary(best?.first, best?.second, last)
}

data class ExerciseFichaEffects(val blurDp: Float, val darkness: Float, val scale: Float, val headerAlpha: Float)

/** Efectos ligados al desplazamiento; sin reloj ni animación continua. */
fun exerciseFichaEffects(scrollDp: Float, reducedMotion: Boolean = false, canBlur: Boolean = true): ExerciseFichaEffects {
    val scroll = if (scrollDp.isFinite()) scrollDp.coerceAtLeast(0f) else 0f
    val fraction = (scroll / 300f).coerceIn(0f, 1f)
    return ExerciseFichaEffects(
        blurDp = if (canBlur) 16f * fraction else 0f,
        darkness = 0.68f * fraction,
        scale = if (reducedMotion) 1f else 1f + 0.08f * fraction,
        headerAlpha = ((scroll - 230f) / 80f).coerceIn(0f, 1f),
    )
}
