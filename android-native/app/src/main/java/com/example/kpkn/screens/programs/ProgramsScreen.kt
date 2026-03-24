package com.example.kpkn.screens.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.Program
import com.example.kpkn.ui.components.EmptyStateView
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.components.SwipeToDeleteCard

/**
 * ProgramsScreen — Display all programs (active + inactive).
 * Equivalent to PWA: ProgramsView.tsx
 *
 * Reactive UI:
 * - Top section: Active program with "Continue" button
 * - Bottom section: Inactive programs list with swipe-to-delete
 * - Empty state if no programs exist
 *
 * Navigation:
 * - Tap inactive program → navigate to ProgramDetail(programId)
 * - "Continue" button on active program → navigate to ProgramDetail(programId)
 * - Swipe-to-delete → deletes program from repository
 */
@Composable
fun ProgramsScreen(
    viewModel: ProgramsViewModel = viewModel(),
    onNavigateToProgram: (String) -> Unit,
) {
    // ─── Collect reactive state from ViewModel ────────────────────────────

    val activeProgram by viewModel.activeProgram.collectAsState()
    val inactivePrograms by viewModel.inactivePrograms.collectAsState()

    // ─── Render UI ────────────────────────────────────────────────────────

    Scaffold(
        topBar = { ProgramsTopBar() },
    ) { paddingValues ->
        if (activeProgram == null && inactivePrograms.isEmpty()) {
            // Empty state: no programs at all
            EmptyStateView(
                title = "Sin programas",
                subtitle = "Crea o importa un programa para comenzar",
                actionLabel = "Crear Programa",
                onAction = { /* TODO: navigate to create program */ },
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // ─── Active Program Section ────────────────────────────────
                if (activeProgram != null) {
                    item { SectionHeader("Tu Programa Activo") }
                    item {
                        ActiveProgramCard(
                            program = activeProgram!!,
                            viewModel = viewModel,
                            onNavigate = onNavigateToProgram,
                        )
                    }
                }

                // ─── Inactive Programs Section ─────────────────────────────
                if (inactivePrograms.isNotEmpty()) {
                    item { SectionHeader("Otros Programas") }
                    items(inactivePrograms) { program ->
                        InactiveProgramCard(
                            program = program,
                            viewModel = viewModel,
                            onNavigate = onNavigateToProgram,
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * Top bar for Programs screen.
 * Shows title "Mis Programas" with optional settings button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramsTopBar() {
    TopAppBar(
        title = { Text("Mis Programas", fontWeight = FontWeight.Black) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Card for the active program.
 * Shows:
 * - Program name
 * - Program description
 * - Statistics: weeks and sessions
 * - "Continue" button to navigate to detail
 * - Optional "Pause" button (future enhancement)
 */
@Composable
private fun ActiveProgramCard(
    program: Program,
    viewModel: ProgramsViewModel,
    onNavigate: (String) -> Unit,
) {
    val stats = viewModel.getProgramStats(program)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Program name
            Text(
                text = program.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            // Program description (if present)
            if (!program.description.isNullOrBlank()) {
                Text(
                    text = program.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }

            // Statistics row: weeks + sessions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatBadge(label = "Semanas", value = stats.weeks.toString())
                StatBadge(label = "Sesiones", value = stats.sessions.toString())
            }

            // Continue button
            Button(
                onClick = { onNavigate(program.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                )
                Text("CONTINUAR", fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * Card for an inactive program (in swipe-to-delete wrapper).
 * Shows:
 * - Program name
 * - Program statistics: weeks and sessions
 * - Tap to navigate to detail
 * - Swipe left to delete
 */
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
                .noRippleClickable { onNavigate(program.id) },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Program info (left side)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "${stats.weeks} semanas • ${stats.sessions} sesiones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Tap indicator (right side, optional)
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Ver detalles",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Small badge showing a statistic label + value.
 * Used in ActiveProgramCard for weeks and sessions.
 */
@Composable
private fun StatBadge(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}

/**
 * Clickable modifier that removes ripple effect.
 * Used for custom click feedback (or none).
 */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )
