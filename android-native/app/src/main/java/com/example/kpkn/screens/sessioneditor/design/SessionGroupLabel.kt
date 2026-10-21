package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Etiqueta de un espacio de la sesión («CARDIO» o «FUERZA») sobre el negro, sin caja (SESSION_EDITOR_V1 §2, punto 5). Con [moveLabel]
 * lleva a su derecha la acción que mueve ese espacio al inicio o al final.
 */
@Composable
internal fun SessionGroupLabel(
    label: String,
    modifier: Modifier = Modifier,
    moveLabel: String? = null,
    onMove: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Alineada con el borde del módulo, como la pantalla (16 dp).
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (moveLabel != null) {
            KpknTextAction(text = moveLabel, onClick = onMove, color = WizardColors.textMuted)
        }
    }
}
