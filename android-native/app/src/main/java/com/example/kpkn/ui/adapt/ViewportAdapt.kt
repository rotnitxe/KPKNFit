package com.example.kpkn.ui.adapt

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
