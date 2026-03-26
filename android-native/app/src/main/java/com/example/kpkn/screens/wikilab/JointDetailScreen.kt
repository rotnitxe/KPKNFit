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
import com.example.kpkn.data.repository.InjuryInfo
import com.example.kpkn.data.repository.WikiLabRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JointDetailScreen(
    jointId: String,
    onNavigateToMuscle: (String) -> Unit,
    onNavigateToTendon: (String) -> Unit,
    onNavigateToPattern: (String) -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onBack: () -> Unit,
) {
    val joint = WikiLabRepository.getJointById(jointId)

    if (joint == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Articulación no encontrada")
        }
        return
    }

    val typeLabel = WikiLabRepository.getJointTypeLabel(joint.type)
    val muscleIds = WikiLabRepository.parseStringList(joint.musclesCrossing)
    val tendonIds = WikiLabRepository.parseStringList(joint.tendonsRelated)
    val patternIds = WikiLabRepository.parseStringList(joint.movementPatterns)
    val injuries = WikiLabRepository.parseInjuries(joint.commonInjuries)
    val protectionIds = WikiLabRepository.parseStringList(joint.protectiveExercises)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Header ──────────────────────────────────────────────────
            item {
                Column {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E88E5).copy(alpha = 0.12f),
                    ) {
                        Text(
                            typeLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E88E5),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        joint.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        joint.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            // ─── Movement Patterns ───────────────────────────────────────
            if (patternIds.isNotEmpty()) {
                item {
                    JointEntitiesCard(
                        title = "PATRONES DE MOVIMIENTO",
                        color = Color(0xFF43A047),
                        icon = Icons.Default.Sync,
                        entities = patternIds.mapNotNull { id ->
                            WikiLabRepository.getPatternById(id)
                        }.map { Triple(it.id, it.name, "") },
                        onClick = onNavigateToPattern,
                    )
                }
            }

            // ─── Muscles Crossing ────────────────────────────────────────
            if (muscleIds.isNotEmpty()) {
                item {
                    JointEntitiesCard(
                        title = "MÚSCULOS QUE LA CRUZAN",
                        color = Color(0xFF9C27B0),
                        icon = Icons.Default.FitnessCenter,
                        entities = muscleIds.mapNotNull { id ->
                            WikiLabRepository.getMuscleById(id)
                        }.map { Triple(it.id, it.name, it.bodyPart ?: "") },
                        onClick = onNavigateToMuscle,
                    )
                }
            }

            // ─── Related Tendons ─────────────────────────────────────────
            if (tendonIds.isNotEmpty()) {
                item {
                    JointEntitiesCard(
                        title = "TENDONES RELACIONADOS",
                        color = Color(0xFFFF8F00),
                        icon = Icons.Default.Link,
                        entities = tendonIds.mapNotNull { id ->
                            WikiLabRepository.getTendonById(id)
                        }.map { Triple(it.id, it.name, it.description ?: "") },
                        onClick = onNavigateToTendon,
                    )
                }
            }

            // ─── Common Injuries ─────────────────────────────────────────
            if (injuries.isNotEmpty()) {
                item {
                    InjuriesCard(injuries, onNavigateToExercise)
                }
            }

            // ─── Protective Exercises ────────────────────────────────────
            if (protectionIds.isNotEmpty()) {
                item {
                    JointEntitiesCard(
                        title = "EJERCICIOS PROTECTORES",
                        color = Color(0xFF43A047),
                        icon = Icons.Default.Shield,
                        entities = protectionIds.map { id ->
                            Triple(
                                id,
                                id.replace("db_", "").replace("_", " ").replaceFirstChar { it.uppercase() },
                                "Protección"
                            )
                        },
                        onClick = onNavigateToExercise,
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── ENTITIES CARD ────────────────────────────────────────────────────────

@Composable
private fun JointEntitiesCard(
    title: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    entities: List<Triple<String, String, String>>,
    onClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
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
        entities.forEach { (id, name, subtitle) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(id) }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(Modifier.size(8.dp), RoundedCornerShape(50), color) {}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = color.copy(alpha = 0.4f))
            }
        }
    }
}

// ─── INJURIES CARD ─────────────────────────────────────────────────────────

@Composable
private fun InjuriesCard(
    injuries: List<InjuryInfo>,
    onNavigateToExercise: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = Color(0xFFE53935))
            Spacer(Modifier.width(6.dp))
            Text(
                "LESIONES COMUNES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        injuries.forEachIndexed { i, injury ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(injury.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                injury.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                }

                // Contraindications
                injury.contraindications?.let { contra ->
                    if (contra.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Contraindicaciones:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                        contra.forEach { c ->
                            Text("• $c", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Return progressions
                injury.returnProgressions?.let { progs ->
                    if (progs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Progresión de retorno:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        progs.forEachIndexed { j, p ->
                            Text("${j + 1}. $p", style = MaterialTheme.typography.labelSmall, color = Color(0xFF43A047))
                        }
                    }
                }
            }
            if (i < injuries.size - 1) HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
    }
}
