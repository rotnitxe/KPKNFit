package com.example.kpkn.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.kpkn.services.diagnostics.ReportOrigin
import com.example.kpkn.services.diagnostics.ReportRequest
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingReportRequest(
    val origin: ReportOrigin,
    val screen: String,
    val sessionId: String? = null,
    val workoutId: String? = null,
)

object ReportRequestBus {
    private val _pending = MutableStateFlow<PendingReportRequest?>(null)
    val pending: StateFlow<PendingReportRequest?> = _pending.asStateFlow()

    fun requestGesture(screen: String) {
        _pending.value = PendingReportRequest(ReportOrigin.GESTURE, screen)
    }

    fun requestVoice(screen: String, sessionId: String? = null, workoutId: String? = null) {
        _pending.value = PendingReportRequest(ReportOrigin.VOICE, screen, sessionId, workoutId)
    }

    fun consume() {
        _pending.value = null
    }
}

@Composable
fun ReportDialog(
    request: PendingReportRequest,
    onDismiss: () -> Unit,
    onSave: (ReportRequest) -> Unit,
) {
    var comment by remember(request) { mutableStateOf("") }
    var category by remember(request) { mutableStateOf("") }

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar inconsistencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El comentario y el contexto técnico se guardarán en un JSONL. La IA lo analizará después, si está configurada.")
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(8_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("¿Qué problema encontraste?") },
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoría (opcional)") },
                    singleLine = true,
                )
            }
        },
        dismissButton = {
            KpknAlertDismissButton(text = "Cancelar", onClick = onDismiss)
        },
        confirmButton = {
            KpknAlertConfirmButton(
                text = "Guardar reporte",
                enabled = comment.isNotBlank(),
                onClick = {
                    onSave(
                        ReportRequest(
                            origin = request.origin,
                            comment = comment,
                            category = category.ifBlank { null },
                            screen = request.screen,
                            sessionId = request.sessionId,
                            workoutId = request.workoutId,
                        ),
                    )
                },
            )
        },
    )
}
