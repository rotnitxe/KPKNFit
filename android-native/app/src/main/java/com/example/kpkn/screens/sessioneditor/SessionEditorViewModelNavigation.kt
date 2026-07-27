package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.CompetitionSessionSync
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
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
            if (!persistRecoverableSession(state)) {
                updateUi { it.copy(snackbarMessage = "Error al guardar el borrador de la sesión actual") }
                return
            }
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
        if (!persistRecoverableSession(state)) {
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
            localDraftHistory = listOf(buildDraftSnapshot(session = newSession, previous = null, reason = "Nueva sesión")),
            hasUnsavedChanges = false,
            draftBundle = it.draftBundle?.copy(sessionId = newSession.id, dayOfWeek = dayOfWeek),
            snackbarMessage = "Sesión creada para ${dayLabel(dayOfWeek)}",
        )
    }
    refreshDerivedStateImmediate()
    loadHistory()
    return SessionEditorSaveResult(success = true, message = "")
}

fun SessionEditorViewModel.createCompetitionSessionForDay(dayOfWeek: Int): SessionEditorSaveResult {
    val state = currentUiState
    if (state.hasUnsavedChanges) {
        if (!persistRecoverableSession(state)) {
            updateUi { it.copy(snackbarMessage = "Error al guardar el borrador de la sesión actual") }
            return SessionEditorSaveResult(success = false, message = "")
        }
    }

    val existingOnDay = state.weekSessions.firstOrNull { it.dayOfWeek == dayOfWeek }
    if (existingOnDay != null) {
        requestSessionSwitch(existingOnDay.id)
        return SessionEditorSaveResult(success = true, message = "")
    }

    val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(
        success = false,
        message = "No pudimos recuperar el programa.",
    )
    val week = findWeek(program, state.macroIndex, state.mesoIndex, state.weekId)
    val keyDate = week?.let { findCompetitionKeyDateForWeekDay(program, it, dayOfWeek) }
    val eventDate = keyDate?.eventDate ?: keyDate?.startDate
    val sessionName = keyDate?.title?.takeIf { it.isNotBlank() } ?: "Sesion Competicion ${dayLabel(dayOfWeek)}"

    val newSession = createDraftSession(UUID.randomUUID().toString(), dayOfWeek).copy(
        name = sessionName,
        isMainSession = true,
        isMeetDay = true,
        isCompetitionSession = true,
        focus = "Competición",
        competitionDetails = CompetitionDetails(
            competitionDate = eventDate,
        ),
        competitionRecordId = UUID.randomUUID().toString(),
        competitionKeyDateId = keyDate?.id,
        competitionRecordMode = CompetitionRecordMode.HYBRID,
        competitionSportType = defaultCompetitionSportType(program.mode),
        lastModifiedAtMs = System.currentTimeMillis(),
    )
    if (!repository.upsertSessionInProgram(programId, state.weekId, state.macroIndex, state.mesoIndex, newSession)) {
        return SessionEditorSaveResult(success = false, message = "No pudimos crear la sesión de competición en esta semana.")
    }
    // Crear el CompetitionRecord de forma atómica junto con la sesión: evita records huérfanos
    // si el usuario navega fuera antes de guardar la sesión explícitamente.
    CompetitionSessionSync.merge(newSession, existingRecord = null, programId = program.id, weekId = state.weekId)
        ?.let { record -> runCatching { CompetitionRepository.getInstance() }.getOrNull()?.upsert(record) }

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
            localDraftHistory = listOf(buildDraftSnapshot(session = newSession, previous = null, reason = "Nueva sesión competición")),
            hasUnsavedChanges = false,
            draftBundle = it.draftBundle?.copy(sessionId = newSession.id, dayOfWeek = dayOfWeek),
            snackbarMessage = "Sesión de competición creada para ${dayLabel(dayOfWeek)}",
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
            localDraftHistory = listOf(buildDraftSnapshot(session = resolvedSession, previous = null, reason = "Cambio de sesión")),
            ruleDefaults = persistedDraft?.ruleDefaults ?: it.ruleDefaults,
            partRuleDefaults = persistedDraft?.partRuleDefaults ?: emptyMap(),
            ruleLimits = persistedDraft?.ruleLimits ?: it.ruleLimits,
            selectedExercisesIds = persistedDraft?.selectedExercisesIds.orEmpty(),
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

    repository.updateProgram(updatedProgram)
    syncCompetitionRecordFromSession(draft, program, state.weekId)
    clearPersistedDraft(
        weekId = state.weekId,
        macroIndex = state.macroIndex,
        mesoIndex = state.mesoIndex,
        sessionId = draft.id,
    )
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
            localDraftHistory = listOf(buildDraftSnapshot(session = draft, previous = null, reason = "Guardado")),
            pendingSessionSwitchId = null,
            pendingWeekId = null,
            pendingMacroIndex = null,
            pendingMesoIndex = null,
            roadmapOptions = buildRoadmapOptions(program),
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
    return SessionEditorSaveResult(true, "Sesión guardada$warningSuffix")
}

internal fun SessionEditorViewModel.syncCompetitionRecordFromSession(session: Session, program: Program, weekId: String) {
    if (!session.isCompetitionMeet) return
    val repository = runCatching { CompetitionRepository.getInstance() }.getOrNull() ?: return
    val existing = session.competitionRecordId?.let { repository.getById(it) }
    val merged = CompetitionSessionSync.merge(session, existing, program.id, weekId) ?: return
    repository.upsert(merged)
}

internal fun SessionEditorViewModel.appendDraftSnapshot(
    history: List<SessionDraftSnapshot>,
    snapshot: SessionDraftSnapshot,
): List<SessionDraftSnapshot> {
    val last = history.lastOrNull()
    if (last != null && last.session == snapshot.session) return history
    return (history + snapshot).takeLast(SessionEditorViewModel.MAX_LOCAL_DRAFT_SNAPSHOTS)
}

internal fun SessionEditorViewModel.buildDraftSnapshot(
    session: Session,
    previous: Session?,
    reason: String,
): SessionDraftSnapshot {
    val changedFields = if (previous == null) {
        listOf("base")
    } else {
        detectChangedFields(previous = previous, current = session)
    }
    val exercises = session.allExercises()
    return SessionDraftSnapshot(
        id = UUID.randomUUID().toString(),
        session = session,
        savedAtMs = System.currentTimeMillis(),
        reason = reason,
        changedFields = changedFields,
        exerciseCount = exercises.size,
        setCount = exercises.sumOf { it.sets.size.coerceAtLeast(1) },
        partCount = session.parts.size,
    )
}

internal fun SessionEditorViewModel.detectChangedFields(previous: Session, current: Session): List<String> {
    val changes = mutableListOf<String>()
    if (previous.name != current.name) changes += "nombre"
    if (previous.description != current.description) changes += "descripción"
    if (previous.dayOfWeek != current.dayOfWeek) changes += "día"
    if (previous.parts.size != current.parts.size) changes += "grupos"
    val previousExercises = previous.allExercises()
    val currentExercises = current.allExercises()
    if (previousExercises.size != currentExercises.size) changes += "ejercicios"
    val previousSets = previousExercises.sumOf { it.sets.size.coerceAtLeast(1) }
    val currentSets = currentExercises.sumOf { it.sets.size.coerceAtLeast(1) }
    if (previousSets != currentSets) changes += "series"
    if (previous.isCompetitionSession != current.isCompetitionSession) changes += "modo competición"
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
                        val cloneForWeek = if (week.id == state.weekId) draft else draft.copy(id = UUID.randomUUID().toString())
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

