package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.ProgramVolumeBar
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import com.example.kpkn.screens.programdetail.design.programVolumeStateColor
import com.example.kpkn.screens.programdetail.design.programVolumeStateLabel
import com.example.kpkn.screens.programdetail.design.programVolumeStateOf
import com.example.kpkn.ui.design.KpknEntranceMemory
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de un músculo de la sesión (SE4): el nombre en `bodyStrong`, las series a la derecha en `metricM` con su unidad, la barra de P4 con
 * sus zonas y su marca, y debajo el estado en su color. Es la misma tabla de estados que la pestaña Volumen (`programVolumeStateOf`).
 * Sin zonas, la barra solo lleva la marca y el estado sale «Sin calibrar».
 */
@Composable
fun SessionImpactMuscleRow(
    muscle: SessionImpactMuscle,
    scaleMax: Double,
    modifier: Modifier = Modifier,
) {
    val state = programVolumeStateOf(sets = muscle.sets, zones = muscle.zones, overtrained = false, overreaching = false)
    val color = programVolumeStateColor(state)
    val label = programVolumeStateLabel(state = state, sets = muscle.sets, zones = muscle.zones, overtrained = false)
    val series = programVolumeSeries(muscle.sets)
    // La marca sube desde cero solo la primera vez que se compone este músculo, como en la pestaña Volumen.
    val animateIn = remember(muscle.name) { KpknEntranceMemory.firstTime("sesion-impacto-barra/${muscle.name}", 0) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${muscle.name}, $series series, $label"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = muscle.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KpknMetric(value = series, unit = "series", style = KpknType.metricM)
        }
        Spacer(Modifier.height(10.dp))
        ProgramVolumeBar(
            sets = muscle.sets,
            zones = muscle.zones,
            scaleMax = scaleMax,
            markColor = color,
            animateIn = animateIn,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = KpknType.caption,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
