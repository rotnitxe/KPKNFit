package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.domain.templates.SessionTemplateEngine
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimateSessionDurationMinutes
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.calculations.suggestRestSeconds
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.VolumeCalculator
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
import java.util.UUID
import kotlin.math.roundToInt

@Serializable
data class SessionEditorRuleDefaults(
    val setCount: Int = 3,
    val reps: Int = 10,
    val rpe: Double = 8.0,
)

@Serializable
data class SessionEditorRuleLimits(
    val maxRPE: Double? = null,
    val maxExercisesPerMuscle: Int? = null,
    val maxVolumePerMuscleSession: Double? = null,
    val maxVolumePerMuscleWeekly: Double? = null,
    val maxSamePatternPerSession: Int? = null,
    val rigidLimits: Boolean = false,
)

@Serializable
private data class PersistedSessionEditorDraft(
    val programId: String,
    val sessionId: String,
    val weekId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val dayOfWeek: Int? = null,
    val session: Session,
    val ruleDefaults: SessionEditorRuleDefaults = SessionEditorRuleDefaults(),
    val ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
    val savedAtMs: Long = System.currentTimeMillis(),
)

enum class SessionEditorAugeStatus {
    OPTIMAL,
    WARNING,
    FATIGUING,
}

enum class SessionEditorAugeAlertSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

enum class SessionEditorAugeAlertSource {
    SESSION,
    WEEK,
    SYSTEM,
    EXERCISE,
}

enum class SessionEditorAugeCorrectionType {
    REDUCE_SERIES,
    REDUCE_RPE,
    REDUCE_VOLUME_RPE,
    ADD_SERIES,
}

data class SessionEditorAugeAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: SessionEditorAugeAlertSeverity,
    val source: SessionEditorAugeAlertSource = SessionEditorAugeAlertSource.SESSION,
    val muscle: String? = null,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val correctionType: SessionEditorAugeCorrectionType? = null,
)

data class SessionEditorAugeExerciseInsight(
    val exerciseId: String,
    val name: String,
    val muscular: Int,
    val cns: Int,
    val spinal: Int,
    val total: Int,
    val suggestion: String? = null,
)

data class SessionEditorVolumeThreshold(
    val sessionMev: Double,
    val sessionMav: Double,
    val sessionMrv: Double,
    val weeklyMev: Double,
    val weeklyMav: Double,
    val weeklyMrv: Double,
)

data class SessionEditorAugeSummary(
    val sessionDrain: PredictedDrain = PredictedDrain(0, 0, 0),
    val weeklyDrain: PredictedDrain = PredictedDrain(0, 0, 0),
    val sessionSetCount: Int = 0,
    val weeklySetCount: Int = 0,
    val sessionDurationMinutes: Int = 0,
    val weeklyDurationMinutes: Int = 0,
    val sessionDifficulty: Int = 0,
    val weeklyDifficulty: Int = 0,
    val status: SessionEditorAugeStatus = SessionEditorAugeStatus.OPTIMAL,
    val alerts: List<SessionEditorAugeAlert> = emptyList(),
    val suggestions: List<SessionEditorAugeAlert> = emptyList(),
    val topExercises: List<SessionEditorAugeExerciseInsight> = emptyList(),
    val muscleDrainProjection: Map<String, Int> = emptyMap(),
    val sessionVolumeByMuscle: Map<String, Double> = emptyMap(),
    val weeklyVolumeByMuscle: Map<String, Double> = emptyMap(),
    val volumeThresholdsByMuscle: Map<String, SessionEditorVolumeThreshold> = emptyMap(),
    val usesCalibratedVolumeThresholds: Boolean = false,
    val sessionEnergy: SessionEnergySummary = SessionEnergySummary(),
) {
    val alertCount: Int
        get() = alerts.size

    val criticalAlertCount: Int
        get() = 0

    val warningAlertCount: Int
        get() = 0

    val hasCriticalAlerts: Boolean
        get() = false
}

data class SessionEditorUiState(
    val session: Session? = null,
    val originalSession: Session? = null,
    val programId: String = "",
    val draftBundle: SessionDraftBundle? = null,
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val dayOfWeek: Int? = null,
    val isNewSession: Boolean = false,
    val siblingSessions: List<Session> = emptyList(),
    val weekSessions: List<Session> = emptyList(),
    val weekStartDay: Int = 1,
    val roadmapOptions: List<SessionRoadmapOption> = emptyList(),
    val cloneDayOptions: List<SessionCloneDayOption> = emptyList(),
    val cloneSourceOptions: List<SessionCloneSourceOption> = emptyList(),
    val selectedSiblingSessionId: String? = null,
    val workoutLogs: List<WorkoutLog> = emptyList(),
    val feedbackByLogId: Map<String, PostSessionFeedback> = emptyMap(),
    val localDraftHistory: List<SessionDraftSnapshot> = emptyList(),
    val sheet: SessionEditorSheet = SessionEditorSheet.NONE,
    val searchQuery: String = "",
    val pickerTargetPartId: String? = null,
    val pickerTargetExerciseId: String? = null,
    val warmupExerciseId: String? = null,
    val quickActionsPartId: String? = null,
    val quickActionsExerciseId: String? = null,
    val collapsedPartIds: Set<String> = emptySet(),
    val hasUnsavedChanges: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val estimatedDurationMinutes: Int = 0,
    val predictedDrain: PredictedDrain? = null,
    val augeSummary: SessionEditorAugeSummary = SessionEditorAugeSummary(),
    val ruleDefaults: SessionEditorRuleDefaults = SessionEditorRuleDefaults(),
    val ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
    val pendingSessionSwitchId: String? = null,
    val pendingWeekId: String? = null,
    val pendingMacroIndex: Int? = null,
    val pendingMesoIndex: Int? = null,
    val supersetManagerPartId: String? = null,
    val supersetManagerSupersetId: String? = null,
    val isSimpleProgram: Boolean = false,
    val latestBodyMeasurement: BodyMeasurementEntry? = null,
    val allProgramExerciseCandidates: List<ProgramExerciseCandidate> = emptyList(),
    // ─── Session Templates ────────────────────────────────────────────────────
    /** Free-text filter applied to the template picker list. */
    val templateSearchQuery: String = "",
    /**
     * Non-null when the user selected a template but the session already has
     * content: the editor waits for an explicit [SessionTemplateApplyMode] choice
     * before applying.
     */
    val templateApplyDecision: SessionTemplateApplyDecision? = null,
    // ─── Session Assistant ─────────────────────────────────────────────────────
    val assistantReport: com.example.kpkn.domain.sessionassistant.SessionAssistantReport? = null,
    val ghostExerciseCards: List<com.example.kpkn.domain.sessionassistant.GhostExerciseCard> = emptyList(),
    // ─── Multi-session & Navigation ────────────────────────────────────────────
    val snackbarMessage: String? = null,
    val hasActiveLoops: Boolean = false,
)

data class SessionDraftSnapshot(
    val id: String,
    val session: Session,
    val savedAtMs: Long,
    val reason: String,
    val changedFields: List<String>,
    val exerciseCount: Int,
    val setCount: Int,
    val partCount: Int,
)

data class SessionEditorSaveResult(
    val success: Boolean,
    val message: String,
)

