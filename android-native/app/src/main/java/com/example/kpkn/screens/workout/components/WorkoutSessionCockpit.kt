package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.SessionSavedNote
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutRmCalcContent
import com.example.kpkn.screens.workout.sessionCalorieStatus
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Páginas del cockpit «Sesión» (LS6). El menú de la sesión abre la suya: «Notas» y «Fotos» van directas a su página. */
enum class WorkoutCockpitPage(val label: String) {
    Overview("Resumen"),
    Photos("Fotos"),
    Tools("Utilidades"),
    Notes("Notas"),
}

/** Herramientas de la página «Utilidades». */
private enum class CockpitToolTab(val title: String) {
    Timer("Temporizador"),
    Rm("Cálculo RM"),
    Calories("Calorías"),
}

/**
 * Cockpit «Sesión» (LS6): una hoja KPKN con un control segmentado de cuatro páginas, que abre en [initialPage]. Sustituye al panel que
 * subía desde abajo. Los datos y las llamadas son los de siempre.
 */
@Composable
fun WorkoutSessionCockpit(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    sessionProgressLabel: String,
    liveEnergySummary: SessionEnergySummary,
    sessionNotes: String,
    sessionSavedNotes: List<SessionSavedNote> = emptyList(),
    sessionPhotos: List<String>,
    sessionMedia: List<WorkoutMedia> = emptyList(),
    sessionChecklist: List<SessionChecklistItem>,
    onSessionNotesChange: (String) -> Unit,
    onSaveSessionNote: (String) -> Unit = {},
    onAddSessionPhoto: (Uri) -> Unit,
    onBeginSessionCameraCapture: suspend () -> WorkoutMediaCaptureController.ExternalPhotoCapture? = { null },
    onCompleteSessionCameraCapture: (String, Boolean) -> Unit = { _, _ -> },
    onSessionCameraLaunchFailed: (String) -> Unit = {},
    mediaCaptureError: String? = null,
    onRetryPendingMediaCaptures: () -> Unit = {},
    onRemoveSessionPhoto: (String) -> Unit,
    onRemoveSessionMedia: (String) -> Unit = {},
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    sessionAccentColor: Color,
    bodyWeight: Double? = null,
    modifier: Modifier = Modifier,
    poseOverlayEnabled: Boolean = false,
    /** Página que se abre: la que pide quien abre el cockpit («Notas», «Fotos»…). */
    initialPage: WorkoutCockpitPage = WorkoutCockpitPage.Overview,
    onDismiss: () -> Unit = {},
) {
    KpknSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        stableHeightFraction = 0.86f,
    ) {
        WorkoutSessionCockpitBody(
            exercises = exercises,
            completedSets = completedSets,
            milestones = milestones,
            sessionProgressLabel = sessionProgressLabel,
            liveEnergySummary = liveEnergySummary,
            sessionNotes = sessionNotes,
            sessionSavedNotes = sessionSavedNotes,
            sessionPhotos = sessionPhotos,
            sessionMedia = sessionMedia,
            sessionChecklist = sessionChecklist,
            onSessionNotesChange = onSessionNotesChange,
            onSaveSessionNote = onSaveSessionNote,
            onAddSessionPhoto = onAddSessionPhoto,
            onBeginSessionCameraCapture = onBeginSessionCameraCapture,
            onCompleteSessionCameraCapture = onCompleteSessionCameraCapture,
            onSessionCameraLaunchFailed = onSessionCameraLaunchFailed,
            mediaCaptureError = mediaCaptureError,
            onRetryPendingMediaCaptures = onRetryPendingMediaCaptures,
            onRemoveSessionPhoto = onRemoveSessionPhoto,
            onRemoveSessionMedia = onRemoveSessionMedia,
            onAddChecklistItem = onAddChecklistItem,
            onToggleChecklistItem = onToggleChecklistItem,
            onRemoveChecklistItem = onRemoveChecklistItem,
            sessionAccentColor = sessionAccentColor,
            bodyWeight = bodyWeight,
            poseOverlayEnabled = poseOverlayEnabled,
            initialPage = initialPage,
        )
    }
}

