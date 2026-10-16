package com.example.kpkn.screens.workout

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

internal val WorkoutLiveRelatorSlotHeight = 36.dp
internal val WorkoutLiveRelatorFontSp = 13.sp
internal const val WorkoutLiveRelatorMaxLines = 2

/** Interlineado de la frase del relator: dos líneas de 16 sp (LR1g). La banda se mide con esta misma cifra. */
internal val WorkoutLiveRelatorLineHeight = 16.sp

/** Fundido al cambiar de momento: sale en 150 ms y entra en 220 ms (LR1g). */
private const val RelatorFadeOutMs = 150
private const val RelatorFadeInMs = 220

/**
 * Duración de la fase trabajando (≈ 1,9 s, H2).
 */
private const val RELATOR_WORKING_DURATION_MS = 1900L

/**
 * Duración del destello breve en la fase hecho (0,9 s, H2).
 */
private const val RELATOR_SPARK_DURATION_MS = 900L

/**
 * Frase del relator (LV2):
 * - Una sola frase, centrada.
 * - Brillo continuo sobre toda la frase con Brush.linearGradient (ciclo de 5,4 s: 3 s barrido, 2,4 s pausa).
 * - Con «reducir movimiento»: tinta fija al 80 % (la acción al 100 %), sin barrido.
 * - Acciones como parte de la oración: peso SemiBold (600), color #EDE6DA, subrayado punteado de acento
 *   (SymbolPalette.musculo al 90 %, grosor 2 dp, 5 dp bajo la línea base). Objetivo táctil de 48 dp sin mover el texto.
 * - Al pulsar, tres fases (H2): En reposo -> Trabajando (≈ 1,9 s con personajes) -> Hecho (destello y pop).
 * - Momentos solo con animación (H5) cuando [silentAnimation] no es NONE.
 */
