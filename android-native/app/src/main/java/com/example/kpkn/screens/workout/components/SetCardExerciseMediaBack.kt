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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.SessionExerciseDetailSheet
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.screens.workout.design.VideoThumbBadge
import com.example.kpkn.screens.workout.design.VisualModeAnalysisAction
import com.example.kpkn.screens.workout.design.VisualModeAnalysisLine
import com.example.kpkn.screens.workout.design.fitVisualModeAnalysisText
import com.example.kpkn.screens.workout.design.videoThumbContentDescription
import com.example.kpkn.screens.workout.design.visualModeAnalysisLine
import com.example.kpkn.screens.workout.technique.TechniqueRecorderRequest
import com.example.kpkn.screens.workout.technique.TechniqueRecorderStage
import com.example.kpkn.screens.workout.technique.stopTechniqueRecorderBeforeExit
import com.example.kpkn.screens.workout.withExerciseCaptureContext
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Etiquetas de prueba de «Modo visual» (LV8). */
internal const val MediaFaceTag = "media_face"
internal const val MediaExpandedTag = "media_expanded"
internal const val MediaShutterTag = "media_shutter"

/** Rojo del botón de grabar del prototipo (A3). */
internal val RecordRed = Color(0xFFEF6F64)

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
 * En «Foto», expandir y plegar solo traslada la superficie de [MediaCameraSession]. En «Vídeo», la tarjeta cede la cámara al grabador
 * y el plegado espera Finalize antes de devolverla. [effortLabel] es el esfuerzo marcado en la tarjeta («RIR 2»); [startExpanded]
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
    effortLabel: String? = null,
    onShowHistory: (() -> Unit)? = null,
    startExpanded: Boolean = false,
    /** Solo para hojas de render y pruebas: muestra los controles de la cámara aunque no haya controlador de captura. */
    showControlsWithoutCamera: Boolean = false,
    onOpenAnalysis: ((String) -> Unit)? = null,
    /** Datos de ejemplo para hojas de render, sin abrir la cámara ni leer Room. */
    mediaFilesOverride: List<WorkoutMedia>? = null,
    videoThumbBadges: Map<String, VideoThumbBadge> = emptyMap(),
    onRetryAnalysis: ((String) -> Unit)? = null,
    videoAnalysis: SetVideoAnalysisUi? = null,
    /** Error de ejemplo para comprobar el pie sin iniciar una captura. */
    captureErrorOverride: String? = null,
    techniqueVideoSettings: TechniqueVideoSettings = TechniqueVideoSettings(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val exerciseKey = exercise.catalogDefinitionId
        ?: exercise.exerciseDbId
        ?: exercise.id
    val techniqueRes = remember(exercise.id, exercise.catalogDefinitionId, exercise.catalogConfigurationId,
        exercise.exerciseDbId, exercise.selectedExecutionOption, exercise.selectedAspects) {
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
    val observedCaptureError by (mediaCapture?.captureError ?: idleError).collectAsStateWithLifecycle()
    val captureError = captureErrorOverride ?: observedCaptureError
    val bindGeneration by (mediaCapture?.bindGeneration ?: idleBind).collectAsStateWithLifecycle()
    val normalizedActiveSide = com.example.kpkn.data.media.WorkoutMediaSides.normalize(side, exercise)
    val exerciseFiles = remember(sessionMedia, exercise.id, exerciseKey, setIndex, normalizedActiveSide, mediaFilesOverride) {
        if (mediaFilesOverride != null) return@remember mediaFilesOverride
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
    var techniqueOpen by remember { mutableStateOf(false) }
    var foldRequested by remember { mutableStateOf(false) }
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
            foldExpanded = { expanded = false; techniqueOpen = false; foldRequested = false },
        )
    }

    val cameraActive = !techniqueOpen && isMediaCameraActive(cameraReady, isSettledPage, mediaCapture != null)
    val session = rememberMediaCameraSession(
        mediaCapture = mediaCapture,
        active = cameraActive,
        isRecording = recording,
        bindGeneration = bindGeneration,
        onBindFailed = { mediaCapture?.fallbackVideoCaptureToSd() },
        stopOnDispose = !techniqueOpen,
    )

    fun openExpanded() {
        foldRequested = false
        techniqueOpen = mode == MediaMode.VIDEO
        expanded = true
    }

    fun onShutter() {
        if (mode == MediaMode.VIDEO) openExpanded()
        else mediaCapture?.takePhoto(captureRequest)
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
                (mode == MediaMode.VIDEO) || showControlsWithoutCamera,
            onShutter = { onShutter() },
            onGallery = { galleryLauncher.launch(arrayOf("image/*", "video/*")) },
            galleryEnabled = mediaCapture != null || showControlsWithoutCamera,
            onFicha = { showFicha = true },
            thumbs = exerciseFiles,
            onThumb = { previewMedia = it },
            onAlbum = { showAlbum = true },
            albumEnabled = mediaCapture != null || showControlsWithoutCamera,
            videoThumbBadges = videoThumbBadges,
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
                    onClick = { openExpanded() },
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
                val line = when {
                    captureError != null -> VisualModeAnalysisLine(
                        "${captureError.orEmpty()} · reintentar", "reintentar", VisualModeAnalysisAction.RETRY,
                    )
                    videoAnalysis != null -> visualModeAnalysisLine(videoAnalysis.state, setIndex)
                    else -> VisualModeAnalysisLine(saveLine)
                }
                val onLineAction: (() -> Unit)? = when {
                    captureError != null -> onRetryPendingCaptures
                    line.action == VisualModeAnalysisAction.OPEN -> onOpenAnalysis?.let { open -> { open(videoAnalysis!!.mediaId) } }
                    line.action == VisualModeAnalysisAction.RETRY -> onRetryAnalysis?.let { retry -> { retry(videoAnalysis!!.mediaId) } }
                    else -> null
                }
                MediaAnalysisLine(
                    line = line,
                    onAction = onLineAction,
                    color = when {
                        captureError != null -> WizardColors.danger
                        videoAnalysis?.state is SetVideoAnalysisState.Unsupported -> WizardColors.textFaint
                        else -> WizardColors.textMuted
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Expandido (G3): ENCIMA de todo, en el anfitrión de superposiciones de la app.
        if (expanded) {
            KpknPortal {
                ExpandedMediaOverlay(
                    startBounds = faceBounds,
                    title = "Modo visual · serie ${setIndex + 1}",
                    subtitle = details,
                    onFolded = { expanded = false; techniqueOpen = false; foldRequested = false },
                    foldRequested = foldRequested,
                    beforeFold = {
                        if (techniqueOpen) mediaCapture?.let {
                            stopTechniqueRecorderBeforeExit(it::stopIfRecording, it.isRecording)
                        }
                    },
                    stage = { stageModifier ->
                        if (techniqueOpen) TechniqueRecorderStage(
                            request = TechniqueRecorderRequest(captureRequest, exercise, "Serie ${setIndex + 1}"),
                            mediaCapture = mediaCapture,
                            prefer60FpsSetting = techniqueVideoSettings.prefer60Fps,
                            maxRecordSeconds = techniqueVideoSettings.maxRecordSeconds,
                            defaultPlateDiameterMm = techniqueVideoSettings.plateDiameterMm,
                            onFinished = { foldRequested = true },
                            onSwitchToPhoto = { mode = MediaMode.PHOTO; foldRequested = true },
                            onOpenFicha = { showFicha = true },
                            modifier = stageModifier,
                            onOpenAlbum = { showAlbum = true },
                            onOpenGallery = { galleryLauncher.launch(arrayOf("image/*", "video/*")) },
                        ) else Stage(stageModifier = stageModifier, compact = false, showPreview = true)
                    },
                )
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
                        // El historial vive en el cuerpo de la sesión; salir del grabador detiene primero su captura.
                        if (techniqueOpen) mediaCapture?.stopIfRecording()
                        expanded = false
                        techniqueOpen = false
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

/** La banda mide siempre 48 dp: ni el texto ni el enlace desplazan la cámara. */
@Composable
private fun MediaAnalysisLine(
    line: VisualModeAnalysisLine,
    onAction: (() -> Unit)?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val measurer = rememberTextMeasurer()
    val captionStyle = KpknType.caption
    val label = line.actionLabel
    fun annotated(text: String) = buildAnnotatedString {
        val start = if (label != null && onAction != null) text.lastIndexOf(label) else -1
        if (start >= 0 && label != null) {
            append(text.substring(0, start))
            val actionStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = WizardColors.text, textDecoration = TextDecoration.None)
            withStyle(actionStyle) {
                withLink(LinkAnnotation.Clickable(
                    tag = "visual-analysis-${line.action}",
                    styles = TextLinkStyles(actionStyle),
                    linkInteractionListener = { onAction?.invoke() },
                )) { append(label.replace(' ', '\u00A0')) }
            }
        } else append(text)
    }
    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .testTag("media_analysis_line")
            .clearAndSetSemantics {
                contentDescription = line.text
                text = AnnotatedString(layout?.layoutInput?.text?.text ?: line.text)
                getTextLayoutResult { results -> layout?.let { results.add(it) } ?: false }
                if (onAction != null) {
                    role = Role.Button
                    onClick(label = label) { onAction(); true }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val limits = constraints
        val displayed = remember(line, limits, measurer, captionStyle, onAction) {
            val fitted = fitVisualModeAnalysisText(line) { candidate ->
                !measurer.measure(
                    text = annotated(candidate), style = captionStyle,
                    maxLines = 2, overflow = TextOverflow.Clip, constraints = limits,
                ).hasVisualOverflow
            }
            annotated(fitted)
        }
        val actionStart = if (label != null && onAction != null) displayed.text.lastIndexOf(label.replace(' ', '\u00A0')) else -1
        Box(
            modifier = Modifier.fillMaxSize()
            .then(if (actionStart >= 0 && label != null) Modifier.pointerInput(displayed.text, onAction) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val measured = layout ?: return@awaitEachGesture
                    // La palabra conserva su ancho; su objetivo ocupa los 48 dp de alto de la banda.
                    val onLink = (0 until measured.lineCount).any { index ->
                        val start = maxOf(actionStart, measured.getLineStart(index))
                        val end = minOf(actionStart + label.length, measured.getLineEnd(index, visibleEnd = true))
                        if (start < end) {
                            val bounds = measured.getPathForRange(start, end).getBounds()
                            down.position.x in (bounds.left - 6.dp.toPx())..(bounds.right + 6.dp.toPx())
                        } else false
                    }
                    if (onLink) {
                        // Resolver antes del enlace de Text evita que su área propia absorba
                        // el toque de la banda sin activar la acción o la active dos veces.
                        down.consume()
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null) {
                            up.consume()
                            onAction?.invoke()
                        }
                    }
                }
            } else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = displayed,
                modifier = Modifier.fillMaxWidth().testTag("media_analysis_text"),
                style = captionStyle,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout = it },
            )
        }
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
    videoThumbBadges: Map<String, VideoThumbBadge> = emptyMap(),
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(if (compact) 22.dp else 24.dp)
    val shutterOuter = if (compact) 60.dp else 68.dp
    val shutterInner = if (compact) 44.dp else 50.dp
    val thumbSize = if (compact) 36.dp else 44.dp
    val thumbTarget = 48.dp
    val maxThumbs = if (compact) 3 else 4
    BoxWithConstraints(
        modifier = modifier
            .testTag(if (compact) "media_stage_compact" else "media_stage_expanded")
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2622), Color(0xFF121110)),
                    center = Offset.Unspecified,
                ),
            ),
    ) {
        val leftMax = maxWidth - 12.dp - (shutterOuter + 16.dp + 8.dp)
        val leftBottom = 14.dp + (shutterOuter - 48.dp) / 2
        val galleryWidth = when {
            !galleryEnabled -> 0.dp
            compact -> 48.dp
            else -> with(density) { measurer.measure(AnnotatedString("Galería"), KpknType.chip).size.width.toDp() } + 24.dp
        }
        val albumWidth = if (albumEnabled) {
            with(density) { measurer.measure(AnnotatedString("Ver álbum"), KpknType.chip).size.width.toDp() } + 20.dp
        } else 0.dp
        val maxShown = minOf(maxThumbs, thumbs.size)
        val oneRowRoom = leftMax - 12.dp - (if (galleryEnabled) galleryWidth + 6.dp else 0.dp) - (if (albumEnabled) albumWidth + 6.dp else 0.dp)
        val oneRowThumbs = thumbCountThatFits(oneRowRoom, thumbTarget, 6.dp, maxShown)
        val twoRows = compact || oneRowThumbs < minOf(2, maxShown)
        val room = leftMax - (if (compact) 0.dp else 12.dp) - (if (galleryEnabled && !compact) galleryWidth + 6.dp else 0.dp)
        val shown = thumbs.take(if (twoRows) thumbCountThatFits(room, thumbTarget, 6.dp, maxShown) else oneRowThumbs)
        val firstRowHeight = if (shown.isNotEmpty() || (!compact && galleryEnabled)) 48.dp else 0.dp
        val secondRowHeight = if (albumEnabled || (compact && galleryEnabled)) 48.dp else 0.dp
        val groupHeight = (if (twoRows) firstRowHeight + secondRowHeight else 48.dp) + if (compact) 0.dp else 12.dp
        val occupiedBottom = maxOf(shutterOuter + 14.dp, groupHeight + leftBottom - if (compact) 0.dp else 6.dp)
        if (showPreview && cameraReady) {
            MediaCameraSurface(session = session, modifier = Modifier.fillMaxSize())
        } else if (!cameraReady) {
            Box(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = occupiedBottom + 8.dp)
                    .testTag("media_camera_message_area"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Activa la cámara para grabar desde esta tarjeta",
                    modifier = Modifier.testTag("media_camera_message"),
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
        MediaOverlayPill(
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
                    MediaOverlayPill(description = "Galería", onClick = onGallery) {
                        Text("Galería", style = KpknType.chip, color = WizardColors.text, maxLines = 1)
                    }
                }
            }
        }
        val thumbTiles: @Composable (List<WorkoutMedia>) -> Unit = { list ->
            list.forEach { media ->
                val badge = if (media.kind == WorkoutMediaKind.VIDEO) videoThumbBadges[media.id] else null
                val talkBackDesc = if (media.kind == WorkoutMediaKind.VIDEO) {
                    videoThumbContentDescription(badge, media.durationMs)
                } else "Foto de ${media.exerciseName ?: "la serie"}"
                Box(
                    modifier = Modifier
                        .size(thumbTarget)
                        .testTag("media_thumb_${media.id}")
                        .clickable(role = Role.Button, onClick = { onThumb(media) })
                        .semantics { contentDescription = talkBackDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(thumbSize).clearAndSetSemantics {}) {
                        WorkoutMediaThumb(
                            media = media,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            visualModeOverlay = true,
                            hasAnalysisBadge = badge != null,
                        )
                        if (badge != null) {
                            VideoThumbBadgeOverlay(
                                badge = badge,
                                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)
                                    .testTag("media_badge_${media.id}"),
                            )
                        }
                    }
                }
            }
        }
        // Expandido, el grupo lleva su fondo translúcido como en G3 (el contenedor mide 12 dp más que las filas); en la tarjeta, no.
        val groupModifier = if (compact) {
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = leftBottom)
                .testTag("media_thumb_strip")
        } else {
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = leftBottom - 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(6.dp)
                .testTag("media_thumb_strip")
        }
        if (twoRows) {
            Column(modifier = groupModifier) {
                if (galleryEnabled || shown.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!compact) galleryButton()
                        thumbTiles(shown)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (compact) galleryButton()
                    if (albumEnabled) MediaAlbumPill(onClick = onAlbum)
                }
            }
        } else {
            Row(
                modifier = groupModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                thumbTiles(shown)
                galleryButton()
                if (albumEnabled) MediaAlbumPill(onClick = onAlbum)
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

/** Insignia estática: el arco cambia solo cuando llega otro porcentaje, también con movimiento reducido. */
@Composable
private fun VideoThumbBadgeOverlay(badge: VideoThumbBadge, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(16.dp).widthIn(min = 16.dp)
            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
            .border(1.dp, WizardColors.text.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (badge is VideoThumbBadge.Analyzing) {
            Canvas(Modifier.size(12.dp)) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(WizardColors.text.copy(alpha = 0.20f), style = stroke)
                drawArc(
                    WizardColors.text, startAngle = -90f,
                    sweepAngle = 360f * badge.progress.coerceIn(0, 100) / 100f,
                    useCenter = false, style = stroke,
                )
            }
        } else {
            val text = when (badge) {
                is VideoThumbBadge.Reps -> if (badge.approximate) "≈${badge.count}" else "${badge.count}"
                VideoThumbBadge.Inconclusive -> "?"
                VideoThumbBadge.Failed -> "!"
                else -> ""
            }
            Text(
                text,
                style = KpknType.chip.copy(fontSize = 10.sp, lineHeight = 10.sp),
                color = when (badge) {
                    VideoThumbBadge.Failed -> WizardColors.danger
                    VideoThumbBadge.Inconclusive -> WizardColors.textMuted
                    else -> WizardColors.text
                },
                maxLines = 1,
            )
        }
    }
}

/** «Ver álbum» (A3): píldora de tinta de 32 dp con texto oscuro; la zona táctil mide 48 dp. */
@Composable
internal fun MediaAlbumPill(onClick: () -> Unit) {
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
internal fun MediaOverlayPill(
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
internal fun ExpandedMediaOverlay(
    startBounds: Rect,
    title: String,
    subtitle: String,
    onFolded: () -> Unit,
    stage: @Composable (Modifier) -> Unit,
    foldRequested: Boolean = false,
    beforeFold: suspend () -> Unit = {},
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    var folding by remember { mutableStateOf(false) }
    val latestBeforeFold by rememberUpdatedState(beforeFold)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(durationMillis = ExpandMillis, easing = FastOutSlowInEasing))
    }
    DisposableEffectTracker()
    val fold: () -> Unit = {
        if (!folding) {
            folding = true
            scope.launch {
                latestBeforeFold()
                if (reduced) progress.snapTo(0f) else progress.animateTo(0f, tween(durationMillis = ExpandMillis, easing = FastOutSlowInEasing))
                onFolded()
            }
        }
    }
    BackHandler(enabled = true) { fold() }
    LaunchedEffect(foldRequested) { if (foldRequested) fold() }

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
                    Text(title, modifier = Modifier.fillMaxWidth(), style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, modifier = Modifier.fillMaxWidth(), style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
