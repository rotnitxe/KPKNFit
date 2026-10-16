package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

/** Choosing a mode keeps the existing permission/activation route in the caller. */
@Composable
fun VoiceCaptureModeDialog(onChosen: (VoiceCaptureMode) -> Unit, onDismissRequest: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<VoiceCaptureMode?>(null) }
    HeaderSheet(onDismissRequest ?: {}, modifier, dismissible = onDismissRequest != null) { close ->
        HeaderSheetFrame("Modo de voz", footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderSheetAction("Ahora no", close, Modifier.weight(1f), enabled = onDismissRequest != null)
                HeaderSheetAction("Empezar", { selected?.let(onChosen) }, Modifier.weight(1f), primary = true, enabled = selected != null)
            }
        }) { VoiceModeChoices(selected) { selected = it } }
    }
}

@Composable
internal fun VoiceModeChoices(selected: VoiceCaptureMode?, onSelect: (VoiceCaptureMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(VoiceCaptureMode.HANDS_FREE, VoiceCaptureMode.MUSIC).forEach { mode ->
            val active = selected == mode
            KpknModule(modifier = Modifier.weight(1f).semantics { this.selected = active }, onClick = { onSelect(mode) },
                accent = if (active) WizardColors.text else null, contentPadding = PaddingValues(12.dp)) {
                val visibility = rememberFeedbackAnimationVisibility()
                val reduced = wizardReducedMotion()
                val clock = rememberRestClock(active && mode == VoiceCaptureMode.MUSIC && visibility.visible && !reduced)
                val ink = if (active) WizardColors.text else WizardColors.textMuted
                Canvas(Modifier.fillMaxWidth().height(66.dp).then(visibility.modifier)) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    if (mode == VoiceCaptureMode.HANDS_FREE) {
                        drawArc(ink, 180f, 180f, false, Offset(cx - 22.dp.toPx(), cy - 22.dp.toPx()), androidx.compose.ui.geometry.Size(44.dp.toPx(), 44.dp.toPx()), style = stroke)
                        listOf(-1, 1).forEach { side -> drawLine(ink, Offset(cx + side * 22.dp.toPx(), cy), Offset(cx + side * 22.dp.toPx(), cy + 16.dp.toPx()), 7.dp.toPx(), StrokeCap.Round) }
                    } else {
                        listOf(-14f, -5f, 5f, 14f).forEachIndexed { i, x ->
                            val wave = if (active && !reduced) .6f + .4f * kotlin.math.sin(clock.seconds * 2 + i) else .7f
                            val h = (if (i == 1 || i == 2) 20.dp.toPx() else 11.dp.toPx()) * wave
                            drawLine(ink, Offset(cx + x.dp.toPx(), cy - h), Offset(cx + x.dp.toPx(), cy + h), 4.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
                Text(if (mode == VoiceCaptureMode.HANDS_FREE) "Manos libres" else "Música", style = KpknType.bodyStrong, color = ink)
                Spacer(Modifier.height(6.dp))
                Text(if (mode == VoiceCaptureMode.HANDS_FREE) "Micrófono de auriculares; reduce la calidad de la música." else "Micrófono del teléfono; conserva la calidad de la música.", style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
    }
}
