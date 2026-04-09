package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.screens.auge.AugeViewModel
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessGateScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    onReady: () -> Unit,
) {
    val augeViewModel: AugeViewModel = viewModel()
    val context = LocalContext.current
    val programRepository = remember { ProgramRepository.getInstance() }
    val augeRepository = remember(context) { AugeRepository.getInstance(context) }

    val settings by programRepository.settings.collectAsState()
    val programs by programRepository.programs.collectAsState()
    val dashboard by augeViewModel.dashboard.collectAsState()
    val readiness by augeViewModel.readiness.collectAsState()
    val perMuscle by augeViewModel.perMuscle.collectAsState()

    val todayWellbeing by produceState<DailyWellbeingLog?>(initialValue = null) {
        value = augeRepository.getTodayWellbeing()
    }

    BackHandler(enabled = true) {}

    val currentProgram = remember(programs, programId) {
        programs.firstOrNull { it.id == programId }
    }
    val sessionExercises = remember(currentProgram, sessionId) {
        currentProgram?.let { findSessionExercises(it, sessionId) }.orEmpty()
    }
    val sessionMuscleIds = remember(sessionExercises) {
        sessionExercises
            .flatMap { ex ->
                val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
                dbInfo?.involvedMuscles
                    ?.filter { it.role == MuscleRole.PRIMARY || it.role == MuscleRole.SECONDARY }
                    ?.map { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
                    .orEmpty()
            }
            .distinct()
    }
    val sessionMuscleBatteries = remember(sessionMuscleIds, perMuscle) {
        sessionMuscleIds.associateWith { muscleId ->
            perMuscle[muscleId]?.recoveryScore ?: 100
        }
    }

    val neuralAuto = dashboard.channels.firstOrNull { it.id == RecoveryChannelId.SYSTEM }?.score
        ?: readiness?.score
        ?: 75
    val spinalAuto = dashboard.channels.firstOrNull { it.id == RecoveryChannelId.STRUCTURE }?.score
        ?: 75

    val initialNeural = remember(todayWellbeing, neuralAuto) {
        (todayWellbeing?.manualNeuralBattery ?: neuralAuto).coerceIn(0, 100)
    }
    val initialSpinal = remember(todayWellbeing, spinalAuto) {
        (todayWellbeing?.manualSpinalBattery ?: spinalAuto).coerceIn(0, 100)
    }
    var neural by remember(initialNeural) { mutableIntStateOf(initialNeural) }
    var spinal by remember(initialSpinal) { mutableIntStateOf(initialSpinal) }

    val initialMuscleAdjustments = remember(sessionMuscleBatteries, todayWellbeing) {
        sessionMuscleBatteries.mapValues { (muscleId, autoValue) ->
            todayWellbeing?.manualMuscleBatteries?.get(muscleId)?.coerceIn(0, 100) ?: autoValue.coerceIn(0, 100)
        }
    }
    val muscleAdjustments = remember(initialMuscleAdjustments) {
        mutableStateMapOf<String, Int>().also { map ->
            map.putAll(initialMuscleAdjustments)
        }
    }

    val preparedWord = when (settings.userVitals.gender) {
        Gender.FEMALE -> "preparada"
        Gender.MALE -> "preparado"
        else -> "preparado(a)"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Readiness AUGE", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "¿Qué tan $preparedWord te sientes para esta sesión?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Si los valores automáticos de cada RING y/o músculos a trabajar en la sesión no te representan, puedes corregirlos para representar fielmente cómo te sientes. Completar esto nos ayuda a darte valores más fiables con el tiempo y a ajustar mejor las recomendaciones de cargas para tus ejercicios para un progreso sostenible y seguro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AdjustableReadinessRingCard(
                    modifier = Modifier.weight(1f),
                    title = "Energía general",
                    subtitle = "SNC",
                    value = neural,
                    ringColor = Color(0xFF448AFF),
                    onValueChange = { neural = it },
                )
                AdjustableReadinessRingCard(
                    modifier = Modifier.weight(1f),
                    title = "Columna",
                    subtitle = "Estructura",
                    value = spinal,
                    ringColor = Color(0xFFFFD740),
                    onValueChange = { spinal = it },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Batería muscular de la sesión",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )

            if (muscleAdjustments.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No se detectaron músculos principales para esta sesión.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    muscleAdjustments.keys.sorted().forEach { muscleId ->
                        val value = muscleAdjustments[muscleId] ?: 100
                        CompactMuscleBatteryBarRow(
                            muscleLabel = muscleId,
                            value = value,
                            onValueChange = { updated -> muscleAdjustments[muscleId] = updated },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val base = todayWellbeing
                    val log = DailyWellbeingLog(
                        id = base?.id ?: UUID.randomUUID().toString(),
                        date = LocalDate.now().toString(),
                        sleepQuality = base?.sleepQuality ?: 3,
                        stressLevel = base?.stressLevel ?: 3,
                        doms = base?.doms ?: 1,
                        motivation = base?.motivation ?: 3,
                        sleepHours = base?.sleepHours ?: 7.5,
                        moodState = base?.moodState,
                        workIntensity = base?.workIntensity,
                        studyIntensity = base?.studyIntensity,
                        manualMuscularBattery = null,
                        manualNeuralBattery = neural,
                        manualSpinalBattery = spinal,
                        manualMuscleBatteries = muscleAdjustments.toMap(),
                        notes = base?.notes,
                    )

                    augeViewModel.saveWellbeing(log)

                    WorkoutReadinessBridge.store(
                        WorkoutReadinessBridge.ReadinessAdjustments(
                            neural = neural,
                            muscular = null,
                            spinal = spinal,
                            perMuscle = muscleAdjustments.toMap(),
                        )
                    )
                    onReady()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    "Guardar y entrenar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdjustableReadinessRingCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    value: Int,
    ringColor: Color,
    onValueChange: (Int) -> Unit,
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Box(
                modifier = Modifier
                    .size(138.dp)
                    .semantics {
                        contentDescription = "$title $subtitle"
                        stateDescription = "$value por ciento"
                        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 100)
                        setProgress { target ->
                            onValueChange(target.toInt().coerceIn(0, 100))
                            true
                        }
                    }
                    .pointerInput(value) {
                        detectDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onDragEnd = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += -dragAmount.y
                                val step = (dragAccumulator / 4f).toInt()
                                if (step != 0) {
                                    onValueChange((value + step).coerceIn(0, 100))
                                    dragAccumulator -= (step * 4f)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                ReadinessRingVisual(value = value, color = ringColor)
                Text(
                    text = "$value%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = ringColor,
                )
            }

            Text(
                "Desliza arriba/abajo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadinessRingVisual(
    value: Int,
    color: Color,
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "readinessRing",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 9.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = center,
            style = Stroke(strokePx),
        )

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedValue,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(strokePx),
        )
    }
}

@Composable
private fun CompactMuscleBatteryBarRow(
    muscleLabel: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val clamped = value.coerceIn(0, 100)
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val accent = when {
        clamped >= 80 -> Color(0xFF22C55E)
        clamped >= 55 -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                muscleLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$clamped%",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .semantics {
                    contentDescription = "Batería muscular $muscleLabel"
                    stateDescription = "$clamped por ciento"
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..100f, 100)
                    setProgress { target ->
                        onValueChange(target.toInt().coerceIn(0, 100))
                        true
                    }
                }
                .pointerInput(clamped, barWidthPx) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        onValueChange((ratio * 100f).toInt().coerceIn(0, 100))
                    }
                }
                .pointerInput(clamped, barWidthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = ((dragAmount.x / barWidthPx) * 100f).toInt()
                        if (delta != 0) onValueChange((clamped + delta).coerceIn(0, 100))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(clamped / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
            )
        }
    }
}

private fun findSessionExercises(program: Program, sessionId: String): List<Exercise> {
    for (macro in program.macrocycles) {
        for (block in macro.blocks) {
            for (meso in block.mesocycles) {
                for (week in meso.weeks) {
                    val session = week.sessions.firstOrNull { it.id == sessionId }
                    if (session != null) {
                        return if (session.parts.isNotEmpty()) {
                            session.parts.flatMap { it.exercises }
                        } else {
                            session.exercises
                        }
                    }
                }
            }
        }
    }
    return emptyList()
}
