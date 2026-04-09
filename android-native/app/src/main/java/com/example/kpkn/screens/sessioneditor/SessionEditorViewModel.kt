package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateWeightFrom1RMAndIntensity
import com.example.kpkn.domain.calculations.estimateSessionDurationMinutes
import com.example.kpkn.domain.calculations.suggestRestSeconds
import com.example.kpkn.domain.training.VolumeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) {
    val alertCount: Int
        get() = alerts.count { it.severity != SessionEditorAugeAlertSeverity.INFO }

    val hasCriticalAlerts: Boolean
        get() = alerts.any { it.severity == SessionEditorAugeAlertSeverity.CRITICAL }
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
    val selectedSiblingSessionId: String? = null,
    val workoutLogs: List<WorkoutLog> = emptyList(),
    val feedbackByLogId: Map<String, PostSessionFeedback> = emptyMap(),
    val localDraftHistory: List<Session> = emptyList(),
    val sheet: SessionEditorSheet = SessionEditorSheet.NONE,
    val searchQuery: String = "",
    val pickerTargetPartId: String? = null,
    val pickerTargetExerciseId: String? = null,
    val warmupExerciseId: String? = null,
    val quickActionsPartId: String? = null,
    val quickActionsExerciseId: String? = null,
    val collapsedPartIds: Set<String> = emptySet(),
    val hasUnsavedChanges: Boolean = false,
    val estimatedDurationMinutes: Int = 0,
    val predictedDrain: PredictedDrain? = null,
    val augeSummary: SessionEditorAugeSummary = SessionEditorAugeSummary(),
    val ruleDefaults: SessionEditorRuleDefaults = SessionEditorRuleDefaults(),
    val ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
    val pendingSessionSwitchId: String? = null,
    val supersetManagerPartId: String? = null,
    val supersetManagerSupersetId: String? = null,
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

    private val repository = ProgramRepository.getInstance()
    private val augeRepository = AugeRepository.getInstance(application)
    private val exerciseIndex = EXERCISE_DATABASE.associateBy { it.id }
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
        loadSession()
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
        val draft = persistedDraft?.session ?: fallbackDraft
        val resolvedRuleDefaults = persistedDraft?.ruleDefaults ?: SessionEditorRuleDefaults()
        val resolvedRuleLimits = persistedDraft?.ruleLimits ?: SessionEditorRuleLimits()
        val loadedFromDraft = persistedDraft != null

        _uiState.value = SessionEditorUiState(
            session = draft,
            originalSession = existing ?: draft,
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
            selectedSiblingSessionId = draft.id,
            localDraftHistory = listOf(draft),
            ruleDefaults = resolvedRuleDefaults,
            ruleLimits = resolvedRuleLimits,
            hasUnsavedChanges = loadedFromDraft,
        )

        refreshDerivedState()
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
            state.copy(localDraftHistory = (state.localDraftHistory + current).takeLast(20))
        }
        _uiState.update { state ->
            val updated = transform(current).normalizeSession()
            state.copy(
                session = updated,
                dayOfWeek = updated.dayOfWeek ?: state.dayOfWeek,
                hasUnsavedChanges = updated != state.originalSession,
            )
        }
        refreshDerivedState()
    }

    private fun refreshDerivedState() {
        val state = _uiState.value
        val session = state.session ?: return
        val exercises = session.allExercises()
        val totalSets = exercises.sumOf { it.sets.size.coerceAtLeast(1) }
        val averageRest = exercises.mapNotNull { it.restTime }.ifEmpty { listOf(90) }.average().toInt()
        val draftAwareWeekSessions = if (state.weekSessions.any { it.id == session.id }) {
            state.weekSessions.map { if (it.id == session.id) session else it }
        } else {
            state.weekSessions + session
        }
        val summary = runCatching {
            buildAugeSummary(
                currentSession = session,
                weekSessions = draftAwareWeekSessions,
                exerciseIndex = exerciseIndex,
                settings = repository.settings.value,
            )
        }.getOrElse {
            SessionEditorAugeSummary(
                sessionDrain = PredictedDrain(0, 0, 0),
                weeklyDrain = PredictedDrain(0, 0, 0),
                sessionSetCount = totalSets,
                sessionDurationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
            )
        }
        _uiState.update {
            it.copy(
                estimatedDurationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
                predictedDrain = summary.sessionDrain,
                augeSummary = summary,
            )
        }
    }

    fun updateSessionName(name: String) = updateSession { it.copy(name = name) }
    fun updateSessionDescription(description: String) = updateSession { it.copy(description = description) }
    fun updateSessionMeetDay(isMeetDay: Boolean) = updateSession {
        it.copy(isMeetDay = isMeetDay, isCompetitionSession = if (isMeetDay) true else it.isCompetitionSession)
    }
    fun updateSessionMeetBodyweight(bodyweight: Double?) = updateSession { it.copy(meetBodyweight = bodyweight) }
    fun updateDayOfWeek(dayOfWeek: Int) = updateSession { it.copy(dayOfWeek = dayOfWeek) }
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

    fun openPicker(partId: String?, exerciseId: String? = null) {
        _uiState.update {
            it.copy(
                sheet = SessionEditorSheet.EXERCISE_PICKER,
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

    fun addExerciseToPart(partId: String?, info: ExerciseMuscleInfo): String {
        val newExercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = info.name,
            exerciseDbId = info.id,
            trainingMode = if (info.category.equals("Fuerza", true)) TrainingMode.PERCENT else TrainingMode.REPS,
            restTime = suggestRestSeconds(3, 8.0),
            sets = listOf(ExerciseSet(UUID.randomUUID().toString(), targetReps = if (info.category.equals("Fuerza", true)) 5 else 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE)),
            setupCues = info.setupCues.orEmpty(),
            executionCues = info.executionCues.orEmpty(),
        )
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

    fun replaceExerciseInPart(partId: String?, exerciseId: String, info: ExerciseMuscleInfo) {
        updateExercise(partId, exerciseId) { current ->
            current.copy(
                name = info.name,
                exerciseDbId = info.id,
                setupCues = info.setupCues.orEmpty(),
                executionCues = info.executionCues.orEmpty(),
            )
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
        openPicker(state.quickActionsPartId, exerciseId)
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
            fun Exercise.applyDefaults(): Exercise {
                val sets = List(defaults.setCount) { index ->
                    val existing = this.sets.getOrNull(index)
                    (existing ?: ExerciseSet(id = UUID.randomUUID().toString())).copy(
                        targetReps = defaults.reps,
                        targetRPE = defaults.rpe,
                        intensityMode = IntensityMode.RPE,
                    ).normalizeSet(this)
                }
                return copy(sets = sets, restTime = suggestRestSeconds(sets.size, defaults.rpe))
            }

            session.copy(
                exercises = if (partId == null) session.exercises.map { it.applyDefaults() } else session.exercises,
                parts = session.parts.map { part ->
                    if (partId != null && part.id != partId) return@map part
                    part.copy(exercises = part.exercises.map { it.applyDefaults() })
                }
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
        val normalizedMaxRpe = maxRPE?.takeIf { it > 0.0 }?.coerceIn(1.0, 10.0)
        val normalizedMaxExercises = maxExercisesPerMuscle?.takeIf { it > 0 }
        _uiState.update { state ->
            state.copy(
                ruleLimits = SessionEditorRuleLimits(
                    maxRPE = normalizedMaxRpe,
                    maxExercisesPerMuscle = normalizedMaxExercises,
                )
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

    fun restoreDraftSnapshot(snapshot: Session) {
        _uiState.update { state ->
            state.copy(session = snapshot, hasUnsavedChanges = snapshot != state.originalSession)
        }
        refreshDerivedState()
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
        val current = session.background ?: SessionBackground(SessionBackgroundType.COLOR, DEFAULT_SESSION_GRADIENTS.first())
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

    fun exportToSession(targetSessionId: String) {
        val state = _uiState.value
        val source = state.session ?: return
        val program = repository.getProgramById(programId) ?: return
        val updatedProgram = program.updateWeekSessions(state.macroIndex, state.mesoIndex, state.weekId) { sessions ->
            sessions.map { session ->
                if (session.id == targetSessionId) {
                    val importedParts = source.transferableParts().map(::clonePartForTransfer)
                    session.copy(parts = normalizeTransferredParts(session.parts + importedParts))
                } else session
            }
        }
        repository.updateProgram(updatedProgram)
        closeSheet()
        loadSession()
    }

    fun importFromSession(sourceSessionId: String) {
        val source = _uiState.value.weekSessions.find { it.id == sourceSessionId } ?: return
        updateSession { current ->
            val importedParts = source.transferableParts().map(::clonePartForTransfer)
            current.copy(parts = normalizeTransferredParts(current.parts + importedParts))
        }
        closeSheet()
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

    fun requestSessionSwitch(targetSessionId: String) {
        val state = _uiState.value
        if (state.session?.id == targetSessionId) return
        if (state.hasUnsavedChanges) {
            _uiState.update { it.copy(pendingSessionSwitchId = targetSessionId, sheet = SessionEditorSheet.SAVE) }
            return
        }
        switchToSession(targetSessionId)
    }

    fun createSessionForDay(dayOfWeek: Int): SessionEditorSaveResult {
        val state = _uiState.value
        if (state.hasUnsavedChanges) {
            return SessionEditorSaveResult(
                success = false,
                message = "Guarda o descarta los cambios actuales antes de crear una nueva sesión.",
            )
        }

        val existingOnDay = state.weekSessions.firstOrNull { it.dayOfWeek == dayOfWeek }
        if (existingOnDay != null) {
            requestSessionSwitch(existingOnDay.id)
            return SessionEditorSaveResult(
                success = true,
                message = "Ese día ya tiene sesión. Te llevamos a ella.",
            )
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
                siblingSessions = (it.siblingSessions + newSession).sortedBy { session -> session.dayOfWeek ?: 99 },
                weekSessions = (it.weekSessions + newSession).sortedBy { session -> session.dayOfWeek ?: 99 },
                selectedSiblingSessionId = newSession.id,
                localDraftHistory = listOf(newSession),
                hasUnsavedChanges = true,
                draftBundle = it.draftBundle?.copy(sessionId = newSession.id, dayOfWeek = dayOfWeek),
            )
        }
        refreshDerivedState()
        loadHistory()
        return SessionEditorSaveResult(success = true, message = "Sesión creada para ${dayLabel(dayOfWeek)}")
    }

    fun discardAndSwitchPendingSession() {
        val target = _uiState.value.pendingSessionSwitchId ?: return
        _uiState.value.session?.let {
            clearPersistedDraft(
                weekId = _uiState.value.weekId,
                macroIndex = _uiState.value.macroIndex,
                mesoIndex = _uiState.value.mesoIndex,
                sessionId = it.id,
            )
        }
        _uiState.update { it.copy(pendingSessionSwitchId = null, sheet = SessionEditorSheet.NONE) }
        switchToSession(target)
    }

    private fun switchToSession(targetSessionId: String) {
        val program = repository.getProgramById(programId) ?: return
        val currentState = _uiState.value
        val located = locateSession(
            program = program,
            targetSessionId = targetSessionId,
            targetWeekId = currentState.weekId,
            targetMacroIndex = currentState.macroIndex,
            targetMesoIndex = currentState.mesoIndex,
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
                selectedSiblingSessionId = resolvedSession.id,
                hasUnsavedChanges = persistedDraft != null,
                pendingSessionSwitchId = null,
                sheet = SessionEditorSheet.NONE,
                localDraftHistory = listOf(resolvedSession),
                ruleDefaults = persistedDraft?.ruleDefaults ?: it.ruleDefaults,
                ruleLimits = persistedDraft?.ruleLimits ?: it.ruleLimits,
            )
        }
        refreshDerivedState()
        loadHistory()
    }

    private fun validateBeforeSave(state: SessionEditorUiState, draft: Session): String? {
        if (draft.name.isBlank()) {
            return "La sesión debe tener un nombre antes de guardar."
        }

        val maxRpe = state.ruleLimits.maxRPE
        if (maxRpe != null) {
            draft.allExercises().forEach { exercise ->
                exercise.sets.forEach { set ->
                    val effectiveRpe = when {
                        set.targetRPE != null -> set.targetRPE
                        set.targetRIR != null -> (10 - set.targetRIR).toDouble()
                        set.isFailure || set.intensityMode == IntensityMode.FAILURE -> 10.0
                        else -> null
                    }
                    if (effectiveRpe != null && effectiveRpe > maxRpe) {
                        return "Intensidad máxima configurada: RPE ${formatOneDecimal(maxRpe)}. Hay series que la superan (RPE ${formatOneDecimal(effectiveRpe)})."
                    }
                }
            }
        }

        val maxExercisesPerMuscle = state.ruleLimits.maxExercisesPerMuscle
        if (maxExercisesPerMuscle != null) {
            val muscleCount = mutableMapOf<String, Int>()
            draft.allExercises().forEach { exercise ->
                val info = resolveExerciseInfo(exercise, exerciseIndex)
                val primaryMuscle = info
                    ?.involvedMuscles
                    ?.firstOrNull { it.role == MuscleRole.PRIMARY }
                    ?.let { VolumeCalculator.normalizeMuscleGroup(it.muscle, it.emphasis) }
                    ?: "_desconocido"
                muscleCount[primaryMuscle] = (muscleCount[primaryMuscle] ?: 0) + 1
            }

            val exceeded = muscleCount.entries.firstOrNull { (_, count) -> count > maxExercisesPerMuscle }
            if (exceeded != null) {
                return "Máx $maxExercisesPerMuscle ejercicios por músculo. ${exceeded.key} tiene ${exceeded.value}."
            }
        }

        return null
    }

    fun saveSession(scope: SessionSaveScope = SessionSaveScope.SESSION_ONLY): SessionEditorSaveResult {
        val state = _uiState.value
        val draft = state.session ?: return SessionEditorSaveResult(false, "No hay una sesión activa para guardar.")
        val program = repository.getProgramById(programId) ?: return SessionEditorSaveResult(false, "No pudimos encontrar el programa activo.")
        if (state.weekId.isBlank()) return SessionEditorSaveResult(false, "No pudimos identificar la semana para guardar.")

        val validationError = validateBeforeSave(state, draft)
        if (validationError != null) {
            return SessionEditorSaveResult(false, validationError)
        }
        val pendingSessionSwitchId = state.pendingSessionSwitchId

        val updatedProgram = if (scope == SessionSaveScope.MESOCYCLE) applySessionToMesocycle(program, state, draft) else {
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
                localDraftHistory = listOf(draft),
                pendingSessionSwitchId = null,
            )
        }
        switchToSession(pendingSessionSwitchId ?: draft.id)
        return SessionEditorSaveResult(true, "Sesión guardada")
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

    companion object {
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
}

private data class AugeVolumeAccumulator(
    var flat: Double = 0.0,
    var effective: Double = 0.0,
    var fail: Double = 0.0,
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
)

private fun buildAugeSummary(
    currentSession: Session,
    weekSessions: List<Session>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    settings: Settings,
): SessionEditorAugeSummary {
    val currentMetrics = computeSessionAugeComputation(currentSession, exerciseIndex, settings)
    val weeklySessions = if (weekSessions.any { it.id == currentSession.id }) weekSessions else weekSessions + currentSession
    val weeklyMetrics = weeklySessions.map { computeSessionAugeComputation(it, exerciseIndex, settings) }
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

    currentMetrics.volumeMap.entries
        .sortedByDescending { it.value.effective }
        .forEach { (muscle, data) ->
            if (data.effective <= sessionLimit) return@forEach
            val message = when {
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) >= 0.7 ->
                    "Muchas series cerca del fallo. Seguir sumando volumen en $muscle aporta poco y fatiga mucho."
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) <= 0.3 ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. El retorno ya es decreciente."
                else ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. Conviene recortar un poco el bloque."
            }
            alerts += SessionEditorAugeAlert(
                id = "volume-session-$muscle",
                title = "Volumen alto en $muscle",
                message = message,
                severity = SessionEditorAugeAlertSeverity.WARNING,
                source = SessionEditorAugeAlertSource.SESSION,
                muscle = muscle,
                correctionType = SessionEditorAugeCorrectionType.REDUCE_SERIES,
            )
        }

    val weeklyVolumeMap = mutableMapOf<String, AugeVolumeAccumulator>()
    weeklySessions.forEach { session ->
        accumulateSessionVolume(session, exerciseIndex, weeklyVolumeMap)
    }
    weeklyVolumeMap.entries
        .sortedByDescending { it.value.flat }
        .forEach { (muscle, data) ->
            if (data.flat <= weeklyLimit) return@forEach
            alerts += SessionEditorAugeAlert(
                id = "volume-week-$muscle",
                title = "Semana cargada para $muscle",
                message = "${formatOneDecimal(data.flat)} series equivalentes sobre $weeklyLimit. Redistribuir la semana puede ayudarte a llegar más fresco.",
                severity = SessionEditorAugeAlertSeverity.WARNING,
                source = SessionEditorAugeAlertSource.WEEK,
                muscle = muscle,
                correctionType = SessionEditorAugeCorrectionType.REDUCE_SERIES,
            )
        }

    if (currentMetrics.totalSpinalLoad > 15.0) {
        val topAxialExercise = currentMetrics.exerciseInsights.maxByOrNull { it.spinal }
        alerts += SessionEditorAugeAlert(
            id = "system-spinal",
            title = "Carga axial alta",
            message = "La sesión está acumulando mucha carga espinal. Conviene bajar un poco la densidad o la intensidad del ejercicio más demandante.",
            severity = SessionEditorAugeAlertSeverity.CRITICAL,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topAxialExercise?.exerciseId,
            exerciseName = topAxialExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE,
        )
    }

    if (currentMetrics.drain.cns >= 80 || currentMetrics.averageRpe >= 9.2) {
        val topNeuralExercise = currentMetrics.exerciseInsights.maxByOrNull { it.cns }
        alerts += SessionEditorAugeAlert(
            id = "system-neural",
            title = "Carga neural elevada",
            message = "El SNC ya va alto para esta sesión. Bajar RPE o %1RM en el ejercicio más neurálgico te deja margen sin romper el plan.",
            severity = if (currentMetrics.drain.cns >= 85) SessionEditorAugeAlertSeverity.CRITICAL else SessionEditorAugeAlertSeverity.WARNING,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topNeuralExercise?.exerciseId,
            exerciseName = topNeuralExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_RPE,
        )
    }

    if (currentMetrics.elbowStress > 8) {
        alerts += SessionEditorAugeAlert(
            id = "system-elbow",
            title = "Estrés de codo acumulado",
            message = "Hay bastante trabajo aislado de tríceps en ángulos agresivos. Revisa intensidad o reparte mejor el trabajo accesorio.",
            severity = SessionEditorAugeAlertSeverity.WARNING,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (currentMetrics.kneeStress > 8) {
        alerts += SessionEditorAugeAlert(
            id = "system-knee",
            title = "Fricción patelar alta",
            message = "Extensiones puras o patrones similares se están acumulando. Mejor bajar un poco la densidad o reforzar el calentamiento.",
            severity = SessionEditorAugeAlertSeverity.WARNING,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    val suggestions = mutableListOf<SessionEditorAugeAlert>()
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
            message = "El RPE medio está bajo. Si el objetivo era más estímulo, todavía hay margen para subir un poco la intensidad.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    currentMetrics.exerciseInsights.forEach { insight ->
        val exerciseSets = currentSession.allExercises().find { it.id == insight.exerciseId }?.validAugeSets()?.size ?: 0
        if (exerciseSets in 1..2) {
            suggestions += SessionEditorAugeAlert(
                id = "suggest-margin-series-${insight.exerciseId}",
                title = "Margen de serie",
                message = "${insight.name} solo tiene $exerciseSets serie${if (exerciseSets > 1) "s" else ""}. Considera agregar una más para optimizar el estímulo.",
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.EXERCISE,
                exerciseId = insight.exerciseId,
                exerciseName = insight.name,
            )
        }
    }

    val highRpeOrFailureSets = currentSession.allExercises().flatMap { it.validAugeSets() }.count { set ->
        set.effectiveTargetRpe() >= 9.5 || set.isFailure || set.intensityMode == IntensityMode.FAILURE
    }
    if (highRpeOrFailureSets >= 4) {
        alerts += SessionEditorAugeAlert(
            id = "system-high-failure",
            title = "Al fallo / RPE alto frecuente",
            message = "Hay $highRpeOrFailureSets series cerca del fallo. Considera dejar margen de recuperación bajando RPE o usando RIR más conservadores.",
            severity = SessionEditorAugeAlertSeverity.WARNING,
            source = SessionEditorAugeAlertSource.SYSTEM,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_RPE,
        )
    }

    currentMetrics.volumeMap.entries
        .filter { (_, data) -> data.effective > sessionLimit * 0.55 && data.effective <= sessionLimit }
        .take(3)
        .forEach { (muscle, data) ->
            suggestions += SessionEditorAugeAlert(
                id = "warn-series-excedidas-$muscle",
                title = "Series altas en $muscle",
                message = "${formatOneDecimal(data.effective)}/$sessionLimit pts. Cerca del límite; una serie más y AUGE sugerirá recortar.",
                severity = SessionEditorAugeAlertSeverity.WARNING,
                source = SessionEditorAugeAlertSource.SESSION,
                muscle = muscle,
            )
        }

    currentMetrics.volumeMap.entries
        .sortedBy { it.value.effective }
        .take(2)
        .forEach { (muscle, data) ->
            if (data.effective <= 0.0 || data.effective >= sessionLimit * 0.55) return@forEach
            suggestions += SessionEditorAugeAlert(
                id = "suggest-add-$muscle",
                title = "$muscle con margen",
                message = "${formatOneDecimal(data.effective)}/$sessionLimit pts. Si buscas más estímulo, aún cabe una serie bien colocada.",
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.SESSION,
                muscle = muscle,
                correctionType = SessionEditorAugeCorrectionType.ADD_SERIES,
            )
        }

    val status = computeAugeStatus(currentMetrics.drain, weeklyMetrics.map { it.drain }, currentSession.id, weeklySessions)

    val orderedAlerts = alerts
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<SessionEditorAugeAlert> {
                when (it.severity) {
                    SessionEditorAugeAlertSeverity.CRITICAL -> 3
                    SessionEditorAugeAlertSeverity.WARNING -> 2
                    SessionEditorAugeAlertSeverity.INFO -> 1
                }
            }.thenBy {
                when (it.source) {
                    SessionEditorAugeAlertSource.SYSTEM -> 0
                    SessionEditorAugeAlertSource.SESSION -> 1
                    SessionEditorAugeAlertSource.WEEK -> 2
                    SessionEditorAugeAlertSource.EXERCISE -> 3
                }
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
    )
}

private fun computeSessionAugeComputation(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    settings: Settings,
): SessionAugeComputation {
    val exercises = session.allExercises()
    val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
    val volumeMap = mutableMapOf<String, AugeVolumeAccumulator>()
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

        val metrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, info.equipment)
        var muscular = 0.0
        var cns = 0.0
        var spinal = 0.0
        totalSets += validSets.size

        validSets.forEach { set ->
            val effectiveRpe = set.effectiveTargetRpe()
            rpeSum += effectiveRpe
            rpeCount++
            if (exercise.trainingMode == TrainingMode.PERCENT && set.targetPercentageRM != null) {
                rmSum += set.targetPercentageRM / 100.0
                rmCount++
            }

            val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
            info.involvedMuscles.forEach { muscle ->
                val normalized = VolumeCalculator.normalizeMuscleGroup(muscle.muscle, muscle.emphasis)
                val hyperFactor = HYPERTROPHY_ROLE_MULTIPLIERS[muscle.role] ?: 0.0
                val bucket = volumeMap.getOrPut(normalized) { AugeVolumeAccumulator() }
                bucket.flat += hyperFactor
                bucket.effective += hyperFactor * volumeMultiplier
                if (effectiveRpe >= 9.5) bucket.fail += hyperFactor
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
    val predictedDrain = runCatching {
        AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings)
    }.getOrDefault(PredictedDrain(0, 0, 0))

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
                val normalized = VolumeCalculator.normalizeMuscleGroup(muscle.muscle, muscle.emphasis)
                val hyperFactor = HYPERTROPHY_ROLE_MULTIPLIERS[muscle.role] ?: 0.0
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
): SessionEditorAugeStatus {
    val baseSessionsPerWeek = 5
    val currentCombined = currentDrain.combinedDrain()
    val otherCombined = weeklyDrains.zip(weeklySessions)
        .filter { (_, session) -> session.id != currentSessionId }
        .sumOf { (drain, _) -> drain.combinedDrain() }
    val otherSessionsCount = weeklySessions.count { it.id != currentSessionId }
    val remainingQuota = (100.0 - otherCombined).coerceAtLeast(0.0)
    val slotsLeft = (baseSessionsPerWeek - otherSessionsCount).coerceAtLeast(1)
    val recommendedPerSession = if (weeklySessions.size <= 1) 100.0 / baseSessionsPerWeek else remainingQuota / slotsLeft

    return when {
        currentCombined <= recommendedPerSession * 0.95 -> SessionEditorAugeStatus.OPTIMAL
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
    spinal > 60.0 -> "Fatiga espinal alta. Bajar intensidad o usar una variante más estable puede ayudarte."
    cns > 70.0 -> "Carga neural elevada. Reducir RPE o %1RM deja más margen para el resto de la sesión."
    muscular > 80.0 -> "Volumen muscular alto. Una serie menos probablemente mantiene el estímulo."
    else -> null
}

private fun resolveExerciseInfo(
    exercise: Exercise,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    return exercise.exerciseDbId?.let(exerciseIndex::get)
        ?: exercise.exerciseId?.let(exerciseIndex::get)
        ?: exerciseIndex.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
}

private fun resolvePrimaryMuscle(info: ExerciseMuscleInfo): String? {
    val primary = info.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }
        ?: info.involvedMuscles.firstOrNull()
        ?: return null
    return VolumeCalculator.normalizeMuscleGroup(primary.muscle, primary.emphasis)
}

private fun defaultSessionVolumeLimit(settings: Settings): Int {
    val base = if (settings.calorieGoalObjective == CalorieGoalObjective.DEFICIT) 4.8 else 6.0
    return base.roundToInt().coerceAtLeast(4)
}

private fun defaultWeeklyVolumeLimit(settings: Settings): Int = when (settings.calorieGoalObjective) {
    CalorieGoalObjective.DEFICIT -> 16
    CalorieGoalObjective.MAINTENANCE -> 18
    CalorieGoalObjective.SURPLUS -> 20
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

private fun Session.transferableParts(): List<SessionPart> {
    if (parts.isNotEmpty()) return parts
    return if (exercises.isEmpty()) emptyList() else listOf(
        SessionPart(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Bloque importado" },
            exercises = exercises,
            color = PART_COLORS.firstOrNull(),
        )
    )
}

private fun clonePartForTransfer(part: SessionPart): SessionPart {
    val supersetIds = part.exercises.mapNotNull { it.supersetId }.distinct().associateWith { UUID.randomUUID().toString() }
    return part.copy(
        id = UUID.randomUUID().toString(),
        exercises = part.exercises.map { cloneExerciseForTransfer(it, supersetIds) },
    )
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

private fun normalizeTransferredParts(parts: List<SessionPart>): List<SessionPart> =
    parts

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
private const val DEFAULT_GRADIENT_EMBER = "gradient://ember"
private val DEFAULT_SESSION_GRADIENTS = listOf("gradient://ember", "gradient://lagoon", "gradient://velvet", "gradient://forest")
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
    name = "Nueva Sesión",
    description = "",
    parts = emptyList(),
    dayOfWeek = dayOfWeek,
    background = SessionBackground(
        type = SessionBackgroundType.COLOR,
        value = DEFAULT_GRADIENT_EMBER,
        style = SessionBackgroundStyle(blur = 0f, brightness = 0.92f),
    ),
    coverStyle = CoverStyle(filters = CoverFilters(), labelPosition = LabelPosition.BOTTOM_LEFT),
    isMainSession = true,
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
    val normalizedBackground = background ?: SessionBackground(SessionBackgroundType.COLOR, DEFAULT_GRADIENT_EMBER, SessionBackgroundStyle(0f, 0.92f))
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
    val resolved1rm = when {
        reference1RM != null -> reference1RM
        prFor1RM != null -> calculateHybrid1RM(prFor1RM.weight, prFor1RM.reps)
        else -> null
    }
    return copy(restTime = restTime ?: suggestRestSeconds(normalizedSets.size, normalizedSets.mapNotNull { it.targetRPE }.averageOrNull() ?: 8.0), reference1RM = resolved1rm, sets = normalizedSets)
}

private fun ExerciseSet.normalizeSet(exercise: Exercise): ExerciseSet {
    val normalized = copy(intensityMode = intensityMode ?: IntensityMode.RPE)
    val autoWeight = if (exercise.trainingMode == TrainingMode.PERCENT && exercise.reference1RM != null) {
        calculateWeightFrom1RMAndIntensity(exercise.reference1RM, normalized)
    } else normalized.weight
    return normalized.copy(weight = autoWeight ?: normalized.weight)
}

private fun createNextSetTemplate(exercise: Exercise, template: ExerciseSet): ExerciseSet {
    val base = template.copy(id = UUID.randomUUID().toString())
    // En modo RM se estima reducción de carga por fatiga; el resto mantiene la intensidad elegida
    if (exercise.trainingMode != TrainingMode.PERCENT) return base
    if ((template.intensityMode ?: IntensityMode.RPE) != IntensityMode.SOLO_RM) return base

    val reps = template.targetReps ?: 1
    val fatigueDrop = when {
        reps >= 10 -> 5.0
        reps >= 6  -> 4.0
        reps >= 3  -> 3.0
        else       -> 2.0
    }
    return base.copy(
        targetPercentageRM = ((template.targetPercentageRM ?: 100.0) - fatigueDrop).coerceAtLeast(45.0),
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