/** Contenido del cockpit sin la hoja: el segmento y la página elegida. Separado para poder montarlo suelto en las pruebas. */
@Composable
internal fun WorkoutSessionCockpitBody(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    sessionProgressLabel: String,
    liveEnergySummary: SessionEnergySummary,
    sessionNotes: String,
    sessionSavedNotes: List<SessionSavedNote>,
    sessionPhotos: List<String>,
    sessionMedia: List<WorkoutMedia>,
    sessionChecklist: List<SessionChecklistItem>,
    onSessionNotesChange: (String) -> Unit,
    onSaveSessionNote: (String) -> Unit,
    onAddSessionPhoto: (Uri) -> Unit,
    onBeginSessionCameraCapture: suspend () -> WorkoutMediaCaptureController.ExternalPhotoCapture?,
    onCompleteSessionCameraCapture: (String, Boolean) -> Unit,
    onSessionCameraLaunchFailed: (String) -> Unit,
    mediaCaptureError: String?,
    onRetryPendingMediaCaptures: () -> Unit,
    onRemoveSessionPhoto: (String) -> Unit,
    onRemoveSessionMedia: (String) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    sessionAccentColor: Color,
    bodyWeight: Double?,
    poseOverlayEnabled: Boolean,
    initialPage: WorkoutCockpitPage,
) {
    val pages = WorkoutCockpitPage.entries
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    val reduced = wizardReducedMotion()
    val slidePx = with(LocalDensity.current) { 12.dp.roundToPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Sesión", style = KpknType.title, color = WizardColors.text)
            Text(text = sessionProgressLabel, style = KpknType.caption, color = WizardColors.textMuted)
        }
        KpknSegmentedControl(
            options = pages.map { it.label },
            selectedIndex = pages.indexOf(page),
            onSelect = { page = pages[it] },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedContent(
            targetState = page,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = {
                if (reduced) {
                    fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 120))
                } else {
                    val offset = if (targetState.ordinal > initialState.ordinal) slidePx else -slidePx
                    (fadeIn(animationSpec = tween(durationMillis = 220)) +
                        slideInHorizontally(animationSpec = tween(durationMillis = 220)) { offset }) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 220))
                }
            },
            label = "cockpitPage",
        ) { current ->
            when (current) {
                WorkoutCockpitPage.Overview -> CockpitOverviewPage(
                    exercises = exercises,
                    completedSets = completedSets,
                    milestones = milestones,
                    sessionAccentColor = sessionAccentColor,
                )
                WorkoutCockpitPage.Photos -> CockpitPhotosPage(
                    sessionPhotos = sessionPhotos,
                    sessionMedia = sessionMedia,
                    onAddPhoto = onAddSessionPhoto,
                    onBeginCameraCapture = onBeginSessionCameraCapture,
                    onCompleteCameraCapture = onCompleteSessionCameraCapture,
                    onCameraLaunchFailed = onSessionCameraLaunchFailed,
                    captureError = mediaCaptureError,
                    onRetryPendingCaptures = onRetryPendingMediaCaptures,
                    onRemovePhoto = onRemoveSessionPhoto,
                    onRemoveMedia = onRemoveSessionMedia,
                    poseOverlayEnabled = poseOverlayEnabled,
                )
                WorkoutCockpitPage.Tools -> CockpitToolsPage(
                    liveEnergySummary = liveEnergySummary,
                    sessionAccentColor = sessionAccentColor,
                    bodyWeight = bodyWeight,
                )
                WorkoutCockpitPage.Notes -> CockpitNotesPage(
                    sessionNotes = sessionNotes,
                    sessionSavedNotes = sessionSavedNotes,
                    sessionChecklist = sessionChecklist,
                    onSessionNotesChange = onSessionNotesChange,
                    onSaveSessionNote = onSaveSessionNote,
                    onAddChecklistItem = onAddChecklistItem,
                    onToggleChecklistItem = onToggleChecklistItem,
                    onRemoveChecklistItem = onRemoveChecklistItem,
                )
            }
        }
    }
}

/** Columna con desplazamiento para cada página del cockpit. */
@Composable
private fun CockpitPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Línea de separación de 1 dp dentro de un módulo. */
@Composable
private fun CockpitDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}

