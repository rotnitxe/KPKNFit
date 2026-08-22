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
                        text = "Articulaciones",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                }
            }

            // ─── Header Description & Search ──────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Índice de articulaciones biomecánicas. Selecciona un artículo para revisar su anatomía, tendones asociados y prevención de lesiones.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar articulación...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif)) },
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

            val bodyPartOrder = listOf("upper" to "Tren superior", "lower" to "Tren inferior", "spine" to "Columna")
            bodyPartOrder.forEach { (key, label) ->
                val jointsInGroup = grouped[key] ?: emptyList()
                if (jointsInGroup.isNotEmpty()) {
                    item {
                        WikiSectionHeader(label.uppercase())
                    }

                    items(jointsInGroup, key = { it.id }) { joint ->
                        val injuries = WikiLabRepository.parseInjuries(joint.commonInjuries)
                        val muscleCount = WikiLabRepository.parseStringList(joint.musclesCrossing).size

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToJoint(joint.id) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = joint.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = APRENDE_LINK_COLOR
                                ),
                                fontWeight = FontWeight.Bold,
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Text(
                                text = "Tipo: " + WikiLabRepository.getJointTypeLabel(joint.type),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.68f),
                            )
                            
                            Spacer(Modifier.height(2.dp))
                            
                            Text(
                                text = "Músculos cruzados: $muscleCount · Lesiones conocidas: ${injuries.size}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                            
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = APRENDE_DIVIDER, thickness = 1.dp)
                        }
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
