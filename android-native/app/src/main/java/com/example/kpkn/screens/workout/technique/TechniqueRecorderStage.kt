@file:OptIn(androidx.camera.core.ExperimentalSessionConfig::class)

package com.example.kpkn.screens.workout.technique

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.media.technique.ExerciseRefResolver
import com.example.kpkn.data.work.AnalysisGate
import com.example.kpkn.domain.biomechanics.framing.FramingCheckStatus
import com.example.kpkn.domain.biomechanics.framing.FramingInstruction
import com.example.kpkn.domain.biomechanics.framing.FramingSpec
import com.example.kpkn.domain.biomechanics.framing.SpokenFramingGuidePolicy
import com.example.kpkn.domain.biomechanics.framing.allowsCapture
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutCameraChoice
import com.example.kpkn.screens.workout.WorkoutCameraLens
import com.example.kpkn.screens.workout.components.WorkoutCameraSwitchButton
import com.example.kpkn.screens.workout.technique.design.FramingChip
import com.example.kpkn.screens.workout.technique.design.FramingGuideOverlay
import com.example.kpkn.screens.workout.technique.design.RecTimerPill
import com.example.kpkn.screens.workout.technique.design.RecordButton
import com.example.kpkn.services.workout.TechniqueCaptureStartResult
import com.example.kpkn.services.workout.TechniqueCaptureStopResult
import com.example.kpkn.services.workout.TechniqueCaptureVoiceLink
import com.example.kpkn.services.workout.WorkoutTtsManager
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.workout.components.MediaAlbumPill
import com.example.kpkn.screens.workout.components.MediaOverlayPill
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.screens.workout.TechniqueRecordingResult
import com.example.kpkn.screens.workout.components.RecordRed
import com.example.kpkn.screens.workout.technique.design.FpsBadge
import com.example.kpkn.screens.workout.technique.design.PlateDiameterBadge
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.flow.StateFlow

/**
 * Petición para abrir el grabador de técnica a pantalla completa (VT8a).
 *
 * @property captureRequest datos de la captura que se pasarán al controlador al grabar.
 * @property exercise ejercicio de la serie para resolver perfil, silueta y calibración de disco.
 * @property setLabel etiqueta visible en la barra superior (p. ej. "Serie 2").
 */
data class TechniqueRecorderRequest(
    val captureRequest: WorkoutMediaCaptureRequest,
    val exercise: Exercise? = null,
    val setLabel: String = "",
)


private const val TAG = "KpknRecorder"
private const val WORKOUT_PREFS = "workout_prefs"
internal const val SpokenFramingGuidePrefKey = "technique_spoken_framing_guide"

internal const val TechniqueRecorderStageTag = "technique_recorder_stage"
internal val TechniqueRecorderRequestKey = SemanticsPropertyKey<WorkoutMediaCaptureRequest>("TechniqueRecorderRequest")

/** El plegado espera Finalize antes de devolver la cámara a la tarjeta. */
internal suspend fun stopTechniqueRecorderBeforeExit(stop: () -> Unit, recording: StateFlow<Boolean>) {
    stop()
    recording.first { !it }
}

/** Una toma ocupa la escena desde su inicio hasta el resultado real de persistencia. */
internal class TechniqueRecorderCompletion {
    private var active = true
    private var generation = 0
    var pending by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun start(
        onResult: (TechniqueRecordingResult) -> Unit,
        onFinished: () -> Unit,
        capture: ((TechniqueRecordingResult) -> Unit) -> Unit,
    ) {
        if (!active || pending) return
        pending = true
        errorMessage = null
        val attempt = ++generation
        val callback: (TechniqueRecordingResult) -> Unit = { result ->
            if (active && pending && generation == attempt) {
                pending = false
                errorMessage = result.errorMessage
                onResult(result)
                if (result is TechniqueRecordingResult.Saved) onFinished()
            }
        }
        try {
            capture(callback)
        } catch (cancelled: CancellationException) {
            pending = false
            throw cancelled
        } catch (_: Exception) {
            callback(TechniqueRecordingResult.Error())
        }
    }

    /** Un Finalize tardío no puede cerrar la escena siguiente. */
    fun close() { active = false }
}

/** Nulo fuera del wrapper es pendiente; perfil nulo dentro significa resolución terminada sin perfil compatible. */
private data class RecorderResolvedProfile(val profile: AnalysisProfile?)

