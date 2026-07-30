package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.exercises.ExerciseCatalogExclusiveFilter
import com.example.kpkn.domain.exercises.ExerciseCatalogRegion
import com.example.kpkn.domain.exercises.ExerciseCatalogSort
import com.example.kpkn.domain.exercises.calculateFriendlyFatigue
import com.example.kpkn.domain.exercises.calculateSearchScore
import com.example.kpkn.domain.exercises.deduplicateCatalogVisualResults
import com.example.kpkn.domain.exercises.matchesExclusiveCatalogFilter
import com.example.kpkn.domain.exercises.resolveExerciseRegion
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

internal fun discomfortCountsByExercise(
    workoutLogs: List<WorkoutLog>,
): Map<String, List<Pair<String, Int>>> {
    val map = mutableMapOf<String, MutableMap<String, Int>>()
    workoutLogs.forEach { log ->
        log.postExerciseReports.forEach { report ->
            val key = report.canonicalExerciseId ?: report.exerciseDbId ?: report.exerciseId
            if (key.isBlank()) return@forEach
            val bucket = map.getOrPut(key) { mutableMapOf() }
            report.discomfortIds
                .filter { it != "none" }
                .forEach { discomfortId ->
                    val label = discomfortLabel(discomfortId)
                    bucket[label] = (bucket[label] ?: 0) + 1
                }
        }
    }
    return map.mapValues { (_, value) ->
        value.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }
}

internal fun buildExerciseUtilityBullets(exercise: ExerciseMuscleInfo): List<String> {
    val bullets = mutableListOf<String>()
    val region = resolveExerciseRegion(exercise)
    val type = exercise.type?.lowercase().orEmpty()
    val fatigue = calculateFriendlyFatigue(exercise).overall

    if (exercise.functionalTransfer?.isNotBlank() == true) {
        bullets += exercise.functionalTransfer
    }

    if (type.contains("básico") || type.contains("basico") || exercise.tier?.equals("T1", true) == true) {
        bullets += "Muy útil para mejorar básicos del programa y capacidad de producir fuerza."
    }

    if (type.contains("aislamiento") || type.contains("accesorio")) {
        bullets += "Buena opción para reforzar puntos débiles con fatiga sistémica controlada."
    }

    if (exercise.bracingRecommended == true || fatigue >= 7) {
        bullets += "Puede mejorar tolerancia estructural y control técnico cuando se programa con criterio."
    } else {
        bullets += "Útil para salud muscular/articular al acumular práctica de calidad con fatiga moderada."
    }

    bullets += when (region) {
        ExerciseCatalogRegion.FULL -> "Aporta utilidad general para rendimiento global y coordinación."
        ExerciseCatalogRegion.UPPER -> "Útil para salud de hombro y mejora de empuje/tirón del tren superior."
        ExerciseCatalogRegion.LOWER -> "Útil para potencia del tren inferior, estabilidad y rendimiento atlético."
        ExerciseCatalogRegion.CORE -> "Útil para estabilidad del tronco y transmisión de fuerza."
        ExerciseCatalogRegion.ALL -> "Útil para construir base general según tu objetivo."
    }

    if (!exercise.sportsRelevance.isNullOrEmpty()) {
        bullets += "Muy usado en: ${exercise.sportsRelevance.take(4).joinToString(", ")}."
    }

    bullets += when {
        fatigue <= 3 -> "Permite acumular práctica técnica sin castigar demasiado la recuperación."
        fatigue <= 6 -> "Equilibrio entre estímulo y recuperación para progresar con constancia."
        else -> "Conviene periodizar su uso porque genera una demanda alta de recuperación."
    }

    return bullets.distinct().take(5)
}

internal data class MuscleVolumeContribution(
    val muscle: String,
    val role: MuscleRole,
    val seriesEquivalent: Double,
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
            .add(MuscleVolumeContribution(muscle, involvement.role, contribution))
    }

    return grouped.values.map { entries ->
        val topRole = entries.minByOrNull { rolePriority[it.role] ?: 99 }?.role ?: MuscleRole.SECONDARY
        MuscleVolumeContribution(
            muscle = entries.first().muscle,
            role = topRole,
            seriesEquivalent = entries.maxOf { it.seriesEquivalent }.coerceIn(0.0, 1.0),
        )
    }.sortedByDescending { it.seriesEquivalent }
}

internal fun roleVolumeLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Primario · motor principal del patrón"
    MuscleRole.SECONDARY -> "Secundario · asiste y completa el movimiento"
    MuscleRole.STABILIZER -> "Estabilizador · controla la postura"
    MuscleRole.NEUTRALIZER -> "Neutralizador · evita compensación"
}

internal fun roleVolumeWhy(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Recibe la mayor parte del estímulo por serie."
    MuscleRole.SECONDARY -> "Aporta volumen parcial; útil para equilibrio semanal."
    MuscleRole.STABILIZER -> "Fatiga local sin ser el objetivo hipertrofia principal."
    MuscleRole.NEUTRALIZER -> "Trabajo de control; no cuenta como volumen directo típico."
}

internal fun formatSeriesEquivalent(value: Double): String {
    val normalized = value.coerceAtLeast(0.0)
    val text = "%.1f".format(normalized)
    return "$text serie"
}

internal fun fatigueColor(score: Int): Color = when {
    score <= 3 -> Color(0xFF22C55E)
    score <= 6 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

internal fun fatigueLabel(score: Int): String = when {
    score <= 3 -> "Poca fatiga"
    score <= 6 -> "Fatiga media"
    score <= 8 -> "Alta fatiga"
    else -> "Fatiga muy alta"
}
