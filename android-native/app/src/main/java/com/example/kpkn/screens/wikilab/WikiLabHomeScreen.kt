package com.example.kpkn.screens.wikilab

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.searchConcepts

// ═══════════════════════════════════════════════════════════════════════
// LEARN HOME — "Wikipedia del Entrenamiento"
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiLabHomeScreen(
    onNavigateToExercises: () -> Unit,
    onNavigateToMuscleAnatomy: () -> Unit,
    onNavigateToJoints: () -> Unit,
    onNavigateToMovementPatterns: () -> Unit,
    onNavigateToBiomechanics: () -> Unit,
    onNavigateToConcepts: () -> Unit,
    onNavigateToLearn: () -> Unit = {},
    onNavigateToExercise: (String) -> Unit,
    onNavigateToMuscle: (String) -> Unit,
    onNavigateToChain: (String) -> Unit,
    onNavigateToConcept: (String) -> Unit,
    onNavigateToJoint: (String) -> Unit,
    onNavigateToPattern: (String) -> Unit,
) {
    val muscles by WikiLabRepository.muscles.collectAsState()
    val joints by WikiLabRepository.joints.collectAsState()
    val tendons by WikiLabRepository.tendons.collectAsState()
    val patterns by WikiLabRepository.patterns.collectAsState()
    val chains by WikiLabRepository.chains.collectAsState()
    val customExercises by CustomExerciseRepository.customExercises.collectAsState()
    val exerciseCatalog = remember(customExercises) {
        (EXERCISE_DATABASE + customExercises)
            .associateBy { it.id.lowercase() }
            .values
            .toList()
    }

    var searchQuery by remember { mutableStateOf("") }
    val isSearching = searchQuery.isNotBlank()

    // ─── Search results ──────────────────────────────────────────────
    val searchResults = remember(searchQuery, muscles, joints, patterns) {
        if (searchQuery.isBlank()) return@remember emptyList<SearchResult>()
        val q = searchQuery.lowercase()
        val results = mutableListOf<SearchResult>()

        // Search exercises
        exerciseCatalog.filter {
            it.name.lowercase().contains(q) ||
                it.involvedMuscles.any { m -> m.muscle.lowercase().contains(q) }
        }.take(5).mapTo(results) {
            SearchResult(it.id, it.name, "Ejercicio", SearchResultType.EXERCISE, Color(0xFFE53935))
        }

        // Search muscles
        muscles.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }.mapNotNull {
            val canonicalId = canonicalWikiLabMuscleIdFromEntityId(it.id) ?: return@mapNotNull null
            WikiLabRepository.getMuscleById(canonicalId)
        }.distinctBy { it.id }
            .take(5)
            .mapTo(results) {
                SearchResult(
                    it.id,
                    it.name,
                    "Músculo · ${WikiLabRepository.getBodyPartLabel(it.bodyPart)}",
                    SearchResultType.MUSCLE,
                    Color(0xFF9C27B0),
                )
            }

        // Search joints
        joints.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }.take(4).mapTo(results) {
            SearchResult(it.id, it.name, "Articulación · ${WikiLabRepository.getJointTypeLabel(it.type)}", SearchResultType.JOINT, Color(0xFF1E88E5))
        }

        // Search patterns
        patterns.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }.take(4).mapTo(results) {
            SearchResult(it.id, it.name, "Patrón de Movimiento", SearchResultType.PATTERN, Color(0xFF43A047))
        }

        // Search concepts
        searchConcepts(searchQuery).take(5).mapTo(results) {
            SearchResult(it.id, it.name, "Concepto · ${it.category.label}", SearchResultType.CONCEPT, it.category.color)
        }

        results
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // ═══════════════════════════════════════════════════════════════
        // HERO BANNER + SEARCH
        // ═══════════════════════════════════════════════════════════════
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D47A1),
                                Color(0xFF1565C0),
                                Color(0xFF1976D2),
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
            ) {
                Column {
                    // Logo + Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Learn",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = (-0.5).sp,
                            )
                            Text(
                                "Enciclopedia del Entrenamiento",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatBadge("${exerciseCatalog.size}", "Ejercicios")
                        StatBadge("${muscles.size}", "Músculos")
                        StatBadge("${joints.size}", "Articulaciones")
                        StatBadge("${TRAINING_CONCEPTS_DATABASE.size}", "Conceptos")
                    }

                    Spacer(Modifier.height(16.dp))

                    // Global Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Buscar ejercicio, músculo, articulación, concepto...",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.7f))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
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
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        ),
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SEARCH RESULTS (when searching)
        // ═══════════════════════════════════════════════════════════════
        if (isSearching) {
            item {
                Text(
                    "${searchResults.size} resultados para \"$searchQuery\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            items(searchResults, key = { "${it.type}-${it.id}" }) { result ->
                SearchResultCard(
                    result = result,
                    onClick = {
                        when (result.type) {
                            SearchResultType.EXERCISE -> onNavigateToExercise(result.id)
                            SearchResultType.MUSCLE -> onNavigateToMuscle(result.id)
                            SearchResultType.JOINT -> onNavigateToJoint(result.id)
                            SearchResultType.PATTERN -> onNavigateToPattern(result.id)
                            SearchResultType.CONCEPT -> onNavigateToConcept(result.id)
                        }
                    },
                )
            }

            if (searchResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Sin resultados",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // MAIN CONTENT (when NOT searching)
        // ═══════════════════════════════════════════════════════════════
        if (!isSearching) {

            // ─── Section Grid (2 columns) ────────────────────────────────
            item {
                SectionLabel("EXPLORAR", modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Ejercicios",
                            subtitle = "${exerciseCatalog.size} ejercicios",
                            icon = Icons.Default.FitnessCenter,
                            color = Color(0xFFE53935),
                            onClick = onNavigateToExercises,
                            modifier = Modifier.weight(1f),
                        )
                        WikiSectionTile(
                            title = "Atlas Anatómico",
                            subtitle = "${muscles.size} músculos",
                            icon = Icons.Default.Accessibility,
                            color = Color(0xFF9C27B0),
                            onClick = onNavigateToMuscleAnatomy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Articulaciones",
                            subtitle = "${joints.size} articulaciones",
                            icon = Icons.Default.Hub,
                            color = Color(0xFF1E88E5),
                            onClick = onNavigateToJoints,
                            modifier = Modifier.weight(1f),
                        )
                        WikiSectionTile(
                            title = "Patrones",
                            subtitle = "${patterns.size} patrones",
                            icon = Icons.Default.Sync,
                            color = Color(0xFF43A047),
                            onClick = onNavigateToMovementPatterns,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Biomecánica",
                            subtitle = "Palitos y palancas",
                            icon = Icons.Default.Architecture,
                            color = Color(0xFFFF8F00),
                            onClick = onNavigateToBiomechanics,
                            modifier = Modifier.weight(1f),
                        )
                        WikiSectionTile(
                            title = "Conceptos Clave",
                            subtitle = "${TRAINING_CONCEPTS_DATABASE.size} conceptos",
                            icon = Icons.Default.School,
                            color = Color(0xFF5C6BC0),
                            onClick = onNavigateToConcepts,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Learn",
                            subtitle = "Aprende y mejora",
                            icon = Icons.Default.School,
                            color = Color(0xFF7E57C2),
                            onClick = onNavigateToLearn,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // ─── Conceptos Destacados ────────────────────────────────────
            item {
                SectionLabel(
                    "CONCEPTOS DESTACADOS",
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }

            item {
                val featured = remember {
                    TRAINING_CONCEPTS_DATABASE.filter {
                        it.id in listOf("volumen-entrenamiento", "tension-mecanica", "rir", "sobrecarga-progresiva", "rom", "fallo-muscular")
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(featured) { concept ->
                        FeaturedConceptCard(
                            name = concept.name,
                            shortDesc = concept.shortDescription,
                            color = concept.category.color,
                            categoryLabel = concept.category.label,
                            onClick = { onNavigateToConcept(concept.id) },
                        )
                    }
                }
            }

            // ─── Cadenas Cinéticas ───────────────────────────────────────
            item {
                SectionLabel(
                    "CADENAS CINÉTICAS",
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(chains) { chain ->
                        KineticChainCard(
                            name = chain.name,
                            description = chain.description.take(90) + if (chain.description.length > 90) "..." else "",
                            muscleCount = WikiLabRepository.parseStringList(chain.muscles).size,
                            color = chainColor(chain.id),
                            onClick = { onNavigateToChain(chain.id) },
                        )
                    }
                }
            }

            // ─── Músculos Populares ──────────────────────────────────────
            item {
                SectionLabel(
                    "MÚSCULOS POPULARES",
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }

            item {
                val popular = listOf(
                    "pectoral", "espalda", "cuádriceps", "glúteos",
                    "bíceps", "tríceps", "deltoides", "isquiosurales",
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(popular) { id ->
                        val muscle = WikiLabRepository.getMuscleById(id)
                        if (muscle != null) {
                            PopularMuscleCard(
                                name = muscle.name,
                                bodyPart = WikiLabRepository.getBodyPartLabel(muscle.bodyPart),
                                color = bodyPartColorSimple(muscle.bodyPart),
                                onClick = { onNavigateToMuscle(id) },
                            )
                        }
                    }
                }
            }

            // ─── Articulaciones Principales ──────────────────────────────
            item {
                SectionLabel(
                    "ARTICULACIONES PRINCIPALES",
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }

            item {
                val mainJoints = joints.take(6)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(mainJoints) { joint ->
                        JointQuickCard(
                            name = joint.name,
                            type = WikiLabRepository.getJointTypeLabel(joint.type),
                            injuryCount = WikiLabRepository.parseInjuries(joint.commonInjuries).size,
                            onClick = { onNavigateToJoint(joint.id) },
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// COMPONENTS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun StatBadge(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WikiSectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
                }
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FeaturedConceptCard(
    name: String,
    shortDesc: String,
    color: Color,
    categoryLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.15f),
            ) {
                Text(
                    categoryLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                shortDesc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun KineticChainCard(
    name: String,
    description: String,
    muscleCount: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$muscleCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = color,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PopularMuscleCard(
    name: String,
    bodyPart: String,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(Modifier.size(12.dp), CircleShape, color) {}
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
            Text(
                bodyPart,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JointQuickCard(
    name: String,
    type: String,
    injuryCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Hub,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF1E88E5),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                type,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1E88E5),
            )
            if (injuryCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "$injuryCount lesiones documentadas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SEARCH RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════

private enum class SearchResultType { EXERCISE, MUSCLE, JOINT, PATTERN, CONCEPT }

private data class SearchResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val type: SearchResultType,
    val color: Color,
)

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = result.color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (result.type) {
                            SearchResultType.EXERCISE -> Icons.Default.FitnessCenter
                            SearchResultType.MUSCLE -> Icons.Default.Accessibility
                            SearchResultType.JOINT -> Icons.Default.Hub
                            SearchResultType.PATTERN -> Icons.Default.Sync
                            SearchResultType.CONCEPT -> Icons.Default.School
                        },
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = result.color,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = result.color,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                modifier = Modifier.size(18.dp),
                tint = result.color.copy(alpha = 0.5f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════

private fun chainColor(id: String): Color = when (id) {
    "tren-superior" -> Color(0xFF1E88E5)
    "tren-inferior" -> Color(0xFF43A047)
    "core" -> Color(0xFFFF8F00)
    "cadena-anterior" -> Color(0xFFE53935)
    "cadena-posterior" -> Color(0xFF9C27B0)
    else -> Color(0xFF757575)
}

private fun bodyPartColorSimple(bodyPart: String?): Color = when (bodyPart) {
    "upper" -> Color(0xFF1E88E5)
    "lower" -> Color(0xFF43A047)
    "core" -> Color(0xFFFF8F00)
    "spine" -> Color(0xFF9C27B0)
    else -> Color(0xFF757575)
}
