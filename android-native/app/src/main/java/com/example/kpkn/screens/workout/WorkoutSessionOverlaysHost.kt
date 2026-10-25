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
) {
    // Las molestias que quedaron presentes la sesión anterior, con el día en que se anotaron («Rodilla anterior · lunes»).
    TrainingMaxPreparation(viewModel.missingTrainingMaxes(session), enabled = !showReadinessSheet,
        onConfirm = viewModel::confirmTrainingMax)
    if (showReadinessSheet) {
        val previous = remember(session.id, uiState.startTimeMs) {
            viewModel.recentWorkoutLogs(90 * 24, uiState.startTimeMs).filter { it.sessionId == session.id }
                .filter { com.example.kpkn.domain.time.TrainingDates.logDateMs(it) < uiState.startTimeMs }
                .take(6)
        }
        PreSessionPreparationDialog(
            notes = uiState.sessionNotes,
            previous = previous.map { log ->
                val duration = log.actualDurationSeconds?.let { "$it s" } ?: "${log.durationMinutes} min (precisión original)"
                "${log.date.take(10)} · $duration · ${log.completedExercises.size} ejercicios"
            },
            discomforts = uiState.previousSessionDiscomforts.map { com.example.kpkn.data.models.discomfortLabel(it) },
            onNotes = viewModel::setSessionNotes,
            recordedSportId = uiState.recordedSportId,
            onSport = viewModel::setRecordedSport,
            onDismiss = onReadinessDismissed,
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
        WorkoutDrawer(title = "Historial", onDismiss = { viewModel.hideHistorySheet() }, hazeState = bottomHazeState) {
            if (historyTag != null) {
                // Chip informativo: muestra qué etiqueta sale primero; tocarlo no hace nada.
                KpknChip(
                    text = "Mostrando primero: $historyTag",
                    selected = true,
                    onClick = {},
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            ExerciseHistoryContent(history = history, activeTag = historyTag)
        }
    }

    if (showExitDialog) {
        KpknGlassDialog(
            onDismissRequest = { onShowExitDialogChange(false) },
            shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
        ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "¿Qué deseas hacer?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Puedes pausar y continuar más tarde, terminar hasta acá, o abandonar sin guardar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onShowExitDialogChange(false) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text("Continuar entrenando")
                        }
                        Button(
                            onClick = {
                                viewModel.finishUpToCurrentPoint()
                                onShowExitDialogChange(false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Text("Terminar hasta acá")
                        }
                        Button(
                            onClick = {
                                val exitRequestId = RepairBenchmarkTrace.workoutExitRequested("pause_exit")
                                viewModel.stopRestTimer()
                                onBack()
                                RepairBenchmarkTrace.workoutExitNavigationCommitted(exitRequestId)
                                onShowExitDialogChange(false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Text("Pausar y salir")
                        }
                        Button(
                            onClick = {
                                onShowExitDialogChange(false)
                                viewModel.cancelWorkout()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("Abandonar sin guardar")
                        }
                    }
                }
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
        KpknGlassDialog(
            onDismissRequest = { dismissedPersistentDiscomfortExercise = currentExerciseForAlert.id },
            shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Aviso preventivo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "En este ejercicio reportaste $discomfortLabel. " +
                        "Preocúpate por hacer una correcta técnica y manejar cargas adecuadas para ti. " +
                        "Si la molestia persiste en el tiempo, te recomendamos que cambies de ejercicio y " +
                        "que te asesores con un kinesiólogo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { dismissedPersistentDiscomfortExercise = currentExerciseForAlert.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Entendido, continuar")
                }
            }
        }
    }
}
