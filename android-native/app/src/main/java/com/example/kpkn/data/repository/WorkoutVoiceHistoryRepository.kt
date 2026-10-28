package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toVoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryAudit
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryRetention
import com.example.kpkn.domain.workout.VoiceHistoryValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Historial de Modo Voz persistido en Room.
 *
 * Sólo se escriben capturas confirmadas por el usuario (transcripción + valores),
 * nunca audio. La retención se aplica en cada inserción: 30 días y 500 entradas
 * por sesión. Desactivar o pausar Modo Voz no elimina nada.
 */
class WorkoutVoiceHistoryRepository(
    context: Context,
    private val db: KpknDatabase = KpknDatabase.getInstance(context.applicationContext),
) {
    private val dao = db.workoutVoiceHistoryDao()

    fun observe(sessionKey: String): Flow<List<VoiceHistoryEntry>> =
        dao.observe(sessionKey).map { entities -> entities.map { it.toVoiceHistoryEntry() } }

    suspend fun record(entry: VoiceHistoryEntry) = withContext(Dispatchers.IO) {
        dao.upsert(entry.toEntity())
        dao.deleteOlderThan(entry.createdAtEpochMs - VoiceHistoryRetention.RETENTION_MS)
        dao.trimSession(entry.sessionKey, VoiceHistoryRetention.MAX_ENTRIES_PER_SESSION)
    }

    /** Corrige una captura, incrementa su revisión y audita el cambio anterior/nuevo. */
    suspend fun correct(
        id: String,
        values: VoiceHistoryValues,
        atEpochMs: Long,
    ): VoiceHistoryEntry? = withContext(Dispatchers.IO) {
        val current = dao.byId(id)?.toVoiceHistoryEntry() ?: return@withContext null
        val next = current.copy(
            values = values,
            status = current.status,
            revision = current.revision + 1,
            audit = VoiceHistoryAudit(previous = current.values, next = values),
            updatedAtEpochMs = atEpochMs,
        )
        dao.upsert(next.toEntity())
        next
    }

    /** «Descartar» una captura por revisar o quitar la de una serie deshecha. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun markRegistered(
        sessionKey: String,
        exerciseId: String,
        setIdx: Int,
        side: String?,
        atEpochMs: Long,
    ) = withContext(Dispatchers.IO) {
        dao.markRegistered(sessionKey, exerciseId, setIdx, side, atEpochMs)
    }

    companion object {
        @Volatile private var INSTANCE: WorkoutVoiceHistoryRepository? = null

        fun init(context: Context): WorkoutVoiceHistoryRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkoutVoiceHistoryRepository(context.applicationContext).also { INSTANCE = it }
            }

        fun forDatabase(context: Context, db: KpknDatabase): WorkoutVoiceHistoryRepository =
            WorkoutVoiceHistoryRepository(context.applicationContext, db)

        fun getInstance(): WorkoutVoiceHistoryRepository =
            INSTANCE ?: error("WorkoutVoiceHistoryRepository not initialized — call init(context) first.")

        fun closeInstance() {
            INSTANCE = null
        }
    }
}
