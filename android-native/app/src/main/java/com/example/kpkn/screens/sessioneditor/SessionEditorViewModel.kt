package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.domain.templates.SessionTemplateEngine
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.sessionassistant.TimeCoachEngine
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimateSessionDurationMinutes
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.calculations.suggestRestSeconds
import com.example.kpkn.domain.exercises.TechnicalAspectEngine
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

@Serializable
internal data class PersistedSessionEditorDraft(
    val programId: String,
    val sessionId: String,
    val weekId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val dayOfWeek: Int? = null,
    val session: Session,
    val ruleDefaults: SessionEditorRuleDefaults = SessionEditorRuleDefaults(),
    val partRuleDefaults: Map<String, SessionEditorRuleDefaults> = emptyMap(),
    val ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
    val selectedExercisesIds: Set<String> = emptySet(),
    val savedAtMs: Long = System.currentTimeMillis(),
)


class SessionEditorViewModel(
    application: Application,
    internal val programId: String,
    private val sessionId: String,
    private val draftWeekId: String?,
    private val draftMacroIndex: Int?,
    private val draftMesoIndex: Int?,
    private val draftDayOfWeek: Int?,
) : AndroidViewModel(application) {

    companion object {
        internal const val MAX_LOCAL_DRAFT_SNAPSHOTS = 12

        fun factory(
            programId: String,
            sessionId: String,
            draftWeekId: String? = null,
            draftMacroIndex: Int? = null,
            draftMesoIndex: Int? = null,
            draftDayOfWeek: Int? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return SessionEditorViewModel(app, programId, sessionId, draftWeekId, draftMacroIndex, draftMesoIndex, draftDayOfWeek) as T
            }
        }
    }

    internal val repository = ProgramRepository.getInstance()
    internal val augeRepository = AugeRepository.getInstance(application)
    internal val nutritionRepository = runCatching { NutritionRepository.getInstance() }.getOrNull()
    internal val templateRepository = SessionTemplateRepository.getInstance(application)
    private val ruleTemplateStore = RuleTemplateStore.getInstance(application)
    private val trainedVersionStore = TrainedSessionVersionStore.getInstance(application)

    /** Combined (system + user) template list, updated reactively. */
    val allTemplates: StateFlow<List<SessionTemplate>> = templateRepository.allTemplates
    internal val exerciseIndex: Map<String, ExerciseMuscleInfo>
        get() = catalogExerciseIndex()
    private var augeJob: Job? = null
    private var autoSaveJob: Job? = null
    private var loadSessionJob: Job? = null
    private var textHistoryDebounceJob: Job? = null
    private var textHistoryBaseline: Session? = null

    internal fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(2000)
            if (!_uiState.value.autoSaveEnabled) return@launch
            withContext(Dispatchers.IO) {
                persistRecoverableSession()
            }
        }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoSaveEnabled = enabled) }
        if (enabled) scheduleAutoSave()
    }

    private data class CachedWeeklyMetrics(
        val programId: String,
        val mesoIndex: Int,
        val settingsHash: Int,
        val catalogVersion: Int,
        val perSession: Map<String, Pair<Int, SessionAugeComputation>>,
    )

    @Volatile
    private var weeklyMetricsCache: CachedWeeklyMetrics? = null

    private var assistantJob: Job? = null

    private fun Session.contentHashForAuge(): Int {
        // Hash solo de lo que afecta AUGE: ejercicios/parts/supersets/warmup/targetDuration
        // Excluye name/description/lastModifiedAtMs/dayOfWeek
        var r = exercises.hashCode()
        r = 31 * r + parts.hashCode()
        r = 31 * r + supersetGroups.hashCode()
        r = 31 * r + (targetDurationMinutes ?: 0)
        r = 31 * r + warmup.hashCode()
        r = 31 * r + isMeetDay.hashCode()
        return r
    }

    internal val draftPrefs by lazy {
        getApplication<Application>().getSharedPreferences(SESSION_EDITOR_DRAFT_PREFS, Context.MODE_PRIVATE)
    }
    internal val draftJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(SessionEditorUiState(programId = programId))
    val uiState: StateFlow<SessionEditorUiState> = _uiState.asStateFlow()

    /** Read-only snapshot for same-package ViewModel extensions. */
    internal val currentUiState: SessionEditorUiState
        get() = _uiState.value

    internal fun updateUi(transform: (SessionEditorUiState) -> SessionEditorUiState) {
        _uiState.update(transform)
    }

    internal fun replaceUiState(state: SessionEditorUiState) {
        _uiState.value = state
    }

    internal fun draftStorageKey(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        sessionId: String,
    ): String {
        val safeWeekId = weekId.ifBlank { "__unspecified_week__" }
        return "program=$programId|week=$safeWeekId|macro=$macroIndex|meso=$mesoIndex|editor=$sessionId"
    }

    internal fun persistedDraftFor(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        sessionId: String,
    ): PersistedSessionEditorDraft? {
        val key = draftStorageKey(
            weekId = weekId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            sessionId = sessionId,
        )
        val raw = draftPrefs.getString(key, null) ?: return null
        val decoded = runCatching { draftJson.decodeFromString<PersistedSessionEditorDraft>(raw) }.getOrNull() ?: return null
        return decoded.takeIf {
            it.programId == programId &&
                it.sessionId == sessionId &&
                it.weekId == weekId &&
                it.macroIndex == macroIndex &&
                it.mesoIndex == mesoIndex
        }
    }

    internal fun persistDraft(state: SessionEditorUiState = _uiState.value): Boolean {
        val session = state.session ?: return false
        val payload = PersistedSessionEditorDraft(
            programId = programId,
            sessionId = session.id,
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            dayOfWeek = state.dayOfWeek,
            session = session,
            ruleDefaults = state.ruleDefaults,
            partRuleDefaults = state.partRuleDefaults,
            ruleLimits = state.ruleLimits,
            selectedExercisesIds = state.selectedExercisesIds,
        )
        val key = draftStorageKey(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = session.id,
        )
        return runCatching {
            draftPrefs.edit().putString(key, draftJson.encodeToString(payload)).apply()
        }.isSuccess
    }

    internal fun persistRecoverableSession(state: SessionEditorUiState = _uiState.value): Boolean {
        val session = state.session?.ensureModifiedTimestamp() ?: return false
        val draftOk = persistDraft(state.copy(session = session))
        if (state.weekId.isNotBlank()) {
            repository.upsertSessionInProgram(
                programId = programId,
                weekId = state.weekId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                session = session,
            )
        }
        return draftOk
    }

    internal fun clearPersistedDraft(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        sessionId: String,
    ) {
        val key = draftStorageKey(
            weekId = weekId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            sessionId = sessionId,
        )
        draftPrefs.edit().remove(key).apply()
    }

    fun saveDraftForExit() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = persistRecoverableSession()
            if (ok) {
                _uiState.update { it.copy(hasUnsavedChanges = false) }
            }
        }
    }

    fun retryLoadSession() {
        _uiState.update { it.copy(loadErrorMessage = null) }
        loadSession()
    }

    fun discardDraftForCurrentSession() {
        val state = _uiState.value
        val session = state.session ?: return
        clearPersistedDraft(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = session.id,
        )
        _uiState.update { it.copy(pendingTransferToDays = null) }
        textHistoryBaseline = null
        textHistoryDebounceJob?.cancel()
    }

    init {
        observeBodyMeasurements()
        observeRepositoryRecovery()
        loadSession()
    }

    private fun observeRepositoryRecovery() {
        viewModelScope.launch {
            repository.programs.collect {
                val state = _uiState.value
                if (state.session == null || state.loadErrorMessage != null) {
                    loadSession()
                }
            }
        }
        viewModelScope.launch {
            repository.isReady.collect { ready ->
                if (ready && (_uiState.value.session == null || _uiState.value.loadErrorMessage != null)) {
                    loadSession()
                }
            }
        }
    }

    private fun observeBodyMeasurements() {
        val repo = nutritionRepository ?: return
        viewModelScope.launch {
            repo.bodyMeasurements.collect { entries ->
                val latest = entries
                    .asSequence()
                    .filter { it.weight != null }
                    .maxByOrNull { it.date }
                _uiState.update { it.copy(latestBodyMeasurement = latest) }
            }
        }
    }

    internal fun latestBodyMeasurementOrNull(): BodyMeasurementEntry? {
        return nutritionRepository
            ?.bodyMeasurements
            ?.value
            ?.asSequence()
            ?.filter { it.weight != null }
            ?.maxByOrNull { it.date }
    }

    internal fun loadSession() {
        loadSessionJob?.cancel()
        loadSessionJob = viewModelScope.launch {
            val loadedState = withContext(Dispatchers.IO) {
                loadSessionInternal()
            } ?: return@launch
            replaceUiState(loadedState)
            refreshDerivedStateImmediate()
            loadHistory()
        }
    }

    private fun loadSessionInternal(): SessionEditorUiState? {
        val program = repository.getProgramById(programId)
        if (program == null) {
            if (repository.isReady.value) {
                Log.w("SessionEditor", "Program not found. programId=$programId sessionId=$sessionId")
                _uiState.update { it.copy(loadErrorMessage = "No pudimos recuperar este programa.") }
            }
            return null
        }
        val located = locateSession(program, sessionId, draftWeekId, draftMacroIndex, draftMesoIndex)
        val targetWeekId = located?.week?.id ?: draftWeekId.orEmpty()
        val targetMacroIndex = draftMacroIndex ?: located?.macroIndex ?: 0
        val targetMesoIndex = draftMesoIndex ?: located?.mesoIndex ?: 0
        val week = located?.week ?: findWeek(program, targetMacroIndex, targetMesoIndex, targetWeekId)
        if (week == null && targetWeekId.isNotBlank() && repository.isReady.value) {
            Log.w("SessionEditor", "Week not found. programId=$programId weekId=$targetWeekId sessionId=$sessionId")
            _uiState.update { it.copy(loadErrorMessage = "No pudimos recuperar la semana de esta sesión.") }
            return null
        }
        val existing = located?.session
        val fallbackDraft = existing ?: createDraftSession(sessionId, draftDayOfWeek)
        val persistedDraft = persistedDraftFor(
            weekId = targetWeekId,
            macroIndex = targetMacroIndex,
            mesoIndex = targetMesoIndex,
            sessionId = fallbackDraft.id,
        )
        val draft = SupersetRules.normalizeSession(
            resolveNewestSession(existing, fallbackDraft, persistedDraft).normalizedIdentityFields(),
        )
        val weekSessions = ensureSessionInList(week?.sessions.orEmpty(), draft)
        if (existing == null && week != null && targetWeekId.isNotBlank()) {
            repository.upsertSessionInProgram(
                programId = programId,
                weekId = targetWeekId,
                macroIndex = targetMacroIndex,
                mesoIndex = targetMesoIndex,
                session = draft.ensureModifiedTimestamp(),
            )
        }
        val resolvedRuleDefaults = persistedDraft?.ruleDefaults ?: run {
            val exercises = draft.allExercises()
            if (exercises.isEmpty()) SessionEditorRuleDefaults() else {
                val restValues = exercises.mapNotNull { it.restTime }.sorted()
                val medianRest = if (restValues.isEmpty()) 90 else restValues[restValues.size / 2]
                val sideValues = exercises.mapNotNull { it.restBetweenSidesSeconds }.sorted()
                val medianSide = if (sideValues.isEmpty()) 0 else sideValues[sideValues.size / 2]
                val avgSets = exercises.map { it.sets.size.coerceAtLeast(1) }.average().takeIf { it.isFinite() }?.roundToInt()?.coerceIn(1, 6) ?: 3
                val avgReps = exercises.flatMap { it.sets }.mapNotNull { it.targetReps }.average().takeIf { it.isFinite() }?.roundToInt()?.coerceIn(1, 30) ?: 10
                val avgRpe = exercises.flatMap { it.sets }.mapNotNull { it.targetRPE }.average().takeIf { it.isFinite() }?.coerceIn(1.0, 10.0) ?: 8.0
                val supersetGroups = draft.allSupersetGroups()
                val avgBetween = supersetGroups.map { it.restBetweenExercises }.average().takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 600) ?: 60
                val avgRound = supersetGroups.map { it.restAfterSuperset }.average().takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 600) ?: 120
                SessionEditorRuleDefaults(
                    setCount = avgSets,
                    reps = avgReps,
                    rpe = avgRpe,
                    normalRestSeconds = medianRest.coerceIn(0, 600),
                    betweenSidesRestSeconds = medianSide.coerceIn(0, 300),
                    supersetBetweenRestSeconds = avgBetween,
                    supersetRoundRestSeconds = avgRound,
                )
            }
        }
        val resolvedPartRuleDefaults = persistedDraft?.partRuleDefaults ?: emptyMap()
        val resolvedRuleLimits = persistedDraft?.ruleLimits ?: SessionEditorRuleLimits()
        val loadedFromDraft = persistedDraft != null && persistedDraft.session != existing
        val roadmapOptions = buildRoadmapOptions(program)
        val cloneDayOptions = buildCloneDayOptions(program, currentSessionId = draft.id)
        val cloneSourceOptions = buildCloneSourceOptions(program, currentSessionId = draft.id)

        val allProgramExerciseCandidates = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { week ->
                week.sessions.flatMap { session ->
                    val sessionName = session.name.ifBlank { "Día ${session.dayOfWeek ?: "?"}" }
                    val loose = session.exercises.map { ex ->
                        ProgramExerciseCandidate(
                            exerciseId = ex.id,
                            exerciseName = ex.name,
                            exerciseDbId = ex.exerciseDbId,
                            sessionDayOfWeek = session.dayOfWeek,
                            sessionName = sessionName,
                            partName = null,
                        )
                    }
                    val inParts = session.parts.flatMap { part ->
                        part.exercises.map { ex ->
                            ProgramExerciseCandidate(
                                exerciseId = ex.id,
                                exerciseName = ex.name,
                                exerciseDbId = ex.exerciseDbId,
                                sessionDayOfWeek = session.dayOfWeek,
                                sessionName = sessionName,
                                partName = part.name,
                            )
                        }
                    }
                    loose + inParts
                }
            }
            .distinctBy { it.exerciseId }
        val competitionMovementIds = buildCompetitionMovementIds(program)
        val competitionKeyDaysInWeek = buildCompetitionKeyDaysInWeek(program, week)

        return SessionEditorUiState(
            session = draft,
            originalSession = SupersetRules.normalizeSession((existing ?: draft).normalizedIdentityFields()),
            loadErrorMessage = null,
            programId = programId,
            draftBundle = SessionDraftBundle(
                sessionId = draft.id,
                weekId = targetWeekId,
                macroIndex = targetMacroIndex,
                mesoIndex = targetMesoIndex,
                dayOfWeek = draft.dayOfWeek ?: draftDayOfWeek,
                siblingSessionIds = weekSessions.map { it.id },
                weekSessionIds = weekSessions.map { it.id },
            ),
            weekId = targetWeekId,
            macroIndex = targetMacroIndex,
            mesoIndex = targetMesoIndex,
            dayOfWeek = draft.dayOfWeek ?: draftDayOfWeek,
            isNewSession = existing == null,
            siblingSessions = weekSessions.sortedBy { it.dayOfWeek ?: 99 },
            weekSessions = weekSessions,
            weekStartDay = (program.startDay ?: 1).coerceIn(1, 7),
            roadmapOptions = roadmapOptions,
            cloneDayOptions = cloneDayOptions,
            cloneSourceOptions = cloneSourceOptions,
            selectedSiblingSessionId = draft.id,
            localDraftHistory = trainedVersionStore.loadForSession(draft.id),
            ruleDefaults = resolvedRuleDefaults,
            partRuleDefaults = resolvedPartRuleDefaults,
            ruleLimits = resolvedRuleLimits,
            ruleTemplates = ruleTemplateStore.loadAll(),
            hasUnsavedChanges = loadedFromDraft,
            isSimpleProgram = program.isSimpleTemporalProgram,
            hasActiveLoops = program.loops.isNotEmpty() && program.loopState != null,
            latestBodyMeasurement = latestBodyMeasurementOrNull(),
            allProgramExerciseCandidates = allProgramExerciseCandidates,
            competitionMovementIds = competitionMovementIds,
            competitionKeyDaysInWeek = competitionKeyDaysInWeek,
            selectedExercisesIds = persistedDraft?.selectedExercisesIds.orEmpty(),
        )
    }

    private fun buildCompetitionMovementIds(program: Program): Set<String> {
        val plannedIds = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { week -> week.sessions }
            .filter { it.isCompetitionMeet }
            .flatMap { session -> session.allExercises() }
            .filter { it.isCompetitionLift }
            .flatMap { exercise ->
                listOfNotNull(
                    exercise.resolvedCanonicalExerciseId(),
                    exercise.exerciseDbId,
                    exercise.exerciseId,
                    exercise.canonicalExerciseId,
                )
            }
        val recordIds = runCatching {
            CompetitionRepository.getInstance().activeCompetitionExerciseIds()
        }.getOrDefault(emptySet())
        return (plannedIds + recordIds).filter { it.isNotBlank() }.toSet()
    }

    internal fun loadHistory() {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            val logs = repository.getLogsForSession(currentSession.id).sortedByDescending { it.date }
            val feedbackByLogId = logs.mapNotNull { log ->
                augeRepository.getFeedbackForLog(log.id)?.let { log.id to it }
            }.toMap()
            _uiState.update { it.copy(workoutLogs = logs, feedbackByLogId = feedbackByLogId) }
        }
    }

    internal fun updateSession(reason: String = "Edición", transform: (Session) -> Session) {
        val current = _uiState.value.session ?: return
        val transformed = transform(current)
        if (transformed == current) return
        val updated = transformed.copy(lastModifiedAtMs = System.currentTimeMillis())
        _uiState.update { state ->
            state.copy(
                session = updated,
                dayOfWeek = updated.dayOfWeek ?: state.dayOfWeek,
                hasUnsavedChanges = updated != state.originalSession,
            )
        }
        scheduleAugeRecalc()
        scheduleAutoSave()
    }

    fun updateCurrentSession(transform: (Session) -> Session) = updateSession(transform = transform)

    private fun updateSessionDay(dayOfWeek: Int) = updateSession { session ->
        session.copy(dayOfWeek = dayOfWeek)
    }

    internal fun scheduleAugeRecalc() {
        augeJob?.cancel()
        augeJob = viewModelScope.launch {
            delay(300)
            withContext(Dispatchers.Default) {
                val state = _uiState.value
                val session = state.session ?: return@withContext
                recalcAndPushAuge(state, session)
            }
        }
    }

    internal fun refreshDerivedStateImmediate() {
        augeJob?.cancel()
        augeJob = null
        val state = _uiState.value
        val session = state.session ?: return
        viewModelScope.launch(Dispatchers.Default) {
            recalcAndPushAuge(state, session)
        }
    }

    private fun recalcAndPushAuge(state: SessionEditorUiState, session: Session) {
        val settingsEarly = repository.settings.value
        val exercises = session.allExercises()
        val totalSets = exercises.sumOf { it.sets.size.coerceAtLeast(1) }
        val averageRest = exercises.mapNotNull { it.restTime }.ifEmpty { listOf(settingsEarly.restTimerDefaultSeconds) }.average().toInt()
        val draftAwareWeekSessions = if (state.weekSessions.any { it.id == session.id }) {
            state.weekSessions.map { if (it.id == session.id) session else it }
        } else {
            state.weekSessions + session
        }
        val sessionEnergy = runCatching {
            TrainingEnergyEngine.estimatePlannedSession(session, repository.settings.value)
        }.getOrElse { SessionEnergySummary() }
        val programLogs = repository.getLogsForProgram(state.programId)
        val program = repository.getProgramById(state.programId)
        val settingsVal = repository.settings.value
        val index = exerciseIndex
        val catalogVersion = index.size
        val settingsHash = settingsVal.hashCode()
        val cache = weeklyMetricsCache
        val canReuseCache = cache != null && cache.programId == state.programId && cache.mesoIndex == state.mesoIndex && cache.settingsHash == settingsHash && cache.catalogVersion == catalogVersion
        val perSessionMutable = mutableMapOf<String, Pair<Int, SessionAugeComputation>>()
        if (canReuseCache) {
            perSessionMutable.putAll(cache!!.perSession)
        }
        fun cachedCompute(s: Session): SessionAugeComputation {
            val h = s.contentHashForAuge()
            val cached = if (canReuseCache) cache?.perSession?.get(s.id) else null
            if (cached != null && cached.first == h) return cached.second
            val computed = computeSessionAugeComputation(s, index, settingsVal, programLogs, state.programId, state.mesoIndex)
            perSessionMutable[s.id] = h to computed
            return computed
        }
        val summary = runCatching {
            val currentMetrics = cachedCompute(session)
            val weeklyMetrics = draftAwareWeekSessions.map { cachedCompute(it) }
            weeklyMetricsCache = CachedWeeklyMetrics(
                programId = state.programId,
                mesoIndex = state.mesoIndex,
                settingsHash = settingsHash,
                catalogVersion = catalogVersion,
                perSession = perSessionMutable,
            )
            buildAugeSummaryFromMetrics(
                currentSession = session,
                weekSessions = draftAwareWeekSessions,
                currentMetrics = currentMetrics,
                weeklyMetrics = weeklyMetrics,
                program = program,
                settings = settingsVal,
            )
        }.getOrElse {
            // Fallback sin cache
            try {
                buildAugeSummary(
                    currentSession = session,
                    weekSessions = draftAwareWeekSessions,
                    exerciseIndex = index,
                    settings = settingsVal,
                    programLogs = programLogs,
                    program = program,
                    programId = state.programId,
                    mesoIndex = state.mesoIndex,
                )
            } catch (_: Throwable) {
                SessionEditorAugeSummary(
                    sessionDrain = PredictedDrain(0, 0, 0),
                    weeklyDrain = PredictedDrain(0, 0, 0),
                    sessionSetCount = totalSets,
                    sessionDurationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
                    sessionEnergy = sessionEnergy,
                )
            }
        }
        val timeBreakdown = runCatching {
            calculateSessionTimeBreakdown(
                exercises = exercises,
                supersetGroups = session.allSupersetGroups(),
                sessionWarmup = session.warmup,
                restTimerDefaultSeconds = settingsVal.restTimerDefaultSeconds,
            )
        }.getOrNull()
        // Assistant bajo demanda: inmediato si AUGE abierto, si no debounce 2500ms
        val shouldEvaluateAssistantNow = state.sheet == SessionEditorSheet.AUGE
        if (shouldEvaluateAssistantNow) {
            val assistantReport = runCatching {
                val templates = allTemplates.value
                SessionAssistantEngine.evaluate(
                    input = SessionAssistantInput(
                        allExercisesInSession = session.allExercises(),
                        weekSessions = draftAwareWeekSessions,
                        currentSessionId = session.id,
                        program = program,
                        settings = settingsVal,
                        workoutLogs = programLogs,
                        exerciseIndex = index,
                        ruleLimits = com.example.kpkn.domain.sessionassistant.SessionEditorRuleLimits(
                            maxRPE = state.ruleLimits.maxRPE ?: 10.0,
                            maxExercisesPerMuscle = state.ruleLimits.maxExercisesPerMuscle ?: 6,
                            maxVolumePerMuscleSession = state.ruleLimits.maxVolumePerMuscleSession ?: 12.0,
                            maxVolumePerMuscleWeekly = state.ruleLimits.maxVolumePerMuscleWeekly ?: 24.0,
                            maxSamePatternPerSession = state.ruleLimits.maxSamePatternPerSession ?: 4,
                            rigidLimits = state.ruleLimits.rigidLimits,
                        ),
                        mesoIndex = state.mesoIndex,
                        programId = state.programId,
                        targetDurationMinutes = session.targetDurationMinutes,
                        supersetGroups = session.allSupersetGroups(),
                        sessionWarmup = session.warmup,
                    ),
                    allTemplates = templates,
                )
            }.getOrNull()
            _uiState.update {
                it.copy(
                    estimatedDurationMinutes = timeBreakdown?.totalMinutes
                        ?: estimateSessionDurationMinutes(totalSets, averageRest),
                    sessionTimeBreakdown = timeBreakdown,
                    predictedDrain = summary.sessionDrain,
                    augeSummary = summary.copy(
                        sessionEnergy = sessionEnergy,
                        sessionTimeBreakdown = timeBreakdown,
                        sessionDurationMinutes = timeBreakdown?.totalMinutes
                            ?: estimateSessionDurationMinutes(totalSets, averageRest),
                    ),
                    assistantReport = assistantReport,
                    ghostExerciseCards = assistantReport?.tarjetasFantasma ?: emptyList(),
                )
            }
        } else {
            // Rings/volumen sin assistant inmediato
            _uiState.update {
                it.copy(
                    estimatedDurationMinutes = timeBreakdown?.totalMinutes
                        ?: estimateSessionDurationMinutes(totalSets, averageRest),
                    sessionTimeBreakdown = timeBreakdown,
                    predictedDrain = summary.sessionDrain,
                    augeSummary = summary.copy(
                        sessionEnergy = sessionEnergy,
                        sessionTimeBreakdown = timeBreakdown,
                        sessionDurationMinutes = timeBreakdown?.totalMinutes
                            ?: estimateSessionDurationMinutes(totalSets, averageRest),
                    ),
                )
            }
            assistantJob?.cancel()
            assistantJob = viewModelScope.launch(Dispatchers.Default) {
                delay(2500)
                val s = _uiState.value
                val sess = s.session ?: return@launch
                // Si se abrió AUGE entretanto, el otro path ya se encargó
                if (s.sheet == SessionEditorSheet.AUGE) return@launch
                val prog = repository.getProgramById(s.programId)
                val logs = repository.getLogsForProgram(s.programId)
                val setVal = repository.settings.value
                val idx = exerciseIndex
                val report = runCatching {
                    val templates = allTemplates.value
                    SessionAssistantEngine.evaluate(
                        input = SessionAssistantInput(
                            allExercisesInSession = sess.allExercises(),
                            weekSessions = if (s.weekSessions.any { it.id == sess.id }) s.weekSessions.map { if (it.id == sess.id) sess else it } else s.weekSessions + sess,
                            currentSessionId = sess.id,
                            program = prog,
                            settings = setVal,
                            workoutLogs = logs,
                            exerciseIndex = idx,
                            ruleLimits = com.example.kpkn.domain.sessionassistant.SessionEditorRuleLimits(
                                maxRPE = s.ruleLimits.maxRPE ?: 10.0,
                                maxExercisesPerMuscle = s.ruleLimits.maxExercisesPerMuscle ?: 6,
                                maxVolumePerMuscleSession = s.ruleLimits.maxVolumePerMuscleSession ?: 12.0,
                                maxVolumePerMuscleWeekly = s.ruleLimits.maxVolumePerMuscleWeekly ?: 24.0,
                                maxSamePatternPerSession = s.ruleLimits.maxSamePatternPerSession ?: 4,
                                rigidLimits = s.ruleLimits.rigidLimits,
                            ),
                            mesoIndex = s.mesoIndex,
                            programId = s.programId,
                            targetDurationMinutes = sess.targetDurationMinutes,
                            supersetGroups = sess.allSupersetGroups(),
                            sessionWarmup = sess.warmup,
                        ),
                        allTemplates = templates,
                    )
                }.getOrNull()
                _uiState.update { it.copy(assistantReport = report, ghostExerciseCards = report?.tarjetasFantasma ?: emptyList()) }
            }
        }
    }

    fun refreshAssistantImmediate() {
        val state = _uiState.value
        val session = state.session ?: return
        assistantJob?.cancel()
        viewModelScope.launch(Dispatchers.Default) {
            recalcAndPushAuge(state, session)
        }
    }

    // ─── Feature 2: Duración objetivo ────────────────────────────────────────────

    /** Actualiza la duración objetivo de la sesión (Feature 2). null = sin límite. */
    fun updateSessionName(name: String) = updateSessionTextField { it.copy(name = name) }
    fun updateSessionDescription(description: String) = updateSessionTextField { it.copy(description = description) }

    /** Text edits: debounce autosave; no AUGE recalc for name/description. */
    private fun updateSessionTextField(transform: (Session) -> Session) {
        val current = _uiState.value.session ?: return
        _uiState.update { state ->
            val base = state.session ?: return@update state
            val updated = transform(base).copy(lastModifiedAtMs = System.currentTimeMillis())
            state.copy(
                session = updated,
                dayOfWeek = updated.dayOfWeek ?: state.dayOfWeek,
                hasUnsavedChanges = updated != state.originalSession,
            )
        }
        scheduleAutoSave()
        textHistoryDebounceJob?.cancel()
        textHistoryDebounceJob = viewModelScope.launch {
            delay(800)
            textHistoryBaseline = null
        }
    }
    fun updateSessionMeetDay(isMeetDay: Boolean) {
        val current = _uiState.value.session ?: return
        if (!isMeetDay) {
            val backup = current.trainingBackup
            if (backup != null && backup.catalogSchemaVersion < 2) {
                updateUi { it.copy(snackbarMessage = "No se puede restaurar un respaldo de sesión anterior al catálogo actual.") }
                return
            }
        }
        updateSession {
            if (isMeetDay) {
                it.copy(
                    isMeetDay = true,
                    isCompetitionSession = true,
                    trainingBackup = TrainingBackup(
                        exercises = it.exercises,
                        parts = it.parts,
                        warmup = it.warmup,
                        savedAtMs = System.currentTimeMillis(),
                        catalogSchemaVersion = 2,
                    ),
                    exercises = emptyList(),
                    parts = emptyList(),
                    warmup = emptyList(),
                )
            } else {
                val backup = it.trainingBackup
                if (backup != null && backup.catalogSchemaVersion >= 2) {
                    it.copy(
                        isMeetDay = false,
                        isCompetitionSession = false,
                        exercises = backup.exercises,
                        parts = backup.parts,
                        warmup = backup.warmup,
                        trainingBackup = null,
                    )
                } else {
                    it.copy(isMeetDay = false, isCompetitionSession = false)
                }
            }
        }
    }    fun updateSessionMeetBodyweight(bodyweight: Double?) = updateSession { it.copy(meetBodyweight = bodyweight) }

    fun syncMeetBodyweightFromLatestMeasurement(): SessionEditorSaveResult {
        val latest = _uiState.value.latestBodyMeasurement ?: return SessionEditorSaveResult(
            success = false,
            message = "No hay una medición corporal reciente para sincronizar.",
        )
        val weight = latest.weight ?: return SessionEditorSaveResult(
            success = false,
            message = "La última medición no incluye peso.",
        )
        updateSession { it.copy(meetBodyweight = weight) }
        return SessionEditorSaveResult(
            success = true,
            message = "Peso sincronizado desde medición (${formatOneDecimal(weight)} kg · ${latest.date}).",
        )
    }
    fun updateDayOfWeek(dayOfWeek: Int) = updateSessionDay(dayOfWeek)
    fun openSheet(sheet: SessionEditorSheet) {
        _uiState.update { state ->
            state.copy(
                sheet = sheet,
                rulesSheetInitialTab = if (sheet == SessionEditorSheet.RULES) 0 else state.rulesSheetInitialTab,
                quickActionsPartId = if (sheet == SessionEditorSheet.QUICK_ACTIONS) state.quickActionsPartId else null,
                quickActionsExerciseId = if (sheet == SessionEditorSheet.QUICK_ACTIONS) state.quickActionsExerciseId else null,
            )
        }
        if (sheet == SessionEditorSheet.AUGE) {
            refreshAssistantImmediate()
        }
    }

    fun openRulesSheet(initialTab: Int = 0) {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.RULES,
                rulesSheetInitialTab = initialTab.coerceIn(0, 1),
            )
        }
        if (initialTab == 1) {
            refreshTimeCoachSuggestions()
        }
    }

    fun clearRulesSheetInitialTab() {
        _uiState.update { it.copy(rulesSheetInitialTab = 0) }
    }

    /** Genera sugerencias del coach solo cuando el usuario abre TIEMPO. */
    fun refreshTimeCoachSuggestions() {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val session = state.session ?: return@launch
            val settingsForBreakdown = repository.settings.value
            val breakdown = state.sessionTimeBreakdown ?: runCatching {
                calculateSessionTimeBreakdown(
                    exercises = session.allExercises(),
                    supersetGroups = session.allSupersetGroups(),
                    sessionWarmup = session.warmup,
                    restTimerDefaultSeconds = settingsForBreakdown.restTimerDefaultSeconds,
                )
            }.getOrNull() ?: return@launch
            val suggestions = runCatching {
                TimeCoachEngine.generate(
                    session = session,
                    breakdown = breakdown,
                    targetDurationMinutes = session.targetDurationMinutes,
                    exerciseIndex = exerciseIndex,
                    dismissedIds = state.dismissedTimeCoachIds,
                )
            }.getOrDefault(emptyList())
            updateUi { it.copy(timeCoachSuggestions = suggestions) }
        }
    }

    fun applyRuleTemplate(templateId: String, partId: String? = null) {
        val template = currentUiState.ruleTemplates.firstOrNull { it.id == templateId } ?: return
        patchRuleDefaults(partId) { template.defaults }
    }

    fun saveCurrentRulesAsTemplate(name: String) {
        val created = ruleTemplateStore.saveAsTemplate(name, currentUiState.ruleDefaults)
        updateUi { it.copy(ruleTemplates = ruleTemplateStore.loadAll(), snackbarMessage = "Plantilla «${created.name}» guardada") }
    }

    fun renameRuleTemplate(templateId: String, name: String) {
        updateUi { it.copy(ruleTemplates = ruleTemplateStore.rename(templateId, name)) }
    }

    fun deleteRuleTemplate(templateId: String) {
        updateUi { it.copy(ruleTemplates = ruleTemplateStore.delete(templateId)) }
    }

    fun applyTimeCoachSuggestion(suggestionId: String) {
        val suggestion = currentUiState.timeCoachSuggestions.firstOrNull { it.id == suggestionId } ?: return
        updateSession { session ->
            TimeCoachEngine.apply(session, suggestion.action)
        }
        val action = suggestion.action
        if (action is com.example.kpkn.domain.sessionassistant.TimeCoachAction.ReduceRests &&
            action.alsoUpdateRuleDefaults
        ) {
            updateUi { state ->
                state.copy(
                    ruleDefaults = state.ruleDefaults.copy(normalRestSeconds = action.targetRestSeconds),
                    dismissedTimeCoachIds = state.dismissedTimeCoachIds + suggestionId,
                    snackbarMessage = "Ajuste de tiempo aplicado (−${suggestion.minutesSaved} min)",
                )
            }
        } else {
            updateUi { state ->
                state.copy(
                    dismissedTimeCoachIds = state.dismissedTimeCoachIds + suggestionId,
                    snackbarMessage = "Ajuste de tiempo aplicado (−${suggestion.minutesSaved} min)",
                )
            }
        }
        // Tras el recalc asíncrono, refrescar coach cuando termine (debounce corto).
        viewModelScope.launch {
            delay(400)
            refreshTimeCoachSuggestions()
        }
    }

    fun dismissTimeCoachSuggestion(suggestionId: String) {
        updateUi {
            it.copy(
                dismissedTimeCoachIds = it.dismissedTimeCoachIds + suggestionId,
                timeCoachSuggestions = it.timeCoachSuggestions.filterNot { s -> s.id == suggestionId },
            )
        }
    }

    fun closeSheet() {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.NONE,
                searchQuery = "",
                pickerTargetPartId = null,
                pickerTargetExerciseId = null,
                warmupExerciseId = null,
                quickActionsPartId = null,
                quickActionsExerciseId = null,
                supersetManagerPartId = null,
                supersetManagerSupersetId = null,
                supersetDraft = null,
                templateSearchQuery = "",
                templateApplyDecision = null,
            )
        }
    }

    // ─── Session Templates ────────────────────────────────────────────────────

    /** Opens the template browser sheet. */

    fun restoreDraftSnapshot(snapshot: SessionDraftSnapshot) {
        _uiState.update { state ->
            val restoredSession = snapshot.session
            state.copy(
                session = restoredSession,
                hasUnsavedChanges = restoredSession != state.originalSession,
                sheet = SessionEditorSheet.NONE,
                snackbarMessage = "Versión restaurada · ${formatHistoryTimestamp(snapshot.savedAtMs)}",
            )
        }
        textHistoryBaseline = null
        textHistoryDebounceJob?.cancel()
        scheduleAugeRecalc()
        scheduleAutoSave()
    }

    /** Reloads trained versions for the current session (e.g. after finishing a workout). */
    fun refreshTrainedVersions() {
        val sessionId = _uiState.value.session?.id ?: return
        _uiState.update {
            it.copy(localDraftHistory = trainedVersionStore.loadForSession(sessionId))
        }
    }

}
