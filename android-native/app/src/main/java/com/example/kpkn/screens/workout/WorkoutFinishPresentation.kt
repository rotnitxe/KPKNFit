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

/** The session belongs to its local start date even if the athlete finishes after midnight. */
internal fun finishSessionLocalDate(
    startTimeMs: Long,
    fallback: LocalDate = LocalDate.now(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): LocalDate = if (startTimeMs > 0L) {
    runCatching { java.time.Instant.ofEpochMilli(startTimeMs).atZone(zone).toLocalDate() }.getOrDefault(fallback)
} else fallback

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
    val sameCatalogIdentity = if (aConfig != null || bConfig != null) {
        aConfig != null && aConfig == bConfig
    } else {
        a.resolvedCanonicalExerciseId() == b.resolvedCanonicalExerciseId()
    }
    return sameCatalogIdentity &&
        a.selectedAspects.orEmpty() == b.selectedAspects.orEmpty() &&
        a.variantName.orEmpty() == b.variantName.orEmpty() &&
        a.performanceProfileId.orEmpty() == b.performanceProfileId.orEmpty()
}

private fun finishUnit(set: CompletedSet): com.example.kpkn.data.models.UnitModeV2 =
    set.recordedPayloadV3?.unitMode
        ?: set.homologatedResultV3?.unitMode
        ?: set.setOutcomeV2?.unitMode
        ?: when {
            (set.distanceKm ?: 0.0) > 0.0 -> com.example.kpkn.data.models.UnitModeV2.DISTANCE
            (set.timeSeconds ?: 0) > 0 && set.reps <= 0 -> com.example.kpkn.data.models.UnitModeV2.TIME
            else -> com.example.kpkn.data.models.UnitModeV2.REPS
        }

internal enum class FinishWeeklyMetricKind {
    ESTIMATED_ONE_RM,
    ADDED_LOAD,
    ASSISTANCE,
    BODYWEIGHT_REPS,
    DISTANCE_KM,
    TIME_SECONDS,
}

/** Numeric values and units used for both sides of the finish-sheet weekly chart. */
internal data class FinishWeeklyMetric(
    val kind: FinishWeeklyMetricKind,
    val currentValue: Double,
    val previousValue: Double?,
) {
    val lowerIsBetter: Boolean get() = kind == FinishWeeklyMetricKind.ASSISTANCE
    val unitLabel: String get() = when (kind) {
        FinishWeeklyMetricKind.ESTIMATED_ONE_RM -> "kg e1RM"
        FinishWeeklyMetricKind.ADDED_LOAD -> "kg agregado"
        FinishWeeklyMetricKind.ASSISTANCE -> "kg asistencia"
        FinishWeeklyMetricKind.BODYWEIGHT_REPS -> "reps"
        FinishWeeklyMetricKind.DISTANCE_KM -> "km"
        FinishWeeklyMetricKind.TIME_SECONDS -> "tiempo"
    }

    fun display(value: Double): String = when (kind) {
        FinishWeeklyMetricKind.ESTIMATED_ONE_RM -> "${finishNumber(value)} kg e1RM"
        FinishWeeklyMetricKind.ADDED_LOAD -> "${finishNumber(value)} kg agregado"
        FinishWeeklyMetricKind.ASSISTANCE -> "${finishNumber(value)} kg asistencia"
        FinishWeeklyMetricKind.BODYWEIGHT_REPS -> "${value.toInt()} reps"
        FinishWeeklyMetricKind.DISTANCE_KM -> "${finishNumber(value, 2)} km"
        FinishWeeklyMetricKind.TIME_SECONDS -> finishDurationLabel(value.toInt())
    }
}

/**
 * Resolves the chart metric using the same mode, unit and candidate-selection result as the comparison model.
 * In particular, assisted work is displayed as assistance kg (lower is better), and LASTRE only compares matching reps.
 */
