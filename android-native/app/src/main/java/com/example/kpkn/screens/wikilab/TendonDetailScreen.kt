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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tendon.name,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Title & Description ──────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tendon.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF8F00)
                        )
                    )
                    tendon.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.9f),
                            ),
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            // ─── Infobox (Wikipedia Table) ────────────────────────────────
            item {
                WikiTendonInfobox(tendon)
            }

            // ─── Insight Card (Notice) ────────────────────────────────────
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
                        Column {
                            WikiSectionHeader("Músculo Relacionado")
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToMuscle(canonicalMuscleId) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF9C27B0)) {}
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = muscle.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = Color(0xFF9C27B0)
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = muscle.description.take(100) + "...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Related Joint ───────────────────────────────────────────
            tendon.jointId?.let { jointId ->
                val joint = WikiLabRepository.getJointById(jointId)
                if (joint != null) {
                    item {
                        Column {
                            WikiSectionHeader("Articulación Relacionada")
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToJoint(jointId) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF1E88E5)) {}
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = joint.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = Color(0xFF1E88E5)
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = WikiLabRepository.getJointTypeLabel(joint.type),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Injuries & Pathology ─────────────────────────────────────
            if (injuries.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Lesiones Comunes y Patología")
                        Spacer(Modifier.height(8.dp))
                        injuries.forEach { injury ->
                            Column(Modifier.fillMaxWidth().padding(start = 12.dp).padding(vertical = 6.dp)) {
                                Text(
                                    text = injury.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                                injury.description?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.8f),
                                        lineHeight = 18.sp
                                    )
                                }
                                injury.contraindications?.let { contra ->
                                    if (contra.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("Contraindicaciones:", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                        contra.forEach { c ->
                                            Text("• $c", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                                injury.returnProgressions?.let { progs ->
                                    if (progs.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("Progresión de retorno:", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                                        progs.forEachIndexed { j, p ->
                                            Text("${j + 1}. $p", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color(0xFF43A047))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Protective Exercises ─────────────────────────────────────
            if (protectiveExercises.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Ejercicios Protectores")
                        Spacer(Modifier.height(8.dp))
                        protectiveExercises.forEach { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToExercise(exercise.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF66BB6A)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF66BB6A)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                if (exercise.subtitle.isNotEmpty()) {
                                    Text(
                                        text = "· ${exercise.subtitle}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Recovery Timeline ────────────────────────────────────────
            item {
                Column {
                    WikiSectionHeader("Tiempos de Adaptación y Recuperación")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Los tendones tienen una tasa metabólica más baja que los músculos y requieren de 6 a 12 semanas para adaptarse estructuralmente a cargas nuevas de entrenamiento. En caso de una tendinopatía o sobrecarga, la recuperación fisiológica completa suele tomar de 3 a 6 meses de fortalecimiento progresivo.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.8f),
                        ),
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── WIKIPEDIA UI COMPONENTS ──────────────────────────────────────────────

@Composable
private fun WikiSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
    }
}

@Composable
private fun WikiTendonInfobox(tendon: TendonEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Ficha Técnica Tendinosa",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(color = Color(0xFF2C2C2C))
            
            InfoboxRow("Estructura", tendon.name)
            tendon.muscleId?.let { InfoboxRow("Músculo Asociado", it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }) }
            tendon.jointId?.let { InfoboxRow("Articulación Asociada", it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }) }
        }
    }
}

@Composable
private fun InfoboxRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
