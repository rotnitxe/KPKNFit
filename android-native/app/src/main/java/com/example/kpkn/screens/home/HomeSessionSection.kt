package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.example.kpkn.screens.sessioneditor.components.SessionBackgroundLayer
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
    onCreateProgram: () -> Unit = {},
    modifier: Modifier = Modifier,
    voiceArmForNextSession: Boolean = false,
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
    onVoiceArmForNextSessionChange: (Boolean) -> Unit = {},
    onVoiceCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit = {},
) {
    var activeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessions) {
        val preferred = sessions.indexOfFirst { it.isToday && !it.isCompleted }
            .takeIf { it >= 0 }
            ?: sessions.indexOfFirst { it.isToday }.takeIf { it >= 0 }
            ?: sessions.indexOfFirst { it.isOngoing }.takeIf { it >= 0 }
            ?: 0
        activeIndex = preferred.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
    }

    Column(modifier.fillMaxWidth()) {
        val current = sessions.getOrNull(activeIndex) ?: sessions.firstOrNull()
        val isCurrentToday = current?.isToday == true
        val headerTitle = when {
            !hasActiveProgram -> "Sesión de hoy"
            sessions.isEmpty() || sessions.none { it.isToday } -> "Próxima sesión"
            isCurrentToday -> "Sesión de hoy"
            else -> "Próxima sesión"
        }

        SectionHeader(headerTitle, Modifier.padding(horizontal = 24.dp))

        if (!hasActiveProgram) {
            NoProgramSessionCard(
                modifier = Modifier.padding(horizontal = 24.dp),
                onCreateProgram = onCreateProgram,
            )
        } else if (sessions.isEmpty()) {
            RestDayCard(modifier = Modifier.padding(horizontal = 24.dp))
        } else {
            val currentItem = sessions.getOrElse(activeIndex) { sessions.first() }

            if (sessions.size > 1) {
                val pagerState = rememberPagerState(initialPage = activeIndex) { sessions.size }
                LaunchedEffect(activeIndex) {
                    if (pagerState.currentPage != activeIndex) pagerState.animateScrollToPage(activeIndex)
                }
                LaunchedEffect(pagerState.currentPage) { activeIndex = pagerState.currentPage }
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val pageItem = sessions[page]
                    SessionCard(
                        item = pageItem,
                        currentDayOfWeek = currentDayOfWeek,
                        perMuscle = perMuscle,
                        onStart = { onStartWorkout(pageItem.session, pageItem.program) },
                        onResume = onResumeWorkout,
                        onEdit = { onEditSession(pageItem.session, pageItem.program) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                        voiceArmForNextSession = voiceArmForNextSession,
                        voiceCaptureMode = voiceCaptureMode,
                        onVoiceArmForNextSessionChange = onVoiceArmForNextSessionChange,
                        onVoiceCaptureModeChange = onVoiceCaptureModeChange,
                    )
                }
            } else {
                SessionCard(
                    item = currentItem,
                    currentDayOfWeek = currentDayOfWeek,
                    perMuscle = perMuscle,
                    onStart = { onStartWorkout(currentItem.session, currentItem.program) },
                    onResume = onResumeWorkout,
                    onEdit = { onEditSession(currentItem.session, currentItem.program) },
                    modifier = Modifier.padding(horizontal = 24.dp),
                    voiceArmForNextSession = voiceArmForNextSession,
                    voiceCaptureMode = voiceCaptureMode,
                    onVoiceArmForNextSessionChange = onVoiceArmForNextSessionChange,
                    onVoiceCaptureModeChange = onVoiceCaptureModeChange,
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
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    voiceArmForNextSession: Boolean = false,
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
    onVoiceArmForNextSessionChange: (Boolean) -> Unit = {},
    onVoiceCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit = {},
) {
    val isToday = item.isToday

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
                SessionBackgroundLayer(
                    background = item.session.background,
                    blurDp = (item.session.background?.style?.blur ?: 0f).dp,
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
                            modifier = Modifier.size(48.dp),
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
                                    item.isCompleted -> Icons.Default.Refresh
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = when {
                                    item.isCompleted -> "Repetir sesión"
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

            if (!item.isCompleted) {
                SessionVoicePrearmRow(
                    armed = voiceArmForNextSession,
                    captureMode = voiceCaptureMode,
                    onArmedChange = onVoiceArmForNextSessionChange,
                    onCaptureModeChange = onVoiceCaptureModeChange,
                )
            }

            run {
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
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (sessionMuscles.isEmpty()) {
                            Text(
                                "Esta sesión no tiene grupos musculares detectados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        } else sessionMuscles.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { muscle ->
                                    val score = perMuscle[muscle]?.recoveryScore ?: 100
                                    val accent = batteryColor(score)
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = accent.copy(alpha = 0.13f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                                            Text(
                                                muscle,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "$score%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = accent,
                                                fontWeight = FontWeight.Black,
                                            )
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
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
private fun NoProgramSessionCard(
    modifier: Modifier = Modifier,
    onCreateProgram: () -> Unit = {},
) {
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
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCreateProgram,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Crear programa", fontWeight = FontWeight.Bold)
            }
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

/** Pre-activación de voz desde la tarjeta de hoy: switch + selector de modo. */
@Composable
private fun SessionVoicePrearmRow(
    armed: Boolean,
    captureMode: com.example.kpkn.data.models.VoiceCaptureMode,
    onArmedChange: (Boolean) -> Unit,
    onCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Entrenar con Comandos de Voz",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "La voz se activará sola al entrar a esta sesión",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                )
            }
            Switch(
                checked = armed,
                onCheckedChange = onArmedChange,
            )
        }
        if (armed) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Modo",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                val musicSelected = captureMode == com.example.kpkn.data.models.VoiceCaptureMode.MUSIC
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SegmentedButton(
                        selected = !musicSelected,
                        onClick = {
                            if (musicSelected) onCaptureModeChange(com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Manos libres", fontSize = 11.sp) }
                    SegmentedButton(
                        selected = musicSelected,
                        onClick = {
                            if (!musicSelected) onCaptureModeChange(com.example.kpkn.data.models.VoiceCaptureMode.MUSIC)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Música", fontSize = 11.sp) }
                }
            }
        }
    }
}

