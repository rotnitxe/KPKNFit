package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.sessions.SessionTemplateTag
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.kpkn.domain.templates.SessionTemplateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.kpkn.domain.workout.SupersetRules
import java.util.UUID

fun SessionEditorViewModel.openTemplates() {
    openSheet(SessionEditorSheet.TEMPLATES)
}

/** Updates the free-text filter in the template browser. */
fun SessionEditorViewModel.setTemplateSearchQuery(query: String) {
    updateUi { it.copy(templateSearchQuery = query) }
}

/**
 * Called when the user taps a template card.
 *
 * - If the current session is empty, applies the template immediately.
 * - Otherwise, stores an [SessionTemplateApplyDecision] asking the user to
 *   choose between [SessionTemplateApplyMode.REPLACE] and [SessionTemplateApplyMode.APPEND].
 */
fun SessionEditorViewModel.selectTemplate(template: SessionTemplate) {
    val session = currentUiState.session ?: return
    if (SessionTemplateEngine.sessionHasContent(session)) {
        updateUi {
            it.copy(templateApplyDecision = SessionTemplateApplyDecision(template))
        }
    } else {
        applyTemplateInternal(template, SessionTemplateApplyMode.REPLACE)
    }
}

/**
 * Confirms the pending template apply with [mode].
 * No-op when there is no pending decision.
 */
fun SessionEditorViewModel.confirmTemplateApply(mode: SessionTemplateApplyMode) {
    val decision = currentUiState.templateApplyDecision ?: return
    applyTemplateInternal(decision.template, mode)
}

/** Cancels a pending template apply decision. */
fun SessionEditorViewModel.cancelTemplateApply() {
    updateUi { it.copy(templateApplyDecision = null) }
}

/**
 * Saves the current session as a new user template with the given metadata.
 * Returns `true` on success, `false` if there is no session to save.
 */
fun SessionEditorViewModel.saveCurrentSessionAsTemplate(
    name: String,
    description: String,
    tags: List<SessionTemplateTag>,
): Boolean {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) {
        Log.w("SessionEditorVM", "saveCurrentSessionAsTemplate: nombre vacio")
        return false
    }
    val session = currentUiState.session ?: return false
    if (session.allExercises().size > 12) {
        Log.w("SessionEditorVM", "Plantilla grande ${session.allExercises().size} ejercicios")
    }
    val now = java.time.Instant.now().toString()
    val allExercises = session.exercises + session.parts.flatMap { it.exercises }
    val template = SessionTemplate(
        id = UUID.randomUUID().toString(),
        sourceType = SessionTemplateSourceType.USER,
        name = name.trim(),
        description = description.trim(),
        tags = tags,
        exerciseCount = allExercises.size,
        partCount = session.parts.size,
        session = session,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
    )
    templateRepository.saveUserTemplate(template)
    return true
}

internal fun SessionEditorViewModel.applyTemplateInternal(template: SessionTemplate, mode: SessionTemplateApplyMode) {
    val session = currentUiState.session ?: return
    // ensure large templates don't block UI
    if (template.session.allExercises().size > 12) {
        Log.w("SessionEditorVM", "applyTemplateInternal plantilla grande ${template.id}")
    }
    viewModelScope.launch {
        val prepared = withContext(Dispatchers.Default) { template }
        val result = SessionTemplateEngine.applyTemplate(prepared, session, mode)
        updateSession { result }
        updateUi { it.copy(sheet = SessionEditorSheet.NONE, templateApplyDecision = null, templateSearchQuery = "") }
        return@launch
    }
    // fallback for tests without scope - keep original sync path for now
    
    val result = SessionTemplateEngine.applyTemplate(template, session, mode)
    updateSession { result }
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.NONE,
            templateApplyDecision = null,
            templateSearchQuery = "",
        )
    }
}
