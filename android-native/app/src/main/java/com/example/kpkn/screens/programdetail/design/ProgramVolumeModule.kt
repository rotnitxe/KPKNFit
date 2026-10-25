package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

/**
 * Módulo «Volumen de la semana» (P4): cabecera con «Calibrar», el tipo de alcance si hay más de uno (Semana, Bloque, Programa), el
 * selector del alcance, los controles de cuenta y una fila por músculo. Sin estado: la fila desplegada y el alcance los trae quien lo
 * usa.
 *
 * @param muscles las filas ya ordenadas: primero las prioridades del usuario y luego por series.
 * @param expandedMuscle el músculo desplegado, o null; [breakdown] es su desglose.
 * @param totalVolume distingue un total acumulado de una semana o un promedio semanal.
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
    unassignedCount: Int = 0,
    totalVolume: Boolean = false,
    controls: @Composable () -> Unit = {},
) {
    KpknModule(modifier = modifier.kpknEntrance("programa-volumen", 0)) {
        KpknModuleHeader(
            label = if (totalVolume) "Volumen total" else "Volumen de la semana",
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
        if (unassignedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (unassignedCount == 1) "1 registro sin fecha no se cuenta" else "$unassignedCount registros sin fecha no se cuentan",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(12.dp))
        // Sin calibrar en todo el módulo, la etiqueta «Sin calibrar» se dice una sola vez (en la invitación de arriba), no en cada fila.
        val uncalibrated = muscles.none { it.zones != null }
        // Los músculos sin series no tienen fila: se juntan al final en una línea.
        val withSets = muscles.filter { it.sets > 0.0 || (it.real ?: 0.0) > 0.0 }
        val withoutSets = muscles.filter { it.sets <= 0.0 && (it.real ?: 0.0) <= 0.0 }
        withSets.forEachIndexed { index, muscle ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            }
            val expanded = expandedMuscle == muscle.name
            ProgramVolumeRow(
                muscle = muscle,
                expanded = expanded,
                onToggle = { onToggleMuscle(muscle.name) },
                breakdown = if (expanded) breakdown else null,
                showStateLabel = !uncalibrated,
                totalVolume = totalVolume,
            )
        }
        if (withoutSets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${if (totalVolume) "Sin series en total" else "Sin series esta semana"}: ${spanishList(withoutSets.map { it.name })}",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Spacer(Modifier.height(14.dp))
        controls()
    }
}

/** «A», «A y B», «A, B y C»: la enumeración de nombres en español. */
private fun spanishList(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " y " + names.last()
}
