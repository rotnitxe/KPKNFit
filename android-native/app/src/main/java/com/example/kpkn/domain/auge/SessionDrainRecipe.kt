package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.roundToInt

/** Cada consumidor fija sus decisiones históricas; ninguna combinación es canónica en 2.2a. */
internal data class RecipeOptions(
    val restSource: RestSource,
    val volumeCounting: VolumeCounting,
    val axialGatePosition: AxialGatePosition,
    val muscleMultiplierSource: MuscleMultiplierSource,
    val densitySource: DensitySource,
    val primaryMuscleSource: PrimaryMuscleSource,
    val missingCatalog: MissingCatalog,
    val cardioHandling: CardioHandling,
    val softCapUnits: SoftCapUnits,
) {
    enum class RestSource { SUPERSET_OR_EXERCISE, EXERCISE, PLANNED_OR_90, PLANNED_OR_SETTINGS }
    enum class VolumeCounting { UNILATERAL_HALVES_ROUNDED, EFFECTIVE_SET_COUNT }
    enum class AxialGatePosition { BEFORE_ACCUMULATION, AFTER_ACCUMULATION, NOT_APPLIED }
    enum class MuscleMultiplierSource { REMAPPED_PILLARS, DIRECT_KEYS, NONE }
    enum class DensitySource { COMPLETED_METADATA, PLANNED_LEGACY_SUPERSET, PLANNED_GROUP_OR_LEGACY }
    enum class PrimaryMuscleSource { SNAPSHOT_PILLAR, CATALOG_VOLUME_GROUP }
    enum class MissingCatalog { HEURISTIC, SKIP }
    enum class CardioHandling { CONSUMER_HOOK, LEGACY_STRENGTH }
    enum class SoftCapUnits { CHANNEL_PERCENT, MUSCLE_STRESS_UNITS, LOG_PERCENT, NONE }
}

internal data class AdjustedSetDrain(
    val muscular: Double,
    val cns: Double,
    val spinal: Double,
    val accumulatedDrain: Double,
)

internal data class RecipeExerciseIdentity(
    val exerciseName: String,
    val catalogConfigurationId: String?,
    val exerciseDbId: String?,
    val exerciseId: String?,
)

internal data class RecipeExerciseContext(
    val metrics: AugeMetrics,
    val involvedMuscles: List<InvolvedMuscle>,
    val primaryMuscle: String,
    val muscleMultiplier: Double,
    val restTime: Int,
    val densityMultiplier: Double,
    val axialGate: Double,
    val postAccumulationAxialGate: Double,
)

/**
 * Recorrido común de las series efectivas y ajuste anterior a los topes.
 * El consumidor conserva el cardio, las unidades del tope, el reparto y los bonos.
 * El resolvedor inyectado evita añadir dependencias del dominio hacia datos.
 */
