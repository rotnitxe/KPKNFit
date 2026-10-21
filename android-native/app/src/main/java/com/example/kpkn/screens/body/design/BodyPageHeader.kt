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
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType

/**
 * Cabecera de Cuerpo (BODY_V1 §2.1): «Cuerpo» en `display` y el botón «+», que abre el registro de hoy. [topInset] es la altura de la
 * barra de estado. [addModifier] lleva el ancla del recorrido.
 */
@Composable
fun BodyPageHeader(
    topInset: Dp,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    addModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = topInset + 12.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cuerpo",
            style = KpknType.display,
            color = WizardColors.text,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Registrar medidas",
            onClick = onAdd,
            modifier = addModifier,
        )
    }
}
