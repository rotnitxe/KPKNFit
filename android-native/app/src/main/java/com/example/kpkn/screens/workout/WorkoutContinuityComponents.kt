package com.example.kpkn.screens.workout

/**
 * Continuity transition UI retained for androidTest fixtures.
 * El feedback del ejercicio en producción usa [WorkoutPostExerciseFeedbackContent], solo desde la capa del descanso (WorkoutRestOverlayHost).
 */

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.exercises.displayNameWithSelectedChips

internal enum class WorkoutContinuityPhase {
    CURRENT_EXERCISE,
    SUPERSET,
    NEXT_EXERCISE,
    NEXT_BLOCK,
    SESSION_FINISH,
}

internal data class WorkoutContinuityState(
    val phase: WorkoutContinuityPhase,
    val eyebrow: String,
    val title: String,
    val body: String,
    val progressLabel: String,
    val nextExerciseName: String? = null,
    val nextSetLabel: String? = null,
    val accentHex: String? = null,
    val feedbackPrompt: String? = null,
)

data class WorkoutContinuityTransitionTarget(
    val key: String,
    val eyebrow: String,
    val title: String,
    val body: String,
    val accentHex: String? = null,
)

internal fun buildWorkoutContinuityState(
    session: Session,
    visibleExercises: List<Exercise>,
    currentExerciseIdx: Int,
    currentSetIdx: Int,
    feedbackPrompt: String? = null,
): WorkoutContinuityState? {
    val currentExercise = visibleExercises.getOrNull(currentExerciseIdx) ?: return null
    val currentPart = session.partForExercise(currentExercise.id)
    val visibleParts = session.visibleParts(visibleExercises)
    val currentPartIndex = currentPart?.let { part -> visibleParts.indexOfFirst { it.id == part.id }.takeIf { it >= 0 } }
    val blockLabel = when {
        currentPart != null && currentPartIndex != null -> "Bloque ${currentPartIndex + 1} de ${visibleParts.size}"
        currentPart != null -> "Bloque activo"
        else -> "Sesión activa"
    }
    val progressLabel = "Ejercicio ${currentExerciseIdx + 1} de ${visibleExercises.size}"
    val currentSetNumber = currentSetIdx + 1

    val supersetTarget = nextSupersetHop(visibleExercises, currentExerciseIdx, currentSetIdx)
    if (supersetTarget != null) {
        val nextExercise = visibleExercises[supersetTarget.first]
        return WorkoutContinuityState(
            phase = WorkoutContinuityPhase.SUPERSET,
            eyebrow = blockLabel,
            title = "Empalma con ${nextExercise.displayNameWithSelectedChips()}",
            body = "Superset en la misma ronda. Después de cerrar aquí, sigues con Serie ${supersetTarget.second + 1}.",
            progressLabel = progressLabel,
            nextExerciseName = nextExercise.displayNameWithSelectedChips(),
            nextSetLabel = "Serie ${supersetTarget.second + 1}",
            accentHex = currentPart?.color,
            feedbackPrompt = feedbackPrompt,
        )
    }

    val lastSetIndex = currentExercise.sets.lastIndex.coerceAtLeast(0)
    if (currentSetIdx < lastSetIndex) {
        val remainingSets = lastSetIndex - currentSetIdx
        return WorkoutContinuityState(
            phase = WorkoutContinuityPhase.CURRENT_EXERCISE,
            eyebrow = blockLabel,
            title = if (remainingSets == 1) {
                "Última serie para cerrar ${currentExercise.displayNameWithSelectedChips()}"
            } else {
                "Quedan $remainingSets series en ${currentExercise.displayNameWithSelectedChips()}"
            },
            body = "Mantienes el foco en ${currentPart?.name ?: session.name}. Ahora vas por Serie $currentSetNumber.",
            progressLabel = progressLabel,
            nextExerciseName = currentExercise.displayNameWithSelectedChips(),
            nextSetLabel = "Serie ${currentSetNumber + 1}",
            accentHex = currentPart?.color,
            feedbackPrompt = feedbackPrompt,
        )
    }

    val nextExercise = visibleExercises.getOrNull(currentExerciseIdx + 1)
    if (nextExercise == null) {
        return WorkoutContinuityState(
            phase = WorkoutContinuityPhase.SESSION_FINISH,
            eyebrow = blockLabel,
            title = "Cierras ${currentPart?.name ?: session.name}",
            body = "Esta es la última estación de la sesión. Al completar la serie, pasas al cierre final.",
            progressLabel = progressLabel,
            accentHex = currentPart?.color,
            feedbackPrompt = feedbackPrompt,
        )
    }

    val nextPart = session.partForExercise(nextExercise.id)
    val entersNewBlock = currentPart?.id != nextPart?.id && nextPart != null
    return WorkoutContinuityState(
        phase = if (entersNewBlock) WorkoutContinuityPhase.NEXT_BLOCK else WorkoutContinuityPhase.NEXT_EXERCISE,
        eyebrow = if (entersNewBlock) "Cambio de bloque" else blockLabel,
        title = if (entersNewBlock) {
            "Se abre ${nextPart.name}"
        } else {
            "Sigue con ${nextExercise.displayNameWithSelectedChips()}"
        },
        body = if (entersNewBlock) {
            "Cierras ${currentExercise.displayNameWithSelectedChips()} y entras al siguiente bloque con una transición limpia."
        } else {
            "Después de esta serie, el flujo continúa sin salir del ritmo actual."
        },
        progressLabel = progressLabel,
        nextExerciseName = nextExercise.displayNameWithSelectedChips(),
        nextSetLabel = "Serie 1",
        accentHex = nextPart?.color ?: currentPart?.color,
        feedbackPrompt = feedbackPrompt,
    )
}