internal class SessionDrainRecipe(
    private val settings: Settings,
    private val tanks: BatteryTanks,
    private val adaptiveCache: AugeAdaptiveCache,
    private val options: RecipeOptions,
    private val resolveInfo: (RecipeExerciseIdentity) -> ExerciseMuscleInfo?,
) {
    var accumulatedDrain: Double = 0.0

    private val integerVolume = mutableMapOf<String, Int>()
    private val halfVolume = mutableMapOf<String, Double>()
    private val muscleMultipliers = when (options.muscleMultiplierSource) {
        RecipeOptions.MuscleMultiplierSource.REMAPPED_PILLARS ->
            remapMuscleMultiplierMapToPillars(adaptiveCache.muscleDrainMultipliers)
        RecipeOptions.MuscleMultiplierSource.DIRECT_KEYS -> adaptiveCache.muscleDrainMultipliers
        RecipeOptions.MuscleMultiplierSource.NONE -> emptyMap()
    }

    fun softCap(drain: Double, accumulated: Double, cap: Double): Double =
        if (options.softCapUnits == RecipeOptions.SoftCapUnits.NONE) drain
        else AugeUtils.applySessionSoftCap(drain, accumulated, cap)

    fun forEachCompleted(
        exercises: List<CompletedExercise>,
        onCardio: (CompletedExercise) -> Unit,
        onSet: (CompletedExercise, CompletedSet, RecipeExerciseContext, SetDrain, AdjustedSetDrain, Int) -> Unit,
    ) {
        walk<CompletedExercise, CompletedSet>(
            exercises = exercises,
            hasCardio = { it.cardioDetails != null },
            prepare = { ex ->
                contextFor(ExerciseInput(
                    identity = RecipeExerciseIdentity(ex.exerciseName, ex.catalogConfigurationId, ex.exerciseDbId, ex.exerciseId),
                    snapshot = ex.effectiveMuscles,
                    restTime = ex.restTime,
                    supersetId = ex.supersetId,
                    groupSupersetId = ex.supersetId,
                    supersetExerciseCount = ex.supersetExerciseCount,
                    supersetRounds = ex.supersetRounds,
                    supersetRestBetween = ex.supersetRestBetween,
                    supersetRestAfter = ex.supersetRestAfter,
                ))
            },
            effectiveSets = { ex -> ex.sets.filter(AugeFatigueEngine::isSetEffective) },
            complete = { _, set -> set },
            onCardio = onCardio,
            onSet = onSet,
        )
    }

    fun forEachPlanned(
        exercises: List<Exercise>,
        onCardio: (Exercise) -> Unit,
        onSet: (Exercise, ExerciseSet, RecipeExerciseContext, SetDrain, AdjustedSetDrain, Int) -> Unit,
    ) {
        walk<Exercise, ExerciseSet>(
            exercises = exercises,
            hasCardio = { it.cardioDetails != null },
            prepare = { ex ->
                contextFor(ExerciseInput(
                    identity = RecipeExerciseIdentity(ex.name, ex.catalogConfigurationId, ex.exerciseDbId, ex.exerciseId),
                    snapshot = ex.effectiveMuscles,
                    restTime = ex.restTime,
                    supersetId = ex.supersetId,
                    groupSupersetId = ex.supersetGroupRefOrLegacyId(),
                    supersetExerciseCount = 1,
                    supersetRounds = null,
                    supersetRestBetween = ex.supersetRestBetween,
                    supersetRestAfter = ex.supersetRestAfter,
                ))
            },
            effectiveSets = { ex -> ex.sets.filterNot { it.isIneffective } },
            complete = { ex, set -> plannedSet(ex, set) },
            onCardio = onCardio,
            onSet = onSet,
        )
    }

    private fun <E, S> walk(
        exercises: List<E>,
        hasCardio: (E) -> Boolean,
        prepare: (E) -> RecipeExerciseContext?,
        effectiveSets: (E) -> List<S>,
        complete: (E, S) -> CompletedSet,
        onCardio: (E) -> Unit,
        onSet: (E, S, RecipeExerciseContext, SetDrain, AdjustedSetDrain, Int) -> Unit,
    ) {
        for (exercise in exercises) {
            if (hasCardio(exercise) && options.cardioHandling == RecipeOptions.CardioHandling.CONSUMER_HOOK) {
                onCardio(exercise)
                continue
            }
            val context = prepare(exercise) ?: continue
            var integerCount = integerVolume[context.primaryMuscle] ?: 0
            var halfCount = halfVolume[context.primaryMuscle] ?: 0.0
            for (sourceSet in effectiveSets(exercise)) {
                val set = complete(exercise, sourceSet)
                val accumulatedSets = when (options.volumeCounting) {
                    RecipeOptions.VolumeCounting.UNILATERAL_HALVES_ROUNDED -> {
                        halfCount += if (set.side != null) 0.5 else 1.0
                        halfCount.roundToInt()
                    }
                    RecipeOptions.VolumeCounting.EFFECTIVE_SET_COUNT -> {
                        integerCount += 1
                        integerCount
                    }
                }
                val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set = set,
                    metrics = context.metrics,
                    tanks = tanks,
                    accumulatedSets = accumulatedSets,
                    restTime = context.restTime,
                    densityMultiplier = context.densityMultiplier,
                    cnsMultiplier = adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier = adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier = context.muscleMultiplier,
                    weightUnit = settings.weightUnit,
                )
                val adjusted = adjustSet(drain, accumulatedDrain, options, context.axialGate)
                accumulatedDrain = adjusted.accumulatedDrain
                onSet(exercise, sourceSet, context, drain, adjusted, accumulatedSets)
            }
            when (options.volumeCounting) {
                RecipeOptions.VolumeCounting.UNILATERAL_HALVES_ROUNDED -> halfVolume[context.primaryMuscle] = halfCount
                RecipeOptions.VolumeCounting.EFFECTIVE_SET_COUNT -> integerVolume[context.primaryMuscle] = integerCount
            }
        }
    }

    private data class ExerciseInput(
        val identity: RecipeExerciseIdentity,
        val snapshot: List<InvolvedMuscle>?,
        val restTime: Int?,
        val supersetId: String?,
        val groupSupersetId: String?,
        val supersetExerciseCount: Int,
        val supersetRounds: Int?,
        val supersetRestBetween: Int?,
        val supersetRestAfter: Int?,
    )

    private fun contextFor(input: ExerciseInput): RecipeExerciseContext? {
        val info = resolveInfo(input.identity)
        if (info == null && options.missingCatalog == RecipeOptions.MissingCatalog.SKIP) return null
        val metrics = AugeFatigueEngine.getDynamicAugeMetrics(input.identity.exerciseName, info?.equipment, info) ?: AugeMetrics()
        val involved = AugeFatigueEngine.resolveInvolvedMuscles(input.identity.exerciseName, input.snapshot, info)
        val primary = when (options.primaryMuscleSource) {
            RecipeOptions.PrimaryMuscleSource.SNAPSHOT_PILLAR ->
                involved.find { it.role == MuscleRole.PRIMARY }
                    ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                    ?: involved.firstOrNull()?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
                    ?: ""
            RecipeOptions.PrimaryMuscleSource.CATALOG_VOLUME_GROUP -> {
                val muscle = info?.involvedMuscles?.firstOrNull { it.role == MuscleRole.PRIMARY }
                    ?: info?.involvedMuscles?.firstOrNull()
                muscle?.let { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) } ?: "Core"
            }
        }
        val rest = when (options.restSource) {
            RecipeOptions.RestSource.SUPERSET_OR_EXERCISE -> input.supersetRestBetween ?: requireNotNull(input.restTime)
            RecipeOptions.RestSource.EXERCISE -> requireNotNull(input.restTime)
            RecipeOptions.RestSource.PLANNED_OR_90 -> input.restTime ?: 90
            RecipeOptions.RestSource.PLANNED_OR_SETTINGS -> input.restTime ?: settings.restTimerDefaultSeconds
        }
        val density = when (options.densitySource) {
            RecipeOptions.DensitySource.COMPLETED_METADATA -> AugeFatigueEngine.getDensityMultiplierForExercise(
                supersetId = input.supersetId,
                restTime = input.supersetRestBetween ?: input.restTime ?: rest,
                supersetExerciseCount = input.supersetExerciseCount,
                supersetRounds = input.supersetRounds,
                supersetRestAfter = input.supersetRestAfter,
            )
            RecipeOptions.DensitySource.PLANNED_LEGACY_SUPERSET ->
                AugeFatigueEngine.getDensityMultiplierForExercise(input.supersetId, input.restTime ?: 90)
            RecipeOptions.DensitySource.PLANNED_GROUP_OR_LEGACY ->
                AugeFatigueEngine.getDensityMultiplierForExercise(input.groupSupersetId, input.restTime ?: settings.restTimerDefaultSeconds)
        }
        val gate = AugeFatigueEngine.spinalAxialGate(info?.axialLoadFactor)
        return RecipeExerciseContext(
            metrics = metrics,
            involvedMuscles = involved,
            primaryMuscle = primary,
            muscleMultiplier = lookupMuscleDrainMultiplier(muscleMultipliers, primary),
            restTime = rest,
            densityMultiplier = density,
            axialGate = gate,
            postAccumulationAxialGate = if (options.axialGatePosition == RecipeOptions.AxialGatePosition.AFTER_ACCUMULATION) gate else 1.0,
        )
    }

    companion object {
        /** Se mantiene el promedio de tres canales sin topar, incluso su dependencia cruzada (F-60). */
        fun adjustSet(drain: SetDrain, accumulatedDrain: Double, options: RecipeOptions, axialGate: Double): AdjustedSetDrain {
            val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
            val decayK = AugeUtils.SESSION_DECAY_K
            val diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
            val muscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
            val cns = drain.cnsDrainPct * conservationFactor * diminishingFactor
            val spinal = if (options.axialGatePosition == RecipeOptions.AxialGatePosition.BEFORE_ACCUMULATION) {
                drain.spinalDrainPct * conservationFactor * diminishingFactor * axialGate
            } else {
                drain.spinalDrainPct * conservationFactor * diminishingFactor
            }
            return AdjustedSetDrain(muscular, cns, spinal, accumulatedDrain + (muscular + cns + spinal) / 3.0)
        }

        fun conserveCardio(drain: Double): Double = drain * AugeUtils.SESSION_CONSERVATION_FACTOR

        /** Adaptación histórica: las técnicas y la duración planificadas no se añaden a la serie sintética. */
        fun plannedSet(exercise: Exercise, set: ExerciseSet): CompletedSet {
            val percentage = set.targetPercentageRM
            val reference = exercise.reference1RM
            val weight = if (exercise.trainingMode == TrainingMode.RM && percentage != null && reference != null && reference > 0.0) {
                (percentage / 100.0) * reference
            } else {
                set.weight ?: 60.0
            }
            return CompletedSet(
                id = set.id,
                weight = weight,
                reps = set.plannedRepAnchor() ?: 8,
                rpe = set.targetRPE,
                rir = set.targetRIR,
                actualIntensityMode = set.intensityMode,
                actualIntensityValue = when (set.intensityMode) {
                    IntensityMode.RPE -> set.targetRPE
                    IntensityMode.RIR -> set.targetRIR?.toDouble()
                    else -> null
                },
                isFailure = set.isFailure || set.intensityMode == IntensityMode.FAILURE,
            )
        }
    }
}
