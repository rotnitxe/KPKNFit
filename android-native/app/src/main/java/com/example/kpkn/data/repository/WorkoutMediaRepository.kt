package com.example.kpkn.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.system.Os
import android.annotation.TargetApi
import android.content.Intent
import androidx.room.withTransaction
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toWorkoutMedia
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.media.WorkoutMediaCaptureRecoveryResult
import com.example.kpkn.data.media.WorkoutMediaPendingCapture
import com.example.kpkn.data.media.WorkoutMediaPendingCaptureState
import com.example.kpkn.data.media.WorkoutMediaPendingRetryAlbum
import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.data.media.WorkoutMediaFinalizePolicy
import com.example.kpkn.data.media.WorkoutMediaImportResult
import com.example.kpkn.data.media.WorkoutMediaLegacyImporter
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.WorkoutMediaThumbnailer
import com.example.kpkn.data.media.PoseTrajectoryAnalysis
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

/**
 * Unified persistence for workout photos and videos (Room + private filesDir store).
 *
 * Does not own live-session UI. Capture/albums call this repository.
 * [importLegacyIfNeeded] is idempotent via Settings.workoutMediaLegacyImportDone.
 */
class WorkoutMediaRepository(
    context: Context,
    private val db: KpknDatabase = KpknDatabase.getInstance(context),
    store: WorkoutMediaStore? = null,
) {
    private val appContext = context.applicationContext
    private val injectedStore = store
    private val store: WorkoutMediaStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        injectedStore ?: WorkoutMediaStore(appContext.filesDir)
    }
    private val importer: WorkoutMediaLegacyImporter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkoutMediaLegacyImporter(appContext.filesDir, db, this@WorkoutMediaRepository.store)
    }
    private val captureJournal: WorkoutMediaCaptureJournal by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkoutMediaCaptureJournal(appContext.filesDir)
    }
    private val inFlightLock = Any()
    private val inFlight = mutableMapOf<String, Deferred<WorkoutMedia?>>()
    private val pendingUriRequestsLock = Any()
    private val pendingUriRequests = linkedMapOf<String, PendingUriCaptureRequest>()
    private val startupRecovery: Deferred<WorkoutMediaCaptureRecoveryResult> =
        MediaPersistenceOwner.scope.async(Dispatchers.IO) { recoverJournalEntries() }

    /** Process-owned retry state remains observable after the workout ViewModel is cleared. */
    val pendingCaptureRetryState: StateFlow<WorkoutMediaPendingCaptureState> =
        MediaPersistenceOwner.pendingCaptureRetryState.asStateFlow()

    suspend fun prepareCameraCaptureFile(
        id: String,
        kind: WorkoutMediaKind,
        createdAtMs: Long,
    ): File = withContext(Dispatchers.IO) {
        store.destinationFile(id, kind, createdAtMs).also { file ->
            if (file.parentFile?.isDirectory != true) {
                throw IOException("No se pudo preparar el almacenamiento privado de la captura.")
            }
        }
    }

    suspend fun pendingCaptureSourceFile(id: String, extension: String): File =
        withContext(Dispatchers.IO) { captureJournal.pendingSourceFile(id, extension) }

    suspend fun isManagedPath(file: File): Boolean =
        withContext(Dispatchers.IO) { store.isManagedPath(file) }

    suspend fun poseSidecarFile(id: String, createdAtMs: Long): File =
        withContext(Dispatchers.IO) { store.poseSidecarFile(id, createdAtMs) }

    suspend fun journalCapture(entry: WorkoutMediaCaptureJournalEntry) = withContext(Dispatchers.IO) {
        captureJournal.writeCapturing(entry)
    }

    /** Reserve the UUID before its durable marker becomes visible to recovery. */
    suspend fun beginCameraCapture(entry: WorkoutMediaCaptureJournalEntry) = withContext(Dispatchers.IO) {
        check(entry.sourceUri == null) { "Camera captures must use an app-owned source file" }
        MediaPersistenceOwner.activeCameraCaptures.add(entry.media.id)
        try {
            captureJournal.writeCapturing(entry)
        } catch (error: Exception) {
            MediaPersistenceOwner.activeCameraCaptures.remove(entry.media.id)
            throw error
        }
    }

    /** Prevent a retry from ingesting a file while CameraX still owns its writer. */
    fun markCameraCaptureStarted(id: String) = MediaPersistenceOwner.activeCameraCaptures.add(id)

    fun markCameraCaptureCompleted(id: String) {
        MediaPersistenceOwner.activeCameraCaptures.remove(id)
    }

    /**
     * Reconnects an ActivityResult to the durable, frozen camera request after
     * Compose or its ViewModel has been recreated. The UUID is accepted only
     * when the journal still describes an app-owned session PHOTO.
     */
    suspend fun externalCameraPhotoForResult(id: String): WorkoutMediaCaptureJournalEntry? =
        withContext(Dispatchers.IO) {
            // Do not race startup recovery for this UUID. A process restored from
            // an external camera launch must keep the capturing marker until
            // this ActivityResult or an explicit retry resolves it.
            startupRecovery.await()
            val canonicalId = runCatching { UUID.fromString(id).toString() }.getOrNull()
            if (canonicalId == null || canonicalId != id) {
                throw IOException("El identificador de captura no es válido.")
            }
            val entry = captureJournal.read(id) ?: return@withContext null
            val media = entry.media
            if (
                media.id != id ||
                media.kind != WorkoutMediaKind.PHOTO ||
                media.createdAtMs <= 0L ||
                media.sessionKey.isNullOrBlank() ||
                media.programId.isNullOrBlank() ||
                media.sessionId.isNullOrBlank() ||
                entry.sourceUri != null ||
                entry.discardedByRetention ||
                entry.deleteSourceAfterCommit
            ) {
                throw IOException("La captura no coincide con una foto de sesión pendiente.")
            }
            val source = runCatching { File(entry.sourceFilePath).canonicalFile }
                .getOrElse { throw IOException("La ruta de captura ya no es válida.", it) }
            val mediaPath = runCatching { File(media.filePath).canonicalFile }
                .getOrElse { throw IOException("La ruta de captura ya no es válida.", it) }
            val expectedPath = runCatching {
                store.destinationFile(id, WorkoutMediaKind.PHOTO, media.createdAtMs).canonicalFile
            }.getOrElse { throw IOException("La ruta de captura ya no es válida.", it) }
            if (source != mediaPath || source != expectedPath || !store.isManagedPath(source)) {
                throw IOException("La captura no pertenece al almacenamiento privado de sesión.")
            }
            MediaPersistenceOwner.activeCameraCaptures.add(id)
            entry
        }

    /**
     * Immediately transfers an ActivityResult to the process-owned IO lane.
     * Journal lookup, cancellation cleanup and ingestion therefore survive
     * disposal of the composition and WorkoutViewModel observer.
     */
    fun submitExternalCameraPhotoResult(
        id: String,
        succeeded: Boolean,
    ): Deferred<WorkoutMedia?> = synchronized(MediaPersistenceOwner.externalCameraResultLock) {
        MediaPersistenceOwner.externalCameraResults[id]?.let { return@synchronized it }
        lateinit var work: Deferred<WorkoutMedia?>
        work = MediaPersistenceOwner.scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                // Startup recovery must settle first, preserving and publishing
                // any non-READY camera entry before this result resolves it.
                startupRecovery.await()
                val entry = externalCameraPhotoForResult(id)
                if (entry == null) {
                    return@async getById(id)
                        ?: throw IOException("La captura pendiente ya no está disponible.")
                }
                markCameraCaptureCompleted(id)
                if (succeeded || entry.readyToIngest) {
                    submitReadyCapture(entry).await()
                } else {
                    abandonEmptyCaptureAsync(entry).await()
                    null
                }
            } finally {
                synchronized(MediaPersistenceOwner.externalCameraResultLock) {
                    if (MediaPersistenceOwner.externalCameraResults[id] === work) {
                        MediaPersistenceOwner.externalCameraResults.remove(id)
                    }
                }
            }
        }
        MediaPersistenceOwner.externalCameraResults[id] = work
        work.start()
        work
    }

    /** The returned work belongs to the process-level repository owner, not the caller's VM. */
    fun submitReadyCapture(entry: WorkoutMediaCaptureJournalEntry): Deferred<WorkoutMedia?> =
        enqueueTracked(entry, "No se pudo guardar el medio. Podés reintentar.") {
            check(!entry.discardedByRetention) { "La captura fue rechazada por la política de retención." }
            captureJournal.writeReady(entry)
            persistJournalEntry(entry.copy(readyToIngest = true))
        }.also { work ->
            work.invokeOnCompletion { markCameraCaptureCompleted(entry.media.id) }
        }

    /** Applies CameraX's retention policy on the process-owned IO dispatcher. */
    fun submitFinalizedVideoCapture(
        entry: WorkoutMediaCaptureJournalEntry,
        hasError: Boolean,
        errorCode: Int,
    ): Deferred<WorkoutMedia?> = enqueueTracked(
        entry,
        if (entry.media.kind == WorkoutMediaKind.VIDEO) {
            "No se pudo guardar el vídeo. Podés reintentar."
        } else {
            "No se pudo guardar la foto. Podés reintentar."
        },
    ) {
        val existing = db.workoutMediaDao().getById(entry.media.id)
        if (existing != null) {
            val media = persistWithAssociation(existing.toWorkoutMedia())
            cleanupCommittedCapture(entry, media)
            return@enqueueTracked media
        }
        val source = File(entry.sourceFilePath)
        val shouldRetain = WorkoutMediaFinalizePolicy.shouldRetainFile(
            fileLengthBytes = source.length(),
            hasError = hasError,
            errorCode = errorCode,
        )
        if (!shouldRetain) {
            captureJournal.writeDiscarded(entry)
            discardRejectedCapture(entry)
            null
        } else {
            captureJournal.writeReady(entry)
            persistJournalEntry(entry.copy(readyToIngest = true))
        }
    }.also { work ->
        work.invokeOnCompletion { markCameraCaptureCompleted(entry.media.id) }
    }

    /**
     * Accepts a picker result synchronously and performs all permission, MIME,
     * path, journal, copy, and Room work on the process-owned IO lane. The seed
     * contains the immutable metadata captured at selection time; its initial
     * PHOTO kind is only a placeholder until the resolver identifies the URI.
     */
    fun submitUriCapture(uri: Uri, mediaSeed: WorkoutMedia): Deferred<WorkoutMedia?> {
        rememberPendingUriRequest(uri, mediaSeed)
        val entry = WorkoutMediaCaptureJournalEntry(
            media = mediaSeed.copy(filePath = "", kind = WorkoutMediaKind.PHOTO),
            sourceFilePath = "",
            sourceUri = uri.toString(),
            deleteSourceAfterCommit = true,
        )
        return enqueueTracked(entry, "No se pudo guardar el medio seleccionado. Podés reintentar.") {
            persistUriCapture(uri, mediaSeed)
        }
    }

    /** Retries the exact UUID and frozen seed already stored in the journal. */
    fun submitUriCapture(entry: WorkoutMediaCaptureJournalEntry): Deferred<WorkoutMedia?> {
        val uri = Uri.parse(entry.sourceUri ?: "")
        rememberPendingUriRequest(uri, entry.media)
        return enqueueTracked(entry, "No se pudo guardar el medio seleccionado. Podés reintentar.") {
            if (entry.sourceUri.isNullOrBlank()) {
                throw IOException("La captura seleccionada no conserva su URI de origen.")
            }
            persistUriCapture(uri, entry.media, entry)
        }
    }

    fun recoverPendingCaptures(): Deferred<WorkoutMediaCaptureRecoveryResult> = startupRecovery

    suspend fun abandonEmptyCapture(entry: WorkoutMediaCaptureJournalEntry) = withContext(Dispatchers.IO) {
        val source = File(entry.sourceFilePath)
        if (source.isFile && source.length() > 0L) return@withContext false
        if (source.isFile && source.length() == 0L) {
            when {
                store.isManagedPath(source) -> store.deleteManaged(source.absolutePath)
                captureJournal.ownsSource(source) -> source.delete()
            }
        }
        captureJournal.remove(entry.media.id)
        true
    }

    fun abandonEmptyCaptureAsync(entry: WorkoutMediaCaptureJournalEntry): Deferred<Unit> {
        val ingest = enqueueTracked(entry, captureRetryMessage(entry)) {
            val abandoned = abandonEmptyCapture(entry)
            if (!abandoned) {
                throw IOException("La captura conserva un archivo recuperable. Reintentá desde el historial.")
            }
            null
        }
        return MediaPersistenceOwner.scope.async(Dispatchers.IO) {
            ingest.await()
            Unit
        }
    }

    /** Used only when CameraX's retention policy rejects the finalized file. */
    suspend fun discardRejectedCapture(entry: WorkoutMediaCaptureJournalEntry) = withContext(Dispatchers.IO) {
        val source = File(entry.sourceFilePath)
        when {
            store.isManagedPath(source) -> store.deleteManaged(source.absolutePath)
            captureJournal.ownsSource(source) -> source.delete()
        }
        captureJournal.remove(entry.media.id)
    }

    fun discardRejectedCaptureAsync(entry: WorkoutMediaCaptureJournalEntry): Deferred<Unit> =
        MediaPersistenceOwner.scope.async(Dispatchers.IO) { discardRejectedCapture(entry) }

    /** One bounded retry pass; a later user retry or next process start may try again. */
    suspend fun retryPendingCapturesForSession(sessionKey: String) = withContext(Dispatchers.IO) {
        val entries = captureJournal.readAll().filter { it.media.sessionKey == sessionKey }
        val journalIds = entries.mapTo(hashSetOf()) { it.media.id }
        val memoryOnlyRequests = synchronized(pendingUriRequestsLock) {
            pendingUriRequests.values.filter {
                it.mediaSeed.sessionKey == sessionKey && it.mediaSeed.id !in journalIds
            }
        }
        for (request in memoryOnlyRequests) {
            submitUriCapture(request.uri, request.mediaSeed).await()
        }
        for (entry in entries) {
            when {
                entry.discardedByRetention -> discardRejectedCapture(entry)
                entry.sourceUri != null -> submitUriCapture(entry).await()
                entry.readyToIngest -> submitReadyCapture(entry).await()
                else -> {
                    if (MediaPersistenceOwner.activeCameraCaptures.contains(entry.media.id)) {
                        throw IOException("La captura de cámara todavía está en curso.")
                    }
                    val source = File(entry.sourceFilePath)
                    if (source.isFile && source.length() > 0L) {
                        // Explicit user retry is the recovery boundary for a
                        // non-empty camera output whose completion marker failed.
                        submitReadyCapture(entry.copy(readyToIngest = true)).await()
                    } else {
                        // Empty interrupted captures can be removed safely on
                        // an explicit retry. Do not report a failed retry after
                        // the journal has already been cleared.
                        abandonEmptyCaptureAsync(entry).await()
                    }
                }
            }
        }
    }

    /** Resolves durable log identity for UI rows without touching Room on Main. */
    suspend fun pendingCaptureRetryAlbums(): List<WorkoutMediaPendingRetryAlbum> = withContext(Dispatchers.IO) {
        pendingCaptureRetryState.value.capturesById.values
            .mapNotNull { capture ->
                val key = capture.entry.media.sessionKey?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                key to capture
            }
            .groupBy({ it.first }, { it.second })
            .map { (sessionKey, captures) ->
                val newest = captures.maxByOrNull { it.entry.media.createdAtMs }!!
                val associationLogId = try {
                    db.workoutMediaSessionAssociationDao()
                        .getBySessionKey(sessionKey)
                        ?.workoutLogId
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A transient database read must not kill the albums state
                    // collector or hide the retry row. Frozen journal metadata
                    // remains a safe fallback until Room can be queried again.
                    null
                }
                val logId = associationLogId ?: captures.firstNotNullOfOrNull {
                    it.entry.media.workoutLogId?.takeIf(String::isNotBlank)
                }
                val media = newest.entry.media
                WorkoutMediaPendingRetryAlbum(
                    sessionKey = sessionKey,
                    workoutLogId = logId,
                    programId = media.programId.orEmpty(),
                    sessionId = media.sessionId.orEmpty(),
                    sessionName = media.sessionName,
                    createdAtMs = media.createdAtMs,
                    pendingCount = captures.size,
                    errorMessage = newest.errorMessage,
                )
            }
            .sortedByDescending { it.createdAtMs }
    }

    suspend fun importLegacyIfNeeded(force: Boolean = false): WorkoutMediaImportResult =
        withContext(Dispatchers.IO) { importer.importIfNeeded(force) }

    suspend fun upsert(media: WorkoutMedia) = withContext(Dispatchers.IO) {
        persistWithAssociation(media)
        Unit
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
        db.withTransaction {
            db.workoutMediaSessionAssociationDao().bind(sessionKey, workoutLogId)
            db.workoutMediaDao().attachSessionKeyToLog(sessionKey, workoutLogId)
        }
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
        destinationExtension: String? = null,
    ): WorkoutMedia? = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() <= 0L) throw IOException("El archivo de captura todavía no está disponible.")
        val createdAtMs = seed.createdAtMs.takeIf { it > 0L }
            ?: source.lastModified().takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val id = seed.id.ifBlank { UUID.randomUUID().toString() }
        db.workoutMediaDao().getById(id)?.let { existing ->
            return@withContext persistWithAssociation(existing.toWorkoutMedia())
        }
        val dest = if (store.isManagedPath(source)) {
            source
        } else {
            val extension = destinationExtension ?: source.extension
            val candidate = store.destinationFile(id, kind, createdAtMs, extension)
            if (candidate.exists() && !candidate.delete()) {
                throw IOException("No se pudo reintentar la copia privada del medio.")
            }
            store.copyIntoStore(source, id, kind, createdAtMs, extension)
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
        val persisted = persistWithAssociation(media)
        if (moveIfUnmanaged && source.canonicalFile != dest.canonicalFile && source.exists() && !source.delete()) {
            throw IOException("El medio quedó guardado, pero no se pudo limpiar su copia temporal.")
        }
        persisted
    }

    private fun enqueueTracked(
        entry: WorkoutMediaCaptureJournalEntry,
        failureMessage: String,
        work: suspend () -> WorkoutMedia?,
    ): Deferred<WorkoutMedia?> =
        synchronized(inFlightLock) {
            val id = entry.media.id
            inFlight[id]?.let { return@synchronized it }
            // Reserve before creating/starting the worker and install all terminal
            // state updates inside that worker, before its Deferred is completed.
            val attempt = MediaPersistenceOwner.reserveAttempt(id)
            lateinit var deferred: Deferred<WorkoutMedia?>
            deferred = MediaPersistenceOwner.scope.async(start = CoroutineStart.LAZY) {
                try {
                    work().also { MediaPersistenceOwner.clearPendingIfCurrent(id, attempt) }
                } catch (cancelled: CancellationException) {
                    publishCaptureFailure(entry, failureMessage, attempt)
                    throw cancelled
                } catch (failure: Exception) {
                    publishCaptureFailure(entry, failureMessage, attempt)
                    throw failure
                } finally {
                    synchronized(inFlightLock) {
                        if (inFlight[id] === deferred) inFlight.remove(id)
                    }
                }
            }
            inFlight[id] = deferred
            deferred.start()
            deferred
        }

    /** Runs inside the app-owned IO job, before its failure can reach an awaiter. */
    private fun publishCaptureFailure(
        entry: WorkoutMediaCaptureJournalEntry,
        failureMessage: String,
        attempt: Long,
    ) {
        if (entry.discardedByRetention) {
            MediaPersistenceOwner.clearPendingIfCurrent(entry.media.id, attempt)
            return
        }
        val durableEntry = runCatching { captureJournal.read(entry.media.id) }.getOrNull() ?: entry
        MediaPersistenceOwner.recordFailureIfCurrent(entry.media.id, attempt, durableEntry, failureMessage)
    }

    private suspend fun persistJournalEntry(entry: WorkoutMediaCaptureJournalEntry): WorkoutMedia {
        check(entry.readyToIngest) { "La captura todavía no terminó." }
        val existing = db.workoutMediaDao().getById(entry.media.id)
        if (existing != null) {
            val media = persistWithAssociation(existing.toWorkoutMedia())
            cleanupCommittedCapture(entry, media)
            return media
        }
        val source = ensureSourceFile(entry)
        val saved = ingestFile(
            source = source,
            kind = entry.media.kind,
            seed = entry.media,
            moveIfUnmanaged = false,
            destinationExtension = entry.resolvedExtension,
        ) ?: throw IOException("No se pudo guardar el medio de la sesión.")
        cleanupCommittedCapture(entry, saved)
        return saved
    }

    private suspend fun persistUriCapture(
        uri: Uri,
        mediaSeed: WorkoutMedia,
        existingEntry: WorkoutMediaCaptureJournalEntry? = null,
    ): WorkoutMedia {
        val source = existingEntry?.let { File(it.sourceFilePath) }
            ?: captureJournal.pendingSourceFile(mediaSeed.id, "pending")
        val entry = existingEntry ?: WorkoutMediaCaptureJournalEntry(
            media = mediaSeed.copy(filePath = source.absolutePath, kind = WorkoutMediaKind.PHOTO),
            sourceFilePath = source.absolutePath,
            sourceUri = uri.toString(),
            deleteSourceAfterCommit = true,
        )
        if (entry.sourceUri != uri.toString() || entry.media.id != mediaSeed.id) {
            throw IOException("La captura pendiente no coincide con el medio seleccionado.")
        }

        // A crash can occur after Room commits and before the journal is removed.
        // Resolve that idempotently before touching the URI again; providers may
        // revoke access after the durable row has already been written.
        db.workoutMediaDao().getById(mediaSeed.id)?.let { existing ->
            val saved = persistWithAssociation(existing.toWorkoutMedia())
            existingEntry?.let { cleanupCommittedCapture(it, saved) }
            synchronized(pendingUriRequestsLock) { pendingUriRequests.remove(mediaSeed.id) }
            return saved
        }

        // The marker must exist before resolver calls: getType or URI access can
        // fail transiently, and the user retry must retain this exact UUID.
        captureJournal.writeCapturing(entry)
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val uriFormat = if (existingEntry?.readyToIngest == true) {
            // A READY URI entry owns its MIME decision. New markers persist both
            // kind and extension; older markers may recover the extension from
            // the source bytes before consulting a still-available provider.
            val extension = entry.resolvedExtension?.let { stored ->
                supportedExtension(stored)
                    ?: throw IOException("El registro pendiente contiene una extensión no compatible.")
            } ?: supportedExtension(source.extension)
                ?: detectSupportedExtension(source)
                ?: resolveUriMimeType(appContext.contentResolver.getType(uri))?.extension
                ?: throw IOException("No se pudo recuperar el formato del medio pendiente. Podés reintentar.")
            val detectedKind = kindForExtension(extension)
            if (detectedKind != null && detectedKind != entry.media.kind) {
                throw IOException("El formato del medio pendiente no coincide con su tipo guardado.")
            }
            UriMediaFormat(entry.media.kind, extension)
        } else {
            val mime = appContext.contentResolver.getType(uri)
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IOException("No se pudo identificar el tipo del medio seleccionado. Podés reintentar.")
            resolveUriMimeType(mime)
                ?: throw IOException("El tipo del medio seleccionado no es compatible. Podés reintentar.")
        }
        val resolvedEntry = entry.copy(
            media = entry.media.copy(kind = uriFormat.kind),
            resolvedExtension = uriFormat.extension,
        )
        ensureSourceFile(resolvedEntry)
        // The durable READY marker records the resolved kind. A failed MIME
        // lookup leaves the earlier capturing marker in place for same-UUID retry.
        captureJournal.writeReady(resolvedEntry)
        val saved = persistJournalEntry(resolvedEntry.copy(readyToIngest = true))
        synchronized(pendingUriRequestsLock) { pendingUriRequests.remove(mediaSeed.id) }
        return saved
    }

    private fun resolveUriMimeType(rawMime: String?): UriMediaFormat? {
        val mime = rawMime
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return when (mime) {
            "image/png" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "png")
            "image/jpeg", "image/jpg", "image/pjpeg" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "jpg")
            "image/webp" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "webp")
            "image/gif" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "gif")
            "image/heic", "image/heic-sequence" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "heic")
            "image/heif", "image/heif-sequence" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "heif")
            "image/avif", "image/avif-sequence" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "avif")
            "image/bmp", "image/x-ms-bmp" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "bmp")
            "image/tiff", "image/x-tiff" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "tif")
            "image/svg+xml" -> UriMediaFormat(WorkoutMediaKind.PHOTO, "svg")
            "video/mp4" -> UriMediaFormat(WorkoutMediaKind.VIDEO, "mp4")
            "video/webm" -> UriMediaFormat(WorkoutMediaKind.VIDEO, "webm")
            "video/quicktime" -> UriMediaFormat(WorkoutMediaKind.VIDEO, "mov")
            "video/x-m4v" -> UriMediaFormat(WorkoutMediaKind.VIDEO, "m4v")
            "video/3gpp" -> UriMediaFormat(WorkoutMediaKind.VIDEO, "3gp")
            else -> {
                // Preserve the prior image/* and video/* picker contract while
                // ensuring provider MIME text cannot become an unsafe path extension.
                val kind = when {
                    mime.startsWith("image/") -> WorkoutMediaKind.PHOTO
                    mime.startsWith("video/") -> WorkoutMediaKind.VIDEO
                    else -> return null
                }
                val extension = android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mime)
                    ?.let(::supportedExtension)
                    ?: mime.substringAfter('/', "")
                        .filter { it in 'a'..'z' || it in '0'..'9' }
                        .take(8)
                        .let(::supportedExtension)
                    ?: return null
                UriMediaFormat(kind, extension)
            }
        }
    }

    private fun kindForExtension(extension: String): WorkoutMediaKind? = when {
        extension in VIDEO_EXTENSIONS -> WorkoutMediaKind.VIDEO
        extension in PHOTO_EXTENSIONS -> WorkoutMediaKind.PHOTO
        else -> null
    }

    private fun supportedExtension(extension: String): String? {
        val normalized = extension.lowercase()
        if (normalized in TEMPORARY_EXTENSIONS) return null
        return normalized.takeIf { candidate ->
            candidate.length in 1..8 && candidate.all { it in 'a'..'z' || it in '0'..'9' }
        }
    }

    /** Sniff durable legacy pending sources on the repository IO lane. */
    private fun detectSupportedExtension(source: File): String? {
        if (!source.isFile || source.length() < 8L) return null
        val header = ByteArray(32)
        val count = source.inputStream().use { input -> input.read(header) }
        if (count < 8) return null
        val prefix = header.copyOf(count)
        if (prefix.hasPrefix(PNG_SIGNATURE)) return "png"
        if (prefix[0] == 0xFF.toByte() && prefix[1] == 0xD8.toByte() && prefix[2] == 0xFF.toByte()) {
            return "jpg"
        }
        if (count >= 12 && prefix.copyOfRange(0, 4).contentEquals(RIFF_SIGNATURE) &&
            prefix.copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE)
        ) return "webp"
        if (prefix.copyOfRange(0, 6).contentEquals(GIF87_SIGNATURE) ||
            prefix.copyOfRange(0, 6).contentEquals(GIF89_SIGNATURE)
        ) return "gif"
        if (prefix[0] == 0x42.toByte() && prefix[1] == 0x4D.toByte()) return "bmp"
        if (prefix.copyOfRange(0, 4).contentEquals(TIFF_LE_SIGNATURE) ||
            prefix.copyOfRange(0, 4).contentEquals(TIFF_BE_SIGNATURE)
        ) return "tif"
        if (prefix.hasPrefix(WEBM_SIGNATURE)) return "webm"
        if (count >= 12 && prefix.copyOfRange(4, 8).contentEquals(FTYP_MARKER)) {
            val brand = String(prefix, 8, 4, Charsets.US_ASCII).lowercase()
            return when {
                brand.startsWith("avif") || brand.startsWith("avis") -> "avif"
                brand in HEIC_BRANDS -> "heic"
                brand in HEIF_BRANDS -> "heif"
                brand.startsWith("qt") -> "mov"
                brand.startsWith("m4v") -> "m4v"
                brand.startsWith("3gp") -> "3gp"
                else -> "mp4"
            }
        }
        return null
    }

    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private data class UriMediaFormat(
        val kind: WorkoutMediaKind,
        val extension: String,
    )

    private fun rememberPendingUriRequest(uri: Uri, mediaSeed: WorkoutMedia) {
        // Journal entries carry their private temp path and may have a resolved
        // MIME kind, while the picker request intentionally keeps neither.
        val frozenSeed = mediaSeed.copy(filePath = "", kind = WorkoutMediaKind.PHOTO)
        val request = PendingUriCaptureRequest(uri = uri, mediaSeed = frozenSeed)
        synchronized(pendingUriRequestsLock) {
            val previous = pendingUriRequests[mediaSeed.id]
            check(previous == null || previous == request) {
                "A media UUID cannot be reused for different URI metadata."
            }
            if (previous == null) pendingUriRequests[mediaSeed.id] = request
        }
    }

    private data class PendingUriCaptureRequest(
        val uri: Uri,
        val mediaSeed: WorkoutMedia,
    )

    private fun cleanupCommittedCapture(entry: WorkoutMediaCaptureJournalEntry, media: WorkoutMedia) {
        if (File(media.filePath).canonicalFile != File(entry.sourceFilePath).canonicalFile) {
            captureJournal.deleteOwnedSource(entry)
        }
        captureJournal.remove(entry.media.id)
    }

    private suspend fun ensureSourceFile(entry: WorkoutMediaCaptureJournalEntry): File = withContext(Dispatchers.IO) {
        val source = File(entry.sourceFilePath).canonicalFile
        if (!store.isManagedPath(source) && !captureJournal.ownsSource(source)) {
            throw IOException("La captura pendiente apunta fuera del almacenamiento privado.")
        }
        if (source.isFile && source.length() > 0L) return@withContext source
        val uriText = entry.sourceUri ?: throw IOException("El archivo de captura todavía no está disponible.")
        val uri = Uri.parse(uriText)
        val parent = source.parentFile ?: throw IOException("No se pudo preparar el almacenamiento de la captura.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("No se pudo preparar el almacenamiento de la captura.")
        val temporary = File(parent, "${source.name}.${UUID.randomUUID()}.part")
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el medio seleccionado.")
            input.use { stream ->
                FileOutputStream(temporary).use { output ->
                    stream.copyTo(output)
                    output.fd.sync()
                }
            }
            if (temporary.length() <= 0L) throw IOException("El medio seleccionado está vacío.")
            if (source.exists() && !source.delete()) throw IOException("No se pudo reemplazar la copia incompleta.")
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }

        var renameError: Exception? = try {
            Os.rename(temporary.absolutePath, source.absolutePath)
            null
        } catch (error: Exception) {
            error
        }
        if (!temporary.exists() && source.isFile) return@withContext source

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                AtomicUriSourceMove.replace(temporary, source)
            } catch (error: Exception) {
                if (source.isFile && !temporary.exists()) return@withContext source
                if (renameError == null) renameError = error else renameError?.addSuppressed(error)
            }
        }
        if (temporary.exists() || !source.isFile) {
            // Keep the complete .part file so a retry can finish safely.
            throw IOException("No se pudo completar la copia temporal del medio seleccionado.", renameError)
        }
        source
    }

    private suspend fun persistWithAssociation(media: WorkoutMedia): WorkoutMedia = db.withTransaction {
        val association = media.sessionKey?.takeIf { it.isNotBlank() }
            ?.let { db.workoutMediaSessionAssociationDao().getBySessionKey(it) }
        val associatedLogId = association?.workoutLogId
        if (!associatedLogId.isNullOrBlank() &&
            !media.workoutLogId.isNullOrBlank() &&
            media.workoutLogId != associatedLogId
        ) {
            throw IOException("La captura ya pertenece a otra sesión finalizada.")
        }
        val linkedLogId = associatedLogId ?: media.workoutLogId
        val linkedLog = linkedLogId?.let { id ->
            db.workoutLogDao().getById(id)?.toWorkoutLog()
                ?: if (association != null) throw IOException("No se encontró el log asociado a la captura.") else null
        }
        val persisted = media.copy(
            workoutLogId = linkedLogId,
            isPr = media.isPr || (linkedLog?.let { WorkoutMediaPrFlags.idsToMarkForLog(listOf(media), it).isNotEmpty() } == true),
        )
        db.workoutMediaDao().upsert(persisted.toEntity())
        persisted
    }

    private suspend fun recoverJournalEntries(): WorkoutMediaCaptureRecoveryResult {
        val entries = try {
            captureJournal.readAll()
        } catch (error: Exception) {
            MediaPersistenceOwner.markJournalUnreadable()
            MediaPersistenceOwner.markRecoveryFinished()
            return WorkoutMediaCaptureRecoveryResult(unreadableJournal = true)
        }
        val failed = linkedSetOf<String>()
        for (entry in entries) {
            if (!entry.readyToIngest && entry.sourceUri == null &&
                MediaPersistenceOwner.activeCameraCaptures.contains(entry.media.id)
            ) continue
            try {
                submitRecoveryCapture(entry).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                entry.media.sessionKey?.let(failed::add)
                // submitRecoveryCapture owns failure publication before its
                // Deferred becomes failed. Do not republish this stale snapshot.
            }
        }
        MediaPersistenceOwner.markRecoveryFinished()
        return WorkoutMediaCaptureRecoveryResult(failedSessionKeys = failed)
    }

    /** Recovery uses the same UUID worker/token as user retries and capture callbacks. */
    private fun submitRecoveryCapture(entry: WorkoutMediaCaptureJournalEntry): Deferred<WorkoutMedia?> =
        enqueueTracked(entry, captureRetryMessage(entry)) {
            db.workoutMediaDao().getById(entry.media.id)?.let { existing ->
                val saved = persistWithAssociation(existing.toWorkoutMedia())
                cleanupCommittedCapture(entry, saved)
                return@enqueueTracked saved
            }
            when {
                entry.discardedByRetention -> {
                    discardRejectedCapture(entry)
                    null
                }
                entry.sourceUri != null -> persistUriCapture(Uri.parse(entry.sourceUri), entry.media, entry)
                entry.readyToIngest -> persistJournalEntry(entry)
                else -> {
                    val source = File(entry.sourceFilePath)
                    if (source.isFile && source.length() > 0L) {
                        // A process restart cannot prove CameraX finished writing
                        // this file. Keep the capturing marker and let an explicit
                        // user retry promote it to READY after the writer is gone.
                        throw IOException("La captura de cámara no se marcó como finalizada. Reintentá desde el historial.")
                    } else {
                        // A non-READY camera journal may belong to an external
                        // camera activity whose ActivityResult is being restored
                        // after process death. Preserve even an empty marker so
                        // that callback can resolve the UUID and clean it safely.
                        throw IOException("La captura de cámara espera el resultado de la actividad. Reintentá desde el historial.")
                    }
                }
            }
        }

    private fun captureRetryMessage(entry: WorkoutMediaCaptureJournalEntry): String = when {
        entry.sourceUri != null -> "No se pudo recuperar el medio seleccionado. Podés reintentar."
        entry.media.kind == WorkoutMediaKind.VIDEO -> "No se pudo guardar el vídeo. Podés reintentar."
        else -> "No se pudo guardar la foto. Podés reintentar."
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
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "3gp")
        private val TEMPORARY_EXTENSIONS = setOf("pending", "capturing", "tmp", "part")
        private val PHOTO_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "heic", "heics", "heif", "heifs",
            "avif", "avifs", "bmp", "tif", "tiff", "svg",
        )
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val WEBM_SIGNATURE = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        private val RIFF_SIGNATURE = byteArrayOf(0x52, 0x49, 0x46, 0x46)
        private val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)
        private val GIF87_SIGNATURE = "GIF87a".toByteArray(Charsets.US_ASCII)
        private val GIF89_SIGNATURE = "GIF89a".toByteArray(Charsets.US_ASCII)
        private val TIFF_LE_SIGNATURE = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
        private val TIFF_BE_SIGNATURE = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)
        private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx")
        private val HEIF_BRANDS = setOf("mif1", "msf1")
        private val FTYP_MARKER = byteArrayOf(0x66, 0x74, 0x79, 0x70)
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

