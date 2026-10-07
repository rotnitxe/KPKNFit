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