@Composable
private fun CockpitOverviewPage(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    sessionAccentColor: Color,
) {
    val starExercises = remember(exercises) { exercises.filter { it.isStarTarget } }
    val orderedMilestones = remember(milestones) {
        milestones.sortedByDescending { it.createdAtIso }.take(12)
    }

    CockpitPageColumn {
        KpknModule {
            KpknModuleHeader(label = "Hitos de la sesión")
            if (orderedMilestones.isEmpty()) {
                Text(
                    text = "Todavía no hay hitos. Aparecen al batir un récord o alcanzar una meta estrella.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                orderedMilestones.forEachIndexed { index, milestone ->
                    if (index > 0) CockpitDivider()
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(text = milestone.label, style = KpknType.bodyStrong, color = WizardColors.text)
                        Text(text = milestone.exerciseName, style = KpknType.caption, color = WizardColors.textMuted)
                        milestone.detail?.let { detail ->
                            Text(text = detail, style = KpknType.caption, color = WizardColors.textFaint)
                        }
                    }
                }
            }
        }
        if (starExercises.isNotEmpty()) {
            KpknModule {
                KpknModuleHeader(label = "Ejercicios estrella")
                starExercises.forEachIndexed { index, exercise ->
                    val goal = exercise.goal1RM?.takeIf { it > 0 } ?: return@forEachIndexed
                    // All tags of this starred exercise count toward the same 1RM goal.
                    val bestE1rm = completedSets
                        .filterKeys { key ->
                            (key.startsWith("${exercise.id}_") || key == exercise.id) &&
                                !key.contains("_warmup_")
                        }
                        .values
                        .filter { !it.isWarmup && !it.skipped && (it.weight ?: 0.0) > 0 && it.reps > 0 }
                        .maxOfOrNull { calculateHybrid1RM(it.weight ?: 0.0, it.reps) }
                        ?: 0.0
                    val progress = (bestE1rm / goal).toFloat().coerceIn(0f, 1f)
                    if (index > 0) CockpitDivider()
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(text = exercise.name, style = KpknType.bodyStrong, color = WizardColors.text)
                        Text(
                            text = "${bestE1rm.toTrimmedNumberString()} / ${goal.toTrimmedNumberString()} kg e1RM",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        KpknProgressBar(
                            progress = progress,
                            color = sessionAccentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitPhotosPage(
    sessionPhotos: List<String>,
    sessionMedia: List<WorkoutMedia>,
    onAddPhoto: (Uri) -> Unit,
    onBeginCameraCapture: suspend () -> WorkoutMediaCaptureController.ExternalPhotoCapture?,
    onCompleteCameraCapture: (String, Boolean) -> Unit,
    onCameraLaunchFailed: (String) -> Unit,
    captureError: String?,
    onRetryPendingCaptures: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onRemoveMedia: (String) -> Unit,
    poseOverlayEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraCaptureId by rememberSaveable { mutableStateOf<String?>(null) }
    var preparingCamera by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<WorkoutMedia?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAddPhoto(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val captureId = pendingCameraCaptureId
        if (captureId != null) onCompleteCameraCapture(captureId, ok)
        pendingCameraCaptureId = null
    }
    val startCameraCapture = {
        if (!preparingCamera && pendingCameraCaptureId == null) {
            preparingCamera = true
            scope.launch {
                val capture = try {
                    onBeginCameraCapture()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } finally {
                    preparingCamera = false
                }
                if (capture != null) {
                    pendingCameraCaptureId = capture.id
                    try {
                        cameraLauncher.launch(capture.uri)
                    } catch (_: Exception) {
                        pendingCameraCaptureId = null
                        onCameraLaunchFailed(capture.id)
                    }
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCameraCapture()
    }

    CockpitPageColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknSecondaryPill(
                text = "Cámara",
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        startCameraCapture()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !preparingCamera && pendingCameraCaptureId == null,
                modifier = Modifier.weight(1f),
            )
            KpknSecondaryPill(
                text = "Galería",
                onClick = { galleryLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f),
            )
        }
        if (!captureError.isNullOrBlank()) {
            KpknModule(modifier = Modifier.fillMaxWidth(), accent = WizardColors.danger) {
                Text(text = captureError, style = KpknType.body, color = WizardColors.text)
                KpknTextAction(
                    text = "Reintentar",
                    onClick = onRetryPendingCaptures,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
        if (sessionMedia.isEmpty() && sessionPhotos.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.Layers,
                title = "Todavía no hay fotos ni vídeos",
                body = "Añádelos con la cámara o desde la galería.",
            )
        } else if (sessionMedia.isNotEmpty()) {
            sessionMedia.forEach { item ->
                Box {
                    WorkoutMediaThumb(
                        media = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        onClick = { preview = item },
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.Close,
                        contentDescription = "Quitar foto",
                        onClick = { onRemoveMedia(item.id) },
                        modifier = Modifier.align(Alignment.TopEnd),
                        filled = true,
                    )
                }
            }
        } else {
            sessionPhotos.forEach { path ->
                Box {
                    LocalMediaImage(
                        source = rememberLocalMediaImageSource(File(path)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(KpknShape.module),
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.Close,
                        contentDescription = "Quitar foto",
                        onClick = { onRemovePhoto(path) },
                        modifier = Modifier.align(Alignment.TopEnd),
                        filled = true,
                    )
                }
            }
        }
    }
    preview?.let { item ->
        WorkoutMediaPreviewDialog(
            media = item,
            onDismiss = { preview = null },
            poseOverlayEnabled = poseOverlayEnabled,
        )
    }
}

@Composable
private fun CockpitToolsPage(
    liveEnergySummary: SessionEnergySummary,
    sessionAccentColor: Color,
    bodyWeight: Double?,
) {
    var selectedTool by remember { mutableStateOf(CockpitToolTab.Timer) }
    var timerRunning by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(timerRunning) {
        while (timerRunning) {
            delay(250)
            elapsedMs += 250
        }
    }

    val totalSeconds = (elapsedMs / 1000L).toInt()
    val mm = totalSeconds / 60
    val ss = totalSeconds % 60

    CockpitPageColumn {
        KpknSegmentedControl(
            options = CockpitToolTab.entries.map { it.title },
            selectedIndex = selectedTool.ordinal,
            onSelect = { selectedTool = CockpitToolTab.entries[it] },
            modifier = Modifier.fillMaxWidth(),
            size = KpknSegmentedSize.Compact,
        )
        when (selectedTool) {
            CockpitToolTab.Timer -> KpknModule(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "%02d:%02d".format(mm, ss),
                    style = KpknType.metricXL,
                    color = WizardColors.text,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpknIconButton(
                        icon = if (timerRunning) KpknLineIcons.Pause else KpknLineIcons.Play,
                        contentDescription = if (timerRunning) "Pausar" else "Iniciar",
                        onClick = { timerRunning = !timerRunning },
                        filled = true,
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.Repeat,
                        contentDescription = "Reiniciar",
                        onClick = {
                            timerRunning = false
                            elapsedMs = 0L
                        },
                    )
                }
                Text(
                    text = "Independiente del descanso entre series",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            CockpitToolTab.Rm -> WorkoutRmCalcContent(
                bodyWeight = bodyWeight,
                sessionAccentColor = sessionAccentColor,
                softInputs = true,
            )
            CockpitToolTab.Calories -> {
                val completedCount = liveEnergySummary.exerciseContributions.sumOf { it.completedSets }
                val status = sessionCalorieStatus(liveEnergySummary, completedCount, bodyWeight)
                val kcal = status.kcal
                KpknModule {
                    KpknModuleHeader(label = "Calorías quemadas")
                    if (kcal != null) {
                        KpknMetric(
                            value = kcal.toString(),
                            unit = "kcal",
                            style = KpknType.metricL,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text(
                            text = status.hint.orEmpty(),
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitNotesPage(
    sessionNotes: String,
    sessionSavedNotes: List<SessionSavedNote>,
    sessionChecklist: List<SessionChecklistItem>,
    onSessionNotesChange: (String) -> Unit,
    onSaveSessionNote: (String) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
) {
    val lastSaved = sessionSavedNotes.lastOrNull()?.text
    var draftItem by remember { mutableStateOf("") }
    // La acción de guardar solo existe cuando hay texto que guardar: nada de controles apagados.
    val saveAction: (@Composable () -> Unit)? = if (sessionNotes.isNotBlank()) {
        { KpknTextAction(text = "Guardar nota", onClick = { onSaveSessionNote(sessionNotes) }) }
    } else {
        null
    }

    CockpitPageColumn {
        KpknModule {
            KpknModuleHeader(label = "Notas de sesión", action = saveAction)
            KpknTextField(
                value = sessionNotes,
                onValueChange = onSessionNotesChange,
                placeholder = "Anotaciones generales de la sesión…",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )
            lastSaved?.let { saved ->
                Text(
                    text = saved,
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        KpknModule {
            KpknModuleHeader(label = "Tareas y objetivos")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpknTextField(
                    value = draftItem,
                    onValueChange = { draftItem = it },
                    placeholder = "Nueva tarea",
                    modifier = Modifier.weight(1f),
                )
                if (draftItem.isNotBlank()) {
                    KpknTextAction(
                        text = "Añadir",
                        onClick = {
                            onAddChecklistItem(draftItem)
                            draftItem = ""
                        },
                    )
                }
            }
            if (sessionChecklist.isEmpty()) {
                Text(
                    text = "Aún no hay tareas. Añade objetivos para esta sesión.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                sessionChecklist.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KpknCheckRow(
                            checked = item.done,
                            title = item.text,
                            onClick = { onToggleChecklistItem(item.id) },
                            modifier = Modifier.weight(1f),
                        )
                        KpknIconButton(
                            icon = KpknLineIcons.Close,
                            contentDescription = "Eliminar",
                            onClick = { onRemoveChecklistItem(item.id) },
                        )
                    }
                }
            }
        }
    }
}
