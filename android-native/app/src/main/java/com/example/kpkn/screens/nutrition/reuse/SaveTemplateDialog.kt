package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * «Guardar como plantilla»: pide el nombre (precargado con la comida y la fecha). Valida con [MealReuse.validateTemplateName] antes de
 * entregarlo; [onConfirm] recibe el nombre ya limpio. La escritura la hace quien la abre (durable, con su id estable).
 */
@Composable
fun SaveTemplateDialog(
    initialName: String,
    summary: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        when (val check = MealReuse.validateTemplateName(name)) {
            is MealReuse.TemplateNameCheck.Valid -> onConfirm(check.name)
            is MealReuse.TemplateNameCheck.Invalid -> error = check.message
        }
    }
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { KpknAlertConfirmButton(text = "Guardar", onClick = { submit() }, modifier = Modifier.testTag("template-save-confirm")) },
        dismissButton = { KpknAlertDismissButton(text = "Cancelar", onClick = onDismiss) },
        title = { Text("Guardar como plantilla") },
        text = {
            Column {
                Text(summary, style = KpknType.caption, color = WizardColors.textMuted)
                Spacer(Modifier.height(12.dp))
                KpknTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.testTag("template-name-field"),
                    label = "Nombre",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    error = error,
                )
            }
        },
    )
}
