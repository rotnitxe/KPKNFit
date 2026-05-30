package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.domain.auge.AugeUtils.physiologicalFloor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
/**
 * AugeFatigueEngine — Motor de Fatiga AUGE v3.0 para Kotlin.
 * Funciones puras, sin estado. Equivalente a @kpkn/shared-domain fatigue.ts
 */
object AugeFatigueEngine {

    const val WEEKLY_CNS_FATIGUE_REFERENCE = 4000.0
    private const val MAX_TECHNIQUE_FATIGUE_MULTIPLIER = 4.0

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


    private fun applySoftCap(drain: Double, accumulated: Double, cap: Double): Double {
        if (drain <= 0.0 || cap <= 0.0) return 0.0
        val p = (accumulated / cap).coerceIn(0.0, 1.0)
        val damping = when {
            p <= 0.40 -> 1.0 - p * 0.5
            p <= 0.70 -> 0.80 * exp(-3.2 * (p - 0.40))
            else      -> 0.30 * exp(-5.5 * (p - 0.70))
        }
        return (drain * damping).coerceAtLeast(0.0)
    }

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
        val levelMult = 1.2 // Advanced por defecto
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

    private fun deriveAugeMetricsHeuristic(exerciseName: String, equipment: String?): AugeMetrics {
        val lower = exerciseName.lowercase().trim()
        val hasEquipment = equipment?.lowercase() ?: ""

        val (baseEfc, baseCnc, baseSsc) = when {
            lower.contains("deadlift") || lower.contains("peso muerto") || lower.contains("rumano") -> Triple(4.0, 4.0, 1.6)
            lower.contains("squat") || lower.contains("sentadilla") || lower.contains("hack squat") -> Triple(3.8, 3.8, 1.2)
            lower.contains("bench press") || lower.contains("press banca") || lower.contains("press de banca") -> Triple(3.2, 3.5, 0.8)
            lower.contains("overhead press") || lower.contains("military press") || lower.contains("press militar") -> Triple(3.0, 3.5, 1.0)
            lower.contains("pull up") || lower.contains("pull-up") || lower.contains("dominada") || lower.contains("chin up") || lower.contains("chin-up") -> Triple(3.0, 3.0, 0.3)
            lower.contains("row") || lower.contains("remo") && !lower.contains("rumano") -> Triple(3.0, 3.2, 0.7)
            lower.contains("hip thrust") || lower.contains("empuje de cadera") -> Triple(3.5, 3.0, 0.8)
            lower.contains("clean") || lower.contains("snatch") || lower.contains("arranque") || lower.contains("cargada") || lower.contains("envion") || lower.contains("envión") -> Triple(4.5, 4.5, 1.4)
            lower.contains("lunge") || lower.contains("zancada") || lower.contains("bulgarian") || lower.contains("búlgaro") || lower.contains("bulgara") || lower.contains("búlgara") -> Triple(2.8, 2.5, 0.5)
            lower.contains("curl") || lower.contains("bicep") || lower.contains("bíceps") -> Triple(1.5, 1.5, 0.1)
            lower.contains("extension") && (lower.contains("tricep") || lower.contains("tríceps")) -> Triple(1.5, 1.5, 0.1)
            lower.contains("lateral") || lower.contains("deltoides") && lower.contains("lateral") -> Triple(1.5, 1.5, 0.1)
            lower.contains("pushdown") || lower.contains("pressdown") || lower.contains("frances") || lower.contains("francés") -> Triple(1.5, 1.5, 0.1)
            lower.contains("leg press") || lower.contains("prensa") -> Triple(3.0, 2.5, 0.8)
            lower.contains("leg curl") || lower.contains("femoral") || lower.contains("curl de pierna") -> Triple(2.0, 1.8, 0.2)
            lower.contains("leg extension") || lower.contains("extension de cuadriceps") || lower.contains("extensión de cuádriceps") -> Triple(2.2, 2.0, 0.1)
            lower.contains("calf") || lower.contains("pantorrilla") || lower.contains("gemelo") -> Triple(1.5, 1.2, 0.1)
            lower.contains("fly") || lower.contains("apertura") || lower.contains("pec deck") || lower.contains("crossover") -> Triple(2.0, 1.8, 0.2)
            lower.contains("dip") || lower.contains("fondo") -> Triple(2.8, 3.0, 0.6)
            lower.contains("good morning") || lower.contains("buenos dias") || lower.contains("buenos días") -> Triple(2.8, 2.5, 1.2)
            lower.contains("hyperextension") || lower.contains("hiperextension") || lower.contains("hiperextensión") -> Triple(2.0, 1.5, 0.8)
            lower.contains("carry") || lower.contains("cargada") && lower.contains("granjero") -> Triple(2.0, 2.5, 1.0)
            else -> Triple(2.5, 2.5, 0.5)
        }

        var efc = baseEfc; var cnc = baseCnc; var ssc = baseSsc

        if (hasEquipment.contains("mancuerna") || hasEquipment.contains("dumbbell") || hasEquipment.contains("dumbbells")) {
            cnc += 0.2; ssc -= 0.2
        }
        if (hasEquipment.contains("smith")) {
            cnc -= 0.5; efc -= 0.2
        }
        if (hasEquipment.contains("cable") || hasEquipment.contains("polea")) {
            cnc -= 0.3; efc += 0.2
        }
        if (hasEquipment.contains("barra")) {
            ssc += 0.2
        }

        if (exerciseName.contains("pausa", ignoreCase = true) || exerciseName.contains("pause", ignoreCase = true)) {
            cnc += 0.3; efc += 0.5
        }
        if (exerciseName.contains("deficit", ignoreCase = true) || exerciseName.contains("déficit", ignoreCase = true)) {
            ssc += 0.2; efc += 0.3
        }
        if (exerciseName.contains("parcial", ignoreCase = true) || exerciseName.contains("partial", ignoreCase = true)) {
            efc -= 0.2; ssc += 0.2
        }

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
    ): AugeMetrics? = deriveAugeMetricsFromDb(dbInfo) ?: deriveAugeMetricsHeuristic(exerciseName, equipment)

