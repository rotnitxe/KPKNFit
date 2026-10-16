package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isCardio

internal const val RecordingEffortRequired = "Marca tu esfuerzo RIR o RPE antes de registrar la serie."

/** Solo una edición sin un nuevo informe puede conservar el esfuerzo real ya guardado. El objetivo del plan nunca entra aquí. */
internal fun preserveExistingRecordingEffort(intensity: Double?, advanced: SetAdvancedFeedback, existing: CompletedSet?): SetAdvancedFeedback {
    val hasReport = intensity != null || advanced.actualIntensityMode != null || advanced.actualIntensityValue != null || advanced.rir != null || advanced.reachedFailure
    if (hasReport || existing == null) return advanced
    val storedMode = existing.actualIntensityMode?.takeUnless {
        it == IntensityMode.FAILURE && !existing.isFailure && existing.recordedPayloadV3?.reachedFailure != true
    }
    val mode = storedMode ?: when {
        existing.isFailure -> IntensityMode.FAILURE
        existing.rir != null -> IntensityMode.RIR
        existing.rpe != null -> IntensityMode.RPE
        else -> null
    }
    return advanced.copy(
        rir = existing.rir,
        reachedFailure = existing.isFailure,
        actualIntensityMode = mode,
        actualIntensityValue = existing.actualIntensityValue ?: if (mode == IntensityMode.RIR) existing.rir?.toDouble() else existing.rpe,
        intensityAdjusted = existing.recordedPayloadV3?.intensityAdjusted ?: true,
    )
}

/** La escala de la app admite RPE 0…10 y RIR por encima de 10; ni NaN ni el guion de SOLO_RM son informes. */
internal fun recordingEffortError(intensity: Double?, advanced: SetAdvancedFeedback): String? {
    if (!advanced.intensityAdjusted) return RecordingEffortRequired
    if (intensity?.isFinite() == false || advanced.actualIntensityValue?.isFinite() == false || (advanced.rir ?: 0) < 0) {
        return "Revisa el esfuerzo: el valor debe ser finito y no negativo."
    }
    if (advanced.reachedFailure || advanced.actualIntensityMode == IntensityMode.FAILURE) return null
    val value = advanced.actualIntensityValue ?: advanced.rir?.toDouble() ?: intensity ?: return RecordingEffortRequired
    val mode = advanced.actualIntensityMode ?: if (advanced.rir != null) IntensityMode.RIR else IntensityMode.RPE
    return when {
        value < 0.0 -> "Revisa el esfuerzo: el valor no puede ser negativo."
        mode == IntensityMode.RPE && value > 10.0 -> "Revisa el esfuerzo: el RPE debe estar entre 0 y 10."
        mode == IntensityMode.RPE || mode == IntensityMode.RIR -> null
        // AMRAP informa reserva o fallo; estar planificado como AMRAP por sí solo no basta.
        mode == IntensityMode.AMRAP && advanced.rir != null -> null
        else -> RecordingEffortRequired
    }
}

/** Normaliza solo el informe ya validado; nunca completa nada desde los objetivos del plan. */
internal fun normalizeRecordingEffort(intensity: Double?, advanced: SetAdvancedFeedback): SetAdvancedFeedback {
    val mode = advanced.actualIntensityMode ?: when {
        advanced.reachedFailure -> IntensityMode.FAILURE
        advanced.rir != null -> IntensityMode.RIR
        else -> IntensityMode.RPE
    }
    val value = advanced.actualIntensityValue ?: advanced.rir?.toDouble() ?: intensity
    return advanced.copy(
        actualIntensityMode = mode,
        actualIntensityValue = value,
        rir = when (mode) {
            IntensityMode.RIR -> value?.toInt()
            IntensityMode.AMRAP -> advanced.rir
            else -> null
        },
        reachedFailure = advanced.reachedFailure || mode == IntensityMode.FAILURE,
    )
}

/** Completar el reloj de fuerza devuelve a su informe; descanso, cardio y preparaciones conservan su navegación. */
internal fun timedStrengthStepAwaitingReport(state: WorkoutUiState, exercises: List<Exercise>, steps: List<WorkoutStep>): WorkoutStep? {
    if (state.isRestTimerRunning || state.restModalState != null) return null
    val active = state.activeStepKey?.let { key -> steps.firstOrNull { it.stepKey == key } }
    if (active != null && active.type != WorkoutStepType.WORKING_SET) return null
    val exercise = exercises.getOrNull(state.currentExerciseIdx)?.takeUnless { it.isCardio } ?: return null
    val set = exercise.sets.getOrNull(state.currentSetIdx) ?: return null
    if (set.unitModeV2 != UnitModeV2.TIME && set.targetDuration == null && exercise.trainingMode != TrainingMode.TIME) return null
    val step = active ?: steps.firstOrNull { it.type == WorkoutStepType.WORKING_SET && it.exerciseId == exercise.id && it.setIndex == state.currentSetIdx } ?: return null
    val index = step.setIndex ?: return null
    val completed = if (step.side != null) {
        buildCompletedSetKey(exercise.id, index, step.side) in state.completedSets
    } else isSetDone(state.completedSets, exercise.id, index, exercise.isEffectivelyUnilateral())
    if (completed) return null
    return step
}
