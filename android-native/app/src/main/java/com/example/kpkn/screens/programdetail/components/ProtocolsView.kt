package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.ui.components.KpknAlertDialog

@Composable
fun ProtocolsView(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedProtocol by remember { mutableStateOf<Protocol?>(null) }
    var protocolToApply by remember { mutableStateOf<Protocol?>(null) }
    var filterTag by remember { mutableStateOf<String?>(null) }
    var showApplyDialog by remember { mutableStateOf(false) }

    val allTags = remember { PROTOCOL_LIBRARY.flatMap { it.tags }.distinct().sorted() }
    val filtered = remember(filterTag) {
        if (filterTag == null) PROTOCOL_LIBRARY
        else PROTOCOL_LIBRARY.filter { it.tags.contains(filterTag) }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Protocolos", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text("Sistemas de progresión comprobados", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // Tag filters
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = filterTag == null,
                    onClick = { filterTag = null },
                    label = { Text("Todos", fontSize = 9.sp) },
                )
            }
            items(allTags) { tag ->
                FilterChip(
                    selected = filterTag == tag,
                    onClick = { filterTag = if (filterTag == tag) null else tag },
                    label = { Text(tag.replaceFirstChar { it.uppercase() }, fontSize = 9.sp) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Protocol cards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filtered.forEach { protocol ->
                ProtocolCard(
                    protocol = protocol,
                    onClick = { selectedProtocol = protocol },
                )
            }
        }

        Spacer(Modifier.height(120.dp))
    }

    // Detail modal
    if (selectedProtocol != null) {
        ProtocolDetailModal(
            protocol = selectedProtocol!!,
            onApply = { protocolToApply = selectedProtocol; showApplyDialog = true; selectedProtocol = null },
            onDismiss = { selectedProtocol = null },
        )
    }

    // Apply warning dialog
    if (showApplyDialog && protocolToApply != null) {
        KpknAlertDialog(
            onDismissRequest = { showApplyDialog = false; protocolToApply = null },
            title = { Text("Aplicar Protocolo", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Esto reemplazará la estructura actual del programa con \"${protocolToApply?.name}\".")
                    Spacer(Modifier.height(8.dp))
                    Text("¿Continuar?", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    protocolToApply?.let { protocol ->
                        val newProgram = ProgramProtocolEngine.applyProtocol(program, protocol)
                        onUpdateProgram(newProgram)
                    }
                    showApplyDialog = false
                    protocolToApply = null
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDialog = false; protocolToApply = null }) { Text("Cancelar") }
            },
        )
    }
}

// ─── Protocol Card ──────────────────────────────────────────────────────────

@Composable
private fun ProtocolCard(protocol: Protocol, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(protocol.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(protocol.name, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text("por ${protocol.author}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(protocol.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                protocol.tags.forEach { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(tag.replaceFirstChar { it.uppercase() }, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${protocol.blocks.sumOf { it.weeks }} semanas · ${protocol.blocks.size} bloques", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─── Detail Modal ───────────────────────────────────────────────────────────

@Composable
private fun ProtocolDetailModal(protocol: Protocol, onApply: () -> Unit, onDismiss: () -> Unit) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(protocol.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text(protocol.name, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(protocol.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("por ${protocol.author}", fontSize = 10.sp, fontWeight = FontWeight.Bold)

                if (protocol.sessionCategories.isNotEmpty()) {
                    Text("Categorías de sesión", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    protocol.sessionCategories.forEach { cat ->
                        Text("• $cat", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text("Bloques", fontSize = 10.sp, fontWeight = FontWeight.Black)
                protocol.blocks.forEach { block ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(block.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${block.weeks}sem", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Intensidad: ${block.intensityMin}-${block.intensityMax}%", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Objetivo: ${block.goal}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            block.volumeModifier?.let {
                                Text("Modificador volumen: x${it}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply) { Text("Aplicar Protocolo") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
