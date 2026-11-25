package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.LimitingFactorCategory
import com.example.kpkn.data.models.LimitingFactorReport
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.roundToInt

/**
 * Contabilidad pura de intensidad muscular ponderada a partir de RIR Global, RPE o Fallo declarado por serie.
 *
 * Regla de ponderación de rol (misma matriz de carga muscular que la aplicación):
 * - Agonista principal (`PRIMARY`) = 1.0
 * - Sinergista (`SECONDARY`) = 0.5
 * - Estabilizador o músculo limitante enlazado (`STABILIZER`) = 0.3
 * - Neutralizador (`NEUTRALIZER`) = 0.0
 *
 * Convierte cualquier RIR global ingresado en la serie (`RPE = 10 - RIR`, Fallo = `RPE 10 / RIR 0`)
 * sin mutar los registros del usuario ni inventar esfuerzos cuando la serie no declaró intensidad.
 */
data class MuscleIntensityRow(
    val muscleName: String,
    val weightedRpeMean: Double,
    val weightedRirMean: Double,
    val failureSetCount: Int,
    val ratedSetCount: Int,
    val weightedExposure: Double,
    val intensityScore01: Float,
    val topExercises: List<String> = emptyList(),
)

data class ExerciseIntensityBreakdown(
    val exerciseId: String,
    val exerciseName: String,
    val ratedSetCount: Int,
    val meanRpe: Double?,
    val meanRir: Double?,
    val failureSetCount: Int,
    val primaryMuscles: List<String>,
    val limitingFactors: List<LimitingFactorReport> = emptyList(),
)

data class LimitingFactorTally(
    val id: String,
    val label: String,
    val category: LimitingFactorCategory,
    val count: Int,
    val linkedMuscleNames: List<String> = emptyList(),
    val autoLinkedCoreCount: Int = 0,
    val exerciseNames: List<String> = emptyList(),
)

data class MuscleIntensitySummary(
    val muscles: List<MuscleIntensityRow>,
    val top3Muscles: List<MuscleIntensityRow>,
    val exercises: List<ExerciseIntensityBreakdown>,
    val limitingFactorTallies: List<LimitingFactorTally>,
) {
    val hasIntensityData: Boolean get() = muscles.isNotEmpty()
}

object MuscleIntensityAccounting {

    const val PRIMARY_WEIGHT = 1.0
    const val SECONDARY_WEIGHT = 0.5
    const val LIMITING_STABILIZER_WEIGHT = 0.3
    const val NEUTRALIZER_WEIGHT = 0.0

    private class MuscleAccumulator {
        var weightedRpeSum: Double = 0.0
        var weightSum: Double = 0.0
        var failureSets: Int = 0
        var ratedSets: Int = 0
        val exerciseScores = linkedMapOf<String, Double>()

        fun add(rpe: Double, weight: Double, isFailure: Boolean, exerciseName: String) {
            if (weight <= 0.0) return
            weightedRpeSum += rpe * weight
            weightSum += weight
            ratedSets += 1
            if (isFailure) failureSets += 1
            val currentMax = exerciseScores[exerciseName] ?: 0.0
            if (rpe > currentMax) exerciseScores[exerciseName] = rpe
        }
    }

    private class FactorAccumulator(
        val id: String,
        val label: String,
        val category: LimitingFactorCategory,
    ) {
        var count: Int = 0
        var autoCoreCount: Int = 0
        val muscles = linkedSetOf<String>()
        val exercises = linkedSetOf<String>()
    }

    fun computeForSession(
        exercises: List<CompletedExercise>,
        limitingFactorsByExerciseId: Map<String, List<LimitingFactorReport>> = emptyMap(),
        exerciseIndex: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): MuscleIntensitySummary = computeInternal(
        items = exercises.map { ex ->
            Triple(ex, limitingFactorsByExerciseId[ex.exerciseId].orEmpty(), ex.exerciseName)
        },
        exerciseIndex = exerciseIndex,
    )

    fun computeForLogs(
        logs: List<WorkoutLog>,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): MuscleIntensitySummary {
        val items = mutableListOf<Triple<CompletedExercise, List<LimitingFactorReport>, String>>()
        logs.forEach { log ->
            val factorsByExId = log.postExerciseReports.associate { it.exerciseId to it.limitingFactors }
            val factorsByCanonical = log.postExerciseReports
                .filter { !it.canonicalExerciseId.isNullOrBlank() }
                .associate { it.canonicalExerciseId!! to it.limitingFactors }
            log.completedExercises.forEach { ex ->
                val factors = factorsByExId[ex.exerciseId]
                    ?: ex.canonicalExerciseId?.let { factorsByCanonical[it] }
                    ?: emptyList()
                items += Triple(ex, factors, ex.exerciseName)
            }
        }
        return computeInternal(items, exerciseIndex)
    }

