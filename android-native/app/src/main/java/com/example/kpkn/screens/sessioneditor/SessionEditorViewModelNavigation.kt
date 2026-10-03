package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.isExerciseCatalogV2RuntimeReady
import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues

import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.normalizeEditorScheduledTechniques
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

fun SessionEditorViewModel.clearSnackbarMessage() {
    updateUi { it.copy(snackbarMessage = null) }
}

fun SessionEditorViewModel.setMainSessionForDay(sessionId: String) {
    val state = currentUiState
    val program = repository.getProgramById(programId) ?: return
    val day = state.dayOfWeek ?: return
    val updated = program.macrocycles.map { macro ->
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    if (week.id != state.weekId) return@map week
                    week.copy(sessions = week.sessions.map { session ->
                        if (session.dayOfWeek != day) session
                        else session.copy(isMainSession = session.id == sessionId)
                    })
                })
            })
        })
    }
    repository.updateProgram(program.copy(macrocycles = updated))
    updateUi { it.copy(snackbarMessage = "Sesión principal actualizada") }
    switchToSession(sessionId)
}

fun SessionEditorViewModel.requestSessionSwitch(
    targetSessionId: String,
    targetWeekId: String? = null,
    targetMacroIndex: Int? = null,
    targetMesoIndex: Int? = null,
) {
        val state = currentUiState
        if (state.session?.id == targetSessionId) return
        if (state.hasUnsavedChanges) {
            // B4: persist (encode) en IO, no bloquear UI
            viewModelScope.launch(Dispatchers.IO) {
                val ok = persistRecoverableSession(state)
                withContext(Dispatchers.Main) {
                    if (!ok) {
                        updateUi { it.copy(snackbarMessage = "Error al guardar el borrador de la sesión actual") }
                        return@withContext
                    }
                    switchToSession(targetSessionId, targetWeekId, targetMacroIndex, targetMesoIndex)
                }
            }
            return
        }
        switchToSession(targetSessionId, targetWeekId, targetMacroIndex, targetMesoIndex)
    }

fun SessionEditorViewModel.selectRoadmapDay(dayOfWeek: Int): SessionEditorSaveResult {
    val state = currentUiState
    if (state.dayOfWeek == dayOfWeek) {
        return SessionEditorSaveResult(success = true, message = "")
    }
    val targetSession = state.siblingSessions.firstOrNull { it.dayOfWeek == dayOfWeek }
    if (targetSession != null) {
        requestSessionSwitch(targetSession.id)
        return SessionEditorSaveResult(success = true, message = "")
    }
    return createSessionForDay(dayOfWeek)
}

fun SessionEditorViewModel.createSessionForDay(dayOfWeek: Int): SessionEditorSaveResult {
    val state = currentUiState
    if (state.hasUnsavedChanges) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = persistRecoverableSession(state)
            withContext(Dispatchers.Main) {
                if (!ok) {
                    updateUi { it.copy(snackbarMessage = "Error al guardar el borrador de la sesión actual") }
                    return@withContext
                }
                completeCreateSessionForDay(dayOfWeek)
            }
        }
        return SessionEditorSaveResult(success = true, message = "")
    }
    return completeCreateSessionForDay(dayOfWeek)
}

private fun SessionEditorViewModel.completeCreateSessionForDay(dayOfWeek: Int): SessionEditorSaveResult {
    val state = currentUiState
    val existingOnDay = state.weekSessions.firstOrNull { it.dayOfWeek == dayOfWeek }
    if (existingOnDay != null) {
        requestSessionSwitch(existingOnDay.id)
        return SessionEditorSaveResult(success = true, message = "")
    }

    val newSession = createDraftSession(UUID.randomUUID().toString(), dayOfWeek).copy(
        name = defaultSessionNameForDay(dayOfWeek),
        isMainSession = true,
        lastModifiedAtMs = System.currentTimeMillis(),
    )
    if (!repository.upsertSessionInProgram(programId, state.weekId, state.macroIndex, state.mesoIndex, newSession)) {
        return SessionEditorSaveResult(success = false, message = "No pudimos crear la sesión en esta semana.")
    }

    updateUi {
        val updatedWeekSessions = ensureSessionInList(it.weekSessions, newSession)
        it.copy(
            session = newSession,
            originalSession = newSession,
            dayOfWeek = dayOfWeek,
            isNewSession = true,
            selectedSiblingSessionId = newSession.id,
            siblingSessions = updatedWeekSessions.sortedBy { session -> session.dayOfWeek ?: 99 },
            weekSessions = updatedWeekSessions,
            // A fresh UUID cannot have trained history; do not read SharedPreferences on Main.
            localDraftHistory = emptyList(),
            ruleDefaults = newSession.persistedRuleDefaults?.let(SessionEditorRuleDefaults::fromPersisted)
                ?: newSession.inferredEditorRuleDefaults(),
            partRuleDefaults = emptyMap(),
            savedPartRuleDefaults = emptyMap(),
            ruleLimits = SessionEditorRuleLimits(),
            savedRuleLimits = SessionEditorRuleLimits(),
            // A fresh session has no preference record: the previous session's extras must not leak in.
            savedRuleExtras = SessionEditorGlobalRuleExtras(),
            pendingTransferToDays = null,
            hasUnsavedChanges = false,
            draftBundle = it.draftBundle?.copy(sessionId = newSession.id, dayOfWeek = dayOfWeek),
            snackbarMessage = "Sesión creada para ${dayLabel(dayOfWeek)}",
            strengthSpaceCommitted = false,
            cardioSpacePlacement = null,
        )
    }
    refreshDerivedStateImmediate()
    loadHistory()
    return SessionEditorSaveResult(success = true, message = "")
}

