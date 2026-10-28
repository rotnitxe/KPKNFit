package com.example.kpkn.screens.workout

import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.sp
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
    MENU("Sesión"), VIEW("Vista de registro"), VOICE("Conversación de voz"), ROUTE("Ruta de la sesión"), MILESTONES("Hitos"), PHOTOS("Fotos y vídeos"),
    TIMER("Temporizador"), RM("Calculadora RM"), CALORIES("Calorías"), NOTES("Notas y tareas"),
    ASSISTANCE("Asistencia de sesión"), ANALYSIS_HELP("Cómo leer este análisis"),
}

/** Tarjetas y Lista comparten el mismo borrador; Foco quedó descartada (Refinamiento 02). */
internal enum class LiveSessionView(val title: String) { CARDS("Tarjetas"), LIST("Lista") }

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
    calibratedLoads: Map<String, Double> = emptyMap(),
    completedExercises: List<CompletedExercise> = emptyList(),
    omittedSetKeys: Set<String> = emptySet(),
    currentExerciseId: String? = null,
    supersetGroups: List<SupersetGroup> = emptyList(),
    liveEnergySummary: SessionEnergySummary? = null,
    bodyWeight: Double? = null,
    startTimeMs: Long = 0L,
    targetMinutes: Int? = null,
    onSelectExercise: (String) -> Unit = {},
    onSelectSuperset: (String) -> Unit = {},
    onOpenExerciseOptions: (String) -> Unit = {},
    onOpenSupersetOptions: (String) -> Unit = onOpenExerciseOptions,
    onAddExercise: () -> Unit = {},
    onSelectSet: ((String, Int) -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
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
    onOpenVoice: () -> Unit = {},
    onOpenStory: () -> Unit = {},
    timer: LiveSessionTimerState = rememberLiveSessionTimer(),
    sessionAccentColor: Color = Color(0xFFF49A6E),
    currentView: LiveSessionView = LiveSessionView.CARDS,
    onSelectView: (LiveSessionView) -> Unit = {},
    sessionTitle: String = "",
    sessionProgress: String = "",
    onOpenReadiness: (() -> Unit)? = null,
    onOpenSessionTime: (() -> Unit)? = null,
    onQuickRecord: (() -> Unit)? = null,
    onShortenSession: (() -> Unit)? = null,
    onFinishSession: (() -> Unit)? = null,
    /** «Por qué aparece esta referencia» de la asistencia: el historial del ejercicio en curso. */
    onOpenReferenceHistory: (() -> Unit)? = null,
    /** «Revisar descanso sugerido»: solo mientras hay un descanso con sugerencia. */
    onOpenRestSuggestion: (() -> Unit)? = null,
) {
    val bodyStates = rememberSaveableStateHolder()
    if (destination == null) return
    // Hojas del prototipo 02: menú «Tu sesión» en filas, vista de registro y el resto con la misma hoja (título en Syne, ✕ y «Listo»).
    val title = liveSessionSheetTitle(destination)
    val withFooter = destination !in setOf(LiveSessionDestination.MENU, LiveSessionDestination.VIEW, LiveSessionDestination.ROUTE,
        LiveSessionDestination.ASSISTANCE)
    LiveSheet(
        title = title,
        onDismiss = { onDestination(null) },
        tag = "live-session-sheet-${destination.name.lowercase()}",
        stableHeightFraction = if (destination == LiveSessionDestination.ROUTE) .84f else null,
        footer = if (withFooter) { close -> LiveSheetPrimary("Listo", close) } else null,
    ) { close ->
        bodyStates.SaveableStateProvider(destination.name) {
            when (destination) {
                LiveSessionDestination.MENU -> {
                    if (sessionTitle.isNotBlank() || sessionProgress.isNotBlank()) {
                        LiveSheetSubtitle(listOf(sessionTitle, sessionProgress).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                    fun go(next: LiveSessionDestination) = onDestination(next)
                    fun run(action: () -> Unit) { onDestination(null); action() }
                    LiveSheetSectionLabel("Sesión", Modifier.padding(top = 0.dp))
                    LiveSheetMenuRow("Vista de registro", { go(LiveSessionDestination.VIEW) }, icon = KpknLineIcons.Layers, small = currentView.title,
                        modifier = Modifier.testTag("live-session-menu-view"))
                    LiveSheetMenuRow("Recorrido", { go(LiveSessionDestination.ROUTE) }, icon = KpknLineIcons.Route,
                        modifier = Modifier.testTag("live-session-menu-route"))
                    onOpenReadiness?.let { LiveSheetMenuRow("Antes de entrenar", { run(it) }, icon = KpknLineIcons.Pulse) }
                    onOpenSessionTime?.let { LiveSheetMenuRow("Tiempo de sesión", { run(it) }, icon = KpknLineIcons.Clock) }
                    onFinishSession?.let { LiveSheetMenuRow("Completar sesión", { run(it) }, icon = KpknLineIcons.Flag) }
                    LiveSheetSectionLabel("Registro")
                    onQuickRecord?.let { LiveSheetMenuRow("Registro rápido de una serie", { run(it) }, icon = KpknLineIcons.Check) }
                    onShortenSession?.let { LiveSheetMenuRow("Acortar sesión", { run(it) }, icon = KpknLineIcons.Sliders) }
                    LiveSheetMenuRow("Modo de voz", { run(onOpenVoice) }, icon = KpknLineIcons.Mic, modifier = Modifier.testTag("live-session-menu-voice"))
                    LiveSheetMenuRow("Notas y tareas", { go(LiveSessionDestination.NOTES) }, icon = KpknLineIcons.Edit,
                        modifier = Modifier.testTag("live-session-menu-notes"))
                    LiveSheetMenuRow("Álbum de sesión", { run(onOpenPhotos) }, icon = KpknLineIcons.Camera,
                        modifier = Modifier.testTag("live-session-menu-photos"))
                    LiveSheetSectionLabel("Herramientas")
                    LiveSheetMenuRow("Cronómetro independiente", { go(LiveSessionDestination.TIMER) }, icon = KpknLineIcons.Clock,
                        modifier = Modifier.testTag("live-session-menu-timer"))
                    LiveSheetMenuRow("Hitos", { go(LiveSessionDestination.MILESTONES) }, icon = KpknLineIcons.Trophy,
                        modifier = Modifier.testTag("live-session-menu-milestones"))
                    LiveSheetMenuRow("Calculadora RM", { go(LiveSessionDestination.RM) }, icon = KpknLineIcons.Target,
                        modifier = Modifier.testTag("live-session-menu-rm"))
                    LiveSheetMenuRow("Calorías", { go(LiveSessionDestination.CALORIES) }, icon = KpknLineIcons.Flame,
                        modifier = Modifier.testTag("live-session-menu-calories"))
                    LiveSheetMenuRow("Historia de la sesión", { run(onOpenStory) }, icon = KpknLineIcons.Star,
                        modifier = Modifier.testTag("workout-story-header"))
                    LiveSheetMenuRow("Ayuda y explicaciones", { go(LiveSessionDestination.ASSISTANCE) }, icon = KpknLineIcons.Info,
                        modifier = Modifier.testTag("live-session-menu-assistance"))
                }
                // `assistance` del prototipo 02: ayuda solo cuando aporta una explicación o una acción concreta.
                LiveSessionDestination.ASSISTANCE -> {
                    LiveSheetSubtitle("Ayuda solo cuando aporta una explicación o una acción concreta.")
                    LiveSheetMenuRow("Por qué aparece esta referencia", { onDestination(null); onOpenReferenceHistory?.invoke() },
                        icon = KpknLineIcons.History, enabled = onOpenReferenceHistory != null,
                        modifier = Modifier.testTag("live-assistance-reference"))
                    LiveSheetMenuRow("Revisar descanso sugerido", { onDestination(null); onOpenRestSuggestion?.invoke() },
                        icon = KpknLineIcons.Clock, enabled = onOpenRestSuggestion != null,
                        small = if (onOpenRestSuggestion == null) "Durante el descanso" else null,
                        modifier = Modifier.testTag("live-assistance-rest"))
                    LiveSheetMenuRow("Entender las métricas del vídeo", { onDestination(LiveSessionDestination.ANALYSIS_HELP) }, icon = KpknLineIcons.Info,
                        modifier = Modifier.testTag("live-assistance-analysis"))
                    LiveSessionHelpNote("Explicaciones en su sitio",
                        "La referencia de carga se explica en su historial, el descanso sugerido en el propio descanso y las métricas en el análisis del vídeo.")
                }
                LiveSessionDestination.ANALYSIS_HELP -> {
                    LiveSheetSubtitle("El análisis sale de tu vídeo: la pose y la barra se estiman fotograma a fotograma.")
                    LiveSessionHelpNote("Qué se puede comprobar",
                        "Seleccionar una repetición, desplazar la línea de tiempo, activar capas y consultar las métricas disponibles.")
                    LiveSessionHelpNote("Unidades y confianza",
                        "Sin escala física validada, la velocidad permanece en unidades relativas. Un dato ausente se muestra como «—»; " +
                            "los valores estimados llevan «≈».")
                }
                LiveSessionDestination.VIEW -> {
                    LiveSheetSubtitle("Tarjetas y Lista comparten el mismo borrador. Cambiar de vista no registra ni borra nada.")
                    LiveSessionView.entries.forEach { view ->
                        LiveSheetMenuRow(view.title, { onSelectView(view); onDestination(null) }, icon = null,
                            small = if (view == currentView) "Actual" else null,
                            modifier = Modifier.testTag("live-session-view-${view.name.lowercase()}"))
                    }
                }
                LiveSessionDestination.ROUTE -> LiveRouteSheetContent(
                    exercises, completedSets, completedExercises, omittedSetKeys, currentExerciseId, supersetGroups,
                    liveEnergySummary, bodyWeight, startTimeMs, targetMinutes, calibratedLoads,
                    onSelectExercise = { onSelectExercise(it); onDestination(null) },
                    onSelectSuperset = { onSelectSuperset(it); onDestination(null) },
                    onSelectSet = onSelectSet?.let { select -> { id, index -> select(id, index); onDestination(null) } },
                    onOpenExerciseOptions = { onDestination(null); onOpenExerciseOptions(it) },
                    onOpenSupersetOptions = { onDestination(null); onOpenSupersetOptions(it) },
                    onReorder = onReorder?.let { reorder -> { onDestination(null); reorder() } },
                    onAddExercise = { onDestination(null); onAddExercise() },
                )
                LiveSessionDestination.MILESTONES -> LiveSessionMilestonesContent(exercises, completedSets, milestones, sessionAccentColor)
                LiveSessionDestination.PHOTOS -> LaunchedEffect(Unit) { onDestination(null); onOpenPhotos() }
                LiveSessionDestination.TIMER -> LiveSessionTimerContent(timer)
                LiveSessionDestination.RM -> WorkoutRmCalcContent(bodyWeight = bodyWeight, sessionAccentColor = sessionAccentColor, softInputs = true)
                LiveSessionDestination.VOICE -> LaunchedEffect(Unit) { onDestination(null); onOpenVoice() }
                LiveSessionDestination.CALORIES -> LiveSessionCaloriesContent(liveEnergySummary, bodyWeight)
                LiveSessionDestination.NOTES -> LiveSessionNotesContent(sessionNotes, sessionSavedNotes, sessionChecklist,
                    onSessionNotesChange, onSaveSessionNote, onAddChecklistItem, onToggleChecklistItem, onRemoveChecklistItem, sessionAccentColor)
            }
        }
    }
}

/** Títulos de las hojas de la sesión como en el prototipo 02. */
internal fun liveSessionSheetTitle(destination: LiveSessionDestination): String = when (destination) {
    LiveSessionDestination.MENU -> "Tu sesión"
    LiveSessionDestination.VIEW -> "Vista de registro"
    LiveSessionDestination.ROUTE -> "Recorrido de la sesión"
    LiveSessionDestination.TIMER -> "Cronómetro"
    LiveSessionDestination.NOTES -> "Notas de sesión"
    LiveSessionDestination.MILESTONES -> "Hitos de la sesión"
    else -> destination.title
}

/** `.v2-history-note` con título: filete de 2, el título 13/600 y el texto de 12. */
@Composable
private fun LiveSessionHelpNote(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).drawBehind {
            drawLine(Color(0x2DFFFFFF), androidx.compose.ui.geometry.Offset(1.dp.toPx(), 0f),
                androidx.compose.ui.geometry.Offset(1.dp.toPx(), size.height), 2.dp.toPx())
        }.padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(title, style = KpknType.bodyStrong.copy(fontSize = 13.sp), color = WizardColors.text)
        Text(body, Modifier.padding(top = 8.dp), style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = WizardColors.textMuted)
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
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(KpknSetFormat.rest((timer.elapsedMillis(now) / 1000).toInt()),
            style = KpknType.display.copy(fontSize = 52.sp, lineHeight = 58.sp, fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.text, modifier = Modifier.padding(vertical = 18.dp).testTag("live-session-timer-value"))
        LiveSheetSubtitle("Independiente del descanso entre series.")
        LiveSheetPrimary(if (timer.startedAtMillis == null) "Iniciar" else "Pausar", { now = clock(); timer.toggle(now) })
        LiveSheetTextAction("Reiniciar", { timer.reset() })
    }
}

@Composable
internal fun LiveSessionCaloriesContent(summary: SessionEnergySummary?, bodyWeight: Double?) {
    val status = sessionCalorieStatus(summary, summary?.exerciseContributions?.sumOf { it.completedSets } ?: 0, bodyWeight)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Energía registrada", style = KpknType.bodyStrong, color = WizardColors.textMuted)
        status.kcal?.let { Text("≈ $it kcal", style = KpknType.metricXL, color = WizardColors.text) }
        status.hint?.let { Text(it, style = KpknType.body, color = WizardColors.textMuted) }
    }
}
