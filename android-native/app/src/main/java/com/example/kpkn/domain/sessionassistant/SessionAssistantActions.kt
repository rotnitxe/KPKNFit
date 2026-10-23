package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import kotlin.math.roundToInt

/** Lo que las acciones necesitan del entorno: el catálogo, los descansos de superserie del editor y el id del grupo nuevo. */
data class AssistantActionParameters(
    val exerciseIndex: Map<String, ExerciseMuscleInfo> = emptyMap(),
    val supersetBetweenRestSeconds: Int = 60,
    val supersetRoundRestSeconds: Int = 120,
    val defaultRestSeconds: Int = 90,
    val newGroupId: () -> String = { "superset_group_${System.currentTimeMillis()}" },
)

/**
 * Las acciones del asistente como funciones PURAS sobre una sesión. El editor las aplica con ellas y el asistente las SIMULA con
 * las mismas antes de proponerlas: lo que se anuncia es lo que se aplica (AUD1 §2.7, «Acción viable»).
 */
object SessionAssistantActions {
    /** Suelo de descanso entre series al recortarlo. */
    const val MIN_REST_SECONDS = 30

    fun apply(session: Session, action: AssistantDetailAction, params: AssistantActionParameters = AssistantActionParameters()): Session =
        when (action) {
            is AssistantDetailAction.LowerRpe ->
                if (action.exerciseId != null) lowerRpeOnExercise(session, action.exerciseId, action.amount) else lowerRpeOnAllExercises(session)
            is AssistantDetailAction.ReduceSet -> when {
                action.exerciseId != null -> reduceSetsForExercise(session, action.exerciseId)
                action.muscle != null -> reduceSetsForMuscle(session, action.muscle, params.exerciseIndex)
                else -> session
            }
            is AssistantDetailAction.ReduceRest -> reduceRest(session, action.seconds, action.exerciseIds, action.defaultRestSeconds)
            is AssistantDetailAction.ConvertToDropSet -> convertExerciseToDropSet(session, action.exerciseId)
            is AssistantDetailAction.ConvertToSuperset -> convertToSupersetWithNext(session, action.exerciseId, params)
            AssistantDetailAction.RemoveFailure -> convertFailureToRir(session)
        }

    /** Quita la última serie del ejercicio (nunca la única). */
    fun reduceSetsForExercise(session: Session, exerciseId: String): Session =
        session.transformExercisesFlat { exercise ->
            if (exercise.id != exerciseId || exercise.sets.size <= 1) exercise
            else exercise.copy(sets = exercise.sets.dropLast(1))
        }

    fun reduceRest(session: Session, seconds: Int, exerciseIds: List<String>, defaultRestSeconds: Int = 90): Session =
        session.transformExercisesFlat { exercise ->
            if (exerciseIds.isNotEmpty() && exercise.id !in exerciseIds) return@transformExercisesFlat exercise
            val currentRest = exercise.restTime ?: defaultRestSeconds
            exercise.copy(restTime = maxOf(MIN_REST_SECONDS, currentRest - seconds))
        }

    fun lowerRpeOnExercise(session: Session, exerciseId: String, amount: Double = 0.5): Session =
        session.transformExercisesFlat { exercise ->
            if (exercise.id != exerciseId) exercise else lowerEffort(exercise, amount)
        }

    fun lowerRpeOnAllExercises(session: Session): Session =
        session.transformExercisesFlat { exercise -> lowerEffort(exercise, 0.5) }

    /**
     * Baja el esfuerzo de cada serie según su magnitud real: el fallo pasa a RIR 1; el RPE baja [amount] (suelo 6); el RIR sube; y en
     * un ejercicio por %RM baja el porcentaje (5 puntos por punto de RPE, suelo 40 %), porque ahí el esfuerzo es la carga. Una serie
     * sin modo explícito se lee por lo que trae (RPE o RIR); sin ningún esfuerzo escrito no hay nada que bajar.
     */
    private fun lowerEffort(exercise: Exercise, amount: Double): Exercise =
        exercise.copy(sets = exercise.sets.map { set ->
            if (exercise.trainingMode == TrainingMode.RM && set.targetPercentageRM != null) {
                return@map set.copy(targetPercentageRM = (set.targetPercentageRM - amount * 5.0).coerceAtLeast(40.0))
            }
            val mode = set.intensityMode ?: when {
                set.targetRPE != null -> IntensityMode.RPE
                set.targetRIR != null -> IntensityMode.RIR
                else -> null
            }
            when (mode) {
                IntensityMode.FAILURE -> set.copy(intensityMode = IntensityMode.RIR, targetRIR = 1, isFailure = false)
                IntensityMode.RPE -> {
                    val currentRpe = set.targetRPE ?: 8.0
                    set.copy(targetRPE = maxOf(6.0, currentRpe - amount))
                }
                IntensityMode.RIR -> {
                    val currentRir = set.targetRIR ?: 2
                    set.copy(targetRIR = (currentRir + amount.roundToInt().coerceAtLeast(1)).coerceAtMost(5))
                }
                else -> set
            }
        })

