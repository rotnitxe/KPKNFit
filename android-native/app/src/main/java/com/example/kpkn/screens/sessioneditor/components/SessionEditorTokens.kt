package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.ViewportAdapt
import com.example.kpkn.ui.adapt.ViewportAdaptMath
import com.example.kpkn.ui.adapt.ViewportDensityClass
import com.example.kpkn.ui.adapt.adapt

enum class SessionEditorBreakpoint {
    Compact,
    Normal,
    Comfortable,
}

data class SessionEditorSpacing(
    val screenPadding: Dp,
    val cardPadding: Dp,
    val cardGap: Dp,
    val chipHeight: Dp,
    val heroCompactThreshold: Int,
    val bottomContentPadding: Dp,
    val fabBottomPadding: Dp,
    val touchTargetMin: Dp,
    val cardShape: RoundedCornerShape,
    val setCardShape: RoundedCornerShape,
)

object SessionEditorTokens {
    fun spacing(breakpoint: SessionEditorBreakpoint): SessionEditorSpacing = when (breakpoint) {
        SessionEditorBreakpoint.Compact -> SessionEditorSpacing(
            screenPadding = 12.dp,
            cardPadding = 10.dp,
            cardGap = 6.dp,
            chipHeight = 32.dp,
            heroCompactThreshold = 120,
            bottomContentPadding = 96.dp,
            fabBottomPadding = 88.dp,
            touchTargetMin = 48.dp,
            cardShape = RoundedCornerShape(16.dp),
            setCardShape = RoundedCornerShape(14.dp),
        )
        SessionEditorBreakpoint.Normal -> SessionEditorSpacing(
            screenPadding = 16.dp,
            cardPadding = 12.dp,
            cardGap = 8.dp,
            chipHeight = 36.dp,
            heroCompactThreshold = 160,
            bottomContentPadding = 110.dp,
            fabBottomPadding = 104.dp,
            touchTargetMin = 48.dp,
            cardShape = RoundedCornerShape(18.dp),
            setCardShape = RoundedCornerShape(16.dp),
        )
        SessionEditorBreakpoint.Comfortable -> SessionEditorSpacing(
            screenPadding = 20.dp,
            cardPadding = 14.dp,
            cardGap = 10.dp,
            chipHeight = 40.dp,
            heroCompactThreshold = 180,
            bottomContentPadding = 120.dp,
            fabBottomPadding = 112.dp,
            touchTargetMin = 48.dp,
            cardShape = RoundedCornerShape(20.dp),
            setCardShape = RoundedCornerShape(18.dp),
        )
    }

    fun breakpointFor(widthDp: Int): SessionEditorBreakpoint = when {
        widthDp <= 360 -> SessionEditorBreakpoint.Compact
        widthDp <= 400 -> SessionEditorBreakpoint.Normal
        else -> SessionEditorBreakpoint.Comfortable
    }

    fun breakpointFor(adapt: ViewportAdapt): SessionEditorBreakpoint = when {
        adapt.shouldReflow || adapt.densityClass == ViewportDensityClass.Compact ->
            SessionEditorBreakpoint.Compact
        adapt.densityClass == ViewportDensityClass.Regular ->
            SessionEditorBreakpoint.Normal
        else -> SessionEditorBreakpoint.Comfortable
    }

    fun spacing(breakpoint: SessionEditorBreakpoint, adapt: ViewportAdapt): SessionEditorSpacing {
        val base = spacing(breakpoint)
        val minTouch = ViewportAdaptMath.MIN_TOUCH_DP.dp
        val cardRadius = when (breakpoint) {
            SessionEditorBreakpoint.Compact -> 16.dp
            SessionEditorBreakpoint.Normal -> 18.dp
            SessionEditorBreakpoint.Comfortable -> 20.dp
        }.adapt(adapt)
        val setRadius = when (breakpoint) {
            SessionEditorBreakpoint.Compact -> 14.dp
            SessionEditorBreakpoint.Normal -> 16.dp
            SessionEditorBreakpoint.Comfortable -> 18.dp
        }.adapt(adapt)
        return base.copy(
            screenPadding = base.screenPadding.adapt(adapt),
            cardPadding = base.cardPadding.adapt(adapt),
            cardGap = base.cardGap.adapt(adapt),
            chipHeight = base.chipHeight.adapt(adapt),
            bottomContentPadding = base.bottomContentPadding.adapt(adapt),
            fabBottomPadding = base.fabBottomPadding.adapt(adapt),
            touchTargetMin = base.touchTargetMin.adapt(adapt, min = minTouch),
            cardShape = RoundedCornerShape(cardRadius),
            setCardShape = RoundedCornerShape(setRadius),
        )
    }
}

@Composable
fun rememberSessionEditorSpacing(): SessionEditorSpacing {
    val adapt = LocalViewportAdapt.current
    return remember(adapt) {
        SessionEditorTokens.spacing(SessionEditorTokens.breakpointFor(adapt), adapt)
    }
}

@Composable
fun rememberSessionEditorBreakpoint(): SessionEditorBreakpoint {
    val adapt = LocalViewportAdapt.current
    return remember(adapt) { SessionEditorTokens.breakpointFor(adapt) }
}
