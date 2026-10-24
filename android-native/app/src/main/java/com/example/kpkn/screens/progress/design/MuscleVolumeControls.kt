package com.example.kpkn.screens.progress.design

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.MuscleVolumePanelRequest
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCountControls
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize

/**
 * Cabecera de ajustes del panel (T5a): la ventana (1 / 4 / 12 semanas), promedio por semana o total, y los mismos controles de rol y
 * de ajuste por intensidad de la pestaña Volumen del programa (`ProgramVolumeCountControls`), para que digan lo mismo.
 */
@Composable
fun MuscleVolumeControls(
    request: MuscleVolumePanelRequest,
    onWindow: (MuscleVolumeWindow) -> Unit,
    onAverage: (Boolean) -> Unit,
    onMode: (VolumeCountingMode) -> Unit,
    onAdjust: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.testTag("muscle-volume-controls")) {
        KpknModuleHeader("Ventana de tiempo")
        KpknSegmentedControl(
            options = listOf("1 semana", "4 semanas", "12 semanas"),
            selectedIndex = request.window.ordinal,
            onSelect = { onWindow(MuscleVolumeWindow.entries[it]) },
            size = KpknSegmentedSize.Compact,
            modifier = Modifier.testTag("muscle-volume-window"),
        )
        Spacer(Modifier.height(8.dp))
        KpknSegmentedControl(
            options = listOf("Media semanal", "Total"),
            selectedIndex = if (request.averagePerWeek) 0 else 1,
            onSelect = { onAverage(it == 0) },
            size = KpknSegmentedSize.Compact,
            descriptions = listOf("Promedio de series por semana", "Total de series de la ventana"),
            modifier = Modifier.testTag("muscle-volume-average"),
        )
        Spacer(Modifier.height(16.dp))
        ProgramVolumeCountControls(
            indirectMode = request.mode.ordinal,
            onIndirectMode = { onMode(VolumeCountingMode.entries[it]) },
            adjustByIntensity = request.adjustByIntensity,
            onAdjustByIntensity = onAdjust,
        )
    }
}
