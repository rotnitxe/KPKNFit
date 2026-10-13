package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import dev.chrisbanes.haze.HazeState

/**
 * Isla inferior del editor (SESSION_EDITOR_V1 §2, punto 9; KPKN_UI_V2 §2): vidrio v2 con «Añadir ejercicio» (principal), el asistente de la
 * sesión (torre) y el tiempo («≈ 58 min»). Sustituye al grupo de botones flotantes arrastrable.
 *
 * Va a 12 dp de cada lado, centrada sobre el borde inferior. En una fila de 64 dp cabe la pastilla con su «+» si sobra sitio; en
 * pantallas justas, la pastilla queda sin «+»; y al 130 % de letra, o en pantallas muy estrechas, la pastilla ocupa su propia fila y la
 * torre y el tiempo van debajo, para que ninguna cifra ni palabra se corte.
 *
 * Se esconde mientras hay una hoja o un diálogo abiertos ([KpknModalTracker]) o si [visible] es falso (arrastre). Entra con un fundido y
 * 8 dp de subida en 220 ms; con movimiento reducido, solo fundidos.
 *
 * @param timeLabel «≈ 58 min»; si es nulo, el chip no aparece.
 */
@Composable
fun SessionEditorBottomIsland(
    visible: Boolean,
    timeLabel: String?,
    hazeState: HazeState,
    onAddExercise: () -> Unit,
    onAssistant: () -> Unit,
    onTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val risePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val shown = visible && KpknModalTracker.openCount == 0
    AnimatedVisibility(
        visible = shown,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        enter = if (reduced) {
            fadeIn(animationSpec = tween(durationMillis = 120))
        } else {
            fadeIn(animationSpec = tween(durationMillis = 220)) +
                slideInVertically(animationSpec = tween(durationMillis = 220)) { risePx }
        },
        exit = fadeOut(animationSpec = tween(durationMillis = if (reduced) 120 else 140)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Al 130 % de letra, o en una pantalla que no da 336 dp de ancho, la pastilla va en su fila y la torre y el tiempo debajo.
            val stacked = LocalDensity.current.fontScale > 1f || maxWidth < 336.dp
            // En una fila, el «+» solo si sobra sitio: a 360 dp la pastilla se queda con el texto para que quepa todo.
            val withIcon = !stacked && maxWidth >= 360.dp
            if (stacked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .kpknGlass(hazeState = hazeState, shape = KpknShape.island, variant = KpknGlassVariant.ISLAND)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KpknPrimaryPill(
                        text = "Añadir ejercicio",
                        onClick = onAddExercise,
                        modifier = Modifier.fillMaxWidth(),
                        icon = KpknLineIcons.Plus,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpknIconButton(
                            icon = KpknLineIcons.Tower,
                            contentDescription = "Asistente de la sesión",
                            onClick = onAssistant,
                        )
                        if (timeLabel != null) {
                            IslandTimeChip(label = timeLabel, onClick = onTime)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .kpknGlass(hazeState = hazeState, shape = KpknShape.island, variant = KpknGlassVariant.ISLAND)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpknPrimaryPill(
                        text = "Añadir ejercicio",
                        onClick = onAddExercise,
                        modifier = Modifier.weight(1f),
                        icon = if (withIcon) KpknLineIcons.Plus else null,
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.Tower,
                        contentDescription = "Asistente de la sesión",
                        onClick = onAssistant,
                    )
                    if (timeLabel != null) {
                        IslandTimeChip(label = timeLabel, onClick = onTime)
                    }
                }
            }
        }
    }
}

/** Chip del tiempo de la isla: «≈ 58 min» en su píldora, con zona táctil de 48 dp. Abre el control de tiempo. */
@Composable
private fun IslandTimeChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Abrir el tiempo de la sesión", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionPillLabel(label = label, chevron = false)
    }
}
