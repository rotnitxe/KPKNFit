package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val WorkoutLiveRelatorSlotHeight = 36.dp
internal val WorkoutLiveRelatorFontSp = 13.sp
internal const val WorkoutLiveRelatorMaxLines = 2
private val RelatorLineHeightSp = 16.sp

@Composable
internal fun rememberLiveRelatorLine(snapshot: LiveRelatorSnapshot): RelatorResolution {
    var idleCycle by remember { mutableIntStateOf(0) }
    val parentKey = snapshot.parentContextKey.ifBlank { snapshot.setKey }
    LaunchedEffect(parentKey, snapshot.setKey, snapshot.phase) {
        idleCycle = 0
        while (true) {
            delay(RELATOR_IDLE_ROTATE_MS)
            idleCycle++
        }
    }
    val live = snapshot.copy(idleCycle = idleCycle)
    var shown by remember {
        mutableStateOf(WorkoutLiveRelator.resolve(live.copy(lastChangedField = RelatorChangedField.NONE)))
    }
    if (!live.lastChangedField.isReaction) {
        val idle = WorkoutLiveRelator.resolve(live)
        SideEffect { shown = idle }
        return idle
    }
    LaunchedEffect(
        live.phase,
        live.parentContextKey,
        live.setKey,
        live.lastChangedField,
        live.enteredWeightRaw,
        live.enteredReps,
        live.enteredIntensity,
        live.dropSetCount,
        live.reachedFailure,
        live.warmupIsLastIncomplete,
        live.visible,
        live.idleCycle,
        live.assistOffer?.stickyKey,
    ) {
        delay(RELATOR_DEBOUNCE_MS)
        shown = WorkoutLiveRelator.resolve(live, previousText = shown.text)
    }
    return shown
}

@Composable
internal fun WorkoutLiveRelatorLine(
    text: String?,
    phaseKey: String,
    modifier: Modifier = Modifier,
    actions: List<RelatorAssistAction> = emptyList(),
    onAction: (RelatorAssistAction) -> Unit = {},
    accentColor: Color = Color.Unspecified,
) {
    if (text.isNullOrBlank()) return
    var announcedPhase by remember { mutableStateOf<String?>(null) }
    val announce = phaseKey != announcedPhase
    LaunchedEffect(phaseKey) { announcedPhase = phaseKey }

    val infinite = rememberInfiniteTransition(label = "relator_shimmer")
    val shift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "relator_shimmer_x",
    )
    val pieces = remember(text, actions) { relatorInlinePieces(text, actions) }
    val talkBack = if (actions.isEmpty()) {
        text
    } else {
        text + ". Acciones: " + actions.joinToString(". ") { it.clickableSpan() }
    }
    val linkColor = accentColor.takeOrElse { MaterialTheme.colorScheme.secondary }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = actions.isEmpty()) {
                contentDescription = talkBack
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = text,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (
                    fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { full -> -full / 5 } +
                        expandHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Start,
                            clip = false,
                        )
                    ) togetherWith (
                    fadeOut(tween(180)) +
                        slideOutHorizontally(tween(180)) { full -> full / 6 }
                    ) using SizeTransform(clip = false) { _, _ -> snap() }
            },
            label = "relator_copy",
        ) { line ->
            val linePieces = if (line == text) pieces else relatorInlinePieces(line, actions)
            ShimmerRelatorText(
                pieces = linePieces,
                shift = shift,
                accentColor = linkColor,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ShimmerRelatorText(
    pieces: List<RelatorInlinePiece>,
    shift: Float,
    accentColor: Color,
    onAction: (RelatorAssistAction) -> Unit,
) {
    var textWidth by remember { mutableFloatStateOf(0f) }
    val muted = Color.White.copy(alpha = 0.40f)
    val highlight = Color.White
    val travel = textWidth.coerceAtLeast(1f)
    val startX = travel * (shift * 1.85f - 0.55f)
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to muted,
            0.38f to muted,
            0.50f to highlight,
            0.62f to muted,
            1.00f to muted,
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + travel * 0.46f, 0f),
    )
    val actionMuted = accentColor.copy(alpha = 0.55f)
    val actionBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to actionMuted,
            0.38f to actionMuted,
            0.50f to accentColor,
            0.62f to actionMuted,
            1.00f to actionMuted,
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + travel * 0.46f, 0f),
    )
    val annotated = remember(pieces, accentColor, onAction, brush, actionBrush) {
        buildAnnotatedString {
            var actionIndex = 0
            pieces.forEach { piece ->
                when (piece) {
                    is RelatorInlinePiece.Copy -> withStyle(SpanStyle(brush = brush)) {
                        append(piece.text)
                    }
                    is RelatorInlinePiece.Action -> {
                        val action = piece.action
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "relator_action_$actionIndex",
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        brush = actionBrush,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                ),
                                linkInteractionListener = { onAction(action) },
                            ),
                        ) {
                            append(piece.label)
                        }
                        actionIndex++
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        style = MaterialTheme.typography.labelSmall.merge(
            TextStyle(
                fontSize = WorkoutLiveRelatorFontSp,
                lineHeight = RelatorLineHeightSp,
            ),
        ),
        maxLines = WorkoutLiveRelatorMaxLines,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        onTextLayout = { layout ->
            textWidth = layout.size.width.toFloat()
        },
    )
}

