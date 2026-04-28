package com.example.kpkn.domain.energy

import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.screens.workout.PostExerciseFeedback
import kotlin.math.min

object TrainingEnergyEngine {

    private const val ACTIVE_KCAL_FACTOR = 0.008
    private const val EPOC_RAW_FACTOR = 0.012
    private const val EPOC_HARD_SET_RATIO_COEF = 0.18
    private const val EPOC_AVG_TTC_COEF = 0.07
    private const val EPOC_BASE_RATIO = 0.10

    private val BODYWIGHT_EXERCISE_PATTERNS = mapOf(
        "pullup" to 0.65, "chinup" to 0.65, "dominada" to 0.65, "dominadas" to 0.65,
        "pull-up" to 0.65, "chin-up" to 0.65,
        "dip" to 0.65, "dips" to 0.65, "fondo" to 0.65, "fondos" to 0.65,
        "pushup" to 0.65, "push-up" to 0.65, "push up" to 0.65,
        "flexion" to 0.65, "flexiones" to 0.65, "lagartija" to 0.65,
        "sentadilla" to 0.35, "squat" to 0.35,
        "zancada" to 0.35, "lunge" to 0.35, "lunges" to 0.35,
        "step-up" to 0.35, "step up" to 0.35,
        "peso muerto" to 0.20, "deadlift" to 0.20,
        "press banca" to 0.08, "bench press" to 0.08,
        "remo" to 0.08, "row" to 0.08,
        "press militar" to 0.08, "overhead press" to 0.08, "ohp" to 0.08,
    )

    private fun bodyweightParticipation(exerciseName: String, equipment: String?): Double {
        val lower = exerciseName.lowercase().trim()
        return BODYWIGHT_EXERCISE_PATTERNS.entries.firstOrNull { (key, _) ->
            lower.contains(key)
        }?.value ?: 0.0
    }

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

    private fun estimateActiveSetKcal(
        effectiveLoadKg: Double,
        effectiveReps: Double,
        efc: Double,
        rpeMultiplier: Double,
        densityMultiplier: Double,
    ): Double {
        val efcFactor = (efc / 2.5).coerceIn(0.35, 2.4)
        val techniqueActiveFactor = 1.0
        return effectiveLoadKg *
            effectiveReps *
            efcFactor *
            rpeMultiplier *
            densityMultiplier *
            techniqueActiveFactor *
            ACTIVE_KCAL_FACTOR
    }

