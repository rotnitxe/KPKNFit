package com.example.kpkn.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Esqueleto común de [KpknRadioRow] y [KpknCheckRow]: fila de 56 dp como mínimo, la marca a la izquierda en un hueco de 24 dp, el
 * título en `bodyStrong` y el subtítulo en `caption`. [modifier] trae ya el `selectable` o el `toggleable` de quien la usa. Deshabilitada,
 * la fila va al 38 %.
 */
@Composable
internal fun KpknOptionRow(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    mark: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            mark()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
            if (subtitle != null) {
                Text(text = subtitle, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
    }
}
