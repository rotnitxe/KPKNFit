package com.example.kpkn.screens.workout

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.kpkn.data.media.WorkoutAlbumGrouping
import com.example.kpkn.data.media.PoseTrajectoryAnalyzer
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class WorkoutMediaSessionMeta(
    val sessionKey: String,
    val programId: String,
    val sessionId: String,
    val sessionName: String?,
)

data class WorkoutMediaCaptureRequest(
    val exerciseId: String? = null,
    val canonicalExerciseId: String? = null,
    val exerciseName: String? = null,
    val setIndex: Int? = null,
    val side: String? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isPr: Boolean = false,
)

/**
 * CameraX remains UI-scoped, while each accepted file and its metadata are journaled
 * and ingested by the repository owner so VM teardown cannot cancel persistence.
 */
class WorkoutMediaCaptureController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val repository: WorkoutMediaRepository,
    private val sessionMeta: () -> WorkoutMediaSessionMeta,
    private val poseTrajectoryEnabled: () -> Boolean = { false },
) {
    data class ExternalPhotoCapture internal constructor(
        val uri: Uri,
        internal val entry: WorkoutMediaCaptureJournalEntry,
        internal val request: WorkoutMediaCaptureRequest,
    ) {
        val id: String get() = entry.media.id
    }

    val imageCapture: ImageCapture = ImageCapture.Builder().build()

    private var recorder: Recorder = Recorder.Builder()
        .setQualitySelector(hdThenSdSelector())
        .build()
    var videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)
        private set

    private val _usingSdFallback = MutableStateFlow(false)
    val usingSdFallback: StateFlow<Boolean> = _usingSdFallback.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _captureError = MutableStateFlow<String?>(null)
    val captureError: StateFlow<String?> = _captureError.asStateFlow()

    private val _sessionMedia = MutableStateFlow<List<WorkoutMedia>>(emptyList())
    val sessionMedia: StateFlow<List<WorkoutMedia>> = _sessionMedia.asStateFlow()

    private val _showSessionAlbumSheet = MutableStateFlow(false)
    val showSessionAlbumSheet: StateFlow<Boolean> = _showSessionAlbumSheet.asStateFlow()

    private val _requestOpenMediaFaceExerciseId = MutableStateFlow<String?>(null)
    val requestOpenMediaFaceExerciseId: StateFlow<String?> = _requestOpenMediaFaceExerciseId.asStateFlow()

    private val _bindGeneration = MutableStateFlow(0)
    val bindGeneration: StateFlow<Int> = _bindGeneration.asStateFlow()

    private val importStarted = AtomicBoolean(false)
    private var activeRecording: Recording? = null
    private var videoStartJob: kotlinx.coroutines.Job? = null

    init {
        ensureLegacyImport()
        scope.launch {
            try {
                val recovery = repository.recoverPendingCaptures().await()
                val key = sessionMeta().sessionKey
                if (recovery.unreadableJournal || key in recovery.failedSessionKeys) {
                    _captureError.value = "Hay una captura pendiente que requiere reintento."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = "No se pudo recuperar una captura pendiente."
            }
            refreshSessionMedia()
        }
        refreshSessionMedia()
    }

    fun ensureLegacyImport() {
        if (!importStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                repository.importLegacyIfNeeded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = "No se pudieron importar medios anteriores."
            }
            refreshSessionMedia()
        }
    }

    fun refreshSessionMedia() {
        scope.launch(Dispatchers.IO) {
            try {
                val key = sessionMeta().sessionKey
                _sessionMedia.value = repository.listForSessionKey(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = "No se pudieron cargar los medios de la sesión."
            }
        }
    }

    fun openAlbumSheet() {
        refreshSessionMedia()
        _showSessionAlbumSheet.value = true
    }

    fun dismissAlbumSheet() {
        _showSessionAlbumSheet.value = false
    }

    fun requestOpenMediaFace(exerciseId: String) {
        _requestOpenMediaFaceExerciseId.value = exerciseId.takeIf { it.isNotBlank() }
    }

    fun consumeOpenMediaFace() {
        _requestOpenMediaFaceExerciseId.value = null
    }

    fun isPoseTrajectoryEnabled(): Boolean = poseTrajectoryEnabled()

    /** Reserve the destination and journal frozen metadata before an external camera is launched. */
    suspend fun beginExternalPhotoCapture(
        request: WorkoutMediaCaptureRequest = WorkoutMediaCaptureRequest(),
    ): ExternalPhotoCapture? {
        val meta = sessionMeta()
        val id = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        var preparedEntry: WorkoutMediaCaptureJournalEntry? = null
        return try {
            withContext(Dispatchers.IO) {
                val file = repository.prepareCameraCaptureFile(id, WorkoutMediaKind.PHOTO, createdAtMs)
                val entry = captureEntry(file, WorkoutMediaKind.PHOTO, request, id, createdAtMs, meta)
                repository.beginCameraCapture(entry)
                preparedEntry = entry
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file,
                )
                ExternalPhotoCapture(uri = uri, entry = entry, request = request)
            }.also { _captureError.value = null }
        } catch (cancelled: CancellationException) {
            preparedEntry?.let { entry ->
                repository.markCameraCaptureCompleted(entry.media.id)
                repository.abandonEmptyCaptureAsync(entry)
            }
            throw cancelled
        } catch (error: Exception) {
            preparedEntry?.let { entry ->
                repository.markCameraCaptureCompleted(entry.media.id)
                repository.abandonEmptyCaptureAsync(entry)
            }
            _captureError.value = "No se pudo preparar la cámara. Podés reintentar."
            null
        }
    }

    /** Completion hands persistence to the repository-owned worker, independent of UI observer lifetime. */
    fun completeExternalPhotoCapture(
        capture: ExternalPhotoCapture,
        succeeded: Boolean,
    ): Deferred<com.example.kpkn.data.models.WorkoutMedia?>? {
        val entry = capture.entry
        if (succeeded) {
            _captureError.value = null
            val work = repository.submitReadyCapture(entry)
            observeDurableWrite(
                entry = entry,
                request = capture.request,
                work = work,
                failureMessage = "No se pudo guardar la foto. Podés reintentar.",
            )
            return work
        }

        repository.markCameraCaptureCompleted(entry.media.id)
        val cleanup = repository.abandonEmptyCaptureAsync(entry)
        scope.launch {
            try {
                cleanup.await()
                _captureError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = "La captura quedó pendiente. Podés reintentar."
            }
            refreshSessionMedia()
        }
        return null
    }

    /**
     * Resolves the external camera result using its saveable UUID, not an
     * Activity-scoped ticket object. Session and exercise fields come only from
     * the validated journal snapshot written before launching the camera.
     */
    fun completeExternalPhotoCaptureById(
        captureId: String,
        succeeded: Boolean,
    ): Deferred<WorkoutMedia?> {
        val work = repository.submitExternalCameraPhotoResult(captureId, succeeded)
        observeExternalPhotoResult(work, succeeded = succeeded, launchFailed = false)
        return work
    }

    fun failExternalCameraLaunch(captureId: String): Deferred<WorkoutMedia?> {
        val work = repository.submitExternalCameraPhotoResult(captureId, succeeded = false)
        observeExternalPhotoResult(work, succeeded = false, launchFailed = true)
        return work
    }

    private fun observeExternalPhotoResult(
        work: Deferred<WorkoutMedia?>,
        succeeded: Boolean,
        launchFailed: Boolean,
    ) {
        scope.launch {
            try {
                work.await()
                _captureError.value = if (launchFailed) {
                    "No se pudo abrir la cámara. Podés reintentar."
                } else {
                    null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _captureError.value = when {
                    launchFailed || !succeeded -> "La foto quedó pendiente. Podés reintentar."
                    else -> "No se pudo guardar la foto. Podés reintentar."
                }
            }
            refreshSessionMedia()
        }
    }

    fun fallbackVideoCaptureToSd(): VideoCapture<Recorder> {
        if (_usingSdFallback.value) return videoCapture
        recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
        _usingSdFallback.value = true
        _bindGeneration.value = _bindGeneration.value + 1
        return videoCapture
    }

    fun takePhoto(request: WorkoutMediaCaptureRequest) {
        val meta = sessionMeta()
        val id = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        scope.launch {
            var preparedEntry: WorkoutMediaCaptureJournalEntry? = null
            try {
                val entry = withContext(Dispatchers.IO) {
                    val dest = repository.prepareCameraCaptureFile(id, WorkoutMediaKind.PHOTO, createdAtMs)
                    captureEntry(dest, WorkoutMediaKind.PHOTO, request, id, createdAtMs, meta).also {
                        repository.beginCameraCapture(it)
                    }
                }
                preparedEntry = entry
                _captureError.value = null
                val dest = File(entry.sourceFilePath)
                val options = ImageCapture.OutputFileOptions.Builder(dest).build()
                imageCapture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(appContext),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            persistDurably(entry, request)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            repository.markCameraCaptureCompleted(entry.media.id)
                            // This checks the file on the repository IO owner; a non-empty
                            // output remains journaled for the visible manual retry action.
                            repository.abandonEmptyCaptureAsync(entry)
                            _captureError.value = "No se pudo guardar la foto. Podés reintentar."
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                repository.markCameraCaptureCompleted(id)
                throw cancelled
            } catch (error: Exception) {
                preparedEntry?.let { repository.markCameraCaptureCompleted(it.media.id) }
                _captureError.value = "No se pudo iniciar la captura de la foto."
                preparedEntry?.let(repository::abandonEmptyCaptureAsync)
            }
        }
    }

    fun toggleVideo(audioGranted: Boolean, request: WorkoutMediaCaptureRequest) {
        if (_isRecording.value) {
            stopVideo()
            return
        }
        if (videoStartJob?.isActive == true) return
        val meta = sessionMeta()
        val id = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        videoStartJob = scope.launch {
            var preparedEntry: WorkoutMediaCaptureJournalEntry? = null
            try {
                val entry = withContext(Dispatchers.IO) {
                    val dest = repository.prepareCameraCaptureFile(id, WorkoutMediaKind.VIDEO, createdAtMs)
                    captureEntry(dest, WorkoutMediaKind.VIDEO, request, id, createdAtMs, meta).also {
                        repository.beginCameraCapture(it)
                    }
                }
                preparedEntry = entry
                _captureError.value = null
                val dest = File(entry.sourceFilePath)
                val pendingRecording = videoCapture.output
                    .prepareRecording(appContext, FileOutputOptions.Builder(dest).build())
                    .apply {
                        if (audioGranted) withAudioEnabled()
                    }
                val pending = pendingRecording.start(ContextCompat.getMainExecutor(appContext)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            activeRecording = null
                            _isRecording.value = false
                            videoStartJob = null
                            _captureError.value = null
                            observeDurableWrite(
                                entry = entry,
                                request = request,
                                work = repository.submitFinalizedVideoCapture(entry, event.hasError(), event.error),
                                nullResultMessage = "No se pudo guardar el vídeo.",
                            )
                        }
                    }
                activeRecording = pending
                _isRecording.value = true
            } catch (cancelled: CancellationException) {
                repository.markCameraCaptureCompleted(id)
                throw cancelled
            } catch (error: Exception) {
                preparedEntry?.let { repository.markCameraCaptureCompleted(it.media.id) }
                _isRecording.value = false
                videoStartJob = null
                _captureError.value = "No se pudo iniciar la captura del vídeo."
                preparedEntry?.let(repository::abandonEmptyCaptureAsync)
            }
        }
    }

    fun stopVideo() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun stopIfRecording() {
        if (videoStartJob?.isActive == true) {
            videoStartJob?.cancel()
            videoStartJob = null
        }
        if (_isRecording.value) stopVideo()
    }

    fun ingestUri(uri: Uri, request: WorkoutMediaCaptureRequest) {
        val meta = sessionMeta()
        val id = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        val mediaSeed = captureMedia(
            filePath = "",
            kind = WorkoutMediaKind.PHOTO,
            request = request,
            id = id,
            createdAtMs = createdAtMs,
            meta = meta,
        )
        // Freeze the selection context and hand it to the process owner before
        // starting any VM-scoped observer. The worker journals before querying
        // MIME or opening the URI, so VM teardown cannot drop the selection.
        val observerEntry = WorkoutMediaCaptureJournalEntry(
            media = mediaSeed,
            sourceFilePath = "",
            sourceUri = uri.toString(),
            deleteSourceAfterCommit = true,
        )
        _captureError.value = null
        observeDurableWrite(
            entry = observerEntry,
            request = request,
            work = repository.submitUriCapture(uri, mediaSeed),
            failureMessage = "No se pudo guardar el medio seleccionado. Podés reintentar.",
        )
    }

    fun ingestLegacyPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            val meta = sessionMeta()
            paths.forEach { path ->
                val source = withContext(Dispatchers.IO) {
                    val file = File(path)
                    if (!file.isFile || file.length() <= 0L || repository.isManagedPath(file)) {
                        return@withContext null
                    }
                    val canonical = com.example.kpkn.data.media.WorkoutMediaStore.canonicalPath(file)
                    val stableId = com.example.kpkn.data.media.WorkoutMediaLegacyImporter.legacyId(canonical)
                    Triple(file, stableId, file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis())
                } ?: return@forEach
                val (file, stableId, createdAtMs) = source
                if (repository.getById(stableId) != null) return@forEach
                persistCaptured(
                    file = file,
                    kind = WorkoutMediaStoreKind(file),
                    request = WorkoutMediaCaptureRequest(),
                    id = stableId,
                    createdAtMs = createdAtMs,
                    meta = meta,
                    deleteSourceAfter = false,
                )
            }
        }
    }

    fun delete(id: String) {
        scope.launch {
            try {
                repository.delete(id)
                refreshSessionMedia()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = "No se pudo eliminar el medio."
            }
        }
    }

    fun retryPendingCaptures() {
        val key = sessionMeta().sessionKey
        scope.launch {
            try {
                repository.retryPendingCapturesForSession(key)
                _captureError.value = null
                refreshSessionMedia()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = "No se pudo recuperar el medio. Podés reintentar."
            }
        }
    }

    private suspend fun persistCaptured(
        file: File,
        kind: WorkoutMediaKind,
        request: WorkoutMediaCaptureRequest,
        id: String,
        createdAtMs: Long,
        meta: WorkoutMediaSessionMeta,
        deleteSourceAfter: Boolean = false,
    ) {
        val entry = captureEntry(file, kind, request, id, createdAtMs, meta, deleteSourceAfterCommit = deleteSourceAfter)
        try {
            repository.journalCapture(entry)
            observeDurableWrite(entry, request, repository.submitReadyCapture(entry))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _captureError.value = "No se pudo preparar el medio para guardarlo."
        }
    }

    /** Hand completed camera output to the process-owned IO worker immediately. */
    private fun persistDurably(
        entry: WorkoutMediaCaptureJournalEntry,
        request: WorkoutMediaCaptureRequest,
    ) {
        observeDurableWrite(entry, request, repository.submitReadyCapture(entry))
    }

    private fun captureEntry(
        file: File,
        kind: WorkoutMediaKind,
        request: WorkoutMediaCaptureRequest,
        id: String,
        createdAtMs: Long,
        meta: WorkoutMediaSessionMeta,
        sourceUri: String? = null,
        deleteSourceAfterCommit: Boolean = false,
    ): WorkoutMediaCaptureJournalEntry {
        val media = captureMedia(
            filePath = file.absolutePath,
            kind = kind,
            request = request,
            id = id,
            createdAtMs = createdAtMs,
            meta = meta,
        )
        return WorkoutMediaCaptureJournalEntry(
            media = media,
            sourceFilePath = file.absolutePath,
            sourceUri = sourceUri,
            deleteSourceAfterCommit = deleteSourceAfterCommit,
        )
    }

    private fun captureMedia(
        filePath: String,
        kind: WorkoutMediaKind,
        request: WorkoutMediaCaptureRequest,
        id: String,
        createdAtMs: Long,
        meta: WorkoutMediaSessionMeta,
    ): WorkoutMedia = WorkoutMedia(
            id = id,
            kind = kind,
            filePath = filePath,
            createdAtMs = createdAtMs,
            sessionKey = meta.sessionKey,
            programId = meta.programId,
            sessionId = meta.sessionId,
            sessionName = meta.sessionName,
            exerciseId = request.exerciseId,
            canonicalExerciseId = request.canonicalExerciseId ?: request.exerciseId,
            exerciseName = request.exerciseName,
            setIndex = request.setIndex,
            side = request.side,
            weightKg = request.weightKg,
            reps = request.reps,
            isPr = request.isPr,
        )

    private fun observeDurableWrite(
        entry: WorkoutMediaCaptureJournalEntry,
        request: WorkoutMediaCaptureRequest,
        work: Deferred<WorkoutMedia?>,
        nullResultMessage: String? = null,
        failureMessage: String? = null,
    ) {
        scope.launch {
            try {
                val saved = work.await()
                if (saved == null) {
                    if (nullResultMessage == null) throw IOException("El medio no se pudo guardar.")
                    _captureError.value = nullResultMessage
                    refreshSessionMedia()
                    return@launch
                }
                _captureError.value = null
                if (saved.kind == WorkoutMediaKind.VIDEO && poseTrajectoryEnabled()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { attachPoseTrack(saved, request) }
                        refreshSessionMedia()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = failureMessage ?: if (entry.media.kind == WorkoutMediaKind.VIDEO) {
                    "No se pudo guardar el vídeo. Podés reintentar."
                } else {
                    "No se pudo guardar la foto. Podés reintentar."
                }
            }
            refreshSessionMedia()
        }
    }

    private suspend fun attachPoseTrack(saved: WorkoutMedia, request: WorkoutMediaCaptureRequest) {
        val video = File(saved.filePath)
        val sidecar = repository.poseSidecarFile(saved.id, saved.createdAtMs)
        val family = TrajectoryFamily.fromRelatorName(
            relatorFamilyFrom(
                exerciseName = request.exerciseName.orEmpty(),
                jointIds = emptyList(),
                movementPatternId = null,
            ).name,
        )
        val track = runCatching {
            PoseTrajectoryAnalyzer().analyzeVideo(
                videoFile = video,
                mediaId = saved.id,
                family = family,
                sidecarFile = sidecar,
            )
        }.getOrNull() ?: return
        if (track.frames.isEmpty()) return
        repository.upsert(saved.copy(poseTrackPath = sidecar.absolutePath))
    }

    companion object {
        fun hdThenSdSelector(): QualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.SD, Quality.LOWEST),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
        )

        fun sessionKey(programId: String, sessionId: String, startTimeMs: Long): String =
            WorkoutAlbumGrouping.workoutMediaSessionKey(programId, sessionId, startTimeMs)
    }
}

private fun WorkoutMediaStoreKind(file: File): WorkoutMediaKind =
    com.example.kpkn.data.media.WorkoutMediaStore.kindOf(file)
