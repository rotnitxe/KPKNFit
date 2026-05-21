package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    MuscleCategory("Todos", emptyList(), Color(0xFF757575)),
    MuscleCategory("Pecho", listOf("Pectorales"), Color(0xFFE53935)),
    MuscleCategory("Espalda", listOf("Dorsales", "Trapecio", "Erectores Espinales"), Color(0xFF1E88E5)),
    MuscleCategory("Hombros", listOf("Deltoides"), Color(0xFFFF8F00)),
    MuscleCategory("Piernas", listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores"), Color(0xFF43A047)),
    MuscleCategory("Brazos", listOf("Bíceps", "Tríceps", "Antebrazo"), Color(0xFF8E24AA)),
    MuscleCategory("Core", listOf("Core", "Abdomen"), Color(0xFF00897B)),
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

    Column(modifier = modifier.fillMaxSize()) {
        // ═══ Header with Search ═══
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE53935).copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(20.dp), tint = Color(0xFFE53935))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Ejercicios",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                Text(
                    "${exerciseCatalog.size} ejercicios con métricas de fatiga",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Incluye el aporte al volumen por músculo para entender cuánto suma cada ejercicio.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar ejercicio, músculo o equipo...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            }
        }

        // Results count
        Text(
            "${filtered.size} resultados",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )

        // ═══ Exercise List ═══
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.id }) { exercise ->
                ExerciseCard(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = onCreateExercise,
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp),
            containerColor = Color(0xFFE53935),
            contentColor = Color.White,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear ejercicio")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// EXERCISE CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ExerciseCard(exercise: ExerciseMuscleInfo, onClick: () -> Unit) {
    val canonicalInvolved = remember(exercise.id, exercise.involvedMuscles) {
        collapseInvolvedMusclesToCanonical(exercise.involvedMuscles)
    }
    val primaryMuscles = canonicalInvolved.filter { it.role == MuscleRole.PRIMARY }
    val secondaryCount = canonicalInvolved.count { it.role != MuscleRole.PRIMARY }
    val equipment = exercise.equipment ?: "Peso Corporal"
    val primaryColor = primaryMuscles.firstOrNull()?.muscle?.let { muscleColor(it) }
        ?: MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Primary muscle color indicator
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(Modifier.size(14.dp), CircleShape, primaryColor) {}
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    primaryMuscles.joinToString(", ") { it.muscle },
                    style = MaterialTheme.typography.bodySmall,
                    color = primaryColor,
                    maxLines = 1,
                )
                if (secondaryCount > 0) {
                    Text(
                        "+$secondaryCount secundario${if (secondaryCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    equipment,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
