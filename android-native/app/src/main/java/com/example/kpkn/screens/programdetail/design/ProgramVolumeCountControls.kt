package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknType

/**
 * Controles que cambian lo que cuenta el volumen (P4): el alcance indirecto (directo, +secundario, +estabilizador) en píldoras
 * `KpknChip`, y el ajuste por fatiga con `KpknSwitch`. Son los mismos datos que leía la pestaña antes; solo cambia cómo se ven.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramVolumeCountControls(
    indirectMode: Int,
    onIndirectMode: (Int) -> Unit,
    adjustByIntensity: Boolean,
    onAdjustByIntensity: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Alcance del volumen",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Directo" to 0, "+Secundario" to 1, "+Estabilizador" to 2).forEach { (label, mode) ->
                KpknChip(
                    text = label,
                    selected = indirectMode == mode,
                    onClick = { onIndirectMode(mode) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ajustar por fatiga",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            KpknSwitch(checked = adjustByIntensity, onCheckedChange = onAdjustByIntensity)
        }
    }
}
