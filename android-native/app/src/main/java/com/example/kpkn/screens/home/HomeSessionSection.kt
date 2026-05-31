package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MuscleRecoveryStatus
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
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessions.size) { activeIndex = 0 }

    Column(modifier.fillMaxWidth()) {
        val current = sessions.getOrNull(activeIndex) ?: sessions.firstOrNull()
        val isCurrentToday = remember(current) {
            current?.let { item ->
                if (com.example.kpkn.domain.training.ProgramCalendarEngine.isCalendarized(item.program)) {
                    val projection = com.example.kpkn.domain.training.ProgramCalendarEngine.project(item.program)
                    projection.scheduledDateFor(item.session, item.location.weekId) == java.time.LocalDate.now()
                } else {
                    item.dayOfWeek == currentDayOfWeek
                }
            } ?: false
        }
        val isCurrentCompleted = current?.isCompleted == true
        val headerTitle = if (isCurrentToday && !isCurrentCompleted) "Sesión de hoy" else "Próxima sesión"

        SectionHeader(headerTitle, Modifier.padding(horizontal = 24.dp))

        if (!hasActiveProgram) {
            NoProgramSessionCard(Modifier.padding(horizontal = 24.dp))
        } else if (sessions.isEmpty()) {
            RestDayCard(modifier = Modifier.padding(horizontal = 24.dp))
        } else {
            val current = sessions.getOrElse(activeIndex) { sessions.first() }

            SessionCard(
                item = current,
                currentDayOfWeek = currentDayOfWeek,
                perMuscle = perMuscle,
                onStart = { onStartWorkout(current.session, current.program) },
                onResume = onResumeWorkout,
                onEdit = { onEditSession(current.session, current.program) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            SessionCarousel(
                sessions = sessions,
                activeIndex = activeIndex,
                perMuscle = perMuscle,
                onIndexChange = { activeIndex = it },
                modifier = Modifier.padding(top = 16.dp)
            )
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
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = remember(item) {
        if (com.example.kpkn.domain.training.ProgramCalendarEngine.isCalendarized(item.program)) {
            val projection = com.example.kpkn.domain.training.ProgramCalendarEngine.project(item.program)
            projection.scheduledDateFor(item.session, item.location.weekId) == java.time.LocalDate.now()
        } else {
            item.dayOfWeek == currentDayOfWeek
        }
    }

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
                    .aspectRatio(16f / 5.5f)
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
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Text(
                        if (isToday && !item.isCompleted) "Sesión de hoy" else "Próxima sesión",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }

                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.20f),
                            ),
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar",
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                        FilledIconButton(
                            onClick = when {
                                item.isOngoing -> onResume
                                item.isCompleted -> onStart
                                else -> onStart
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = when {
                                    item.isCompleted -> Color(0xFF66BB6A)
                                    else -> Color.White
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = when {
                                    item.isCompleted -> Icons.Default.Check
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = when {
                                    item.isCompleted -> "Completado"
                                    item.isOngoing -> "Reanudar"
                                    else -> "Iniciar"
                                },
                                modifier = Modifier.size(24.dp),
                                tint = Color.Black,
                            )
                        }
                    }
                }
            }

            var musclesExpanded by remember { mutableStateOf(false) }

            if (sessionMuscles.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable { musclesExpanded = !musclesExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Músculos involucrados",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
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
                            Icon(
                                if (musclesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (musclesExpanded) "Contraer" else "Expandir",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = musclesExpanded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sessionMuscles.chunked(3).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { muscle ->
                                    val score = perMuscle[muscle]?.recoveryScore ?: 100
                                    Row(
                                        Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(batteryColor(score))
                                        )
                                        Text(
                                            muscle,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "$score%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = batteryColor(score),
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
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
        }
    }
}
