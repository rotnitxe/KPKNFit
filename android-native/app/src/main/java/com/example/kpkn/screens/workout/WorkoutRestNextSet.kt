package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.workout.design.workoutRestNextLine

/**
 * Línea «Siguiente…» del descanso (KPKN LS3): la serie que sigue a la registrada, del mismo ejercicio, con su dato planificado. Sin serie
 * siguiente (o ya hecha), no hay línea: nunca se inventa un dato. En una superserie dentro de la ronda, la siguiente es otro ejercicio,
 * así que tampoco hay línea.
 */
internal fun restNextLineFor(
    kind: RestTimerKind?,
    setJustLoggedKey: String?,
    completedSetKeys: Set<String>,
    visibleExercises: List<Exercise>,
): String? {
    if (kind == RestTimerKind.SUPERSET_ROUND || kind == RestTimerKind.BETWEEN_SIDES) {
        return workoutRestNextLine(kind, nextSetNumber = null, plannedWeightKg = null, plannedReps = null)
    }
    if (kind == RestTimerKind.SUPERSET_INTRA) return null
    val logged = setJustLoggedKey?.let(::parseCompletedSetKey) ?: return null
    val exercise = visibleExercises.firstOrNull { it.id == logged.exerciseId } ?: return null
    val nextIdx = logged.setIdx + 1
    val next = exercise.sets.getOrNull(nextIdx) ?: return null
    if (next.isEmptySlot || "${exercise.id}_$nextIdx" in completedSetKeys) return null
    return workoutRestNextLine(
        kind = kind,
        nextSetNumber = nextIdx + 1,
        plannedWeightKg = next.weight,
        plannedReps = next.targetReps,
    )
}

/** Avisos honestos del aviso del descanso: sin notificaciones o sin alarma exacta. Vacío si ambas están concedidas. */
internal fun restNoticesFor(state: WorkoutRestModalState?): List<String> = buildList {
    if (state?.notificationsEnabled == false) add("Sin notificaciones: el aviso es solo sonoro.")
    if (state?.exactAlarmGranted == false) add("Sin alarma exacta: el aviso puede retrasarse con la pantalla apagada.")
}
