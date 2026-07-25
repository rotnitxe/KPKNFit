package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.theme.HomeCardSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

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
                item(key = "create-program") {
                    CreateProgramChip(onClick = onCreateProgram)
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(program: Program, isActive: Boolean, onClick: () -> Unit) {
    val bgColors = programCardCoverColors(program.coverImage)
    Column(modifier = Modifier.width(176.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(bgColors)),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    "ACTIVO",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                        Text(
                            when (program.mode) {
                                com.example.kpkn.data.models.ProgramMode.POWERLIFTING -> "Powerlifting"
                                com.example.kpkn.data.models.ProgramMode.POWERBUILDING -> "Powerbuilding"
                                com.example.kpkn.data.models.ProgramMode.HYPERTROPHY -> "Hipertrofia"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.64f),
                            maxLines = 1,
                            fontSize = 9.sp,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            program.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${program.totalProgramWeeks.coerceAtLeast(1)} semanas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                }
            }
        }
    }
}

private fun programCardCoverColors(coverValue: String?): List<Color> = when (coverValue) {
    "gradient://lagoon" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))
    "gradient://velvet" -> listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))
    "gradient://forest" -> listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))
    else -> listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))
}


@Composable
private fun CreateProgramChip(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .height(112.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Nuevo programa",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyProgramsCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardSurface),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Crea tu primer programa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "La Home se llenará con tus sesiones y métricas cuando lo actives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}