class SessionEditorViewModel(
    application: Application,
    private val programId: String,
    private val sessionId: String,
    private val draftWeekId: String?,
    private val draftMacroIndex: Int?,
    private val draftMesoIndex: Int?,
    private val draftDayOfWeek: Int?,
) : AndroidViewModel(application) {

    companion object {
        private const val MAX_LOCAL_DRAFT_SNAPSHOTS = 12

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

    private val repository = ProgramRepository.getInstance()
    private val augeRepository = AugeRepository.getInstance(application)
    private val nutritionRepository = runCatching { NutritionRepository.getInstance() }.getOrNull()
    private val templateRepository = SessionTemplateRepository.getInstance(application)

    /** Combined (system + user) template list, updated reactively. */
    val allTemplates: StateFlow<List<SessionTemplate>> = templateRepository.allTemplates
        .stateIn(viewModelScope, SharingStarted.Lazily, templateRepository.allTemplates.value)
    private val exerciseIndex = run {
        val base = EXERCISE_DATABASE.associateBy { it.id.lowercase() }
        val aliasEntries = EXERCISE_ID_ALIASES.mapNotNull { (alias, canonical) ->
            base[canonical]?.let { alias.lowercase() to it }
        }.toMap()
        base + aliasEntries
    }
    private var augeJob: Job? = null
    private var autoSaveJob: Job? = null

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(2000)
            if (!_uiState.value.autoSaveEnabled) return@launch
            withContext(Dispatchers.IO) {
                persistDraft()
            }
        }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoSaveEnabled = enabled) }
        if (enabled) scheduleAutoSave()
    }

    private data class CachedWeeklyMetrics(
        val sessionIds: Set<String>,
        val metrics: List<SessionAugeComputation>,
    )

    @Volatile
    private var weeklyMetricsCache: CachedWeeklyMetrics? = null

    private val draftPrefs by lazy {
        getApplication<Application>().getSharedPreferences(SESSION_EDITOR_DRAFT_PREFS, Context.MODE_PRIVATE)
    }
    private val draftJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(SessionEditorUiState(programId = programId))
    val uiState: StateFlow<SessionEditorUiState> = _uiState.asStateFlow()

    private fun draftStorageKey(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        sessionId: String,
    ): String {
        val safeWeekId = weekId.ifBlank { "__unspecified_week__" }
        return "program=$programId|week=$safeWeekId|macro=$macroIndex|meso=$mesoIndex|editor=$sessionId"
    }

    private fun persistedDraftFor(
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

    private fun persistDraft(state: SessionEditorUiState = _uiState.value): Boolean {
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
            ruleLimits = state.ruleLimits,
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

    private fun clearPersistedDraft(
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

    fun saveDraftForExit(): Boolean {
        val ok = persistDraft()
        if (ok) {
            _uiState.update { it.copy(hasUnsavedChanges = false) }
        }
        return ok
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
    }

    init {
        observeBodyMeasurements()
        loadSession()
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

    private fun latestBodyMeasurementOrNull(): BodyMeasurementEntry? {
        return nutritionRepository
            ?.bodyMeasurements
            ?.value
            ?.asSequence()
            ?.filter { it.weight != null }
            ?.maxByOrNull { it.date }
    }

    private fun loadSession() {
        val program = repository.getProgramById(programId) ?: return
        val located = locateSession(program, sessionId, draftWeekId, draftMacroIndex, draftMesoIndex)
        val targetWeekId = located?.week?.id ?: draftWeekId.orEmpty()
        val targetMacroIndex = draftMacroIndex ?: located?.macroIndex ?: 0
        val targetMesoIndex = draftMesoIndex ?: located?.mesoIndex ?: 0
        val week = located?.week ?: findWeek(program, targetMacroIndex, targetMesoIndex, targetWeekId)
        val existing = located?.session
        val fallbackDraft = existing ?: createDraftSession(sessionId, draftDayOfWeek)
        val persistedDraft = persistedDraftFor(
            weekId = targetWeekId,
            macroIndex = targetMacroIndex,
            mesoIndex = targetMesoIndex,
            sessionId = fallbackDraft.id,
        )
        val draft = (persistedDraft?.session ?: fallbackDraft).normalizedIdentityFields()
        val resolvedRuleDefaults = persistedDraft?.ruleDefaults ?: SessionEditorRuleDefaults()
        val resolvedRuleLimits = persistedDraft?.ruleLimits ?: SessionEditorRuleLimits()
        val loadedFromDraft = persistedDraft != null
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

        _uiState.value = SessionEditorUiState(
            session = draft,
            originalSession = (existing ?: draft).normalizedIdentityFields(),
            programId = programId,
            draftBundle = SessionDraftBundle(
                sessionId = draft.id,
                weekId = targetWeekId,
                macroIndex = targetMacroIndex,
                mesoIndex = targetMesoIndex,
                dayOfWeek = draft.dayOfWeek ?: draftDayOfWeek,
                siblingSessionIds = week?.sessions.orEmpty().map { it.id },
                weekSessionIds = week?.sessions.orEmpty().map { it.id },
            ),
            weekId = targetWeekId,
            macroIndex = targetMacroIndex,
            mesoIndex = targetMesoIndex,
            dayOfWeek = draft.dayOfWeek ?: draftDayOfWeek,
            isNewSession = existing == null,
            siblingSessions = week?.sessions.orEmpty().sortedBy { it.dayOfWeek ?: 99 },
            weekSessions = week?.sessions.orEmpty(),
            weekStartDay = (program.startDay ?: 1).coerceIn(1, 7),
            roadmapOptions = roadmapOptions,
            cloneDayOptions = cloneDayOptions,
            cloneSourceOptions = cloneSourceOptions,
            selectedSiblingSessionId = draft.id,
            localDraftHistory = listOf(buildDraftSnapshot(session = draft, previous = null, reason = "Inicio")),
            ruleDefaults = resolvedRuleDefaults,
            ruleLimits = resolvedRuleLimits,
            hasUnsavedChanges = loadedFromDraft,
            isSimpleProgram = program.isSimpleTemporalProgram,
            hasActiveLoops = program.loops.isNotEmpty() && program.loopState != null,
            latestBodyMeasurement = latestBodyMeasurementOrNull(),
            allProgramExerciseCandidates = allProgramExerciseCandidates,
        )

        refreshDerivedStateImmediate()
        loadHistory()
    }

    private fun loadHistory() {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            val logs = repository.getLogsForSession(currentSession.id).sortedByDescending { it.date }
            val feedbackByLogId = logs.mapNotNull { log ->
                augeRepository.getFeedbackForLog(log.id)?.let { log.id to it }
            }.toMap()
            _uiState.update { it.copy(workoutLogs = logs, feedbackByLogId = feedbackByLogId) }
        }
    }

    private fun updateSession(transform: (Session) -> Session) {
        val current = _uiState.value.session ?: return
        _uiState.update { state ->
            state.copy(localDraftHistory = appendDraftSnapshot(
                history = state.localDraftHistory,
                snapshot = buildDraftSnapshot(session = current, previous = state.localDraftHistory.lastOrNull()?.session, reason = "Edición"),
            ))
        }
        _uiState.update { state ->
            val updated = transform(current)
            state.copy(
                session = updated,
                dayOfWeek = updated.dayOfWeek ?: state.dayOfWeek,
                hasUnsavedChanges = updated != state.originalSession,
            )
        }
        scheduleAugeRecalc()
        scheduleAutoSave()
    }

    fun updateCurrentSession(transform: (Session) -> Session) = updateSession(transform)

    private fun updateSessionDay(dayOfWeek: Int) = updateSession { session ->
        session.copy(dayOfWeek = dayOfWeek)
    }

    private fun scheduleAugeRecalc() {
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

    private fun refreshDerivedStateImmediate() {
        augeJob?.cancel()
        augeJob = null
        val state = _uiState.value
        val session = state.session ?: return
        recalcAndPushAuge(state, session)
    }

    private fun recalcAndPushAuge(state: SessionEditorUiState, session: Session) {
        val exercises = session.allExercises()
        val totalSets = exercises.sumOf { it.sets.size.coerceAtLeast(1) }
        val averageRest = exercises.mapNotNull { it.restTime }.ifEmpty { listOf(90) }.average().toInt()
        val draftAwareWeekSessions = if (state.weekSessions.any { it.id == session.id }) {
            state.weekSessions.map { if (it.id == session.id) session else it }
        } else {
            state.weekSessions + session
        }
        val sessionEnergy = runCatching {
            TrainingEnergyEngine.estimatePlannedSession(session, repository.settings.value)
        }.getOrElse { SessionEnergySummary() }
        val summary = runCatching {
            buildAugeSummary(
                currentSession = session,
                weekSessions = draftAwareWeekSessions,
                exerciseIndex = exerciseIndex,
                settings = repository.settings.value,
                programLogs = repository.getLogsForProgram(state.programId),
                program = repository.getProgramById(state.programId),
                programId = state.programId,
                mesoIndex = state.mesoIndex,
            )
        }.getOrElse {
            SessionEditorAugeSummary(
                sessionDrain = PredictedDrain(0, 0, 0),
                weeklyDrain = PredictedDrain(0, 0, 0),
                sessionSetCount = totalSets,
                sessionDurationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
                sessionEnergy = sessionEnergy,
            )
        }
        val assistantReport = runCatching {
            val templates = allTemplates.value
            SessionAssistantEngine.evaluate(
                input = SessionAssistantInput(
                    allExercisesInSession = session.allExercises(),
                    weekSessions = draftAwareWeekSessions,
                    currentSessionId = session.id,
                    program = repository.getProgramById(state.programId),
                    settings = repository.settings.value,
                    workoutLogs = repository.getLogsForProgram(state.programId),
                    exerciseIndex = exerciseIndex,
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
                ),
                allTemplates = templates,
            )
        }.getOrNull()
        _uiState.update {
            it.copy(
                estimatedDurationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
                predictedDrain = summary.sessionDrain,
                augeSummary = summary.copy(sessionEnergy = sessionEnergy),
                assistantReport = assistantReport,
                ghostExerciseCards = assistantReport?.tarjetasFantasma ?: emptyList(),
            )
        }
    }

    fun updateSessionName(name: String) = updateSession { it.copy(name = name) }
    fun updateSessionDescription(description: String) = updateSession { it.copy(description = description) }
    fun updateSessionMeetDay(isMeetDay: Boolean) = updateSession {
        if (isMeetDay) {
            it.copy(
                isMeetDay = true,
                isCompetitionSession = true,
                trainingBackup = TrainingBackup(
                    exercises = it.exercises,
                    parts = it.parts,
                    warmup = it.warmup,
                    savedAtMs = System.currentTimeMillis(),
                ),
                exercises = emptyList(),
                parts = emptyList(),
                warmup = emptyList(),
            )
        } else {
            val backup = it.trainingBackup
            if (backup != null) {
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
    fun updateSessionMeetBodyweight(bodyweight: Double?) = updateSession { it.copy(meetBodyweight = bodyweight) }

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
                quickActionsPartId = if (sheet == SessionEditorSheet.QUICK_ACTIONS) state.quickActionsPartId else null,
                quickActionsExerciseId = if (sheet == SessionEditorSheet.QUICK_ACTIONS) state.quickActionsExerciseId else null,
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
                templateSearchQuery = "",
                templateApplyDecision = null,
            )
        }
    }

    // ─── Session Templates ────────────────────────────────────────────────────

    /** Opens the template browser sheet. */
    fun openTemplates() {
        openSheet(SessionEditorSheet.TEMPLATES)
    }

    /** Updates the free-text filter in the template browser. */
    fun setTemplateSearchQuery(query: String) {
        _uiState.update { it.copy(templateSearchQuery = query) }
    }

    /**
     * Called when the user taps a template card.
     *
     * - If the current session is empty, applies the template immediately.
     * - Otherwise, stores an [SessionTemplateApplyDecision] asking the user to
     *   choose between [SessionTemplateApplyMode.REPLACE] and [SessionTemplateApplyMode.APPEND].
     */
    fun selectTemplate(template: SessionTemplate) {
        val session = _uiState.value.session ?: return
        if (SessionTemplateEngine.sessionHasContent(session)) {
            _uiState.update {
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
    fun confirmTemplateApply(mode: SessionTemplateApplyMode) {
        val decision = _uiState.value.templateApplyDecision ?: return
        applyTemplateInternal(decision.template, mode)
    }

    /** Cancels a pending template apply decision. */
    fun cancelTemplateApply() {
        _uiState.update { it.copy(templateApplyDecision = null) }
    }

    /**
     * Saves the current session as a new user template with the given metadata.
     * Returns `true` on success, `false` if there is no session to save.
     */
    fun saveCurrentSessionAsTemplate(
        name: String,
        description: String,
        tags: List<SessionTemplateTag>,
    ): Boolean {
        val session = _uiState.value.session ?: return false
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

    private fun applyTemplateInternal(template: SessionTemplate, mode: SessionTemplateApplyMode) {
        val session = _uiState.value.session ?: return
        val result = SessionTemplateEngine.applyTemplate(template, session, mode)
        updateSession { result }
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.NONE,
                templateApplyDecision = null,
                templateSearchQuery = "",
            )
        }
    }

    fun openSupersetManager(partId: String?, supersetId: String) {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.SUPERSERIE_MANAGER,
                supersetManagerPartId = partId,
                supersetManagerSupersetId = supersetId,
            )
        }
    }
    fun togglePartCollapsed(partId: String) {
        _uiState.update { state ->
            val collapsed = state.collapsedPartIds.toMutableSet()
            if (!collapsed.add(partId)) collapsed.remove(partId)
            state.copy(collapsedPartIds = collapsed)
        }
    }

    fun addPart() {
        val currentParts = _uiState.value.session?.parts.orEmpty()
        val nextColor = PART_COLORS[currentParts.size % PART_COLORS.size]
        updateSession {
            it.copy(parts = it.parts + SessionPart(UUID.randomUUID().toString(), "Grupo ${it.parts.size + 1}", color = nextColor))
        }
    }

    fun removePart(partId: String, keepExercises: Boolean) = updateSession { session ->
        val removedPart = session.parts.firstOrNull { it.id == partId } ?: return@updateSession session
        val remaining = session.parts.filterNot { it.id == partId }

        session.copy(
            parts = remaining,
            exercises = if (keepExercises) session.exercises + removedPart.exercises else session.exercises,
        )
    }

    fun updatePartName(partId: String, name: String) = updateSession { session ->
        session.copy(parts = session.parts.map { if (it.id == partId) it.copy(name = name) else it })
    }

    fun updatePartColor(partId: String, color: String) = updateSession { session ->
        session.copy(parts = session.parts.map { if (it.id == partId) it.copy(color = color) else it })
    }

    fun movePart(partId: String, direction: Int) = updateSession { session ->
        session.copy(parts = moveItem(session.parts, partId, direction) { it.id })
    }

    fun movePartToIndex(partId: String, targetIndex: Int) = updateSession { session ->
        val currentIndex = session.parts.indexOfFirst { it.id == partId }
        if (currentIndex == -1) return@updateSession session
        val safeTarget = targetIndex.coerceIn(0, session.parts.lastIndex)
        if (currentIndex == safeTarget) return@updateSession session

        val mutable = session.parts.toMutableList()
        val moved = mutable.removeAt(currentIndex)
        mutable.add(safeTarget, moved)
        session.copy(parts = mutable.toList())
    }

    fun openPicker(partId: String?, exerciseId: String? = null, searchQuery: String = "") {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.EXERCISE_PICKER,
                pickerTargetPartId = partId,
                pickerTargetExerciseId = exerciseId,
                searchQuery = searchQuery,
                quickActionsPartId = null,
                quickActionsExerciseId = null,
            )
        }
    }

    fun openRelationshipPicker(partId: String?, exerciseId: String) {
        val current = _uiState.value.session ?: return
        val target = if (partId == null) {
            current.exercises.firstOrNull { it.id == exerciseId }
        } else {
            current.parts.firstOrNull { it.id == partId }?.exercises?.firstOrNull { it.id == exerciseId }
        } ?: return

        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.RELATIONSHIP_PICKER,
                pickerTargetPartId = partId,
                pickerTargetExerciseId = exerciseId,
                searchQuery = "",
                quickActionsPartId = null,
                quickActionsExerciseId = null,
            )
        }
    }

    /** Abre el picker para añadir ejercicios sueltos (sin grupo). */
    fun openPickerForUncategorized() {
        openPicker(partId = null)
    }

    fun setSearchQuery(query: String) { _uiState.update { it.copy(searchQuery = query) } }

    fun linkExerciseRelativeTo(partId: String?, exerciseId: String, anchorExerciseId: String?) {
        val state = _uiState.value
        val session = state.session ?: return
        val anchor = if (anchorExerciseId == null) {
            null
        } else {
            // Search in current session first, then in all program candidates
            session.allExercises().firstOrNull { it.id == anchorExerciseId }
                ?: state.allProgramExerciseCandidates.firstOrNull { it.exerciseId == anchorExerciseId }?.let { candidate ->
                    // Build a minimal Exercise from candidate for resolvedCanonicalExerciseId()
                    Exercise(
                        id = candidate.exerciseId,
                        name = candidate.exerciseName,
                        exerciseDbId = candidate.exerciseDbId,
                    )
                }
        }
        updateExercise(partId, exerciseId) { current ->
            if (anchor == null) {
                current.copy(
                    relativeToCanonicalExerciseId = null,
                    relationshipType = null,
                    relationshipNotes = null,
                ).normalizedIdentityFields()
            } else {
                current.copy(
                    relativeToCanonicalExerciseId = anchor.resolvedCanonicalExerciseId(),
                    relationshipType = current.relationshipType ?: ExerciseRelationshipType.VARIATION,
                    relationshipNotes = current.relationshipNotes ?: "Relativo a ${anchor.name}",
                ).normalizedIdentityFields()
            }
        }
        closeSheet()
    }

    fun updateExerciseRelationshipType(
        partId: String?,
        exerciseId: String,
        relationshipType: ExerciseRelationshipType?,
    ) = updateExercise(partId, exerciseId) { exercise ->
        exercise.copy(relationshipType = relationshipType).normalizedIdentityFields()
    }

    fun updateExerciseRelationshipNotes(
        partId: String?,
        exerciseId: String,
        notes: String?,
    ) = updateExercise(partId, exerciseId) { exercise ->
        exercise.copy(relationshipNotes = notes).normalizedIdentityFields()
    }

    fun addExerciseToPart(partId: String?, info: ExerciseMuscleInfo): String {
        val currentSession = _uiState.value.session
        val newExercise = createExerciseFromInfo(info).let { base ->
            if (currentSession?.isMeetDay == true) base.asCompetitionMovement() else base
        }
        updateSession { session ->
            if (partId == null) {
                session.copy(exercises = session.exercises + newExercise)
            } else {
                session.copy(parts = session.parts.map { if (it.id == partId) it.copy(exercises = it.exercises + newExercise) else it })
            }
        }
        closeSheet()
        return newExercise.id
    }

    fun addExercisesToPart(partId: String?, infos: List<ExerciseMuscleInfo>): List<String> {
        val currentSession = _uiState.value.session
        val newExercises = infos.map { info ->
            createExerciseFromInfo(info).let { base ->
                if (currentSession?.isMeetDay == true) base.asCompetitionMovement() else base
            }
        }
        updateSession { session ->
            if (partId == null) {
                session.copy(exercises = session.exercises + newExercises)
            } else {
                session.copy(parts = session.parts.map { part ->
                    if (part.id == partId) part.copy(exercises = part.exercises + newExercises)
                    else part
                })
            }
        }
        closeSheet()
        return newExercises.map { it.id }
    }

    fun addBlankExerciseToPart(partId: String?): String {
        val currentSession = _uiState.value.session
        val newExercise = createBlankExercise().let { base ->
            if (currentSession?.isMeetDay == true) base.asCompetitionMovement() else base
        }
        updateSession { session ->
            if (partId == null) {
                session.copy(exercises = session.exercises + newExercise)
            } else {
                session.copy(parts = session.parts.map { if (it.id == partId) it.copy(exercises = it.exercises + newExercise) else it })
            }
        }
        return newExercise.id
    }

    fun addCompetitionMovement(name: String): String {
        val movementName = name.ifBlank { "Movimiento competición" }
        val newExercise = createBlankExercise().copy(name = movementName).asCompetitionMovement()
        updateSession { session ->
            if (!session.isMeetDay) return@updateSession session
            session.copy(exercises = session.exercises + newExercise)
        }
        return newExercise.id
    }

    fun replaceExerciseInPart(partId: String?, exerciseId: String, info: ExerciseMuscleInfo) {
        updateExercise(partId, exerciseId) { current ->
            current.replacedWithCatalogExercise(info)
        }
        closeSheet()
    }

    fun removeExercise(partId: String?, exerciseId: String) = updateSession { session ->
        if (partId == null) {
            session.copy(exercises = session.exercises.filterNot { ex -> ex.id == exerciseId })
        } else {
            session.copy(parts = session.parts.map { if (it.id == partId) it.copy(exercises = it.exercises.filterNot { ex -> ex.id == exerciseId }) else it })
        }
    }

    fun moveExercise(partId: String?, exerciseId: String, direction: Int) = updateSession { session ->
        if (partId == null) {
            session.copy(exercises = moveItem(session.exercises, exerciseId, direction) { it.id })
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = moveItem(part.exercises, exerciseId, direction) { it.id }) else part
            })
        }
    }

    fun moveExerciseToPart(
        sourcePartId: String?,
        exerciseId: String,
        targetPartId: String?,
        targetIndex: Int? = null,
    ) = updateSession { session ->
        if (sourcePartId == targetPartId && targetIndex == null) return@updateSession session

        var movedExercise: Exercise? = null
        val strippedSession = if (sourcePartId == null) {
            val remainingExercises = session.exercises.filterNot { exercise ->
                val shouldMove = exercise.id == exerciseId
                if (shouldMove) movedExercise = exercise
                shouldMove
            }
            session.copy(exercises = remainingExercises)
        } else {
            val strippedParts = session.parts.map { part ->
                if (part.id != sourcePartId) part
                else part.copy(
                    exercises = part.exercises.filterNot { exercise ->
                        val shouldMove = exercise.id == exerciseId
                        if (shouldMove) movedExercise = exercise
                        shouldMove
                    }
                )
            }
            session.copy(parts = strippedParts)
        }
        val dragged = movedExercise ?: return@updateSession session

        if (targetPartId == null) {
            val mutable = strippedSession.exercises.toMutableList()
            val safeIndex = (targetIndex ?: mutable.size).coerceIn(0, mutable.size)
            mutable.add(safeIndex, dragged)
            strippedSession.copy(exercises = mutable.toList())
        } else {
            strippedSession.copy(
                parts = strippedSession.parts.map { part ->
                    if (part.id != targetPartId) part
                    else {
                        val mutable = part.exercises.toMutableList()
                        val safeIndex = (targetIndex ?: mutable.size).coerceIn(0, mutable.size)
                        mutable.add(safeIndex, dragged)
                        part.copy(exercises = mutable.toList())
                    }
                }
            )
        }
    }

    fun updateExercise(partId: String?, exerciseId: String, transform: (Exercise) -> Exercise) = updateSession { session ->
        if (partId == null) {
            session.copy(exercises = session.exercises.map { ex -> if (ex.id == exerciseId) transform(ex).normalizeExercise() else ex })
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id != partId) part else part.copy(exercises = part.exercises.map { ex -> if (ex.id == exerciseId) transform(ex).normalizeExercise() else ex })
            })
        }
    }

    fun addSet(partId: String?, exerciseId: String) = updateExercise(partId, exerciseId) { exercise ->
        val template = exercise.sets.lastOrNull()
        val nextSet = template?.let { createNextSetTemplate(exercise, it) } ?: ExerciseSet(
            id = UUID.randomUUID().toString(),
            targetReps = 8,
            targetRPE = 8.0,
            intensityMode = IntensityMode.RPE,
        )
        exercise.copy(sets = exercise.sets + nextSet)
    }

    fun removeSet(partId: String?, exerciseId: String, setId: String) = updateExercise(partId, exerciseId) { exercise ->
        exercise.copy(sets = exercise.sets.filterNot { it.id == setId })
    }

    fun moveSet(partId: String?, exerciseId: String, setId: String, direction: Int) = updateExercise(partId, exerciseId) { exercise ->
        exercise.copy(sets = moveItem(exercise.sets, setId, direction) { it.id })
    }

    fun updateSet(partId: String?, exerciseId: String, setId: String, transform: (ExerciseSet) -> ExerciseSet) =
        updateExercise(partId, exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.id == setId) transform(it).normalizeSet(exercise) else it })
        }

    fun openWarmup(exerciseId: String) {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.WARMUP,
                warmupExerciseId = exerciseId,
                quickActionsPartId = null,
                quickActionsExerciseId = null,
            )
        }
    }

    fun openExerciseQuickActions(partId: String?, exerciseId: String) {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.QUICK_ACTIONS,
                quickActionsPartId = partId,
                quickActionsExerciseId = exerciseId,
                pickerTargetPartId = null,
                pickerTargetExerciseId = null,
                warmupExerciseId = null,
                searchQuery = "",
            )
        }
    }

    fun triggerQuickActionOpenPicker() {
        val state = _uiState.value
        val exerciseId = state.quickActionsExerciseId ?: return
        val contextualQuery = state.session
            ?.let { session ->
                if (state.quickActionsPartId == null) {
                    session.exercises.firstOrNull { it.id == exerciseId }
                } else {
                    session.parts
                        .firstOrNull { it.id == state.quickActionsPartId }
                        ?.exercises
                        ?.firstOrNull { it.id == exerciseId }
                }
            }
            ?.name
            .orEmpty()

        openPicker(
            partId = state.quickActionsPartId,
            exerciseId = exerciseId,
            searchQuery = contextualQuery,
        )
    }

    fun triggerQuickActionOpenWarmup() {
        val exerciseId = _uiState.value.quickActionsExerciseId ?: return
        openWarmup(exerciseId)
    }

    fun triggerQuickActionDelete() {
        val state = _uiState.value
        val exerciseId = state.quickActionsExerciseId ?: return
        removeExercise(state.quickActionsPartId, exerciseId)
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.NONE,
                quickActionsPartId = null,
                quickActionsExerciseId = null,
            )
        }
    }

    fun triggerQuickActionLinkSuperset() {
        val state = _uiState.value
        val exerciseId = state.quickActionsExerciseId ?: return
        linkExerciseWithNext(state.quickActionsPartId, exerciseId)
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.NONE,
                quickActionsPartId = null,
                quickActionsExerciseId = null,
            )
        }
    }

    fun triggerQuickActionUnlinkSuperset() {
        val state = _uiState.value
        val exerciseId = state.quickActionsExerciseId ?: return
        unlinkExerciseFromSuperset(state.quickActionsPartId, exerciseId)
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.NONE,
                quickActionsPartId = null,
                quickActionsExerciseId = null,
            )
        }
    }
    fun updateWarmupSets(partId: String?, exerciseId: String, sets: List<WarmupSetDefinition>) = updateExercise(partId, exerciseId) { it.copy(warmupSets = sets) }

    fun applyRuleDefaultsToSession(partId: String? = null) {
        val defaults = _uiState.value.ruleDefaults
        updateSession { session ->
            SessionEditorRulesEngine.applyDefaults(
                session = session,
                defaults = defaults,
                partId = partId,
            )
        }
        closeSheet()
    }

    fun updateRuleDefaults(setCount: Int? = null, reps: Int? = null, rpe: Double? = null) {
        _uiState.update { state ->
            state.copy(
                ruleDefaults = state.ruleDefaults.copy(
                    setCount = setCount ?: state.ruleDefaults.setCount,
                    reps = reps ?: state.ruleDefaults.reps,
                    rpe = rpe ?: state.ruleDefaults.rpe,
                )
            )
        }
    }

    fun updateRuleLimits(maxRPE: Double?, maxExercisesPerMuscle: Int?) {
        _uiState.update { state ->
            state.copy(
                ruleLimits = SessionEditorRulesEngine.normalizeRuleLimits(
                    existing = state.ruleLimits,
                    maxRPE = maxRPE,
                    maxExercisesPerMuscle = maxExercisesPerMuscle,
                )
            )
        }
    }

    fun updateAdvancedRuleLimits(
        maxVolumePerMuscleSession: Double?,
        maxVolumePerMuscleWeekly: Double?,
        maxSamePatternPerSession: Int?,
        rigidLimits: Boolean,
    ) {
        _uiState.update { state ->
            state.copy(
                ruleLimits = SessionEditorRulesEngine.normalizeAdvancedRuleLimits(
                    existing = state.ruleLimits,
                    maxVolumePerMuscleSession = maxVolumePerMuscleSession,
                    maxVolumePerMuscleWeekly = maxVolumePerMuscleWeekly,
                    maxSamePatternPerSession = maxSamePatternPerSession,
                    rigidLimits = rigidLimits,
                )
            )
        }
    }

    fun applyGlobalIntensityAdjustment(
        targetMode: IntensityMode,
        value: Double,
        targetMuscles: Set<String>?,
    ) {
        updateSession { session ->
            SessionEditorRulesEngine.applyGlobalIntensityAdjustment(
                session = session,
                targetMode = targetMode,
                value = value,
                targetMuscles = targetMuscles,
                exerciseIndex = exerciseIndex,
            )
        }
    }

    fun linkExerciseWithNext(partId: String?, exerciseId: String) = updateSession { session ->
        val sourceExercises = if (partId == null) session.exercises else session.parts.firstOrNull { it.id == partId }?.exercises
            ?: return@updateSession session
        val currentIndex = sourceExercises.indexOfFirst { it.id == exerciseId }
        if (currentIndex < 0 || currentIndex >= sourceExercises.lastIndex) return@updateSession session

        val mutableExercises = sourceExercises.toMutableList()
        val current = mutableExercises[currentIndex]
        val next = mutableExercises[currentIndex + 1]
        val resolvedSupersetId = current.supersetId ?: next.supersetId ?: UUID.randomUUID().toString()

        mutableExercises[currentIndex] = current.copy(supersetId = resolvedSupersetId)
        mutableExercises[currentIndex + 1] = next.copy(supersetId = resolvedSupersetId)

        if (partId == null) {
            session.copy(exercises = mutableExercises)
        } else {
            session.copy(
                parts = session.parts.map { part ->
                    if (part.id == partId) part.copy(exercises = mutableExercises) else part
                }
            )
        }
    }

    fun unlinkExerciseFromSuperset(partId: String?, exerciseId: String) = updateExercise(partId, exerciseId) {
        it.copy(supersetId = null, supersetRestBetween = null, supersetRestAfter = null)
    }

    fun linkExercisesAsSuperset(partId: String?, exerciseIds: List<String>) = updateSession { session ->
        val sourceExercises = if (partId == null) session.exercises
        else session.parts.firstOrNull { it.id == partId }?.exercises ?: return@updateSession session
        val supersetId = UUID.randomUUID().toString()
        val updated = sourceExercises.map { ex ->
            if (ex.id in exerciseIds) ex.copy(supersetId = supersetId) else ex
        }
        if (partId == null) {
            session.copy(exercises = updated)
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = updated) else part
            })
        }
    }

    fun updateSupersetRestBetween(partId: String?, supersetId: String, restSeconds: Int) = updateSession { session ->
        val updater: (List<Exercise>) -> List<Exercise> = { exercises ->
            exercises.map { ex ->
                if (ex.supersetId == supersetId) ex.copy(supersetRestBetween = restSeconds) else ex
            }
        }
        if (partId == null) {
            session.copy(exercises = updater(session.exercises))
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = updater(part.exercises)) else part
            })
        }
    }

    fun updateSupersetRestAfter(partId: String?, supersetId: String, restSeconds: Int) = updateSession { session ->
        val updater: (List<Exercise>) -> List<Exercise> = { exercises ->
            exercises.map { ex ->
                if (ex.supersetId == supersetId) ex.copy(supersetRestAfter = restSeconds) else ex
            }
        }
        if (partId == null) {
            session.copy(exercises = updater(session.exercises))
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = updater(part.exercises)) else part
            })
        }
    }

    fun removeFromSuperset(partId: String?, exerciseId: String) = updateExercise(partId, exerciseId) {
        it.copy(supersetId = null, supersetRestBetween = null, supersetRestAfter = null)
    }

    fun moveExerciseFreely(fromPartId: String?, fromIndex: Int, toPartId: String?, toIndex: Int) = updateSession { session ->
        val sourceExercises = if (fromPartId == null) session.exercises
        else session.parts.firstOrNull { it.id == fromPartId }?.exercises ?: return@updateSession session
        if (fromIndex !in sourceExercises.indices) return@updateSession session

        val exercise = sourceExercises[fromIndex]
        val sourceWithout = sourceExercises.toMutableList().also { it.removeAt(fromIndex) }

        var updatedSession = if (fromPartId == null) {
            session.copy(exercises = sourceWithout)
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == fromPartId) part.copy(exercises = sourceWithout) else part
            })
        }

        val targetExercises = if (toPartId == null) updatedSession.exercises
        else updatedSession.parts.firstOrNull { it.id == toPartId }?.exercises ?: return@updateSession session
        val insertIndex = toIndex.coerceIn(0, targetExercises.size)
        val targetWith = targetExercises.toMutableList().also { it.add(insertIndex, exercise) }

        updatedSession = if (toPartId == null) {
            updatedSession.copy(exercises = targetWith)
        } else {
            updatedSession.copy(parts = updatedSession.parts.map { part ->
                if (part.id == toPartId) part.copy(exercises = targetWith) else part
            })
        }
        updatedSession
    }

    fun restoreDraftSnapshot(snapshot: SessionDraftSnapshot) {
        _uiState.update { state ->
            val current = state.session
            val history = if (current != null) {
                appendDraftSnapshot(
                    history = state.localDraftHistory,
                    snapshot = buildDraftSnapshot(
                        session = current,
                        previous = state.localDraftHistory.lastOrNull()?.session,
                        reason = "Antes de restaurar",
                    ),
                )
            } else {
                state.localDraftHistory
            }
            val restoredSession = snapshot.session
            state.copy(
                session = restoredSession,
                localDraftHistory = history,
                hasUnsavedChanges = restoredSession != state.originalSession,
            )
        }
        scheduleAugeRecalc()
    }

    fun updateBackgroundValue(value: String, type: SessionBackgroundType) = updateSession { session ->
        session.copy(
            background = (session.background ?: SessionBackground(type = type, value = value)).copy(
                type = type,
                value = value,
                style = session.background?.style ?: SessionBackgroundStyle(blur = 0f, brightness = 0.92f),
            )
        )
    }

    fun updateBackgroundStyle(blur: Float? = null, brightness: Float? = null) = updateSession { session ->
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

    fun updateLabelPosition(position: LabelPosition) = updateSession { session ->
        session.copy(coverStyle = (session.coverStyle ?: CoverStyle(filters = CoverFilters())).copy(labelPosition = position))
    }

    fun updateFilterBrightness(brightness: Float) = updateSession { session ->
        val style = session.coverStyle ?: CoverStyle(filters = CoverFilters())
        session.copy(coverStyle = style.copy(filters = (style.filters ?: CoverFilters()).copy(brightness = brightness)))
    }

    fun updateCoverFilters(
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

    fun updateCoverMotion(enabled: Boolean) = updateSession { session ->
        val style = session.coverStyle ?: CoverStyle(filters = CoverFilters())
        session.copy(coverStyle = style.copy(enableMotion = enabled))
    }

    @Deprecated("Use cloneCurrentSessionToTargets")
    fun exportToSession(targetSessionId: String) {
        cloneCurrentSessionToTargets(
            targetKeys = _uiState.value.cloneDayOptions
                .filter { it.existingSessionId == targetSessionId }
                .map { it.key }
                .toSet(),
            selectedExerciseIds = null,
            applyMode = SessionCloneApplyMode.APPEND,
        )
    }

    @Deprecated("Use importFromSourceSession")
    fun importFromSession(sourceSessionId: String) {
        importFromSourceSession(
            sourceSessionId = sourceSessionId,
            selectedExerciseIds = null,
            applyMode = SessionCloneApplyMode.APPEND,
        )
    }

    fun cloneCurrentSessionToTargets(
        targetKeys: Set<String>,
        selectedExerciseIds: Set<String>?,
        applyMode: SessionCloneApplyMode,
    ): SessionEditorSaveResult {
        if (targetKeys.isEmpty()) {
            return SessionEditorSaveResult(false, "Selecciona al menos un día destino.")
        }
        if (selectedExerciseIds != null && selectedExerciseIds.isEmpty()) {
            return SessionEditorSaveResult(false, "Selecciona al menos un ejercicio para clonación parcial.")
        }
        val state = _uiState.value
        val source = state.session ?: return SessionEditorSaveResult(false, "No hay sesión origen activa.")
        val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(false, "No pudimos encontrar el programa.")
        val targets = state.cloneDayOptions.filter { it.key in targetKeys }
        if (targets.isEmpty()) return SessionEditorSaveResult(false, "No se encontraron destinos válidos.")

        val updatedProgram = targets.fold(program) { acc, target ->
            applyCloneToTarget(
                program = acc,
                source = source,
                target = target,
                selectedExerciseIds = selectedExerciseIds,
                applyMode = applyMode,
            )
        }
        repository.updateProgram(updatedProgram)
        closeSheet()
        loadSession()

        val modeLabel = if (selectedExerciseIds.isNullOrEmpty()) "completa" else "parcial"
        return SessionEditorSaveResult(
            success = true,
            message = "Clonación $modeLabel aplicada en ${targets.size} día${if (targets.size > 1) "s" else ""}.",
        )
    }

    fun importFromSourceSession(
        sourceSessionId: String,
        selectedExerciseIds: Set<String>?,
        applyMode: SessionCloneApplyMode,
    ): SessionEditorSaveResult {
        if (selectedExerciseIds != null && selectedExerciseIds.isEmpty()) {
            return SessionEditorSaveResult(false, "Selecciona al menos un ejercicio para importación parcial.")
        }
        val state = _uiState.value
        val sourceOption = state.cloneSourceOptions.firstOrNull { it.sessionId == sourceSessionId }
            ?: return SessionEditorSaveResult(false, "No se encontró la sesión origen.")
        val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(false, "No pudimos encontrar el programa.")
        val sourceSession = program.findSessionInProgram(
            macroIndex = sourceOption.macroIndex,
            mesoIndex = sourceOption.mesoIndex,
            weekId = sourceOption.weekId,
            sessionId = sourceOption.sessionId,
        ) ?: return SessionEditorSaveResult(false, "No se pudo leer la sesión origen.")

        updateSession { current ->
            mergeSessions(
                base = current,
                incoming = sourceSession,
                selectedExerciseIds = selectedExerciseIds,
                applyMode = applyMode,
            )
        }
        closeSheet()
        val modeLabel = if (selectedExerciseIds.isNullOrEmpty()) "completa" else "parcial"
        return SessionEditorSaveResult(success = true, message = "Importación $modeLabel aplicada desde ${sourceOption.sessionName}.")
    }

    private fun applyCloneToTarget(
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

    fun applyAugeCorrection(
        alertId: String,
        overrideType: SessionEditorAugeCorrectionType? = null,
    ) {
        val alert = (_uiState.value.augeSummary.alerts + _uiState.value.augeSummary.suggestions)
            .firstOrNull { it.id == alertId } ?: return

        val correctionType = overrideType ?: alert.correctionType

        when (correctionType) {
            SessionEditorAugeCorrectionType.REDUCE_SERIES -> reduceSeriesForAugeAlert(alert)
            SessionEditorAugeCorrectionType.REDUCE_RPE -> reduceRpeForAugeAlert(alert)
            SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE -> reduceVolumeAndRpeForAugeAlert(alert)
            SessionEditorAugeCorrectionType.ADD_SERIES -> addSeriesForAugeAlert(alert)
            null -> Unit
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun setMainSessionForDay(sessionId: String) {
        val state = _uiState.value
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
        _uiState.update { it.copy(snackbarMessage = "Sesión principal actualizada") }
        switchToSession(sessionId)
    }

    fun requestSessionSwitch(
        targetSessionId: String,
        targetWeekId: String? = null,
        targetMacroIndex: Int? = null,
        targetMesoIndex: Int? = null,
    ) {
        val state = _uiState.value
        if (state.session?.id == targetSessionId) return
        if (state.hasUnsavedChanges) {
            val saveResult = saveSession(skipRefresh = true)
            if (!saveResult.success) {
                _uiState.update { it.copy(snackbarMessage = "Error al guardar: ${saveResult.message}") }
                return
            }
            _uiState.update { it.copy(snackbarMessage = "Sesión guardada con éxito") }
        }
        switchToSession(targetSessionId, targetWeekId, targetMacroIndex, targetMesoIndex)
    }

    fun selectRoadmapDay(dayOfWeek: Int): SessionEditorSaveResult {
        val state = _uiState.value
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

    fun createSessionForDay(dayOfWeek: Int): SessionEditorSaveResult {
        val state = _uiState.value
        if (state.hasUnsavedChanges) {
            val saveResult = saveSession()
            if (!saveResult.success) {
                _uiState.update { it.copy(snackbarMessage = "Error al guardar: ${saveResult.message}") }
                return SessionEditorSaveResult(success = false, message = "")
            }
        }

        val existingOnDay = state.weekSessions.firstOrNull { it.dayOfWeek == dayOfWeek }
        if (existingOnDay != null) {
            requestSessionSwitch(existingOnDay.id)
            return SessionEditorSaveResult(success = true, message = "")
        }

        val newSession = createDraftSession(UUID.randomUUID().toString(), dayOfWeek).copy(
            name = "Sesión ${dayLabelShort(dayOfWeek)}",
            isMainSession = true,
        )

        _uiState.update {
            it.copy(
                session = newSession,
                originalSession = newSession,
                dayOfWeek = dayOfWeek,
                isNewSession = true,
                selectedSiblingSessionId = newSession.id,
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

    fun discardAndSwitchPendingSession() {
        val state = _uiState.value
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
        _uiState.update {
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

    fun selectRoadmapOption(option: SessionRoadmapOption) {
        val program = repository.getProgramById(programId) ?: return
        val week = findWeek(program, option.macroIndex, option.mesoIndex, option.weekId) ?: return
        val preferredDay = _uiState.value.dayOfWeek
        val targetSession = week.sessions.firstOrNull { it.dayOfWeek == preferredDay } ?: week.sessions.firstOrNull() ?: return
        // Use requestSessionSwitch so unsaved changes trigger the save guard instead of
        // silently discarding them when the user changes weeks via the roadmap menu.
        requestSessionSwitch(
            targetSessionId = targetSession.id,
            targetWeekId = option.weekId,
            targetMacroIndex = option.macroIndex,
            targetMesoIndex = option.mesoIndex,
        )
    }

    private fun switchToSession(
        targetSessionId: String,
        targetWeekId: String? = _uiState.value.weekId,
        targetMacroIndex: Int? = _uiState.value.macroIndex,
        targetMesoIndex: Int? = _uiState.value.mesoIndex,
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
        val resolvedSession = persistedDraft?.session ?: located.session
        val roadmapOptions = buildRoadmapOptions(program)
        val cloneDayOptions = buildCloneDayOptions(program, currentSessionId = resolvedSession.id)
        val cloneSourceOptions = buildCloneSourceOptions(program, currentSessionId = resolvedSession.id)
        _uiState.update {
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
                    siblingSessionIds = weekSessions.map(Session::id),
                    weekSessionIds = weekSessions.map(Session::id),
                ),
                dayOfWeek = resolvedSession.dayOfWeek,
                siblingSessions = weekSessions.sortedBy { session -> session.dayOfWeek ?: 99 },
                weekSessions = weekSessions,
                roadmapOptions = roadmapOptions,
                cloneDayOptions = cloneDayOptions,
                cloneSourceOptions = cloneSourceOptions,
                selectedSiblingSessionId = resolvedSession.id,
                hasUnsavedChanges = persistedDraft != null,
                pendingSessionSwitchId = null,
                sheet = SessionEditorSheet.NONE,
                localDraftHistory = listOf(buildDraftSnapshot(session = resolvedSession, previous = null, reason = "Cambio de sesión")),
                ruleDefaults = persistedDraft?.ruleDefaults ?: it.ruleDefaults,
                ruleLimits = persistedDraft?.ruleLimits ?: it.ruleLimits,
            )
        }
        refreshDerivedStateImmediate()
        loadHistory()
    }

    fun saveSession(scope: SessionSaveScope = SessionSaveScope.SESSION_ONLY, skipRefresh: Boolean = false): SessionEditorSaveResult {
        val state = _uiState.value
        val rawDraft = state.session ?: return SessionEditorSaveResult(false, "No hay una sesión activa para guardar.")
        val draft = rawDraft.normalizeSession()
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
        clearPersistedDraft(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = draft.id,
        )
        _uiState.update {
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

    private fun appendDraftSnapshot(
        history: List<SessionDraftSnapshot>,
        snapshot: SessionDraftSnapshot,
    ): List<SessionDraftSnapshot> {
        val last = history.lastOrNull()
        if (last != null && last.session == snapshot.session) return history
        return (history + snapshot).takeLast(MAX_LOCAL_DRAFT_SNAPSHOTS)
    }

    private fun buildDraftSnapshot(
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

    private fun detectChangedFields(previous: Session, current: Session): List<String> {
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

    private fun applySessionToMesocycle(program: Program, state: SessionEditorUiState, draft: Session): Program {
        return program.copy(
            macrocycles = program.macrocycles.mapIndexed { macroIndex, macro ->
                if (macroIndex != state.macroIndex) return@mapIndexed macro
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.mapIndexed { mesoIndex, meso ->
                        if (mesoIndex != state.mesoIndex) return@mapIndexed meso
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

    private fun normalizeMainSessions(sessions: List<Session>): List<Session> {
        val mainByDay = mutableMapOf<Int, String>()
        val fallbackByDay = mutableMapOf<Int, String>()
        sessions.forEach { session ->
            val day = session.dayOfWeek ?: 1
            fallbackByDay.putIfAbsent(day, session.id)
            if (session.isMainSession && day !in mainByDay) mainByDay[day] = session.id
        }
        fallbackByDay.forEach { (day, id) -> mainByDay.putIfAbsent(day, id) }
        return sessions.map { session ->
            val day = session.dayOfWeek ?: 1
            session.copy(isMainSession = mainByDay[day] == session.id)
        }
    }

    private fun reduceSeriesForAugeAlert(alert: SessionEditorAugeAlert) {
        val targetExerciseIds = orderedExerciseIdsForAlert(alert)
        updateSession { session ->
            session.transformExercises { exercise ->
                if (exercise.id !in targetExerciseIds || exercise.sets.size <= 1) return@transformExercises exercise
                exercise.copy(sets = exercise.sets.dropLast(1))
            }
        }
    }

    private fun reduceRpeForAugeAlert(alert: SessionEditorAugeAlert) {
        val targetExerciseIds = orderedExerciseIdsForAlert(alert)
        updateSession { session ->
            session.transformExercises { exercise ->
                if (exercise.id !in targetExerciseIds) return@transformExercises exercise
                exercise.copy(sets = exercise.sets.map { it.lowerAugeIntensity() })
            }
        }
    }

    private fun reduceVolumeAndRpeForAugeAlert(alert: SessionEditorAugeAlert) {
        val targetExerciseIds = orderedExerciseIdsForAlert(alert)
        updateSession { session ->
            session.transformExercises { exercise ->
                if (exercise.id !in targetExerciseIds) return@transformExercises exercise
                val trimmedSets = when {
                    exercise.sets.size > 3 -> exercise.sets.take(3)
                    exercise.sets.size > 1 -> exercise.sets.dropLast(1)
                    else -> exercise.sets
                }
                exercise.copy(sets = trimmedSets.map { it.lowerAugeIntensity(capRpe = 7.0) })
            }
        }
    }

    private fun addSeriesForAugeAlert(alert: SessionEditorAugeAlert) {
        val targetExerciseIds = orderedExerciseIdsForAlert(alert)
        updateSession { session ->
            session.transformExercises { exercise ->
                if (exercise.id !in targetExerciseIds) return@transformExercises exercise
                val template = exercise.sets.lastOrNull()
                val nextSet = template?.let { createNextSetTemplate(exercise, it) } ?: ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetReps = 8,
                    targetRPE = 8.0,
                    intensityMode = IntensityMode.RPE,
                )
                exercise.copy(sets = exercise.sets + nextSet)
            }
        }
    }

    fun addGhostExercise(cardId: String) {
        val card = _uiState.value.ghostExerciseCards.find { it.cardId == cardId } ?: return
        val info = exerciseIndex[card.exerciseDbId.lowercase()] ?: return
        val newExercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = card.name,
            exerciseDbId = card.exerciseDbId,
            sets = (1..card.sets).map {
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetReps = card.reps,
                    targetRPE = card.rpe,
                    intensityMode = IntensityMode.RPE,
                )
            },
            restTime = card.restSeconds,
        )
        updateSession { session ->
            session.copy(
                exercises = session.exercises + newExercise,
            )
        }
    }

    fun applyAssistantSuggestion(suggestionId: String) {
        val suggestion = _uiState.value.assistantReport?.ajustes
            ?.firstOrNull { it.id == suggestionId } ?: return

        when (suggestion.type) {
            com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_SET -> {
                val muscle = suggestion.muscle
                if (muscle != null) {
                    updateSession { session ->
                        reduceSetsForMuscle(session, muscle)
                    }
                }
            }
            com.example.kpkn.domain.sessionassistant.AssistantActionType.LOWER_RPE -> {
                updateSession { session ->
                    lowerRpeOnAllExercises(session)
                }
            }
            com.example.kpkn.domain.sessionassistant.AssistantActionType.REMOVE_FAILURE -> {
                updateSession { session ->
                    convertFailureToRir(session)
                }
            }
            else -> Unit
        }
    }

    private fun reduceSetsForMuscle(session: Session, muscle: String): Session {
        var applied = false
        fun updateExercise(exercise: Exercise): Exercise {
            if (applied) return exercise
            val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return exercise
            val normalized = info.involvedMuscles.any { m ->
                VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis) == muscle
            }
            if (!normalized) return exercise
            if (exercise.sets.size <= 1) return exercise
            applied = true
            return exercise.copy(sets = exercise.sets.dropLast(1))
        }
        val updatedParts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map(::updateExercise))
        }
        val updatedExercises = session.exercises.map(::updateExercise)
        return session.copy(parts = updatedParts, exercises = updatedExercises)
    }

    private fun lowerRpeOnAllExercises(session: Session): Session {
        return session.transformExercises { exercise ->
            exercise.copy(sets = exercise.sets.map { set ->
                when (set.intensityMode) {
                    IntensityMode.FAILURE -> set.copy(
                        intensityMode = IntensityMode.RIR,
                        targetRIR = 1,
                        isFailure = false,
                    )
                    IntensityMode.RPE -> {
                        val currentRpe = set.targetRPE ?: 8.0
                        set.copy(targetRPE = maxOf(6.0, currentRpe - 0.5))
                    }
                    IntensityMode.RIR -> {
                        val currentRir = set.targetRIR ?: 2
                        set.copy(targetRIR = (currentRir + 1).coerceAtMost(5))
                    }
                    else -> set
                }
            })
        }
    }

    private fun convertFailureToRir(session: Session): Session {
        return session.transformExercises { exercise ->
            exercise.copy(sets = exercise.sets.map { set ->
                if (set.isFailure || set.intensityMode == IntensityMode.FAILURE) {
                    set.copy(
                        intensityMode = IntensityMode.RIR,
                        targetRIR = 1,
                        targetRPE = null,
                        isFailure = false,
                    )
                } else {
                    set
                }
            })
        }
    }

    private fun orderedExerciseIdsForAlert(alert: SessionEditorAugeAlert): Set<String> {
        val currentSession = _uiState.value.session ?: return emptySet()
        val rankedExerciseIds = _uiState.value.augeSummary.topExercises.map { it.exerciseId }
        return when {
            alert.exerciseId != null -> linkedSetOf(alert.exerciseId)
            alert.muscle != null -> {
                val matching = currentSession.allExercises()
                    .filter { exerciseMatchesPrimaryMuscle(it, alert.muscle) }
                    .map { it.id }
                    .toSet()
                linkedSetOf<String>().apply {
                    rankedExerciseIds.filterTo(this) { it in matching }
                    matching.filterTo(this) { it !in this }
                }
            }
            else -> linkedSetOf<String>().apply {
                addAll(rankedExerciseIds)
                addAll(currentSession.allExercises().map { it.id })
            }
        }
    }

    private fun exerciseMatchesPrimaryMuscle(exercise: Exercise, muscle: String?): Boolean {
        if (muscle.isNullOrBlank()) return false
        val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return false
        val primary = resolvePrimaryMuscle(info) ?: return false
        return primary == VolumeCalculator.normalizeMuscleGroup(muscle)
    }

}

private data class AugeVolumeAccumulator(
    var flat: Double = 0.0,
    var effective: Double = 0.0,
    var fail: Double = 0.0,
)

private data class MuscleRoleBreakdown(
    var primary: Double = 0.0,
    var secondary: Double = 0.0,
    var stabilizer: Double = 0.0,
    var neutralizer: Double = 0.0,
) {
    val total: Double
        get() = primary + secondary + stabilizer + neutralizer
    val primaryShare: Double
        get() = if (total > 0.0) primary / total else 0.0
    val secondaryShare: Double
        get() = if (total > 0.0) secondary / total else 0.0
    val stabilizerShare: Double
        get() = if (total > 0.0) (stabilizer + neutralizer) / total else 0.0
}

private data class MuscleRecommendationContext(
    val breakdown: MuscleRoleBreakdown = MuscleRoleBreakdown(),
    var usesPercent: Boolean = false,
    var usesRir: Boolean = false,
    var usesFailure: Boolean = false,
)

private data class SessionAugeComputation(
    val drain: PredictedDrain,
    val setCount: Int,
    val durationMinutes: Int,
    val difficulty: Int,
    val averageRpe: Double,
    val volumeMap: Map<String, AugeVolumeAccumulator>,
    val totalSpinalLoad: Double,
    val elbowStress: Int,
    val kneeStress: Int,
    val exerciseInsights: List<SessionEditorAugeExerciseInsight>,
    val muscleRoleMap: Map<String, MuscleRoleBreakdown>,
    val muscleRecommendationContext: Map<String, MuscleRecommendationContext>,
)

private fun buildAugeSummary(
    currentSession: Session,
    weekSessions: List<Session>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    settings: Settings,
    programLogs: List<WorkoutLog>,
    program: Program?,
    programId: String,
    mesoIndex: Int,
): SessionEditorAugeSummary {
    val currentMetrics = computeSessionAugeComputation(
        session = currentSession,
        exerciseIndex = exerciseIndex,
        settings = settings,
        programLogs = programLogs,
        programId = programId,
        mesoIndex = mesoIndex,
    )
    val weeklySessions = if (weekSessions.any { it.id == currentSession.id }) weekSessions else weekSessions + currentSession
    val weeklyMetrics = weeklySessions.map {
        computeSessionAugeComputation(
            session = it,
            exerciseIndex = exerciseIndex,
            settings = settings,
            programLogs = programLogs,
            programId = programId,
            mesoIndex = mesoIndex,
        )
    }
    val weeklyDrain = aggregateWeeklyDrain(weeklyMetrics.map { it.drain })
    val weeklySetCount = weeklyMetrics.sumOf { it.setCount }
    val weeklyDurationMinutes = weeklyMetrics.sumOf { it.durationMinutes }
    val weeklyDifficulty = computeDifficulty(
        averageRpe = weeklyMetrics.map { it.averageRpe }.filter { it > 0.0 }.averageOrNull() ?: 0.0,
        averageRm = 0.0,
    )

    val sessionLimit = defaultSessionVolumeLimit(settings)
    val weeklyLimit = defaultWeeklyVolumeLimit(settings)
    val alerts = mutableListOf<SessionEditorAugeAlert>()
    val suggestions = mutableListOf<SessionEditorAugeAlert>()

    currentMetrics.volumeMap.entries
        .sortedByDescending { it.value.effective }
        .forEach { (muscle, data) ->
            if (data.effective <= sessionLimit) return@forEach
            val roleBreakdown = currentMetrics.muscleRoleMap[muscle] ?: MuscleRoleBreakdown()
            val context = currentMetrics.muscleRecommendationContext[muscle] ?: MuscleRecommendationContext()
            val intensityHint = when {
                context.usesFailure -> "conviene quitar fallo o pasar a RIR"
                context.usesRir -> "subir un poco el RIR"
                context.usesPercent -> "bajar %RM"
                else -> "bajar RPE"
            }
            val roleHint = when {
                roleBreakdown.stabilizerShare >= 0.45 -> "Gran parte viene de estabilizadores; ajustar técnica o bajar intensidad global ayuda."
                roleBreakdown.secondaryShare >= 0.45 -> "Mucho volumen llega por secundarios en multiarticulares."
                else -> "El volumen es principalmente directo."
            }
            val message = when {
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) >= 0.7 ->
                    "Muchas series cerca del fallo. $roleHint Mejor recortar series y $intensityHint."
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) <= 0.3 ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. $roleHint Puedes bajar series o $intensityHint."
                else ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. $roleHint Recorta un poco o $intensityHint."
            }
            val correction = when {
                context.usesFailure || context.usesRir || context.usesPercent -> SessionEditorAugeCorrectionType.REDUCE_RPE
                else -> SessionEditorAugeCorrectionType.REDUCE_SERIES
            }
            suggestions += SessionEditorAugeAlert(
                id = "volume-session-$muscle",
                title = "Recomendación para $muscle",
                message = message,
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.SESSION,
                muscle = muscle,
                correctionType = correction,
            )
        }

    val weeklyVolumeMap = mutableMapOf<String, AugeVolumeAccumulator>()
    weeklySessions.forEach { session ->
        accumulateSessionVolume(session, exerciseIndex, weeklyVolumeMap)
    }
    val volumeThresholdsByMuscle = buildVolumeThresholdsByMuscle(
        sessionVolumeByMuscle = currentMetrics.volumeMap.mapValues { (_, value) -> value.flat },
        weeklyVolumeByMuscle = weeklyVolumeMap.mapValues { (_, value) -> value.flat },
        program = program,
        settings = settings,
    )
    weeklyVolumeMap.entries
        .sortedByDescending { it.value.flat }
        .forEach { (muscle, data) ->
            if (data.flat <= weeklyLimit) return@forEach
            suggestions += SessionEditorAugeAlert(
                id = "volume-week-$muscle",
                title = "Recomendación semanal para $muscle",
                message = "${formatOneDecimal(data.flat)} series equivalentes sobre $weeklyLimit. Repartir el estímulo de la semana te deja más fresco.",
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.WEEK,
                muscle = muscle,
                correctionType = SessionEditorAugeCorrectionType.REDUCE_SERIES,
            )
        }

    if (currentMetrics.totalSpinalLoad > 25.0) {
        val topAxialExercise = currentMetrics.exerciseInsights.maxByOrNull { it.spinal }
        val critical = currentMetrics.totalSpinalLoad > 40.0
        suggestions += SessionEditorAugeAlert(
            id = "system-spinal",
            title = "Recomendación columna",
            message = if (critical) {
                "La sesión acumula bastante carga axial. Bajar series o intensidad del ejercicio más demandante protege la columna."
            } else {
                "La sesión ya suma carga axial relevante. Ajustar densidad o intensidad puede mejorar la tolerancia."
            },
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topAxialExercise?.exerciseId,
            exerciseName = topAxialExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE,
        )
    }

    if (currentMetrics.drain.cns >= 85 || currentMetrics.averageRpe >= 9.3) {
        val topNeuralExercise = currentMetrics.exerciseInsights.maxByOrNull { it.cns }
        suggestions += SessionEditorAugeAlert(
            id = "system-neural",
            title = "Recomendación energía",
            message = "Tu Energía ya va alta para esta sesión. Bajar RPE, subir RIR o reducir %1RM deja margen sin romper el plan.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topNeuralExercise?.exerciseId,
            exerciseName = topNeuralExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_RPE,
        )
    }

    if (currentMetrics.elbowStress > 8) {
        suggestions += SessionEditorAugeAlert(
            id = "system-elbow",
            title = "Recomendación codos",
            message = "Hay bastante trabajo aislado de tríceps en ángulos agresivos. Ajustar intensidad o distribuir mejor los accesorios ayuda.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (currentMetrics.kneeStress > 8) {
        suggestions += SessionEditorAugeAlert(
            id = "system-knee",
            title = "Recomendación rodillas",
            message = "Extensiones puras o patrones similares se están acumulando. Bajar densidad o reforzar calentamiento mejora tolerancia.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (settings.calorieGoalObjective == CalorieGoalObjective.DEFICIT) {
        suggestions += SessionEditorAugeAlert(
            id = "info-deficit",
            title = "Déficit activo",
            message = "En déficit calórico conviene apretar menos el volumen efectivo por sesión para que la recuperación no se caiga.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (currentMetrics.averageRpe in 0.1..<6.5) {
        suggestions += SessionEditorAugeAlert(
            id = "suggest-light-intensity",
            title = "Sesión liviana",
            message = "La intensidad media está baja. Si el objetivo era más estímulo, aún hay margen para subir la intensidad (RPE, RIR o %RM).",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    val status = computeAugeStatus(currentMetrics.drain, weeklyMetrics.map { it.drain }, currentSession.id, weeklySessions, settings)

    val orderedAlerts = alerts
        .distinctBy { it.id }
        .sortedWith(
            compareBy<SessionEditorAugeAlert> {
                when (it.source) {
                    SessionEditorAugeAlertSource.SYSTEM -> 0
                    SessionEditorAugeAlertSource.SESSION -> 1
                    SessionEditorAugeAlertSource.WEEK -> 2
                    SessionEditorAugeAlertSource.EXERCISE -> 3
                }
            }.thenBy {
                it.title
            }
        )

    val orderedSuggestions = suggestions
        .distinctBy { it.id }
        .sortedBy { suggestion ->
            when (suggestion.source) {
                SessionEditorAugeAlertSource.SESSION -> 0
                SessionEditorAugeAlertSource.SYSTEM -> 1
                SessionEditorAugeAlertSource.WEEK -> 2
                SessionEditorAugeAlertSource.EXERCISE -> 3
            }
        }

    return SessionEditorAugeSummary(
        sessionDrain = currentMetrics.drain,
        weeklyDrain = weeklyDrain,
        sessionSetCount = currentMetrics.setCount,
        weeklySetCount = weeklySetCount,
        sessionDurationMinutes = currentMetrics.durationMinutes,
        weeklyDurationMinutes = weeklyDurationMinutes,
        sessionDifficulty = currentMetrics.difficulty,
        weeklyDifficulty = weeklyDifficulty,
        status = status,
        alerts = orderedAlerts,
        suggestions = orderedSuggestions,
        topExercises = currentMetrics.exerciseInsights.sortedByDescending { it.total }.take(4),
        muscleDrainProjection = currentMetrics.volumeMap.mapValues { (_, acc) ->
            (acc.effective / (sessionLimit.coerceAtLeast(1)) * 100.0).roundToInt().coerceIn(0, 100)
        },
        sessionVolumeByMuscle = currentMetrics.volumeMap.mapValues { (_, acc) -> acc.flat },
        weeklyVolumeByMuscle = weeklyVolumeMap.mapValues { (_, acc) -> acc.flat },
        volumeThresholdsByMuscle = volumeThresholdsByMuscle,
        usesCalibratedVolumeThresholds = program?.volumeRecommendations?.isNotEmpty() == true && program.athleteProfileScore != null,
    )
}

private fun computeSessionAugeComputation(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    settings: Settings,
    programLogs: List<WorkoutLog>,
    programId: String,
    mesoIndex: Int,
): SessionAugeComputation {
    val exercises = session.allExercises()
    val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
    val volumeMap = mutableMapOf<String, AugeVolumeAccumulator>()
    val roleMap = mutableMapOf<String, MuscleRoleBreakdown>()
    val recommendationContext = mutableMapOf<String, MuscleRecommendationContext>()
    var totalSets = 0
    var totalSpinalLoad = 0.0
    var elbowStress = 0
    var kneeStress = 0
    var rpeSum = 0.0
    var rpeCount = 0
    var rmSum = 0.0
    var rmCount = 0

    val exerciseInsights = exercises.mapNotNull { exercise ->
        val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return@mapNotNull null
        val validSets = exercise.validAugeSets()
        if (validSets.isEmpty()) return@mapNotNull null

        val metrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, info.equipment, info) ?: AugeMetrics()
        var muscular = 0.0
        var cns = 0.0
        var spinal = 0.0
        totalSets += validSets.size

        validSets.forEach { set ->
            val effectiveRpe = set.effectiveTargetRpe()
            rpeSum += effectiveRpe
            rpeCount++
            if (exercise.trainingMode == TrainingMode.RM && set.targetPercentageRM != null) {
                rmSum += set.targetPercentageRM / 100.0
                rmCount++
            }

            val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
            SessionMuscleFilter.relevantMusclesFor(info).forEach { muscle ->
                val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val hyperFactor = resolveMuscleVolumeContribution(muscle)
                val bucket = volumeMap.getOrPut(normalized) { AugeVolumeAccumulator() }
                bucket.flat += hyperFactor
                bucket.effective += hyperFactor * volumeMultiplier
                if (effectiveRpe >= 9.5) bucket.fail += hyperFactor

                val roleBucket = roleMap.getOrPut(normalized) { MuscleRoleBreakdown() }
                when (muscle.role) {
                    MuscleRole.PRIMARY -> roleBucket.primary += 1.0
                    MuscleRole.SECONDARY -> roleBucket.secondary += 1.0
                    MuscleRole.STABILIZER -> roleBucket.stabilizer += 1.0
                    MuscleRole.NEUTRALIZER -> roleBucket.neutralizer += 1.0
                }
            }

            totalSpinalLoad += info.axialLoadFactor ?: 0.0

            val completedSet = CompletedSet(
                id = set.id,
                weight = set.weight ?: 60.0,
                reps = set.targetReps ?: 8,
                rpe = set.targetRPE,
                rir = set.targetRIR,
                isFailure = set.isFailure || set.intensityMode == IntensityMode.FAILURE,
            )
            val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                set = completedSet,
                metrics = metrics,
                tanks = tanks,
                accumulatedSets = 0,
                restTime = exercise.restTime ?: 90,
                densityMultiplier = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId = exercise.supersetId,
                    restTime = exercise.restTime ?: 90,
                ),
            )
            muscular += drain.muscularDrainPct
            cns += drain.cnsDrainPct
            spinal += drain.spinalDrainPct
        }

        SessionMuscleFilter.relevantMusclesFor(info).forEach { muscle ->
            val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
            val ctx = recommendationContext.getOrPut(normalized) { MuscleRecommendationContext() }
            if (exercise.trainingMode == TrainingMode.RM) ctx.usesPercent = true
            if (validSets.any { it.targetRIR != null }) ctx.usesRir = true
            if (validSets.any { it.isFailure || it.intensityMode == IntensityMode.FAILURE }) ctx.usesFailure = true
        }

        val name = info.name.lowercase()
        if (name.contains("press francés") || name.contains("press frances") || name.contains("rompecráneos") || name.contains("rompecraneos") || name.contains("extensión en polea") || name.contains("extension en polea")) {
            elbowStress += validSets.size
        }
        if (name.contains("extensión de cuádriceps") || name.contains("extension de cuadriceps") || name.contains("sissy")) {
            kneeStress += validSets.size
        }

        val insight = SessionEditorAugeExerciseInsight(
            exerciseId = exercise.id,
            name = exercise.name,
            muscular = muscular.coerceAtMost(100.0).roundToInt(),
            cns = cns.coerceAtMost(100.0).roundToInt(),
            spinal = spinal.coerceAtMost(100.0).roundToInt(),
            total = ((muscular + cns + spinal) / 3.0).coerceAtMost(100.0).roundToInt(),
            suggestion = exerciseSuggestionForInsight(muscular, cns, spinal),
        )
        insight
    }

    val averageRest = exercises.mapNotNull { it.restTime }.ifEmpty { listOf(90) }.average().toInt()
    val predictedDrain = try {
        val base = AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings)
        val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
            logs = programLogs,
            programId = programId,
            mesoIndex = mesoIndex,
        )
        AugeFatigueEngine.adjustPredictedDrainWithEMA(base, ema)
    } catch (_: Throwable) {
        val fallback = AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings)
        val adjustedFallback = runCatching {
            val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
                logs = programLogs,
                programId = programId,
                mesoIndex = mesoIndex,
            )
            AugeFatigueEngine.adjustPredictedDrainWithEMA(fallback, ema)
        }.getOrDefault(fallback)
        if (adjustedFallback.cns == 0 && adjustedFallback.muscular == 0 && adjustedFallback.spinal == 0 && totalSets > 0) {
            PredictedDrain(cns = 10, muscular = 14, spinal = 8)
        } else {
            adjustedFallback
        }
    }

    return SessionAugeComputation(
        drain = predictedDrain,
        setCount = totalSets,
        durationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
        difficulty = computeDifficulty(
            averageRpe = if (rpeCount > 0) rpeSum / rpeCount else 0.0,
            averageRm = if (rmCount > 0) rmSum / rmCount else 0.0,
        ),
        averageRpe = if (rpeCount > 0) rpeSum / rpeCount else 0.0,
        volumeMap = volumeMap,
        totalSpinalLoad = totalSpinalLoad,
        elbowStress = elbowStress,
        kneeStress = kneeStress,
        exerciseInsights = exerciseInsights,
        muscleRoleMap = roleMap,
        muscleRecommendationContext = recommendationContext,
    )
}

private fun accumulateSessionVolume(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    targetMap: MutableMap<String, AugeVolumeAccumulator>,
) {
    session.allExercises().forEach { exercise ->
        val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return@forEach
        exercise.validAugeSets().forEach { set ->
            val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(set.effectiveTargetRpe())
            info.involvedMuscles.forEach { muscle ->
                val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val hyperFactor = resolveMuscleVolumeContribution(muscle)
                val bucket = targetMap.getOrPut(normalized) { AugeVolumeAccumulator() }
                bucket.flat += hyperFactor
                bucket.effective += hyperFactor * volumeMultiplier
            }
        }
    }
}

private fun computeAugeStatus(
    currentDrain: PredictedDrain,
    weeklyDrains: List<PredictedDrain>,
    currentSessionId: String,
    weeklySessions: List<Session>,
    settings: Settings,
): SessionEditorAugeStatus {
    val baseSessionsPerWeek = 5
    val weeklyBudget = when (settings.calorieGoalObjective) {
        CalorieGoalObjective.DEFICIT -> 220.0
        CalorieGoalObjective.MAINTENANCE -> 260.0
        CalorieGoalObjective.SURPLUS -> 300.0
    }
    val currentCombined = currentDrain.combinedDrain()
    val otherCombined = weeklyDrains.zip(weeklySessions)
        .filter { (_, session) -> session.id != currentSessionId }
        .sumOf { (drain, _) -> drain.combinedDrain() }
    val otherSessionsCount = weeklySessions.count { it.id != currentSessionId }
    val remainingQuota = (weeklyBudget - otherCombined).coerceAtLeast(0.0)
    val slotsLeft = (baseSessionsPerWeek - otherSessionsCount).coerceAtLeast(1)
    val recommendedPerSession = if (weeklySessions.size <= 1) weeklyBudget / baseSessionsPerWeek else remainingQuota / slotsLeft

    return when {
        currentCombined <= recommendedPerSession * 0.90 -> SessionEditorAugeStatus.OPTIMAL
        currentCombined <= recommendedPerSession * 1.25 -> SessionEditorAugeStatus.WARNING
        else -> SessionEditorAugeStatus.FATIGUING
    }
}

private fun computeDifficulty(averageRpe: Double, averageRm: Double): Int {
    if (averageRpe <= 0.0 && averageRm <= 0.0) return 0
    return ((averageRpe / 10.0) * 3.0 + (averageRm * 5.0) + 2.0)
        .roundToInt()
        .coerceIn(1, 10)
}

private fun exerciseSuggestionForInsight(
    muscular: Double,
    cns: Double,
    spinal: Double,
): String? = when {
    spinal > 75.0 -> "Fatiga de columna alta. Bajar intensidad o usar una variante más estable puede ayudarte."
    cns > 85.0 -> "Carga neural elevada. Reducir RPE o %1RM deja más margen para el resto de la sesión."
    muscular > 88.0 -> "Volumen muscular alto. Una serie menos probablemente mantiene el estímulo."
    else -> null
}

private fun resolveExerciseInfo(
    exercise: Exercise,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    val byId = exercise.exerciseDbId ?: exercise.exerciseId
    return byId?.lowercase()?.let(exerciseIndex::get)
        ?: exerciseIndex.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
}

private fun resolvePrimaryMuscle(info: ExerciseMuscleInfo): String? {
    val primary = info.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }
        ?: info.involvedMuscles.firstOrNull()
        ?: return null
    return VolumeCalculator.normalizeMuscleGroup(primary.muscle, primary.emphasis)
}

private fun defaultSessionVolumeLimit(settings: Settings): Int {
    val base = when (settings.calorieGoalObjective) {
        CalorieGoalObjective.DEFICIT -> 8.0
        CalorieGoalObjective.MAINTENANCE -> 9.0
        CalorieGoalObjective.SURPLUS -> 10.0
    }
    val athleteAdjustment = when (settings.athleteType) {
        AthleteType.BODYBUILDER, AthleteType.POWERBUILDER -> 0.8
        AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> -0.4
        else -> 0.0
    }
    return (base + athleteAdjustment).roundToInt().coerceAtLeast(6)
}

private fun defaultWeeklyVolumeLimit(settings: Settings): Int = when (settings.calorieGoalObjective) {
    CalorieGoalObjective.DEFICIT -> 20
    CalorieGoalObjective.MAINTENANCE -> 24
    CalorieGoalObjective.SURPLUS -> 28
}

private fun buildVolumeThresholdsByMuscle(
    sessionVolumeByMuscle: Map<String, Double>,
    weeklyVolumeByMuscle: Map<String, Double>,
    program: Program?,
    settings: Settings,
): Map<String, SessionEditorVolumeThreshold> {
    val personalized = program
        ?.volumeRecommendations
        .orEmpty()
        .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
        .mapValues { (_, grouped) ->
            val mev = grouped.sumOf { it.minEffectiveVolume }.toDouble().coerceAtLeast(1.0)
            val mav = grouped.sumOf { it.maxAdaptiveVolume }.toDouble().coerceAtLeast(mev)
            val mrv = grouped.sumOf { it.maxRecoverableVolume }.toDouble().coerceAtLeast(mav)
            Triple(mev, mav, mrv)
        }

    val involvedMuscles = (sessionVolumeByMuscle.keys + weeklyVolumeByMuscle.keys)
        .filter { it.isNotBlank() }
        .toSet()

    if (involvedMuscles.isEmpty()) return emptyMap()

    val defaultWeeklyMrv = defaultWeeklyVolumeLimit(settings).toDouble().coerceAtLeast(8.0)
    val defaultWeeklyMav = (defaultWeeklyMrv * 0.8).coerceAtLeast(6.0)
    val defaultWeeklyMev = (defaultWeeklyMav * 0.65).coerceAtLeast(4.0)

    return involvedMuscles.associateWith { muscle ->
        val fromProgram = personalized[muscle]
        val weeklyMev = fromProgram?.first ?: defaultWeeklyMev
        val weeklyMav = fromProgram?.second ?: defaultWeeklyMav
        val weeklyMrv = fromProgram?.third ?: defaultWeeklyMrv
        SessionEditorVolumeThreshold(
            sessionMev = (weeklyMev / 3.0).coerceAtLeast(1.0),
            sessionMav = (weeklyMav / 3.0).coerceAtLeast(1.0),
            sessionMrv = (weeklyMrv / 3.0).coerceAtLeast(1.0),
            weeklyMev = weeklyMev,
            weeklyMav = weeklyMav,
            weeklyMrv = weeklyMrv,
        )
    }
}

private fun aggregateWeeklyDrain(drains: List<PredictedDrain>): PredictedDrain {
    if (drains.isEmpty()) return PredictedDrain(0, 0, 0)

    // Evita saturar a 100 muy pronto: combinación lineal + amortiguación por acumulación.
    val cnsRaw = drains.sumOf { it.cns.toDouble() }
    val muscularRaw = drains.sumOf { it.muscular.toDouble() }
    val spinalRaw = drains.sumOf { it.spinal.toDouble() }

    val dampen = { value: Double ->
        val scaled = value * 0.72
        val nonlinear = value * value * 0.0022
        (scaled - nonlinear).roundToInt().coerceIn(0, 100)
    }

    return PredictedDrain(
        cns = dampen(cnsRaw),
        muscular = dampen(muscularRaw),
        spinal = dampen(spinalRaw),
    )
}

private fun PredictedDrain.combinedDrain(): Double = (cns + muscular + spinal) / 3.0

private fun Exercise.validAugeSets(): List<ExerciseSet> = sets.filterNot { it.isIneffective }

private fun ExerciseSet.effectiveTargetRpe(): Double {
    if (isFailure || intensityMode == IntensityMode.FAILURE) return 10.0
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

private fun ExerciseSet.lowerAugeIntensity(capRpe: Double? = null): ExerciseSet {
    val effectiveRpe = effectiveTargetRpe()
    if (effectiveRpe < 7.0 && capRpe == null) return this

    val targetRpe = maxOf(6.0, (capRpe ?: (effectiveRpe - 0.5)))
    return when (intensityMode ?: IntensityMode.RPE) {
        IntensityMode.SOLO_RM -> copy(
            targetPercentageRM = ((targetPercentageRM ?: 100.0) - 5.0).coerceAtLeast(55.0),
        )
        IntensityMode.RIR -> copy(
            targetRIR = ((targetRIR ?: 2) + 1).coerceAtMost(5),
            isFailure = false,
        )
        IntensityMode.FAILURE -> copy(
            intensityMode = IntensityMode.RIR,
            targetRIR = 1,
            targetRPE = null,
            isFailure = false,
        )
        else -> copy(
            targetRPE = targetRpe.coerceAtMost(capRpe ?: 10.0),
            targetRIR = null,
            isFailure = false,
        )
    }
}

private data class ClonePayload(
    val parts: List<SessionPart>,
    val looseExercises: List<Exercise>,
)

private fun Session.buildCloneExerciseOptions(): List<SessionCloneExerciseOption> {
    val fromParts = parts.flatMap { part ->
        part.exercises.map { exercise ->
            SessionCloneExerciseOption(
                exerciseId = exercise.id,
                name = exercise.name.ifBlank { "Ejercicio" },
                sourcePartName = part.name,
            )
        }
    }
    val loose = exercises.map { exercise ->
        SessionCloneExerciseOption(
            exerciseId = exercise.id,
            name = exercise.name.ifBlank { "Ejercicio" },
            sourcePartName = null,
        )
    }
    return fromParts + loose
}

private fun buildClonePayload(
    source: Session,
    selectedExerciseIds: Set<String>?,
): ClonePayload {
    val filter: (Exercise) -> Boolean = { exercise ->
        selectedExerciseIds == null || exercise.id in selectedExerciseIds
    }
    val sourceParts = if (source.parts.isNotEmpty()) source.parts else {
        if (source.exercises.isEmpty()) emptyList() else listOf(
            SessionPart(
                id = UUID.randomUUID().toString(),
                name = source.name.ifBlank { "Bloque importado" },
                exercises = source.exercises,
                color = PART_COLORS.firstOrNull(),
            )
        )
    }
    val clonedParts = sourceParts.mapNotNull { part ->
        val selected = part.exercises.filter(filter)
        if (selected.isEmpty()) return@mapNotNull null
        val supersetIds = selected.mapNotNull { it.supersetId }.distinct().associateWith { UUID.randomUUID().toString() }
        part.copy(
            id = UUID.randomUUID().toString(),
            exercises = selected.map { cloneExerciseForTransfer(it, supersetIds) },
        )
    }

    val loose = source.exercises
        .filter(filter)
        .map { cloneExerciseForTransfer(it, emptyMap()) }

    return ClonePayload(parts = clonedParts, looseExercises = loose)
}

private fun cloneExerciseForTransfer(
    exercise: Exercise,
    supersetIds: Map<String, String>,
): Exercise = exercise.copy(
    id = UUID.randomUUID().toString(),
    supersetId = exercise.supersetId?.let(supersetIds::get),
    warmupSets = exercise.warmupSets.map { it.copy(id = UUID.randomUUID().toString()) },
    sets = exercise.sets.map { it.copy(id = UUID.randomUUID().toString()) },
)

private fun mergeSessionWithPayload(
    base: Session,
    source: Session,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Session {
    if (applyMode == SessionCloneApplyMode.REPLACE) {
        return createSessionFromPayload(
            source = source,
            dayOfWeek = base.dayOfWeek,
            targetName = base.name,
            payload = payload,
            selectedExerciseIds = selectedExerciseIds,
            existingId = base.id,
            preserveBackgroundFrom = base,
        )
    }
    return base.copy(
        exercises = base.exercises + payload.looseExercises,
        parts = base.parts + payload.parts,
    )
}

private fun createSessionFromPayload(
    source: Session,
    dayOfWeek: Int?,
    targetName: String,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
    existingId: String? = null,
    preserveBackgroundFrom: Session? = null,
): Session {
    val name = when {
        selectedExerciseIds == null -> source.name.ifBlank { targetName.ifBlank { "Sesión" } }
        else -> targetName.ifBlank { source.name.ifBlank { "Sesión" } }
    }
    val base = preserveBackgroundFrom ?: source
    return source.copy(
        id = existingId ?: UUID.randomUUID().toString(),
        name = name,
        dayOfWeek = dayOfWeek,
        exercises = payload.looseExercises,
        parts = payload.parts,
        background = base.background,
        coverStyle = base.coverStyle,
        isMainSession = true,
    )
}

private fun createSessionForTargetDay(
    source: Session,
    dayOfWeek: Int,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
): Session = createSessionFromPayload(
    source = source,
    dayOfWeek = dayOfWeek,
    targetName = "Sesión ${dayLabelShort(dayOfWeek)}",
    payload = payload,
    selectedExerciseIds = selectedExerciseIds,
)

private fun mergeSessions(
    base: Session,
    incoming: Session,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Session {
    val payload = buildClonePayload(incoming, selectedExerciseIds)
    return mergeSessionWithPayload(
        base = base,
        source = incoming,
        payload = payload,
        selectedExerciseIds = selectedExerciseIds,
        applyMode = applyMode,
    )
}

private fun Program.findSessionInProgram(
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
    sessionId: String,
): Session? {
    val macro = macrocycles.getOrNull(macroIndex) ?: return null
    val meso = macro.blocks.flatMap { it.mesocycles }.getOrNull(mesoIndex) ?: return null
    val week = meso.weeks.firstOrNull { it.id == weekId } ?: return null
    return week.sessions.firstOrNull { it.id == sessionId }
}

private fun buildCloneDayOptions(
    program: Program,
    currentSessionId: String,
): List<SessionCloneDayOption> {
    val options = mutableListOf<SessionCloneDayOption>()
    var globalMesoIndex = 0
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEachIndexed { blockIndex, block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    (1..7).forEach { day ->
                        val existing = week.sessions.firstOrNull { it.dayOfWeek == day }
                        options += SessionCloneDayOption(
                            key = "$macroIndex|$globalMesoIndex|${week.id}|$day",
                            macroIndex = macroIndex,
                            mesoIndex = globalMesoIndex,
                            weekId = week.id,
                            dayOfWeek = day,
                            macroName = macro.name,
                            blockName = block.name,
                            mesoName = meso.name,
                            weekName = week.name,
                            existingSessionId = existing?.id,
                            existingSessionName = existing?.name,
                            isCurrentSessionDay = existing?.id == currentSessionId,
                        )
                    }
                }
                globalMesoIndex++
            }
        }
    }
    return options
}

private fun buildCloneSourceOptions(
    program: Program,
    currentSessionId: String,
): List<SessionCloneSourceOption> {
    val options = mutableListOf<SessionCloneSourceOption>()
    var globalMesoIndex = 0
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    week.sessions.forEach { session ->
                        if (session.id == currentSessionId) return@forEach
                        options += SessionCloneSourceOption(
                            sessionId = session.id,
                            dayOfWeek = session.dayOfWeek,
                            macroIndex = macroIndex,
                            mesoIndex = globalMesoIndex,
                            weekId = week.id,
                            macroName = macro.name,
                            blockName = block.name,
                            mesoName = meso.name,
                            weekName = week.name,
                            sessionName = session.name.ifBlank { "Sesión" },
                            exerciseCount = session.allExercises().size,
                            exercises = session.buildCloneExerciseOptions(),
                        )
                    }
                }
                globalMesoIndex++
            }
        }
    }
    return options
}

    private fun createExerciseFromInfo(info: ExerciseMuscleInfo): Exercise =
        Exercise(
            id = UUID.randomUUID().toString(),
            name = info.name,
            exerciseDbId = info.id,
            exerciseId = info.id,
            canonicalExerciseId = info.id.lowercase(),
            exerciseFamilyId = info.id.lowercase(),
            trainingMode = if (info.category.equals("Fuerza", true)) TrainingMode.RM else TrainingMode.REPS,
            restTime = suggestRestSeconds(3, 8.0),
            sets = listOf(
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetReps = if (info.category.equals("Fuerza", true)) 5 else 8,
                    targetRPE = 8.0,
                    intensityMode = IntensityMode.RPE,
                )
            ),
            setupCues = info.setupCues.orEmpty(),
            executionCues = info.executionCues.orEmpty(),
        )

    private fun createBlankExercise(): Exercise =
        Exercise(
            id = UUID.randomUUID().toString(),
            name = "",
            canonicalExerciseId = null,
            exerciseFamilyId = null,
            trainingMode = TrainingMode.REPS,
            restTime = 90,
            sets = listOf(
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetReps = 8,
                    targetRPE = 8.0,
                    intensityMode = IntensityMode.RPE,
                )
            ),
        )

