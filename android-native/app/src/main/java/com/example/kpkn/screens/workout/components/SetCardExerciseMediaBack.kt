package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest

@Composable
internal fun SetCardExerciseMediaBack(
    exercise: Exercise,
    onFlipBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSettledPage: Boolean = true,
    setIndex: Int = 0,
    side: String? = null,
    weightKg: Double? = null,
    reps: Int? = null,
    isPr: Boolean = false,
    mediaCapture: WorkoutMediaCaptureController? = null,
) {
    val context = LocalContext.current
    val exerciseKey = exercise.catalogDefinitionId
        ?: exercise.exerciseDbId
        ?: exercise.id
    val techniqueRes = remember(exercise.id, exercise.catalogDefinitionId, exercise.catalogConfigurationId) {
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = exercise.catalogDefinitionId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.id,
            catalogConfigurationId = exercise.catalogConfigurationId,
            selectedImplementation = exercise.selectedExecutionOption
                ?: exercise.selectedAspects?.values?.firstOrNull(),
        )
    }
    val emptyMedia = remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<WorkoutMedia>()) }
    val idleRecording = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val idleError = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
    val idleBind = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
    val sessionMedia by (mediaCapture?.sessionMedia ?: emptyMedia).collectAsStateWithLifecycle()
    val recording by (mediaCapture?.isRecording ?: idleRecording).collectAsStateWithLifecycle()
    val captureError by (mediaCapture?.captureError ?: idleError).collectAsStateWithLifecycle()
    val bindGeneration by (mediaCapture?.bindGeneration ?: idleBind).collectAsStateWithLifecycle()
    val exerciseFiles = remember(sessionMedia, exercise.id, exerciseKey) {
        sessionMedia.filter { item ->
            item.exerciseId == exercise.id ||
                item.canonicalExerciseId == exerciseKey ||
                item.exerciseId == exerciseKey
        }
    }
    var cameraReady by remember { mutableStateOf(false) }
    var previewMedia by remember { mutableStateOf<WorkoutMedia?>(null) }
    val captureRequest = WorkoutMediaCaptureRequest(
        exerciseId = exercise.id,
        canonicalExerciseId = exercise.canonicalExerciseId ?: exercise.exerciseDbId ?: exerciseKey,
        exerciseName = exercise.name,
        setIndex = setIndex,
        side = side,
        weightKg = weightKg,
        reps = reps,
        isPr = isPr,
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        mediaCapture?.ingestUri(uri, captureRequest)
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        mediaCapture?.toggleVideo(granted, captureRequest)
    }

    fun toggleVideo() {
        if (mediaCapture == null) return
        if (recording) {
            mediaCapture.stopVideo()
            return
        }
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        mediaCapture.toggleVideo(true, captureRequest)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraReady = granted
    }
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            cameraReady = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(isSettledPage) {
        if (!isSettledPage) {
            mediaCapture?.stopIfRecording()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = WorkoutUiTokens.CardShape,
        color = WorkoutUiTokens.setCardColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onFlipBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver a la serie",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
                Text(
                    "Ver ejercicio/Fotos",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { mediaCapture?.openAlbumSheet() },
                    modifier = Modifier.size(32.dp),
                    enabled = mediaCapture != null,
                ) {
                    Icon(
                        Icons.Default.Collections,
                        contentDescription = "Álbum",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
            }

            Text(
                "Técnica KPKN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = RoundedCornerShape(12.dp),
                color = WorkoutUiTokens.setInnerColor(),
            ) {
                if (techniqueRes != null) {
                    Image(
                        painter = painterResource(techniqueRes),
                        contentDescription = "Foto de técnica de ${exercise.name}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text(
                            "Aún no hay foto de técnica",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                if (cameraReady && isSettledPage && mediaCapture != null) {
                    InCardCameraPreview(
                        imageCapture = mediaCapture.imageCapture,
                        videoCapture = mediaCapture.videoCapture,
                        bindCamera = isSettledPage,
                        isRecording = recording,
                        bindGeneration = bindGeneration,
                        onBindFailed = { mediaCapture.fallbackVideoCaptureToSd() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Activa la cámara para grabar desde esta tarjeta",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (captureError != null) {
                Text(
                    captureError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = WorkoutUiTokens.dangerColor(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MediaActionChip(
                    text = "Foto",
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    onClick = { mediaCapture?.takePhoto(captureRequest) },
                    modifier = Modifier.weight(1f),
                    enabled = cameraReady && mediaCapture != null,
                )
                MediaActionChip(
                    text = if (recording) "Parar" else "Vídeo",
                    icon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    onClick = { toggleVideo() },
                    modifier = Modifier.weight(1f),
                    enabled = cameraReady && mediaCapture != null,
                    selected = recording,
                )
                MediaActionChip(
                    text = "Galería",
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    onClick = { galleryLauncher.launch(arrayOf("image/*", "video/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = mediaCapture != null,
                )
            }

            if (exerciseFiles.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(exerciseFiles, key = { it.id }) { item ->
                        WorkoutMediaThumb(
                            media = item,
                            modifier = Modifier.size(56.dp),
                            onClick = { previewMedia = item },
                        )
                    }
                }
            }
        }
    }
    previewMedia?.let { item ->
        WorkoutMediaPreviewDialog(
            media = item,
            onDismiss = { previewMedia = null },
            poseOverlayEnabled = mediaCapture?.isPoseTrajectoryEnabled() == true,
        )
    }
}

@Composable
private fun MediaActionChip(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        } else {
            WorkoutUiTokens.setInnerHighestColor()
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            icon()
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InCardCameraPreview(
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>,
    modifier: Modifier = Modifier,
    bindCamera: Boolean = true,
    isRecording: Boolean = false,
    bindGeneration: Int = 0,
    onBindFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val recordingRef = rememberUpdatedRecording(isRecording)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView = this
            }
        },
    )
    DisposableEffect(previewView, lifecycleOwner, imageCapture, videoCapture, bindCamera, bindGeneration) {
        if (!bindCamera) return@DisposableEffect onDispose { }
        val view = previewView ?: return@DisposableEffect onDispose { }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val bound = runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    videoCapture,
                )
            }
            if (bound.isFailure) {
                onBindFailed()
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            if (recordingRef()) return@onDispose
            runCatching { providerFuture.get().unbindAll() }
        }
    }
}

@Composable
private fun rememberUpdatedRecording(isRecording: Boolean): () -> Boolean {
    val latest = remember { mutableStateOf(isRecording) }
    latest.value = isRecording
    return { latest.value }
}
