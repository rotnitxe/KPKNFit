package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate

@Composable
fun SplitView(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showGallery by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<SplitTemplate?>(null) }
    var showMigrationDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Trial banner
        val isNewSplit = program.selectedSplitId != null && !program.splitTrialSeen
        if (isNewSplit) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0891B2)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Periodo de Prueba", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
                        Text("Prueba este split por una semana. Siempre podrás cambiarlo.", fontSize = 9.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                    IconButton(onClick = {
                        onUpdateProgram(program.copy(splitTrialSeen = true))
                    }) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Current split header
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF6750A4))) {
            Column(Modifier.padding(16.dp)) {
                Text("Gestor de Splits", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
                Text(
                    program.selectedSplitId?.let { sid -> SPLIT_TEMPLATES.find { it.id == sid }?.name ?: sid } ?: "Sin split definido",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Change split button
        OutlinedButton(onClick = { showGallery = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Cambiar Split", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        // Split gallery
        if (showGallery) {
            SplitGallery(
                currentSplitId = program.selectedSplitId,
                onSelect = { template ->
                    selectedTemplate = template
                    showMigrationDialog = true
                    showGallery = false
                },
                onDismiss = { showGallery = false },
            )
        }

        // Migration dialog
        if (showMigrationDialog && selectedTemplate != null) {
            SplitMigrationDialog(
                template = selectedTemplate!!,
                onMigrate = {
                    onUpdateProgram(program.copy(selectedSplitId = selectedTemplate!!.id, splitTrialSeen = false))
                    showMigrationDialog = false
                    selectedTemplate = null
                },
                onClean = {
                    onUpdateProgram(program.copy(selectedSplitId = selectedTemplate!!.id, splitTrialSeen = false))
                    showMigrationDialog = false
                    selectedTemplate = null
                },
                onDismiss = { showMigrationDialog = false; selectedTemplate = null },
            )
        }

        // Current split pattern visualization
        val currentTemplate = SPLIT_TEMPLATES.find { it.id == program.selectedSplitId }
        if (currentTemplate != null) {
            Text("Patrón actual", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SplitPatternVisualizer(pattern = currentTemplate.pattern)
            Spacer(Modifier.height(8.dp))
            Text("Dificultad: ${currentTemplate.difficulty.name}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (currentTemplate.pros.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("✓ ${currentTemplate.pros.first()}", fontSize = 9.sp, color = Color(0xFF10B981))
            }
            if (currentTemplate.cons.isNotEmpty()) {
                Text("✗ ${currentTemplate.cons.first()}", fontSize = 9.sp, color = Color(0xFFEF4444))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ─── Split Gallery ──────────────────────────────────────────────────────────

@Composable
private fun SplitGallery(
    currentSplitId: String?,
    onSelect: (SplitTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Plantillas de Split", fontSize = 14.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SPLIT_TEMPLATES) { template ->
                    SplitTemplateCard(
                        template = template,
                        isSelected = template.id == currentSplitId,
                        onClick = { onSelect(template) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitTemplateCard(template: SplitTemplate, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(template.name, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(template.description, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (isSelected) {
                    Icon(Icons.Default.Check, "Seleccionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(template.pattern) { day ->
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (day == "Descanso") MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(day.take(4), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                template.tags.forEach { tag ->
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(tag.name.replace("_", " "), fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─── Pattern Visualizer ─────────────────────────────────────────────────────

@Composable
fun SplitPatternVisualizer(pattern: List<String>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(pattern) { day ->
            val isRest = day == "Descanso"
            Box(
                Modifier.size(width = 40.dp, height = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRest) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(0.5.dp, if (isRest) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.take(3),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRest) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Migration Dialog ───────────────────────────────────────────────────────

@Composable
private fun SplitMigrationDialog(
    template: SplitTemplate,
    onMigrate: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar Split", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Aplicar: ${template.name}")
                Spacer(Modifier.height(8.dp))
                Text("¿Deseas migrar los ejercicios existentes o empezar limpio?", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClean) { Text("Empezar limpio") }
                Button(onClick = onMigrate) { Text("Migrar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
