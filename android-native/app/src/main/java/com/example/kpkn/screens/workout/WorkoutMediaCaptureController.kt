package com.example.kpkn.screens.workout

import android.content.Context
import android.net.Uri
import android.view.Surface
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
import com.example.kpkn.data.media.DeferredMediaDelete
import com.example.kpkn.data.media.WorkoutAlbumGrouping
import com.example.kpkn.data.media.WorkoutMediaCaptureJournalEntry
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.data.work.AnalysisGate
import com.example.kpkn.screens.workout.technique.CaptureMetadataRecorder
import com.example.kpkn.screens.workout.technique.RecorderSession
import com.example.kpkn.screens.workout.technique.TechniqueCaptureMetadataInterval
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
    /** Definición y configuración del catálogo v2 del ejercicio (null = ejercicio fuera del catálogo o sin resolver). */
    val catalogDefinitionId: String? = null,
    val catalogConfigurationId: String? = null,
)

/** Resultado de persistencia, independiente del código Finalize de CameraX. */
sealed interface TechniqueRecordingResult {
    val errorMessage: String?

    data class Saved(val media: WorkoutMedia) : TechniqueRecordingResult {
        override val errorMessage: String? = null
    }
    data object Discarded : TechniqueRecordingResult {
        override val errorMessage = "No se pudo guardar el vídeo. Puedes volver a grabar."
    }
    data class Error(override val errorMessage: String = "No se pudo guardar el vídeo. Puedes reintentar.") : TechniqueRecordingResult
}

