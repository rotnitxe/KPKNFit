package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.VolumeCalculator

/** Cómo está la sesión frente a un límite: la línea que se muestra bajo su fila y si choca. */
data class LimitStatus(val text: String, val within: Boolean)

/** Los cinco límites de Reglas › Límites con su estado actual; null = ese límite no está puesto. */
data class SessionLimitStatuses(
    val maxRpe: LimitStatus?,
    val exercisesPerMuscle: LimitStatus?,
    val volumePerMuscleSession: LimitStatus?,
    val volumePerMuscleWeekly: LimitStatus?,
    val samePattern: LimitStatus?,
) {
    val anyClash: Boolean
        get() = listOfNotNull(maxRpe, exercisesPerMuscle, volumePerMuscleSession, volumePerMuscleWeekly, samePattern).any { !it.within }
}

/**
 * La sesión frente a cada límite (`pulido:as-reg-limites`): «dentro» o lo que lo supera, con las mismas cuentas que valida el
 * editor al guardar (`SessionEditorRulesEngine.validateBeforeSave`). Kotlin puro.
 */
object AssistantLimitStatus {

    fun compute(
        session: Session,
        weekSessions: List<Session>,
        maxRpe: Double?,
        maxExercisesPerMuscle: Int?,
        maxVolumePerMuscleSession: Double?,
        maxVolumePerMuscleWeekly: Double?,
        maxSamePattern: Int?,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): SessionLimitStatuses {
        val exercises = session.allExercises().distinctBy { it.id }.filter { it.cardioDetails == null }
        val num = AssistantSuggestionPreview::number
        return SessionLimitStatuses(
            maxRpe = maxRpe?.let { limit ->
                val worst = exercises.mapNotNull { ex -> ex.sets.mapNotNull { plannedRpe(it) }.maxOrNull() }
                val over = worst.count { it > limit }
                if (over > 0) LimitStatus("$over ${if (over == 1) "ejercicio" else "ejercicios"} por encima", false)
                else LimitStatus(worst.maxOrNull()?.let { "El más alto: ${num(it)} · dentro" } ?: "Sin esfuerzo que comparar · dentro", true)
            },
            exercisesPerMuscle = maxExercisesPerMuscle?.let { limit ->
                val byMuscle = linkedMapOf<String, Int>()
                exercises.forEach { ex ->
                    val info = resolveCatalogExerciseInfoInIndex(
                        index = exerciseIndex,
                        catalogConfigurationId = ex.catalogConfigurationId,
                        exerciseDbId = ex.exerciseDbId,
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.name,
                    ) ?: return@forEach
                    val primary = info.involvedMuscles.firstOrNull { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
                        ?: info.involvedMuscles.firstOrNull()
                    if (primary != null) {
                        val key = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
                        byMuscle[key] = (byMuscle[key] ?: 0) + 1
                    }
                }
                val top = byMuscle.maxByOrNull { it.value }
                when {
                    top == null -> LimitStatus("Sin ejercicios que contar · dentro", true)
                    top.value > limit -> LimitStatus("${top.key} tiene ${top.value}", false)
                    else -> LimitStatus("El máximo es ${top.value} · dentro", true)
                }
            },
            volumePerMuscleSession = maxVolumePerMuscleSession?.let { limit ->
                val top = AssistantMuscleBreakdown.rows(session, exerciseIndex).firstOrNull()
                when {
                    top == null -> LimitStatus("Sin series que contar · dentro", true)
                    top.volume > limit -> LimitStatus("${top.muscle} tiene ${num(top.volume)}", false)
                    else -> LimitStatus("El máximo: ${top.muscle} ${num(top.volume)} · dentro", true)
                }
            },
            volumePerMuscleWeekly = maxVolumePerMuscleWeekly?.let { limit ->
                val week = if (weekSessions.any { it.id == session.id }) weekSessions.map { if (it.id == session.id) session else it } else weekSessions + session
                val top = AssistantMuscleBreakdown.weekly(week, exerciseIndex).maxByOrNull { it.value }
                when {
                    top == null -> LimitStatus("Sin series que contar · dentro", true)
                    top.value > limit -> LimitStatus("${top.key} lleva ${num(top.value)}", false)
                    else -> LimitStatus("${top.key} lleva ${num(top.value)} · dentro", true)
                }
            },
            samePattern = maxSamePattern?.let { limit ->
                val byPattern = linkedMapOf<String, Int>()
                exercises.forEach { ex ->
                    val info = resolveCatalogExerciseInfoInIndex(
                        index = exerciseIndex,
                        catalogConfigurationId = ex.catalogConfigurationId,
                        exerciseDbId = ex.exerciseDbId,
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.name,
                    ) ?: return@forEach
                    val pattern = info.force?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    byPattern[pattern] = (byPattern[pattern] ?: 0) + 1
                }
                val top = byPattern.maxByOrNull { it.value }
                when {
                    top == null -> LimitStatus("Sin patrones que contar · dentro", true)
                    top.value > limit -> LimitStatus("${top.key}: ${top.value}", false)
                    else -> LimitStatus("${top.key}: ${top.value} · dentro", true)
                }
            },
        )
    }

    private fun plannedRpe(set: com.example.kpkn.data.models.ExerciseSet): Double? = when {
        set.isIneffective || set.isEmptySlot -> null
        set.isFailure -> 10.0
        set.targetRPE != null -> set.targetRPE.coerceIn(1.0, 10.0)
        set.targetRIR != null -> (10 - set.targetRIR).toDouble().coerceIn(1.0, 10.0)
        else -> null
    }
}
