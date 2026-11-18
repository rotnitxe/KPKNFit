package com.example.kpkn.screens.technique.player.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.player.TechniqueSpeed
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

/** Etiquetas de prueba de los mandos del reproductor (VT7a + Pulido v1 `.tc-ctrl`). */
const val TECHNIQUE_PLAY_TAG = "technique-play"
const val TECHNIQUE_FRAME_BACK_TAG = "technique-frame-back"
const val TECHNIQUE_FRAME_FORWARD_TAG = "technique-frame-forward"
const val TECHNIQUE_SPEED_TAG = "technique-speed"
const val TECHNIQUE_LOOP_TAG = "technique-loop"
const val TECHNIQUE_PREV_REP_TAG = "technique-prev-rep"
const val TECHNIQUE_NEXT_REP_TAG = "technique-next-rep"

private val ControlCream = Color(0xFFF2EEE6)
private val ControlDarkInk = Color(0xFF111111)
private val ControlGlassFill = Color(0x14FFFFFF)
private val ControlGlassBorder = Color(0x1FFFFFFF)

/**
 * Los mandos del reproductor de técnica (`.tc-ctrl`):
 * - Fila única centrada: `[‹]` `[›]` fotogramas (`38.dp` visual, `48.dp` táctil), `[◁]` rep anterior (`46.dp`),
 *   `[▶ / ❚❚]` botón crema principal (`58.dp`), `[▷]` rep siguiente (`46.dp`) y `[🔁]` bucle (`46.dp`, crema invertido si activo).
 * - [showSpeedRow] = `true` mantiene el selector `0,25× | 0,5× | 1×` debajo para llamadas aisladas; en la vista `.tc-sheet`
 *   completa (`showSpeedRow = false`) la velocidad vive flotando sobre `.tc-hud` como `.tc-speed`.
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
    showSpeedRow: Boolean = true,
    onPrevRep: (() -> Unit)? = null,
    onNextRep: (() -> Unit)? = null,
    repNavigationAvailable: Boolean = loopAvailable,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleControlButton(
                icon = KpknLineIcons.FrameBack,
                contentDescription = "Fotograma anterior",
                visualSize = 38.dp,
                iconSize = 16.dp,
                fill = ControlGlassFill,
                border = ControlGlassBorder,
                tint = ControlCream,
                onClick = onFrameBack,
                modifier = Modifier.testTag(TECHNIQUE_FRAME_BACK_TAG),
            )
            if (onPrevRep != null) {
                CircleControlButton(
                    icon = KpknLineIcons.ChevronLeft,
                    contentDescription = "Repetición anterior",
                    visualSize = 46.dp,
                    iconSize = 18.dp,
                    fill = Color(0x0EFFFFFF),
                    border = ControlGlassBorder,
                    tint = ControlCream,
                    enabled = repNavigationAvailable,
                    onClick = onPrevRep,
                    modifier = Modifier.testTag(TECHNIQUE_PREV_REP_TAG),
                )
            }
            CircleControlButton(
                icon = if (isPlaying) KpknLineIcons.Pause else KpknLineIcons.PlayFilled,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                visualSize = 58.dp,
                iconSize = 22.dp,
                fill = ControlCream,
                border = Color.Transparent,
                tint = ControlDarkInk,
                onClick = onPlayPause,
                modifier = Modifier.testTag(TECHNIQUE_PLAY_TAG),
            )
            if (onNextRep != null) {
                CircleControlButton(
                    icon = KpknLineIcons.ChevronRight,
                    contentDescription = "Repetición siguiente",
                    visualSize = 46.dp,
                    iconSize = 18.dp,
                    fill = Color(0x0EFFFFFF),
                    border = ControlGlassBorder,
                    tint = ControlCream,
                    enabled = repNavigationAvailable,
                    onClick = onNextRep,
                    modifier = Modifier.testTag(TECHNIQUE_NEXT_REP_TAG),
                )
            }
            CircleControlButton(
                icon = KpknLineIcons.FrameForward,
                contentDescription = "Fotograma siguiente",
                visualSize = 38.dp,
                iconSize = 16.dp,
                fill = ControlGlassFill,
                border = ControlGlassBorder,
                tint = ControlCream,
                onClick = onFrameForward,
                modifier = Modifier.testTag(TECHNIQUE_FRAME_FORWARD_TAG),
            )
            val loopStateText = if (loopEnabled) "Activado" else "Desactivado"
            CircleControlButton(
                icon = KpknLineIcons.Repeat,
                contentDescription = "Repetir repetición en bucle",
                visualSize = 46.dp,
                iconSize = 18.dp,
                fill = if (loopEnabled && loopAvailable) ControlCream else Color(0x0EFFFFFF),
                border = if (loopEnabled && loopAvailable) Color.Transparent else ControlGlassBorder,
                tint = if (loopEnabled && loopAvailable) ControlDarkInk else ControlCream,
                enabled = loopAvailable,
                onClick = onLoopToggle,
                modifier = Modifier
                    .testTag(TECHNIQUE_LOOP_TAG)
                    .semantics { stateDescription = loopStateText },
            )
        }

        if (showSpeedRow) {
            Spacer(Modifier.height(8.dp))
            TechniqueSpeedSelector(
                speed = speed,
                onSpeedChange = onSpeedChange,
                modifier = Modifier.testTag(TECHNIQUE_SPEED_TAG),
            )
        }
    }
}

/** Píldora segmentada de velocidad `.tc-speed` (`0,25× · 0,5× · 1×`). Garantiza altura mínima táctil de `48.dp`. */
@Composable
fun TechniqueSpeedSelector(
    speed: TechniqueSpeed,
    onSpeedChange: (TechniqueSpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xA6141418))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in TechniqueSpeed.entries) {
            val isSelected = option == speed
            val label = speedLabel(option)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) ControlCream else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSpeedChange(option) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = "Velocidad $label"
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = KpknType.chip.copy(fontSize = 11.5.sp, fontFeatureSettings = "tnum"),
                    color = if (isSelected) ControlDarkInk else Color(0xCCFFFFFF),
                )
            }
        }
    }
}

private fun speedLabel(speed: TechniqueSpeed): String = when (speed) {
    TechniqueSpeed.QUARTER -> "0,25×"
    TechniqueSpeed.HALF -> "0,5×"
    TechniqueSpeed.NORMAL -> "1×"
}

@Composable
private fun CircleControlButton(
    icon: ImageVector,
    contentDescription: String,
    visualSize: Dp,
    iconSize: Dp,
    fill: Color,
    border: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .clip(CircleShape)
                .background(fill)
                .then(if (border != Color.Transparent) Modifier.border(1.dp, border, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