private fun Session.transformExercises(transform: (Exercise) -> Exercise): Session {
    var applied = false

    fun updateExercise(exercise: Exercise): Exercise {
        if (applied) return exercise
        val updated = transform(exercise)
        if (updated != exercise) {
            applied = true
        }
        return updated
    }

    val updatedParts = parts.map { part ->
        part.copy(exercises = part.exercises.map(::updateExercise))
    }
    val updatedExercises = exercises.map(::updateExercise)

    return copy(parts = updatedParts, exercises = updatedExercises)
}

private fun formatOneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

private data class LocatedSession(
    val session: Session,
    val week: ProgramWeek,
    val macroIndex: Int,
    val mesoIndex: Int,
)

private const val SESSION_EDITOR_DRAFT_PREFS = "session_editor_drafts"
private const val DEFAULT_SESSION_BACKGROUND = "solid://obsidian"
private val DEFAULT_SESSION_BACKGROUNDS = listOf(
    DEFAULT_SESSION_BACKGROUND,
    "gradient://ember",
    "gradient://lagoon",
    "gradient://velvet",
    "gradient://forest",
)
val PART_COLORS = listOf("#00F0FF", "#3B82F6", "#00F19F", "#A855F7", "#EAB308", "#F43F5E", "#06B6D4", "#8B5CF6")

