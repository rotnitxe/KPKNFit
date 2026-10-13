package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Aviso de eventos antiguos por migrar (P3c): un módulo teñido de `energia` con la frase de [legacyEventsLine] y «Pasar a ciclos», que
 * convierte los eventos al sistema nuevo.
 */
@Composable
internal fun ProgramCycleLegacyNotice(
    count: Int,
    onMigrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier, accent = SymbolPalette.energia) {
        Text(
            text = legacyEventsLine(count),
            style = KpknType.body,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(8.dp))
        KpknTextAction(
            text = "Pasar a ciclos",
            onClick = onMigrate,
            icon = KpknLineIcons.Repeat,
        )
    }
}

/**
 * Aviso de choque (P3c): dos o más ciclos caen en la misma semana. Lista cada choque por ciclo («C5: Descarga + Test 1RM») y pide que se
 * posponga o se cancele uno. Módulo teñido de `danger`.
 */
@Composable
internal fun ProgramCycleCollisionNotice(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier, accent = WizardColors.danger) {
        Text(
            text = "Ciclos que coinciden",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        Column {
            lines.forEach { line ->
                Spacer(Modifier.height(4.dp))
                Text(text = line, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pospón o cancela uno para que no coincidan.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}
