package com.example.kpkn.domain.training

import kotlin.math.floor

/**
 * 5/3/1 (CAL5, RES1 §3, fuentes secundarias salvo la condición de las repeticiones, que es de Wendler):
 * - la serie «+» NO mueve el TM: registra el récord de repeticiones y el e1RM de Wendler (`peso × reps × 0,0333 + peso`,
 *   la misma cuenta que Epley);
 * - el TM sube un salto fijo por ciclo (2,5 kg en el tren superior, 5 kg en el inferior) si se cumplieron las repeticiones
 *   mínimas con buena técnica; si no, se congela sin ninguna bajada del 2,5 %;
 * - tras dos ciclos SEGUIDOS congelados el TM se reinicia un 10 % (Wendler: volver al 90 % del TM y recalcular; el umbral de
 *   dos ciclos es decisión de producto, `CalibrationPolicy.fiveThreeOneResetAfterFrozenCycles`).
 * Dominio puro.
 */
object Wendler531Progression {
    /** Constante de la cuenta de Wendler para el e1RM de la serie «+». */
    const val E1RM_FACTOR = 0.0333

    /** El e1RM de la serie «+» como lo calcula Wendler. */
    fun e1rmKg(weightKg: Double, reps: Int): Double =
        if (weightKg <= 0.0 || reps < 1) 0.0 else weightKg * reps * E1RM_FACTOR + weightKg

    /**
     * true si este ciclo congelado debe reiniciar el TM: con [resetAfter] ciclos seguidos (2) en la racha 2, 4, 6… La cuenta
     * vuelve a empezar tras cada reinicio. [resetAfter] ≤ 0 = esta regla no reinicia.
     */
    fun resetsAtFrozenStreak(streak: Int, resetAfter: Int): Boolean =
        resetAfter > 0 && streak > 0 && streak % resetAfter == 0

    /** El TM tras el reinicio: [tmKg] menos [resetPercent] %, al múltiplo más cercano de [stepKg] y nunca por encima del TM. */
    fun resetTmKg(tmKg: Double, resetPercent: Double, stepKg: Double): Double {
        val target = tmKg * (1.0 - resetPercent / 100.0)
        val rounded = if (stepKg > 0.0) floor(target / stepKg + 0.5) * stepKg else target
        return minOf(rounded, tmKg)
    }
}
