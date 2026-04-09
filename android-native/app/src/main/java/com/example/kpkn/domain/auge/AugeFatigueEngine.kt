package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
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

    fun getDynamicAugeMetrics(exerciseName: String, equipment: String? = null): AugeMetrics {
        val name = exerciseName.lowercase()

        var efc = 2.5; var ssc = 0.5; var cnc = 2.5

        // 1. Diccionario de patrones fundamentales
        when {
            name.contains("peso muerto") || name.contains("deadlift") -> {
                efc = 5.0; ssc = 2.0; cnc = 5.0
                if (name.contains("rumano") || name.contains("rdl"))  { efc = 4.2; ssc = 1.8; cnc = 4.0 }
                if (name.contains("sumo"))                             { efc = 4.8; ssc = 1.6; cnc = 4.8 }
            }
            name.contains("sentadilla") || name.contains("squat") -> {
                efc = 4.5; ssc = 1.5; cnc = 4.5
                if (name.contains("frontal") || name.contains("front"))           { efc = 4.2; ssc = 1.2; cnc = 4.5 }
                if (name.contains("búlgara") || name.contains("bulgarian"))       { efc = 3.8; ssc = 0.8; cnc = 3.5 }
                if (name.contains("hack"))                                         { efc = 3.5; ssc = 0.4; cnc = 3.0 }
            }
            name.contains("press militar") || name.contains("ohp") -> { efc = 4.0; ssc = 1.5; cnc = 4.2 }
            name.contains("press banca") || name.contains("bench press") -> { efc = 3.8; ssc = 0.3; cnc = 3.8 }
            name.contains("dominada") || name.contains("pull-up") || name.contains("pullup") -> { efc = 4.0; ssc = 0.2; cnc = 4.0 }
            name.contains("remo") || name.contains("row") -> {
                efc = 4.2; ssc = 1.6; cnc = 4.0
                if (name.contains("seal") || name.contains("pecho apoyado")) { efc = 3.2; ssc = 0.1; cnc = 2.5 }
            }
            name.contains("hip thrust") || name.contains("puente") -> { efc = 3.5; ssc = 0.5; cnc = 3.0 }
            name.contains("clean") || name.contains("snatch") -> { efc = 4.8; ssc = 1.8; cnc = 5.0 }
            name.contains("zancada") || name.contains("lunge") -> { efc = 3.5; ssc = 0.6; cnc = 3.2 }
            name.contains("press inclinado") -> { efc = 3.5; ssc = 0.2; cnc = 3.5 }
            name.contains("extensión") && (name.contains("cuádriceps") || name.contains("pierna")) -> { efc = 2.5; ssc = 0.1; cnc = 2.0 }
            name.contains("curl") -> { efc = 2.0; ssc = 0.1; cnc = 2.0 }
        }

        // 2. Modificadores de herramienta
        val eq = equipment?.lowercase() ?: ""
        if (name.contains("mancuerna") || eq == "mancuerna") {
            cnc = min(5.0, cnc + 0.2); ssc = max(0.0, ssc - 0.2)
        } else if (name.contains("smith") || name.contains("multipower")) {
            cnc = max(1.0, cnc - 0.5); efc = max(1.0, efc - 0.2)
        } else if (name.contains("polea") || name.contains("cable") || eq == "polea") {
            cnc = max(1.0, cnc - 0.3); efc = min(5.0, efc + 0.2)
        }

        // 3. Modificadores de técnica
        if (name.contains("pausa") || name.contains("paused")) { cnc = min(5.0, cnc + 0.3); efc = min(5.0, efc + 0.5) }
        if (name.contains("déficit") || name.contains("deficit")) { ssc = min(2.0, ssc + 0.2); efc = min(5.0, efc + 0.3) }
        if (name.contains("parcial") || name.contains("rack pull")) { ssc = min(2.0, ssc + 0.2); efc = max(1.0, efc - 0.2) }

        return AugeMetrics(
            efc = efc.coerceIn(1.0, 5.0),
            ssc = ssc.coerceIn(0.0, 2.0),
            cnc = cnc.coerceIn(1.0, 5.0),
        )
    }

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
        return baseRpe.coerceIn(1.0, 12.0)
    }

    // ─── ¿El set cuenta para fatiga? ─────────────────────────────────────────

    fun isSetEffective(set: CompletedSet): Boolean {
        val hasTime = (set.timeSeconds ?: 0) > 0
        if (set.reps <= 0 && !hasTime && set.weight <= 0.0) return false
        val rpe = getEffectiveRPE(set)
        return rpe >= 5.0
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
    ): Double {
        if (supersetId.isNullOrBlank()) return 1.0
        return when {
            restTime <= 45 -> 1.30
            restTime <= 75 -> 1.24
            else -> 1.18
        }
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

    // ─── Estrés total de sesión completada (para historial) ──────────────────

    fun calculateCompletedSessionDrain(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        tanks: BatteryTanks = BatteryTanks(cns = 600.0, muscular = 500.0, spinal = 4000.0),
    ): PredictedDrain {
        var totalCns = 0.0
        var totalMuscular = 0.0
        var totalSpinal = 0.0
        val muscleVolumeMap = mutableMapOf<String, Int>()

        completedExercises.forEach { ex ->
            val lookupId = (ex.exerciseDbId ?: ex.exerciseId)?.lowercase()
            val dbInfo = lookupId?.let { exerciseDb[it] }
            val metrics = getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment)
            val densityMult = getDensityMultiplierForExercise(ex.supersetId, ex.restTime)
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
                totalCns += drain.cnsDrainPct
                totalMuscular += drain.muscularDrainPct
                totalSpinal += drain.spinalDrainPct
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        return PredictedDrain(
            cns = totalCns.coerceAtMost(100.0).toInt(),
            muscular = totalMuscular.coerceAtMost(100.0).toInt(),
            spinal = totalSpinal.coerceAtMost(100.0).toInt(),
        )
    }

    fun calculateCompletedSessionStress(
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): Double {
        val summary = calculateCompletedSessionDrain(
            completedExercises = completedExercises,
            exerciseDb = exerciseDb,
            tanks = BatteryTanks(cns = 600.0, muscular = 500.0, spinal = 4000.0),
        )
        return (summary.cns * 0.45) + (summary.muscular * 0.25) + (summary.spinal * 0.30)
    }

    // ─── Costo estimado de sesión futura ─────────────────────────────────────

    fun calculatePredictedSessionDrain(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): PredictedDrain {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        var totalCns = 0.0; var totalMuscular = 0.0; var totalSpinal = 0.0
        val muscleVolumeMap = mutableMapOf<String, Int>()

        val exercises = if (session.parts.isNotEmpty())
            session.parts.flatMap { it.exercises }
        else session.exercises

        exercises.forEach { ex ->
            val dbInfo = exerciseDb[ex.exerciseDbId] ?: exerciseDb.values.find {
                it.name.equals(ex.name, ignoreCase = true)
            }
            val metrics = getDynamicAugeMetrics(ex.name, dbInfo?.equipment)
            val densityMult = getDensityMultiplierForExercise(ex.supersetId, ex.restTime ?: 90)
            val primaryMuscle = dbInfo?.involvedMuscles
                ?.find { it.role == MuscleRole.PRIMARY }
                ?.let { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
                ?: "Core"
            var accumulated = muscleVolumeMap[primaryMuscle] ?: 0

            ex.sets.forEach { s ->
                if (s.isIneffective) return@forEach
                accumulated++
                // Use a synthetic completed set from the planned set
                val syntheticSet = CompletedSet(
                    id = "",
                    weight = s.weight ?: 60.0,
                    reps = s.targetReps ?: 8,
                    rpe = s.targetRPE,
                    rir = s.targetRIR,
                    isFailure = s.isFailure || s.intensityMode == IntensityMode.FAILURE,
                )
                val drain = calculateSetBatteryDrain(
                    set = syntheticSet,
                    metrics = metrics,
                    tanks = tanks,
                    accumulatedSets = accumulated,
                    restTime = ex.restTime ?: 90,
                    densityMultiplier = densityMult,
                )
                totalCns      += drain.cnsDrainPct
                totalMuscular += drain.muscularDrainPct
                totalSpinal   += drain.spinalDrainPct
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        return PredictedDrain(
            cns      = totalCns.coerceAtMost(100.0).toInt(),
            muscular = totalMuscular.coerceAtMost(100.0).toInt(),
            spinal   = totalSpinal.coerceAtMost(100.0).toInt(),
        )
    }

    fun calculateAdjustedPredictedDrain(
        session: Session,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): PredictedDrain {
        val tanks = calculatePersonalizedBatteryTanks(settings)
        val conservationFactor = 0.85
        val decayK = 0.65
        var totalCns = 0.0; var totalMuscular = 0.0; var totalSpinal = 0.0
        var accumulatedDrain = 0.0

        val exercises = if (session.parts.isNotEmpty())
            session.parts.flatMap { it.exercises }
        else session.exercises

        exercises.forEach { ex ->
            val dbInfo = exerciseDb[ex.exerciseDbId] ?: exerciseDb.values.find {
                it.name.equals(ex.name, ignoreCase = true)
            }
            val metrics = getDynamicAugeMetrics(ex.name, dbInfo?.equipment)
            val densityMult = getDensityMultiplierForExercise(ex.supersetId, ex.restTime ?: 90)

            ex.sets.forEach { s ->
                if (s.isIneffective) return@forEach
                val syntheticSet = CompletedSet(
                    id = "",
                    weight = s.weight ?: 60.0,
                    reps = s.targetReps ?: 8,
                    rpe = s.targetRPE,
                    rir = s.targetRIR,
                    isFailure = s.isFailure || s.intensityMode == IntensityMode.FAILURE,
                )
                val rawDrain = calculateSetBatteryDrain(
                    set = syntheticSet,
                    metrics = metrics,
                    tanks = tanks,
                    accumulatedSets = 0,
                    restTime = ex.restTime ?: 90,
                    densityMultiplier = densityMult,
                )
                val diminishingFactor = 1.0 / (1.0 + decayK * accumulatedDrain)
                val adjustedMuscular = rawDrain.muscularDrainPct * conservationFactor * diminishingFactor
                val adjustedCns = rawDrain.cnsDrainPct * conservationFactor * diminishingFactor
                val adjustedSpinal = rawDrain.spinalDrainPct * conservationFactor * diminishingFactor

                totalMuscular += adjustedMuscular
                totalCns += adjustedCns
                totalSpinal += adjustedSpinal
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
            }
        }

        return PredictedDrain(
            cns = totalCns.coerceAtMost(100.0).toInt(),
            muscular = totalMuscular.coerceAtMost(100.0).toInt(),
            spinal = totalSpinal.coerceAtMost(100.0).toInt(),
        )
    }
}
