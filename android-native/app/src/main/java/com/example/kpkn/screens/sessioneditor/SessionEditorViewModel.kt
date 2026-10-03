package com.example.kpkn.screens.sessioneditor

import android.app.Application
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
import com.example.kpkn.domain.exercises.catalogv2.SessionCatalogNameReconciler
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.normalizeEditorScheduledTechniques
import kotlinx.coroutines.CancellationException
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
    val pendingTransferToDays: PendingTransferToDays? = null,
    /** Null marks a pre-split legacy draft; new drafts pin their committed editor-pref baseline. */
    val committedPartRuleDefaults: Map<String, SessionEditorRuleDefaults>? = null,
    val committedRuleLimits: SessionEditorRuleLimits? = null,
    /**
     * Complete committed global defaults (Room's core + confirmed extras) at the time
     * the draft was written. Null marks a draft from before the global extras were
     * versioned: its [ruleDefaults] win as a whole, as they always did.
     */
    val committedRuleBaseline: SessionEditorCommittedRuleBaseline? = null,
)

private sealed interface SessionEditorLoadResult {
    data class Loaded(val state: SessionEditorUiState) : SessionEditorLoadResult
    data class Failed(val message: String) : SessionEditorLoadResult
    data object WaitingForRepository : SessionEditorLoadResult
}


