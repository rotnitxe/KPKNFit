package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MovementPatternReadiness
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.domain.auge.AugeUtils
import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.discomfort.DiscomfortSearch
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingAdjustLevel
import com.example.kpkn.ui.components.rings.RingAdjustMuscles
import com.example.kpkn.ui.components.rings.RingAdjustPanel
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.components.rings.TowerRingsStyle
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknModule
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Una molestia que quedó presente la sesión anterior y el día en que se anotó («lunes»), si se sabe. */
data class ReadinessReportedDiscomfort(
    val id: String,
    val whenLabel: String? = null,
)

/** Cuántas filas de chips de molestias salen de entrada; lo que no cabe queda tras «Más (N)». */
private const val DiscomfortRowsCollapsed = 3

/** Rojo suave del punto de una molestia que ya se reportó antes; el de las sugeridas es el amarillo de la energía. */
private val ReportedDot = Color(0xFFEF6F64)

/** Fondo del buscador de molestias (G1). */
private val SearchFill = Color(0xFF161616)

/**
 * Estado previo (G1): «¿Cómo llegas a entrenar hoy?». La torre luminosa con la leyenda de tres columnas; tocar un anillo (o su columna)
 * abre el ajuste oficial ([RingAdjustPanel]) en ese canal. Debajo, las molestias en línea (buscador y chips: «Ninguna», las que
 * quedaron de la sesión anterior y las sugeridas para los músculos de hoy), la voz en una fila y el ✓ que confirma.
 *
 * Lo que se ajusta va al mismo estado de siempre (`neural`, `spinal`, `muscleAdjustments` y las marcas `userEdited*`) y solo los canales
 * editados se entregan como ajustes manuales en [onSave]: `manualMuscular` va siempre a null y los músculos editados salen en
 * `manualMuscleBatteries`. Los valores son los visibles (la Columna se invierte al guardar, en quien recibe [onSave]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutReadinessSheet(
    showReadinessSheet: Boolean,
    gender: Gender?,
    sessionMuscleStartingBatteries: Map<String, Int>,
    readinessNeuralStart: Int,
    readinessMuscularStart: Int,
    readinessSpinalStart: Int,
    hazeState: HazeState,
    onSave: (
        neural: Int,
        muscular: Int?,
        spinal: Int,
        perMuscle: Map<String, Int>,
        discomforts: List<String>,
        /** Only channels the user actually edited — used for AUGE manual overrides. */
        manualNeural: Int?,
        manualSpinal: Int?,
        manualMuscular: Int?,
        manualMuscleBatteries: Map<String, Int>,
    ) -> Unit,
    patternReadiness: List<MovementPatternReadiness> = emptyList(),
    exerciseReadinessMap: Map<String, ExerciseReadiness> = emptyMap(),
    sessionExercises: List<Exercise> = emptyList(),
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    augeSnapshot: AugeSnapshot? = null,
    initialDiscomforts: List<String> = emptyList(),
    voiceSessionEnabled: Boolean = false,
    voiceCaptureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
    onVoiceToggle: () -> Unit = {},
    onVoiceCaptureModeChange: (VoiceCaptureMode) -> Unit = {},
    ringsStyle: TowerRingsStyle = TowerRingsStyle.LUMINOUS,
    forcedGleamProgress: Float? = null,
    /**
     * Cerrar el estado previo sin responder (Atrás, tocar fuera o arrastrar la hoja hacia abajo; LV4, 15): la sesión se ve y el estado
     * previo queda pendiente. Con `null` la hoja sigue siendo obligatoria, como antes.
     */
    onDismiss: (() -> Unit)? = null,
    /** Molestias que quedaron presentes la sesión anterior, con el día en que se anotaron (chips con punto rojo). */
    reportedDiscomforts: List<ReadinessReportedDiscomfort> = emptyList(),
    /**
     * Pilares de los músculos de hoy, para sugerir molestias. Con `null` (lo normal) salen de [sessionExercises]; las pruebas y las hojas de
     * render los fijan sin cargar el catálogo de ejercicios.
     */
    suggestionPillars: List<String>? = null,
    /** Opens the existing permission/mode flow; no simulated microphone state. */
    onOpenVoice: (() -> Unit)? = null,
) {
    if (!showReadinessSheet) return

    var neural by rememberSaveable { mutableIntStateOf(readinessNeuralStart) }
    var muscular by rememberSaveable { mutableIntStateOf(readinessMuscularStart) }
    var spinal by rememberSaveable { mutableIntStateOf(readinessSpinalStart) }
    val muscleAdjustments = rememberSaveable(saver = muscleMapSaver()) { mutableStateMapOf<String, Int>() }
    // Músculos que no son de la sesión y la persona ha tocado en el cuerpo: van aparte para no mover el promedio de los de hoy.
    val otherMuscleAdjustments = rememberSaveable(saver = muscleMapSaver()) { mutableStateMapOf<String, Int>() }
    val derivedMuscular by derivedStateOf {
        if (muscleAdjustments.isEmpty()) readinessMuscularStart.coerceIn(0, 100)
        else muscleAdjustments.values.average().toInt().coerceIn(0, 100)
    }
    var userEditedNeural by rememberSaveable { mutableStateOf(false) }
    var userEditedSpinal by rememberSaveable { mutableStateOf(false) }
    val userEditedMuscles = rememberSaveable(saver = editedMusclesSaver()) { mutableStateMapOf<String, Boolean>() }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var selectedDiscomforts by rememberSaveable { mutableStateOf(listOf<String>()) }
    var adjustChannel by remember { mutableStateOf<RingChannel?>(null) }
    var showVoiceControls by remember { mutableStateOf(false) }

    LaunchedEffect(
        initialized,
        readinessNeuralStart,
        readinessMuscularStart,
        readinessSpinalStart,
        sessionMuscleStartingBatteries,
    ) {
        if (!initialized) {
            neural = readinessNeuralStart
            muscular = derivedMuscular
            spinal = readinessSpinalStart
            muscleAdjustments.clear()
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                muscleAdjustments[muscleId] = value.coerceIn(0, 100)
            }
            selectedDiscomforts = initialDiscomforts
            initialized = true
        } else {
            if (!userEditedNeural) neural = readinessNeuralStart
            muscular = readinessMuscularStart
            if (!userEditedSpinal) spinal = readinessSpinalStart
            val validMuscles = sessionMuscleStartingBatteries.keys
            muscleAdjustments.keys
                .filter { it !in validMuscles }
                .forEach { muscleAdjustments.remove(it) }
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                if (userEditedMuscles[muscleId] != true) {
                    muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                }
            }
        }
    }
    KpknSheet(
        onDismissRequest = { onDismiss?.invoke() },
        dismissible = onDismiss != null,
        stableHeightFraction = 0.86f,
        hazeState = hazeState,
    ) {
        if (showVoiceControls) {
            androidx.activity.compose.BackHandler { showVoiceControls = false }
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Modo de voz", style = KpknType.title, color = WizardColors.text)
                ReadinessVoiceRow(voiceSessionEnabled, voiceCaptureMode, onVoiceToggle, onVoiceCaptureModeChange)
                KpknCompactPill(text = "Volver", onClick = { showVoiceControls = false }, filled = false)
            }
        } else if (adjustChannel != null) {
    // El ajuste oficial sustituye el contenido del estado previo, en el canal que se tocó.
    adjustChannel?.let { channel ->
        val bodyValues = readinessBodyValues(muscleAdjustments, otherMuscleAdjustments, perMuscle)
        val bodyBefore = readinessBodyBefore(sessionMuscleStartingBatteries, perMuscle)
        val editedIds = muscleAdjustments.keys.filter { userEditedMuscles[it] == true }.toSet() + otherMuscleAdjustments.keys
        RingAdjustPanel(
            modifier = Modifier.testTag("readiness-adjust-panel"),
            initialChannel = channel,
            onDismiss = { adjustChannel = null },
            energy = RingAdjustLevel(value = neural, before = readinessNeuralStart, edited = userEditedNeural),
            spine = RingAdjustLevel(value = spinal, before = readinessSpinalStart, edited = userEditedSpinal),
            muscles = RingAdjustMuscles(
                values = bodyValues,
                before = bodyBefore,
                highlighted = muscleAdjustments.keys.toSet(),
                edited = editedIds,
            ),
            onEnergyChange = {
                neural = it
                userEditedNeural = true
            },
            onSpineChange = {
                spinal = it
                userEditedSpinal = true
            },
            onMuscleChange = { id, value ->
                if (muscleAdjustments.containsKey(id)) {
                    muscleAdjustments[id] = value.coerceIn(0, 100)
                    userEditedMuscles[id] = true
                } else {
                    otherMuscleAdjustments[id] = value.coerceIn(0, 100)
                }
            },
            onEnergyReset = {
                neural = readinessNeuralStart
                userEditedNeural = false
            },
            onSpineReset = {
                spinal = readinessSpinalStart
                userEditedSpinal = false
            },
            onMusclesReset = {
                sessionMuscleStartingBatteries.forEach { (muscleId, value) -> muscleAdjustments[muscleId] = value.coerceIn(0, 100) }
                userEditedMuscles.clear()
                otherMuscleAdjustments.clear()
            },
        )
    }
        } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.82f to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "¿Cómo llegas a entrenar hoy?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    // 22 / 28 y corte equilibrado: en dos líneas no queda un «hoy?» suelto.
                    style = KpknType.title.copy(fontSize = 22.sp, lineHeight = 28.sp, lineBreak = LineBreak.Heading),
                    color = WizardColors.text,
                )

                // 1. La torre y las tres leyendas conservan sus acciones accesibles de ajuste.
                ReadinessTowerBlock(
                    muscular = derivedMuscular,
                    neural = neural,
                    spinal = spinal,
                    style = ringsStyle,
                    forcedGleamProgress = forcedGleamProgress,
                    onChannelTap = { adjustChannel = it },
                )

                // 2. Molestias en línea: buscador y chips.
                val sessionPillars = suggestionPillars ?: remember(sessionExercises) { getSessionPillarMuscleIds(sessionExercises) }
                ReadinessDiscomfortBlock(
                    selected = selectedDiscomforts,
                    onSelectedChange = { selectedDiscomforts = it },
                    reported = reportedDiscomforts,
                    initialSelected = initialDiscomforts,
                    sessionPillarMuscleIds = sessionPillars,
                )


            }

            // 4. BOTÓN PRINCIPAL — ✓ de 64 dp anclado abajo, con la piel del ✓ de la serie (LR1b)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KpknIconButton(
                        icon = KpknLineIcons.Mic,
                        contentDescription = "Modo de voz",
                        onClick = { if (onOpenVoice != null) onOpenVoice() else showVoiceControls = true },
                        modifier = Modifier.size(48.dp).testTag("readiness-voice-fab"),
                    )
                ReadinessConfirmButton(
                    onClick = {
                        val editedMuscleMap = muscleAdjustments
                            .filter { (id, _) -> userEditedMuscles[id] == true }
                            .mapValues { (_, v) -> v.coerceIn(0, 100) } +
                            otherMuscleAdjustments.mapValues { (_, v) -> v.coerceIn(0, 100) }
                        val manualMuscular = null
                        onSave(
                            neural,
                            derivedMuscular,
                            spinal,
                            muscleAdjustments.toMap() + otherMuscleAdjustments.toMap(),
                            selectedDiscomforts.toList(),
                            if (userEditedNeural) neural.coerceIn(0, 100) else null,
                            if (userEditedSpinal) spinal.coerceIn(0, 100) else null,
                            manualMuscular,
                            editedMuscleMap,
                        )
                    },
                )
                }
            }
        }
            }
    }

}

