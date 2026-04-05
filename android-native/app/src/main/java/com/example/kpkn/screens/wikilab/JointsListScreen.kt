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
fun JointsListScreen(
    onNavigateToJoint: (String) -> Unit,
    onBack: () -> Unit,
) {
    val joints by WikiLabRepository.joints.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, joints) {
        if (query.isBlank()) joints
        else joints.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    val grouped = remember(filtered) {
        filtered.groupBy { it.bodyPart ?: "other" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Articulaciones", fontWeight = FontWeight.Black) },
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
                                listOf(Color(0xFF0D47A1).copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Hub, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${joints.size} articulaciones documentadas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar articulación...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            val bodyPartOrder = listOf("upper" to "Tren Superior", "lower" to "Tren Inferior", "spine" to "Columna")
            val bodyPartColors = mapOf("upper" to Color(0xFF1E88E5), "lower" to Color(0xFF43A047), "spine" to Color(0xFF9C27B0))

            bodyPartOrder.forEach { (key, label) ->
                val jointsInGroup = grouped[key] ?: emptyList()
                if (jointsInGroup.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Surface(Modifier.size(8.dp), CircleShape, bodyPartColors[key] ?: Color.Gray) {}
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = bodyPartColors[key] ?: Color.Gray,
                            )
                        }
                    }

                    items(jointsInGroup, key = { it.id }) { joint ->
                        val color = bodyPartColors[joint.bodyPart] ?: Color(0xFF757575)
                        val injuries = WikiLabRepository.parseInjuries(joint.commonInjuries)
                        val muscleCount = WikiLabRepository.parseStringList(joint.musclesCrossing).size

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { onNavigateToJoint(joint.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = color.copy(alpha = 0.12f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Hub, null, Modifier.size(20.dp), tint = color)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        joint.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        WikiLabRepository.getJointTypeLabel(joint.type),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (muscleCount > 0) {
                                            Text(
                                                "$muscleCount músculos",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (injuries.isNotEmpty()) {
                                            Text(
                                                "${injuries.size} lesiones",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFE53935),
                                            )
                                        }
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = color.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}
