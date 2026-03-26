package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.db.MuscleGroupEntity
import com.example.kpkn.data.repository.WikiLabRepository

// ─── BODY PART CARD ───────────────────────────────────────────────────────

private data class BodyPartCard(
    val key: String,
    val label: String,
    val icon: String,
    val color: Color,
)

private val BODY_PART_CARDS = listOf(
    BodyPartCard("upper", "Tren Superior", "💪", Color(0xFF1E88E5)),
    BodyPartCard("lower", "Tren Inferior", "🦵", Color(0xFF43A047)),
    BodyPartCard("core", "Core", "🎯", Color(0xFFFF8F00)),
    BodyPartCard("spine", "Columna", "🧬", Color(0xFF9C27B0)),
)

// ─── MAIN SCREEN ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleCategoryScreen(
    onNavigateToMuscle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val muscles by WikiLabRepository.muscles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Atlas Anatómico",
                        fontWeight = FontWeight.Black,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Body Part Overview Cards ────────────────────────────────
            item {
                Text(
                    "PARTES DEL CUERPO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (0.1f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(BODY_PART_CARDS) { card ->
                val musclesInPart = muscles.filter { it.bodyPart == card.key }
                BodyPartCard(
                    card = card,
                    muscleCount = musclesInPart.size,
                    onClick = { /* Scroll to muscles section */ },
                )
            }

            // ─── All Muscles Grouped by Body Part ────────────────────────
            BODY_PART_CARDS.forEach { card ->
                val musclesInPart = muscles.filter { it.bodyPart == card.key }
                if (musclesInPart.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            card.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (0.1f).sp,
                            color = card.color,
                        )
                    }

                    items(musclesInPart, key = { it.id }) { muscle ->
                        MuscleCard(
                            muscle = muscle,
                            color = card.color,
                            onClick = { onNavigateToMuscle(muscle.id) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── BODY PART CARD ───────────────────────────────────────────────────────

@Composable
private fun BodyPartCard(
    card: BodyPartCard,
    muscleCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = card.color.copy(alpha = 0.08f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(card.icon, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = card.color,
                )
                Text(
                    "$muscleCount músculos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = card.color.copy(alpha = 0.5f),
            )
        }
    }
}

// ─── MUSCLE CARD ──────────────────────────────────────────────────────────

@Composable
private fun MuscleCard(
    muscle: MuscleGroupEntity,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color indicator
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = RoundedCornerShape(50),
                        color = color,
                    ) {}
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    muscle.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    muscle.description.take(60) + if (muscle.description.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            // Volume badges
            if (muscle.mev != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "MEV ${muscle.mev}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        "MRV ${muscle.mrv ?: "?"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
