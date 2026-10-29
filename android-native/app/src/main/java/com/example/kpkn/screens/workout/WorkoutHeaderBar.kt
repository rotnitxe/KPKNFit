package com.example.kpkn.screens.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import kotlinx.coroutines.delay
import com.example.kpkn.screens.sessioneditor.sessionCoverAccentColor
import com.example.kpkn.ui.components.kpknCoverGlass
import com.example.kpkn.ui.components.kpknCoverGlassOrFallback
import com.example.kpkn.ui.components.kpknFloatingSurface
import com.example.kpkn.services.workout.VoiceSessionState
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import coil.compose.AsyncImage
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.parseProgramStudioCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.restyled
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.components.HeroCoverKind
import com.example.kpkn.screens.programdetail.components.heroCoverKind
import com.example.kpkn.ui.components.rememberProgramCover

private const val HeaderCompactScale = 0.80f

/** Decide si el título del ejercicio debe usar el carrusel continuo lento (LV1 punto 1). */
internal fun workoutHeaderShouldUseMarquee(
    textWidth: Int,
    containerWidth: Int,
    reducedMotion: Boolean,
): Boolean = textWidth > containerWidth && !reducedMotion

/** Visible glyphs in the header title (name + " · " chips). */
internal fun workoutHeaderTitleGlyphCount(exerciseName: String, chips: List<String>): Int =
    exerciseName.length + chips.sumOf { 3 + it.length }

/**
 * Shrinks the header title with length so it stays on one line.
 * The header slot height never grows with the exercise name.
 */
internal fun workoutHeaderTitleFontScale(glyphCount: Int): Float = when {
    glyphCount <= 22 -> 1.00f
    glyphCount <= 32 -> 0.88f
    glyphCount <= 44 -> 0.76f
    glyphCount <= 58 -> 0.66f
    else -> 0.58f
}

/** Escala del visor (pantalla estrecha o baja). Vale 1 en el formato de referencia. */
@Composable
private fun headerViewportScale(): Float {
    val adapt = LocalViewportAdapt.current
    val policy = LiveViewportPolicyMath.compute(
        widthDp = adapt.widthDp,
        heightDp = adapt.heightDp,
    )
    return adapt.uniformScale * policy.headerMul
}

/** Escala compacta de la cabecera: espaciado y elementos de tamaño fijo, como antes. */
@Composable
private fun headerCompactScale(): Float = HeaderCompactScale * headerViewportScale()

/** Texto de las píldoras de la cabecera: Inter 12 (cifras SemiBold, etiquetas Medium), escalado solo por el visor. */
private fun headerChipStyle(weight: FontWeight, scale: Float): TextStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = weight,
    fontSize = 12.sp * scale,
    lineHeight = 16.sp * scale,
)

/** Escala el tamaño y el interlineado de un estilo por [k]. */
private fun TextStyle.scaledBy(k: Float): TextStyle = copy(
    fontSize = fontSize * k,
    lineHeight = lineHeight * k,
)

private val PaceChipGreen = Color(0xFF66BB6A)
private val PaceChipRed = Color(0xFFFF5252)
private val PaceChipNeutral = WizardColors.text

/** Rojo de señal de la cabecera (Superserie, Adaptar): va como texto sobre la píldora inset. */
private val HeaderSignalRed = Color(0xFFFF8A80)

private sealed class PendingSessionTimeChange {
    data class SetMinutes(val minutes: Int) : PendingSessionTimeChange()
    data object Clear : PendingSessionTimeChange()
}

internal fun sessionTimeChipAccent(
    isExceeded: Boolean,
    coachPaceAlert: String?,
): Color = when {
    isExceeded || coachPaceAlert == "excedido" -> PaceChipRed
    coachPaceAlert == "retrasado" || coachPaceAlert == "apurar" -> PaceChipRed
    coachPaceAlert == "adelantado" -> PaceChipGreen
    else -> PaceChipNeutral
}

