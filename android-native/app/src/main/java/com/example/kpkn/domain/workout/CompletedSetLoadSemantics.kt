package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.effectiveRepEquivalent

/**
 * Single read-side contract for recorded load modalities.
 *
 * `CompletedSet.weight` is the external load entered by the athlete for LOAD/LASTRE and
 * zero for BODYWEIGHT/ASSISTED. Body mass is a reference snapshot, never a lifted load.
 * Legacy sets without a payload keep their historical external load reading.
 */
object CompletedSetLoadSemantics {
    fun mode(set: CompletedSet): LoadModeV2? = set.loadMode
        ?: set.recordedPayloadV3?.loadInputMode
        ?: set.homologatedResultV3?.loadMode
        ?: set.setOutcomeV2?.loadMode

    fun bodyweightKg(set: CompletedSet): Double? =
        set.recordedPayloadV3?.bodyWeightSnapshot?.takeIf { it.isFinite() && it > 0.0 }

    /** External kg used by tonnage, external-load records and comparable 1RM. */
    fun externalLoadKg(set: CompletedSet): Double? = when (mode(set)) {
        LoadModeV2.BODYWEIGHT, LoadModeV2.ASSISTED -> 0.0
        LoadModeV2.LASTRE -> set.recordedPayloadV3?.externalLoad?.takeIf { it.isFinite() && it >= 0.0 }
            ?: set.originalLoad?.takeIf { it.isFinite() && it >= 0.0 }
        LoadModeV2.LOAD -> set.recordedPayloadV3?.externalLoad?.takeIf { it.isFinite() && it >= 0.0 }
            ?: set.originalLoad?.takeIf { it.isFinite() && it >= 0.0 }
            ?: set.weight.takeIf { it.isFinite() && it >= 0.0 }
        null -> set.weight.takeIf { it.isFinite() && it >= 0.0 }
    }

    /** Assistance entered by the athlete; null when it was not preserved. */
    fun assistanceKg(set: CompletedSet): Double? = when (mode(set)) {
        LoadModeV2.ASSISTED -> set.recordedPayloadV3?.assistedLoad?.takeIf { it.isFinite() && it >= 0.0 }
            ?: set.originalLoad?.takeIf { it.isFinite() && it >= 0.0 }
        else -> null
    }

    /**
     * Only conventional external load is eligible for an estimated 1RM in kg.
     * LASTRE is compared by added kg at equal reps; BODYWEIGHT by reps/time; ASSISTED by
     * lower assistance at equal reps. Legacy sets without modality evidence retain the
     * historical external reading.
     */
    fun supportsEstimatedOneRm(set: CompletedSet): Boolean = when (mode(set)) {
        null, LoadModeV2.LOAD -> true
        LoadModeV2.BODYWEIGHT, LoadModeV2.LASTRE, LoadModeV2.ASSISTED -> false
    }

    /** Tonnage for the main work set, including the external load only. */
    fun externalTonnageKg(set: CompletedSet, effectiveReps: Double): Double =
        (externalLoadKg(set) ?: 0.0) * effectiveReps.coerceAtLeast(0.0)

    /** Drop sets carry their own entered external load; body mass is never added. */
    fun dropTonnageKg(set: CompletedSet, dropWeight: Double, reps: Int): Double = when (mode(set)) {
        LoadModeV2.BODYWEIGHT, LoadModeV2.ASSISTED -> 0.0
        else -> dropWeight.takeIf { it.isFinite() && it >= 0.0 }?.times(reps.coerceAtLeast(0)) ?: 0.0
    }

    /** Rest-pause reps reuse the external load of the main set. */
    fun restPauseTonnageKg(set: CompletedSet, reps: Int): Double =
        (externalLoadKg(set) ?: 0.0) * reps.coerceAtLeast(0)

    /** External tonnage for one set, including valid drops and rest-pauses. */
    fun totalExternalTonnageKg(
        set: CompletedSet,
        effectiveReps: Double = set.effectiveRepEquivalent(),
    ): Double = externalTonnageKg(set, effectiveReps) +
        set.dropSets.sumOf { drop -> dropTonnageKg(set, drop.weight, drop.reps) } +
        set.restPauses.sumOf { rp -> restPauseTonnageKg(set, rp.reps) }

    /** Recalculates session external tonnage directly from completed working sets. */
    fun sessionExternalTonnageKg(exercises: List<CompletedExercise>): Double =
        exercises.sumOf { ex ->
            ex.sets.filter { !it.skipped && !it.isWarmup }.sumOf { set ->
                totalExternalTonnageKg(set, set.effectiveRepEquivalent())
            }
        }

    /** Recomputes historical tonnage from available sets when set-level evidence exists. */
    fun historicalLogTonnageKg(log: WorkoutLog): Double =
        if (log.completedExercises.isEmpty()) {
            log.totalVolume.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        } else {
            sessionExternalTonnageKg(log.completedExercises)
        }

    fun displayLoad(set: CompletedSet): String = when (mode(set)) {
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> externalLoadKg(set)?.takeIf { it > 0.0 }
            ?.let { "Peso corporal + ${formatKg(it)} kg de lastre" }
            ?: "Peso corporal + lastre · Carga sin detalle"
        LoadModeV2.ASSISTED -> assistanceKg(set)?.takeIf { it > 0.0 }
            ?.let { "${formatKg(it)} kg de asistencia" }
            ?: "Asistencia · Carga sin detalle"
        LoadModeV2.LOAD, null -> externalLoadKg(set)?.takeIf { it > 0.0 }
            ?.let { "${formatKg(it)} kg" }
            ?: "Carga sin detalle"
    }

    fun displaySet(set: CompletedSet): String {
        val quantity = set.timeSeconds?.takeIf { it > 0 }?.let { "$it s" }
            ?: "${set.reps.coerceAtLeast(0)} reps"
        return "${displayLoad(set)} · $quantity"
    }

    private fun formatKg(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')
}
