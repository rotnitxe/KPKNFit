package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.media.ExerciseAlbumModel
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.SessionExerciseDetailSheet
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.screens.workout.withExerciseCaptureContext
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Etiquetas de prueba de «Modo visual» (LV8). */
internal const val MediaFaceTag = "media_face"
internal const val MediaExpandedTag = "media_expanded"
internal const val MediaShutterTag = "media_shutter"

/** Rojo del botón de grabar del prototipo (A3). */
private val RecordRed = Color(0xFFEF6F64)

/** Duración de la expansión y del plegado de «Modo visual» (320 ms, curva estándar). */
private const val ExpandMillis = 320

/** Lo que hace el botón redondo: una foto o un vídeo (A3). */
private enum class MediaMode { PHOTO, VIDEO }

/**
 * «Modo visual» de la tarjeta de la serie (LV8, A3 y G3): la cámara usa el espacio que haya dentro de la tarjeta, con «Foto» y «Vídeo»,
 * «Ficha», las miniaturas de la sesión, «Galería», «Ver álbum» y la línea «Se guarda en el álbum con la serie 3 · 95 kg × 6 · RIR 2.»;
 * conserva la imagen «Técnica KPKN» (pequeña) y el icono del álbum. «Expandir» la lleva a casi toda la pantalla ENCIMA de todo (la tarjeta
 * crece desde su sitio, 320 ms) y plegar o Atrás la devuelve; la tarjeta de debajo no cambia de tamaño.
 *
 * La cámara (CameraX) respeta [isSettledPage] y `stopIfRecording` y no se suelta mientras se graba, tampoco al expandir y plegar: solo
 * cambia la vista donde se pinta ([MediaCameraSession]). [effortLabel] es el esfuerzo marcado en la tarjeta («RIR 2»); [startExpanded]
 * abre el overlay de entrada (pruebas y hojas de render).
 */
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
    onRetryPendingCaptures: () -> Unit = {},
    onOpenRecorder: ((WorkoutMediaCaptureRequest) -> Unit)? = null,
    effortLabel: String? = null,
    onShowHistory: (() -> Unit)? = null,
    startExpanded: Boolean = false,
    /** Solo para hojas de render y pruebas: muestra los controles de la cámara aunque no haya controlador de captura. */
    showControlsWithoutCamera: Boolean = false,
    onOpenAnalysis: ((String) -> Unit)? = null,
    recorderOpen: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
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
    val normalizedActiveSide = com.example.kpkn.data.media.WorkoutMediaSides.normalize(side, exercise)
    val exerciseFiles = remember(sessionMedia, exercise.id, exerciseKey, setIndex, normalizedActiveSide) {
        sessionMedia.filter { item ->
            val matchesExercise = item.exerciseId == exercise.id ||
                item.canonicalExerciseId == exerciseKey ||
                item.exerciseId == exerciseKey
            val matchesSet = item.setIndex == null || item.setIndex == setIndex
            val matchesSide = com.example.kpkn.data.media.WorkoutMediaSides.normalize(item.side, exercise) == normalizedActiveSide
            matchesExercise && matchesSet && matchesSide
        }
    }
    var cameraReady by remember { mutableStateOf(false) }
    var previewMedia by remember { mutableStateOf<WorkoutMedia?>(null) }
    var mode by remember { mutableStateOf(MediaMode.VIDEO) }
    var expanded by remember { mutableStateOf(startExpanded) }
    var showAlbum by remember { mutableStateOf(false) }
    var showFicha by remember { mutableStateOf(false) }
    var faceBounds by remember { mutableStateOf(Rect.Zero) }
    val canonicalKey = exercise.canonicalExerciseId ?: exercise.exerciseDbId ?: exerciseKey
    val captureRequest = WorkoutMediaCaptureRequest(
        exerciseId = exercise.id,
        canonicalExerciseId = canonicalKey,
        exerciseName = exercise.name,
        setIndex = setIndex,
        side = side,
        weightKg = weightKg,
        reps = reps,
        isPr = isPr,
    ).withExerciseCaptureContext(exercise)

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

    LaunchedEffect(hasCamera, mediaCapture) {
        if (mediaCapture == null) {
            cameraReady = false
        } else if (hasCamera) {
            cameraReady = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(isSettledPage) {
        onMediaPageSettledChanged(
            isSettledPage = isSettledPage,
            stopIfRecording = { mediaCapture?.stopIfRecording() },
            foldExpanded = { expanded = false },
        )
    }

    val cameraActive = !recorderOpen && isMediaCameraActive(cameraReady, isSettledPage, mediaCapture != null)
    val session = rememberMediaCameraSession(
        mediaCapture = mediaCapture,
        active = cameraActive,
        isRecording = recording,
        bindGeneration = bindGeneration,
        onBindFailed = { mediaCapture?.fallbackVideoCaptureToSd() },
        stopOnDispose = !recorderOpen,
    )

    fun onShutter() {
        if (mode == MediaMode.VIDEO && !recording && onOpenRecorder != null) {
            expanded = false
            onOpenRecorder(captureRequest)
            return
        }
        if (mediaCapture == null) return
        if (mode == MediaMode.PHOTO) mediaCapture.takePhoto(captureRequest) else toggleVideo()
    }

    val loadLabel = ExerciseAlbumModel.loadLabel(weightKg, reps)
    val details = listOfNotNull(loadLabel, effortLabel).joinToString(" · ")
    val saveLine = buildString {
        append("Se guarda en el álbum con la serie ${setIndex + 1}")
        if (details.isNotEmpty()) append(" · ").append(details)
        append('.')
    }
    val fontScale = density.fontScale

    @Composable
    fun Stage(stageModifier: Modifier, compact: Boolean, showPreview: Boolean) {
        MediaStage(
            modifier = stageModifier,
            compact = compact,
            session = session,
            cameraReady = cameraReady && mediaCapture != null,
            showPreview = showPreview,
            mode = mode,
            onModeChange = { mode = it },
            recording = recording,
            captureEnabled = (cameraReady && mediaCapture != null) ||
                (mode == MediaMode.VIDEO && onOpenRecorder != null) || showControlsWithoutCamera,
            onShutter = { onShutter() },
            onGallery = { galleryLauncher.launch(arrayOf("image/*", "video/*")) },
            galleryEnabled = mediaCapture != null || showControlsWithoutCamera,
            onFicha = { showFicha = true },
            thumbs = exerciseFiles,
            onThumb = { previewMedia = it },
            onAlbum = { showAlbum = true },
            albumEnabled = mediaCapture != null || showControlsWithoutCamera,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        KpknModule(
            modifier = Modifier
                .fillMaxSize()
                .testTag(MediaFaceTag)
                .onGloballyPositioned { faceBounds = it.boundsInRoot() },
            contentPadding = PaddingValues(10.dp),
        ) {
            // Cabecera: volver a «Serie N», el icono del álbum y «Expandir».
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaFacePill(
                    text = "Serie ${setIndex + 1}",
                    leading = {
                        Icon(
                            KpknLineIcons.ChevronLeft,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = WizardColors.text,
                        )
                    },
                    onClick = onFlipBack,
                    description = "Volver a la serie",
                )
                Box(modifier = Modifier.weight(1f))
                if (mediaCapture != null || showControlsWithoutCamera) {
                    MediaFaceIconButton(
                        description = "Álbum",
                        onClick = { showAlbum = true },
                    ) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = WizardColors.text,
                        )
                    }
                    Box(modifier = Modifier.width(6.dp))
                }
                // Con la letra muy grande «Expandir» queda solo con su icono para que la cabecera no se parta.
                MediaFacePill(
                    text = if (fontScale > 1.15f) null else "Expandir",
                    leading = { ExpandGlyph(expand = true) },
                    onClick = { expanded = true },
                    description = "Expandir",
                    modifier = Modifier.testTag("media_expand"),
                )
            }
            Box(modifier = Modifier.height(8.dp))
            Stage(
                stageModifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                compact = true,
                showPreview = !expanded,
            )
            Box(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (techniqueRes != null) {
                    Image(
                        painter = painterResource(techniqueRes),
                        contentDescription = "Foto de técnica de ${exercise.name}",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KpknSurface.inset),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (captureError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                captureError.orEmpty(),
                                modifier = Modifier.weight(1f, fill = false),
                                style = KpknType.caption,
                                color = WizardColors.danger,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Reintentar",
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 32.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = onRetryPendingCaptures,
                                    )
                                    .padding(vertical = 6.dp),
                                style = KpknType.action,
                                color = WizardColors.text,
                            )
                        }
                    } else {
                        Text(
                            saveLine,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Expandido (G3): ENCIMA de todo, en el anfitrión de superposiciones de la app.
        if (expanded) {
            KpknPortal {
                ExpandedMediaOverlay(
                    startBounds = faceBounds,
                    title = "Modo visual · serie ${setIndex + 1}",
                    subtitle = details,
                    onFolded = { expanded = false },
                ) { stageModifier ->
                    Stage(stageModifier = stageModifier, compact = false, showPreview = true)
                }
            }
        }
        if (showAlbum && mediaCapture != null) {
            ExerciseAlbumSheet(
                exerciseName = exercise.name,
                keys = listOfNotNull(
                    canonicalKey,
                    exercise.canonicalExerciseId,
                    exercise.exerciseDbId,
                    exerciseKey,
                    exercise.id,
                ).distinct(),
                mediaCapture = mediaCapture,
                onDismiss = { showAlbum = false },
                onOpenAnalysis = onOpenAnalysis,
            )
        }
        if (showFicha) {
            SessionExerciseDetailSheet(
                exercise = exercise,
                onDismiss = { showFicha = false },
                onShowHistory = onShowHistory?.let { openHistory ->
                    {
                        // El historial vive en el cuerpo de la sesión: liberar solo su velo,
                        // conservando la cámara y la grabación en la cara compacta.
                        expanded = false
                        openHistory()
                    }
                },
            )
        }
    }
    previewMedia?.let { item ->
        WorkoutMediaPreviewDialog(
            media = item,
            onDismiss = { previewMedia = null },
            poseOverlayEnabled = mediaCapture?.isPoseTrajectoryEnabled() == true,
            onOpenAnalysis = onOpenAnalysis,
        )
    }
}

/** Píldora de la cabecera (A3): `inset` de 32 dp con icono y texto; la zona táctil mide 48 dp. Sin texto, queda el icono. */
@Composable
private fun MediaFacePill(
    text: String?,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill)
                .padding(start = 10.dp, end = if (text == null) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            leading()
            if (text != null) Text(text, style = KpknType.chip, color = WizardColors.text, maxLines = 1)
        }
    }
}

