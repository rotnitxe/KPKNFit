package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.domain.auge.AugeUtils.physiologicalFloor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
/**
 * AugeFatigueEngine — Motor de Fatiga AUGE v3.0 para Kotlin.
 * Funciones puras, sin estado. Equivalente a @kpkn/shared-domain fatigue.ts
 */
object AugeFatigueEngine {

    private const val MAX_TECHNIQUE_FATIGUE_MULTIPLIER = 4.0

    /** Canonical fallback when predicted-drain calc fails but the session has work. */
    val DEFAULT_FALLBACK_PREDICTED_DRAIN: PredictedDrain = AugeUtils.DEFAULT_FALLBACK_PREDICTED_DRAIN

    const val STRESS_WEIGHT_CNS = AugeUtils.STRESS_WEIGHT_CNS
    const val STRESS_WEIGHT_MUSCULAR = AugeUtils.STRESS_WEIGHT_MUSCULAR
    const val STRESS_WEIGHT_SPINAL = AugeUtils.STRESS_WEIGHT_SPINAL

    // ─── Capacidades base por tipo de atleta ─────────────────────────────────

    private val ATHLETE_CAPACITY: Map<AthleteType, Double> = mapOf(
        AthleteType.ENTHUSIAST    to 500.0,
        AthleteType.HYBRID        to 650.0,
        AthleteType.CALISTHENICS  to 600.0,
        AthleteType.BODYBUILDER   to 1000.0,
        AthleteType.POWERBUILDER  to 1100.0,
        AthleteType.POWERLIFTER   to 1200.0,
        AthleteType.WEIGHTLIFTER  to 1000.0,
    )

    fun getAthleteCapacity(settings: Settings): Double =
        ATHLETE_CAPACITY[settings.athleteType] ?: 500.0


    private fun applySoftCap(drain: Double, accumulated: Double, cap: Double): Double =
        AugeUtils.applySessionSoftCap(drain, accumulated, cap)

    private fun normalizeBias(profile: PredictionBiasProfile): Triple<Double, Double, Double> {
        val confidence = (profile.sampleCount.coerceIn(0, 30) / 30.0)
        return Triple(
            (profile.cnsBias * confidence).coerceIn(-15.0, 15.0),
            (profile.muscularBias * confidence).coerceIn(-15.0, 15.0),
            (profile.spinalBias * confidence).coerceIn(-15.0, 15.0),
        )
    }

    // ─── Tanques de batería personalizados ───────────────────────────────────

    fun calculatePersonalizedBatteryTanks(settings: Settings): BatteryTanks {
        // Base tanks alineados con fatigue.ts PWA (baseMuscular=300, baseCns=250, baseSpinal=4000)
        val baseMuscular = 300.0; val baseCns = 250.0; val baseSpinal = 4000.0
        val levelMult = when (settings.athleteType) {
            AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> 1.2
            AthleteType.BODYBUILDER, AthleteType.POWERBUILDER -> 1.1
            AthleteType.HYBRID, AthleteType.CALISTHENICS, AthleteType.ZERCHER_LIFTER -> 1.0
            AthleteType.ENTHUSIAST -> 0.9
        }
        val cnsMult: Double; val muscMult: Double; val spineMult: Double
        when (settings.athleteType) {
            AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> { cnsMult = 1.3; spineMult = 1.4; muscMult = 0.9 }
            AthleteType.BODYBUILDER, AthleteType.POWERBUILDER  -> { cnsMult = 0.9; spineMult = 0.9; muscMult = 1.3 }
            else -> { cnsMult = 1.15; spineMult = 1.15; muscMult = 1.15 }
        }
        return BatteryTanks(
            cns      = baseCns      * levelMult * cnsMult,
            muscular = baseMuscular * levelMult * muscMult,
            spinal   = baseSpinal   * levelMult * spineMult,
        )
    }

    // ─── Métricas dinámicas AUGE por nombre de ejercicio ─────────────────────

    private fun deriveAugeMetricsFromDb(dbInfo: ExerciseMuscleInfo?): AugeMetrics? {
        if (dbInfo == null) return null
        val efc = dbInfo.efc ?: return null
        val cnc = dbInfo.cnc ?: return null
        val ssc = dbInfo.ssc ?: return null
        return AugeMetrics(
            efc = efc.coerceIn(1.0, 5.0),
            cnc = cnc.coerceIn(1.0, 5.0),
            ssc = ssc.coerceIn(0.0, 2.0),
        )
    }

    fun getDynamicAugeMetrics(
        exerciseName: String,
        equipment: String? = null,
        dbInfo: ExerciseMuscleInfo? = null,
    ): AugeMetrics? = deriveAugeMetricsFromDb(dbInfo)

    // ─── RPE efectivo (traduce RPE / RIR / failure) ──────────────────────────