private fun SessionEditorViewModel.locateSession(
    program: Program,
    targetSessionId: String,
    targetWeekId: String?,
    targetMacroIndex: Int?,
    targetMesoIndex: Int?,
): LocatedSession? {
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEachIndexed { mesoIndex, meso ->
                meso.weeks.forEach { week ->
                    if (targetWeekId != null && week.id != targetWeekId) return@forEach
                    val found = week.sessions.find { it.id == targetSessionId }
                    if (found != null) return LocatedSession(found, week, macroIndex, mesoIndex)
                }
            }
        }
    }
    if (targetMacroIndex != null && targetMesoIndex != null && !targetWeekId.isNullOrBlank()) {
        val week = findWeek(program, targetMacroIndex, targetMesoIndex, targetWeekId)
        val found = week?.sessions?.find { it.id == targetSessionId }
        if (found != null) return LocatedSession(found, week, targetMacroIndex, targetMesoIndex)
    }
    return null
}

private fun SessionEditorViewModel.findWeek(
    program: Program,
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
): ProgramWeek? {
    val macro = program.macrocycles.getOrNull(macroIndex) ?: return null
    return macro.blocks.flatMap { it.mesocycles }.getOrNull(mesoIndex)?.weeks?.find { it.id == weekId }
}

private fun createDraftSession(sessionId: String, dayOfWeek: Int?): Session = Session(
    id = sessionId,
    name = "",
    description = "",
    parts = emptyList(),
    dayOfWeek = dayOfWeek,
    background = SessionBackground(
        type = SessionBackgroundType.COLOR,
        value = DEFAULT_SESSION_BACKGROUND,
        style = SessionBackgroundStyle(blur = 0f, brightness = 0.92f),
    ),
    coverStyle = CoverStyle(filters = CoverFilters(), labelPosition = LabelPosition.BOTTOM_LEFT),
    isMainSession = true,
)

