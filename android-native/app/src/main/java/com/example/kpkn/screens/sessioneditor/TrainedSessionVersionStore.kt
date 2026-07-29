package com.example.kpkn.screens.sessioneditor

import android.content.Context
import android.content.SharedPreferences
import com.example.kpkn.data.models.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Persists structural session snapshots captured after a completed (trained) workout,
 * only when the structure differs from the last saved version for that session.
 */
class TrainedSessionVersionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadForSession(sessionId: String): List<SessionDraftSnapshot> {
        if (sessionId.isBlank()) return emptyList()
        val raw = prefs.getString(keyFor(sessionId), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PersistedTrainedVersion>>(raw).map { it.toSnapshot() }
        } catch (_: Exception) {
            emptyList()
        }
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
        val current = loadForSession(sessionId)
        val last = current.lastOrNull()
        if (last != null && structuralEquals(last.session, session)) {
            return current
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
        persist(sessionId, next)
        return next
    }

    private fun persist(sessionId: String, snapshots: List<SessionDraftSnapshot>) {
        val encoded = snapshots.map { PersistedTrainedVersion.from(it) }
        prefs.edit().putString(keyFor(sessionId), json.encodeToString(encoded)).apply()
    }

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
