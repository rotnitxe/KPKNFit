package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.kpknWindowGlass
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
                    (todayWellbeing?.manualMuscleBatteries.orEmpty()) + manualMuscleBatteries
                } else {
                    todayWellbeing?.manualMuscleBatteries.orEmpty()
                },
                notes = todayWellbeing?.notes,
                preWorkoutDiscomforts = discomforts,
            )
            augeViewModel.saveWellbeing(log)
            viewModel.saveReadinessAdjustments(
                neural = neural,
                muscular = muscular,
                spinal = spinal,
                perMuscle = perMuscle,
                sleepQuality = todayWellbeing?.sleepQuality,
            )
            onReadinessDismissed()
        },
        patternReadiness = uiState.patternReadiness,
        exerciseReadinessMap = uiState.exerciseReadinessMap,
        sessionExercises = session.exercises,
        onDismissWithoutVerify = onReadinessDismissed,
        initialDiscomforts = todayWellbeing?.preWorkoutDiscomforts ?: emptyList(),
    )

    val mobilityExercisesForSession = remember(uiState.previousSessionDiscomforts) {
        if (uiState.previousSessionDiscomforts.isNotEmpty()) {
            MobilityExerciseCatalog.getMobilityForDiscomforts(uiState.previousSessionDiscomforts)
        } else {
            emptyList()
        }
    }
    var showMobilityBanner by remember(uiState.previousSessionDiscomforts) {
        mutableStateOf(uiState.previousSessionDiscomforts.isNotEmpty())
    }
    var showMobilityPicker by remember { mutableStateOf(false) }

    if (showMobilityBanner && mobilityExercisesForSession.isNotEmpty()) {
        val discomfortLabels = uiState.previousSessionDiscomforts.mapNotNull { id ->
            DISCOMFORT_CATALOG.find { it.id == id }?.label
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A2744),
            border = BorderStroke(1.dp, Color(0xFF448AFF).copy(alpha = 0.3f)),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Healing, null, tint = Color(0xFF448AFF))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Molestias detectadas: ${discomfortLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "¿Agregar ejercicios de movilidad para estas zonas?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showMobilityBanner = false }) {
                        Text("Omitir")
                    }
                    Button(onClick = { showMobilityPicker = true }) {
                        Text("Agregar movilidad (${mobilityExercisesForSession.size} ejercicios)")
                    }
                }
            }
        }
    }

    if (showMobilityPicker) {
        var mobilitySearchQuery by remember { mutableStateOf("") }
        val filteredMobility = remember(mobilitySearchQuery) {
            if (mobilitySearchQuery.isBlank()) mobilityExercisesForSession
            else MobilityExerciseCatalog.searchMobilityByName(mobilitySearchQuery)
        }
        val mobilityDialogShape = RoundedCornerShape(20.dp)
        Dialog(onDismissRequest = { showMobilityPicker = false }) {
            Surface(
                modifier = Modifier.kpknWindowGlass(mobilityDialogShape),
                shape = mobilityDialogShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Agregar movilidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    OutlinedTextField(
                        value = mobilitySearchQuery,
                        onValueChange = { mobilitySearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Buscar ejercicio") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF555555),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2A2A2A),
                            unfocusedContainerColor = Color(0xFF2A2A2A),
                        ),
                    )
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        filteredMobility.forEach { mob ->
                            Surface(
                                onClick = {
                                    viewModel.addMobilityExerciseToSession(mob.name, mob.durationSeconds)
                                    showMobilityPicker = false
                                    showMobilityBanner = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF333333),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(mob.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(mob.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF448AFF).copy(alpha = 0.15f)) {
                                        Text(
                                            "${mob.durationSeconds}s",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF448AFF),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showMobilityPicker = false; showMobilityBanner = false },
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancelar") }
                    }
                }
            }
        }
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
        val exitDialogShape = RoundedCornerShape(KpknGlass.DialogCornerRadius)
        Dialog(onDismissRequest = { onShowExitDialogChange(false) }) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .wrapContentHeight()
                    .kpknWindowGlass(exitDialogShape),
                shape = exitDialogShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
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
    }
}
