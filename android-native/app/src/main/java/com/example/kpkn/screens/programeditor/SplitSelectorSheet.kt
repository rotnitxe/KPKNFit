package com.example.kpkn.screens.programeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSelectorSheet(
    currentSplitId: String? = null,
    currentStartDay: Int = 1,
    showScopeSelector: Boolean = false, // true in editor mode, false in wizard
    onApply: (SplitTemplate, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf("gallery") } // "gallery" | "configure"
    var selectedSplit by remember { mutableStateOf<SplitTemplate?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterTag by remember { mutableStateOf<SplitTag?>(null) }
    var startDay by remember { mutableIntStateOf(currentStartDay) }

    val filtered = remember(searchQuery, filterTag) {
        SPLIT_TEMPLATES.filter { split ->
            val matchSearch = searchQuery.isBlank() || split.name.contains(searchQuery, ignoreCase = true)
            val matchTag = filterTag == null || split.tags.contains(filterTag)
            matchSearch && matchTag
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            if (step == "gallery") {
                // ── Gallery ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Seleccionar Split", fontSize = 16.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Buscar split") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(8.dp))

                // Tags
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filterTag == null,
                            onClick = { filterTag = null },
                            label = { Text("Todos", fontSize = 9.sp) },
                        )
                    }
                    items(SplitTag.entries) { tag ->
                        FilterChip(
                            selected = filterTag == tag,
                            onClick = { filterTag = if (filterTag == tag) null else tag },
                            label = { Text(tag.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 9.sp) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(filtered) { split ->
                        SplitCard(
                            split = split,
                            isCurrent = split.id == currentSplitId,
                            onClick = {
                                selectedSplit = split
                                step = "configure"
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

            } else {
                // ── Configure ────────────────────────────────────────────
                val split = selectedSplit ?: return@Column

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { step = "gallery" }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                    Text(split.name, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Pattern preview
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        split.pattern.forEach { day ->
                            val isRest = day == "Descanso"
                            Box(
                                Modifier
                                    .height(32.dp)
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isRest) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.primary
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.take(3),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRest) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                )
                            }
                        }
                    }

                    // Start day selector
                    Text("Día de inicio de semana", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val days = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
                    var dayExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = dayExpanded,
                        onExpandedChange = { dayExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = days.getOrElse(startDay) { "Lunes" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Inicio de semana") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                            days.forEachIndexed { idx, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { startDay = idx; dayExpanded = false },
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onApply(split, startDay) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Aplicar Split", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─── Split Card ──────────────────────────────────────────────────────────────

@Composable
fun SplitCard(
    split: SplitTemplate,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = if (isCurrent) CardDefaults.outlinedCardBorder() else null,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(split.name, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(split.description, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (isCurrent) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("Actual", fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Pattern bars
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                split.pattern.forEach { day ->
                    val isRest = day == "Descanso"
                    Box(
                        Modifier
                            .height(20.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isRest) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val workDays = split.pattern.count { it != "Descanso" }
            Text("$workDays días de entrenamiento · ${split.difficulty.name.lowercase()}", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