@Composable
private fun MediaFaceIconButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Las flechas de «Expandir» y «Plegar» del prototipo, dibujadas con el trazo de la casa. */
@Composable
private fun ExpandGlyph(expand: Boolean) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val ink = Color(0xFFF2EEE6)
        val unit = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(ink, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), strokeWidth = stroke.width, cap = StrokeCap.Round)
        if (expand) {
            line(4f, 9f, 4f, 4f); line(4f, 4f, 9f, 4f)
            line(20f, 15f, 20f, 20f); line(20f, 20f, 15f, 20f)
            line(4f, 4f, 10f, 10f); line(20f, 20f, 14f, 14f)
        } else {
            line(9f, 4f, 9f, 9f); line(9f, 9f, 4f, 9f)
            line(15f, 20f, 15f, 15f); line(15f, 15f, 20f, 15f)
            line(9f, 9f, 3f, 3f); line(15f, 15f, 21f, 21f)
        }
    }
}

/**
 * El escenario de «Modo visual»: la vista previa de la cámara (o, sin ella, un fondo con la rejilla de tercios), el selector «Foto» y
 * «Vídeo» y «Ficha» arriba, y abajo las miniaturas de la sesión, «Galería», «Ver álbum» y el botón redondo (A3, G3). Lo usan la tarjeta
 * y el overlay expandido; solo cambia su tamaño. Las miniaturas que caben dependen del ancho.
 */
