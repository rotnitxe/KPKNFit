package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
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
    val session = currentUiState.activeVariantSession ?: return
    if (!SessionTemplateEngine.canApplyTemplate(template, session)) {
        updateUi {
            it.copy(
                snackbarMessage = "Esta plantilla no está disponible para una sesión de competición o aún no fue validada.",
            )
        }
        return
    }
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

/** Durable USER-template lifecycle commands exposed to the editor catalog. */
fun SessionEditorViewModel.archiveUserTemplate(id: String) {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { templateRepository.archiveUserTemplateNow(id) }
        updateUi { it.copy(snackbarMessage = if (result.isSuccess) "Plantilla archivada." else "No se pudo archivar la plantilla.") }
    }
}

fun SessionEditorViewModel.restoreUserTemplate(id: String) {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { templateRepository.restoreUserTemplateNow(id) }
        updateUi { it.copy(snackbarMessage = if (result.isSuccess) "Plantilla restaurada." else "No se pudo restaurar la plantilla.") }
    }
}

fun SessionEditorViewModel.deleteUserTemplate(id: String) {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { templateRepository.deleteUserTemplateNow(id) }
        updateUi { it.copy(snackbarMessage = if (result.isSuccess) "Plantilla eliminada." else "No se pudo eliminar la plantilla.") }
    }
}

/** Persists editable metadata before exposing the updated USER template. */
fun SessionEditorViewModel.updateUserTemplateMetadata(
    template: SessionTemplate,
    name: String,
    description: String,
    difficulty: com.example.kpkn.data.splits.Difficulty = template.difficulty,
    focusCategory: com.example.kpkn.data.sessions.SessionTemplateFocusCategory? = template.focusCategory,
    durationClass: com.example.kpkn.data.sessions.SessionTemplateDurationClass = template.durationClass,
    splitIds: List<String> = template.splitIds,
    splitDayLabels: List<String> = template.splitDayLabels,
    autoGenerationEligible: Boolean = template.autoGenerationEligible,
) {
    viewModelScope.launch {
        val result = updateUserTemplateMetadataNow(
            template = template,
            name = name,
            description = description,
            difficulty = difficulty,
            focusCategory = focusCategory,
            durationClass = durationClass,
            splitIds = splitIds,
            splitDayLabels = splitDayLabels,
            autoGenerationEligible = autoGenerationEligible,
        )
        updateUi { it.copy(snackbarMessage = if (result.isSuccess) "Metadatos actualizados." else "No se pudieron actualizar los metadatos.") }
    }
}

/** Durable metadata command used by the editor dialog before it dismisses. */
suspend fun SessionEditorViewModel.updateUserTemplateMetadataNow(
    template: SessionTemplate,
    name: String,
    description: String,
    difficulty: com.example.kpkn.data.splits.Difficulty = template.difficulty,
    focusCategory: com.example.kpkn.data.sessions.SessionTemplateFocusCategory? = template.focusCategory,
    durationClass: com.example.kpkn.data.sessions.SessionTemplateDurationClass = template.durationClass,
    splitIds: List<String> = template.splitIds,
    splitDayLabels: List<String> = template.splitDayLabels,
    autoGenerationEligible: Boolean = template.autoGenerationEligible,
): Result<Unit> {
    val normalized = template.copy(
        name = name.trim().ifBlank { template.name },
        description = description.trim(),
        difficulty = difficulty,
        focusCategory = focusCategory,
        durationClass = durationClass,
        splitIds = splitIds,
        splitDayLabels = splitDayLabels,
        autoGenerationEligible = autoGenerationEligible,
    )
    return withContext(Dispatchers.IO) { templateRepository.updateUserTemplateNow(normalized) }
}

private fun SessionEditorViewModel.buildCurrentSessionTemplate(
    name: String,
    description: String,
    tags: List<SessionTemplateTag>,
): SessionTemplate? {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) {
        Log.w("SessionEditorVM", "saveCurrentSessionAsTemplate: nombre vacio")
        return null
    }
    // A/B/C/D editors must materialize the active variant.  The base editor
    // deliberately has no active variant, though, and still needs a durable
    // blueprint save from the canonical session shown on screen.
    val session = currentUiState.activeVariantSession ?: currentUiState.session ?: return null
    if (!SessionTemplateEngine.sessionHasCompleteExecutableContent(session)) {
        Log.w("SessionEditorVM", "saveCurrentSessionAsTemplate: sesión sin prescripción completa")
        return null
    }
    if (session.allExercises().size > 12) {
        Log.w("SessionEditorVM", "Plantilla grande ${session.allExercises().size} ejercicios")
    }
    val now = java.time.Instant.now().toString()
    val allExercises = session.exercises + session.parts.flatMap { it.exercises }
    return SessionTemplate(
        id = UUID.randomUUID().toString(),
        sourceType = SessionTemplateSourceType.USER,
        name = name.trim(),
        description = description.trim(),
        tags = tags,
        exerciseCount = allExercises.size,
        partCount = session.parts.size,
        // Never persist completed/live state, variants or meet metadata in a
        // reusable blueprint. The canonical cloner is also responsible for IDs.
        session = SessionTemplateEngine.cloneForTemplateStorage(session),
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
        publicationStatus = SessionTemplatePublicationStatus.KPKN_NATIVE,
        autoGenerationEligible = false,
    )
}

