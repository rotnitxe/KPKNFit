package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.design.KpknType

/**
 * Explicación de un reparto (P5): su descripción, sus ventajas y sus precauciones, en un diálogo KPKN con un solo botón, «Cerrar». Sustituye
 * al `AlertDialog` de Material de siempre.
 */
@Composable
internal fun SplitInfoDialog(
    split: SplitTemplate,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = split.name, style = KpknType.title, color = WizardColors.text)
        },
        text = {
            Column {
                Text(text = split.description, style = KpknType.body, color = WizardColors.textMuted)
                Spacer(Modifier.height(14.dp))
                SplitExplainer(title = "Beneficios", items = split.pros.take(4))
                Spacer(Modifier.height(12.dp))
                SplitExplainer(title = "Precauciones", items = split.cons.take(4))
            }
        },
        confirmButton = {
            KpknAlertConfirmButton(text = "Cerrar", onClick = onDismiss)
        },
    )
}

/** Un bloque de la explicación: su título en `bodyStrong` y una línea por punto en `caption`. */
@Composable
private fun SplitExplainer(title: String, items: List<String>) {
    Column {
        Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
        items.forEach { item ->
            Text(text = "• $item", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}
