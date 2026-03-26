package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.DiscomfortEntry
import com.example.kpkn.domain.training.WeekAdherence

@Composable
fun VolumeView(
    program: Program,
    programLogs: List<WorkoutLog>,
    totalAdherence: Int,
    weeklyAdherence: List<WeekAdherence>,
    programDiscomforts: List<DiscomfortEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Análisis de Volumen", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))

        // Adherence widget
        AdherenceWidget(totalAdherence = totalAdherence, weeklyAdherence = weeklyAdherence)
        Spacer(Modifier.height(12.dp))

        // Volume widget placeholder
        VolumeWidget(programLogs = programLogs)
        Spacer(Modifier.height(12.dp))

        // Strength widget placeholder
        StrengthWidget()
        Spacer(Modifier.height(12.dp))

        // Discomfort widget
        if (programDiscomforts.isNotEmpty()) {
            DiscomfortWidget(discomforts = programDiscomforts)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(120.dp))
    }
}

// ─── Adherence Widget ───────────────────────────────────────────────────────

@Composable
private fun AdherenceWidget(totalAdherence: Int, weeklyAdherence: List<WeekAdherence>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Adherencia", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$totalAdherence%", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))

            // Weekly bars
            if (weeklyAdherence.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weeklyAdherence.forEach { week ->
                        val barHeight = (week.pct.coerceIn(0, 100) / 100f * 56).dp
                        val barColor = when {
                            week.pct >= 80 -> Color(0xFF10B981)
                            week.pct >= 50 -> Color(0xFFFBBF24)
                            else -> Color(0xFFEF4444)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(barColor),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${weeklyAdherence.size} semanas", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Volume Widget ──────────────────────────────────────────────────────────

@Composable
private fun VolumeWidget(programLogs: List<WorkoutLog>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Volumen por Sesión", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))

            if (programLogs.isEmpty()) {
                Text("Sin datos de entrenamiento", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val totalVolume = programLogs.sumOf { it.totalVolume }
                val avgVolume = if (programLogs.isNotEmpty()) totalVolume / programLogs.size else 0.0

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("${totalVolume.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Volumen total (kg)", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("${avgVolume.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Promedio/sesión", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("${programLogs.size}", fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Sesiones", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─── Strength Widget ────────────────────────────────────────────────────────

@Composable
private fun StrengthWidget() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Fuerza Relativa", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Completar con datos de 1RM y peso corporal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Discomfort Widget ──────────────────────────────────────────────────────

@Composable
private fun DiscomfortWidget(discomforts: List<DiscomfortEntry>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Molestias Reportadas", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))

            discomforts.take(5).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width((entry.count * 12).dp.coerceAtMost(100.dp))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        entry.count >= 5 -> Color(0xFFEF4444)
                                        entry.count >= 3 -> Color(0xFFFBBF24)
                                        else -> Color(0xFF10B981)
                                    }
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${entry.count}x", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
