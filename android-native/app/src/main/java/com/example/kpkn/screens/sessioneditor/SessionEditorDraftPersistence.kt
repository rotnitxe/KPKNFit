package com.example.kpkn.screens.sessioneditor

import android.content.Context
import android.content.SharedPreferences
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.normalizeMobilityCompatibility
import com.example.kpkn.domain.workout.normalizeEditorScheduledTechniques
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal enum class DraftWriteStatus {
    WRITTEN,
    STALE,
    FAILED,
}

internal data class DraftWriteOutcome(
    val status: DraftWriteStatus,
    val failure: Throwable? = null,
)

/**
 * Editor-only preferences of one session, committed together by a single
 * `commit()` so parts, limits and global extras can never be half-written.
 *
 * [globalRuleExtras] == null marks a v1 record (written before the extras
 * existed); any record written by this build carries a non-null value, even when
 * every extra is at its default. The eight core fields are never stored here:
 * Room's `Session.persistedRuleDefaults` stays their only authority.
 */
@kotlinx.serialization.Serializable
internal data class SessionEditorRulePreferences(
    val partRuleDefaults: Map<String, SessionEditorRuleDefaults> = emptyMap(),
    val ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
    val globalRuleExtras: SessionEditorGlobalRuleExtras? = null,
)

/**
 * Complete, versioned snapshot of the global defaults that were committed when a
 * draft was written: Room's core at that moment plus the confirmed extras. It is
 * the common ancestor of the core three-way merge in [resolveGlobalRuleDefaults].
 */
@kotlinx.serialization.Serializable
internal data class SessionEditorCommittedRuleBaseline(
    val version: Int = 1,
    val ruleDefaults: SessionEditorRuleDefaults,
)

internal data class ResolvedGlobalRuleDefaults(
    /** What the form shows: Room's core (merged with any pending draft edit) plus the extras. */
    val current: SessionEditorRuleDefaults,
    /** Extras confirmed in the preference store; dirtiness of the extras is judged against it. */
    val savedExtras: SessionEditorGlobalRuleExtras,
)

internal data class ResolvedSessionEditorRulePreferences(
    val current: SessionEditorRulePreferences,
    val committed: SessionEditorRulePreferences,
    val global: ResolvedGlobalRuleDefaults,
)

/** An unreadable record is not the same as an absent one: it must not be overwritten implicitly. */
internal sealed interface RulePreferencesRead {
    data object Absent : RulePreferencesRead
    data object Unreadable : RulePreferencesRead
    data class Present(val value: SessionEditorRulePreferences) : RulePreferencesRead
}

/**
 * Tolerant codec for the preference record: unknown keys (a newer build) are
 * ignored and unknown enum values degrade to their defaults instead of making the
 * whole record unreadable.
 */
internal val sessionEditorStoreJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

internal fun sessionEditorRulePreferencesStorageKey(programId: String, sessionId: String): String =
    "program(${programId.length})=$programId|session(${sessionId.length})=$sessionId"

internal interface DraftKeyValueStorage {
    suspend fun write(key: String, value: String): Boolean
    suspend fun remove(key: String): Boolean
}

/**
 * A single IO writer for session-editor drafts. Tickets are advanced on the
 * caller thread, while encoding and disk access run in [scope]. Advancing a
 * ticket invalidates every older queued write for that key; deletes use the
 * same queue and therefore cannot be undone by a delayed autosave.
 */
