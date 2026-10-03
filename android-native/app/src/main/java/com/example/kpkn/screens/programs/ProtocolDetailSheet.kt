package com.example.kpkn.screens.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton

/**
 * Ficha de un método de la biblioteca antes de usarlo. Conserva su firma para sus tres llamadores
 * (Programas, Inicio y el editor del macrociclo), pero ya no redacta nada: busca la entrada del
 * catálogo del protocolo y delega en [PlanInfoSheet], la hoja única «Cómo funciona» (C.P9). El botón
 * principal mantiene la etiqueta de cada llamador («Usar este plan», «Reemplazar plan»); cablear la
 * biblioteca y el asistente a «Configurar este plan» es cosa de C.P5 y C.P6.
 */
@Composable
fun ProtocolDetailSheet(
    protocol: Protocol,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    continueLabel: String = "Usar este plan",
    onCreateCopy: (() -> Unit)? = null,
) {
    val entry = remember(protocol.id) { PersonalizedPlanCatalog.find("protocol:${protocol.id}") }
    if (entry != null) {
        PlanInfoSheet(
            entry = entry,
            mode = PlanInfoMode.LIBRARY,
            onDismiss = onDismiss,
            onPrimaryAction = onContinue,
            primaryActionLabel = continueLabel,
            secondaryActionLabel = if (onCreateCopy != null) "Crear una copia" else null,
            onSecondaryAction = onCreateCopy,
        )
    } else {
        ProtocolFallbackSheet(
            protocol = protocol,
            onDismiss = onDismiss,
            onContinue = onContinue,
            continueLabel = continueLabel,
            onCreateCopy = onCreateCopy,
        )
    }
}

/** Un protocolo que el catálogo no ofrece (oculto): solo su nombre y su descripción, sin códigos. */
@Composable
private fun ProtocolFallbackSheet(
    protocol: Protocol,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    continueLabel: String,
    onCreateCopy: (() -> Unit)?,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(protocol.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(protocol.description, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KpknSheetWhiteButton(text = continueLabel, onClick = onContinue)
            onCreateCopy?.let { createCopy ->
                TextButton(onClick = createCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Crear una copia", color = Color.White)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
