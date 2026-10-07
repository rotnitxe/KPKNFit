package com.example.kpkn.domain.onboarding

import kotlin.math.roundToInt

/**
 * Valores estables del bloque Entreno v2: los que viajan por `setStepChoice(s)` y los que guardan las opciones del
 * catálogo ([SetupStepDefinitions]). Un solo sitio para traducir entre el valor estable y el tipo del dominio, así el
 * catálogo, los reductores y los controles no repiten literales.
 *
 * Los alias antiguos («strength», «muscle», «complete_athlete», «health», «mixed», «machines», «none»,
 * «bodyweight_only»…) se siguen leyendo para que borradores y llamadas anteriores no se rompan; nunca se ofrecen de nuevo.
 */
object EntrenoStepValues {

    // ── Tiempo por sesión ──────────────────────────────────────────────────────
    const val SESSION_MINUTES_MIN = 20
    const val SESSION_MINUTES_MAX = 180
    const val SESSION_MINUTES_STEP = 5

    /** Minutos válidos más cercanos a [value]: dentro del rango y múltiplo de [SESSION_MINUTES_STEP]. */
    fun roundSessionMinutes(value: Double): Int {
        val clamped = value.coerceIn(SESSION_MINUTES_MIN.toDouble(), SESSION_MINUTES_MAX.toDouble())
        val stepped = (clamped / SESSION_MINUTES_STEP).roundToInt() * SESSION_MINUTES_STEP
        return stepped.coerceIn(SESSION_MINUTES_MIN, SESSION_MINUTES_MAX)
    }

    // ── Lugares ────────────────────────────────────────────────────────────────
    fun placeValue(place: TrainingPlace): String = place.name.lowercase()

    /** Lugar de un valor estable; los entornos antiguos de un solo valor se leen como el lugar que hoy les corresponde. */
    fun placeOf(value: String): TrainingPlace? = when (value.trim().lowercase()) {
        "gym", "machines", "gimnasio completo", "principalmente máquinas" -> TrainingPlace.GYM
        "home", "none", "entreno en casa", "sin material" -> TrainingPlace.HOME
        "public" -> TrainingPlace.PUBLIC
        else -> null
    }

    // ── Objetivo ───────────────────────────────────────────────────────────────
    fun goalValue(profile: TrainingGoalProfile): String = profile.name.lowercase()

    /**
     * Perfil de un valor estable. Los valores antiguos del paso (cuatro objetivos más Salud y Fuerza + cardio) se leen
     * como el perfil que hoy les corresponde: Fuerza → Powerlifting, Músculo → Culturismo, Atleta completo y
     * Fuerza + cardio → Fuerza y cardio, Salud → Funcional y saludable.
     */
    fun goalProfileOf(value: String): TrainingGoalProfile? {
        val key = value.trim().lowercase()
        TrainingGoalProfile.entries.firstOrNull { goalValue(it) == key }?.let { return it }
        return when (key) {
            "strength" -> TrainingGoalProfile.POWERLIFTING
            "muscle" -> TrainingGoalProfile.BODYBUILDING
            "complete_athlete", "mixed" -> TrainingGoalProfile.STRENGTH_CARDIO
            "health" -> TrainingGoalProfile.FUNCTIONAL_HEALTH
            else -> null
        }
    }

    // ── Material ───────────────────────────────────────────────────────────────
    /** Símbolo de material de un valor estable (su nombre); «bodyweight_only» y los nombres antiguos de categoría se leen igual. */
    fun symbolOf(value: String): EquipmentSymbolId? {
        val key = value.trim()
        EquipmentSymbolId.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.let { return it }
        return when (key.uppercase()) {
            "BAND" -> EquipmentSymbolId.BANDS
            "SMITH_MACHINE" -> EquipmentSymbolId.SMITH
            else -> null
        }
    }

    // ── Días ───────────────────────────────────────────────────────────────────
    /** Día de la semana de un valor estable («1» lunes … «7» domingo). */
    fun weekdayOf(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it in 1..7 }

    /**
     * Una semana repartida de [count] días (de 1 a 7), con descanso entre ellos mientras se pueda: lo que se siembra
     * cuando un programa de días fijos pide esa frecuencia. Es una sugerencia; la persona confirma sus días.
     */
    fun defaultWeekdays(count: Int): Set<Int> = when (count.coerceIn(1, 7)) {
        1 -> setOf(1)
        2 -> setOf(1, 4)
        3 -> setOf(1, 3, 5)
        4 -> setOf(1, 2, 4, 5)
        5 -> setOf(1, 2, 3, 4, 5)
        6 -> setOf(1, 2, 3, 4, 5, 6)
        else -> setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