    fun getEffectiveRPE(set: CompletedSet): Double {
        var baseRpe = 7.0

        baseRpe = when {
            set.actualIntensityMode == IntensityMode.FAILURE -> 10.8
            set.actualIntensityMode == IntensityMode.RPE && set.actualIntensityValue != null -> set.actualIntensityValue
            set.rir != null         -> (10 - set.rir).toDouble()
            set.actualIntensityMode == IntensityMode.RIR && set.actualIntensityValue != null -> 10.0 - set.actualIntensityValue
            set.rpe != null         -> set.rpe
            else                    -> 7.0
        }

        if (set.isFailure) baseRpe = max(baseRpe, 11.2)
        if (set.isFailedSet) baseRpe = max(baseRpe, 10.2)

        var techniqueBonus = 0.0
        if (set.dropSets.isNotEmpty()) techniqueBonus += set.dropSets.size * 1.5
        if (set.restPauses.isNotEmpty()) techniqueBonus += set.restPauses.size * 1.0
        if (set.isPartial && (set.partialReps ?: 0) > 0) techniqueBonus += 0.5
        if (techniqueBonus > 0 && baseRpe < 10.0) baseRpe = 10.0

        return baseRpe.coerceIn(1.0, 12.0)
    }

    // ─── ¿El set cuenta para fatiga? ─────────────────────────────────────────

    fun isSetEffective(set: CompletedSet): Boolean {
        if (set.skipped) return false
        if (set.isWarmup) return false
        val hasTime = (set.timeSeconds ?: 0) > 0
        if (set.reps <= 0 && !hasTime && set.weight <= 0.0) return false
        val rpe = getEffectiveRPE(set)
        return rpe >= 6.0
    }

    fun calculateRpeMultiplier(rpe: Double): Double {
        val bounded = rpe.coerceIn(1.0, 12.0)
        val cappedLinear = min(bounded, 10.0)
        val base = 1.0 + (cappedLinear / 10.0).pow(4.2)
        if (bounded <= 10.0) return base

        val overshoot = bounded - 10.0
        val failureBonus = (exp(overshoot * 0.5) - 1.0) * 0.3
        return (base + failureBonus).coerceAtMost(2.3)
    }

    fun getDensityMultiplierForExercise(
        supersetId: String?,
        restTime: Int,
        supersetExerciseCount: Int = 1,
        supersetRounds: Int? = null,
        supersetRestAfter: Int? = null,
    ): Double {
        if (supersetId.isNullOrBlank()) return 1.0
        val intraRestFactor = when {
            restTime <= 45 -> 1.30
            restTime <= 75 -> 1.24
            else -> 1.18
        }
        val exerciseCountFactor = 1.0 + ((supersetExerciseCount.coerceAtLeast(2) - 2) * 0.045)
        val roundFactor = supersetRounds
            ?.takeIf { it > 0 }
            ?.let { 1.0 + ((it - 1).coerceAtMost(5) * 0.018) }
            ?: 1.0
        val postRestFactor = when {
            supersetRestAfter == null -> 1.0
            supersetRestAfter <= 60 -> 1.08
            supersetRestAfter <= 120 -> 1.03
            supersetRestAfter >= 240 -> 0.95
            else -> 1.0
        }
        return (intraRestFactor * exerciseCountFactor * roundFactor * postRestFactor)
            .coerceIn(1.10, 1.45)
    }

    fun calculateMuscularProgressiveMultiplier(accumulatedSets: Int): Double {
        val sets = accumulatedSets.coerceAtLeast(1)
        return when {
            sets <= 3 -> 1.0 + (sets - 1) * 0.05
            sets <= 6 -> 1.10 - (sets - 3) * 0.10
            else -> max(0.3, 0.80 * exp(-(sets - 6) * 0.25))
        }
    }

    fun calculateSystemicProgressiveMultiplier(accumulatedSets: Int): Double {
        val progression = (accumulatedSets.coerceAtLeast(1) - 1).toDouble()
        return (1.0 + 0.055 * progression.pow(1.55)).coerceIn(1.0, 2.6)
    }

    fun calculateMuscularTechniqueMultiplier(set: CompletedSet): Double {
        val debtPenalty = if (set.debt > 0.0) 1.0 + min(0.15, set.debt * 0.03) else 1.0
        val failurePenalty = when {
            set.isFailure && set.isFailedSet -> 1.15
            set.isFailure -> 1.10
            set.isFailedSet -> 1.05
            else -> 1.0
        }
        val dropPenalty = if (set.dropSets.isEmpty()) 1.0 else (1.0 + 0.10 * set.dropSets.size).coerceAtMost(1.20)
        val restPausePenalty = if (set.restPauses.isEmpty()) 1.0 else (1.0 + 0.08 * set.restPauses.size).coerceAtMost(1.15)
        val partialPenalty = if ((set.partialReps ?: 0) > 0) 1.03 else 1.0
        return debtPenalty * failurePenalty * dropPenalty * restPausePenalty * partialPenalty
    }