/** Guarda un mapa de músculos (nombre → valor) al girar o recrearse la pantalla. */
private fun muscleMapSaver() = mapSaver(
    save = { value: SnapshotStateMap<String, Int> -> value.toMap() },
    restore = { restored ->
        mutableStateMapOf<String, Int>().apply {
            restored.forEach { (key, value) ->
                val n = (value as? Number)?.toInt() ?: return@forEach
                this[key] = n
            }
        }
    },
)

/** Conserva qué músculos ajustó la persona junto con sus valores al recrear la pantalla. */
private fun editedMusclesSaver() = mapSaver(
    save = { value: SnapshotStateMap<String, Boolean> -> value.toMap() },
    restore = { restored ->
        mutableStateMapOf<String, Boolean>().apply {
            restored.forEach { (key, value) -> if (value == true) this[key] = true }
        }
    },
)

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Los músculos del ajuste (todos los que tienen dato, no solo los de hoy)
// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Los valores de todos los músculos para el cuerpo del ajuste: los de hoy ([session]) y los que la persona tocó fuera de la sesión
 * ([others]) y, para el resto, el de [perMuscle] cuando ese músculo tiene alguna sesión registrada (sin ella no hay dato y no se
 * inventa un 100 %: la zona sale sin datos). Cada músculo entra una sola vez, por su pilar.
 */
