package com.example.kpkn.screens.workout.technique.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Píldora del cronómetro durante la grabación de técnica (VT8a).
 * Muestra «● 0:12» con el punto rojo y la duración formateada en minutos y segundos.
 */
@Composable
fun RecTimerPill(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val minutes = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formatted = "%d:%02d".format(minutes, secs)

    val transition = rememberInfiniteTransition(label = "recDotPulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recDotAlpha",
    )

    Surface(
        modifier = modifier,
        shape = KpknShape.pill,
        color = KpknSurface.module.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(dotAlpha),
                shape = KpknShape.pill,
                color = WizardColors.danger,
            ) {}
            Text(
                text = formatted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                letterSpacing = 0.5.sp,
            )
        }
    }
}
