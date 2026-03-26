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
import com.example.kpkn.data.db.TendonEntity
import com.example.kpkn.data.repository.InjuryInfo
import com.example.kpkn.data.repository.WikiLabRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TendonDetailScreen(
    tendonId: String,
    onNavigateToMuscle: (String) -> Unit,
    onNavigateToJoint: (String) -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tendon = WikiLabRepository.getTendonById(tendonId)

    if (tendon == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tendón no encontrado")
        }
        return
    }

    val injuries = WikiLabRepository.parseInjuries(tendon.commonInjuries)
    val protectionIds = WikiLabRepository.parseStringList(tendon.protectiveExercises)

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
                        color = Color(0xFFFF8F00).copy(alpha = 0.12f),
                    ) {
                        Text(
                            "Tendón",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8F00),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tendon.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    tendon.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            // ─── Related Muscle ──────────────────────────────────────────
            tendon.muscleId?.let { muscleId ->
                val muscle = WikiLabRepository.getMuscleById(muscleId)
                if (muscle != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { onNavigateToMuscle(muscleId) }
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FitnessCenter, null, Modifier.size(14.dp), tint = Color(0xFF9C27B0))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "MÚSCULO RELACIONADO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (0.1f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(muscle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0))
                            Text(muscle.description.take(100) + "...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ─── Related Joint ───────────────────────────────────────────
            tendon.jointId?.let { jointId ->
                val joint = WikiLabRepository.getJointById(jointId)
                if (joint != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { onNavigateToJoint(jointId) }
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Hub, null, Modifier.size(14.dp), tint = Color(0xFF1E88E5))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "ARTICULACIÓN RELACIONADA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (0.1f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(joint.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                            Text(
                                WikiLabRepository.getJointTypeLabel(joint.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ─── Injuries ────────────────────────────────────────────────
            if (injuries.isNotEmpty()) {
                item {
                    TendonInjuriesCard(injuries, onNavigateToExercise)
                }
            }

            // ─── Protective Exercises ────────────────────────────────────
            if (protectionIds.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, Modifier.size(14.dp), tint = Color(0xFF43A047))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "EJERCICIOS PROTECTORES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.1f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        protectionIds.forEach { id ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onNavigateToExercise(id) }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(Modifier.size(8.dp), RoundedCornerShape(50), Color(0xFF43A047)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    id.replace("db_", "").replace("_", " ").replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFF43A047).copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            // ─── TTC Recovery Info ───────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFF8F00).copy(alpha = 0.08f))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, Modifier.size(16.dp), tint = Color(0xFFFF8F00))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TIEMPO DE RECUPERACIÓN TENDINOSA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (0.1f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los tendones requieren de 6 a 12 semanas para adaptarse a cargas nuevas. La recuperación completa de una tendinopatía puede tomar de 3 a 6 meses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TendonInjuriesCard(
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
                injury.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp) }

                injury.contraindications?.let { contra ->
                    if (contra.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Contraindicaciones:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                        contra.forEach { c -> Text("• $c", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }

                injury.returnProgressions?.let { progs ->
                    if (progs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Progresión de retorno:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        progs.forEachIndexed { j, p -> Text("${j + 1}. $p", style = MaterialTheme.typography.labelSmall, color = Color(0xFF43A047)) }
                    }
                }
            }
            if (i < injuries.size - 1) HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
    }
}