internal fun buildWorkoutContinuityTransitionTarget(
    session: Session,
    visibleExercises: List<Exercise>,
    currentExerciseIdx: Int,
): WorkoutContinuityTransitionTarget? {
    if (currentExerciseIdx <= 0) return null
    val currentExercise = visibleExercises.getOrNull(currentExerciseIdx) ?: return null
    val previousExercise = visibleExercises.getOrNull(currentExerciseIdx - 1)
    val currentPart = session.partForExercise(currentExercise.id)
    val previousPart = previousExercise?.let { session.partForExercise(it.id) }
    val entersNewBlock = currentPart?.id != previousPart?.id && currentPart != null
    return WorkoutContinuityTransitionTarget(
        key = "${currentExercise.id}_${currentExerciseIdx}",
        eyebrow = if (entersNewBlock) "Nuevo bloque" else "Siguiente estación",
        title = if (entersNewBlock) {
            "Entras a ${currentPart.name}"
        } else {
            "Ahora sigue ${currentExercise.displayNameWithSelectedChips()}"
        },
        body = if (entersNewBlock) {
            "El flujo cambia de bloque, pero mantienes el ritmo de la sesión."
        } else {
            "La transición se siente limpia: cambias de ejercicio sin perder contexto."
        },
        accentHex = currentPart?.color,
    )
}

internal fun pendingWorkoutFeedbackHandoffExercise(
    visibleExercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    loggedSetKey: String?,
    currentExerciseId: String?,
): Exercise? {
    val sourceKey = loggedSetKey ?: return null
    val loggedExercise = visibleExercises
        .sortedByDescending { it.id.length }
        .firstOrNull { sourceKey == it.id || sourceKey.startsWith("${it.id}_") }
        ?: return null
    if (loggedExercise.id == currentExerciseId) return null
    if (postExerciseFeedbackByExerciseId.containsKey(loggedExercise.id)) return null
    val isComplete = loggedExercise.sets.isNotEmpty() && loggedExercise.sets.indices.all { setIdx ->
        completedSets.containsKey("${loggedExercise.id}_$setIdx") ||
            (loggedExercise.isUnilateral &&
                completedSets.containsKey("${loggedExercise.id}_${setIdx}_L") &&
                completedSets.containsKey("${loggedExercise.id}_${setIdx}_R"))
    }
    return loggedExercise.takeIf { isComplete }
}

private fun nextSupersetHop(
    exercises: List<Exercise>,
    currentIdx: Int,
    currentSetIdx: Int,
): Pair<Int, Int>? {
    val groupId = exercises.getOrNull(currentIdx)?.supersetGroupRefOrLegacyId() ?: return null
    val group = exercises.indices.filter { exercises[it].supersetGroupRefOrLegacyId() == groupId }
    if (group.size <= 1) return null

    val position = group.indexOf(currentIdx)
    if (position < 0) return null

    for (groupPos in (position + 1) until group.size) {
        val candidateExerciseIdx = group[groupPos]
        if (currentSetIdx in exercises[candidateExerciseIdx].sets.indices) {
            return candidateExerciseIdx to currentSetIdx
        }
    }

    return null
}

private fun Session.partForExercise(exerciseId: String): SessionPart? =
    parts.firstOrNull { part -> part.exercises.any { it.id == exerciseId } }

private fun Session.visibleParts(visibleExercises: List<Exercise>): List<SessionPart> {
    val visibleIds = visibleExercises.map { it.id }.toSet()
    return parts.filter { part -> part.exercises.any { it.id in visibleIds } }
}
