package com.example.kpkn.ui.adapt

import kotlin.math.max
import kotlin.math.min

enum class ViewportDensityClass {
    Compact,
    Regular,
    Comfortable,
}

/**
 * Snapshot of the current viewport versus the design canvas (411 × 891 dp).
 *
 * [uniformScale] is the Photoshop-corner factor: `min(width/refW, height/refH)`,
 * never applied on a single axis. Leftover space is margin, not stretch.
 *
 * [shouldReflow] is true only when that factor would go below [ViewportAdaptMath.MIN_SCALE]
 * — then structure may change (stack, shorten labels) instead of shrinking further.
 */
data class ViewportAdapt(
    val widthDp: Float,
    val heightDp: Float,
    val uniformScale: Float,
    val aspect: Float,
    val referenceAspect: Float,
    val densityClass: ViewportDensityClass,
    val shouldReflow: Boolean,
) {
    companion object {
        val Identity: ViewportAdapt = ViewportAdaptMath.compute(
            widthDp = ViewportAdaptMath.REF_WIDTH_DP,
            heightDp = ViewportAdaptMath.REF_HEIGHT_DP,
        )
    }
}

object ViewportAdaptMath {
    const val REF_WIDTH_DP = 411f
    const val REF_HEIGHT_DP = 891f
    const val MIN_SCALE = 0.72f
    const val MAX_SCALE = 1.12f
    const val COMPACT_WIDTH_DP = 360f
    const val COMFORTABLE_WIDTH_DP = 400f
    const val MIN_LABEL_SP = 11f
    const val MIN_TOUCH_DP = 40f

    const val LIVE_PAGER_REF_WIDTH_DP = 411f
    const val LIVE_PAGER_REF_HEIGHT_DP = 520f

    fun compute(
        widthDp: Float,
        heightDp: Float,
        refWidthDp: Float = REF_WIDTH_DP,
        refHeightDp: Float = REF_HEIGHT_DP,
        minScale: Float = MIN_SCALE,
        maxScale: Float = MAX_SCALE,
    ): ViewportAdapt {
        val safeW = widthDp.coerceAtLeast(0f)
        val safeH = heightDp.coerceAtLeast(0f)
        val widthScale = if (safeW > 0f) safeW / refWidthDp else 1f
        val heightScale = if (safeH > 0f && safeH < 10_000f) safeH / refHeightDp else 1f
        val raw = min(widthScale, heightScale)
        val uniformScale = raw.coerceIn(minScale, maxScale)
        val aspect = if (safeH > 0f) safeW / safeH else refWidthDp / refHeightDp
        val referenceAspect = refWidthDp / refHeightDp
        val densityClass = when {
            safeW <= COMPACT_WIDTH_DP -> ViewportDensityClass.Compact
            safeW <= COMFORTABLE_WIDTH_DP -> ViewportDensityClass.Regular
            else -> ViewportDensityClass.Comfortable
        }
        return ViewportAdapt(
            widthDp = safeW,
            heightDp = safeH,
            uniformScale = uniformScale,
            aspect = aspect,
            referenceAspect = referenceAspect,
            densityClass = densityClass,
            shouldReflow = raw < minScale,
        )
    }
}

/**
 * Live-workout chrome restore from leftover axes, not from raw width buckets.
 *
 * [leftoverY] (tall-narrow / Flip inner): unused height after uniform XY scale.
 * [leftoverX] (short / square / landscape): unused width after uniform XY scale.
 *
 * Does not change [ViewportAdapt.uniformScale]; other screens keep margin-as-leftover.
 */
data class LiveViewportPolicy(
    val tNarrow: Float,
    val tShort: Float,
    val leftoverX: Float,
    val leftoverY: Float,
    val headerMul: Float,
    val cardWidthMul: Float,
    val cardHeightMul: Float,
    val cardPageFillX: Float,
    val topNudgeFraction: Float,
    val minPeekFraction: Float,
) {
    companion object {
        val Identity: LiveViewportPolicy = LiveViewportPolicyMath.identity()
    }
}

