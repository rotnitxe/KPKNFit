package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import kotlin.math.roundToInt

/** Variantes legítimas del consumidor. */
internal data class RecipeOptions(
    /** C conserva el respaldo de estrés del aprendiz sin V2; los demás usan ganchos con su propio reparto de cardio. */
    val cardioHandling: CardioHandling,
    /** A/P/F: porcentaje por canal; B: unidades de estrés; C: sin tope; D/E: porcentaje por log. */
    val softCapUnits: SoftCapUnits,
) {
    enum class CardioHandling { CONSUMER_HOOK, LEGACY_STRENGTH }
    enum class SoftCapUnits { CHANNEL_PERCENT, MUSCLE_STRESS_UNITS, LOG_PERCENT, NONE }
}

/** Acumulados independientes, antes de topes y bonos del consumidor (F-60). */
internal data class ChannelDrainAccumulation( // Cada canal amortigua solamente su propio drenaje.
    val muscular: Double = 0.0, // Porcentaje muscular ajustado de la sesión.
    val cns: Double = 0.0, // Porcentaje neural ajustado de la sesión.
    val spinal: Double = 0.0, // Porcentaje espinal tras la puerta axial cuando procede.
) // Las unidades son porcentajes, nunca unidades de estrés muscular.

internal data class AdjustedSetDrain(
    val muscular: Double,
    val cns: Double,
    val spinal: Double,
    val accumulatedDrain: ChannelDrainAccumulation, // Sin promedio ni realimentación entre canales.
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
    val restTime: Int,
    val densityMultiplier: Double,
    val axialGate: Double,
)

/**
 * Recorrido común de las series efectivas y ajuste anterior a los topes.
 * El consumidor conserva el cardio, las unidades del tope y el reparto.
 * El resolvedor inyectado evita añadir dependencias del dominio hacia datos.
 */
