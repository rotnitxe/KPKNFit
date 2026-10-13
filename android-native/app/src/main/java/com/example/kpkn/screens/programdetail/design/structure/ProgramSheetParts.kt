package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Etiqueta de sección dentro de una hoja o de un módulo: eyebrow en mayúsculas y `textMuted`. */
@Composable
internal fun ProgramSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
        modifier = modifier,
    )
}

/**
 * Aviso del motor («La competición no cae dentro de las semanas actuales…») como una línea: un `Info` en `energia` y el texto en
 * `caption`. Sin caja: es una nota de la pieza, no un módulo.
 */
@Composable
internal fun ProgramEngineMessage(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = KpknLineIcons.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = SymbolPalette.energia,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
    }
}
