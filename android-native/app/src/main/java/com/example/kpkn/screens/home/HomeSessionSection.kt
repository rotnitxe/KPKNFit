package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.ui.components.SectionHeader

// ─── Today Session Section ──────────────────────────────────────────────────

@Composable
fun HomeSessionSection(
    sessions: List<TodaySessionItem>,
    hasActiveProgram: Boolean = true,
    currentDayOfWeek: Int,
    onStartWorkout: (Session, Program) -> Unit,
    onResumeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(0) }

    // Reset index when sessions change
    LaunchedEffect(sessions.size) { activeIndex = 0 }

    Column(modifier.fillMaxWidth()) {
        SectionHeader("Sesión de hoy", Modifier.padding(horizontal = 24.dp))

        if (!hasActiveProgram) {
            NoProgramSessionCard(Modifier.padding(horizontal = 24.dp))
        } else if (sessions.isEmpty()) {
            RestDayCard(Modifier.padding(horizontal = 24.dp))
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val current = sessions.getOrElse(activeIndex) { sessions.first() }

                SessionHeroCard(
                    item = current,
                    currentDayOfWeek = currentDayOfWeek,
                    onStart = { onStartWorkout(current.session, current.program) },
                    onResume = onResumeWorkout,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                // Pagination
                if (sessions.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        IconButton(
                            onClick = { activeIndex = if (activeIndex > 0) activeIndex - 1 else sessions.size - 1 },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Anterior",
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(sessions.size) { i ->
                                val isActive = i == activeIndex
                                Box(
                                    Modifier
                                        .width(if (isActive) 20.dp else 6.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        ),
                                )
                            }
                        }

                        IconButton(
                            onClick = { activeIndex = if (activeIndex < sessions.size - 1) activeIndex + 1 else 0 },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Siguiente",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Session Hero Card (16:9 with cover image) ──────────────────────────────

@Composable
private fun SessionHeroCard(
    item: TodaySessionItem,
    currentDayOfWeek: Int,
    onStart: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = item.dayOfWeek == currentDayOfWeek

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            // Background: cover image or gradient
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    ),
            )

            // Overlay
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
            )

            // Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp),
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Text(
                    if (isToday) "Sesión de hoy" else "Próxima sesión",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }

            // Content
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        item.program.name,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        item.session.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                }

                // Play button
                FilledIconButton(
                    onClick = if (item.isOngoing) onResume else onStart,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = if (item.isOngoing) "Reanudar" else "Iniciar",
                        modifier = Modifier.size(28.dp),
                        tint = Color.Black,
                    )
                }
            }
        }
    }
}

// ─── No Program Session Card ────────────────────────────────────────────────

@Composable
private fun NoProgramSessionCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Sin programa activo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Crea un programa de entrenamiento para ver tu sesión del día aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ─── Rest Day Card ──────────────────────────────────────────────────────────

@Composable
private fun RestDayCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Día de descanso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Configura un programa para hoy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