@Composable
private fun MediaStage(
    modifier: Modifier,
    compact: Boolean,
    session: MediaCameraSession,
    cameraReady: Boolean,
    showPreview: Boolean,
    mode: MediaMode,
    onModeChange: (MediaMode) -> Unit,
    recording: Boolean,
    captureEnabled: Boolean,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    galleryEnabled: Boolean,
    onFicha: () -> Unit,
    thumbs: List<WorkoutMedia>,
    onThumb: (WorkoutMedia) -> Unit,
    onAlbum: () -> Unit,
    albumEnabled: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(if (compact) 22.dp else 24.dp)
    val shutterOuter = if (compact) 60.dp else 68.dp
    val shutterInner = if (compact) 44.dp else 50.dp
    val thumbSize = if (compact) 36.dp else 44.dp
    val maxThumbs = if (compact) 3 else 4
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2622), Color(0xFF121110)),
                    center = Offset.Unspecified,
                ),
            ),
    ) {
        if (showPreview && cameraReady) {
            MediaCameraSurface(session = session, modifier = Modifier.fillMaxSize())
        } else if (!cameraReady) {
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Activa la cámara para grabar desde esta tarjeta",
                    style = KpknType.caption,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 3,
                )
            }
        }
        // Rejilla de tercios del prototipo, muy tenue, por encima de la imagen.
        Canvas(Modifier.fillMaxSize()) {
            val lineColor = Color(0xFFF2EEE6).copy(alpha = 0.06f)
            val strokeWidth = 1.dp.toPx()
            for (i in 1..2) {
                val x = size.width * i / 3f
                val y = size.height * i / 3f
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
            }
        }

        // Arriba a la izquierda: «Foto» y «Vídeo».
        ModeSwitch(
            mode = mode,
            onModeChange = onModeChange,
            enabled = !recording,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )
        // Arriba a la derecha: «Ficha». Su zona táctil de 48 dp sobresale 8 dp de la píldora: sube 8 dp para quedar a la altura del selector.
        OverlayPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 12.dp),
            description = "Ficha del ejercicio",
            onClick = onFicha,
        ) {
            Icon(
                KpknLineIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = WizardColors.text,
            )
            Text("Ficha", style = KpknType.chip, color = WizardColors.text, maxLines = 1)
        }

        // Abajo a la izquierda: «Galería», las miniaturas de la sesión y «Ver álbum»; abajo a la derecha, el botón redondo (A3, G3). Van en una sola
        // fila si caben al menos dos miniaturas junto a los dos botones; si no (la tarjeta, que es estrecha, y los teléfonos de 360 dp), en dos filas:
        // arriba «Galería» y las miniaturas que caben y debajo «Ver álbum». Así las miniaturas de la sesión se conservan en las dos vistas.
        val leftMax = maxWidth - 12.dp - (shutterOuter + 16.dp + 8.dp)
        // El grupo se centra en vertical con el botón redondo.
        val leftBottom = 14.dp + (shutterOuter - 48.dp) / 2
        val galleryWidth = when {
            !galleryEnabled -> 0.dp
            compact -> 48.dp
            else -> with(density) { measurer.measure(AnnotatedString("Galería"), KpknType.chip).size.width.toDp() } + 24.dp
        }
        val albumWidth = if (albumEnabled) {
            with(density) { measurer.measure(AnnotatedString("Ver álbum"), KpknType.chip).size.width.toDp() } + 20.dp
        } else {
            0.dp
        }
        val maxShown = minOf(maxThumbs, thumbs.size)
        val oneRowRoom = leftMax - 12.dp - (if (galleryEnabled) galleryWidth + 6.dp else 0.dp) - (if (albumEnabled) albumWidth + 6.dp else 0.dp)
        val oneRowThumbs = thumbCountThatFits(oneRowRoom, thumbSize, 6.dp, maxShown)
        val twoRows = compact || oneRowThumbs < minOf(2, maxShown)
        val galleryButton: @Composable () -> Unit = {
            if (galleryEnabled) {
                if (compact) {
                    OverlayCircleButton(description = "Galería", onClick = onGallery) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = WizardColors.text,
                        )
                    }
                } else {
                    OverlayPill(description = "Galería", onClick = onGallery) {
                        Text("Galería", style = KpknType.chip, color = WizardColors.text, maxLines = 1)
                    }
                }
            }
        }
        val thumbTiles: @Composable (List<WorkoutMedia>) -> Unit = { list ->
            list.forEach { media ->
                WorkoutMediaThumb(
                    media = media,
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(RoundedCornerShape(12.dp)),
                    onClick = { onThumb(media) },
                )
            }
        }
        // Expandido, el grupo lleva su fondo translúcido como en G3 (el contenedor mide 12 dp más que las filas); en la tarjeta, no.
        val groupModifier = if (compact) {
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = leftBottom)
        } else {
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = leftBottom - 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(6.dp)
        }
        if (twoRows) {
            val room = leftMax - (if (compact) 0.dp else 12.dp) - (if (galleryEnabled) galleryWidth + 6.dp else 0.dp)
            val shown = thumbs.take(thumbCountThatFits(room, thumbSize, 6.dp, maxShown))
            Column(modifier = groupModifier) {
                if (galleryEnabled || shown.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        galleryButton()
                        thumbTiles(shown)
                    }
                }
                if (albumEnabled) AlbumPill(onClick = onAlbum)
            }
        } else {
            Row(
                modifier = groupModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                thumbTiles(thumbs.take(oneRowThumbs))
                galleryButton()
                if (albumEnabled) AlbumPill(onClick = onAlbum)
            }
        }

        // Abajo a la derecha: el botón redondo (foto o vídeo, según el selector).
        ShutterButton(
            mode = mode,
            recording = recording,
            enabled = captureEnabled,
            outer = shutterOuter,
            inner = shutterInner,
            onClick = onShutter,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp),
        )
    }
}

