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
import androidx.compose.runtime.MutableIntState
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
import com.example.kpkn.ui.adapt.ViewportAdapt
import com.example.kpkn.ui.adapt.ViewportAdaptMath
import com.example.kpkn.ui.adapt.LiveViewportPolicy
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState

/**
 * Uniform live-pager scale derived from the pager hole (width/height).
 * Defaults to the window [LocalViewportAdapt] so callers outside the pager
 * still track the shared X+Y factor.
 */
val LocalLivePagerAdaptScale = compositionLocalOf { 1f }

val LocalLivePagerShouldReflow = compositionLocalOf { false }

val LocalLiveViewportPolicy = compositionLocalOf { LiveViewportPolicy.Identity }

/**
 * Wrap height in px of SetInputCardV2 (the visible Reportar serie stack).
 * Prep and rest live cards clamp to this; not [WorkoutUiTokens.LivePagerBaseHeight].
 */
val LocalLivePagerWorkingSetVisualHeightPx = compositionLocalOf<MutableIntState?> { null }

/** First measured working-set wrap wins so MOV/APR cannot inflate past S1. */
internal fun publishLivePagerWorkingSetVisualHeight(holder: MutableIntState?, heightPx: Int) {
    if (holder == null || heightPx <= 0) return
    if (holder.intValue == 0) {
        holder.intValue = heightPx
    }
}

