package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.edText

/** La ventana para deshacer un borrado: 5 s (la misma que cuenta el editor al quitar un ejercicio, AUD2-01/02). */
internal const val SESSION_UNDO_WINDOW_MS = 5_000

/**
 * Aviso de un ejercicio que se acaba de quitar (`.ed-undo`, Pulido v1 · EDITOR · A2): ocupa el sitio de su fila (68 dp como mínimo, o el
 * tamaño que le dé quien lo pone) con «Ejercicio eliminado» y «Deshacer» (botón de 44 dp sobre #ffffff10), y una línea de 3 dp abajo
 * (`left:20; right:12; bottom:6`, fondo #ffffff18, relleno #f2eee6d0) que se vacía durante la ventana. Desaparece solo cuando el borrado se
 * confirma; [onUndo] lo deja donde estaba. [startKey] reinicia la cuenta si llega otro borrado; con movimiento reducido la línea queda quieta,
 * a media altura.
 */
@Composable
internal fun SessionExerciseUndoNotice(
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    startKey: Any? = null,
    windowMillis: Int = SESSION_UNDO_WINDOW_MS,
    label: String = "Ejercicio eliminado",
    /** Avance de la cuenta atrás (1 = llena, 0 = vacía) fijado de fuera: las hojas de render congelan el aviso («al 62 %»). Sin valor, corre sola. */
    frozenRemaining: Float? = null,
) {
    val reduced = wizardReducedMotion()
    val remaining = remember(startKey) { Animatable(frozenRemaining ?: if (reduced) 0.6f else 1f) }
    // pulido:transicion ed-X08 .ed-undo .cd i scaleX(var(--cd)) 5 s lineal (ed-J22)
    LaunchedEffect(startKey, reduced, frozenRemaining) {
        if (frozenRemaining != null) {
            remaining.snapTo(frozenRemaining)
            return@LaunchedEffect
        }
        if (reduced) return@LaunchedEffect
        remaining.snapTo(1f)
        remaining.animateTo(0f, tween(durationMillis = windowMillis, easing = LinearEasing))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 68.dp)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = edText(14f, FontWeight.SemiBold, EdColors.muted, lineHeight = 17.5f),
                modifier = Modifier.weight(1f),
            )
            // `.ed-undo button`: 44 dp de alto, 14 de aire a los lados, radio 22, #ffffff10.
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x10FFFFFF))
                    .clickable(role = Role.Button, onClick = onUndo)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Deshacer", style = edText(14f, FontWeight.SemiBold, EdColors.ink, lineHeight = 17.5f))
            }
        }
        // La cuenta atrás: una línea de 3 dp que se vacía. Se lee al dibujar, sin recomponer por cuadro.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 12.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .testTag("session_undo_countdown")
                // La cuenta atrás también se lee: 1 al empezar y 0 cuando el borrado se confirma. Se evalúa al leer la semántica, sin recomponer.
                .semantics {
                    contentDescription = "Tiempo para deshacer"
                    progressBarRangeInfo = ProgressBarRangeInfo(remaining.value, 0f..1f)
                }
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x18FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .graphicsLayer {
                        scaleX = remaining.value
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Color(0xD0F2EEE6)),
            )
        }
    }
}
