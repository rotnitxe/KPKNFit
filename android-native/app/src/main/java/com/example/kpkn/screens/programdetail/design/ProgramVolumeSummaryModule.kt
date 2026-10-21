package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

/**
 * Módulo «Resumen» de la pestaña Volumen (P4): las series totales como cifra con su unidad, el músculo activo y el más cargado, el
 * reparto de empuje, tirón y pierna, y las molestias como filas. Lo que repite un dato de las barras se deja fuera; el resto va aquí.
 */
@Composable
fun ProgramVolumeSummaryModule(
    totalSets: Double,
    activeMuscles: Int,
    topMuscle: String?,
    split: ProgramVolumeSplit,
    discomforts: List<ProgramVolumeDiscomfort>,
    associations: List<ProgramVolumeAssociation>,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.kpknEntrance("programa-volumen", 1)) {
        KpknModuleHeader(label = "Resumen")
        Spacer(Modifier.height(10.dp))
        KpknMetric(
            value = programVolumeSeries(totalSets),
            unit = "series/sem",
            style = KpknType.metricL,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryStat(
                label = "Músculos activos",
                value = activeMuscles.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryStat(
                label = "Más cargado",
                value = topMuscle ?: "Sin datos",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        ProgramVolumeSplitBar(split = split)
        if (discomforts.isNotEmpty() || associations.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Spacer(Modifier.height(16.dp))
            ProgramVolumeDiscomfortRows(discomforts = discomforts, associations = associations)
        }
    }
}

/** Un dato del resumen: su etiqueta en `caption` y su valor en `bodyStrong`, en una línea. */
@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        Text(
            text = value,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
