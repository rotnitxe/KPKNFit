package com.example.kpkn.screens.progress.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.MuscleVolumePanel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.progress.MuscleVolumeBodyModel
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Vista «Lista» (T5a): una fila por grupo muscular con sus series registradas, la barra relativa al máximo de la ventana y, tocándola,
 * el desglose por ejercicio. Es también la alternativa accesible al cuerpo: dice lo mismo que el mapa, músculo a músculo.
 *
 * [zones] son las zonas semanales del programa calibrado (vacío si no hay); [expandedMuscle] es la selección compartida con el cuerpo.
 */
@Composable
fun MuscleVolumeListModule(
    panel: MuscleVolumePanel,
    zones: Map<String, ProgramVolumeZones>,
    expandedMuscle: String?,
    onToggleMuscle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.testTag("muscle-volume-list")) {
        KpknModuleHeader("Series por músculo")
        Text(
            text = "Series registradas · ${if (panel.request.averagePerWeek) "promedio por semana" else "total"} · " +
                MuscleVolumeBodyModel.rangeText(panel),
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        panel.rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            val rowZones = zones[row.muscleName]
            val scaleMax = maxOf(panel.maxSets, rowZones?.maxRecoverable?.toDouble() ?: 0.0, 1.0)
            MuscleVolumeRowItem(
                row = row,
                unit = panel.unit,
                scaleMax = scaleMax,
                zones = rowZones,
                expanded = expandedMuscle == row.muscleName,
                onToggle = { onToggleMuscle(row.muscleName) },
            ) {
                MuscleVolumeBreakdown(row = row, unit = panel.unit, mode = panel.request.mode)
            }
        }
    }
}
