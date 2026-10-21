package com.example.kpkn.data.media

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isEffectivelyUnilateral

/**
 * Normalización de la lateralidad (lado) de un medio grabado en una serie.
 * - En ejercicios bilaterales el lado se normaliza a `null`.
 * - En unilaterales, normaliza «l» / «left» a «left», y «r» / «right» a «right».
 * - [token] extrae el token en mayúscula («L», «R») para componer claves de serie.
 */
object WorkoutMediaSides {
    /**
     * Devuelve el lado canónico («left» o «right»), o `null` si el ejercicio es bilateral o no hay lado.
     */
    fun normalize(side: String?, isUnilateral: Boolean): String? {
        if (!isUnilateral) return null
        val raw = side?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (raw.lowercase()) {
            "l", "left" -> "left"
            "r", "right" -> "right"
            else -> raw.lowercase()
        }
    }

    /**
     * Sobrecarga para [Exercise]: consulta si el ejercicio es efectivamente unilateral.
     */
    fun normalize(side: String?, exercise: Exercise?): String? =
        normalize(side, exercise?.isEffectivelyUnilateral() == true)

    /**
     * Token de lateralidad para sufijos de clave de serie (p. ej. "L", "R").
     * Extraído de `WorkoutMediaPrFlags.sideToken`.
     */
    fun token(side: String?): String? {
        val raw = side?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (raw.lowercase()) {
            "l", "left" -> "L"
            "r", "right" -> "R"
            else -> raw.take(1).uppercase()
        }
    }
}