    fun calculateSystemicTechniqueMultiplier(set: CompletedSet): Double {
        val debtPenalty = if (set.debt > 0.0) 1.0 + min(0.35, set.debt * 0.06) else 1.0
        val failurePenalty = when {
            set.isFailure && set.isFailedSet -> 1.28
            set.isFailure -> 1.22
            set.isFailedSet -> 1.12
            else -> 1.0
        }
        val dropPenalty = if (set.dropSets.isEmpty()) {
            1.0
        } else {
            1.0 + 0.20 * (2.0.pow(set.dropSets.size.toDouble()) - 1.0)
        }
        val restPausePenalty = if (set.restPauses.isEmpty()) {
            1.0
        } else {
            1.0 + 0.22 * (2.0.pow(set.restPauses.size.toDouble()) - 1.0)
        }
        val partialPenalty = if ((set.partialReps ?: 0) > 0) 1.05 else 1.0
        return debtPenalty * failurePenalty * dropPenalty * restPausePenalty * partialPenalty
    }

    private fun estimateRelativeLoadRatio(
        set: CompletedSet,
        reps: Double,
        rpe: Double,
    ): Double? {
        val homologatedLoad = set.homologatedResultV3?.augeEquivalentLoad ?: set.weight
        val estimatedRm = set.homologatedResultV3?.estimatedRm
        if (homologatedLoad > 0.0 && estimatedRm != null && estimatedRm > 0.0) {
            return (homologatedLoad / estimatedRm).coerceIn(0.30, 1.20)
        }

        if (set.actualIntensityMode == IntensityMode.SOLO_RM) return 0.98
        if (reps <= 1.5 && rpe >= 9.5) return 0.95
        if (reps <= 3.0 && rpe >= 9.0) return 0.90
        if (reps <= 5.0 && rpe >= 8.5) return 0.85
        return null
    }

    private fun calculateNearRmFatigueMultiplier(relativeLoadRatio: Double?): Double {
        val ratio = relativeLoadRatio ?: return 1.0
        return when {
            ratio >= 0.98 -> 1.40
            ratio >= 0.94 -> 1.30
            ratio >= 0.90 -> 1.20
            ratio >= 0.85 -> 1.10
            else -> 1.0
        }
    }

    fun calculateTechniquePenalty(technicalQuality: Int, effortSignal: Double): Double {
        val quality = technicalQuality.coerceIn(1, 5).toDouble()
        val boundedEffort = min(effortSignal, 12.0).coerceAtLeast(1.0)
        return 1.0 + (((5.0 - quality) / 4.0).pow(2.0) * (boundedEffort / 10.0).pow(3.0) * 0.5)
    }

    // ─── Drain por set ───────────────────────────────────────────────────────