/** Durable save entrypoint used by the editor UI. The Result is emitted only
 * after Room has accepted the row; failures never publish a phantom Flow item. */
fun SessionEditorViewModel.saveCurrentSessionAsTemplateDurably(
    name: String,
    description: String,
    tags: List<SessionTemplateTag> = emptyList(),
) {
    val template = buildCurrentSessionTemplate(name, description, tags)
    if (template == null) {
        updateUi { it.copy(snackbarMessage = "Añade un nombre y al menos una sesión válida.") }
        return
    }
    viewModelScope.launch {
        val saved = withContext(Dispatchers.IO) { templateRepository.saveUserTemplateNow(template) }
        updateUi {
            it.copy(
                snackbarMessage = if (saved.isSuccess) {
                    "Plantilla guardada."
                } else {
                    "No se pudo guardar la plantilla. Intenta nuevamente."
                },
            )
        }
    }
}

/**
 * Suspendable API for callers that need a read-back contract. It never returns
 * success until the Room write completes.
 */
suspend fun SessionEditorViewModel.saveCurrentSessionAsTemplateNow(
    name: String,
    description: String,
    tags: List<SessionTemplateTag> = emptyList(),
): Result<SessionTemplate> {
    val template = buildCurrentSessionTemplate(name, description, tags)
        ?: return Result.failure(IllegalArgumentException("Nombre o sesión inválidos"))
    return withContext(Dispatchers.IO) {
        templateRepository.saveUserTemplateNow(template).map { template }
    }
}

/**
 * Compatibility API for callers that used the old Boolean-returning method.
 * It is now suspendable and returns the durable Room result, so success can
 * never be reported before the DAO write has completed.
 */
@Deprecated("Use saveCurrentSessionAsTemplateDurably or saveCurrentSessionAsTemplateNow")
suspend fun SessionEditorViewModel.saveCurrentSessionAsTemplate(
    name: String,
    description: String,
    tags: List<SessionTemplateTag>,
): Result<SessionTemplate> = saveCurrentSessionAsTemplateNow(name, description, tags)

internal fun SessionEditorViewModel.applyTemplateInternal(template: SessionTemplate, mode: SessionTemplateApplyMode) {
    if (templateApplyJob?.isActive == true) {
        updateUi { it.copy(snackbarMessage = "La plantilla anterior todavía se está aplicando.") }
        return
    }
    val state = currentUiState
    val session = state.activeVariantSession ?: return
    val activeVariant = state.activeVariant
    val expectedSessionId = session.id
    val expectedModifiedAt = session.lastModifiedAtMs
    if (!SessionTemplateEngine.canApplyTemplate(template, session)) {
        updateUi { it.copy(snackbarMessage = "La plantilla no se puede aplicar a esta sesión.") }
        return
    }
    // ensure large templates don't block UI
    if (template.session.allExercises().size > 12) {
        Log.w("SessionEditorVM", "applyTemplateInternal plantilla grande ${template.id}")
    }
    templateApplyJob = viewModelScope.launch {
        // Clone and merge off Main. The commit below is conditional on the same
        // active variant/version so an intervening edit is never overwritten.
        val result = withContext(Dispatchers.Default) {
            SessionTemplateEngine.applyTemplate(template, session, mode)
        }
        val latest = currentUiState
        val latestSession = latest.activeVariantSession
        if (latest.activeVariant != activeVariant ||
            latestSession?.id != expectedSessionId ||
            latestSession.lastModifiedAtMs != expectedModifiedAt
        ) {
            updateUi {
                it.copy(
                    templateApplyDecision = null,
                    snackbarMessage = "La sesión cambió mientras se aplicaba la plantilla; no se sobrescribieron tus cambios.",
                )
            }
            return@launch
        }
        updateSession { result }
        updateUi { it.copy(sheet = SessionEditorSheet.NONE, templateApplyDecision = null, templateSearchQuery = "") }
    }
}
