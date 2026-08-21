package com.example.kpkn.screens.programs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.kpkn.ui.components.KpknAlertDialog

/**
 * ProgramsScreen — List of training programs (active + inactive).
 * Equivalent to PWA: ProgramsView.tsx
 */
@Composable
fun ProgramsScreen(
    onNavigateToProgram: (String) -> Unit,
    onCreateProgram: () -> Unit,
    viewModel: ProgramsViewModel = viewModel(),
) {
    val programs by viewModel.programs.collectAsState()
    val archivedPrograms by viewModel.archivedPrograms.collectAsState()
    val activeProgram by viewModel.activeProgram.collectAsState()
    val inactivePrograms by viewModel.inactivePrograms.collectAsState()
    val programQueue by viewModel.programQueue.collectAsState()
    var menuProgram by remember { mutableStateOf<Program?>(null) }

    if (programs.isEmpty()) {
        EmptyStateView(
            title = "Comienza Hoy",
            subtitle = "Aún no tienes programas configurados",
            actionLabel = "Crear primer programa",
            onAction = { onCreateProgram() },
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
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
                            text = "Gestiona tus planes de entrenamiento",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { onCreateProgram() },
                        modifier = Modifier.wrapContentWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
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
                        onLongPress = { menuProgram = activeProgram },
                    )
                }
            }

            if (programQueue.isNotEmpty()) {
                item {
                    ProgramQueueSection(
                        queue = programQueue,
                        onMove = viewModel::moveQueuedProgram,
                        onRemove = viewModel::removeFromQueue,
                    )
                }
            }

            if (inactivePrograms.isNotEmpty()) {
                items(inactivePrograms) { program ->
                    InactiveProgramCard(
                        program = program,
                        viewModel = viewModel,
                        onNavigate = onNavigateToProgram,
                        onLongPress = { menuProgram = program },
                    )
                }
            }

            if (archivedPrograms.isNotEmpty()) {
                item {
                    Text(
                        "Archivados",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                    )
                }
                items(archivedPrograms) { program ->
                    ArchivedProgramCard(
                        program = program,
                        onNavigate = onNavigateToProgram,
                        onRestore = { viewModel.restoreArchivedProgram(program.id) },
                        onDeleteForever = { viewModel.permanentlyDeleteProgram(program.id) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    menuProgram?.let { program ->
        KpknAlertDialog(
            onDismissRequest = { menuProgram = null },
            title = { Text(program.name, fontWeight = FontWeight.Black) },
            text = { Text("Gestiona este programa.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addToQueue(program.id)
                        menuProgram = null
                    },
                ) { Text("Añadir a la cola") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.archiveProgram(program.id)
                        menuProgram = null
                    }) { Text("Archivar") }
                    TextButton(onClick = { menuProgram = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

@Composable
private fun ActiveProgramCard(
    program: Program,
    viewModel: ProgramsViewModel,
    onNavigate: (String) -> Unit,
    onLongPress: () -> Unit,
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
                .combinedClickable(onClick = { onNavigate(program.id) }, onLongClick = onLongPress),
            shape = MaterialTheme.shapes.extraLarge,
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Column(
                modifier = Modifier
                    .background(programCoverBrush(program.coverImage))
                    .padding(24.dp),
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
                                MaterialTheme.colorScheme.tertiary.copy(alpha = pulseAlpha)
                            ),
                    )
                    Text(
                        text = "EJECUTANDO AHORA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.tertiary,
                        letterSpacing = 1.sp,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
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
                    color = Color.White.copy(alpha = 0.78f),
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
    onLongPress: () -> Unit,
) {
    val stats = viewModel.getProgramStats(program)

    SwipeToDeleteCard(
        onDelete = { viewModel.deleteProgram(program.id) },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .combinedClickable(onClick = { onNavigate(program.id) }, onLongClick = onLongPress),
            shape = MaterialTheme.shapes.extraLarge,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(programCoverBrush(program.coverImage))
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        text = "${stats.weeks} semanas · ${stats.sessions} sesiones",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Ver detalles",
                    tint = Color.White.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun ArchivedProgramCard(
    program: Program,
    onNavigate: (String) -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable { onNavigate(program.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(program.name, fontWeight = FontWeight.Bold)
                Text("Archivado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRestore) { Text("Restaurar") }
                TextButton(onClick = onDeleteForever) { Text("Borrar") }
            }
        }
    }
}

@Composable
private fun ProgramQueueSection(
    queue: List<Program>,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("COLA DE PROGRAMAS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        queue.forEachIndexed { index, program ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                    Text(program.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onMove(program.id, -1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, null) }
                    IconButton(onClick = { onMove(program.id, 1) }, enabled = index < queue.lastIndex) { Icon(Icons.Default.ArrowDownward, null) }
                    IconButton(onClick = { onRemove(program.id) }) { Icon(Icons.Default.Close, "Eliminar de la cola") }
                }
            }
        }
    }
}

private fun programCoverBrush(coverImage: String?): Brush {
    val colors = when (coverImage) {
        "gradient://lagoon" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))
        "gradient://velvet" -> listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))
        "gradient://forest" -> listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))
        else -> listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))
    }
    return Brush.linearGradient(colors)
}
