package com.example.kpkn.domain.energy

import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.calculateWeightFrom1RMAndIntensity
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.templates.SessionTemplateQualityRules
import kotlin.math.min

object TrainingEnergyEngine {

    const val METHOD_VERSION = "auge-energy-v2"

    /** Calibrated so a documented heavy leg day lands ~400–550 kcal mid. */
    private const val ACTIVE_KCAL_FACTOR = 0.0085

    private const val EPOC_RAW_FACTOR = 0.014
    private const val EPOC_HARD_SET_RATIO_COEF = 0.18
    private const val EPOC_AVG_TTC_COEF = 0.07
    private const val EPOC_CNC_RATIO_COEF = 0.08
    private const val EPOC_BASE_RATIO = 0.10

    /** Elevated metabolism during inter-set rest (kcal/min), scaled by EFC/RPE. */
    private const val REST_KCAL_PER_MIN_BASE = 3.2

    private fun resolveDbInfo(
        exerciseDbId: String?,
        exerciseId: String?,
        exerciseName: String,
    ): ExerciseMuscleInfo? {
        val resolvedId = (exerciseDbId ?: exerciseId)?.lowercase()?.let { rawId ->
            EXERCISE_ID_ALIASES[rawId] ?: rawId
        }
        return resolvedId?.let { EXERCISE_DATABASE_BY_ID[it] }
            ?: EXERCISE_DATABASE_BY_ID.values.find {
                it.name.equals(exerciseName, ignoreCase = true)
            }
    }

    /**
     * Bodyweight contribution to effective load from catalog fields
     * (equipment / pattern / force / bodyPart) — not exercise-name substrings.
     */
    fun bodyweightParticipation(dbInfo: ExerciseMuscleInfo?): Double {
        if (dbInfo == null) return 0.0
        val equipment = dbInfo.equipment?.lowercase().orEmpty()
        val pattern = dbInfo.movementPattern?.lowercase().orEmpty()
        val force = dbInfo.force?.lowercase().orEmpty()
        val bodyPart = dbInfo.bodyPart?.lowercase().orEmpty()
        val combined = "$equipment $pattern $force $bodyPart ${dbInfo.category.orEmpty().lowercase()}"

        val isBodyweightEquip = equipment.contains("peso corporal") ||
            equipment.contains("bodyweight") ||
            equipment.contains("calisten") ||
            equipment.contains("suspensión") ||
            equipment.contains("suspension") ||
            equipment.contains("anillo") ||
            equipment.contains("barra fija") ||
            equipment.contains("paralela")

        if (isBodyweightEquip) {
            return when {
                pattern.contains("dominada") || pattern.contains("pull") ||
                    force.contains("tirón") || force.contains("tiron") ||
                    pattern.contains("fondo") || pattern.contains("dip") ||
                    pattern.contains("flexion") || pattern.contains("push") -> 0.65
                else -> 0.55
            }
        }

        val base = when {
            pattern.contains("sentadilla") || pattern.contains("squat") ||
                force.contains("sentadilla") || pattern.contains("zancada") ||
                pattern.contains("lunge") || pattern.contains("step") -> 0.30
            pattern.contains("bisagra") || pattern.contains("hinge") ||
                force.contains("bisagra") || pattern.contains("peso muerto") ||
                pattern.contains("deadlift") || pattern.contains("rdl") -> 0.20
            pattern.contains("prensa") ||
                (pattern.contains("press") && bodyPart.contains("lower")) -> 0.05
            force.contains("empuje") || force.contains("press") ||
                pattern.contains("press") || pattern.contains("remo") ||
                pattern.contains("row") -> 0.08
            SessionTemplateQualityRules.isIsolation(dbInfo) -> 0.0
            SessionTemplateQualityRules.isCompound(dbInfo) && bodyPart.contains("lower") -> 0.15
            SessionTemplateQualityRules.isCompound(dbInfo) -> 0.08
            else -> 0.0
        }
        // Mild bump for free-weight compounds that unload partially onto the body.
        return if (
            base == 0.0 &&
            combined.contains("barra") &&
            SessionTemplateQualityRules.isCompound(dbInfo)
        ) {
            0.05
        } else {
            base
        }
    }