internal fun readinessBodyValues(
    session: Map<String, Int>,
    others: Map<String, Int>,
    perMuscle: Map<String, MuscleRecoveryStatus>,
): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    val covered = HashSet<String>()
    fun add(id: String, value: Int) {
        val key = getAugeMusclePillarId(id)
        if (!covered.add(key)) return
        out[id] = value.coerceIn(0, 100)
    }
    session.forEach { (id, value) -> add(id, value) }
    others.forEach { (id, value) -> add(id, value) }
    perMuscle.forEach { (name, status) ->
        if (status.hoursSinceLastSession < 0) return@forEach
        add(name, status.recoveryScore)
    }
    return out
}

/** Los valores de partida de esos músculos: los de hoy tal como llegaron y, el resto, los del motor con sesión registrada. */
internal fun readinessBodyBefore(
    sessionStart: Map<String, Int>,
    perMuscle: Map<String, MuscleRecoveryStatus>,
): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    val covered = HashSet<String>()
    fun add(id: String, value: Int) {
        val key = getAugeMusclePillarId(id)
        if (!covered.add(key)) return
        out[id] = value.coerceIn(0, 100)
    }
    sessionStart.forEach { (id, value) -> add(id, value) }
    perMuscle.forEach { (name, status) ->
        if (status.hoursSinceLastSession < 0) return@forEach
        add(name, status.recoveryScore)
    }
    return out
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Molestias que quedaron de la sesión anterior
// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Las molestias de [previousIds] (las que quedaron presentes la sesión anterior) con el día en que se anotaron: el del registro más
 * reciente de [history] que las dio por presentes («lunes»). Sin registro con fecha, solo el id. Función pura: la fecha de hoy y la
 * zona horaria vienen de fuera.
 */
