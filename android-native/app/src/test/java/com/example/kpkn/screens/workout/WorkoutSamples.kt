package com.example.kpkn.screens.workout

/**
 * Datos de ejemplo de la sesión en vivo para la hoja de render del marco (LS2a). Son textos y cifras fijas, sin VM: el marco se
 * pinta con las piezas sin estado de `screens/workout/design/`.
 */
internal object WorkoutSamples {
    const val EXERCISE_NAME = "Press inclinado con mancuernas en banco"
    const val EXERCISE_NAME_SHORT = "Press inclinado"
    const val EYEBROW_SUPERSET = "Superserie A · ronda 2 de 3"
    const val EYEBROW_PLAIN = "Ejercicio 3 de 6 · Torso A"
    const val DETAIL = "Mancuernas · Agarre neutro"
    const val TARGET = "Objetivo 4 × 6–8 · RIR 2"
    const val LAST = "Última vez 80 kg × 8"
    const val PROTOCOL = "Drop-set"
    const val PACE_AHEAD = "Vas por delante"
    const val CLOCK = "24:31"
    val DONE = listOf(true, true, false, false, false, false)
}
