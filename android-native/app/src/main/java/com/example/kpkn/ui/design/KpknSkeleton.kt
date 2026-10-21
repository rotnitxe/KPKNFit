package com.example.kpkn.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/**
 * Bloque de carga (KPKN_UI_V2): relleno `inset` con un brillo que cruza de izquierda a derecha en 1,2 s. Con movimiento reducido, el
 * bloque queda quieto y sin brillo.
 */
@Composable
fun KpknSkeleton(modifier: Modifier = Modifier, shape: Shape = KpknShape.module) {
    if (wizardReducedMotion()) {
        Box(modifier = modifier.clip(shape).background(KpknSurface.inset, shape))
    } else {
        KpknSkeletonShimmer(modifier = modifier, shape = shape)
    }
}

/** Bloque con el brillo animado. El bucle se lee solo al dibujar, así que no recompone por cuadro. */
@Composable
private fun KpknSkeletonShimmer(modifier: Modifier, shape: Shape) {
    val transition = rememberInfiniteTransition(label = "kpknSkeleton")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "kpknSkeletonSweep",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(KpknSurface.inset, shape)
            .drawWithContent {
                drawContent()
                // Franja de brillo del 45 % del ancho, que entra por la izquierda y sale por la derecha.
                val band = size.width * 0.45f
                val start = -band + (size.width + band) * sweep
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                        startX = start,
                        endX = start + band,
                    ),
                )
            },
    )
}

/**
 * Módulo de carga (KPKN_UI_V2): una cabecera corta y [lines] líneas de texto, con el mismo relleno y filete que un módulo real, para que
 * la pantalla no salte al llegar los datos.
 */
@Composable
fun KpknSkeletonModule(lines: Int = 3, modifier: Modifier = Modifier) {
    val count = lines.coerceAtLeast(1)
    val widths = listOf(1f, 0.82f, 0.6f)
    KpknModule(modifier = modifier) {
        KpknSkeleton(modifier = Modifier.width(96.dp).height(12.dp), shape = KpknShape.pill)
        Spacer(Modifier.height(16.dp))
        for (i in 0 until count) {
            KpknSkeleton(
                modifier = Modifier.fillMaxWidth(widths[i % widths.size]).height(14.dp),
                shape = KpknShape.pill,
            )
            if (i < count - 1) Spacer(Modifier.height(10.dp))
        }
    }
}