    /**
     * Mono vs multi / isolation vs compound recruitment mass factor from catalog.
     */
    fun recruitmentFactor(dbInfo: ExerciseMuscleInfo?): Double {
        if (dbInfo == null) return 1.0
        val primaryCount = dbInfo.involvedMuscles.count { it.role == MuscleRole.PRIMARY }
        val coreBoost = when (dbInfo.coreInvolvement?.lowercase()) {
            "high", "alto", "alta" -> 1.08
            "medium", "medio", "media" -> 1.03
            else -> 1.0
        }
        val type = dbInfo.type?.lowercase().orEmpty()
        val base = when {
            type.contains("básic") || type.contains("basic") || type.contains("compuest") ||
                type.contains("compound") || primaryCount >= 2 -> 1.35
            SessionTemplateQualityRules.isCompound(dbInfo) -> 1.20
            type.contains("accesor") -> 1.08
            SessionTemplateQualityRules.isIsolation(dbInfo) ||
                type.contains("aislam") || type.contains("isolation") -> 0.78
            primaryCount == 1 -> 0.88
            else -> 1.0
        }
        return (base * coreBoost).coerceIn(0.65, 1.55)
    }

    private fun estimateActiveSetKcal(
        effectiveLoadKg: Double,
        effectiveReps: Double,
        efc: Double,
        recruitment: Double,
        rpeMultiplier: Double,
        densityMultiplier: Double,
    ): Double {
        val efcFactor = (efc / 2.5).coerceIn(0.35, 2.4)
        return effectiveLoadKg *
            effectiveReps *
            efcFactor *
            recruitment *
            rpeMultiplier *
            densityMultiplier *
            ACTIVE_KCAL_FACTOR
    }

    private fun estimateRestOverheadKcal(
        restSeconds: Int,
        efc: Double,
        rpeMultiplier: Double,
    ): Double {
        if (restSeconds <= 0) return 0.0
        val efcScale = (efc / 2.5).coerceIn(0.5, 1.8)
        val effortScale = (0.55 + 0.45 * (rpeMultiplier / 1.5).coerceIn(0.5, 1.4))
        return (restSeconds / 60.0) * REST_KCAL_PER_MIN_BASE * efcScale * effortScale
    }

    private data class SetEnergyScore(
        val activeKcal: Double,
        val restKcal: Double,
        val rpe: Double,
        val ttc: Double,
        val cnc: Double,
        val isHard: Boolean,
    )

    private fun classifyHardSet(rpe: Double, isFailure: Boolean, isFailedSet: Boolean): Boolean =
        rpe >= 9.5 || isFailure || isFailedSet

    private fun rpeForPlannedSet(set: ExerciseSet): Double = when {
        set.isFailure || set.intensityMode == IntensityMode.FAILURE -> 10.8
        set.targetRPE != null -> set.targetRPE
        set.targetRIR != null -> (10.0 - set.targetRIR).toDouble()
        else -> 7.0
    }

    /**
     * Resolve planned external load without inventing magic kilograms.
     * Order: explicit weight → %1RM × capacity → 1RM+intensity → suggested load.
     */
    fun resolvePlannedExternalLoadKg(
        exercise: Exercise,
        set: ExerciseSet,
        weightUnit: WeightUnit,
    ): ResolvedPlannedLoad {
        set.weight?.takeIf { it > 0.0 }?.let { raw ->
            val kg = if (weightUnit == WeightUnit.LBS) raw / 2.2046226218 else raw
            return ResolvedPlannedLoad(kg, ResolvedLoadSource.EXPLICIT_WEIGHT)
        }

        val reference = resolveReferenceCapacity(exercise)
        if (reference != null && reference > 0.0) {
            val refKg = if (weightUnit == WeightUnit.LBS) reference / 2.2046226218 else reference
            set.targetPercentageRM?.takeIf { it > 0.0 }?.let { pct ->
                return ResolvedPlannedLoad(
                    externalLoadKg = refKg * pct / 100.0,
                    source = ResolvedLoadSource.PERCENT_1RM,
                )
            }
            calculateWeightFrom1RMAndIntensity(reference, set)?.takeIf { it > 0.0 }?.let { raw ->
                val kg = if (weightUnit == WeightUnit.LBS) raw / 2.2046226218 else raw
                return ResolvedPlannedLoad(kg, ResolvedLoadSource.ONE_RM_INTENSITY)
            }
            calculateSuggestedLoad(exercise, set)?.takeIf { it > 0.0 }?.let { raw ->
                val kg = if (weightUnit == WeightUnit.LBS) raw / 2.2046226218 else raw
                return ResolvedPlannedLoad(kg, ResolvedLoadSource.SUGGESTED_FROM_1RM)
            }
        }

        val loadMode = set.loadModeV2
        if (loadMode == LoadModeV2.BODYWEIGHT) {
            return ResolvedPlannedLoad(0.0, ResolvedLoadSource.BODYWEIGHT_ONLY)
        }

        return ResolvedPlannedLoad(null, ResolvedLoadSource.MISSING)
    }

