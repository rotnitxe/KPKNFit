package com.example.kpkn.screens.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal enum class LiveSessionDestination(val title: String) {
    MENU("Sesión"), ROUTE("Ruta de la sesión"), MILESTONES("Hitos"), PHOTOS("Fotos y vídeos"),
    TIMER("Temporizador"), RM("Calculadora RM"), CALORIES("Calorías"), NOTES("Notas y tareas"),
}

/** Monotonic time survives closing the sheet; paused time never includes time spent away. */
@Stable
internal class LiveSessionTimerState(paused: Long = 0L, started: Long? = null) {
    var pausedMillis by mutableLongStateOf(paused)
        private set
    var startedAtMillis by mutableStateOf(started)
        private set
    fun elapsedMillis(now: Long): Long = pausedMillis + (startedAtMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    fun toggle(now: Long) {
        if (startedAtMillis == null) startedAtMillis = now else {
            pausedMillis = elapsedMillis(now)
            startedAtMillis = null
        }
    }
    fun reset() { pausedMillis = 0L; startedAtMillis = null }
}

@Composable
internal fun rememberLiveSessionTimer(vararg inputs: Any?): LiveSessionTimerState = rememberSaveable(*inputs, saver = listSaver(
    save = { listOf(it.pausedMillis, it.startedAtMillis ?: -1L) },
    restore = { LiveSessionTimerState(it[0], it[1].takeIf { start -> start >= 0 }) },
)) { LiveSessionTimerState() }

/** Destinations share the original data/callbacks. Photos delegates to the existing session album. */
@Composable
internal fun LiveSessionSheetsHost(
    destination: LiveSessionDestination?,
    onDestination: (LiveSessionDestination?) -> Unit,
    exercises: List<Exercise> = emptyList(),
    completedSets: Map<String, CompletedSet> = emptyMap(),
    completedExercises: List<CompletedExercise> = emptyList(),
    omittedSetKeys: Set<String> = emptySet(),
    currentExerciseId: String? = null,
    supersetGroups: List<SupersetGroup> = emptyList(),
    liveEnergySummary: SessionEnergySummary = SessionEnergySummary(),
    bodyWeight: Double? = null,
    startTimeMs: Long = 0L,
    targetMinutes: Int? = null,
    ringPreview: PostSessionPreview? = null,
    onSelectExercise: (String) -> Unit = {},
    onSelectSuperset: (String) -> Unit = {},
    onOpenExerciseOptions: (String) -> Unit = {},
    onOpenSupersetOptions: (String) -> Unit = onOpenExerciseOptions,
    onAddExercise: () -> Unit = {},
    milestones: List<SessionMilestone> = emptyList(),
    sessionNotes: String = "",
    sessionSavedNotes: List<SessionSavedNote> = emptyList(),
    sessionChecklist: List<SessionChecklistItem> = emptyList(),
    onSessionNotesChange: (String) -> Unit = {},
    onSaveSessionNote: (String) -> Unit = {},
    onAddChecklistItem: (String) -> Unit = {},
    onToggleChecklistItem: (String) -> Unit = {},
    onRemoveChecklistItem: (String) -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    timer: LiveSessionTimerState = rememberLiveSessionTimer(),
    sessionAccentColor: Color = Color(0xFFF49A6E),
) {
    val bodyStates = rememberSaveableStateHolder()
    if (destination == null) return
    HeaderSheet(onDismiss = { onDestination(null) }, stableHeightFraction = if (destination == LiveSessionDestination.ROUTE) .84f else .76f) { close ->
        Column(Modifier.fillMaxSize().testTag("live-session-sheet-${destination.name.lowercase()}")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("live-session-sheet-heading"), verticalAlignment = Alignment.CenterVertically) {
                Text(destination.title, Modifier.weight(1f), style = KpknType.title, color = WizardColors.text)
                KpknIconButton(KpknLineIcons.Close, "Cerrar ${destination.title}", close)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                bodyStates.SaveableStateProvider(destination.name) {
                when (destination) {
                    LiveSessionDestination.MENU -> Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiveSessionDestination.entries.filter { it != LiveSessionDestination.MENU }.forEach { item ->
                            HeaderSheetAction(item.title, {
                                if (item == LiveSessionDestination.PHOTOS) { onDestination(null); onOpenPhotos() }
                                else onDestination(item)
                            }, Modifier.fillMaxWidth().testTag("live-session-menu-${item.name.lowercase()}"))
                        }
                    }
                    LiveSessionDestination.ROUTE -> CockpitSessionSummary(
                        exercises, completedSets, completedExercises, omittedSetKeys, currentExerciseId, supersetGroups,
                        liveEnergySummary, bodyWeight, startTimeMs, targetMinutes, ringPreview,
                        onSelectExercise = { onSelectExercise(it); onDestination(null) },
                        onSelectSuperset = { onSelectSuperset(it); onDestination(null) },
                        onOpenExerciseOptions = { onDestination(null); onOpenExerciseOptions(it) },
                        onOpenSupersetOptions = { onDestination(null); onOpenSupersetOptions(it) },
                        onAddExercise = { onDestination(null); onAddExercise() },
                    )
                    LiveSessionDestination.MILESTONES -> LiveSessionMilestonesContent(exercises, completedSets, milestones, sessionAccentColor)
                    LiveSessionDestination.PHOTOS -> LaunchedEffect(Unit) { onDestination(null); onOpenPhotos() }
                    LiveSessionDestination.TIMER -> LiveSessionTimerContent(timer)
                    LiveSessionDestination.RM -> Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        WorkoutRmCalcContent(bodyWeight = bodyWeight, sessionAccentColor = sessionAccentColor, softInputs = true)
                    }
                    LiveSessionDestination.CALORIES -> LiveSessionCaloriesContent(liveEnergySummary, bodyWeight)
                    LiveSessionDestination.NOTES -> LiveSessionNotesContent(sessionNotes, sessionSavedNotes, sessionChecklist,
                        onSessionNotesChange, onSaveSessionNote, onAddChecklistItem, onToggleChecklistItem, onRemoveChecklistItem, sessionAccentColor)
                }
                }
            }
        }
    }
}

@Composable
internal fun LiveSessionTimerContent(timer: LiveSessionTimerState, clock: () -> Long = SystemClock::elapsedRealtime) {
    var now by remember { mutableLongStateOf(clock()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(timer.startedAtMillis, owner) {
        now = clock()
        if (timer.startedAtMillis != null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { now = clock(); delay(250) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(KpknSetFormat.rest((timer.elapsedMillis(now) / 1000).toInt()), style = KpknType.metricXL,
            color = WizardColors.text, modifier = Modifier.testTag("live-session-timer-value"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderSheetAction(if (timer.startedAtMillis == null) "Iniciar" else "Pausar", { now = clock(); timer.toggle(now) }, Modifier.weight(1f), primary = true)
            HeaderSheetAction("Reiniciar", { timer.reset() }, Modifier.weight(1f))
        }
        Text("Independiente del descanso entre series", style = KpknType.caption, color = WizardColors.textMuted)
    }
}

@Composable
internal fun LiveSessionCaloriesContent(summary: SessionEnergySummary, bodyWeight: Double?) {
    val status = sessionCalorieStatus(summary, summary.exerciseContributions.sumOf { it.completedSets }, bodyWeight)
    Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calorías quemadas", style = KpknType.bodyStrong, color = WizardColors.textMuted)
        status.kcal?.let { Text("≈ $it kcal", style = KpknType.metricXL, color = WizardColors.text) }
        status.hint?.let { Text(it, style = KpknType.body, color = WizardColors.textMuted) }
    }
}
