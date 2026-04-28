package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
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
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.ui.components.SectionHeader

@Composable
fun HomeSessionSection(
    sessions: List<TodaySessionItem>,
    hasActiveProgram: Boolean = true,
    currentDayOfWeek: Int,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    onStartWorkout: (Session, Program) -> Unit,
    onResumeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(0) }
    var isRestDayToday by remember { mutableStateOf(false) }

    LaunchedEffect(sessions.size) { activeIndex = 0 }

    Column(modifier.fillMaxWidth()) {
        SectionHeader("Sesión de hoy", Modifier.padding(horizontal = 24.dp))

        if (!hasActiveProgram) {
            NoProgramSessionCard(Modifier.padding(horizontal = 24.dp))
        } else if (sessions.isEmpty() || isRestDayToday) {
            RestDayCard(
                modifier = Modifier.padding(horizontal = 24.dp),
                onCancelRestDay = { isRestDayToday = false }
            )
        } else {
            val current = sessions.getOrElse(activeIndex) { sessions.first() }

            SessionCard(
                item = current,
                currentDayOfWeek = currentDayOfWeek,
                perMuscle = perMuscle,
                onStart = { onStartWorkout(current.session, current.program) },
                onResume = onResumeWorkout,
                onSetRestDay = { isRestDayToday = true },
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            if (sessions.size > 1) {
                SessionCarousel(
                    sessions = sessions,
                    activeIndex = activeIndex,
                    perMuscle = perMuscle,
                    onIndexChange = { activeIndex = it },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    item: TodaySessionItem,
    currentDayOfWeek: Int,
    perMuscle: Map<String, MuscleRecoveryStatus>,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onSetRestDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = item.dayOfWeek == currentDayOfWeek
    var musclesExpanded by remember { mutableStateOf(false) }

    val sessionMuscles = remember(item.session) {
        getSessionInvolvedMuscles(item.session)
    }

    val durationDisplay = remember(item.log, item.session) {
        getSessionDurationDisplay(item.session, item.log)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7.2f)
            ) {
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

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Text(
                        if (isToday) "Sesión de hoy" else "Próxima sesión",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }

                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            item.program.name,
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            item.session.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    durationDisplay,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    FilledIconButton(
                        onClick = if (item.isOngoing) onResume else onStart,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = if (item.isOngoing) "Reanudar" else "Iniciar",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Black,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { musclesExpanded = !musclesExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Músculos involucrados",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sessionMuscles.isEmpty()) {
                            Text(
                                "Sin datos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        } else {
                            val avgRecovery = sessionMuscles.mapNotNull { perMuscle[it]?.recoveryScore }.average()
                            Text(
                                if (avgRecovery.isNaN()) "--%" else "${avgRecovery.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (avgRecovery.isNaN()) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                } else {
                                    batteryColor(avgRecovery.toInt())
                                },
                            )
                        }
                        Icon(
                            if (musclesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (musclesExpanded) "Contraer" else "Expandir",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = musclesExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (sessionMuscles.isEmpty()) {
                        Text(
                            "No hay datos de músculos para esta sesión",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        sessionMuscles.chunked(3).forEach { muscleRow ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                muscleRow.forEach { muscle ->
                                    val status = perMuscle[muscle]
                                    val score = status?.recoveryScore ?: 100

                                    Column(
                                        Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            muscle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            "${score}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = batteryColor(score),
                                        )
                                        LinearProgressIndicator(
                                            progress = { score / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(50)),
                                            color = batteryColor(score),
                                            trackColor = Color.White.copy(alpha = 0.1f),
                                        )
                                    }
                                }
                                repeat(3 - muscleRow.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onSetRestDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        )
                    )
                ),
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "No entrenaré hoy",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SessionCarousel(
    sessions: List<TodaySessionItem>,
    activeIndex: Int,
    perMuscle: Map<String, MuscleRecoveryStatus>,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem(activeIndex)
    }

    Column(modifier) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(sessions) { index, item ->
                val sessionMuscles = remember(item.session) { getSessionInvolvedMuscles(item.session) }
                val avgRecovery = sessionMuscles.mapNotNull { perMuscle[it]?.recoveryScore }.average()
                val isSelected = index == activeIndex

                Surface(
                    onClick = { onIndexChange(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.width(100.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            item.session.name.take(15),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                        if (sessionMuscles.isNotEmpty() && !avgRecovery.isNaN()) {
                            Text(
                                "${avgRecovery.toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    batteryColor(avgRecovery.toInt())
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onIndexChange(if (activeIndex > 0) activeIndex - 1 else sessions.size - 1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Anterior",
                    modifier = Modifier.size(18.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(sessions.size) { i ->
                    val isActive = i == activeIndex
                    Box(
                        Modifier
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            ),
                    )
                }
            }

            IconButton(
                onClick = { onIndexChange(if (activeIndex < sessions.size - 1) activeIndex + 1 else 0) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Siguiente",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun getSessionInvolvedMuscles(session: Session): List<String> {
    val muscles = linkedSetOf<String>()

    fun collectMuscles(exercise: Exercise) {
        val info = resolveExercise(exercise.resolvedCanonicalExerciseId())
            ?: resolveExercise(exercise.exerciseDbId ?: exercise.exerciseId)
            ?: return

        SessionMuscleFilter.relevantMusclesFor(info)
            .asSequence()
            .map { involvement -> getAugeMuscleDisplayId(involvement.muscle, involvement.emphasis) }
            .filter { it.isNotBlank() }
            .forEach(muscles::add)
    }

    session.exercises.forEach(::collectMuscles)
    session.parts.forEach { part -> part.exercises.forEach(::collectMuscles) }

    return muscles.toList()
}

private fun getSessionDurationDisplay(session: Session, log: WorkoutLog?): String {
    if (log != null) {
        return "${log.durationMinutes} min promedio"
    }
    
    val exercises = session.exercises + session.parts.flatMap { it.exercises }
    if (exercises.isEmpty()) return "Sin datos"
    
    val totalSets = exercises.sumOf { it.sets.size }
    val estimatedMinutes = (totalSets * 3.5) + 10
    
    return "~${estimatedMinutes.toInt()} min"
}

@Composable
private fun NoProgramSessionCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RestDayCard(
    modifier: Modifier = Modifier,
    onCancelRestDay: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Día de descanso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Hoy es tu día de recuperación activa",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            OutlinedButton(
                onClick = onCancelRestDay,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancelar descanso")
            }
        }
    }
}
