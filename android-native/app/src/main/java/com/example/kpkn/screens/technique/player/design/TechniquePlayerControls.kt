package com.example.kpkn.screens.technique.player.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.technique.player.TechniqueSpeed
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize

/** Etiquetas de prueba de los mandos del reproductor de técnica. */
const val TECHNIQUE_PLAY_TAG = "technique-play"
const val TECHNIQUE_FRAME_BACK_TAG = "technique-frame-back"
const val TECHNIQUE_FRAME_FORWARD_TAG = "technique-frame-forward"
const val TECHNIQUE_SPEED_TAG = "technique-speed"
const val TECHNIQUE_LOOP_TAG = "technique-loop"

private val SPEED_LABELS = listOf("0,25×", "0,5×", "1×")
private val SPEED_SPOKEN = listOf("Un cuarto de velocidad", "Media velocidad", "Velocidad normal")

/**
 * Los mandos del reproductor de técnica (sin estado). Dos filas:
 * 1. Fotograma anterior, play o pausa (el botón de tinta) y fotograma siguiente, centrados.
 * 2. La velocidad, en un control segmentado compacto «0,25× · 0,5× · 1×», y el bucle de la repetición elegida.
 *
 * Todo mide 48 dp de zona táctil y lleva descripción para TalkBack. El bucle se pinta de tinta cuando está activo y, sin
 * repeticiones que repetir ([loopAvailable] en falso), apagado y sin respuesta.
 */
@Composable
fun TechniquePlayerControls(
    isPlaying: Boolean,
    speed: TechniqueSpeed,
    loopEnabled: Boolean,
    onPlayPause: () -> Unit,
    onFrameBack: () -> Unit,
    onFrameForward: () -> Unit,
    onSpeedChange: (TechniqueSpeed) -> Unit,
    onLoopToggle: () -> Unit,
    modifier: Modifier = Modifier,
    loopAvailable: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknIconButton(
                icon = KpknLineIcons.FrameBack,
                contentDescription = "Fotograma anterior",
                onClick = onFrameBack,
                modifier = Modifier.testTag(TECHNIQUE_FRAME_BACK_TAG),
            )
            KpknIconButton(
                icon = if (isPlaying) KpknLineIcons.Pause else KpknLineIcons.Play,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                onClick = onPlayPause,
                modifier = Modifier.testTag(TECHNIQUE_PLAY_TAG),
                filled = true,
            )
            KpknIconButton(
                icon = KpknLineIcons.FrameForward,
                contentDescription = "Fotograma siguiente",
                onClick = onFrameForward,
                modifier = Modifier.testTag(TECHNIQUE_FRAME_FORWARD_TAG),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknSegmentedControl(
                options = SPEED_LABELS,
                selectedIndex = speed.ordinal,
                onSelect = { onSpeedChange(TechniqueSpeed.entries[it]) },
                modifier = Modifier.weight(1f).testTag(TECHNIQUE_SPEED_TAG),
                size = KpknSegmentedSize.Compact,
                descriptions = SPEED_SPOKEN,
            )
            KpknIconButton(
                icon = KpknLineIcons.Repeat,
                contentDescription = "Bucle en la repetición elegida",
                onClick = if (loopAvailable) onLoopToggle else ({}),
                modifier = Modifier
                    .testTag(TECHNIQUE_LOOP_TAG)
                    .alpha(if (loopAvailable) 1f else 0.38f)
                    .semantics {
                        stateDescription = if (loopEnabled && loopAvailable) "Activado" else "Desactivado"
                        if (!loopAvailable) disabled()
                    },
                filled = loopEnabled && loopAvailable,
            )
        }
    }
}
