package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.generator.Prescriber
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

data class RpeTopSetHit(
    val liftSlot: LiftSlot,
    val prescribedReps: Int,
    val prescribedRpe: Double,
    val actualWeightKg: Double,
    val actualReps: Int,
    val actualRpe: Double,
)

data class RpeTopSetOutcome(
    val liftSlot: LiftSlot,
    val estimatedOneRmKg: Double,
    val impliedTrainingMaxKg: Double,
    val tmDeltaKg: Double,
    val explanation: String,
)

/** Propuesta del protocolo a partir de un top set registrado; nunca cambia una referencia por sí sola. */
object RpeTopSetEngine {
    const val MAX_TM_STEP_FRACTION = 0.05
    const val DEAD_BAND_KG = 1.0

    fun estimateOneRm(weightKg: Double, reps: Int, rpe: Double): Double {
        if (!weightKg.isFinite() || weightKg <= 0.0 || reps <= 0 || !rpe.isFinite() || rpe !in 1.0..10.0) return 0.0
        return StrengthModel.estimate1RM(weight = weightKg, reps = reps, rpe = rpe)
    }

    fun evaluate(hit: RpeTopSetHit, currentTmKg: Double?, trainingMaxPercent: Double): RpeTopSetOutcome? {
        if (currentTmKg == null || !currentTmKg.isFinite() || currentTmKg <= 0.0 ||
            !trainingMaxPercent.isFinite() || trainingMaxPercent <= 0.0 || trainingMaxPercent > 1.0) return null
        val estimated = estimateOneRm(hit.actualWeightKg, hit.actualReps, hit.actualRpe)
        if (!estimated.isFinite() || estimated <= 0.0) return null
        val implied = Prescriber.roundLoad(estimated * trainingMaxPercent)
        val cap = currentTmKg * MAX_TM_STEP_FRACTION
        val raw = implied - currentTmKg
        val delta = raw.coerceIn(-cap, cap)
        // roundLoad tiene mínimo de 2,5 kg: no inventar un incremento y no redondear por encima del tope.
        val rounded = if (abs(delta) < DEAD_BAND_KG) 0.0 else
            minOf(Prescriber.roundLoad(abs(delta)), floor(cap / 2.5) * 2.5) * sign(delta)
        val explanation = "Top set a RPE ${format(hit.actualRpe)}: ${format(hit.actualWeightKg)} kg × ${hit.actualReps} ⇒ " +
            "1RM estimado ${format(estimated)} kg; " +
            if (rounded == 0.0) "el TM se mantiene." else "TM ${if (rounded > 0) "+" else ""}${format(rounded)} kg."
        return RpeTopSetOutcome(hit.liftSlot, estimated, implied, rounded, explanation)
    }

    private fun format(value: Double): String =
        (if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)).replace('.', ',')
}