fun SessionEditorViewModel.discardAndSwitchPendingSession() {
    viewModelScope.launch {
        if (!discardAndSwitchPendingSessionAndAwait()) {
            updateUi { it.copy(snackbarMessage = "No se pudo descartar el borrador. Vuelve a intentarlo.") }
        }
    }
}

internal suspend fun SessionEditorViewModel.discardAndSwitchPendingSessionAndAwait(): Boolean {
    val state = currentUiState
    val target = state.pendingSessionSwitchId ?: return false
    val preferencesPreserved = state.session?.let { session ->
        preserveEditorRuleBaselineForDiscard(session.id, state.toSavedRulePreferences())
    } ?: true
    if (!preferencesPreserved) return false
    val cleared = state.session?.let {
        clearPersistedDraft(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = it.id,
        )
    } ?: true
    if (!cleared) return false
    val pendingWeekId = state.pendingWeekId
    val pendingMacroIndex = state.pendingMacroIndex
    val pendingMesoIndex = state.pendingMesoIndex
    updateUi {
        it.copy(
            pendingSessionSwitchId = null,
            pendingWeekId = null,
            pendingMacroIndex = null,
            pendingMesoIndex = null,
            sheet = SessionEditorSheet.NONE,
        )
    }
    switchToSession(target, pendingWeekId, pendingMacroIndex, pendingMesoIndex)
    return true
}

fun SessionEditorViewModel.selectRoadmapOption(option: SessionRoadmapOption) {
    val program = repository.getProgramById(programId) ?: return
    val week = findWeek(program, option.macroIndex, option.mesoIndex, option.weekId) ?: return
    val preferredDay = currentUiState.dayOfWeek
    val targetSession = week.sessions.firstOrNull { it.dayOfWeek == preferredDay } ?: week.sessions.firstOrNull()
    if (targetSession == null) {
        val day = preferredDay ?: 1
        val newSession = createDraftSession(UUID.randomUUID().toString(), day).copy(
            name = defaultSessionNameForDay(day),
            isMainSession = true,
            lastModifiedAtMs = System.currentTimeMillis(),
        )
        if (repository.upsertSessionInProgram(programId, option.weekId, option.macroIndex, option.mesoIndex, newSession)) {
            requestSessionSwitch(
                targetSessionId = newSession.id,
                targetWeekId = option.weekId,
                targetMacroIndex = option.macroIndex,
                targetMesoIndex = option.mesoIndex,
            )
        }
        return
    }
    // Use requestSessionSwitch so unsaved changes trigger the save guard instead of
    // silently discarding them when the user changes weeks via the roadmap menu.
    requestSessionSwitch(
        targetSessionId = targetSession.id,
        targetWeekId = option.weekId,
        targetMacroIndex = option.macroIndex,
        targetMesoIndex = option.mesoIndex,
    )
}

internal fun SessionEditorViewModel.switchToSession(
    targetSessionId: String,
    targetWeekId: String? = currentUiState.weekId,
    targetMacroIndex: Int? = currentUiState.macroIndex,
    targetMesoIndex: Int? = currentUiState.mesoIndex,
) {
    val program = repository.getProgramById(programId) ?: return
    val located = locateSession(
        program = program,
        targetSessionId = targetSessionId,
        targetWeekId = targetWeekId,
        targetMacroIndex = targetMacroIndex,
        targetMesoIndex = targetMesoIndex,
    ) ?: return
    val resolvedWeekId = located.week.id
    val resolvedMacroIndex = located.macroIndex
    val resolvedMesoIndex = located.mesoIndex
    val weekSessions = located.week.sessions
    invalidateInitialSessionLoadForSwitch()
    invalidateDraftWritesFor(currentUiState)
    val generation = ++sessionSwitchGeneration
    viewModelScope.launch {
        val (persistedDraft, localDraftHistory, editorRulePreferences) = withContext(Dispatchers.IO) {
            val draft = persistedDraftFor(
                weekId = resolvedWeekId,
                macroIndex = resolvedMacroIndex,
                mesoIndex = resolvedMesoIndex,
                sessionId = located.session.id,
            )
            val history = runCatching { trainedVersionStore.loadForSession(located.session.id) }
                .getOrDefault(emptyList())
            val preferences = loadEditorRulePreferencesFor(located.session.id, draft, located.session)
            Triple(draft, history, preferences)
        }
        if (generation != sessionSwitchGeneration) return@launch
        val resolvedSession = resolveNewestSession(located.session, located.session, persistedDraft)
        val resolvedWeekSessions = ensureSessionInList(weekSessions, resolvedSession)
        val roadmapOptions = buildRoadmapOptions(program)
        val cloneDayOptions = buildCloneDayOptions(program, currentSessionId = resolvedSession.id)
        val cloneSourceOptions = buildCloneSourceOptions(program, currentSessionId = resolvedSession.id)
        val competitionKeyDaysInWeek = buildCompetitionKeyDaysInWeek(program, located.week)
        updateUi {
            it.copy(
                session = resolvedSession,
                originalSession = located.session,
                weekId = resolvedWeekId,
                macroIndex = resolvedMacroIndex,
                mesoIndex = resolvedMesoIndex,
                draftBundle = SessionDraftBundle(
                    sessionId = resolvedSession.id,
                    weekId = resolvedWeekId,
                    macroIndex = resolvedMacroIndex,
                    mesoIndex = resolvedMesoIndex,
                    dayOfWeek = resolvedSession.dayOfWeek,
                    siblingSessionIds = resolvedWeekSessions.map(Session::id),
                    weekSessionIds = resolvedWeekSessions.map(Session::id),
                ),
                dayOfWeek = resolvedSession.dayOfWeek,
                siblingSessions = resolvedWeekSessions.sortedBy { session -> session.dayOfWeek ?: 99 },
                weekSessions = resolvedWeekSessions,
                roadmapOptions = roadmapOptions,
                cloneDayOptions = cloneDayOptions,
                cloneSourceOptions = cloneSourceOptions,
                competitionKeyDaysInWeek = competitionKeyDaysInWeek,
                selectedSiblingSessionId = resolvedSession.id,
                pendingSessionSwitchId = null,
                sheet = SessionEditorSheet.NONE,
                localDraftHistory = localDraftHistory,
                ruleDefaults = editorRulePreferences.global.current,
                savedRuleExtras = editorRulePreferences.global.savedExtras,
                partRuleDefaults = editorRulePreferences.current.partRuleDefaults,
                savedPartRuleDefaults = editorRulePreferences.committed.partRuleDefaults,
                ruleLimits = editorRulePreferences.current.ruleLimits,
                savedRuleLimits = editorRulePreferences.committed.ruleLimits,
                selectedExercisesIds = persistedDraft?.selectedExercisesIds.orEmpty(),
                availableVariants = computeAvailableVariants(resolvedSession),
                activeVariant = WeekVariant.A,
                pendingTransferToDays = persistedDraft?.pendingTransferToDays,
                strengthSpaceCommitted = false,
                cardioSpacePlacement = null,
            )
        }
        refreshDerivedStateImmediate()
        loadHistory()
    }
}

