package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.repository.WikiLabRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatternsListScreen(
    onNavigateToPattern: (String) -> Unit,
    onBack: () -> Unit,
) {
    val patterns by WikiLabRepository.patterns.collectAsState()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Patrones de Movimiento",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding),
            contentPadding = PaddingValues(bottom = 164.dp),
        ) {
            // Hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B2027), Color.Black)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Patrones de movimiento",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            "${patterns.size} patrones biomecánicos del entrenamiento.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                            color = Color.White.copy(alpha = 0.7f),
                        )
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E1E1E),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Sync, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.7f))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pattern.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    pattern.description.take(80) + if (pattern.description.length > 80) "..." else "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.3f))
                        }

                        Spacer(Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            forceTypes.forEach { ft ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFF1E1E1E),
                                ) {
                                    Text(
                                        ft,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            chainTypes.forEach { ct ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFF1A1A1A),
                                ) {
                                    Text(
                                        when (ct) {
                                            "anterior" -> "Cadena Anterior"
                                            "posterior" -> "Cadena Posterior"
                                            "full" -> "Cuerpo Completo"
                                            else -> ct
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "$muscleCount músculos",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                            Text(
                                "$exerciseCount ejercicios",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
