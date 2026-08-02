package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine.calculateSetBatteryDrain
import com.example.kpkn.domain.auge.AugeFatigueEngine.getDynamicAugeMetrics
import com.example.kpkn.domain.auge.AugeFatigueEngine.isSetEffective
import com.example.kpkn.domain.auge.AugeUtils.clamp
import com.example.kpkn.domain.auge.AugeUtils.safeExp
import com.example.kpkn.domain.auge.AugeUtils.logDateMs
import com.example.kpkn.domain.auge.AugeUtils.physiologicalFloor
import com.example.kpkn.domain.auge.AugeUtils.decelerateBattery
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

/**
 * AugeRecoveryEngine — Motor de Recuperación AUGE v3.0 para Kotlin.
 * Equivalente a @kpkn/shared-domain recovery.ts + recoveryService.ts
 */
object AugeRecoveryEngine {

    // ─── Perfiles de recuperación (horas base) ────────────────────────────────

    private val RECOVERY_PROFILES = mapOf(
        "fast"   to 24.0,
        "medium" to 48.0,
        "slow"   to 72.0,
        "heavy"  to 96.0,
    )

    private val MUSCLE_PROFILE_MAP = mapOf(
        "Bíceps" to "fast", "Tríceps" to "fast", "Deltoides" to "fast",
        "Deltoides Anterior" to "fast", "Deltoides Lateral" to "fast", "Deltoides Posterior" to "fast",
        "Pantorrillas" to "fast", "Abdomen" to "fast", "Antebrazo" to "fast",
        "Gemelos" to "fast", "Sóleo" to "fast", "Oblicuos" to "fast", "Costales" to "fast",
        "Pectorales" to "medium", "Dorsales" to "medium", "Hombros" to "medium", "Trapecio" to "medium",
        "Romboide" to "medium", "Redondo Menor" to "medium", "Redondo Mayor" to "medium",
        "Infraespinoso" to "medium", "Supraespinoso" to "medium", "Subescapular" to "medium",
        "Serrato Anterior" to "medium",
        "Cuádriceps" to "slow", "Glúteos" to "slow",
        "Glúteo Mayor" to "slow", "Glúteo Medio" to "slow", "Glúteo Menor" to "slow",
        "Aductores" to "medium", "Tensor Fascia Lata" to "medium",
        "Isquiosurales" to "heavy", "Bíceps Femoral" to "heavy", "Semitendinoso" to "heavy",
        "Semimembranoso" to "heavy", "Erectores Espinales" to "heavy", "Core" to "medium",
        "Cuello" to "medium", "Psoas" to "medium", "Lumbar" to "heavy",
    )