@Composable
internal fun WorkoutLiveRelatorLine(
    text: String?,
    phaseKey: String,
    modifier: Modifier = Modifier,
    actions: List<RelatorAssistAction> = emptyList(),
    onAction: (RelatorAssistAction) -> Unit = {},
    accentColor: Color = Color.Unspecified,
    silentAnimation: RelatorSilentAnimation = RelatorSilentAnimation.NONE,
    forceShimmerSweepProgress: Float? = null,
    forceWorkingProgress: Float? = null,
    forceSparkProgress: Float? = null,
    onLoadPop: (() -> Unit)? = null,
) {
    if (silentAnimation != RelatorSilentAnimation.NONE) {
        RelatorSilentAnimationSlot(
            silentAnimation = silentAnimation,
            text = text,
            modifier = modifier,
        )
        return
    }

    if (text.isNullOrBlank()) return

    var announcedPhase by remember { mutableStateOf<String?>(null) }
    val announce = phaseKey != announcedPhase
    LaunchedEffect(phaseKey) { announcedPhase = phaseKey }

    val reduceMotion = rememberRelatorReduceMotion()

    // Estado visual de 3 fases (H2): reposo, trabajando y hecho
    var workingAction by remember(phaseKey) { mutableStateOf<RelatorAssistAction?>(null) }
    var doneAction by remember(phaseKey) { mutableStateOf<RelatorAssistAction?>(null) }
    var previousWeightForUndo by remember(phaseKey) { mutableStateOf<String?>(null) }
    var sparkProgress by remember(phaseKey) { mutableFloatStateOf(0f) }

    val isWorking = workingAction != null || forceWorkingProgress != null
    val isDone = doneAction != null || forceSparkProgress != null

    // Temporizador de la fase trabajando -> hecho
    LaunchedEffect(workingAction) {
        val action = workingAction ?: return@LaunchedEffect
        delay(RELATOR_WORKING_DURATION_MS)
        doneAction = action
        workingAction = null
        onLoadPop?.invoke()
    }

    // Temporizador del destello verde en hecho
    LaunchedEffect(doneAction) {
        if (doneAction == null) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            sparkProgress = (elapsed.toFloat() / RELATOR_SPARK_DURATION_MS).coerceIn(0f, 1f)
            if (sparkProgress >= 1f) break
            delay(16L)
        }
    }

    val activeAction = workingAction ?: doneAction ?: actions.firstOrNull()
    val effectiveWorkingAction = activeAction ?: actions.firstOrNull()

    // Frase efectiva según la fase (H2)
    val displayData = remember(text, actions, isWorking, isDone, activeAction, previousWeightForUndo) {
        when {
            isWorking && activeAction != null -> {
                val action = activeAction
                val copy = when (action.kind) {
                    RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
                    RelatorAssistActionKind.ADJUST_LOAD,
                    -> {
                        val kg = action.weightKg?.let { formatRelatorKg(it) }
                            ?: extractWeightFromLabel(action.label)
                            ?: "97,5"
                        "El equipo está cargando los discos de $kg\u00A0kg…"
                    }
                    RelatorAssistActionKind.START_REST,
                    RelatorAssistActionKind.EXTEND_REST,
                    -> "Ajustando el tiempo de descanso…"
                    RelatorAssistActionKind.SKIP_REMAINING_WARMUPS -> "Saltando el calentamiento restante…"
                    else -> text
                }
                DisplayPhrase(copy, emptyList())
            }
            isDone && activeAction != null -> {
                val action = activeAction
                when (action.kind) {
                    RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
                    RelatorAssistActionKind.ADJUST_LOAD,
                    -> {
                        val setNum = if (action.setIndex >= 0) (action.setIndex + 1).toString() else "1"
                        val newKg = action.weightKg?.let { formatRelatorKg(it) }
                            ?: extractWeightFromLabel(action.label)
                            ?: "97,5"
                        val prevKg = previousWeightForUndo
                            ?: extractPreviousWeight(text)
                            ?: "95"
                        val undoLabel = "vuelve a $prevKg"
                        val undoAction = RelatorAssistAction(
                            kind = RelatorAssistActionKind.ADJUST_LOAD,
                            label = undoLabel,
                            exerciseId = action.exerciseId,
                            setIndex = action.setIndex,
                            side = action.side,
                            span = undoLabel,
                            weightKg = parseKg(prevKg),
                        )
                        DisplayPhrase(
                            "Listo: la serie $setNum va con $newKg\u00A0kg. Si te arrepientes, $undoLabel.",
                            listOf(undoAction),
                        )
                    }
                    RelatorAssistActionKind.START_REST,
                    RelatorAssistActionKind.EXTEND_REST,
                    -> DisplayPhrase("Listo: descanso ajustado.", emptyList())
                    RelatorAssistActionKind.SKIP_REMAINING_WARMUPS ->
                        DisplayPhrase("Listo: pasamos directo a las series efectivas.", emptyList())
                    else -> DisplayPhrase(text, actions)
                }
            }
            else -> DisplayPhrase(text, actions)
        }
    }

    val currentText = displayData.text
    val currentActions = displayData.actions

    val onActionState = rememberUpdatedState(onAction)
    val handleActionClick: (RelatorAssistAction) -> Unit = { action ->
        if (isWorking) {
            // No admite un segundo toque mientras trabaja (3c)
        } else if (isDone && action.label.startsWith("vuelve a")) {
            // Deshacer pulsado: vuelve a reposo y aplica la carga anterior
            onActionState.value(action)
            doneAction = null
            workingAction = null
        } else {
            // Guardar peso anterior antes de cambiar carga (para deshacer)
            if (action.kind == RelatorAssistActionKind.APPLY_SUGGESTED_LOAD ||
                action.kind == RelatorAssistActionKind.ADJUST_LOAD
            ) {
                previousWeightForUndo = extractPreviousWeight(text) ?: "95"
            }

            // Fiabilidad (manda): el EFECTO se aplica en el ViewModel en el momento del toque (3c)
            onActionState.value(action)

            // Abrir hojas es instantáneo (sin fase de trabajo)
            if (action.kind.hasWorkingPhase()) {
                workingAction = action
            }
        }
    }

    val talkBack = if (currentActions.isEmpty()) {
        currentText
    } else {
        currentText + ". Acciones: " + currentActions.joinToString(". ") { it.clickableSpan() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = currentActions.isEmpty()) {
                contentDescription = talkBack
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!isWorking && !isDone) {
            // Reposo: texto centrado en la banda para mantener simetría idéntica (Punto 2)
            AnimatedContent(
                targetState = currentText,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val fade = if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(
                            tween(RelatorFadeInMs, delayMillis = RelatorFadeOutMs, easing = FastOutSlowInEasing),
                        ) togetherWith fadeOut(tween(RelatorFadeOutMs, easing = FastOutSlowInEasing))
                    }
                    fade using SizeTransform(clip = false) { _, _ -> snap() }
                },
                label = "relator_copy",
            ) { line ->
                val lineActions = if (line == currentText) currentActions else emptyList()
                val pieces = remember(line, lineActions) { relatorInlinePieces(line, lineActions) }
                RelatorTextLine(
                    pieces = pieces,
                    text = line,
                    actions = lineActions,
                    onAction = handleActionClick,
                    isWorking = false,
                    reduceMotion = reduceMotion,
                    forceShimmerSweepProgress = forceShimmerSweepProgress,
                )
            }
        } else {
            // Fases trabajando y hecho (H2):
            // - El texto CAMBIA a la frase de trabajo / hecho y se ubica arriba
            // - La franja de animación de personajes / destello se ubica DEBAJO del texto, sin tocar ni tapar ninguna letra
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Frase arriba (con barrido continuo rápido a 1,4 s si isWorking)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val lineActions = currentActions
                    val pieces = remember(currentText, lineActions) { relatorInlinePieces(currentText, lineActions) }
                    RelatorTextLine(
                        pieces = pieces,
                        text = currentText,
                        actions = lineActions,
                        onAction = handleActionClick,
                        isWorking = isWorking,
                        reduceMotion = reduceMotion,
                        forceShimmerSweepProgress = forceShimmerSweepProgress,
                    )
                }

                // Franja de abajo: personajes (trabajando) o destello breve (hecho)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (isWorking && effectiveWorkingAction != null) {
                        val workingP = forceWorkingProgress ?: (rememberWorkingProgress(reduceMotion))
                        RelatorWorkingScene(
                            actionKind = effectiveWorkingAction.kind,
                            progress = workingP,
                            reduceMotion = reduceMotion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                        )
                    } else if (isDone) {
                        val sparkP = forceSparkProgress ?: sparkProgress
                        RelatorSparkDoneScene(
                            progress = sparkP,
                            reduceMotion = reduceMotion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class DisplayPhrase(
    val text: String,
    val actions: List<RelatorAssistAction>,
)

internal fun RelatorAssistActionKind.hasWorkingPhase(): Boolean = when (this) {
    RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
    RelatorAssistActionKind.ADJUST_LOAD,
    RelatorAssistActionKind.START_REST,
    RelatorAssistActionKind.EXTEND_REST,
    RelatorAssistActionKind.SKIP_REMAINING_WARMUPS,
    -> true
    else -> false
}

@Composable
private fun rememberWorkingProgress(reduceMotion: Boolean): Float {
    if (reduceMotion) return 0.5f
    val transition = rememberInfiniteTransition(label = "relator_working_anim")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "working_p",
    )
    return p
}

/** «Reducir movimiento» del sistema (escala de animación 0). */
@Composable
internal fun rememberRelatorReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Franja del relator solo con animación (H5): onda de respiración, confeti de récord o agachado con puntos.
 */
@Composable
private fun RelatorSilentAnimationSlot(
    silentAnimation: RelatorSilentAnimation,
    text: String?,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberRelatorReduceMotion()
    val transition = rememberInfiniteTransition(label = "relator_silent_anim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "silent_progress",
    )
    val talkBack = when (silentAnimation) {
        RelatorSilentAnimation.REST_BREATHING -> text?.ifBlank { null } ?: "Descansando"
        RelatorSilentAnimation.RECORD_CELEBRATION -> text?.ifBlank { null } ?: "Nuevo récord"
        RelatorSilentAnimation.PREPARATION_SQUAT -> text?.ifBlank { null } ?: "Preparándote"
        RelatorSilentAnimation.NONE -> text.orEmpty()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = talkBack },
        contentAlignment = Alignment.Center,
    ) {
        when (silentAnimation) {
            RelatorSilentAnimation.REST_BREATHING -> RelatorBreathingWaveScene(progress, reduceMotion = reduceMotion)
            RelatorSilentAnimation.RECORD_CELEBRATION -> RelatorRecordCelebrationScene(progress, reduceMotion = reduceMotion)
            RelatorSilentAnimation.PREPARATION_SQUAT -> RelatorPreparationSquatScene(progress, reduceMotion = reduceMotion)
            RelatorSilentAnimation.NONE -> Unit
        }
    }
}

