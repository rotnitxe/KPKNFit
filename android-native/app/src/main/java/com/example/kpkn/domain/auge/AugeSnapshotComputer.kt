package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.declaredCheckInChannels
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Metas ya resueltas por el llamador; sin metas, la recuperación nutricional es neutra. */
data class NutritionRecoveryContext(
    val goalsByDate: Map<LocalDate, DayGoalsResult> = emptyMap(),
)

/** Entradas de una evaluación: bienestar y caché normalizados, instante y zona explícitos. */
data class AugeSnapshotInputs(
    val history: List<WorkoutLog>,
    val wellbeing: DailyWellbeingLog?,
    val settings: Settings,
    val exerciseDb: Map<String, ExerciseMuscleInfo>,
    val nowMs: Long,
    val zone: ZoneId,
    val sleepLogs: List<SleepLog> = emptyList(),
    val nutritionLogs: List<NutritionLog> = emptyList(),
    val feedbacks: List<PostSessionFeedback> = emptyList(),
    val adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    val nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
)

/** Núcleo común de Home, programa y vista previa; no publica ni persiste estado. */
data class AugeSnapshotCore(
    val batteries: GlobalBatteries,
    val perMuscle: Map<String, MuscleRecoveryStatus>,
    val dashboard: RecoveryDashboard,
    val readiness: AugeReadinessVerdict,
    val articular: Map<ArticularBattery, ArticularBatteryState>,
    val cumulativeFatigue: Double,
    val coverage: RingsCoverage,
    val continuous: AugeContinuousReading = AugeContinuousReading.NEUTRAL,
)

/** Mismas entradas ⇒ misma instantánea. No carga datos, aprende parámetros ni consulta el reloj. */
object AugeSnapshotComputer {
    fun compute(inputs: AugeSnapshotInputs): AugeSnapshotCore = with(inputs.copy(
        // Los logs antiguos sin hora se resuelven aquí: los motores reciben fechas absolutas.
        history = inputs.history.map { log ->
            val instant = runCatching { OffsetDateTime.parse(log.date).toInstant() }
                .recoverCatching { ZonedDateTime.parse(log.date).toInstant() }
                .recoverCatching { LocalDate.parse(log.date.take(10)).atStartOfDay(inputs.zone).toInstant() }
                .getOrNull()
            val date = instant?.toString()
            if (date == null || date == log.date) log else log.copy(date = date)
        },
    )) {
        val muscleDetails = AugeRecoveryEngine.getPerMuscleBatteryDetails(
            history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs, feedbacks, adaptiveCache,
            nowOverrideMs = nowMs, zone = zone, nutritionContext = nutritionContext,
        )
        val muscles = muscleDetails.mapValues { it.value.status }
        val articular = AugeTtcEngine.calculateArticularBatteries(
            history, exerciseDb, feedbacks, wellbeing, nowMs = nowMs, zone = zone,
        )
        val globalDetail = AugeRecoveryEngine.calculateGlobalBatteriesDetail(
            history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs, feedbacks, adaptiveCache,
            precomputedMuscles = muscles, articularBatteries = articular,
            nowOverrideMs = nowMs, zone = zone, nutritionContext = nutritionContext,
            precomputedMuscleDetails = muscleDetails,
        )
        val batteries = globalDetail.batteries
        val dashboardDetail = AugeRecoveryEngine.calculateRecoveryDashboardDetail(
            batteries, muscles, articular, wellbeing, sleepLogs,
            recentSessionCount = AugeRecoveryEngine.recentSessionCount(history, nowMs),
            spinalExact = globalDetail.finalSpinalExact,
        )
        val dashboard = dashboardDetail.dashboard
        val twoWeeksAgo = nowMs - 14L * 24 * 3_600_000L
        val cumulativeFatigue = history.filter { AugeUtils.logDateMs(it) >= twoWeeksAgo }.sumOf { log ->
            AugeFatigueEngine.calculateCompletedSessionStress(
                completedExercises = log.completedExercises,
                exerciseDb = exerciseDb,
                settings = settings,
                adaptiveCache = adaptiveCache,
                durationMinutes = log.durationMinutes,
            )
        }
        val floors = AugeUtils.physiologicalFloor(settings)
        val continuous = AugeContinuousReading(
            energy = AugeChannelReading(
                exact = globalDetail.energyExact,
                atFloor = globalDetail.energyExact <= floors.cns + 1e-9,
                blocked = false,
            ),
            spineRaw = AugeChannelReading(
                exact = globalDetail.spineRawExact,
                atFloor = globalDetail.spineRawExact <= floors.spinal + 1e-9,
                blocked = false,
            ),
            spineShown = AugeChannelReading(
                exact = dashboardDetail.structureExact,
                atFloor = dashboardDetail.structureExact <= floors.spinal + 1e-9,
                blocked = dashboardDetail.structureBlocked,
            ),
            musclesGlobal = AugeChannelReading(
                exact = globalDetail.musclesGlobalExact,
                atFloor = globalDetail.musclesGlobalExact <= floors.muscular + 1e-9,
                blocked = globalDetail.musclesGlobalBlocked,
            ),
            perMuscle = muscleDetails.mapValues { (_, detail) ->
                AugeChannelReading(
                    exact = detail.exact,
                    atFloor = detail.exact <= detail.floor + 1e-9,
                    blocked = detail.domsCapped || detail.discomfortPenaltyApplied,
                )
            },
        )
        AugeSnapshotCore(
            batteries, muscles, dashboard, AugeRecoveryEngine.calculateDailyReadiness(dashboard, wellbeing),
            articular, cumulativeFatigue,
            computeSnapshotCoverage(history, settings, wellbeing, dashboard, batteries, nowMs, zone),
            continuous,
        )
    }
}

/** Procedencia por canal con las mismas entradas y el mismo instante de las baterías. */
internal fun computeSnapshotCoverage(
    history: List<WorkoutLog>,
    settings: Settings,
    wellbeing: DailyWellbeingLog?,
    dashboard: RecoveryDashboard,
    batteries: GlobalBatteries,
    nowMs: Long,
    zone: ZoneId,
): RingsCoverage {
    val contribution = InitialRecoveryEvidencePolicy.resolve(
        InitialRecoveryPolicyInput(settings.initialRecoveryEvidence, nowMs, workoutLogs = history, zone = zone),
    )
    val engineScores = mapOf(
        RecoveryChannelId.MUSCULAR to batteries.muscular,
        RecoveryChannelId.SYSTEM to batteries.cnc,
        RecoveryChannelId.STRUCTURE to batteries.spinal,
    )
    return RingsCoverage.fromEngineInputs(
        historyIsEmpty = history.isEmpty(),
        wellbeing = wellbeing,
        contribution = contribution,
        declaredChannels = declaredCheckInChannels(wellbeing),
        channelConfidence = dashboard.channels.associate { it.id to it.confidence },
        score = { channel -> dashboard.channelScore(channel, engineScores.getValue(channel)) },
    )
}
