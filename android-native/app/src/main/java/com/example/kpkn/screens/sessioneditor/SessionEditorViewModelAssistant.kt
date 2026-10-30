package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionPreview

/*
 * Deshacer y Descartar del asistente (pulido `as-sug-lista`, `as-sug-aplicada`, `as-pla-aplicada`).
 *
 * El motor aplica sobre una sesión inmutable, así que volver atrás es guardar la sesión previa. Cada entrada recuerda la marca de
 * última modificación que dejó su acción: si la persona edita algo más, la marca cambia y la entrada se descarta sola, para que
 * «Deshacer» nunca pise cambios posteriores.
 */

private fun SessionEditorViewModel.activeStamp(): Long? = currentUiState.activeVariantSession?.lastModifiedAtMs

/** Aplica las casillas marcadas de la sugerencia y la pliega en una línea con «Deshacer». */
fun SessionEditorViewModel.applyAssistantSuggestionUndoable(
    suggestion: AssistantSuggestion,
    acceptedDetailIds: List<String>,
    position: Int,
) {
    val before = currentUiState.activeVariantSession ?: return
    val accepted = suggestion.details.filter { it.id in acceptedDetailIds }
    applyAssistantSuggestion(suggestion.id, acceptedDetailIds)
    val saved = AssistantSuggestionPreview.savedMinutes(accepted)
    val changes = accepted.size.coerceAtLeast(1)
    val summary = buildString {
        append(changes)
        append(if (changes == 1) " cambio" else " cambios")
        if (saved > 0) append(" · −$saved min")
    }
    val outcome = AssistantSuggestionOutcome(
        suggestionId = suggestion.id,
        kind = AssistantSuggestionOutcome.Kind.APPLIED,
        summary = summary,
        before = before,
        variant = currentUiState.activeVariant,
        stamp = activeStamp(),
        position = position,
    )
    updateUi { state -> state.copy(assistantOutcomes = state.assistantOutcomes.filterNot { it.suggestionId == suggestion.id } + outcome) }
}

/** «Descartar»: la sugerencia no vuelve a salir mientras la sesión no cambie. */
fun SessionEditorViewModel.dismissAssistantSuggestion(suggestionId: String, position: Int) {
    val outcome = AssistantSuggestionOutcome(
        suggestionId = suggestionId,
        kind = AssistantSuggestionOutcome.Kind.DISMISSED,
        summary = "",
        before = null,
        variant = currentUiState.activeVariant,
        stamp = activeStamp(),
        position = position,
    )
    updateUi { state -> state.copy(assistantOutcomes = state.assistantOutcomes.filterNot { it.suggestionId == suggestionId } + outcome) }
}

/** «Deshacer» de una línea plegada: devuelve la sesión previa si se aplicó y la sugerencia vuelve a la lista. */
fun SessionEditorViewModel.undoAssistantOutcome(suggestionId: String) {
    val outcome = currentUiState.assistantOutcomes.firstOrNull { it.suggestionId == suggestionId } ?: return
    updateUi { state -> state.copy(assistantOutcomes = state.assistantOutcomes.filterNot { it.suggestionId == suggestionId }) }
    val before = outcome.before ?: return
    if (currentUiState.activeVariant == outcome.variant && outcome.stamp == activeStamp()) {
        updateSession(reason = "Deshacer sugerencia") { before }
        refreshAssistantImmediate()
    }
}

/** Deshace la última plantilla aplicada desde la hoja y cierra su resultado. */
fun SessionEditorViewModel.undoTemplateApply() {
    val done = currentUiState.templateApplyProgress as? TemplateApplyProgress.Done ?: return
    updateUi { it.copy(templateApplyProgress = null) }
    if (currentUiState.activeVariant == done.variant && done.stamp == activeStamp()) {
        updateSession(reason = "Deshacer plantilla") { current -> current.restoreStructureFrom(done.before) }
        refreshAssistantImmediate()
    }
}

/** «Ver mi sesión»: cierra el resultado de la plantilla (y con él la hoja). */
fun SessionEditorViewModel.finishTemplateApply() {
    updateUi { it.copy(templateApplyProgress = null, sheet = SessionEditorSheet.NONE) }
}

/** Los campos de contenido que una plantilla reescribe; el resto de la sesión (nombre, descripción, fondo…) no se toca. */
internal fun Session.restoreStructureFrom(source: Session): Session = copy(
    exercises = source.exercises,
    parts = source.parts,
    warmup = source.warmup,
    supersetGroups = source.supersetGroups,
    origin = source.origin,
    cardioFirst = source.cardioFirst,
    targetDurationMinutes = source.targetDurationMinutes,
    sessionB = source.sessionB,
    sessionC = source.sessionC,
    sessionD = source.sessionD,
    trainingBackup = source.trainingBackup,
)
