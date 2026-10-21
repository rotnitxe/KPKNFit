package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso de transición de bloque (P2, KPKN_UI_V2 §1): un módulo teñido de `energia` con el mensaje en tipo frase. La acción principal va en
 * una píldora de tinta; la alternativa, en una píldora de borde; y descartar, en texto (sin mayúsculas, con hasta dos líneas). Sin
 * [primaryLabel] ni [secondaryLabel], solo queda descartar. Sin estado.
 */
@Composable
internal fun ProgramTransitionModule(
    message: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth(), accent = SymbolPalette.energia) {
        Text(text = message, style = KpknType.body, color = WizardColors.text)
        if (primaryLabel != null) {
            Spacer(Modifier.height(12.dp))
            KpknCompactPill(text = primaryLabel, onClick = onPrimary, fullWidth = true)
        }
        if (secondaryLabel != null) {
            Spacer(Modifier.height(8.dp))
            KpknCompactPill(text = secondaryLabel, onClick = onSecondary, filled = false, fullWidth = true)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onDismiss),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dismissLabel,
                style = KpknType.action,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
