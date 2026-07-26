package com.example.kpkn.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * KPKN "Liquid Glass" blur system.
 *
 * Canonical values validated on the session editor roadmap dock. Do NOT tweak per-surface;
 * always reuse [kpknGlassStyle] + [Modifier.kpknGlass] so every glass surface matches.
 * See docs "Blur KPKN.md" for the full contract.
 */
object KpknGlass {
    /** Blur strength. High radius is what makes the frosted look read as glass. */
    val BlurRadius = 40.dp

    /**
     * Subtle white tint that gives the frosted sheen. Kept low so content stays legible.
     * (20% darker pass reduced this from 0.12 -> 0.10.)
     */
    val Tint = Color.White.copy(alpha = 0.10f)

    /**
     * Dark scrim baked into the blur. This is the "darkness" knob.
     * (20% darker pass raised this from 0.22 -> 0.26.)
     */
    val Scrim = Color.Black.copy(alpha = 0.26f)

    /** Fine grain that removes banding on flat gradients. */
    const val NoiseFactor = 0.05f

    /** Hairline border that defines the glass edge. */
    val BorderColor = Color.White.copy(alpha = 0.16f)
    val BorderWidth = 1.dp
}

/**
 * The single source of truth for the KPKN glass appearance.
 * Reuse everywhere instead of building ad-hoc [HazeStyle]s.
 */
fun kpknGlassStyle(): HazeStyle = HazeStyle(
    blurRadius = KpknGlass.BlurRadius,
    tint = HazeTint(KpknGlass.Tint),
    backgroundColor = KpknGlass.Scrim,
    noiseFactor = KpknGlass.NoiseFactor,
)

/**
 * Applies the canonical KPKN glass to any surface.
 *
 * IMPORTANT: the content that should appear blurred behind this surface must be wrapped in a
 * sibling `Modifier.hazeSource(state)` node. The glass node must be drawn AFTER (over) that
 * source — never nested inside it — or nothing will blur.
 *
 * @param withBorder draws the hairline glass edge (default on).
 */
fun Modifier.kpknGlass(
    hazeState: HazeState,
    shape: Shape,
    withBorder: Boolean = true,
): Modifier = this
    .clip(shape)
    .hazeEffect(state = hazeState, style = kpknGlassStyle())
    .then(
        if (withBorder) {
            Modifier.border(width = KpknGlass.BorderWidth, color = KpknGlass.BorderColor, shape = shape)
        } else {
            Modifier
        }
    )
