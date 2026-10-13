package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknType

/**
 * Configuración del registro (N3b): «Ajustar medidas» abre las medidas de casa, y «Olvidar lo aprendido» borra las elecciones
 * recordadas (en `danger`). «Cerrar» es la única acción del diálogo.
 */
@Composable
fun LoggerSettingsDialog(
    onAdjustMeasures: () -> Unit,
    onForgetLearned: () -> Unit,
    onClose: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "Configuración",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LoggerSettingsRow(
                    title = "Ajustar medidas",
                    detail = "Personaliza el volumen en ml de tazas y platos",
                    onClick = onAdjustMeasures,
                )
                LoggerFoodDivider()
                LoggerSettingsRow(
                    title = "Olvidar lo aprendido",
                    detail = "Borra las elecciones y porciones recordadas",
                    onClick = onForgetLearned,
                    destructive = true,
                )
            }
        },
        confirmButton = { KpknAlertConfirmButton(text = "Cerrar", onClick = onClose) },
    )
}

/** Una opción del diálogo: título y pie, con toda la fila tocable (48 dp). */
@Composable
private fun LoggerSettingsRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = KpknType.bodyStrong,
            color = if (destructive) WizardColors.danger else WizardColors.text,
        )
        Text(text = detail, style = KpknType.caption, color = WizardColors.textMuted)
    }
}
