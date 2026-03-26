package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.auge.AugeTtcEngine

// ─── Muscle Color Map ────────────────────────────────────────────────────────

private val MUSCLE_COLORS = mapOf(
    "Pecho" to Color(0xFFE53935),
    "Pecho Superior" to Color(0xFFE53935),
    "Dorsal" to Color(0xFF1E88E5),
    "Espalda Baja" to Color(0xFF1565C0),
    "Hombro Anterior" to Color(0xFFFF8F00),
    "Hombro Lateral" to Color(0xFFFFA000),
    "Hombro Posterior" to Color(0xFFFFB300),
    "Cuádriceps" to Color(0xFF43A047),
    "Isquiotibiales" to Color(0xFF2E7D32),
    "Glúteos" to Color(0xFF558B2F),
    "Gemelos" to Color(0xFF33691E),
    "Bíceps" to Color(0xFF8E24AA),
    "Tríceps" to Color(0xFF7B1FA2),
    "Braquial" to Color(0xFF9C27B0),
    "Manguito Rotador" to Color(0xFFFF6F00),
    "Core" to Color(0xFF00897B),
    "Abdomen" to Color(0xFF00897B),
    "Oblicuos" to Color(0xFF00695C),
    "Trapecio Medio" to Color(0xFF1976D2),
    "Trapecios" to Color(0xFF1976D2),
    "Antebrazo" to Color(0xFF795548),
    "Espalda" to Color(0xFF1565C0),
)

private fun muscleColor(name: String): Color =
    MUSCLE_COLORS[name] ?: Color(0xFF757575)

// ─── Category filter ─────────────────────────────────────────────────────────

private data class MuscleCategory(val label: String, val keywords: List<String>)

private val CATEGORIES = listOf(
    MuscleCategory("Todos", emptyList()),
    MuscleCategory("Pecho", listOf("Pecho")),
    MuscleCategory("Espalda", listOf("Dorsal", "Espalda Baja", "Espalda", "Trapecio", "Trapecios")),
    MuscleCategory("Hombros", listOf("Hombro")),
    MuscleCategory("Piernas", listOf("Cuádriceps", "Isquiotibiales", "Glúteos", "Gemelos")),
    MuscleCategory("Brazos", listOf("Bíceps", "Tríceps", "Braquial")),
    MuscleCategory("Core", listOf("Core", "Abdomen", "Oblicuos")),
)

// ─── Main Composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiLabScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var selectedExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }

    val filtered = remember(query, selectedCategory) {
        val cat = CATEGORIES[selectedCategory]
        EXERCISE_DATABASE.filter { ex ->
            // Category filter
            val catMatch = cat.keywords.isEmpty() || ex.involvedMuscles.any { m ->
                cat.keywords.any { kw -> m.muscle.contains(kw, ignoreCase = true) }
            }
            // Text filter
            val textMatch = query.isBlank() ||
                ex.name.contains(query, ignoreCase = true) ||
                ex.involvedMuscles.any { m -> m.muscle.contains(query, ignoreCase = true) } ||
                (ex.equipment?.contains(query, ignoreCase = true) == true)
            catMatch && textMatch
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "WikiLab",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                }
                Text(
                    "Enciclopedia de ejercicios · ${EXERCISE_DATABASE.size} ejercicios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // Search bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar ejercicio, músculo o equipo...") },
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

                // Category chips
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

        // Exercise list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { exercise ->
                ExerciseCard(exercise = exercise, onClick = { selectedExercise = exercise })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Detail sheet
    if (selectedExercise != null) {
        ExerciseDetailSheet(exercise = selectedExercise!!, onDismiss = { selectedExercise = null })
    }
}

// ─── Exercise Card ───────────────────────────────────────────────────────────