object LiveViewportPolicyMath {
    const val NARROW_LEFTOVER_Y_REF = 0.112f
    const val SHORT_LEFTOVER_X_REF = 0.20f
    const val HEADER_NARROW_BOOST = 0.20f
    const val HEADER_SHORT_COMPACT = 0.12f
    /** Visual page width vs 13.9 (1.104): uniform 18% corner scale → 1.303. */
    const val CARD_WIDTH_BOOST = 0.3027f
    /** Visual height vs 13.9 (1.464): uniform 18% corner scale → 1.728. */
    const val CARD_HEIGHT_BOOST = 0.7275f
    const val NUDGE_CANONICAL = 0.05f
    const val NUDGE_NARROW = 0.04f
    const val NUDGE_SHORT = 0.03f
    const val PEEK_FLOOR = 0.12f
    const val PEEK_FLOOR_NARROW = 0.07f
    const val PEEK_FLOOR_SHORT = 0.08f
    const val PEEK_CEILING = 0.26f
    const val PEEK_SCALE_FLOOR = 0.14f
    const val PEEK_BASE_WIDE = 0.20f
    const val PEEK_BASE_COMPACT_WIDTH = 0.22f
    const val PEEK_BASE_REFLOW = 0.26f
    const val COMPACT_WIDTH_THRESHOLD_DP = 420f
    const val LIVE_PAGER_BASE_HEIGHT_DP = 480f
    const val HEADER_COMPACT_SCALE = 0.80f

    fun identity(): LiveViewportPolicy = LiveViewportPolicy(
        tNarrow = 0f,
        tShort = 0f,
        leftoverX = 0f,
        leftoverY = 0f,
        headerMul = 1f,
        cardWidthMul = 1f,
        cardHeightMul = 1f,
        cardPageFillX = 0f,
        topNudgeFraction = NUDGE_CANONICAL,
        minPeekFraction = PEEK_FLOOR,
    )

    fun leftoverY(widthScale: Float, heightScale: Float): Float =
        (heightScale - widthScale).coerceAtLeast(0f)

    fun leftoverX(widthScale: Float, heightScale: Float): Float =
        (widthScale - heightScale).coerceAtLeast(0f)

    fun headerScale(uniformScale: Float, headerMul: Float): Float =
        HEADER_COMPACT_SCALE * uniformScale * headerMul

    fun compute(
        widthDp: Float,
        heightDp: Float,
        availablePagerHeightDp: Float = Float.POSITIVE_INFINITY,
        cardScale: Float = 1f,
        livePagerBaseHeightDp: Float = LIVE_PAGER_BASE_HEIGHT_DP,
        godModeActive: Boolean = false,
        refWidthDp: Float = ViewportAdaptMath.REF_WIDTH_DP,
        refHeightDp: Float = ViewportAdaptMath.REF_HEIGHT_DP,
    ): LiveViewportPolicy {
        if (godModeActive) return identity()
        val safeW = widthDp.coerceAtLeast(0f)
        val safeH = heightDp.coerceAtLeast(0f)
        val widthScale = if (safeW > 0f) safeW / refWidthDp else 1f
        val heightScale = if (safeH > 0f && safeH < 10_000f) safeH / refHeightDp else 1f
        val leftoverY = (heightScale - widthScale).coerceAtLeast(0f)
        val leftoverX = (widthScale - heightScale).coerceAtLeast(0f)
        val tNarrow = (leftoverY / NARROW_LEFTOVER_Y_REF).coerceIn(0f, 1f)
        val tShort = (leftoverX / SHORT_LEFTOVER_X_REF).coerceIn(0f, 1f)
        val headerMul = (1f + HEADER_NARROW_BOOST * tNarrow) * (1f - HEADER_SHORT_COMPACT * tShort)
        val cardWidthMul = 1f + CARD_WIDTH_BOOST * max(tNarrow, tShort * 0.85f)
        val unclampedHeightMul = if (tShort > 0f) {
            1f
        } else {
            1f + CARD_HEIGHT_BOOST * tNarrow
        }
        val topNudgeFraction = (
            NUDGE_CANONICAL -
                (NUDGE_CANONICAL - NUDGE_NARROW) * tNarrow -
                (NUDGE_CANONICAL - NUDGE_SHORT) * tShort
            ).coerceIn(NUDGE_SHORT, NUDGE_CANONICAL)
        val minPeekFraction = (
            PEEK_FLOOR -
                (PEEK_FLOOR - PEEK_FLOOR_NARROW) * tNarrow -
                (PEEK_FLOOR - PEEK_FLOOR_SHORT) * tShort
            ).coerceIn(minOf(PEEK_FLOOR_NARROW, PEEK_FLOOR_SHORT), PEEK_FLOOR)
        val cardHeightMul = clampCardHeightMul(
            unclamped = unclampedHeightMul,
            livePagerBaseHeightDp = livePagerBaseHeightDp,
            cardScale = cardScale,
            topNudgeFraction = topNudgeFraction,
            availablePagerHeightDp = availablePagerHeightDp,
        )
        return LiveViewportPolicy(
            tNarrow = tNarrow,
            tShort = tShort,
            leftoverX = leftoverX,
            leftoverY = leftoverY,
            headerMul = headerMul,
            cardWidthMul = cardWidthMul,
            cardHeightMul = cardHeightMul,
            cardPageFillX = tNarrow,
            topNudgeFraction = topNudgeFraction,
            minPeekFraction = minPeekFraction,
        )
    }

