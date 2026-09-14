package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toWorkoutMedia
import com.example.kpkn.data.media.WorkoutMediaImportResult
import com.example.kpkn.data.media.WorkoutMediaLegacyImporter
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.data.media.WorkoutMediaThumbnailer
import com.example.kpkn.data.media.PoseTrajectoryAnalysis
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Unified persistence for workout photos and videos (Room v26 + private filesDir store).
 *
 * Does not own live-session UI. Capture/albums call this repository.
 * [importLegacyIfNeeded] is idempotent via Settings.workoutMediaLegacyImportDone.
 */
class WorkoutMediaRepository(
    context: Context,
    private val db: KpknDatabase = KpknDatabase.getInstance(context),
    private val store: WorkoutMediaStore = WorkoutMediaStore(context),
) {
    private val appContext = context.applicationContext
    private val importer = WorkoutMediaLegacyImporter(appContext, db, store)

    fun store(): WorkoutMediaStore = store

    suspend fun importLegacyIfNeeded(force: Boolean = false): WorkoutMediaImportResult =
        withContext(Dispatchers.IO) { importer.importIfNeeded(force) }

    suspend fun upsert(media: WorkoutMedia) = withContext(Dispatchers.IO) {
        db.workoutMediaDao().upsert(media.toEntity())
    }

    suspend fun getById(id: String): WorkoutMedia? = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getById(id)?.toWorkoutMedia()
    }

    suspend fun listAll(): List<WorkoutMedia> = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getAll().map { it.toWorkoutMedia() }
    }

    fun observeAll(): Flow<List<WorkoutMedia>> =
        db.workoutMediaDao().observeAll().map { rows -> rows.map { it.toWorkoutMedia() } }

    fun observeForSessionKey(sessionKey: String): Flow<List<WorkoutMedia>> =
        db.workoutMediaDao().observeBySessionKey(sessionKey).map { rows -> rows.map { it.toWorkoutMedia() } }

    suspend fun listForWorkoutLog(workoutLogId: String): List<WorkoutMedia> = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getByWorkoutLogId(workoutLogId).map { it.toWorkoutMedia() }
    }

    suspend fun listForSessionKey(sessionKey: String): List<WorkoutMedia> = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getBySessionKey(sessionKey).map { it.toWorkoutMedia() }
    }

    suspend fun listForSessionId(sessionId: String): List<WorkoutMedia> = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getBySessionId(sessionId).map { it.toWorkoutMedia() }
    }

    suspend fun listForExercise(canonicalExerciseId: String): List<WorkoutMedia> = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getByCanonicalExerciseId(canonicalExerciseId).map { it.toWorkoutMedia() }
    }

    suspend fun prHighlights(fromMs: Long, toMs: Long): List<WorkoutMedia> = withContext(Dispatchers.IO) {
        db.workoutMediaDao().getPrHighlights(fromMs, toMs).map { it.toWorkoutMedia() }
    }

    suspend fun attachToLog(sessionKey: String, workoutLogId: String) = withContext(Dispatchers.IO) {
        db.workoutMediaDao().attachSessionKeyToLog(sessionKey, workoutLogId)
    }

    suspend fun markPr(id: String, isPr: Boolean) = withContext(Dispatchers.IO) {
        db.workoutMediaDao().setPr(id, isPr)
    }

    suspend fun updateCaption(id: String, caption: String?) = withContext(Dispatchers.IO) {
        db.workoutMediaDao().setCaption(id, caption)
    }

    suspend fun markPrFlags(
        sessionKey: String,
        completedSets: Map<String, CompletedSet>,
        milestones: List<SessionMilestone>,
    ) = withContext(Dispatchers.IO) {
        val media = db.workoutMediaDao().getBySessionKey(sessionKey).map { it.toWorkoutMedia() }
        WorkoutMediaPrFlags.idsToMark(media, completedSets, milestones).forEach { id ->
            db.workoutMediaDao().setPr(id, true)
        }
    }

    suspend fun ingestFile(
        source: File,
        kind: WorkoutMediaKind,
        seed: WorkoutMedia,
        moveIfUnmanaged: Boolean = false,
    ): WorkoutMedia? = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() <= 0L) return@withContext null
        val createdAtMs = seed.createdAtMs.takeIf { it > 0L }
            ?: source.lastModified().takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val id = seed.id.ifBlank { UUID.randomUUID().toString() }
        val dest = if (store.isManagedPath(source)) {
            source
        } else {
            store.copyIntoStore(source, id, kind, createdAtMs).also {
                if (moveIfUnmanaged) runCatching { source.delete() }
            }
        }
        val thumb = store.thumbFile(id)
        val probe = WorkoutMediaThumbnailer.probeAndThumb(dest, thumb, kind)
        val media = seed.copy(
            id = id,
            kind = kind,
            filePath = dest.absolutePath,
            thumbPath = thumb.takeIf { probe.thumbWritten }?.absolutePath ?: seed.thumbPath,
            createdAtMs = createdAtMs,
            durationMs = probe.durationMs ?: seed.durationMs,
            width = probe.width ?: seed.width,
            height = probe.height ?: seed.height,
        )
        db.workoutMediaDao().upsert(media.toEntity())
        media
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val existing = db.workoutMediaDao().getById(id)
        db.workoutMediaDao().delete(id)
        existing?.filePath?.let { store.deleteManaged(it) }
        existing?.thumbPath?.let { store.deleteManaged(it) }
        existing?.poseTrackPath?.let { store.deleteManaged(it) }
        existing?.filePath?.let { path ->
            store.deleteManaged(PoseTrajectoryAnalysis.sidecarFile(File(path)).absolutePath)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: WorkoutMediaRepository? = null

        fun init(context: Context): WorkoutMediaRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkoutMediaRepository(context.applicationContext).also { INSTANCE = it }
            }

        fun forDatabase(context: Context, db: KpknDatabase): WorkoutMediaRepository =
            WorkoutMediaRepository(context.applicationContext, db)

        fun getInstance(): WorkoutMediaRepository =
            INSTANCE ?: error("WorkoutMediaRepository not initialized — call init(context) first.")

        fun closeInstance() {
            INSTANCE = null
        }
    }
}
