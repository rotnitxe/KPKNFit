package com.example.kpkn.screens.wikilab

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.*

// ═══════════════════════════════════════════════════════════════════════
// CONCEPTS LIST SCREEN
// ═══════════════════════════════════════════════════════════════════════

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

    // Group by category
    val grouped = remember(filtered) {
        filtered.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Conceptos Clave",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = PaddingValues(bottom = 164.dp),
        ) {
            // ─── Hero Banner ──────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF101317),
                                    Color(0xFF181C22),
                                    Color.Black,
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Column {
                        Text(
                            "Conceptos clave",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            "${TRAINING_CONCEPTS_DATABASE.size} conceptos fundamentales del entrenamiento.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                            color = Color.White.copy(alpha = 0.7f),
                        )

                        Spacer(Modifier.height(16.dp))

                        // Search
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "Buscar concepto...",
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, "Limpiar", tint = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.4f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )
                    }
                }
            }

            // ─── Category Chips ───────────────────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
                            leadingIcon = {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = cat.color,
                                ) {}
                            },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            }

            // ─── Results Count ────────────────────────────────────────────
            item {
                Text(
                    "${filtered.size} conceptos",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // ─── Grouped Concept Cards ────────────────────────────────────
            grouped.forEach { (category, concepts) ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = category.color,
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Text(
                            category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = category.color,
                        )
                    }
                }

                items(concepts, key = { it.id }) { concept ->
                    ConceptListCard(
                        concept = concept,
                        onClick = { onNavigateToConcept(concept.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConceptListCard(
    concept: TrainingConcept,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212),
        ),
        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category color indicator
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = concept.category.color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        concept.name.first().toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                        fontWeight = FontWeight.Black,
                        color = concept.category.color,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    concept.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    concept.shortDescription,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = concept.category.color.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CONCEPT DETAIL SCREEN
// ═══════════════════════════════════════════════════════════════════════

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            concept.name,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = concept.category.color,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = concept.category.color.copy(alpha = 0.12f),
                        ) {
                            Text(
                                concept.category.label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = concept.category.color,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Black,
                    scrolledContainerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ─── Header ──────────────────────────────────────────────────
            item {
                Column {
                    Text(
                        concept.shortDescription,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Definition ──────────────────────────────────────────────
            item {
                WikiSection(
                    title = "DEFINICIÓN",
                    icon = Icons.Default.MenuBook,
                    color = concept.category.color,
                ) {
                    Text(
                        concept.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Key Points ──────────────────────────────────────────────
            if (concept.keyPoints.isNotEmpty()) {
                item {
                    WikiSection(
                        title = "PUNTOS CLAVE",
                        icon = Icons.Default.LightbulbCircle,
                        color = Color(0xFFFF8F00),
                    ) {
                        concept.keyPoints.forEach { point ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                crossAxisAlignment = CrossAxisAlignment.Start,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .offset(y = 7.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFFF8F00),
                                ) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    point,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Practical Application ───────────────────────────────────
            item {
                WikiSection(
                    title = "APLICACIÓN PRÁCTICA",
                    icon = Icons.Default.Build,
                    color = Color(0xFF43A047),
                ) {
                    Text(
                        concept.practicalApplication,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Examples ────────────────────────────────────────────────
            if (concept.examples.isNotEmpty()) {
                item {
                    WikiSection(
                        title = "EJEMPLOS",
                        icon = Icons.Default.Lightbulb,
                        color = Color(0xFF1E88E5),
                    ) {
                        concept.examples.forEachIndexed { i, example ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                            ) {
                                Text(
                                    "${i + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E88E5),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    example,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Common Mistakes ─────────────────────────────────────────
            if (concept.commonMistakes.isNotEmpty()) {
                item {
                    WikiSection(
                        title = "ERRORES COMUNES",
                        icon = Icons.Default.Warning,
                        color = Color(0xFFE53935),
                    ) {
                        concept.commonMistakes.forEach { mistake ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    modifier = Modifier.size(14.dp).offset(y = 2.dp),
                                    tint = Color(0xFFE53935),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    mistake,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Related Concepts ────────────────────────────────────────
            if (relatedConcepts.isNotEmpty()) {
                item {
                    WikiSection(
                        title = "CONCEPTOS RELACIONADOS",
                        icon = Icons.Default.Link,
                        color = Color(0xFF5C6BC0),
                    ) {
                        relatedConcepts.forEach { related ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onNavigateToConcept(related.id) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = related.category.color.copy(alpha = 0.1f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            related.name.first().toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = related.category.color,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        related.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        related.category.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = related.category.color,
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = related.category.color.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun WikiSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(15.dp),
                tint = color,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// Row helper for crossAxisAlignment
@Composable
private fun Row(
    modifier: Modifier = Modifier,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Center,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = when (crossAxisAlignment) {
            CrossAxisAlignment.Start -> Alignment.Top
            CrossAxisAlignment.End -> Alignment.Bottom
            else -> Alignment.CenterVertically
        },
        content = content,
    )
}

private enum class CrossAxisAlignment { Start, Center, End }
