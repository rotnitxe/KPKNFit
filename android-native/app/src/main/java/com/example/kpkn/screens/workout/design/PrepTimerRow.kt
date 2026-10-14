package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Temporizador del bloque de movilidad (KPKN LS5b), con el mismo patrón que el de la serie por tiempo (LS2b): el reloj en `metricL`
 * tabular y, a su derecha, «+30 s» y «Reiniciar» como acciones de texto; debajo, «Empezar» o «Pausar» a todo el ancho, con su icono
 * delante. Sin estado.
 */
@Composable
fun PrepTimerRow(
    clock: String,
    running: Boolean,
    onToggle: () -> Unit,
    onAddSeconds: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = clock, style = KpknType.metricL, color = WizardColors.text, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                KpknTextAction(text = "+30 s", onClick = onAddSeconds)
                KpknTextAction(text = "Reiniciar", onClick = onReset)
            }
        }
        KpknSecondaryPill(
            text = if (running) "Pausar" else "Empezar",
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            icon = if (running) KpknLineIcons.Pause else KpknLineIcons.Play,
        )
    }
}