    // ─── RPE efectivo (traduce RPE / RIR / failure) ──────────────────────────

    fun getEffectiveRPE(set: CompletedSet): Double {
        var baseRpe = 7.0

        baseRpe = when {
            set.actualIntensityMode == IntensityMode.FAILURE -> 10.8
            set.actualIntensityMode == IntensityMode.RPE && set.actualIntensityValue != null -> set.actualIntensityValue
            set.actualIntensityMode == IntensityMode.RIR && set.actualIntensityValue != null -> 10.0 - set.actualIntensityValue
            set.rpe != null         -> set.rpe
            set.rir != null         -> (10 - set.rir).toDouble()
            else                    -> 7.0
        }

        if (set.isFailure) baseRpe = max(baseRpe, 11.2)
        if (set.isFailedSet) baseRpe = max(baseRpe, 10.2)

        var techniqueBonus = 0.0
        if (set.dropSets.isNotEmpty()) techniqueBonus += set.dropSets.size * 1.5
        if (set.restPauses.isNotEmpty()) techniqueBonus += set.restPauses.size * 1.0
        if (set.isPartial && (set.partialReps ?: 0) > 0) techniqueBonus += 0.5
        if (techniqueBonus > 0 && baseRpe < 10.0) baseRpe = 10.0

        return (baseRpe + techniqueBonus).coerceIn(1.0, 12.0)
    }

    // ─── ¿El set cuenta para fatiga? ─────────────────────────────────────────