@Composable
private fun RelatorTextLine(
    pieces: List<RelatorInlinePiece>,
    text: String,
    actions: List<RelatorAssistAction>,
    onAction: (RelatorAssistAction) -> Unit,
    isWorking: Boolean,
    reduceMotion: Boolean,
    forceShimmerSweepProgress: Float? = null,
) {
    val onActionState = rememberUpdatedState(onAction)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Progreso del barrido de luz (5,4 s normal, 1,4 s trabajando; 55 % barrido y pausa)
    val cycleMs = if (isWorking) 1400 else 5400
    val transition = rememberInfiniteTransition(label = "relator_shimmer")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_p",
    )
    val progress = forceShimmerSweepProgress ?: (if (reduceMotion) 0f else animProgress)

    val actionByTag = remember(pieces) {
        buildMap {
            pieces.forEachIndexed { index, piece ->
                if (piece is RelatorInlinePiece.Action) {
                    relatorLinkWrapChunks(piece.label).forEachIndexed { chunkIndex, chunk ->
                        if (chunk.isNotBlank()) {
                            put("relator-action-$index-$chunkIndex", piece.action)
                        }
                    }
                }
            }
        }
    }

    // Tinta base y cresta del barrido (3b)
    val density = LocalDensity.current
    val crestWidthPx = 180f * density.density
    val paragraphWidthPx = 360f * density.density
    // Barrido activo en 0..55 %, pausa el resto del ciclo (H2)
    val sweepFraction = (progress / 0.55f).coerceIn(0f, 1f)
    val sweepStartOffset = -crestWidthPx
    val sweepEndOffset = paragraphWidthPx + crestWidthPx
    val crestLeft = if (progress <= 0.55f) {
        sweepStartOffset + (sweepEndOffset - sweepStartOffset) * sweepFraction
    } else {
        sweepEndOffset + 200f
    }

    val shimmerBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFFF2EEE6).copy(alpha = 0.58f),
            0.5f to Color(0xFFFFFFFF),
            1.0f to Color(0xFFF2EEE6).copy(alpha = 0.58f),
        ),
        start = Offset(crestLeft, 0f),
        end = Offset(crestLeft + crestWidthPx, crestWidthPx * 0.176f),
    )

    val annotated = buildAnnotatedString {
        pieces.forEachIndexed { index, piece ->
            when (piece) {
                is RelatorInlinePiece.Copy -> {
                    if (reduceMotion) {
                        withStyle(SpanStyle(color = Color(0xFFF2EEE6).copy(alpha = 0.80f))) {
                            append(piece.text)
                        }
                    } else {
                        append(piece.text)
                    }
                }
                is RelatorInlinePiece.Action -> {
                    relatorLinkWrapChunks(piece.label).forEachIndexed { chunkIndex, chunk ->
                        if (chunk.isBlank()) {
                            append(chunk)
                        } else {
                            val tag = "relator-action-$index-$chunkIndex"
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = tag,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = if (reduceMotion) Color(0xFFEDE6DA) else Color.Unspecified,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = TextDecoration.None,
                                        ),
                                    ),
                                    linkInteractionListener = { onActionState.value(piece.action) },
                                ),
                            ) {
                                append(chunk)
                            }
                        }
                    }
                }
            }
        }
    }
    val annotatedState = rememberUpdatedState(annotated)
    val actionsState = rememberUpdatedState(actionByTag)

    val spans = remember(text, actions) { relatorActionSpans(text, actions) }

    val baseStyle = KpknType.caption.copy(
        fontSize = WorkoutLiveRelatorFontSp,
        lineHeight = WorkoutLiveRelatorLineHeight,
        hyphens = Hyphens.None,
        lineBreak = LineBreak.Simple,
    )
    val effectiveStyle = if (reduceMotion) {
        baseStyle.copy(color = Color(0xFFF2EEE6).copy(alpha = 0.80f))
    } else {
        baseStyle.copy(brush = shimmerBrush)
    }

    Text(
        text = annotated,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .drawWithContent {
                drawContent()
                // Subrayado punteado de acento (SymbolPalette.musculo al 90 %, grosor 2 dp, 5 dp bajo línea base, 3a)
                val layout = layoutResult ?: return@drawWithContent
                val segments = relatorActionLineSegments(text, actions, layout)
                val underlineColor = SymbolPalette.musculo.copy(alpha = 0.90f)
                val strokeWidth = 2.dp.toPx()
                val dotDash = PathEffect.dashPathEffect(floatArrayOf(2.5f * density.density, 2.5f * density.density), 0f)

                segments.forEach { seg ->
                    val y = seg.baseline + 5.dp.toPx()
                    drawLine(
                        color = underlineColor,
                        start = Offset(seg.bounds.left, y),
                        end = Offset(seg.bounds.right, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                        pathEffect = dotDash,
                    )
                }
            }
            .then(
                if (actionByTag.isEmpty()) {
                    Modifier
                } else {
                    // Objetivo táctil de 48 dp con área invisible que no mueve el texto (3a)
                    Modifier.pointerInput(actionByTag) {
                        detectTapGestures { pos ->
                            val layout = layoutResult ?: return@detectTapGestures
                            val segments = relatorActionLineSegments(text, actions, layout)
                            val minTargetHeightPx = 48.dp.toPx()
                            val horizPaddingPx = 6.dp.toPx()

                            // Buscar primero en tramos de acción con objetivo de 48 dp
                            var matchedAction: RelatorAssistAction? = null
                            for (seg in segments) {
                                val centerY = (seg.bounds.top + seg.bounds.bottom) / 2f
                                val halfHeight = maxOf(minTargetHeightPx / 2f, (seg.bounds.height + 16.dp.toPx()) / 2f)
                                val hitRect = Rect(
                                    left = seg.bounds.left - horizPaddingPx,
                                    top = centerY - halfHeight,
                                    right = seg.bounds.right + horizPaddingPx,
                                    bottom = centerY + halfHeight,
                                )
                                if (hitRect.contains(pos)) {
                                    matchedAction = seg.action
                                    break
                                }
                            }

                            if (matchedAction != null) {
                                onActionState.value(matchedAction)
                                return@detectTapGestures
                            }

                            // Fallback al click estándar sobre caracteres
                            val txt = annotatedState.value
                            val offset = layout.getOffsetForPosition(pos)
                            val tag = txt.getLinkAnnotations(offset, offset)
                                .mapNotNull { range -> (range.item as? LinkAnnotation.Clickable)?.tag }
                                .firstOrNull()
                            val fallback = tag?.let { actionsState.value[it] } ?: return@detectTapGestures
                            onActionState.value(fallback)
                        }
                    }
                },
            ),
        style = effectiveStyle,
        maxLines = WorkoutLiveRelatorMaxLines,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        onTextLayout = { layout -> layoutResult = layout },
    )
}

