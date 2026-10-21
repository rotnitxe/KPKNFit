package com.example.kpkn.data.repository

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.AugeSnapshotInputs
import com.example.kpkn.domain.auge.NutritionRecoveryContext
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.nutrition.resolveDayGoalsByDate
import java.time.Instant
import java.time.ZoneId

/** Adaptador de datos: Home y programa cargan y normalizan las mismas entradas. */
internal suspend fun loadAugeSnapshotInputs(
    auge: AugeRepository,
    nutrition: NutritionRepository,
    history: List<WorkoutLog>,
    settings: Settings,
    nowMs: Long,
    zone: ZoneId,
): AugeSnapshotInputs {
    val wellbeing = auge.getEffectiveManualAnchors(nowMs, zone)
    val cache = auge.getAdaptiveCache()
    return AugeSnapshotInputs(
        history = history,
        wellbeing = wellbeing?.copy(manualMuscleBatteries = remapMuscleIntMapToPillars(wellbeing.manualMuscleBatteries)),
        settings = settings,
        exerciseDb = catalogExerciseIndex(),
        nowMs = nowMs,
        zone = zone,
        sleepLogs = auge.getLastNSleepLogs(7),
        nutritionLogs = nutrition.nutritionLogs.value,
        feedbacks = auge.getPostSessionFeedbacks(),
        adaptiveCache = cache,
        nutritionContext = nutrition.augeRecoveryContext(settings, nowMs, zone),
    )
}

/** Resuelve metas históricas en datos; el dominio solo recibe el resultado. */
internal fun NutritionRepository.augeRecoveryContext(settings: Settings, nowMs: Long, zone: ZoneId): NutritionRecoveryContext {
    if (!settings.algorithmSettings.augeEnableNutritionTracking) return NutritionRecoveryContext()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return NutritionRecoveryContext(
        resolveDayGoalsByDate(
            dates = (0L until 7L).map { today.minusDays(it) },
            settings = settings,
            activePlan = activeNutritionPlan,
            snapshots = dailyGoalSnapshots.value,
            today = today,
        ),
    )
}
