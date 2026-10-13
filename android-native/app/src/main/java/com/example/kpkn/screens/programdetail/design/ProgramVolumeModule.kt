package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.kpknEntrance

/**
 * Módulo «Volumen de la semana» (P4): cabecera con «Calibrar», el tipo de alcance si hay más de uno (Semana, Bloque, Programa), el
 * selector del alcance, los controles de cuenta y una fila por músculo. Sin estado: la fila desplegada y el alcance los trae quien lo
 * usa.
 *
 * @param muscles las filas ya ordenadas: primero las prioridades del usuario y luego por series.
 * @param expandedMuscle el músculo desplegado, o null; [breakdown] es su desglose.
 * @param controls los controles de cuenta (alcance indirecto y ajuste por fatiga).
 */
@Composable
fun ProgramVolumeModule(
    muscles: List<ProgramVolumeMuscle>,
    scope: ProgramVolumeScope,
    onSelectKind: (Int) -> Unit,
    onPreviousScope: () -> Unit,
    onNextScope: () -> Unit,
    expandedMuscle: String?,
    onToggleMuscle: (String) -> Unit,
    breakdown: ProgramVolumeBreakdown?,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit = {},
) {
    KpknModule(modifier = modifier.kpknEntrance("programa-volumen", 0)) {
        KpknModuleHeader(
            label = "Volumen de la semana",
            action = { KpknTextAction(text = "Calibrar", onClick = onCalibrate) },
        )
        if (scope.kinds.size > 1) {
            Spacer(Modifier.height(8.dp))
            KpknSegmentedControl(
                options = scope.kinds,
                selectedIndex = scope.selectedKind,
                onSelect = onSelectKind,
                modifier = Modifier.fillMaxWidth(),
                size = KpknSegmentedSize.Compact,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgramVolumeScopeSelector(scope = scope, onPrevious = onPreviousScope, onNext = onNextScope)
        Spacer(Modifier.height(12.dp))
        muscles.forEachIndexed { index, muscle ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            }
            val expanded = expandedMuscle == muscle.name
            ProgramVolumeRow(
                muscle = muscle,
                expanded = expanded,
                onToggle = { onToggleMuscle(muscle.name) },
                breakdown = if (expanded) breakdown else null,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Spacer(Modifier.height(14.dp))
        controls()
    }
}