internal fun readinessReportedBefore(
    previousIds: List<String>,
    history: List<WorkoutLog>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ReadinessReportedDiscomfort> {
    val dated = history
        .mapNotNull { log -> AugeUtils.logDateMs(log).takeIf { it > 0L }?.let { ms -> log to ms } }
        .sortedByDescending { it.second }
    return previousIds.filter { it != "none" }.distinct().map { id ->
        val last = dated.firstOrNull { (log, _) -> id in log.stillPresentDiscomfortIds }
        val label = last?.let { (_, ms) -> readinessWhenLabel(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), today) }
        ReadinessReportedDiscomfort(id = id, whenLabel = label)
    }
}

/** «hoy», «ayer», el día de la semana («lunes») dentro de la última semana y «hace 2 semanas» después; sin fecha futura. */
internal fun readinessWhenLabel(date: LocalDate, today: LocalDate): String? {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days < 0 -> null
        days == 0L -> "hoy"
        days == 1L -> "ayer"
        days < 7 -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es"))
        days < 14 -> "hace 1 semana"
        else -> "hace ${days / 7} semanas"
    }
}

/** Lo que dice el chip de una molestia anotada antes: «Rodilla anterior · lunes». */
internal fun readinessReportedChipText(reported: ReadinessReportedDiscomfort): String =
    reported.whenLabel?.let { "${discomfortLabel(reported.id)} · $it" } ?: discomfortLabel(reported.id)

// ──────────────────────────────────────────────────────────────────────────────────────────────
// La torre y su leyenda
// ──────────────────────────────────────────────────────────────────────────────────────────────

/** Medidas de la torre del estado previo: la de G1 (250 × 186 sobre 300 × 230), un poco más grande para el dedo. */
private val TowerWidth = 260.dp
private const val TowerAspect = 200f / 260f

@Composable
private fun ReadinessTowerBlock(
    muscular: Int,
    neural: Int,
    spinal: Int,
    style: TowerRingsStyle,
    forcedGleamProgress: Float?,
    onChannelTap: (RingChannel) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, TowerWidth)
            // Sin animación: al volver del ajuste la torre enseña ya el valor nuevo.
            KpknTowerRings(
                values = TowerRingValues(columna = spinal, musculos = muscular, energia = neural),
                modifier = Modifier
                    .width(width)
                    .height(width * TowerAspect)
                    .testTag("readiness-tower"),
                onRingTap = onChannelTap,
                animate = false,
                style = style,
                forcedGleamProgress = forcedGleamProgress,
            )
        }
        // Leyenda en el orden de la torre vista de frente (G1): Músculos, Energía y Columna; cada columna también abre su ajuste.
        // Las de los lados pesan lo mismo y algo más que la del centro: «MÚSCULOS» es la palabra más larga y con la letra grande no cabía.
        Row(Modifier.fillMaxWidth()) {
            ReadinessLegendColumn("Músculos", muscular, KpknRingColors.musculos, RingChannel.MUSCULOS, onChannelTap, Modifier.weight(LegendSideWeight))
            ReadinessLegendColumn("Energía", neural, KpknRingColors.energia, RingChannel.ENERGIA, onChannelTap, Modifier.weight(1f))
            ReadinessLegendColumn("Columna", spinal, KpknRingColors.columna, RingChannel.COLUMNA, onChannelTap, Modifier.weight(LegendSideWeight))
        }

    }
}

/** Peso de las columnas de los lados de la leyenda respecto a la del centro (1). */
private const val LegendSideWeight = 1.25f

