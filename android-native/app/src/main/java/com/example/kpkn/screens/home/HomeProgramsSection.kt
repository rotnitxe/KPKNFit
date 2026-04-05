package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.components.icons.CaupolicanIcon

// ─── Programs Section ───────────────────────────────────────────────────────
// Parity with PWA "Tus Programas" horizontal scroll.

@Composable
fun HomeProgramsSection(
    programs: List<Program>,
    activeProgramId: String?,
    onProgramClick: (String) -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Tus Programas", Modifier.padding(horizontal = 24.dp))

        if (programs.isEmpty()) {
            EmptyProgramsCard(
                modifier = Modifier.padding(horizontal = 24.dp),
                onClick = onCreateProgram,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(programs, key = { it.id }) { program ->
                    ProgramCard(
                        program = program,
                        isActive = program.id == activeProgramId,
                        onClick = { onProgramClick(program.id) },
                    )
                }
            }
        }
    }
}

// ─── Program Card ───────────────────────────────────────────────────────────

@Composable
private fun ProgramCard(program: Program, isActive: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.width(176.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                ProgramCardBackground(program.coverImage)

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (isActive) {
                            AssistChip(
                                onClick = onClick,
                                label = { Text("Activo") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.18f),
                                    labelColor = Color.White,
                                ),
                            )
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        Text(
                            when (program.mode) {
                                com.example.kpkn.data.models.ProgramMode.POWERLIFTING -> "Powerlifting"
                                com.example.kpkn.data.models.ProgramMode.POWERBUILDING -> "Powerbuilding"
                                com.example.kpkn.data.models.ProgramMode.HYPERTROPHY -> "Hipertrofia"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            program.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${program.totalProgramWeeks.coerceAtLeast(1)} semanas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.74f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCardBackground(coverValue: String?) {
    when {
        coverValue.isNullOrBlank() || coverValue.startsWith("gradient://") -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(programCardGradientColors(coverValue))),
            ) {
                CaupolicanIcon(
                    tint = Color.White.copy(alpha = 0.08f),
                    size = 48.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        else -> {
            AsyncImage(
                model = coverValue,
                contentDescription = "Portada del programa",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun programCardGradientColors(coverValue: String?): List<Color> = when (coverValue) {
    "gradient://lagoon" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))
    "gradient://velvet" -> listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))
    "gradient://forest" -> listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))
    else -> listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))
}

// ─── Empty State ────────────────────────────────────────────────────────────

@Composable
private fun EmptyProgramsCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Crea tu primer programa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "La Home se llenará con tus sesiones y métricas cuando lo actives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