    private data class SetEnergyScore(
        val activeKcal: Double,
        val rpe: Double,
        val ttc: Double,
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

    private fun buildEnergyConfidence(
        hasBodyWeight: Boolean,
        metricsExerciseRatio: Double,
        realRpeSetRatio: Double,
    ): EnergyConfidence {
        var score = 0
        if (hasBodyWeight) score++
        if (metricsExerciseRatio >= 0.60) score++
        if (realRpeSetRatio >= 0.60) score++
        return when {
            score >= 3 && metricsExerciseRatio >= 0.80 && realRpeSetRatio >= 0.80 -> EnergyConfidence.HIGH
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
    ): SessionEnergySummary {
        val setScores = mutableListOf<SetEnergyScore>()
        val contributions = mutableListOf<ExerciseEnergyContribution>()
        val notes = mutableListOf<String>()
        val userWeight = userBodyWeightKg
        if (userWeight == null) {
            notes.add("Peso corporal no disponible — confianza baja")
        }

        var exerciseCount = 0
        var exercisesWithMetrics = 0
        var totalSetCount = 0
        var setsWithRealRpe = 0

        var rawTotalActive = 0.0
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
                if (dbInfo?.efc != null && dbInfo?.cnc != null && dbInfo?.ssc != null) {
                    exercisesWithMetrics++
                }
                exerciseCount++
                val bodyweightPart = bodyweightParticipation(compEx.exerciseName, dbInfo?.equipment)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId = compEx.supersetId,
                    restTime = compEx.restTime,
                )

                val effSets = compEx.sets.filter { !it.skipped }
                val postFeedback = postExerciseFeedback?.entries?.firstOrNull {
                    it.value.exerciseId == compEx.exerciseId ||
                        it.value.canonicalExerciseId == compEx.canonicalExerciseId
                }?.value

                var exerciseActiveKcal = 0.0
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
                    val effectiveLoad = set.homologatedResultV3?.augeEquivalentLoad
                        ?: set.weight
                    val effectiveReps = set.effectiveRepEquivalent()

                    val loadForKcal = effectiveLoad + (userWeight ?: 0.0) * bodyweightPart

                    val activeKcal = if (loadForKcal > 0.0) {
                        estimateActiveSetKcal(
                            effectiveLoadKg = loadForKcal,
                            effectiveReps = effectiveReps,
                            efc = efc,
                            rpeMultiplier = rpeMult,
                            densityMultiplier = densityMult,
                        )
                    } else {
                        0.0
                    }

                    val isHard = classifyHardSet(effectiveRpe, set.isFailure, set.isFailedSet)
                    if (isHard) exerciseHardCount++

                    val score = SetEnergyScore(
                        activeKcal = activeKcal,
                        rpe = effectiveRpe,
                        ttc = ttc,
                        isHard = isHard,
                    )
                    exerciseSetScores.add(score)
                    setScores.add(score)
                    exerciseActiveKcal += activeKcal
                }

                val plannedTotalSets = compEx.sets.size
                val sumSetEnergyScore = exerciseSetScores.sumOf { it.activeKcal }
                val exerciseEpocRaw = exerciseSetScores.sumOf { score ->
                    score.activeKcal * (score.ttc / 3.0).coerceIn(0.2, 2.0) * AugeFatigueEngine.calculateRpeMultiplier(score.rpe) * EPOC_RAW_FACTOR
                }
                val hardSetRatio = if (exerciseSetScores.isNotEmpty()) {
                    exerciseHardCount.toDouble() / exerciseSetScores.size
                } else 0.0
                val avgTtcNorm = (ttc / 5.0).coerceIn(0.0, 1.0)
                val epocMaxRatio = (EPOC_BASE_RATIO + EPOC_HARD_SET_RATIO_COEF * hardSetRatio + EPOC_AVG_TTC_COEF * avgTtcNorm)
                    .coerceIn(0.12, 0.35)

                val techniqueRecoveryMultiplier = if (postFeedback != null) {
                    AugeFatigueEngine.calculateTechniquePenalty(
                        technicalQuality = postFeedback.technicalQuality.coerceIn(1, 5),
                        effortSignal = exerciseSetScores.map { it.rpe }.average().takeIf { !it.isNaN() } ?: 7.0,
                    )
                } else {
                    1.0
                }

                val exerciseEpoc = min(
                    exerciseEpocRaw * techniqueRecoveryMultiplier,
                    exerciseActiveKcal * epocMaxRatio,
                )

                val exerciseTotal = (exerciseActiveKcal + exerciseEpoc).toInt()

                rawTotalActive += exerciseActiveKcal
                rawTotalEpoc += exerciseEpoc

                contributions.add(
                    ExerciseEnergyContribution(
                        exerciseId = compEx.exerciseId,
                        exerciseDbId = compEx.exerciseDbId ?: compEx.exerciseId,
                        exerciseName = compEx.exerciseName,
                        activeKcal = exerciseActiveKcal.toInt(),
                        epocKcal = exerciseEpoc.toInt(),
                        totalKcal = exerciseTotal,
                        percentageOfSession = 0.0,
                        completedSets = effSets.size,
                        totalSets = plannedTotalSets,
                    )
                )
            }
        } else if (plannedSets != null) {
            for ((exerciseName, exercise, plannedSet) in plannedSets) {
                val dbInfo = resolveDbInfo(
                    exerciseDbId = exercise.exerciseDbId,
                    exerciseId = exercise.exerciseId,
                    exerciseName = exerciseName,
                )
                val efc = dbInfo?.efc ?: 2.5
                val ttc = dbInfo?.ttc ?: 1.5
                if (dbInfo?.efc != null && dbInfo?.cnc != null && dbInfo?.ssc != null) {
                    exercisesWithMetrics++
                }
                exerciseCount++
                val bodyweightPart = bodyweightParticipation(exerciseName, dbInfo?.equipment)
                val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId = exercise.supersetId,
                    restTime = exercise.restTime ?: 90,
                )

                val plannedRpe = rpeForPlannedSet(plannedSet)
                val hasRealRpe = plannedSet.targetRPE != null || plannedSet.targetRIR != null
                    || plannedSet.isFailure || plannedSet.intensityMode == IntensityMode.FAILURE
                if (hasRealRpe) setsWithRealRpe++
                totalSetCount++
                if (plannedRpe >= 5.0) {
                    val rpeMult = AugeFatigueEngine.calculateRpeMultiplier(plannedRpe)
                    val plannedWeight = plannedSet.weight ?: 0.0
                    val plannedReps = plannedSet.targetReps?.toDouble() ?: 0.0
                    if (plannedSet.weight == null) {
                        notes.add("Falta peso planificado para \"$exerciseName\" — kcal omitidas")
                    }

                    var loadForKcal = plannedWeight + (userWeight ?: 0.0) * bodyweightPart

                    val activeKcal = if (loadForKcal > 0.0 && plannedRpe >= 5.0) {
                        estimateActiveSetKcal(
                            effectiveLoadKg = loadForKcal,
                            effectiveReps = plannedReps,
                            efc = efc,
                            rpeMultiplier = rpeMult,
                            densityMultiplier = densityMult,
                        )
                    } else {
                        0.0
                    }

                    val isHard = classifyHardSet(plannedRpe, plannedSet.isFailure || plannedSet.intensityMode == IntensityMode.FAILURE, false)

                    val score = SetEnergyScore(
                        activeKcal = activeKcal,
                        rpe = plannedRpe,
                        ttc = ttc,
                        isHard = isHard,
                    )
                    setScores.add(score)

                    val hardRatio = if (setScores.isNotEmpty()) {
                        setScores.count { it.isHard }.toDouble() / setScores.size
                    } else 0.0
                    val avgTtc = if (setScores.isNotEmpty()) {
                        setScores.map { it.ttc }.average()
                    } else ttc
                    val avgTtcNorm = (avgTtc / 5.0).coerceIn(0.0, 1.0)
                    val epocMaxRatio = (EPOC_BASE_RATIO + EPOC_HARD_SET_RATIO_COEF * hardRatio + EPOC_AVG_TTC_COEF * avgTtcNorm)
                        .coerceIn(0.12, 0.35)

                    val epocRaw = score.activeKcal * (score.ttc / 3.0).coerceIn(0.2, 2.0) * rpeMult * EPOC_RAW_FACTOR
                    val exerciseEpoc = min(epocRaw, activeKcal * epocMaxRatio)
                    val exerciseTotal = (activeKcal + exerciseEpoc).toInt()

                    rawTotalActive += activeKcal
                    rawTotalEpoc += exerciseEpoc

                    contributions.add(
                        ExerciseEnergyContribution(
                            exerciseId = exercise.id,
                            exerciseDbId = exercise.exerciseDbId,
                            exerciseName = exerciseName,
                            activeKcal = activeKcal.toInt(),
                            epocKcal = exerciseEpoc.toInt(),
                            totalKcal = exerciseTotal,
                            percentageOfSession = 0.0,
                            completedSets = 1,
                            totalSets = 1,
                        )
                    )
                }
            }
        }

        val totalActive = rawTotalActive.toInt()
        val totalEpoc = rawTotalEpoc.toInt()
        val totalMid = totalActive + totalEpoc

        val metricsExerciseRatio = if (exerciseCount > 0) exercisesWithMetrics.toDouble() / exerciseCount else 0.0
        val realRpeSetRatio = if (totalSetCount > 0) setsWithRealRpe.toDouble() / totalSetCount else 0.0
        val confidence = buildEnergyConfidence(
            hasBodyWeight = userWeight != null,
            metricsExerciseRatio = metricsExerciseRatio,
            realRpeSetRatio = realRpeSetRatio,
        )

        val totalContributions = contributions.fold(0) { acc: Int, c: ExerciseEnergyContribution -> acc + c.totalKcal }
        val updatedContributions = (if (totalContributions > 0) {
            contributions.sortedByDescending { it.totalKcal }.map {
                it.copy(percentageOfSession = (it.totalKcal.toDouble() / totalContributions * 100.0))
            }
        } else {
            contributions
        })

        val maxTotal = setScores.size
        val completedTotal = if (completedExercises != null) {
            var sum = 0
            for (ex in completedExercises) {
                sum += ex.sets.count { !it.skipped }
            }
            sum
        } else {
            setScores.size
        }

        return SessionEnergySummary(
            activeKcal = calorieRange(totalActive, confidence),
            epocKcal = calorieRange(totalEpoc, confidence),
            totalKcal = calorieRange(totalMid, confidence),
            projectedTotalKcal = if (completedTotal < maxTotal && totalMid > 0) {
                ((totalMid.toDouble() / completedTotal.coerceAtLeast(1)) * maxTotal).toInt()
            } else null,
            confidence = confidence,
            source = if (isPlanned) EnergyEstimateSource.PLANNED else EnergyEstimateSource.LIVE,
            exerciseContributions = updatedContributions,
            notes = notes,
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
