package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Caché del análisis de técnica de un vídeo (Room v29, §3.9). La FUENTE DE VERDAD es `<id>.analysis.json`
 * (`TechniqueSidecars`): esta fila se reconstruye desde ahí, así que perderla no pierde ningún dato.
 *
 * Es hija de `workout_media` con `ON DELETE CASCADE`: borrar el medio borra su análisis, y la restauración de copias
 * (`DELETE FROM workout_media`) los vacía todos hasta que `reconcile()` los rehace. Por eso `workout_media` NUNCA se
 * escribe con REPLACE (dispararía la cascada): solo `@Upsert` y actualizaciones dirigidas.
 *
 * Los enums se guardan por NOMBRE (`status`, `profileId`, `viewKind`, `confidence`, `velocityUnit`, `scaleSource`).
 * La columna es `viewKind` y no `view`, que es palabra reservada de SQL. Un `null` en una medida es un dato que no se
 * pudo medir (o que aún no existe: QUEUED y RUNNING solo traen el estado), nunca un cero. Solo son NOT NULL lo que toda
 * fila necesita: el estado, el avance, las versiones, `exerciseKey` (el hub agrupa por ella), las fechas y `flagsJson`.
 */
@Entity(
    tableName = "video_analysis",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["exerciseKey", "recordedAtMs"]),
    ],
)
data class VideoAnalysisEntity(
    @PrimaryKey val mediaId: String,
    val status: String,
    val progressPct: Int,
    val errorCode: String? = null,
    val engineVersion: Int,
    val poseVersion: Int,
    val poseEngine: String? = null,
    val profileId: String? = null,
    val exerciseKey: String,
    val configurationKey: String? = null,
    val exerciseName: String? = null,
    val setKey: String? = null,
    val loadKg: Double? = null,
    val recordedAtMs: Long,
    val sourceFps: Double? = null,
    val analyzedFps: Double? = null,
    val viewKind: String? = null,
    val confidence: String? = null,
    val repCount: Int? = null,
    val partialReps: Int? = null,
    val failedReps: Int? = null,
    val tutMs: Long? = null,
    val tempoEccMs: Int? = null,
    val tempoPauseAfterEccMs: Int? = null,
    val tempoConMs: Int? = null,
    val tempoPauseAfterConMs: Int? = null,
    val romMeanDeg: Double? = null,
    val romCvPct: Double? = null,
    val velocityUnit: String? = null,
    val velocityLossPct: Double? = null,
    val mcvFirst: Double? = null,
    val mcvBest: Double? = null,
    val mcvLast: Double? = null,
    val peakBest: Double? = null,
    val scaleSource: String? = null,
    val plateDiameterMm: Int? = null,
    val barDeviation: Double? = null,
    val barDeviationUnit: String? = null,
    val trunkMeanDeg: Double? = null,
    val flagsJson: String,
    val reportPath: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/** Un vídeo de la sesión con lo justo para pintar su sugerencia de repeticiones: el medio unido a su análisis. */
data class VideoAnalysisSessionRow(
    val mediaId: String,
    val exerciseId: String?,
    val setIndex: Int?,
    val side: String?,
    val createdAtMs: Long,
    val status: String,
    val progressPct: Int,
    val repCount: Int?,
    val partialReps: Int?,
    val failedReps: Int?,
    val confidence: String?,
)

/** Un ejercicio del hub «Técnica en vídeo»: sus vídeos analizados y el más reciente. */
data class VideoAnalysisExerciseRow(
    val exerciseKey: String,
    val exerciseName: String?,
    val videoCount: Int,
    val lastRecordedAtMs: Long,
)

/** Un punto del perfil carga-velocidad: la mejor repetición completa de una serie con su carga. */
data class VideoAnalysisLoadPoint(
    val mediaId: String,
    val loadKg: Double,
    val mcvBest: Double,
    val velocityUnit: String?,
    val recordedAtMs: Long,
)

@Dao
interface VideoAnalysisDao {
    /**
     * Inserta o actualiza la fila entera. `@Upsert` es «insertar y, si ya existe, actualizar»: no borra nada. Sobre un
     * medio que no existe falla por la clave foránea (un análisis sin vídeo no tiene sentido).
     */
    @Upsert
    suspend fun upsert(entity: VideoAnalysisEntity)

    @Query("SELECT * FROM video_analysis WHERE mediaId = :id LIMIT 1")
    suspend fun getByMedia(id: String): VideoAnalysisEntity?

    @Query("SELECT * FROM video_analysis WHERE mediaId = :id LIMIT 1")
    fun observeByMedia(id: String): Flow<VideoAnalysisEntity?>

    /** Vídeos recientes en cualquier estado; el id resuelve los empates de fecha. */
    @Query("SELECT * FROM video_analysis ORDER BY recordedAtMs DESC, mediaId ASC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<VideoAnalysisEntity>>

    /** Los vídeos de una sesión que tienen análisis (cualquier estado), del más antiguo al más reciente. */
    @Query(
        """
        SELECT m.id AS mediaId, m.exerciseId AS exerciseId, m.setIndex AS setIndex, m.side AS side,
               m.createdAtMs AS createdAtMs, a.status AS status, a.progressPct AS progressPct,
               a.repCount AS repCount, a.partialReps AS partialReps, a.failedReps AS failedReps,
               a.confidence AS confidence
        FROM video_analysis a
        INNER JOIN workout_media m ON m.id = a.mediaId
        WHERE m.sessionKey = :sessionKey AND m.kind = 'VIDEO'
        ORDER BY m.createdAtMs ASC, m.id ASC
        """,
    )
    fun observeBySessionKey(sessionKey: String): Flow<List<VideoAnalysisSessionRow>>

    /**
     * Ejercicios con algún análisis terminado (DONE), del más reciente al más antiguo. El nombre es el de la fila más
     * reciente del grupo: con un solo MAX, SQLite toma las columnas sueltas de la fila que lo tiene.
     */
    @Query(
        """
        SELECT exerciseKey AS exerciseKey, exerciseName AS exerciseName, COUNT(*) AS videoCount,
               MAX(recordedAtMs) AS lastRecordedAtMs
        FROM video_analysis
        WHERE status = 'DONE'
        GROUP BY exerciseKey
        ORDER BY lastRecordedAtMs DESC, exerciseKey ASC
        """,
    )
    fun observeExerciseSummaries(): Flow<List<VideoAnalysisExerciseRow>>

    /** Los análisis terminados (DONE) de un ejercicio, por fecha de grabación. */
    @Query(
        """
        SELECT * FROM video_analysis
        WHERE exerciseKey = :exerciseKey AND status = 'DONE'
        ORDER BY recordedAtMs ASC, mediaId ASC
        """,
    )
    fun observeTrend(exerciseKey: String): Flow<List<VideoAnalysisEntity>>

    /** Puntos carga-velocidad de una configuración (solo análisis DONE con carga y MCV), por fecha. */
    @Query(
        """
        SELECT mediaId AS mediaId, loadKg AS loadKg, mcvBest AS mcvBest, velocityUnit AS velocityUnit,
               recordedAtMs AS recordedAtMs
        FROM video_analysis
        WHERE configurationKey = :configurationKey AND status = 'DONE' AND loadKg IS NOT NULL AND mcvBest IS NOT NULL
        ORDER BY recordedAtMs ASC, mediaId ASC
        """,
    )
    fun observeConfigurationPoints(configurationKey: String): Flow<List<VideoAnalysisLoadPoint>>

    /** Solo estado y avance; el resto de la fila no se toca. Devuelve las filas cambiadas (0 si no hay fila). */
    @Query(
        """
        UPDATE video_analysis SET status = :status, progressPct = :progressPct, updatedAtMs = :updatedAtMs
        WHERE mediaId = :id
        """,
    )
    suspend fun updateProgress(id: String, status: String, progressPct: Int, updatedAtMs: Long): Int

    @Query("DELETE FROM video_analysis WHERE mediaId = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT mediaId FROM video_analysis")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM video_analysis")
    suspend fun count(): Int
}