    fun isSetEffective(set: CompletedSet): Boolean {
        if (set.skipped) return false
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
        val failureBonus = (exp(overshoot * 0.9) - 1.0) * 0.58
        return (base + failureBonus).coerceAtMost(3.4)
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

    private fun calculateSetProgressiveFatigueMultiplier(accumulatedSets: Int): Double {
        val progression = (accumulatedSets.coerceAtLeast(1) - 1).toDouble()
        return (1.0 + 0.055 * progression.pow(1.55)).coerceIn(1.0, 2.6)
    }

    private fun calculateTechniqueIntensityMultiplier(set: CompletedSet): Double {
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
            val countPenalty = 1.0 + 0.16 * (2.0.pow(set.dropSets.size.toDouble()) - 1.0)
            val depthPenalty = if (set.weight > 0.0) {
                val avgDepth = set.dropSets
                    .map { ds -> ((set.weight - ds.weight) / set.weight).coerceIn(0.0, 0.7) }
                    .average()
                1.0 + avgDepth * 0.20
            } else {
                1.0
            }
            countPenalty * depthPenalty
        }

        val restPausePenalty = if (set.restPauses.isEmpty()) {
            1.0
        } else {
            val countPenalty = 1.0 + 0.18 * (2.0.pow(set.restPauses.size.toDouble()) - 1.0)
            val restValues = set.restPauses.map { it.restTime.toDouble() }.filter { it > 0.0 }
            val avgRest = if (restValues.isEmpty()) 20.0 else restValues.average()
            val densityPenalty = when {
                avgRest <= 15.0 -> 1.18
                avgRest <= 25.0 -> 1.11
                avgRest <= 35.0 -> 1.05
                else -> 1.0
            }
            countPenalty * densityPenalty
        }

        val partialPenalty = if ((set.partialReps ?: 0) > 0) 1.05 else 1.0
        return (debtPenalty * failurePenalty * dropPenalty * restPausePenalty * partialPenalty)
            .coerceIn(1.0, MAX_TECHNIQUE_FATIGUE_MULTIPLIER)
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
    ): SetDrain {
        if (set.skipped) return SetDrain(cnsDrainPct = 0.0, muscularDrainPct = 0.0, spinalDrainPct = 0.0)
        val rpe = getEffectiveRPE(set)
        val reps = when {
            (set.timeSeconds ?: 0) > 0 -> ((set.timeSeconds ?: 0).coerceAtLeast(5) / 5.0)
            else -> set.reps.coerceAtLeast(1).toDouble()
        }
        val repsFactor = reps.pow(0.65)
        val rpeMult = calculateRpeMultiplier(rpe)
        val setProgressiveMult = calculateSetProgressiveFatigueMultiplier(accumulatedSets)
        val localRestMult = when {
            restTime <= 30 -> 1.30
            restTime <= 45 -> 1.22
            restTime <= 75 -> 1.12
            restTime <= 120 -> 1.05
            restTime >= 240 -> 0.88
            restTime >= 180 -> 0.92
            else -> 1.0
        }
        val systemRestMult = when {
            restTime <= 30 -> 1.26
            restTime <= 45 -> 1.18
            restTime <= 75 -> 1.10
            restTime <= 120 -> 1.04
            restTime >= 240 -> 0.90
            restTime >= 180 -> 0.94
            else -> 1.0
        }
        val structureRestMult = when {
            restTime <= 30 -> 1.20
            restTime <= 60 -> 1.12
            restTime <= 90 -> 1.06
            restTime >= 240 -> 0.91
            restTime >= 180 -> 0.95
            else -> 1.0
        }
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
        val effectiveLoad = set.homologatedResultV3?.augeEquivalentLoad ?: set.weight
        val loadFactor = if (effectiveLoad > 0.0) {
            1.0 + ln(1.0 + (effectiveLoad / 20.0)) * 0.25
        } else {
            1.0
        }

        val techniqueFactor = calculateTechniqueIntensityMultiplier(set)
        val nearRmRatio = estimateRelativeLoadRatio(set, reps, rpe)
        val nearRmMult = calculateNearRmFatigueMultiplier(nearRmRatio)
        val density = densityMultiplier.coerceIn(0.85, 1.45)
        val muscularDensityMult = 1.0 + (density - 1.0) * 0.90
        val systemDensityMult = 1.0 + (density - 1.0) * 1.05
        val structureDensityMult = 1.0 + (density - 1.0) * 1.15

        val rawMuscular =
            metrics.efc * repsFactor * rpeMult * setProgressiveMult * localRestMult * muscularBias *
                techniqueFactor * muscularDensityMult * (1.0 + (nearRmMult - 1.0) * 0.35) * 1.85
        val rawCns =
            metrics.cnc * repsFactor * rpeMult * setProgressiveMult * systemRestMult * systemBias *
                techniqueFactor * systemDensityMult * nearRmMult * 1.15
        val rawSpinal =
            metrics.ssc * repsFactor * rpeMult * setProgressiveMult * structureRestMult * structureBias * loadFactor *
                techniqueFactor * structureDensityMult * (1.0 + (nearRmMult - 1.0) * 1.20) * 5.2

        return SetDrain(
            muscularDrainPct = (rawMuscular / tanks.muscular * 100).coerceIn(0.0, 100.0),
            cnsDrainPct      = (rawCns      / tanks.cns      * 100).coerceIn(0.0, 100.0),
            spinalDrainPct   = (rawSpinal   / tanks.spinal   * 100).coerceIn(0.0, 100.0),
        )
    }