internal fun formatRelatorKg(kg: Double): String {
    val rounded = kotlin.math.round(kg * 10.0) / 10.0
    val asLong = rounded.toLong()
    val str = if (kotlin.math.abs(rounded - asLong) < 1e-6) asLong.toString() else rounded.toString()
    return str.replace('.', ',')
}

private fun parseKg(text: String): Double? =
    text.replace(',', '.').trim().toDoubleOrNull()

private fun extractWeightFromLabel(label: String): String? {
    val regex = Regex("(\\d+(?:[,.]\\d+)?)\\s*kg", RegexOption.IGNORE_CASE)
    return regex.find(label)?.groupValues?.get(1)
}

private fun extractPreviousWeight(phrase: String): String? {
    val regex = Regex("(\\d+(?:[,.]\\d+)?)\\s*kg", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(phrase).map { it.groupValues[1] }.toList()
    return matches.firstOrNull()
}

internal data class RelatorActionLineSegment(
    val line: Int,
    val startOffset: Int,
    val endOffset: Int,
    val bounds: Rect,
    val baseline: Float,
    val action: RelatorAssistAction,
)

internal fun relatorActionLineSegments(
    text: String,
    actions: List<RelatorAssistAction>,
    layout: TextLayoutResult,
): List<RelatorActionLineSegment> {
    if (text.isBlank() || actions.isEmpty()) return emptyList()
    val spans = relatorActionSpans(text, actions)
    if (spans.isEmpty()) return emptyList()
    val segments = mutableListOf<RelatorActionLineSegment>()
    for (span in spans) {
        val s = span.start.coerceIn(0, text.length)
        val e = span.endExclusive.coerceIn(0, text.length)
        if (s >= e) continue
        val startLine = layout.getLineForOffset(s)
        val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(s))
        for (line in startLine..endLine) {
            val lineStart = maxOf(s, layout.getLineStart(line))
            val lineEnd = minOf(e, layout.getLineEnd(line))
            var trimmedEnd = lineEnd
            while (trimmedEnd > lineStart && text[trimmedEnd - 1].isWhitespace()) {
                trimmedEnd--
            }
            if (lineStart < trimmedEnd) {
                val path = layout.getPathForRange(lineStart, trimmedEnd)
                val bounds = path.getBounds()
                val baseline = layout.getLineBaseline(line)
                segments += RelatorActionLineSegment(
                    line = line,
                    startOffset = lineStart,
                    endOffset = trimmedEnd,
                    bounds = bounds,
                    baseline = baseline,
                    action = span.action,
                )
            }
        }
    }
    return segments
}

