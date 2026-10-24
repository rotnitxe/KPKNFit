package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.SessionEnergySummary

data class SessionCalorieStatus(
    val kcal: Int?,
    val hint: String?,
)

@Suppress("UNUSED_PARAMETER")
fun sessionCalorieStatus(summary: SessionEnergySummary?, completedSetCount: Int, bodyWeight: Double?): SessionCalorieStatus {
    // The retired method cannot be made available merely by supplying body weight or lifting more sets.
    val valid=summary?.takeIf { !it.methodVersion.startsWith("auge-energy") }?.totalKcal?.mid?.takeIf { it>0 }
    return SessionCalorieStatus(valid,if(valid==null) "Sin energía registrada mediante un método compatible; no equivale a cero." else null)
}
