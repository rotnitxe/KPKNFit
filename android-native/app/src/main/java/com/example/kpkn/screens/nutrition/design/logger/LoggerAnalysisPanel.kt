package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknType

/** Retraso de cada uno de los tres puntos, para que respiren en cascada. */
private val DotDelays = listOf(0, 180, 360)

/** Mensajes que rotan mientras se interpreta, según la fase. */
internal fun loggerAnalysisMessages(interpreting: Boolean): List<String> = if (interpreting) {
    listOf(
        "Convirtiendo la comida en un registro editable",
        "Comprobando porciones y cantidades",
        "Ajustando el resumen para que sea más consistente",
    )
} else {
    listOf(
        "Leyendo tu comida",
        "Estimando alimentos y cantidades",
        "Esto puede tardar un poco más la primera vez",
        "Preparando un resumen para que lo revises",
    )
}

/**
 * Análisis en curso (NUTRITION_V1 §3): tres puntos de energía que respiran y un mensaje que rota cada 2,6 s, sin tarjeta. Con movimiento
 * reducido, un «Interpretando…» fijo y sin animación. [elapsedMs] decide qué mensaje toca.
 */
@Composable
fun LoggerAnalysisPanel(
    interpreting: Boolean,
    elapsedMs: Long,
    modifier: Modifier = Modifier,
) {
    if (wizardReducedMotion()) {
        Text(
            text = "Interpretando…",
            style = KpknType.body,
            color = WizardColors.textMuted,
            modifier = modifier,
        )
    } else {
        LoggerAnalysisAnimated(interpreting = interpreting, elapsedMs = elapsedMs, modifier = modifier)
    }
}

@Composable
private fun LoggerAnalysisAnimated(interpreting: Boolean, elapsedMs: Long, modifier: Modifier) {
    val messages = loggerAnalysisMessages(interpreting)
    val messageIndex = ((elapsedMs / 2600L).toInt()).mod(messages.size.coerceAtLeast(1))
    val transition = rememberInfiniteTransition(label = "loggerAnalysisDots")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Interpretando la comida" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DotDelays.forEachIndexed { index, delay ->
                val dotAlpha by transition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, delayMillis = delay, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "loggerAnalysisDot$index",
                )
                Spacer(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .background(SymbolPalette.energia, CircleShape),
                )
            }
        }
        AnimatedContent(targetState = messages[messageIndex], label = "loggerAnalysisMessage") { message ->
            Text(text = message, style = KpknType.body, color = WizardColors.textMuted)
        }
    }
}