@Composable
private fun ExerciseCard(exercise: ExerciseMuscleInfo, onClick: () -> Unit) {
    val primaryMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.PRIMARY }
    val secondaryCount = exercise.involvedMuscles.count { it.role != MuscleRole.PRIMARY }
    val equipment = exercise.equipment ?: "Peso Corporal"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Primary muscle color indicator
            val color = primaryMuscles.firstOrNull()?.muscle?.let { muscleColor(it) }
                ?: MaterialTheme.colorScheme.outline
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(14.dp),
                        shape = RoundedCornerShape(50),
                        color = color,
                    ) {}
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + muscles
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    primaryMuscles.joinToString(", ") { it.muscle },
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
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

            // Equipment chip
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

// ─── Detail Bottom Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(exercise: ExerciseMuscleInfo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()

    val primaryMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.PRIMARY }
    val secondaryMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.SECONDARY }
    val stabilizerMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.STABILIZER }

    // TTC score (tendón/composición)
    val ttc = remember(exercise.id) {
        try { AugeTtcEngine.calculateTTC(exercise.name, exercise.equipment) } catch (_: Exception) { null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp).width(32.dp).height(4.dp)
                    .then(Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.width(32.dp).height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                ) {}
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )

            // Equipment + ID
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        exercise.equipment ?: "Peso Corporal",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        exercise.id,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            // TTC Score
            if (ttc != null && ttc > 0) {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            ttc <= 3 -> Color(0xFF2E7D32).copy(alpha = 0.1f)
                            ttc <= 6 -> Color(0xFFFF8F00).copy(alpha = 0.1f)
                            else -> Color(0xFFE53935).copy(alpha = 0.1f)
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "SEMANAS TTC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.1f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Semanas para recuperación completa del tendón",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "%.1f".format(ttc),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = when {
                                ttc <= 3 -> Color(0xFF2E7D32)
                                ttc <= 6 -> Color(0xFFFF8F00)
                                else -> Color(0xFFE53935)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary muscles
            if (primaryMuscles.isNotEmpty()) {
                Text(
                    "MÚSCULOS PRIMARIOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (0.1f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                primaryMuscles.forEach { m ->
                    MuscleRow(m.muscle, MuscleRole.PRIMARY)
                }
                Spacer(Modifier.height(16.dp))
            }

            // Secondary muscles
            if (secondaryMuscles.isNotEmpty()) {
                Text(
                    "MÚSCULOS SECUNDARIOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (0.1f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                secondaryMuscles.forEach { m ->
                    MuscleRow(m.muscle, MuscleRole.SECONDARY)
                }
                Spacer(Modifier.height(16.dp))
            }

            // Stabilizers
            if (stabilizerMuscles.isNotEmpty()) {
                Text(
                    "ESTABILIZADORES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (0.1f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                stabilizerMuscles.forEach { m ->
                    MuscleRow(m.muscle, MuscleRole.STABILIZER)
                }
            }
        }
    }
}

// ─── Muscle Row ──────────────────────────────────────────────────────────────

@Composable
private fun MuscleRow(name: String, role: MuscleRole) {
    val color = muscleColor(name)
    val dotSize = when (role) {
        MuscleRole.PRIMARY -> 12.dp
        MuscleRole.SECONDARY -> 10.dp
        else -> 8.dp
    }
    val textStyle = when (role) {
        MuscleRole.PRIMARY -> MaterialTheme.typography.titleSmall
        MuscleRole.SECONDARY -> MaterialTheme.typography.bodyMedium
        else -> MaterialTheme.typography.bodySmall
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier.size(dotSize),
            shape = RoundedCornerShape(50),
            color = color,
        ) {}
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            style = textStyle,
            fontWeight = if (role == MuscleRole.PRIMARY) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.1f),
        ) {
            Text(
                when (role) {
                    MuscleRole.PRIMARY -> "Principal"
                    MuscleRole.SECONDARY -> "Secundario"
                    MuscleRole.STABILIZER -> "Estabilizador"
                    MuscleRole.NEUTRALIZER -> "Neutralizador"
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
