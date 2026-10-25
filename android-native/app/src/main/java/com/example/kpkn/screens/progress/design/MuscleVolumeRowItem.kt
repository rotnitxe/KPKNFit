package com.example.kpkn.screens.progress.design

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
import com.example.kpkn.domain.training.MuscleVolumeRow
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.design.ProgramVolumeBar
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import com.example.kpkn.screens.programdetail.design.programVolumeStateColor
import com.example.kpkn.screens.programdetail.design.programVolumeStateLabel
import com.example.kpkn.screens.programdetail.design.programVolumeStateOf
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEntranceMemory
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de un músculo del panel (T5a): nombre, series registradas con su unidad, barra relativa al máximo de la ventana (con las zonas
 * del programa si lo hay y está calibrado) y, desplegada, el desglose por ejercicio que se pasa en [breakdown].
 *
 * Tres estados honestos: con datos, cero real («Sin series registradas en esta ventana») y sin datos («—», sin barra). Toda la parte
 * superior es un botón de al menos 48 dp; su semántica lee músculo, cifra y estado, y si está desplegada.
 */
@Composable
fun MuscleVolumeRowItem(
    row: MuscleVolumeRow,
    unit: String,
    scaleMax: Double,
    zones: ProgramVolumeZones?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    breakdown: @Composable () -> Unit,
) {
    val reduced = wizardReducedMotion()
    val sets = row.sets
    val seriesText = sets?.let { programVolumeSeries(it) } ?: "—"
    val state = sets?.let { programVolumeStateOf(it, zones) }
    val stateColor = state?.let { programVolumeStateColor(it) } ?: WizardColors.textMuted
    val stateLabel = if (sets != null && zones != null && state != null) {
        programVolumeStateLabel(state, sets, zones)
    } else {
        null
    }
    val caption = when {
        sets == null -> "Sin datos en esta ventana"
        sets <= 0.0 -> "Sin series registradas en esta ventana"
        else -> stateLabel
    }
    val spoken = when {
        sets == null -> "${row.muscleName}, sin datos"
        else -> "${row.muscleName}, $seriesText $unit" + (stateLabel?.let { ", $it" } ?: "")
    }
    val interaction = remember { MutableInteractionSource() }
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else 220),
        label = "muscleVolumeChevron",
    )
    val animateIn = remember(row.muscleName) { KpknEntranceMemory.firstTime("progreso-volumen-barra/${row.muscleName}", 0) }
    val enter = if (reduced) fadeIn(tween(120)) else fadeIn(tween(220)) + expandVertically(tween(220))
    val exit = if (reduced) fadeOut(tween(120)) else fadeOut(tween(160)) + shrinkVertically(tween(220))

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("muscle-volume-row-${row.muscleName}")
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
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
                    text = row.muscleName,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = seriesText,
                        style = KpknType.metricM,
                        color = if (sets == null) WizardColors.textFaint else WizardColors.text,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (sets != null) {
                        Text(
                            text = unit,
                            style = KpknType.unit,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronTurn },
                    tint = WizardColors.textFaint,
                )
            }
            if (sets != null) {
                Spacer(Modifier.height(10.dp))
                ProgramVolumeBar(
                    sets = sets,
                    zones = zones,
                    scaleMax = scaleMax,
                    markColor = if (zones != null) stateColor else WizardColors.text,
                    animateIn = animateIn,
                )
            }
            if (caption != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = KpknType.caption,
                    color = if (sets != null && sets > 0.0 && stateLabel != null) stateColor else WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) { breakdown() }
        }
    }
}
