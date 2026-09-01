package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
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
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.models.Exercise

@Composable
internal fun SetCardExerciseMediaBack(
    exercise: Exercise,
    onFlipBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    var userFiles by remember(exerciseKey) {
        mutableStateOf(ExerciseUserMediaStore.list(context, exerciseKey))
    }
    var cameraReady by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember {
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD)).build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val dest = ExerciseUserMediaStore.newPhotoFile(context, exerciseKey)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        userFiles = ExerciseUserMediaStore.list(context, exerciseKey)
    }

    fun refresh() {
        userFiles = ExerciseUserMediaStore.list(context, exerciseKey)
    }

    fun takePhoto() {
        val file = ExerciseUserMediaStore.newPhotoFile(context, exerciseKey)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureError = null
                    refresh()
                }

                override fun onError(exception: ImageCaptureException) {
                    captureError = "No se pudo guardar la foto"
                }
            },
        )
    }

    fun toggleVideo() {
        if (recording) {
            activeRecording?.stop()
            activeRecording = null
            recording = false
            return
        }
        val file = ExerciseUserMediaStore.newVideoFile(context, exerciseKey)
        val pending = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = false
                    activeRecording = null
                    if (event.hasError()) {
                        captureError = "No se pudo guardar el vídeo"
                        file.delete()
                    } else {
                        captureError = null
                        refresh()
                    }
                }
            }
        activeRecording = pending
        recording = true
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
    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
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
                )
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
                if (cameraReady) {
                    InCardCameraPreview(
                        imageCapture = imageCapture,
                        videoCapture = videoCapture,
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
                    onClick = { takePhoto() },
                    modifier = Modifier.weight(1f),
                    enabled = cameraReady,
                )
                MediaActionChip(
                    text = if (recording) "Parar" else "Vídeo",
                    icon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    onClick = { toggleVideo() },
                    modifier = Modifier.weight(1f),
                    enabled = cameraReady,
                    selected = recording,
                )
                MediaActionChip(
                    text = "Galería",
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                )
            }

            if (userFiles.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(userFiles, key = { it.absolutePath }) { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = file.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WorkoutUiTokens.setInnerHighestColor()),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
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
    DisposableEffect(previewView, lifecycleOwner, imageCapture, videoCapture) {
        val view = previewView ?: return@DisposableEffect onDispose { }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    videoCapture,
                )
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
        }
    }
}
