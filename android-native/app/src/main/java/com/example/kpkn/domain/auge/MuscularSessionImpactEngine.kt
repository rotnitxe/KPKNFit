package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeMetrics
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import kotlin.math.roundToInt
import com.example.kpkn.data.models.effectiveRepEquivalent
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.training.VolumeCalculator
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlin.math.exp

/** Persisted, deterministic muscular impact produced at the end of a session. */
@Serializable
data class MuscularSessionImpactV2(
    val modelVersion: String = "muscle-impact-v2",
    val completionInstantIso: String,
    val globalMuscularDrain: Int,
    val perMuscle: Map<String, MuscleSessionImpactV2>,
    val involvedVolumeMuscles: Set<String>,
    val setInputHash: String,
    val contextHash: String,
)

@Serializable
data class MuscleSessionImpactV2(
    val stressUnits: Double,
    val capacityAtCompletion: Double,
    val immediateDrainPct: Double,
    val directStressUnits: Double,
    val indirectStressUnits: Double,
)

/** The single input shape used by the planned and completed adapters. */
data class MuscularSessionInput(
    val completedExercises: List<CompletedExercise>,
    val completionInstantIso: String,
    val source: Source,
    val setInputHash: String = MuscularSessionImpactEngine.completedSetInputHash(completedExercises),
    val editorGlobalMuscularDrain: Int? = null,
) {
    enum class Source { PLANNED, COMPLETED }
}

/**
 * Canonical local muscular impact.  The global editor formula remains the source of
 * the global ring; this engine owns only the local attribution and the immutable
 * snapshot consumed by finish, Home and history.
 */
object MuscularSessionImpactEngine {
    const val MODEL_VERSION = "muscle-impact-v2"

    fun fromCompletedExercises(
        completedExercises: List<CompletedExercise>,
        completionInstantIso: String,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): MuscularSessionInput {
        val global = AugeFatigueEngine.calculateCompletedSessionDrain(
            completedExercises = completedExercises,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = adaptiveCache,
        ).muscular
        return MuscularSessionInput(
            completedExercises = completedExercises,
            completionInstantIso = completionInstantIso,
            source = MuscularSessionInput.Source.COMPLETED,
            editorGlobalMuscularDrain = global,
        )
    }

