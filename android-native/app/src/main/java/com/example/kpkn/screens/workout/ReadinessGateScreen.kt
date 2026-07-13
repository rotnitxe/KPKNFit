package com.example.kpkn.screens.workout

import androidx.compose.runtime.mutableStateListOf
import com.example.kpkn.screens.workout.components.PreWorkoutDiscomfortSelector

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.screens.workout.components.MinimalMuscleSlider
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.data.models.ringScore
import java.time.LocalDate
import java.util.UUID

@Suppress("unused")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReadinessGateScreen(
    programId: String,
    sessionId: String,
    onReady: () -> Unit,
) {
    val augeViewModel: AugeViewModel = viewModel()
    val context = LocalContext.current
    val programRepository = remember { ProgramRepository.getInstance() }
    val augeRepository = remember(context) { AugeRepository.getInstance(context) }

    val settings by programRepository.settings.collectAsState()
    val programs by programRepository.programs.collectAsState()
    val augeSnapshot by augeViewModel.snapshot.collectAsState()
    val perMuscle by augeViewModel.perMuscle.collectAsState()
    val isLoading by augeViewModel.isLoading.collectAsState()

    val todayWellbeing by produceState<DailyWellbeingLog?>(initialValue = null) {
        value = augeRepository.getTodayWellbeing()
    }

    BackHandler(enabled = true) {}

    val currentProgram = remember(programs, programId) {
        programs.firstOrNull { it.id == programId }
    }
    val sessionName = remember(currentProgram, sessionId) {
        if (currentProgram == null) return@remember "Sesión"
        for (macro in currentProgram.macrocycles) {
            for (block in macro.blocks) {
                for (meso in block.mesocycles) {
                    for (week in meso.weeks) {
                        val s = week.sessions.firstOrNull { it.id == sessionId }
                        if (s != null) return@remember s.name
                    }
                }
            }
        }
        "Sesión"
    }
    val sessionExercises = remember(currentProgram, sessionId) {
        currentProgram?.let { findSessionExercises(it, sessionId) }.orEmpty()
    }
    val sessionMuscleIds = remember(sessionExercises) {
        sessionExercises
            .flatMap { ex ->
                val dbInfo = EXERCISE_DATABASE_BY_ID[ex.resolvedCanonicalExerciseId()]
                    ?: ex.exerciseDbId?.lowercase()?.let(EXERCISE_DATABASE_BY_ID::get)
                    ?: ex.exerciseId?.lowercase()?.let(EXERCISE_DATABASE_BY_ID::get)
                dbInfo?.involvedMuscles
                    ?.filter { resolveMuscleVolumeContribution(it) > 0.0 }
                    ?.map { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
                    .orEmpty()
            }
            .let { deduplicateCanonicalMuscles(it) }
    }
    val sessionMuscleBatteries = remember(sessionMuscleIds, perMuscle) {
        sessionMuscleIds.associateWith { muscleId ->
            perMuscle[muscleId]?.recoveryScore ?: 100
        }
    }

    val neuralAuto = augeSnapshot.ringScore(RecoveryChannelId.SYSTEM)
    val muscularAuto = augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR)
    val spinalAuto = augeSnapshot.ringScore(RecoveryChannelId.STRUCTURE)

    var initialized by rememberSaveable(programId, sessionId) { mutableStateOf(false) }
    var userEditedNeural by rememberSaveable(programId, sessionId) { mutableStateOf(false) }
    var userEditedSpinal by rememberSaveable(programId, sessionId) { mutableStateOf(false) }
    val userEditedMuscles = remember(programId, sessionId) { mutableStateMapOf<String, Boolean>() }
    var neural by rememberSaveable(programId, sessionId) { mutableIntStateOf(neuralAuto.coerceIn(0, 100)) }
    var muscular by rememberSaveable(programId, sessionId) { mutableIntStateOf(muscularAuto.coerceIn(0, 100)) }
    var spinal by rememberSaveable(programId, sessionId) { mutableIntStateOf(spinalAuto.coerceIn(0, 100)) }
    val muscleAdjustments = remember(programId, sessionId) { mutableStateMapOf<String, Int>() }
    val selectedDiscomforts = remember(programId, sessionId) { mutableStateListOf<String>() }

    LaunchedEffect(isLoading, todayWellbeing, neuralAuto, muscularAuto, spinalAuto, sessionMuscleBatteries, initialized) {
        if (!isLoading && !initialized) {
            neural = neuralAuto.coerceIn(0, 100)
            muscular = muscularAuto.coerceIn(0, 100)
            spinal = spinalAuto.coerceIn(0, 100)
            muscleAdjustments.clear()
            sessionMuscleBatteries.forEach { (muscleId, autoValue) ->
                muscleAdjustments[muscleId] = autoValue.coerceIn(0, 100)
            }
            selectedDiscomforts.clear()
            selectedDiscomforts.addAll(todayWellbeing?.preWorkoutDiscomforts ?: emptyList())
            initialized = true
        } else if (initialized) {
            if (!userEditedNeural) {
                neural = neuralAuto.coerceIn(0, 100)
            }
            muscular = muscularAuto.coerceIn(0, 100)
            if (!userEditedSpinal) {
                spinal = spinalAuto.coerceIn(0, 100)
            }
            sessionMuscleBatteries.forEach { (muscleId, autoValue) ->
                if (userEditedMuscles[muscleId] != true) {
                    muscleAdjustments[muscleId] = autoValue.coerceIn(0, 100)
                }
            }
        }
    }

    // Keep session muscle list in sync without overwriting live user edits.
    LaunchedEffect(sessionMuscleBatteries.keys, isLoading) {
        if (initialized && !isLoading) {
            val valid = sessionMuscleBatteries.keys
            muscleAdjustments.keys.toList()
                .filter { it !in valid }
                .forEach { muscleAdjustments.remove(it) }
            valid.forEach { key ->
                if (!muscleAdjustments.containsKey(key)) {
                    muscleAdjustments[key] = (sessionMuscleBatteries[key] ?: 100).coerceIn(0, 100)
                }
            }
        }
    }

    val preparedWord = when (settings.userVitals.gender) {
        Gender.FEMALE -> "preparada"
        Gender.MALE -> "preparado"
        else -> "preparado(a)"
    }

    var allowSheetDismiss by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            when (target) {
                SheetValue.Hidden -> allowSheetDismiss
                SheetValue.PartiallyExpanded -> false
                SheetValue.Expanded -> true
            }
        },
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = sessionName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Vista previa de tu sesión",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val previewExercises = sessionExercises.take(6)
            if (previewExercises.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "No hay ejercicios cargados para esta sesión.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                previewExercises.forEachIndexed { index, exercise ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${exercise.name}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xFF1E1E1E),
            tonalElevation = 0.dp,
        ) {
            val hasMuscles = muscleAdjustments.isNotEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Antes de empezar tu sesión de entrenamiento, responde lo siguiente:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "¿Qué tan $preparedWord te sientes?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                var descriptionExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { descriptionExpanded = !descriptionExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (descriptionExpanded) "Ocultar instrucciones" else "Ver instrucciones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (descriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (descriptionExpanded) "Colapsar" else "Expandir",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                AnimatedVisibility(
                    visible = descriptionExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Text(
                        text = "De acuerdo al sistema de RINGS, este es tu estado a nivel de energía, columna y músculos involucrados para esta sesión. Si no te representan los porcentajes porque consideras que te sientes menos preparado o fresco para esta sesión, puedes cambiar libremente los porcentajes hasta que te identifiquen al 100%. Encima de cada RING, arrastra hacia arriba o abajo para cambiar el porcentaje, y para los músculos, desliza tu dedo hacia izquierda o derecha para ajustar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Justify,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AdjustableRingCompact(
                        modifier = Modifier.weight(1f),
                        title = "Energía",
                        value = neural,
                        ringColor = Color(0xFF448AFF),
                        ringSize = 132,
                        onValueChange = { neural = it; userEditedNeural = true },
                    )
                    AdjustableRingCompact(
                        modifier = Modifier.weight(1f),
                        title = "Columna",
                        value = spinal,
                        ringColor = Color(0xFFFFD740),
                        ringSize = 132,
                        onValueChange = { spinal = it; userEditedSpinal = true },
                    )
                }

                if (hasMuscles) {
                    Text(
                        text = "Músculos de la sesión",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        muscleAdjustments.keys.sorted().forEach { muscleId ->
                            val value = muscleAdjustments[muscleId] ?: 100
                            MinimalMuscleSlider(
                                modifier = Modifier.weight(1f),
                                muscleLabel = muscleId,
                                value = value,
                                onValueChange = { updated -> muscleAdjustments[muscleId] = updated; userEditedMuscles[muscleId] = true },
                            )
                        }
                    }
                }

                PreWorkoutDiscomfortSelector(
                    selectedDiscomforts = selectedDiscomforts,
                    onDiscomfortsChanged = { list ->
                        selectedDiscomforts.clear()
                        selectedDiscomforts.addAll(list)
                    }
                )

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
                            manualMuscularBattery = muscular,
                            manualNeuralBattery = neural,
                            manualSpinalBattery = spinal,
                            manualMuscleBatteries = muscleAdjustments.toMap(),
                            notes = base?.notes,
                            preWorkoutDiscomforts = selectedDiscomforts.toList(),
                        )

                        augeViewModel.saveWellbeing(log)
                        WorkoutReadinessBridge.store(
                            WorkoutReadinessBridge.ReadinessAdjustments(
                                neural = neural,
                                muscular = muscular,
                                spinal = spinal,
                                perMuscle = muscleAdjustments.toMap(),
                                sleepQuality = base?.sleepQuality,
                            ),
                        )
                        allowSheetDismiss = true
                        onReady()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        "Guardar y entrenar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AdjustableRingCompact(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    ringColor: Color,
    ringSize: Int,
    onValueChange: (Int) -> Unit,
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableIntStateOf(value) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .size(ringSize.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "$value por ciento"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 100)
                    setProgress { target ->
                        onValueChange(target.toInt().coerceIn(0, 100))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumulator = 0f
                            dragStartValue = value
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += -dragAmount.y
                            val step = (dragAccumulator / 1.25f).toInt()
                            onValueChange((dragStartValue + step).coerceIn(0, 100))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ReadinessRingVisual(value = value, color = ringColor)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = ringColor,
                    fontSize = 20.sp,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ringColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }

        Text(
            "Arrastrar",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun ReadinessRingVisual(
    value: Int,
    color: Color,
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "readinessRing",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 7.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.12f),
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
internal fun MuscleSliderChip(
    modifier: Modifier = Modifier,
    muscleLabel: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    MinimalMuscleSlider(
        modifier = modifier,
        muscleLabel = muscleLabel,
        value = value,
        onValueChange = onValueChange,
    )
}

internal fun findSessionExercises(program: Program, sessionId: String): List<Exercise> {
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