@Composable
private fun ReadinessLegendColumn(
    label: String,
    value: Int,
    color: Color,
    channel: RingChannel,
    onTap: (RingChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = "$value\u00A0%"
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("readiness-legend-${channel.name}")
            .semantics { contentDescription = "$label $percent. Toca para ajustarlo" }
            .clickable(role = Role.Button) { onTap(channel) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Eyebrow en el color de su anillo y cifra en Syne con espacio inseparable (LR1b).
        Text(
            text = label.uppercase(Locale.forLanguageTag("es")),
            // 12 sp con menos aire entre letras: con la letra grande «MÚSCULOS» y «ENERGÍA» no se tocan.
            style = KpknType.eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.sp),
            color = WizardColors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        Text(
            text = percent,
            style = KpknType.metricL.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = WizardColors.text,
            maxLines = 1,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Molestias en línea (G1)
// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Las molestias elegidas que no son de las anotadas antes: se fijan al principio de la lista para que ninguna quede oculta tras
 * «Más (N)». «Ninguna» no cuenta (no es una molestia).
 */
internal fun readinessPinnedChoices(selected: List<String>, reportedIds: Set<String>): List<String> =
    selected.filter { it != "none" && it !in reportedIds }.distinct()

/** El orden de los chips tras «Ninguna»: las anotadas antes, las fijadas y, al final, las sugeridas; cada molestia sale una sola vez. */
internal fun readinessChipOrder(reportedIds: List<String>, pinned: List<String>, suggestedIds: List<String>): List<String> =
    (reportedIds + pinned + suggestedIds).distinct()

/**
 * Cuántas filas ocupan unos chips de anchos [widths] (px) puestos uno tras otro, con [spacing] entre ellos, en un ancho de [maxWidth]:
 * la misma cuenta que hace `FlowRow` (un chip pasa a la fila de abajo si no cabe en la actual; uno más ancho que la fila ocupa una).
 */
internal fun readinessChipRows(widths: List<Int>, maxWidth: Int, spacing: Int): Int {
    var rows = 0
    var used = 0
    widths.forEach { raw ->
        val width = raw.coerceAtMost(maxWidth)
        if (rows == 0) {
            rows = 1
            used = width
        } else if (used + spacing + width <= maxWidth) {
            used += spacing + width
        } else {
            rows++
            used = width
        }
    }
    return rows
}

/**
 * Cuántos de los chips de [widths] se ven de entrada: todos si caben en [maxRows] filas y, si no, los que dejan sitio al chip
 * «Más (N)» (de ancho [moreWidth] según cuántos quedan ocultos) al final de la última fila.
 */
internal fun readinessVisibleChips(
    widths: List<Int>,
    maxWidth: Int,
    spacing: Int,
    maxRows: Int,
    moreWidth: (hidden: Int) -> Int,
): Int {
    if (readinessChipRows(widths, maxWidth, spacing) <= maxRows) return widths.size
    for (visible in widths.size - 1 downTo 1) {
        val withMore = widths.take(visible) + moreWidth(widths.size - visible)
        if (readinessChipRows(withMore, maxWidth, spacing) <= maxRows) return visible
    }
    return 1
}

/** Un chip de molestia ya resuelto: lo que dice, si está elegido, su punto de color (o ninguno) y qué hace al tocarlo. */
private class DiscomfortChipSpec(
    val id: String,
    val text: String,
    val selected: Boolean,
    val dot: Color?,
    val onClick: () -> Unit,
)

@Composable
private fun ReadinessDiscomfortBlock(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    reported: List<ReadinessReportedDiscomfort>,
    initialSelected: List<String>,
    sessionPillarMuscleIds: List<String>,
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(sessionPillarMuscleIds) { DiscomfortSuggestionEngine.suggestForMuscles(sessionPillarMuscleIds) }
    val results = remember(query) { if (query.isBlank()) emptyList() else DiscomfortSearch.search(query).map { it.entry } }
    val reportedIds = remember(reported) { reported.map { it.id }.toSet() }
    // Las elegidas fuera de las anotadas se fijan al principio al salir de la búsqueda y al plegar «Más»; tocando en la lista no se mueve nada.
    var pinned by remember { mutableStateOf(readinessPinnedChoices(selected + initialSelected, reportedIds)) }

    fun toggle(id: String) {
        onSelectedChange(if (id in selected) selected - id else selected + id)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "¿Alguna molestia hoy?".uppercase(Locale.forLanguageTag("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        ReadinessSearchField(
            value = query,
            onValueChange = { next ->
                if (next.isBlank() && query.isNotBlank()) pinned = readinessPinnedChoices(selected, reportedIds)
                query = next
            },
        )
        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Text(
                    text = "Sin resultados para «${query.trim()}»",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                DiscomfortChipFlow(
                    chips = results.map { entry ->
                        DiscomfortChipSpec(
                            id = entry.id,
                            text = entry.label,
                            selected = entry.id in selected,
                            dot = if (entry.id in reportedIds) ReportedDot else KpknRingColors.energia,
                            onClick = { toggle(entry.id) },
                        )
                    },
                    foldable = false,
                    expanded = true,
                    onExpandedChange = {},
                )
            }
        } else {
            val order = remember(reported, pinned, suggestions) {
                readinessChipOrder(
                    reportedIds = reported.map { it.id },
                    pinned = pinned,
                    suggestedIds = suggestions.map { it.id },
                )
            }
            val chips = buildList {
                add(
                    DiscomfortChipSpec(
                        id = "none",
                        text = "Ninguna",
                        selected = selected.isEmpty(),
                        dot = null,
                        onClick = { onSelectedChange(emptyList()) },
                    ),
                )
                order.forEach { id ->
                    val item = reported.firstOrNull { it.id == id }
                    add(
                        DiscomfortChipSpec(
                            id = id,
                            text = if (item != null) readinessReportedChipText(item) else discomfortLabel(id),
                            selected = id in selected,
                            dot = if (item != null) ReportedDot else KpknRingColors.energia,
                            onClick = { toggle(id) },
                        ),
                    )
                }
            }
            DiscomfortChipFlow(
                chips = chips,
                foldable = true,
                expanded = expanded,
                onExpandedChange = { open ->
                    // Al plegar, las elegidas se fijan arriba: ninguna queda oculta tras «Más».
                    if (!open) pinned = readinessPinnedChoices(selected, reportedIds)
                    expanded = open
                },
            )
        }
    }
}

/** Separación entre chips de molestias y medidas de cada chip (las usa el chip y la cuenta de filas). */
private val DiscomfortChipSpacing = 8.dp
private val DiscomfortChipPadStart = 14.dp
private val DiscomfortChipPadStartWithDot = 5.dp
private val DiscomfortChipPadEnd = 12.dp
private val DiscomfortChipDotSize = 20.dp
private val DiscomfortChipDotGap = 6.dp

/**
 * Los chips de molestias en filas. Con [foldable], de entrada solo [DiscomfortRowsCollapsed] filas y, tras el último chip, «Más (N)»
 * (N: los que quedan fuera), que los despliega todos con «Menos» al final para volver a plegarlos. Se cuenta en filas y no en chips:
 * con la letra grande caben menos por fila y el resto de la hoja no se aleja. Los chips ocultos no se componen: no llegan ni al
 * lector de pantalla.
 *
 * El ancho de cada chip se mide con el mismo estilo y las mismas medidas que el chip, para que la cuenta de filas coincida con la que
 * hace `FlowRow` al colocarlos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscomfortChipFlow(
    chips: List<DiscomfortChipSpec>,
    foldable: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val spacingPx = with(density) { DiscomfortChipSpacing.roundToPx() }
        val keys = chips.map { it.text to (it.dot != null) }
        val widths = remember(keys, density, measurer) { keys.map { (text, dot) -> discomfortChipWidth(measurer, density, text, dot) } }
        val folds = foldable && readinessChipRows(widths, maxWidthPx, spacingPx) > DiscomfortRowsCollapsed
        val visible = if (!folds || expanded) {
            chips.size
        } else {
            readinessVisibleChips(widths, maxWidthPx, spacingPx, DiscomfortRowsCollapsed) { hidden ->
                discomfortChipWidth(measurer, density, "Más ($hidden)", false)
            }
        }
        val hidden = chips.size - visible
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DiscomfortChipSpacing)) {
            chips.take(visible).forEach { chip ->
                key(chip.id) {
                    DiscomfortInlineChip(
                        text = chip.text,
                        selected = chip.selected,
                        dot = chip.dot,
                        onClick = chip.onClick,
                        modifier = Modifier.testTag("readiness-discomfort-${chip.id}"),
                    )
                }
            }
            if (hidden > 0) {
                DiscomfortInlineChip(
                    text = "Más ($hidden)",
                    selected = false,
                    dot = null,
                    checkable = false,
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier.testTag("readiness-discomfort-more"),
                )
            } else if (folds && expanded) {
                DiscomfortInlineChip(
                    text = "Menos",
                    selected = false,
                    dot = null,
                    checkable = false,
                    onClick = { onExpandedChange(false) },
                    modifier = Modifier.testTag("readiness-discomfort-less"),
                )
            }
        }
    }
}

/** El ancho (px) que ocupa un chip de molestia con ese texto: relleno, punto y texto en una línea, como lo dibuja [DiscomfortInlineChip]. */
private fun discomfortChipWidth(measurer: TextMeasurer, density: Density, text: String, hasDot: Boolean): Int {
    val textWidth = measurer.measure(text = text, style = KpknType.chip, softWrap = false, maxLines = 1).size.width
    return with(density) {
        val start = (if (hasDot) DiscomfortChipPadStartWithDot else DiscomfortChipPadStart).roundToPx()
        val dot = if (hasDot) DiscomfortChipDotSize.roundToPx() + DiscomfortChipDotGap.roundToPx() else 0
        start + dot + textWidth + DiscomfortChipPadEnd.roundToPx()
    }
}

/** Buscador de molestias (G1): 40 dp, píldora `#161616` con filete, lupa y marcador «Busca o elige abajo». */
@Composable
private fun ReadinessSearchField(value: String, onValueChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(SearchFill, shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = KpknLineIcons.Search,
            contentDescription = null,
            tint = WizardColors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "Busca o elige abajo",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = KpknType.caption.copy(color = WizardColors.text),
                cursorBrush = SolidColor(WizardColors.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("readiness-discomfort-search")
                    .semantics { contentDescription = "Buscar molestia" },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button) { onValueChange("") }
                    .semantics { contentDescription = "Limpiar búsqueda" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Close,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Chip de una molestia (G1): 32 dp de alto sobre `inset`, con un punto de color delante si [dot] (rojo: la anotaste antes; amarillo:
 * la sugerimos); elegido, en tinta con texto negro. Su zona táctil mide 48 dp y es una casilla (`Role.Checkbox`); con [checkable] en
 * falso (los de «Más» y «Menos») es un botón.
 */
@Composable
private fun DiscomfortInlineChip(
    text: String,
    selected: Boolean,
    dot: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checkable: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink = if (selected) WizardColors.ctaContent else WizardColors.text
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(
                if (checkable) {
                    Modifier.toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        interactionSource = interaction,
                        indication = null,
                        onValueChange = { onClick() },
                    )
                } else {
                    Modifier.clickable(role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(if (selected) WizardColors.text else KpknSurface.inset, KpknShape.pill)
                .padding(start = if (dot != null) DiscomfortChipPadStartWithDot else DiscomfortChipPadStart, end = DiscomfortChipPadEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(
                    modifier = Modifier
                        .size(DiscomfortChipDotSize)
                        .clip(CircleShape)
                        .background(dot.copy(alpha = if (selected) 0.30f else 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot, CircleShape))
                }
                Spacer(Modifier.width(DiscomfortChipDotGap))
            }
            Text(
                text = text,
                style = KpknType.chip,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// La voz en una fila (G1)
// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * La voz (G1): «VOZ» y, a su derecha, la píldora «Comandos de voz» que enciende o apaga el control por voz, el (i) que explica cómo
 * funciona y, con la voz encendida, las píldoras de modo «Manos libres» y «Música». Con poco ancho, los modos pasan a la línea de
 * abajo. Pulsar el modo ya elegido no lo cambia, pero abre su explicación, como siempre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessVoiceRow(
    enabled: Boolean,
    mode: VoiceCaptureMode,
    onToggle: () -> Unit,
    onModeChange: (VoiceCaptureMode) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var modeInfo by remember { mutableStateOf<VoiceCaptureMode?>(null) }
    var blockHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { blockHeight = it.height },
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = "Voz".uppercase(Locale.forLanguageTag("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            VoicePill(
                text = "Comandos de voz",
                selected = enabled,
                role = Role.Switch,
                withMic = true,
                onClick = onToggle,
                modifier = Modifier.testTag("readiness-voice-toggle"),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button) { showInfo = true }
                    .semantics { contentDescription = "Cómo funciona la voz" }
                    .testTag("readiness-voice-info"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Info,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (enabled) {
                VoicePill(
                    text = "Manos libres",
                    selected = mode == VoiceCaptureMode.HANDS_FREE,
                    role = Role.RadioButton,
                    withMic = false,
                    onClick = {
                        // Pulsar el modo ya elegido no lo cambia, pero sí abre su explicación (como antes).
                        if (mode != VoiceCaptureMode.HANDS_FREE) onModeChange(VoiceCaptureMode.HANDS_FREE)
                        modeInfo = VoiceCaptureMode.HANDS_FREE
                    },
                    modifier = Modifier.testTag("readiness-voice-mode-hands-free"),
                )
                VoicePill(
                    text = "Música",
                    selected = mode == VoiceCaptureMode.MUSIC,
                    role = Role.RadioButton,
                    withMic = false,
                    onClick = {
                        if (mode != VoiceCaptureMode.MUSIC) onModeChange(VoiceCaptureMode.MUSIC)
                        modeInfo = VoiceCaptureMode.MUSIC
                    },
                    modifier = Modifier.testTag("readiness-voice-mode-music"),
                )
            }
        }
    }

    val popupOffset = IntOffset(0, blockHeight + with(density) { 8.dp.toPx() }.toInt())
    if (showInfo) {
        Popup(
            alignment = Alignment.TopStart,
            offset = popupOffset,
            properties = PopupProperties(focusable = true),
            onDismissRequest = { showInfo = false },
        ) {
            VoiceInfoCard(
                title = "¿Cómo funciona la voz?",
                body = "Controla tu sesión hablando: registra series, " +
                    "cambia pesos y gestiona descansos con comandos de voz. " +
                    "La voz estará activa durante toda la sesión.",
                example = "Ejemplos: “2 series más”, “sube a 80 kg”, “pausa de 90 segundos”.",
                onDismiss = { showInfo = false },
            )
        }
    }
    modeInfo?.takeIf { enabled }?.let { picked ->
        val (title, body) = when (picked) {
            VoiceCaptureMode.HANDS_FREE ->
                "Modo manos libres" to
                    "Registra tus series con el micrófono de tus auriculares, incluso por bluetooth, " +
                    "sin acercarte al celular. La contra: el audio suena con calidad de llamada " +
                    "y degrada la música de fondo."
            VoiceCaptureMode.MUSIC ->
                "Modo música" to
                    "Usa el micrófono del celular, no el de tus auriculares: tu música se escucha " +
                    "perfecta. Solo necesitas tener el teléfono cerca (sin desbloquearlo) para " +
                    "registrar tus series por voz."
        }
        Popup(
            alignment = Alignment.TopStart,
            offset = popupOffset,
            properties = PopupProperties(focusable = true),
            onDismissRequest = { modeInfo = null },
        ) {
            VoiceInfoCard(title = title, body = body, example = null, onDismiss = { modeInfo = null })
        }
    }
}

/** Píldora de la fila de voz: 34 dp, con filete de tinta si está activa; zona táctil de 48 dp. */
@Composable
private fun VoicePill(
    text: String,
    selected: Boolean,
    role: Role,
    withMic: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val edge = if (selected) WizardColors.text else Color.White.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = selected,
                role = role,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(KpknShape.pill)
                .background(if (selected) WizardColors.text.copy(alpha = 0.06f) else Color.Transparent, KpknShape.pill)
                .border(1.dp, edge, KpknShape.pill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (withMic) {
                Icon(
                    imageVector = KpknLineIcons.Mic,
                    contentDescription = null,
                    tint = if (selected) WizardColors.text else WizardColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = KpknType.chip,
                color = if (selected) WizardColors.text else WizardColors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/** Tarjeta de explicación de la voz o de un modo: módulo de la piel nueva con «Entendido» (compacto). */
@Composable
private fun VoiceInfoCard(
    title: String,
    body: String,
    example: String?,
    onDismiss: () -> Unit,
) {
    KpknModule(
        modifier = Modifier.width(300.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Mic,
                    contentDescription = null,
                    tint = WizardColors.text,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
            }
            Text(text = body, style = KpknType.body, color = WizardColors.textMuted)
            if (example != null) {
                Text(text = example, style = KpknType.caption, color = WizardColors.textFaint)
            }
            KpknCompactPill(
                text = "Entendido",
                onClick = onDismiss,
                fullWidth = true,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// El resto de piezas que se conservan
// ──────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AdjustableRingCompact(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    ringColor: Color,
    ringSize: Int,
    onValueChange: (Int) -> Unit,
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableIntStateOf(value) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .size(ringSize.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "$value por ciento"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 100)
                    setProgress { target ->
                        onValueChange(target.toInt().coerceIn(0, 100))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumulator = 0f
                            dragStartValue = value
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += -dragAmount.y
                            val step = (dragAccumulator / 1.25f).toInt()
                            onValueChange((dragStartValue + step).coerceIn(0, 100))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ReadinessRingVisual(value = value, color = ringColor)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = ringColor,
                    fontSize = 20.sp,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ringColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }

        Text(
            "Arrastrar verticalmente",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun ReadinessRingVisual(
    value: Int,
    color: Color,
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "readinessRing",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 6.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = radius,
            center = center,
            style = Stroke(strokePx),
        )

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedValue,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(strokePx),
        )
    }
}

/**
 * Botón ✓ de confirmar y entrenar (LR1b): círculo de 64 dp en tinta con la marca en negro, como el ✓ de la serie (LR1a). No reutiliza
 * [WorkoutRecordFab] porque ese registra la serie y su etiqueta sería otra.
 */
@Composable
private fun ReadinessConfirmButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .shadow(10.dp, CircleShape, clip = false)
            .size(64.dp)
            .clip(CircleShape)
            .background(WizardColors.text)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Confirmar y entrenar" }
            .testTag("readiness-confirm"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = KpknLineIcons.Check,
            contentDescription = null,
            tint = WizardColors.ctaContent,
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun getSessionPillarMuscleIds(exercises: List<Exercise>): List<String> {
    val pillars = linkedSetOf<String>()
    fun collect(exercise: Exercise) {
        val info = resolveExercise(exercise.resolvedCanonicalExerciseId())
            ?: resolveExercise(exercise.exerciseDbId ?: exercise.exerciseId)
            ?: return
        SessionMuscleFilter.relevantMusclesFor(info)
            .forEach { involvement ->
                getAugeMusclePillarId(involvement.muscle, involvement.emphasis)
                    .takeIf { it.isNotBlank() }
                    ?.let(pillars::add)
            }
    }
    exercises.forEach(::collect)
    return pillars.toList()
}