/** Espera el trabajo del repositorio; un fichero retenido aún debe quedar persistido. */
internal suspend fun awaitTechniqueRecordingResult(write: suspend () -> WorkoutMedia?): TechniqueRecordingResult = try {
    write()?.let { TechniqueRecordingResult.Saved(it) } ?: TechniqueRecordingResult.Discarded
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    TechniqueRecordingResult.Error()
}

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
    /** El esfuerzo («RIR 2», «Fallo») con el que se registró la serie de un medio, si la sesión en curso la tiene (álbum por ejercicio, LV8). */
    private val effortLabelProvider: (WorkoutMedia) -> String? = { null },
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

    private val _recordedDurationNanos = MutableStateFlow(0L)
    val recordedDurationNanos: StateFlow<Long> = _recordedDurationNanos.asStateFlow()

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
    private var techniqueMetadataInterval: TechniqueCaptureMetadataInterval? = null

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
            _captureError.value = "No se pudo preparar la cámara. Puedes reintentar."
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
                failureMessage = "No se pudo guardar la foto. Puedes reintentar.",
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
                _captureError.value = "La captura quedó pendiente. Puedes reintentar."
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
                    "No se pudo abrir la cámara. Puedes reintentar."
                } else {
                    null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _captureError.value = when {
                    launchFailed || !succeeded -> "La foto quedó pendiente. Puedes reintentar."
                    else -> "No se pudo guardar la foto. Puedes reintentar."
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
                            _captureError.value = "No se pudo guardar la foto. Puedes reintentar."
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
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            AnalysisGate.cameraBusy.value = true
                            _recordedDurationNanos.value = 0L
                        }
                        is VideoRecordEvent.Status -> {
                            _recordedDurationNanos.value = event.recordingStats.recordedDurationNanos
                        }
                        is VideoRecordEvent.Finalize -> {
                            activeRecording = null
                            _isRecording.value = false
                            videoStartJob = null
                            _captureError.value = null
                            _recordedDurationNanos.value = 0L
                            AnalysisGate.cameraBusy.value = false
                            observeDurableWrite(
                                entry = entry,
                                request = request,
                                work = repository.submitFinalizedVideoCapture(entry, event.hasError(), event.error),
                                nullResultMessage = "No se pudo guardar el vídeo.",
                            )
                        }
                    }
                }
                activeRecording = pending
                _isRecording.value = true
            } catch (cancelled: CancellationException) {
                AnalysisGate.cameraBusy.value = false
                repository.markCameraCaptureCompleted(id)
                throw cancelled
            } catch (error: Exception) {
                AnalysisGate.cameraBusy.value = false
                preparedEntry?.let { repository.markCameraCaptureCompleted(it.media.id) }
                _isRecording.value = false
                videoStartJob = null
                _captureError.value = "No se pudo iniciar la captura del vídeo."
                preparedEntry?.let(repository::abandonEmptyCaptureAsync)
            }
        }
    }

    /**
     * Graba técnica a pantalla completa (VT8a) con límite de duración configurable,
     * pausa de análisis en AnalysisGate y relectura de cifras y lado al finalizar.
     */
    fun startTechniqueRecording(
        requestProvider: () -> WorkoutMediaCaptureRequest,
        audioGranted: Boolean,
        session: RecorderSession,
        exercise: Exercise? = null,
        metadataRecorder: CaptureMetadataRecorder? = null,
        onFinalized: ((TechniqueRecordingResult) -> Unit)? = null,
        targetRotation: Int = Surface.ROTATION_0,
        maxRecordSeconds: Int = 180,
        plateDiameterOverrideMm: Int? = null,
        defaultPlateDiameterMm: Int = 450,
    ) {
        if (_isRecording.value) {
            stopVideo()
            return
        }
        if (videoStartJob?.isActive == true) return
        val meta = sessionMeta()
        val id = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        val initialRequest = requestProvider().withExerciseCaptureContext(exercise)
        val metadataInterval = TechniqueCaptureMetadataInterval(metadataRecorder, targetRotation)
        techniqueMetadataInterval = metadataInterval
        videoStartJob = scope.launch {
            var preparedEntry: WorkoutMediaCaptureJournalEntry? = null
            try {
                val entry = withContext(Dispatchers.IO) {
                    val dest = repository.prepareCameraCaptureFile(id, WorkoutMediaKind.VIDEO, createdAtMs)
                    captureEntry(dest, WorkoutMediaKind.VIDEO, initialRequest, id, createdAtMs, meta).also {
                        repository.beginCameraCapture(it)
                    }
                }
                preparedEntry = entry
                _captureError.value = null
                val dest = File(entry.sourceFilePath)
                val durationMs = (maxRecordSeconds.coerceAtLeast(5) * 1000L)
                val fileOutputOptions = FileOutputOptions.Builder(dest)
                    .setDurationLimitMillis(durationMs)
                    .build()
                val pendingRecording = session.videoCapture.output
                    .prepareRecording(appContext, fileOutputOptions)
                    .apply {
                        if (audioGranted) withAudioEnabled()
                    }
                val pending = pendingRecording.start(ContextCompat.getMainExecutor(appContext)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            if (!metadataInterval.onStarted()) return@start
                            AnalysisGate.cameraBusy.value = true
                            _recordedDurationNanos.value = 0L
                        }
                        is VideoRecordEvent.Status -> {
                            _recordedDurationNanos.value = event.recordingStats.recordedDurationNanos
                        }
                        is VideoRecordEvent.Finalize -> {
                            metadataInterval.stopCollecting()
                            if (techniqueMetadataInterval === metadataInterval) techniqueMetadataInterval = null
                            activeRecording = null
                            _isRecording.value = false
                            videoStartJob = null
                            _captureError.value = null
                            _recordedDurationNanos.value = 0L
                            AnalysisGate.cameraBusy.value = false

                            val finalRequest = requestProvider().withExerciseCaptureContext(exercise)

                            scope.launch(Dispatchers.IO) {
                                val result = awaitTechniqueRecordingResult {
                                    // 1. Guardar metadatos de captura (gravedad, focal y fps) en capture.json antes de entregar el vídeo
                                    metadataInterval.save(dest)

                                    // 2. Si el usuario seleccionó un diámetro distinto al de los ajustes, guardarlo con CaptureOverridesFile.update
                                    if (plateDiameterOverrideMm != null && plateDiameterOverrideMm != defaultPlateDiameterMm) {
                                        val captureFile = TechniqueSidecars.captureFile(dest)
                                        runCatching {
                                            CaptureOverridesFile.update(captureFile) { current ->
                                                current.copy(plateDiameterMm = plateDiameterOverrideMm)
                                            }
                                        }
                                    }

                                    // 3. Registrar el medio finalizado en el repositorio (el pipeline arrancará después de que exista capture.json)
                                    val finalEntry = captureEntry(
                                        file = dest,
                                        kind = WorkoutMediaKind.VIDEO,
                                        request = finalRequest,
                                        id = id,
                                        createdAtMs = createdAtMs,
                                        meta = meta,
                                    )

                                    repository.submitFinalizedVideoCapture(finalEntry, event.hasError(), event.error).await()
                                }

                                withContext(Dispatchers.Main) {
                                    _captureError.value = result.errorMessage
                                    refreshSessionMedia()
                                    onFinalized?.invoke(result)
                                }
                            }
                        }
                    }
                }
                activeRecording = pending
                _isRecording.value = true
            } catch (cancelled: CancellationException) {
                metadataInterval.stopCollecting()
                if (techniqueMetadataInterval === metadataInterval) techniqueMetadataInterval = null
                AnalysisGate.cameraBusy.value = false
                repository.markCameraCaptureCompleted(id)
                throw cancelled
            } catch (error: Exception) {
                metadataInterval.stopCollecting()
                if (techniqueMetadataInterval === metadataInterval) techniqueMetadataInterval = null
                AnalysisGate.cameraBusy.value = false
                preparedEntry?.let { repository.markCameraCaptureCompleted(it.media.id) }
                _isRecording.value = false
                videoStartJob = null
                _captureError.value = "No se pudo iniciar la captura del vídeo."
                preparedEntry?.let(repository::abandonEmptyCaptureAsync)
                onFinalized?.invoke(TechniqueRecordingResult.Error(_captureError.value!!))
            }
        }
    }

    fun stopVideo() {
        techniqueMetadataInterval?.stopCollecting()
        AnalysisGate.cameraBusy.value = false
        _recordedDurationNanos.value = 0L
        activeRecording?.stop()
        activeRecording = null
    }

    fun stopIfRecording() {
        techniqueMetadataInterval?.stopCollecting()
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
            failureMessage = "No se pudo guardar el medio seleccionado. Puedes reintentar.",
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

    /** Los medios de un ejercicio de TODAS las sesiones (álbum por ejercicio, LV8): por las [keys] con las que se guardaron, del más reciente al más antiguo. */
    suspend fun listForExercise(keys: List<String>): List<WorkoutMedia> = repository.listForExerciseKeys(keys)

    /** El esfuerzo («RIR 2», «RPE 7,5», «Fallo») con el que se registró la serie de [media], si la sesión en curso la tiene; nulo si no se sabe. */
    fun effortLabelFor(media: WorkoutMedia): String? =
        if (media.sessionKey == sessionMeta().sessionKey) effortLabelProvider(media) else null

    /** Combina el historial persistido y las series de esta sesión, sin atribuir a otro día lo registrado hoy. */
    suspend fun effortLabelsFor(items: List<WorkoutMedia>): Map<String, String> =
        repository.effortLabelsForMedia(items) + items.mapNotNull { item -> effortLabelFor(item)?.let { item.id to it } }.toMap()

    /** El aviso pertenece al álbum; una eliminación aceptada pertenece al repositorio y sobrevive al ViewModel. */
    fun newDeferredDelete(timerScope: CoroutineScope, onCommitted: () -> Unit = {}): DeferredMediaDelete {
        lateinit var deferred: DeferredMediaDelete
        deferred = DeferredMediaDelete(
            scope = timerScope,
            commit = { items ->
                repository.deleteAlbumMediaAsync(items).invokeOnCompletion { error ->
                    if (error != null) {
                        deferred.restoreFailed(items)
                        _captureError.value = "No se pudo eliminar el medio."
                    }
                    refreshSessionMedia()
                    // Solo la actualización visual se cancela si ya se cerró la sesión.
                    scope.launch { onCommitted() }
                }
            },
        )
        return deferred
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
                _captureError.value = "No se pudo recuperar el medio. Puedes reintentar."
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
            catalogDefinitionId = request.catalogDefinitionId,
            catalogConfigurationId = request.catalogConfigurationId,
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _captureError.value = failureMessage ?: if (entry.media.kind == WorkoutMediaKind.VIDEO) {
                    "No se pudo guardar el vídeo. Puedes reintentar."
                } else {
                    "No se pudo guardar la foto. Puedes reintentar."
                }
            }
            refreshSessionMedia()
        }
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
