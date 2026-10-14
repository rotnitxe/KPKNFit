package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.screens.workout.design.WorkoutExerciseHeader
import com.example.kpkn.screens.workout.design.WorkoutExerciseMenu
import com.example.kpkn.screens.workout.design.WorkoutProgressBar
import com.example.kpkn.screens.workout.design.WorkoutSessionMenu
import com.example.kpkn.screens.workout.design.WorkoutTopRow

/**
 * Marco de la sesión en vivo (LS2a, §2 puntos 1 a 3): la fila superior con la hora y «Más opciones», la barra de progreso y la cabecera
 * del ejercicio, con sus dos menús. Sustituye a `WorkoutHeaderBar`. Solo guarda lo de la propia pantalla (qué menú está abierto y la
 * petición de abrir el ajuste de tiempo); los datos y las acciones llegan de quien lo monta.
 *
 * «Ultra rápido» abre el ajuste de tiempo, donde vive el modo ultrarrápido, como hacía el chip de la cabecera vieja.
 */
@Composable
internal fun WorkoutLiveFrameHeader(
    eyebrow: String,
    exerciseName: String,
    exerciseChips: List<String>,
    exerciseTagNames: List<String>,
    targetLine: String?,
    lastLine: String?,
    protocolLabel: String?,
    exerciseIndex: Int,
    exerciseCount: Int,
    doneFlags: List<Boolean>,
    onOpenRoute: () -> Unit,
    startTimeMs: Long,
    isComplete: Boolean,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    voiceSessionEnabled: Boolean,
    onToggleVoice: (() -> Unit)?,
    onOpenTags: () -> Unit,
    onOpenCockpit: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onFinishSession: () -> Unit,
    onMinimize: () -> Unit,
    onHistory: () -> Unit,
    onSuperset: (() -> Unit)?,
    onAdapt: (() -> Unit)?,
    onChangeExercise: (() -> Unit)?,
    onSetAbsoluteTimeLimit: (totalMinutes: Int, persistToSession: Boolean) -> Unit = { _, _ -> },
    onClearTimeLimit: (persistToSession: Boolean) -> Unit = {},
    pacingAlertMode: PacingAlertMode = PacingAlertMode.FINAL,
    onPacingAlertModeChange: (PacingAlertMode) -> Unit = {},
    currentTargetMinutes: Int? = null,
    sessionHasProgrammedTime: Boolean = false,
    pacingAlertMessage: String? = null,
    coachPaceAlert: String? = null,
    onUltraFastPreview: (() -> Unit)? = null,
    ultraFastApplied: Boolean = false,
    ultraFastSavedSeconds: Int = 0,
    onRevertUltraFast: (() -> Unit)? = null,
    onTechnique: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var sessionMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    var exerciseMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    var openSessionTimeRequest by remember { mutableIntStateOf(0) }
    val detailLine = (exerciseChips + exerciseTagNames)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() }

    Column(modifier = modifier.fillMaxWidth().statusBarsPadding()) {
        WorkoutTopRow(
            onMinimize = onMinimize,
            onMore = { sessionMenuAnchor = it },
            voiceActive = voiceSessionEnabled,
            center = {
                WorkoutChronometer(
                    startTimeMs = startTimeMs,
                    isComplete = isComplete,
                    sessionTimeRemainingSeconds = sessionTimeRemainingSeconds,
                    onAdjustTimeLimit = onAdjustTimeLimit,
                    onSetAbsoluteTimeLimit = onSetAbsoluteTimeLimit,
                    onClearTimeLimit = onClearTimeLimit,
                    pacingAlertMode = pacingAlertMode,
                    onPacingAlertModeChange = onPacingAlertModeChange,
                    currentTargetMinutes = currentTargetMinutes,
                    sessionHasProgrammedTime = sessionHasProgrammedTime,
                    pacingAlertMessage = pacingAlertMessage,
                    coachPaceAlert = coachPaceAlert,
                    onUltraFastPreview = onUltraFastPreview,
                    ultraFastApplied = ultraFastApplied,
                    ultraFastSavedSeconds = ultraFastSavedSeconds,
                    onRevertUltraFast = onRevertUltraFast,
                    openSessionTimeRequest = openSessionTimeRequest,
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        WorkoutProgressBar(
            exerciseCount = exerciseCount,
            currentIndex = exerciseIndex,
            doneFlags = doneFlags,
            onOpenRoute = onOpenRoute,
        )
        Spacer(Modifier.height(12.dp))
        WorkoutExerciseHeader(
            eyebrow = eyebrow,
            name = exerciseName,
            detailLine = detailLine,
            targetLine = targetLine,
            lastLine = lastLine,
            protocolLabel = protocolLabel,
            onOptions = { exerciseMenuAnchor = it },
            onTechnique = onTechnique,
            onChangeExercise = onChangeExercise,
        )
        Spacer(Modifier.height(12.dp))
    }

    sessionMenuAnchor?.let { anchor ->
        WorkoutSessionMenu(
            anchor = anchor,
            voiceEnabled = voiceSessionEnabled,
            onDismiss = { sessionMenuAnchor = null },
            onCockpit = onOpenCockpit,
            onNotes = onOpenNotes,
            onPhotos = onOpenPhotos,
            onTags = onOpenTags,
            onVoiceMode = onOpenVoiceMode,
            onToggleVoice = { onToggleVoice?.invoke() },
            onFinish = onFinishSession,
        )
    }
    exerciseMenuAnchor?.let { anchor ->
        WorkoutExerciseMenu(
            anchor = anchor,
            onDismiss = { exerciseMenuAnchor = null },
            onHistory = onHistory,
            onUltraFast = { openSessionTimeRequest++ },
            onAdapt = onAdapt,
            onSuperset = onSuperset,
        )
    }
}

/**
 * Eyebrow de la cabecera (LS2a §2 punto 3): «EJERCICIO 3 DE 6 · TORSO A» fuera de superserie y «SUPERSERIE A · RONDA 2 DE 3» dentro.
 * [supersetLetter] es null si el ejercicio no está en superserie.
 */
internal fun workoutLiveEyebrow(
    exerciseIndex: Int,
    exerciseCount: Int,
    sessionName: String,
    supersetLetter: String?,
    round: Int,
    roundCount: Int,
): String =
    if (supersetLetter != null) {
        "Superserie $supersetLetter · ronda ${round.coerceAtLeast(1)} de ${roundCount.coerceAtLeast(1)}"
    } else {
        "Ejercicio ${exerciseIndex + 1} de $exerciseCount · $sessionName"
    }

/**
 * Línea de objetivo (LS2a): «Objetivo 4 × 6–8 · RIR 2», con las series planificadas (sin huecos), el rango de repeticiones y el RIR del
 * primer dato que lo tenga. Sin series, null: no se pinta nada.
 */
internal fun workoutLiveTargetLine(sets: List<ExerciseSet>): String? {
    val planned = sets.filter { !it.isEmptySlot }
    if (planned.isEmpty()) return null
    val reps = planned.firstNotNullOfOrNull { set ->
        set.targetRepsRange?.let { range -> if (range.min == range.max) "${range.min}" else "${range.min}–${range.max}" }
            ?: set.targetReps?.toString()
    }
    val rir = planned.firstNotNullOfOrNull { it.targetRIR }
    val base = if (reps != null) "Objetivo ${planned.size} × $reps" else "Objetivo ${planned.size} series"
    return if (rir != null) "$base · RIR $rir" else base
}

/** Línea de la última vez («Última vez 80 kg × 8»), o null si no hay historial: entonces no se pinta nada (nunca «—» suelto). */
internal fun workoutLiveLastLine(lastLabel: String?): String? = lastLabel?.takeIf { it.isNotBlank() }?.let { "Última vez $it" }

/**
 * Tramos hechos de la barra de progreso (LS2a): un ejercicio está hecho cuando todas sus series planificadas están registradas u
 * omitidas. Un ejercicio sin series no cuenta como hecho.
 */
internal fun workoutLiveDoneFlags(
    exercises: List<Exercise>,
    completedSets: Map<String, com.example.kpkn.data.models.CompletedSet>,
    omittedSetKeys: Set<String>,
): List<Boolean> = exercises.map { exercise ->
    val total = WorkoutStepRules.totalRoadmapSlotsForExercise(exercise, omittedSetKeys)
    total > 0 && WorkoutStepRules.completedRoadmapSlotsForExercise(exercise, completedSets, omittedSetKeys) >= total
}
