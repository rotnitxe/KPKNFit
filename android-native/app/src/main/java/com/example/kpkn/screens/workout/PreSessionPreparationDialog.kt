package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.screens.training.recordedSportLabel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Optional context in the existing session; there is no readiness score or activation gate. */
@Composable
internal fun PreSessionPreparationDialog(
    notes: String,
    previous: List<String>,
    discomforts: List<String>,
    onNotes: (String) -> Unit,
    onDismiss: () -> Unit,
    recordedSportId: String? = null,
    onSport: (String?) -> Unit = {},
) {
    var choosingSport by remember { mutableStateOf(false) }
    // A bounded dialog keeps the action outside the scrolling context, including at large font sizes.
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(240.dp)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(maxHeight.coerceAtMost(600.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Antes de entrenar", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).testTag("pre-session-context"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Consulta los objetivos del plan. El registro breve es opcional y no cambia tus cargas.")
                if (previous.isEmpty()) Text("Sin sesiones anteriores con este identificador. Consulta el historial para otras disciplinas.")
                else {
                    Text("Registros anteriores de esta sesión")
                    previous.forEach { Text(it) }
                    Text("El contexto puede haber cambiado; estas referencias no miden preparación corporal.")
                }
                TextButton(onClick = { choosingSport = !choosingSport }) {
                    Text("Disciplina (opcional): ${SportId.entries.firstOrNull { it.name == recordedSportId }?.let(::recordedSportLabel) ?: "sin especificar"}")
                }
                if (choosingSport) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        TextButton(onClick = { onSport(null); choosingSport = false }) { Text("Sin especificar") }
                        SportId.entries.forEach { sport ->
                            TextButton(onClick = { onSport(sport.name); choosingSport = false }) { Text(recordedSportLabel(sport)) }
                        }
                    }
                }
                Text("Cansancio, molestias o contexto de hoy (opcional)")
                BasicTextField(value = notes, onValueChange = onNotes, minLines = 2, maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(12.dp)
                        .semantics { contentDescription = "Notas opcionales antes de entrenar" })
                if (discomforts.isEmpty()) Text("Sin molestias anteriores registradas.")
                else discomforts.forEach { Text(it) }
            }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Continuar") }
            }
        }
    }
}
