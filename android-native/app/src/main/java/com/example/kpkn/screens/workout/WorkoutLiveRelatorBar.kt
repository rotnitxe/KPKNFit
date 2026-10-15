package com.example.kpkn.screens.workout

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

internal val WorkoutLiveRelatorSlotHeight = 36.dp
internal val WorkoutLiveRelatorFontSp = 13.sp
internal const val WorkoutLiveRelatorMaxLines = 2

/** Interlineado de la frase del relator: dos líneas de 16 sp (LR1g). La banda se mide con esta misma cifra. */
internal val WorkoutLiveRelatorLineHeight = 16.sp

/** Tinta de la frase: fija al 72 %, sin brillo (LR1g). */
private const val RelatorInkAlpha = 0.72f

/** Fundido al cambiar de momento: sale en 150 ms y entra en 220 ms (LR1g). */
private const val RelatorFadeOutMs = 150
private const val RelatorFadeInMs = 220

/**
 * Frase del relator (LR1g): texto fijo en tinta al 72 %, centrado, sin brillo. Las acciones son enlaces en tinta y
 * subrayados. Al cambiar de momento solo se funde: sale en 150 ms y entra en 220 ms, sin deslizar ni expandir. Con
 * «reducir movimiento» no hay animación. [accentColor] se conserva por las llamadas: el relator va en tinta neutra.
 */
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

    val reduceMotion = rememberRelatorReduceMotion()
    val pieces = remember(text, actions) { relatorInlinePieces(text, actions) }
    val talkBack = if (actions.isEmpty()) {
        text
    } else {
        text + ". Acciones: " + actions.joinToString(". ") { it.clickableSpan() }
    }

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
            val linePieces = if (line == text) pieces else relatorInlinePieces(line, actions)
            RelatorTextLine(
                pieces = linePieces,
                onAction = onAction,
            )
        }
    }
}

/** «Reducir movimiento» del sistema (escala de animación 0), igual que en el resto de la app. */
@Composable
private fun rememberRelatorReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Composable
private fun RelatorTextLine(
    pieces: List<RelatorInlinePiece>,
    onAction: (RelatorAssistAction) -> Unit,
) {
    val ink = WizardColors.text
    val onActionState = rememberUpdatedState(onAction)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
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

    val annotated = buildAnnotatedString {
        pieces.forEachIndexed { index, piece ->
            when (piece) {
                is RelatorInlinePiece.Copy -> append(piece.text)
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
                                            color = ink,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = TextDecoration.Underline,
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

    Text(
        text = annotated,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(
                if (actionByTag.isEmpty()) {
                    Modifier
                } else {
                    Modifier.pointerInput(actionByTag) {
                        detectTapGestures { pos ->
                            val layout = layoutResult ?: return@detectTapGestures
                            val text = annotatedState.value
                            val offset = layout.getOffsetForPosition(pos)
                            val tag = text.getLinkAnnotations(offset, offset)
                                .mapNotNull { range -> (range.item as? LinkAnnotation.Clickable)?.tag }
                                .firstOrNull()
                            val action = tag?.let { actionsState.value[it] } ?: return@detectTapGestures
                            onActionState.value(action)
                        }
                    }
                },
            ),
        color = ink.copy(alpha = RelatorInkAlpha),
        style = KpknType.caption.copy(
            fontSize = WorkoutLiveRelatorFontSp,
            lineHeight = WorkoutLiveRelatorLineHeight,
            hyphens = Hyphens.None,
            lineBreak = LineBreak.Simple,
        ),
        maxLines = WorkoutLiveRelatorMaxLines,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        onTextLayout = { layout -> layoutResult = layout },
    )
}