    enum class ResolvedLoadSource {
        EXPLICIT_WEIGHT,
        PERCENT_1RM,
        ONE_RM_INTENSITY,
        SUGGESTED_FROM_1RM,
        BODYWEIGHT_ONLY,
        MISSING,
    }

    data class ResolvedPlannedLoad(
        val externalLoadKg: Double?,
        val source: ResolvedLoadSource,
    )

    private fun buildEnergyConfidence(
        hasBodyWeight: Boolean,
        metricsExerciseRatio: Double,
        realRpeSetRatio: Double,
        loadCoverageRatio: Double,
    ): EnergyConfidence {
        var score = 0
        if (hasBodyWeight) score++
        if (metricsExerciseRatio >= 0.60) score++
        if (realRpeSetRatio >= 0.60) score++
        if (loadCoverageRatio >= 0.70) score++
        return when {
            score >= 4 && metricsExerciseRatio >= 0.80 && realRpeSetRatio >= 0.80 -> EnergyConfidence.HIGH
            score >= 2 -> EnergyConfidence.MEDIUM
            else -> EnergyConfidence.LOW
        }
    }

    private fun calorieRange(mid: Int, confidence: EnergyConfidence): CalorieRange {
        val rangePct = when (confidence) {
            EnergyConfidence.HIGH -> 0.15
            EnergyConfidence.MEDIUM -> 0.25
            EnergyConfidence.LOW -> 0.35
        }
        return CalorieRange(
            mid = mid,
            low = (mid * (1.0 - rangePct)).toInt(),
            high = (mid * (1.0 + rangePct)).toInt(),
        )
    }

