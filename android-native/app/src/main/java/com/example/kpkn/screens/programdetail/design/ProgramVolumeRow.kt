package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEntranceMemory
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de un músculo (P4): el nombre en `bodyStrong`, las series a la derecha en `metricM` con la unidad debajo, la barra por zonas y el
 * estado en su color, con «Real» a la derecha si hay registros. Toda la parte superior es un botón que despliega el desglose de
 * [breakdown] (ejercicios y cabezas) con 220 ms; sin movimiento reducido, solo un fundido.
 *
 * Su semántica lee el músculo, sus series y su estado en una frase, y dice si está desplegada.
 */
@Composable
fun ProgramVolumeRow(
    muscle: ProgramVolumeMuscle,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    breakdown: ProgramVolumeBreakdown? = null,
    /** Falso cuando el estado lo dice el módulo una sola vez (todo el volumen sin calibrar); la semántica sigue leyéndolo. */
    showStateLabel: Boolean = true,
    totalVolume: Boolean = false,
    /** Vista «Hecho»: la cifra, la marca y el estado son lo registrado y el plan queda como trazo y como texto. */
    doneView: Boolean = false,
) {
    val reduced = wizardReducedMotion()
    val shown = if (doneView) muscle.real ?: 0.0 else muscle.sets
    val state = if (doneView) programVolumeStateOf(shown, muscle.zones) else muscle.state
    val stateColor = programVolumeStateColor(state)
    val label = programVolumeStateLabel(state, shown, muscle.zones)
    val seriesText = programVolumeSeries(shown)
    val unit = if (totalVolume) "series" else "series/sem"
    val interaction = remember { MutableInteractionSource() }
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else 220),
        label = "volumeRowChevron",
    )
    // La barra sube desde cero solo la primera vez que el proceso compone este músculo, como las entradas de los módulos.
    val animateIn = remember(muscle.name) { KpknEntranceMemory.firstTime("programa-volumen-barra/${muscle.name}", 0) }
    val enter = if (reduced) fadeIn(tween(120)) else fadeIn(tween(220)) + expandVertically(tween(220))
    val exit = if (reduced) fadeOut(tween(120)) else fadeOut(tween(160)) + shrinkVertically(tween(220))

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("volumen_fila_${muscle.name}")
                .semantics(mergeDescendants = true) {
                    contentDescription = "${muscle.name}, $seriesText $unit, $label"
                    stateDescription = if (expanded) "Desplegado" else "Plegado"
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) "Ocultar desglose" else "Ver desglose",
                    onClick = onToggle,
                )
                .padding(vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = muscle.name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // La unidad conserva su ancho natural; no comparte una línea estrecha con la cifra.
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$seriesText $unit"
                    },
                ) {
                    Text(
                        text = seriesText,
                        style = KpknType.metricM,
                        color = WizardColors.text,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = unit,
                        style = KpknType.unit,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronTurn },
                    tint = WizardColors.textFaint,
                )
            }
            Spacer(Modifier.height(10.dp))
            ProgramVolumeBar(
                sets = shown,
                planTick = if (doneView) muscle.sets else null,
                zones = muscle.zones,
                scaleMax = muscle.scaleMax,
                markColor = stateColor,
                animateIn = animateIn,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showStateLabel) {
                    Text(
                        text = label,
                        style = KpknType.caption,
                        color = stateColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                (if (doneView) muscle.sets else muscle.real)?.let { other ->
                    Text(
                        text = "${if (doneView) "Plan" else "Real"} ${programVolumeSeries(other)} $unit",
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (breakdown != null) {
            AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
                ProgramVolumeBreakdownList(breakdown = breakdown)
            }
        }
    }
}
