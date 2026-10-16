package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknHazeEffect
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.util.Locale

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*

/**
 * Descanso a pantalla completa (LV6, F1): los RINGS como reloj, sin círculo. Arriba el ejercicio y el relator; luego las pestañas de
 * forma (Torre · Pista · Espiral), la forma con el corredor, el tiempo, −15 s / «Saltar descanso» / +15 s y «Jugar mientras descansas».
 * Debajo, sin perder nada de lo de siempre: la última serie, el plan de sesión y el descanso sugerido, y «Saltar series restantes…».
 *
 * El tiempo SIEMPRE es [remainingSeconds] (el del controlador); la forma solo lo dibuja. Las acciones (±15 s, saltar, descanso sugerido,
 * saltar series) son las mismas funciones que usan la tarjeta, la notificación y el minijuego.
 */
@Composable
internal fun RestTimerOverlay(
    state: WorkoutRestModalState,
    remainingSeconds: Int,
    hazeState: HazeState,
    pendingRestSuggestion: PendingRestSuggestion? = null,
    lastSetOutcome: SetOutcomeV2? = null,
    lastCompletedSet: CompletedSet? = null,
    lastCompletedSets: List<Pair<String, CompletedSet>> = emptyList(),
    isAdaptiveActive: Boolean = false,
    sessionAccentColor: Color = Color.White,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    skipExerciseLabel: String? = null,
    onSkipExercise: (() -> Unit)? = null,
    onUseAdaptive: (() -> Unit)? = null,
    onWarmupEffort: ((Double) -> Unit)? = null,
    postExerciseFeedbackContent: (@Composable () -> Unit)? = null,
    feedbackExerciseCount: Int = 0,
    onMinimize: (() -> Unit)? = null,
    relatorText: String? = null,
    relatorPhaseKey: String = "",
    relatorActions: List<RelatorAssistAction> = emptyList(),
    onRelatorAction: (RelatorAssistAction) -> Unit = {},
    shape: RestClockShape = RestClockShape.TOWER,
    onShapeChange: (RestClockShape) -> Unit = {},
    headline: RestHeadline? = null,
    nextSet: RestNextSetSummary? = null,
    recordStore: RestGameRecordStore? = null,
    clockActive: Boolean = true,
    initiallyPlaying: Boolean = false,
    gameEngine: RestGameEngine? = null,
) {
    // El total es el del descanso entero (con los ajustes de ±15 s): así la forma no vuelve a cero al ajustar el tiempo.
    val totalSeconds = state.totalRestSeconds().coerceAtLeast(remainingSeconds).coerceAtLeast(1)
    val progress = RestClockMath.progress(totalSeconds, remainingSeconds)
    val hasFeedback = postExerciseFeedbackContent != null
    var playing by rememberSaveable { mutableStateOf(initiallyPlaying) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .kpknHazeEffect(hazeState)
            // Velo negro al 92 % sobre la tarjeta de detrás: lo de debajo no se transparenta (LR1a).
            .background(KpknSurface.canvas.copy(alpha = 0.92f))
            // The rest layer is modal.  Consume taps in its transparent
            // margins too, otherwise a touch can fall through to the media
            // card and open Photos/camera after "Saltar descanso".
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .zIndex(6f),
    ) {
        AnimatedContent(
            targetState = hasFeedback,
            transitionSpec = {
                (if (targetState) {
                    (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
                } else {
                    (slideInVertically { -it } + fadeIn()) togetherWith
                    (slideOutVertically { it } + fadeOut())
                }).using(SizeTransform(clip = false))
            },
            label = "feedback-layout-transition",
        ) { showingFeedback ->
            if (showingFeedback) {
                FeedbackContent(
                    state = state,
                    remainingSeconds = remainingSeconds,
                    progress = progress,
                    sessionAccentColor = sessionAccentColor,
                    pendingRestSuggestion = pendingRestSuggestion,
                    isAdaptiveActive = isAdaptiveActive,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    onUseAdaptive = onUseAdaptive,
                    postExerciseFeedbackContent = postExerciseFeedbackContent ?: {},
                    feedbackExerciseCount = feedbackExerciseCount,
                    showTimerChrome = remainingSeconds > 0,
                    relatorText = relatorText,
                    relatorPhaseKey = relatorPhaseKey,
                    relatorActions = relatorActions,
                    onRelatorAction = onRelatorAction,
                    shape = shape,
                    clockActive = clockActive,
                )
            } else {
                AnimatedContent(
                    targetState = playing,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                    label = "rest-game-transition",
                ) { isPlaying ->
                    if (isPlaying) {
                        // El juego vive dentro de la misma superficie. El descanso manda: al llegar a 0 esta capa deja de componerse y
                        // el juego se cierra con ella; «Saltar descanso» desde aquí es el mismo onSkip.
                        RestGameFullScreen(
                            remainingSeconds = remainingSeconds,
                            totalSeconds = totalSeconds,
                            nextSet = nextSet,
                            onBack = { playing = false },
                            onSkip = onSkip,
                            recordStore = recordStore,
                            engine = gameEngine,
                            running = clockActive,
                        )
                    } else {
                        NormalRestContent(
                            state = state,
                            remainingSeconds = remainingSeconds,
                            totalSeconds = totalSeconds,
                            progress = progress,
                            pendingRestSuggestion = pendingRestSuggestion,
                            lastSetOutcome = lastSetOutcome,
                            lastCompletedSet = lastCompletedSet,
                            lastCompletedSets = lastCompletedSets,
                            isAdaptiveActive = isAdaptiveActive,
                            sessionAccentColor = sessionAccentColor,
                            onDecrease = onDecrease,
                            onIncrease = onIncrease,
                            onSkip = onSkip,
                            skipExerciseLabel = skipExerciseLabel,
                            onSkipExercise = onSkipExercise,
                            onUseAdaptive = onUseAdaptive,
                            onMinimize = onMinimize,
                            relatorText = relatorText,
                            relatorPhaseKey = relatorPhaseKey,
                            relatorActions = relatorActions,
                            onRelatorAction = onRelatorAction,
                            shape = shape,
                            onShapeChange = onShapeChange,
                            headline = headline,
                            onPlay = { playing = true },
                            clockActive = clockActive,
                        )
                    }
                }
            }
        }
    }
}

/** El tiempo del descanso con su etiqueta: «DESCANSO», «2:41» y «de 4:09 · sugerido». Lo lee TalkBack como una sola frase. */
@Composable
private fun RestTimeReadout(
    state: WorkoutRestModalState,
    remainingSeconds: Int,
    totalSeconds: Int,
    isAdaptiveActive: Boolean,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    showTotal: Boolean = true,
) {
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val totalLabel = buildString {
        append("de ").append(formatRestClock(totalSeconds))
        when {
            isAdaptiveActive -> append(" · sugerido")
            state.isManualOverride -> append(" · manual")
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = restKindText(state.kind).uppercase(),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        RestClockText(
            text = formatRestClock(safeRemaining),
            maxFontSize = maxFontSize,
            modifier = Modifier.semantics { contentDescription = RestClockMath.spokenRemaining(safeRemaining) },
        )
        if (showTotal) {
            Text(
                text = totalLabel,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}

@Composable
private fun FeedbackContent(
    state: WorkoutRestModalState,
    remainingSeconds: Int,
    progress: Float,
    sessionAccentColor: Color,
    pendingRestSuggestion: PendingRestSuggestion?,
    isAdaptiveActive: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onUseAdaptive: (() -> Unit)?,
    postExerciseFeedbackContent: @Composable () -> Unit,
    feedbackExerciseCount: Int = 0,
    showTimerChrome: Boolean = true,
    relatorText: String? = null,
    relatorPhaseKey: String = "",
    relatorActions: List<RelatorAssistAction> = emptyList(),
    onRelatorAction: (RelatorAssistAction) -> Unit = {},
    shape: RestClockShape = RestClockShape.TOWER,
    clockActive: Boolean = true,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(
                start = WorkoutUiTokens.ScreenHorizontalPadding,
                end = WorkoutUiTokens.ScreenHorizontalPadding,
                top = 8.dp,
                bottom = 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RestOverlayRelatorLine(
            text = relatorText,
            phaseKey = relatorPhaseKey,
            actions = relatorActions,
            onAction = onRelatorAction,
            accentColor = sessionAccentColor,
        )

        // ─── ADAPTIVE REST SUGGESTION AT TOP ───
        if (pendingRestSuggestion != null && !isAdaptiveActive && onUseAdaptive != null) {
            RestFigureModule(
                title = "Descanso sugerido",
                value = formatRestClock(pendingRestSuggestion.adaptiveSeconds),
                modifier = Modifier.fillMaxWidth(),
                sparkle = true,
                onClick = onUseAdaptive,
            )
        }

        if (showTimerChrome) {
            Spacer(Modifier.height(16.dp))

            // Shrink ANIMADO: entra con el tamaño del reloj del descanso normal y colapsa a su tamaño compacto mientras aparece el
            // panel de feedback.
            val shrinkTargetHeight = if (feedbackExerciseCount <= 1) 128.dp else 108.dp
            val shrinkTargetFontSp = if (feedbackExerciseCount <= 1) 34f else 30f
            var shrinkStarted by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shrinkStarted = true }
            val clockHeight by animateDpAsState(
                targetValue = if (shrinkStarted) shrinkTargetHeight else 230.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "rest-timer-shrink",
            )
            val fontSize = animateFloatAsState(
                targetValue = if (shrinkStarted) shrinkTargetFontSp else 52f,
                label = "rest-timer-font",
            ).value.sp

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RestRingsClock(
                    shape = shape,
                    progress = progress,
                    animationsOn = clockActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(clockHeight),
                )
                RestClockText(
                    text = formatRestClock(remainingSeconds.coerceAtLeast(0)),
                    maxFontSize = fontSize,
                    modifier = Modifier.semantics {
                        contentDescription = RestClockMath.spokenRemaining(remainingSeconds.coerceAtLeast(0))
                    },
                )
                if (!state.notificationsEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sin notificaciones el aviso es solo sonoro.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (!state.exactAlarmGranted) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sin alarma exacta el aviso puede retrasarse con la pantalla apagada.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = restKindText(state.kind).uppercase(),
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        postExerciseFeedbackContent()
    }
}

/**
 * El descanso normal (F1). Todo lo de siempre sigue a mano: minimizar, el relator, ajustar el tiempo, saltar, la última serie, el
 * plan y el descanso sugerido, saltar las series que quedan. Lo que cambia es el reloj: la forma de RINGS con su corredor.
 */
@Composable
private fun NormalRestContent(
    state: WorkoutRestModalState,
    remainingSeconds: Int,
    totalSeconds: Int,
    progress: Float,
    pendingRestSuggestion: PendingRestSuggestion?,
    lastSetOutcome: SetOutcomeV2?,
    lastCompletedSet: CompletedSet?,
    lastCompletedSets: List<Pair<String, CompletedSet>>,
    isAdaptiveActive: Boolean,
    sessionAccentColor: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    skipExerciseLabel: String?,
    onSkipExercise: (() -> Unit)?,
    onUseAdaptive: (() -> Unit)?,
    onMinimize: (() -> Unit)?,
    relatorText: String?,
    relatorPhaseKey: String,
    relatorActions: List<RelatorAssistAction>,
    onRelatorAction: (RelatorAssistAction) -> Unit,
    shape: RestClockShape,
    onShapeChange: (RestClockShape) -> Unit,
    headline: RestHeadline?,
    onPlay: () -> Unit,
    clockActive: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // La forma toma lo que cabe: entre 210 y 300 dp según el alto de la pantalla.
        val shapeHeight = (maxHeight * 0.30f).coerceIn(210.dp, 300.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = WorkoutUiTokens.ScreenHorizontalPadding,
                    end = WorkoutUiTokens.ScreenHorizontalPadding,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // El bloque de F1 va 8 dp más adentro que lo de debajo (el módulo de la última serie, el plan y el sugerido conservan su ancho de
            // siempre: «DESCANSO SUGERIDO» tiene que seguir cabiendo en dos líneas con la letra al 130 %).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Asa de minimizar a la izquierda y, al lado, el ejercicio de la serie que acabas de hacer (F1).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onMinimize != null) {
                        KpknIconButton(
                            icon = KpknLineIcons.ChevronDown,
                            contentDescription = "Minimizar",
                            onClick = onMinimize,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headline?.title ?: state.exerciseName,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        headline?.subtitle?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // El relator a 12 dp de la cabecera y el reloj a 16 dp del relator (LR1g).
                Spacer(Modifier.height(12.dp))
                RestOverlayRelatorLine(
                    text = relatorText,
                    phaseKey = relatorPhaseKey,
                    actions = relatorActions,
                    onAction = onRelatorAction,
                    accentColor = sessionAccentColor,
                )
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lr1g-aro"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RestShapeTabs(selected = shape, onSelect = onShapeChange)
                    RestRingsClock(
                        shape = shape,
                        progress = progress,
                        animationsOn = clockActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(shapeHeight),
                    )
                    RestTimeReadout(
                        state = state,
                        remainingSeconds = remainingSeconds,
                        totalSeconds = totalSeconds,
                        isAdaptiveActive = isAdaptiveActive,
                        maxFontSize = 52.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ajustar tiempo",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RestStepPill(
                        text = "−15 s",
                        contentDescription = "Restar 15 segundos",
                        onClick = onDecrease,
                        modifier = Modifier.weight(1f),
                    )
                    RestSkipPill(
                        text = "Saltar descanso",
                        onClick = onSkip,
                        modifier = Modifier.weight(1.8f),
                    )
                    RestStepPill(
                        text = "+15 s",
                        contentDescription = "Sumar 15 segundos",
                        onClick = onIncrease,
                        modifier = Modifier.weight(1f),
                    )
                }
                RestPlayPill(
                    text = "Jugar mientras descansas",
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lastCompletedSets.isNotEmpty()) {
                    lastCompletedSets.forEach { (exerciseName, set) ->
                        RestLastSetModule(
                            title = exerciseName,
                            titleIsEyebrow = false,
                            set = set,
                            outcome = lastSetOutcome ?: set.setOutcomeV2,
                        )
                    }
                } else {
                    val referenceSet = lastCompletedSet ?: pendingRestSuggestion?.lastSet
                    val referenceOutcome = lastSetOutcome ?: referenceSet?.setOutcomeV2
                    if (referenceSet != null) {
                        RestLastSetModule(
                            title = "Última serie",
                            titleIsEyebrow = true,
                            set = referenceSet,
                            outcome = referenceOutcome,
                        )
                    }
                }

                if (pendingRestSuggestion != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RestFigureModule(
                            title = if (isAdaptiveActive) "DESCANSO DINÁMICO" else "PLAN DE SESIÓN",
                            value = formatRestClock(state.plannedSeconds),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            footnote = if (state.isManualOverride) "Manual" else null,
                        )

                        if (!isAdaptiveActive && onUseAdaptive != null) {
                            RestFigureModule(
                                title = "Descanso sugerido",
                                value = formatRestClock(pendingRestSuggestion.adaptiveSeconds),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                sparkle = true,
                                onClick = onUseAdaptive,
                            )
                        }
                    }
                }

                if (onSkipExercise != null) {
                    RestSecondaryPill(
                        text = skipExerciseLabel ?: "Saltar series restantes e ir al siguiente ejercicio",
                        icon = Icons.Default.SkipNext,
                        onClick = onSkipExercise,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RestOverlayRelatorLine(
    text: String?,
    phaseKey: String,
    actions: List<RelatorAssistAction>,
    onAction: (RelatorAssistAction) -> Unit,
    accentColor: Color,
) {
    if (text.isNullOrBlank()) return
    WorkoutLiveRelatorLine(
        text = text,
        phaseKey = phaseKey,
        actions = actions,
        onAction = onAction,
        accentColor = accentColor,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RestTinyBadge(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.13f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/**
 * Módulo de una cifra del descanso (LR1a): eyebrow en `textMuted`, cifra en `metricM` sin cero delante. Con [sparkle] lleva el
 * acento en su icono; con [onClick] es un botón entero. [footnote] es una línea opcional entre el eyebrow y la cifra.
 */
@Composable
private fun RestFigureModule(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    sparkle: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    KpknModule(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (sparkle) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = SymbolPalette.musculo,
                )
            }
            Text(
                text = title.uppercase(Locale("es")),
                // Espaciado de letra algo menor para que «DESCANSO SUGERIDO» quepa en dos líneas al 130 % (LR1a).
                style = KpknType.eyebrow.copy(letterSpacing = 0.8.sp),
                color = WizardColors.textMuted,
                // Hasta dos líneas: «PLAN DE SESIÓN» y «DESCANSO SUGERIDO» se parten entre palabras al 130 % de letra.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Text(
            text = value,
            style = KpknType.metricM,
            color = WizardColors.text,
            maxLines = 1,
        )
    }
}

/**
 * Última serie del descanso (LR1a): módulo de una fila con el icono de historial, el título y la cifra «kg × reps» con coma y
 * espacios inseparables. Las marcas (récord, fallo, drop) siguen como estaban.
 */
@Composable
private fun RestLastSetModule(
    title: String,
    titleIsEyebrow: Boolean,
    set: CompletedSet,
    outcome: SetOutcomeV2?,
) {
    val loadStr = if (set.weight > 0.0) "${formatWeight(set.weight)} kg" else "Peso corporal"
    val valueStr = if (set.timeSeconds != null) "${set.timeSeconds}s" else "${set.reps} reps"
    // Espacios inseparables: la cifra no se parte al ajustarse (KPKN_UI_V2 §5).
    val figure = "$loadStr × $valueStr".replace(' ', ' ')
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.History,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (titleIsEyebrow) title.uppercase(Locale("es")) else title,
                    style = if (titleIsEyebrow) KpknType.eyebrow else KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = figure,
                    style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (outcome?.isContextPr == true || outcome?.isGlobalPr == true) {
                    RestTinyBadge("Récord", Color(0xFFFFD740))
                }
                if (set.isFailure || set.isFailedSet) {
                    RestTinyBadge("Fallo", Color(0xFFFF5252))
                }
                if (set.dropSets.isNotEmpty()) {
                    RestTinyBadge("Drop", Color(0xFF40C4FF))
                }
            }
        }
    }
}

/**
 * Tiempo del descanso en UNA sola línea: si «m:ss» no cabe con [maxFontSize], la letra encoge (mínimo 20 sp) hasta que quepa.
 * Nunca se parte ni se corta con puntos suspensivos. Syne ExtraBold con cifras tabulares, como `metricXL` (LR1a).
 */
@Composable
internal fun RestClockText(
    text: String,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    // Tamaño automático de la original (TextAutoSize): la mayor letra de 20 a la máxima que cabe en una línea (LR1a).
    val autoSize = remember(maxFontSize) {
        TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = maxFontSize, stepSize = 1.sp)
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = WizardFonts.display,
            fontWeight = FontWeight.ExtraBold,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "'lnum' 1, 'tnum' 1",
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = autoSize,
    )
}

/**
 * «Saltar series restantes…» (LR1a): píldora secundaria con borde de tinta al 24 %, Inter SemiBold 14 y el icono delante, en el mismo
 * sitio. Una línea con elipsis, como antes. Sin ripple.
 */
@Composable
private fun RestSecondaryPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(KpknShape.pill)
            .border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = WizardColors.text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = KpknType.action,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Helpers
private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return String.format(Locale.forLanguageTag("es"), "%02d:%02d", min, sec)
}

/** Cronómetro del descanso sin cero delante de los minutos: «3:00», no «03:00» (LR1a). */
private fun formatRestClock(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return String.format(Locale.forLanguageTag("es"), "%d:%02d", min, sec)
}

/** Nombre del tipo de descanso, en minúsculas (el llamante lo pasa a mayúsculas donde toca). */
private fun restKindText(kind: RestTimerKind): String = when (kind) {
    RestTimerKind.SUPERSET_INTRA -> "superserie"
    RestTimerKind.SUPERSET_ROUND -> "siguiente ronda"
    RestTimerKind.WARMUP -> "aproximación"
    RestTimerKind.BETWEEN_SIDES -> "entre lados"
    RestTimerKind.STANDARD -> "descanso"
}

private fun formatWeight(weight: Double): String {
    return if (weight % 1.0 == 0.0) {
        weight.toInt().toString()
    } else {
        String.format(Locale.forLanguageTag("es"), "%.1f", weight)
    }
}

@Suppress("unused")
private fun formatIntensity(intensity: Double): String {
    return if (intensity % 1.0 == 0.0) {
        intensity.toInt().toString()
    } else {
        String.format(Locale.forLanguageTag("es"), "%.1f", intensity)
    }
}

@Composable
fun RestTimerPill(
    remainingSeconds: Int,
    totalSeconds: Int,
    exerciseName: String,
    sessionAccentColor: Color = Color.White,
    onClick: () -> Unit,
) {
    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xFF1A1A2E).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.35f)),
        shadowElevation = 8.dp,
        modifier = Modifier.zIndex(7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val sw = 2.5.dp.toPx()
                    val radius = (size.minDimension - sw) / 2f
                    val rect = Size(radius * 2f, radius * 2f)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = rect,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = sessionAccentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = rect,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = formatTime(remainingSeconds.coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
            if (exerciseName.isNotBlank()) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Expandir",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