internal fun finishWeeklyMetric(lift: FinishLiftProgress): FinishWeeklyMetric? {
    val best = lift.best ?: return null
    val previous = lift.previousWeek
    val selectedUnit = finishUnit(best)
    if (selectedUnit == com.example.kpkn.data.models.UnitModeV2.DISTANCE) {
        val current = (best.distanceKm ?: 0.0).takeIf { it > 0.0 } ?: return null
        return FinishWeeklyMetric(
            FinishWeeklyMetricKind.DISTANCE_KM,
            current,
            previous?.comparableScore?.div(1000.0)?.takeIf { it > 0.0 },
        )
    }
    if (selectedUnit == com.example.kpkn.data.models.UnitModeV2.TIME) {
        val current = (best.timeSeconds ?: 0).toDouble().takeIf { it > 0.0 } ?: return null
        return FinishWeeklyMetric(
            FinishWeeklyMetricKind.TIME_SECONDS,
            current,
            previous?.comparableScore?.takeIf { it > 0.0 },
        )
    }

    return when (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(best)) {
        com.example.kpkn.data.models.LoadModeV2.LASTRE -> {
            val current = com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(best) ?: return null
            FinishWeeklyMetric(FinishWeeklyMetricKind.ADDED_LOAD, current, previous?.weight?.takeIf { it >= 0.0 })
        }
        com.example.kpkn.data.models.LoadModeV2.ASSISTED -> {
            val current = com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(best) ?: return null
            FinishWeeklyMetric(FinishWeeklyMetricKind.ASSISTANCE, current, previous?.weight?.takeIf { it >= 0.0 })
        }
        com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT ->
            FinishWeeklyMetric(FinishWeeklyMetricKind.BODYWEIGHT_REPS, best.reps.toDouble(), previous?.comparableScore?.takeIf { it > 0.0 })
        com.example.kpkn.data.models.LoadModeV2.LOAD,
        null,
        -> {
            if (!com.example.kpkn.domain.workout.CompletedSetLoadSemantics.supportsEstimatedOneRm(best)) return null
            val current = StrengthModel.estimate1RM(best).takeIf { it > 0.0 } ?: return null
            FinishWeeklyMetric(FinishWeeklyMetricKind.ESTIMATED_ONE_RM, current, previous?.estimated1Rm?.takeIf { it > 0.0 })
        }
    }
}

private fun finishDurationLabel(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return when {
        minutes == 0 -> "$safeSeconds s"
        remainder == 0 -> "$minutes min"
        else -> "$minutes min $remainder s"
    }
}

private fun finishContextKey(set: CompletedSet): List<String> = listOf(
    set.contextKey.orEmpty(),
    set.recordedPayloadV3?.contextProfileId.orEmpty(),
    set.homologatedResultV3?.contextKey.orEmpty(),
    set.setOutcomeV2?.contextKey.orEmpty(),
    set.setupProfileId.orEmpty(),
    set.machineBrand.orEmpty(),
    set.tagId.orEmpty(),
    set.subTagIds.sorted().joinToString(","),
    (set.side ?: set.recordedPayloadV3?.side).orEmpty(),
)

private fun finishComparableEstimate(set: CompletedSet): Double? =
    if (!com.example.kpkn.domain.workout.CompletedSetLoadSemantics.supportsEstimatedOneRm(set)) null
    else StrengthModel.estimate1RM(set)

