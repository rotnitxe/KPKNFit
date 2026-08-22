package com.example.kpkn.screens.wikilab

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.*

// ─── CONCEPTS LIST SCREEN ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingConceptsScreen(
    onNavigateToConcept: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ConceptCategory?>(null) }

    val categories = remember { getConceptCategories() }
    val filtered = remember(query, selectedCategory) {
        val bySearch = searchConcepts(query)
        if (selectedCategory != null) {
            bySearch.filter { it.category == selectedCategory }
        } else bySearch
    }

    val grouped = remember(filtered) {
        filtered.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {}, // Cabecera fija eliminada para máximo espacio de scroll
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ─── Scrollable Header (Retroceso + Título) ───────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Conceptos Clave",
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            }

            // ─── Search & Subtitle ────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Glosario enciclopédico de principios de entrenamiento, biomecánica aplicada y metodologías de fuerza.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar concepto...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Color.White,
                        ),
                    )
                }
            }

            // ─── Category Chips ───────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = {
                                Text(
                                    "Todos",
                                    fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = APRENDE_MUTED_FILL,
                                labelColor = Color.White.copy(alpha = 0.72f),
                                selectedLabelColor = Color.White,
                            ),
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = {
                                Text(
                                    cat.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = APRENDE_MUTED_FILL,
                                labelColor = Color.White.copy(alpha = 0.72f),
                                selectedLabelColor = Color.White,
                                iconColor = APRENDE_LINK_COLOR,
                                selectedLeadingIconColor = APRENDE_LINK_COLOR,
                            ),
                            leadingIcon = {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = APRENDE_DIVIDER,
                                ) {}
                            },
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                }
            }

            // ─── Grouped Concept Cards ────────────────────────────────────
            grouped.forEach { (category, concepts) ->
                item {
                    WikiSectionHeader(category.label.uppercase())
                }

                items(concepts, key = { it.id }) { concept ->
                    ConceptListRow(
                        concept = concept,
                        onClick = { onNavigateToConcept(concept.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ConceptListRow(
    concept: TrainingConcept,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = concept.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                color = APRENDE_LINK_COLOR
            ),
            fontWeight = FontWeight.Bold,
        )
        
        Spacer(Modifier.height(4.dp))
        
        Text(
            text = concept.shortDescription,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.8f),
        )
        
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = APRENDE_DIVIDER, thickness = 1.dp)
    }
}

// ─── CONCEPT DETAIL SCREEN ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptDetailScreen(
    conceptId: String,
    onNavigateToConcept: (String) -> Unit,
    onBack: () -> Unit,
) {
    val concept = remember(conceptId) {
        TRAINING_CONCEPTS_DATABASE.find { it.id == conceptId }
    }

    if (concept == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text("Concepto no encontrado", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
            }
        }
        return
    }

    val relatedConcepts = remember(concept) {
        concept.relatedConcepts.mapNotNull { id ->
            TRAINING_CONCEPTS_DATABASE.find { it.id == id }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {}, // Cabecera fija eliminada
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Scrollable Header (Retroceso + Título) ───────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = concept.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                }
            }

            // ─── Short Description ────────────────────────────────────────
            item {
                Text(
                    concept.shortDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color = Color.White.copy(alpha = 0.9f)
                    ),
                    lineHeight = 22.sp,
                )
            }

            // ─── Infobox ──────────────────────────────────────────────────
            item {
                WikiConceptInfobox(concept)
            }

            // ─── Definition ──────────────────────────────────────────────
            item {
                Column {
                    WikiSectionHeader("Definición")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        concept.definition,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.8f)
                        ),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // ─── Key Points ───────────────────────────────────────────────
            if (concept.keyPoints.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Puntos Clave")
                        Spacer(Modifier.height(8.dp))
                        concept.keyPoints.forEach { point ->
                            Row(
                                modifier = Modifier.padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Surface(
                                    modifier = Modifier.padding(top = 8.dp).size(6.dp),
                                    shape = CircleShape,
                                    color = APRENDE_DIVIDER,
                                ) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    point,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Practical Application ────────────────────────────────────
            item {
                Column {
                    WikiSectionHeader("Aplicación Práctica")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        concept.practicalApplication,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.8f)
                        ),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // ─── Examples ─────────────────────────────────────────────────
            if (concept.examples.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Ejemplos")
                        Spacer(Modifier.height(8.dp))
                        concept.examples.forEachIndexed { i, example ->
                            Row(
                                modifier = Modifier.padding(start = 12.dp).padding(vertical = 4.dp),
                            ) {
                                Text(
                                    "${i + 1}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.72f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    example,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Common Mistakes ──────────────────────────────────────────
            if (concept.commonMistakes.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Errores Comunes")
                        Spacer(Modifier.height(8.dp))
                        concept.commonMistakes.forEach { mistake ->
                            Row(
                                modifier = Modifier.padding(start = 12.dp).padding(vertical = 4.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    modifier = Modifier.size(16.dp).offset(y = 2.dp),
                                    tint = Color(0xFFE53935),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    mistake,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Related Concepts ─────────────────────────────────────────
            if (relatedConcepts.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Conceptos Relacionados")
                        Spacer(Modifier.height(8.dp))
                        relatedConcepts.forEach { related ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToConcept(related.id) }
                                    .padding(start = 12.dp).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(6.dp),
                                    shape = CircleShape,
                                    color = APRENDE_DIVIDER,
                                ) {}
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = related.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = APRENDE_LINK_COLOR
                                        ),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = related.category.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── WIKIPEDIA UI COMPONENTS ──────────────────────────────────────────────

@Composable
private fun WikiSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = APRENDE_DIVIDER, thickness = 1.dp)
    }
}

@Composable
private fun WikiConceptInfobox(concept: TrainingConcept) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Ficha Técnica Concepto",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(color = APRENDE_DIVIDER)
            
            InfoboxRow("Concepto", concept.name)
            InfoboxRow("Categoría", concept.category.label)
        }
    }
}

@Composable
private fun InfoboxRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
