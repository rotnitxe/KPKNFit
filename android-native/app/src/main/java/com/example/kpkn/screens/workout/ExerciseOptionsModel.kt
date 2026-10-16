package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioProgramMode
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.sessioneditor.sessionExerciseListDose

/**
 * Lo que dice la cabecera de las opciones de un ejercicio (LV11, A2; prototipo C2) bajo su nombre: la dosis del plan con los datos
 * reales («4 × 4–6 · 80 % 1RM · RIR 2») y cuánto va hecho («1/4 hecha»). Un ejercicio de cardio dice su modo y su duración, como antes.
 * Una superserie dice sus rondas y sus ejercicios.
 */
internal fun exerciseOptionsSubtitle(
    exercise: Exercise,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
): String {
    if (exercise.cardioDetails != null) return cardioContextSummary(exercise)
    val dose = sessionExerciseListDose(exercise, includeRest = false)
    val total = WorkoutStepRules.totalRoadmapSlotsForExercise(exercise, omittedSetKeys)
    if (total <= 0) return dose
    val done = WorkoutStepRules.completedRoadmapSlotsForExercise(exercise, completedSets, omittedSetKeys)
    return "$dose · $done/$total ${if (done == 1) "hecha" else "hechas"}"
}

/** «3 rondas · 2 ejercicios» (con singular cuando toca). */
internal fun supersetOptionsSubtitle(rounds: Int, members: Int): String {
    val roundsText = if (rounds == 1) "1 ronda" else "$rounds rondas"
    val membersText = if (members == 1) "1 ejercicio" else "$members ejercicios"
    return "$roundsText · $membersText"
}

/** Espacio inseparable entre la cifra y su unidad: «20 min» nunca queda con «min» solo en la línea de abajo. */
private const val NBSP = "\u00A0"

/** Resumen de un ejercicio de cardio: «Espacio cardio · Intervalos · 20 min». */
internal fun cardioContextSummary(exercise: Exercise): String {
    val details = exercise.cardioDetails
    val mode = when (details?.programMode()) {
        CardioProgramMode.HIIT_SIT -> "HIIT / SIT"
        CardioProgramMode.INTERVALS -> "Intervalos"
        CardioProgramMode.STEADY -> "Estático"
        null -> "Cardio"
    }
    val duration = details?.effectiveDurationSeconds()?.takeIf { it > 0 }?.let { seconds ->
        val minutes = seconds / 60
        val remainder = seconds % 60
        when {
            minutes > 0 && remainder > 0 -> "${minutes}m ${remainder}s"
            minutes > 0 -> "$minutes${NBSP}min"
            else -> "$remainder${NBSP}s"
        }
    }
    return listOfNotNull("Espacio cardio", mode, duration).joinToString(" · ")
}
