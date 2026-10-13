package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso de un análisis que no salió bien (NUTRITION_V1 §3): módulo teñido de `danger` con el título y el mensaje en lenguaje llano, y
 * «Reintentar», que vuelve a interpretar el texto ([onRetry]). El detalle técnico no se ve por defecto: se pide con «Ver detalle»
 * ([onToggleDetail]), y [detailShown] lo muestra en `caption`, sin monoespaciado. Quien compone guarda ese estado.
 */
@Composable
fun LoggerAnalysisNotice(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    technicalDetail: String? = null,
    detailShown: Boolean = false,
    onToggleDetail: () -> Unit = {},
) {
    KpknModule(modifier = modifier, accent = WizardColors.danger) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = KpknLineIcons.Info,
                contentDescription = null,
                tint = WizardColors.danger,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
                Text(text = message, style = KpknType.body, color = WizardColors.textMuted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KpknTextAction(text = "Reintentar", onClick = onRetry, icon = KpknLineIcons.Repeat)
            if (technicalDetail != null) {
                Spacer(Modifier.weight(1f))
                KpknTextAction(
                    text = if (detailShown) "Ocultar detalle" else "Ver detalle",
                    onClick = onToggleDetail,
                )
            }
        }
        if (technicalDetail != null && detailShown) {
            Text(text = technicalDetail, style = KpknType.caption, color = WizardColors.textFaint)
        }
    }
}
