package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
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
        topBar = {}, // Cabecera fija eliminada para máximo espacio de scroll
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ─── Scrollable Header (Retroceso + Título) ───────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Patrones de Movimiento",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                }
            }

            // ─── Header Description ───────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Índice de patrones biomecánicos y cadenas cinéticas del cuerpo humano. Selecciona un artículo para revisar su reclutamiento, demandas y errores técnicos comunes.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            // ─── Section Divider ──────────────────────────────────────────
            item {
                WikiSectionHeader("ARTÍCULOS DISPONIBLES")
            }

            // ─── List of Patterns ─────────────────────────────────────────
            items(patterns, key = { it.id }) { pattern ->
                val forceTypes = WikiLabRepository.parseStringList(pattern.forceTypes)
                val chainTypes = WikiLabRepository.parseStringList(pattern.chainTypes)
                val muscleCount = WikiLabRepository.parseStringList(pattern.primaryMuscles).size
                val exerciseCount = resolveWikiLabExerciseLinks(
                    WikiLabRepository.parseStringList(pattern.exampleExercises)
                ).size

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPattern(pattern.id) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = pattern.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFF29B6F6)
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = pattern.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.8f),
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Fuerzas: " + forceTypes.joinToString(", ") + " · " + "Cadenas: " + chainTypes.map {
                            when (it) {
                                "anterior" -> "Cadena Anterior"
                                "posterior" -> "Cadena Posterior"
                                "full" -> "Cuerpo Completo"
                                else -> it
                            }
                        }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.6f),
                    )

                    Text(
                        text = "Motores principales: $muscleCount · Ejercicios de ejemplo: $exerciseCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── WIKIPEDIA UI COMPONENTS ──────────────────────────────────────────────

@Composable
private fun WikiSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
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