    fun fromPlannedSession(
        session: Session,
        completionInstantIso: String,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): MuscularSessionInput {
        val planned = session.allExercises().mapNotNull { exercise ->
            val sets = exercise.sets.mapNotNull { set ->
                if (set.isIneffective || set.isEmptySlot) return@mapNotNull null
                val weight = when {
                    exercise.trainingMode == com.example.kpkn.data.models.TrainingMode.RM &&
                        set.targetPercentageRM != null && exercise.reference1RM != null && exercise.reference1RM > 0.0 ->
                        set.targetPercentageRM / 100.0 * exercise.reference1RM
                    else -> set.weight ?: 60.0
                }
                CompletedSet(
                    id = set.id,
                    weight = weight,
                    reps = set.plannedRepAnchor() ?: 8,
                    rpe = set.targetRPE,
                    rir = set.targetRIR,
                    isFailure = set.isFailure || set.intensityMode == IntensityMode.FAILURE,
                    actualIntensityMode = set.intensityMode,
                    actualIntensityValue = when (set.intensityMode) {
                        IntensityMode.RPE -> set.targetRPE
                        IntensityMode.RIR -> set.targetRIR?.toDouble()
                        else -> null
                    },
                )
            }
            if (sets.isEmpty()) null else CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = exercise.exerciseDbId,
                canonicalExerciseId = exercise.canonicalExerciseId,
                effectiveMuscles = exercise.effectiveMuscles,
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetGroupRefOrLegacyIdForImpact(),
                supersetExerciseCount = 1,
                catalogRevision = exercise.catalogRevision,
                catalogDefinitionId = exercise.catalogDefinitionId,
                catalogConfigurationId = exercise.catalogConfigurationId,
                performanceProfileId = exercise.performanceProfileId,
                cardioDetails = exercise.cardioDetails,
                sets = sets,
            )
        }
        val global = AugeFatigueEngine.calculateAdjustedPredictedDrain(
            session = session,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = adaptiveCache,
        ).muscular
        return MuscularSessionInput(
            completedExercises = planned,
            completionInstantIso = completionInstantIso,
            source = MuscularSessionInput.Source.PLANNED,
            editorGlobalMuscularDrain = global,
        )
    }

    fun evaluate(
        input: MuscularSessionInput,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        capacitiesAtCompletion: Map<String, Double> = emptyMap(),
    ): MuscularSessionImpactV2 {
        val perMuscleAccumulator = linkedMapOf<String, Accumulator>()
        val involvedMuscles = linkedSetOf<String>()
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)

        input.completedExercises.forEach { exercise ->
            if (exercise.cardioDetails != null) return@forEach
            val dbInfo = resolveCatalogExerciseInfoInIndex(
                index = exerciseDb,
                catalogConfigurationId = exercise.catalogConfigurationId,
                exerciseDbId = exercise.exerciseDbId,
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.exerciseName,
            )
            val involved = AugeFatigueEngine.resolveInvolvedMuscles(
                exerciseName = exercise.exerciseName,
                snapshot = exercise.effectiveMuscles,
                dbInfo = dbInfo,
            )
            // This is deliberately the only volume-contribution resolver in the
            // local path.  Role multipliers are not multiplied a second time.
            val contributions = VolumeCalculator.buildPerExerciseMuscleContributions(involved)
            if (contributions.isEmpty()) return@forEach
            involvedMuscles += contributions.keys
            val roleByMuscle = involved
                .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
                .mapValues { (_, values) ->
                    values.maxByOrNull { it.volumeContribution ?: 0.0 }?.role
                        ?: MuscleRole.PRIMARY
                }
            val metrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.exerciseName, dbInfo?.equipment, dbInfo)
                ?: AugeMetrics()
            val density = AugeFatigueEngine.getDensityMultiplierForExercise(
                supersetId = exercise.supersetId,
                restTime = exercise.supersetRestBetween ?: exercise.restTime,
                supersetExerciseCount = exercise.supersetExerciseCount,
                supersetRounds = exercise.supersetRounds,
                supersetRestAfter = exercise.supersetRestAfter,
            )
            var accumulatedSets = 0.0
            exercise.sets.forEach { set ->
                if (!AugeFatigueEngine.isSetEffective(set)) return@forEach
                accumulatedSets += if (set.side == null) 1.0 else 0.5
                val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set = set,
                    metrics = metrics,
                    tanks = tanks,
                    accumulatedSets = accumulatedSets.roundToInt(),
                    restTime = exercise.supersetRestBetween ?: exercise.restTime,
                    densityMultiplier = density,
                    cnsMultiplier = adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier = adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier = 1.0,
                    weightUnit = settings.weightUnit,
                )
                val diminishing = 1.0 / (1.0 + 0.65 * ((accumulatedSets - 1.0).coerceAtLeast(0.0) / 10.0))
                val baseStress = drain.muscularDrainPct * 10.0 * diminishing
                contributions.forEach { (muscle, contribution) ->
                    val accumulator = perMuscleAccumulator.getOrPut(muscle) { Accumulator() }
                    val stress = baseStress * contribution
                    accumulator.stress += stress
                    if (roleByMuscle[muscle] == MuscleRole.PRIMARY) {
                        accumulator.direct += stress
                    } else {
                        accumulator.indirect += stress
                    }
                }
            }
        }

        val contextHash = contextHash(settings, adaptiveCache)
        val perMuscle = perMuscleAccumulator.mapValues { (muscle, accumulator) ->
            val capacity = capacitiesAtCompletion[muscle]
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: defaultCapacity(settings)
            val rawDrain = 100.0 * (1.0 - exp(-accumulator.stress / capacity.coerceAtLeast(120.0)))
            MuscleSessionImpactV2(
                stressUnits = accumulator.stress,
                capacityAtCompletion = capacity,
                immediateDrainPct = rawDrain.coerceIn(0.0, 100.0),
                directStressUnits = accumulator.direct,
                indirectStressUnits = accumulator.indirect,
            )
        }
        return MuscularSessionImpactV2(
            modelVersion = MODEL_VERSION,
            completionInstantIso = input.completionInstantIso,
            globalMuscularDrain = (input.editorGlobalMuscularDrain ?: globalFromLocal(perMuscle)).coerceIn(0, 100),
            perMuscle = perMuscle,
            involvedVolumeMuscles = involvedMuscles,
            setInputHash = input.setInputHash,
            contextHash = contextHash,
        )
    }

    fun evaluate(
        completedExercises: List<CompletedExercise>,
        completionInstantIso: String,
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        capacitiesAtCompletion: Map<String, Double> = emptyMap(),
    ): MuscularSessionImpactV2 = evaluate(
        input = fromCompletedExercises(completedExercises, completionInstantIso, exerciseDb, settings, adaptiveCache),
        exerciseDb = exerciseDb,
        settings = settings,
        adaptiveCache = adaptiveCache,
        capacitiesAtCompletion = capacitiesAtCompletion,
    )

    fun completedSetInputHash(completedExercises: List<CompletedExercise>): String {
        val canonical = completedExercises.joinToString("|") { exercise ->
            val muscles = exercise.effectiveMuscles.orEmpty()
                .sortedWith(compareBy({ it.muscle }, { it.role.name }, { it.volumeContribution ?: -1.0 }))
                .joinToString(",") { "${it.muscle}:${it.role}:${it.volumeContribution ?: "null"}" }
            val sets = exercise.sets.joinToString(",") { set ->
                listOf(
                    set.id, set.weight, set.reps, set.timeSeconds, set.rpe, set.rir,
                    set.isFailure, set.isFailedSet, set.isPartial, set.partialReps,
                    set.actualIntensityMode, set.actualIntensityValue, set.skipped, set.isWarmup,
                    set.side, set.dropSets.size, set.restPauses.size,
                ).joinToString(";")
            }
            listOf(
                exercise.exerciseId, exercise.exerciseDbId, exercise.catalogDefinitionId,
                exercise.catalogConfigurationId, exercise.performanceProfileId,
                exercise.restTime, exercise.supersetId, muscles, sets,
            ).joinToString("#")
        }
        return sha256(canonical)
    }

    /** Lightweight snapshot hash used while the finish sheet is still open. */
    fun completedSetInputHash(completedSets: Map<String, CompletedSet>): String = sha256(
        completedSets.toSortedMap().entries.joinToString("|") { (key, set) ->
            listOf(
                key, set.id, set.weight, set.reps, set.timeSeconds, set.rpe, set.rir,
                set.actualIntensityMode, set.actualIntensityValue, set.isFailure,
                set.isFailedSet, set.skipped, set.isWarmup, set.side,
            ).joinToString(";")
        },
    )

    fun contextHash(settings: Settings, adaptiveCache: AugeAdaptiveCache): String = sha256(
        listOf(
            MODEL_VERSION,
            settings.athleteType,
            settings.weightUnit,
            settings.algorithmSettings.augeFatigueSensitivity,
            adaptiveCache.schemaVersion,
            adaptiveCache.cnsDrainMultiplier,
            adaptiveCache.spinalDrainMultiplier,
        ).joinToString("|")
    )

    private data class Accumulator(var stress: Double = 0.0, var direct: Double = 0.0, var indirect: Double = 0.0)

    private fun globalFromLocal(perMuscle: Map<String, MuscleSessionImpactV2>): Int {
        if (perMuscle.isEmpty()) return 0
        val totalStress = perMuscle.values.sumOf { it.stressUnits }
        return (100.0 * (1.0 - exp(-totalStress / 700.0))).toInt().coerceIn(0, 100)
    }

    private fun defaultCapacity(settings: Settings): Double =
        AugeFatigueEngine.getAthleteCapacity(settings).coerceIn(120.0, 3500.0)

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private fun Exercise.supersetGroupRefOrLegacyIdForImpact(): String? =
    supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }
