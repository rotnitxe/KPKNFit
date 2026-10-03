package com.example.kpkn.domain.text

/**
 * Concordancia de número en español para los textos que llevan una cantidad
 * («1 día», «3 días», «0 series»). Dominio puro: sin `android.*` ni recursos.
 *
 * Regla del español: SOLO el uno va en singular. El cero y cualquier otra
 * cantidad van en plural («0 días», «2 días»). El proyecto no usa recursos
 * `<plurals>`, así que este ayudante es la única fuente de la concordancia.
 *
 * Hay dos usos:
 * - Cantidad con su sustantivo: [withNoun] y los atajos ([days], [sets]…).
 * - Frase completa que cambia de forma con el uno (no solo la palabra): [choose],
 *   p. ej. «Encaja con tu semana de 1 día» frente a «Encaja con tus 3 días por semana».
 */
object SpanishPlurals {

    /** true solo para la cantidad uno (también −1); el cero, el dos y los demás piden plural. */
    fun isSingular(quantity: Int): Boolean = quantity == 1 || quantity == -1

    /**
     * Elige la forma que corresponde a [quantity]. Sirve para una palabra
     * («elegido» / «elegidos») o para una frase completa cuando el uno cambia
     * más que el sustantivo.
     */
    fun choose(quantity: Int, singular: String, plural: String): String =
        if (isSingular(quantity)) singular else plural

    /** Cantidad con su sustantivo concordado: «1 día», «3 días», «0 días». */
    fun withNoun(quantity: Int, singular: String, plural: String): String =
        "$quantity ${choose(quantity, singular, plural)}"

    fun days(quantity: Int): String = withNoun(quantity, "día", "días")

    fun weeks(quantity: Int): String = withNoun(quantity, "semana", "semanas")

    fun sessions(quantity: Int): String = withNoun(quantity, "sesión", "sesiones")

    fun exercises(quantity: Int): String = withNoun(quantity, "ejercicio", "ejercicios")

    /** Series de un ejercicio: «1 serie», «3 series». */
    fun sets(quantity: Int): String = withNoun(quantity, "serie", "series")

    /** Abreviatura habitual de «repeticiones»: 1 rep, 8 reps. */
    fun reps(quantity: Int): String = withNoun(quantity, "rep", "reps")

    fun blocks(quantity: Int): String = withNoun(quantity, "bloque", "bloques")

    fun plans(quantity: Int): String = withNoun(quantity, "plan", "planes")

    fun steps(quantity: Int): String = withNoun(quantity, "paso", "pasos")
}
