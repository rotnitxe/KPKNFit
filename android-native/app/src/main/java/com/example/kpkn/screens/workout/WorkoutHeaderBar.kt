package com.example.kpkn.screens.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithContent
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
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import kotlinx.coroutines.delay
import com.example.kpkn.screens.sessioneditor.sessionCoverAccentColor
import com.example.kpkn.ui.components.kpknCoverGlass
import com.example.kpkn.services.workout.VoiceSessionState
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.data.models.SetAdjustmentSuggestion
import dev.chrisbanes.haze.HazeState
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
    val targetSeconds = (resolvedTargetMinutes * 60).coerceAtLeast(1)
    val ringProgress = if (hasLimit) {
        (displayRemaining.coerceAtLeast(0).toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val text = buildString {
        append("%d:%02d".format(java.util.Locale.getDefault(), elapsedSeconds / 60, elapsedSeconds % 60))
        if (hasLimit) append(" / " + (liveTargetMinutes ?: resolvedTargetMinutes) + "′")
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

    Row(
        modifier = modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset)
            .clickable { showAdjustDialog = true }
            .height(27.dp)
            .padding(horizontal = 9.dp * headerCompactScale(), vertical = 5.dp * headerCompactScale()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp * headerCompactScale()),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp * headerCompactScale())) {
            if (hasLimit) {
                CircularProgressIndicator(
                    progress = { ringProgress },
                    modifier = Modifier.size(18.dp * headerCompactScale()),
                    color = accent,
                    trackColor = WizardColors.textFaint.copy(alpha = 0.30f),
                    strokeWidth = 1.6.dp * headerCompactScale(),
                )
            }
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(11.dp * headerCompactScale()),
                tint = accent,
            )
        }
        Text(
            text = text,
            style = headerChipStyle(FontWeight.SemiBold, headerViewportScale()).copy(
                fontFeatureSettings = "tnum",
            ),
            color = accent,
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
    pacingAlertMessage: String? = null,
    coachPaceAlert: String? = null,
    exerciseTag: String? = null,
    isSuperset: Boolean = false,
    exerciseReadiness: ExerciseReadiness? = null,
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
    readinessAdjustment: SetAdjustmentSuggestion? = null,
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
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
) {
    val headerScale = headerCompactScale()
    val typeScale = headerViewportScale()
    val compactHeader = compactLayout ?: (LocalConfiguration.current.screenHeightDp < 800)
    fun cs(dp: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = dp * headerScale

    val density = LocalDensity.current
    var headerContentHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxWidth().zIndex(8f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (headerContentHeightPx > 0) {
                        Modifier.height(with(density) { headerContentHeightPx.toDp() })
                    } else {
                        Modifier.matchParentSize()
                    },
                )
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.White,
                            0.70f to Color.White,
                            1.00f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(KpknSurface.canvas),
            )
            // Portada del programa activo como fondo tenue (LV1 punto 13)
            val effectiveCover = program?.coverImage ?: programCover
            if (!effectiveCover.isNullOrBlank()) {
                val bundledCover = rememberProgramCover(programCoverEntryIdOf(effectiveCover), with(density) { 360.dp.roundToPx() })
                val coverKind = heroCoverKind(effectiveCover, hasBundledCover = bundledCover != null)
                val identity = remember(program, effectiveCover) {
                    if (program != null) {
                        coverIdentityForProgram(program)
                    } else {
                        val choice = parseProgramStudioCover(effectiveCover)
                        val base = coverIdentityFor("session_header_program", null)
                        if (choice != null) base.restyled(choice.style, choice.palette) else base
                    }
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("workout_header_cover_background")
                        .graphicsLayer { alpha = 0.18f },
                ) {
                    when {
                        coverKind == HeroCoverKind.REMOTE -> {
                            AsyncImage(
                                model = effectiveCover,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        coverKind == HeroCoverKind.BUNDLED && bundledCover != null -> {
                            Image(
                                bitmap = bundledCover,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        coverKind == HeroCoverKind.STUDIO || coverKind == HeroCoverKind.GRADIENT -> {
                            CoverStudioArt(
                                identity = identity,
                                modifier = Modifier.fillMaxSize(),
                                animate = false,
                                reduced = true,
                            )
                        }
                    }
                }
            }
            if (bodyHazeState != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .kpknCoverGlass(bodyHazeState, RectangleShape, withBorder = false),
                )
            }
        }

        // LV-C: en la cabecera compacta los botones de icono conservan 44 dp de layout; Compose amplía su zona de toque a los 48 dp
        // mínimos (se mide con touchBoundsInRoot en LiveHeaderTouchTargetsTest). Así el relator no pierde los 4 dp de alto que le
        // quitaba la fila, sobre todo a 740 dp con letra al 130 %. Los chips tampoco reservan 48 dp de layout.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (compactHeader) 44.dp else LocalMinimumInteractiveComponentSize.current) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerContentHeightPx = it.height }
                .windowInsetsPadding(statusBarInsets)
                .padding(start = 12.dp, end = 12.dp, top = if (compactHeader) 0.dp else 10.dp, bottom = if (compactHeader) 0.dp else 7.dp)
                .testTag("live-session-header"),
            verticalArrangement = Arrangement.spacedBy(if (compactHeader) 0.dp else 3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val titleGlyphs = workoutHeaderTitleGlyphCount(exerciseName, exerciseChips)
                    val titleLengthScale = workoutHeaderTitleFontScale(titleGlyphs)
                    val titleBase = KpknType.title.copy(fontSize = if (compactHeader) 18.sp else 20.sp, lineHeight = if (compactHeader) 20.sp else 24.sp)
                    val reducedMotion = wizardReducedMotion()
                    val titleAnnotated = remember(exerciseName, exerciseChips) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = WizardColors.text)) {
                                append(exerciseName)
                            }
                            exerciseChips.forEach { chip ->
                                append(" · ")
                                withStyle(SpanStyle(color = WizardColors.textMuted)) {
                                    append(chip)
                                }
                            }
                        }
                    }
                    val titleStyle = titleBase
                    val measurer = rememberTextMeasurer()
                    val textLayout = remember(titleAnnotated, titleStyle, measurer) {
                        measurer.measure(titleAnnotated, style = titleStyle, maxLines = 1, softWrap = false)
                    }
                    // Font metrics can exceed the nominal line height (notably the compact title).
                    val titleSlotHeight = with(density) {
                        maxOf(titleBase.lineHeight.toDp(), textLayout.size.height.toDp())
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(titleSlotHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        val containerWidthPx = constraints.maxWidth
                        val textWidthPx = textLayout.size.width
                        val useMarquee = workoutHeaderShouldUseMarquee(
                            textWidth = textWidthPx,
                            containerWidth = containerWidthPx,
                            reducedMotion = reducedMotion,
                        )

                        val titleOffset = remember(titleAnnotated, textWidthPx, containerWidthPx) { Animatable(0f) }
                        val overflowPx = (textWidthPx - containerWidthPx).coerceAtLeast(0).toFloat()
                        LaunchedEffect(useMarquee, overflowPx) {
                            titleOffset.snapTo(0f)
                            if (useMarquee) {
                                val travelMs = (overflowPx / (12f * density.density) * 1000).toInt().coerceAtLeast(1200)
                                while (true) {
                                    delay(2500)
                                    titleOffset.animateTo(overflowPx, tween(travelMs, easing = LinearEasing))
                                    delay(2500)
                                    titleOffset.animateTo(0f, tween(travelMs, easing = LinearEasing))
                                }
                            }
                        }
                        val isMoving = titleOffset.value > 0.5f
                        val effectiveMoving = isMoving || forcedMarqueeMoving

                        // Fundido de 16 dp en el borde derecho, y en el izquierdo mientras se mueve (LV1 punto 1)
                        val fadeModifier = if (useMarquee && effectiveMoving) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val fadePx = 16.dp.toPx()
                                    // Fundido derecho
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0f to Color.Black,
                                            1f to Color.Transparent,
                                            startX = (size.width - fadePx).coerceAtLeast(0f),
                                            endX = size.width,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    // Fundido izquierdo mientras se mueve
                                    if (effectiveMoving) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                1f to Color.Black,
                                                startX = 0f,
                                                endX = fadePx,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                }
                        } else Modifier

                        Box(Modifier.fillMaxWidth().clipToBounds().then(fadeModifier)) {
                            Text(
                                text = titleAnnotated,
                                modifier = Modifier
                                    .then(if (useMarquee && effectiveMoving) Modifier.wrapContentWidth(Alignment.Start, unbounded = true) else Modifier.fillMaxWidth())
                                    .graphicsLayer { translationX = -titleOffset.value }
                                    .then(if (useMarquee) Modifier.testTag("workout_header_title_marquee") else Modifier),
                                style = titleStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = if (useMarquee && effectiveMoving) TextOverflow.Clip else TextOverflow.Ellipsis,
                            )
                        }

                    }
                    Text(
                        text = buildString {
                            if (!protocolLabel.isNullOrBlank()) append("$protocolLabel · ")
                            if (!groupName.isNullOrBlank()) append("$groupName · ")
                            append(sessionName)
                        },
                        style = (if (compactHeader) KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp) else KpknType.caption).scaledBy(typeScale),
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (onStoryClick != null) {
                        KpknIconButton(
                            icon = KpknLineIcons.Book,
                            contentDescription = "Historia de la sesión",
                            onClick = onStoryClick,
                            modifier = Modifier.then(if (compactHeader) Modifier.size(44.dp) else Modifier).testTag("workout-story-header"),
                        )
                    }
                    onToggleVoice?.let { toggle ->
                        KpknIconButton(KpknLineIcons.Mic, if (voiceSessionEnabled) "Desactivar voz" else "Activar voz", toggle,
                            modifier = Modifier.then(if (compactHeader) Modifier.size(44.dp) else Modifier).testTag("workout-voice-header"), filled = voiceSessionEnabled)
                    }
                    onMoreClick?.let { more ->
                        KpknIconButton(KpknLineIcons.More, "Más opciones de sesión", more,
                            modifier = Modifier.then(if (compactHeader) Modifier.size(44.dp) else Modifier).testTag("workout-more-header"))
                    }
                }
            }
            // Fila de chips a todo el ancho de la cabecera, debajo del subtítulo: los botones de icono se quedan arriba (LR1a2).
            var openSessionTimeRequest by remember { mutableIntStateOf(0) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Máscara de alfa ANTES del desplazamiento (LR1a2): el fundido cae sobre la ventana visible. Con la máscara después,
                    // caía sobre el contenido entero, que llega fuera de la pantalla, y el chip que no cabe se cortaba con un borde recto.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = size.width - 24.dp.toPx(),
                                endX = size.width,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(cs(6.dp)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
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
                }

                // Low-score adaptation is narrated through the relator, not a percentage chip.
                if (ultraFastApplied) {
                    Surface(
                        onClick = { openSessionTimeRequest++ },
                        shape = KpknShape.pill,
                        color = KpknSurface.inset,
                    ) {
                        Text(
                            "ULTRARRÁPIDO",
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            style = headerChipStyle(FontWeight.Medium, typeScale),
                            color = PaceChipGreen,
                            maxLines = 1,
                        )
                    }
                }

                if (isSuperset) {
                    Surface(
                        shape = KpknShape.pill,
                        color = KpknSurface.inset,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(cs(3.dp)),
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = HeaderSignalRed,
                                modifier = Modifier.size(cs(12.dp)),
                            )
                            Text(
                                "Superserie",
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = HeaderSignalRed,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // Multi-tag chips (new system)
                activeMainTags.forEach { tag ->
                    Surface(
                        onClick = { onTagClick(tag.id) },
                        color = KpknSurface.inset,
                        shape = KpknShape.pill,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(cs(3.dp)),
                        ) {
                            Text(
                                text = activeMainTagLabels[tag.id] ?: tag.name,
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = WizardColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Editar",
                                modifier = Modifier.size(cs(12.dp)),
                                tint = WizardColors.textMuted,
                            )
                        }
                    }
                }
                activeSubTags.forEach { subTag ->
                    Surface(
                        onClick = { onRemoveSubTag(subTag.id) },
                        color = KpknSurface.inset,
                        shape = KpknShape.pill,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(cs(3.dp)),
                        ) {
                            Text(
                                text = subTag.name,
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = WizardColors.textMuted,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Quitar",
                                modifier = Modifier.size(cs(10.dp)),
                                tint = WizardColors.textFaint,
                            )
                        }
                    }
                }
                if (activeMainTags.isEmpty()) {
                    Surface(
                        onClick = onOpenTagList,
                        color = KpknSurface.inset,
                        shape = KpknShape.pill,
                    ) {
                        Text(
                            text = if (hasCreatedTags) "Etiquetas" else "Sin etiquetas",
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            style = headerChipStyle(FontWeight.Medium, typeScale),
                            color = WizardColors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