    private fun scaleSpinalDrainToUi(rawSpinalSessionDrain: Double, tanks: BatteryTanks): Double {
        val capacity = max(70.0, tanks.spinal * 0.02)
        val rawPct = (rawSpinalSessionDrain / capacity) * 100.0
        val batteryDrop = 100.0 * (1.0 - exp(-rawPct / 24.0))
        return batteryDrop.coerceIn(0.0, 100.0)
    }

    // ─── Estrés total de sesión completada (para historial) ──────────────────

    fun calculateCompletedSessionDrain(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
    ): PredictedDrain {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        val floor = physiologicalFloor(settings)
        val muscularCap = (100 - floor.muscular).coerceAtLeast(5).toDouble()
        val cnsCap = (100 - floor.cns).coerceAtLeast(5).toDouble()
        val spinalCap = (100 - floor.spinal).coerceAtLeast(5).toDouble()

        var totalCns = 0.0
        var totalMuscular = 0.0
        var totalSpinal = 0.0
        val muscleVolumeMap = mutableMapOf<String, Int>()
        val conservationFactor = 0.85
        val decayK = 0.65
        var accumulatedDrain = 0.0

        completedExercises.forEach { ex ->
            val lookupId = (ex.exerciseDbId ?: ex.exerciseId)?.lowercase()
            val dbInfo = lookupId?.let { exerciseDb[it] }
            val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo)
                ?: run {
                    android.util.Log.d(
                        "AugeFatigueEngine",
                        "Sin métricas de fatiga para '${ex.exerciseName}' (id=$lookupId) — ejercicio omitido del drenaje",
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
            val primaryMuscle = dbInfo?.involvedMuscles
                ?.find { it.role == MuscleRole.PRIMARY }
                ?.let { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
                ?: "Core"
            var accumulated = muscleVolumeMap[primaryMuscle] ?: 0

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
                val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                val adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                val adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                val adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                totalMuscular += applySoftCap(adjustedMuscular, totalMuscular, muscularCap)
                totalCns += applySoftCap(adjustedCns, totalCns, cnsCap)
                totalSpinal += applySoftCap(adjustedSpinal, totalSpinal, spinalCap)
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        val scaledSpinal = scaleSpinalDrainToUi(totalSpinal, tanks)
        return PredictedDrain(
            cns = totalCns.coerceAtMost(cnsCap).toInt(),
            muscular = totalMuscular.coerceAtMost(muscularCap).toInt(),
            spinal = scaledSpinal.coerceAtMost(spinalCap).toInt(),
        )
            .let { raw ->
                val (cnsBias, muscularBias, spinalBias) = normalizeBias(settings.augePredictionBias)
                PredictedDrain(
                    cns = (raw.cns + cnsBias).toInt().coerceIn(0, cnsCap.toInt()),
                    muscular = (raw.muscular + muscularBias).toInt().coerceIn(0, muscularCap.toInt()),
                    spinal = (raw.spinal + spinalBias).toInt().coerceIn(0, spinalCap.toInt()),
                )
            }
    }

    fun calculateCompletedSessionStress(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
    ): Double {
        val summary = calculateCompletedSessionDrain(
            completedExercises = completedExercises,
            exerciseDb = exerciseDb,
            settings = settings,
        )
        return (summary.cns * 0.45) + (summary.muscular * 0.25) + (summary.spinal * 0.30)
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
    ): PredictedDrain {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        val conservationFactor = 0.85
        val decayK = 0.65
        val floor = physiologicalFloor(settings)
        val muscularCap = (100 - floor.muscular).coerceAtLeast(5).toDouble()
        val cnsCap = (100 - floor.cns).coerceAtLeast(5).toDouble()
        val spinalCap = (100 - floor.spinal).coerceAtLeast(5).toDouble()
        var totalCns = 0.0; var totalMuscular = 0.0; var totalSpinal = 0.0
        var accumulatedDrain = 0.0
        val muscleVolumeMap = mutableMapOf<String, Int>()

        val exercises = session.exercises + session.parts.flatMap { it.exercises }

        exercises.forEach { ex ->
            val resolvedId = (ex.exerciseDbId ?: ex.exerciseId)?.lowercase()?.let { rawId ->
                EXERCISE_ID_ALIASES[rawId] ?: rawId
            }
            val dbInfo = resolvedId?.let { exerciseDb[it] } ?: exerciseDb.values.find {
                it.name.equals(ex.name, ignoreCase = true)
            }
            val metrics = getDynamicAugeMetrics(ex.name, dbInfo?.equipment, dbInfo)
                ?: run {
                    android.util.Log.d(
                        "AugeFatigueEngine",
                        "Sin métricas de fatiga para '${ex.name}' — ejercicio omitido del drenaje ajustado",
                    )
                    return@forEach
                }
            val densityMult = getDensityMultiplierForExercise(ex.supersetId, ex.restTime ?: 90)
            val primaryMuscle = dbInfo?.involvedMuscles
                ?.find { it.role == MuscleRole.PRIMARY }
                ?.let { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
                ?: "Core"
            var accumulated = muscleVolumeMap[primaryMuscle] ?: 0

            ex.sets.forEach { s ->
                if (s.isIneffective) return@forEach
                accumulated++
                val calculatedWeight = if (ex.trainingMode == TrainingMode.RM && s.targetPercentageRM != null && ex.reference1RM != null && ex.reference1RM!! > 0.0) {
                    (s.targetPercentageRM / 100.0) * ex.reference1RM!!
                } else {
                    s.weight ?: 60.0
                }
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
                val rawDrain = calculateSetBatteryDrain(
                    set = syntheticSet,
                    metrics = metrics,
                    tanks = tanks,
                    accumulatedSets = accumulated,
                    restTime = ex.restTime ?: 90,
                    densityMultiplier = densityMult,
                )
                val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                val adjustedMuscular = rawDrain.muscularDrainPct * conservationFactor * diminishingFactor
                val adjustedCns = rawDrain.cnsDrainPct * conservationFactor * diminishingFactor
                val adjustedSpinal = rawDrain.spinalDrainPct * conservationFactor * diminishingFactor

                totalMuscular += applySoftCap(adjustedMuscular, totalMuscular, muscularCap)
                totalCns += applySoftCap(adjustedCns, totalCns, cnsCap)
                totalSpinal += applySoftCap(adjustedSpinal, totalSpinal, spinalCap)
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        val scaledSpinal = scaleSpinalDrainToUi(totalSpinal, tanks)
        return PredictedDrain(
            cns = totalCns.coerceAtMost(cnsCap).toInt(),
            muscular = totalMuscular.coerceAtMost(muscularCap).toInt(),
            spinal = scaledSpinal.coerceAtMost(spinalCap).toInt(),
        )
            .let { raw ->
                val (cnsBias, muscularBias, spinalBias) = normalizeBias(settings.augePredictionBias)
                PredictedDrain(
                    cns = (raw.cns + cnsBias).toInt().coerceIn(0, cnsCap.toInt()),
                    muscular = (raw.muscular + muscularBias).toInt().coerceIn(0, muscularCap.toInt()),
                    spinal = (raw.spinal + spinalBias).toInt().coerceIn(0, spinalCap.toInt()),
                )
            }
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
