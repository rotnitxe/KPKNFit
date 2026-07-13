package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.calculateSearchScore
import com.example.kpkn.domain.exercises.deduplicateCatalogVisualResults

// ═══════════════════════════════════════════════════════════════════════
private fun muscleColor(name: String): Color = wikilabMuscleColor(name)

// ═══════════════════════════════════════════════════════════════════════
// CATEGORY FILTER
// ═══════════════════════════════════════════════════════════════════════

private data class MuscleCategory(val label: String, val keywords: List<String>, val color: Color)

private val CATEGORIES = listOf(
    MuscleCategory("Todos", emptyList(), Color(0xFF8A9099)),
    MuscleCategory("Pecho", listOf("Pectorales"), Color(0xFF1E88E5)),
    MuscleCategory("Espalda", listOf("Dorsales", "Trapecio", "Erectores Espinales"), Color(0xFF43A047)),
    MuscleCategory("Hombros", listOf("Deltoides"), Color(0xFFFF8F00)),
    MuscleCategory("Piernas", listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores"), Color(0xFF9C27B0)),
    MuscleCategory("Brazos", listOf("Bíceps", "Tríceps", "Antebrazo"), Color(0xFFE53935)),
    MuscleCategory("Core", listOf("Core", "Abdomen"), Color(0xFF00ACC1)),
)

// ═══════════════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiLabScreen(
    modifier: Modifier = Modifier,
    onCreateExercise: () -> Unit = {},
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) }

    val customExercises by CustomExerciseRepository.customExercises.collectAsState()
    val exerciseCatalog = remember(customExercises) {
        (EXERCISE_DATABASE + customExercises)
            .associateBy { it.id.lowercase() }
            .values
            .toList()
    }

    val filtered = remember(query, selectedCategory, exerciseCatalog) {
        val cat = CATEGORIES[selectedCategory]
        val matches = exerciseCatalog.filter { ex ->
            val canonicalInvolved = collapseInvolvedMusclesToCanonical(ex.involvedMuscles)
            val catMatch = cat.keywords.isEmpty() || ex.involvedMuscles.any { m ->
                cat.keywords.any { kw -> canonicalMuscleDisplayName(m.muscle, m.emphasis).contains(kw, ignoreCase = true) }
            }
            val textMatch = if (query.isBlank()) {
                true
            } else {
                calculateSearchScore(ex, query) > 0 ||
                    canonicalInvolved.any { m -> m.muscle.contains(query, ignoreCase = true) }
            }
            catMatch && textMatch
        }
        val sorted = if (query.isBlank()) {
            matches.sortedBy { it.name }
        } else {
            matches.sortedWith(
                compareByDescending<ExerciseMuscleInfo> { calculateSearchScore(it, query) }
                    .thenBy { kotlin.math.abs(it.name.length - query.trim().length) }
                    .thenBy { it.name }
            )
        }
        deduplicateCatalogVisualResults(sorted)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, selectedCategory) {
        listState.scrollToItem(0)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {}, // Cabecera fija eliminada para máximo espacio de scroll
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                // ─── Scrollable Header (Retroceso + Título) ───────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Ejercicios",
                            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                    }
                }

                // ─── Subtitle & Description ───────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Índice de ejercicios y guía anatómica. Selecciona un elemento para ver su nivel, músculos implicados, equipamiento y fatiga.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // ─── Search Bar ───────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar artículo...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, "Limpiar")
                                }
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
                    Spacer(Modifier.height(8.dp))
                }

                // ─── Category Filter Chips ────────────────────────────────
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(CATEGORIES.indices.toList()) { idx ->
                            val cat = CATEGORIES[idx]
                            val selected = selectedCategory == idx
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = idx },
                                label = {
                                    Text(
                                        cat.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                    )
                                },
                                leadingIcon = if (idx > 0) {
                                    {
                                        Surface(Modifier.size(6.dp), CircleShape, cat.color) {}
                                    }
                                } else null,
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ─── Results Counter ──────────────────────────────────────
                item {
                    Text(
                        text = "${filtered.size} artículos",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // ─── List of Exercise Rows ────────────────────────────────
                items(filtered, key = { it.id }) { exercise ->
                    ExerciseCard(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            FloatingActionButton(
                onClick = onCreateExercise,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear ejercicio")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// EXERCISE ROW (NO CARDS)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ExerciseCard(exercise: ExerciseMuscleInfo, onClick: () -> Unit) {
    val canonicalInvolved = remember(exercise.id, exercise.involvedMuscles) {
        collapseInvolvedMusclesToCanonical(exercise.involvedMuscles)
    }
    val primaryMuscles = canonicalInvolved.filter { it.role == MuscleRole.PRIMARY }
    val secondaryCount = canonicalInvolved.count { it.role != MuscleRole.PRIMARY }
    val equipment = exercise.equipment ?: "Peso Corporal"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                color = Color(0xFF29B6F6) // Wikipedia link color
            ),
            fontWeight = FontWeight.Bold,
        )
        
        Spacer(Modifier.height(4.dp))
        
        Text(
            text = "Músculos principales: " + primaryMuscles.joinToString(", ") { it.muscle } + 
                (if (secondaryCount > 0) " (+$secondaryCount secundarios)" else ""),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.8f),
        )
        
        Text(
            text = "Equipamiento: $equipment",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.5f),
        )
        
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)
    }
}
