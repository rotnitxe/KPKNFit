package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine.calculateSetBatteryDrain
import com.example.kpkn.domain.auge.AugeFatigueEngine.getDynamicAugeMetrics
import com.example.kpkn.domain.auge.AugeFatigueEngine.isSetEffective
import com.example.kpkn.domain.auge.AugeUtils.clamp
import com.example.kpkn.domain.auge.AugeUtils.safeExp
import com.example.kpkn.domain.auge.AugeUtils.logDateMs
import com.example.kpkn.domain.auge.AugeUtils.physiologicalFloor
import com.example.kpkn.domain.auge.AugeUtils.decelerateBattery
import com.example.kpkn.domain.auge.AugeUtils.batteryPercent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Detalle interno de una batería muscular: el valor continuo antes de redondear y el estado visible. */
internal data class MuscleBatteryDetail(
    val exact: Double,
    val status: MuscleRecoveryStatus,
    val floor: Double,
    val discomfortPenaltyApplied: Boolean,
    val domsCapped: Boolean,
)

/** Detalle interno de la batería de Energía: el Double crudo y los enteros publicados. */
internal data class SystemicFatigueDetail(
    val exact: Double,
    val cnsBattery: Int,
    val gymLoad: Int,
    val lifeLoad: Int = 0,
)

/** Detalle interno de la batería espinal cruda. */
internal data class SpinalBatteryDetail(
    val exact: Double,
    val battery: Int,
)

/** Detalle interno de la Columna mostrada (blend axial + articular + guardia). */
internal data class StructureRingDetail(
    val exact: Double,
    val score: Int,
    val articularDriven: Boolean,
)

/** Detalle interno de las baterías globales con los intermedios continuos que necesita la lectura continua. */
internal data class GlobalBatteriesDetail(
    val batteries: GlobalBatteries,
    val energyExact: Double,
    val spineRawExact: Double,
    val finalSpinalExact: Double,
    val musclesGlobalExact: Double,
    val musclesGlobalBlocked: Boolean,
)

/** Detalle interno del dashboard con la Columna mostrada antes de redondear. */
internal data class RecoveryDashboardDetail(
    val dashboard: RecoveryDashboard,
    val structureExact: Double,
    val structureBlocked: Boolean,
)

/**
 * AugeRecoveryEngine — Motor de Recuperación AUGE v3.0 para Kotlin.
 * Equivalente a @kpkn/shared-domain recovery.ts + recoveryService.ts
 */
object AugeRecoveryEngine {

    private const val ACUTE_FEEDBACK_WINDOW_MS = 14L * 24 * 3_600 * 1_000

    // Los músculos "pilar" para el cálculo de batería muscular global
    private val PILLAR_MUSCLES = AugeParameters.PILLARS

    // Set completo de baterías por músculo: pilares + todo grupo con perfil de
    // recuperación (Aductores, Antebrazo, Cuello, Psoas, rotadores, etc.).
    // El promedio global sigue usando solo PILLAR_MUSCLES; este set alimenta
    // perMuscle para la UI y el readiness de sesiones que involucran esos músculos.
    private val BATTERY_MUSCLES: List<String> =
        (PILLAR_MUSCLES + AugeParameters.PROFILED_MUSCLES.map { getAugeMusclePillarId(it) }).distinct()

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun densityMultiplierForCompletedExercise(ex: CompletedExercise): Double =
        AugeFatigueEngine.getDensityMultiplierForExercise(
            supersetId = ex.supersetId,
            restTime = ex.supersetRestBetween ?: ex.restTime,
            supersetExerciseCount = ex.supersetExerciseCount,
            supersetRounds = ex.supersetRounds,
            supersetRestAfter = ex.supersetRestAfter,
        )

    private fun involvedMusclesFor(
        exercise: CompletedExercise,
        info: ExerciseMuscleInfo?,
    ): List<InvolvedMuscle> = AugeFatigueEngine.resolveInvolvedMuscles(
        exerciseName = exercise.exerciseName,
        snapshot = exercise.effectiveMuscles,
        dbInfo = info,
    )

    private fun normKey(s: String) = s
        .lowercase().trim()
        .replace("á","a").replace("é","e").replace("í","i")
        .replace("ó","o").replace("ú","u").replace("ü","u")

    private fun recoveryBand(score: Int): RecoveryBand = RecoveryBands.band(score)

    private fun confidenceLabel(score: Int): String = when {
        score >= 80 -> "Alta"
        score >= 60 -> "Media"
        else -> "Baja"
    }

    private fun actionForChannel(id: RecoveryChannelId, score: Int): String =
        RecoveryBands.action(id, score)


    private fun parseWellbeingDate(dateStr: String, zone: ZoneId): Long = try {
        val ld = LocalDate.parse(dateStr.take(10))
        ld.atStartOfDay(zone).toInstant().toEpochMilli()
    } catch (e: Exception) { 0L }

    /**
     * Solo las anclas musculares condicionan la guardia espinal; Energía no puede reescribirla.
     * Un log anterior a esas anclas se evalúa sin el estado muscular posterior (R-09).
     */
    private fun latestManualAnchorMs(wellbeing: DailyWellbeingLog?, zone: ZoneId): Long {
        if (wellbeing == null) return 0L
        val global = wellbeing.manualMuscleBatteries.keys.maxOfOrNull { wellbeing.legacyMuscleAnchorMs(it, zone) } ?: 0L
        val perMuscle = wellbeing.manualMuscleOverridesV2.values.maxOfOrNull { it.anchorEpochMs } ?: 0L
        return max(global, perMuscle)
    }

    /** Solo los ajustes que ya existían pueden sustituir la evidencia inicial. */
    private fun hasManualMuscularAnchorsAt(wellbeing: DailyWellbeingLog?, now: Long, zone: ZoneId): Boolean {
        if (wellbeing == null) return false
        val window = (now - MANUAL_ANCHOR_WINDOW_MS + 1)..now
        val globalMuscular = wellbeing.manualMuscularBattery != null && wellbeing.manualAnchorMs(RecoveryChannelId.MUSCULAR, zone) in window
        return globalMuscular || wellbeing.manualMuscleBatteries.keys.any { wellbeing.legacyMuscleAnchorMs(it, zone) in window } ||
            wellbeing.manualMuscleOverridesV2.values.any { it.anchorEpochMs in window }
    }

    private fun manualMuscleOverride(
        wellbeing: DailyWellbeingLog?,
        muscleName: String,
    ): ManualMuscleBatteryOverride? = wellbeing?.manualMuscleOverridesV2
        ?.entries
        ?.firstOrNull { (key, _) -> normKey(key) == normKey(muscleName) }
        ?.value

    private fun muscleMatchesCategory(specificMuscle: String, category: String): Boolean {
        return matchesAugeMuscleTarget(specificMuscle, category)
    }

    /**
     * Promedio ponderado de sueño de los últimos 3 días.
     * Pesos: 50% última noche, 30% anteayer, 20% hace 3 días.
     * Equivalente a recoveryService.ts línea 310-313.
     * Returns null when there is no sleep log and wellbeing has no hours recorded.
     */
    private fun calculateWeightedSleepHours(
        sleepLogs: List<SleepLog>,
        wellbeing: DailyWellbeingLog?,
    ): Double? {
        val sorted = sleepLogs
            .sortedByDescending { it.endTime }
            .take(3)
        return when (sorted.size) {
            0    -> wellbeing?.sleepHours
            1    -> sorted[0].duration
            2    -> sorted[0].duration * 0.6 + sorted[1].duration * 0.4
            else -> sorted[0].duration * 0.5 + sorted[1].duration * 0.3 + sorted[2].duration * 0.2
        }
    }

