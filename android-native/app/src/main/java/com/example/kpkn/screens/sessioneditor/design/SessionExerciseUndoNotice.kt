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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** La ventana para deshacer un borrado: 5 s (la misma que cuenta el editor al quitar un ejercicio, AUD2-01/02). */
internal const val SESSION_UNDO_WINDOW_MS = 5_000

/**
 * Aviso de un ejercicio que se acaba de quitar (ED-A · A4): ocupa el sitio de su fila, de 64 dp, con «Ejercicio eliminado» y «Deshacer», y
 * una línea fina abajo que se vacía durante los 5 s de la ventana. Desaparece solo cuando el borrado se confirma; [onUndo] lo deja
 * donde estaba. [startKey] reinicia la cuenta si llega otro borrado; con movimiento reducido la línea queda quieta, a media altura.
 */
@Composable
internal fun SessionExerciseUndoNotice(
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    startKey: Any? = null,
    windowMillis: Int = SESSION_UNDO_WINDOW_MS,
) {
    val reduced = wizardReducedMotion()
    val remaining = remember(startKey) { Animatable(if (reduced) 0.6f else 1f) }
    LaunchedEffect(startKey, reduced) {
        if (reduced) return@LaunchedEffect
        remaining.snapTo(1f)
        remaining.animateTo(0f, tween(durationMillis = windowMillis, easing = LinearEasing))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ejercicio eliminado",
                style = KpknType.bodyStrong,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            KpknTextAction(
                text = "Deshacer",
                onClick = onUndo,
                color = WizardColors.text,
            )
        }
        // La cuenta atrás: una línea de 2 dp que se vacía. Se lee al dibujar, sin recomponer por cuadro.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 12.dp)
                .fillMaxWidth()
                .height(2.dp)
                .testTag("session_undo_countdown")
                // La cuenta atrás también se lee: 1 al empezar y 0 cuando el borrado se confirma. Se evalúa al leer la semántica, sin recomponer.
                .semantics {
                    contentDescription = "Tiempo para deshacer"
                    progressBarRangeInfo = ProgressBarRangeInfo(remaining.value, 0f..1f)
                }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp))
                .background(WizardColors.text.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .graphicsLayer {
                        scaleX = remaining.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .background(WizardColors.text.copy(alpha = 0.45f)),
            )
        }
    }
}
