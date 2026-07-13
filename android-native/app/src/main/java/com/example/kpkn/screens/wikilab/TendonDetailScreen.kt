package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
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
    val protectiveExercises = remember(protectionIds) {
        resolveWikiLabExerciseLinks(protectionIds)
    }
    val tendonGuide = remember(tendon.id) { buildTendonGuide(tendon) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(tendon.name, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = Color.White)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF8F00).copy(alpha = 0.12f),
                        ) {
                            Text(
                                "Tendon",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8F00),
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
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Header ──────────────────────────────────────────────────
            item {
                Column {
                    tendon.description?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.9f),
                            ),
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            item {
                WikiLabInsightCard(
                    title = tendonGuide.title,
                    accent = tendonGuide.accent,
                    icon = tendonGuide.icon,
                    summary = tendonGuide.summary,
                    bullets = tendonGuide.bullets,
                )
            }

            // ─── Related Muscle ──────────────────────────────────────────
            tendon.muscleId?.let { muscleId ->
                val canonicalMuscleId = canonicalWikiLabMuscleIdFromEntityId(muscleId) ?: muscleId
                val muscle = WikiLabRepository.getMuscleById(canonicalMuscleId)
                if (muscle != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121212))
                                .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
                                .clickable { onNavigateToMuscle(canonicalMuscleId) }
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FitnessCenter, null, Modifier.size(14.dp), tint = Color(0xFF9C27B0))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "MÚSCULO RELACIONADO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (0.1f).sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(muscle.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0))
                            Text(muscle.description.take(100) + "...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f))
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121212))
                                .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
                                .clickable { onNavigateToJoint(jointId) }
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Hub, null, Modifier.size(14.dp), tint = Color(0xFF1E88E5))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "ARTICULACIÓN RELACIONADA",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (0.1f).sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(joint.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                            Text(
                                WikiLabRepository.getJointTypeLabel(joint.type),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.7f),
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
            if (protectiveExercises.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121212))
                            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, Modifier.size(14.dp), tint = Color(0xFF43A047))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "EJERCICIOS PROTECTORES",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.1f).sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        protectiveExercises.forEach { exercise ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onNavigateToExercise(exercise.id) }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(Modifier.size(8.dp), RoundedCornerShape(50), Color(0xFF43A047)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    exercise.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = Color.White),
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121212))
                        .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, Modifier.size(16.dp), tint = Color(0xFFFF8F00))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TIEMPO DE RECUPERACIÓN TENDINOSA",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (0.1f).sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los tendones requieren de 6 a 12 semanas para adaptarse a cargas nuevas. La recuperación completa de una tendinopatía puede tomar de 3 a 6 meses.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.7f),
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = Color(0xFFE53935))
            Spacer(Modifier.width(6.dp))
            Text(
                "LESIONES COMUNES",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(8.dp))

        injuries.forEachIndexed { i, injury ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(injury.name, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                injury.description?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f), lineHeight = 18.sp) }

                injury.contraindications?.let { contra ->
                    if (contra.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Contraindicaciones:", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                        contra.forEach { c -> Text("• $c", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f)) }
                    }
                }

                injury.returnProgressions?.let { progs ->
                    if (progs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Progresión de retorno:", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        progs.forEachIndexed { j, p -> Text("${j + 1}. $p", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color(0xFF43A047)) }
                    }
                }
            }
            if (i < injuries.size - 1) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF1E1E1E))
        }
    }
}
