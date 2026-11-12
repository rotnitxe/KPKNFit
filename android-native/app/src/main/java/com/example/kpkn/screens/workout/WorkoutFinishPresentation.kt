package com.example.kpkn.screens.workout

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Datos ya cargados en memoria para el cierre; no consulta disco ni modifica registros. */
internal data class FinishPresentationContext(
    val program: Program? = null,
    val weekNumber: Int? = null,
    val history: List<WorkoutLog> = emptyList(),
    val photos: List<StoryPhoto> = emptyList(),
    val calories: Int? = null,
    val records: Int = 0,
    val milestones: List<com.example.kpkn.data.models.SessionMilestone> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

internal data class StoryPhoto(
    val path: String,
    val exerciseName: String? = null,
    val detail: String? = null,
    val setIndex: Int? = null,
    val createdAtMs: Long? = null,
    val isPr: Boolean = false,
)
internal data class FinishProgressPoint(
    val date: String,
    val weight: Double,
    val reps: Int,
    val estimated1Rm: Double,
    val displayLabel: String? = null,
    val loadKnown: Boolean = true,
    val comparableScore: Double = estimated1Rm,
    val mode: com.example.kpkn.data.models.LoadModeV2? = null,
)
internal data class FinishLiftProgress(
    val exerciseId: String,
    val name: String,
    val best: CompletedSet?,
    val setCount: Int,
    val previous: FinishProgressPoint?,
    val history: List<FinishProgressPoint>,
    val totalHistorySessions: Int = history.size,
    val first: FinishProgressPoint? = history.firstOrNull(),
    /** Mejor serie comparable de la semana calendario inmediatamente anterior (lunes-domingo). */
    val previousWeek: FinishProgressPoint? = null,
) {
    val deltaKg: Double?
        get() {
            val today = best ?: return null
            val prev = previous ?: return null
            if (!prev.loadKnown || today.reps <= 0 || today.reps != prev.reps) return null
            return when (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(today)) {
                com.example.kpkn.data.models.LoadModeV2.LOAD,
                com.example.kpkn.data.models.LoadModeV2.LASTRE,
                null,
                -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(today)?.let { it - prev.weight }
                com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT,
                com.example.kpkn.data.models.LoadModeV2.ASSISTED,
                -> null
            }
        }
    val isImproved: Boolean get() = deltaKg?.let { it > 0.01 } == true
}

/** Configuraciones distintas nunca comparten la gráfica; en históricos sin configuración se conserva la identidad/aspectos. */
private fun sameFinishExercise(a: CompletedExercise, b: CompletedExercise): Boolean {
    val aConfig = a.catalogConfigurationId?.takeIf { it.isNotBlank() }
    val bConfig = b.catalogConfigurationId?.takeIf { it.isNotBlank() }
    if (aConfig != null || bConfig != null) return aConfig != null && aConfig == bConfig
    return a.resolvedCanonicalExerciseId() == b.resolvedCanonicalExerciseId() && a.selectedAspects.orEmpty() == b.selectedAspects.orEmpty()
}

private fun finishComparableEstimate(set: CompletedSet): Double? =
    if (!com.example.kpkn.domain.workout.CompletedSetLoadSemantics.supportsEstimatedOneRm(set)) null
    else StrengthModel.estimate1RM(set)

private fun bestFinishSet(exercise: CompletedExercise): CompletedSet? {
    val sets = exercise.sets.filter { !it.skipped && !it.isWarmup && !it.isFailedSet }
    if (exercise.cardioDetails != null) {
        return sets.maxWithOrNull(
            compareBy<CompletedSet> { it.distanceKm ?: 0.0 }
                .thenBy { it.timeSeconds ?: 0 }
        )
    }
    return sets.filter { it.reps > 0 || (it.timeSeconds ?: 0) > 0 || (it.distanceKm ?: 0.0) > 0.0 }
        .maxWithOrNull(
            compareBy<CompletedSet> { finishComparableEstimate(it) ?: -1.0 }
                .thenBy { set ->
                    when (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(set)) {
                        com.example.kpkn.data.models.LoadModeV2.ASSISTED ->
                            com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(set)?.let { -it } ?: Double.NEGATIVE_INFINITY
                        com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> 0.0
                        com.example.kpkn.data.models.LoadModeV2.LOAD,
                        com.example.kpkn.data.models.LoadModeV2.LASTRE,
                        null,
                        -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(set) ?: Double.NEGATIVE_INFINITY
                    }
                }
                .thenBy { it.reps }
                .thenBy { it.timeSeconds ?: 0 }
        )
}

internal fun buildFinishLiftProgress(
    completed: List<CompletedExercise>,
    history: List<WorkoutLog>,
    today: LocalDate = LocalDate.now(),
): List<FinishLiftProgress> =
    completed.map { exercise ->
        val current = bestFinishSet(exercise)
        val currentContext = current?.contextKey?.takeIf { it.isNotBlank() }
        val currentSide = current?.side?.takeIf { it.isNotBlank() }
        val isCardio = exercise.cardioDetails != null
        val currentMode = current?.let { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(it) }

        fun pointAt(log: WorkoutLog): FinishProgressPoint? {
            if (current == null) return null
            val matchingExercises = log.completedExercises.filter { sameFinishExercise(exercise, it) }
            val candidateSets = matchingExercises.flatMap { it.sets }
                .filter { !it.skipped && !it.isWarmup && !it.isFailedSet }
                .filter { candidate ->
                    val candContext = candidate.contextKey?.takeIf { it.isNotBlank() }
                    val candSide = candidate.side?.takeIf { it.isNotBlank() }
                    if (currentContext != null && candContext != null && currentContext != candContext) return@filter false
                    if (currentSide != null && candSide != null && currentSide != candSide) return@filter false
                    if (isCardio) {
                        (candidate.distanceKm ?: 0.0) > 0.0 || (candidate.timeSeconds ?: 0) > 0
                    } else {
                        val candMode = com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(candidate)
                        candMode == currentMode
                    }
                }
            if (candidateSets.isEmpty()) return null

            val bestCandidate: CompletedSet? = if (isCardio) {
                if ((current.distanceKm ?: 0.0) > 0.0) {
                    candidateSets.maxByOrNull { it.distanceKm ?: 0.0 }
                } else {
                    candidateSets.maxByOrNull { it.timeSeconds ?: 0 }
                }
            } else when (currentMode) {
                com.example.kpkn.data.models.LoadModeV2.LOAD, null -> {
                    val known = candidateSets.filter { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) != null }
                    known.maxByOrNull {
                        finishComparableEstimate(it)
                            ?: com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it)
                            ?: 0.0
                    } ?: candidateSets.maxByOrNull { it.reps }
                }
                com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> {
                    candidateSets.maxByOrNull { if (it.reps > 0) it.reps.toDouble() else (it.timeSeconds ?: 0).toDouble() }
                }
                com.example.kpkn.data.models.LoadModeV2.LASTRE -> {
                    val known = candidateSets.filter { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) != null }
                    val pool = if (known.isNotEmpty()) {
                        known.filter { it.reps == current.reps }.ifEmpty { known }
                    } else {
                        candidateSets.filter { it.reps == current.reps }.ifEmpty { candidateSets }
                    }
                    pool.maxWithOrNull(
                        compareBy<CompletedSet> { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) ?: Double.NEGATIVE_INFINITY }
                            .thenBy { it.reps }
                    )
                }
                com.example.kpkn.data.models.LoadModeV2.ASSISTED -> {
                    val known = candidateSets.filter { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it) != null }
                    if (known.isNotEmpty()) {
                        val equalReps = known.filter { it.reps == current.reps }
                        (if (equalReps.isNotEmpty()) equalReps else known)
                            .minWithOrNull(
                                compareBy<CompletedSet> { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it) ?: Double.POSITIVE_INFINITY }
                                    .thenByDescending { it.reps }
                            )
                    } else {
                        candidateSets.maxByOrNull { it.reps }
                    }
                }
            }
            val best = bestCandidate ?: return null

            val resolvedLoad: Double? = when (currentMode) {
                com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> 0.0
                com.example.kpkn.data.models.LoadModeV2.ASSISTED -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(best)
                com.example.kpkn.data.models.LoadModeV2.LASTRE,
                com.example.kpkn.data.models.LoadModeV2.LOAD,
                null,
                -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(best)
            }
            val loadKnown = isCardio || currentMode == com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT || resolvedLoad != null
            val pointWeight = resolvedLoad ?: 0.0
            val estimate = finishComparableEstimate(best) ?: 0.0
            val comparableScore = when {
                isCardio -> (best.distanceKm ?: 0.0) * 1000.0 + (best.timeSeconds ?: 0).toDouble()
                currentMode == com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT ->
                    if (best.reps > 0) best.reps.toDouble() else (best.timeSeconds ?: 0).toDouble()
                currentMode == com.example.kpkn.data.models.LoadModeV2.LASTRE -> if (loadKnown) {
                    (if (best.reps == current.reps) 1_000_000.0 else 0.0) + pointWeight * 1000.0 + best.reps
                } else {
                    best.reps.toDouble()
                }
                currentMode == com.example.kpkn.data.models.LoadModeV2.ASSISTED -> if (loadKnown) {
                    (if (best.reps == current.reps) 1_000_000.0 else 0.0) - pointWeight * 1000.0 + best.reps
                } else {
                    best.reps.toDouble()
                }
                else -> estimate
            }
            val label = if (isCardio) {
                if ((best.distanceKm ?: 0.0) > 0.0) "${finishNumber(best.distanceKm ?: 0.0)} km"
                else "${(best.timeSeconds ?: 0) / 60} min"
            } else {
                com.example.kpkn.domain.workout.CompletedSetLoadSemantics.displaySet(best)
            }
            return FinishProgressPoint(
                date = log.date,
                weight = pointWeight,
                reps = best.reps,
                estimated1Rm = estimate,
                displayLabel = label,
                loadKnown = loadKnown,
                comparableScore = comparableScore,
                mode = currentMode,
            )
        }

        val sorted = history.sortedBy { com.example.kpkn.domain.time.TrainingDates.logDateMs(it) }
        val points = sorted.mapNotNull(::pointAt)
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val previousStart = monday.minusWeeks(1)
        val previousEnd = monday.minusDays(1)
        val previousWeekCandidates = sorted.filter { log ->
            val date = runCatching {
                java.time.Instant.ofEpochMilli(com.example.kpkn.domain.time.TrainingDates.logDateMs(log))
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            }.getOrNull()
            date != null && !date.isBefore(previousStart) && !date.isAfter(previousEnd)
        }.mapNotNull(::pointAt)
        val previousWeek = previousWeekCandidates.maxByOrNull { it.comparableScore }
            ?: previousWeekCandidates.lastOrNull()

        FinishLiftProgress(
            exercise.exerciseId, exercise.exerciseName, current,
            logicalSetCountFromCompleted(listOf(exercise)), points.lastOrNull(), points.takeLast(24), points.size,
            points.firstOrNull(), previousWeek,
        )
    }

