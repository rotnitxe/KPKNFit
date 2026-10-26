package com.example.kpkn.domain.workout

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.performedPurpose
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.plannedRepAnchor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure load-suggestion helpers for live workout sessions.
 * Android/Room lookups stay in the ViewModel adapter.
 */
object LoadSuggestionEngine {

    /** Native recipes own their §12.4 progression; unresolved manual loads also bypass generic suggestions. */
    fun shouldDeferToNativeProgression(exercise: Exercise, setIdx: Int, side: String? = null): Boolean {
        if (exercise.nativeProgressionManaged) return true
        val requiredSides = exercise.sets.getOrNull(setIdx)?.manualLoadRequiredSides.orEmpty()
        return if (side.isNullOrBlank()) {
            requiredSides.isNotEmpty()
        } else {
            side in requiredSides || "bilateral" in requiredSides
        }
    }

    fun roundLoad(weight: Double, incrementKg: Double = 0.5): Double {
        require(incrementKg.isFinite() && incrementKg > 0.0)
        if (weight <= 0.0) return 0.0
        return ((weight / incrementKg).roundToInt() * incrementKg).coerceAtLeast(incrementKg)
    }

    fun inputLoad(set: CompletedSet, loadMode: LoadModeV2): Double {
        val payload = set.recordedPayloadV3
        set.originalLoad?.let { return if (loadMode == LoadModeV2.BODYWEIGHT) 0.0 else it }
        return when (loadMode) {
            LoadModeV2.ASSISTED -> payload?.assistedLoad ?: set.weight
            LoadModeV2.BODYWEIGHT -> 0.0
            else -> payload?.externalLoad ?: set.weight
        }
    }

    /** Resolves the load mode that was actually logged for [set]. */
    fun resolvedLoadMode(set: CompletedSet): LoadModeV2 =
        set.loadMode ?: set.recordedPayloadV3?.loadInputMode
            ?: set.homologatedResultV3?.loadMode
            ?: LoadModeV2.LOAD

    /**
     * Descriptive estimated load capacity; never a body-state or automatic prescription.
     * ASSISTED and BODYWEIGHT must not use the homologation pseudo-metric
     * `(200 - assistance)` as if it were a barbell 1RM.
     */
    fun estimatedCapacity(set: CompletedSet): Double? {
        val mode = resolvedLoadMode(set)
        val unit = set.recordedPayloadV3?.unitMode ?: set.homologatedResultV3?.unitMode ?: UnitModeV2.REPS
        if (unit != UnitModeV2.REPS || mode == LoadModeV2.ASSISTED || mode == LoadModeV2.BODYWEIGHT) return null
        set.homologatedResultV3?.takeIf { it.estimatorVersion >= com.example.kpkn.domain.calculations.WORKOUT_DERIVED_ESTIMATOR_VERSION }?.estimatedRm?.takeIf { it > 0.0 }?.let { return it }
        // Sin valor guardado: modelo único con el esfuerzo registrado, con el mismo rango de repeticiones de siempre.
        return if (set.weight > 0.0 && set.reps > 0) {
            StrengthModel.estimate1RM(set).takeIf { it > 0.0 }
        } else {
            null
        }
    }

}