    fun calculateSetBatteryDrain(
        set: CompletedSet,
        metrics: AugeMetrics,
        tanks: BatteryTanks,
        accumulatedSets: Int = 0,
        restTime: Int = 90,
        densityMultiplier: Double = 1.0,
        cnsMultiplier: Double = 1.0,
        spinalMultiplier: Double = 1.0,
        muscleMultiplier: Double = 1.0,
        weightUnit: WeightUnit = WeightUnit.KG,
    ): SetDrain {
        if (set.skipped) return SetDrain(cnsDrainPct = 0.0, muscularDrainPct = 0.0, spinalDrainPct = 0.0)
        // Unilateral L/R halves: each side is half a logical set so L+R ≈ one bilateral set
        val sideScale = if (set.side != null) 0.5 else 1.0
        val rpe = getEffectiveRPE(set)
        val baseReps = when {
            (set.timeSeconds ?: 0) > 0 -> ((set.timeSeconds ?: 0).coerceAtLeast(5) / 5.0)
            else -> set.reps.coerceAtLeast(1).toDouble()
        }
        
        val dropReps = set.dropSets.sumOf { it.reps }.toDouble()
        val rpReps = set.restPauses.sumOf { it.reps }.toDouble()

        // Repeticiones efectivas diferenciadas por tipo de tejido/fatiga
        val repsMuscular = baseReps + 0.40 * dropReps + 0.60 * rpReps
        val repsCns = baseReps + 0.85 * dropReps + 0.90 * rpReps
        val repsSpinal = baseReps + 0.30 * dropReps + 0.80 * rpReps

        val repsFactorMuscular = repsMuscular.pow(0.65)
        val repsFactorCns = repsCns.pow(0.65)
        val repsFactorSpinal = repsSpinal.pow(0.65)

        val rpeMult = calculateRpeMultiplier(rpe)
        
        // Bifurcación de Volumen Progresivo (Junk Volume vs. Estimulante)
        val muscularProgressiveMult = calculateMuscularProgressiveMultiplier(accumulatedSets)
        val systemicProgressiveMult = calculateSystemicProgressiveMultiplier(accumulatedSets)
        
        // Cruce de Descansos (Descansos cortos reducen daño muscular mecánico pero disparan SNC)
        val localRestMult = when {
            restTime <= 30 -> 0.85
            restTime <= 45 -> 0.90
            restTime <= 75 -> 0.95
            restTime <= 120 -> 1.00
            restTime >= 240 -> 1.10
            restTime >= 180 -> 1.05
            else -> 1.0
        }
        val systemRestMult = when {
            restTime <= 30 -> 1.35
            restTime <= 45 -> 1.25
            restTime <= 75 -> 1.15
            restTime <= 120 -> 1.05
            restTime >= 240 -> 0.85
            restTime >= 180 -> 0.90
            else -> 1.0
        }
        val structureRestMult = when {
            restTime <= 30 -> 1.25
            restTime <= 60 -> 1.15
            restTime <= 90 -> 1.08
            restTime >= 240 -> 0.88
            restTime >= 180 -> 0.92
            else -> 1.0
        }
        
        val reps = baseReps // para compatibilidad con biases
        val muscularBias = when {
            reps >= 15.0 -> 1.15
            reps <= 4.0 -> 0.78
            reps >= 12.0 -> 1.08
            else -> 1.0
        }
        val systemBias = when {
            reps <= 4.0 -> 1.25
            reps >= 12.0 -> 0.88
            else -> 1.0
        }
        val structureBias = when {
            reps <= 4.0 -> 1.22
            reps >= 12.0 -> 0.85
            else -> 1.0
        }
        val effectiveLoadRaw = set.homologatedResultV3?.augeEquivalentLoad ?: set.weight
        val effectiveLoad = if (weightUnit == WeightUnit.LBS) effectiveLoadRaw / 2.2046226218 else effectiveLoadRaw
        val loadFactor = if (effectiveLoad > 0.0) {
            1.0 + ln(1.0 + (effectiveLoad / 20.0)) * 0.25
        } else {
            1.0
        }

        // Bifurcación de Técnicas de Intensidad (Dropsets y Rest-pauses)
        val muscularTechniqueFactor = calculateMuscularTechniqueMultiplier(set)
        val systemicTechniqueFactor = calculateSystemicTechniqueMultiplier(set)
        
        val nearRmRatio = estimateRelativeLoadRatio(set, reps, rpe)
        val nearRmMult = calculateNearRmFatigueMultiplier(nearRmRatio)
        val density = densityMultiplier.coerceIn(0.85, 1.45)
        
        // Bifurcación de densidad (Supersets: incrementan fatiga metabólica muscular y disparan el SNC)
        val muscularDensityMult = if (density > 1.0) 1.0 + (density - 1.0) * 0.35 else 1.0
        val systemDensityMult = 1.0 + (density - 1.0) * 1.50
        val structureDensityMult = 1.0 + (density - 1.0) * 1.15

        val assistedCount = set.assistedReps ?: 0
        val assistedMultiplier = 1.0 + (assistedCount * 0.20)

        val rawMuscular =
            metrics.efc * repsFactorMuscular * rpeMult * muscularProgressiveMult * localRestMult * muscularBias *
                muscularTechniqueFactor * muscularDensityMult * (1.0 + (nearRmMult - 1.0) * 0.35) * 1.85 * assistedMultiplier * muscleMultiplier
        val rawCns =
            metrics.cnc * repsFactorCns * rpeMult * systemicProgressiveMult * systemRestMult * systemBias *
                systemicTechniqueFactor * systemDensityMult * nearRmMult * 1.15 * assistedMultiplier * cnsMultiplier
        val rawSpinal =
            metrics.ssc * repsFactorSpinal * rpeMult * systemicProgressiveMult * structureRestMult * structureBias * loadFactor *
                systemicTechniqueFactor * structureDensityMult * (1.0 + (nearRmMult - 1.0) * 1.20) * 5.2 * assistedMultiplier * spinalMultiplier

        return SetDrain(
            // Cap per-set so one set cannot saturate a whole channel (was 100%)
            muscularDrainPct = (rawMuscular / tanks.muscular * 100).coerceIn(0.0, 32.0) * sideScale,
            cnsDrainPct      = (rawCns      / tanks.cns      * 100).coerceIn(0.0, 32.0) * sideScale,
            spinalDrainPct   = (rawSpinal   / tanks.spinal   * 100).coerceIn(0.0, 35.0) * sideScale,
        )
    }

    private fun scaleSpinalDrainToUi(rawSpinalSessionDrain: Double, tanks: BatteryTanks): Double {
        val capacity = max(70.0, tanks.spinal * 0.02)
        val rawPct = (rawSpinalSessionDrain / capacity) * 100.0
        val batteryDrop = 100.0 * (1.0 - exp(-rawPct / 70.0))
        return batteryDrop.coerceIn(0.0, 100.0)
    }

