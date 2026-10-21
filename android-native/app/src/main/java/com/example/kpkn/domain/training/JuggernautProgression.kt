package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule

/**
 * Juggernaut Method 2.0 (CAL5): el TM nuevo tras cada ola es `TM + (repeticiones del AMRAP de realización − estándar) × kg
 * por repetición`, con 2,5 kg por repetición en sentadilla y peso muerto (5 lb) y 1,25 kg en banca y press militar (2,5 lb) y un
 * tope de 10 repeticiones extra. El estándar es 10, 8, 5 o 3 según la ola (RES1 §5.2: fuentes secundarias, Lift Vault y
 * Liftosaur; el tope de 10 no está confirmado en el libro). Sin superar el estándar el TM no cambia y se avisa de que quizá
 * esté alto. Dominio puro.
 */
object JuggernautProgression {

    enum class Outcome {
        /** Más repeticiones que el estándar: el TM sube. */
        RAISED,

        /** Justo el estándar: el TM no cambia. */
        AT_STANDARD,

        /** Menos que el estándar: el TM no cambia y se avisa de que puede estar alto. */
        BELOW_STANDARD,

        /** Se levantó claramente menos que lo prescrito: las repeticiones no miden el TM. */
        LIGHTER_LOAD,
    }

    data class Result(
        val liftSlot: LiftSlot,
        val standardReps: Int,
        val actualReps: Int,
        /** Repeticiones extra que cuentan, ya con el tope. */
        val countedExtraReps: Int,
        val kgPerRep: Double,
        val deltaKg: Double,
        val outcome: Outcome,
    )

    /** Kilos por repetición sobre el estándar: tren inferior (sentadilla, peso muerto) o superior (banca, press militar). */
    fun kgPerRep(rule: ProgressionRule.RepsOverStandardTm, lift: LiftSlot): Double = when (lift) {
        LiftSlot.SQUAT, LiftSlot.DEADLIFT -> rule.lowerKgPerRep
        LiftSlot.BENCH, LiftSlot.OVERHEAD -> rule.upperKgPerRep
    }

    /**
     * La regla aplicada a [hit], el AMRAP de realización de un levantamiento. null si el hit no trae levantamiento o
     * estándar (el AMRAP de una serie sin mínimo no mide nada).
     */
    fun evaluate(rule: ProgressionRule.RepsOverStandardTm, hit: AmrapHit): Result? {
        val lift = hit.liftSlot ?: return null
        val standard = hit.prescribedReps
        if (standard <= 0) return null
        val perRep = kgPerRep(rule, lift)
        val extra = hit.actualReps - standard
        val lighter = hit.weightRatio != null && hit.weightRatio < ProgramAutoregulationEngine.LIGHTER_LOAD_RATIO
        val outcome = when {
            lighter && extra > 0 -> Outcome.LIGHTER_LOAD
            extra > 0 -> Outcome.RAISED
            extra == 0 -> Outcome.AT_STANDARD
            else -> Outcome.BELOW_STANDARD
        }
        val counted = if (outcome == Outcome.RAISED) extra.coerceAtMost(rule.maxExtraReps) else 0
        return Result(
            liftSlot = lift,
            standardReps = standard,
            actualReps = hit.actualReps,
            countedExtraReps = counted,
            kgPerRep = perRep,
            deltaKg = counted * perRep,
            outcome = outcome,
        )
    }

    /** `TM + (reps − estándar) × kg por repetición`: el TM nuevo sin redondear al material (el motor de autor lo redondea). */
    fun newTmKg(tmKg: Double, rule: ProgressionRule.RepsOverStandardTm, hit: AmrapHit): Double =
        tmKg + (evaluate(rule, hit)?.deltaKg ?: 0.0)

    /** La frase para el atleta: «13 repeticiones con estándar de 5: TM +20 kg» o el aviso de que el TM quizá esté alto. */
    fun summary(result: Result): String {
        val reps = com.example.kpkn.domain.text.SpanishPlurals.reps(result.actualReps)
        val standard = "estándar de ${result.standardReps}"
        return when (result.outcome) {
            Outcome.RAISED -> "$reps con $standard: TM +${NativeProgressionText.formatKg(result.deltaKg)} kg"
            Outcome.AT_STANDARD -> "$reps, justo el $standard: el TM no cambia"
            Outcome.BELOW_STANDARD ->
                "$reps, por debajo del $standard: el TM no cambia y quizá esté alto"
            Outcome.LIGHTER_LOAD ->
                "$reps con menos carga que la prescrita: no miden el TM, que no cambia"
        }
    }
}