@TargetApi(Build.VERSION_CODES.O)
private object AtomicUriSourceMove {
    fun replace(source: File, target: File) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

/** Process-owned IO survives WorkoutViewModel cancellation; the journal survives process death. */
internal object MediaPersistenceOwner {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val externalCameraResultLock = Any()
    val externalCameraResults = mutableMapOf<String, Deferred<WorkoutMedia?>>()
    val activeCameraCaptures = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val pendingCaptureRetryState = MutableStateFlow(WorkoutMediaPendingCaptureState())
    private val attemptLock = Any()
    private val attemptSequence = AtomicLong()
    private val currentAttemptById = mutableMapOf<String, Long>()

    /** Globally monotonic tokens prevent a late completion from matching a later retry. */
    fun reserveAttempt(id: String): Long = synchronized(attemptLock) {
        attemptSequence.incrementAndGet().also { currentAttemptById[id] = it }
    }

    fun recordFailureIfCurrent(
        id: String,
        attempt: Long,
        entry: WorkoutMediaCaptureJournalEntry,
        errorMessage: String,
    ) = synchronized(attemptLock) {
        if (currentAttemptById[id] != attempt) return@synchronized
        currentAttemptById.remove(id)
        if (entry.discardedByRetention) return@synchronized
        pendingCaptureRetryState.update { current ->
            current.copy(capturesById = current.capturesById + (
                id to WorkoutMediaPendingCapture(entry, errorMessage)
            ))
        }
    }

    fun clearPendingIfCurrent(id: String, attempt: Long) = synchronized(attemptLock) {
        if (currentAttemptById[id] != attempt) return@synchronized
        currentAttemptById.remove(id)
        pendingCaptureRetryState.update { current ->
            if (id !in current.capturesById) current
            else current.copy(capturesById = current.capturesById - id)
        }
    }

    fun releaseAttemptIfCurrent(id: String, attempt: Long) = synchronized(attemptLock) {
        if (currentAttemptById[id] == attempt) currentAttemptById.remove(id)
    }

    fun markJournalUnreadable() {
        pendingCaptureRetryState.update { it.copy(journalUnreadable = true) }
    }

    fun markRecoveryFinished() {
        pendingCaptureRetryState.update { it.copy(recoveryFinished = true) }
    }
}
