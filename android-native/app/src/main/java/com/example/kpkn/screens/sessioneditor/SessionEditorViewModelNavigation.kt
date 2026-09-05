package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.isExerciseCatalogV2RuntimeReady
import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues

import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
    val updated = program.macrocycles.map { macro ->
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    val day = state.dayOfWeek ?: return@map week
                    val daySessions = week.sessions.filter { it.dayOfWeek == day }
                    if (daySessions.isEmpty() || week.id != state.weekId) return@map week
                    week.copy(sessions = week.sessions.map { s ->
                        s.copy(isMainSession = s.id == sessionId)
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
        val ok = kotlinx.coroutines.runBlocking(Dispatchers.IO) { persistRecoverableSession(state) }
        if (!ok) {
            updateUi { it.copy(snackbarMessage = "Error al guardar el borrador de la sesión actual") }
            return SessionEditorSaveResult(success = false, message = "")
        }
    }

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
            localDraftHistory = TrainedSessionVersionStore.getInstance(getApplication()).loadForSession(newSession.id),
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
    val state = currentUiState
    val target = state.pendingSessionSwitchId ?: return
    state.session?.let {
        clearPersistedDraft(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = it.id,
        )
    }
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
    val persistedDraft = persistedDraftFor(
        weekId = resolvedWeekId,
        macroIndex = resolvedMacroIndex,
        mesoIndex = resolvedMesoIndex,
        sessionId = located.session.id,
    )
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
            hasUnsavedChanges = persistedDraft != null,
            pendingSessionSwitchId = null,
            sheet = SessionEditorSheet.NONE,
            localDraftHistory = TrainedSessionVersionStore.getInstance(getApplication()).loadForSession(resolvedSession.id),
            ruleDefaults = persistedDraft?.ruleDefaults ?: it.ruleDefaults,
            partRuleDefaults = persistedDraft?.partRuleDefaults ?: emptyMap(),
            ruleLimits = persistedDraft?.ruleLimits ?: it.ruleLimits,
            selectedExercisesIds = persistedDraft?.selectedExercisesIds.orEmpty(),
            strengthSpaceCommitted = false,
            cardioSpacePlacement = null,
        )
    }
    refreshDerivedStateImmediate()
    loadHistory()
}