    // ─── Estrés total de sesión completada (para historial) ──────────────────

    data class SessionDrainBreakdown(
        val global: PredictedDrain,
        /** Pillar id → muscular drain % attributed in this session. */
        val perMuscleMuscular: Map<String, Int>,
    )

    private data class AggregateDrainAcc(
        var totalCns: Double = 0.0,
        var totalMuscular: Double = 0.0,
        var totalSpinal: Double = 0.0,
        var accumulatedDrain: Double = 0.0,
        val muscleVolumeMap: MutableMap<String, Double> = mutableMapOf(),
        val perMuscleMuscular: MutableMap<String, Double> = mutableMapOf(),
    )

    private fun roleWeightForDrain(role: MuscleRole): Double = when (role) {
        MuscleRole.PRIMARY -> 1.0
        MuscleRole.SECONDARY -> 0.5
        MuscleRole.STABILIZER, MuscleRole.NEUTRALIZER -> 0.4
    }

    private fun attributeMuscularDrain(
        acc: AggregateDrainAcc,
        adjustedMuscular: Double,
        primaryPillar: String,
        involvementWeights: List<Pair<String, Double>>,
    ) {
        if (adjustedMuscular <= 0.0) return
        val weightSum = involvementWeights.sumOf { it.second }
        if (weightSum <= 0.0 || involvementWeights.isEmpty()) {
            acc.perMuscleMuscular[primaryPillar] =
                (acc.perMuscleMuscular[primaryPillar] ?: 0.0) + adjustedMuscular
            return
        }
        involvementWeights.forEach { (pillar, weight) ->
            val share = adjustedMuscular * (weight / weightSum)
            acc.perMuscleMuscular[pillar] = (acc.perMuscleMuscular[pillar] ?: 0.0) + share
        }
    }

    private fun aggregateSetIntoSessionDrain(
        acc: AggregateDrainAcc,
        set: CompletedSet,
        metrics: AugeMetrics,
        tanks: BatteryTanks,
        restTime: Int,
        densityMult: Double,
        primaryPillar: String,
        involvementWeights: List<Pair<String, Double>>,
        muscleMult: Double,
        cnsMult: Double,
        spinalMult: Double,
        weightUnit: WeightUnit,
        muscularCap: Double,
        cnsCap: Double,
        spinalCap: Double,
    ) {
        val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
        val decayK = AugeUtils.SESSION_DECAY_K
        var accumulated = acc.muscleVolumeMap[primaryPillar] ?: 0.0
        accumulated += if (set.side != null) 0.5 else 1.0
        val drain = calculateSetBatteryDrain(
            set = set,
            metrics = metrics,
            tanks = tanks,
            accumulatedSets = accumulated.roundToInt(),
            restTime = restTime,
            densityMultiplier = densityMult,
            cnsMultiplier = cnsMult,
            spinalMultiplier = spinalMult,
            muscleMultiplier = muscleMult,
            weightUnit = weightUnit,
        )
        val diminishingFactor = 1.0 / (1.0 + decayK * (acc.accumulatedDrain / 100.0))
        val adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
        val adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
        val adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
        val cappedMuscular = applySoftCap(adjustedMuscular, acc.totalMuscular, muscularCap)
        val cappedCns = applySoftCap(adjustedCns, acc.totalCns, cnsCap)
        val cappedSpinal = applySoftCap(adjustedSpinal, acc.totalSpinal, spinalCap)
        acc.totalMuscular += cappedMuscular
        acc.totalCns += cappedCns
        acc.totalSpinal += cappedSpinal
        acc.accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
        attributeMuscularDrain(acc, cappedMuscular, primaryPillar, involvementWeights)
        acc.muscleVolumeMap[primaryPillar] = accumulated
    }

    private fun finalizeSessionDrain(
        acc: AggregateDrainAcc,
        tanks: BatteryTanks,
        settings: Settings,
        muscularCap: Double,
        cnsCap: Double,
        spinalCap: Double,
        hardEffectiveSets: Int,
    ): SessionDrainBreakdown {
        val scaledSpinal = scaleSpinalDrainToUi(acc.totalSpinal, tanks)
        var result = PredictedDrain(
            cns = acc.totalCns.coerceAtMost(cnsCap).toInt(),
            muscular = acc.totalMuscular.coerceAtMost(muscularCap).toInt(),
            spinal = scaledSpinal.coerceAtMost(spinalCap).toInt(),
        )
        result = ensureMinimumHardSessionDrain(result, hardEffectiveSets, muscularCap, cnsCap, spinalCap)
        val (cnsBias, muscularBias, spinalBias) = normalizeBias(settings.augePredictionBias)
        val global = PredictedDrain(
            cns = (result.cns + cnsBias).toInt().coerceIn(0, cnsCap.toInt()),
            muscular = (result.muscular + muscularBias).toInt().coerceIn(0, muscularCap.toInt()),
            spinal = (result.spinal + spinalBias).toInt().coerceIn(0, spinalCap.toInt()),
        )
        val perMuscle = acc.perMuscleMuscular.mapValues { (_, v) ->
            v.roundToInt().coerceIn(0, muscularCap.toInt())
        }
        return SessionDrainBreakdown(global = global, perMuscleMuscular = perMuscle)
    }

