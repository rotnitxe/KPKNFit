package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Línea de una superserie (SESSION_EDITOR_V1 §2, punto 6): «Superserie · 3 rondas · 1:30 entre rondas» en `caption` y, a la derecha,
 * «Editar», que despliega la gestión de la superserie. [dragModifier] va en el texto: la pulsación larga arrastra el bloque entero.
 */
@Composable
internal fun SessionSupersetCaption(
    text: String,
    editing: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            // Hasta dos líneas: «entre rondas» no se pierde al 130 % de letra.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(dragModifier)
                .padding(vertical = 12.dp),
        )
        KpknTextAction(
            text = "Editar",
            onClick = onEdit,
            color = if (editing) WizardColors.text else WizardColors.textMuted,
        )
    }
}
