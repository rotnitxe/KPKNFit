package com.example.kpkn.screens.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.displayName
import androidx.compose.runtime.remember
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton

@Composable
fun ProtocolDetailSheet(
    protocol: Protocol,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    continueLabel: String = "Usar este plan",
    onCreateCopy: (() -> Unit)? = null,
) {
    val recipe = protocol.recipe
    val sampleWeek = recipe?.weeks?.firstOrNull()
    val spec = protocol.fidelitySpec
    val catalogNames = remember(protocol.id) {
        com.example.kpkn.data.exercises.catalogConfigurationDisplayNameIndex()
    }
    val entry = remember(protocol.id) {
        com.example.kpkn.data.programs.PersonalizedPlanCatalog.find("protocol:${protocol.id}")
    }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(entry?.title ?: protocol.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(protocol.name, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            Text(protocol.author, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            protocol.source.primaryUrl?.let {
                Text(it, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            }
            Text(protocol.description, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            val disclaimer = protocol.source.disclaimer ?: "No afiliado a ${protocol.author}"
            Text(disclaimer, color = Color(0xFFFBBF24), style = MaterialTheme.typography.bodySmall)
            spec?.let { fidelity ->
                Text("Requisitos", fontWeight = FontWeight.Bold, color = Color.White)
                val needs = buildList {
                    fidelity.claimedLevel?.let { add("Nivel $it") }
                    add("${fidelity.expectedWeeks} semanas · ${fidelity.expectedDaysPerWeek} días/sem")
                    if (fidelity.requiresPercent) add("Necesita TM / 1RM")
                    if (fidelity.requiresAmrap) add("AMRAP")
                    if (fidelity.requiresRpe) add("RPE")
                }
                Text(needs.joinToString(" · "), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            if (protocol.exemptions.isNotEmpty()) {
                Text("Notas KPKN", fontWeight = FontWeight.Bold, color = Color.White)
                protocol.exemptions.forEach { exemption ->
                    Text(
                        "${exemption.rule} (${exemption.scope}): ${exemption.justification}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            sampleWeek?.let { week ->
                Text("Semana tipo ${week.weekName.ifBlank { week.weekNumber.toString() }}", fontWeight = FontWeight.Bold, color = Color.White)
                week.days.forEach { day ->
                    Text(
                        "${day.label}\n" + day.slots.joinToString("\n") { slot ->
                            val prescription = slot.sets.firstOrNull()
                            val reps = prescription?.reps?.toString()
                                ?: prescription?.repsMin?.let { min ->
                                    prescription.repsMax?.let { max -> "$min–$max" } ?: "$min+"
                                } ?: "Según prescripción"
                            val intensity = prescription?.percent?.let { " · ${it.toInt()}%" }
                                ?: prescription?.rpe?.let { " · RPE $it" }
                                ?: prescription?.rir?.let { " · RIR $it" }.orEmpty()
                            val technique = slot.technique?.let { " · ${it.displayName()}" }.orEmpty()
                            val tag = if (slot.source == SlotSource.KPKN_DEFAULT) " · Aporte KPKN" else ""
                            val name = catalogNames[slot.lift.configurationId] ?: "Ejercicio pendiente de catálogo"
                            "$name · ${slot.sets.size} × $reps$intensity$technique$tag"
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (week.days.any { day -> day.slots.any { it.source == SlotSource.KPKN_DEFAULT } }) {
                    Text(
                        "[KPKN] = sugerido por KPKN, no del autor.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            KpknSheetWhiteButton(text = continueLabel, onClick = onContinue)
            onCreateCopy?.let { createCopy ->
                TextButton(onClick = createCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Crear una copia", color = Color.White)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
