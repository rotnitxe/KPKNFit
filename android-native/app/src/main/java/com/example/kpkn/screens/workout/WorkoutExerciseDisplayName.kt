package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.buildExerciseCatalogLookup
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.exerciseDisplayName

private val workoutExerciseCatalogLookup by lazy {
    buildExerciseCatalogLookup(exerciseCatalogSnapshot())
}

internal fun displayWorkoutExerciseName(exercise: Exercise): String =
    exerciseDisplayName(exercise, workoutExerciseCatalogLookup)

/** Traduce cada chip conocido a una frase hablada natural ("con mancuernas",
 *  "en polea alta", "con agarre supino"...). Los desconocidos se mantienen. */
internal val SPOKEN_CHIP_PHRASES = mapOf(
    "Polea Alta" to "en polea alta",
    "Polea Media" to "en polea media",
    "Polea Baja" to "en polea baja",
    "Polea" to "en polea",
    "Máquina" to "en máquina",
    "Máquina Smith" to "en máquina Smith",
    "Máquina GHD" to "en máquina GHD",
    "GHD" to "en máquina GHD",
    "Mancuernas" to "con mancuernas",
    "Mancuerna" to "con mancuerna",
    "Barra" to "con barra",
    "Barra EZ" to "con barra EZ",
    "Barra H" to "con barra H",
    "Barra Hex" to "con barra hexagonal",
    "Barra T" to "con barra T",
    "Barra de Seguridad" to "con barra de seguridad",
    "Kettlebell" to "con kettlebell",
    "Banda" to "con banda",
    "Discos" to "con discos",
    "Peso Corporal" to "con peso corporal",
    "Deslizadores" to "con deslizadores",
    "TRX" to "en TRX",
    "Supino" to "con agarre supino",
    "Neutro" to "con agarre neutro",
    "Prono" to "con agarre prono",
    "Amplio" to "con agarre amplio",
    "Medio" to "con agarre medio",
    "Cerrado" to "con agarre cerrado",
    "Bilateral" to "de forma bilateral",
    "Unilateral" to "de forma unilateral",
    "Sentado" to "sentado",
    "De pie" to "de pie",
    "Plano" to "con apoyo plano",
    "Pies Elevados" to "con los pies elevados",
)

/** Versión legible en voz del nombre del ejercicio: el separador "·" se
 *  convierte en frases habladas ("Curl Martillo con mancuernas y agarre
 *  supino") para que el TTS suene natural. */
internal fun spokenWorkoutExerciseName(exercise: Exercise): String {
    val display = displayWorkoutExerciseName(exercise)
    if (!display.contains(" · ")) return display
    val parts = display.split(" · ")
    val head = parts.first()
    val spokenChips = parts.drop(1).mapNotNull { chip ->
        val trimmed = chip.trim()
        SPOKEN_CHIP_PHRASES[trimmed] ?: trimmed
    }
    return if (spokenChips.isEmpty()) head else "$head ${spokenChips.joinToString(" y ")}"
}