    /**
     * Sleep hours are a habit, not the Energy/SNC structure the product measures.
     * Recovery τ must not stretch or shrink from a 7.5 h fallback written by readiness.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun systemicRecoveryMultiplier(
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog>,
    ): Double = 1.0

    private fun resolveDbInfo(
        ex: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
        index = exerciseDb,
        catalogConfigurationId = ex.catalogConfigurationId,
        exerciseDbId = ex.exerciseDbId,
        exerciseId = ex.exerciseId,
        exerciseName = ex.exerciseName,
    )

    private fun calculateMuscleDiscomfortPenaltyPct(
        muscleName: String,
        history: List<WorkoutLog>,
        now: Long,
        wellbeing: DailyWellbeingLog? = null,
    ): Double {
        var penalty = 0.0
        history.forEach { log ->
            val logTime = logDateMs(log)
            if (logTime <= 0L) return@forEach
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)
            val timeDecay = safeExp(-(hoursSince / 84.0))
            log.postExerciseReports.forEach { report ->
                report.discomfortIds
                    .asSequence()
                    .filter { it != "none" }
                    .distinct()
                    .forEach { discomfortId ->
                        val entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] ?: return@forEach
                        val matchesMuscle = entry.relatedMuscles.any { related ->
                            muscleMatchesCategory(related, muscleName) ||
                                muscleMatchesCategory(muscleName, related)
                        }
                        if (!matchesMuscle) return@forEach

                        val quality = report.technicalQuality.coerceIn(1, 10)
                        val qualityMult = when {
                            quality <= 4 -> 1.25
                            quality <= 6 -> 1.10
                            quality >= 9 -> 0.85
                            else -> 1.0
                        }
                        penalty += 8.0 * qualityMult * timeDecay
                    }
            }
        }
        wellbeing?.preWorkoutDiscomforts
            ?.asSequence()
            ?.filter { it != "none" }
            ?.distinct()
            ?.forEach { discomfortId ->
                val entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] ?: return@forEach
                val matchesMuscle = entry.relatedMuscles.any { related ->
                    muscleMatchesCategory(related, muscleName) ||
                        muscleMatchesCategory(muscleName, related)
                }
                if (matchesMuscle) {
                    penalty += 8.0
                }
            }
        return clamp(penalty, 0.0, 30.0)
    }

    private fun calculateMuscleFeedbackPenaltyPct(
        muscleName: String,
        feedbacks: List<PostSessionFeedback>,
    ): Double {
        if (feedbacks.isEmpty()) return 0.0
        var total = 0.0
        var samples = 0

        feedbacks.forEach { fb ->
            val direct = fb.muscleFeedback.entries.filter { (key, _) ->
                muscleMatchesCategory(key, muscleName) || muscleMatchesCategory(muscleName, key)
            }
            if (direct.isEmpty()) return@forEach

            direct.forEach { (_, entry) ->
                val domsPenalty = when (entry.doms.coerceIn(1, 5)) {
                    5 -> 12.0
                    4 -> 8.0
                    3 -> 4.0
                    else -> 0.0
                }
                val jointPenalty = if (entry.jointPain) 4.0 else 0.0
                val strengthAdj = when {
                    entry.strengthCapacity <= 4 -> 5.0
                    entry.strengthCapacity <= 6 -> 2.0
                    entry.strengthCapacity >= 9 -> -2.0
                    else -> 0.0
                }
                total += domsPenalty + jointPenalty + strengthAdj
                samples++
            }
        }

        if (samples <= 0) return 0.0
        return clamp(total / samples, -4.0, 16.0)
    }

    private fun calculateSystemFeedbackPenaltyPct(
        feedbacks: List<PostSessionFeedback>,
    ): Double {
        if (feedbacks.isEmpty()) return 0.0
        val avgRecovery = feedbacks.map { it.cnsRecovery.coerceIn(1, 10) }.average()
        return when {
            avgRecovery <= 4.0 -> 12.0
            avgRecovery <= 5.5 -> 8.0
            avgRecovery <= 7.0 -> 3.0
            avgRecovery >= 9.0 -> -4.0
            else -> 0.0
        }
    }

    /** Columna = axial + articular + guard muscles (dorsales, erectores/lumbar, trapecio). */
    internal fun structureRingScore(
        spinal: Int,
        articularFloor: Int,
        guardMuscleScores: Collection<Int>,
    ): Int = structureRingScoreDetail(spinal, articularFloor, guardMuscleScores).score

    /** Variante exacta: el blend continuo antes de redondear; [articularDriven] avisa de que el mínimo no es axial. */
    internal fun structureRingScoreDetail(
        spinal: Int,
        articularFloor: Int,
        guardMuscleScores: Collection<Int>,
    ): StructureRingDetail {
        val muscleGuard = guardMuscleScores.minOrNull() ?: 100
        val blended = (spinal * 0.50) + (articularFloor * 0.25) + (muscleGuard * 0.25)
        val exact = min(spinal.toDouble(), blended).coerceIn(0.0, 100.0)
        return StructureRingDetail(
            exact = exact,
            score = min(spinal, blended.roundToInt()).coerceIn(0, 100),
            articularDriven = blended < spinal,
        )
    }

    /** Variante exacta con la Columna cruda continua (misma fórmula, sin el entero intermedio). */
    internal fun structureRingScoreDetail(
        spinal: Double,
        articularFloor: Int,
        guardMuscleScores: Collection<Int>,
    ): StructureRingDetail {
        val muscleGuard = guardMuscleScores.minOrNull() ?: 100
        val blended = (spinal * 0.50) + (articularFloor * 0.25) + (muscleGuard * 0.25)
        val exact = min(spinal, blended).coerceIn(0.0, 100.0)
        return StructureRingDetail(
            exact = exact,
            score = batteryPercent(exact),
            articularDriven = blended < spinal,
        )
    }

    /**
     * Invierte el blend de Columna: el usuario edita el valor mostrado y
     * anclamos el canal spinal crudo.
     */
    fun invertStructureRingScore(
        desiredBlended: Int,
        articularFloor: Int,
        guardMuscleScores: Collection<Int>,
    ): Int {
        val muscleGuard = guardMuscleScores.minOrNull() ?: 100
        val intercept = 0.25 * articularFloor + 0.25 * muscleGuard
        val desired = desiredBlended.coerceIn(0, 100).toDouble()
        val twoIntercept = 2.0 * intercept
        val raw = if (desired <= twoIntercept) desired else 2.0 * (desired - intercept)
        return raw.roundToInt().coerceIn(0, 100)
    }

    fun structureInputs(
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
    ): Pair<Int, List<Int>> {
        val weakestArticular = articularBatteries.entries.sortedBy { it.value.recoveryScore }.take(2)
        val articularFloor = weakestArticular
            .takeIf { it.isNotEmpty() }
            ?.map { it.value.recoveryScore.toDouble() }
            ?.average()
            ?.let { batteryPercent(it) }
            ?: 100
        val guardScores = SPINE_GUARD_MUSCLES.mapNotNull { name ->
            perMuscle.entries.firstOrNull { muscleMatchesCategory(it.key, name) }?.value?.recoveryScore
        }
        return articularFloor to guardScores
    }

    private fun muscularLoadInWindow(
        history: List<WorkoutLog>,
        fromMs: Long,
        toMs: Long,
    ): Double = history
        .filter { logDateMs(it) > 0L && logDateMs(it) in fromMs until toMs }
        .sumOf { log ->
            log.muscularImpactV2?.perMuscle?.values?.sumOf { it.stressUnits } ?: 0.0
        }

    fun recentSessionCount(history: List<WorkoutLog>, nowMs: Long): Int {
        val cutoff = nowMs - 28L * 24 * 3600_000L
        return history.count { log ->
            val ms = logDateMs(log)
            ms > 0L && ms in cutoff until nowMs
        }
    }

    /**
     * ACWR muscular: estrés V2 de 7 días ÷ carga semanal crónica del mes. La crónica divide entre las semanas
     * reales de seguimiento (2 a 4, ver [AugeClassifiers.chronicWeeklyLoad]) y no siempre entre 4.
     * [nowMs] es obligatorio: ninguna llamada puede caer en el reloj real sin querer.
     */
    internal fun muscularAcwrFor(history: List<WorkoutLog>, nowMs: Long): Double? {
        val day = 24L * 3600_000L
        val acute = muscularLoadInWindow(history, nowMs - 7L * day, nowMs)
        val month = muscularLoadInWindow(history, nowMs - 28L * day, nowMs)
        if (month <= 0.0) return null
        val chronicWeekly = AugeClassifiers.chronicWeeklyLoad(month, AugeClassifiers.userTrackingSpanDays(history, nowMs))
        val recentV2 = history.filter { log ->
            val ms = logDateMs(log)
            ms > 0L &&
                ms in (nowMs - 28L * day) until nowMs &&
                (log.muscularImpactV2?.perMuscle?.values?.sumOf { it.stressUnits } ?: 0.0) > 0.0
        }
        if (recentV2.size < 4) return null
        val oldest = recentV2.minOf { logDateMs(it) }
        if (nowMs - oldest < 14L * day) return null
        return AugeClassifiers.computeAcwr(acute, chronicWeekly)
    }

    private fun applyMuscularAcwr(muscularAvg: Double, history: List<WorkoutLog>, nowMs: Long): Double {
        val acwr = muscularAcwrFor(history, nowMs) ?: return muscularAvg
        val factor = AugeClassifiers.muscularLoadFactorFromAcwr(acwr)
        return (100.0 - (100.0 - muscularAvg) * factor).coerceIn(0.0, 100.0)
    }

