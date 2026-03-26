package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.repository.WikiLabRepository

// ─── MAIN SCREEN ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiLabHomeScreen(
    onNavigateToExercises: () -> Unit,
    onNavigateToMuscleAnatomy: () -> Unit,
    onNavigateToJoints: () -> Unit,
    onNavigateToMovementPatterns: () -> Unit,
    onNavigateToBiomechanics: () -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onNavigateToMuscle: (String) -> Unit,
    onNavigateToChain: (String) -> Unit,
) {
    val muscles by WikiLabRepository.muscles.collectAsState()
    val joints by WikiLabRepository.joints.collectAsState()
    val patterns by WikiLabRepository.patterns.collectAsState()
    val chains by WikiLabRepository.chains.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ─── Header ──────────────────────────────────────────────────────
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "WikiLab",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    "Enciclopedia de ejercicios, anatomía y biomecánica",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ─── Quick Navigation Cards ──────────────────────────────────────
        item {
            Text(
                "SECCIONES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            WikiSectionCard(
                title = "Ejercicios",
                description = "Enciclopedia con métricas AUGE",
                icon = Icons.Default.FitnessCenter,
                color = Color(0xFFE53935),
                onClick = onNavigateToExercises,
            )
        }

        item {
            WikiSectionCard(
                title = "Atlas Anatómico",
                description = "${muscles.size} músculos con inserciones, volumen y más",
                icon = Icons.Default.Analytics,
                color = Color(0xFF9C27B0),
                onClick = onNavigateToMuscleAnatomy,
            )
        }

        item {
            WikiSectionCard(
                title = "Articulaciones",
                description = "${joints.size} articulaciones con lesiones y protectores",
                icon = Icons.Default.Hub,
                color = Color(0xFF1E88E5),
                onClick = onNavigateToJoints,
            )
        }

        item {
            WikiSectionCard(
                title = "Patrones de Movimiento",
                description = "${patterns.size} patrones biomecánicos",
                icon = Icons.Default.Sync,
                color = Color(0xFF43A047),
                onClick = onNavigateToMovementPatterns,
            )
        }

        item {
            WikiSectionCard(
                title = "Palitos Biomecánicos",
                description = "Análisis de palancas y ángulos articulares",
                icon = Icons.Default.Compare,
                color = Color(0xFFFF8F00),
                onClick = onNavigateToBiomechanics,
            )
        }

        // ─── Kinetic Chains Section ──────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "CADENAS CINÉTICAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chains) { chain ->
                    ChainCard(
                        name = chain.name,
                        description = chain.description.take(80) + if (chain.description.length > 80) "..." else "",
                        color = chainColor(chain.id),
                        onClick = { onNavigateToChain(chain.id) },
                    )
                }
            }
        }

        // ─── Quick Muscle Access ─────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "MÚSCULOS POPULARES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val popular = listOf("pectoral", "dorsal-ancho", "cuádriceps", "glúteo-mayor", "bíceps", "tríceps")
                items(popular) { id ->
                    val muscle = WikiLabRepository.getMuscleById(id)
                    if (muscle != null) {
                        val color = bodyPartColorSimple(muscle.bodyPart)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = 0.1f),
                            modifier = Modifier.clickable { onNavigateToMuscle(id) },
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    muscle.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                )
                                muscle.mev?.let { mev ->
                                    Text(
                                        "MEV $mev",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── SECTION CARD ──────────────────────────────────────────────────────────

@Composable
private fun WikiSectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = color,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = color.copy(alpha = 0.5f),
            )
        }
    }
}

// ─── CHAIN CARD ────────────────────────────────────────────────────────────

@Composable
private fun ChainCard(
    name: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }
    }
}

// ─── HELPERS ───────────────────────────────────────────────────────────────

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
