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
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton

@Composable
fun ProtocolDetailSheet(
    protocol: Protocol,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val recipe = protocol.recipe
    val sampleWeek = recipe?.weeks?.firstOrNull()
    val spec = protocol.fidelitySpec
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${protocol.emoji} ${protocol.name}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
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
                Text("Semana tipo ${week.weekNumber}", fontWeight = FontWeight.Bold, color = Color.White)
                week.days.forEach { day ->
                    Text(
                        "${day.label}: " + day.slots.take(6).joinToString(" → ") { slot ->
                            val sets = slot.sets.size
                            val reps = slot.sets.firstOrNull()?.reps ?: slot.sets.firstOrNull()?.repsMin
                            val pct = slot.sets.firstOrNull()?.percent?.let { "${it.toInt()}%" } ?: "RPE"
                            val tag = if (slot.source == SlotSource.KPKN_DEFAULT) " [KPKN]" else ""
                            "${slot.lift.configurationId.substringBefore("__")} $sets×$reps @$pct$tag"
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
            KpknSheetWhiteButton(text = "Usar este protocolo", onClick = onContinue)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
