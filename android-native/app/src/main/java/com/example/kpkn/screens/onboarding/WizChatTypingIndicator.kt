package com.example.kpkn.screens.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WizChatTypingIndicator(accent: Color, reducedMotion: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "wizchat-typing")
    val phase = if (reducedMotion) 1f else transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "wizchat-typing-phase",
    ).value
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart) {
            Surface(
                color = accent,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                modifier = Modifier.semantics { contentDescription = "El asistente está escribiendo" },
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val offset = when (index) {
                            0 -> phase
                            1 -> ((phase + 0.33f) % 1f)
                            else -> ((phase + 0.66f) % 1f)
                        }
                        Box(
                            Modifier
                                .size(7.dp)
                                .graphicsLayer { alpha = 0.35f + 0.65f * offset; translationY = -4f * offset }
                                .background(Color(0xFF061725).copy(alpha = 0.85f), CircleShape),
                        )
                    }
                    Text(
                        "escribiendo…",
                        color = Color(0xFF061725).copy(alpha = 0.72f),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
