package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.launch

/**
 * Guardar la sesión actual como plantilla (menú «⋯», «Guardar como plantilla»): nombre obligatorio y descripción opcional. Llama a
 * [onSave], la misma escritura que usa la pestaña de plantillas del asistente. Si sale bien, llama a [onSaved] (quien lo pone cierra el
 * diálogo y avisa); si falla, lo dice en texto llano y deja el formulario como estaba.
 */
@Composable
fun SessionTemplateSaveDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onSave: suspend (String, String) -> Result<SessionTemplate>,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    KpknAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = "Guardar sesión como plantilla",
                style = KpknType.dialogTitle,
                color = WizardColors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KpknTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nombre",
                    singleLine = true,
                    enabled = !saving,
                )
                KpknTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Descripción",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    enabled = !saving,
                )
                error?.let { message ->
                    Text(
                        text = message,
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }
            }
        },
        confirmButton = {
            KpknPrimaryPill(
                text = if (saving) "Guardando…" else "Guardar",
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "Escribe un nombre para la plantilla."
                        return@KpknPrimaryPill
                    }
                    saving = true
                    error = null
                    scope.launch {
                        val outcome: Result<SessionTemplate> = runCatching {
                            onSave(trimmed, description.trim())
                        }.fold(
                            onSuccess = { it },
                            onFailure = { Result.failure<SessionTemplate>(it) },
                        )
                        saving = false
                        outcome.fold(
                            onSuccess = { onSaved() },
                            onFailure = { error = "No se pudo guardar la plantilla. Inténtalo de nuevo." },
                        )
                    }
                },
            )
        },
        dismissButton = {
            KpknTextAction(
                text = "Cancelar",
                onClick = { if (!saving) onDismiss() },
            )
        },
    )
}