internal fun resolveSessionAccentColor(background: SessionBackground?): Color {
    return sessionCoverAccentColor(background)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutChronometer(
    startTimeMs: Long,
    isComplete: Boolean,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    onSetAbsoluteTimeLimit: (totalMinutes: Int, persistToSession: Boolean) -> Unit = { minutes, _ ->
        onAdjustTimeLimit(minutes - ((sessionTimeRemainingSeconds ?: 0) / 60).coerceAtLeast(0))
    },
    onClearTimeLimit: (persistToSession: Boolean) -> Unit = {},
    pacingAlertMode: PacingAlertMode = PacingAlertMode.FINAL,
    onPacingAlertModeChange: (PacingAlertMode) -> Unit = {},
    currentTargetMinutes: Int? = null,
    sessionHasProgrammedTime: Boolean = false,
    completedWorkingSetCount: Int = 0,
    totalWorkingSetCount: Int = 0,
    pacingAlertMessage: String? = null,
    coachPaceAlert: String? = null,
    onUltraFastPreview: (() -> Unit)? = null,
    ultraFastApplied: Boolean = false,
    ultraFastSavedSeconds: Int = 0,
    onRevertUltraFast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    openSessionTimeRequest: Int = 0,
) {
    var elapsedSeconds by remember(startTimeMs) { androidx.compose.runtime.mutableIntStateOf(0) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingChange by remember { mutableStateOf<PendingSessionTimeChange?>(null) }

    val chronometerOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(startTimeMs, isComplete, chronometerOwner) {
        if (startTimeMs <= 0) return@LaunchedEffect
        fun updateClock() {
            elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        }
        updateClock()
        if (!isComplete) chronometerOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) { updateClock(); delay(1000L) }
        }
    }

    LaunchedEffect(showAdjustDialog) {
        if (!showAdjustDialog) pendingChange = null
    }

    LaunchedEffect(openSessionTimeRequest) {
        if (openSessionTimeRequest > 0) showAdjustDialog = true
    }

    val hasLimit = sessionTimeRemainingSeconds != null
    val displayRemaining = sessionTimeRemainingSeconds ?: 0
    val isExceeded = hasLimit && (
        pacingAlertMessage == SessionTimeCues.EXHAUSTED ||
            coachPaceAlert == "excedido" ||
            (currentTargetMinutes?.takeIf { it > 0 }?.let { elapsedSeconds >= it * 60 } == true)
    )
    val liveTargetMinutes = currentTargetMinutes?.takeIf { it > 0 }
    val resolvedTargetMinutes = liveTargetMinutes
        ?: if (hasLimit) {
            ((elapsedSeconds + displayRemaining.coerceAtLeast(0)) / 60).coerceAtLeast(5)
        } else {
            60
        }

    val text = buildString {
        append("%d:%02d".format(java.util.Locale.getDefault(), elapsedSeconds / 60, elapsedSeconds % 60))
    }

    val accent = sessionTimeChipAccent(isExceeded = isExceeded, coachPaceAlert = coachPaceAlert)
    val countdownMessage = pacingAlertMessage?.takeIf { it in SessionTimeCues.ALL }

    fun closeOverlay() {
        pendingChange = null
        showAdjustDialog = false
        showTimePicker = false
    }

    fun applyPending(persistToSession: Boolean) {
        when (val pending = pendingChange) {
            is PendingSessionTimeChange.SetMinutes -> onSetAbsoluteTimeLimit(pending.minutes, persistToSession)
            PendingSessionTimeChange.Clear -> onClearTimeLimit(persistToSession)
            null -> Unit
        }
        closeOverlay()
    }

    fun proposeMinutes(minutes: Int) {
        val clamped = minutes.coerceAtLeast(5)
        if (liveTargetMinutes == clamped) {
            pendingChange = null
            return
        }
        pendingChange = PendingSessionTimeChange.SetMinutes(clamped)
    }

    fun proposeClear() {
        if (!hasLimit) return
        if (sessionHasProgrammedTime) {
            pendingChange = PendingSessionTimeChange.Clear
        } else {
            onClearTimeLimit(false)
            closeOverlay()
        }
    }

    val pendingMinutes = (pendingChange as? PendingSessionTimeChange.SetMinutes)?.minutes
    val selectedPreset = pendingMinutes ?: liveTargetMinutes

    // `.timer-pill` del prototipo 02: 40 dp de alto, radio 19, blanco al 3 %, relleno 9, icono de cronómetro de 15 dp y el tiempo
    // transcurrido en tinta (11 sp, 600). El objetivo, el ritmo y los avisos viven en la hoja «Tiempo de sesión»; la píldora solo
    // tiñe el icono cuando el ritmo pide atención (excedido, retrasado o adelantado).
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0x07FFFFFF))
            .clickable(onClickLabel = "Tiempo de la sesión") { showAdjustDialog = true }
            .semantics { contentDescription = "Tiempo de la sesión: $text" + if (hasLimit) " de ${liveTargetMinutes ?: resolvedTargetMinutes} minutos" else "" }
            .height(40.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = KpknLineIcons.Timer,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (accent == PaceChipNeutral) WizardColors.text else accent,
        )
        Text(
            text = text,
            style = KpknType.action.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = WizardColors.text,
            maxLines = 1,
            softWrap = false,
        )
    }

    if (showAdjustDialog) {
        com.example.kpkn.screens.workout.components.SessionTimeSheet(
            elapsedSeconds = elapsedSeconds, startTimeMs = startTimeMs,
            targetMinutes = liveTargetMinutes ?: resolvedTargetMinutes.takeIf { hasLimit }, accent = accent,
            completedWorkingSets = completedWorkingSetCount, totalWorkingSets = totalWorkingSetCount,
            selectedMinutes = selectedPreset,
            pendingDescription = when (val pending = pendingChange) {
                is PendingSessionTimeChange.SetMinutes -> "Objetivo: ${pending.minutes} min. ¿Cómo aplicarlo?"
                PendingSessionTimeChange.Clear -> "Quitar el objetivo. ¿Cómo aplicarlo?"
                null -> null
            },
            onProposeMinutes = ::proposeMinutes, onAssignTime = { showTimePicker = true },
            onClear = ::proposeClear, onApplyPending = ::applyPending,
            alertMode = pacingAlertMode, onAlertMode = onPacingAlertModeChange,
            ultraFastApplied = ultraFastApplied, ultraFastSavedSeconds = ultraFastSavedSeconds,
            onUltraFastPreview = onUltraFastPreview?.let { callback -> { closeOverlay(); callback() } },
            onRevertUltraFast = onRevertUltraFast?.let { callback -> { closeOverlay(); callback() } },
            onDismiss = ::closeOverlay,
        )
    }

    if (showTimePicker) {
        val pickerMinutes = pendingMinutes ?: resolvedTargetMinutes
        val hours = pickerMinutes / 60
        val minutes = pickerMinutes % 60
        com.example.kpkn.ui.components.KpknNativeTimePickerDialog(
            title = "Duración de sesión",
            initialHour = hours.coerceIn(0, 23),
            initialMinute = minutes.coerceIn(0, 59),
            hint = "Horas : minutos",
            onConfirm = { h, m ->
                val total = (h * 60 + m).coerceAtLeast(5)
                showTimePicker = false
                proposeMinutes(total)
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
internal fun WorkoutHeaderBar(
    exerciseName: String,
    exerciseChips: List<String> = emptyList(),
    sessionName: String,
    groupName: String?,
    startTimeMs: Long,
    isComplete: Boolean,
    // La portada ya no pinta degradado; el parámetro se conserva para el llamador.
    @Suppress("UNUSED_PARAMETER") background: SessionBackground?,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    onSetAbsoluteTimeLimit: (totalMinutes: Int, persistToSession: Boolean) -> Unit = { _, _ -> },
    onClearTimeLimit: (persistToSession: Boolean) -> Unit = {},
    pacingAlertMode: PacingAlertMode = PacingAlertMode.FINAL,
    onPacingAlertModeChange: (PacingAlertMode) -> Unit = {},
    currentTargetMinutes: Int? = null,
    sessionHasProgrammedTime: Boolean = false,
    completedWorkingSetCount: Int = 0,
    totalWorkingSetCount: Int = 0,
    totalExerciseCount: Int = 0,
    pacingAlertMessage: String? = null,
    coachPaceAlert: String? = null,
    exerciseTag: String? = null,
    isSuperset: Boolean = false,
    activeMainTags: List<WorkoutTag> = emptyList(),
    activeMainTagLabels: Map<String, String> = emptyMap(),
    activeSubTags: List<WorkoutSubTag> = emptyList(),
    hasCreatedTags: Boolean = false,
    onTagClick: (String) -> Unit = {},
    onRemoveSubTag: (String) -> Unit = {},
    onOpenTagList: () -> Unit = {},
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode? = null,
    onVoiceCaptureModeChange: ((com.example.kpkn.data.models.VoiceCaptureMode) -> Unit)? = null,
    voiceSessionEnabled: Boolean = false,
    voiceSessionState: VoiceSessionState = VoiceSessionState(),
    onToggleVoice: (() -> Unit)? = null,
    onUltraFastPreview: (() -> Unit)? = null,
    ultraFastApplied: Boolean = false,
    ultraFastSavedSeconds: Int = 0,
    onRevertUltraFast: (() -> Unit)? = null,
    onAdaptClick: (() -> Unit)? = null,
    godModeActive: Boolean = false,
    onStoryClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onReplaceClick: (() -> Unit)? = null,
    nicknameKey: String? = null,
    nicknameValue: String = "",
    canonicalExerciseName: String = exerciseName,
    onNicknameChange: ((String) -> Unit)? = null,
    onCreateSupersetClick: (() -> Unit)? = null,
    bodyHazeState: HazeState? = null,
    protocolLabel: String? = null,
    programCover: String? = null,
    program: Program? = null,
    forcedMarqueeMoving: Boolean = false,
    compactLayout: Boolean? = null,
    /** En la vista Lista el contexto del ejercicio va dentro de la lista (prototipo 02) y la cabecera queda solo con la isla. */
    showExerciseContext: Boolean = true,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    /** Cada incremento abre «Tiempo de sesión» (lo pide el menú ⋯ de la sesión). */
    sessionTimeRequest: Int = 0,
) {
    val headerScale = headerCompactScale()
    val typeScale = headerViewportScale()
    val compactHeader = compactLayout ?: (LocalConfiguration.current.screenHeightDp < 800)
    fun cs(dp: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = dp * headerScale

    val density = LocalDensity.current
    var headerContentHeightPx by remember { mutableIntStateOf(0) }

    // Refinamiento 02: la cabecera no pinta fondo propio (ni portada, ni relleno, ni degradado). Solo flota la cápsula de vidrio sobre la
    // sesión, como en el prototipo; lo que pasa por debajo se ve a través del vidrio.
    Box(modifier = Modifier.fillMaxWidth().zIndex(8f)) {
        // LV-C: en la cabecera compacta los botones de icono conservan 44 dp de layout; Compose amplía su zona de toque a los 48 dp
        // mínimos (se mide con touchBoundsInRoot en LiveHeaderTouchTargetsTest). Así el relator no pierde los 4 dp de alto que le
        // quitaba la fila, sobre todo a 740 dp con letra al 130 %. Los chips tampoco reservan 48 dp de layout.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (compactHeader) 44.dp else LocalMinimumInteractiveComponentSize.current) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerContentHeightPx = it.height }
                .windowInsetsPadding(statusBarInsets)
                .padding(start = 10.dp, end = 10.dp, top = if (compactHeader) 0.dp else 10.dp, bottom = if (compactHeader) 0.dp else 7.dp)
                .testTag("live-session-header"),
            verticalArrangement = Arrangement.spacedBy(if (compactHeader) 0.dp else 3.dp),
        ) {
            var openSessionTimeRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(sessionTimeRequest) { if (sessionTimeRequest > 0) openSessionTimeRequest++ }
            // Isla del prototipo 02 (`.phone > .island`): 60 dp de alto (53 en pantallas bajas), radio 30, relleno 15 / 8, degradado
            // gris a 135° con borde blanco al 13 %, reflejo interior arriba y sombra. Título y subtítulo a la izquierda, en una línea;
            // el cronómetro y «⋯» a la derecha, centrados en vertical.
            val capsuleShape = RoundedCornerShape(30.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compactHeader) 2.dp else 8.dp, bottom = cs(6.dp))
                    // Píldora flotante: gris oscuro plano con sombra real de elevación (pedido del usuario, 29-oct), sin vidrio.
                    .kpknFloatingSurface(capsuleShape, com.example.kpkn.ui.components.KpknFloating.PillElevation)
                    .heightIn(min = if (compactHeader) 53.dp else 60.dp)
                    .padding(start = 15.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    // Una línea cada una, como el prototipo 02 (`white-space: nowrap; text-overflow: ellipsis`): la isla no crece.
                    Text(
                        text = sessionName,
                        style = KpknType.action.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                        color = WizardColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$totalExerciseCount ejercicios · $completedWorkingSetCount de $totalWorkingSetCount series registradas",
                        modifier = Modifier.padding(top = 3.dp),
                        style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = WizardColors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
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
                        completedWorkingSetCount = completedWorkingSetCount,
                        totalWorkingSetCount = totalWorkingSetCount,
                        pacingAlertMessage = pacingAlertMessage,
                        coachPaceAlert = coachPaceAlert,
                        onUltraFastPreview = onUltraFastPreview,
                        ultraFastApplied = ultraFastApplied,
                        ultraFastSavedSeconds = ultraFastSavedSeconds,
                        onRevertUltraFast = onRevertUltraFast,
                        openSessionTimeRequest = openSessionTimeRequest,
                    )
                    onMoreClick?.let { more ->
                        KpknIconButton(KpknLineIcons.More, "Más opciones de sesión", more,
                            modifier = Modifier.size(44.dp).testTag("workout-more-header"))
                    }
                }
            }
            }
            // Contexto del ejercicio fuera de la cápsula (prototipo 02): nombre en Syne 18 y una sola línea de contexto. En la Lista lo
            // pinta la propia lista, para que se desplace con ella.
            if (showExerciseContext) {
                WorkoutExerciseContextBlock(
                    exerciseName = exerciseName,
                    exerciseChips = exerciseChips,
                    ultraFastApplied = ultraFastApplied,
                    onUltraFastClick = { openSessionTimeRequest++ },
                    isSuperset = isSuperset,
                    activeMainTags = activeMainTags,
                    activeMainTagLabels = activeMainTagLabels,
                    activeSubTags = activeSubTags,
                    onTagClick = onTagClick,
                    onOpenTagList = onOpenTagList,
                    onToggleVoice = onToggleVoice,
                    voiceSessionEnabled = voiceSessionEnabled,
                    modifier = Modifier.padding(start = 6.dp, top = if (compactHeader) 4.dp else 8.dp),
                )
            }
        }
        }
    }
}

