package com.example.kpkn.screens.workout

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
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
import com.example.kpkn.data.media.WorkoutAlbumGrouping
import com.example.kpkn.data.media.WorkoutMediaFinalizePolicy
import com.example.kpkn.data.media.PoseTrajectoryAnalyzer
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * VM-scoped capture: CameraX UseCases survive pager dispose, Finalize never deletes a
 * valid file, and thumbs/duration are generated with MediaMetadataRetriever.
 */
class WorkoutMediaCaptureController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val repository: WorkoutMediaRepository,
    private val sessionMeta: () -> WorkoutMediaSessionMeta,
    private val poseTrajectoryEnabled: () -> Boolean = { false },
) {
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
    private var pendingVideoFile: File? = null
    private var pendingVideoRequest: WorkoutMediaCaptureRequest? = null

    init {
        ensureLegacyImport()
        refreshSessionMedia()
    }

    fun ensureLegacyImport() {
        if (!importStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            runCatching { repository.importLegacyIfNeeded() }
            refreshSessionMedia()
        }
    }

    fun refreshSessionMedia() {
        scope.launch(Dispatchers.IO) {
            val key = sessionMeta().sessionKey
            _sessionMedia.value = repository.listForSessionKey(key)
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
        val dest = repository.store().destinationFile(id, WorkoutMediaKind.PHOTO, createdAtMs)
        val options = ImageCapture.OutputFileOptions.Builder(dest).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        persistCaptured(dest, WorkoutMediaKind.PHOTO, request, id, createdAtMs, meta)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _captureError.value = "No se pudo guardar la foto"
                    runCatching { dest.delete() }
                }
            },
        )
    }

    fun toggleVideo(audioGranted: Boolean, request: WorkoutMediaCaptureRequest) {
        if (_isRecording.value) {
            stopVideo()
            return
        }
        val meta = sessionMeta()
        val id = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        val dest = repository.store().destinationFile(id, WorkoutMediaKind.VIDEO, createdAtMs)
        pendingVideoFile = dest
        pendingVideoRequest = request
        val pending = videoCapture.output
            .prepareRecording(appContext, FileOutputOptions.Builder(dest).build())
            .apply {
                if (audioGranted) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(appContext)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    _isRecording.value = false
                    val file = pendingVideoFile ?: dest
                    val captureRequest = pendingVideoRequest ?: request
                    pendingVideoFile = null
                    pendingVideoRequest = null
                    val keep = WorkoutMediaFinalizePolicy.shouldRetainFile(
                        fileLengthBytes = file.length(),
                        hasError = event.hasError(),
                        errorCode = event.error,
                    )
                    if (!keep) {
                        _captureError.value = "No se pudo guardar el vídeo"
                        runCatching { file.delete() }
                        return@start
                    }
                    if (event.hasError()) {
                        _captureError.value = null
                    } else {
                        _captureError.value = null
                    }
                    scope.launch {
                        persistCaptured(
                            file = file,
                            kind = WorkoutMediaKind.VIDEO,
                            request = captureRequest,
                            id = id,
                            createdAtMs = createdAtMs,
                            meta = meta,
                        )
                    }
                }
            }
        activeRecording = pending
        _isRecording.value = true
        _captureError.value = null
    }

    fun stopVideo() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun stopIfRecording() {
        if (_isRecording.value) stopVideo()
    }

    fun ingestUri(uri: Uri, request: WorkoutMediaCaptureRequest) {
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                val mime = appContext.contentResolver.getType(uri).orEmpty()
                val kind = if (mime.startsWith("video/")) WorkoutMediaKind.VIDEO else WorkoutMediaKind.PHOTO
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?: if (kind == WorkoutMediaKind.VIDEO) "mp4" else "jpg"
                val temp = File(appContext.cacheDir, "workout_media_pick_${UUID.randomUUID()}.$ext")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                kind to temp
            } ?: return@launch
            persistCaptured(
                file = copied.second,
                kind = copied.first,
                request = request,
                id = UUID.randomUUID().toString(),
                createdAtMs = System.currentTimeMillis(),
                meta = sessionMeta(),
                deleteSourceAfter = true,
            )
        }
    }

    fun ingestLegacyPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            val meta = sessionMeta()
            val existing = repository.listForSessionKey(meta.sessionKey).map { it.filePath }.toSet()
            paths.forEach { path ->
                val file = File(path)
                if (!file.isFile || file.length() <= 0L) return@forEach
                if (repository.store().isManagedPath(file)) return@forEach
                val canonical = com.example.kpkn.data.media.WorkoutMediaStore.canonicalPath(file)
                val stableId = com.example.kpkn.data.media.WorkoutMediaLegacyImporter.legacyId(canonical)
                if (repository.getById(stableId) != null) return@forEach
                persistCaptured(
                    file = file,
                    kind = WorkoutMediaStoreKind(file),
                    request = WorkoutMediaCaptureRequest(),
                    id = stableId,
                    createdAtMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                    meta = meta,
                    deleteSourceAfter = false,
                )
            }
        }
    }

    fun delete(id: String) {
        scope.launch {
            repository.delete(id)
            refreshSessionMedia()
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
        val seed = WorkoutMedia(
            id = id,
            kind = kind,
            filePath = file.absolutePath,
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
        val saved = repository.ingestFile(
            source = file,
            kind = kind,
            seed = seed,
            moveIfUnmanaged = deleteSourceAfter,
        )
        if (saved == null) {
            _captureError.value = if (kind == WorkoutMediaKind.VIDEO) {
                "No se pudo guardar el vídeo"
            } else {
                "No se pudo guardar la foto"
            }
        } else {
            _captureError.value = null
            if (kind == WorkoutMediaKind.VIDEO && poseTrajectoryEnabled()) {
                scope.launch(Dispatchers.IO) {
                    attachPoseTrack(saved, request)
                    refreshSessionMedia()
                }
            }
        }
        refreshSessionMedia()
    }

    private suspend fun attachPoseTrack(saved: WorkoutMedia, request: WorkoutMediaCaptureRequest) {
        val video = File(saved.filePath)
        val sidecar = repository.store().poseSidecarFile(saved.id, saved.createdAtMs)
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
