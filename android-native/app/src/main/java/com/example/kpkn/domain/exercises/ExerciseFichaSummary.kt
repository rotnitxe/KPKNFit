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
    fun estimate(set: CompletedSet): Double? {
        if (!com.example.kpkn.domain.workout.CompletedSetLoadSemantics.supportsEstimatedOneRm(set)) return null
        val externalWeight = com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(set) ?: return null
        return set.homologatedResultV3
            ?.takeIf { it.estimatorVersion >= com.example.kpkn.domain.calculations.WORKOUT_DERIVED_ESTIMATOR_VERSION }
            ?.estimatedRm
            ?.takeIf { it.isFinite() && it > 0 }
            ?: if (externalWeight.isFinite() && externalWeight > 0 && set.reps > 0) {
                StrengthModel.estimate1RM(set).takeIf { it.isFinite() && it > 0 }
            } else null
    }
    val best = matching.mapNotNull { (_, set) -> estimate(set)?.let { set to it } }.maxByOrNull { it.second }
    val fallbackBest = if (best == null) {
        matching.map { it.second }.filter {
            com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(it) != null
        }.maxWithOrNull(
            compareBy<CompletedSet>(
                { it.reps },
                { it.timeSeconds ?: 0 },
                {
                    when (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(it)) {
                        com.example.kpkn.data.models.LoadModeV2.ASSISTED ->
                            com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it)?.let { kg -> -kg }
                                ?: Double.NEGATIVE_INFINITY
                        else -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it)
                            ?: Double.NEGATIVE_INFINITY
                    }
                },
            ),
        )
    } else null
    return ExerciseHistorySummary(best?.first ?: fallbackBest, best?.second, last)
}

data class ExerciseFichaEffects(val blurDp: Float, val darkness: Float, val scale: Float, val headerAlpha: Float, val titleAlpha: Float)

/** Efectos ligados al desplazamiento; sin reloj ni animación continua. */
fun exerciseFichaEffects(scrollDp: Float, reducedMotion: Boolean = false, canBlur: Boolean = true): ExerciseFichaEffects {
    val scroll = if (scrollDp.isFinite()) scrollDp.coerceAtLeast(0f) else 0f
    val fraction = (scroll / 300f).coerceIn(0f, 1f)
    return ExerciseFichaEffects(
        blurDp = if (canBlur) 16f * fraction else 0f,
        darkness = 0.68f * fraction,
        scale = if (reducedMotion) 1f else 1f + 0.08f * fraction,
        // Finish fading the large title before the fixed title starts appearing.
        titleAlpha = (1f - (scroll - 230f) / 40f).coerceIn(0f, 1f),
        headerAlpha = ((scroll - 270f) / 40f).coerceIn(0f, 1f),
    )
}
