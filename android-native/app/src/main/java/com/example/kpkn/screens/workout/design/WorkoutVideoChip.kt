package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

internal const val WorkoutVideoChipTag = "workout_video_chip"
internal const val WorkoutVideoMenuTag = "workout_video_menu"

internal enum class VideoChipTone { TEXT, ACCENT, MUTED, DANGER }
internal data class VideoChipContent(val text: String, val tone: VideoChipTone = VideoChipTone.TEXT, val progress: Int? = null)

internal fun workoutVideoChipContent(state: SetVideoAnalysisState): VideoChipContent? = when (state) {
    is SetVideoAnalysisState.Analyzing -> state.progress.coerceIn(0, 100).let { VideoChipContent("$it\u00A0%", progress = it) }
    is SetVideoAnalysisState.Suggest -> when (state.confidence) {
        CountConfidence.ALTA -> VideoChipContent("${state.reps}" + if (state.partials > 0) " ·${state.partials}p" else "")
        CountConfidence.MEDIA -> VideoChipContent(approxCount(state.reps))
        CountConfidence.BAJA -> VideoChipContent("?", VideoChipTone.MUTED)
    }
    is SetVideoAnalysisState.Mismatch -> when (state.confidence) {
        CountConfidence.ALTA -> VideoChipContent("${state.counted}", if (state.countsDiffer) VideoChipTone.ACCENT else VideoChipTone.TEXT)
        CountConfidence.MEDIA -> VideoChipContent(approxCount(state.counted))
        CountConfidence.BAJA -> VideoChipContent("?", VideoChipTone.MUTED)
    }
    is SetVideoAnalysisState.Agrees -> VideoChipContent("✓ ${state.counted}")
    SetVideoAnalysisState.Inconclusive -> VideoChipContent("?", VideoChipTone.MUTED)
    is SetVideoAnalysisState.Failed -> VideoChipContent("!", VideoChipTone.DANGER)
    is SetVideoAnalysisState.Unsupported -> null
}

/** Píldora sin estado. Clickable extiende el área táctil a 48 dp sin reservar esa altura en la fila. */
@Composable
internal fun WorkoutVideoChip(
    value: SetVideoAnalysisUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sessionAccentColor: Color = SymbolPalette.musculo,
) {
    val content = workoutVideoChipContent(value.state) ?: return
    val color = when (content.tone) {
        VideoChipTone.TEXT -> WizardColors.text
        VideoChipTone.ACCENT -> sessionAccentColor
        VideoChipTone.MUTED -> WizardColors.textMuted
        VideoChipTone.DANGER -> WizardColors.danger
    }
    Row(
        modifier.widthIn(min = 48.dp).height(24.dp)
            .clip(CircleShape)
            .background(Color(0xFF1F1F1F))
            .border(1.dp, WizardColors.text.copy(alpha = 0.12f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(WorkoutVideoChipTag)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "${workoutVideoRepsText(value.state)}. Toca para ver las opciones"
                onClick(label = "Ver opciones del vídeo") { onClick(); true }
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KpknLineIcons.Play, contentDescription = null, tint = WizardColors.textMuted, modifier = Modifier.size(12.dp))
        if (content.progress != null) {
            Canvas(Modifier.size(12.dp)) {
                val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(WizardColors.text.copy(alpha = 0.12f), style = stroke)
                drawArc(color, -90f, content.progress * 3.6f, useCenter = false, style = stroke)
            }
        }
        Text(content.text, style = KpknType.chip.copy(fontSize = 11.sp, lineHeight = 14.sp), color = color, maxLines = 1)
    }
}

/** Alinea el menú al borde final del chip y lo mantiene dentro de la ventana, sin cubrir su ancla. */
internal class WorkoutVideoMenuPositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val end = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = end.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - gap - popupContentSize.height
        return IntOffset(x, y.coerceAtLeast(0))
    }
}
