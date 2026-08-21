package com.example.kpkn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Root [HazeState] provided by MainActivity. Glass surfaces (sheets, dialogs, docks)
 * should read this instead of inventing per-screen states when possible.
 * Null = no blur source available; callers must fall back to a solid dark scrim.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * KPKN **DarkMica** — dark gray panel with light blur + translucency.
 *
 * Not Liquid Glass (high blur + near-clear tint) and not a flat solid.
 *
 * Recipe:
 * 1. Semi-opaque dark [Base] underlay.
 * 2. Live Haze blur on top at [EffectAlpha] so the mica still breathes.
 * 3. [Tint] darkens that blur layer (Haze draws tint *over* the sample).
 *
 * Always reuse [Modifier.kpknGlass] / [Modifier.kpknHazeEffect]. See "Blur KPKN.md".
 */
object KpknGlass {
    /**
     * Defocus strength only — raise this for “more blur”, never [EffectAlpha].
     * History: 40 → 14 → 32 → 48 → 72 → 100.
     */
    val BlurRadius = 100.dp

    /**
     * Opacity of the live blur layer over [Base].
     * Keep stable when tuning blur; raising this adds transparency, not defocus.
     */
    const val EffectAlpha = 0.42f

    /**
     * Neutral near-black tint over the blur sample (R=G=B, no blue cast).
     */
    val Tint = Color(0xFF0E0E0E).copy(alpha = 0.58f)

    /**
     * Neutral near-black mica underlay — slightly less transparent than before.
     */
    val Base = Color(0xFF0E0E0E).copy(alpha = 0.94f)

    /** Compatibility alias. */
    val Scrim: Color get() = Base

    const val NoiseFactor = 0.02f

    val BorderColor = Color.White.copy(alpha = 0.05f)
    val BorderWidth = 1.dp

    /** Near-opaque fallback when blur source is missing. */
    val FallbackScrim = Color(0xFF0E0E0E).copy(alpha = 0.97f)

    val WindowFallbackScrim = Color(0xF80E0E0E)

    val SheetCornerRadius = 28.dp
    val DialogCornerRadius = 28.dp
}

/**
 * Haze style for the live blur layer over [KpknGlass.Base].
 */
fun kpknGlassStyle(): HazeStyle = HazeStyle(
    blurRadius = KpknGlass.BlurRadius,
    tint = HazeTint(KpknGlass.Tint),
    backgroundColor = Color.Transparent,
    noiseFactor = KpknGlass.NoiseFactor,
    fallbackTint = HazeTint(KpknGlass.Tint),
)

/**
 * DarkMica: dark translucent base + light live blur.
 */
fun Modifier.kpknHazeEffect(hazeState: HazeState): Modifier = this
    .background(KpknGlass.Base)
    .hazeEffect(state = hazeState, style = kpknGlassStyle()) {
        alpha = KpknGlass.EffectAlpha
    }

/**
 * Applies canonical KPKN DarkMica.
 *
 * IMPORTANT: blur source must be a sibling `hazeSource` drawn before this node.
 */
fun Modifier.kpknGlass(
    hazeState: HazeState,
    shape: Shape,
    withBorder: Boolean = true,
    additionalScrim: Color = Color.Transparent,
): Modifier = this
    .clip(shape)
    .kpknHazeEffect(hazeState)
    .then(additionalScrimModifier(additionalScrim))
    .then(
        if (withBorder) {
            Modifier.border(width = KpknGlass.BorderWidth, color = KpknGlass.BorderColor, shape = shape)
        } else {
            Modifier
        },
    )

fun Modifier.kpknGlassOrFallback(
    hazeState: HazeState?,
    shape: Shape,
    withBorder: Boolean = true,
    additionalScrim: Color = Color.Transparent,
): Modifier = if (hazeState != null) {
    kpknGlass(
        hazeState,
        shape,
        withBorder = withBorder,
        additionalScrim = additionalScrim,
    )
} else {
    this
        .clip(shape)
        .background(KpknGlass.FallbackScrim)
        .then(additionalScrimModifier(additionalScrim))
        .then(
            if (withBorder) {
                Modifier.border(width = KpknGlass.BorderWidth, color = KpknGlass.BorderColor, shape = shape)
            } else {
                Modifier
            },
        )
}

private fun additionalScrimModifier(color: Color): Modifier = if (color.alpha > 0f) {
    Modifier.drawWithContent {
        drawRect(color = color)
        drawContent()
    }
} else {
    Modifier
}

fun Modifier.kpknWindowGlass(
    shape: Shape,
    withBorder: Boolean = true,
): Modifier = this
    .clip(shape)
    .background(KpknGlass.WindowFallbackScrim)
    .then(
        if (withBorder) {
            Modifier.border(
                width = KpknGlass.BorderWidth,
                color = KpknGlass.BorderColor,
                shape = shape,
            )
        } else {
            Modifier
        },
    )