private fun SessionEditorViewModel.buildRoadmapOptions(program: Program): List<SessionRoadmapOption> {
    return program.macrocycles.flatMapIndexed { macroIndex, macro ->
        var mesoOffset = 0
        macro.blocks.flatMapIndexed { blockIndex, block ->
            val blockOffset = mesoOffset
            val items = block.mesocycles.flatMapIndexed { mesoIndex, meso ->
                meso.weeks.mapIndexed { weekIndex, week ->
                    SessionRoadmapOption(
                        macroIndex = macroIndex,
                        blockIndex = blockIndex,
                        mesoIndex = blockOffset + mesoIndex,
                        weekIndex = weekIndex,
                        weekId = week.id,
                        macroName = macro.name,
                        blockName = block.name,
                        weekName = week.name,
                        sessionCount = week.sessions.size,
                    )
                }
            }
            mesoOffset += block.mesocycles.size
            items
        }
    }
}

private fun Program.updateWeekById(
    weekId: String,
    transform: (ProgramWeek) -> ProgramWeek,
): Program = copy(
    macrocycles = macrocycles.map { macro ->
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    if (week.id == weekId) transform(week) else week
                })
            })
        })
    }
)

private fun <T> moveItem(list: List<T>, targetId: String, direction: Int, key: (T) -> String): List<T> {
    val mutable = list.toMutableList()
    val index = mutable.indexOfFirst { key(it) == targetId }
    if (index == -1) return list
    val target = (index + direction).coerceIn(0, mutable.lastIndex)
    if (index == target) return list
    val moved = mutable.removeAt(index)
    mutable.add(target, moved)
    return mutable
}