/** «Ver álbum» (A3): píldora de tinta de 32 dp con texto oscuro; la zona táctil mide 48 dp. */
@Composable
private fun AlbumPill(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(WizardColors.text, KpknShape.pill)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Ver álbum", style = KpknType.chip, color = WizardColors.ctaContent, maxLines = 1)
        }
    }
}

/** Cuántas miniaturas de [thumb] caben en [available] con [gap] entre ellas, sin pasar de [max]. */
internal fun thumbCountThatFits(available: Dp, thumb: Dp, gap: Dp, max: Int): Int {
    if (max <= 0 || available <= 0.dp) return 0
    return ((available + gap) / (thumb + gap)).toInt().coerceIn(0, max)
}

/** Selector «Foto» y «Vídeo» sobre la cámara (A3): píldora translúcida con el elegido en tinta. */
@Composable
private fun ModeSwitch(
    mode: MediaMode,
    onModeChange: (MediaMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(3.dp),
    ) {
        listOf(MediaMode.PHOTO to "Foto", MediaMode.VIDEO to "Vídeo").forEach { (option, label) ->
            val selected = option == mode
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) WizardColors.text else Color.Transparent, RoundedCornerShape(13.dp))
                    .clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onModeChange(option) },
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = KpknType.chip,
                    color = if (selected) WizardColors.ctaContent else WizardColors.text.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Píldora translúcida sobre la cámara (A3: «Ficha», «Galería»): 32 dp con filete al 18 %; la zona táctil mide 48 dp. */