    fun calculateCompletedSessionDrainBreakdown(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): SessionDrainBreakdown {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        val floor = physiologicalFloor(settings)
        val muscularCap = (100 - floor.muscular).coerceAtLeast(5).toDouble()
        val cnsCap = (100 - floor.cns).coerceAtLeast(5).toDouble()
        val spinalCap = (100 - floor.spinal).coerceAtLeast(5).toDouble()
        val weightUnit = settings.weightUnit
        val remappedMultipliers = remapMuscleMultiplierMapToPillars(adaptiveCache.muscleDrainMultipliers)
        val acc = AggregateDrainAcc()

        completedExercises.forEach { ex ->
            val lookupId = (ex.catalogConfigurationId ?: ex.exerciseDbId ?: ex.exerciseId)?.lowercase()
            val resolvedId = lookupId?.let { raw ->
                com.example.kpkn.data.exercises.catalogSearchRedirects()[raw] ?: raw
            }
            val dbInfo = resolvedId?.let { exerciseDb[it] }
            val resolvedIdStr = resolvedId ?: dbInfo?.let { info ->
                exerciseDb.entries.find { it.value === info }?.key
            } ?: lookupId ?: "n/a"
            val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo)
                ?: run {
                    android.util.Log.d(
                        "AugeFatigueEngine",
                        "Sin métricas de fatiga para '${ex.exerciseName}' (id=$resolvedIdStr) — ejercicio omitido del drenaje",
                    )
                    return@forEach
                }
            val densityMult = getDensityMultiplierForExercise(
                supersetId = ex.supersetId,
                restTime = ex.supersetRestBetween ?: ex.restTime,
                supersetExerciseCount = ex.supersetExerciseCount,
                supersetRounds = ex.supersetRounds,
                supersetRestAfter = ex.supersetRestAfter,
            )
            // Completed logs carry the chip-adjusted snapshot. Fall back to the
            // catalog only for logs created before chip metadata was persisted.
            val involved = ex.effectiveMuscles?.takeIf { it.isNotEmpty() }
                ?: dbInfo?.involvedMuscles.orEmpty()
            val primaryMuscle = involved
                .find { it.role == MuscleRole.PRIMARY }
                ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                ?: "Core"
            val involvementWeights = involved
                .map { getAugeMusclePillarId(it.muscle, it.emphasis) to roleWeightForDrain(it.role) }
                .groupBy({ it.first }, { it.second })
                .map { (pillar, weights) -> pillar to weights.maxOrNull()!! }
            val muscleMult = lookupMuscleDrainMultiplier(remappedMultipliers, primaryMuscle)

            ex.sets.forEach { s ->
                if (!isSetEffective(s)) return@forEach
                aggregateSetIntoSessionDrain(
                    acc = acc,
                    set = s,
                    metrics = metrics,
                    tanks = tanks,
                    restTime = ex.supersetRestBetween ?: ex.restTime,
                    densityMult = densityMult,
                    primaryPillar = primaryMuscle,
                    involvementWeights = involvementWeights,
                    muscleMult = muscleMult,
                    cnsMult = adaptiveCache.cnsDrainMultiplier,
                    spinalMult = adaptiveCache.spinalDrainMultiplier,
                    weightUnit = weightUnit,
                    muscularCap = muscularCap,
                    cnsCap = cnsCap,
                    spinalCap = spinalCap,
                )
            }
        }

