package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

fun SessionEditorViewModel.updateBackgroundValue(value: String, type: SessionBackgroundType) = updateSession { session ->
    session.copy(
        background = (session.background ?: SessionBackground(type = type, value = value)).copy(
            type = type,
            value = value,
            style = session.background?.style ?: SessionBackgroundStyle(blur = 0f, brightness = 0.92f),
        )
    )
}

fun SessionEditorViewModel.updateBackgroundStyle(blur: Float? = null, brightness: Float? = null) = updateSession { session ->
    val current = session.background ?: SessionBackground(SessionBackgroundType.COLOR, DEFAULT_SESSION_BACKGROUNDS.first())
    session.copy(
        background = current.copy(
            style = (current.style ?: SessionBackgroundStyle()).copy(
                blur = blur ?: current.style?.blur,
                brightness = brightness ?: current.style?.brightness,
            )
        )
    )
}

fun SessionEditorViewModel.updateLabelPosition(position: LabelPosition) = updateSession { session ->
    session.copy(coverStyle = (session.coverStyle ?: CoverStyle(filters = CoverFilters())).copy(labelPosition = position))
}

fun SessionEditorViewModel.updateFilterBrightness(brightness: Float) = updateSession { session ->
    val style = session.coverStyle ?: CoverStyle(filters = CoverFilters())
    session.copy(coverStyle = style.copy(filters = (style.filters ?: CoverFilters()).copy(brightness = brightness)))
}

fun SessionEditorViewModel.updateCoverFilters(
    contrast: Float? = null,
    saturation: Float? = null,
    grayscale: Float? = null,
    vignette: Float? = null,
) = updateSession { session ->
    val style = session.coverStyle ?: CoverStyle(filters = CoverFilters())
    val filters = style.filters ?: CoverFilters()
    session.copy(
        coverStyle = style.copy(
            filters = filters.copy(
                contrast = contrast ?: filters.contrast,
                saturation = saturation ?: filters.saturation,
                grayscale = grayscale ?: filters.grayscale,
                vignette = vignette ?: filters.vignette,
            )
        )
    )
}

fun SessionEditorViewModel.updateCoverMotion(enabled: Boolean) = updateSession { session ->
    val style = session.coverStyle ?: CoverStyle(filters = CoverFilters())
    session.copy(coverStyle = style.copy(enableMotion = enabled))
}

fun SessionEditorViewModel.cloneCurrentSessionToTargets(
    targetKeys: Set<String>,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): SessionEditorSaveResult {
    if (targetKeys.isEmpty()) {
        return SessionEditorSaveResult(false, "Selecciona al menos un día destino.")
    }
    if (selectedExerciseIds != null && selectedExerciseIds.isEmpty()) {
        return SessionEditorSaveResult(false, "Selecciona al menos un ejercicio para transferencia parcial.")
    }
    val state = currentUiState
    val source = state.session ?: return SessionEditorSaveResult(false, "No hay sesión origen activa.")
    val targets = state.cloneDayOptions.filter { it.key in targetKeys && !it.isCurrentSessionDay }
    if (targets.isEmpty()) return SessionEditorSaveResult(false, "No se encontraron destinos válidos.")

    // Stage only — applied when the user saves (unified draft semantics with import).
    updateUi {
        it.copy(
            pendingTransferToDays = PendingTransferToDays(
                targetKeys = targets.map { t -> t.key }.toSet(),
                selectedExerciseIds = selectedExerciseIds,
                applyMode = applyMode,
                sourceSession = source,
            ),
            hasUnsavedChanges = true,
            sheet = SessionEditorSheet.NONE,
            snackbarMessage = "Transferencia pendiente a ${targets.size} día${if (targets.size > 1) "s" else ""}. Guarda para aplicarla.",
        )
    }
    val modeLabel = if (selectedExerciseIds.isNullOrEmpty()) "completa" else "parcial"
    return SessionEditorSaveResult(
        success = true,
        message = "Transferencia $modeLabel pendiente en ${targets.size} día${if (targets.size > 1) "s" else ""}. Guarda para aplicarla.",
    )
}

fun SessionEditorViewModel.importFromSourceSession(
    sourceSessionId: String,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): SessionEditorSaveResult {
    if (selectedExerciseIds != null && selectedExerciseIds.isEmpty()) {
        return SessionEditorSaveResult(false, "Selecciona al menos un ejercicio para transferencia parcial.")
    }
    val state = currentUiState
    val sourceOption = state.cloneSourceOptions.firstOrNull { it.sessionId == sourceSessionId }
        ?: return SessionEditorSaveResult(false, "No se encontró la sesión origen.")
    val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(false, "No pudimos encontrar el programa.")
    val sourceSession = program.findSessionInProgram(
        macroIndex = sourceOption.macroIndex,
        mesoIndex = sourceOption.mesoIndex,
        weekId = sourceOption.weekId,
        sessionId = sourceOption.sessionId,
    ) ?: return SessionEditorSaveResult(false, "No se pudo leer la sesión origen.")

    updateSession(reason = "Transferencia") { current ->
            mergeSessions(
                base = current,
                incoming = sourceSession,
                selectedExerciseIds = selectedExerciseIds,
                applyMode = applyMode,
            )
        }
    closeSheet()
    val modeLabel = if (selectedExerciseIds.isNullOrEmpty()) "completa" else "parcial"
    return SessionEditorSaveResult(
        success = true,
        message = "Transferencia $modeLabel al borrador desde ${sourceOption.sessionName}. Revisa y guarda.",
    )
}

internal fun SessionEditorViewModel.applyPendingTransfersToProgram(
    program: Program,
    pending: PendingTransferToDays,
    cloneDayOptions: List<SessionCloneDayOption>,
): Program {
    val targets = cloneDayOptions.filter { it.key in pending.targetKeys && !it.isCurrentSessionDay }
    return targets.fold(program) { acc, target ->
        applyCloneToTarget(
            program = acc,
            source = pending.sourceSession,
            target = target,
            selectedExerciseIds = pending.selectedExerciseIds,
            applyMode = pending.applyMode,
        )
    }
}

internal fun SessionEditorViewModel.applyCloneToTarget(
    program: Program,
    source: Session,
    target: SessionCloneDayOption,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Program {
    val payload = buildClonePayload(source, selectedExerciseIds)
    return program.updateWeekById(target.weekId) { week ->
        val sessions = week.sessions.toMutableList()
        val existingIndex = sessions.indexOfFirst { it.id == target.existingSessionId }
        if (existingIndex >= 0) {
            val existing = sessions[existingIndex]
            sessions[existingIndex] = mergeSessionWithPayload(
                base = existing,
                source = source,
                payload = payload,
                selectedExerciseIds = selectedExerciseIds,
                applyMode = applyMode,
            ).copy(dayOfWeek = target.dayOfWeek)
        } else {
            sessions += createSessionForTargetDay(
                source = source,
                dayOfWeek = target.dayOfWeek,
                payload = payload,
                selectedExerciseIds = selectedExerciseIds,
            )
        }
        week.copy(sessions = normalizeMainSessions(sessions))
    }
}

