package com.example.kpkn.screens.programs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.programs.ProgramStats
import com.example.kpkn.screens.programs.ProgramsViewModel
import com.example.kpkn.ui.components.EmptyStateView
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.ui.components.icons.DumbbellIcon

/**
 * ProgramsScreen — List of training programs (active + inactive).
 * Equivalent to PWA: ProgramsView.tsx
 */
@Composable
fun ProgramsScreen(
    onNavigateToProgram: (String) -> Unit,
    onCreateProgram: () -> Unit,
    viewModel: ProgramsViewModel = viewModel { ProgramsViewModel() },
) {
    val programs by viewModel.programs.collectAsState()
    val activeProgram by viewModel.activeProgram.collectAsState()
    val inactivePrograms by viewModel.inactivePrograms.collectAsState()

    if (programs.isEmpty()) {
        EmptyStateView(
            title = "Comienza Hoy",
            subtitle = "Aún no tienes programas configurados",
            actionLabel = "Crear primer programa",
            onAction = onCreateProgram,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "PROGRAMAS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Gestiona tus planes de entrenamiento".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Button(
                        onClick = onCreateProgram,
                        modifier = Modifier.wrapContentWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Nuevo", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }

            if (activeProgram != null) {
                item {
                    ActiveProgramCard(
                        program = activeProgram!!,
                        viewModel = viewModel,
                        onNavigate = onNavigateToProgram,
                    )
                }
            }

            if (inactivePrograms.isNotEmpty()) {
                items(inactivePrograms) { program ->
                    InactiveProgramCard(
                        program = program,
                        viewModel = viewModel,
                        onNavigate = onNavigateToProgram,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ActiveProgramCard(
    program: Program,
    viewModel: ProgramsViewModel,
    onNavigate: (String) -> Unit,
) {
    val stats = viewModel.getProgramStats(program)

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    SwipeToDeleteCard(
        onDelete = { viewModel.deleteProgram(program.id) },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clickable { onNavigate(program.id) },
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                Color(0xFF22C55E).copy(alpha = pulseAlpha)
                            ),
                    )
                    Text(
                        text = "EJECUTANDO AHORA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF22C55E),
                        letterSpacing = 1.sp,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = program.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Continuar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = "${stats.weeks} semanas · ${stats.sessions} sesiones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InactiveProgramCard(
    program: Program,
    viewModel: ProgramsViewModel,
    onNavigate: (String) -> Unit,
) {
    val stats = viewModel.getProgramStats(program)

    SwipeToDeleteCard(
        onDelete = { viewModel.deleteProgram(program.id) },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clickable { onNavigate(program.id) },
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = program.name.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "${stats.weeks} semanas · ${stats.sessions} sesiones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Ver detalles",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