fun SessionEditorViewModel.saveSession(scope: SessionSaveScope = SessionSaveScope.SESSION_ONLY, skipRefresh: Boolean = false): SessionEditorSaveResult {
    val state = currentUiState
    val rawDraft = state.session ?: return SessionEditorSaveResult(false, "No hay una sesión activa para guardar.")
    val draft = rawDraft.normalizeSession().copy(lastModifiedAtMs = System.currentTimeMillis())
    val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(false, "No pudimos encontrar el programa activo.")
    if (state.weekId.isBlank()) return SessionEditorSaveResult(false, "No pudimos identificar la semana para guardar.")

    // Once v2 is actually loaded, no legacy/partial identity may cross the save boundary.
    if (isExerciseCatalogV2RuntimeReady()) {
        val catalogIssues = draft.catalogV2SelectionIssues()
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

    val updatedProgram = if (effectiveScope == SessionSaveScope.MESOCYCLE) applySessionToMesocycle(program, state, draft) else {
        program.updateWeekSessions(state.macroIndex, state.mesoIndex, state.weekId) { sessions ->
            val replaced = sessions.map { if (it.id == draft.id) draft else it }
            if (replaced.none { it.id == draft.id }) normalizeMainSessions(replaced + draft) else normalizeMainSessions(replaced)
        }
    }

    val pendingTransfer = state.pendingTransferToDays
    val programWithTransfers = if (pendingTransfer != null) {
        applyPendingTransfersToProgram(
            program = updatedProgram,
            pending = pendingTransfer,
            cloneDayOptions = state.cloneDayOptions,
        )
    } else {
        updatedProgram
    }

    repository.updateProgram(programWithTransfers)
    clearPersistedDraft(
        weekId = state.weekId,
        macroIndex = state.macroIndex,
        mesoIndex = state.mesoIndex,
        sessionId = draft.id,
    )
    val transferSuffix = if (pendingTransfer != null) {
        " · Transferencia aplicada a ${pendingTransfer.targetKeys.size} día(s)"
    } else {
        ""
    }
    updateUi {
        it.copy(
            originalSession = draft,
            hasUnsavedChanges = false,
            isNewSession = false,
            sheet = SessionEditorSheet.NONE,
            draftBundle = it.draftBundle?.copy(
                sessionId = draft.id,
                dayOfWeek = draft.dayOfWeek,
            ),
            localDraftHistory = TrainedSessionVersionStore.getInstance(getApplication()).loadForSession(draft.id),
            pendingSessionSwitchId = null,
            pendingWeekId = null,
            pendingMacroIndex = null,
            pendingMesoIndex = null,
            pendingTransferToDays = null,
            roadmapOptions = buildRoadmapOptions(programWithTransfers),
            cloneDayOptions = buildCloneDayOptions(programWithTransfers, currentSessionId = draft.id),
            cloneSourceOptions = buildCloneSourceOptions(programWithTransfers, currentSessionId = draft.id),
        )
    }
    if (!skipRefresh) {
        switchToSession(
            targetSessionId = pendingSessionSwitchId ?: draft.id,
            targetWeekId = if (pendingSessionSwitchId != null) pendingWeekId else null,
            targetMacroIndex = if (pendingSessionSwitchId != null) pendingMacroIndex else null,
            targetMesoIndex = if (pendingSessionSwitchId != null) pendingMesoIndex else null,
        )
    }
    val warningSuffix = validation.warnings.takeIf { it.isNotEmpty() }?.joinToString(separator = " | ", prefix = " (Alertas: ")?.plus(")") ?: ""
    return SessionEditorSaveResult(true, "Sesión guardada$transferSuffix$warningSuffix")
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

internal fun SessionEditorViewModel.applySessionToMesocycle(program: Program, state: SessionEditorUiState, draft: Session): Program {
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
                        val cloneForWeek = if (week.id == state.weekId) draft else com.example.kpkn.domain.templates.SessionTemplateEngine.cloneSessionContent(draft).copy(id = UUID.randomUUID().toString())
                        val updatedSessions = week.sessions.toMutableList()
                        val sameDayIndex = updatedSessions.indexOfFirst { it.dayOfWeek == draft.dayOfWeek && it.isMainSession == draft.isMainSession }
                        when {
                            updatedSessions.any { it.id == cloneForWeek.id } -> {
                                val replaceIndex = updatedSessions.indexOfFirst { it.id == cloneForWeek.id }
                                updatedSessions[replaceIndex] = cloneForWeek
                            }
                            sameDayIndex >= 0 -> updatedSessions[sameDayIndex] = cloneForWeek.copy(id = updatedSessions[sameDayIndex].id)
                            else -> updatedSessions.add(cloneForWeek)
                        }
                        week.copy(sessions = normalizeMainSessions(updatedSessions))
                    })
                })
            })
        }
    )
}

internal fun SessionEditorViewModel.normalizeMainSessions(sessions: List<Session>): List<Session> {
    val distinctSessions = sessions.distinctBy { it.id }
    val mainByDay = mutableMapOf<Int, String>()
    val fallbackByDay = mutableMapOf<Int, String>()
    distinctSessions.forEach { session ->
        val day = session.dayOfWeek ?: 1
        fallbackByDay.putIfAbsent(day, session.id)
        if (session.isMainSession && day !in mainByDay) mainByDay[day] = session.id
    }
    fallbackByDay.forEach { (day, id) -> mainByDay.putIfAbsent(day, id) }
    return distinctSessions.map { session ->
        val day = session.dayOfWeek ?: 1
        session.copy(isMainSession = mainByDay[day] == session.id)
    }
}
