package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "workout_media",
    indices = [
        Index("workoutLogId"),
        Index("canonicalExerciseId"),
        Index("createdAtMs"),
        Index("isPr"),
        // v29: `video_analysis.observeBySessionKey` une por la clave de sesión.
        Index("sessionKey"),
    ],
)
data class WorkoutMediaEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val filePath: String,
    val thumbPath: String?,
    val createdAtMs: Long,
    val sessionKey: String?,
    val workoutLogId: String?,
    val programId: String?,
    val sessionId: String?,
    val sessionName: String?,
    val exerciseId: String?,
    val canonicalExerciseId: String?,
    val exerciseName: String?,
    val setIndex: Int?,
    val side: String?,
    val weightKg: Double?,
    val reps: Int?,
    val isPr: Boolean,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val caption: String?,
    val poseTrackPath: String?,
    // v29: ids del catálogo v2 del ejercicio (null = ejercicio fuera del catálogo o vídeo anterior a la v29).
    val catalogDefinitionId: String? = null,
    val catalogConfigurationId: String? = null,
)

fun WorkoutMedia.toEntity() = WorkoutMediaEntity(
    id = id,
    kind = kind.name,
    filePath = filePath,
    thumbPath = thumbPath,
    createdAtMs = createdAtMs,
    sessionKey = sessionKey,
    workoutLogId = workoutLogId,
    programId = programId,
    sessionId = sessionId,
    sessionName = sessionName,
    exerciseId = exerciseId,
    canonicalExerciseId = canonicalExerciseId,
    exerciseName = exerciseName,
    setIndex = setIndex,
    side = side,
    weightKg = weightKg,
    reps = reps,
    isPr = isPr,
    durationMs = durationMs,
    width = width,
    height = height,
    caption = caption,
    poseTrackPath = poseTrackPath,
    catalogDefinitionId = catalogDefinitionId,
    catalogConfigurationId = catalogConfigurationId,
)

fun WorkoutMediaEntity.toWorkoutMedia(): WorkoutMedia = WorkoutMedia(
    id = id,
    kind = runCatching { WorkoutMediaKind.valueOf(kind) }.getOrDefault(WorkoutMediaKind.PHOTO),
    filePath = filePath,
    thumbPath = thumbPath,
    createdAtMs = createdAtMs,
    sessionKey = sessionKey,
    workoutLogId = workoutLogId,
    programId = programId,
    sessionId = sessionId,
    sessionName = sessionName,
    exerciseId = exerciseId,
    canonicalExerciseId = canonicalExerciseId,
    exerciseName = exerciseName,
    setIndex = setIndex,
    side = side,
    weightKg = weightKg,
    reps = reps,
    isPr = isPr,
    durationMs = durationMs,
    width = width,
    height = height,
    caption = caption,
    poseTrackPath = poseTrackPath,
    catalogDefinitionId = catalogDefinitionId,
    catalogConfigurationId = catalogConfigurationId,
)

@Dao
interface WorkoutMediaDao {
    @Query("SELECT * FROM workout_media WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutMediaEntity?

    @Query("SELECT * FROM workout_media ORDER BY createdAtMs DESC")
    suspend fun getAll(): List<WorkoutMediaEntity>

    @Query("SELECT * FROM workout_media ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<WorkoutMediaEntity>>

    @Query("SELECT * FROM workout_media WHERE workoutLogId = :workoutLogId ORDER BY createdAtMs DESC")
    suspend fun getByWorkoutLogId(workoutLogId: String): List<WorkoutMediaEntity>

    @Query("SELECT * FROM workout_media WHERE sessionKey = :sessionKey ORDER BY createdAtMs DESC")
    suspend fun getBySessionKey(sessionKey: String): List<WorkoutMediaEntity>

    @Query("SELECT * FROM workout_media WHERE sessionKey = :sessionKey ORDER BY createdAtMs DESC")
    fun observeBySessionKey(sessionKey: String): Flow<List<WorkoutMediaEntity>>

    @Query("SELECT * FROM workout_media WHERE sessionId = :sessionId ORDER BY createdAtMs DESC")
    suspend fun getBySessionId(sessionId: String): List<WorkoutMediaEntity>

    @Query("SELECT * FROM workout_media WHERE canonicalExerciseId = :canonicalExerciseId ORDER BY createdAtMs DESC")
    suspend fun getByCanonicalExerciseId(canonicalExerciseId: String): List<WorkoutMediaEntity>

    @Query(
        """
        SELECT * FROM workout_media
        WHERE isPr = 1 AND createdAtMs BETWEEN :fromMs AND :toMs
        ORDER BY createdAtMs DESC
        """,
    )
    suspend fun getPrHighlights(fromMs: Long, toMs: Long): List<WorkoutMediaEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: WorkoutMediaEntity): Long

    @Upsert
    suspend fun upsert(entity: WorkoutMediaEntity)

    @Query("DELETE FROM workout_media WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        UPDATE workout_media
        SET workoutLogId = :workoutLogId
        WHERE sessionKey = :sessionKey AND (workoutLogId IS NULL OR workoutLogId = '')
        """,
    )
    suspend fun attachSessionKeyToLog(sessionKey: String, workoutLogId: String)

    @Query("UPDATE workout_media SET isPr = :isPr WHERE id = :id")
    suspend fun setPr(id: String, isPr: Boolean)

    @Query("UPDATE workout_media SET caption = :caption WHERE id = :id")
    suspend fun setCaption(id: String, caption: String?)

    // Actualizaciones dirigidas (v29): cambian solo sus columnas y no leen ni reescriben la fila. NUNCA pasar una
    // escritura de este DAO a REPLACE: REPLACE borra la fila y dispara el ON DELETE CASCADE de `video_analysis`.
    // Cada una devuelve las filas cambiadas (0 si el medio ya no existe).

    @Query("UPDATE workout_media SET poseTrackPath = :path WHERE id = :id")
    suspend fun setPoseTrackPath(id: String, path: String?): Int

    /** Las repeticiones y los kg de la serie a la que pertenece el medio (la «foto» de la serie al grabar). */
    @Query("UPDATE workout_media SET reps = :reps, weightKg = :weightKg WHERE id = :id")
    suspend fun setSetSnapshot(id: String, reps: Int?, weightKg: Double?): Int

    @Query(
        """
        UPDATE workout_media
        SET catalogDefinitionId = :definitionId, catalogConfigurationId = :configurationId
        WHERE id = :id
        """,
    )
    suspend fun setCatalogIds(id: String, definitionId: String?, configurationId: String?): Int

    @Query(
        """
        UPDATE workout_media
        SET exerciseId = :exerciseId, canonicalExerciseId = :canonicalExerciseId, exerciseName = :exerciseName,
            catalogDefinitionId = :definitionId, catalogConfigurationId = :configurationId
        WHERE id = :id
        """,
    )
    suspend fun setExerciseAssignment(
        id: String,
        exerciseId: String?,
        canonicalExerciseId: String?,
        exerciseName: String?,
        definitionId: String?,
        configurationId: String?,
    ): Int

    @Query("SELECT COUNT(*) FROM workout_media")
    suspend fun count(): Int
}
