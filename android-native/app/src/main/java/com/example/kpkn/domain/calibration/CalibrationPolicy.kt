package com.example.kpkn.domain.calibration

import kotlinx.serialization.Serializable

/** Product thresholds, kept together so a decision can retain the policy that produced it. */
object CalibrationPolicy {
    val defaults = CalibrationPolicySnapshot(
        version = 1,
        minimumFullReps = 3,
        maximumFullReps = 10,
        minimumRir = 0,
        maximumRir = 1,
        lowRepsFactor = 0.95,
        lowerRatioThreshold = 0.97,
        upperRatioThreshold = 1.025,
        minimumFactor = 0.92,
        maximumFactor = 1.05,
        referenceValidityDays = 42,
        defaultLoadIncrementKg = 2.5,
        fiveThreeOneResetAfterFrozenCycles = 2,
    )
}

@Serializable
data class CalibrationPolicySnapshot(
    val version: Int,
    val minimumFullReps: Int,
    val maximumFullReps: Int,
    val minimumRir: Int,
    val maximumRir: Int,
    val lowRepsFactor: Double,
    val lowerRatioThreshold: Double,
    val upperRatioThreshold: Double,
    val minimumFactor: Double,
    val maximumFactor: Double,
    val referenceValidityDays: Int,
    val defaultLoadIncrementKg: Double,
    /** Reserved for CAL5; CAL2 does not change any program's progression. */
    val fiveThreeOneResetAfterFrozenCycles: Int,
) {
    init {
        require(version > 0 && minimumFullReps > 0 && maximumFullReps >= minimumFullReps)
        require(minimumRir >= 0 && maximumRir >= minimumRir)
        require(lowerRatioThreshold > 0.0 && lowerRatioThreshold < 1.0)
        require(upperRatioThreshold > 1.0 && upperRatioThreshold.isFinite())
        require(minimumFactor > 0.0 && minimumFactor <= lowerRatioThreshold)
        require(maximumFactor >= upperRatioThreshold && maximumFactor.isFinite())
        require(lowRepsFactor in minimumFactor..1.0)
        require(referenceValidityDays > 0 && fiveThreeOneResetAfterFrozenCycles > 0)
        require(defaultLoadIncrementKg.isFinite() && defaultLoadIncrementKg > 0.0)
    }
}

/** Shared with the live load controller. Missing/invalid capacities cannot adjust a load. */
fun sessionCapacityAdjustmentFactor(
    capacityKg: Double?,
    referenceKg: Double?,
    policy: CalibrationPolicySnapshot = CalibrationPolicy.defaults,
): Double {
    if (capacityKg == null || !capacityKg.isFinite() || capacityKg <= 0.0 ||
        referenceKg == null || !referenceKg.isFinite() || referenceKg <= 0.0
    ) return 1.0
    val ratio = capacityKg / referenceKg
    return when {
        ratio >= policy.upperRatioThreshold -> ratio.coerceAtMost(policy.maximumFactor)
        ratio <= policy.lowerRatioThreshold -> ratio.coerceAtLeast(policy.minimumFactor)
        else -> 1.0
    }
}
