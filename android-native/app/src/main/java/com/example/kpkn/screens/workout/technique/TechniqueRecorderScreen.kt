@file:OptIn(androidx.camera.core.ExperimentalSessionConfig::class)

package com.example.kpkn.screens.workout.technique

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import com.example.kpkn.data.work.AnalysisGate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.technique.design.FramingChip
import com.example.kpkn.screens.workout.technique.design.FramingGuideOverlay
import com.example.kpkn.screens.workout.technique.design.RecTimerPill
import com.example.kpkn.screens.workout.technique.design.RecordButton
import com.example.kpkn.screens.workout.technique.design.RecorderTopBar

/**
 * Pantalla completa de grabación de técnica en vídeo (VT8a).
 *
 * Se abre como capa/diálogo a pantalla completa sin Material genérico:
 * - Vista previa 16:9 en modo COMPATIBLE y FIT_CENTER;
 * - Barra superior con botón de cerrar, ejercicio, serie e insignias de 60 fps y disco;
 * - Silueta de guía anatómica según la vista preferida del perfil;
 * - Comprobación continua de encuadre (~2 Hz) con histéresis de 3 OK;
 * - Botón de grabación de 72 dp con cronómetro en píldora flotante;
 * - Registro de metadatos de captura (gravedad, focal y fps) al finalizar.
 */
