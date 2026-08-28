package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState
import kotlin.math.min

/**
 * Uniform live-pager scale derived from viewport width/height.
 * Applied equally on X and Y so cards shrink as a whole (never width-only crush).
 */
val LocalLivePagerAdaptScale = compositionLocalOf { 1f }

object WorkoutUiTokens {
    val ScreenHorizontalPadding = 12.dp
    val CardShape = RoundedCornerShape(28.dp)
    val InnerCardShape = RoundedCornerShape(20.dp)
    val ChipShape = RoundedCornerShape(999.dp)
    val DockShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val SectionGap = 12.dp
    val FieldGap = 8.dp
    val TouchTargetMinSize = 48.dp
    /** Minimum readable label size for gym/distance UI. */
    val MinLabelSp = 11.sp
    val MinTouchTarget = 40.dp
    /**
     * Shared proportional scale for every live pager card.
     *
     * The base dimensions remain the previous compact layout; the frame
     * renders them at this factor so MOV/APR and working sets grow together.
     * Final on-screen scale = [LivePagerCardScale] * [LocalLivePagerAdaptScale].
     * Adapt scale is 1f outside god mode so compact cards keep this size.
     */
    val LivePagerCardScale = 1.20f
    val LivePagerBaseHeight = 440.dp
    /**
     * Tallest expected NORMAL set card (plan + report + tabs + footer CTA) before adapt.
     */
    val LivePagerNormalExpandedBaseHeight = 520.dp
    val LivePagerSlotHeight = LivePagerBaseHeight * LivePagerCardScale
    /** Design reference for proportional adapt (typical phone width / card slot). */
    val LivePagerReferenceWidth = 411.dp
    val LivePagerReferenceHeight = 520.dp
    val LivePagerEdgeFadeWidth = 16.dp

    fun liveAdaptScale(availableWidth: Dp, availableHeight: Dp): Float {
        val widthScale = if (availableWidth > 0.dp) {
            (availableWidth / LivePagerReferenceWidth).coerceIn(0.72f, 1f)
        } else {
            1f
        }
        val heightScale = if (availableHeight > 0.dp && availableHeight < 10_000.dp) {
            (availableHeight / LivePagerReferenceHeight).coerceIn(0.72f, 1f)
        } else {
            1f
        }
        return min(widthScale, heightScale)
    }

    @Composable
    fun effectiveLivePagerCardScale(): Float =
        LivePagerCardScale * LocalLivePagerAdaptScale.current

    @Composable
    fun effectiveLivePagerSlotHeight(): Dp =
        LivePagerBaseHeight * effectiveLivePagerCardScale()

    /** Fixed pager slot shared by every page type (NORMAL, MOV, APR, REST). */
    @Composable
    fun effectiveLivePagerStableHeight(): Dp =
        LivePagerNormalExpandedBaseHeight * effectiveLivePagerCardScale()

    const val GodModeEnterMs = 260
    const val GodModeExitMs = 220
    const val GodModeScrimAlpha = 0.32f
    val GodModeBadgeVisual = 20.dp
    val GodModeRoadmapCardHeight = 64.dp
    val GodModePlusCardWidth = 88.dp

    // Semántica de Colores Material 3
    @Composable
    fun setCardColor(): Color = MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun setInnerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun setInnerHighestColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

    @Composable
    fun dangerContainerColor(): Color = MaterialTheme.colorScheme.errorContainer

    @Composable
    fun successColor(): Color = Color(0xFF66BB6A) // Material Green 400

    @Composable
    fun warningColor(): Color = Color(0xFFFFD740) // Material Amber A200

    @Composable
    fun dangerColor(): Color = Color(0xFFFF5252)

    @Composable
    fun infoBlue(): Color = Color(0xFF448AFF)

    /** Readiness / recovery score → green / amber / red. */
    fun readinessColor(score: Int): Color = when {
        score >= 75 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFFC107)
        else -> Color(0xFFFF5252)
    }
}

@Composable
fun WorkoutGlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    shape: Shape = WorkoutUiTokens.CardShape,
    border: BorderStroke? = BorderStroke(1.dp, KpknGlass.BorderColor),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .kpknGlassOrFallback(hazeState, shape, withBorder = false)
            .then(
                if (border != null) Modifier.border(border, shape)
                else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun WorkoutMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.heightIn(min = WorkoutUiTokens.TouchTargetMinSize),
        shape = WorkoutUiTokens.InnerCardShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
                if (badgeText != null) {
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun WorkoutPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val resolvedContentColor = contentColor
        ?: com.example.kpkn.screens.sessioneditor.contentOn(containerColor)
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = WorkoutUiTokens.ChipShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = resolvedContentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