/** Real effort only: planned failure does not establish that failure was reached. */
internal fun finishActualRir(set: CompletedSet): Double? {
    val rir = when {
        set.isFailure -> 0.0
        set.actualIntensityMode == IntensityMode.RIR -> set.actualIntensityValue ?: set.rir?.toDouble()
        set.actualIntensityMode == IntensityMode.RPE -> (set.actualIntensityValue ?: set.rpe)
            ?.takeIf { it in 0.0..10.0 }?.let { 10.0 - it }
        set.actualIntensityMode != null -> null
        set.rir != null -> set.rir.toDouble()
        else -> set.rpe?.takeIf { it in 0.0..10.0 }?.let { 10.0 - it }
    }
    // The recorder allows RIR >10. Keep RPE0 and real RIR12 valid.
    return rir?.takeIf { it.isFinite() && it >= 0.0 }
}

internal fun finishAverageRir(completed: List<CompletedExercise>): Double? = completed.flatMap { it.sets }
    .filter { !it.skipped && !it.isWarmup }.mapNotNull(::finishActualRir)
    .takeIf { it.isNotEmpty() }?.average()

/** «8.420», «7,5»: punto de millar y coma decimal (antes devolvía «8 420», con espacio). */
internal fun finishNumber(value: Double, decimals: Int = 1): String = finishGrouped(value, decimals)

internal fun finishSetLabel(set: CompletedSet?): String = when {
    set == null -> "Sin serie comparable"
    (set.distanceKm ?: 0.0) > 0.0 -> "${finishNumber(set.distanceKm ?: 0.0)} km"
    (set.timeSeconds ?: 0) > 0 && set.reps == 0 -> "${(set.timeSeconds ?: 0) / 60} min"
    else -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.displaySet(set)
}

internal fun finishDeltaLabel(delta: Double?): String = when {
    delta == null -> "Sin registro previo"
    kotlin.math.abs(delta) < 0.01 -> "="
    delta > 0 -> "+${finishNumber(delta)} kg"
    else -> "${finishNumber(delta)} kg"
}
