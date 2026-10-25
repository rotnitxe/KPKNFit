package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.EnergyEstimateSource
import com.example.kpkn.data.models.isRetiredTrainingEnergyMethod

data class SessionCalorieStatus(
    val kcal: Int?,
    val hint: String?,
)

@Suppress("UNUSED_PARAMETER")
fun sessionCalorieStatus(summary: SessionEnergySummary?, completedSetCount: Int, bodyWeight: Double?): SessionCalorieStatus {
    // The retired method cannot be made available merely by supplying body weight or lifting more sets.
    val valid=summary?.takeIf { !isRetiredTrainingEnergyMethod(it.methodVersion) && it.source == EnergyEstimateSource.FINAL && it.totalKcal.low >= 0 && it.totalKcal.low <= it.totalKcal.mid && it.totalKcal.mid <= it.totalKcal.high }?.totalKcal?.mid?.takeIf { it>=0 }
    return SessionCalorieStatus(valid,if(valid==null) "Sin energía registrada mediante un método compatible; no equivale a cero." else null)
}
