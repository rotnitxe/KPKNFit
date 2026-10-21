package com.example.kpkn.domain.calibration

import com.example.kpkn.domain.calculations.CAPACITY_ESTIMATOR_VERSION
import com.example.kpkn.domain.calculations.calculateHybrid1RM

/** Only applied/accepted calibrations are supplied here, never kept/reverted proposals. */
data class AcceptedCalibrationReference(
    val context: CalibrationContext,
    val capacityKg: Double,
    val acceptedAtEpochMillis: Long,
    val estimatorVersion: Int = CAPACITY_ESTIMATOR_VERSION,
)

/** Already queried contextual e1RM (e.g. EWMA), recalculated with the current estimator. */
data class HistoricalCalibrationReference(
    val context: CalibrationContext,
    val capacityKg: Double,
    val estimatorVersion: Int = CAPACITY_ESTIMATOR_VERSION,
)

data class DeclaredCalibrationOneRm(val context: CalibrationContext, val capacityKg: Double)

/** For LASTRE, weightKg is the declared added load, in the same convention as C. */
data class DeclaredCalibrationPr(val context: CalibrationContext, val weightKg: Double, val reps: Int)

data class CalibrationReferenceCandidates(
    val acceptedCalibrations: List<AcceptedCalibrationReference> = emptyList(),
    val history: HistoricalCalibrationReference? = null,
    val declaredOneRm: DeclaredCalibrationOneRm? = null,
    val declaredPr: DeclaredCalibrationPr? = null,
)

data class ResolvedCalibrationReference(val capacityKg: Double?, val source: CalibrationReferenceSource)

object ReferenceResolver {
    fun resolve(
        context: CalibrationContext,
        candidates: CalibrationReferenceCandidates,
        nowEpochMillis: Long,
        policy: CalibrationPolicySnapshot = CalibrationPolicy.defaults,
    ): ResolvedCalibrationReference {
        require(nowEpochMillis >= 0L)
        val validityMillis = policy.referenceValidityDays * 24L * 60L * 60L * 1000L
        val accepted = candidates.acceptedCalibrations.asSequence()
            .filter { it.context == context && validCapacity(it.capacityKg) }
            .filter { it.estimatorVersion == CAPACITY_ESTIMATOR_VERSION }
            .filter { it.acceptedAtEpochMillis in 0L..nowEpochMillis }
            .filter { nowEpochMillis - it.acceptedAtEpochMillis < validityMillis }
            .maxByOrNull { it.acceptedAtEpochMillis }
        if (accepted != null) return ResolvedCalibrationReference(
            accepted.capacityKg, CalibrationReferenceSource.ACCEPTED_CALIBRATION,
        )
        candidates.history?.takeIf {
            it.context == context && validCapacity(it.capacityKg) &&
                it.estimatorVersion == CAPACITY_ESTIMATOR_VERSION
        }?.let { return ResolvedCalibrationReference(it.capacityKg, CalibrationReferenceSource.HISTORY) }
        candidates.declaredOneRm?.takeIf {
            it.context == context && validCapacity(it.capacityKg)
        }?.let { return ResolvedCalibrationReference(it.capacityKg, CalibrationReferenceSource.DECLARED_1RM) }
        candidates.declaredPr?.takeIf {
            it.context == context && validCapacity(it.weightKg) && it.reps > 0
        }?.let {
            val capacity = calculateHybrid1RM(it.weightKg, it.reps)
            if (validCapacity(capacity)) return ResolvedCalibrationReference(capacity, CalibrationReferenceSource.DECLARED_PR)
        }
        return ResolvedCalibrationReference(null, CalibrationReferenceSource.NONE)
    }

    private fun validCapacity(value: Double): Boolean = value.isFinite() && value > 0.0
}