/** Nombre del ejercicio en Syne 18, su línea de contexto (equipo · señales · etiqueta ⌄) y el micrófono (prototipo 02, `.context`). */
@Composable
internal fun WorkoutExerciseContextBlock(
    exerciseName: String,
    exerciseChips: List<String>,
    ultraFastApplied: Boolean,
    onUltraFastClick: () -> Unit,
    isSuperset: Boolean,
    activeMainTags: List<WorkoutTag>,
    activeMainTagLabels: Map<String, String>,
    activeSubTags: List<WorkoutSubTag>,
    onTagClick: (String) -> Unit,
    onOpenTagList: () -> Unit,
    onToggleVoice: (() -> Unit)?,
    voiceSessionEnabled: Boolean,
    modifier: Modifier = Modifier,
    /** En la Lista cada ejercicio ya lleva su cabecera: el título grande repetía el primero y se quedaba fijo, así que no se pinta. */
    showTitle: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Título del ejercicio más presente (pedido del usuario, 29-oct): 23 sp en lugar de 18.
            if (showTitle) Text(
                exerciseName,
                style = KpknType.title.copy(fontSize = 23.sp, lineHeight = 27.sp, letterSpacing = (-0.45).sp),
                color = WizardColors.text,
                modifier = Modifier.testTag("workout-header-exercise"),
            )
            WorkoutHeaderContextLine(
                exerciseChips = workoutHeaderVisibleChips(exerciseName, exerciseChips),
                ultraFastApplied = ultraFastApplied,
                onUltraFastClick = onUltraFastClick,
                isSuperset = isSuperset,
                activeMainTags = activeMainTags,
                activeMainTagLabels = activeMainTagLabels,
                activeSubTags = activeSubTags,
                onTagClick = onTagClick,
                onOpenTagList = onOpenTagList,
            )
        }
        onToggleVoice?.let { toggle ->
            // Micrófono sin relleno; activo, con el acento tenue del prototipo (.mic-on).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (voiceSessionEnabled) HeaderMicOnTint else Color.Transparent)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = toggle)
                    .semantics { contentDescription = if (voiceSessionEnabled) "Desactivar voz" else "Activar voz" }
                    .testTag("workout-voice-header"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    KpknLineIcons.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (voiceSessionEnabled) HeaderMicOnInk else WizardColors.textMuted,
                )
            }
        }
    }
}

