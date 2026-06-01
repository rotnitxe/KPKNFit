package com.example.kpkn.screens.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.learn.LearnCategory
import com.example.kpkn.data.learn.LearnModule
import com.example.kpkn.data.repository.LearnRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LearnHomeScreen(
    onOpenCourse: (String) -> Unit,
    onBack: () -> Unit,
) {
    val modules = remember { LearnRepository.getModules() }
    val progress by LearnRepository.progress.collectAsState()
    val badges by LearnRepository.badges.collectAsState()
    val completedCount = LearnRepository.completedCoursesCount()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Cursos",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header con resumen
            item {
                LearnHeaderCard(
                    completedCount = completedCount,
                    totalCourses = modules.size,
                    badgesCount = badges.size,
                )
            }

            // Badges destacados si hay
            if (badges.isNotEmpty()) {
                item {
                    Text(
                        "Tus insignias",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White
                        ),
                        fontWeight = FontWeight.Black,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        badges.take(4).forEach { badge ->
                            val tierColor = when (badge.tier) {
                                com.example.kpkn.data.learn.BadgeTier.ORO -> Color(0xFFFFD700)
                                com.example.kpkn.data.learn.BadgeTier.PLATA -> Color(0xFFC0C0C0)
                                com.example.kpkn.data.learn.BadgeTier.BRONCE -> Color(0xFFCD7F32)
                                null -> Color.Gray
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF121212),
                                border = BorderStroke(1.dp, tierColor.copy(alpha = 0.25f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = tierColor, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        badge.courseName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = Color.White.copy(alpha = 0.9f)
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cursos por categoría
            val categories = LearnCategory.entries.toList()
            categories.forEach { category ->
                val categoryModules = modules.filter { it.category == category }
                if (categoryModules.isEmpty()) return@forEach

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier.width(4.dp).height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(category.color.copy(alpha = 0.8f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            category.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White
                            ),
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                items(categoryModules) { module ->
                    val moduleProgress = progress[module.id]
                    val isCompleted = moduleProgress?.isCompleted == true
                    CourseCard(
                        module = module,
                        progress = moduleProgress?.let {
                            it.totalSubmodulesCompleted.toFloat() / module.submodules.size.coerceAtLeast(1)
                        } ?: 0f,
                        isCompleted = isCompleted,
                        onClick = { onOpenCourse(module.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun LearnHeaderCard(
    completedCount: Int,
    totalCourses: Int,
    badgesCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212),
        ),
        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Formación",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    Text(
                        "Cursos serios y compactos sobre entrenamiento y nutrición.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem("$completedCount/$totalCourses", "Completados")
                StatItem("$badgesCount", "Insignias")
                val overallProgress = if (totalCourses > 0) completedCount * 100 / totalCourses else 0
                StatItem("$overallProgress%", "Progreso")
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.40f)
        )
    }
}

@Composable
private fun CourseCard(
    module: LearnModule,
    progress: Float,
    isCompleted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212),
        ),
        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icono del curso
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(module.icon, style = MaterialTheme.typography.headlineSmall)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        module.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCompleted) {
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    module.shortDescription,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.55f)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${module.estimatedMinutes} min · ${module.submodules.size} módulos",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                    if (module.isSpecial) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE53935).copy(alpha = 0.12f),
                        ) {
                            Text(
                                "RECOMENDADO",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = Color(0xFFE53935),
                            )
                        }
                    }
                }
                if (progress > 0f && !isCompleted) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = module.category.color,
                        trackColor = module.category.color.copy(alpha = 0.12f),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
