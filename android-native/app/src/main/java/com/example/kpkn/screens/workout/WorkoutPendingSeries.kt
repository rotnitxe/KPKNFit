package com.example.kpkn.screens.workout

/** Cuántos ejercicios se nombran en el aviso antes de resumir el resto con «y N más». */
internal const val PENDING_SERIES_MAX_NAMED_EXERCISES = 3

internal data class PendingSeriesGroup(
    val exerciseId: String,
    val exerciseName: String,
    val seriesCount: Int,
)

/**
 * Aviso del resumen final cuando quedan series de fuerza o cardio sin hacer.
 *
 * El texto es neutral a propósito: quien termina puede haberlo decidido, así que no se
 * le reprocha nada; solo se le dice qué quedará fuera si guarda ya.
 */
internal data class PendingSeriesNotice(
    val totalSeries: Int,
    val groups: List<PendingSeriesGroup>,
    /** Paso al que salta «Seguir con ellas»: la primera serie pendiente en el orden de la sesión. */
    val firstStepKey: String,
) {
    val message: String
        get() = pendingSeriesMessage(totalSeries, groups)

    val actionLabel: String
        get() = if (totalSeries == 1) PENDING_SERIES_ACTION_SINGLE else PENDING_SERIES_ACTION_PLURAL
}

internal const val PENDING_SERIES_ACTION_PLURAL = "Seguir con ellas"
internal const val PENDING_SERIES_ACTION_SINGLE = "Seguir con ella"

/**
 * Convierte los pasos pendientes ([WorkoutStepNavigator.pendingSeriesSteps]) en el aviso del
 * resumen. Devuelve `null` si no queda nada pendiente.
 *
 * Una serie unilateral con los dos lados pendientes cuenta una sola vez: se agrupa por
 * ejercicio y número de serie.
 */
internal fun buildPendingSeriesNotice(
    pendingSteps: List<WorkoutStep>,
    nameFor: (WorkoutStep) -> String = { it.exerciseName },
): PendingSeriesNotice? {
    val firstStep = pendingSteps.firstOrNull() ?: return null
    val distinctSeries = pendingSteps.distinctBy { it.exerciseId to (it.setIndex ?: 0) }
    val groups = distinctSeries
        .groupBy { it.exerciseId }
        .map { (exerciseId, steps) ->
            PendingSeriesGroup(
                exerciseId = exerciseId,
                exerciseName = nameFor(steps.first()),
                seriesCount = steps.size,
            )
        }
    return PendingSeriesNotice(
        totalSeries = distinctSeries.size,
        groups = groups,
        firstStepKey = firstStep.stepKey,
    )
}

internal fun pendingSeriesMessage(totalSeries: Int, groups: List<PendingSeriesGroup>): String {
    val head = if (totalSeries == 1) {
        "Te queda 1 serie sin hacer"
    } else {
        "Te quedan $totalSeries series sin hacer"
    }
    if (groups.isEmpty()) return "$head."
    val named = groups.take(PENDING_SERIES_MAX_NAMED_EXERCISES).joinToString(", ") { group ->
        // Con un solo ejercicio y una sola serie el número sobra: «Press de banca».
        if (totalSeries == 1) group.exerciseName else "${group.exerciseName} (${group.seriesCount})"
    }
    val hiddenExercises = groups.size - PENDING_SERIES_MAX_NAMED_EXERCISES
    val tail = if (hiddenExercises > 0) {
        if (hiddenExercises == 1) " y 1 ejercicio más" else " y $hiddenExercises ejercicios más"
    } else {
        ""
    }
    return "$head: $named$tail."
}
