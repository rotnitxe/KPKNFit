@file:OptIn(androidx.camera.core.ExperimentalSessionConfig::class)

package com.example.kpkn.screens.workout.technique

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.media.technique.ExerciseRefResolver
import com.example.kpkn.data.work.AnalysisGate
import com.example.kpkn.domain.biomechanics.framing.FramingSpec
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.technique.design.FramingChip
import com.example.kpkn.screens.workout.technique.design.FramingGuideOverlay
import com.example.kpkn.screens.workout.technique.design.RecTimerPill
import com.example.kpkn.screens.workout.technique.design.RecordButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
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
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.workout.components.MediaAlbumPill
import com.example.kpkn.screens.workout.components.MediaOverlayPill
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.screens.workout.technique.design.FpsBadge
import com.example.kpkn.screens.workout.technique.design.PlateDiameterBadge
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
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

internal const val TechniqueRecorderStageTag = "technique_recorder_stage"
internal val TechniqueRecorderRequestKey = SemanticsPropertyKey<WorkoutMediaCaptureRequest>("TechniqueRecorderRequest")

/** El plegado espera Finalize antes de devolver la cámara a la tarjeta. */
internal suspend fun stopTechniqueRecorderBeforeExit(stop: () -> Unit, recording: StateFlow<Boolean>) {
    stop()
    recording.first { !it }
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
    val mounted = remember { java.util.concurrent.atomic.AtomicBoolean(true) }

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
    val canRecord = profileResolution != null && cameraPermissionGranted && !isCameraUnavailable && activeSession != null && mediaCapture != null

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

    val is60FpsGranted = activeSession?.tier in listOf(
        RecorderFpsTier.FEATURE_GROUP_60,
        RecorderFpsTier.RANGE_60,
    )

    val originalOrientation = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    var isStopping by remember { mutableStateOf(false) }

    fun startRecording() {
        if (!canRecord || isStopping || mediaCapture?.isRecording?.value == true) return
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        framingController.isRecording = true

        activeSession?.let { session ->
            session.videoCapture.targetRotation = targetRotation
            mediaCapture?.startTechniqueRecording(
                requestProvider = { latestRequest.captureRequest },
                audioGranted = audioPermissionGranted,
                session = session,
                exercise = request.exercise,
                metadataRecorder = metadataRecorder,
                onFinalized = { file ->
                    if (mounted.get()) {
                        activity?.requestedOrientation = originalOrientation
                        framingController.isRecording = false
                        isStopping = false
                        if (file != null) latestOnFinished()
                    }
                },
                targetRotation = targetRotation,
                maxRecordSeconds = maxRecordSeconds,
                plateDiameterOverrideMm = plateDiameterMm,
                defaultPlateDiameterMm = defaultPlateDiameterMm,
            )
        }
    }

    fun stopRecording() {
        if (isStopping) return
        isStopping = true
        framingController.isRecording = false
        if (mediaCapture?.isRecording?.value == true) {
            mediaCapture.stopVideo()
        } else {
            isStopping = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Detener grabación al salir o desmontarse el composable
    DisposableEffect(Unit) {
        onDispose {
            mounted.set(false)
            orientationListener.disable()
            mediaCapture?.stopIfRecording()
            AnalysisGate.cameraBusy.value = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Enlace de ciclo de vida con CameraX
    DisposableEffect(previewView, lifecycleOwner, cameraPermissionGranted, profileResolution, desiredWant60, cameraRetryGeneration) {
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
                hasCheckedCameras = true
                hasAvailableCamera = hasBack || hasFront

                if (!hasBack && !hasFront) {
                    Log.w(TAG, "No hay cámaras disponibles en el dispositivo")
                    return@bindIfActive
                }

                val cameraSelector = if (hasBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                val want60 = desiredWant60

                runCatching {
                    cameraProvider.unbindAll()
                    val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                    activeCameraInfo = cameraInfo
                    val session = RecorderSessionFactory.createSession(cameraInfo, want60)
                    // Capturar los casos creados por ESTE efecto antes del bind, también si un enlace falla a medias.
                    ownership.own {
                        if (activeSession === session) activeSession = null
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
                    val boundCamera = if (session.tier == RecorderFpsTier.FALLBACK_30) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            session.preview,
                            session.videoCapture,
                        )
                    } else {
                        runCatching {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                session.sessionConfig,
                            )
                        }.getOrElse {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                session.preview,
                                session.videoCapture,
                            )
                        }
                    }
                    activeSession = session
                    boundCamera
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
        if (captureError != null && !isRecordingState) {
            framingController.isRecording = false
            isStopping = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    TechniqueRecorderStageContent(
        modifier = modifier.semantics { this[TechniqueRecorderRequestKey] = request.captureRequest },
        isRecording = isRecordingState,
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
        hasPermission = cameraPermissionGranted,
        hasCamera = !isCameraUnavailable && mediaCapture != null,
        isPermanentlyDenied = isPermanentlyDenied,
        captureError = captureError,
        canRecord = canRecord && !isStopping,
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
    ) {
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
    }
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
    preview: @Composable () -> Unit = {},
) {
    val available = hasPermission && hasCamera
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
                if (onOpenFicha != null) MediaOverlayPill(
                    description = "Ficha del ejercicio", onClick = onOpenFicha,
                ) { Text("Ficha", style = KpknType.chip, color = WizardColors.text) }
            }
            if (is60FpsGranted || showsPlateBadge) Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (is60FpsGranted) FpsBadge()
                if (showsPlateBadge) PlateDiameterBadge(
                    diameterMm = plateDiameterMm, expanded = plateBadgeExpanded,
                    onToggleExpanded = onTogglePlateExpanded,
                    onSelectDiameterMm = onSelectPlateDiameterMm,
                    visualMode = true,
                )
            }
            if (available && !isRecording) FramingChip(
                verdict = verdict, isFrontPreferred = preferredView == ViewKind.FRONT,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("technique_framing"),
            )
            Box(Modifier.weight(1f).fillMaxWidth().testTag("technique_guide_area")) {
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
                        expandedScene = true, showSilhouette = !isRecording,
                    )
                }
            }
            if (captureError != null) Text(captureError, style = KpknType.caption, color = WizardColors.danger)
            Row(
                Modifier.fillMaxWidth().testTag("technique_controls"),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    if (onOpenGallery != null) KpknTextAction("Galería", onOpenGallery)
                    if (onOpenAlbum != null) MediaAlbumPill(onOpenAlbum)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRecording) RecTimerPill(elapsedSeconds = elapsedSeconds, animate = false)
                    RecordButton(
                        isRecording = isRecording, onClick = onRecord,
                        modifier = Modifier.testTag("technique_record"),
                        enabled = available && (isRecording || canRecord), visualMode = true,
                    )
                }
            }
        }
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
