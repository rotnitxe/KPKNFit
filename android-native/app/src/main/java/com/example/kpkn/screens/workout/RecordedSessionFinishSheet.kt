package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CalibrationDecisionRecord
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.domain.training.insights.RpeConversion
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlin.math.max

/**
 * Cierre de la sesión (Pulido v1 · punto 18): UNA hoja sin scroll con volumen, cuatro anillos, músculos, mejoras, historia y las
 * tres acciones rápidas; Anotaciones es un panel que entra desde la derecha. El estado vive aquí; el dibujo, en [FinishSheetContent].
 * `calibrationDecisions` se sigue aceptando (la decisión se guarda igual) pero el lienzo aprobado no tiene sitio para su resumen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordedSessionFinishSheet(
    name: String,
    completedExercises: List<CompletedExercise>,
    durationMinutes: Int,
    initialNotes: String,
    saving: Boolean,
    discomforts: List<String> = emptyList(),
    @Suppress("UNUSED_PARAMETER") calibrationDecisions: List<CalibrationDecisionRecord> = emptyList(),
    presentation: FinishPresentationContext = FinishPresentationContext(),
    checklist: List<SessionChecklistItem> = emptyList(),
    onAddTask: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    onRemoveTask: (String) -> Unit = {},
    onSummaryReady: (WorkoutSessionSummary) -> Unit = {},
    onShareAfterSave: (WorkoutStoryData, WorkoutStoryOptions) -> Unit = { _, _ -> },
    onNotes: (String) -> Unit,
    onAlbum: () -> Unit,
    onConfirm: (String, Double?) -> Unit,
    onDismiss: () -> Unit,
    /** Series planificadas de la sesión (para el anillo «Series»); null = las hechas. */
    plannedSets: Int? = null,
    /** Tiempo objetivo de la sesión en minutos (para el anillo «Duración»); null = vuelta de reloj de 60 min. */
    targetMinutes: Int? = null,
    /** «No se pudo guardar la sesión…» en rojo suave sobre el botón; la hoja se queda y el botón vuelve a estar activo. */
    errorMessage: String? = null,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var effort by rememberSaveable { mutableStateOf<Double?>(null) }
    var shareAfterSave by rememberSaveable { mutableStateOf(false) }
    var showStory by rememberSaveable { mutableStateOf(false) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var storyOptions by rememberSaveable(stateSaver = storyOptionsSaver) { mutableStateOf(WorkoutStoryOptions()) }
    // El mensaje de error se queda mientras la hoja siga abierta (el aviso del controlador se consume al mostrarse).
    var shownError by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(errorMessage) { if (errorMessage != null) shownError = errorMessage }
    LaunchedEffect(saving) { if (saving) shownError = null }

    val lifts = remember(completedExercises, presentation) { buildFinishLiftProgress(completedExercises, presentation.history) }
    val sessionRpe = RpeConversion.sessionRpe(effort, completedExercises)
    val story = remember(completedExercises, presentation, durationMinutes, effort) {
        WorkoutStoryData(
            name, presentation, durationMinutes, sessionTonnage(completedExercises), logicalSetCountFromCompleted(completedExercises),
            finishAverageRir(completedExercises), "Sin registro", lifts, sessionRpe = RpeConversion.sessionRpe(effort, completedExercises),
        )
    }
    LaunchedEffect(name, completedExercises, durationMinutes) {
        onSummaryReady(WorkoutSessionSummary(durationMinutes, logicalSetCountFromCompleted(completedExercises), discomforts))
    }
    val model = remember(name, completedExercises, durationMinutes, sessionRpe, presentation, lifts, plannedSets, targetMinutes) {
        buildFinishModel(name, completedExercises, durationMinutes, sessionRpe, presentation, lifts, plannedSets, targetMinutes)
    }
    val blocked = finishEmptySessionGuidance(completedExercises)

    val config = LocalConfiguration.current
    val tier = finishTierFor(config.screenHeightDp, config.fontScale, heavy = model.record != null || model.cardio != null || shownError != null)
    val dims = FinishDims(
        tier = tier, big = config.fontScale > 1.15f, dense = model.record != null || model.cardio != null, hasError = shownError != null,
        roomy = config.screenHeightDp * 0.94f / config.fontScale.coerceAtLeast(0.5f) >= 840f,
    )
    val context = LocalContext.current
    // Miniatura de la historia (la propia imagen a baja resolución), en segundo plano.
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, story, model.empty) {
        if (model.empty) return@produceState
        value = try {
            val full = renderWorkoutStory(context.applicationContext, story, WorkoutStoryOptions())
            android.graphics.Bitmap.createScaledBitmap(full, 114, 203, true).also { if (it !== full) full.recycle() }.asImageBitmap()
        } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
    }
    val ridges = remember(completedExercises) { finishRidges(completedExercises) }
    val ridgeRecord = remember(completedExercises, model.record) {
        model.record?.let { r -> completedExercises.filter { it.cardioDetails == null }.indexOfFirst { it.exerciseName == r.exerciseName } } ?: -1
    }
    val state = FinishSheetState(
        model = model, dims = dims, saving = saving, errorMessage = shownError, blockedMessage = blocked, notes = notes,
        photoCount = presentation.photos.size, shareAfterSave = shareAfterSave, panelOpen = panelOpen, checklist = checklist,
        effortSelected = effort, storyThumb = thumb, ridges = ridges, ridgeRecordIndex = ridgeRecord,
    )
    val actions = FinishSheetActions(
        onShare = { shareAfterSave = it }, onPrepareStory = { showStory = true }, onOpenNotes = { panelOpen = true },
        onClosePanel = { panelOpen = false }, onPhotos = onAlbum,
        onSave = {
            if (shareAfterSave) onShareAfterSave(story, storyOptions)
            onConfirm(notes, effort)
        },
        onBack = { if (!saving) onDismiss() },
        onNotes = { notes = it; onNotes(it) }, onEffort = { effort = it },
        onAddTask = onAddTask, onToggleTask = onToggleTask, onRemoveTask = onRemoveTask,
    )

    // La hoja del lienzo: 94 % del alto (a lo que mida el contenido si no hay series), fondo #1B1B1D, radio 28 y asa de 40 × 4 a 8 dp del borde.
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1B1B1D),
        scrimColor = Color(0xA8000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth().then(if (model.empty) Modifier else Modifier.fillMaxHeight(0.94f))) {
            FinishSheetContent(state, actions)
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF)))
        }
    }
    if (showStory) {
        WorkoutStorySheet(
            data = story,
            hazeState = remember { HazeState() },
            options = storyOptions,
            onOptionsChange = { storyOptions = it },
            shareAfterSave = shareAfterSave,
            onShareAfterSaveChange = { shareAfterSave = it },
            onDismiss = { showStory = false },
        )
    }
}

/**
 * Las líneas del encabezado: una por ejercicio de fuerza (hasta cinco) con un pico por serie; la altura de cada pico es el peso de la
 * serie respecto a la más pesada de la sesión, entre 0,3 y 0,95 como en el lienzo.
 */
internal fun finishRidges(completed: List<CompletedExercise>): List<List<Float>> {
    val strength = completed.filter { it.cardioDetails == null }.map { ex -> ex.sets.filter { !it.skipped && !it.isWarmup } }.filter { it.isNotEmpty() }
    val heaviest = max(1.0, strength.maxOfOrNull { sets -> sets.maxOf { it.weight } } ?: 1.0)
    return strength.take(5).map { sets -> sets.take(6).map { (0.3f + 0.65f * (it.weight / heaviest).toFloat()).coerceIn(0.3f, 0.95f) } }
}
