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
import com.example.kpkn.screens.progress.BodyMetric
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

/**
 * Vista «Cuerpo» (T5a): frente y espalda coloreados con los MISMOS números de la lista, y un conmutador Volumen | Recuperación AUGE.
 * «Recuperación» conserva lo que el cuerpo ya pintaba en Inicio (azul si llega a 80 %, naranja si no, con el porcentaje de AUGE);
 * «Volumen» pinta series registradas con su propia escala y leyenda. Las dos escalas no se mezclan nunca. Los músculos sin región
 * (Cuello, Antebrazo, Aductores, Romboides) salen como chips bajo el cuerpo. Tocar una región o un chip abre el desglose, el mismo de
 * la lista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleVolumeBodyModule(
    state: MuscleVolumePanelUiState,
    actions: MuscleVolumePanelActions,
    modifier: Modifier = Modifier,
) {
    val panel = state.panel
    val volume = state.metric == BodyMetric.VOLUME
    val paints = remember(panel, state.recovery, state.metric) {
        if (volume) MuscleVolumeBodyModel.volumePaints(panel) else MuscleVolumeBodyModel.recoveryPaints(state.recovery)
    }
    val summary = remember(panel, state.recovery, state.metric) {
        if (volume) MuscleVolumeBodyModel.volumeSummary(panel) else MuscleVolumeBodyModel.recoverySummary(state.recovery)
    }
    val descriptions = remember(panel, state.recovery, state.metric) {
        MuscleVolumeBodyModel.bodyRegions.associateWith { name ->
            if (volume) {
                val sets = panel?.row(name)?.sets
                if (sets == null) "$name, sin datos" else "$name, ${programVolumeSeries(sets)} ${panel?.unit.orEmpty()}"
            } else {
                state.recovery[name]?.let { "$name, ${it.coerceIn(0, 100)} % recuperado" } ?: "$name, sin datos de recuperación"
            }
        }
    }
    val unmapped = remember(state.metric, state.recovery) {
        if (volume) {
            VolumeCalculator.standardVolumeMuscles.filter { it !in MuscleVolumeBodyModel.bodyRegions }
        } else {
            state.recovery.keys.filter { it !in MuscleVolumeBodyModel.bodyRegions }.sorted()
        }
    }

    KpknModule(modifier = modifier.testTag("muscle-volume-body-module")) {
        KpknModuleHeader("Cuerpo")
        KpknSegmentedControl(
            options = listOf("Volumen", "Recuperación AUGE"),
            selectedIndex = state.metric.ordinal,
            onSelect = { actions.onMetric(BodyMetric.entries[it]) },
            size = KpknSegmentedSize.Compact,
            modifier = Modifier.testTag("muscle-volume-metric"),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (volume) {
                "Series registradas en la ventana. No es recuperación ni carga."
            } else {
                "Recuperación estimada por AUGE, en porcentaje. No son series."
            },
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
        if (volume && panel != null) {
            MuscleVolumeLegend(
                title = MuscleVolumeBodyModel.legendTitle(panel),
                entries = MuscleVolumeScale.legend(panel.request.window, panel.request.averagePerWeek)
                    .map { (shade, label) -> LegendEntry(MuscleVolumeBodyModel.swatch(shade), label) } +
                    LegendEntry(MuscleVolumeBodyModel.swatch(VolumeShade.NO_DATA), "sin datos"),
            )
        } else if (!volume) {
            MuscleVolumeLegend(
                title = "Recuperación AUGE · %",
                entries = listOf(
                    LegendEntry(MuscleVolumeBodyModel.recoveryPaint(90), "80 o más"),
                    LegendEntry(MuscleVolumeBodyModel.recoveryPaint(50), "menos de 80"),
                    LegendEntry(MuscleVolumeBodyModel.recoveryPaint(null), "sin datos"),
                ),
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
                    val value = if (volume) {
                        panel?.row(name)?.sets?.let { programVolumeSeries(it) } ?: "—"
                    } else {
                        state.recovery[name]?.let { "${it.coerceIn(0, 100)} %" } ?: "—"
                    }
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

/** Lo elegido en el cuerpo: en volumen, el desglose de la lista; en recuperación, el porcentaje de AUGE. */
@Composable
private fun BodyDetail(state: MuscleVolumePanelUiState) {
    val panel = state.panel
    val selected = state.selectedMuscle
    if (state.metric == BodyMetric.RECOVERY) {
        if (selected == null) {
            Text("Toca una región para ver su recuperación.", style = KpknType.caption, color = WizardColors.textMuted)
            return
        }
        val percent = state.recovery[selected]?.coerceIn(0, 100)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("muscle-volume-recovery-detail")) {
            Text(selected, style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            if (percent == null) {
                Text("Sin datos", style = KpknType.caption, color = WizardColors.textMuted)
            } else {
                KpknMetric(value = percent.toString(), unit = "% recuperado", style = KpknType.metricM)
            }
        }
        return
    }
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
