package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.*

/** Resolves authored load bases only. No energy, body-state coefficients or physiological estimates. */
object TrainingLoadEvidence {
    fun resolvePlannedExternalLoadKg(
        exercise: Exercise,
        set: ExerciseSet,
        @Suppress("UNUSED_PARAMETER") weightUnit: WeightUnit,
    ): ResolvedPlannedLoad {
        set.weight?.takeIf { it > 0.0 }?.let { raw ->
            return ResolvedPlannedLoad(raw, ResolvedLoadSource.EXPLICIT_WEIGHT)
        }

        // El %RM se lee sobre la referencia de SU base: una serie PERCENT_TM sobre el TM declarado (sin TM no se
        // multiplica por el 1RM); las demás bases, sobre la capacidad/1RM.
        val usesTrainingMaxPercent = set.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM
        if (usesTrainingMaxPercent) {
            com.example.kpkn.domain.calculations.percentLoadKg(exercise, set)?.let { kg ->
                return ResolvedPlannedLoad(externalLoadKg = kg, source = ResolvedLoadSource.PERCENT_1RM)
            }
        }
        val reference = resolveReferenceCapacity(exercise)
        if (reference != null && reference > 0.0) {
            if (!usesTrainingMaxPercent) {
                set.targetPercentageRM?.takeIf { it > 0.0 }?.let { pct ->
                    return ResolvedPlannedLoad(
                        externalLoadKg = reference * pct / 100.0,
                        source = ResolvedLoadSource.PERCENT_1RM,
                    )
                }
            }
            calculateWeightFrom1RMAndIntensity(reference, set)?.takeIf { it > 0.0 }?.let { raw ->
                return ResolvedPlannedLoad(raw, ResolvedLoadSource.ONE_RM_INTENSITY)
            }
            calculateSuggestedLoad(exercise, set)?.takeIf { it > 0.0 }?.let { raw ->
                return ResolvedPlannedLoad(raw, ResolvedLoadSource.SUGGESTED_FROM_1RM)
            }
        }

        val loadMode = set.loadModeV2
        if (loadMode == LoadModeV2.BODYWEIGHT) {
            return ResolvedPlannedLoad(0.0, ResolvedLoadSource.BODYWEIGHT_ONLY)
        }

        return ResolvedPlannedLoad(null, ResolvedLoadSource.MISSING)
    }

    enum class ResolvedLoadSource {
        EXPLICIT_WEIGHT,
        PERCENT_1RM,
        ONE_RM_INTENSITY,
        SUGGESTED_FROM_1RM,
        BODYWEIGHT_ONLY,
        MISSING,
    }

    data class ResolvedPlannedLoad(
        val externalLoadKg: Double?,
        val source: ResolvedLoadSource,
    )

}