    /**
     * Fallback capacity for callers that skip the V2 precompute map.
     * Same catalog share as everywhere else (secondary = 0.5), never role × activation.
     * [nowMs] es obligatorio: la capacidad se mide en el instante de la evaluación, nunca con el reloj real.
     */
    private fun calculateUserWorkCapacity(
        muscleName: String,
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        nowMs: Long,
        @Suppress("UNUSED_PARAMETER") adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): Double {
        val now = nowMs
        val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
        val recentLogs = history.filter {
            logDateMs(it) > 0L && logDateMs(it) > fourWeeksAgo - 7L * 24 * 3600 * 1000
        }
        val baseFloor = AugeFatigueEngine.getAthleteCapacity(settings)
        if (recentLogs.isEmpty()) return baseFloor

        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        var totalStress = 0.0

        recentLogs.forEach { log ->
            val logMs = logDateMs(log)
            val daysSince = (now - logMs) / (24.0 * 3600 * 1000)
            val decay = if (daysSince <= 28.0) 1.0
                else if (daysSince >= 35.0) 0.0
                else (35.0 - daysSince) / 7.0

            val storedV2Stress = log.muscularImpactV2?.perMuscle?.entries
                ?.filter { (key, _) -> muscleMatchesCategory(key, muscleName) }
                ?.sumOf { (_, impact) -> impact.stressUnits }
            if (storedV2Stress != null && storedV2Stress > 0.0) {
                totalStress += storedV2Stress * decay
                return@forEach
            }

            log.completedExercises.forEach { ex ->
                ex.cardioDetails?.let { cardio ->
                    val duration = ex.sets.sumOf { it.timeSeconds ?: 0 }
                        .takeIf { it > 0 }
                        ?: return@forEach
                    val rpe = ex.sets.firstOrNull { (it.timeSeconds ?: 0) > 0 }?.rpe ?: cardio.resolvedRpe()
                    val cardioDrain = CardioRingDrainEngine.drain(cardio, duration, rpe, settings)
                    val matched = cardioDrain.muscleDrains.entries
                        .filter { (key, _) -> muscleMatchesCategory(key, muscleName) }
                        .sumOf { it.value }
                    totalStress += matched * AugeUtils.STRESS_UNITS_PER_DRAIN_PCT * decay
                    return@forEach
                }
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val involvedMuscles = involvedMusclesFor(ex, dbInfo)
                val involvement = involvedMuscles.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                } ?: return@forEach

                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
                val densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0
                val setStress = ex.sets.sumOf { s ->
                    if (!isSetEffective(s)) return@sumOf 0.0
                    accumulated += 1
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                    )
                    drain.muscularDrainPct
                }
                totalStress += setStress * resolveMuscleVolumeContribution(involvement) *
                    AugeUtils.STRESS_UNITS_PER_DRAIN_PCT * decay
            }
        }

        val weeklyAvg = totalStress / 4.0
        val calculatedCapacity = weeklyAvg * 1.8
        return AugeUtils.clampWorkCapacity(max(calculatedCapacity, baseFloor))
    }


    private fun sessionDrainRecipe(
        settings: Settings,
        tanks: BatteryTanks,
        adaptiveCache: AugeAdaptiveCache,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        options: RecipeOptions,
    ): SessionDrainRecipe = SessionDrainRecipe(settings, tanks, adaptiveCache, options) { identity ->
        resolveCatalogExerciseInfoInIndex(
            index = exerciseDb,
            catalogConfigurationId = identity.catalogConfigurationId,
            exerciseDbId = identity.exerciseDbId,
            exerciseId = identity.exerciseId,
            exerciseName = identity.exerciseName,
        )
    }

    // ─── 1. BATERÍA MUSCULAR INDIVIDUAL ──────────────────────────────────────

    fun calculateMuscleBattery(
        muscleName: String,
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        nutritionMultiplier: Double = 1.0,
        sleepLogs: List<SleepLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        precomputedCapacity: Double? = null,
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MuscleRecoveryStatus = calculateMuscleBatteryDetail(
        muscleName, history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks,
        adaptiveCache, precomputedCapacity, nowOverride, zone,
    ).status

    /** Misma fórmula que [calculateMuscleBattery]; devuelve además el Double previo al redondeo y sus topes. */
    internal fun calculateMuscleBatteryDetail(
        muscleName: String,
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        nutritionMultiplier: Double = 1.0,
        sleepLogs: List<SleepLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        precomputedCapacity: Double? = null,
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MuscleBatteryDetail {
        val now = nowOverride
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val capacity = precomputedCapacity ?: calculateUserWorkCapacity(
            muscleName, history, settings, exerciseDb, now, adaptiveCache,
        )

        val adaptiveHours = adaptiveCache.personalizedRecoveryHours[muscleName.lowercase().trim()]
            ?: adaptiveCache.personalizedRecoveryHours[normKey(muscleName)]
            ?: adaptiveCache.personalizedRecoveryHours.entries.firstOrNull {
                normKey(it.key) == normKey(muscleName)
            }?.value
        val baseRecoveryTime = clamp(
            adaptiveHours ?: AugeParameters.baseTauHours(muscleName),
            AugeParameters.TAU_MIN_H,
            AugeParameters.TAU_MAX_H,
        )

        var multiplier = nutritionMultiplier

        val acuteFeedbacks = feedbacks.filter { fb ->
            val fbMs = parseWellbeingDate(fb.date, zone)
            fbMs == 0L || (now - fbMs) <= ACUTE_FEEDBACK_WINDOW_MS
        }
        val feedbackPenaltyPct = calculateMuscleFeedbackPenaltyPct(muscleName, acuteFeedbacks)
        val discomfortPenaltyPct = calculateMuscleDiscomfortPenaltyPct(
            muscleName = muscleName,
            history = history,
            now = now,
            wellbeing = wellbeing,
        )
        val recoveryTimeMultiplier = 1.0 +
            (feedbackPenaltyPct.coerceAtLeast(0.0) / 48.0) +
            (discomfortPenaltyPct.coerceAtLeast(0.0) / 120.0)
        val sleepMult = systemicRecoveryMultiplier(wellbeing, sleepLogs)
        val realRecoveryTime = baseRecoveryTime * max(0.5, multiplier) * recoveryTimeMultiplier * sleepMult

        val k = AugeUtils.TAU_K_95 / max(1.0, realRecoveryTime)
        val tenDaysAgo = now - 10L * 24 * 3600 * 1000
        val muscularCap = (100 - physiologicalFloor(settings).muscular).coerceAtLeast(5).toDouble()
        val sessionStressCap = (muscularCap / 100.0) * capacity.coerceAtLeast(1.0)

        // Un V2 futuro se ignora también al elegir un posible ajuste global anterior.
        val manualOverrideV2 = manualMuscleOverride(wellbeing, muscleName)
            ?.takeIf { it.anchorEpochMs in (now - MANUAL_ANCHOR_WINDOW_MS + 1)..now }
        val manualScore = manualOverrideV2?.battery
            ?: wellbeing?.manualMuscleBatteries?.let { lookupMuscleScore(it, muscleName) }
        val anchorMs = manualOverrideV2?.anchorEpochMs ?: wellbeing?.legacyMuscleAnchorMs(muscleName, zone) ?: 0L
        val hoursSinceAnchor = max(0.0, (now - anchorMs) / 3_600_000.0)
        var accumulatedFatigue = 0.0

        // Un ajuste manual fechado después del instante evaluado aún no existía: no se aplica.
        val relevantHistory = if (manualScore != null && anchorMs > tenDaysAgo && anchorMs <= now) {
            val manualBattery = manualScore.coerceIn(0, 100).toDouble()
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val impliedRawFatiguePct = -AugeParameters.C_MUSCLE * ln(1.0 - penalty / 100.0)
            val manualLoad = (impliedRawFatiguePct / 100.0) * capacity
            accumulatedFatigue = manualLoad * exp(-k * com.example.kpkn.domain.auge.AugeUtils.getSigmoidalHours(hoursSinceAnchor))
            history.filter { logDateMs(it) > anchorMs && logDateMs(it) > tenDaysAgo }
        } else {
            history.filter { logDateMs(it) > tenDaysAgo }
        }

        var effectiveSetsCount = 0
        var lastSessionDate = 0L

        relevantHistory.forEach { log ->
            val logTime = logDateMs(log)
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)
            var sessionMuscleStress = 0.0
            var sessionSoftAccum = 0.0

            // V2 logs already contain canonical local stress and the capacity
            // captured at finish. Reuse that snapshot directly; applying the
            // historical role/activation path here would double-count it.
            val storedImpact = log.muscularImpactV2?.perMuscle?.entries
                ?.firstOrNull { (key, _) -> muscleMatchesCategory(key, muscleName) }
                ?.value
            if (storedImpact != null) {
                val logCap = AugeUtils.normalizeStoredCapacity(storedImpact.capacityAtCompletion, capacity)
                val decayedStress = storedImpact.stressUnits * safeExp(
                    -k * AugeUtils.getSigmoidalHours(hoursSince),
                )
                val equivalent = decayedStress * (capacity / logCap.coerceAtLeast(1.0))
                val capped = AugeUtils.applySessionSoftCap(equivalent, sessionSoftAccum, sessionStressCap)
                sessionSoftAccum += capped
                sessionMuscleStress += capped
                if (hoursSince <= 168.0 && capped > 0.0) {
                    effectiveSetsCount += log.completedExercises.sumOf { ex ->
                        val involvement = involvedMusclesFor(ex, resolveDbInfo(ex, exerciseDb))
                            .any { muscleMatchesCategory(it.muscle, muscleName) }
                        if (involvement) ex.sets.count(::isSetEffective) else 0
                    }
                    lastSessionDate = max(lastSessionDate, logTime)
                }
                accumulatedFatigue += sessionMuscleStress
                return@forEach
            }

            val recipe = sessionDrainRecipe(settings, tanks, adaptiveCache, exerciseDb, RecipeOptions(
                cardioHandling = RecipeOptions.CardioHandling.CONSUMER_HOOK,
                softCapUnits = RecipeOptions.SoftCapUnits.MUSCLE_STRESS_UNITS,
            ))
            recipe.forEachCompleted(
                exercises = log.completedExercises,
                onCardio = cardio@ { ex ->
                    val cardio = ex.cardioDetails ?: return@cardio
                    val duration = ex.sets.sumOf { it.timeSeconds ?: 0 }
                        .takeIf { it > 0 }
                        ?: return@cardio
                    val rpe = ex.sets.firstOrNull { (it.timeSeconds ?: 0) > 0 }?.rpe ?: cardio.resolvedRpe()
                    val cardioDrain = CardioRingDrainEngine.drain(cardio, duration, rpe, settings)
                    val matched = cardioDrain.muscleDrains.entries
                        .filter { (key, _) -> muscleMatchesCategory(key, muscleName) }
                        .sumOf { it.value } * AugeUtils.STRESS_UNITS_PER_DRAIN_PCT
                    val capped = recipe.softCap(SessionDrainRecipe.conserveCardio(matched), sessionSoftAccum, sessionStressCap)
                    sessionSoftAccum += capped
                    sessionMuscleStress += capped
                    if (hoursSince <= 168 && matched > 0.0) effectiveSetsCount++
                    if (logTime > lastSessionDate && matched > 0.0) lastSessionDate = logTime
                },
                onSet = { _, _, context, _, adjusted, _ ->
                    val involvement = context.involvedMuscles.find {
                        muscleMatchesCategory(it.muscle, muscleName)
                    }
                    if (involvement != null) {
                        val share = resolveMuscleVolumeContribution(involvement)
                        val contrib = adjusted.muscular * share * AugeUtils.STRESS_UNITS_PER_DRAIN_PCT
                        val capped = recipe.softCap(contrib, sessionSoftAccum, sessionStressCap)
                        sessionSoftAccum += capped
                        sessionMuscleStress += capped

                        if (hoursSince <= 168 && (involvement.role == MuscleRole.PRIMARY || involvement.role == MuscleRole.SECONDARY)) {
                            effectiveSetsCount++
                        }
                    }
                },
            )

            if (sessionMuscleStress > 0) {
                accumulatedFatigue += sessionMuscleStress * safeExp(-k * com.example.kpkn.domain.auge.AugeUtils.getSigmoidalHours(hoursSince))
                if (logTime > lastSessionDate) lastSessionDate = logTime
            }
        }

        val rawFatiguePct = (accumulatedFatigue / capacity) * 100.0
        val fatiguePenalty = clamp(100.0 * (1.0 - safeExp(-rawFatiguePct / AugeParameters.C_MUSCLE)), 0.0, 100.0)
        var battery = clamp(100.0 - fatiguePenalty, 0.0, 100.0)

        if (accumulatedFatigue <= 0.1 && (wellbeing?.doms ?: 1) <= 2) battery = 100.0

        val domsCap = when (wellbeing?.doms ?: 1) {
            5    -> 20.0
            4    -> 50.0
            3    -> 85.0
            else -> 100.0
        }
        val domsCapped = battery > domsCap
        battery = min(battery, domsCap)

        var hoursToRecovery = 0
        if (battery < 90 && accumulatedFatigue > 0) {
            val targetFatigue = -AugeParameters.C_MUSCLE * ln(0.9) * capacity / 100.0
            if (accumulatedFatigue > targetFatigue) {
                hoursToRecovery = max(0.0, -ln(targetFatigue / accumulatedFatigue) / k).toInt()
            }
        }

        val floor = physiologicalFloor(settings).muscular.toDouble()
        // Pre-workout discomfort is a readiness signal now, not only a τ stretch
        battery = clamp(battery - discomfortPenaltyPct * 0.35, 0.0, 100.0)
        // El suelo fisiológico se aplica después de las penalizaciones.
        battery = max(clamp(battery, 0.0, 100.0), floor)

        val status = when {
            battery >= 95 -> RecoveryStatus.FRESH
            battery >= 85 -> RecoveryStatus.OPTIMAL
            battery >= 40 -> RecoveryStatus.RECOVERING
            else          -> RecoveryStatus.EXHAUSTED
        }

        return MuscleBatteryDetail(
            exact = battery,
            status = MuscleRecoveryStatus(
                muscleName             = muscleName,
                // Se redondea (≥ 99,5 ⇒ 100): truncar dejaba el 99 durante días y sesgaba toda cifra −0,5.
                recoveryScore          = batteryPercent(battery),
                hoursToRecovery        = hoursToRecovery,
                hoursSinceLastSession  = if (lastSessionDate > 0) ((now - lastSessionDate) / 3_600_000).toInt() else -1,
                effectiveSets          = effectiveSetsCount,
                status                 = status,
            ),
            floor = floor,
            discomfortPenaltyApplied = discomfortPenaltyPct > 0.0,
            domsCapped = domsCapped,
        )
    }

    fun calculateMuscleSessionStress(
        muscleName: String,
        log: WorkoutLog,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        adaptiveCache: AugeAdaptiveCache
    ): Double {
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        var sessionMuscleStress = 0.0

        val recipe = sessionDrainRecipe(settings, tanks, adaptiveCache, exerciseDb, RecipeOptions(
            cardioHandling = RecipeOptions.CardioHandling.LEGACY_STRENGTH,
            softCapUnits = RecipeOptions.SoftCapUnits.NONE,
        ))
        recipe.forEachCompleted(
            exercises = log.completedExercises,
            onCardio = {},
            onSet = { _, _, context, _, adjusted, _ ->
                val involvement = context.involvedMuscles.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                }
                if (involvement != null) {
                    val share = resolveMuscleVolumeContribution(involvement)
                    sessionMuscleStress += adjusted.muscular * share * AugeUtils.STRESS_UNITS_PER_DRAIN_PCT
                }
            },
        )
        return sessionMuscleStress
    }

    // ─── 2. BATERÍA SNC / SISTÉMICA ───────────────────────────────────────────

    fun calculateSystemicFatigue(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Triple<Int, Int, Int> = calculateSystemicFatigueDetail(
        history, wellbeing, settings, exerciseDb, sleepLogs, feedbacks, adaptiveCache, nowOverride, zone,
    ).let { Triple(it.cnsBattery, it.gymLoad, it.lifeLoad) }

    /** Misma fórmula que [calculateSystemicFatigue]; conserva el Double de la batería antes de redondear. */
    internal fun calculateSystemicFatigueDetail(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SystemicFatigueDetail {
        val now = nowOverride
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val baseTau = adaptiveCache.cnsRecoveryHours ?: AugeParameters.DEFAULT_TAU_ENERGY_H
        val feedbackPenalty = calculateSystemFeedbackPenaltyPct(feedbacks)
        val sleepMult = systemicRecoveryMultiplier(wellbeing, sleepLogs)
        val cnsCap = (100 - physiologicalFloor(settings).cns).coerceAtLeast(5).toDouble()
        val tauHours = baseTau * (1.0 + feedbackPenalty.coerceAtLeast(0.0) / 48.0) * sleepMult
        val last10Days = now - 10L * 24 * 3600 * 1000

        val manualNeural = wellbeing?.manualNeuralBattery
        val anchorMs = wellbeing?.manualAnchorMs(RecoveryChannelId.SYSTEM, zone) ?: 0L
        val hoursSinceAnchor = max(0.0, (now - anchorMs) / 3_600_000.0)
        var accumulatedGymLoad = 0.0

        // Un ajuste manual posterior al instante evaluado aún no existía: no se aplica. Un registro posterior
        // tampoco había ocurrido: no cuenta (R-10), así que ambas ramas filtran a <= now.
        val recentLogs = if (manualNeural != null && anchorMs > last10Days && anchorMs <= now) {
            val manualBattery = manualNeural.coerceIn(0, 100).toDouble()
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val impliedRawGymPct = -AugeParameters.C_ENERGY * ln(1.0 - penalty / 100.0)
            val capacity = AugeParameters.energyCapacity(tanks.cns)
            val manualLoad = (impliedRawGymPct / 100.0) * capacity
            accumulatedGymLoad = manualLoad * AugeUtils.recoveryDecay(hoursSinceAnchor, tauHours)
            history.filter { logDateMs(it) > anchorMs && logDateMs(it) > last10Days && logDateMs(it) <= now }
        } else {
            history.filter { logDateMs(it) > last10Days && logDateMs(it) <= now }
        }

        recentLogs.forEach { log ->
            val hoursSince = max(0.0, (now - logDateMs(log)) / 3_600_000.0)
            var sessionCns = 0.0
            var sessionSoftAccum = 0.0

            val recipe = sessionDrainRecipe(settings, tanks, adaptiveCache, exerciseDb, RecipeOptions(
                cardioHandling = RecipeOptions.CardioHandling.CONSUMER_HOOK,
                softCapUnits = RecipeOptions.SoftCapUnits.LOG_PERCENT,
            ))
            recipe.forEachCompleted(
                exercises = log.completedExercises,
                onCardio = cardio@ { ex ->
                    val cardio = ex.cardioDetails ?: return@cardio
                    val duration = ex.sets.sumOf { it.timeSeconds ?: 0 }
                        .takeIf { it > 0 }
                        ?: return@cardio
                    val rpe = ex.sets.firstOrNull { (it.timeSeconds ?: 0) > 0 }?.rpe ?: cardio.resolvedRpe()
                    val cardioDrain = CardioRingDrainEngine.drain(cardio, duration, rpe, settings)
                    val capped = recipe.softCap(
                        SessionDrainRecipe.conserveCardio(cardioDrain.cns),
                        sessionSoftAccum,
                        cnsCap,
                    )
                    sessionSoftAccum += capped
                    sessionCns += capped
                },
                onSet = { _, _, _, _, adjusted, _ ->
                    val capped = recipe.softCap(adjusted.cns, sessionSoftAccum, cnsCap)
                    sessionSoftAccum += capped
                    sessionCns += capped
                },
            )

            sessionCns = SessionDrainRecipe.durationCns(sessionCns, log.durationMinutes)

            accumulatedGymLoad += sessionCns * AugeUtils.recoveryDecay(hoursSince, tauHours)
        }

        val capacity = AugeParameters.energyCapacity(tanks.cns)
        val rawGymPct = (accumulatedGymLoad / capacity) * 100.0
        val normalizedGymFatigue = clamp(100.0 * (1.0 - safeExp(-rawGymPct / AugeParameters.C_ENERGY)), 0.0, 100.0)

        val total = normalizedGymFatigue
        val cnsExact = clamp(100.0 - total, 0.0, 100.0)

        return SystemicFatigueDetail(
            exact = cnsExact,
            cnsBattery = batteryPercent(cnsExact),
            gymLoad = batteryPercent(normalizedGymFatigue),
            lifeLoad = 0,
        )
    }

    // ─── 3. BATERÍA ESPINAL ───────────────────────────────────────────────────

    internal val SPINE_GUARD_MUSCLES = AugeParameters.SPINE_GUARD_MUSCLES

    private fun calculateSpineFatigueMultiplier(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        sleepLogs: List<SleepLog>,
        nutritionMultiplier: Double,
        feedbacks: List<PostSessionFeedback>,
        adaptiveCache: AugeAdaptiveCache,
        erectorsCapacity: Double? = null,
        latsCapacity: Double? = null,
        trapsCapacity: Double? = null,
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Double {
        val erectors = calculateMuscleBattery("Erectores Espinales", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, erectorsCapacity, nowOverride, zone).recoveryScore.toDouble()
        val lats = calculateMuscleBattery("Dorsales", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, latsCapacity, nowOverride, zone).recoveryScore.toDouble()
        val traps = calculateMuscleBattery("Trapecio", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, trapsCapacity, nowOverride, zone).recoveryScore.toDouble()
        // Lumbar maps to Erectores. Guard muscles: erectores/lumbar, dorsales, trapecio.
        val spf = (erectors * 0.45) + (lats * 0.30) + (traps * 0.25)

        if (spf >= 80.0) return 1.0

        val fatigueDeficit = (100.0 - spf) / 100.0
        return 1.0 + (fatigueDeficit * fatigueDeficit * 0.75)
    }

    fun calculateSpinalBattery(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
    ): Int = calculateSpinalBatteryDetail(
        history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs, feedbacks, adaptiveCache,
        nowOverride, zone, nutritionContext,
    ).battery

    /** Misma fórmula que [calculateSpinalBattery]; conserva el Double crudo antes de redondear. */
    internal fun calculateSpinalBatteryDetail(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowOverride: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
    ): SpinalBatteryDetail {
        val now = nowOverride
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val tauHours = adaptiveCache.spinalRecoveryHours ?: AugeParameters.DEFAULT_TAU_SPINAL_H
        val spinalCap = (100 - physiologicalFloor(settings).spinal).coerceAtLeast(5).toDouble()
        val last10Days = now - 10L * 24 * 3600 * 1000

        val manualSpinal = wellbeing?.manualSpinalBattery
        val anchorMs = wellbeing?.manualAnchorMs(RecoveryChannelId.STRUCTURE, zone) ?: 0L
        val hoursSinceAnchor = max(0.0, (now - anchorMs) / 3_600_000.0)
        // Ancla más reciente del bienestar: un log anterior a ella se evalúa sin el estado de hoy (R-09 b).
        val latestAnchorMs = latestManualAnchorMs(wellbeing, zone)
        var accumulatedSpinalLoad = 0.0

        // Un ajuste manual posterior al instante evaluado aún no existía: no se aplica. Un registro posterior
        // tampoco había ocurrido: no cuenta (R-10), así que ambas ramas filtran a <= now.
        val recentLogs = if (manualSpinal != null && anchorMs > last10Days && anchorMs <= now) {
            val manualBattery = manualSpinal.coerceIn(0, 100).toDouble()
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val impliedRawPct = -AugeParameters.C_SPINAL * ln(1.0 - penalty / 100.0)
            val capacity = AugeParameters.spinalCapacity(tanks.spinal)
            val manualLoad = (impliedRawPct / 100.0) * capacity
            accumulatedSpinalLoad = manualLoad * AugeUtils.recoveryDecay(
                AugeUtils.getSpinalRecoveryHours(hoursSinceAnchor),
                tauHours,
            )
            history.filter { logDateMs(it) > anchorMs && logDateMs(it) > last10Days && logDateMs(it) <= now }
        } else {
            history.filter { logDateMs(it) > last10Days && logDateMs(it) <= now }
        }

        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel, nutritionContext, now, zone)

        val completionIso = Instant.ofEpochMilli(now).toString()
        val erectorsCapacity = AugeMuscleCapacityEngine.calculateUserWorkCapacity(
            muscleName = "Erectores Espinales",
            history = history,
            settings = settings,
            exerciseDb = exerciseDb,
            completionInstantIso = completionIso,
            adaptiveCache = adaptiveCache,
        )
        val latsCapacity = AugeMuscleCapacityEngine.calculateUserWorkCapacity(
            muscleName = "Dorsales",
            history = history,
            settings = settings,
            exerciseDb = exerciseDb,
            completionInstantIso = completionIso,
            adaptiveCache = adaptiveCache,
        )
        val trapsCapacity = AugeMuscleCapacityEngine.calculateUserWorkCapacity(
            muscleName = "Trapecio",
            history = history,
            settings = settings,
            exerciseDb = exerciseDb,
            completionInstantIso = completionIso,
            adaptiveCache = adaptiveCache,
        )

        recentLogs.forEach { log ->
            val logTime = logDateMs(log)
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)

            // Corrección de Causalidad: la guardia espinal se mide con la historia ESTRICTAMENTE anterior a logTime,
            // así el propio log no baja la guardia que multiplica su drenaje espinal (R-09 a).
            val historyUpToLog = history.filter { logDateMs(it) < logTime }
            val feedbacksUpToLog = feedbacks.filter { fb ->
                try {
                    val fbDateMs = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.parse(fb.date)?.time ?: 0L
                    fbDateMs <= logTime
                } catch (_: Exception) { false }
            }

            val spineProtectionMultiplier = calculateSpineFatigueMultiplier(
                history = historyUpToLog,
                // Un log anterior a las anclas del bienestar no ve el estado de hoy: no lo reescribe (R-09 b).
                wellbeing = if (logTime < latestAnchorMs) null else wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionMultiplier = nutritionMultiplier,
                feedbacks = feedbacksUpToLog,
                adaptiveCache = adaptiveCache,
                erectorsCapacity = erectorsCapacity,
                latsCapacity = latsCapacity,
                trapsCapacity = trapsCapacity,
                nowOverride = logTime,
                zone = zone,
            )

            var sessionSpinalLoad = 0.0
            var sessionSoftAccum = 0.0

            val recipe = sessionDrainRecipe(settings, tanks, adaptiveCache, exerciseDb, RecipeOptions(
                cardioHandling = RecipeOptions.CardioHandling.CONSUMER_HOOK,
                softCapUnits = RecipeOptions.SoftCapUnits.LOG_PERCENT,
            ))
            recipe.forEachCompleted(
                exercises = log.completedExercises,
                onCardio = cardio@ { ex ->
                    val cardio = ex.cardioDetails ?: return@cardio
                    val duration = ex.sets.sumOf { it.timeSeconds ?: 0 }
                        .takeIf { it > 0 }
                        ?: return@cardio
                    val rpe = ex.sets.firstOrNull { (it.timeSeconds ?: 0) > 0 }?.rpe ?: cardio.resolvedRpe()
                    val cardioDrain = CardioRingDrainEngine.drain(cardio, duration, rpe, settings)
                    val capped = recipe.softCap(
                        SessionDrainRecipe.conserveCardio(cardioDrain.spinal),
                        sessionSoftAccum,
                        spinalCap,
                    )
                    sessionSoftAccum += capped
                    sessionSpinalLoad += capped
                },
                onSet = { _, _, _, _, adjusted, _ -> // La receta ya aplicó la puerta axial antes de acumular.
                    val contrib = adjusted.spinal * spineProtectionMultiplier // SPF conserva su posición posterior a la receta.
                    val capped = recipe.softCap(contrib, sessionSoftAccum, spinalCap)
                    sessionSoftAccum += capped
                    sessionSpinalLoad += capped
                },
            )

            sessionSpinalLoad = SessionDrainRecipe.durationSpinal(sessionSpinalLoad, log.durationMinutes)
            accumulatedSpinalLoad += sessionSpinalLoad * AugeUtils.recoveryDecay(
                AugeUtils.getSpinalRecoveryHours(hoursSince),
                tauHours,
            )
        }

        val capacity = AugeParameters.spinalCapacity(tanks.spinal)
        val rawPct = (accumulatedSpinalLoad / capacity) * 100.0
        val normalizedLoad = clamp(100.0 * (1.0 - safeExp(-rawPct / AugeParameters.C_SPINAL)), 0.0, 100.0)
        val exact = clamp(100.0 - normalizedLoad, 0.0, 100.0)
        return SpinalBatteryDetail(exact = exact, battery = batteryPercent(exact))
    }

    // ─── 4. BATERÍAS GLOBALES ─────────────────────────────────────────────────

    fun calculateGlobalBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        precomputedMuscles: Map<String, MuscleRecoveryStatus>? = null,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
        nowOverrideMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
    ): GlobalBatteries = calculateGlobalBatteriesDetail(
        history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs, feedbacks, adaptiveCache,
        precomputedMuscles, articularBatteries, nowOverrideMs, zone, nutritionContext,
    ).batteries

    /**
     * Misma fórmula que [calculateGlobalBatteries]; conserva los intermedios continuos (sin cambiar ningún entero).
     * [precomputedMuscleDetails] evita una segunda evaluación del motor cuando el llamador ya tiene los detalles.
     */
    internal fun calculateGlobalBatteriesDetail(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        precomputedMuscles: Map<String, MuscleRecoveryStatus>? = null,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
        nowOverrideMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
        precomputedMuscleDetails: Map<String, MuscleBatteryDetail>? = null,
    ): GlobalBatteriesDetail {
        val evaluationNow = nowOverrideMs
        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel, nutritionContext, evaluationNow, zone)
        val precomputedStatuses = precomputedMuscles ?: precomputedMuscleDetails?.mapValues { it.value.status }

        val gatedPillarBatteries = mutableListOf<Int>()
        val gatedPillarExacts = mutableListOf<Double>()
        PILLAR_MUSCLES.forEach { muscleName ->
            val detail = precomputedMuscleDetails?.get(muscleName)
            val status = precomputedStatuses?.get(muscleName)
            if (precomputedStatuses != null && detail == null && status == null) return@forEach
            val computedDetail = if (precomputedStatuses == null) {
                calculateMuscleBatteryDetail(
                    muscleName = muscleName,
                    history = history,
                    wellbeing = wellbeing,
                    settings = settings,
                    exerciseDb = exerciseDb,
                    nutritionMultiplier = nutritionMultiplier,
                    sleepLogs = sleepLogs,
                    feedbacks = feedbacks,
                    adaptiveCache = adaptiveCache,
                    nowOverride = evaluationNow,
                    zone = zone,
                )
            } else {
                null
            }
            val muscleScore = detail?.status?.recoveryScore
                ?: status?.recoveryScore
                ?: computedDetail?.status?.recoveryScore
                ?: return@forEach
            val muscleExact = detail?.exact ?: computedDetail?.exact ?: muscleScore.toDouble()

            val relatedArtic = AugeTtcEngine.articularBatteriesFor(muscleName)
            val articularAvg = if (relatedArtic.isEmpty()) {
                null
            } else {
                relatedArtic
                    .mapNotNull { articularBatteries[it]?.recoveryScore }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
            }
            if (articularAvg == null) {
                gatedPillarBatteries += muscleScore
                gatedPillarExacts += muscleExact
            } else {
                // BLEND SUAVE: muscular * (0.6 + 0.4 * articular/100)
                val coef = 0.6 + 0.4 * (articularAvg / 100.0)
                gatedPillarBatteries += (muscleScore * coef).roundToInt().coerceIn(0, 100)
                gatedPillarExacts += muscleExact * coef
            }
        }

        // El ajuste muscular global tampoco existe antes de su ancla: se usa el cálculo automático.
        val manualMuscular = wellbeing?.manualMuscularBattery?.takeIf {
            wellbeing.manualAnchorMs(RecoveryChannelId.MUSCULAR, zone) in (evaluationNow - MANUAL_ANCHOR_WINDOW_MS + 1)..evaluationNow
        }
        var manualRecovered = 0.0
        val muscularAvg = if (manualMuscular != null) {
            // Same anchor + temporal decay semantics as neural/spinal — do not freeze raw value all day
            val manualBattery = manualMuscular.coerceIn(0, 100).toDouble()
            val anchorMs = wellbeing.manualAnchorMs(RecoveryChannelId.MUSCULAR, zone)
            val hoursSinceAnchor = max(0.0, (evaluationNow - anchorMs) / 3_600_000.0)
            val baseTau = adaptiveCache.personalizedRecoveryHours.values
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H
            val sleepMult = systemicRecoveryMultiplier(wellbeing, sleepLogs)
            val tauHours = max(1.0, baseTau * sleepMult)
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val k = AugeUtils.TAU_K_95 / tauHours
            manualRecovered = 100.0 - penalty * exp(-k * AugeUtils.getSigmoidalHours(hoursSinceAnchor))
            batteryPercent(manualRecovered)
        } else if (gatedPillarBatteries.isEmpty()) {
            100
        } else {
            val overallAvg = gatedPillarBatteries.average()
            val sortedAsc = gatedPillarBatteries.sorted()
            val bottomCount = maxOf(1, (sortedAsc.size * 0.25).toInt())
            val bottomQuartileAvg = sortedAsc.take(bottomCount).average()
            batteryPercent(overallAvg * 0.85 + bottomQuartileAvg * 0.15)
        }

        val muscularAvgExact = if (manualMuscular != null) {
            manualRecovered
        } else if (gatedPillarExacts.isEmpty()) {
            100.0
        } else {
            val overallAvg = gatedPillarExacts.average()
            val sortedAsc = gatedPillarExacts.sorted()
            val bottomCount = maxOf(1, (sortedAsc.size * 0.25).toInt())
            val bottomQuartileAvg = sortedAsc.take(bottomCount).average()
            overallAvg * 0.85 + bottomQuartileAvg * 0.15
        }

        // Solo un ajuste ya vigente sustituye al anillo automático con ACWR.
        val muscularWithAcwr = if (manualMuscular != null) {
            muscularAvg.toDouble()
        } else {
            // ACWR comparte el instante explícito del resto del cálculo.
            applyMuscularAcwr(muscularAvg.toDouble(), history, evaluationNow)
        }
        val muscularWithAcwrExact = if (manualMuscular != null) {
            muscularAvgExact
        } else {
            applyMuscularAcwr(muscularAvgExact, history, evaluationNow)
        }

        val systemic = calculateSystemicFatigueDetail(
            history = history,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
            nowOverride = evaluationNow,
            zone = zone,
        )
        val cncBattery = systemic.cnsBattery
        val spinalDetail = calculateSpinalBatteryDetail(
            history = history,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            nutritionContext = nutritionContext,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
            nowOverride = evaluationNow,
            zone = zone,
        )
        val spinalBattery = spinalDetail.battery

        val floor = physiologicalFloor(settings)

        // Los tres valores finales de los anillos también se redondean: aquí ya hay decimales (ACWR y desaceleración).
        val decelMuscular = decelerateBattery(muscularWithAcwr)
        val finalMuscular = batteryPercent(
            max(
                clamp(decelMuscular, 0.0, 100.0),
                floor.muscular.toDouble(),
            ),
        )
        val finalCnc = batteryPercent(
            max(
                decelerateBattery(
                    clamp(cncBattery.toDouble(), 0.0, 100.0),
                ),
                floor.cns.toDouble(),
            ),
        )
        val finalSpinal = batteryPercent(
            max(
                decelerateBattery(
                    clamp(spinalBattery.toDouble(), 0.0, 100.0),
                ),
                floor.spinal.toDouble(),
            ),
        )
        val finalMuscularExact = max(
            clamp(decelerateBattery(muscularWithAcwrExact), 0.0, 100.0),
            floor.muscular.toDouble(),
        )
        val finalSpinalExact = max(
            decelerateBattery(clamp(spinalDetail.exact, 0.0, 100.0)),
            floor.spinal.toDouble(),
        )

        val initialContribution = InitialRecoveryEvidencePolicy.resolve(
            InitialRecoveryPolicyInput(
                evidence = settings.initialRecoveryEvidence,
                nowMs = evaluationNow,
                workoutLogs = history,
                zone = zone,
            ),
        )
        // Las anclas futuras tampoco pueden desactivar una estimación inicial que sí existía.
        val anchorWindow = (evaluationNow - MANUAL_ANCHOR_WINDOW_MS + 1)..evaluationNow
        val muscularEstimated = initialContribution.isEstimated && !hasManualMuscularAnchorsAt(wellbeing, evaluationNow, zone)
        val systemEstimated = initialContribution.isEstimated && (wellbeing?.manualNeuralBattery == null || wellbeing.manualAnchorMs(RecoveryChannelId.SYSTEM, zone) !in anchorWindow)
        val structureEstimated = initialContribution.isEstimated && (wellbeing?.manualSpinalBattery == null || wellbeing.manualAnchorMs(RecoveryChannelId.STRUCTURE, zone) !in anchorWindow)
        val estimated = muscularEstimated || systemEstimated || structureEstimated
        fun combine(real: Int, initial: Int?, enabled: Boolean, minimum: Int): Int =
            if (enabled && initial != null) batteryPercent(real * initial / 100.0).coerceIn(minimum, 100) else real
        fun combineExact(real: Double, initial: Int?, enabled: Boolean, minimum: Int): Double =
            if (enabled && initial != null) (real * initial / 100.0).coerceIn(minimum.toDouble(), 100.0) else real
        // Con evidencia v2 y los pilares de precomputedMuscles (getPerMuscleBatteries), la parte muscular ya está
        // dentro de los pilares: no componerla otra vez.
        val initialInPillars = precomputedStatuses != null &&
            PILLAR_MUSCLES.all { it in precomputedStatuses } &&
            (settings.initialRecoveryEvidence?.estimatorVersion ?: 0) >= 2
        val muscularComposedExact = if (initialInPillars) {
            finalMuscularExact
        } else {
            combineExact(finalMuscularExact, initialContribution.muscular, muscularEstimated, floor.muscular)
        }
        return GlobalBatteriesDetail(
            batteries = GlobalBatteries(
                muscular = if (initialInPillars) {
                    finalMuscular
                } else {
                    combine(finalMuscular, initialContribution.muscular, muscularEstimated, floor.muscular)
                },
                cnc = combine(finalCnc, initialContribution.system, systemEstimated, floor.cns),
                spinal = combine(finalSpinal, initialContribution.structure, structureEstimated, floor.spinal),
                sourceLabel = when {
                    estimated -> "Estimación inicial"
                    history.isNotEmpty() -> "Historial real"
                    wellbeing?.manualMuscularBattery != null || wellbeing?.manualNeuralBattery != null || wellbeing?.manualSpinalBattery != null -> "Ajuste manual"
                    else -> "Sin calibrar"
                },
                sourceConfidence = initialContribution.confidence.takeIf { estimated },
                sourceId = if (estimated) "declared-wizard" else if (history.isNotEmpty()) "workout-history" else null,
                sourceAnchorMs = initialContribution.anchorMs.takeIf { estimated },
                sourceExpiresAtMs = settings.initialRecoveryEvidence?.expiresAtMs?.takeIf { estimated },
            ),
            energyExact = systemic.exact,
            spineRawExact = spinalDetail.exact,
            finalSpinalExact = combineExact(finalSpinalExact, initialContribution.structure, structureEstimated, floor.spinal),
            musclesGlobalExact = muscularComposedExact,
            musclesGlobalBlocked = manualMuscular != null,
        )
    }

    // ─── 5. PER-MUSCLE BATTERIES (pilares + grupos con perfil) ────────────────

    fun getPerMuscleBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowOverrideMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
    ): Map<String, MuscleRecoveryStatus> =
        getPerMuscleBatteryDetails(
            history, wellbeing, settings, exerciseDb, sleepLogs, nutritionLogs, feedbacks, adaptiveCache,
            nowOverrideMs, zone, nutritionContext,
        ).mapValues { it.value.status }

    /** Mismos estados que [getPerMuscleBatteries], con el Double de cada músculo y sus topes. */
    internal fun getPerMuscleBatteryDetails(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowOverrideMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
    ): Map<String, MuscleBatteryDetail> {
        val evaluationNow = nowOverrideMs
        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel, nutritionContext, evaluationNow, zone)

        // Optimización O(N): Truncar historia a los últimos 30 días (fatiga anterior a 30 días es < 0.001%)
        val thirtyDaysAgo = evaluationNow - 30L * 24 * 3600_000L
        // A finish preview is evaluated at its frozen instant. A log at that
        // instant is not allowed to increase the capacity used to measure itself.
        val historyForCapacity = history.filter {
            val ms = logDateMs(it)
            ms > 0L && ms in thirtyDaysAgo until evaluationNow
        }
        val historyForBatteries = history.filter {
            val ms = logDateMs(it)
            ms > 0L && ms in thirtyDaysAgo..evaluationNow
        }

        // Optimización O(N^2): Precalcular capacidades de los músculos antes de iterar
        val precomputedCapacities = BATTERY_MUSCLES.associateWith { muscle ->
            AugeMuscleCapacityEngine.calculateUserWorkCapacity(
                muscleName = muscle,
                history = historyForCapacity,
                settings = settings,
                exerciseDb = exerciseDb,
                completionInstantIso = Instant.ofEpochMilli(evaluationNow).toString(),
                adaptiveCache = adaptiveCache,
            )
        }

        // Evidencia v2 de RINGS (E3a): cada pilar compone su carga con la misma ley multiplicativa del global.
        // Se omite con ajustes manuales musculares, como calculateGlobalBatteries, para no contar dos veces.
        // Un ajuste posterior al corte no sustituye la evidencia inicial de los pilares.
        val hasManualMuscularAnchors = hasManualMuscularAnchorsAt(wellbeing, evaluationNow, zone)
        val initialByPillar = if (hasManualMuscularAnchors) {
            emptyMap<String, Int>()
        } else {
            InitialRecoveryEvidencePolicy.v2PillarBatteries(
                InitialRecoveryPolicyInput(
                    evidence = settings.initialRecoveryEvidence,
                    nowMs = evaluationNow,
                    workoutLogs = history,
                    zone = zone,
                ),
                InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS,
            )
        }
        val muscularFloor = physiologicalFloor(settings).muscular

        return BATTERY_MUSCLES.associateWith { muscle ->
            val detail = calculateMuscleBatteryDetail(
                muscleName = muscle,
                history = historyForBatteries,
                wellbeing = wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                nutritionMultiplier = nutritionMultiplier,
                sleepLogs = sleepLogs,
                feedbacks = feedbacks,
                adaptiveCache = adaptiveCache,
                precomputedCapacity = precomputedCapacities[muscle],
                nowOverride = evaluationNow,
                zone = zone,
            )
            val initial = initialByPillar[muscle] ?: return@associateWith detail
            combineWithInitialEvidence(detail, initial, muscularFloor)
        }
    }

    /** Sumar fatigas antes de la exponencial equivale a real · inicial / 100; se conserva el suelo. */
    private fun combineWithInitialEvidence(
        detail: MuscleBatteryDetail,
        initial: Int,
        floor: Int,
    ): MuscleBatteryDetail {
        if (initial >= 100) return detail
        val status = detail.status
        val score = batteryPercent(status.recoveryScore * initial / 100.0).coerceIn(floor, 100)
        return detail.copy(
            exact = (detail.exact * initial / 100.0).coerceIn(floor.toDouble(), 100.0),
            status = status.copy(recoveryScore = score, status = recoveryStatusFor(score)),
        )
    }

    private fun recoveryStatusFor(battery: Int): RecoveryStatus = when {
        battery >= 95 -> RecoveryStatus.FRESH
        battery >= 85 -> RecoveryStatus.OPTIMAL
        battery >= 40 -> RecoveryStatus.RECOVERING
        else -> RecoveryStatus.EXHAUSTED
    }

    // ─── 6. DASHBOARD Y READINESS DIARIA ─────────────────────────────────────

    fun calculateRecoveryDashboard(
        batteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog> = emptyList(),
        recentSessionCount: Int = 0,
    ): RecoveryDashboard = calculateRecoveryDashboardDetail(
        batteries, perMuscle, articularBatteries, wellbeing, sleepLogs, recentSessionCount,
    ).dashboard

    /** Misma fórmula que [calculateRecoveryDashboard]; [spinalExact] permite usar la Columna cruda continua. */
    internal fun calculateRecoveryDashboardDetail(
        batteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog> = emptyList(),
        recentSessionCount: Int = 0,
        spinalExact: Double = batteries.spinal.toDouble(),
    ): RecoveryDashboardDetail {
        val weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        val lowestMuscles = perMuscle.values.sortedBy { it.recoveryScore }.take(2)
        val weakestArticular = articularBatteries.entries.sortedBy { it.value.recoveryScore }.take(2)
        val articularFloor = weakestArticular
            .takeIf { it.isNotEmpty() }
            ?.map { it.value.recoveryScore.toDouble() }
            ?.average()
            ?.let { batteryPercent(it) }
            ?: 100
        val guardScores = SPINE_GUARD_MUSCLES.mapNotNull { name ->
            perMuscle.entries.firstOrNull { muscleMatchesCategory(it.key, name) }?.value?.recoveryScore
        }
        val structureScore = structureRingScore(
            spinal = batteries.spinal,
            articularFloor = articularFloor,
            guardMuscleScores = guardScores,
        )
        val structureDetail = structureRingScoreDetail(
            spinal = spinalExact,
            articularFloor = articularFloor,
            guardMuscleScores = guardScores,
        )
        val muscularScore = batteries.muscular
        val systemScore = batteries.cnc
        val displayStructureScore = structureScore
        val muscleGuard = guardScores.minOrNull() ?: 100

        val baseConfidence = when {
            recentSessionCount >= 16 -> 82
            recentSessionCount >= 8 -> 70
            recentSessionCount >= 4 -> 58
            else -> 44
        }
        val muscularConfidence = (baseConfidence + if (perMuscle.isNotEmpty()) 8 else 0).coerceIn(35, 95)
        val systemConfidence = (
            baseConfidence +
                (if (sleepLogs.size >= 3) 12 else 0) +
                (if (wellbeing != null) 8 else 0)
            ).coerceIn(35, 95)
        val structureConfidence = (baseConfidence + if (articularBatteries.isNotEmpty()) 12 else 0).coerceIn(35, 95)

        val muscularCauses = buildList {
            if (lowestMuscles.isNotEmpty() && lowestMuscles.first().recoveryScore < RecoveryBands.HIGH_MIN) {
                add(lowestMuscles.joinToString(" y ") { "${it.muscleName} ${it.recoveryScore}%" })
            } else {
                add("Promedio de grupos pilar estable")
            }
            if (wellbeing?.manualMuscularBattery != null) add("Ajuste manual de readiness")
            if ((wellbeing?.doms ?: 1) >= 4) add("Agujetas altas hoy")
            if (weightedSleep != null && weightedSleep < 6.5) add("Sueño reciente por debajo de lo ideal")
        }
        val systemCauses = buildList {
            when {
                weightedSleep == null -> Unit
                weightedSleep < 5.5 -> add("Poco sueño en las últimas noches")
                weightedSleep < 6.5 -> add("Sueño subóptimo reciente")
                weightedSleep >= 8.5 -> add("Buen colchón de sueño")
            }
            if (wellbeing?.manualNeuralBattery != null) add("Ajuste manual de readiness")
            if ((wellbeing?.stressLevel ?: 3) >= 4) add("Estrés alto fuera del entrenamiento")
            if (systemScore < RecoveryBands.NORMAL_MIN) add("Carga neural reciente acumulada")
        }
        val structureCauses = buildList {
            if (batteries.spinal < RecoveryBands.NORMAL_MIN) add("Carga axial reciente elevada")
            if (muscleGuard < RecoveryBands.NORMAL_MIN) add("Dorsales, erectores o trapecio aún cargados")
            if (weakestArticular.isNotEmpty() && articularFloor < RecoveryBands.HIGH_MIN) {
                add(weakestArticular.joinToString(" y ") { "${AugeTtcEngine.articularLabel(it.key)} ${it.value.recoveryScore}%" })
            }
            if (isEmpty() && displayStructureScore >= RecoveryBands.NORMAL_MIN) add("Sin cuello de botella estructural claro")
            if (wellbeing?.manualSpinalBattery != null) add("Ajuste manual de readiness")
            if ((wellbeing?.doms ?: 1) >= 4) add("Tejidos aún sensibles hoy")
        }

        val channels = listOf(
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.MUSCULAR,
                title = "Músculos",
                shortTitle = "Mús.",
                score = muscularScore,
                band = recoveryBand(muscularScore),
                description = RecoveryBands.description(RecoveryChannelId.MUSCULAR),
                action = actionForChannel(RecoveryChannelId.MUSCULAR, muscularScore),
                causes = muscularCauses.take(3),
                confidence = muscularConfidence,
                editable = false,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.SYSTEM,
                title = "Energía",
                shortTitle = "En.",
                score = systemScore,
                band = recoveryBand(systemScore),
                description = RecoveryBands.description(RecoveryChannelId.SYSTEM),
                action = actionForChannel(RecoveryChannelId.SYSTEM, systemScore),
                causes = systemCauses.take(3),
                confidence = systemConfidence,
                editable = true,
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.STRUCTURE,
                title = "Columna",
                shortTitle = "Col.",
                score = displayStructureScore,
                band = recoveryBand(displayStructureScore),
                description = RecoveryBands.description(RecoveryChannelId.STRUCTURE),
                action = actionForChannel(RecoveryChannelId.STRUCTURE, displayStructureScore),
                causes = structureCauses.take(3),
                confidence = structureConfidence,
                editable = true,
            ),
        )

        // Promedio ponderado de tres enteros: se redondea, no se trunca (truncar restaba 0,5 de media).
        val overallScore = batteryPercent(
            channels.first { it.id == RecoveryChannelId.SYSTEM }.score * 0.40 +
                channels.first { it.id == RecoveryChannelId.MUSCULAR }.score * 0.35 +
                channels.first { it.id == RecoveryChannelId.STRUCTURE }.score * 0.25,
        )
        val headline = RecoveryBands.headline(recoveryBand(overallScore))
        val recommendation = RecoveryBands.recommendation(recoveryBand(overallScore))
        val confidenceAverage = channels.map { it.confidence }.average().toInt()
        val limitingChannel = channels.minByOrNull { it.score }
        val summary = limitingChannel?.let {
            "${it.title} es hoy el factor limitante (${it.score}%)."
        } ?: "Tu estado está equilibrado."

        return RecoveryDashboardDetail(
            dashboard = RecoveryDashboard(
                overallScore = overallScore,
                headline = headline,
                summary = summary,
                recommendation = recommendation,
                confidenceLabel = confidenceLabel(confidenceAverage),
                channels = channels,
                dataLabel = batteries.sourceLabel,
            ),
            structureExact = structureDetail.exact,
            structureBlocked = structureDetail.articularDriven,
        )
    }

    fun calculateDailyReadiness(
        dashboard: RecoveryDashboard,
        wellbeing: DailyWellbeingLog?,
    ): AugeReadinessVerdict {
        val score = dashboard.overallScore.coerceIn(0, 100)
        val (label, color) = RecoveryBands.dailyReadinessLabel(recoveryBand(score))
        val details = buildList {
            dashboard.channels.flatMap { it.causes }.take(4).forEach { add(it) }
            if ((wellbeing?.doms ?: 1) >= 4) add("Agujetas altas reportadas")
        }.distinct()

        return AugeReadinessVerdict(
            score = score,
            label = label,
            color = color,
            details = details,
            action = dashboard.recommendation,
            confidenceLabel = dashboard.confidenceLabel,
        )
    }

    // ─── 9. MULTIPLICADOR NUTRICIONAL ────────────────────────────────────────

    fun getNutritionMultiplier(
        settings: Settings,
        nutritionLogs: List<NutritionLog>,
        stressLevel: Int,
        context: NutritionRecoveryContext,
        nowMs: Long,
        zone: ZoneId,
    ): Double {
        if (!settings.algorithmSettings.augeEnableNutritionTracking) return 1.0
        return NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = nutritionLogs,
            goalsByDate = context.goalsByDate,
            stressLevel = stressLevel,
            nowMs = nowMs,
            zone = zone,
        ).recoveryTimeMultiplier
    }

    // Expose PILLAR_MUSCLES para la UI
    val pillarMuscles: List<String> get() = PILLAR_MUSCLES

    // ─── Post-Session Preview (finish-sheet ↔ home-screen consistency) ─────────

    /** Vista previa = Home con el log transitorio (y su V2) dentro, en el instante congelado. */
    @Suppress("UNUSED_PARAMETER")
    fun previewPostSessionBatteries(
        baseHistory: List<WorkoutLog>,
        previewLog: WorkoutLog,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        // Compatibilidad: el computador recalcula siempre la articular con la sesión incluida.
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
        nowOverrideMs: Long,
        finishOperationId: String? = null,
        completionInstantIso: String? = null,
        inputHash: String? = null,
        automaticImpact: MuscularSessionImpactV2? = null,
        nutritionContext: NutritionRecoveryContext = NutritionRecoveryContext(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PostSessionPreview {
        val evaluationNow = nowOverrideMs
        val impact = automaticImpact ?: previewLog.muscularImpactV2
        val closedLog = previewLog.copy(muscularImpactV2 = impact)
        val inputs = AugeSnapshotInputs(
            history = baseHistory,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            nowMs = evaluationNow,
            zone = zone,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
            nutritionContext = nutritionContext,
        )
        val base = AugeSnapshotComputer.compute(inputs)
        val closedInputs = inputs.copy(history = baseHistory + closedLog)
        val closed = AugeSnapshotComputer.compute(closedInputs)
        val tomorrow = AugeSnapshotComputer.compute(closedInputs.copy(nowMs = evaluationNow + 24L * 3_600_000L))
        val structure = closed.dashboard.channelScore(RecoveryChannelId.STRUCTURE, closed.batteries.spinal)
        val baseStructure = base.dashboard.channelScore(RecoveryChannelId.STRUCTURE, base.batteries.spinal)
        return PostSessionPreview(
            neural = closed.batteries.cnc,
            spinal = structure,
            muscular = closed.batteries.muscular,
            perMuscle = closed.perMuscle,
            globalCnsDrain = (base.batteries.cnc - closed.batteries.cnc).coerceIn(0, 100),
            globalMuscularDrain = (base.batteries.muscular - closed.batteries.muscular).coerceIn(0, 100),
            globalSpinalDrain = (baseStructure - structure).coerceIn(0, 100),
            spinalRaw = closed.batteries.spinal,
            tomorrowNeural = tomorrow.batteries.cnc,
            tomorrowSpinal = tomorrow.dashboard.channelScore(RecoveryChannelId.STRUCTURE, tomorrow.batteries.spinal),
            preSessionMuscular = base.batteries.muscular,
            preSessionNeural = base.batteries.cnc,
            preSessionSpinal = baseStructure,
            finishOperationId = finishOperationId,
            completionInstantIso = completionInstantIso,
            inputHash = inputHash,
            automaticImpact = impact,
            involvedVolumeMuscles = impact?.involvedVolumeMuscles.orEmpty(),
        )
    }
}