    fun clampCardHeightMul(
        unclamped: Float,
        livePagerBaseHeightDp: Float,
        cardScale: Float,
        topNudgeFraction: Float,
        availablePagerHeightDp: Float,
    ): Float {
        if (!availablePagerHeightDp.isFinite() || availablePagerHeightDp <= 0f) return unclamped
        // Never flatten below identity. A tight pager hole used to drive mul < 1
        // after first layout (readiness sheet → measured dock), cancelling the
        // extra Y growth. Only cap leftover-Y *boost* (unclamped > 1).
        if (unclamped <= 1f) return unclamped
        val totalAtOne = livePagerBaseHeightDp * cardScale.coerceAtLeast(0.01f) * (1f + topNudgeFraction)
        if (totalAtOne <= 0f) return unclamped
        val maxMul = availablePagerHeightDp / totalAtOne
        return unclamped.coerceAtMost(maxMul.coerceAtLeast(1f))
    }

    fun livePagerScaleX(adaptScale: Float, pageFillX: Float): Float {
        if (adaptScale >= 1f) return adaptScale
        return (adaptScale + (1f - adaptScale) * pageFillX.coerceIn(0f, 1f)).coerceAtMost(1f)
    }

    fun livePagerPeekFraction(
        availableWidthDp: Float,
        cardScale: Float,
        shouldReflow: Boolean,
        cardWidthMul: Float,
        minPeekFraction: Float,
        maxPeekFraction: Float = PEEK_CEILING,
    ): Float {
        val basePeek = when {
            shouldReflow -> PEEK_BASE_REFLOW
            availableWidthDp < COMPACT_WIDTH_THRESHOLD_DP -> PEEK_BASE_COMPACT_WIDTH
            else -> PEEK_BASE_WIDE
        }
        // scale<1 used to *increase* peek (thinner cards on Flip). Keep
        // widening only when the card is scaled up past the page.
        val scaledPeek = if (cardScale >= 1f) {
            (basePeek * cardScale - (cardScale - 1f) / 2f)
                .coerceIn(PEEK_SCALE_FLOOR, maxPeekFraction)
        } else {
            basePeek.coerceIn(minPeekFraction, maxPeekFraction)
        }
        val maxPageFrac = (1f - 2f * minPeekFraction).coerceAtLeast(0f)
        val targetPageFrac = ((1f - 2f * scaledPeek) * cardWidthMul).coerceIn(0f, maxPageFrac)
        val adjusted = (1f - targetPageFrac) / 2f
        return adjusted.coerceIn(minPeekFraction, maxPeekFraction)
    }
}