    // Los músculos "pilar" para el cálculo de batería muscular global
    private val PILLAR_MUSCLES = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )

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
    ): List<InvolvedMuscle> = exercise.effectiveMuscles?.takeIf { it.isNotEmpty() }
        ?: info?.involvedMuscles.orEmpty()

    private fun normKey(s: String) = s
        .lowercase().trim()
        .replace("á","a").replace("é","e").replace("í","i")
        .replace("ó","o").replace("ú","u").replace("ü","u")

    private fun nowMs() = System.currentTimeMillis()

    private fun recoveryBand(score: Int): RecoveryBand = when {
        score >= 85 -> RecoveryBand.HIGH
        score >= 70 -> RecoveryBand.NORMAL
        score >= 50 -> RecoveryBand.MODERATE
        score >= 35 -> RecoveryBand.LOW
        else -> RecoveryBand.CRITICAL
    }

    private fun confidenceLabel(score: Int): String = when {
        score >= 80 -> "Alta"
        score >= 60 -> "Media"
        else -> "Baja"
    }

    private fun actionForChannel(id: RecoveryChannelId, score: Int): String = when (id) {
        RecoveryChannelId.MUSCULAR -> when {
            score >= 85 -> "Puedes meter volumen alto si la sesión lo pide."
            score >= 70 -> "Volumen normal y buena ejecución."
            score >= 50 -> "Modera series duras en el músculo más cargado."
            score >= 35 -> "Prioriza técnica y recorta volumen local."
            else -> "No fuerces volumen local hoy."
        }
        RecoveryChannelId.SYSTEM -> when {
            score >= 85 -> "Buen día para intensidad y coordinación."
            score >= 70 -> "Empuja normal, sin necesidad de ir al límite."
            score >= 50 -> "Mejor dejar alguna repetición en reserva."
            score >= 35 -> "Evita sets al fallo y compuestos muy demandantes."
            else -> "Haz una sesión ligera o técnica."
        }
        RecoveryChannelId.STRUCTURE -> when {
            score >= 85 -> "Toleras bien carga axial y tensión conectiva."
            score >= 70 -> "Carga estructural normal con buena técnica."
            score >= 50 -> "Conviene moderar impacto axial y variantes agresivas."
            score >= 35 -> "Usa variantes estables o menos compresivas."
            else -> "Evita carga axial o trabajo explosivo hoy."
        }
    }


    private fun parseWellbeingDate(dateStr: String): Long = try {
        val ld = LocalDate.parse(dateStr.take(10))
        ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) { 0L }

    private fun manualBatteryAnchorMs(wellbeing: DailyWellbeingLog?): Long {
        if (wellbeing == null) return 0L
        return wellbeing.manualBatteryAnchorMs
            ?: if (wellbeing.manualNeuralBattery != null
                || wellbeing.manualMuscularBattery != null
                || wellbeing.manualSpinalBattery != null
                || wellbeing.manualMuscleBatteries.isNotEmpty()
            ) nowMs()
            else wellbeing.date.let { parseWellbeingDate(it) }
    }

    private fun muscleMatchesCategory(specificMuscle: String, category: String): Boolean {
        return matchesAugeMuscleTarget(specificMuscle, category)
    }

    /**
     * Promedio ponderado de sueño de los últimos 3 días.
     * Pesos: 50% última noche, 30% anteayer, 20% hace 3 días.
     * Equivalente a recoveryService.ts línea 310-313.
     * Fallback: wellbeing.sleepHours o 7.5 si no hay datos.
     */
    private fun calculateWeightedSleepHours(
        sleepLogs: List<SleepLog>,
        wellbeing: DailyWellbeingLog?,
    ): Double {
        val sorted = sleepLogs
            .sortedByDescending { it.endTime }
            .take(3)
        return when (sorted.size) {
            0    -> wellbeing?.sleepHours ?: 7.5
            1    -> sorted[0].duration
            2    -> sorted[0].duration * 0.6 + sorted[1].duration * 0.4
            else -> sorted[0].duration * 0.5 + sorted[1].duration * 0.3 + sorted[2].duration * 0.2
        }
    }

    private fun systemicRecoveryMultiplier(
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog>,
    ): Double {
        val hours = calculateWeightedSleepHours(sleepLogs, wellbeing)
        val quality = wellbeing?.sleepQuality ?: 3
        val multiplier = when {
            hours >= 7.5 && quality >= 4 -> 0.85
            hours >= 7.0 && quality >= 3 -> 0.92
            hours < 6.0 || quality <= 2 -> 1.18
            hours < 6.5 -> 1.08
            else -> 1.0
        }
        return multiplier.coerceIn(0.85, 1.25)
    }

    private fun resolveDbInfo(
        ex: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val lookupId = (ex.catalogConfigurationId ?: ex.exerciseDbId ?: ex.exerciseId)?.lowercase()
        return lookupId?.let { exerciseDb[it] }
    }

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

    /**
     * Capacidad de trabajo dinámica para un músculo basada en el historial de 4 semanas.
     * Equivalente a calculateUserWorkCapacity() en recoveryService.ts líneas 136-175.
     *
     * La capacidad = promedio semanal de estrés para ese músculo × 1.8 (supercompensación),
     * con suelo en el floor del tipo de atleta y techo en 3500.
     */
    private fun calculateUserWorkCapacity(
        muscleName: String,
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Double {
        val now = nowMs()
        val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
        val recentLogs = history.filter { logDateMs(it) > fourWeeksAgo - 7L * 24 * 3600 * 1000 } // 28-35d fade window
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

            log.completedExercises.forEach { ex ->
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

                val roleMult = FATIGUE_ROLE_MULTIPLIERS[involvement.role] ?: 1.0
                totalStress += setStress * roleMult * decay
            }
        }

        // Promedio semanal × buffer supercompensación (1.8×)
        val weeklyAvg = totalStress / 4.0
        val calculatedCapacity = weeklyAvg * 1.8

        return clamp(max(calculatedCapacity, baseFloor), 500.0, 3500.0)
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
        nowOverride: Long? = null,
    ): MuscleRecoveryStatus {
        val now = nowOverride ?: nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val capacity = precomputedCapacity ?: calculateUserWorkCapacity(muscleName, history, settings, exerciseDb)

        val profileKey = MUSCLE_PROFILE_MAP.entries
            .firstOrNull { normKey(it.key) == normKey(muscleName) }?.value ?: "medium"
        val adaptiveHours = adaptiveCache.personalizedRecoveryHours[muscleName.lowercase().trim()]
            ?: adaptiveCache.personalizedRecoveryHours[normKey(muscleName)]
            ?: adaptiveCache.personalizedRecoveryHours.entries.firstOrNull {
                normKey(it.key) == normKey(muscleName)
            }?.value
        val baseRecoveryTime = clamp(adaptiveHours ?: RECOVERY_PROFILES[profileKey] ?: 48.0, 18.0, 144.0)

        var multiplier = nutritionMultiplier

        val age = settings.userVitals.age ?: 25
        if (age > 35) multiplier *= (1.0 + (age - 35) * 0.01)
        val gender = settings.userVitals.gender
        if (gender == Gender.FEMALE) multiplier *= 0.85

        val feedbackPenaltyPct = calculateMuscleFeedbackPenaltyPct(muscleName, feedbacks)
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

        val k = 2.9957 / max(1.0, realRecoveryTime)
        val tenDaysAgo = now - 10L * 24 * 3600 * 1000
        val muscularCap = (100 - physiologicalFloor(settings).muscular).coerceAtLeast(5).toDouble()

        val manualScore = wellbeing?.manualMuscleBatteries?.let { lookupMuscleScore(it, muscleName) }
        val anchorMs = manualBatteryAnchorMs(wellbeing)
        val hoursSinceAnchor = max(0.0, (now - anchorMs) / 3_600_000.0)
        var accumulatedFatigue = 0.0

        val relevantHistory = if (manualScore != null && anchorMs > 0) {
            val manualBattery = manualScore.coerceIn(0, 100).toDouble()
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val impliedRawFatiguePct = -90.0 * ln(1.0 - penalty / 100.0)
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
            val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
            val decayK = AugeUtils.SESSION_DECAY_K
            var accumulatedDrain = 0.0
            val overallMuscleVolumeMap = mutableMapOf<String, Int>()
            var sessionMuscleStress = 0.0
            var sessionSoftAccum = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val involvedMuscles = involvedMusclesFor(ex, dbInfo)
                val involvement = involvedMuscles.find {
                    muscleMatchesCategory(it.muscle, muscleName)
                }

                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo)
                val densityMult = densityMultiplierForCompletedExercise(ex)
                val primaryMuscle = involvedMuscles
                    .find { it.role == MuscleRole.PRIMARY }
                    ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                    ?: "Core"
                var accumulated = overallMuscleVolumeMap[primaryMuscle] ?: 0
                val muscleMult = lookupMuscleDrainMultiplier(
                    adaptiveCache.muscleDrainMultipliers,
                    primaryMuscle,
                )

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated++
                    
                    val resolvedMetrics = metrics ?: AugeMetrics()
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = resolvedMetrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                        cnsMultiplier = adaptiveCache.cnsDrainMultiplier,
                        spinalMultiplier = adaptiveCache.spinalDrainMultiplier,
                        muscleMultiplier = muscleMult,
                    )
                    
                    val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                    val adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                    val adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                    val adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                    
                    accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
                    
                    if (involvement != null) {
                        val roleMult = FATIGUE_ROLE_MULTIPLIERS[involvement.role] ?: 1.0
                        val activation = involvement.volumeContribution ?: VOLUME_CONTRIBUTION_FALLBACKS[involvement.role] ?: 1.0
                        val contrib = adjustedMuscular * roleMult * activation
                        val capped = AugeUtils.applySessionSoftCap(contrib, sessionSoftAccum, muscularCap)
                        sessionSoftAccum += capped
                        sessionMuscleStress += capped
                        
                        if (hoursSince <= 168 && (involvement.role == MuscleRole.PRIMARY || involvement.role == MuscleRole.SECONDARY)) {
                            effectiveSetsCount++
                        }
                    }
                }
                overallMuscleVolumeMap[primaryMuscle] = accumulated
            }

            if (sessionMuscleStress > 0) {
                accumulatedFatigue += sessionMuscleStress * safeExp(-k * com.example.kpkn.domain.auge.AugeUtils.getSigmoidalHours(hoursSince))
                if (logTime > lastSessionDate) lastSessionDate = logTime
            }
        }

        val rawFatiguePct = (accumulatedFatigue / capacity) * 100.0
        val fatiguePenalty = clamp(100.0 * (1.0 - safeExp(-rawFatiguePct / 90.0)), 0.0, 100.0)
        var battery = clamp(100.0 - fatiguePenalty, 0.0, 100.0)

        if (accumulatedFatigue <= 0.1 && (wellbeing?.doms ?: 1) <= 2) battery = 100.0

        val domsCap = when (wellbeing?.doms ?: 1) {
            5    -> 20.0
            4    -> 50.0
            3    -> 85.0
            else -> 100.0
        }
        battery = min(battery, domsCap)

        val status = when {
            battery >= 95 -> RecoveryStatus.FRESH
            battery >= 85 -> RecoveryStatus.OPTIMAL
            battery >= 40 -> RecoveryStatus.RECOVERING
            else          -> RecoveryStatus.EXHAUSTED
        }

        var hoursToRecovery = 0
        if (battery < 90 && accumulatedFatigue > 0) {
            val targetFatigue = -90.0 * ln(0.9) * capacity / 100.0
            if (accumulatedFatigue > targetFatigue) {
                hoursToRecovery = max(0.0, -ln(targetFatigue / accumulatedFatigue) / k).toInt()
            }
        }

        val floor = physiologicalFloor(settings).muscular.toDouble()
        val myDelta = adaptiveCache.muscleDeltas[muscleName.lowercase().trim()]
            ?: adaptiveCache.muscleDeltas[normKey(muscleName)]
            ?: adaptiveCache.muscleDeltas.entries.firstOrNull { normKey(it.key) == normKey(muscleName) }?.value
            ?: 0.0
        // Pre-workout discomfort is a readiness signal now, not only a τ stretch
        battery = clamp(battery - discomfortPenaltyPct * 0.35, 0.0, 100.0)
        // Apply deltas first, then enforce physiological floor so calibration cannot sink below floor
        battery = max(clamp(battery + myDelta, 0.0, 100.0), floor)

        return MuscleRecoveryStatus(
            muscleName             = muscleName,
            recoveryScore          = battery.toInt(),
            hoursToRecovery        = hoursToRecovery,
            hoursSinceLastSession  = if (lastSessionDate > 0) ((now - lastSessionDate) / 3_600_000).toInt() else -1,
            effectiveSets          = effectiveSetsCount,
            status                 = status,
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
        val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
        val decayK = AugeUtils.SESSION_DECAY_K
        var accumulatedDrain = 0.0
        val overallMuscleVolumeMap = mutableMapOf<String, Int>()
        var sessionMuscleStress = 0.0
        val remappedMultipliers = remapMuscleMultiplierMapToPillars(adaptiveCache.muscleDrainMultipliers)

        log.completedExercises.forEach { ex ->
            val dbInfo = resolveDbInfo(ex, exerciseDb)
            val involvedMuscles = involvedMusclesFor(ex, dbInfo)
            val involvement = involvedMuscles.find {
                muscleMatchesCategory(it.muscle, muscleName)
            }

            val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo)
            val densityMult = densityMultiplierForCompletedExercise(ex)
            val primaryMuscle = involvedMuscles
                .find { it.role == MuscleRole.PRIMARY }
                ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                ?: "Core"
            var accumulated = overallMuscleVolumeMap[primaryMuscle] ?: 0
            val muscleMult = lookupMuscleDrainMultiplier(remappedMultipliers, primaryMuscle)

            ex.sets.forEach { s ->
                if (!isSetEffective(s)) return@forEach
                accumulated++
                val resolvedMetrics = metrics ?: AugeMetrics()
                val drain = calculateSetBatteryDrain(
                    set = s,
                    metrics = resolvedMetrics,
                    tanks = tanks,
                    accumulatedSets = accumulated,
                    restTime = ex.restTime,
                    densityMultiplier = densityMult,
                    cnsMultiplier = adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier = adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier = muscleMult,
                )
                val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                val adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                val adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                val adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
                
                if (involvement != null) {
                    val roleMult = FATIGUE_ROLE_MULTIPLIERS[involvement.role] ?: 1.0
                    val activation = involvement.volumeContribution ?: VOLUME_CONTRIBUTION_FALLBACKS[involvement.role] ?: 1.0
                    sessionMuscleStress += adjustedMuscular * roleMult * activation
                }
            }
            overallMuscleVolumeMap[primaryMuscle] = accumulated
        }
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
    ): Triple<Int, Int, Int> { // Triple(cnsBattery, gymLoad, lifeLoad)
        val now = nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val baseTau = adaptiveCache.cnsRecoveryHours ?: 36.0
        val feedbackPenalty = calculateSystemFeedbackPenaltyPct(feedbacks)
        val sleepMult = systemicRecoveryMultiplier(wellbeing, sleepLogs)
        val cnsCap = (100 - physiologicalFloor(settings).cns).coerceAtLeast(5).toDouble()
        val tauHours = baseTau * (1.0 + feedbackPenalty.coerceAtLeast(0.0) / 48.0) * sleepMult
        val last10Days = now - 10L * 24 * 3600 * 1000

        val manualNeural = wellbeing?.manualNeuralBattery
        val anchorMs = manualBatteryAnchorMs(wellbeing)
        val hoursSinceAnchor = max(0.0, (now - anchorMs) / 3_600_000.0)
        var accumulatedGymLoad = 0.0

        val recentLogs = if (manualNeural != null && anchorMs > 0) {
            val manualBattery = manualNeural.coerceIn(0, 100).toDouble()
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val impliedRawGymPct = -75.0 * ln(1.0 - penalty / 100.0)
            val capacity = max(80.0, tanks.cns * 1.15)
            val manualLoad = (impliedRawGymPct / 100.0) * capacity
            accumulatedGymLoad = manualLoad * exp(-hoursSinceAnchor / tauHours)
            history.filter { logDateMs(it) > anchorMs && logDateMs(it) > last10Days }
        } else {
            history.filter { logDateMs(it) > last10Days }
        }

        recentLogs.forEach { log ->
            val hoursSince = max(0.0, (now - logDateMs(log)) / 3_600_000.0)
            val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
            val decayK = AugeUtils.SESSION_DECAY_K
            var accumulatedDrain = 0.0
            val overallMuscleVolumeMap = mutableMapOf<String, Int>()
            var sessionCns = 0.0
            var sessionSoftAccum = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val involvedMuscles = involvedMusclesFor(ex, dbInfo)
                val primaryMuscle = involvedMuscles
                    .find { it.role == MuscleRole.PRIMARY }
                    ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                    ?: "Core"
                var accumulated = overallMuscleVolumeMap[primaryMuscle] ?: 0
                val densityMult = densityMultiplierForCompletedExercise(ex)
                val muscleMult = lookupMuscleDrainMultiplier(
                    adaptiveCache.muscleDrainMultipliers,
                    primaryMuscle,
                )

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated++
                    val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                        cnsMultiplier = adaptiveCache.cnsDrainMultiplier,
                        spinalMultiplier = adaptiveCache.spinalDrainMultiplier,
                        muscleMultiplier = muscleMult,
                    )
                    
                    val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                    val adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                    val adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                    val adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                    
                    accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
                    
                    var currentCns = adjustedCns
                    if (s.weight > 0.0 && s.reps <= 3 && AugeFatigueEngine.getEffectiveRPE(s) >= 9.5) {
                        currentCns += adjustedCns * 0.15
                    }
                    val capped = AugeUtils.applySessionSoftCap(currentCns, sessionSoftAccum, cnsCap)
                    sessionSoftAccum += capped
                    sessionCns += capped
                }
                overallMuscleVolumeMap[primaryMuscle] = accumulated
            }

            val durationMin = log.durationMinutes
            if (durationMin > 90) sessionCns *= 1.15
            else if (durationMin > 75) sessionCns *= 1.08

            accumulatedGymLoad += sessionCns * safeExp(-(hoursSince / tauHours))
        }

        val capacity = max(80.0, tanks.cns * 1.15)
        val rawGymPct = (accumulatedGymLoad / capacity) * 100.0
        val normalizedGymFatigue = clamp(100.0 * (1.0 - safeExp(-rawGymPct / 75.0)), 0.0, 100.0)

        val total = normalizedGymFatigue
        val cnsBattery = clamp(100.0 - total, 0.0, 100.0).toInt()

        return Triple(cnsBattery, normalizedGymFatigue.toInt(), 0)
    }

    // ─── 3. BATERÍA ESPINAL ───────────────────────────────────────────────────

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
        coreCapacity: Double? = null,
        glutesCapacity: Double? = null,
        latsCapacity: Double? = null,
        nowOverride: Long? = null,
    ): Double {
        val erectors = calculateMuscleBattery("Erectores Espinales", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, erectorsCapacity, nowOverride).recoveryScore.toDouble()
        val core = calculateMuscleBattery("Core", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, coreCapacity, nowOverride).recoveryScore.toDouble()
        val glutes = calculateMuscleBattery("Glúteos", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, glutesCapacity, nowOverride).recoveryScore.toDouble()
        val lats = calculateMuscleBattery("Dorsales", history, wellbeing, settings, exerciseDb, nutritionMultiplier, sleepLogs, feedbacks, adaptiveCache, latsCapacity, nowOverride).recoveryScore.toDouble()

        // Spine Protection Factor (SPF) - Promedio ponderado de rigidez y bracing activo
        val spf = (erectors * 0.50) + (core * 0.25) + (glutes * 0.15) + (lats * 0.10)
        
        if (spf >= 80.0) return 1.0

        // Amplificación exponencial de fatiga espinal debido a Spinal Bracing Failure
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
    ): Int {
        val now = nowMs()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val tauHours = adaptiveCache.spinalRecoveryHours ?: 52.0
        val spinalCap = (100 - physiologicalFloor(settings).spinal).coerceAtLeast(5).toDouble()
        val last10Days = now - 10L * 24 * 3600 * 1000

        val manualSpinal = wellbeing?.manualSpinalBattery
        val anchorMs = manualBatteryAnchorMs(wellbeing)
        val hoursSinceAnchor = max(0.0, (now - anchorMs) / 3_600_000.0)
        var accumulatedSpinalLoad = 0.0

        val recentLogs = if (manualSpinal != null && anchorMs > 0) {
            val manualBattery = manualSpinal.coerceIn(0, 100).toDouble()
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val impliedRawPct = -70.0 * ln(1.0 - penalty / 100.0)
            val capacity = max(70.0, tanks.spinal * 0.02)
            val manualLoad = (impliedRawPct / 100.0) * capacity
            accumulatedSpinalLoad = manualLoad * exp(-com.example.kpkn.domain.auge.AugeUtils.getSpinalRecoveryHours(hoursSinceAnchor) / tauHours)
            history.filter { logDateMs(it) > anchorMs && logDateMs(it) > last10Days }
        } else {
            history.filter { logDateMs(it) > last10Days }
        }

        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel)

        // Optimización O(N^2): Precalcular capacidades musculares de los erectores, core, glúteos y lumbares una sola vez
        val erectorsCapacity = calculateUserWorkCapacity("Erectores Espinales", history, settings, exerciseDb)
        val coreCapacity = calculateUserWorkCapacity("Core", history, settings, exerciseDb)
        val glutesCapacity = calculateUserWorkCapacity("Glúteos", history, settings, exerciseDb)
        val latsCapacity = calculateUserWorkCapacity("Dorsales", history, settings, exerciseDb)

        recentLogs.forEach { log ->
            val logTime = logDateMs(log)
            val hoursSince = max(0.0, (now - logTime) / 3_600_000.0)

            // Corrección de Causalidad: calcular spineProtectionMultiplier usando la historia hasta logTime
            val historyUpToLog = history.filter { logDateMs(it) <= logTime }
            val feedbacksUpToLog = feedbacks.filter { fb ->
                try {
                    val fbDateMs = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(fb.date)?.time ?: 0L
                    fbDateMs <= logTime
                } catch (_: Exception) { false }
            }

            val spineProtectionMultiplier = calculateSpineFatigueMultiplier(
                history = historyUpToLog,
                wellbeing = wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                sleepLogs = sleepLogs,
                nutritionMultiplier = nutritionMultiplier,
                feedbacks = feedbacksUpToLog,
                adaptiveCache = adaptiveCache,
                erectorsCapacity = erectorsCapacity,
                coreCapacity = coreCapacity,
                glutesCapacity = glutesCapacity,
                latsCapacity = latsCapacity,
                nowOverride = logTime,
            )

            val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
            val decayK = AugeUtils.SESSION_DECAY_K
            var accumulatedDrain = 0.0
            val overallMuscleVolumeMap = mutableMapOf<String, Int>()
            var sessionSpinalLoad = 0.0
            var sessionSoftAccum = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val involvedMuscles = involvedMusclesFor(ex, dbInfo)
                val primaryMuscle = involvedMuscles
                    .find { it.role == MuscleRole.PRIMARY }
                    ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                    ?: "Core"
                var accumulated = overallMuscleVolumeMap[primaryMuscle] ?: 0
                val densityMult = densityMultiplierForCompletedExercise(ex)
                val muscleMult = lookupMuscleDrainMultiplier(
                    adaptiveCache.muscleDrainMultipliers,
                    primaryMuscle,
                )

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated += 1
                    val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                        cnsMultiplier = adaptiveCache.cnsDrainMultiplier,
                        spinalMultiplier = adaptiveCache.spinalDrainMultiplier,
                        muscleMultiplier = muscleMult,
                    )
                    
                    val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                    val adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                    val adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                    val adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                    
                    accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
                    
                    val contrib = adjustedSpinal * spineProtectionMultiplier
                    val capped = AugeUtils.applySessionSoftCap(contrib, sessionSoftAccum, spinalCap)
                    sessionSoftAccum += capped
                    sessionSpinalLoad += capped
                }
                overallMuscleVolumeMap[primaryMuscle] = accumulated
            }

            if (log.durationMinutes > 90) sessionSpinalLoad *= 1.08
            accumulatedSpinalLoad += sessionSpinalLoad * safeExp(-(com.example.kpkn.domain.auge.AugeUtils.getSpinalRecoveryHours(hoursSince) / tauHours))
        }

        val capacity = max(70.0, tanks.spinal * 0.02)
        val rawPct = (accumulatedSpinalLoad / capacity) * 100.0
        val normalizedLoad = clamp(100.0 * (1.0 - safeExp(-rawPct / 70.0)), 0.0, 100.0)
        return clamp(100.0 - normalizedLoad, 0.0, 100.0).toInt()
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
    ): GlobalBatteries {
        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel)

        val gatedPillarBatteries = PILLAR_MUSCLES.mapNotNull { muscleName ->
            val muscleScore = if (precomputedMuscles != null) {
                precomputedMuscles[muscleName]?.recoveryScore
            } else {
                calculateMuscleBattery(
                    muscleName = muscleName,
                    history = history,
                    wellbeing = wellbeing,
                    settings = settings,
                    exerciseDb = exerciseDb,
                    nutritionMultiplier = nutritionMultiplier,
                    sleepLogs = sleepLogs,
                    feedbacks = feedbacks,
                    adaptiveCache = adaptiveCache,
                ).recoveryScore
            } ?: return@mapNotNull null

            val relatedArtic = AugeTtcEngine.articularBatteriesFor(muscleName)
            if (relatedArtic.isEmpty()) return@mapNotNull muscleScore

            val articularAvg = relatedArtic
                .mapNotNull { articularBatteries[it]?.recoveryScore }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: return@mapNotNull muscleScore

            // BLEND SUAVE: muscular * (0.6 + 0.4 * articular/100)
            val coef = 0.6 + 0.4 * (articularAvg / 100.0)
            (muscleScore * coef).roundToInt().coerceIn(0, 100)
        }

        val muscularAvg = if (wellbeing?.manualMuscularBattery != null) {
            // Same anchor + temporal decay semantics as neural/spinal — do not freeze raw value all day
            val manualBattery = wellbeing.manualMuscularBattery.coerceIn(0, 100).toDouble()
            val anchorMs = manualBatteryAnchorMs(wellbeing)
            val hoursSinceAnchor = max(0.0, (nowMs() - anchorMs) / 3_600_000.0)
            val baseTau = adaptiveCache.personalizedRecoveryHours.values
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: 48.0
            val sleepMult = systemicRecoveryMultiplier(wellbeing, sleepLogs)
            val tauHours = max(1.0, baseTau * sleepMult)
            val penalty = (100.0 - manualBattery).coerceIn(0.0, 99.9)
            val k = 2.9957 / tauHours
            val recovered = 100.0 - penalty * exp(-k * AugeUtils.getSigmoidalHours(hoursSinceAnchor))
            recovered.roundToInt().coerceIn(0, 100)
        } else if (gatedPillarBatteries.isEmpty()) {
            100
        } else {
            val overallAvg = gatedPillarBatteries.average()
            val sortedAsc = gatedPillarBatteries.sorted()
            val bottomCount = maxOf(1, (sortedAsc.size * 0.25).toInt())
            val bottomQuartileAvg = sortedAsc.take(bottomCount).average()
            (overallAvg * 0.85 + bottomQuartileAvg * 0.15).toInt()
        }

        val (cncBattery, _, _) = calculateSystemicFatigue(
            history = history,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
        )
        val spinalBattery = calculateSpinalBattery(
            history = history,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
        )

        val avgMuscleDelta = if (adaptiveCache.muscleDeltas.isNotEmpty()) {
            adaptiveCache.muscleDeltas.values.average().coerceIn(-25.0, 25.0)
        } else 0.0
        val cnsDelta = adaptiveCache.cnsLearningDelta.coerceIn(-15.0, 15.0)
        val spinalDelta = adaptiveCache.spinalLearningDelta.coerceIn(-15.0, 15.0)
        val floor = physiologicalFloor(settings)

        val decelMuscular = decelerateBattery(muscularAvg.toDouble())
        val finalMuscular = max(
            clamp(decelMuscular + avgMuscleDelta, 0.0, 100.0),
            floor.muscular.toDouble(),
        ).toInt()
        val finalCnc = max(
            decelerateBattery(
                clamp(cncBattery.toDouble() + cnsDelta, 0.0, 100.0),
            ),
            floor.cns.toDouble(),
        ).toInt().coerceIn(0, 100)
        val finalSpinal = max(
            decelerateBattery(
                clamp(spinalBattery.toDouble() + spinalDelta, 0.0, 100.0),
            ),
            floor.spinal.toDouble(),
        ).toInt().coerceIn(0, 100)

        return GlobalBatteries(
            muscular = finalMuscular,
            cnc      = finalCnc,
            spinal   = finalSpinal,
        )
    }

    // ─── 5. PER-MUSCLE BATTERIES (todos los pilares) ──────────────────────────

    fun getPerMuscleBatteries(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        sleepLogs: List<SleepLog> = emptyList(),
        nutritionLogs: List<NutritionLog> = emptyList(),
        feedbacks: List<PostSessionFeedback> = emptyList(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): Map<String, MuscleRecoveryStatus> {
        val stressLevel = wellbeing?.stressLevel ?: 3
        val nutritionMultiplier = getNutritionMultiplier(settings, nutritionLogs, stressLevel)

        // Optimización O(N): Truncar historia a los últimos 30 días (fatiga anterior a 30 días es < 0.001%)
        val thirtyDaysAgo = nowMs() - 30L * 24 * 3600_000L
        val recentHistory = history.filter { logDateMs(it) >= thirtyDaysAgo }

        // Optimización O(N^2): Precalcular capacidades de los músculos pilares antes de iterar
        val precomputedCapacities = PILLAR_MUSCLES.associateWith { muscle ->
            calculateUserWorkCapacity(muscle, recentHistory, settings, exerciseDb)
        }

        return PILLAR_MUSCLES.associateWith { muscle ->
            calculateMuscleBattery(
                muscleName = muscle,
                history = recentHistory,
                wellbeing = wellbeing,
                settings = settings,
                exerciseDb = exerciseDb,
                nutritionMultiplier = nutritionMultiplier,
                sleepLogs = sleepLogs,
                feedbacks = feedbacks,
                adaptiveCache = adaptiveCache,
                precomputedCapacity = precomputedCapacities[muscle],
            )
        }
    }

    // ─── 6. DASHBOARD Y READINESS DIARIA ─────────────────────────────────────

    fun calculateRecoveryDashboard(
        batteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState>,
        wellbeing: DailyWellbeingLog?,
        sleepLogs: List<SleepLog> = emptyList(),
        recentSessionCount: Int = 0,
    ): RecoveryDashboard {
        val weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        val lowestMuscles = perMuscle.values.sortedBy { it.recoveryScore }.take(2)
        val weakestArticular = articularBatteries.entries.sortedBy { it.value.recoveryScore }.take(2)
        val articularFloor = weakestArticular
            .takeIf { it.isNotEmpty() }
            ?.map { it.value.recoveryScore.toDouble() }
            ?.average()
            ?.toInt()
            ?: 100
        val structureScore = min(
            batteries.spinal,
            ((batteries.spinal * 0.6) + (articularFloor * 0.4)).toInt(),
        ).coerceIn(0, 100)
        val muscularScore = batteries.muscular
        val systemScore = batteries.cnc
        val displayStructureScore = structureScore

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
            if (lowestMuscles.isNotEmpty() && lowestMuscles.first().recoveryScore < 80) {
                add(lowestMuscles.joinToString(" y ") { "${it.muscleName} ${it.recoveryScore}%" })
            } else {
                add("Promedio de grupos pilar estable")
            }
            if (wellbeing?.manualMuscularBattery != null) add("Ajuste manual de readiness")
            if ((wellbeing?.doms ?: 1) >= 4) add("Agujetas altas hoy")
            if (weightedSleep < 6.5) add("Sueño reciente por debajo de lo ideal")
        }
        val systemCauses = buildList {
            when {
                weightedSleep < 5.5 -> add("Poco sueño en las últimas noches")
                weightedSleep < 6.5 -> add("Sueño subóptimo reciente")
                weightedSleep >= 8.5 -> add("Buen colchón de sueño")
            }
            if (wellbeing?.manualNeuralBattery != null) add("Ajuste manual de readiness")
            if ((wellbeing?.stressLevel ?: 3) >= 4) add("Estrés alto fuera del entrenamiento")
            if (systemScore < 70) add("Carga neural reciente acumulada")
        }
        val structureCauses = buildList {
            if (displayStructureScore < 75) add("Carga axial reciente elevada")
            if (weakestArticular.isNotEmpty()) {
                add(weakestArticular.joinToString(" y ") { "${AugeTtcEngine.articularLabel(it.key)} ${it.value.recoveryScore}%" })
            } else {
                add("Sin cuello de botella estructural claro")
            }
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
                description = "Promedio del estado de todos tus músculos hoy.",
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
                description = "Qué tanta intensidad, coordinación y producción de fuerza toleras hoy.",
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
                description = "Cómo llega hoy tu columna, tus tendones y tus articulaciones a la carga.",
                action = actionForChannel(RecoveryChannelId.STRUCTURE, displayStructureScore),
                causes = structureCauses.take(3),
                confidence = structureConfidence,
                editable = true,
            ),
        )

        val overallScore = (
            channels.first { it.id == RecoveryChannelId.SYSTEM }.score * 0.40 +
                channels.first { it.id == RecoveryChannelId.MUSCULAR }.score * 0.35 +
                channels.first { it.id == RecoveryChannelId.STRUCTURE }.score * 0.25
            ).toInt().coerceIn(0, 100)
        val headline = when (recoveryBand(overallScore)) {
            RecoveryBand.HIGH -> "Listo para empujar"
            RecoveryBand.NORMAL -> "Buen estado para entrenar"
            RecoveryBand.MODERATE -> "Día para moderar"
            RecoveryBand.LOW -> "Llega cargado"
            RecoveryBand.CRITICAL -> "Prioriza recuperación"
        }
        val recommendation = when (recoveryBand(overallScore)) {
            RecoveryBand.HIGH -> "Hoy puedes entrenar normal o fuerte si la sesión lo pide."
            RecoveryBand.NORMAL -> "Hoy conviene entrenar normal, dejando algo en reserva."
            RecoveryBand.MODERATE -> "Hoy conviene moderar volumen o intensidad según el ring más bajo."
            RecoveryBand.LOW -> "Hoy conviene priorizar técnica, variantes estables y menos carga."
            RecoveryBand.CRITICAL -> "Hoy conviene descargar o hacer solo trabajo liviano."
        }
        val confidenceAverage = channels.map { it.confidence }.average().toInt()
        val limitingChannel = channels.minByOrNull { it.score }
        val summary = limitingChannel?.let {
            "${it.title} es hoy el factor limitante (${it.score}%)."
        } ?: "Tu estado está equilibrado."

        return RecoveryDashboard(
            overallScore = overallScore,
            headline = headline,
            summary = summary,
            recommendation = recommendation,
            confidenceLabel = confidenceLabel(confidenceAverage),
            channels = channels,
        )
    }

    fun calculateDailyReadiness(
        dashboard: RecoveryDashboard,
        wellbeing: DailyWellbeingLog?,
    ): AugeReadinessVerdict {
        val score = dashboard.overallScore.coerceIn(0, 100)
        val (label, color) = when (recoveryBand(score)) {
            RecoveryBand.HIGH -> "Óptimo para entrenar" to ReadinessColor.GREEN
            RecoveryBand.NORMAL -> "Buen estado" to ReadinessColor.GREEN
            RecoveryBand.MODERATE -> "Moderado" to ReadinessColor.YELLOW
            RecoveryBand.LOW -> "Cargado" to ReadinessColor.YELLOW
            RecoveryBand.CRITICAL -> "Descanso recomendado" to ReadinessColor.RED
        }
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

    // ─── 7. ENCUESTAS PENDIENTES ─────────────────────────────────────────────

    fun checkPendingSurveys(
        history: List<WorkoutLog>,
        feedbacks: List<PostSessionFeedback>,
    ): PendingQuestionnaire? {
        val now = nowMs()
        val twoHours  = 2L  * 3600 * 1000
        val fortyEightHours = 48L * 3600 * 1000

        return history.firstOrNull { log ->
            val timeSince = now - logDateMs(log)
            timeSince > twoHours &&
            timeSince < fortyEightHours &&
            feedbacks.none { it.logId == log.id }
        }?.let { log ->
            PendingQuestionnaire(
                logId         = log.id,
                sessionName   = log.sessionName,
                muscleGroups  = emptyList(), // Se llena en la UI con los músculos del log
                stillPresentDiscomfortIds = log.stillPresentDiscomfortIds,
                scheduledTimeMs = logDateMs(log) + 24 * 3600 * 1000,
            )
        }
    }

    // ─── 8. RECOMENDACIÓN DE SUEÑO ───────────────────────────────────────────

    fun calculateSleepRecommendations(
        settings: Settings,
        wellbeing: DailyWellbeingLog?,
        todayLog: WorkoutLog?,
    ): SleepRecommendation {
        val base = settings.sleepTargetHours
        var extra = 0.0
        val reasons = mutableListOf<String>()

        todayLog?.let { log ->
            val volume = log.completedExercises.size * 3
            if (volume > 15 || (log.sessionStressScore ?: 0.0) > 50) {
                extra += 0.75; reasons.add("Alta carga neural")
            } else if (volume > 10) {
                extra += 0.5; reasons.add("Volumen moderado")
            }
        }

        wellbeing?.let { w ->
            if (w.workIntensity == IntensityLevel.HIGH || w.studyIntensity == IntensityLevel.HIGH) {
                extra += 0.5; reasons.add("Carga cognitiva alta")
            }
            if (w.stressLevel >= 4) {
                extra += 0.25; reasons.add("Estrés elevado")
            }
        }

        return SleepRecommendation(
            targetHours = (base + extra).coerceIn(6.0, 10.0),
            reasons = reasons,
        )
    }

    // ─── 9. MULTIPLICADOR NUTRICIONAL ────────────────────────────────────────

    fun getNutritionMultiplier(
        settings: Settings,
        nutritionLogs: List<NutritionLog> = emptyList(),
        stressLevel: Int = 3,
    ): Double {
        if (!settings.algorithmSettings.augeEnableNutritionTracking) return 1.0
        val activePlan = runCatching { NutritionRepository.getInstance().activeNutritionPlan }.getOrNull()
        return NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = nutritionLogs,
            settings = settings,
            activePlan = activePlan,
            stressLevel = stressLevel,
        ).recoveryTimeMultiplier
    }

    // Expose PILLAR_MUSCLES para la UI
    val pillarMuscles: List<String> get() = PILLAR_MUSCLES

    // ─── 10. RANKINGS DE SESIONES ─────────────────────────────────────────────

    /**
     * Calcula el ranking de sesiones del historial ordenadas por drenaje total.
     * Incluye desglose por canal (muscular, cns, espinal).
     */
    fun calculateSessionDrainRankings(
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): List<SessionDrainRanking> {
        return history.map { log ->
            val drain = AugeFatigueEngine.calculateCompletedSessionDrain(
                completedExercises = log.completedExercises,
                exerciseDb = exerciseDb,
                settings = settings,
            )
            SessionDrainRanking(
                logId = log.id,
                sessionName = log.sessionName,
                date = log.date.take(10),
                totalDrain = drain.cns * AugeUtils.STRESS_WEIGHT_CNS +
                    drain.muscular * AugeUtils.STRESS_WEIGHT_MUSCULAR +
                    drain.spinal * AugeUtils.STRESS_WEIGHT_SPINAL,
                cnsDrain = drain.cns.toDouble(),
                muscularDrain = drain.muscular.toDouble(),
                spinalDrain = drain.spinal.toDouble(),
            )
        }.sortedByDescending { it.totalDrain }
    }

    /**
     * Calcula el ranking de ejercicios agregado por drenaje promedio.
     * Agrupa por ejerciseName/exerciseDbId para promediar a través del historial.
     */
    fun calculateExerciseDrainRankings(
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): List<ExerciseDrainRanking> {
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        // Map: exerciseKey -> (totalMuscular, totalCns, totalSpinal, sessionCount)
        data class DrainAccum(
            var muscular: Double = 0.0,
            var cns: Double = 0.0,
            var spinal: Double = 0.0,
            var count: Int = 0,
            var name: String = "",
            var dbId: String? = null,
        )
        val accumMap = mutableMapOf<String, DrainAccum>()

        history.forEach { log ->
            log.completedExercises.forEach { ex ->
                val key = (ex.exerciseDbId ?: ex.exerciseName).lowercase().trim()
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
                val densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0
                var sessMuscular = 0.0
                var sessCns = 0.0
                var sessSpinal = 0.0

                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated++
                    val drain = calculateSetBatteryDrain(
                        set = s,
                        metrics = metrics,
                        tanks = tanks,
                        accumulatedSets = accumulated,
                        restTime = ex.restTime,
                        densityMultiplier = densityMult,
                    )
                    sessMuscular += drain.muscularDrainPct
                    sessCns += drain.cnsDrainPct
                    sessSpinal += drain.spinalDrainPct
                }

                val entry = accumMap.getOrPut(key) {
                    DrainAccum(name = ex.exerciseName, dbId = ex.exerciseDbId)
                }
                entry.muscular += sessMuscular
                entry.cns += sessCns
                entry.spinal += sessSpinal
                entry.count++
            }
        }

        return accumMap.values.map { a ->
            val n = maxOf(1, a.count).toDouble()
            val normFactor = 0.01
            ExerciseDrainRanking(
                exerciseName = a.name,
                exerciseDbId = a.dbId,
                overallDrain = ((a.muscular / n * AugeUtils.STRESS_WEIGHT_MUSCULAR) +
                    (a.cns / n * AugeUtils.STRESS_WEIGHT_CNS) +
                    (a.spinal / n * AugeUtils.STRESS_WEIGHT_SPINAL)) * normFactor,
                muscularDrain = (a.muscular / n) * normFactor,
                cnsDrain = (a.cns / n) * normFactor,
                spinalDrain = (a.spinal / n) * normFactor,
                sessionCount = a.count,
            )
        }.sortedByDescending { it.overallDrain }
    }

    /**
     * Calcula estadísticas personales de recuperación del último mes.
     * Compara el tiempo necesario para cada canal usando el historial de WorkoutLogs.
     */
    fun calculatePersonalRecoveryStats(
        history: List<WorkoutLog>,
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): PersonalRecoveryStats {
        val cutoffMs = System.currentTimeMillis() - 30L * 24 * 3600_000L
        val recent = history.filter { logDateMs(it) >= cutoffMs }

        if (recent.isEmpty()) {
            return PersonalRecoveryStats(
                avgRecoveryHoursOverall = 48.0,
                avgRecoveryHoursMuscular = 48.0,
                avgRecoveryHoursCns = 36.0,
                avgRecoveryHoursSpinal = 52.0,
                fastestRecoverySession = null,
                slowestRecoverySession = null,
                sampleCount = 0,
            )
        }

        // Estimate recovery hours per session based on drain intensity
        data class SessionRecovery(val name: String, val muscular: Double, val cns: Double, val spinal: Double)

        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val sessionStats = recent.map { log ->
            var totalMuscular = 0.0
            var totalCns = 0.0
            var totalSpinal = 0.0

            log.completedExercises.forEach { ex ->
                val dbInfo = resolveDbInfo(ex, exerciseDb)
                val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
                val densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0
                ex.sets.forEach { s ->
                    if (!isSetEffective(s)) return@forEach
                    accumulated++
                    val drain = calculateSetBatteryDrain(
                        set = s, metrics = metrics, tanks = tanks,
                        accumulatedSets = accumulated, restTime = ex.restTime, densityMultiplier = densityMult,
                    )
                    totalMuscular += drain.muscularDrainPct
                    totalCns += drain.cnsDrainPct
                    totalSpinal += drain.spinalDrainPct
                }
            }

            // Estimate recovery hours: higher drain → more hours needed
            // Muscular: base 24-96h, CNC: base 12-48h, Spinal: base 24-72h
            val muscularHours = clamp(24.0 + totalMuscular * 0.5, 24.0, 96.0)
            val cnsHours = clamp(12.0 + totalCns * 0.3, 12.0, 48.0)
            val spinalHours = clamp(24.0 + totalSpinal * 0.4, 24.0, 72.0)
            SessionRecovery(log.sessionName, muscularHours, cnsHours, spinalHours)
        }

        val avgMuscular = sessionStats.map { it.muscular }.average()
        val avgCns = sessionStats.map { it.cns }.average()
        val avgSpinal = sessionStats.map { it.spinal }.average()
        val avgOverall = (avgMuscular * AugeUtils.STRESS_WEIGHT_MUSCULAR +
            avgCns * AugeUtils.STRESS_WEIGHT_CNS +
            avgSpinal * AugeUtils.STRESS_WEIGHT_SPINAL)

        val fastest = sessionStats.minByOrNull { it.muscular + it.cns + it.spinal }?.name
        val slowest = sessionStats.maxByOrNull { it.muscular + it.cns + it.spinal }?.name

        return PersonalRecoveryStats(
            avgRecoveryHoursOverall = avgOverall,
            avgRecoveryHoursMuscular = avgMuscular,
            avgRecoveryHoursCns = avgCns,
            avgRecoveryHoursSpinal = avgSpinal,
            fastestRecoverySession = fastest,
            slowestRecoverySession = slowest,
            sampleCount = recent.size,
        )
    }

    // ─── Post-Session Preview (finish-sheet ↔ home-screen consistency) ─────────

    /**
     * Calcula el estado de baterías **después** de agregar un log transitorio
     * al historial existente. Usa exactamente las mismas fórmulas que el home.
     */
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
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
    ): PostSessionPreview {
        val historyWithPreview = baseHistory + previewLog
        val muscles = getPerMuscleBatteries(
            history = historyWithPreview,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
        )
        val batteries = calculateGlobalBatteries(
            history = historyWithPreview,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
            precomputedMuscles = muscles,
            articularBatteries = articularBatteries,
        )
        // Calcular baterías base (sin la sesión preview) para derivar drains netos
        val baseMuscles = getPerMuscleBatteries(
            history = baseHistory,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
        )
        val baseBatteries = calculateGlobalBatteries(
            history = baseHistory,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            sleepLogs = sleepLogs,
            nutritionLogs = nutritionLogs,
            feedbacks = feedbacks,
            adaptiveCache = adaptiveCache,
            precomputedMuscles = baseMuscles,
            articularBatteries = articularBatteries,
        )
        return PostSessionPreview(
            neural = batteries.cnc,
            spinal = batteries.spinal,
            muscular = batteries.muscular,
            perMuscle = muscles,
            globalCnsDrain = (baseBatteries.cnc - batteries.cnc).coerceIn(0, 100),
            globalMuscularDrain = (baseBatteries.muscular - batteries.muscular).coerceIn(0, 100),
            globalSpinalDrain = (baseBatteries.spinal - batteries.spinal).coerceIn(0, 100),
        )
    }
}
