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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Articulacion no encontrada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val typeLabel = WikiLabRepository.getJointTypeLabel(joint.type)
    val muscleIds = remember(joint.musclesCrossing) {
        WikiLabRepository.parseStringList(joint.musclesCrossing)
            .mapNotNull { canonicalWikiLabMuscleIdFromEntityId(it) }
            .distinct()
    }
    val tendonIds = WikiLabRepository.parseStringList(joint.tendonsRelated)
    val patternIds = WikiLabRepository.parseStringList(joint.movementPatterns)
    val injuries = WikiLabRepository.parseInjuries(joint.commonInjuries)
    val protectionIds = WikiLabRepository.parseStringList(joint.protectiveExercises)
    val protectiveExercises = remember(protectionIds) {
        resolveWikiLabExerciseLinks(protectionIds, subtitle = "Proteccion")
    }
    val jointGuide = remember(joint.id) { buildJointGuide(joint) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            joint.name,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = Color.White,
                        )
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1E88E5).copy(alpha = 0.12f)) {
                            Text(
                                typeLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E88E5),
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
                    joint.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color = Color.White.copy(alpha = 0.9f),
                    ),
                    lineHeight = 22.sp,
                )
            }

            item {
                WikiLabInsightCard(
                    title = jointGuide.title,
                    accent = jointGuide.accent,
                    icon = jointGuide.icon,
                    summary = jointGuide.summary,
                    bullets = jointGuide.bullets,
                )
            }

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

            if (muscleIds.isNotEmpty()) {
                item {
                    JointEntitiesCard(
                        title = "MUSCULOS QUE LA CRUZAN",
                        color = Color(0xFF9C27B0),
                        icon = Icons.Default.FitnessCenter,
                        entities = muscleIds.mapNotNull { id ->
                            WikiLabRepository.getMuscleById(id)
                        }.map { Triple(it.id, it.name, it.bodyPart ?: "") },
                        onClick = onNavigateToMuscle,
                    )
                }
            }

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

            if (injuries.isNotEmpty()) {
                item { InjuriesCard(injuries) }
            }

            if (protectiveExercises.isNotEmpty()) {
                item {
                    JointEntitiesCard(
                        title = "EJERCICIOS PROTECTORES",
                        color = Color(0xFF43A047),
                        icon = Icons.Default.Shield,
                        entities = protectiveExercises.map { exercise ->
                            Triple(exercise.id, exercise.name, exercise.subtitle)
                        },
                        onClick = onNavigateToExercise,
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

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
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = Color.White.copy(alpha = 0.5f),
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
                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
                    }
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = color.copy(alpha = 0.4f))
            }
        }
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.dp, Color(0xFF1E1E1E)), RoundedCornerShape(12.dp)),
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
                    drawArc(
                        color = accentColor.copy(alpha = 0.5f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - 60f, center.y - 60f),
                        size = Size(120f, 120f),
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
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
                    drawArc(
                        color = accentColor.copy(alpha = 0.5f),
                        startAngle = 50f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(center.x - 40f, center.y - 40f),
                        size = Size(80f, 80f),
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                    )
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

@Composable
private fun InjuriesCard(injuries: List<InjuryInfo>) {
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
                injury.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f), lineHeight = 18.sp)
                }
                injury.contraindications?.let { contra ->
                    if (contra.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Contraindicaciones:", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                        contra.forEach { c ->
                            Text("- $c", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
                injury.returnProgressions?.let { progs ->
                    if (progs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Progresion de retorno:", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        progs.forEachIndexed { j, p ->
                            Text("${j + 1}. $p", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color(0xFF43A047))
                        }
                    }
                }
            }
            if (i < injuries.size - 1) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF1E1E1E))
        }
    }
}