private fun Program.updateWeekSessions(
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
    transform: (List<Session>) -> List<Session>,
): Program = copy(
    macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
        if (currentMacroIndex != macroIndex) return@mapIndexed macro
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                if (currentMesoIndex != mesoIndex) return@mapIndexed meso
                meso.copy(weeks = meso.weeks.map { week ->
                    if (week.id != weekId) week else week.copy(sessions = transform(week.sessions))
                })
            })
        })
    }
)

private fun Session.normalizeSession(): Session {
    val normalizedBackground = background ?: SessionBackground(SessionBackgroundType.COLOR, DEFAULT_SESSION_BACKGROUND, SessionBackgroundStyle(0f, 0.92f))
    val normalizedCoverStyle = coverStyle ?: CoverStyle(filters = CoverFilters(), labelPosition = LabelPosition.BOTTOM_LEFT)
    val groupedParts = parts.filterNot { it.isUncategorized() }
    val uncategorizedExercises = parts.filter { it.isUncategorized() }.flatMap { it.exercises }
    val normalizedParts = groupedParts.map { part ->
        part.copy(exercises = part.exercises.map { it.normalizeExercise() })
    }
    val normalizedLooseExercises = (exercises + uncategorizedExercises).map { it.normalizeExercise() }
    return copy(
        description = description ?: "",
        exercises = normalizedLooseExercises,
        parts = normalizedParts,
        background = normalizedBackground,
        coverStyle = normalizedCoverStyle,
    )
}

