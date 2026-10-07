package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.calculations.calculateWeightFrom1RM
import kotlin.math.roundToInt

/**
 * Prescripción base de un ejercicio (antes de ajustar a minutos): repeticiones objetivo, esfuerzo, series y descanso con
 * sus límites. Los límites son lo que el ajuste a minutos puede mover: nunca baja de [minSets] ni de [minRest] ni sube de
 * [maxSets] ni de [maxRest].
 *
 * Criterios de dominio (los que se decidieron distinto al brief están en `docs/entreno-v2` del informe del paquete):
 * - Fuerza (compuestos pesados): 3–6 repeticiones con RIR 2 para quien ya entrena; el novato y quien vuelve usan 6–10
 *   repeticiones con RIR 3 (técnica antes que carga).
 * - Hipertrofia (compuestos de apoyo y accesorios): 6–12 repeticiones, RIR 1–3. Aislamiento: 10–15.
 * - Funcional: 8–15 repeticiones; potencia: 3–5 explosivas (RIR 4–5) o 10–15 en balísticos con kettlebell.
 * - Descansos mínimos por tipo (nunca se acortan por debajo): pesado 150 s, compuesto 90 s, aislamiento 45 s.
 */
internal data class Rx(
    val repMin: Int,
    val repMax: Int,
    /** Segundos por serie en isométricos y acarreos; null = por repeticiones. */
    val seconds: Int?,
    val rir: Int,
    val baseSets: Int,
    val minSets: Int,
    val maxSets: Int,
    val rest: Int,
    val minRest: Int,
    val maxRest: Int,
    /** Carga alta (≤ 6 repeticiones): admite aproximaciones y descansos largos. */
    val heavy: Boolean,
)

internal object Prescriber {

    const val MIN_REST_HEAVY = 150
    const val MIN_REST_COMPOUND = 90
    const val MIN_REST_ISOLATION = 45

    fun rx(
        kind: ExKind,
        role: ItemRole,
        level: RoutineLevel,
        mode: RoutineMode,
        pattern: RoutinePattern,
        rungReps: IntRange? = null,
        rungSeconds: Int? = null,
        entryReps: IntRange? = null,
    ): Rx {
        DisciplineRx.rx(kind, role, level, mode, pattern, rungReps, rungSeconds, entryReps)?.let { return it }
        val functional = mode == RoutineMode.GENERAL_FUNCTIONAL
        val learning = level == RoutineLevel.NOVICE || level == RoutineLevel.RETURNING
        val isMain = role == ItemRole.MAIN
        return when (kind) {
            ExKind.HEAVY -> when {
                functional -> compound(level, isMain, rungReps ?: 8..12, functional = true)
                level == RoutineLevel.NOVICE -> Rx(8, 10, null, 3, if (isMain) 3 else 3, 2, 4, 120, 90, 180, heavy = false)
                level == RoutineLevel.RETURNING -> Rx(6, 8, null, 3, 3, 2, 4, 150, 120, 210, heavy = false)
                level == RoutineLevel.INTERMEDIATE -> Rx(4, 6, null, 2, if (isMain) 4 else 3, 2, 5, 180, 150, 270, heavy = true)
                else -> Rx(3, 5, null, 2, if (isMain) 4 else 3, 2, 6, 210, 150, 300, heavy = true)
            }
            ExKind.COMPOUND -> compound(level, isMain, rungReps ?: if (functional) 8..12 else null, functional)
            ExKind.ISOLATION -> {
                val reps = rungReps ?: if (pattern == RoutinePattern.CALF) 12..20 else 10..15
                val sets = if (learning || functional) 2 else 3
                Rx(reps.first, reps.last, null, if (level == RoutineLevel.ADVANCED) 1 else if (learning) 3 else 2, sets, 2, if (learning) 3 else 4, 60, MIN_REST_ISOLATION, 90, heavy = false)
            }
            ExKind.CORE_DYNAMIC -> {
                val reps = rungReps ?: 10..15
                Rx(reps.first, reps.last, null, 2, if (level == RoutineLevel.NOVICE || functional) 2 else 3, 2, 4, 45, MIN_REST_ISOLATION, 75, heavy = false)
            }
            ExKind.TIMED -> {
                val carry = pattern == RoutinePattern.CARRY
                val seconds = rungSeconds ?: if (carry) 35 else if (level == RoutineLevel.NOVICE) 25 else 40
                Rx(0, 0, seconds, 2, if (level == RoutineLevel.NOVICE || functional) 2 else 3, 2, 4, if (carry) 60 else 45, MIN_REST_ISOLATION, 90, heavy = false)
            }
            ExKind.BALLISTIC -> {
                val reps = entryReps ?: rungReps ?: 10..15
                val explosive = reps.last <= 5
                Rx(reps.first, reps.last, null, 4, 3, 2, 5, if (explosive) 120 else 75, if (explosive) MIN_REST_COMPOUND else 60, if (explosive) 150 else 120, heavy = false)
            }
        }
    }

