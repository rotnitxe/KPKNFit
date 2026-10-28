package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.design.KpknChip
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun WorkoutSessionOverlaysHost(
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    session: Session,
    visibleExercises: List<Exercise>,
    showReadinessSheet: Boolean,
    readinessHaze: HazeState,
    bottomHazeState: HazeState,
    gender: Gender?,
    onReadinessDismissed: () -> Unit,
    voiceSessionEnabled: Boolean = false,
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
    onVoiceToggle: () -> Unit = {},
    onVoiceCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit = {},
    showExitDialog: Boolean,
    onShowExitDialogChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    /** «Usar» del historial: lleva esa carga al borrador de la serie activa. */
    onUseHistoryWeight: (Double) -> Unit = {},
    /** «Ver opciones del ejercicio» de la alerta de molestia. */
    onOpenExerciseOptions: (String) -> Unit = {},
) {
    // Las molestias que quedaron presentes la sesión anterior, con el día en que se anotaron («Rodilla anterior · lunes»).
    TrainingMaxPreparation(viewModel.missingTrainingMaxes(session), enabled = !showReadinessSheet,
        onConfirm = viewModel::confirmTrainingMax)
    var readinessSaving by remember { mutableStateOf(false) }
    var readinessError by remember { mutableStateOf<String?>(null) }
    var showContext by remember { mutableStateOf(false) }
    LaunchedEffect(session.id) { viewModel.loadReadiness() }
    if (showReadinessSheet) {
        WorkoutReadinessSheet(
            session = session,
            weekLabel = null,
            initial = uiState.todayWellbeing,
            previousDiscomforts = uiState.previousSessionDiscomforts,
            hazeState = readinessHaze,
            onDismiss = onReadinessDismissed,
            saving = readinessSaving,
            error = readinessError,
            voiceEnabled = voiceSessionEnabled,
            captureMode = voiceCaptureMode,
            onVoiceMode = { mode ->
                onVoiceCaptureModeChange(mode)
                if (!voiceSessionEnabled) viewModel.enableVoice(mode)
            },
            onContext = { showContext = true },
            onConfirm = { wellbeing ->
                readinessSaving = true
                readinessError = null
                viewModel.recordReadiness(wellbeing,
                    onSaved = { readinessSaving = false; onReadinessDismissed() },
                    onError = { readinessSaving = false; readinessError = "No se pudo guardar. Reintenta." })
            },
        )
    }

    if (showReadinessSheet && showContext) {
        PreSessionPreparationDialog(
            notes = uiState.sessionNotes,
            previous = viewModel.recentWorkoutLogs(90 * 24, uiState.startTimeMs)
                .filter { it.sessionId == session.id }.take(6)
                .map { "${it.date.take(10)} · ${it.durationMinutes} min" },
            discomforts = uiState.previousSessionDiscomforts.map { com.example.kpkn.data.models.discomfortLabel(it) },
            onNotes = viewModel::setSessionNotes,
            recordedSportId = uiState.recordedSportId,
            onSport = viewModel::setRecordedSport,
            onDismiss = { showContext = false },
        )
    }

    if (uiState.showVolumeAdvanceModal && uiState.pendingVolumeAdvances.isNotEmpty()) {
        BackHandler(enabled = true) { /* El adelanto de volumen requiere acción explícita. */ }
        VolumeAdvanceModal(
            advances = uiState.pendingVolumeAdvances,
            onAccept = { viewModel.acceptVolumeAdvance() },
            onDismiss = { viewModel.dismissVolumeAdvance() },
        )
    }

    LaunchedEffect(uiState.showPostExerciseSheet, uiState.postExerciseTargetIdx, visibleExercises.size) {
        if (uiState.showPostExerciseSheet) {
            viewModel.recoverFromOrphanPostExerciseSheet()
        }
    }

    if (uiState.showHistorySheet && uiState.historySheetExerciseDbId != null) {
        val historyDbId = historySheetDbId(uiState.showHistorySheet, uiState.historySheetExerciseDbId) ?: return
        val historyExercise = visibleExercises.firstOrNull { exercise ->
            historyDbId in identityKeysForExercise(exercise) ||
                exercise.exerciseDbId == historyDbId ||
                exercise.exerciseId == historyDbId ||
                exercise.canonicalExerciseId == historyDbId
        }
        val historyTag = historyExercise?.let { uiState.exerciseTags[it.id] }
        val history = remember(historyDbId, historyTag, historyExercise?.id) {
            if (historyExercise != null) {
                viewModel.getExerciseHistory(historyExercise, preferredTag = historyTag)
            } else {
                viewModel.getExerciseHistory(historyDbId, preferredTag = historyTag)
            }
        }
        // Prototipo 02 (`history`): hoja «Historial del ejercicio» con la gráfica, una línea por sesión y «Usar».
        LiveExerciseHistorySheet(
            exerciseName = historyExercise?.let(::displayWorkoutExerciseName) ?: "Ejercicio",
            activeTag = historyTag,
            history = history,
            onUseWeight = onUseHistoryWeight,
            onDismiss = { viewModel.hideHistorySheet() },
        )
    }

    if (showExitDialog) {
        // Refinamiento 02: la salida es una hoja del prototipo. Seguir entrenando es la acción principal; terminar, pausar y
        // abandonar son filas, la última en rojo.
        com.example.kpkn.screens.workout.components.LiveSheet(
            title = "Salir de la sesión",
            onDismiss = { onShowExitDialogChange(false) },
            tag = "live-exit-sheet",
            footer = { close -> com.example.kpkn.screens.workout.components.LiveSheetPrimary("Continuar entrenando", close) },
        ) {
            com.example.kpkn.screens.workout.components.LiveSheetSubtitle(
                "Puedes pausar y continuar más tarde, terminar hasta acá o abandonar sin guardar.")
            com.example.kpkn.screens.workout.components.LiveSheetMenuRow("Terminar hasta acá", {
                viewModel.finishUpToCurrentPoint()
                onShowExitDialogChange(false)
            }, icon = com.example.kpkn.ui.components.icons.KpknLineIcons.Flag, small = "Guarda lo registrado")
            com.example.kpkn.screens.workout.components.LiveSheetMenuRow("Pausar y salir", {
                val exitRequestId = RepairBenchmarkTrace.workoutExitRequested("pause_exit")
                viewModel.stopRestTimer()
                onBack()
                RepairBenchmarkTrace.workoutExitNavigationCommitted(exitRequestId)
                onShowExitDialogChange(false)
            }, icon = com.example.kpkn.ui.components.icons.KpknLineIcons.Pause, small = "Sigue en curso")
            com.example.kpkn.screens.workout.components.LiveSheetMenuRow("Abandonar sin guardar", {
                onShowExitDialogChange(false)
                viewModel.cancelWorkout()
            }, icon = com.example.kpkn.ui.components.icons.KpknLineIcons.Trash, danger = true)
        }
    }

    // ─── Overlay preventivo: molestia persistente (≥3 sesiones en el patrón) ───
    val currentExerciseForAlert = remember(uiState.currentExerciseIdx, visibleExercises) {
        visibleExercises.getOrNull(uiState.currentExerciseIdx)
    }
    val persistentHit = remember(currentExerciseForAlert?.id) {
        currentExerciseForAlert?.let { viewModel.persistentDiscomfortForExercise(it) }
    }
    val previousDiscomfortIds = uiState.previousSessionDiscomforts.toSet()
    var dismissedPersistentDiscomfortExercise by remember { mutableStateOf<String?>(null) }
    val showPersistentDiscomfortAlert = persistentHit != null &&
        persistentHit.id !in previousDiscomfortIds &&
        dismissedPersistentDiscomfortExercise != currentExerciseForAlert?.id &&
        !showReadinessSheet &&
        !uiState.showPostExerciseSheet &&
        !uiState.showFinishSheet

    if (showPersistentDiscomfortAlert && currentExerciseForAlert != null) {
        val discomfortLabel = persistentHit?.label.orEmpty()
        com.example.kpkn.screens.workout.components.LiveAlert(
            title = "Molestia reportada antes",
            text = "En el historial de este ejercicio hay una molestia de ${discomfortLabel.replaceFirstChar { it.lowercase() }}. " +
                "Revisa cómo te encuentras hoy.",
            primaryLabel = "Entendido",
            onPrimary = { dismissedPersistentDiscomfortExercise = currentExerciseForAlert.id },
            secondaryLabel = "Ver opciones del ejercicio",
            onSecondary = {
                dismissedPersistentDiscomfortExercise = currentExerciseForAlert.id
                onOpenExerciseOptions(currentExerciseForAlert.id)
            },
            onDismiss = { dismissedPersistentDiscomfortExercise = currentExerciseForAlert.id },
            tag = "live-discomfort-alert",
        )
    }
}