private fun bestFinishSet(exercise: CompletedExercise): CompletedSet? {
    val sets = exercise.sets.filter { !it.skipped && !it.isWarmup && !it.isFailedSet }
    if (exercise.cardioDetails != null) {
        val metric = sets.lastOrNull {
            finishUnit(it) == com.example.kpkn.data.models.UnitModeV2.DISTANCE ||
                finishUnit(it) == com.example.kpkn.data.models.UnitModeV2.TIME
        }?.let(::finishUnit) ?: return sets.lastOrNull()
        val comparable = sets.filter { finishUnit(it) == metric }
        return when (metric) {
            com.example.kpkn.data.models.UnitModeV2.DISTANCE -> comparable
                .filter { (it.distanceKm ?: 0.0) > 0.0 }
                .maxByOrNull { it.distanceKm ?: 0.0 }
            com.example.kpkn.data.models.UnitModeV2.TIME -> comparable
                .filter { (it.timeSeconds ?: 0) > 0 }
                .maxByOrNull { it.timeSeconds ?: 0 }
            else -> comparable.lastOrNull()
        }
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
        val currentUnit = current?.let(::finishUnit)
        val currentContextKey = current?.let(::finishContextKey)

        fun pointAt(log: WorkoutLog): FinishProgressPoint? {
            if (current == null) return null
            val matchingExercises = log.completedExercises.filter { sameFinishExercise(exercise, it) }
            val candidateSets = matchingExercises.flatMap { it.sets }
                .filter { !it.skipped && !it.isWarmup && !it.isFailedSet }
                .filter { candidate ->
                    val candContext = candidate.contextKey?.takeIf { it.isNotBlank() }
                    val candSide = candidate.side?.takeIf { it.isNotBlank() }
                    if (currentContext != candContext || currentSide != candSide) return@filter false
                    if (currentContextKey != finishContextKey(candidate)) return@filter false
                    if (finishUnit(candidate) != currentUnit) return@filter false
                    isCardio || com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(candidate) == currentMode
                }
            if (candidateSets.isEmpty()) return null

            val bestCandidate: CompletedSet? = if (isCardio) {
                when (currentUnit) {
                    com.example.kpkn.data.models.UnitModeV2.DISTANCE ->
                        candidateSets.filter { (it.distanceKm ?: 0.0) > 0.0 }.maxByOrNull { it.distanceKm ?: 0.0 }
                    com.example.kpkn.data.models.UnitModeV2.TIME ->
                        candidateSets.filter { (it.timeSeconds ?: 0) > 0 }.maxByOrNull { it.timeSeconds ?: 0 }
                    else -> null
                }
            } else when (currentMode) {
                com.example.kpkn.data.models.LoadModeV2.LOAD, null -> {
                    candidateSets.filter {
                        com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) != null &&
                            finishComparableEstimate(it) != null
                    }.maxByOrNull { finishComparableEstimate(it) ?: 0.0 }
                }
                com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> {
                    when (currentUnit) {
                        com.example.kpkn.data.models.UnitModeV2.TIME -> candidateSets.filter { (it.timeSeconds ?: 0) > 0 }.maxByOrNull { it.timeSeconds ?: 0 }
                        com.example.kpkn.data.models.UnitModeV2.REPS -> candidateSets.filter { it.reps > 0 }.maxByOrNull { it.reps }
                        else -> null
                    }
                }
                com.example.kpkn.data.models.LoadModeV2.LASTRE -> {
                    candidateSets.filter { it.reps == current.reps && it.reps > 0 }
                        .filter { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) != null }
                        .maxByOrNull { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) ?: Double.NEGATIVE_INFINITY }
                }
                com.example.kpkn.data.models.LoadModeV2.ASSISTED -> {
                    candidateSets.filter { it.reps == current.reps && it.reps > 0 }
                        .filter { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it) != null }
                        .minByOrNull { com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it) ?: Double.POSITIVE_INFINITY }
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
                isCardio && currentUnit == com.example.kpkn.data.models.UnitModeV2.DISTANCE -> best.distanceKm?.times(1000.0) ?: 0.0
                isCardio -> (best.timeSeconds ?: 0).toDouble()
                currentMode == com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT ->
                    if (currentUnit == com.example.kpkn.data.models.UnitModeV2.TIME) (best.timeSeconds ?: 0).toDouble() else best.reps.toDouble()
                currentMode == com.example.kpkn.data.models.LoadModeV2.LASTRE -> pointWeight
                currentMode == com.example.kpkn.data.models.LoadModeV2.ASSISTED -> -pointWeight
                else -> estimate
            }
            val label = if (isCardio) {
                finishSetLabel(best, currentUnit)
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

internal fun finishSetLabel(
    set: CompletedSet?,
    selectedUnit: com.example.kpkn.data.models.UnitModeV2? = set?.let(::finishUnit),
): String = when {
    set == null -> "Sin serie comparable"
    selectedUnit == com.example.kpkn.data.models.UnitModeV2.DISTANCE && (set.distanceKm ?: 0.0) > 0.0 ->
        "${finishNumber(set.distanceKm ?: 0.0)} km"
    selectedUnit == com.example.kpkn.data.models.UnitModeV2.TIME && (set.timeSeconds ?: 0) > 0 ->
        finishDurationLabel(set.timeSeconds ?: 0)
    else -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.displaySet(set)
}

internal fun finishDeltaLabel(delta: Double?): String = when {
    delta == null -> "Sin registro previo"
    kotlin.math.abs(delta) < 0.01 -> "="
    delta > 0 -> "+${finishNumber(delta)} kg"
    else -> "${finishNumber(delta)} kg"
}