/** A session is on screen and no load failure is showing: no load result may replace it. */
private fun SessionEditorUiState.isLoadedWithoutFailure(): Boolean =
    session != null && loadErrorMessage == null

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
    internal val trainedVersionStore = TrainedSessionVersionStore.getInstance(application)

    /** Combined (system + user) template list, updated reactively. */
    val allTemplates: StateFlow<List<SessionTemplate>> = templateRepository.allTemplates
    /** User-owned templates, including archived entries for explicit management. */
    val userTemplates: StateFlow<List<SessionTemplate>> = templateRepository.userTemplates
    val templatesReady: StateFlow<Boolean> = templateRepository.isReady
    val corruptTemplateIds: StateFlow<List<String>> = templateRepository.corruptTemplateIds
    internal val exerciseIndex: Map<String, ExerciseMuscleInfo>
        get() = catalogExerciseIndex()
    private var augeJob: Job? = null
    private var autoSaveJob: Job? = null
    @Volatile
    private var loadSessionJob: Job? = null
    /** One template command at a time; prevents stale async REPLACE overwrites. */
    internal var templateApplyJob: Job? = null
    private var textHistoryDebounceJob: Job? = null
    private var textHistoryBaseline: Session? = null
    @Volatile
    internal var activeDurableSaveKey: String? = null
    internal var sessionSwitchGeneration: Long = 0L
    private var loadSessionGeneration: Long = 0L
    private val draftStore by lazy { SessionEditorDraftStore.getInstance(getApplication()) }

    /**
     * [roomSession] is the session exactly as Room holds it (never a newer copy from
     * the draft): the eight core defaults always come from it, only the extras come
     * from the preference record (see [resolveGlobalRuleDefaults]).
     */
    internal suspend fun loadEditorRulePreferencesFor(
        sessionId: String,
        persistedDraft: PersistedSessionEditorDraft?,
        roomSession: Session?,
    ): ResolvedSessionEditorRulePreferences {
        val key = sessionEditorRulePreferencesStorageKey(programId, sessionId)
        val storedRead = draftStore.readRulePreferencesResult(key)
        val stored = (storedRead as? RulePreferencesRead.Present)?.value
        val savedBaselineInDraft = persistedDraft?.let { draft ->
            val committedParts = draft.committedPartRuleDefaults
            val committedLimits = draft.committedRuleLimits
            if (committedParts != null && committedLimits != null) {
                SessionEditorRulePreferences(committedParts, committedLimits)
            } else {
                null
            }
        }
        val legacyBaseline = persistedDraft?.takeIf { savedBaselineInDraft == null }?.let { draft ->
            // Older builds only stored current per-part settings and limits in the recovery draft.
            // They had no commit boundary for these editor-only controls, so preserve them as the
            // installed baseline while migrating to the separate preference store.
            SessionEditorRulePreferences(
                partRuleDefaults = draft.partRuleDefaults,
                ruleLimits = draft.ruleLimits,
            )
        }
        val global = resolveGlobalRuleDefaults(roomSession, stored, persistedDraft)
        val committed = (stored ?: savedBaselineInDraft ?: legacyBaseline ?: SessionEditorRulePreferences())
            .copy(globalRuleExtras = global.savedExtras)
        // Only an ABSENT record is migrated from the draft. An unreadable one is left
        // untouched: it may belong to a newer build, and overwriting it would destroy it.
        if (storedRead == RulePreferencesRead.Absent && persistedDraft != null) {
            runCatching { draftStore.writeRulePreferences(key, committed) }
        }
        val current = persistedDraft?.let { draft ->
            SessionEditorRulePreferences(
                partRuleDefaults = draft.partRuleDefaults,
                ruleLimits = draft.ruleLimits,
                globalRuleExtras = global.current.extras(),
            )
        } ?: committed
        return ResolvedSessionEditorRulePreferences(current = current, committed = committed, global = global)
    }

    /**
     * Discard rewrites the preference record with the committed baseline only when the
     * stored record differs from it. When it already matches, no disk commit happens
     * and no write revision is claimed (a claimed revision would invalidate an
     * in-flight save of the same session). Returns false when a needed write failed.
     */
    internal suspend fun preserveEditorRuleBaselineForDiscard(
        sessionId: String,
        baseline: SessionEditorRulePreferences,
    ): Boolean {
        val key = sessionEditorRulePreferencesStorageKey(programId, sessionId)
        val stored = try {
            draftStore.readRulePreferencesResult(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            RulePreferencesRead.Unreadable
        }
        if (!discardMustWriteRulePreferences(stored, baseline)) return true
        val revision = nextEditorRulePreferencesWriteRevision(sessionId)
        return try {
            persistEditorRulePreferences(sessionId, baseline, revision = revision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Best-effort fan-out of the global extras to the clones a MESOCYCLE save wrote.
     * Their core defaults already travel inside the cloned `Session`; the extras live
     * in per-session preference records, so they are copied here. Only the extras are
     * replaced (a clone's own parts/limits stay), an unreadable record is left alone and
     * a record that already matches is not rewritten. Failures are ignored on purpose:
     * the clones keep default extras and the committed content is unaffected.
     */
    internal suspend fun propagateGlobalRuleExtrasToClones(
        cloneSessionIds: Collection<String>,
        extras: SessionEditorGlobalRuleExtras,
    ): Int {
        var propagated = 0
        for (cloneId in cloneSessionIds) {
            try {
                val key = sessionEditorRulePreferencesStorageKey(programId, cloneId)
                val existing: SessionEditorRulePreferences? = when (val read = draftStore.readRulePreferencesResult(key)) {
                    is RulePreferencesRead.Present -> read.value
                    RulePreferencesRead.Absent -> SessionEditorRulePreferences()
                    RulePreferencesRead.Unreadable -> null
                }
                if (existing == null) continue
                val confirmed = (existing.globalRuleExtras ?: SessionEditorGlobalRuleExtras()).atCurrentVersion()
                if (confirmed == extras.atCurrentVersion()) continue
                if (draftStore.writeRulePreferences(key, existing.copy(globalRuleExtras = extras))) propagated++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Best effort only.
            }
        }
        return propagated
    }

    internal suspend fun persistEditorRulePreferences(
        sessionId: String,
        preferences: SessionEditorRulePreferences,
        revision: Long? = null,
    ): Boolean = draftStore.writeRulePreferences(
        key = sessionEditorRulePreferencesStorageKey(programId, sessionId),
        value = preferences,
        revision = revision,
    )

    internal fun nextEditorRulePreferencesWriteRevision(sessionId: String): Long =
        draftStore.nextRulePreferencesWriteRevision(sessionEditorRulePreferencesStorageKey(programId, sessionId))

    internal fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        val state = _uiState.value
        val payload = createPersistedDraft(state) ?: return
        val (key, draft) = payload
        val generation = draftStore.writer.invalidate(key)
        autoSaveJob = viewModelScope.launch {
            delay(2000)
            if (!_uiState.value.autoSaveEnabled) return@launch
            val outcome = draftStore.writer.enqueueWrite(key, generation, draft).await()
            if (outcome.status == DraftWriteStatus.FAILED && isCurrentDraftKey(key)) {
                updateUi { it.copy(snackbarMessage = "No se pudo guardar el borrador. Vuelve a intentarlo.") }
            }
        }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update { it.copy(autoSaveEnabled = enabled) }
            if (enabled) {
                scheduleAutoSave()
            } else {
                invalidateDraftWritesFor(_uiState.value)
            }
        }
        viewModelScope.launch(Dispatchers.IO) { draftStore.setAutoSaveEnabled(enabled) }
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

    internal fun Session.contentHashForAuge(): Int {
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

    internal val draftJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(SessionEditorUiState(programId = programId))
    val uiState: StateFlow<SessionEditorUiState> = _uiState.asStateFlow()

    /** Drag-and-drop controller owned by the ViewModel (hallazgo I — not Compose `remember`). */
    val dragController = SessionEditorDragController()

    private val _dragUiState = MutableStateFlow(SessionEditorDragUiState())
    val dragUiState: StateFlow<SessionEditorDragUiState> = _dragUiState.asStateFlow()

    /** Visible to [SessionEditorViewModelDrag] publish helpers. */
    internal val dragUiStateMutable: MutableStateFlow<SessionEditorDragUiState>
        get() = _dragUiState

    /** Read-only snapshot for same-package ViewModel extensions. */
    internal val currentUiState: SessionEditorUiState
        get() = _uiState.value

    init {
        bindDragControllerListener()
    }

    internal fun updateUi(transform: (SessionEditorUiState) -> SessionEditorUiState) {
        _uiState.update { current ->
            val next = transform(current)
            next.copy(hasUnsavedChanges = next.hasMeaningfulDraftChanges())
        }
    }

    internal fun replaceUiState(state: SessionEditorUiState) {
        _uiState.value = state.copy(hasUnsavedChanges = state.hasMeaningfulDraftChanges())
    }

    internal fun draftStorageKey(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        sessionId: String,
    ): String = sessionEditorDraftStorageKey(
        programId = programId,
        weekId = weekId,
        macroIndex = macroIndex,
        mesoIndex = mesoIndex,
        sessionId = sessionId,
    )

    internal suspend fun persistedDraftFor(
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
        val decoded = withContext(Dispatchers.IO) {
            val raw = draftStore.readRaw(key) ?: return@withContext null
            runCatching { draftJson.decodeFromString<PersistedSessionEditorDraft>(raw) }.getOrNull()
        } ?: return null
        return decoded.takeIf {
            it.programId == programId &&
                it.sessionId == sessionId &&
                it.weekId == weekId &&
                it.macroIndex == macroIndex &&
                it.mesoIndex == mesoIndex
        }
    }

    private fun createPersistedDraft(state: SessionEditorUiState): Pair<String, PersistedSessionEditorDraft>? {
        val session = state.session ?: return null
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
            pendingTransferToDays = state.pendingTransferToDays,
            committedPartRuleDefaults = state.savedPartRuleDefaults,
            committedRuleLimits = state.savedRuleLimits,
            // Built here, from an immutable state snapshot, never while encoding in IO.
            committedRuleBaseline = SessionEditorCommittedRuleBaseline(ruleDefaults = state.committedRuleDefaults()),
        )
        val key = draftStorageKey(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = session.id,
        )
        return key to payload
    }

    private fun isCurrentDraftKey(key: String): Boolean {
        val state = _uiState.value
        val session = state.session ?: return false
        return key == draftStorageKey(state.weekId, state.macroIndex, state.mesoIndex, session.id)
    }

    internal fun persistDraft(state: SessionEditorUiState = _uiState.value): Boolean {
        val (key, payload) = createPersistedDraft(state) ?: return false
        val write = draftStore.writer.enqueueLatestWrite(key, payload)
        viewModelScope.launch {
            val outcome = write.await()
            if (outcome.status == DraftWriteStatus.FAILED && isCurrentDraftKey(key)) {
                updateUi { it.copy(snackbarMessage = "No se pudo guardar el borrador. Vuelve a intentarlo.") }
            }
        }
        return true
    }

    internal fun invalidateDraftWritesFor(state: SessionEditorUiState) {
        autoSaveJob?.cancel()
        val session = state.session ?: return
        val key = draftStorageKey(state.weekId, state.macroIndex, state.mesoIndex, session.id)
        draftStore.writer.invalidate(key)
    }

    internal suspend fun persistRecoverableSession(state: SessionEditorUiState = _uiState.value): Boolean {
        val (key, payload) = createPersistedDraft(state) ?: return false
        return draftStore.writer.writeLatest(key, payload).status == DraftWriteStatus.WRITTEN
    }

    internal suspend fun clearPersistedDraft(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        sessionId: String,
    ): Boolean {
        val key = draftStorageKey(
            weekId = weekId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            sessionId = sessionId,
        )
        return draftStore.writer.clear(key).status == DraftWriteStatus.WRITTEN
    }

    internal suspend fun clearPersistedDraftAtGeneration(key: String, generation: Long): DraftWriteOutcome =
        draftStore.writer.clearAtGeneration(key, generation)

    internal fun draftGeneration(key: String): Long = draftStore.writer.currentGeneration(key)

    internal suspend fun persistRecoverableSessionAtGeneration(
        state: SessionEditorUiState,
        key: String,
        generation: Long,
    ): DraftWriteOutcome {
        val payload = createPersistedDraft(state) ?: return DraftWriteOutcome(DraftWriteStatus.FAILED)
        if (payload.first != key) return DraftWriteOutcome(DraftWriteStatus.STALE)
        return draftStore.writer.enqueueWrite(key, generation, payload.second).await()
    }

    fun saveDraftForExit() {
        val state = _uiState.value
        // A clean lifecycle transition must not recreate the draft that Save or
        // Discard just removed. Explicit editor-only draft writes already use
        // persistDraft and continue in the application-lifetime writer.
        if (!state.hasUnsavedChanges) return
        val (key, payload) = createPersistedDraft(state) ?: return
        if (key == activeDurableSaveKey) return
        autoSaveJob?.cancel()
        val generation = draftStore.writer.invalidate(key)
        val write = draftStore.writer.enqueueWrite(key, generation, payload)
        viewModelScope.launch {
            val outcome = write.await()
            if (outcome.status == DraftWriteStatus.FAILED && isCurrentDraftKey(key)) {
                updateUi { it.copy(snackbarMessage = "No se pudo guardar el borrador. Vuelve a intentarlo.") }
            }
        }
    }

    suspend fun saveDraftForExitAndAwait(): Boolean {
        autoSaveJob?.cancel()
        val state = _uiState.value
        if (!state.hasUnsavedChanges) {
            val session = state.session ?: return true
            val key = draftStorageKey(state.weekId, state.macroIndex, state.mesoIndex, session.id)
            val latest = draftStore.writer.awaitLatest(key)
            if (latest?.status == DraftWriteStatus.FAILED) {
                val retried = persistRecoverableSession(state)
                if (!retried) {
                    updateUi { it.copy(snackbarMessage = "No se pudo guardar el borrador. Vuelve a intentarlo.") }
                }
                return retried
            }
            return true
        }
        val saved = persistRecoverableSession(state)
        if (!saved) {
            updateUi { it.copy(snackbarMessage = "No se pudo guardar el borrador. Vuelve a intentarlo.") }
        }
        return saved
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        val state = _uiState.value
        if (state.hasUnsavedChanges) createPersistedDraft(state)?.let { (key, payload) ->
            if (key == activeDurableSaveKey) return@let
            draftStore.writer.enqueueLatestWrite(key, payload)
        }
        super.onCleared()
    }

    /**
     * Restarts the load only when there is something to recover: nothing on screen yet, or a
     * failure showing. With a session already loaded and no failure, a restarted load could
     * only race the live editor, and its result would be discarded by [publishLoadedState]
     * anyway.
     */
    fun retryLoadSession() {
        if (_uiState.value.isLoadedWithoutFailure()) return
        // Over an empty editor the failure is cleared so the screen shows progress; over a
        // loaded one it stays until the new result replaces it.
        _uiState.update { if (it.session == null) it.copy(loadErrorMessage = null) else it }
        loadSession()
    }

    fun discardDraftForCurrentSession() {
        viewModelScope.launch {
            if (!discardDraftForCurrentSessionAndAwait()) {
                updateUi { it.copy(snackbarMessage = "No se pudo descartar el borrador. Vuelve a intentarlo.") }
            }
        }
    }

    suspend fun discardDraftForCurrentSessionAndAwait(): Boolean {
        val state = _uiState.value
        val session = state.session ?: return false
        val preferencesPreserved = preserveEditorRuleBaselineForDiscard(session.id, state.toSavedRulePreferences())
        if (!preferencesPreserved) {
            updateUi { it.copy(snackbarMessage = "No se pudieron preservar las preferencias del editor; el borrador sigue disponible.") }
            return false
        }
        val cleared = clearPersistedDraft(
            weekId = state.weekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = session.id,
        )
        if (!cleared) {
            updateUi { it.copy(snackbarMessage = "No se pudo descartar el borrador. Vuelve a intentarlo.") }
            return false
        }
        val restored = state.originalSession
        _uiState.update {
            it.copy(
                session = restored,
                pendingTransferToDays = null,
                hasUnsavedChanges = false,
                ruleDefaults = state.committedRuleDefaults(),
                partRuleDefaults = state.savedPartRuleDefaults,
                ruleLimits = state.savedRuleLimits,
                activeVariant = WeekVariant.A,
                availableVariants = restored?.let(::computeAvailableVariants) ?: listOf(WeekVariant.A),
            )
        }
        textHistoryBaseline = null
        textHistoryDebounceJob?.cancel()
        return true
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
        if (_uiState.value.session?.id?.let { it != sessionId } == true) return
        loadSessionJob?.cancel()
        val loadGeneration = ++loadSessionGeneration
        val switchGeneration = sessionSwitchGeneration
        loadSessionJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                loadSessionInternal()
            }
            if (loadGeneration != loadSessionGeneration || switchGeneration != sessionSwitchGeneration) {
                return@launch
            }
            if (_uiState.value.session?.id?.let { it != sessionId } == true) return@launch
            when (result) {
                is SessionEditorLoadResult.Loaded -> {
                    if (!publishLoadedState(result.state)) return@launch
                    refreshDerivedStateImmediate()
                    loadHistory()
                }
                is SessionEditorLoadResult.Failed -> updateUi { current ->
                    // A late failure must not push an already loaded editor onto its error screen.
                    if (current.isLoadedWithoutFailure()) current else current.copy(loadErrorMessage = result.message)
                }
                SessionEditorLoadResult.WaitingForRepository -> Unit
            }
        }
    }

    /**
     * A load only ever fills an editor that has nothing on screen (no session yet) or that is
     * showing a load failure; it never replaces a session that is already loaded. Redundant
     * reloads (a retry racing the first load, a repository emission, a resume) would otherwise
     * reset the active variant, the rule defaults and any unsaved edit to the persisted
     * snapshot. The check and the swap are one atomic update, so the rule holds whichever
     * thread the load resumes on. Returns false when the result was discarded.
     */
    private fun publishLoadedState(loaded: SessionEditorUiState): Boolean {
        val next = loaded.copy(hasUnsavedChanges = loaded.hasMeaningfulDraftChanges())
        var published = false
        _uiState.update { current ->
            published = !current.isLoadedWithoutFailure()
            if (published) next else current
        }
        return published
    }

    /**
     * Suspends until the most recently started session load has published its result or been
     * discarded. A caller that needs a settled editor joins the load that is really in flight
     * instead of polling [uiState] and starting another one.
     */
    internal suspend fun awaitSessionLoadSettled() {
        loadSessionJob?.join()
    }

    internal fun invalidateInitialSessionLoadForSwitch() {
        loadSessionGeneration++
        loadSessionJob?.cancel()
    }

    private suspend fun loadSessionInternal(): SessionEditorLoadResult {
        val program = repository.getProgramById(programId)
        if (program == null) {
            if (repository.isReady.value) {
                Log.w("SessionEditor", "Program not found. programId=$programId sessionId=$sessionId")
                return SessionEditorLoadResult.Failed("No pudimos recuperar este programa.")
            }
            return SessionEditorLoadResult.WaitingForRepository
        }
        val located = locateSession(program, sessionId, draftWeekId, draftMacroIndex, draftMesoIndex)
        val targetWeekId = located?.week?.id ?: draftWeekId.orEmpty()
        val targetMacroIndex = draftMacroIndex ?: located?.macroIndex ?: 0
        val targetMesoIndex = draftMesoIndex ?: located?.mesoIndex ?: 0
        val week = located?.week ?: findWeek(program, targetMacroIndex, targetMesoIndex, targetWeekId)
        if (week == null && targetWeekId.isNotBlank() && repository.isReady.value) {
            Log.w("SessionEditor", "Week not found. programId=$programId weekId=$targetWeekId sessionId=$sessionId")
            return SessionEditorLoadResult.Failed("No pudimos recuperar la semana de esta sesión.")
        }
        val existing = located?.session
        val fallbackDraft = existing ?: createDraftSession(sessionId, draftDayOfWeek)
        val savedSession = existing ?: fallbackDraft
        val persistedDraft = persistedDraftFor(
            weekId = targetWeekId,
            macroIndex = targetMacroIndex,
            mesoIndex = targetMesoIndex,
            sessionId = fallbackDraft.id,
        )
        // The editor's baseline (what "unchanged" means) is Room's session after the
        // same normalization the dirty check uses; the core global defaults come from it.
        val originalSession = SupersetRules.normalizeSession(
            savedSession
                .normalizeEditorScheduledTechniques()
                .normalizeMobilityCompatibility()
                .normalizedIdentityFields(),
        )
        val editorRulePreferences = loadEditorRulePreferencesFor(fallbackDraft.id, persistedDraft, originalSession)
        // D3: los programas históricos pueden traer espejo suelto+grupo; el
        // colapso por-id corre al abrir (nombres se reconcilian al guardar).
        val draft = SupersetRules.normalizeSession(
            SessionCatalogNameReconciler.normalizeSessionStructure(
                resolveNewestSession(existing, fallbackDraft, persistedDraft)
                    .normalizeEditorScheduledTechniques()
                    .normalizeMobilityCompatibility()
                    .normalizedIdentityFields(),
            ),
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
        val resolvedRuleDefaults = editorRulePreferences.global.current
        val resolvedPartRuleDefaults = editorRulePreferences.current.partRuleDefaults
        val resolvedRuleLimits = editorRulePreferences.current.ruleLimits
        val roadmapOptions = buildRoadmapOptions(program)
        val cloneDayOptions = buildCloneDayOptions(program, currentSessionId = draft.id)
        val cloneSourceOptions = buildCloneSourceOptions(program, currentSessionId = draft.id)
        val localDraftHistory = runCatching { trainedVersionStore.loadForSession(draft.id) }
            .onFailure { error -> Log.w("SessionEditor", "Unable to load trained session history", error) }
            .getOrDefault(emptyList())

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

        return SessionEditorLoadResult.Loaded(SessionEditorUiState(
            programSnapshotForVolume = program,
            session = draft,
            originalSession = originalSession,
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
            localDraftHistory = localDraftHistory,
            ruleDefaults = resolvedRuleDefaults,
            partRuleDefaults = resolvedPartRuleDefaults,
            ruleLimits = resolvedRuleLimits,
            savedPartRuleDefaults = editorRulePreferences.committed.partRuleDefaults,
            savedRuleLimits = editorRulePreferences.committed.ruleLimits,
            savedRuleExtras = editorRulePreferences.global.savedExtras,
            ruleTemplates = ruleTemplateStore.loadAll(),
            autoSaveEnabled = draftStore.autoSaveEnabled(),
            isSimpleProgram = program.isSimpleTemporalProgram,
            protocolLabel = program.sourceProtocolId?.let { id ->
                com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.firstOrNull { it.id == id }
                    ?.let { "${it.emoji} ${it.name}" }
            },
            hasActiveLoops = program.loops.isNotEmpty() && program.loopState != null,
            latestBodyMeasurement = latestBodyMeasurementOrNull(),
            allProgramExerciseCandidates = allProgramExerciseCandidates,
            competitionMovementIds = competitionMovementIds,
            competitionKeyDaysInWeek = competitionKeyDaysInWeek,
            selectedExercisesIds = persistedDraft?.selectedExercisesIds.orEmpty(),
            availableVariants = computeAvailableVariants(draft),
            activeVariant = WeekVariant.A,
            pendingTransferToDays = persistedDraft?.pendingTransferToDays,
        ))
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
        val currentSession = _uiState.value.activeVariantSession ?: _uiState.value.session ?: return
        viewModelScope.launch {
            val logs = repository.getLogsForSession(currentSession.id).sortedByDescending { it.date }
            val programLogs = repository.getLogsForProgram(_uiState.value.programId)
            val cardioHistory = com.example.kpkn.data.models.CardioType.entries.associateWith { type ->
                com.example.kpkn.domain.cardio.CardioHistoryStats.forType(programLogs, type)
            }
            val feedbackByLogId = logs.mapNotNull { log ->
                augeRepository.getFeedbackForLog(log.id)?.let { log.id to it }
            }.toMap()
            _uiState.update { it.copy(workoutLogs = logs, cardioHistoryByType = cardioHistory, feedbackByLogId = feedbackByLogId) }
        }
    }

    internal fun updateSession(reason: String = "Edición", transform: (Session) -> Session) {
        val state = _uiState.value
        val variant = state.activeVariant
        if (variant == WeekVariant.A) {
            val current = state.session ?: return
            val transformed = transform(current).normalizeSession()
            if (transformed == current) return
            val updated = transformed.copy(lastModifiedAtMs = System.currentTimeMillis())
            updateUi { s ->
                s.copy(
                    session = updated,
                    dayOfWeek = updated.dayOfWeek ?: s.dayOfWeek,
                )
            }
        } else {
            val currentVariant = state.activeVariantSession ?: return
            val transformedVariant = transform(currentVariant).normalizeSession()
            if (transformedVariant == currentVariant) return
            val updatedVariant = transformedVariant.copy(lastModifiedAtMs = System.currentTimeMillis())
            val base = state.session ?: return
            val updatedBase = when (variant) {
                WeekVariant.B -> base.copy(sessionB = updatedVariant)
                WeekVariant.C -> base.copy(sessionC = updatedVariant)
                WeekVariant.D -> base.copy(sessionD = updatedVariant)
                else -> base
            }
            updateUi { s ->
                s.copy(
                    session = updatedBase,
                    dayOfWeek = updatedVariant.dayOfWeek ?: s.dayOfWeek,
                )
            }
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
                val session = state.activeVariantSession ?: state.session ?: return@withContext
                recalcAndPushAuge(state, session)
            }
        }
    }

    internal fun refreshDerivedStateImmediate() {
        augeJob?.cancel()
        augeJob = null
        val state = _uiState.value
        val session = state.activeVariantSession ?: state.session ?: return
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
                globalMobilitySeries = session.parts
                    .filter { it.isMobilityGroup }
                    .flatMap { it.mobilitySeries },
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
                val sess = s.activeVariantSession ?: s.session ?: return@launch
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
        val session = state.activeVariantSession ?: state.session ?: return
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
        val state = _uiState.value
        val variant = state.activeVariant
        if (variant == WeekVariant.A) {
            val current = state.session ?: return
            val updated = transform(current)
            if (updated == current) return
            updateUi { s ->
                s.copy(
                    session = updated,
                    dayOfWeek = updated.dayOfWeek ?: s.dayOfWeek,
                )
            }
        } else {
            val currentVariant = state.activeVariantSession ?: return
            val updatedVariant = transform(currentVariant)
            if (updatedVariant == currentVariant) return
            val base = state.session ?: return
            val updatedBase = when (variant) {
                WeekVariant.B -> base.copy(sessionB = updatedVariant)
                WeekVariant.C -> base.copy(sessionC = updatedVariant)
                WeekVariant.D -> base.copy(sessionD = updatedVariant)
                else -> base
            }
            updateUi { s ->
                s.copy(
                    session = updatedBase,
                    dayOfWeek = updatedVariant.dayOfWeek ?: s.dayOfWeek,
                )
            }
        }
        scheduleAutoSave()
        textHistoryDebounceJob?.cancel()
        textHistoryDebounceJob = viewModelScope.launch {
            delay(800)
            textHistoryBaseline = null
        }
    }
    fun updateSessionMeetDay(isMeetDay: Boolean) {
        val current = _uiState.value.activeVariantSession ?: _uiState.value.session ?: return
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
            val session = state.activeVariantSession ?: state.session ?: return@launch
            val settingsForBreakdown = repository.settings.value
            val breakdown = state.sessionTimeBreakdown ?: runCatching {
calculateSessionTimeBreakdown(
                    exercises = session.allExercises(),
                    supersetGroups = session.allSupersetGroups(),
                    sessionWarmup = session.warmup,
                    globalMobilitySeries = session.parts
                        .filter { it.isMobilityGroup }
                        .flatMap { it.mobilitySeries },
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
        updateUi { state ->
            val restoredSession = snapshot.session
            state.copy(
                session = restoredSession,
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
        val sessionId = _uiState.value.activeVariantSession?.id ?: _uiState.value.session?.id ?: return
        viewModelScope.launch {
            val history = runCatching { trainedVersionStore.loadForSessionOnIo(sessionId) }
                .onFailure { error -> Log.w("SessionEditor", "Unable to load trained session history", error) }
                .getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                val currentId = _uiState.value.activeVariantSession?.id ?: _uiState.value.session?.id
                if (currentId == sessionId) {
                    _uiState.update { it.copy(localDraftHistory = history) }
                }
            }
        }
    }

}