/**
 * Los datos de la línea de contexto que el nombre del ejercicio no dice ya. «Barra» y «Alta» sobran en «Sentadilla Trasera con Barra
 * Alta»: un dato se omite si todas sus palabras están en el nombre (sin distinguir mayúsculas ni tildes).
 */
internal fun workoutHeaderVisibleChips(exerciseName: String, chips: List<String>): List<String> {
    fun words(text: String): List<String> = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }
    val nameWords = words(exerciseName).toSet()
    return chips.filter { chip -> words(chip).let { it.isEmpty() || !nameWords.containsAll(it) } }.filter { it.isNotBlank() }
}

/** Título de la cápsula (prototipo 02): «Pierna · Semana 1»; sin semana de programa, solo el nombre de la sesión. */
internal fun workoutHeaderSessionTitle(sessionName: String, weekInBlock: Int?): String =
    weekInBlock?.takeIf { it > 0 }?.let { "$sessionName · Semana $it" } ?: sessionName

private val HeaderMicOnInk = Color(0xFFF49A6E)
private val HeaderMicOnTint = Color(0x12F49A6E)

/** Texto de la etiqueta activa en la línea de contexto: la primera, «+n» por las demás; sin ninguna, «Etiquetas». */
internal fun workoutHeaderTagSummary(mainLabels: List<String>, subTagCount: Int): String {
    if (mainLabels.isEmpty()) return "Etiquetas"
    val extra = mainLabels.size - 1 + subTagCount
    return mainLabels.first() + if (extra > 0) " +$extra" else ""
}