/** Cámara, metadatos y controles del grabador; la cabecera y el plegado pertenecen al anfitrión. */
@Composable
internal fun TechniqueRecorderStage(
    request: TechniqueRecorderRequest,
    mediaCapture: WorkoutMediaCaptureController?,
    prefer60FpsSetting: Boolean = true,
    maxRecordSeconds: Int = 180,
    defaultPlateDiameterMm: Int = 450,
    onFinished: () -> Unit,
    onSwitchToPhoto: () -> Unit,
    onOpenFicha: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val latestRequest by rememberUpdatedState(request)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = (activity as? LifecycleOwner) ?: LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val completion = remember { TechniqueRecorderCompletion() }
    val idleChoice = remember { MutableStateFlow(WorkoutCameraChoice()) }
    val cameraChoice by (mediaCapture?.cameraChoice ?: idleChoice).collectAsStateWithLifecycle()
    val lens = cameraChoice.lens

    // Resolución asíncrona del ExerciseRef y su perfil mediante el catálogo v2
    val profileResolution by produceState<RecorderResolvedProfile?>(
        initialValue = null,
        key1 = request.exercise,
        key2 = mediaCapture,
    ) {
        value = null
        if (mediaCapture == null) {
            value = RecorderResolvedProfile(null)
            return@produceState
        }
        val profile = try {
            withContext(Dispatchers.IO) {
                val ex = request.exercise ?: return@withContext null
                val catalog = CatalogV2ProcessCache.peek()?.catalog
                    ?: try { CatalogV2ProcessCache.getOrLoad(context).catalog }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                val resolver = ExerciseRefResolver(catalog)
                val partialRef = ExerciseRef(
                    definitionId = ex.catalogDefinitionId ?: ex.id,
                    configurationId = ex.catalogConfigurationId,
                    movementPatternId = ex.selectedMovementPattern,
                    equipmentId = null,
                    laterality = null,
                    name = ex.name,
                    isCustom = false,
                )
                val fullRef = resolver.resolve(partialRef, media = null)
                (AnalysisProfileResolver.resolve(fullRef) as? ProfileResolution.Supported)?.profile
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        value = RecorderResolvedProfile(profile)
    }
    val resolvedProfile = profileResolution?.profile
    val desiredWant60 = RecorderSessionFactory.wants60Fps(prefer60FpsSetting, resolvedProfile?.id)

    val preferredView = resolvedProfile?.framing?.preferredViews?.firstOrNull() ?: ViewKind.SIDE
    val usesPlate = resolvedProfile?.usesPlate == true
    val framingSpec = resolvedProfile?.framing ?: FramingSpec(
        preferredViews = setOf(preferredView),
        needsPlate = usesPlate,
        needsFeet = true,
        needsFace = true,
    )

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var activeCameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var activeSession by remember { mutableStateOf<RecorderSession?>(null) }
    var plateDiameterMm by remember(defaultPlateDiameterMm) { mutableStateOf<Int?>(defaultPlateDiameterMm) }
    var plateBadgeExpanded by remember { mutableStateOf(false) }

    val isRecordingFlow = remember(mediaCapture) { mediaCapture?.isRecording ?: MutableStateFlow(false) }
    val isRecordingState by isRecordingFlow.collectAsStateWithLifecycle()
    val recordedDurationFlow = remember(mediaCapture) { mediaCapture?.recordedDurationNanos ?: MutableStateFlow(0L) }
    val recordedDurationNanos by recordedDurationFlow.collectAsStateWithLifecycle()
    val elapsedSeconds = recordedDurationNanos / 1_000_000_000L

    var targetRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    val orientationListener = remember {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rot = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                targetRotation = rot
                activeSession?.videoCapture?.targetRotation = rot
            }
        }
    }

    LaunchedEffect(Unit) {
        if (mediaCapture != null && orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    val metadataRecorder = remember(activeSession, activeCameraInfo) {
        CaptureMetadataRecorder(
            context = context,
            cameraInfo = activeCameraInfo,
            requestedFps = if (desiredWant60) 60f else 30f,
            grantedFps = activeSession?.grantedFps ?: 30f,
        )
    }

    DisposableEffect(metadataRecorder) {
        metadataRecorder.startPreview(targetRotation)
        onDispose { metadataRecorder.close() }
    }
    LaunchedEffect(metadataRecorder, targetRotation) { metadataRecorder.updateTargetRotation(targetRotation) }
    val latestMetadataRecorder by rememberUpdatedState(metadataRecorder)
    val framingController = remember(framingSpec) {
        FramingCheckController(
            scope = scope,
            spec = framingSpec,
            context = context,
            gravityRollProvider = { latestMetadataRecorder.currentRollDeg },
        )
    }

    DisposableEffect(framingController) { onDispose { framingController.close() } }
    LaunchedEffect(previewView, activeSession, framingController) {
        val view = previewView
        if (view != null && activeSession != null) framingController.start { view.bitmap }
    }
    val framingVerdict by framingController.verdict.collectAsStateWithLifecycle()
    val framingStatus by framingController.status.collectAsStateWithLifecycle()

    // La guía hablada es independiente de Modo Voz: su preferencia vive en la app,
    // arranca apagada como el prototipo y nunca enciende el micrófono.
    val guidePreferences = remember(context) {
        context.getSharedPreferences(WORKOUT_PREFS, Context.MODE_PRIVATE)
    }
    var spokenGuideEnabled by remember {
        mutableStateOf(guidePreferences.getBoolean(SpokenFramingGuidePrefKey, false))
    }
    val guidePolicy = remember { SpokenFramingGuidePolicy() }
    val guideTts = remember(context) { WorkoutTtsManager(context) }
    DisposableEffect(guideTts) {
        guideTts.initialize()
        onDispose { guideTts.shutdown() }
    }
    // «Detener» de la cápsula de voz mientras habla la guía.
    LaunchedEffect(guideTts) { TechniqueCaptureVoiceLink.stopGuideRequests.collect { guideTts.stop() } }
    val voiceCommandsEnabled by TechniqueCaptureVoiceLink.voiceEnabled.collectAsStateWithLifecycle()

    // Política inicial aprobada: guía antes de grabar; durante la serie el evaluador
    // congela el veredicto, así que no se interrumpe por oscilaciones del detector.
    LaunchedEffect(spokenGuideEnabled, framingStatus, framingVerdict, isRecordingState, preferredView) {
        if (!spokenGuideEnabled || isRecordingState) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        val text = guidePolicy.onEvaluation(framingStatus, framingVerdict, preferredView, now)
            ?: return@LaunchedEffect
        TechniqueCaptureVoiceLink.noteGuideSpoken(text, now)
        guideTts.speak(text, queueFlush = true)
    }

    fun setSpokenGuide(enabled: Boolean) {
        spokenGuideEnabled = enabled
        guidePreferences.edit().putBoolean(SpokenFramingGuidePrefKey, enabled).apply()
        if (enabled) {
            guidePolicy.reset()
        } else {
            guideTts.stop()
            TechniqueCaptureVoiceLink.clearGuideSpeech()
        }
    }

    fun repeatGuide() {
        val text = if (isRecordingState) {
            FramingInstruction.RECORDING_CAPTION
        } else {
            FramingInstruction.spoken(framingVerdict, framingStatus, preferredView)
        }
        val now = SystemClock.elapsedRealtime()
        guidePolicy.noteSpoken(text, now)
        TechniqueCaptureVoiceLink.noteGuideSpoken(text, now)
        guideTts.speak(text, queueFlush = true)
    }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestedOnce = true
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (mediaCapture != null && !cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isPermanentlyDenied = !cameraPermissionGranted && permissionRequestedOnce &&
        (activity?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true)

    var hasCheckedCameras by remember { mutableStateOf(false) }
    var hasAvailableCamera by remember { mutableStateOf(true) }
    var cameraBindFailed by remember { mutableStateOf(false) }
    var cameraRetryGeneration by remember { mutableIntStateOf(0) }

    val isCameraUnavailable = cameraPermissionGranted && hasCheckedCameras && (!hasAvailableCamera || cameraBindFailed)
    // «No se pudo comprobar» es un error explícito y permite grabar; «comprobando» espera
    // el veredicto estable y un veredicto negativo exige corregir el encuadre.
    val framingReady = framingStatus.allowsCapture(framingVerdict)
    val canRecord = profileResolution != null && cameraPermissionGranted && !isCameraUnavailable &&
        activeSession != null && mediaCapture != null && framingReady

    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        audioPermissionGranted = granted
    }

    val is60FpsGranted = activeSession?.is60FpsGranted == true

    val originalOrientation = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    var isStopping by remember { mutableStateOf(false) }

    fun startRecording(): TechniqueCaptureStartResult {
        val capture = mediaCapture ?: return TechniqueCaptureStartResult.UNAVAILABLE
        if (isStopping || completion.pending) return TechniqueCaptureStartResult.BUSY
        if (capture.isRecording.value) return TechniqueCaptureStartResult.ALREADY_RECORDING
        if (!cameraPermissionGranted || isCameraUnavailable || profileResolution == null) {
            return TechniqueCaptureStartResult.UNAVAILABLE
        }
        val session = activeSession ?: return TechniqueCaptureStartResult.UNAVAILABLE
        if (framingStatus == FramingCheckStatus.CHECKING) return TechniqueCaptureStartResult.CHECKING
        if (!framingReady) return TechniqueCaptureStartResult.NOT_READY
        completion.start(
            onResult = {
                activity?.requestedOrientation = originalOrientation
                framingController.isRecording = false
                isStopping = false
            },
            onFinished = { latestOnFinished() },
            capture = { onFinalized ->
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                framingController.isRecording = true
                session.videoCapture.targetRotation = targetRotation
                capture.startTechniqueRecording(
                    requestProvider = { latestRequest.captureRequest },
                    audioGranted = audioPermissionGranted,
                    session = session,
                    exercise = request.exercise,
                    metadataRecorder = metadataRecorder,
                    onFinalized = onFinalized,
                    targetRotation = targetRotation,
                    maxRecordSeconds = maxRecordSeconds,
                    plateDiameterOverrideMm = plateDiameterMm,
                    defaultPlateDiameterMm = defaultPlateDiameterMm,
                )
            },
        )
        return TechniqueCaptureStartResult.STARTED
    }

    fun stopRecording(): TechniqueCaptureStopResult {
        val capture = mediaCapture ?: return TechniqueCaptureStopResult.UNAVAILABLE
        if (isStopping || completion.pending) return TechniqueCaptureStopResult.BUSY
        if (!capture.isRecording.value) {
            activity?.requestedOrientation = originalOrientation
            return TechniqueCaptureStopResult.NOT_RECORDING
        }
        isStopping = true
        framingController.isRecording = false
        capture.stopVideo()
        return TechniqueCaptureStopResult.STOPPED
    }

    // Enlace de proceso con Modo Voz: sólo con el grabador abierto se reconocen
    // «Iniciar/Detener vídeo»; las órdenes repetidas no crean otra captura.
    val latestStartRecording = rememberUpdatedState<(()->TechniqueCaptureStartResult)>({ startRecording() })
    val latestStopRecording = rememberUpdatedState<(()->TechniqueCaptureStopResult)>({ stopRecording() })
    val voiceLinkHandlers = remember {
        TechniqueCaptureVoiceLink.Handlers(
            start = { latestStartRecording.value() },
            stop = { latestStopRecording.value() },
        )
    }
    DisposableEffect(voiceLinkHandlers) {
        TechniqueCaptureVoiceLink.attach(voiceLinkHandlers)
        onDispose { TechniqueCaptureVoiceLink.detach(voiceLinkHandlers) }
    }

    // Detener grabación al salir o desmontarse el composable
    DisposableEffect(Unit) {
        onDispose {
            completion.close()
            orientationListener.disable()
            mediaCapture?.stopIfRecording()
            AnalysisGate.cameraBusy.value = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Enlace de ciclo de vida con CameraX
    DisposableEffect(previewView, lifecycleOwner, cameraPermissionGranted, profileResolution, desiredWant60, cameraRetryGeneration, lens) {
        val view = previewView ?: return@DisposableEffect onDispose {}
        if (mediaCapture == null || !cameraPermissionGranted || profileResolution == null) return@DisposableEffect onDispose {}

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        val cameraIds = runCatching { cameraManager?.cameraIdList }.getOrNull() ?: emptyArray()
        if (cameraIds.isEmpty()) {
            Log.w(TAG, "No hay cámaras disponibles en el dispositivo")
            hasCheckedCameras = true
            hasAvailableCamera = false
            return@DisposableEffect onDispose {}
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context.applicationContext)
        val ownership = RecorderCameraBindingOwner()
        val listener = Runnable {
            ownership.bindIfActive {
                val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                if (cameraProvider == null) {
                    cameraBindFailed = true
                    hasCheckedCameras = true
                    return@bindIfActive
                }

                val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                mediaCapture.updateCameraAvailability(hasBack, hasFront)
                hasCheckedCameras = true
                hasAvailableCamera = hasBack || hasFront

                if (!hasBack && !hasFront) {
                    Log.w(TAG, "No hay cámaras disponibles en el dispositivo")
                    return@bindIfActive
                }

                val selectedLens = cameraChoice.detected(hasBack, hasFront).lens
                val cameraSelector = if (selectedLens == WorkoutCameraLens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val want60 = desiredWant60

                runCatching {
                    cameraProvider.unbindAll()
                    val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                    activeCameraInfo = cameraInfo
                    val session = RecorderSessionFactory.createSession(cameraInfo, want60)
                    // Capturar los casos creados por ESTE efecto antes del bind, también si un enlace falla a medias.
                    ownership.own {
                        if (activeSession?.sharesUseCasesWith(session) == true) activeSession = null
                        fun releaseOwned() {
                            if (cameraProvider.isBound(session.preview) || cameraProvider.isBound(session.videoCapture)) {
                                cameraProvider.unbind(session.preview, session.videoCapture)
                            }
                        }
                        val capture = mediaCapture
                        if (capture?.isRecording?.value == true) {
                            lifecycleOwner.lifecycleScope.launch {
                                capture.isRecording.first { !it }
                                runCatching { releaseOwned() }
                            }
                        } else runCatching { releaseOwned() }
                    }
                    session.preview.surfaceProvider = view.surfaceProvider
                    session.videoCapture.targetRotation = targetRotation
                    val binding = RecorderSessionFactory.bindWithFallback(
                        session = session,
                        bindConfigured = {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                session.sessionConfig,
                            )
                        },
                        bindUnconfigured = {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                session.preview,
                                session.videoCapture,
                            )
                        },
                        onConfiguredBindFailure = {
                            Log.w(TAG, "Configured camera session bind failed; using nominal 30 FPS fallback")
                        },
                    )
                    activeSession = binding.effectiveSession
                    binding.camera
                }.onSuccess {
                    cameraBindFailed = false
                }.onFailure { e ->
                    Log.e(TAG, "Error al enlazar CameraX", e)
                    cameraBindFailed = true
                }
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose { ownership.close() }
    }

    val captureError by (mediaCapture?.captureError ?: remember { MutableStateFlow<String?>(null) })
        .collectAsStateWithLifecycle()
    LaunchedEffect(captureError) {
        if (captureError != null && !isRecordingState && !completion.pending) {
            framingController.isRecording = false
            isStopping = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    TechniqueRecorderStageContent(
        modifier = modifier.semantics { this[TechniqueRecorderRequestKey] = request.captureRequest },
        isRecording = isRecordingState,
        cameraChoice = cameraChoice,
        onSwitchCamera = { mediaCapture?.switchCamera() },
        elapsedSeconds = elapsedSeconds,
        is60FpsGranted = is60FpsGranted,
        showsPlateBadge = usesPlate,
        plateDiameterMm = plateDiameterMm,
        plateBadgeExpanded = plateBadgeExpanded,
        onTogglePlateExpanded = { plateBadgeExpanded = !plateBadgeExpanded },
        onSelectPlateDiameterMm = { plateDiameterMm = it },
        preferredView = preferredView,
        profileId = resolvedProfile?.id,
        verdict = framingVerdict,
        framingStatus = framingStatus,
        spokenGuideEnabled = spokenGuideEnabled,
        onToggleSpokenGuide = { enabled -> setSpokenGuide(enabled) },
        onRepeatGuide = { repeatGuide() },
        voiceCommandsEnabled = voiceCommandsEnabled,
        onToggleVoiceCommands = TechniqueCaptureVoiceLink::requestVoiceToggle,
        hasPermission = cameraPermissionGranted,
        hasCamera = !isCameraUnavailable && mediaCapture != null,
        isPermanentlyDenied = isPermanentlyDenied,
        captureError = completion.errorMessage ?: captureError,
        canRecord = canRecord && !completion.pending,
        isProcessing = completion.pending && (!isRecordingState || isStopping),
        onRecord = { if (isRecordingState) stopRecording() else startRecording() },
        onSwitchToPhoto = onSwitchToPhoto,
        onOpenFicha = onOpenFicha,
        onOpenAlbum = onOpenAlbum,
        onOpenGallery = onOpenGallery,
        onRequestPermission = {
            if (isPermanentlyDenied) openAppSettings(context)
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onRetryCamera = { cameraBindFailed = false; hasCheckedCameras = false; cameraRetryGeneration += 1 },
        preview = {
            if (mediaCapture != null && cameraPermissionGranted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            previewView = this
                        }
                    },
                )
            }
        },
    )
}

/** La misma escena real se dibuja en las hojas, sin abrir CameraX. */
@Composable
internal fun TechniqueRecorderStageContent(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    elapsedSeconds: Long = 0L,
    is60FpsGranted: Boolean = false,
    showsPlateBadge: Boolean = false,
    plateDiameterMm: Int? = 450,
    plateBadgeExpanded: Boolean = false,
    onTogglePlateExpanded: () -> Unit = {},
    onSelectPlateDiameterMm: (Int?) -> Unit = {},
    preferredView: ViewKind = ViewKind.SIDE,
    profileId: AnalysisProfileId? = null,
    verdict: FramingVerdict = FramingVerdict(ok = false, issues = emptyList(), view = null),
    framingStatus: FramingCheckStatus = FramingCheckStatus.EVALUATED,
    spokenGuideEnabled: Boolean = false,
    onToggleSpokenGuide: (Boolean) -> Unit = {},
    onRepeatGuide: () -> Unit = {},
    voiceCommandsEnabled: Boolean = false,
    onToggleVoiceCommands: () -> Unit = {},
    hasPermission: Boolean = true,
    hasCamera: Boolean = true,
    isPermanentlyDenied: Boolean = false,
    captureError: String? = null,
    canRecord: Boolean = true,
    onRecord: () -> Unit = {},
    onSwitchToPhoto: () -> Unit = {},
    onOpenFicha: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    onRequestPermission: () -> Unit = {},
    onRetryCamera: () -> Unit = {},
    cameraChoice: WorkoutCameraChoice = WorkoutCameraChoice(),
    onSwitchCamera: () -> Unit = {},
    preview: @Composable () -> Unit = {},
    isProcessing: Boolean = false,
) {
    val available = hasPermission && hasCamera
    val framingReady = framingStatus.allowsCapture(verdict)
    val captureTitle = when {
        isRecording -> "Grabación en curso"
        framingStatus == FramingCheckStatus.CHECKING -> "Comprobando encuadre"
        framingReady -> "Listo para grabar"
        else -> "Ajusta el encuadre"
    }
    val captureHint = when {
        isRecording -> "Toca para detener"
        framingStatus == FramingCheckStatus.CHECKING -> "Espera el veredicto antes de grabar"
        framingStatus == FramingCheckStatus.UNAVAILABLE -> "No se pudo comprobar el encuadre; puedes grabar igualmente"
        else -> "El encuadre se revisa antes de comenzar"
    }
    val guideCaption = if (isRecording) {
        FramingInstruction.RECORDING_CAPTION
    } else {
        FramingInstruction.spoken(verdict, framingStatus, preferredView)
    }
    var showFramingGuide by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize().testTag(TechniqueRecorderStageTag)
            .clip(RoundedCornerShape(24.dp)).background(KpknSurface.inset),
    ) {
        preview()
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KpknSegmentedControl(
                    options = listOf("Foto", "Vídeo"), selectedIndex = 1,
                    onSelect = { if (it == 0) onSwitchToPhoto() },
                    modifier = Modifier.width(154.dp).testTag("technique_mode"),
                    size = KpknSegmentedSize.Compact,
                    descriptions = listOf("Cambiar a foto", "Vídeo de técnica"),
                )
                Spacer(Modifier.weight(1f))
                WorkoutCameraSwitchButton(cameraChoice, available && !isRecording && !isProcessing, onSwitchCamera)
                if (onOpenFicha != null) MediaOverlayPill(
                    description = "Ficha del ejercicio", onClick = onOpenFicha,
                ) { Text("Ficha", style = KpknType.chip, color = WizardColors.text) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().testTag("technique_guide_area").zIndex(1f)) {
                when {
                    !hasPermission -> RecorderNoticeCard(
                        title = "Permite la cámara para grabar",
                        actionLabel = if (isPermanentlyDenied) "Abrir ajustes" else "Permitir cámara",
                        onAction = onRequestPermission,
                    )
                    !hasCamera -> RecorderNoticeCard(
                        title = "No se pudo abrir la cámara", actionLabel = "Reintentar", onAction = onRetryCamera,
                    )
                    else -> FramingGuideOverlay(
                        preferredView = preferredView, hasBarbell = showsPlateBadge, profileId = profileId,
                        expandedScene = true, showSilhouette = !isRecording, showGuideText = false,
                    )
                }
                // `.v2-framing-corners`: el marco de cuatro esquinas, ámbar mientras falta y verde al estar listo.
                if (available) FramingCornerFrame(ready = framingReady && !isRecording,
                    Modifier.matchParentSize().padding(start = 28.dp, end = 28.dp, top = 39.dp, bottom = 32.dp))
                if (available && !isRecording) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    FramingChip(
                        verdict = verdict, isFrontPreferred = preferredView == ViewKind.FRONT, visualMode = true,
                        status = framingStatus, hasBarbell = showsPlateBadge,
                        modifier = Modifier.weight(1f).testTag("technique_framing"),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (is60FpsGranted) Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { FpsBadge() }
                        if (showsPlateBadge) PlateDiameterBadge(
                            diameterMm = plateDiameterMm, expanded = plateBadgeExpanded,
                            onToggleExpanded = onTogglePlateExpanded,
                            onSelectDiameterMm = onSelectPlateDiameterMm,
                            visualMode = true,
                        )
                    }
                }
                // `.v2-framing-recording`: «PREPARAR CAPTURA» o «● GRABANDO» con el tiempo, abajo a la derecha.
                if (available) Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp).testTag("technique_recording_state"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isRecording) Box(Modifier.size(5.dp).clip(RoundedCornerShape(percent = 50)).background(Color(0xFFFF9C97)))
                    Text(
                        if (isRecording) "GRABANDO · ${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}" else "PREPARAR CAPTURA",
                        style = KpknType.caption.copy(fontSize = 8.sp, letterSpacing = .5.sp), color = WizardColors.text,
                    )
                }
                if (available && (onOpenGallery != null || onOpenAlbum != null || !isRecording)) Row(
                    Modifier.align(Alignment.BottomStart).padding(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // `.v2-framing-view`: «Vista lateral · cómo encuadrar» abre «Preparar el encuadre».
                    if (!isRecording) Text(
                        "${framingViewLabel(preferredView)} · cómo encuadrar",
                        // Con letra grande cede ancho (y pasa a dos líneas) en vez de aplastar «Ver álbum».
                        Modifier.weight(1f, fill = false).heightIn(min = 32.dp).wrapContentHeight(Alignment.CenterVertically)
                            .clickable(role = Role.Button) { showFramingGuide = true }
                            .clip(RoundedCornerShape(7.dp)).background(Color(0xCC111111)).padding(horizontal = 7.dp, vertical = 5.dp)
                            .testTag("technique_framing_view"),
                        style = KpknType.caption.copy(fontSize = 8.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                        color = Color(0xFFD9D5CD),
                    )
                    if (onOpenGallery != null) MediaOverlayPill("Abrir galería", onOpenGallery) {
                        Text("Galería", style = KpknType.chip, color = WizardColors.text)
                    }
                    if (onOpenAlbum != null) MediaAlbumPill(onOpenAlbum)
                }
            }
            if (available) {
                CaptureGuidePanel(
                    caption = guideCaption,
                    spokenGuideEnabled = spokenGuideEnabled,
                    onToggleSpokenGuide = onToggleSpokenGuide,
                    onRepeatGuide = onRepeatGuide,
                    voiceCommandsEnabled = voiceCommandsEnabled,
                    onToggleVoiceCommands = onToggleVoiceCommands,
                )
            }
            if (captureError != null) Text(captureError, style = KpknType.caption, color = WizardColors.danger)
            // Una sola fila de acciones como el prototipo: REC, estado y comando.
            Row(
                Modifier.fillMaxWidth().testTag("technique_controls"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // `.v2-record-control`: círculo crema de 49 con la cámara (o la pausa al grabar).
                Ref02RecordControl(
                    isRecording = isRecording, onClick = onRecord,
                    modifier = Modifier.testTag("technique_record").semantics {
                        if (isProcessing) stateDescription = "Procesando vídeo"
                    },
                    enabled = available && !isProcessing && (isRecording || canRecord),
                )
                Column(Modifier.weight(1f).testTag("technique_capture_state")) {
                    Text(
                        text = captureTitle,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        modifier = Modifier.testTag("technique_capture_title"),
                    )
                    Text(
                        text = captureHint,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.testTag("technique_capture_hint"),
                    )
                }
                CaptureCommandPill(
                    text = if (isRecording) "«Detener vídeo»" else "«Iniciar vídeo»",
                    enabled = voiceCommandsEnabled && available && !isProcessing && (isRecording || canRecord),
                    modifier = Modifier.testTag("technique_voice_command"),
                    onClick = onRecord,
                )
            }
        }
    }
    if (showFramingGuide) {
        // `framing-guide` del prototipo 02: los cuatro pasos para preparar el encuadre.
        com.example.kpkn.screens.workout.components.LiveSheet(title = "Preparar el encuadre", onDismiss = { showFramingGuide = false },
            tag = "technique_framing_guide") {
            com.example.kpkn.screens.workout.components.LiveSheetSubtitle("${framingViewLabel(preferredView)} para este ejercicio.")
            FRAMING_GUIDE_STEPS.forEach { (title, body) -> FramingGuideStep(title, body) }
        }
    }
}

private fun framingViewLabel(view: ViewKind): String = when (view) {
    ViewKind.FRONT -> "Vista frontal"
    ViewKind.OBLIQUE -> "Vista diagonal"
    else -> "Vista lateral"
}

private val FRAMING_GUIDE_STEPS = listOf(
    "01 · Coloca la cámara a un lado" to "Apóyala en una superficie estable y mantén el móvil nivelado. La vista se adapta al ejercicio que vas a analizar.",
    "02 · Deja espacio al movimiento" to "Incluye cabeza, pies y barra durante todo el recorrido. Deja margen arriba y abajo, también en la posición más baja.",
    "03 · Comprueba lo que se ve" to "Una sola persona, articulaciones visibles y luz suficiente. Si el perfil requiere un disco para calibrar, también debe quedar visible.",
    "04 · Espera la confirmación" to "La guía da una indicación cada vez. «Encuadre listo» confirma la captura, no la calidad técnica del ejercicio.",
)

/** `.v2-history-note` con título: filete de 2, el paso en 13/600 y su explicación en 12. */
@Composable
private fun FramingGuideStep(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).drawBehind {
            drawLine(Color(0x2DFFFFFF), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx())
        }.padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(title, style = KpknType.bodyStrong.copy(fontSize = 13.sp), color = WizardColors.text)
        Text(body, Modifier.padding(top = 8.dp), style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = WizardColors.textMuted)
    }
}

/** El marco de encuadre: borde discontinuo de 1 y cuatro esquinas de 17 × 2. */
@Composable
private fun FramingCornerFrame(ready: Boolean, modifier: Modifier = Modifier) {
    val corner = if (ready) Color(0xFFD7E5D5) else Color(0xFFFFB09B)
    val dashed = if (ready) Color(0x60D7E5D5) else Color(0x42F49A6E)
    androidx.compose.foundation.Canvas(modifier.testTag("technique_framing_corners")) {
        val r = 10.dp.toPx()
        drawRoundRect(dashed, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        val l = 17.dp.toPx()
        val w = 2.dp.toPx()
        val half = w / 2
        listOf(
            Offset(half, half) to Pair(Offset(l, half), Offset(half, l)),
            Offset(size.width - half, half) to Pair(Offset(size.width - l, half), Offset(size.width - half, l)),
            Offset(half, size.height - half) to Pair(Offset(l, size.height - half), Offset(half, size.height - l)),
            Offset(size.width - half, size.height - half) to Pair(Offset(size.width - l, size.height - half), Offset(size.width - half, size.height - l)),
        ).forEach { (origin, ends) ->
            drawLine(corner, origin, ends.first, w)
            drawLine(corner, origin, ends.second, w)
        }
    }
}

/** El control de grabación del prototipo: crema, borde oscuro de 3 y un anillo tenue; cámara en reposo, pausa al grabar. */
@Composable
private fun Ref02RecordControl(isRecording: Boolean, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        // El área táctil y la semántica abarcan los 49 dp enteros (antes quedaban dentro del anillo: 47 dp).
        modifier.size(49.dp).alpha(if (enabled) 1f else .45f)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isRecording) "Detener grabación" else "Iniciar grabación" }
            .border(1.dp, Color(0x8CF2EEE6), RoundedCornerShape(percent = 50))
            .padding(1.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFFF2EEE6))
            .border(3.dp, Color(0xFF111111), RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            if (isRecording) com.example.kpkn.ui.components.icons.KpknLineIcons.Pause else com.example.kpkn.ui.components.icons.KpknLineIcons.Camera,
            null, Modifier.size(if (isRecording) 21.dp else 23.dp), tint = Color(0xFF111111),
        )
    }
}

