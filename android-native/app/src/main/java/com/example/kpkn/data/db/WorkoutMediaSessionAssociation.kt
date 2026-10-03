package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.data.models.WorkoutLog

/** Durable, exact association used when capture persistence arrives after finish. */
@Entity(
    tableName = "workout_media_session_associations",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutLogId")],
)
data class WorkoutMediaSessionAssociationEntity(
    @PrimaryKey val sessionKey: String,
    val workoutLogId: String,
)

@Dao
interface WorkoutMediaSessionAssociationDao {
    @Query("SELECT * FROM workout_media_session_associations WHERE sessionKey = :sessionKey LIMIT 1")
    suspend fun getBySessionKey(sessionKey: String): WorkoutMediaSessionAssociationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: WorkoutMediaSessionAssociationEntity)

    /** Call inside the finish/bind transaction; retries are idempotent and conflicts fail closed. */
    suspend fun bind(sessionKey: String, workoutLogId: String) {
        require(sessionKey.isNotBlank()) { "Workout media session key must not be blank" }
        insertIgnore(WorkoutMediaSessionAssociationEntity(sessionKey, workoutLogId))
        val persisted = getBySessionKey(sessionKey)
            ?: error("Workout media association could not be persisted")
        check(persisted.workoutLogId == workoutLogId) {
            "Workout media session key is already bound to another log"
        }
    }
}

/**
 * Binds a finished log and the media rows already present for its exact session key.
 * Call only from the same `db.withTransaction` that inserts [log].
 */
suspend fun KpknDatabase.bindWorkoutMediaSession(sessionKey: String, log: WorkoutLog) {
    if (sessionKey.isBlank()) return
    check(inTransaction()) { "Workout media binding must share the workout finish transaction" }
    check(workoutLogDao().getById(log.id) != null) { "Cannot bind media before the workout log exists" }

    val associationDao = workoutMediaSessionAssociationDao()
    associationDao.bind(sessionKey, log.id)
    val mediaDao = workoutMediaDao()
    val currentRows = mediaDao.getBySessionKey(sessionKey)
    check(currentRows.all { it.workoutLogId.isNullOrBlank() || it.workoutLogId == log.id }) {
        "A workout media row is already attached to another log"
    }
    mediaDao.attachSessionKeyToLog(sessionKey, log.id)
    WorkoutMediaPrFlags.idsToMarkForLog(currentRows.map { it.toWorkoutMedia() }, log)
        .forEach { mediaDao.setPr(it, true) }
}