internal class SessionDrainRecipe(
    private val settings: Settings,
    private val tanks: BatteryTanks,
    private val adaptiveCache: AugeAdaptiveCache,
    private val options: RecipeOptions,
    private val resolveInfo: (RecipeExerciseIdentity) -> ExerciseMuscleInfo?,
) {
    var accumulatedDrain = ChannelDrainAccumulation() // Estado independiente de los topes del consumidor.
        private set // El cardio también entra mediante una suma explícita por canal.

    /** El gancho conserva su escala de cardio; el acumulado recibe sus porcentajes antes del tope. */
    fun accumulateCardio(muscular: Double, cns: Double, spinal: Double) { // Cardio no adopta la conservación de fuerza.
        accumulatedDrain = ChannelDrainAccumulation( // No mezcla la carga neural con la muscular.
            muscular = accumulatedDrain.muscular + muscular, // Porcentaje muscular del gancho.
            cns = accumulatedDrain.cns + cns, // Porcentaje neural del gancho.
            spinal = accumulatedDrain.spinal + spinal, // Porcentaje espinal del gancho.
        ) // Los topes siguen aplicándose únicamente en cada consumidor.
    } // B/D/E no realimentan el cardio; C conserva su recorrido legacy de series para el aprendiz sin V2.

    private val halfVolume = mutableMapOf<String, Double>()

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
                    isPlanned = false,
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
                    supersetId = ex.supersetGroupRefOrLegacyId(),
                    isPlanned = true,
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
        prepare: (E) -> RecipeExerciseContext,
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
            val context = prepare(exercise)
            var halfCount = halfVolume[context.primaryMuscle] ?: 0.0
            for (sourceSet in effectiveSets(exercise)) {
                val set = complete(exercise, sourceSet)
                halfCount += if (set.side != null) 0.5 else 1.0 // Una pareja de lados equivale a una serie por pilar.
                val accumulatedSets = halfCount.roundToInt() // El multiplicador progresivo recibe el mismo conteo en A–F.
                val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set = set,
                    metrics = context.metrics,
                    tanks = tanks,
                    accumulatedSets = accumulatedSets,
                    restTime = context.restTime,
                    densityMultiplier = context.densityMultiplier,
                    weightUnit = settings.weightUnit,
                )
                val base = adjustSet(drain, accumulatedDrain, context.axialGate) // La puerta axial siempre precede al acumulado.
                accumulatedDrain = base.accumulatedDrain
                val adjusted = withHeavySingleBonus(base, set)
                onSet(exercise, sourceSet, context, drain, adjusted, accumulatedSets)
            }
            halfVolume[context.primaryMuscle] = halfCount // Conserva también las mitades entre ejercicios del mismo pilar.
        }
    }

    private data class ExerciseInput(
        val identity: RecipeExerciseIdentity,
        val snapshot: List<InvolvedMuscle>?,
        val restTime: Int?,
        val supersetId: String?,
        val isPlanned: Boolean,
        val supersetExerciseCount: Int,
        val supersetRounds: Int?,
        val supersetRestBetween: Int?,
        val supersetRestAfter: Int?,
    )

    private fun contextFor(input: ExerciseInput): RecipeExerciseContext {
        val info = resolveInfo(input.identity)
        val metrics = AugeFatigueEngine.getDynamicAugeMetrics(input.identity.exerciseName, info?.equipment, info) ?: AugeMetrics()
        val involved = AugeFatigueEngine.resolveInvolvedMuscles(input.identity.exerciseName, input.snapshot, info)
        val primary = involved.find { it.role == MuscleRole.PRIMARY }
            ?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
            ?: involved.firstOrNull()?.let { getAugeMusclePillarId(it.muscle, it.emphasis) }
            ?: ""
        // El descanso corto de la superserie solo interviene en la densidad.
        val rest = input.restTime ?: settings.restTimerDefaultSeconds
        val density = if (input.isPlanned) {
            AugeFatigueEngine.getDensityMultiplierForExercise(input.supersetId, input.restTime ?: settings.restTimerDefaultSeconds)
        } else {
            AugeFatigueEngine.getDensityMultiplierForExercise(
                supersetId = input.supersetId,
                restTime = input.supersetRestBetween ?: input.restTime ?: rest,
                supersetExerciseCount = input.supersetExerciseCount,
                supersetRounds = input.supersetRounds,
                supersetRestAfter = input.supersetRestAfter,
            )
        }
        val gate = AugeFatigueEngine.spinalAxialGate(info?.axialLoadFactor)
        return RecipeExerciseContext(
            metrics = metrics,
            involvedMuscles = involved,
            primaryMuscle = primary,
            restTime = rest,
            densityMultiplier = density,
            axialGate = gate,
        )
    }

    companion object {
        /**
         * F-60: cada canal usa x' = x + a/(1 + K·x/100), sin promediar otros canales.
         * Para x ≥ 0, ∂x'/∂x = 1 − (a·K/100)/(1 + K·x/100)² > 0 si a·K/100 < 1;
         * también ∂x'/∂a > 0. Con conservación 0,85, K = 0,65 y tope muscular/neural 32,
         * a ≤ 27,2 y a·0,0065 ≤ 0,1768. Columna permite 35: a ≤ 29,75 y el producto
         * sigue siendo ≤ 0,193375. Una puerta axial en [0,1] solo reduce ese límite.
         * Por inducción, el acumulado de cada canal es monótono en su drenaje crudo y en la carga.
         * Los topes y los bonos históricos se aplican después, sin alterar esta recurrencia.
         */
        fun adjustSet(drain: SetDrain, accumulatedDrain: ChannelDrainAccumulation, axialGate: Double): AdjustedSetDrain { // Puerta anterior al acumulado en todos.
            val conservationFactor = AugeUtils.SESSION_CONSERVATION_FACTOR
            val decayK = AugeUtils.SESSION_DECAY_K
            fun diminishing(accumulated: Double) = 1.0 / (1.0 + decayK * (accumulated / 100.0)) // Mismo K, un acumulado por canal.
            val muscular = drain.muscularDrainPct * conservationFactor * diminishing(accumulatedDrain.muscular) // Solo fatiga muscular.
            val cns = drain.cnsDrainPct * conservationFactor * diminishing(accumulatedDrain.cns) // Solo fatiga neural.
            val spinal = drain.spinalDrainPct * conservationFactor * diminishing(accumulatedDrain.spinal) * axialGate // Acumula solo lo aplicado por la puerta.
            return AdjustedSetDrain(muscular, cns, spinal, ChannelDrainAccumulation( // Sin tope blando ni promedio.
                muscular = accumulatedDrain.muscular + muscular, // El canal muscular no recibe SNC ni Columna.
                cns = accumulatedDrain.cns + cns, // El canal neural no recibe músculo ni Columna.
                spinal = accumulatedDrain.spinal + spinal, // El canal espinal conserva la puerta aplicada.
            )) // El consumidor recibe el ajuste y el nuevo estado por separado.
        }

        fun conserveCardio(drain: Double): Double = drain * AugeUtils.SESSION_CONSERVATION_FACTOR

        /** El bono neural precede al tope blando y conserva su exclusión del acumulado de la receta. */
        fun withHeavySingleBonus(drain: AdjustedSetDrain, set: CompletedSet): AdjustedSetDrain =
            if (set.weight > 0.0 && set.reps <= 3 && AugeFatigueEngine.getEffectiveRPE(set) >= 9.5) {
                drain.copy(cns = drain.cns + drain.cns * 0.15)
            } else drain

        /** Post-proceso del total de la sesión, después de los topes por serie. */
        fun durationCns(cns: Double, durationMinutes: Int): Double = when {
            durationMinutes > 90 -> cns * 1.15
            durationMinutes > 75 -> cns * 1.08
            else -> cns
        }

        fun durationSpinal(spinal: Double, durationMinutes: Int): Double =
            if (durationMinutes > 90) spinal * 1.08 else spinal

        /**
         * Adaptación histórica: las técnicas y la duración planificadas no se añaden a la serie sintética.
         * Cada serie del plan es lógica: aquí no se expanden lados ni objetivos izquierdo/derecho.
         */
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