    private fun computeInternal(
        items: List<Triple<CompletedExercise, List<LimitingFactorReport>, String>>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): MuscleIntensitySummary {
        val muscleAccumulators = linkedMapOf<String, MuscleAccumulator>()
        val factorAccumulators = linkedMapOf<String, FactorAccumulator>()
        val exerciseBreakdowns = mutableListOf<ExerciseIntensityBreakdown>()

        items.forEach { (exercise, limitingFactors, exerciseName) ->
            if (exercise.cardioDetails != null) return@forEach

            val resolvedMuscles = ExerciseMuscleResolver.resolveCompletedExerciseMuscles(exercise, exerciseIndex)
            val weightsByMuscle = linkedMapOf<String, Double>()
            val primaryNames = linkedSetOf<String>()

            resolvedMuscles.forEach { involved ->
                val roleWeight = when (involved.role) {
                    MuscleRole.PRIMARY -> PRIMARY_WEIGHT
                    MuscleRole.SECONDARY -> SECONDARY_WEIGHT
                    MuscleRole.STABILIZER -> LIMITING_STABILIZER_WEIGHT
                    MuscleRole.NEUTRALIZER -> NEUTRALIZER_WEIGHT
                }
                if (roleWeight <= 0.0) return@forEach
                val canonical = LimitingFactorCatalog.canonicalMuscleDisplayName(involved.muscle, involved.emphasis)
                if (canonical.isBlank()) return@forEach
                if (involved.role == MuscleRole.PRIMARY) primaryNames += canonical
                val existing = weightsByMuscle[canonical] ?: 0.0
                if (roleWeight > existing) weightsByMuscle[canonical] = roleWeight
            }

            // Los factores limitantes con músculos enlazados (ej. Agarre -> Antebrazo, o Espalda -> Core)
            // aportan exposición estabilizadora mínima de 0.3 si ese músculo no figuraba ya con peso mayor.
            limitingFactors.forEach { factor ->
                val factorAcc = factorAccumulators.getOrPut(factor.id) {
                    FactorAccumulator(factor.id, factor.label, factor.category)
                }
                factorAcc.count += 1
                if (factor.autoLinkedCore) factorAcc.autoCoreCount += 1
                factorAcc.exercises += exerciseName
                factor.linkedMuscleIds.forEach { rawId ->
                    val canonical = LimitingFactorCatalog.canonicalMuscleDisplayName(rawId)
                    if (canonical.isNotBlank()) {
                        factorAcc.muscles += canonical
                        val existing = weightsByMuscle[canonical] ?: 0.0
                        if (LIMITING_STABILIZER_WEIGHT > existing) {
                            weightsByMuscle[canonical] = LIMITING_STABILIZER_WEIGHT
                        }
                    }
                }
            }

            val workingRatedSets: List<Pair<CompletedSet, Double>> = exercise.sets
                .filter(RpeConversion::isWorkingSetForIntensity)
                .mapNotNull { set ->
                    RpeConversion.resolvedSetRpe(set)?.let { rpe -> set to rpe }
                }

            val failureCount = workingRatedSets.count { (set, rpe) -> set.isFailure || rpe >= 9.99 }
            if (workingRatedSets.isNotEmpty() || limitingFactors.isNotEmpty()) {
                val meanRpe = workingRatedSets.takeIf { it.isNotEmpty() }?.map { it.second }?.average()
                val meanRir = meanRpe?.let { (10.0 - it).coerceIn(0.0, 10.0) }
                exerciseBreakdowns += ExerciseIntensityBreakdown(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exerciseName,
                    ratedSetCount = workingRatedSets.size,
                    meanRpe = meanRpe,
                    meanRir = meanRir,
                    failureSetCount = failureCount,
                    primaryMuscles = primaryNames.toList(),
                    limitingFactors = limitingFactors,
                )
            }

            workingRatedSets.forEach { (set, rpe) ->
                val isFailure = set.isFailure || rpe >= 9.99
                weightsByMuscle.forEach { (muscleName, weight) ->
                    muscleAccumulators.getOrPut(muscleName) { MuscleAccumulator() }
                        .add(rpe = rpe, weight = weight, isFailure = isFailure, exerciseName = exerciseName)
                }
            }
        }

        val muscles = muscleAccumulators.entries
            .mapNotNull { (muscleName, acc) ->
                if (acc.weightSum <= 0.0) return@mapNotNull null
                val rpeMean = (acc.weightedRpeSum / acc.weightSum).coerceIn(1.0, 10.0)
                val rirMean = (10.0 - rpeMean).coerceIn(0.0, 10.0)
                val score01 = ((rpeMean - 5.0) / 5.0).toFloat().coerceIn(0f, 1f)
                val topEx = acc.exerciseScores.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .take(3)
                MuscleIntensityRow(
                    muscleName = muscleName,
                    weightedRpeMean = (rpeMean * 10.0).roundToInt() / 10.0,
                    weightedRirMean = (rirMean * 10.0).roundToInt() / 10.0,
                    failureSetCount = acc.failureSets,
                    ratedSetCount = acc.ratedSets,
                    weightedExposure = (acc.weightSum * 10.0).roundToInt() / 10.0,
                    intensityScore01 = score01,
                    topExercises = topEx,
                )
            }
            .sortedWith(
                compareByDescending<MuscleIntensityRow> { it.weightedRpeMean }
                    .thenByDescending { it.failureSetCount }
                    .thenByDescending { it.weightedExposure }
                    .thenBy { it.muscleName },
            )

        val tallies = factorAccumulators.values
            .map { acc ->
                LimitingFactorTally(
                    id = acc.id,
                    label = acc.label,
                    category = acc.category,
                    count = acc.count,
                    linkedMuscleNames = acc.muscles.toList(),
                    autoLinkedCoreCount = acc.autoCoreCount,
                    exerciseNames = acc.exercises.toList(),
                )
            }
            .sortedWith(compareByDescending<LimitingFactorTally> { it.count }.thenBy { it.label })

        return MuscleIntensitySummary(
            muscles = muscles,
            top3Muscles = muscles.take(3),
            exercises = exerciseBreakdowns.sortedByDescending { it.meanRpe ?: 0.0 },
            limitingFactorTallies = tallies,
        )
    }
}