    /**
     * Compuesto. En el modo funcional las series son submáximas (RIR 3) y caben más ejercicios por sesión: 3 series el principal
     * y 2 el resto (la sesión de cuerpo completo lleva potencia, sentadilla, bisagra, empuje, tirón, acarreo y rotación).
     */
    private fun compound(level: RoutineLevel, isMain: Boolean, reps: IntRange?, functional: Boolean = false): Rx {
        val range = reps ?: when (level) {
            RoutineLevel.NOVICE, RoutineLevel.RETURNING -> 8..12
            else -> 6..10
        }
        if (functional) {
            val base = if (isMain && level != RoutineLevel.NOVICE) 3 else 2
            return Rx(range.first, range.last, null, 3, base, 2, if (isMain) 4 else 3, 90, MIN_REST_COMPOUND, 150, heavy = false)
        }
        return when (level) {
            RoutineLevel.NOVICE -> Rx(range.first, range.last, null, 3, 3, 2, 4, 90, MIN_REST_COMPOUND, 150, heavy = false)
            RoutineLevel.RETURNING -> Rx(range.first, range.last, null, 3, 3, 2, 4, 105, MIN_REST_COMPOUND, 150, heavy = false)
            RoutineLevel.INTERMEDIATE -> Rx(range.first, range.last, null, 2, if (isMain) 4 else 3, 2, if (isMain) 5 else 4, 120, MIN_REST_COMPOUND, 180, heavy = false)
            RoutineLevel.ADVANCED -> Rx(range.first, range.last, null, 2, if (isMain) 4 else 3, 2, if (isMain) 5 else 4, 120, MIN_REST_COMPOUND, 180, heavy = false)
        }
    }

    /** Porcentaje del 1RM que corresponde a [repMax] repeticiones con [rir] en reserva (Brzycki/híbrida de la app), a pasos de 2,5 %. */
    fun percentOfOneRm(repMax: Int, rir: Int): Double {
        val effective = (repMax + rir).coerceIn(1, 20)
        val raw = calculateWeightFrom1RM(100.0, effective)
        val stepped = (raw / 2.5).roundToInt() * 2.5
        return stepped.coerceIn(50.0, 95.0)
    }

    /** Carga en kg a pasos de 2,5 kg. */
    fun roundLoad(kg: Double): Double = ((kg / 2.5).roundToInt() * 2.5).coerceAtLeast(2.5)
}

/**
 * Prescripción propia de los modos de disciplina (Fase 2): series, repeticiones y descansos de la disciplina. Devuelve null
 * cuando el tipo de ejercicio se prescribe como en el modo general (core, isométricos de peso corporal, escaleras…).
 *
 * - **Powerlifting**: levantamientos de competición de 2–6 repeticiones (5–6 para quien aprende), 4–6 series y descansos de
 *   3–5 min; las variantes y los accesorios con menos peso y más repeticiones.
 * - **Culturismo**: compuestos de 6–12 repeticiones a 1–2 de reserva, aislamientos de 10–15 con 4–5 series y descansos cortos.
 * - **Strongman**: peso muerto, sentadilla y press de 3–5 repeticiones; acarreos de 30–45 s con descansos largos.
 * - **Halterofilia (base)**: sentadilla y tirón de 3–5 repeticiones; push press y swing explosivos.
 * - **Armwrestling**: trabajo de muñeca y antebrazo de 10–15 repeticiones con 4 series; agarres isométricos de 30–40 s.
 */
internal object DisciplineRx {

    fun rx(
        kind: ExKind,
        role: ItemRole,
        level: RoutineLevel,
        mode: RoutineMode,
        pattern: RoutinePattern,
        rungReps: IntRange?,
        rungSeconds: Int?,
        entryReps: IntRange?,
    ): Rx? {
        val isMain = role == ItemRole.MAIN
        val learning = level == RoutineLevel.NOVICE || level == RoutineLevel.RETURNING
        return when (mode) {
            RoutineMode.CUSTOM_POWERLIFTING -> powerlifting(kind, isMain, level, learning)
            RoutineMode.CUSTOM_BODYBUILDING -> bodybuilding(kind, isMain, level, learning, pattern, rungReps)
            RoutineMode.DISCIPLINE_STRONGMAN -> strongman(kind, isMain, learning, pattern, rungSeconds, entryReps)
            RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE -> weightlifting(kind, isMain, learning, pattern, entryReps, rungReps)
            RoutineMode.DISCIPLINE_ARMWRESTLING -> armwrestling(kind, learning, rungSeconds)
            else -> null
        }
    }

    private fun powerlifting(kind: ExKind, isMain: Boolean, level: RoutineLevel, learning: Boolean): Rx? = when (kind) {
        ExKind.HEAVY -> {
            val reps = when (level) {
                RoutineLevel.NOVICE, RoutineLevel.RETURNING -> 5..6
                RoutineLevel.INTERMEDIATE -> 3..5
                RoutineLevel.ADVANCED -> 2..4
            }
            val base = if (isMain) (if (learning) 4 else 5) else (if (learning) 3 else 4)
            Rx(reps.first, reps.last, null, if (learning) 3 else 2, base, 2, if (isMain) 6 else 5, if (isMain) 240 else 180, Prescriber.MIN_REST_HEAVY, 300, heavy = true)
        }
        ExKind.COMPOUND -> Rx(5, 8, null, 2, 3, 2, 5, 120, Prescriber.MIN_REST_COMPOUND, 180, heavy = false)
        ExKind.ISOLATION -> Rx(8, 12, null, 2, if (learning) 2 else 3, 2, 4, 75, Prescriber.MIN_REST_ISOLATION, 90, heavy = false)
        else -> null
    }

