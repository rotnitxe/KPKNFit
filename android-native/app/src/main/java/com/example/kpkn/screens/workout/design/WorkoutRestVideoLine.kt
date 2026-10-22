package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.CompletedSetPatch
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.screens.workout.components.WorkoutMediaThumb
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

internal const val RestVideoLineTag = "rest_video_line"
internal const val RestVideoTextTag = "rest_video_text"
internal const val RestVideoThumbTag = "rest_video_thumb"

internal enum class RestVideoAction(val label: String) {
    CORRECT("corregir"), MARK_FAILURE("marcar fallo"), OPEN("ver análisis"), WHY("ver por qué"), RETRY("reintentar"),
}

internal data class RestVideoLine(
    val description: String,
    val actions: List<RestVideoAction>,
    val compactDescription: String = description,
) {
    fun phrase(description: String = this.description): String = description + actions.joinToString("") { " · ${it.label}" }
    val text: String get() = phrase()
}

/** La descripción parte del Modo visual; solo el descanso ofrece corregir el registro (D40). */
internal fun workoutRestVideoLine(state: SetVideoAnalysisState): RestVideoLine {
    val description = workoutVideoRepsText(state)
    return when (state) {
        is SetVideoAnalysisState.Analyzing -> RestVideoLine(description, emptyList())
        is SetVideoAnalysisState.Mismatch -> {
            if (state.confidence == CountConfidence.BAJA) return RestVideoLine(description, listOf(RestVideoAction.WHY))
            val actions = buildList {
                if (state.countsDiffer && (state.counted > 0 || state.partials > 0)) add(RestVideoAction.CORRECT)
                if (state.failed > 0 && state.confidence == CountConfidence.ALTA) add(RestVideoAction.MARK_FAILURE)
                if (state.confidence == CountConfidence.MEDIA || isEmpty()) add(RestVideoAction.OPEN)
            }
            val registered = "Registraste ${state.logged}" + if (state.loggedPartials > 0) " + ${state.loggedPartials} parcial${if (state.loggedPartials == 1) "" else "es"}" else ""
            val full = if (state.confidence == CountConfidence.MEDIA && state.countsDiffer) {
                "$registered; ${description.replaceFirst("El vídeo", "el vídeo")}"
            } else description
            val count = if (state.confidence == CountConfidence.MEDIA) approxCount(state.counted) else "${state.counted}"
            val extras = buildString {
                partialsShort(state.partials, state.partialConfidence)?.let { append("+").append(it) }
                if (state.failed > 0) append(" · ${state.failed} fallo${if (state.failed == 1) "" else "s"}")
            }
            val compact = if (state.countsDiffer) "${state.logged}${if (state.loggedPartials > 0) "+${state.loggedPartials}p" else ""}→$count$extras"
                else "Vídeo $count$extras"
            RestVideoLine(full, actions, compact)
        }
        is SetVideoAnalysisState.Suggest -> RestVideoLine(description, listOf(if (state.confidence == CountConfidence.BAJA) RestVideoAction.WHY else RestVideoAction.OPEN),
            if (state.confidence == CountConfidence.BAJA) description
            else if (state.reps == 0 && state.partials == 0 && state.failed > 0) "${state.failed} al fallo"
            else "Vídeo ${if (state.confidence == CountConfidence.MEDIA) approxCount(state.reps) else state.reps}" +
                (partialsShort(state.partials, state.partialConfidence)?.let { " +$it" }.orEmpty()) + (if (state.failed > 0) " · ${state.failed} al fallo" else ""))
        is SetVideoAnalysisState.Agrees -> RestVideoLine(description, listOf(RestVideoAction.OPEN))
        SetVideoAnalysisState.Inconclusive -> RestVideoLine(description, listOf(RestVideoAction.WHY))
        is SetVideoAnalysisState.Failed -> RestVideoLine(description, listOf(RestVideoAction.RETRY), "No se pudo analizar")
        is SetVideoAnalysisState.Unsupported -> RestVideoLine(description, emptyList(), "Análisis no disponible")
    }
}

/** Sin estado de negocio: la miniatura abre y cada enlace conserva una acción explícita. */
@Composable
internal fun WorkoutRestVideoLine(
    media: WorkoutMedia?,
    value: SetVideoAnalysisUi,
    badge: VideoThumbBadge?,
    onPatch: (CompletedSetPatch) -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onRetryAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = workoutRestVideoLine(value.state)
    fun perform(action: RestVideoAction) {
        when (action) {
            RestVideoAction.CORRECT -> (value.state as? SetVideoAnalysisState.Mismatch)?.let { onPatch(completedVideoCorrectionPatch(it)) }
            RestVideoAction.MARK_FAILURE -> onPatch(CompletedSetPatch(reachedFailure = true))
            RestVideoAction.OPEN, RestVideoAction.WHY -> onOpenAnalysis(value.mediaId)
            RestVideoAction.RETRY -> onRetryAnalysis(value.mediaId)
        }
    }
    Row(
        modifier.fillMaxWidth().height(48.dp).testTag(RestVideoLineTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).testTag(RestVideoThumbTag)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onOpenAnalysis(value.mediaId) }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "${videoThumbContentDescription(badge, media?.durationMs)}. ${line.text}"
                    onClick(label = "Ver análisis") { onOpenAnalysis(value.mediaId); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(KpknSurface.inset)) {
                if (media != null) WorkoutMediaThumb(media, Modifier.fillMaxSize(), visualModeOverlay = true, hasAnalysisBadge = badge != null)
                else Icon(KpknLineIcons.Play, null, Modifier.align(Alignment.TopStart).padding(3.dp).size(12.dp), tint = WizardColors.textMuted)
                if (badge != null) RestVideoBadge(badge, Modifier.align(Alignment.BottomEnd).testTag("rest_video_badge"))
            }
        }
        RestVideoPhrase(line, ::perform, Modifier.weight(1f))
    }
}