        val hardSets = completedExercises.flatMap { it.sets }
            .count { isSetEffective(it) && getEffectiveRPE(it) >= 8.0 }
        return finalizeSessionDrain(acc, tanks, settings, muscularCap, cnsCap, spinalCap, hardSets)
    }

    fun calculateCompletedSessionDrain(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): PredictedDrain = calculateCompletedSessionDrainBreakdown(
        completedExercises = completedExercises,
        exerciseDb = exerciseDb,
        settings = settings,
        adaptiveCache = adaptiveCache,
    ).global

    /** Pillar → muscular drain % for finish-sheet seeding (same pipeline as global drain). */
    fun calculateCompletedSessionMuscleDrains(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): Map<String, Int> = calculateCompletedSessionDrainBreakdown(
        completedExercises = completedExercises,
        exerciseDb = exerciseDb,
        settings = settings,
        adaptiveCache = adaptiveCache,
    ).perMuscleMuscular

    private fun ensureMinimumHardSessionDrain(
        drain: PredictedDrain,
        hardEffectiveSets: Int,
        muscularCap: Double,
        cnsCap: Double,
        spinalCap: Double,
    ): PredictedDrain {
        if (hardEffectiveSets < 6) return drain
        val minDrop = 10
        return PredictedDrain(
            cns = max(drain.cns, minDrop).coerceAtMost(cnsCap.toInt()),
            muscular = max(drain.muscular, minDrop).coerceAtMost(muscularCap.toInt()),
            spinal = max(drain.spinal, (minDrop * 0.8).toInt()).coerceAtMost(spinalCap.toInt()),
        )
    }

    fun calculateCompletedSessionStress(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): Double {
        val summary = calculateCompletedSessionDrain(
            completedExercises = completedExercises,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = adaptiveCache,
        )
        return (summary.cns * STRESS_WEIGHT_CNS) +
            (summary.muscular * STRESS_WEIGHT_MUSCULAR) +
            (summary.spinal * STRESS_WEIGHT_SPINAL)
    }

    // ─── Costo estimado de sesión futura ─────────────────────────────────────

    @Deprecated("Use calculateAdjustedPredictedDrain instead", ReplaceWith("calculateAdjustedPredictedDrain(session, exerciseDb, settings)"))
    fun calculatePredictedSessionDrain(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): PredictedDrain = calculateAdjustedPredictedDrain(session, exerciseDb, settings)

    fun calculateAdjustedPredictedDrain(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): PredictedDrain {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        val floor = physiologicalFloor(settings)
        val muscularCap = (100 - floor.muscular).coerceAtLeast(5).toDouble()
        val cnsCap = (100 - floor.cns).coerceAtLeast(5).toDouble()
        val spinalCap = (100 - floor.spinal).coerceAtLeast(5).toDouble()
        val weightUnit = settings.weightUnit
        val remappedMultipliers = remapMuscleMultiplierMapToPillars(adaptiveCache.muscleDrainMultipliers)
        val acc = AggregateDrainAcc()

        val exercises = session.exercises + session.parts.flatMap { it.exercises }

        exercises.forEach { ex ->
            val resolvedId = (ex.catalogConfigurationId ?: ex.exerciseDbId ?: ex.exerciseId)?.lowercase()?.let { rawId ->
                catalogSearchRedirects()[rawId] ?: rawId
            }
            val dbInfo = resolvedId?.let { exerciseDb[it] }
            val metrics = getDynamicAugeMetrics(ex.name, dbInfo?.equipment, dbInfo)
                ?: run {
                    android.util.Log.d(
                        "AugeFatigueEngine",
                        "Sin métricas de fatiga para '${ex.name}' — ejercicio omitido del drenaje ajustado",
                    )
                    return@forEach
                }
            val densityMult = getDensityMultiplierForExercise(ex.supersetId, ex.restTime ?: 90)
            val involved = when {
                !ex.effectiveMuscles.isNullOrEmpty() -> ex.effectiveMuscles!!
                else -> dbInfo?.involvedMuscles.orEmpty()
            }
            val primaryMuscle = involved
                .find { it.role == MuscleRole.PRIMARY }
                ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                ?: "Core"
            val involvementWeights = involved
                .map { getAugeMusclePillarId(it.muscle, it.emphasis) to roleWeightForDrain(it.role) }
                .groupBy({ it.first }, { it.second })
                .map { (pillar, weights) -> pillar to weights.maxOrNull()!! }
            val muscleMult = lookupMuscleDrainMultiplier(remappedMultipliers, primaryMuscle)

            ex.sets.forEach { s ->
                if (s.isIneffective) return@forEach
                val calculatedWeight = if (ex.trainingMode == TrainingMode.RM && s.targetPercentageRM != null && ex.reference1RM != null && ex.reference1RM!! > 0.0) {
                    (s.targetPercentageRM / 100.0) * ex.reference1RM!!
                } else {
                    s.weight ?: 60.0
                }
                // Planned sets are modeled as bilateral equivalents (no L/R side on targets).
                val syntheticSet = CompletedSet(
                    id = "",
                    weight = calculatedWeight,
                    reps = s.targetReps ?: 8,
                    rpe = s.targetRPE,
                    rir = s.targetRIR,
                    actualIntensityMode = s.intensityMode,
                    actualIntensityValue = when (s.intensityMode) {
                        IntensityMode.RPE -> s.targetRPE
                        IntensityMode.RIR -> s.targetRIR?.toDouble()
                        else -> null
                    },
                    isFailure = s.isFailure || s.intensityMode == IntensityMode.FAILURE,
                )
                aggregateSetIntoSessionDrain(
                    acc = acc,
                    set = syntheticSet,
                    metrics = metrics,
                    tanks = tanks,
                    restTime = ex.restTime ?: 90,
                    densityMult = densityMult,
                    primaryPillar = primaryMuscle,
                    involvementWeights = involvementWeights,
                    muscleMult = muscleMult,
                    cnsMult = adaptiveCache.cnsDrainMultiplier,
                    spinalMult = adaptiveCache.spinalDrainMultiplier,
                    weightUnit = weightUnit,
                    muscularCap = muscularCap,
                    cnsCap = cnsCap,
                    spinalCap = spinalCap,
                )
            }
        }

        val hardSets = exercises.sumOf { ex ->
            ex.sets.count { s ->
                !s.isIneffective && (s.targetRPE ?: when {
                    s.targetRIR != null -> (10.0 - s.targetRIR).coerceIn(1.0, 10.0)
                    else -> 7.0
                }) >= 8.0
            }
        }
        return finalizeSessionDrain(acc, tanks, settings, muscularCap, cnsCap, spinalCap, hardSets).global
    }

    private const val EMA_ALPHA = 0.17
    private const val EMA_SMOOTHING = 0.83
    private const val TREND_WINDOW = 3

    fun calculateMesocycleStressEMA(
        logs: List<WorkoutLog>,
        programId: String,
        mesoIndex: Int,
    ): MesocycleStressEMA {
        val relevant = logs
            .filter { it.programId == programId && it.mesoIndex == mesoIndex }
            .sortedBy { it.date }

        val stressScores = relevant.mapNotNull { it.sessionStressScore }

        if (stressScores.isEmpty()) {
            return MesocycleStressEMA(
                programId = programId,
                mesoIndex = mesoIndex,
                emaValue = 0.0,
                sessionCount = 0,
                latestStressScore = null,
                stressTrend = StressTrend.STABLE,
                computedAtMs = System.currentTimeMillis(),
            )
        }

        val emaValue = stressScores.fold(0.0) { acc, score ->
            acc * EMA_SMOOTHING + score * EMA_ALPHA
        }

        val trend = if (stressScores.size >= TREND_WINDOW) {
            val recent = stressScores.takeLast(TREND_WINDOW)
            val firstHalf = recent.take(TREND_WINDOW / 2)
            val secondHalf = recent.takeLast(TREND_WINDOW / 2)
            val avgFirst = firstHalf.average()
            val avgSecond = secondHalf.average()
            when {
                avgSecond > avgFirst * 1.10 -> StressTrend.RISING
                avgSecond < avgFirst * 0.90 -> StressTrend.FALLING
                else -> StressTrend.STABLE
            }
        } else {
            StressTrend.STABLE
        }

        return MesocycleStressEMA(
            programId = programId,
            mesoIndex = mesoIndex,
            emaValue = emaValue,
            sessionCount = stressScores.size,
            latestStressScore = stressScores.lastOrNull(),
            stressTrend = trend,
            computedAtMs = System.currentTimeMillis(),
        )
    }

    fun adjustPredictedDrainWithEMA(
        rawDrain: PredictedDrain,
        ema: MesocycleStressEMA,
    ): PredictedDrain {
        if (ema.sessionCount < 2) return rawDrain

        val avgHistoricalStress = ema.emaValue
        val highStressThreshold = 50.0
        val lowStressThreshold = 25.0

        val adjustmentFactor = when {
            avgHistoricalStress > highStressThreshold -> {
                val severity = ((avgHistoricalStress - highStressThreshold) / 50.0).coerceAtMost(0.5)
                1.0 - (severity * 0.15)
            }
            avgHistoricalStress < lowStressThreshold -> {
                val headroom = ((lowStressThreshold - avgHistoricalStress) / 25.0).coerceAtMost(0.3)
                1.0 + (headroom * 0.10)
            }
            else -> 1.0
        }

        return PredictedDrain(
            cns = (rawDrain.cns * adjustmentFactor).toInt().coerceIn(0, 100),
            muscular = (rawDrain.muscular * adjustmentFactor).toInt().coerceIn(0, 100),
            spinal = (rawDrain.spinal * adjustmentFactor).toInt().coerceIn(0, 100),
        )
    }

    /**
     * Determina si debe mostrarse sugerencia de auto-deload cuando la fatiga se dispara.
     *
     * @param cumulativeFatigue Fatiga acumulada (0-100+)
     * @param readinessScore Score de readiness (0-100)
     * @param settings Configuración del usuario que incluye augeAutoDeload
     * @return true si debe mostrarse la sugerencia de deload
     */
    fun shouldSuggestAutoDeload(
        cumulativeFatigue: Double,
        readinessScore: Int,
        settings: Settings
    ): Boolean {
        if (!settings.algorithmSettings.augeAutoDeload) return false
        
        // Sugerir deload si:
        // 1. Fatiga está muy alta (>75)
        // 2. Y readiness está baja (<40)
        // 3. Esto indica que el atleta necesita descanso preventivo
        val highFatigue = cumulativeFatigue > 75.0
        val lowReadiness = readinessScore < 40
        
        return highFatigue && lowReadiness
    }
}
