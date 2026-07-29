package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class EnergyConfidence { HIGH, MEDIUM, LOW }

@Serializable
enum class EnergyEstimateSource { PLANNED, LIVE, FINAL }

@Serializable
enum class DailyEnergyStatus { DEFICIT, MAINTENANCE, SURPLUS }

@Serializable
data class CalorieRange(
    val low: Int = 0,
    val mid: Int = 0,
    val high: Int = 0,
)

@Serializable
data class ExerciseEnergyContribution(
    val exerciseId: String = "",
    val exerciseDbId: String? = null,
    val exerciseName: String = "",
    val activeKcal: Int = 0,
    val epocKcal: Int = 0,
    val totalKcal: Int = 0,
    val percentageOfSession: Double = 0.0,
    val completedSets: Int = 0,
    val totalSets: Int = 0,
)

@Serializable
data class SessionEnergySummary(
    val activeKcal: CalorieRange = CalorieRange(),
    val epocKcal: CalorieRange = CalorieRange(),
    val totalKcal: CalorieRange = CalorieRange(),
    val projectedTotalKcal: Int? = null,
    val confidence: EnergyConfidence = EnergyConfidence.LOW,
    val source: EnergyEstimateSource = EnergyEstimateSource.PLANNED,
    val methodVersion: String = "auge-energy-v2",
    val exerciseContributions: List<ExerciseEnergyContribution> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class DailyEnergyBalance(
    val consumedKcal: Int = 0,
    val trainingBurnKcal: Int = 0,
    val netKcal: Int = 0,
    val targetKcal: Int = 0,
    val deltaFromTarget: Int = 0,
    val status: DailyEnergyStatus = DailyEnergyStatus.MAINTENANCE,
)
