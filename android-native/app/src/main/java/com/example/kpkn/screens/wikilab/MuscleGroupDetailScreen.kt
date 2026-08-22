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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.db.MuscleGroupEntity
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
    val _muscles by WikiLabRepository.muscles.collectAsState()

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

    val relatedJointIds = WikiLabRepository.parseStringList(muscle.relatedJoints)
    val relatedTendonIds = WikiLabRepository.parseStringList(muscle.relatedTendons)
    val mechFunctions = WikiLabRepository.parseStringList(muscle.mechanicalFunctions)
    val recExerciseIds = WikiLabRepository.parseStringList(muscle.recommendedExercises)
    val recommendedExercises = remember(muscle.id, recExerciseIds) {
        val direct = resolveWikiLabExerciseLinks(recExerciseIds)
        (direct + recommendedExercisesForMuscle(muscle))
            .distinctBy { it.id }
            .take(8)
    }
    val visualGuide = remember(muscle.id) { buildMuscleGuide(muscle) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = muscle.name,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
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
                        text = muscle.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = color
                        )
                    )
                    Text(
                        text = muscle.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Infobox (Wikipedia Table) ────────────────────────────────
            item {
                WikiMuscleInfobox(muscle, bodyPart)
            }

            // ─── Insight Card (Notice) ────────────────────────────────────
            item {
                WikiLabInsightCard(
                    title = visualGuide.title,
                    accent = visualGuide.accent,
                    icon = visualGuide.icon,
                    summary = visualGuide.summary,
                    bullets = visualGuide.bullets,
                )
            }

            // ─── Anatomical Details ───────────────────────────────────────
            if (muscle.origin != null || muscle.insertion != null || mechFunctions.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Anatomía y Función Mecánica")
                        Spacer(Modifier.height(8.dp))
                        
                        muscle.origin?.let {
                            Text(
                                text = "Origen",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                            )
                        }
                        
                        muscle.origin?.let {
                            Text(
                                text = "Inserción",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = muscle.insertion ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                            )
                        }

                        if (mechFunctions.isNotEmpty()) {
                            Text(
                                text = "Funciones Mecánicas Principales",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            mechFunctions.forEach { fn ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp).padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        null,
                                        modifier = Modifier.size(12.dp),
                                        tint = color,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = fn,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Importance Section ───────────────────────────────────────
            if (muscle.importanceMovement != null || muscle.importanceHealth != null) {
                item {
                    Column {
                        WikiSectionHeader("Importancia Funcional y Clínica")
                        Spacer(Modifier.height(8.dp))
                        
                        muscle.importanceMovement?.let {
                            Text(
                                text = "En el Movimiento",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                            )
                        }

                        muscle.importanceHealth?.let {
                            Text(
                                text = "En la Salud y Prevención",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            // ─── Related Joints & Tendons ─────────────────────────────────
            if (relatedJointIds.isNotEmpty() || relatedTendonIds.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Relaciones Articulares y Tendinosas")
                        Spacer(Modifier.height(8.dp))
                        
                        if (relatedJointIds.isNotEmpty()) {
                            Text(
                                text = "Articulaciones Relacionadas",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            relatedJointIds.mapNotNull { id -> WikiLabRepository.getJointById(id) }.forEach { joint ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToJoint(joint.id) }
                                        .padding(start = 12.dp).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(Modifier.size(6.dp), RoundedCornerShape(50), APRENDE_LINK_COLOR) {}
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = joint.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = APRENDE_LINK_COLOR
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "(${WikiLabRepository.getJointTypeLabel(joint.type)})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (relatedTendonIds.isNotEmpty()) {
                            Text(
                                text = "Tendones Relacionados",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            relatedTendonIds.mapNotNull { id -> WikiLabRepository.getTendonById(id) }.forEach { tendon ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToTendon(tendon.id) }
                                        .padding(start = 12.dp).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(Modifier.size(6.dp), RoundedCornerShape(50), APRENDE_LINK_COLOR) {}
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = tendon.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = APRENDE_LINK_COLOR
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Recommended Exercises ────────────────────────────────────
            if (recommendedExercises.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Ejercicios Recomendados")
                        Spacer(Modifier.height(8.dp))
                        recommendedExercises.forEach { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToExercise(exercise.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), APRENDE_LINK_COLOR) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = APRENDE_LINK_COLOR
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

            // ─── Aesthetic Importance ─────────────────────────────────────
            muscle.aestheticImportance?.let { aesthetic ->
                item {
                    Column {
                        WikiSectionHeader("Importancia Estética")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = aesthetic,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.8f),
                            ),
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
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
        HorizontalDivider(color = APRENDE_DIVIDER, thickness = 1.dp)
    }
}

@Composable
private fun WikiMuscleInfobox(muscle: MuscleGroupEntity, bodyPart: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = APRENDE_MUTED_FILL),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Ficha Técnica Muscular",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(color = APRENDE_DIVIDER)
            
            InfoboxRow("Parte del Cuerpo", bodyPart)
            muscle.origin?.let { InfoboxRow("Origen", it) }
            muscle.insertion?.let { InfoboxRow("Inserción", it) }
            muscle.mev?.let { InfoboxRow("MEV (Mínimo Efectivo)", "$it series/sem") }
            muscle.mav?.let { InfoboxRow("MAV (Máximo Adaptativo)", "$it series/sem") }
            muscle.mrv?.let { InfoboxRow("MRV (Máximo Recuperable)", "$it series/sem") }
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
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                    fontWeight = FontWeight.Black,
                    color = color
                )
            }
        }
        Text(
            description,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Serif,
                color = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
        )
    }
}

private fun bodyPartColor(bodyPart: String?): Color = when (bodyPart) {
    "upper", "lower", "core", "spine" -> APRENDE_LINK_COLOR
    else -> Color(0xFF7F8D96)
}

internal data class EntityItem(
    val id: String,
    val name: String,
    val subtitle: String,
)