/**
 * Guía hablada y comandos de captura, dos controles independientes. La guía
 * funciona con Modo Voz apagado; el comando sólo se reconoce con el grabador abierto.
 */
@Composable
private fun CaptureGuidePanel(
    caption: String,
    spokenGuideEnabled: Boolean,
    onToggleSpokenGuide: (Boolean) -> Unit,
    onRepeatGuide: () -> Unit,
    voiceCommandsEnabled: Boolean,
    onToggleVoiceCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KpknSurface.module.copy(alpha = 0.86f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Guía hablada de encuadre", style = KpknType.bodyStrong, color = WizardColors.text)
                Text(
                    text = "Funciona aunque Modo Voz esté apagado.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            KpknSwitch(
                checked = spokenGuideEnabled,
                onCheckedChange = onToggleSpokenGuide,
                modifier = Modifier
                    .testTag("technique_guide_toggle")
                    .semantics { contentDescription = "Guía hablada de encuadre" },
            )
        }
        Row(
            Modifier.fillMaxWidth().testTag("technique_guide_text"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = caption,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f).testTag("technique_spoken_caption"),
            )
            Spacer(Modifier.width(4.dp))
            RecorderTextAction(
                text = "Escuchar",
                onClick = onRepeatGuide,
                modifier = Modifier
                    .testTag("technique_guide_repeat")
                    .semantics { contentDescription = "Escuchar guía de ejemplo" },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Comandos de voz",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            RecorderTextAction(
                text = if (voiceCommandsEnabled) "Desactivar comandos" else "Activar Modo Voz",
                onClick = onToggleVoiceCommands,
                modifier = Modifier.testTag("technique_voice_action"),
            )
        }
        Text(
            text = if (voiceCommandsEnabled) {
                "«Iniciar vídeo» · «Detener vídeo»"
            } else {
                "Activa Modo Voz para controlar la grabación."
            },
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.testTag("technique_voice_hint"),
        )
    }
}

/** Acción de texto con zona táctil de 48 dp y sin recorte de texto. */
@Composable
private fun RecorderTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = WizardColors.text,
) {
    Text(
        text = text,
        style = KpknType.action,
        color = color,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/** Píldora informativa del comando; nunca duplica el control de grabación. */
@Composable
private fun CaptureCommandPill(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** La píldora también se pulsa: hace lo mismo que el comando que enseña. */
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                stateDescription = if (enabled) "Comando disponible" else "Activa Modo Voz"
            },
        shape = KpknShape.pill,
        color = KpknSurface.module.copy(alpha = if (enabled) 0.92f else 0.55f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.28f else 0.12f)),
    ) {
        Text(
            text = text,
            style = KpknType.chip,
            color = if (enabled) WizardColors.text else WizardColors.textFaint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Aviso de cámara dentro de la escena, con acción KPKN de 48 dp. */
@Composable
internal fun RecorderNoticeCard(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        KpknModule(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Text(title, style = KpknType.bodyStrong, color = WizardColors.text, textAlign = TextAlign.Center)
            KpknTextAction(actionLabel, onAction, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