@Composable
private fun RestVideoPhrase(line: RestVideoLine, onAction: (RestVideoAction) -> Unit, modifier: Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val measurer = rememberTextMeasurer()
    val style = KpknType.caption
    fun annotated(description: String): AnnotatedString = buildAnnotatedString {
        append(description)
        line.actions.forEach { action ->
            append(" · ")
            val actionStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = WizardColors.text, textDecoration = TextDecoration.None)
            withStyle(actionStyle) {
                withLink(LinkAnnotation.Clickable(
                    tag = action.name,
                    styles = TextLinkStyles(actionStyle),
                    linkInteractionListener = { onAction(action) },
                )) { append(action.label.replace(' ', '\u00A0')) }
            }
        }
    }
    BoxWithConstraints(
        modifier.height(48.dp).testTag(RestVideoTextTag).clearAndSetSemantics {
            contentDescription = line.text
            text = layout?.layoutInput?.text ?: AnnotatedString(line.text)
            getTextLayoutResult { results -> layout?.let { results.add(it) } ?: false }
            if (line.actions.isNotEmpty()) {
                role = Role.Button
                onClick(label = line.actions.first().label) { onAction(line.actions.first()); true }
                customActions = line.actions.map { action -> CustomAccessibilityAction(action.label) { onAction(action); true } }
            }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Frase completa cuando cabe; abreviar las repeticiones parciales conserva las acciones y todas las cifras.
        val full = annotated(line.description)
        val displayed = if (!measurer.measure(full, style, maxLines = 2, constraints = constraints).hasVisualOverflow) full
            else annotated(line.compactDescription)
        Box(
            Modifier.fillMaxSize().pointerInput(displayed, onAction) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val measured = layout ?: return@awaitEachGesture
                    val hit = measured.layoutInput.text.getLinkAnnotations(0, measured.layoutInput.text.length)
                        .mapNotNull { range ->
                            val action = (range.item as? LinkAnnotation.Clickable)?.tag?.let { tag -> RestVideoAction.entries.firstOrNull { it.name == tag } } ?: return@mapNotNull null
                            val bounds = measured.getPathForRange(range.start, range.end).getBounds()
                            if (down.position.x in (bounds.left - 6.dp.toPx())..(bounds.right + 6.dp.toPx())) action to bounds else null
                        }.minByOrNull { (_, bounds) -> kotlin.math.abs(down.position.y - (48.dp.toPx() - measured.size.height) / 2f - bounds.center.y) }?.first
                    if (hit != null) {
                        down.consume()
                        if (waitForUpOrCancellation(pass = PointerEventPass.Initial)?.also { it.consume() } != null) onAction(hit)
                    }
                }
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(displayed, Modifier.fillMaxWidth(), color = WizardColors.textMuted, style = style,
                maxLines = 2, overflow = TextOverflow.Clip, onTextLayout = { layout = it })
        }
    }
}

/** La misma insignia estática de UI-1a; su progreso no necesita un bucle de animación. */
@Composable
private fun RestVideoBadge(badge: VideoThumbBadge, modifier: Modifier) {
    Box(
        modifier.height(16.dp).widthIn(min = 16.dp)
            .background(KpknSurface.canvas.copy(alpha = 0.72f), CircleShape)
            .border(1.dp, WizardColors.text.copy(alpha = 0.28f), CircleShape).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (badge is VideoThumbBadge.Analyzing) Canvas(Modifier.size(12.dp)) {
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(WizardColors.text.copy(alpha = 0.20f), style = stroke)
            drawArc(WizardColors.text, -90f, 360f * badge.progress.coerceIn(0, 100) / 100f, false, style = stroke)
        } else Text(
            when (badge) {
                is VideoThumbBadge.Reps -> if (badge.approximate) approxCount(badge.count) else "${badge.count}"
                VideoThumbBadge.Inconclusive -> "?"
                VideoThumbBadge.Failed -> "!"
                else -> ""
            },
            style = KpknType.chip.copy(fontSize = 10.sp, lineHeight = 10.sp), maxLines = 1,
            color = when (badge) { VideoThumbBadge.Failed -> WizardColors.danger; VideoThumbBadge.Inconclusive -> WizardColors.textMuted; else -> WizardColors.text },
        )
    }
}
