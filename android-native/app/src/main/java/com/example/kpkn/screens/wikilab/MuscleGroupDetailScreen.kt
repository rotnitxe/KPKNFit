package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.kpkn.data.db.JointEntity
import com.example.kpkn.data.db.MuscleGroupEntity
import com.example.kpkn.data.db.TendonEntity
import com.example.kpkn.data.repository.WikiLabRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupDetailScreen(
    muscleId: String,
    onNavigateToJoint: (String) -> Unit,
    onNavigateToTendon: (String) -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onBack: () -> Unit,
) {
    val muscle = WikiLabRepository.getMuscleById(muscleId)
    val muscles by WikiLabRepository.muscles.collectAsState()

    if (muscle == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.SearchOff,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Músculo no encontrado",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val bodyPart = WikiLabRepository.getBodyPartLabel(muscle.bodyPart)
    val color = bodyPartColor(muscle.bodyPart)

    // Related data
    val relatedJointIds = WikiLabRepository.parseStringList(muscle.relatedJoints)
    val relatedTendonIds = WikiLabRepository.parseStringList(muscle.relatedTendons)
    val mechFunctions = WikiLabRepository.parseStringList(muscle.mechanicalFunctions)
    val recExerciseIds = WikiLabRepository.parseStringList(muscle.recommendedExercises)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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
            // ─── Header ──────────────────────────────────────────────────
            item {
                Column {
                    // Category badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.12f),
                    ) {
                        Text(
                            bodyPart,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        muscle.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = color,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        muscle.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            // ─── Insertion / Origin / Functions ───────────────────────────
            if (muscle.origin != null || muscle.insertion != null || mechFunctions.isNotEmpty()) {
                item {
                    AnatomicalDetailsCard(muscle, mechFunctions)
                }
            }

            // ─── Importance ──────────────────────────────────────────────
            item {
                ImportanceCard(muscle)
            }

            // ─── Volume Recommendations ──────────────────────────────────
            if (muscle.mev != null) {
                item {
                    VolumeCard(muscle, color)
                }
            }

            // ─── Related Joints ──────────────────────────────────────────
            if (relatedJointIds.isNotEmpty()) {
                item {
                    RelatedEntitiesCard(
                        title = "ARTICULACIONES RELACIONADAS",
                        icon = Icons.Default.Hub,
                        color = Color(0xFF1E88E5),
                        entities = relatedJointIds.mapNotNull { id ->
                            WikiLabRepository.getJointById(id)
                        }.map { joint ->
                            EntityItem(joint.id, joint.name, joint.type)
                        },
                        onEntityClick = onNavigateToJoint,
                    )
                }
            }

            // ─── Related Tendons ─────────────────────────────────────────
            if (relatedTendonIds.isNotEmpty()) {
                item {
                    RelatedEntitiesCard(
                        title = "TENDONES RELACIONADOS",
                        icon = Icons.Default.Link,
                        color = Color(0xFFFF8F00),
                        entities = relatedTendonIds.mapNotNull { id ->
                            WikiLabRepository.getTendonById(id)
                        }.map { tendon ->
                            EntityItem(tendon.id, tendon.name, tendon.description ?: "")
                        },
                        onEntityClick = onNavigateToTendon,
                    )
                }
            }

            // ─── Recommended Exercises ───────────────────────────────────
            if (recExerciseIds.isNotEmpty()) {
                item {
                    RelatedEntitiesCard(
                        title = "EJERCICIOS RECOMENDADOS",
                        icon = Icons.Default.FitnessCenter,
                        color = Color(0xFF43A047),
                        entities = recExerciseIds.map { id ->
                            EntityItem(id, id.replace("_", " ").replaceFirstChar { it.uppercase() }, "")
                        },
                        onEntityClick = onNavigateToExercise,
                    )
                }
            }

            // ─── Aesthetic Importance ────────────────────────────────────
            if (muscle.aestheticImportance != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                null,
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "IMPORTANCIA ESTÉTICA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.1f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            muscle.aestheticImportance,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── ANATOMICAL DETAILS ───────────────────────────────────────────────────

@Composable
private fun AnatomicalDetailsCard(
    muscle: MuscleGroupEntity,
    mechFunctions: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "ANATOMÍA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        muscle.origin?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Origen",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        muscle.insertion?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Inserción",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (mechFunctions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Funciones Mecánicas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            mechFunctions.forEach { fn ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        fn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── IMPORTANCE ────────────────────────────────────────────────────────────

@Composable
private fun ImportanceCard(muscle: MuscleGroupEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "IMPORTANCIA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        muscle.importanceMovement?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "En el Movimiento",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }

        muscle.importanceHealth?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "En la Salud",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

// ─── VOLUME RECOMMENDATIONS ───────────────────────────────────────────────

@Composable
private fun VolumeCard(muscle: MuscleGroupEntity, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "VOLUMEN RECOMENDADO (sets/semana)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VolumeBadge("MEV", muscle.mev ?: "?", "Volumen mínimo efectivo", Color(0xFF43A047))
            VolumeBadge("MAV", muscle.mav ?: "?", "Volumen máximo adaptativo", Color(0xFFFF8F00))
            VolumeBadge("MRV", muscle.mrv ?: "?", "Volumen máximo recuperable", Color(0xFFE53935))
        }
    }
}

@Composable
private fun VolumeBadge(label: String, value: String, description: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = color,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = color,
                )
            }
        }
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
        )
    }
}

// ─── RELATED ENTITIES ─────────────────────────────────────────────────────

private data class EntityItem(val id: String, val name: String, val subtitle: String)

@Composable
private fun RelatedEntitiesCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    entities: List<EntityItem>,
    onEntityClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        entities.forEach { entity ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEntityClick(entity.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(50),
                    color = color,
                ) {}
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entity.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (entity.subtitle.isNotEmpty()) {
                        Text(
                            entity.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = color.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ─── HELPERS ───────────────────────────────────────────────────────────────

private fun bodyPartColor(bodyPart: String?): Color = when (bodyPart) {
    "upper" -> Color(0xFF1E88E5)
    "lower" -> Color(0xFF43A047)
    "core" -> Color(0xFFFF8F00)
    "spine" -> Color(0xFF9C27B0)
    else -> Color(0xFF757575)
}
