package com.example.kpkn.data.media

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMedia
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs

data class WorkoutMediaImportResult(
    val skippedBecauseAlreadyDone: Boolean,
    val importedCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val skippedMissingCount: Int = 0,
    val failedCount: Int = 0,
)

/**
 * One-shot, non-destructive import of the three legacy workout photo silos
 * into [WorkoutMediaStore] + Room `workout_media`.
 *
 * Original files and `WorkoutLog.exercisePhotos` / `sessionPhotos` JSON are left intact.
 * Idempotent via [Settings.workoutMediaLegacyImportDone] and stable ids from source path.
 */
class WorkoutMediaLegacyImporter(
    private val filesDir: File,
    private val db: KpknDatabase,
    private val store: WorkoutMediaStore = WorkoutMediaStore(filesDir),
) {
    constructor(context: Context, db: KpknDatabase, store: WorkoutMediaStore = WorkoutMediaStore(context)) : this(
        filesDir = context.applicationContext.filesDir,
        db = db,
        store = store,
    )

    suspend fun importIfNeeded(force: Boolean = false): WorkoutMediaImportResult {
        val settings = db.settingsDao().get()?.toSettings() ?: Settings()
        if (settings.workoutMediaLegacyImportDone && !force) {
            return WorkoutMediaImportResult(skippedBecauseAlreadyDone = true)
        }

        val seenSources = linkedSetOf<String>()
        var imported = 0
        var duplicates = 0
        var missing = 0
        var failed = 0

        suspend fun ingest(
            source: File,
            seed: WorkoutMedia,
        ) {
            val canonical = WorkoutMediaStore.canonicalPath(source)
            if (!seenSources.add(canonical)) {
                duplicates += 1
                return
            }
            if (!source.isFile || source.length() <= 0L) {
                missing += 1
                return
            }
            if (store.isManagedPath(source)) {
                duplicates += 1
                return
            }
            runCatching {
                val createdAtMs = source.lastModified().takeIf { it > 0L } ?: seed.createdAtMs
                val kind = WorkoutMediaStore.kindOf(source)
                val id = seed.id.ifBlank { legacyId(canonical) }
                val dest = store.copyIntoStore(source, id, kind, createdAtMs)
                val inserted = db.workoutMediaDao().insertIgnore(
                    seed.copy(
                        id = id,
                        kind = kind,
                        filePath = dest.absolutePath,
                        createdAtMs = createdAtMs,
                    ).toEntity(),
                )
                if (inserted < 1L) {
                    duplicates += 1
                } else {
                    imported += 1
                }
            }.onFailure {
                failed += 1
            }
        }

        val logs = db.workoutLogDao().getAll().mapNotNull { it.toWorkoutLog() }
        val logsBySession = logs.groupBy { it.sessionId }

        // 1) WorkoutLog JSON paths (richest metadata). Logs stay read-only.
        logs.forEach { log ->
            log.sessionPhotos.forEach { path ->
                val file = File(path)
                if (!file.isFile) {
                    missing += 1
                    return@forEach
                }
                ingest(
                    source = file,
                    seed = sessionSeed(file, log),
                )
            }
            log.exercisePhotos.forEach { (exerciseId, paths) ->
                val completed = log.completedExercises.firstOrNull { it.exerciseId == exerciseId }
                paths.forEach { path ->
                    val file = File(path)
                    if (!file.isFile) {
                        missing += 1
                        return@forEach
                    }
                    ingest(
                        source = file,
                        seed = exerciseSeed(
                            file = file,
                            log = log,
                            exerciseId = exerciseId,
                            canonicalExerciseId = completed?.canonicalExerciseId ?: exerciseId,
                            exerciseName = completed?.exerciseName,
                        ),
                    )
                }
            }
        }

        // 2) filesDir/workout_photos/<sessionId>/...
        val photosRoot = File(filesDir, WORKOUT_PHOTOS_DIR)
        if (photosRoot.isDirectory) {
            photosRoot.listFiles()?.filter { it.isDirectory }?.forEach { sessionDir ->
                val sessionId = sessionDir.name
                sessionDir.listFiles().orEmpty().forEach { child ->
                    val files = when {
                        child.isFile -> listOf(child)
                        child.isDirectory -> child.listFiles()?.filter { it.isFile }.orEmpty().toList()
                        else -> emptyList()
                    }
                    files.filter { it.length() > 0L }.forEach { file ->
                        val parentName = file.parentFile?.name.orEmpty()
                        val isSessionAlbum = parentName == SESSION_ALBUM_DIR || file.parentFile == sessionDir
                        val exerciseId = if (isSessionAlbum) null else parentName
                        val createdAtMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        val closest = closestLog(logsBySession[sessionId].orEmpty(), createdAtMs)
                        val completed = closest?.completedExercises?.firstOrNull { it.exerciseId == exerciseId }
                        ingest(
                            source = file,
                            seed = WorkoutMedia(
                                id = legacyId(WorkoutMediaStore.canonicalPath(file)),
                                kind = WorkoutMediaStore.kindOf(file),
                                filePath = file.absolutePath,
                                createdAtMs = createdAtMs,
                                workoutLogId = closest?.id,
                                programId = closest?.programId,
                                sessionId = sessionId,
                                sessionName = closest?.sessionName,
                                exerciseId = exerciseId,
                                canonicalExerciseId = completed?.canonicalExerciseId ?: exerciseId,
                                exerciseName = completed?.exerciseName,
                            ),
                        )
                    }
                }
            }
        }

        // 3) filesDir/exercise_user_media/<exerciseKey>/...
        val userRoot = File(filesDir, EXERCISE_USER_MEDIA_DIR)
        if (userRoot.isDirectory) {
            userRoot.listFiles()?.filter { it.isDirectory }?.forEach { exerciseDir ->
                val exerciseKey = exerciseDir.name
                exerciseDir.listFiles()?.filter { it.isFile && it.length() > 0L }?.forEach { file ->
                    ingest(
                        source = file,
                        seed = WorkoutMedia(
                            id = legacyId(WorkoutMediaStore.canonicalPath(file)),
                            kind = WorkoutMediaStore.kindOf(file),
                            filePath = file.absolutePath,
                            createdAtMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                            exerciseId = exerciseKey,
                            canonicalExerciseId = exerciseKey,
                        ),
                    )
                }
            }
        }

        db.settingsDao().upsert(
            settings.copy(workoutMediaLegacyImportDone = true).toEntity(),
        )
        return WorkoutMediaImportResult(
            skippedBecauseAlreadyDone = false,
            importedCount = imported,
            skippedDuplicateCount = duplicates,
            skippedMissingCount = missing,
            failedCount = failed,
        )
    }

    private fun sessionSeed(file: File, log: WorkoutLog): WorkoutMedia {
        val canonical = WorkoutMediaStore.canonicalPath(file)
        return WorkoutMedia(
            id = legacyId(canonical),
            kind = WorkoutMediaStore.kindOf(file),
            filePath = file.absolutePath,
            createdAtMs = file.lastModified().takeIf { it > 0L } ?: parseLogEpoch(log.date) ?: System.currentTimeMillis(),
            workoutLogId = log.id,
            programId = log.programId,
            sessionId = log.sessionId,
            sessionName = log.sessionName,
        )
    }

    private fun exerciseSeed(
        file: File,
        log: WorkoutLog,
        exerciseId: String,
        canonicalExerciseId: String,
        exerciseName: String?,
    ): WorkoutMedia {
        val canonical = WorkoutMediaStore.canonicalPath(file)
        return WorkoutMedia(
            id = legacyId(canonical),
            kind = WorkoutMediaStore.kindOf(file),
            filePath = file.absolutePath,
            createdAtMs = file.lastModified().takeIf { it > 0L } ?: parseLogEpoch(log.date) ?: System.currentTimeMillis(),
            workoutLogId = log.id,
            programId = log.programId,
            sessionId = log.sessionId,
            sessionName = log.sessionName,
            exerciseId = exerciseId,
            canonicalExerciseId = canonicalExerciseId,
            exerciseName = exerciseName,
        )
    }

    companion object {
        const val WORKOUT_PHOTOS_DIR = "workout_photos"
        const val EXERCISE_USER_MEDIA_DIR = "exercise_user_media"
        private const val SESSION_ALBUM_DIR = "session"

        fun legacyId(canonicalSourcePath: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalSourcePath.toByteArray(Charsets.UTF_8))
            return "legacy_" + digest.take(16).joinToString("") { byte -> "%02x".format(byte) }
        }

        fun closestLog(logs: List<WorkoutLog>, fileTimeMs: Long): WorkoutLog? {
            if (logs.isEmpty()) return null
            return logs.minByOrNull { log ->
                val epoch = parseLogEpoch(log.date) ?: Long.MAX_VALUE / 4
                abs(epoch - fileTimeMs)
            }
        }

        fun parseLogEpoch(date: String): Long? = runCatching { Instant.parse(date).toEpochMilli() }.getOrNull()
    }
}
