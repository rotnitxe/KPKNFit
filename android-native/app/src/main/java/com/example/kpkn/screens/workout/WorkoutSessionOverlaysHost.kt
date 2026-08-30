package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
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
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.screens.workout.components.WorkoutReadinessSheet
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun WorkoutSessionOverlaysHost(
    viewModel: WorkoutViewModel,
    augeViewModel: AugeViewModel,
    uiState: WorkoutUiState,
    session: Session,
    visibleExercises: List<Exercise>,
    showReadinessSheet: Boolean,
    readinessHaze: HazeState,
    bottomHazeState: HazeState,
    gender: Gender?,
    sessionMuscleStartingBatteries: Map<String, Int>,
    readinessNeuralStart: Int,
    readinessMuscularStart: Int,
    readinessSpinalStart: Int,
    todayWellbeing: DailyWellbeingLog?,
    onReadinessDismissed: () -> Unit,
    perMuscle: Map<String, com.example.kpkn.data.models.MuscleRecoveryStatus> = emptyMap(),
    voiceSessionEnabled: Boolean = false,
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
    onVoiceToggle: () -> Unit = {},
    onVoiceCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit = {},
    showExitDialog: Boolean,
    onShowExitDialogChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    WorkoutReadinessSheet(
        showReadinessSheet = showReadinessSheet,
        gender = gender,
        sessionMuscleStartingBatteries = sessionMuscleStartingBatteries,
        readinessNeuralStart = readinessNeuralStart,
        readinessMuscularStart = readinessMuscularStart,
        readinessSpinalStart = readinessSpinalStart,
        hazeState = readinessHaze,
        onSave = { neural, muscular, spinal, perMuscle, discomforts,
            manualNeural, manualSpinal, manualMuscular, manualMuscleBatteries ->
            val log = DailyWellbeingLog(
                id = todayWellbeing?.id ?: UUID.randomUUID().toString(),
                date = LocalDate.now().toString(),
                sleepQuality = todayWellbeing?.sleepQuality ?: 3,
                stressLevel = todayWellbeing?.stressLevel ?: 3,
                doms = todayWellbeing?.doms ?: 1,
                motivation = todayWellbeing?.motivation ?: 3,
                sleepHours = todayWellbeing?.sleepHours ?: 7.5,
                moodState = todayWellbeing?.moodState,
                workIntensity = todayWellbeing?.workIntensity,
                studyIntensity = todayWellbeing?.studyIntensity,
                manualMuscularBattery = manualMuscular ?: todayWellbeing?.manualMuscularBattery,
                manualNeuralBattery = manualNeural ?: todayWellbeing?.manualNeuralBattery,
                manualSpinalBattery = manualSpinal ?: todayWellbeing?.manualSpinalBattery,
                manualMuscleBatteries = if (manualMuscleBatteries.isNotEmpty()) {
                    remapMuscleIntMapToPillars(
                        (todayWellbeing?.manualMuscleBatteries.orEmpty()) + manualMuscleBatteries,
                    )
                } else {
                    remapMuscleIntMapToPillars(todayWellbeing?.manualMuscleBatteries.orEmpty())
                },
                notes = todayWellbeing?.notes,
                preWorkoutDiscomforts = discomforts,
            )
            augeViewModel.saveWellbeing(log)
            viewModel.saveReadinessAdjustments(
                neural = neural,
                muscular = muscular,
                spinal = spinal,
                perMuscle = remapMuscleIntMapToPillars(perMuscle),
                sleepQuality = todayWellbeing?.sleepQuality,
            )
            onReadinessDismissed()
        },
        patternReadiness = uiState.patternReadiness,
        exerciseReadinessMap = uiState.exerciseReadinessMap,
        sessionExercises = session.exercises,
        perMuscle = perMuscle,
        initialDiscomforts = todayWellbeing?.preWorkoutDiscomforts ?: emptyList(),
        voiceSessionEnabled = voiceSessionEnabled,
        voiceCaptureMode = voiceCaptureMode,
        onVoiceToggle = onVoiceToggle,
        onVoiceCaptureModeChange = onVoiceCaptureModeChange,
    )

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
        val historyDbId = uiState.historySheetExerciseDbId!!
        val historyTag = visibleExercises
            .firstOrNull { (it.exerciseDbId ?: it.exerciseId) == historyDbId }
            ?.let { uiState.exerciseTags[it.id] }
        val history = remember(historyDbId, historyTag) {
            viewModel.getExerciseHistory(historyDbId, preferredTag = historyTag)
        }
        WorkoutDrawer(title = "Historial", onDismiss = { viewModel.hideHistorySheet() }, hazeState = bottomHazeState) {
            if (historyTag != null) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        "Mostrando primero: $historyTag",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
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
                        "Tu entrenamiento en curso se perderá si abandonas sin guardar.",
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
                                viewModel.stopRestTimer()
                                onBack()
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
                                viewModel.stopRestTimer()
                                com.example.kpkn.data.repository.ProgramRepository.getInstance().clearOngoingWorkout()
                                onBack()
                                onShowExitDialogChange(false)
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
