package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpkn.domain.workout.VoiceHistoryAudit
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Historial persistente de Modo Voz: transcripción y valores confirmados, nunca audio.
 * La retención (30 días / 500 por sesión) la aplica el repositorio en cada inserción.
 */
@Entity(
    tableName = "workout_voice_history",
    indices = [
        Index("sessionKey", "createdAtEpochMs"),
        Index("status"),
        Index("exerciseId", "setIdx"),
    ],
)
data class WorkoutVoiceHistoryEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val exerciseId: String,
    val exerciseName: String,
    val setIdx: Int,
    val side: String?,
    val transcript: String,
    val status: String,
    val revision: Int,
    val payload: String,
    val audit: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

internal fun VoiceHistoryEntry.toEntity() = WorkoutVoiceHistoryEntity(
    id = id,
    sessionKey = sessionKey,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    setIdx = setIdx,
    side = side,
    transcript = transcript,
    status = status.name,
    revision = revision,
    payload = dbJson.encodeToString(values),
    audit = audit?.let { dbJson.encodeToString(it) },
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

internal fun WorkoutVoiceHistoryEntity.toVoiceHistoryEntry() = VoiceHistoryEntry(
    id = id,
    sessionKey = sessionKey,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    setIdx = setIdx,
    side = side,
    transcript = transcript,
    values = runCatching { dbJson.decodeFromString<VoiceHistoryValues>(payload) }
        .getOrDefault(VoiceHistoryValues()),
    status = runCatching { VoiceHistoryStatus.valueOf(status) }
        .getOrDefault(VoiceHistoryStatus.REVIEW),
    revision = revision,
    audit = audit?.let { runCatching { dbJson.decodeFromString<VoiceHistoryAudit>(it) }.getOrNull() },
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Dao
interface WorkoutVoiceHistoryDao {
    @Query("SELECT * FROM workout_voice_history WHERE sessionKey = :sessionKey ORDER BY createdAtEpochMs ASC")
    fun observe(sessionKey: String): Flow<List<WorkoutVoiceHistoryEntity>>

    @Query("SELECT * FROM workout_voice_history WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): WorkoutVoiceHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkoutVoiceHistoryEntity)

    @Query(
        "UPDATE workout_voice_history SET status = 'REGISTERED', updatedAtEpochMs = :at " +
            "WHERE sessionKey = :sessionKey AND exerciseId = :exerciseId AND setIdx = :setIdx " +
            "AND (side = :side OR (side IS NULL AND :side IS NULL)) AND status = 'REVIEW'",
    )
    suspend fun markRegistered(
        sessionKey: String,
        exerciseId: String,
        setIdx: Int,
        side: String?,
        at: Long,
    )

    @Query("DELETE FROM workout_voice_history WHERE createdAtEpochMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM workout_voice_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "DELETE FROM workout_voice_history WHERE sessionKey = :sessionKey AND id NOT IN (" +
            "SELECT id FROM workout_voice_history WHERE sessionKey = :sessionKey " +
            "ORDER BY createdAtEpochMs DESC LIMIT :keep)",
    )
    suspend fun trimSession(sessionKey: String, keep: Int)
}

val WORKOUT_VOICE_HISTORY_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_voice_history` (
                `id` TEXT NOT NULL,
                `sessionKey` TEXT NOT NULL,
                `exerciseId` TEXT NOT NULL,
                `exerciseName` TEXT NOT NULL,
                `setIdx` INTEGER NOT NULL,
                `side` TEXT,
                `transcript` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `revision` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `audit` TEXT,
                `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_voice_history_sessionKey_createdAtEpochMs` " +
                "ON `workout_voice_history` (`sessionKey`, `createdAtEpochMs`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_voice_history_status` " +
                "ON `workout_voice_history` (`status`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_voice_history_exerciseId_setIdx` " +
                "ON `workout_voice_history` (`exerciseId`, `setIdx`)",
        )
    }
}
