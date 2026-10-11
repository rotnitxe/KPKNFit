package com.example.kpkn.screens.sessioneditor

import android.content.Context
import android.content.SharedPreferences
import com.example.kpkn.data.models.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists structural session snapshots captured after a completed (trained) workout,
 * only when the structure differs from the last saved version for that session.
 */
class TrainedSessionVersionStore(context: Context) {

    private val appContext = context.applicationContext
    @Volatile
    private var prefs: SharedPreferences? = null
    private val appendLocks = ConcurrentHashMap<String, Any>()
    /** Retains a failed commit in memory so a later load/append retries the same snapshot. */
    private val pendingWrites = ConcurrentHashMap<String, List<SessionDraftSnapshot>>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Synchronous disk-backed read. Call only from a worker thread. */
    fun loadForSession(sessionId: String): List<SessionDraftSnapshot> {
        if (sessionId.isBlank()) return emptyList()
        return synchronized(lockFor(sessionId)) {
            val pending = pendingWrites[sessionId]
            if (pending != null) {
                persist(sessionId, pending)
                pendingWrites.remove(sessionId, pending)
                return@synchronized pending
            }
            val raw = preferences().getString(keyFor(sessionId), null) ?: return@synchronized emptyList()
            decodeSnapshots(raw).getOrDefault(emptyList())
        }
    }

    /** Main-safe facade for editor and lifecycle callers. */
    suspend fun loadForSessionOnIo(sessionId: String): List<SessionDraftSnapshot> =
        withContext(Dispatchers.IO) { loadForSession(sessionId) }

    private fun decodeSnapshots(raw: String): Result<List<SessionDraftSnapshot>> =
        runCatching {
            json.decodeFromString<List<PersistedTrainedVersion>>(raw).map { it.toSnapshot() }
        }

    /**
     * Appends a trained version when [session] structure differs from the last saved one.
     * Returns the updated list (or previous list if unchanged).
     */
    fun maybeAppendAfterTraining(
        sessionId: String,
        session: Session,
        trainedAtMs: Long = System.currentTimeMillis(),
        reason: String = "Sesión entrenada",
    ): List<SessionDraftSnapshot> {
        if (sessionId.isBlank()) return emptyList()
        return synchronized(lockFor(sessionId)) {
            val pending = pendingWrites[sessionId]
            val raw = preferences().getString(keyFor(sessionId), null)
            val current = pending ?: if (raw == null) {
                emptyList()
            } else {
                val decoded = decodeSnapshots(raw)
                if (decoded.isFailure) {
                    throw IllegalStateException("No se pudo leer el historial de versiones entrenadas", decoded.exceptionOrNull())
                }
                decoded.getOrDefault(emptyList())
            }
            val last = current.lastOrNull()
            if (last != null && structuralEquals(last.session, session)) {
                if (pending != null) {
                    persist(sessionId, pending)
                    pendingWrites.remove(sessionId, pending)
                }
                return@synchronized current
            }
            val changedFields = if (last == null) {
                listOf("entreno")
            } else {
                detectChangedFields(previous = last.session, current = session)
            }
            val exercises = session.allExercises()
            val snapshot = SessionDraftSnapshot(
                id = UUID.randomUUID().toString(),
                session = sessionForVersioning(session),
                savedAtMs = trainedAtMs,
                reason = reason,
                changedFields = changedFields.ifEmpty { listOf("estructura") },
                exerciseCount = exercises.size,
                setCount = exercises.sumOf { it.sets.size.coerceAtLeast(1) },
                partCount = session.parts.size,
            )
            val next = (current + snapshot).takeLast(MAX_VERSIONS)
            try {
                persist(sessionId, next)
                pendingWrites.remove(sessionId)
            } catch (error: Exception) {
                pendingWrites[sessionId] = next
                throw error
            }
            next
        }
    }

    /** Main-safe facade; the JSON and synchronous SharedPreferences commit stay on IO. */
    suspend fun maybeAppendAfterTrainingOnIo(
        sessionId: String,
        session: Session,
        trainedAtMs: Long = System.currentTimeMillis(),
        reason: String = "Sesión entrenada",
    ): List<SessionDraftSnapshot> = withContext(Dispatchers.IO) {
        maybeAppendAfterTraining(sessionId, session, trainedAtMs, reason)
    }

    private fun persist(sessionId: String, snapshots: List<SessionDraftSnapshot>) {
        val encoded = snapshots.map { PersistedTrainedVersion.from(it) }
        val committed = preferences().edit().putString(keyFor(sessionId), json.encodeToString(encoded)).commit()
        check(committed) { "No se pudo confirmar el historial de versiones entrenadas" }
    }

    private fun preferences(): SharedPreferences {
        prefs?.let { return it }
        return synchronized(this) {
            prefs ?: appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        }
    }

    private fun lockFor(sessionId: String): Any = appendLocks.computeIfAbsent(sessionId) { Any() }

    companion object {
        private const val PREFS_NAME = "trained_session_versions"
        private const val MAX_VERSIONS = 20

        @Volatile
        private var instance: TrainedSessionVersionStore? = null

        fun getInstance(context: Context): TrainedSessionVersionStore {
            return instance ?: synchronized(this) {
                instance ?: TrainedSessionVersionStore(context.applicationContext).also { instance = it }
            }
        }

        fun keyFor(sessionId: String) = "session_$sessionId"

        /** Strip cosmetic / runtime fields so equality reflects training structure. */
        fun sessionForVersioning(session: Session): Session = session.copy(
            background = null,
            coverStyle = null,
            lastModifiedAtMs = 0L,
            meetResults = null,
            volumeAdvances = emptyList(),
            sessionB = session.sessionB?.let(::sessionForVersioning),
            sessionC = session.sessionC?.let(::sessionForVersioning),
            sessionD = session.sessionD?.let(::sessionForVersioning),
        )

        fun structuralEquals(a: Session, b: Session): Boolean =
            sessionForVersioning(a) == sessionForVersioning(b)
    }
}

@Serializable
private data class PersistedTrainedVersion(
    val id: String,
    val session: Session,
    val savedAtMs: Long,
    val reason: String,
    val changedFields: List<String> = emptyList(),
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val partCount: Int = 0,
) {
    fun toSnapshot() = SessionDraftSnapshot(
        id = id,
        session = session,
        savedAtMs = savedAtMs,
        reason = reason,
        changedFields = changedFields,
        exerciseCount = exerciseCount,
        setCount = setCount,
        partCount = partCount,
    )

    companion object {
        fun from(snapshot: SessionDraftSnapshot) = PersistedTrainedVersion(
            id = snapshot.id,
            session = snapshot.session,
            savedAtMs = snapshot.savedAtMs,
            reason = snapshot.reason,
            changedFields = snapshot.changedFields,
            exerciseCount = snapshot.exerciseCount,
            setCount = snapshot.setCount,
            partCount = snapshot.partCount,
        )
    }
}
