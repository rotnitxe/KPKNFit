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
import com.example.kpkn.data.db.MuscleGroupEntity
import com.example.kpkn.data.repository.WikiLabRepository

// ─── BODY PART DEFINITIONS ───────────────────────────────────────────────

private data class BodyPartDef(
    val key: String,
    val label: String,
    val color: Color,
)

private val BODY_PARTS = listOf(
    BodyPartDef("upper", "Tren superior", Color(0xFF1E88E5)),
    BodyPartDef("lower", "Tren inferior", Color(0xFF43A047)),
    BodyPartDef("core", "Core", Color(0xFFFF8F00)),
    BodyPartDef("spine", "Columna", Color(0xFF9C27B0)),
)

// ─── MAIN SCREEN ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MuscleCategoryScreen(
    onNavigateToMuscle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val muscles by WikiLabRepository.muscles.collectAsState()
    var query by remember { mutableStateOf("") }

    val canonicalMuscles = remember(muscles) {
        muscles
            .mapNotNull { m ->
                val canonicalId = canonicalWikiLabMuscleIdFromEntityId(m.id) ?: return@mapNotNull null
                WikiLabRepository.getMuscleById(canonicalId)
            }
            .distinctBy { it.id }
    }

    val filtered = remember(query, canonicalMuscles) {
        if (query.isBlank()) canonicalMuscles
        else canonicalMuscles.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

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
                        text = "Atlas Anatómico",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                }
            }

            // ─── Description & Search ─────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Índice de grupos musculares con descripciones anatómicas y referencias de volúmenes de entrenamiento.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar artículo...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Color.White,
                        ),
                    )
                }
            }

            // ─── Summary Table ────────────────────────────────────────────
            if (query.isBlank()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141414))
                            .padding(12.dp)
                    ) {
                        Text(
                            "Estructura Anatomía",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFF2C2C2C))
                        BODY_PARTS.forEach { bp ->
                            val count = canonicalMuscles.count { it.bodyPart == bp.key }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(Modifier.size(6.dp), CircleShape, bp.color) {}
                                    Spacer(Modifier.width(8.dp))
                                    Text(bp.label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), color = Color.White)
                                }
                                Text("$count artículos", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = bp.color)
                            }
                        }
                    }
                }
            }

            // ─── Muscles Grouped by Body Part ────────────────────────────
            BODY_PARTS.forEach { bp ->
                val musclesInPart = filtered.filter { it.bodyPart == bp.key }
                if (musclesInPart.isNotEmpty()) {
                    item {
                        WikiSectionHeader(bp.label.uppercase())
                    }

                    items(musclesInPart, key = { it.id }) { muscle ->
                        MuscleAtlasCard(
                            muscle = muscle,
                            color = bp.color,
                            onClick = { onNavigateToMuscle(muscle.id) },
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

@Composable
private fun MuscleAtlasCard(
    muscle: MuscleGroupEntity,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = muscle.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                color = Color(0xFF29B6F6)
            ),
            fontWeight = FontWeight.Bold,
        )
        
        Spacer(Modifier.height(4.dp))
        
        Text(
            text = muscle.description,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.8f),
        )

        if (muscle.mev != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Volumen de Mantenimiento (MEV): ${muscle.mev} series semanales",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                color = color
            )
        }
        
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)
    }
}