    private fun computeSessionEnergyInternally(
        plannedSets: List<Triple<String, Exercise, ExerciseSet>>?,
        completedExercises: List<CompletedExercise>?,
        userBodyWeightKg: Double?,
        postExerciseFeedback: Map<String, PostExerciseFeedback>?,
        isPlanned: Boolean,
        weightUnit: WeightUnit = WeightUnit.KG,
    ): SessionEnergySummary {
        val setScores = mutableListOf<SetEnergyScore>()
        val contributions = mutableListOf<ExerciseEnergyContribution>()
        val notes = mutableListOf<String>()
        val userWeight = when {
            userBodyWeightKg == null -> null
            weightUnit == WeightUnit.LBS -> userBodyWeightKg / 2.2046226218
            else -> userBodyWeightKg
        }
        if (userWeight == null) {
            notes.add("Peso corporal no disponible — confianza baja")
        }

        var exerciseCount = 0
        var exercisesWithMetrics = 0
        var totalSetCount = 0
        var setsWithRealRpe = 0
        var setsWithResolvableLoad = 0

        var rawTotalActive = 0.0
        var rawTotalRest = 0.0
        var rawTotalEpoc = 0.0

        if (completedExercises != null) {
            for (compEx in completedExercises) {
                val dbInfo = resolveDbInfo(
                    exerciseDbId = compEx.exerciseDbId,
                    exerciseId = compEx.exerciseId,
                    exerciseName = compEx.exerciseName,
                )
                val efc = dbInfo?.efc ?: 2.5
                val ttc = dbInfo?.ttc ?: 1.5
                val cnc = dbInfo?.cnc ?: 2.5
                val recruitment = recruitmentFactor(dbInfo)
                if (dbInfo?.efc != null && dbInfo.cnc != null && dbInfo.ssc != null) {
                    exercisesWithMetrics++
                }
                exerciseCount++
                val bodyweightPart = bodyweightParticipation(dbInfo)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId = compEx.supersetId,
                    restTime = compEx.supersetRestBetween ?: compEx.restTime,
                    supersetExerciseCount = compEx.supersetExerciseCount,
                    supersetRounds = compEx.supersetRounds,
                    supersetRestAfter = compEx.supersetRestAfter,
                )
                val restSeconds = compEx.restTime.coerceIn(0, 600)

                val effSets = compEx.sets.filter { !it.skipped }
                val postFeedback = postExerciseFeedback?.entries?.firstOrNull {
                    it.value.exerciseId == compEx.exerciseId ||
                        it.value.canonicalExerciseId == compEx.canonicalExerciseId
                }?.value

                var exerciseActiveKcal = 0.0
                var exerciseRestKcal = 0.0
                var exerciseHardCount = 0
                val exerciseSetScores = mutableListOf<SetEnergyScore>()

                for (set in effSets) {
                    val effectiveRpe = AugeFatigueEngine.getEffectiveRPE(set)
                    totalSetCount++
                    val hasRealRpe = set.rpe != null || set.rir != null || set.actualIntensityValue != null
                        || set.isFailure || set.isFailedSet
                    if (hasRealRpe) setsWithRealRpe++
                    if (effectiveRpe < 5.0) continue

                    val rpeMult = AugeFatigueEngine.calculateRpeMultiplier(effectiveRpe)
                    val effectiveLoadRaw = set.homologatedResultV3?.augeEquivalentLoad
                        ?: set.weight
                    val effectiveLoad = if (weightUnit == WeightUnit.LBS) {
                        effectiveLoadRaw / 2.2046226218
                    } else {
                        effectiveLoadRaw
                    }
                    val effectiveReps = set.effectiveRepEquivalent()
                    val loadForKcal = effectiveLoad + (userWeight ?: 0.0) * bodyweightPart
                    if (loadForKcal > 0.0) setsWithResolvableLoad++

                    val activeKcal = if (loadForKcal > 0.0) {
                        estimateActiveSetKcal(
                            effectiveLoadKg = loadForKcal,
                            effectiveReps = effectiveReps,
                            efc = efc,
                            recruitment = recruitment,
                            rpeMultiplier = rpeMult,
                            densityMultiplier = densityMult,
                        )
                    } else {
                        0.0
                    }
                    val restKcal = estimateRestOverheadKcal(restSeconds, efc, rpeMult)

                    val isHard = classifyHardSet(effectiveRpe, set.isFailure, set.isFailedSet)
                    if (isHard) exerciseHardCount++

                    val score = SetEnergyScore(
                        activeKcal = activeKcal,
                        restKcal = restKcal,
                        rpe = effectiveRpe,
                        ttc = ttc,
                        cnc = cnc,
                        isHard = isHard,
                    )
                    exerciseSetScores.add(score)
                    setScores.add(score)
                    exerciseActiveKcal += activeKcal
                    exerciseRestKcal += restKcal
                }

                val plannedTotalSets = compEx.sets.size
                val exerciseEpocRaw = exerciseSetScores.sumOf { score ->
                    score.activeKcal *
                        (score.ttc / 3.0).coerceIn(0.2, 2.0) *
                        AugeFatigueEngine.calculateRpeMultiplier(score.rpe) *
                        EPOC_RAW_FACTOR
                }
                val hardSetRatio = if (exerciseSetScores.isNotEmpty()) {
                    exerciseHardCount.toDouble() / exerciseSetScores.size
                } else {
                    0.0
                }
                val avgTtcNorm = (ttc / 5.0).coerceIn(0.0, 1.0)
                val avgCncNorm = ((cnc - 1.0) / 4.0).coerceIn(0.0, 1.0)
                val epocMaxRatio = (
                    EPOC_BASE_RATIO +
                        EPOC_HARD_SET_RATIO_COEF * hardSetRatio +
                        EPOC_AVG_TTC_COEF * avgTtcNorm +
                        EPOC_CNC_RATIO_COEF * avgCncNorm
                    ).coerceIn(0.12, 0.40)

                val techniqueRecoveryMultiplier = if (postFeedback != null) {
                    AugeFatigueEngine.calculateTechniquePenalty(
                        technicalQuality = postFeedback.technicalQuality.coerceIn(1, 5),
                        effortSignal = exerciseSetScores.map { it.rpe }.average().takeIf { !it.isNaN() } ?: 7.0,
                    )
                } else {
                    1.0
                }

                val exerciseWorkBase = exerciseActiveKcal + exerciseRestKcal
                val exerciseEpoc = min(
                    exerciseEpocRaw * techniqueRecoveryMultiplier,
                    exerciseActiveKcal * epocMaxRatio,
                )

                val exerciseTotal = (exerciseWorkBase + exerciseEpoc).toInt()

                rawTotalActive += exerciseActiveKcal
                rawTotalRest += exerciseRestKcal
                rawTotalEpoc += exerciseEpoc

                contributions.add(
                    ExerciseEnergyContribution(
                        exerciseId = compEx.exerciseId,
                        exerciseDbId = compEx.exerciseDbId ?: compEx.exerciseId,
                        exerciseName = compEx.exerciseName,
                        activeKcal = (exerciseActiveKcal + exerciseRestKcal).toInt(),
                        epocKcal = exerciseEpoc.toInt(),
                        totalKcal = exerciseTotal,
                        percentageOfSession = 0.0,
                        completedSets = effSets.size,
                        totalSets = plannedTotalSets,
                    ),
                )
            }
        } else if (plannedSets != null) {
            val missingLoadExercises = linkedSetOf<String>()
            val estimatedLoadExercises = linkedSetOf<String>()

            data class PlannedAgg(
                val exerciseName: String,
                var active: Double = 0.0,
                var rest: Double = 0.0,
                var sets: Int = 0,
                var hard: Int = 0,
                var efc: Double = 2.5,
                var ttc: Double = 1.5,
                var cnc: Double = 2.5,
                var exerciseId: String = "",
                var exerciseDbId: String? = null,
                val scores: MutableList<SetEnergyScore> = mutableListOf(),
            )
            val byExercise = linkedMapOf<String, PlannedAgg>()

            for ((exerciseName, exercise, plannedSet) in plannedSets) {
                val dbInfo = resolveDbInfo(
                    exerciseDbId = exercise.exerciseDbId,
                    exerciseId = exercise.exerciseId,
                    exerciseName = exerciseName,
                )
                val efc = dbInfo?.efc ?: 2.5
                val ttc = dbInfo?.ttc ?: 1.5
                val cnc = dbInfo?.cnc ?: 2.5
                val recruitment = recruitmentFactor(dbInfo)
                val bodyweightPart = bodyweightParticipation(dbInfo)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId = exercise.supersetGroupRefOrLegacyId(),
                    restTime = exercise.restTime ?: 90,
                )
                val restSeconds = (exercise.restTime ?: 90).coerceIn(0, 600)

                val plannedRpe = rpeForPlannedSet(plannedSet)
                val hasRealRpe = plannedSet.targetRPE != null || plannedSet.targetRIR != null
                    || plannedSet.isFailure || plannedSet.intensityMode == IntensityMode.FAILURE
                if (hasRealRpe) setsWithRealRpe++
                totalSetCount++

                if (plannedRpe < 5.0) continue

                val rpeMult = AugeFatigueEngine.calculateRpeMultiplier(plannedRpe)
                val resolved = resolvePlannedExternalLoadKg(exercise, plannedSet, weightUnit)
                val plannedReps = plannedSet.targetReps?.toDouble()
                    ?: plannedSet.targetDuration?.toDouble()?.div(3.0)
                    ?: 0.0

                when (resolved.source) {
                    ResolvedLoadSource.MISSING -> missingLoadExercises.add(exerciseName)
                    ResolvedLoadSource.PERCENT_1RM,
                    ResolvedLoadSource.ONE_RM_INTENSITY,
                    ResolvedLoadSource.SUGGESTED_FROM_1RM,
                    -> estimatedLoadExercises.add(exerciseName)
                    else -> Unit
                }

                val external = resolved.externalLoadKg
                val loadForKcal = if (external != null) {
                    external + (userWeight ?: 0.0) * bodyweightPart
                } else if (bodyweightPart > 0.0 && userWeight != null) {
                    userWeight * bodyweightPart
                } else {
                    0.0
                }

                if (loadForKcal > 0.0) setsWithResolvableLoad++

                val activeKcal = if (loadForKcal > 0.0 && plannedReps > 0.0) {
                    estimateActiveSetKcal(
                        effectiveLoadKg = loadForKcal,
                        effectiveReps = plannedReps,
                        efc = efc,
                        recruitment = recruitment,
                        rpeMultiplier = rpeMult,
                        densityMultiplier = densityMult,
                    )
                } else {
                    0.0
                }
                val restKcal = if (activeKcal > 0.0 || loadForKcal > 0.0) {
                    estimateRestOverheadKcal(restSeconds, efc, rpeMult)
                } else {
                    0.0
                }

                val isHard = classifyHardSet(
                    plannedRpe,
                    plannedSet.isFailure || plannedSet.intensityMode == IntensityMode.FAILURE,
                    false,
                )
                val score = SetEnergyScore(
                    activeKcal = activeKcal,
                    restKcal = restKcal,
                    rpe = plannedRpe,
                    ttc = ttc,
                    cnc = cnc,
                    isHard = isHard,
                )
                setScores.add(score)

                val key = exercise.id.ifBlank { exerciseName }
                val isNewExercise = !byExercise.containsKey(key)
                val agg = byExercise.getOrPut(key) {
                    PlannedAgg(
                        exerciseName = exerciseName,
                        efc = efc,
                        ttc = ttc,
                        cnc = cnc,
                        exerciseId = exercise.id,
                        exerciseDbId = exercise.exerciseDbId,
                    )
                }
                if (isNewExercise) {
                    exerciseCount++
                    if (dbInfo?.efc != null && dbInfo.cnc != null && dbInfo.ssc != null) {
                        exercisesWithMetrics++
                    }
                }
                agg.active += activeKcal
                agg.rest += restKcal
                agg.sets += 1
                if (isHard) agg.hard += 1
                agg.scores.add(score)
            }