    private fun bodybuilding(kind: ExKind, isMain: Boolean, level: RoutineLevel, learning: Boolean, pattern: RoutinePattern, rungReps: IntRange?): Rx? = when (kind) {
        ExKind.HEAVY -> {
            val reps = if (isMain) 6..10 else 8..12
            Rx(reps.first, reps.last, null, if (learning) 3 else 1, if (isMain) 4 else 3, 2, if (isMain) 5 else 4, 120, Prescriber.MIN_REST_COMPOUND, 180, heavy = false)
        }
        ExKind.COMPOUND -> {
            val reps = rungReps ?: if (isMain) 6..10 else 8..12
            Rx(reps.first, reps.last, null, if (learning) 3 else if (isMain) 1 else 2, if (isMain) 4 else 3, 2, if (isMain) 5 else 4, 105, Prescriber.MIN_REST_COMPOUND, 150, heavy = false)
        }
        ExKind.ISOLATION -> {
            val reps = rungReps ?: if (pattern == RoutinePattern.CALF) 12..20 else 10..15
            val rir = if (level == RoutineLevel.ADVANCED) 0 else if (learning) 2 else 1
            Rx(reps.first, reps.last, null, rir, if (learning) 3 else 4, 2, if (learning) 4 else 5, 60, Prescriber.MIN_REST_ISOLATION, 90, heavy = false)
        }
        else -> null
    }

    private fun strongman(kind: ExKind, isMain: Boolean, learning: Boolean, pattern: RoutinePattern, rungSeconds: Int?, entryReps: IntRange?): Rx? = when (kind) {
        ExKind.HEAVY -> {
            val reps = if (learning) 5..6 else 3..5
            Rx(reps.first, reps.last, null, 2, if (isMain) 4 else 3, 2, 5, if (isMain) 210 else 180, Prescriber.MIN_REST_HEAVY, 300, heavy = true)
        }
        ExKind.COMPOUND -> Rx(5, 8, null, 2, 3, 2, 5, 120, Prescriber.MIN_REST_COMPOUND, 180, heavy = false)
        ExKind.BALLISTIC -> {
            val reps = entryReps ?: 3..5
            Rx(reps.first, reps.last, null, 4, 4, 2, 6, 150, Prescriber.MIN_REST_COMPOUND, 180, heavy = false)
        }
        ExKind.TIMED -> if (pattern == RoutinePattern.CARRY) {
            Rx(0, 0, rungSeconds ?: if (learning) 30 else 40, 2, 4, 2, 6, 120, Prescriber.MIN_REST_COMPOUND, 150, heavy = false)
        } else {
            null
        }
        ExKind.ISOLATION -> Rx(8, 12, null, 2, 3, 2, 4, 75, Prescriber.MIN_REST_ISOLATION, 90, heavy = false)
        else -> null
    }

    private fun weightlifting(kind: ExKind, isMain: Boolean, learning: Boolean, pattern: RoutinePattern, entryReps: IntRange?, rungReps: IntRange?): Rx? = when (kind) {
        ExKind.HEAVY -> {
            val reps = if (learning) 5..6 else 3..5
            Rx(reps.first, reps.last, null, 2, if (isMain) 4 else 3, 2, 5, 180, Prescriber.MIN_REST_HEAVY, 240, heavy = true)
        }
        ExKind.COMPOUND -> Rx(5, 8, null, 2, 3, 2, 4, 120, Prescriber.MIN_REST_COMPOUND, 180, heavy = false)
        ExKind.BALLISTIC -> {
            val reps = entryReps ?: rungReps ?: 3..5
            val explosive = reps.last <= 5
            Rx(reps.first, reps.last, null, 4, if (explosive) 4 else 3, 2, 6, if (explosive) 120 else 75, if (explosive) Prescriber.MIN_REST_COMPOUND else 60, if (explosive) 180 else 120, heavy = false)
        }
        ExKind.ISOLATION -> Rx(8, 12, null, 2, 3, 2, 4, 75, Prescriber.MIN_REST_ISOLATION, 90, heavy = false)
        else -> null
    }

    private fun armwrestling(kind: ExKind, learning: Boolean, rungSeconds: Int?): Rx? = when (kind) {
        ExKind.ISOLATION -> Rx(10, 15, null, 1, if (learning) 3 else 4, 2, 5, 75, 60, 90, heavy = false)
        ExKind.TIMED -> Rx(0, 0, rungSeconds ?: 35, 2, if (learning) 2 else 3, 2, 5, 75, 60, 90, heavy = false)
        else -> null
    }
}
