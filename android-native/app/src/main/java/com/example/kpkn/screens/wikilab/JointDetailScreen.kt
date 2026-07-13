package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text("Articulación no encontrada", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
            }
        }
        return
    }

    val typeLabel = WikiLabRepository.getJointTypeLabel(joint.type)
    val muscleIds = WikiLabRepository.parseStringList(joint.musclesCrossing)
    val tendonIds = WikiLabRepository.parseStringList(joint.tendonsRelated)
    val patternIds = WikiLabRepository.parseStringList(joint.movementPatterns)
    val injuries = WikiLabRepository.parseInjuries(joint.commonInjuries)
    val protectionIds = WikiLabRepository.parseStringList(joint.protectiveExercises)
    val protectiveExercises = remember(protectionIds) {
        resolveWikiLabExerciseLinks(protectionIds, subtitle = "Protección")
    }
    val jointGuide = remember(joint.id) { buildJointGuide(joint) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        joint.name,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
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
                        text = joint.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E88E5)
                        )
                    )
                    Text(
                        joint.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.9f),
                        ),
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Infobox (Wikipedia Table) ────────────────────────────────
            item {
                WikiJointInfobox(joint, typeLabel)
            }

            // ─── Schematic Visual ─────────────────────────────────────────
            item {
                JointSchematicVisual(joint.type)
            }

            // ─── Insight Card (Notice) ────────────────────────────────────
            item {
                WikiLabInsightCard(
                    title = jointGuide.title,
                    accent = jointGuide.accent,
                    icon = jointGuide.icon,
                    summary = jointGuide.summary,
                    bullets = jointGuide.bullets,
                )
            }

            // ─── Kinematics / Movement Patterns ───────────────────────────
            if (patternIds.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Patrones de Movimiento Asociados")
                        Spacer(Modifier.height(8.dp))
                        patternIds.mapNotNull { id -> WikiLabRepository.getPatternById(id) }.forEach { pattern ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToPattern(pattern.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF43A047)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = pattern.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF43A047)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ─── Muscles Crossing ─────────────────────────────────────────
            if (muscleIds.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Músculos que la Cruzan")
                        Spacer(Modifier.height(8.dp))
                        muscleIds.mapNotNull { id -> WikiLabRepository.getMuscleById(id) }.forEach { muscle ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToMuscle(muscle.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF9C27B0)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = muscle.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF9C27B0)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "· ${WikiLabRepository.getBodyPartLabel(muscle.bodyPart)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Related Tendons ──────────────────────────────────────────
            if (tendonIds.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Tendones Asociados")
                        Spacer(Modifier.height(8.dp))
                        tendonIds.mapNotNull { id -> WikiLabRepository.getTendonById(id) }.forEach { tendon ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToTendon(tendon.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFFFF8F00)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = tendon.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFFFF8F00)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
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
                                            Text("- $c", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f))
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
                        WikiSectionHeader("Ejercicios Protectores Recomendados")
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
private fun WikiJointInfobox(joint: JointEntity, typeLabel: String) {
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
                text = "Ficha Técnica Articular",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(color = Color(0xFF2C2C2C))
            
            InfoboxRow("Tipo de Articulación", typeLabel)
            InfoboxRow("Región", when(joint.bodyPart) {
                "upper" -> "Tren superior"
                "lower" -> "Tren inferior"
                "spine" -> "Columna"
                else -> "General"
            })
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

@Composable
private fun JointSchematicVisual(type: String) {
    val accentColor = Color(0xFF1E88E5)
    val secondaryColor = Color.White.copy(alpha = 0.2f)
    val surfaceColor = Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            when (type) {
                "ball-socket" -> {
                    drawArc(
                        color = secondaryColor,
                        startAngle = 0f,
                        sweepAngle = -180f,
                        useCenter = false,
                        topLeft = Offset(center.x - 40f, center.y - 20f),
                        size = Size(80f, 60f),
                        style = Stroke(width = 8f, cap = StrokeCap.Round),
                    )
                    drawCircle(color = accentColor, radius = 24f, center = Offset(center.x, center.y - 10f))
                    drawLine(
                        color = accentColor,
                        start = Offset(center.x, center.y + 14f),
                        end = Offset(center.x, center.y + 70f),
                        strokeWidth = 12f,
                        cap = StrokeCap.Round,
                    )
                }

                "hinge" -> {
                    drawLine(
                        color = secondaryColor,
                        start = Offset(center.x, center.y - 60f),
                        end = Offset(center.x, center.y),
                        strokeWidth = 16f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(center.x, center.y),
                        end = Offset(center.x + 40f, center.y + 50f),
                        strokeWidth = 16f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color = surfaceColor, radius = 8f, center = center)
                    drawCircle(color = accentColor, radius = 8f, center = center, style = Stroke(width = 4f))
                }

                else -> {
                    drawLine(
                        color = secondaryColor,
                        start = Offset(center.x - 40f, center.y),
                        end = Offset(center.x - 10f, center.y),
                        strokeWidth = 16f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(center.x + 10f, center.y),
                        end = Offset(center.x + 40f, center.y),
                        strokeWidth = 16f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color = accentColor.copy(alpha = 0.4f), radius = 16f, center = center)
                }
            }
        }
    }
}
