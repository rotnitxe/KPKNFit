package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknType

/**
 * Texto de privacidad del registro: todo se calcula en el teléfono, puede equivocarse y se corrige a mano. Español neutro, sin voseo y
 * con menos de 300 caracteres (lo comprueba una prueba).
 */
internal const val LoggerPrivacyText =
    "Todo se calcula en tu teléfono: no se envían tus comidas a servidores ni se usan APIs o IA externas. " +
        "La estimación puede equivocarse en coincidencias y calorías. " +
        "Si algo no cuadra, corrígelo con «Corregir» o ajusta los gramos y los macros a mano."

/** Explicación del registro privado, con su única acción «Entendido». */
@Composable
fun LoggerPrivacyDialog(onClose: () -> Unit) {
    KpknAlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "Registro privado, sin conexión",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        },
        text = {
            Text(text = LoggerPrivacyText, style = KpknType.body, color = WizardColors.textMuted)
        },
        confirmButton = { KpknAlertConfirmButton(text = "Entendido", onClick = onClose) },
    )
}