internal class SerialDraftWriter<T>(
    private val scope: CoroutineScope,
    private val storage: DraftKeyValueStorage,
    private val encode: (T) -> String,
) {
    private val queue = Mutex()
    private val generations = ConcurrentHashMap<String, AtomicLong>()
    private val latestOperations = ConcurrentHashMap<String, CompletableDeferred<DraftWriteOutcome>>()

    fun invalidate(key: String): Long = revisionFor(key).incrementAndGet()

    fun currentGeneration(key: String): Long = revisionFor(key).get()

    fun enqueueLatestWrite(key: String, value: T) = enqueueWrite(key, invalidate(key), value)

    fun enqueueWrite(key: String, generation: Long, value: T): CompletableDeferred<DraftWriteOutcome> =
        enqueueOperation(key, generation) {
            storage.write(key, encode(value))
        }

    suspend fun writeLatest(key: String, value: T): DraftWriteOutcome =
        enqueueLatestWrite(key, value).await()

    suspend fun awaitLatest(key: String): DraftWriteOutcome? = latestOperations[key]?.await()

    fun enqueueClear(key: String): CompletableDeferred<DraftWriteOutcome> {
        val generation = invalidate(key)
        return enqueueOperation(key, generation) { storage.remove(key) }
    }

    suspend fun clear(key: String): DraftWriteOutcome = enqueueClear(key).await()

    /** Removes a snapshot only if no newer editor generation superseded the save that committed it. */
    fun enqueueClearAtGeneration(key: String, generation: Long): CompletableDeferred<DraftWriteOutcome> =
        enqueueOperation(key, generation) { storage.remove(key) }

    suspend fun clearAtGeneration(key: String, generation: Long): DraftWriteOutcome =
        enqueueClearAtGeneration(key, generation).await()

    private fun enqueueOperation(
        key: String,
        generation: Long,
        operation: suspend () -> Boolean,
    ): CompletableDeferred<DraftWriteOutcome> {
        val result = CompletableDeferred<DraftWriteOutcome>()
        latestOperations[key] = result
        val job = scope.launch {
            queue.withLock {
                if (currentGeneration(key) != generation) {
                    result.complete(DraftWriteOutcome(DraftWriteStatus.STALE))
                    return@withLock
                }
                val outcome = try {
                    if (operation()) DraftWriteOutcome(DraftWriteStatus.WRITTEN)
                    else DraftWriteOutcome(DraftWriteStatus.FAILED)
                } catch (failure: Throwable) {
                    DraftWriteOutcome(DraftWriteStatus.FAILED, failure)
                }
                result.complete(outcome)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                result.complete(DraftWriteOutcome(DraftWriteStatus.FAILED, cause))
            }
        }
        return result
    }

    private fun revisionFor(key: String): AtomicLong =
        generations.computeIfAbsent(key) { AtomicLong(0L) }
}