/** Prep / MOV / APR / REST use the exact working-set Surface height. Zero means "not measured yet". */
internal fun resolveLivePagerPrepCardHeightPx(measuredWorkingSetHeightPx: Int, floorPx: Int): Int {
    if (measuredWorkingSetHeightPx > 0) return measuredWorkingSetHeightPx
    return floorPx.coerceAtLeast(0)
}

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
     * Compact 1.20 × 0.85, then two +25% uniform XY steps, then independent axes:
     * Y × [LivePagerCardHeightGrowFactor] (−25%), X × [LivePagerCardWidthSlimFactor]
     * (prior slim × extra −10% × extra −7%).
     * Leftover-Y on 22:9 must not stretch height or refill width.
     */
    val LivePagerCardScale = 1.20f * 0.85f * 1.25f * 1.25f
    /** 0.89 slim, then −10%, then −7% on X. */
    const val LivePagerCardWidthSlimFactor = 0.89f * 0.90f * 0.93f
    /** −25% on Y versus the uniform XY scale. */
    const val LivePagerCardHeightGrowFactor = 0.75f
    /**
     * Height budget for the live card contents, including the in-card record
     * action and the plan/technique controls below it.
     */
    val LivePagerBaseHeight = 480.dp
    /**
     * Tallest expected NORMAL set card (plan + report + tabs + footer CTA) before adapt.
     */
    val LivePagerNormalExpandedBaseHeight = 520.dp
    val LivePagerSlotHeight =
        LivePagerBaseHeight * LivePagerCardScale * LivePagerCardHeightGrowFactor
    /**
     * Extra empty space above every live pager card (5% of the card slot).
     * Keeps a small gap under the header without dropping the card into the cockpit.
     */
    const val LivePagerCardTopNudgeFraction = 0.05f
    /**
     * Extra gap under the live relator so the series card sits in the band
     * between the message and the cockpit. Does not change the 5% nudge.
     */
    val LivePagerRelatorClearance = 48.dp

    fun liveRelatorBandHeight(
        topNudgeFraction: Float = LivePagerCardTopNudgeFraction,
        uniformScale: Float = 1f,
    ): Dp {
        val slot = LivePagerNormalExpandedBaseHeight *
            LivePagerCardScale *
            LivePagerCardHeightGrowFactor *
            uniformScale
        return slot * topNudgeFraction + LivePagerRelatorClearance
    }
    /** Design reference for proportional adapt (typical phone width / card slot). */
    val LivePagerReferenceWidth = ViewportAdaptMath.LIVE_PAGER_REF_WIDTH_DP.dp
    val LivePagerReferenceHeight = ViewportAdaptMath.LIVE_PAGER_REF_HEIGHT_DP.dp
    val LivePagerVeryNarrowWidth = ViewportAdaptMath.COMPACT_WIDTH_DP.dp
    /** Same edge-fade depth as the exercise carousel's chrome. */
    val LivePagerEdgeFadeWidth = 52.dp

    fun livePagerViewportAdapt(
        availableWidth: Dp,
        availableHeight: Dp,
        godModeActive: Boolean,
    ): ViewportAdapt {
        val computed = ViewportAdaptMath.compute(
            widthDp = availableWidth.value,
            heightDp = availableHeight.value,
            refWidthDp = ViewportAdaptMath.LIVE_PAGER_REF_WIDTH_DP,
            refHeightDp = ViewportAdaptMath.LIVE_PAGER_REF_HEIGHT_DP,
        )
        return if (godModeActive) computed.copy(uniformScale = 1f, shouldReflow = false) else computed
    }

    fun liveAdaptScale(availableWidth: Dp, availableHeight: Dp): Float =
        livePagerViewportAdapt(
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            godModeActive = false,
        ).uniformScale

    fun livePagerViewportAdaptScale(
        availableWidth: Dp,
        availableHeight: Dp,
        godModeActive: Boolean,
    ): Float = livePagerViewportAdapt(
        availableWidth = availableWidth,
        availableHeight = availableHeight,
        godModeActive = godModeActive,
    ).uniformScale

    @Composable
    fun effectiveLivePagerCardScale(): Float =
        LivePagerCardScale * LocalLivePagerAdaptScale.current

    @Composable
    fun effectiveLivePagerCardScaleX(): Float =
        effectiveLivePagerCardScale() * LivePagerCardWidthSlimFactor

    @Composable
    fun effectiveLivePagerCardScaleY(): Float =
        effectiveLivePagerCardScale() * LivePagerCardHeightGrowFactor

    @Composable
    fun effectiveLivePagerSlotHeight(): Dp =
        LivePagerBaseHeight * effectiveLivePagerCardScaleY()

    /** Fixed pager slot shared by every page type (NORMAL, MOV, APR, REST). */
    @Composable
    fun effectiveLivePagerStableHeight(): Dp =
        LivePagerNormalExpandedBaseHeight * effectiveLivePagerCardScaleY()

    const val GodModeEnterMs = 260
    const val GodModeExitMs = 220
    const val GodModeScrimAlpha = 0.55f
    const val GodModeCardScale = 0.58f
    val GodModeCardBlur = 18.dp
    val GodModeBadgeVisual = 20.dp
    val GodModeRoadmapCardHeight = 48.dp
    val GodModePlusCardWidth = 88.dp
    val LiveRoadmapCarouselMiniInset = 14.dp

    fun liveRoadmapCarouselCardHeight(uniformScale: Float): Dp =
        GodModeRoadmapCardHeight * uniformScale

    fun liveRoadmapCarouselMiniCardHeight(uniformScale: Float): Dp =
        (GodModeRoadmapCardHeight - LiveRoadmapCarouselMiniInset)
            .coerceAtLeast(32.dp) * uniformScale

    /** Clouds (28) + gap (4) + stepper row (40). */
    val LiveCockpitStepperHeight = 72.dp
    val LiveCockpitHandleHeight = MinTouchTarget
    val LiveCockpitStepperSlotTopPadding = 4.dp
    val LiveCockpitStepperSlotBottomPadding = 12.dp
    val LiveCockpitCarouselBottomPadding = 12.dp

    fun liveCockpitStepperHeight(uniformScale: Float): Dp =
        LiveCockpitStepperHeight * uniformScale.coerceAtLeast(0.01f)

    /** Compact cockpit Y: handle + stepper slot + carousel. Does not include the expanded sheet. */
    fun liveCockpitCompactHeight(uniformScale: Float): Dp {
        val scale = uniformScale.coerceAtLeast(0.01f)
        return LiveCockpitHandleHeight +
            LiveCockpitStepperSlotTopPadding +
            liveCockpitStepperHeight(scale) +
            LiveCockpitStepperSlotBottomPadding +
            liveRoadmapCarouselCardHeight(scale) +
            LiveCockpitCarouselBottomPadding
    }

    // Semántica de Colores Material 3
    /** Serie live: gris ennegrecido para no competir con el acento de sesión. */
    fun setCardColor(): Color = Color(0xFF121212)

    fun setInnerColor(): Color = Color(0xFF1A1A1A)

    fun setInnerHighestColor(): Color = Color(0xFF242424)

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

    /** Live carousel glows: tones of the session accent, not green/red/blue. */
    fun carouselGlowOnPlan(accent: Color): Color = accent.copy(alpha = 0.52f)

    fun carouselGlowBelowPlan(accent: Color): Color =
        androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.32f).copy(alpha = 0.50f)

    fun carouselGlowAbovePlan(accent: Color): Color =
        androidx.compose.ui.graphics.lerp(accent, Color.White, 0.28f).copy(alpha = 0.52f)

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
