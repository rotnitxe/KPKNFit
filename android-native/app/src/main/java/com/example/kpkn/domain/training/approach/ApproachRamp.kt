package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.WarmupSetDefinition

/**
 * Rampa de aproximación sobre la carga de trabajo (Entreno v2): porcentajes de la carga de trabajo del propio
 * ejercicio (nunca del 1RM), con descansos cortos (30–60 s) para que calentar no se coma la sesión. Es la misma
 * forma que `PlanMaterializer.realizeWarmupLoads` ya convierte en kilos alcanzables con discos y mancuernas.
 *
 * - **Rampa larga** (primer ejercicio pesado): novato 3 pasos (40 %×8, 60 %×5, 80 %×3); intermedio 3 pasos; avanzado
 *   3 pasos con menos repeticiones. Con series de trabajo ≥ 90 % se añade el escalón 90 %×1 (intermedio y avanzado).
 * - **Rampa corta** (primer ejercicio de carga moderada, o segundo/tercero pesado con una articulación nueva): 1–2
 *   pasos, porque el cuerpo ya está caliente.
 */
internal object ApproachRamp {

    /** Series de trabajo a partir de este % se consideran casi máximas: la rampa añade un escalón 90 %×1. */
    const val VERY_HEAVY_PERCENT = 90.0

    data class Step(val percent: Double, val reps: Int, val restSeconds: Int)

    fun long(level: ApproachLevel, veryHeavy: Boolean): List<Step> {
        val base = when (level) {
            ApproachLevel.NOVICE, ApproachLevel.INTERMEDIATE -> listOf(
                Step(40.0, 8, 30),
                Step(60.0, 5, 45),
                Step(80.0, 3, 60),
            )
            ApproachLevel.ADVANCED -> listOf(
                Step(40.0, 6, 30),
                Step(60.0, 4, 45),
                Step(80.0, 2, 60),
            )
        }
        val withSingle = veryHeavy && level != ApproachLevel.NOVICE
        return if (withSingle) base + SINGLE else base
    }

    fun short(level: ApproachLevel, veryHeavy: Boolean): List<Step> = when (level) {
        ApproachLevel.NOVICE -> listOf(Step(50.0, 6, 30), Step(75.0, 3, 45))
        ApproachLevel.INTERMEDIATE -> listOf(Step(50.0, 5, 30), Step(75.0, 3, 45))
        ApproachLevel.ADVANCED -> if (veryHeavy) listOf(Step(70.0, 3, 45), SINGLE) else listOf(Step(70.0, 3, 45))
    }

    /** Las definiciones listas para `Exercise.warmupSets`, con ids estables derivados del ejercicio. */
    fun definitions(exerciseId: String, steps: List<Step>): List<WarmupSetDefinition> =
        steps.mapIndexed { index, step ->
            WarmupSetDefinition(
                id = "$exerciseId#ap:$index",
                percentageOfWorkingWeight = step.percent,
                targetReps = step.reps,
                restBetween = step.restSeconds,
            )
        }

    private val SINGLE = Step(90.0, 1, 60)
}