/**
 * Línea de contexto bajo el nombre del ejercicio (prototipo 02): «Barra · Alta · Competición ⌄». La etiqueta abre su gestor (o la
 * lista si hay varias o ninguna). Superserie y ultrarrápido van en la misma línea con su color de señal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutHeaderContextLine(
    exerciseChips: List<String>,
    ultraFastApplied: Boolean,
    onUltraFastClick: () -> Unit,
    isSuperset: Boolean,
    activeMainTags: List<WorkoutTag>,
    activeMainTagLabels: Map<String, String>,
    activeSubTags: List<WorkoutSubTag>,
    onTagClick: (String) -> Unit,
    onOpenTagList: () -> Unit,
) {
    val meta = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp)
    // Iteración 04: sin datos que repitan el nombre ni «Etiquetas ⌄» vacío. Si no queda nada que decir, la línea no ocupa espacio
    // (las etiquetas siguen en las opciones del ejercicio y en el menú de la sesión).
    val hasTags = activeMainTags.isNotEmpty() || activeSubTags.isNotEmpty()
    if (exerciseChips.isEmpty() && !isSuperset && !ultraFastApplied && !hasTags) return
    @Composable fun FlowRowScope.Dot() = Text("·", modifier = Modifier.align(Alignment.CenterVertically), style = meta, color = WizardColors.textFaint)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).testTag("workout-header-context"),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        var first = true
        @Composable fun FlowRowScope.Separator() { if (!first) Dot(); first = false }
        exerciseChips.forEach { chip ->
            Separator()
            Text(chip, modifier = Modifier.align(Alignment.CenterVertically), style = meta, color = WizardColors.textFaint)
        }
        // El prototipo no pinta la superserie aquí: la dicen el kicker de la tarjeta («RONDA 2 · A1») y la Lista («SUPERSERIE A»).
        if (ultraFastApplied) {
            Separator()
            Text(
                "Ultrarrápido",
                modifier = Modifier.align(Alignment.CenterVertically).clickable(onClick = onUltraFastClick),
                style = meta,
                color = PaceChipGreen,
            )
        }
        if (!hasTags) return@FlowRow
        Separator()
        val label = workoutHeaderTagSummary(
            mainLabels = activeMainTags.map { activeMainTagLabels[it.id] ?: it.name },
            subTagCount = activeSubTags.size,
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .heightIn(min = 24.dp)
                .clip(KpknShape.pill)
                .clickable(onClickLabel = "Etiquetas del ejercicio") {
                    if (activeMainTags.size == 1) onTagClick(activeMainTags.first().id) else onOpenTagList()
                }
                .testTag("workout-header-tags"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, style = meta, color = WizardColors.textMuted, maxLines = 1)
            Icon(KpknLineIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(11.dp), tint = WizardColors.textFaint)
        }
    }
}

@Composable
private fun VoiceModeHeaderSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(if (selected) WizardColors.text else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp * headerCompactScale(), vertical = 5.dp * headerCompactScale()),
    ) {
        Text(
            text = label,
            color = if (selected) WizardColors.ctaContent else WizardColors.text,
            fontFamily = WizardFonts.body,
            fontSize = 10.sp * headerCompactScale(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