private fun Exercise.normalizeExercise(): Exercise {
    val normalizedSets = if (sets.isEmpty()) {
        listOf(ExerciseSet(UUID.randomUUID().toString(), targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE))
    } else sets.map { it.normalizeSet(this) }
    val normalizedIdentity = normalizedIdentityFields()
    val resolved1rm = resolveReferenceCapacity(normalizedIdentity.copy(sets = normalizedSets))
    return normalizedIdentity.copy(
        restTime = restTime ?: suggestRestSeconds(normalizedSets.size, normalizedSets.mapNotNull { it.targetRPE }.averageOrNull() ?: 8.0),
        reference1RM = resolved1rm,
        sets = normalizedSets,
    )
}

private fun ExerciseSet.normalizeSet(exercise: Exercise): ExerciseSet {
    val normalized = when (exercise.trainingMode) {
        TrainingMode.RM -> copy(
            intensityMode = IntensityMode.LOAD,
            targetRPE = null,
            targetRIR = null,
            isFailure = false,
            isAmrap = false,
            targetPercentageRM = (targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0),
        )
        TrainingMode.SOLO_RPE -> copy(
            intensityMode = IntensityMode.RPE,
            targetRPE = (targetRPE ?: 8.0).coerceIn(1.0, 10.0),
            targetRIR = null,
            targetPercentageRM = null,
            targetReps = null,
            targetDuration = null,
            isFailure = false,
            isAmrap = false,
        )
        else -> {
            val resolvedMode = when (intensityMode) {
                null, IntensityMode.SOLO_RM -> IntensityMode.RPE
                else -> intensityMode
            }
            copy(intensityMode = resolvedMode)
        }
    }
    val autoWeight = calculateSuggestedLoad(exercise, normalized)
    return normalized.copy(weight = autoWeight ?: normalized.weight)
}

