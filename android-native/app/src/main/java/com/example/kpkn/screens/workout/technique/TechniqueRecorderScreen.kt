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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.technique.design.FramingChip
import com.example.kpkn.screens.workout.technique.design.FramingGuideOverlay
import com.example.kpkn.screens.workout.technique.design.RecTimerPill
import com.example.kpkn.screens.workout.technique.design.RecordButton
import com.example.kpkn.screens.workout.technique.design.RecorderTopBar
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "KpknRecorder"

/**
 * Pantalla completa de grabación de técnica en vídeo (VT8a).
 *
 * Se abre como capa/diálogo a pantalla completa sin Material genérico:
 * - Vista previa 16:9 en modo COMPATIBLE y FIT_CENTER;
 * - Barra superior con botón de cerrar, ejercicio, serie e insignias de 60 fps y disco;
 * - Silueta de guía anatómica según la vista preferida del perfil;
 * - Comprobación continua de encuadre (~2 Hz) con histéresis de 3 OK;
 * - Botón de grabación de 72 dp con cronómetro en píldora flotante;
 * - Estado sin cámara o sin permiso con aviso KPKN v2 y botón de acción;
 * - Registro de metadatos de captura (gravedad, focal y fps) al finalizar.
 */
@Composable
fun TechniqueRecorderScreen(
    request: TechniqueRecorderRequest,
    onDismiss: () -> Unit,
    mediaCapture: WorkoutMediaCaptureController?,
    modifier: Modifier = Modifier,
    prefer60FpsSetting: Boolean = true,
    maxRecordSeconds: Int = 180,
    defaultPlateDiameterMm: Int = 450,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = (activity as? LifecycleOwner) ?: LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Resolución asíncrona del ExerciseRef y su perfil mediante el catálogo v2
    val resolvedProfile by produceState<AnalysisProfile?>(
        initialValue = null,
        key1 = request.exercise,
    ) {
        value = withContext(Dispatchers.IO) {
            val ex = request.exercise ?: return@withContext null
            val catalog = CatalogV2ProcessCache.peek()?.catalog
                ?: runCatching { CatalogV2ProcessCache.getOrLoad(context).catalog }.getOrNull()
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
    }

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
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    val metadataRecorder = remember(activeSession, activeCameraInfo) {
        CaptureMetadataRecorder(
            context = context,
            cameraInfo = activeCameraInfo,
            requestedFps = if (RecorderSessionFactory.wants60Fps(prefer60FpsSetting, resolvedProfile?.id)) 60f else 30f,
            grantedFps = activeSession?.grantedFps ?: 30f,
        )
    }

    val framingController = remember(framingSpec) {
        FramingCheckController(
            scope = scope,
            spec = framingSpec,
            context = context,
            gravityRollProvider = { metadataRecorder.currentRollDeg },
        )
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
        if (!cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isPermanentlyDenied = !cameraPermissionGranted && permissionRequestedOnce &&
        (activity?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true)

    var hasCheckedCameras by remember { mutableStateOf(false) }
    var hasAvailableCamera by remember { mutableStateOf(true) }
    var cameraBindFailed by remember { mutableStateOf(false) }

    val isCameraUnavailable = cameraPermissionGranted && hasCheckedCameras && (!hasAvailableCamera || cameraBindFailed)
    val canRecord = cameraPermissionGranted && !isCameraUnavailable && activeSession != null && mediaCapture != null

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
        if (!canRecord || isStopping) return
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        framingController.isRecording = true
        metadataRecorder.start(targetRotation)

        activeSession?.let { session ->
            session.videoCapture.targetRotation = targetRotation
            mediaCapture?.startTechniqueRecording(
                requestProvider = { request.captureRequest },
                audioGranted = audioPermissionGranted,
                session = session,
                exercise = request.exercise,
                metadataRecorder = metadataRecorder,
                onFinalized = {
                    activity?.requestedOrientation = originalOrientation
                    onDismiss()
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
        if (mediaCapture != null && isRecordingState) {
            mediaCapture.stopVideo()
        } else {
            activity?.requestedOrientation = originalOrientation
            onDismiss()
        }
    }

    // Detener grabación al salir o desmontarse el composable
    DisposableEffect(Unit) {
        onDispose {
            orientationListener.disable()
            framingController.close()
            if (mediaCapture?.isRecording?.value == true) {
                mediaCapture.stopVideo()
            }
            AnalysisGate.cameraBusy.value = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    Dialog(
        onDismissRequest = {
            if (isRecordingState) {
                mediaCapture?.stopVideo()
            }
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Vista previa de cámara en 16:9
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        previewView = this
                    }
                },
            )

            // Enlace de ciclo de vida con CameraX
            DisposableEffect(previewView, lifecycleOwner, cameraPermissionGranted) {
                val view = previewView ?: return@DisposableEffect onDispose {}
                if (!cameraPermissionGranted) return@DisposableEffect onDispose {}

                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                val cameraIds = runCatching { cameraManager?.cameraIdList }.getOrNull() ?: emptyArray()
                if (cameraIds.isEmpty()) {
                    Log.w(TAG, "No hay cámaras disponibles en el dispositivo")
                    hasCheckedCameras = true
                    hasAvailableCamera = false
                    return@DisposableEffect onDispose {}
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(context.applicationContext)
                val listener = Runnable {
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    if (cameraProvider == null) {
                        cameraBindFailed = true
                        hasCheckedCameras = true
                        return@Runnable
                    }

                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    hasCheckedCameras = true
                    hasAvailableCamera = hasBack || hasFront

                    if (!hasBack && !hasFront) {
                        Log.w(TAG, "No hay cámaras disponibles en el dispositivo")
                        return@Runnable
                    }

                    val cameraSelector = if (hasBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    val want60 = RecorderSessionFactory.wants60Fps(prefer60FpsSetting, resolvedProfile?.id)

                    runCatching {
                        cameraProvider.unbindAll()
                        val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                        activeCameraInfo = cameraInfo
                        val session = RecorderSessionFactory.createSession(cameraInfo, want60)
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
                        framingController.start { view.bitmap }
                    }.onFailure { e ->
                        Log.e(TAG, "Error al enlazar CameraX", e)
                        cameraBindFailed = true
                    }
                }
                cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

                onDispose {
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        provider.unbindAll()
                    }
                }
            }

            // Capa con silueta anatómica de guía (solo cuando hay cámara y no está grabando)
            AnimatedVisibility(
                visible = !isRecordingState && cameraPermissionGranted && !isCameraUnavailable,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FramingGuideOverlay(
                    preferredView = preferredView,
                    hasBarbell = usesPlate,
                )
            }

            // Barra superior
            RecorderTopBar(
                exerciseName = request.exercise?.name ?: request.captureRequest.exerciseName ?: "Ejercicio",
                setLabel = request.setLabel,
                is60FpsGranted = is60FpsGranted,
                showsPlateBadge = usesPlate,
                plateDiameterMm = plateDiameterMm,
                plateBadgeExpanded = plateBadgeExpanded,
                onTogglePlateExpanded = { plateBadgeExpanded = !plateBadgeExpanded },
                onSelectPlateDiameterMm = { plateDiameterMm = it },
                onClose = {
                    if (isRecordingState) stopRecording() else onDismiss()
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopCenter),
            )

            // Chip de comprobación de encuadre (arriba, bajo la barra superior, solo si no graba y hay cámara)
            AnimatedVisibility(
                visible = !isRecordingState && cameraPermissionGranted && !isCameraUnavailable,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            ) {
                FramingChip(
                    verdict = framingVerdict,
                    isFrontPreferred = preferredView == ViewKind.FRONT,
                )
            }

            // Aviso KPKN v2: Sin permiso de cámara
            if (!cameraPermissionGranted) {
                RecorderNoticeCard(
                    title = "Permite la cámara para grabar",
                    actionLabel = if (isPermanentlyDenied) "Abrir ajustes" else "Permitir cámara",
                    onAction = {
                        if (isPermanentlyDenied) {
                            openAppSettings(context)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Aviso KPKN v2: Sin cámara física o fallo al enlazar
            if (isCameraUnavailable) {
                RecorderNoticeCard(
                    title = "No se pudo abrir la cámara",
                    actionLabel = "Cerrar",
                    onAction = onDismiss,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Controles inferiores: Cronómetro y Botón REC
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(
                    visible = isRecordingState,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    RecTimerPill(elapsedSeconds = elapsedSeconds)
                }

                RecordButton(
                    isRecording = isRecordingState,
                    enabled = canRecord,
                    onClick = {
                        if (isRecordingState) {
                            stopRecording()
                        } else {
                            startRecording()
                        }
                    },
                )
            }
        }
    }
}

/**
 * Tarjeta modal centrada KPKN v2 para mensajes informativos sobre la vista previa (sin cámara o sin permiso).
 */
@Composable
internal fun RecorderNoticeCard(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = KpknSurface.module,
                    shape = KpknShape.module,
                )
                .border(
                    width = 1.dp,
                    color = KpknSurface.moduleBorder,
                    shape = KpknShape.module,
                )
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = title,
                style = WizardTypography.header,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WizardSpacing.ctaHeight)
                    .background(
                        color = WizardColors.text,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .clickable(
                        role = Role.Button,
                        onClick = onAction,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    style = WizardTypography.cta,
                    color = WizardColors.ctaContent,
                )
            }
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
