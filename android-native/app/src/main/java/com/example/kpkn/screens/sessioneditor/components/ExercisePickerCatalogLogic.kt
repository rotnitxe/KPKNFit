package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.exercises.ExerciseCatalogExclusiveFilter
import com.example.kpkn.domain.exercises.ExerciseCatalogSort
import com.example.kpkn.domain.exercises.calculateFriendlyFatigue
import com.example.kpkn.domain.exercises.calculateSearchScore
import com.example.kpkn.domain.exercises.deduplicateCatalogVisualResults
import com.example.kpkn.domain.exercises.matchesExclusiveCatalogFilter
import com.example.kpkn.domain.exercises.resolvePrimaryMuscleLabel
import com.example.kpkn.domain.training.VolumeCalculator

internal fun filterAndSortExerciseCatalog(
    fullCatalog: List<ExerciseMuscleInfo>,
    normalizedQuery: String,
    sortMode: ExerciseCatalogSort,
    exclusiveFilter: ExerciseCatalogExclusiveFilter = ExerciseCatalogExclusiveFilter.None,
    ascending: Boolean = true,
): List<ExerciseMuscleInfo> {
    val baseFiltered = fullCatalog.filter { matchesExclusiveCatalogFilter(it, exclusiveFilter) }
    val hasActiveSearch = normalizedQuery.isNotBlank()

    val searched = if (!hasActiveSearch) {
        baseFiltered
    } else {
        baseFiltered
            .map { it to calculateSearchScore(it, normalizedQuery) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<ExerciseMuscleInfo, Int>> { it.second }
                    .thenBy { kotlin.math.abs(it.first.name.length - normalizedQuery.length) }
                    .thenBy { it.first.name },
            )
            .map { it.first }
    }

    val byNameAsc = compareBy<ExerciseMuscleInfo> { it.name.lowercase() }
    val byFatigueAsc = compareBy<ExerciseMuscleInfo> { calculateFriendlyFatigue(it).overall }
        .thenBy { it.name.lowercase() }

    // With an active query, keep relevance ranking for alphabetical/relevance modes.
    // Re-sorting A→Z was wiping search order and making multi-word queries look like
    // a full alphabetical catalog (starting at "A").
    val sorted = when {
        hasActiveSearch &&
            (sortMode == ExerciseCatalogSort.NAME || sortMode == ExerciseCatalogSort.RELEVANCE) ->
            searched
        sortMode == ExerciseCatalogSort.NAME ->
            if (ascending) searched.sortedWith(byNameAsc) else searched.sortedWith(byNameAsc.reversed())
        sortMode == ExerciseCatalogSort.FATIGUE_HIGH || sortMode == ExerciseCatalogSort.FATIGUE_LOW ->
            // ascending = menos → más fatigante; descending = más → menos
            if (ascending) searched.sortedWith(byFatigueAsc) else searched.sortedWith(byFatigueAsc.reversed())
        sortMode == ExerciseCatalogSort.GROUP_BY_MUSCLE ->
            searched.sortedWith(compareBy({ resolvePrimaryMuscleLabel(it) }, { it.name.lowercase() }))
        sortMode == ExerciseCatalogSort.RELEVANCE ->
            searched
        else ->
            if (ascending) searched.sortedWith(byNameAsc) else searched.sortedWith(byNameAsc.reversed())
    }
    return deduplicateCatalogVisualResults(sorted)
}

internal data class MuscleVolumeContribution(
    val muscle: String,
    val role: MuscleRole,
    val seriesEquivalent: Double,
    val emphasis: String? = null,
    val sourceInvolvement: InvolvedMuscle? = null,
)

internal fun oneSeriesVolumeContributions(exercise: ExerciseMuscleInfo): List<MuscleVolumeContribution> {
    if (exercise.involvedMuscles.isEmpty()) return emptyList()

    val grouped = linkedMapOf<String, MutableList<MuscleVolumeContribution>>()
    val rolePriority = mapOf(
        MuscleRole.PRIMARY to 0,
        MuscleRole.SECONDARY to 1,
        MuscleRole.STABILIZER to 2,
        MuscleRole.NEUTRALIZER to 3,
    )
    exercise.involvedMuscles.forEach { involvement ->
        val muscle = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
        val contribution = resolveMuscleVolumeContribution(involvement)
        grouped.getOrPut(muscle) { mutableListOf() }
            .add(MuscleVolumeContribution(muscle, involvement.role, contribution, involvement.emphasis, involvement))
    }

    return grouped.values.map { entries ->
        val topRole = entries.minByOrNull { rolePriority[it.role] ?: 99 }?.role ?: MuscleRole.SECONDARY
        val source = entries.filter { it.role == topRole }.maxByOrNull { it.seriesEquivalent }
            ?: entries.maxByOrNull { it.seriesEquivalent }
        MuscleVolumeContribution(
            muscle = entries.first().muscle,
            role = topRole,
            seriesEquivalent = entries.maxOf { it.seriesEquivalent }.coerceIn(0.0, 1.0),
            emphasis = source?.emphasis,
            sourceInvolvement = source?.sourceInvolvement,
        )
    }.sortedWith(
        compareBy<MuscleVolumeContribution>(
            { rolePriority[it.role] ?: 99 },
            { -it.seriesEquivalent },
        ),
    )
}

internal fun roleVolumeLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Primario · motor principal del patrón"
    MuscleRole.SECONDARY -> "Secundario · asiste y completa el movimiento"
    MuscleRole.STABILIZER -> "Estabilizador · controla la postura"
    MuscleRole.NEUTRALIZER -> "Neutralizador · evita compensación"
}

internal fun formatSeriesEquivalent(value: Double): String {
    val normalized = value.coerceAtLeast(0.0)
    val text = "%.1f".format(normalized)
    return "$text serie"
}

internal fun formatVolumePercent(value: Double): String =
    "%.0f%%".format((value.coerceIn(0.0, 1.0)) * 100.0)

internal fun fatigueLabel(score: Int): String = when {
    score <= 3 -> "Poca fatiga"
    score <= 6 -> "Fatiga media"
    score <= 8 -> "Alta fatiga"
    else -> "Fatiga muy alta"
}