private fun createNextSetTemplate(exercise: Exercise, template: ExerciseSet): ExerciseSet {
    val base = template.copy(id = UUID.randomUUID().toString())
    // En modo RM se estima reducción de carga por fatiga; el resto mantiene la intensidad elegida
    if (exercise.trainingMode != TrainingMode.RM) return base

    val reps = template.targetReps ?: 1
    val fatigueDrop = when {
        reps >= 10 -> 5.0
        reps >= 6  -> 4.0
        reps >= 3  -> 3.0
        else       -> 2.0
    }
    return base.copy(
        targetPercentageRM = ((template.targetPercentageRM ?: 100.0) - fatigueDrop).coerceAtLeast(45.0),
        intensityMode = IntensityMode.LOAD,
    )
}

private fun Exercise.asCompetitionMovement(): Exercise {
    val normalizedSets = List(3) { index ->
        val existing = sets.getOrNull(index)
        (existing ?: ExerciseSet(id = UUID.randomUUID().toString())).copy(
            intensityMode = IntensityMode.LOAD,
            targetRPE = existing?.targetRPE,
            targetRIR = null,
            targetReps = 1,
            attemptResult = existing?.attemptResult ?: AttemptResult.PENDING,
            judgingLights = (existing?.judgingLights ?: emptyList()).let { lights ->
                val mutable = lights.toMutableList()
                while (mutable.size < 3) mutable.add(null)
                mutable.take(3)
            },
        )
    }
    return copy(
        isCompetitionLift = true,
        sets = normalizedSets,
        restTime = 0,
    )
}

private fun Session.allExercises(): List<Exercise> = exercises + parts.flatMap { it.exercises }

private fun SessionPart.isUncategorized(): Boolean {
    val normalized = name.trim().lowercase()
    return normalized == "sin categoría" || normalized == "sin categoria" || normalized == "sin grupo"
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun dayLabel(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "día"
}

private fun dayLabelShort(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}