            for (agg in byExercise.values) {
                val hardRatio = if (agg.sets > 0) agg.hard.toDouble() / agg.sets else 0.0
                val avgTtcNorm = (agg.ttc / 5.0).coerceIn(0.0, 1.0)
                val avgCncNorm = ((agg.cnc - 1.0) / 4.0).coerceIn(0.0, 1.0)
                val epocMaxRatio = (
                    EPOC_BASE_RATIO +
                        EPOC_HARD_SET_RATIO_COEF * hardRatio +
                        EPOC_AVG_TTC_COEF * avgTtcNorm +
                        EPOC_CNC_RATIO_COEF * avgCncNorm
                    ).coerceIn(0.12, 0.40)

                val epocRaw = agg.scores.sumOf { score ->
                    score.activeKcal *
                        (score.ttc / 3.0).coerceIn(0.2, 2.0) *
                        AugeFatigueEngine.calculateRpeMultiplier(score.rpe) *
                        EPOC_RAW_FACTOR
                }
                val exerciseEpoc = min(epocRaw, agg.active * epocMaxRatio)
                val exerciseTotal = (agg.active + agg.rest + exerciseEpoc).toInt()

                rawTotalActive += agg.active
                rawTotalRest += agg.rest
                rawTotalEpoc += exerciseEpoc

                contributions.add(
                    ExerciseEnergyContribution(
                        exerciseId = agg.exerciseId,
                        exerciseDbId = agg.exerciseDbId,
                        exerciseName = agg.exerciseName,
                        activeKcal = (agg.active + agg.rest).toInt(),
                        epocKcal = exerciseEpoc.toInt(),
                        totalKcal = exerciseTotal,
                        percentageOfSession = 0.0,
                        completedSets = agg.sets,
                        totalSets = agg.sets,
                    ),
                )
            }

