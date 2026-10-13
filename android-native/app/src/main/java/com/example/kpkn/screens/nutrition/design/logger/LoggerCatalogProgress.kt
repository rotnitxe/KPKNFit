package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknType

/**
 * Avance de la importación del catálogo mientras se busca: «Preparando catálogo…», el porcentaje y una barra KPKN. Los resultados
 * que salen mientras tanto son los que ya hay, y lo dice.
 */
@Composable
fun LoggerCatalogProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val shown = progress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Preparando catálogo…", style = KpknType.caption, color = WizardColors.textMuted)
            Text(text = "${(shown * 100).toInt()}%", style = KpknType.caption, color = WizardColors.textMuted)
        }
        KpknProgressBar(progress = shown)
        Text(
            text = "Los resultados salen de lo que ya hay.",
            style = KpknType.caption,
            color = WizardColors.textFaint,
        )
    }
}