@Composable
fun TechniqueRecorderScreen(
    request: TechniqueRecorderRequest,
    onDismiss: () -> Unit,
    mediaCapture: WorkoutMediaCaptureController?,
    modifier: Modifier = Modifier,
    prefer60FpsSetting: Boolean = true,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = (activity as? LifecycleOwner) ?: LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val profile = remember(request.exercise) {
        request.exercise?.let { ex ->
            val ref = ExerciseRef(
                definitionId = ex.catalogDefinitionId ?: ex.id,
                configurationId = ex.catalogConfigurationId,
                movementPatternId = ex.selectedMovementPattern,
                equipmentId = null,
                laterality = null,
                name = ex.name,
                isCustom = false,
            )
            (AnalysisProfileResolver.resolve(ref) as? ProfileResolution.Supported)?.profile
        }
    }

    val preferredView = profile?.framing?.preferredViews?.firstOrNull() ?: ViewKind.SIDE
    val usesPlate = profile?.usesPlate == true
    val framingSpec = profile?.framing ?: com.example.kpkn.domain.biomechanics.framing.FramingSpec(
        preferredViews = setOf(preferredView),
        needsPlate = usesPlate,
        needsFeet = true,
        needsFace = true,
    )

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var activeCameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var activeSession by remember { mutableStateOf<RecorderSession?>(null) }
    var plateDiameterMm by remember { mutableStateOf<Int?>(450) }
    var plateBadgeExpanded by remember { mutableStateOf(false) }

    val isRecordingFlow = remember(mediaCapture) { mediaCapture?.isRecording ?: MutableStateFlow(false) }
    val isRecordingState by isRecordingFlow.collectAsStateWithLifecycle()
    val recordedDurationFlow = remember(mediaCapture) { mediaCapture?.recordedDurationNanos ?: MutableStateFlow(0L) }
    val recordedDurationNanos by recordedDurationFlow.collectAsStateWithLifecycle()

    var isSimulatedRecording by remember { mutableStateOf(false) }
    var simulatedElapsedNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isSimulatedRecording) {
        if (isSimulatedRecording) {
            val startNs = System.nanoTime()
            while (isSimulatedRecording) {
                delay(100)
                simulatedElapsedNanos = System.nanoTime() - startNs
            }
        }
    }

    val effectiveIsRecording = isRecordingState || isSimulatedRecording
    val effectiveDurationNanos = if (isSimulatedRecording) simulatedElapsedNanos else recordedDurationNanos
    val elapsedSeconds = effectiveDurationNanos / 1_000_000_000L

    val metadataRecorder = remember(activeSession, activeCameraInfo) {
        CaptureMetadataRecorder(
            context = context,
            cameraInfo = activeCameraInfo,
            requestedFps = if (RecorderSessionFactory.wants60Fps(prefer60FpsSetting, profile?.id)) 60f else 30f,
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

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(cameraPermissionGranted) {
        if (!cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

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

    // Bloqueo y restauración de orientación durante grabación
    val originalOrientation = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }

    var isStopping by remember { mutableStateOf(false) }

    fun startRecording() {
        val logFile = File(context.getExternalFilesDir("vt-recorder"), "recorder_log.txt")
        logFile.appendText("startRecording: activeSession=$activeSession mediaCapture=$mediaCapture isStopping=$isStopping\n")
        if (mediaCapture == null || isStopping) return
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        framingController.isRecording = true
        metadataRecorder.start()

        if (activeSession != null) {
            activeSession?.videoCapture?.targetRotation = targetRotation
            mediaCapture.startTechniqueRecording(
                requestProvider = { request.captureRequest },
                audioGranted = audioPermissionGranted,
                session = activeSession!!,
                exercise = request.exercise,
                metadataRecorder = metadataRecorder,
                onFinalized = { dest ->
                    logFile.appendText("onFinalized: dest=$dest\n")
                    activity?.requestedOrientation = originalOrientation
                    onDismiss()
                },
            )
        } else {
            logFile.appendText("startRecording: simulando grabación por falta de cámara física\n")
            isSimulatedRecording = true
        }
    }

    fun stopRecording() {
        val logFile = File(context.getExternalFilesDir("vt-recorder"), "recorder_log.txt")
        logFile.appendText("stopRecording: isStopping=$isStopping effectiveIsRecording=$effectiveIsRecording\n")
        if (isStopping) return
        isStopping = true
        framingController.isRecording = false

        if (isSimulatedRecording) {
            isSimulatedRecording = false
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val dest = File(context.filesDir, "workout_media/simulated_technique.mp4").apply {
                        parentFile?.mkdirs()
                    }
                    context.resources.openRawResource(com.example.kpkn.R.raw.welcome_entreno).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    val meta = metadataRecorder.save(dest)
                    logFile.appendText("stopRecording: guardado vídeo simulado en $dest, capture.json escrito grantedFps=${meta.grantedFps}\n")
                }.onFailure { e ->
                    logFile.appendText("stopRecording error: ${e.stackTraceToString()}\n")
                }
                withContext(Dispatchers.Main) {
                    activity?.requestedOrientation = originalOrientation
                    onDismiss()
                }
            }
        } else if (mediaCapture != null && isRecordingState) {
            mediaCapture.stopVideo()
        } else {
            activity?.requestedOrientation = originalOrientation
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            orientationListener.disable()
            framingController.close()
            AnalysisGate.cameraBusy.value = false
            activity?.requestedOrientation = originalOrientation
        }
    }

    Dialog(
        onDismissRequest = {
            if (effectiveIsRecording) {
                stopRecording()
            } else {
                onDismiss()
            }
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
                val logDir = context.getExternalFilesDir("vt-recorder")?.apply { mkdirs() }
                val logFile = File(logDir, "recorder_log.txt")
                logFile.appendText("DisposableEffect: granted=$cameraPermissionGranted view=$view\n")
                if (!cameraPermissionGranted) return@DisposableEffect onDispose {}
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context.applicationContext)
                val listener = Runnable {
                    logFile.appendText("listener called\n")
                    val cameraProvider = try {
                        cameraProviderFuture.get()
                    } catch (e: Throwable) {
                        logFile.appendText("cameraProvider get error: ${e.stackTraceToString()}\n")
                        null
                    }
                    if (cameraProvider == null) {
                        logFile.appendText("cameraProvider is null\n")
                        return@Runnable
                    }
                    val want60 = RecorderSessionFactory.wants60Fps(prefer60FpsSetting, profile?.id)

                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    logFile.appendText("cameraSelector=$cameraSelector want60=$want60\n")
                    val camera = runCatching {
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
                        logFile.appendText("boundCamera success: tier=${session.tier} grantedFps=${session.grantedFps}\n")
                        boundCamera
                    }.onFailure { e ->
                        logFile.appendText("bind error: ${e.stackTraceToString()}\n")
                        android.util.Log.e("KpknRecorder", "Error al enlazar CameraX", e)
                    }.getOrNull()

                    if (camera != null) {
                        framingController.start {
                            view.bitmap
                        }
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

            // Capa con silueta anatómica de guía (solo cuando no está grabando)
            AnimatedVisibility(
                visible = !effectiveIsRecording,
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
                onCustomPlateSelected = { plateDiameterMm = -1 },
                onClose = {
                    if (effectiveIsRecording) stopRecording() else onDismiss()
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopCenter),
            )

            // Chip de comprobación de encuadre (arriba, bajo la barra superior, solo si no graba)
            AnimatedVisibility(
                visible = !effectiveIsRecording,
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

            // Controles inferiores: Cronómetro y Botón REC
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(
                    visible = effectiveIsRecording,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    RecTimerPill(elapsedSeconds = elapsedSeconds)
                }

                RecordButton(
                    isRecording = effectiveIsRecording,
                    onClick = {
                        val logFile = File(context.getExternalFilesDir("vt-recorder"), "recorder_log.txt")
                        logFile.appendText("RecordButton onClick: effectiveIsRecording=$effectiveIsRecording activeSession=$activeSession mediaCapture=$mediaCapture\n")
                        if (effectiveIsRecording) {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