private data class SessionEditorDurableSaveAttempt(
    val committed: Boolean,
    val transferOutcome: SessionTransferOutcome? = null,
    val cleanupWarning: String? = null,
    val failureMessage: String? = null,
    val uiAcknowledged: Boolean = false,
    val editorPreferencesSaved: Boolean = true,
)

suspend fun SessionEditorViewModel.saveSession(scope: SessionSaveScope = SessionSaveScope.SESSION_ONLY, skipRefresh: Boolean = false): SessionEditorSaveResult {
    val state = currentUiState
    val rawDraft = state.session ?: return SessionEditorSaveResult(false, "No hay una sesión activa para guardar.")
    if (!state.hasMeaningfulSessionChanges()) {
        if (!state.hasMeaningfulDraftChanges()) {
            val pendingTarget = state.pendingSessionSwitchId
            if (!skipRefresh && pendingTarget != null) {
                switchToSession(
                    targetSessionId = pendingTarget,
                    targetWeekId = state.pendingWeekId,
                    targetMacroIndex = state.pendingMacroIndex,
                    targetMesoIndex = state.pendingMesoIndex,
                )
            }
            return SessionEditorSaveResult(true, "No hay cambios que guardar.")
        }
        return saveEditorRulePreferencesOnly(state, skipRefresh)
    }
    val editorPreferencesWriteRevision = nextEditorRulePreferencesWriteRevision(rawDraft.id)
    val normalized = rawDraft.normalizeEditorScheduledTechniques().normalizeSession()
    // D6 del plan 2026-09-16: el guardado auto-cura nombres históricos con el
    // derivado del catálogo cuando el runtime v2 está listo.
    val reconciledDraft = if (isExerciseCatalogV2RuntimeReady()) {
        com.example.kpkn.domain.exercises.catalogv2.SessionCatalogNameReconciler
            .reconcileSession(normalized, com.example.kpkn.data.exercises.catalogConfigurationDisplayNameIndex())
    } else {
        normalized
    }
    val draft = reconciledDraft.copy(
        lastModifiedAtMs = System.currentTimeMillis(),
        persistedRuleDefaults = state.ruleDefaults.toPersisted(),
    )
    val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(false, "No pudimos encontrar el programa activo.")
    if (state.weekId.isBlank()) return SessionEditorSaveResult(false, "No pudimos identificar la semana para guardar.")

    // Once v2 is actually loaded, no legacy/partial identity may cross the save boundary.
    // El draft ya viene reconciliado (D6): el gate valida identidad y nombre.
    if (isExerciseCatalogV2RuntimeReady()) {
        val catalogIssues = draft.catalogV2SelectionIssues(
            com.example.kpkn.data.exercises.catalogConfigurationDisplayNameIndex(),
        )
        if (catalogIssues.isNotEmpty()) {
            val details = catalogIssues.take(3).joinToString(" | ") { issue ->
                "${issue.exerciseId}: ${issue.code}${issue.detail?.let { detail -> " ($detail)" }.orEmpty()}"
            }
            return SessionEditorSaveResult(
                false,
                "El catálogo de ejercicios bloqueó el guardado: $details",
            )
        }
    }

    val validation = SessionEditorRulesEngine.validateBeforeSave(
        draft = draft,
        weekSessions = state.weekSessions,
        ruleLimits = state.ruleLimits,
        exerciseIndex = exerciseIndex,
    )
    if (validation.blockingError != null) {
        return SessionEditorSaveResult(false, validation.blockingError)
    }
    val pendingSessionSwitchId = state.pendingSessionSwitchId
    val pendingWeekId = state.pendingWeekId
    val pendingMacroIndex = state.pendingMacroIndex
    val pendingMesoIndex = state.pendingMesoIndex
    val effectiveScope = if (state.isSimpleProgram) SessionSaveScope.SESSION_ONLY else scope
    val pendingTransfer = state.pendingTransferToDays
    val transferForSave = pendingTransfer?.withLatestSourceFrom(draft)?.let { staged ->
        val normalizedSource = staged.sourceSession.normalizeEditorScheduledTechniques().normalizeSession()
        val reconciledSource = if (isExerciseCatalogV2RuntimeReady()) {
            com.example.kpkn.domain.exercises.catalogv2.SessionCatalogNameReconciler
                .reconcileSession(normalizedSource, com.example.kpkn.data.exercises.catalogConfigurationDisplayNameIndex())
        } else {
            normalizedSource
        }
        staged.copy(sourceSession = reconciledSource)
    }
    if (pendingTransfer != null && transferForSave == null) {
        val message = "La variante de origen de la transferencia ya no existe. Conservamos el borrador; vuelve a elegir el origen."
        updateUi { it.copy(snackbarMessage = message) }
        return SessionEditorSaveResult(false, message)
    }
    if (transferForSave?.selectedExerciseIds?.isNotEmpty() == true &&
        transferForSave.sourceSession.allExercises().none { it.id in transferForSave.selectedExerciseIds }
    ) {
        val message = "Los ejercicios seleccionados ya no están en la variante de origen. Conservamos el borrador; revisa la transferencia."
        updateUi { it.copy(snackbarMessage = message) }
        return SessionEditorSaveResult(false, message)
    }

    val trainedSessionIds = trainedSessionIdsForMesocycleGuard()
    val templateOverrideIds = linkedSetOf<String>()
    val recoverableSnapshot = state.copy(session = draft, pendingTransferToDays = transferForSave)
    val saveKey = draftStorageKey(state.weekId, state.macroIndex, state.mesoIndex, draft.id)
    activeDurableSaveKey = saveKey
    invalidateDraftWritesFor(state)
    val saveGeneration = draftGeneration(saveKey)
    val capturedSwitchGeneration = sessionSwitchGeneration
    var wroteTargetWeek = false
    var transferOutcome: SessionTransferOutcome? = null
    var candidateProgram: Program? = null
    var programBeforeMutation: Program? = null
    var expectedWriteVersion: Long? = null

    val attempt = try {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                var mutateOk = false
                var mutationFailure: Throwable? = null
                try {
                    mutateOk = repository.mutateProgramNow(programId) { current ->
                        programBeforeMutation = current
                        expectedWriteVersion = repository.nextProgramWriteVersionForEditorRecovery()
                        val updatedProgram = if (effectiveScope == SessionSaveScope.MESOCYCLE) {
                            applySessionToMesocycle(current, state, draft, trainedSessionIds, templateOverrideIds)
                        } else {
                            current.updateWeekSessions(state.macroIndex, state.mesoIndex, state.weekId) { sessions ->
                                val replaced = sessions.map { if (it.id == draft.id) draft else it }
                                if (replaced.none { it.id == draft.id }) normalizeEditorMainSessions(replaced + draft) else normalizeEditorMainSessions(replaced)
                            }
                        }
                        wroteTargetWeek = updatedProgram.containsSessionInWeek(state.weekId, draft.id)
                        if (!wroteTargetWeek) return@mutateProgramNow null
                        val transfers = if (transferForSave != null) {
                            applyPendingTransfersToProgram(
                                program = updatedProgram,
                                pending = transferForSave,
                                cloneDayOptions = state.cloneDayOptions,
                            )
                        } else {
                            null
                        }
                        transferOutcome = transfers
                        val written = transfers?.program ?: updatedProgram
                        val frozenSource = freezeManualEdits(written, state, draft, effectiveScope, templateOverrideIds)
                        val candidate = transfers?.copy(program = frozenSource)?.freezeTransferredSessionOverrides() ?: frozenSource
                        candidateProgram = candidate
                        candidate
                    }
                } catch (cancelled: CancellationException) {
                    persistRecoverableSessionAtGeneration(recoverableSnapshot, saveKey, saveGeneration)
                    throw cancelled
                } catch (failure: Throwable) {
                    mutationFailure = failure
                }

                var recoveredProgram: Program? = null
                if (!mutateOk && candidateProgram != null && expectedWriteVersion != null) {
                    recoveredProgram = try {
                        repository.recoverCommittedProgramForEditor(
                            programId = programId,
                            expectedWriteVersion = expectedWriteVersion!!,
                        ) { durable ->
                            durableEditorSaveMatches(
                                durable = durable,
                                before = programBeforeMutation,
                                candidate = candidateProgram!!,
                                weekId = state.weekId,
                                sourceSessionId = draft.id,
                                transferOutcome = transferOutcome,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        mutationFailure = mutationFailure ?: failure
                        null
                    }
                }
                val committed = mutateOk || recoveredProgram != null
                if (!committed || !wroteTargetWeek) {
                    val recovery = persistRecoverableSessionAtGeneration(recoverableSnapshot, saveKey, saveGeneration)
                    val message = if (recovery.status == DraftWriteStatus.WRITTEN || recovery.status == DraftWriteStatus.STALE) {
                        "No se pudo guardar la sesión. Tus cambios siguen en el editor; reintenta el guardado."
                    } else {
                        "No se pudo guardar la sesión ni confirmar el borrador local. Tus cambios siguen en pantalla; reintenta."
                    }
                    withContext(Dispatchers.Main.immediate + NonCancellable) {
                        if (isSameEditorSaveIdentity(state, draft.id, capturedSwitchGeneration)) {
                            updateUi { it.copy(snackbarMessage = message) }
                        }
                    }
                    return@withContext SessionEditorDurableSaveAttempt(
                        committed = false,
                        transferOutcome = transferOutcome,
                        failureMessage = mutationFailure?.let { message },
                    )
                }

                // Room now holds `draft`, so the recoverable draft's committed baseline is the
                // new core (originalSession = draft) with the still-unconfirmed OLD extras:
                // if the preference write below fails, the extras stay pending and a retry
                // is preference-only, never another Room write or another transfer.
                val committedSnapshot = recoverableSnapshot.copy(
                    session = draft,
                    originalSession = draft,
                    pendingTransferToDays = null,
                )
                val committedSnapshotOutcome = try {
                    persistRecoverableSessionAtGeneration(committedSnapshot, saveKey, saveGeneration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    DraftWriteOutcome(DraftWriteStatus.FAILED, failure)
                }
                val editorPreferencesSaved = committedSnapshotOutcome.status == DraftWriteStatus.WRITTEN && (try {
                    persistEditorRulePreferences(
                        sessionId = draft.id,
                        preferences = state.toRulePreferences(),
                        revision = editorPreferencesWriteRevision,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                })

                var cleanupWarning: String? = null
                if (!editorPreferencesSaved) {
                    cleanupWarning = if (
                        committedSnapshotOutcome.status == DraftWriteStatus.WRITTEN ||
                        committedSnapshotOutcome.status == DraftWriteStatus.STALE
                    ) {
                        "La sesión y la transferencia quedaron guardadas; las preferencias del editor siguen pendientes. Reintenta para guardarlas."
                    } else {
                        "La sesión quedó guardada, pero no se pudieron guardar las preferencias ni actualizar el borrador local. El reintento de transferencia conserva su recibo idempotente."
                    }
                } else {
                    val clearOutcome = try {
                        clearPersistedDraftAtGeneration(saveKey, saveGeneration)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        DraftWriteOutcome(DraftWriteStatus.FAILED, failure)
                    }
                    if (clearOutcome.status == DraftWriteStatus.FAILED) {
                        val rewrite = try {
                            persistRecoverableSessionAtGeneration(committedSnapshot, saveKey, saveGeneration)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            DraftWriteOutcome(DraftWriteStatus.FAILED, failure)
                        }
                        cleanupWarning = if (rewrite.status == DraftWriteStatus.WRITTEN) {
                            "La sesión quedó guardada; no se borró el borrador anterior y se actualizó su recuperación."
                        } else {
                            "La sesión quedó guardada; el borrador anterior no se pudo limpiar. El reintento de transferencia es idempotente."
                        }
                    }
                }

                // MESOCYCLE clones inherit the core defaults through the cloned Session; the
                // extras live in per-session preference records, so copy them best-effort.
                // Only after our own record is durable, and never part of the retry path.
                val templateCloneIds = templateOverrideIds.filter { it != draft.id }
                if (editorPreferencesSaved && effectiveScope == SessionSaveScope.MESOCYCLE && templateCloneIds.isNotEmpty()) {
                    propagateGlobalRuleExtrasToClones(templateCloneIds, state.ruleDefaults.extras())
                }

                val programAfterSave = recoveredProgram ?: candidateProgram ?: repository.getProgramById(programId) ?: program
                val localDraftHistory = withContext(Dispatchers.IO) {
                    try {
                        trainedVersionStore.loadForSession(draft.id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
                val roadmapOptions = runCatching { buildRoadmapOptions(programAfterSave) }.getOrDefault(state.roadmapOptions)
                val cloneDayOptions = runCatching { buildCloneDayOptions(programAfterSave, currentSessionId = draft.id) }
                    .getOrDefault(state.cloneDayOptions)
                val cloneSourceOptions = runCatching { buildCloneSourceOptions(programAfterSave, currentSessionId = draft.id) }
                    .getOrDefault(state.cloneSourceOptions)
                var uiAcknowledged = false
                try {
                    withContext(Dispatchers.Main.immediate + NonCancellable) {
                        if (draftGeneration(saveKey) == saveGeneration &&
                            isSameEditorSaveIdentity(state, draft.id, capturedSwitchGeneration)
                        ) {
                            updateUi {
                                it.copy(
                                    originalSession = draft,
                                    session = draft,
                                    savedPartRuleDefaults = if (editorPreferencesSaved) {
                                        state.partRuleDefaults
                                    } else {
                                        it.savedPartRuleDefaults
                                    },
                                    savedRuleLimits = if (editorPreferencesSaved) {
                                        state.ruleLimits
                                    } else {
                                        it.savedRuleLimits
                                    },
                                    savedRuleExtras = if (editorPreferencesSaved) {
                                        state.ruleDefaults.extras()
                                    } else {
                                        it.savedRuleExtras
                                    },
                                    isNewSession = false,
                                    sheet = SessionEditorSheet.NONE,
                                    draftBundle = it.draftBundle?.copy(
                                        sessionId = draft.id,
                                        dayOfWeek = draft.dayOfWeek,
                                    ),
                                    localDraftHistory = localDraftHistory,
                                    pendingSessionSwitchId = if (editorPreferencesSaved) null else it.pendingSessionSwitchId,
                                    pendingWeekId = if (editorPreferencesSaved) null else it.pendingWeekId,
                                    pendingMacroIndex = if (editorPreferencesSaved) null else it.pendingMacroIndex,
                                    pendingMesoIndex = if (editorPreferencesSaved) null else it.pendingMesoIndex,
                                    pendingTransferToDays = null,
                                    availableVariants = computeAvailableVariants(draft),
                                    roadmapOptions = roadmapOptions,
                                    cloneDayOptions = cloneDayOptions,
                                    cloneSourceOptions = cloneSourceOptions,
                                    snackbarMessage = cleanupWarning ?: it.snackbarMessage,
                                )
                            }
                            uiAcknowledged = true
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    cleanupWarning = listOfNotNull(cleanupWarning, "La sesión se guardó, pero no se pudo actualizar el editor: ${error.message ?: "error interno"}.")
                        .joinToString(" ")
                }
                SessionEditorDurableSaveAttempt(
                    committed = true,
                    transferOutcome = transferOutcome,
                    cleanupWarning = cleanupWarning,
                    uiAcknowledged = uiAcknowledged,
                    editorPreferencesSaved = editorPreferencesSaved,
                )
            } finally {
                if (activeDurableSaveKey == saveKey) activeDurableSaveKey = null
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    }

    if (!attempt.committed) {
        return SessionEditorSaveResult(false, attempt.failureMessage ?: "No pudimos guardar la sesión en la semana indicada.")
    }
    if (!attempt.editorPreferencesSaved) {
        return SessionEditorSaveResult(
            false,
            "El contenido quedó guardado en Room, pero las preferencias del editor siguen pendientes. Reintenta el guardado antes de salir.",
        )
    }
    if (!skipRefresh && attempt.uiAcknowledged && attempt.editorPreferencesSaved) {
        switchToSession(
            targetSessionId = pendingSessionSwitchId ?: draft.id,
            targetWeekId = if (pendingSessionSwitchId != null) pendingWeekId else null,
            targetMacroIndex = if (pendingSessionSwitchId != null) pendingMacroIndex else null,
            targetMesoIndex = if (pendingSessionSwitchId != null) pendingMesoIndex else null,
        )
    }
    val transferSuffix = if (pendingTransfer != null) {
        val affectedCount = attempt.transferOutcome?.affectedTargets?.size ?: 0
        " · Transferencia aplicada a $affectedCount de ${pendingTransfer.targetKeys.size} día(s)"
    } else ""
    val warningSuffix = validation.warnings.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " | ", prefix = " (Alertas: ")?.plus(")") ?: ""
    val cleanupSuffix = attempt.cleanupWarning?.let { " · $it" }.orEmpty()
    return SessionEditorSaveResult(true, "Sesión guardada$transferSuffix$warningSuffix$cleanupSuffix")
}

private suspend fun SessionEditorViewModel.saveEditorRulePreferencesOnly(
    state: SessionEditorUiState,
    skipRefresh: Boolean,
): SessionEditorSaveResult {
    val session = state.session ?: return SessionEditorSaveResult(false, "No hay una sesión activa para guardar.")
    val saveKey = draftStorageKey(state.weekId, state.macroIndex, state.mesoIndex, session.id)
    val capturedSwitchGeneration = sessionSwitchGeneration
    val pendingSessionSwitchId = state.pendingSessionSwitchId
    val pendingWeekId = state.pendingWeekId
    val pendingMacroIndex = state.pendingMacroIndex
    val pendingMesoIndex = state.pendingMesoIndex
    // Parts, limits and global extras travel in ONE record (a single commit). This path
    // never touches Room, a manual override or `lastModifiedAtMs`, and has no transfer to repeat.
    val preferences = state.toRulePreferences()
    val preferencesWriteRevision = nextEditorRulePreferencesWriteRevision(session.id)
    activeDurableSaveKey = saveKey
    invalidateDraftWritesFor(state)
    val saveGeneration = draftGeneration(saveKey)
    try {
        val (saved, draftOutcome) = withContext(Dispatchers.IO + NonCancellable) {
            val recoverable = try {
                persistRecoverableSessionAtGeneration(state, saveKey, saveGeneration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                DraftWriteOutcome(DraftWriteStatus.FAILED, failure)
            }
            val preferencesSaved = if (recoverable.status == DraftWriteStatus.WRITTEN) {
                try {
                    persistEditorRulePreferences(session.id, preferences, preferencesWriteRevision)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }
            } else {
                false
            }
            if (!preferencesSaved) {
                false to recoverable
            } else {
                val cleared = try {
                    clearPersistedDraftAtGeneration(saveKey, saveGeneration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    DraftWriteOutcome(DraftWriteStatus.FAILED, failure)
                }
                true to cleared
            }
        }
        if (!saved) {
            val message = if (draftOutcome.status == DraftWriteStatus.WRITTEN || draftOutcome.status == DraftWriteStatus.STALE) {
                "No se pudieron guardar las preferencias del editor. Tus cambios siguen en el borrador; reintenta."
            } else {
                "No se pudieron guardar las preferencias ni confirmar el borrador local. Tus cambios siguen en pantalla; reintenta."
            }
            withContext(Dispatchers.Main.immediate + NonCancellable) {
                if (isSameEditorSaveIdentity(state, session.id, capturedSwitchGeneration)) {
                    updateUi { it.copy(snackbarMessage = message) }
                }
            }
            return SessionEditorSaveResult(false, message)
        }

        val cleanupWarning = if (draftOutcome.status == DraftWriteStatus.FAILED) {
            "Las preferencias quedaron guardadas; el borrador anterior no se pudo limpiar."
        } else {
            null
        }
        var uiAcknowledged = false
        withContext(Dispatchers.Main.immediate + NonCancellable) {
            if (draftGeneration(saveKey) == saveGeneration &&
                isSameEditorSaveIdentity(state, session.id, capturedSwitchGeneration)
            ) {
                updateUi {
                    it.copy(
                        savedPartRuleDefaults = state.partRuleDefaults,
                        savedRuleLimits = state.ruleLimits,
                        savedRuleExtras = state.ruleDefaults.extras(),
                        pendingSessionSwitchId = null,
                        pendingWeekId = null,
                        pendingMacroIndex = null,
                        pendingMesoIndex = null,
                        snackbarMessage = cleanupWarning ?: it.snackbarMessage,
                    )
                }
                uiAcknowledged = true
            }
        }
        if (!skipRefresh && uiAcknowledged && pendingSessionSwitchId != null) {
            switchToSession(pendingSessionSwitchId, pendingWeekId, pendingMacroIndex, pendingMesoIndex)
        }
        return SessionEditorSaveResult(
            true,
            cleanupWarning?.let { "Preferencias guardadas · $it" } ?: "Preferencias del editor guardadas.",
        )
    } finally {
        if (activeDurableSaveKey == saveKey) activeDurableSaveKey = null
    }
}

private fun SessionEditorViewModel.isSameEditorSaveIdentity(
    captured: SessionEditorUiState,
    sessionId: String,
    switchGeneration: Long,
): Boolean {
    val current = currentUiState
    return sessionSwitchGeneration == switchGeneration &&
        current.programId == captured.programId &&
        current.weekId == captured.weekId &&
        current.macroIndex == captured.macroIndex &&
        current.mesoIndex == captured.mesoIndex &&
        current.session?.id == sessionId
}

private fun durableEditorSaveMatches(
    durable: Program,
    before: Program?,
    candidate: Program,
    weekId: String,
    sourceSessionId: String,
    transferOutcome: SessionTransferOutcome?,
): Boolean {
    fun session(program: Program?, targetWeekId: String, id: String): Session? =
        program?.findWeekById(targetWeekId)?.sessions?.firstOrNull { it.id == id }

    val candidateSource = session(candidate, weekId, sourceSessionId) ?: return false
    val durableSource = session(durable, weekId, sourceSessionId) ?: return false
    if (!sameSessionEditorContent(candidateSource, durableSource)) return false
    val beforeSource = session(before, weekId, sourceSessionId)
    val sourceChanged = !sameSessionEditorContent(beforeSource, candidateSource)
    val candidateSourceOverride = candidate.manualSessionOverrides.firstOrNull {
        it.sessionId == sourceSessionId && it.scope == ManualOverrideScope.SESSION
    }
    val durableSourceOverride = durable.manualSessionOverrides.firstOrNull {
        it.sessionId == sourceSessionId && it.scope == ManualOverrideScope.SESSION
    }
    val sourceOverrideMatches = candidateSourceOverride != null && durableSourceOverride != null &&
        candidateSourceOverride.weekId == durableSourceOverride.weekId &&
        candidateSourceOverride.weekOccurrence == durableSourceOverride.weekOccurrence &&
        candidateSourceOverride.recipeDayId == durableSourceOverride.recipeDayId &&
        candidateSourceOverride.scope == durableSourceOverride.scope &&
        candidateSourceOverride.weekId == weekId

    val affected = transferOutcome?.receiptTargets.orEmpty()
    val transfersMatch = transferOutcome?.transferReceipt?.let { receipt ->
        affected.isNotEmpty() && affected.all { target ->
            val candidateTarget = session(candidate, target.option.weekId, target.session.id)
            val durableTarget = session(durable, target.option.weekId, target.session.id)
            candidateTarget != null && durableTarget != null &&
                sameSessionEditorContent(candidateTarget, durableTarget) &&
                candidate.hasTransferReceipt(target.session.id, receipt, target.option.weekId) &&
                durable.hasTransferReceipt(target.session.id, receipt, target.option.weekId) &&
                candidate.manualSessionOverrides.firstOrNull {
                    it.sessionId == target.session.id && it.weekId == target.option.weekId && it.scope == ManualOverrideScope.SESSION
                }?.let { expected ->
                    durable.manualSessionOverrides.firstOrNull {
                        it.sessionId == target.session.id && it.weekId == target.option.weekId && it.scope == ManualOverrideScope.SESSION
                    }?.let { actual ->
                        expected.weekId == actual.weekId &&
                            expected.weekOccurrence == actual.weekOccurrence &&
                            expected.recipeDayId == actual.recipeDayId &&
                            expected.scope == actual.scope
                    }
                } == true
        }
    } == true

    val committedTimestampMatches = candidateSource.lastModifiedAtMs == durableSource.lastModifiedAtMs &&
        beforeSource?.lastModifiedAtMs != candidateSource.lastModifiedAtMs
    return transfersMatch || (sourceOverrideMatches && (sourceChanged || committedTimestampMatches))
}

/**
 * §14.5/AC-G2: congela las sesiones editadas en UNA mutación con el guardado.
 * La sesión destino recibe alcance SESSION (solo esa sesión/ocurrencia); con
 * SessionSaveScope.MESOCYCLE, cuya acción existente sí aplica a futuras
 * ocurrencias de la plantilla, las otras sesiones escritas quedan con alcance
 * TEMPLATE_FUTURE_OCCURRENCES. Solo se marca la prescripción afectada: renombrar
 * o notas no congelan nada y los logs nunca se reescriben.
 */
internal fun SessionEditorViewModel.freezeManualEdits(
    program: Program,
    state: SessionEditorUiState,
    draft: Session,
    effectiveScope: SessionSaveScope,
    templateOverrideIds: Set<String>,
): Program {
    val weekOccurrence = program.weekOccurrenceFor(state.weekId)
    var frozen = PlanMaterializer.withManualSessionOverride(
        program = program,
        sessionId = draft.id,
        weekId = state.weekId,
        weekOccurrence = weekOccurrence,
        recipeDayId = draft.allExercises().mapNotNull { it.recipeDayId }.firstOrNull(),
        scope = ManualOverrideScope.SESSION,
        reason = "Sesión guardada desde el editor",
    )
    if (effectiveScope == SessionSaveScope.MESOCYCLE) {
        templateOverrideIds.filter { it != draft.id }.forEach { sessionId ->
            frozen = PlanMaterializer.withManualSessionOverride(
                program = frozen,
                sessionId = sessionId,
                weekId = null,
                weekOccurrence = null,
                recipeDayId = null,
                scope = ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES,
                reason = "Edición de plantilla aplicada a futuras ocurrencias",
            )
        }
    }
    return frozen
}

internal fun detectChangedFields(previous: Session, current: Session): List<String> {
    val changes = mutableListOf<String>()
    if (previous.name != current.name) changes += "nombre"
    if (previous.description != current.description) changes += "descripción"
    if (previous.dayOfWeek != current.dayOfWeek) changes += "día"
    if (previous.parts.size != current.parts.size) {
        changes += if (current.parts.size > previous.parts.size) "+grupos" else "-grupos"
    }
    val previousExercises = previous.allExercises()
    val currentExercises = current.allExercises()
    val prevIds = previousExercises.map { it.id }.toSet()
    val currIds = currentExercises.map { it.id }.toSet()
    val added = currIds - prevIds
    val removed = prevIds - currIds
    if (added.isNotEmpty()) {
        val names = currentExercises.filter { it.id in added }.take(2).map { it.name.ifBlank { "ejercicio" } }
        changes += "añadió ${names.joinToString(", ")}" + if (added.size > 2) " +${added.size - 2}" else ""
    }
    if (removed.isNotEmpty()) {
        val names = previousExercises.filter { it.id in removed }.take(2).map { it.name.ifBlank { "ejercicio" } }
        changes += "quitó ${names.joinToString(", ")}" + if (removed.size > 2) " +${removed.size - 2}" else ""
    }
    if (previousExercises.map { it.id } != currentExercises.map { it.id } && added.isEmpty() && removed.isEmpty()) {
        changes += "orden"
    }
    val previousSets = previousExercises.sumOf { it.sets.size.coerceAtLeast(1) }
    val currentSets = currentExercises.sumOf { it.sets.size.coerceAtLeast(1) }
    if (previousSets != currentSets) {
        val delta = currentSets - previousSets
        changes += if (delta > 0) "+$delta series" else "$delta series"
    }
    if (previous.allSupersetGroups().size != current.allSupersetGroups().size) changes += "superseries"
    if (previous.targetDurationMinutes != current.targetDurationMinutes) changes += "tiempo"
    if (previous.isCompetitionSession != current.isCompetitionSession) changes += "modo competición"
    if (previous.background != current.background || previous.coverStyle != current.coverStyle) changes += "portada"
    if (changes.isEmpty()) changes += "ajustes"
    return changes
}

internal suspend fun SessionEditorViewModel.trainedSessionIdsForMesocycleGuard(): Set<String> {
    val logs = repository.history.value
    val fromLogs = logs.map { it.sessionId }.toSet()
    val weekSessionIds = currentUiState.weekSessions.map { it.id }
    val fromVersions = withContext(Dispatchers.IO) {
        weekSessionIds.filter { sessionId ->
            runCatching { trainedVersionStore.loadForSession(sessionId) }
                .getOrDefault(emptyList())
                .isNotEmpty()
        }
    }
    return fromLogs + fromVersions
}

internal fun SessionEditorViewModel.applySessionToMesocycle(
    program: Program,
    state: SessionEditorUiState,
    draft: Session,
    trainedSessionIds: Set<String> = emptySet(),
    /** Sesiones tocadas fuera de la semana destino (§14.5: freeze de ocurrencias). */
    mutatedSessionIds: MutableSet<String>? = null,
): Program {
    return program.copy(
        macrocycles = program.macrocycles.mapIndexed { macroIndex, macro ->
            if (macroIndex != state.macroIndex) return@mapIndexed macro
            var globalMesoIndex = 0
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    val matchesMeso = globalMesoIndex == state.mesoIndex
                    globalMesoIndex += 1
                    if (!matchesMeso) return@map meso
                    meso.copy(weeks = meso.weeks.map { week ->
                        val updatedSessions = week.sessions.toMutableList()
                        if (week.id == state.weekId) {
                            val existingIndex = updatedSessions.indexOfFirst { it.id == draft.id }
                            val sameDayIndex = updatedSessions.indexOfFirst { it.dayOfWeek == draft.dayOfWeek }
                            when {
                                existingIndex >= 0 -> updatedSessions[existingIndex] = draft
                                sameDayIndex >= 0 -> updatedSessions[sameDayIndex] = draft.copy(id = updatedSessions[sameDayIndex].id)
                                else -> updatedSessions.add(draft)
                            }
                        } else {
                            val sameDayIndex = updatedSessions.indexOfFirst { it.dayOfWeek == draft.dayOfWeek }
                            if (sameDayIndex >= 0) {
                                val existingId = updatedSessions[sameDayIndex].id
                                if (existingId in trainedSessionIds) return@map week
                                mutatedSessionIds?.add(existingId)
                                updatedSessions[sameDayIndex] = com.example.kpkn.domain.templates.SessionTemplateEngine
                                    .cloneSessionContent(draft)
                                    .copy(id = existingId)
                            } else {
                                val createdId = UUID.randomUUID().toString()
                                mutatedSessionIds?.add(createdId)
                                updatedSessions.add(
                                    com.example.kpkn.domain.templates.SessionTemplateEngine
                                        .cloneSessionContent(draft)
                                        .copy(id = createdId),
                                )
                            }
                        }
                        week.copy(sessions = normalizeEditorMainSessions(updatedSessions))
                    })
                })
            })
        }
    )
}

