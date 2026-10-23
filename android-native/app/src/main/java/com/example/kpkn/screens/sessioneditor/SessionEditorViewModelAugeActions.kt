package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.AssistantActionParameters
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.SessionAssistantActions

/**
 * Las acciones del asistente las define el dominio ([SessionAssistantActions]): el asistente las simula con esas mismas funciones
 * antes de ofrecerlas, así que lo que anuncia es lo que aquí se aplica. Estas extensiones solo aportan el entorno del editor
 * (catálogo y descansos de superserie de Reglas).
 */
private fun SessionEditorViewModel.actionParameters(): AssistantActionParameters = AssistantActionParameters(
    exerciseIndex = exerciseIndex,
    supersetBetweenRestSeconds = currentUiState.ruleDefaults.supersetBetweenRestSeconds,
    supersetRoundRestSeconds = currentUiState.ruleDefaults.supersetRoundRestSeconds,
)

fun SessionEditorViewModel.applyAssistantSuggestion(
    suggestionId: String,
    acceptedDetailIds: List<String>? = null,
) {
    val suggestion = currentUiState.assistantReport?.ajustes
        ?.firstOrNull { it.id == suggestionId } ?: return

    val details = if (!acceptedDetailIds.isNullOrEmpty()) {
        suggestion.details.filter { it.id in acceptedDetailIds }
    } else if (suggestion.details.isNotEmpty()) {
        suggestion.details.filter { it.defaultAccepted }
    } else {
        emptyList()
    }

    if (details.isNotEmpty()) {
        updateSession { session ->
            var next = session
            details.forEach { detail ->
                // A4b: skip superset si ya está agrupado (evita solapamiento ex₁ en 2 sugerencias)
                if (detail.action is AssistantDetailAction.ConvertToSuperset) {
                    val exId = detail.action.exerciseId
                    val all = next.allExercises()
                    val idx = all.indexOfFirst { it.id == exId }
                    val target = all.getOrNull(idx)
                    val partner = all.getOrNull(idx + 1)
                    if (target?.supersetGroupRefOrLegacyId() != null || partner?.supersetGroupRefOrLegacyId() != null) {
                        return@forEach
                    }
                }
                next = applyAssistantDetail(next, detail)
            }
            next
        }
        return
    }

    // Legacy single-action suggestions (no details)
    when (suggestion.type) {
        AssistantActionType.REDUCE_SET -> {
            val muscle = suggestion.muscle
            if (muscle != null) {
                updateSession { session -> reduceSetsForMuscle(session, muscle) }
            }
        }
        AssistantActionType.LOWER_RPE -> {
            updateSession { session ->
                if (suggestion.exerciseId != null) {
                    lowerRpeOnExercise(session, suggestion.exerciseId)
                } else {
                    lowerRpeOnAllExercises(session)
                }
            }
        }
        AssistantActionType.REMOVE_FAILURE -> {
            updateSession { session -> convertFailureToRir(session) }
        }
        AssistantActionType.REDUCE_REST_TIME -> {
            updateSession { session ->
                session.transformExercises { exercise ->
                    val currentRest = exercise.restTime ?: 90
                    exercise.copy(restTime = maxOf(30, currentRest - 15))
                }
            }
        }
        AssistantActionType.CONVERT_TO_DROPSET -> {
            val exerciseId = suggestion.exerciseId ?: return
            updateSession { session -> convertExerciseToDropSet(session, exerciseId) }
        }
        AssistantActionType.CONVERT_TO_SUPERSET -> {
            val targetExerciseId = suggestion.exerciseId ?: return
            updateSession { session -> convertToSupersetWithNext(session, targetExerciseId) }
        }
        AssistantActionType.APPLY_TEMPLATE -> {
            val preview = currentUiState.assistantReport?.plantillasCompatibles?.firstOrNull()
            if (preview == null) {
                updateUi { it.copy(snackbarMessage = "No hay una plantilla compatible para aplicar.") }
            } else {
                selectTemplate(preview.template)
            }
        }
        else -> Unit
    }
}

private fun SessionEditorViewModel.applyAssistantDetail(
    session: Session,
    detail: AssistantSuggestionDetail,
): Session = SessionAssistantActions.apply(session, detail.action, actionParameters())

internal fun SessionEditorViewModel.reduceSetsForExercise(session: Session, exerciseId: String): Session =
    SessionAssistantActions.reduceSetsForExercise(session, exerciseId)

internal fun SessionEditorViewModel.lowerRpeOnExercise(
    session: Session,
    exerciseId: String,
    amount: Double = 0.5,
): Session = SessionAssistantActions.lowerRpeOnExercise(session, exerciseId, amount)

internal fun SessionEditorViewModel.convertExerciseToDropSet(session: Session, exerciseId: String): Session =
    SessionAssistantActions.convertExerciseToDropSet(session, exerciseId)

internal fun SessionEditorViewModel.convertToSupersetWithNext(session: Session, targetExerciseId: String): Session =
    SessionAssistantActions.convertToSupersetWithNext(session, targetExerciseId, actionParameters())

internal fun SessionEditorViewModel.reduceSetsForMuscle(session: Session, muscle: String): Session =
    SessionAssistantActions.reduceSetsForMuscle(session, muscle, exerciseIndex)

internal fun SessionEditorViewModel.lowerRpeOnAllExercises(session: Session): Session =
    SessionAssistantActions.lowerRpeOnAllExercises(session)

internal fun SessionEditorViewModel.convertFailureToRir(session: Session): Session =
    SessionAssistantActions.convertFailureToRir(session)
