package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
                    "Musculo no encontrado",
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
        if (direct.isNotEmpty()) direct else recommendedExercisesForMuscle(muscle)
    }
    val visualGuide = remember(muscle.id) { buildMuscleGuide(muscle) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = muscle.name,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = color,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(6.dp), RoundedCornerShape(50), color) {}
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = bodyPart,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                ),
                                fontWeight = FontWeight.Bold,
                                color = color,
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
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    muscle.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color = Color.White.copy(alpha = 0.9f)
                    ),
                    lineHeight = 22.sp,
                )
            }

            item {
                WikiLabInsightCard(
                    title = visualGuide.title,
                    accent = visualGuide.accent,
                    icon = visualGuide.icon,
                    summary = visualGuide.summary,
                    bullets = visualGuide.bullets,
                )
            }

            if (muscle.origin != null || muscle.insertion != null || mechFunctions.isNotEmpty()) {
                item { AnatomicalDetailsCard(muscle, mechFunctions) }
            }

            item { ImportanceCard(muscle) }

            if (muscle.mev != null || muscle.mav != null || muscle.mrv != null) {
                item { VolumeCard(muscle) }
            }

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

            if (recommendedExercises.isNotEmpty()) {
                item {
                    RelatedEntitiesCard(
                        title = "EJERCICIOS RECOMENDADOS",
                        icon = Icons.Default.FitnessCenter,
                        color = Color(0xFF43A047),
                        entities = recommendedExercises.map { exercise ->
                            EntityItem(exercise.id, exercise.name, exercise.subtitle)
                        },
                        onEntityClick = onNavigateToExercise,
                    )
                }
            }

            muscle.aestheticImportance?.let { aesthetic ->
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
                            Icon(
                                Icons.Default.Star,
                                null,
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "IMPORTANCIA ESTÉTICA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = Color.White.copy(alpha = 0.5f),
                                ),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.1f).sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            aesthetic,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.7f),
                            ),
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MuscleSchematicVisual(color: Color) {
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val accentSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(160.dp, 80.dp)) {
            val center = Offset(size.width / 2, size.height / 2)

            drawLine(
                color = secondaryColor,
                start = Offset(20f, center.y),
                end = Offset(140f, center.y),
                strokeWidth = 14f,
                cap = StrokeCap.Round,
            )

            drawLine(
                color = accentSurfaceColor,
                start = Offset(40f, center.y),
                end = Offset(60f, center.y - 15f),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accentSurfaceColor,
                start = Offset(120f, center.y),
                end = Offset(100f, center.y - 15f),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )

            val path = Path().apply {
                moveTo(60f, center.y - 15f)
                quadraticBezierTo(80f, center.y - 40f, 100f, center.y - 15f)
                quadraticBezierTo(80f, center.y + 10f, 60f, center.y - 15f)
            }

            drawPath(path = path, color = color.copy(alpha = 0.4f))
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 6f, join = StrokeJoin.Round),
            )

            drawLine(
                color = color,
                start = Offset(70f, center.y - 15f),
                end = Offset(85f, center.y - 15f),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(90f, center.y - 15f),
                end = Offset(75f, center.y - 15f),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun AnatomicalDetailsCard(
    muscle: MuscleGroupEntity,
    mechFunctions: List<String>,
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
            Icon(Icons.Default.Biotech, null, Modifier.size(15.dp), tint = Color(0xFF9C27B0))
            Spacer(Modifier.width(7.dp))
            Text(
                "ANATOMÍA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.5f)
                ),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            )
        }

        muscle.origin?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Origen",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.7f)
                )
            )
        }

        muscle.insertion?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Inserción",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.7f)
                )
            )
        }

        if (mechFunctions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Funciones Mecánicas",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                ),
                fontWeight = FontWeight.Bold
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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportanceCard(muscle: MuscleGroupEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TrendingUp, null, Modifier.size(15.dp), tint = Color(0xFF1E88E5))
            Spacer(Modifier.width(7.dp))
            Text(
                "IMPORTANCIA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.5f)
                ),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            )
        }

        muscle.importanceMovement?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "En el Movimiento",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.7f)
                ),
                lineHeight = 18.sp,
            )
        }

        muscle.importanceHealth?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "En la Salud",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.7f)
                ),
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun VolumeCard(muscle: MuscleGroupEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BarChart, null, Modifier.size(15.dp), tint = Color(0xFF43A047))
            Spacer(Modifier.width(7.dp))
            Text(
                "VOLUMEN DE ENTRENAMIENTO (series/semana)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.5f)
                ),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Rangos de referencia orientativos - tu volumen real se personaliza con KPKN",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Serif,
                color = Color.White.copy(alpha = 0.4f)
            ),
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            muscle.mev?.let { VolumeBadge("Inicio", it, "Series de arranque", Color(0xFF43A047)) }
            muscle.mav?.let { VolumeBadge("Óptimo", it, "Rango productivo", Color(0xFFFF8F00)) }
            muscle.mrv?.let { VolumeBadge("Techo", it, "Límite de recuperación", Color(0xFFE53935)) }
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif
                    ),
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.5f)
                ),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
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
                Surface(modifier = Modifier.size(8.dp), shape = RoundedCornerShape(50), color = color) {}
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entity.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entity.subtitle.isNotEmpty()) {
                        Text(
                            entity.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.5f)
                            ),
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = color.copy(alpha = 0.4f))
            }
        }
    }
}

private fun bodyPartColor(bodyPart: String?): Color = when (bodyPart) {
    "upper" -> Color(0xFF1E88E5)
    "lower" -> Color(0xFF43A047)
    "core" -> Color(0xFFFF8F00)
    "spine" -> Color(0xFF9C27B0)
    else -> Color(0xFF757575)
}

internal data class EntityItem(
    val id: String,
    val name: String,
    val subtitle: String,
)
