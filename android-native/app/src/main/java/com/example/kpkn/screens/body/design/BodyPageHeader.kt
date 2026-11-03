package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Cabecera de Cuerpo (BODY_V1 §2.1): «Cuerpo» en `display`. [topInset] es la altura de la barra de estado. El registro de una medición
 * ya no vive aquí: lo abre la isla flotante «Nueva medición» ([BodyNewRecordIsland]), igual que «Registrar comida» en Nutrición.
 */
@Composable
fun BodyPageHeader(
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = topInset + 12.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cuerpo",
            style = KpknType.display,
            color = WizardColors.text,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}
