package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.PlanMaterializer
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
    val source = state.activeVariantSession ?: state.session
        ?: return SessionEditorSaveResult(false, "No hay sesión origen activa.")
    val sourceMainSessionId = state.session?.id ?: source.id
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
                sourceVariantKey = state.activeVariant.name,
                sourceMainSessionId = sourceMainSessionId,
            ),
            hasUnsavedChanges = true,
            sheet = SessionEditorSheet.NONE,
            snackbarMessage = "Transferencia pendiente a ${targets.size} día${if (targets.size > 1) "s" else ""}. Guarda para aplicarla.",
        )
    }
    if (!persistDraft()) {
        updateUi {
            it.copy(
                pendingTransferToDays = null,
                snackbarMessage = "No se pudo preparar la transferencia para recuperación. Vuelve a intentarlo.",
            )
        }
        return SessionEditorSaveResult(false, "No se pudo preparar la transferencia para recuperación. Vuelve a intentarlo.")
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
    cloneDayOptions: List<SessionCloneDayOption> = emptyList(),
): SessionTransferOutcome = applySessionTransfersToProgram(
    program = program,
    currentSessionId = pending.sourceMainSessionId ?: pending.sourceSession.id,
    pending = pending,
)

/** Refresh the staged source from the selected slot at save time, not from the old sheet snapshot. */
internal fun PendingTransferToDays.withLatestSourceFrom(base: Session): PendingTransferToDays? {
    val inferredVariant = when (sourceSession.id) {
        base.sessionB?.id -> "B"
        base.sessionC?.id -> "C"
        base.sessionD?.id -> "D"
        else -> sourceVariantKey
    }
    val resolvedVariant = if (sourceVariantKey == "A" && inferredVariant != "A") inferredVariant else sourceVariantKey
    val latestSource = when (resolvedVariant) {
        "A" -> base
        "B" -> base.sessionB
        "C" -> base.sessionC
        "D" -> base.sessionD
        else -> null
    } ?: return null
    return copy(
        sourceSession = latestSource,
        sourceVariantKey = resolvedVariant,
        sourceMainSessionId = base.id,
    )
}

internal fun SessionEditorViewModel.applyCloneToTarget(
    program: Program,
    source: Session,
    target: SessionCloneDayOption,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Program {
    return applySessionTransferTarget(program, source, target, selectedExerciseIds, applyMode)
}

internal fun SessionTransferOutcome.freezeTransferredSessionOverrides(): Program {
    var frozen = program
    affectedTargets.forEach { affected ->
        val target = affected.option
        val week = frozen.findWeekById(target.weekId) ?: return@forEach
        frozen = PlanMaterializer.withManualSessionOverride(
            program = frozen,
            sessionId = affected.session.id,
            weekId = target.weekId,
            weekOccurrence = target.weekOccurrence ?: frozen.weekOccurrenceFor(target.weekId) ?: week.progressionIndex,
            recipeDayId = target.destinationRecipeDayId,
            scope = ManualOverrideScope.SESSION,
            reason = "Sesión transferida desde el editor",
        )
        val existingOverride = frozen.manualSessionOverrides.firstOrNull {
            it.sessionId == affected.session.id && it.scope == ManualOverrideScope.SESSION
        }
        frozen = frozen.copy(
            manualSessionOverrides = frozen.manualSessionOverrides.map { override ->
                if (override.sessionId == affected.session.id && override.scope == ManualOverrideScope.SESSION) {
                    val reason = listOfNotNull(
                        existingOverride?.reason?.takeIf { it.isNotBlank() && it != "Sesión transferida desde el editor" },
                        "Sesión transferida desde el editor",
                        transferReceipt,
                    ).distinct().joinToString(" · ")
                    override.copy(
                        weekId = target.weekId,
                        weekOccurrence = target.weekOccurrence ?: frozen.weekOccurrenceFor(target.weekId) ?: week.progressionIndex,
                        cycleNumber = frozen.runState?.cycleNumber,
                        recipeDayId = target.destinationRecipeDayId,
                        reason = reason,
                    )
                } else {
                    override
                }
            },
        )
    }
    return frozen
}

