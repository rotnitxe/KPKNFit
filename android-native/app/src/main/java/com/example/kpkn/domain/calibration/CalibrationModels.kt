package com.example.kpkn.domain.calibration

import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.buildRecordedSetContextKey
import com.example.kpkn.domain.calculations.CAPACITY_ESTIMATOR_VERSION

/** Exact identity, without alias matching or fallback to a different piece of equipment/side. */
data class CalibrationContext(
    val configurationId: String,
    /** The full performance/equipment key captured by CAL1, including the selected tag/brand. */
    val equipmentKey: String,
    val side: String? = null,
    val setupProfileId: String? = null,
    val loadMode: LoadModeV2 = LoadModeV2.LOAD,
    val loadQuantityConvention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
) {
    init {
        require(configurationId.isNotBlank() && equipmentKey.isNotBlank())
    }

    val key: String get() = buildRecordedSetContextKey(
        configurationId, equipmentKey, setupProfileId, side, loadQuantityConvention,
    )
}

data class RecordedCalibrationSet(
    val setId: String,
    val exerciseId: String,
    val context: CalibrationContext,
    /** Original external kg. For LASTRE this is only the declared added load, never body mass. */
    val externalLoadKg: Double,
    /** CAL1's valid full reps, already separate from partials: do not subtract them again. */
    val fullReps: Int,
    val partialReps: Int = 0,
    val declaredRir: Int? = null,
    val reachedFailure: Boolean = false,
)

data class PendingCalibrationSet(
    val setId: String,
    val exerciseId: String,
    val context: CalibrationContext,
    /** Saved, unadjusted prescription. Applying a proposal must never overwrite this value. */
    val baseLoadKg: Double,
    val currentLoadKg: Double = baseLoadKg,
    val targetReps: Int? = null,
    /** The adapter resolves RPE/failure into the target reserve before calling the engine. */
    val targetRir: Int = 2,
    val isCompleted: Boolean = false,
    val isManuallyFixed: Boolean = false,
)

data class CalibrationEquipment(val incrementKg: Double = CalibrationPolicy.defaults.defaultLoadIncrementKg) {
    init {
        require(incrementKg.isFinite() && incrementKg > 0.0)
    }
}

enum class CalibrationEligibility {
    OK, NO_EFFORT, RIR_TOO_HIGH, BODYWEIGHT, NO_EXTERNAL_LOAD, PARTIALS_ONLY, REPS_OUT_OF_RANGE,
}

enum class CalibrationDecisionType { RAISE, HOLD, LOWER, FIRST_REFERENCE, LOW_REPS_DROP, NOT_APPLIED }

enum class CalibrationReferenceSource { ACCEPTED_CALIBRATION, HISTORY, DECLARED_1RM, DECLARED_PR, NONE }

data class CalibrationLoadChange(val setId: String, val beforeKg: Double, val afterKg: Double)

data class CalibrationDecision(
    val sourceSetId: String,
    val exerciseId: String,
    val context: CalibrationContext,
    val capacityKg: Double?,
    val referenceKg: Double?,
    val referenceSource: CalibrationReferenceSource,
    val ratio: Double?,
    val factor: Double,
    val type: CalibrationDecisionType,
    val affectedSets: List<CalibrationLoadChange>,
    val eligibility: CalibrationEligibility,
    val reason: String,
    val note: String?,
    val scope: CalibrationScope,
    val effectiveRepsToFailure: Int?,
    val estimatorVersion: Int = CAPACITY_ESTIMATOR_VERSION,
    val policy: CalibrationPolicySnapshot = CalibrationPolicy.defaults,
) {
    /** No percentage is invented for a first reference or for a low-rep fixed drop. */
    val improvementPercent: Double? get() = ratio?.let { (it - 1.0) * 100.0 }

    /** Pure absolute assignment; persistence, acceptance and undo belong to CAL3. */
    fun applyTo(sets: List<PendingCalibrationSet>): List<PendingCalibrationSet> {
        if (scope != CalibrationScope.SESSION || type == CalibrationDecisionType.NOT_APPLIED) return sets
        val changes = affectedSets.associateBy { it.setId }
        return sets.map { set ->
            val change = changes[set.setId]
            if (change != null && set.exerciseId == exerciseId && set.context == context &&
                set.setId != sourceSetId && !set.isCompleted && !set.isManuallyFixed &&
                set.baseLoadKg == change.beforeKg
            ) set.copy(currentLoadKg = change.afterKg) else set
        }
    }
}
