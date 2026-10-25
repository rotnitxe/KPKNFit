package com.example.kpkn.screens.progress.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.MuscleVolumeScale
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.VolumeShade
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import com.example.kpkn.screens.progress.BodySide
import com.example.kpkn.screens.progress.MuscleVolumeBodyModel
import com.example.kpkn.screens.progress.MuscleVolumePanelActions
import com.example.kpkn.screens.progress.MuscleVolumePanelUiState
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Frente y espalda muestran las mismas series registradas que la lista, con desglose al tocar cada región. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleVolumeBodyModule(
    state: MuscleVolumePanelUiState,
    actions: MuscleVolumePanelActions,
    modifier: Modifier = Modifier,
) {
    val panel = state.panel
    val paints = remember(panel) { MuscleVolumeBodyModel.volumePaints(panel) }
    val summary = remember(panel) { MuscleVolumeBodyModel.volumeSummary(panel) }
    val descriptions = remember(panel) {
        MuscleVolumeBodyModel.bodyRegions.associateWith { name ->
            val sets = panel?.row(name)?.sets
            if (sets == null) "$name, sin datos" else "$name, ${programVolumeSeries(sets)} ${panel?.unit.orEmpty()}"
        }
    }
    val unmapped = remember {
        VolumeCalculator.standardVolumeMuscles.filter { it !in MuscleVolumeBodyModel.bodyRegions }
    }

    KpknModule(modifier = modifier.testTag("muscle-volume-body-module")) {
        KpknModuleHeader("Cuerpo")
        Text(
            text = "Series registradas en la ventana.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        KpknSegmentedControl(
            options = listOf("Frente", "Espalda"),
            selectedIndex = state.side.ordinal,
            onSelect = { actions.onSide(BodySide.entries[it]) },
            size = KpknSegmentedSize.Compact,
            modifier = Modifier.testTag("muscle-volume-side"),
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MuscleVolumeBodyMap(
                side = state.side,
                paints = paints,
                selected = state.selectedMuscle,
                onSelect = actions.onSelectRegion,
                summary = summary,
                regionDescriptions = descriptions,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (panel != null) {
            MuscleVolumeLegend(
                title = MuscleVolumeBodyModel.legendTitle(panel),
                entries = MuscleVolumeScale.legend(panel.request.window, panel.request.averagePerWeek)
                    .map { (shade, label) -> LegendEntry(MuscleVolumeBodyModel.swatch(shade), label) } +
                    LegendEntry(MuscleVolumeBodyModel.swatch(VolumeShade.NO_DATA), "sin datos"),
            )
        }

        if (unmapped.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Otros músculos".uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unmapped.forEach { name ->
                    val value = panel?.row(name)?.sets?.let { programVolumeSeries(it) } ?: "—"
                    KpknChip(
                        text = "$name · $value",
                        selected = state.selectedMuscle == name,
                        onClick = { actions.onSelectRegion(name) },
                        modifier = Modifier.testTag("muscle-volume-extra-$name"),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Spacer(Modifier.height(12.dp))
        BodyDetail(state)
    }
}

/** Desglose de las series registradas de la región elegida. */
@Composable
private fun BodyDetail(state: MuscleVolumePanelUiState) {
    val panel = state.panel
    val selected = state.selectedMuscle
    if (panel == null || !panel.hasData) {
        Text(
            "Sin registros en esta ventana: no hay series que repartir.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        return
    }
    val row = selected?.let { panel.row(it) }
    if (row == null) {
        Text(
            "Toca una región o un músculo para ver qué ejercicios lo explican.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("muscle-volume-body-detail")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.muscleName, style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            KpknMetric(value = programVolumeSeries(row.sets ?: 0.0), unit = panel.unit, style = KpknType.metricM)
        }
        MuscleVolumeBreakdown(row = row, unit = panel.unit, mode = panel.request.mode)
    }
}
