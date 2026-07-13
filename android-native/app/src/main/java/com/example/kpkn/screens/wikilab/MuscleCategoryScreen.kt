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
import com.example.kpkn.data.db.MuscleGroupEntity
import com.example.kpkn.data.repository.WikiLabRepository

// ═══════════════════════════════════════════════════════════════════════
// BODY PART DEFINITIONS
// ═══════════════════════════════════════════════════════════════════════

private data class BodyPartDef(
    val key: String,
    val label: String,
    val color: Color,
)

private val BODY_PARTS = listOf(
    BodyPartDef("upper", "Tren superior", Color(0xFFD3D8DF)),
    BodyPartDef("lower", "Tren inferior", Color(0xFFB8C0CC)),
    BodyPartDef("core", "Core", Color(0xFF9EA7B3)),
    BodyPartDef("spine", "Columna", Color(0xFF808A97)),
)

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Atlas Anatómico",
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
            // ─── Hero ────────────────────────────────────────────────────
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
                            "Atlas anatómico",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            "${canonicalMuscles.size} grupos musculares con funciones y referencias de volumen.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                            color = Color.White.copy(alpha = 0.7f),
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar músculo...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
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
            }

            // ─── Body Part Overview Cards ────────────────────────────────
            if (query.isBlank()) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BODY_PARTS.forEach { bp ->
                            val count = canonicalMuscles.count { it.bodyPart == bp.key }
                            Card(
                                modifier = Modifier.widthIn(min = 148.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                                border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                                        fontWeight = FontWeight.Black,
                                        color = bp.color,
                                    )
                                    Text(
                                        bp.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Surface(Modifier.size(10.dp), CircleShape, bp.color) {}
                            Spacer(Modifier.width(8.dp))
                            Text(
                                bp.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = bp.color,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${musclesInPart.size} músculos",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
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
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MUSCLE CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MuscleAtlasCard(
    muscle: MuscleGroupEntity,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color indicator
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(Modifier.size(14.dp), CircleShape, color) {}
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    muscle.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    muscle.description.take(70) + if (muscle.description.length > 70) "..." else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Volume indicator (KPKN personalizado)
            if (muscle.mev != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.1f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            muscle.mev!!,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Serif),
                            fontWeight = FontWeight.Black,
                            color = color,
                        )
                        Text(
                            "series",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
