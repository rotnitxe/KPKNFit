package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Aviso de eventos antiguos por migrar: un `.mod` teñido de `energia` con la frase de [legacyEventsLine] y «Pasar a ciclos», que convierte
 * los eventos al sistema nuevo.
 */
@Composable
internal fun ProgramCycleLegacyNotice(
    count: Int,
    onMigrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier, accent = SymbolPalette.energia) {
        Text(text = legacyEventsLine(count), style = StructureBodyStyle, color = StructureInk)
        StructureAction(text = "Pasar a ciclos", onClick = onMigrate, leadingIcon = KpknLineIcons.Repeat, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Aviso de choque: dos o más ciclos caen en la misma semana. Lista cada choque por ciclo («C5: Descarga + Test 1RM») y pide que se
 * posponga o se cancele uno. `.mod` teñido de `--danger`, con el patrón del aviso «Revisa la estructura temporal».
 */
@Composable
internal fun ProgramCycleCollisionNotice(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier, accent = WizardColors.danger) {
        Text(text = "Ciclos que coinciden", style = StructureRowTitleStyle, color = StructureInk)
        lines.forEach { line -> StructureCaption(text = line, modifier = Modifier.padding(top = 4.dp)) }
        StructureCaption(text = "Pospón o cancela uno para que no coincidan.", color = StructureMuted, modifier = Modifier.padding(top = 8.dp))
    }
}