@Composable
private fun OverlayPill(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(Color.Black.copy(alpha = 0.45f), KpknShape.pill)
                .border(1.dp, Color(0xFFF2EEE6).copy(alpha = 0.18f), KpknShape.pill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun OverlayCircleButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .border(1.dp, Color(0xFFF2EEE6).copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** El botón redondo de grabar o de hacer la foto (A3): aro de tinta de 3 dp con el disco dentro; al grabar, el disco pasa a cuadrado. */
@Composable
private fun ShutterButton(
    mode: MediaMode,
    recording: Boolean,
    enabled: Boolean,
    outer: Dp,
    inner: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val label = when {
        recording -> "Parar grabación"
        mode == MediaMode.PHOTO -> "Hacer foto"
        else -> "Grabar vídeo"
    }
    Box(
        modifier = modifier
            .size(outer)
            .testTag(MediaShutterTag)
            .clip(CircleShape)
            .border(3.dp, WizardColors.text.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val fill = if (mode == MediaMode.VIDEO || recording) RecordRed else WizardColors.text
        Box(
            modifier = Modifier
                .size(if (recording) inner * 0.55f else inner)
                .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
                .background(fill.copy(alpha = if (enabled) 1f else 0.4f)),
        )
    }
}

/**
 * «Modo visual» expandido (G3): ENCIMA de todo, con la animación de la tarjeta que crece desde su sitio ([startBounds], 320 ms con la
 * curva estándar) hasta casi toda la pantalla y, al plegar o con Atrás, la animación inversa. Con «reducir movimiento», sin animación.
 * [onFolded] avisa cuando ya está plegado. El escenario va en [stage]; la tarjeta de debajo no cambia de tamaño.
 */
@Composable
private fun ExpandedMediaOverlay(
    startBounds: Rect,
    title: String,
    subtitle: String,
    onFolded: () -> Unit,
    stage: @Composable (Modifier) -> Unit,
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    var folding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(durationMillis = ExpandMillis, easing = FastOutSlowInEasing))
    }
    DisposableEffectTracker()
    val fold: () -> Unit = {
        if (!folding) {
            folding = true
            scope.launch {
                if (reduced) progress.snapTo(0f) else progress.animateTo(0f, tween(durationMillis = ExpandMillis, easing = FastOutSlowInEasing))
                onFolded()
            }
        }
    }
    BackHandler(enabled = true) { fold() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(300f),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val sideMargin = with(density) { 8.dp.toPx() }
        val verticalMargin = with(density) { 16.dp.toPx() }
        val topInset = with(density) { insets.calculateTopPadding().toPx() }
        val bottomInset = with(density) { insets.calculateBottomPadding().toPx() }
        val target = Rect(sideMargin, topInset + verticalMargin, widthPx - sideMargin, heightPx - bottomInset - verticalMargin)
        val start = if (startBounds.width > 1f && startBounds.height > 1f) startBounds else target
        val p = progress.value
        val rect = lerp(start, target, p)
        val radius = 28.dp + 2.dp * p

        // El velo: oscurece el fondo a la vez que crece y se traga los toques (plegar es con su botón o con Atrás).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * p))
                .pointerInput(Unit) { detectTapGestures { } },
        )
        Column(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() },
                )
                .clip(RoundedCornerShape(radius))
                .background(KpknSurface.module, RoundedCornerShape(radius))
                .border(1.dp, Color(0xFFF2EEE6).copy(alpha = 0.10f), RoundedCornerShape(radius))
                .testTag(MediaExpandedTag),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = p }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = fold)
                        .semantics { contentDescription = "Plegar a la tarjeta" }
                        .testTag("media_fold"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F1F1F), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { ExpandGlyph(expand = false) }
                }
            }
            stage(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

/** Mientras el overlay está abierto, la isla superior se oculta (como con las hojas): sin esto quedaría por encima. */
@Composable
private fun DisposableEffectTracker() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
}
