package com.example.kpkn.screens.myrings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.SleepLogExtended

@Composable
fun SleepTrackingSection(
    sleepLogs: List<SleepLogExtended>,
    onSaveSleep: (SleepLogExtended) -> Unit,
    onDeleteSleep: (String) -> Unit,
    buildSleepLog: (bedTime: String, wakeTime: String, quality: Int, awakenings: Int, notes: String?) -> SleepLogExtended,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SUEÑO",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            FilledTonalIconButton(
                onClick = { showSheet = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar sueño", modifier = Modifier.size(18.dp))
            }
        }

        if (sleepLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sin registros de sueño.\nToca + para añadir uno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        } else {
            sleepLogs.take(7).forEach { log ->
                SleepLogRow(
                    log = log,
                    onDelete = { onDeleteSleep(log.id) },
                )
            }
        }
    }

    if (showSheet) {
        SleepLogBottomSheet(
            onDismiss = { showSheet = false },
            onSave = { bedTime, wakeTime, quality, awakenings, notes ->
                onSaveSleep(buildSleepLog(bedTime, wakeTime, quality, awakenings, notes))
                showSheet = false
            },
        )
    }
}

@Composable
private fun SleepLogRow(
    log: SleepLogExtended,
    onDelete: () -> Unit,
) {
    val qualityColor = when (log.quality) {
        5 -> Color(0xFF22C55E)
        4 -> Color(0xFF4ADE80)
        3 -> Color(0xFFFACC15)
        2 -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    val qualityLabel = when (log.quality) {
        5 -> "Excelente"
        4 -> "Bueno"
        3 -> "Regular"
        2 -> "Malo"
        else -> "Muy malo"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        log.date,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${log.bedTime} → ${log.wakeTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${"%.1f".format(log.duration)}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        qualityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = qualityColor,
                        fontWeight = FontWeight.Medium,
                    )
                    if (log.awakenings > 0) {
                        Text(
                            "${log.awakenings} despertares",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                        )
                    }
                }
                log.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepLogBottomSheet(
    onDismiss: () -> Unit,
    onSave: (bedTime: String, wakeTime: String, quality: Int, awakenings: Int, notes: String?) -> Unit,
) {
    var bedTime by remember { mutableStateOf("23:00") }
    var wakeTime by remember { mutableStateOf("07:00") }
    var quality by remember { mutableIntStateOf(3) }
    var awakenings by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Registrar sueño",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )

            // Time inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = bedTime,
                    onValueChange = { bedTime = it },
                    label = { Text("Hora de dormir") },
                    placeholder = { Text("23:00") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = wakeTime,
                    onValueChange = { wakeTime = it },
                    label = { Text("Hora de despertar") },
                    placeholder = { Text("07:00") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                )
            }

            // Quality slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Calidad del sueño",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    val qualityLabel = when (quality) {
                        1 -> "Muy malo"
                        2 -> "Malo"
                        3 -> "Regular"
                        4 -> "Bueno"
                        else -> "Excelente"
                    }
                    Text(
                        "$quality/5 · $qualityLabel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..5).forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = { Text("$q", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Awakenings
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Despertares nocturnos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { if (awakenings > 0) awakenings-- },
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Text("-", fontWeight = FontWeight.Black)
                        }
                        Text(
                            "$awakenings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                        )
                        TextButton(
                            onClick = { awakenings++ },
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Text("+", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )

            // Save button
            Button(
                onClick = {
                    onSave(bedTime.trim(), wakeTime.trim(), quality, awakenings, notes.takeIf { it.isNotBlank() })
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Guardar", fontWeight = FontWeight.Black)
            }
        }
    }
}