/** Application-lifetime owner: writes keep running after an editor ViewModel is cleared. */
internal class SessionEditorDraftStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val codec = sessionEditorStoreJson
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val draftPreferences = CompletableDeferred<SharedPreferences>()
    private val settingsPreferences = CompletableDeferred<SharedPreferences>()
    private val rulePreferences = CompletableDeferred<SharedPreferences>()
    private val rulePreferencesMutex = Mutex()
    private val rulePreferenceWriteRevisions = ConcurrentHashMap<String, AtomicLong>()
    private val storage = object : DraftKeyValueStorage {
        override suspend fun write(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
            draftPreferences.await().edit().putString(key, value).commit()
        }

        override suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {
            draftPreferences.await().edit().remove(key).commit()
        }
    }

    init {
        writerScope.launch {
            runCatching {
                appContext.getSharedPreferences(SESSION_EDITOR_DRAFT_PREFS, Context.MODE_PRIVATE)
            }.fold(draftPreferences::complete, draftPreferences::completeExceptionally)
        }
        writerScope.launch {
            runCatching {
                appContext.getSharedPreferences(SESSION_EDITOR_SETTINGS_PREFS, Context.MODE_PRIVATE)
            }.fold(settingsPreferences::complete, settingsPreferences::completeExceptionally)
        }
        writerScope.launch {
            runCatching {
                appContext.getSharedPreferences(SESSION_EDITOR_RULE_PREFERENCES_PREFS, Context.MODE_PRIVATE)
            }.fold(rulePreferences::complete, rulePreferences::completeExceptionally)
        }
    }

    val writer = SerialDraftWriter<PersistedSessionEditorDraft>(writerScope, storage) { draft ->
        codec.encodeToString(draft)
    }

    suspend fun readRaw(key: String): String? = withContext(Dispatchers.IO) {
        draftPreferences.await().getString(key, null)
    }

    suspend fun autoSaveEnabled(): Boolean = withContext(Dispatchers.IO) {
        val settings = settingsPreferences.await()
        if (settings.contains(SESSION_EDITOR_AUTOSAVE_KEY)) {
            return@withContext settings.getBoolean(SESSION_EDITOR_AUTOSAVE_KEY, true)
        }
        // Older builds kept editor preferences in the draft file. Read that value
        // once so existing installs retain their choice, then keep it separate.
        val legacy = runCatching {
            draftPreferences.await().getBoolean(SESSION_EDITOR_AUTOSAVE_KEY, true)
        }.getOrDefault(true)
        settings.edit().putBoolean(SESSION_EDITOR_AUTOSAVE_KEY, legacy).commit()
        legacy
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsPreferences.await().edit().putBoolean(SESSION_EDITOR_AUTOSAVE_KEY, enabled).commit()
        }
    }

    fun nextRulePreferencesWriteRevision(key: String): Long =
        rulePreferenceWriteRevisions.computeIfAbsent(key) { AtomicLong(0L) }.incrementAndGet()

    /** Distinguishes an absent record from an unreadable one (which callers must not overwrite implicitly). */
    suspend fun readRulePreferencesResult(key: String): RulePreferencesRead = withContext(Dispatchers.IO) {
        rulePreferencesMutex.withLock {
            val raw = rulePreferences.await().getString(key, null)
            if (raw == null) {
                RulePreferencesRead.Absent
            } else {
                val decoded = runCatching { codec.decodeFromString<SessionEditorRulePreferences>(raw) }.getOrNull()
                if (decoded != null) RulePreferencesRead.Present(decoded) else RulePreferencesRead.Unreadable
            }
        }
    }

    suspend fun readRulePreferences(key: String): SessionEditorRulePreferences? =
        (readRulePreferencesResult(key) as? RulePreferencesRead.Present)?.value

    /** Test hook: stores an arbitrary raw payload (e.g. corrupt JSON) under a record's key. */
    internal suspend fun writeRawRulePreferencesForTests(key: String, raw: String): Boolean =
        withContext(Dispatchers.IO) {
            rulePreferencesMutex.withLock { rulePreferences.await().edit().putString(key, raw).commit() }
        }

    /** Test hook: the exact stored payload of a record, undecoded. */
    internal suspend fun readRawRulePreferencesForTests(key: String): String? =
        withContext(Dispatchers.IO) {
            rulePreferencesMutex.withLock { rulePreferences.await().getString(key, null) }
        }

    suspend fun writeRulePreferences(
        key: String,
        value: SessionEditorRulePreferences,
        revision: Long? = null,
    ): Boolean {
        val writeRevision = revision ?: nextRulePreferencesWriteRevision(key)
        val latestRevision = rulePreferenceWriteRevisions.computeIfAbsent(key) { AtomicLong(0L) }
        latestRevision.accumulateAndGet(writeRevision) { current, requested -> maxOf(current, requested) }
        return withContext(Dispatchers.IO) {
            rulePreferencesMutex.withLock {
                if (latestRevision.get() != writeRevision) return@withLock false
                val raw = codec.encodeToString(value)
                val overridden = rulePreferencesWriteOverrideForTests?.invoke(key, raw)
                if (latestRevision.get() != writeRevision) return@withLock false
                if (overridden != null) return@withLock overridden
                rulePreferences.await().edit().putString(key, raw).commit()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SessionEditorDraftStore? = null

        /** A scoped failure hook for the editor durability tests. */
        @Volatile
        internal var rulePreferencesWriteOverrideForTests: ((String, String) -> Boolean?)? = null

        private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getInstance(context: Context): SessionEditorDraftStore = instance ?: synchronized(this) {
            instance ?: SessionEditorDraftStore(context.applicationContext).also { instance = it }
        }

        fun clearLater(context: Context, key: String) {
            initializationScope.launch { getInstance(context).writer.clear(key) }
        }
    }
}

internal fun Session.withoutGeneratedEditorTimestamps(): Session =
    normalizeEditorScheduledTechniques()
        .normalizeMobilityCompatibility()
        .copy(lastModifiedAtMs = 0L)

internal fun sameSessionEditorContent(first: Session?, second: Session?): Boolean =
    when {
        first == null || second == null -> first == second
        else -> first.withoutGeneratedEditorTimestamps() == second.withoutGeneratedEditorTimestamps()
    }

/**
 * The eight core fields as Room knows them: the persisted block when present,
 * otherwise inferred from the session's own exercises (what a fresh load shows).
 */
internal fun Session?.editorCoreRuleDefaults(): SessionEditorRuleDefaults =
    this?.persistedRuleDefaults?.let(SessionEditorRuleDefaults::fromPersisted)
        ?: this?.inferredEditorRuleDefaults()
        ?: SessionEditorRuleDefaults()

/** Global defaults as last committed: Room's core for [originalSession] plus the confirmed extras. */
internal fun SessionEditorUiState.committedRuleDefaults(): SessionEditorRuleDefaults =
    originalSession.editorCoreRuleDefaults().withExtras(savedRuleExtras)

/** Preference record for the current (possibly unsaved) form: parts, limits and global extras. */
internal fun SessionEditorUiState.toRulePreferences(): SessionEditorRulePreferences =
    SessionEditorRulePreferences(
        partRuleDefaults = partRuleDefaults,
        ruleLimits = ruleLimits,
        globalRuleExtras = ruleDefaults.extras(),
    )

/** Preference record for the last committed baseline (what Discard must restore). */
internal fun SessionEditorUiState.toSavedRulePreferences(): SessionEditorRulePreferences =
    SessionEditorRulePreferences(
        partRuleDefaults = savedPartRuleDefaults,
        ruleLimits = savedRuleLimits,
        globalRuleExtras = savedRuleExtras,
    )

/**
 * Content changes are the only ones that go to Room (and create a manual override
 * and a new `lastModifiedAtMs`). Of the global defaults only the eight Room-backed
 * core fields count: an edit that touches just the extras (scope, RIR/intensity
 * type, compound/isolation overrides) is a preference-only change.
 */
internal fun SessionEditorUiState.hasMeaningfulSessionChanges(): Boolean {
    val sessionChanged = !sameSessionEditorContent(session, originalSession)
    val coreRuleDefaultsChanged = !ruleDefaults.sameCoreAs(committedRuleDefaults())
    return sessionChanged || pendingTransferToDays != null || coreRuleDefaultsChanged
}

internal fun SessionEditorUiState.hasMeaningfulDraftChanges(): Boolean =
    hasMeaningfulSessionChanges() ||
        partRuleDefaults != savedPartRuleDefaults ||
        ruleLimits != savedRuleLimits ||
        ruleDefaults.extras() != savedRuleExtras.atCurrentVersion()

/**
 * Single place that decides what the global defaults are when a session is opened.
 *
 * - Room is the authority of the eight core fields; [roomSession] must be the
 *   session as stored in Room, never a newer copy coming from the draft.
 * - Only the extras are overlaid from the preference record ([stored]); a v1
 *   record (null extras) confirms the default extras.
 * - A draft that carries a [SessionEditorCommittedRuleBaseline] is merged field by
 *   field against Room's current core (three-way): a core field the user touched
 *   keeps the draft's value, an untouched one follows Room, so an external change
 *   (e.g. reps 5 -> 8) is not overwritten by an old snapshot.
 * - Older drafts have no reliable baseline and keep "the draft wins" for the whole
 *   form. A pre-split draft (no pinned part/limit baseline) whose record is absent
 *   migrates its own extras as confirmed; a split draft's extras stay pending.
 */
internal fun resolveGlobalRuleDefaults(
    roomSession: Session?,
    stored: SessionEditorRulePreferences?,
    draft: PersistedSessionEditorDraft?,
): ResolvedGlobalRuleDefaults {
    val roomCore = roomSession.editorCoreRuleDefaults()
    val storedExtras = stored?.globalRuleExtras?.atCurrentVersion()
    if (draft == null) {
        val saved = storedExtras ?: SessionEditorGlobalRuleExtras()
        return ResolvedGlobalRuleDefaults(current = roomCore.withExtras(saved), savedExtras = saved)
    }
    val baseline = draft.committedRuleBaseline
    if (baseline != null) {
        val saved = storedExtras ?: baseline.ruleDefaults.extras()
        return ResolvedGlobalRuleDefaults(
            current = mergeCoreThreeWay(base = baseline.ruleDefaults, draft = draft.ruleDefaults, room = roomCore),
            savedExtras = saved,
        )
    }
    val hasPinnedPreferenceBaseline = draft.committedPartRuleDefaults != null && draft.committedRuleLimits != null
    val saved = when {
        storedExtras != null -> storedExtras
        // A v1 record exists: it confirms the default extras, so the draft's are pending.
        stored != null -> SessionEditorGlobalRuleExtras()
        hasPinnedPreferenceBaseline -> SessionEditorGlobalRuleExtras()
        else -> draft.ruleDefaults.extras()
    }
    return ResolvedGlobalRuleDefaults(current = draft.ruleDefaults, savedExtras = saved)
}

/**
 * Per core field: the draft's value when the user changed it since [base] was
 * committed, Room's current value otherwise. The extras are the draft's.
 */
internal fun mergeCoreThreeWay(
    base: SessionEditorRuleDefaults,
    draft: SessionEditorRuleDefaults,
    room: SessionEditorRuleDefaults,
): SessionEditorRuleDefaults = draft.copy(
    setCount = if (draft.setCount != base.setCount) draft.setCount else room.setCount,
    reps = if (draft.reps != base.reps) draft.reps else room.reps,
    rpe = if (draft.rpe != base.rpe) draft.rpe else room.rpe,
    normalRestSeconds = if (draft.normalRestSeconds != base.normalRestSeconds) draft.normalRestSeconds else room.normalRestSeconds,
    betweenSidesRestSeconds = if (draft.betweenSidesRestSeconds != base.betweenSidesRestSeconds) {
        draft.betweenSidesRestSeconds
    } else {
        room.betweenSidesRestSeconds
    },
    supersetBetweenRestSeconds = if (draft.supersetBetweenRestSeconds != base.supersetBetweenRestSeconds) {
        draft.supersetBetweenRestSeconds
    } else {
        room.supersetBetweenRestSeconds
    },
    supersetRoundRestSeconds = if (draft.supersetRoundRestSeconds != base.supersetRoundRestSeconds) {
        draft.supersetRoundRestSeconds
    } else {
        room.supersetRoundRestSeconds
    },
    applyToNewItems = if (draft.applyToNewItems != base.applyToNewItems) draft.applyToNewItems else room.applyToNewItems,
)

/** Same record ignoring the v1 -> v2 marker and the payload version. */
private fun SessionEditorRulePreferences.comparable(): SessionEditorRulePreferences =
    copy(globalRuleExtras = (globalRuleExtras ?: SessionEditorGlobalRuleExtras()).atCurrentVersion())

/**
 * Whether Discard must rewrite the preference record with the committed baseline.
 * The rewrite exists so a baseline that only lives in the draft (legacy migration)
 * survives the draft's deletion; when the record already equals the baseline the
 * write, its disk commit and its revision (which could invalidate an in-flight
 * save) are skipped. An unreadable record is only replaced when there is
 * something non-default to preserve.
 */
internal fun discardMustWriteRulePreferences(
    stored: RulePreferencesRead,
    baseline: SessionEditorRulePreferences,
): Boolean = when (stored) {
    is RulePreferencesRead.Present -> stored.value.comparable() != baseline.comparable()
    RulePreferencesRead.Absent, RulePreferencesRead.Unreadable ->
        baseline.comparable() != SessionEditorRulePreferences().comparable()
}

internal const val SESSION_EDITOR_SETTINGS_PREFS = "session_editor_preferences"
internal const val SESSION_EDITOR_AUTOSAVE_KEY = "auto_save_enabled"
internal const val SESSION_EDITOR_RULE_PREFERENCES_PREFS = "session_editor_rule_preferences"