    fun convertFailureToRir(session: Session): Session =
        session.transformExercisesFlat { exercise ->
            exercise.copy(sets = exercise.sets.map { set ->
                if (set.isFailure || set.intensityMode == IntensityMode.FAILURE) {
                    set.copy(intensityMode = IntensityMode.RIR, targetRIR = 1, targetRPE = null, isFailure = false)
                } else {
                    set
                }
            })
        }

    /**
     * Convierte las series del ejercicio en drop-set. NO es una acción de tiempo: mantiene el número de series y el descanso y
     * añade un mini-drop a cada una, así que alarga la sesión. El editor la conserva como técnica elegida; el asistente no la ofrece
     * para ahorrar tiempo.
     */
    fun convertExerciseToDropSet(session: Session, exerciseId: String): Session =
        session.transformExercisesFlat { exercise ->
            if (exercise.id != exerciseId) return@transformExercisesFlat exercise
            exercise.copy(sets = exercise.sets.map { set ->
                if (set.isDropSet) set else set.copy(
                    isDropSet = true,
                    dropSets = listOf(DropSetData(weight = set.weight ?: 0.0, reps = (set.plannedRepAnchor() ?: 8) / 2)),
                )
            })
        }

    /** Une el ejercicio con el siguiente en una superserie, salvo que alguno ya pertenezca a un grupo. */
    fun convertToSupersetWithNext(session: Session, targetExerciseId: String, params: AssistantActionParameters): Session {
        val allExercises = session.allExercises()
        val targetIdx = allExercises.indexOfFirst { it.id == targetExerciseId }
        if (targetIdx < 0) return session
        val target = allExercises.getOrNull(targetIdx) ?: return session
        val partner = allExercises.getOrNull(targetIdx + 1) ?: return session
        if (partner.id == targetExerciseId) return session
        if (target.supersetGroupRefOrLegacyId() != null || partner.supersetGroupRefOrLegacyId() != null) return session
        val anchorPartId = session.parts.firstOrNull { part -> part.exercises.any { it.id == targetExerciseId } }?.id
        return SupersetRules.createSuperset(
            session = session,
            groupId = params.newGroupId(),
            exerciseIds = listOf(targetExerciseId, partner.id),
            restBetweenExercises = params.supersetBetweenRestSeconds,
            restAfterSuperset = params.supersetRoundRestSeconds,
            rounds = null,
            anchorPartId = anchorPartId,
            anchorExerciseId = targetExerciseId,
        )
    }

    /**
     * Quita una serie del primer ejercicio que involucre el músculo. Acción antigua por músculo: el asistente nuevo apunta a un
     * ejercicio concreto ([reduceSetsForExercise]); esta se conserva para las sugerencias heredadas.
     */
    fun reduceSetsForMuscle(session: Session, muscle: String, exerciseIndex: Map<String, ExerciseMuscleInfo>): Session {
        var applied = false
        fun updateExercise(exercise: Exercise): Exercise {
            if (applied) return exercise
            val muscles = ExerciseMuscleResolver.effectiveMuscles(exercise, exerciseIndex)
            val involves = muscles.any { m ->
                VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis) == muscle
            }
            if (!involves) return exercise
            if (exercise.sets.size <= 1) return exercise
            applied = true
            return exercise.copy(sets = exercise.sets.dropLast(1))
        }
        val updatedParts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::updateExercise)) }
        val updatedExercises = session.exercises.map(::updateExercise)
        return session.copy(parts = updatedParts, exercises = updatedExercises)
    }

    // ─── Verificación de efecto ──────────────────────────────────────────────

    /** Número de series de trabajo de fuerza de la sesión (sin huecos ni ineficaces). */
    fun strengthSetCount(session: Session): Int =
        session.allExercises().distinctBy { it.id }
            .filter { it.cardioDetails == null }
            .sumOf { ex -> ex.sets.count { !it.isIneffective && !it.isEmptySlot } }

    fun durationSeconds(session: Session): Int = SessionDurationEstimator.estimate(session).totalSeconds

    /** Efecto de pasar de [before] a [after] con el reloj común y el recuento de series. */
    fun measure(before: Session, after: Session): AssistantActionEffect = AssistantActionEffect(
        deltaSeconds = durationSeconds(after) - durationSeconds(before),
        deltaSets = strengthSetCount(after) - strengthSetCount(before),
    )
}
