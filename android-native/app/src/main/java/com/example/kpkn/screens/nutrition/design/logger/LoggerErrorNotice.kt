package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso de un guardado que no salió (N3b): módulo teñido de `danger` con el mensaje en lenguaje llano. Con [onRetry], «Reintentar»
 * vuelve a intentar la acción; el borrador sigue en la hoja. [onDismiss] cierra el aviso. Nunca muestra un detalle técnico.
 */
@Composable
fun LoggerErrorNotice(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    KpknModule(modifier = modifier, accent = WizardColors.danger) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = KpknLineIcons.Info,
                contentDescription = null,
                tint = WizardColors.danger,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = KpknType.body,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar aviso", onClick = onDismiss)
        }
        if (onRetry != null) {
            KpknTextAction(text = "Reintentar", onClick = onRetry, icon = KpknLineIcons.Repeat)
        }
    }
}