            if (missingLoadExercises.isNotEmpty()) {
                val listed = missingLoadExercises.take(5).joinToString(", ")
                val suffix = if (missingLoadExercises.size > 5) "…" else ""
                notes.add(
                    "Sin carga ni 1RM para: $listed$suffix — completa pesos o 1RM para estimar kcal",
                )
            }
            if (estimatedLoadExercises.isNotEmpty()) {
                val listed = estimatedLoadExercises.take(4).joinToString(", ")
                val suffix = if (estimatedLoadExercises.size > 4) "…" else ""
                notes.add("Carga estimada desde 1RM/intensidad en: $listed$suffix")
            }
        }

        val totalActiveWithRest = (rawTotalActive + rawTotalRest).toInt()
        val totalEpoc = rawTotalEpoc.toInt()
        val totalMid = totalActiveWithRest + totalEpoc

        val metricsExerciseRatio = if (exerciseCount > 0) exercisesWithMetrics.toDouble() / exerciseCount else 0.0
        val realRpeSetRatio = if (totalSetCount > 0) setsWithRealRpe.toDouble() / totalSetCount else 0.0
        val loadCoverageRatio = if (totalSetCount > 0) setsWithResolvableLoad.toDouble() / totalSetCount else 0.0
        val confidence = if (totalSetCount > 0 && (loadCoverageRatio < 0.30 || totalMid == 0)) {
            EnergyConfidence.LOW
        } else {
            buildEnergyConfidence(
                hasBodyWeight = userWeight != null,
                metricsExerciseRatio = metricsExerciseRatio,
                realRpeSetRatio = realRpeSetRatio,
                loadCoverageRatio = loadCoverageRatio,
            )
        }

        if (totalMid == 0 && totalSetCount > 0) {
            notes.add(
                "No se pudo estimar gasto: falta peso planificado o 1RM en los ejercicios de la sesión",
            )
        }

        val totalContributions = contributions.fold(0) { acc: Int, c: ExerciseEnergyContribution -> acc + c.totalKcal }
        val updatedContributions = if (totalContributions > 0) {
            contributions.sortedByDescending { it.totalKcal }.map {
                it.copy(percentageOfSession = (it.totalKcal.toDouble() / totalContributions * 100.0))
            }
        } else {
            contributions
        }

        val maxTotal = setScores.size
        val completedTotal = if (completedExercises != null) {
            completedExercises.sumOf { ex -> ex.sets.count { !it.skipped } }
        } else {
            setScores.size
        }

        return SessionEnergySummary(
            activeKcal = calorieRange(totalActiveWithRest, confidence),
            epocKcal = calorieRange(totalEpoc, confidence),
            totalKcal = calorieRange(totalMid, confidence),
            projectedTotalKcal = if (completedTotal < maxTotal && totalMid > 0) {
                ((totalMid.toDouble() / completedTotal.coerceAtLeast(1)) * maxTotal).toInt()
            } else {
                null
            },
            confidence = confidence,
            source = if (isPlanned) EnergyEstimateSource.PLANNED else EnergyEstimateSource.LIVE,
            methodVersion = METHOD_VERSION,
            exerciseContributions = updatedContributions,
            notes = notes.distinct(),
        )
    }

    fun estimatePlannedSession(
        session: Session,
        settings: Settings = Settings(),
    ): SessionEnergySummary {
        val exercises = session.exercises + session.parts.flatMap { it.exercises }
        val plannedSets = exercises.flatMap { ex ->
            ex.sets.map { set -> Triple(ex.name, ex, set) }
        }
        val bodyWeight = settings.userVitals.weight

        return computeSessionEnergyInternally(
            plannedSets = plannedSets,
            completedExercises = null,
            userBodyWeightKg = bodyWeight,
            postExerciseFeedback = null,
            isPlanned = true,
            weightUnit = settings.weightUnit,
        ).copy(source = EnergyEstimateSource.PLANNED)
    }

    fun estimateLiveSession(
        completedExercises: List<CompletedExercise>,
        settings: Settings = Settings(),
    ): SessionEnergySummary {
        val bodyWeight = settings.userVitals.weight

        return computeSessionEnergyInternally(
            plannedSets = null,
            completedExercises = completedExercises,
            userBodyWeightKg = bodyWeight,
            postExerciseFeedback = null,
            isPlanned = false,
            weightUnit = settings.weightUnit,
        ).copy(source = EnergyEstimateSource.LIVE)
    }

    fun estimateCompletedSession(
        completedExercises: List<CompletedExercise>,
        settings: Settings = Settings(),
        postExerciseFeedback: Map<String, PostExerciseFeedback> = emptyMap(),
    ): SessionEnergySummary {
        val bodyWeight = settings.userVitals.weight

        return computeSessionEnergyInternally(
            plannedSets = null,
            completedExercises = completedExercises,
            userBodyWeightKg = bodyWeight,
            postExerciseFeedback = postExerciseFeedback,
            isPlanned = false,
            weightUnit = settings.weightUnit,
        ).copy(source = EnergyEstimateSource.FINAL)
    }

    fun calculateDailyEnergyBalance(
        consumedKcal: Int,
        trainingBurnKcal: Int,
        targetKcal: Int,
    ): DailyEnergyBalance {
        val netKcal = consumedKcal - trainingBurnKcal
        val deltaFromTarget = netKcal - targetKcal
        val status = when {
            deltaFromTarget < -150 -> DailyEnergyStatus.DEFICIT
            deltaFromTarget > 150 -> DailyEnergyStatus.SURPLUS
            else -> DailyEnergyStatus.MAINTENANCE
        }
        return DailyEnergyBalance(
            consumedKcal = consumedKcal,
            trainingBurnKcal = trainingBurnKcal,
            netKcal = netKcal,
            targetKcal = targetKcal,
            deltaFromTarget = deltaFromTarget,
            status = status,
        )
    }
}
