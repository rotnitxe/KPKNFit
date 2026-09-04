package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CreatineProtocol
import com.example.kpkn.data.models.NutritionLog
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Creatine muscle saturation estimates (Hultman 1996; ISSN Kreider 2017).
 */
object CreatineSaturationEngine {

    private const val LOADING_G_PER_KG = 0.3
    private const val LOADING_DAYS = 6
    private const val MAINTENANCE_G_PER_KG = 0.03
    private const val MAINTENANCE_FLOOR_G = 3.0
    private const val MAINTENANCE_CEILING_G = 5.0
    private const val GRADUAL_DAILY_G = 5.0
    private const val GRADUAL_DAYS_TO_SATURATION = 28
    private const val LOADING_EQUIVALENT_DAYS = 6.0

    data class CreatineProtocolDoses(
        val loadingDailyGrams: Double,
        val loadingDays: Int,
        val maintenanceDailyGrams: Double,
        val gradualDailyGrams: Double,
        val loadingEquivalentTotalGrams: Double,
    )

    data class CreatineSaturationState(
        val protocol: CreatineProtocol,
        val protocolStartDate: String?,
        val totalCreatineLoggedGrams: Double,
        val saturationProgress: Double,
        val isSaturated: Boolean,
        val estimatedSaturationDate: String?,
        val dailyTargetGrams: Double,
        val daysOnProtocol: Int,
    )

    fun computeDoses(weightKg: Double?): CreatineProtocolDoses {
        val weight = weightKg?.takeIf { it > 0.0 } ?: 70.0
        val loadingDaily = weight * LOADING_G_PER_KG
        val maintenance = (weight * MAINTENANCE_G_PER_KG).coerceIn(MAINTENANCE_FLOOR_G, MAINTENANCE_CEILING_G)
        val loadingEquivalent = loadingDaily * LOADING_EQUIVALENT_DAYS
        return CreatineProtocolDoses(
            loadingDailyGrams = loadingDaily,
            loadingDays = LOADING_DAYS,
            maintenanceDailyGrams = maintenance,
            gradualDailyGrams = GRADUAL_DAILY_G,
            loadingEquivalentTotalGrams = loadingEquivalent,
        )
    }

    fun totalCreatineFromLogs(logs: List<NutritionLog>): Double =
        logs
            .filter { it.status != com.example.kpkn.data.models.NutritionStatus.PLANNED }
            .flatMap { it.foods }
            .sumOf { it.creatineG }

    fun computeSaturation(
        protocol: CreatineProtocol,
        protocolStartDate: String?,
        weightKg: Double?,
        logs: List<NutritionLog>,
        today: LocalDate = LocalDate.now(),
    ): CreatineSaturationState {
        val doses = computeDoses(weightKg)
        val totalLogged = totalCreatineFromLogs(logs)
        val startDate = protocolStartDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val daysOnProtocol = startDate?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) } ?: 0

        val (dailyTarget, saturationProgress, estimatedDate) = when (protocol) {
            CreatineProtocol.LOADING -> {
                val target = doses.loadingDailyGrams
                val progressByGrams = (totalLogged / doses.loadingEquivalentTotalGrams).coerceIn(0.0, 1.0)
                val progressByDays = (daysOnProtocol.toDouble() / LOADING_DAYS).coerceIn(0.0, 1.0)
                val progress = maxOf(progressByGrams, progressByDays)
                val estDate = startDate?.plusDays(LOADING_DAYS.toLong())?.toString()
                Triple(target, progress, estDate)
            }
            CreatineProtocol.GRADUAL -> {
                val target = doses.gradualDailyGrams
                val progressByGrams = (totalLogged / doses.loadingEquivalentTotalGrams).coerceIn(0.0, 1.0)
                val progressByDays = (daysOnProtocol.toDouble() / GRADUAL_DAYS_TO_SATURATION).coerceIn(0.0, 1.0)
                val progress = maxOf(progressByGrams, progressByDays)
                val estDate = startDate?.plusDays(GRADUAL_DAYS_TO_SATURATION.toLong())?.toString()
                Triple(target, progress, estDate)
            }
            CreatineProtocol.NONE -> {
                val progress = (totalLogged / doses.loadingEquivalentTotalGrams).coerceIn(0.0, 1.0)
                Triple(doses.gradualDailyGrams, progress, null)
            }
        }

        return CreatineSaturationState(
            protocol = protocol,
            protocolStartDate = protocolStartDate,
            totalCreatineLoggedGrams = totalLogged,
            saturationProgress = saturationProgress,
            isSaturated = saturationProgress >= 1.0,
            estimatedSaturationDate = estimatedDate,
            dailyTargetGrams = dailyTarget,
            daysOnProtocol = daysOnProtocol,
        )
    }

    fun todayCreatineGrams(logs: List<NutritionLog>, date: String): Double =
        logs
            .filter { it.date.take(10) == date.take(10) && it.status != com.example.kpkn.data.models.NutritionStatus.PLANNED }
            .flatMap { it.foods }
            .sumOf { it.creatineG }
}
