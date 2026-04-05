package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.repository.WikiLabRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternsListScreen(
    onNavigateToPattern: (String) -> Unit,
    onBack: () -> Unit,
) {
    val patterns by WikiLabRepository.patterns.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patrones de Movimiento", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // Hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2E7D32).copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, null, tint = Color(0xFF43A047), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "${patterns.size} patrones biomecánicos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Clasificación de los movimientos fundamentales del entrenamiento",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(patterns, key = { it.id }) { pattern ->
                val forceTypes = WikiLabRepository.parseStringList(pattern.forceTypes)
                val chainTypes = WikiLabRepository.parseStringList(pattern.chainTypes)
                val muscleCount = WikiLabRepository.parseStringList(pattern.primaryMuscles).size
                val exerciseCount = resolveWikiLabExerciseLinks(
                    WikiLabRepository.parseStringList(pattern.exampleExercises)
                ).size

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onNavigateToPattern(pattern.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF43A047).copy(alpha = 0.12f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Sync, null, Modifier.size(20.dp), tint = Color(0xFF43A047))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pattern.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    pattern.description.take(80) + if (pattern.description.length > 80) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = Color(0xFF43A047).copy(alpha = 0.4f))
                        }

                        Spacer(Modifier.height(8.dp))

                        // Tags row
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            forceTypes.forEach { ft ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF43A047).copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        ft,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF43A047),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            chainTypes.forEach { ct ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E88E5).copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        when (ct) {
                                            "anterior" -> "Cadena Anterior"
                                            "posterior" -> "Cadena Posterior"
                                            "full" -> "Cuerpo Completo"
                                            else -> ct
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1E88E5),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "$muscleCount músculos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$exerciseCount ejercicios",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
