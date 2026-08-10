package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.min
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.MovementPatternReadiness
import com.example.kpkn.ui.components.KpknSheet
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.home.AugeRingsCanvas
import com.example.kpkn.screens.home.RingColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutReadinessSheet(
    showReadinessSheet: Boolean,
    gender: Gender?,
    sessionMuscleStartingBatteries: Map<String, Int>,
    readinessNeuralStart: Int,
    readinessMuscularStart: Int,
    readinessSpinalStart: Int,
    hazeState: HazeState,
    onSave: (
        neural: Int,
        muscular: Int?,
        spinal: Int,
        perMuscle: Map<String, Int>,
        discomforts: List<String>,
        /** Only channels the user actually edited — used for AUGE manual overrides. */
        manualNeural: Int?,
        manualSpinal: Int?,
        manualMuscular: Int?,
        manualMuscleBatteries: Map<String, Int>,
    ) -> Unit,
    patternReadiness: List<MovementPatternReadiness> = emptyList(),
    exerciseReadinessMap: Map<String, ExerciseReadiness> = emptyMap(),
    sessionExercises: List<Exercise> = emptyList(),
    perMuscle: Map<String, com.example.kpkn.data.models.MuscleRecoveryStatus> = emptyMap(),
    initialDiscomforts: List<String> = emptyList(),
    voiceSessionEnabled: Boolean = false,
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
    onVoiceToggle: () -> Unit = {},
    onVoiceCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit = {},
) {
    if (!showReadinessSheet) return

    var neural by rememberSaveable { mutableIntStateOf(readinessNeuralStart) }
    var muscular by rememberSaveable { mutableIntStateOf(readinessMuscularStart) }
    var spinal by rememberSaveable { mutableIntStateOf(readinessSpinalStart) }
    val muscleAdjustments = remember { mutableStateMapOf<String, Int>() }
    val derivedMuscular by derivedStateOf {
        if (muscleAdjustments.isEmpty()) readinessMuscularStart.coerceIn(0, 100)
        else muscleAdjustments.values.average().toInt().coerceIn(0, 100)
    }
    var userEditedNeural by rememberSaveable { mutableStateOf(false) }
    var userEditedSpinal by rememberSaveable { mutableStateOf(false) }
    val userEditedMuscles = remember { mutableStateMapOf<String, Boolean>() }
    var initialized by rememberSaveable { mutableStateOf(false) }
    val selectedDiscomforts = remember { mutableStateListOf<String>() }
    var adjustTarget by remember { mutableStateOf<ReadinessAdjustTarget?>(null) }

    LaunchedEffect(
        initialized,
        readinessNeuralStart,
        readinessMuscularStart,
        readinessSpinalStart,
        sessionMuscleStartingBatteries,
    ) {
        if (!initialized) {
            neural = readinessNeuralStart
            muscular = derivedMuscular
            spinal = readinessSpinalStart
            muscleAdjustments.clear()
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                muscleAdjustments[muscleId] = value.coerceIn(0, 100)
            }
            selectedDiscomforts.clear()
            selectedDiscomforts.addAll(initialDiscomforts)
            initialized = true
        } else {
            if (!userEditedNeural) neural = readinessNeuralStart
            muscular = readinessMuscularStart
            if (!userEditedSpinal) spinal = readinessSpinalStart
            val validMuscles = sessionMuscleStartingBatteries.keys
            muscleAdjustments.keys
                .filter { it !in validMuscles }
                .forEach { muscleAdjustments.remove(it) }
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                if (userEditedMuscles[muscleId] != true) {
                    muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                }
            }
        }
    }
    KpknSheet(
        onDismissRequest = {},
        dismissible = false,
        hazeState = hazeState,
    ) {
        val preparedWord = when (gender) {
            Gender.FEMALE -> "preparada"
            Gender.MALE -> "preparado"
            else -> "preparado(a)"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                    text = "Reporta tu estado antes de entrenar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "¿Qué tan $preparedWord te sientes?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                Spacer(Modifier.height(10.dp))

                // 1. RINGS ENTRENLAZADOS (estilo Home) + ajuste por ring
                InterlacedRingsBlock(
                    muscular = derivedMuscular,
                    neural = neural,
                    spinal = spinal,
                    selectedTarget = adjustTarget,
                    muscleAdjustments = muscleAdjustments,
                    onRingTap = { adjustTarget = it },
                    onDismiss = { adjustTarget = null },
                    onNeuralChange = { neural = it; userEditedNeural = true },
                    onSpinalChange = { spinal = it; userEditedSpinal = true },
                    onMuscleChange = { muscleId, updated ->
                        muscleAdjustments[muscleId] = updated
                        userEditedMuscles[muscleId] = true
                    },
                )

                val sessionPillarMuscleIds = remember(sessionExercises) {
                    getSessionPillarMuscleIds(sessionExercises)
                }
                PreWorkoutDiscomfortSelector(
                    selectedDiscomforts = selectedDiscomforts,
                    onDiscomfortsChanged = { list ->
                        selectedDiscomforts.clear()
                        selectedDiscomforts.addAll(list)
                    },
                    sessionPillarMuscleIds = sessionPillarMuscleIds,
                )

                // ── 2c. Control por voz: pre-entrenamiento ──
                SessionVoiceReadinessCard(
                    voiceSessionEnabled = voiceSessionEnabled,
                    captureMode = voiceCaptureMode,
                    onToggle = onVoiceToggle,
                    onCaptureModeChange = onVoiceCaptureModeChange,
                )

                Spacer(Modifier.height(4.dp))

                // 3. BOTÓN PRINCIPAL — FAB circular amarillo
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    FloatingActionButton(
                        onClick = {
                            val editedMuscleMap = muscleAdjustments
                                .filter { (id, _) -> userEditedMuscles[id] == true }
                                .mapValues { (_, v) -> v.coerceIn(0, 100) }
                            val manualMuscular = if (editedMuscleMap.isNotEmpty()) {
                                editedMuscleMap.values.average().toInt().coerceIn(0, 100)
                            } else null
                            onSave(
                                neural,
                                derivedMuscular,
                                spinal,
                                muscleAdjustments.toMap(),
                                selectedDiscomforts.toList(),
                                if (userEditedNeural) neural.coerceIn(0, 100) else null,
                                if (userEditedSpinal) spinal.coerceIn(0, 100) else null,
                                manualMuscular,
                                editedMuscleMap,
                            )
                        },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirmar y entrenar",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
    }
}

private enum class ReadinessAdjustTarget { MUSCULAR, ENERGY, SPINE }

@Composable
private fun InterlacedRingsBlock(
    muscular: Int,
    neural: Int,
    spinal: Int,
    selectedTarget: ReadinessAdjustTarget?,
    muscleAdjustments: Map<String, Int>,
    onRingTap: (ReadinessAdjustTarget) -> Unit,
    onDismiss: () -> Unit,
    onNeuralChange: (Int) -> Unit,
    onSpinalChange: (Int) -> Unit,
    onMuscleChange: (String, Int) -> Unit,
) {
    val density = LocalDensity.current
    var ringsSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .onSizeChanged { ringsSize = it },
        ) {
            AugeRingsCanvas(
                mp = muscular / 100f,
                sp = neural / 100f,
                cp = spinal / 100f,
            )

            if (ringsSize != IntSize.Zero) {
                val radius = min(ringsSize.width / 5f, ringsSize.height * 0.38f)
                val centerX = ringsSize.width / 2f
                val centerY = ringsSize.height / 2f
                val dx = radius * 1.45f
                val dy = radius * 0.48f
                val centers = listOf(
                    Offset(centerX - dx, centerY - dy),
                    Offset(centerX, centerY + dy),
                    Offset(centerX + dx, centerY - dy),
                )
                val targets = listOf(
                    ReadinessAdjustTarget.MUSCULAR,
                    ReadinessAdjustTarget.ENERGY,
                    ReadinessAdjustTarget.SPINE,
                )
                val hitRadius = radius + with(density) { 12.dp.toPx() }

                targets.forEachIndexed { i, target ->
                    val c = centers[i]
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((c.x - hitRadius).toInt(), (c.y - hitRadius).toInt()) }
                            .size(with(density) { (hitRadius * 2).toInt().toDp() })
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onRingTap(target) }
                            .semantics {
                                contentDescription = when (target) {
                                    ReadinessAdjustTarget.MUSCULAR -> "Ring de músculos. Toca para ajustar."
                                    ReadinessAdjustTarget.ENERGY -> "Ring de energía. Toca para ajustar."
                                    ReadinessAdjustTarget.SPINE -> "Ring de columna. Toca para ajustar."
                                }
                            },
                    )
                }

                selectedTarget?.let { selected ->
                    val selectedIndex = targets.indexOf(selected)
                    val c = centers[selectedIndex]
                    val cardWidthPx = with(density) { 264.dp.toPx() }
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(
                            (c.x - cardWidthPx / 2).toInt(),
                            (c.y + with(density) { 14.dp.toPx() }).toInt(),
                        ),
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = onDismiss,
                    ) {
                        ReadinessAdjustCard(
                            target = selected,
                            neural = neural,
                            spinal = spinal,
                            muscleAdjustments = muscleAdjustments,
                            onNeuralChange = onNeuralChange,
                            onSpinalChange = onSpinalChange,
                            onMuscleChange = onMuscleChange,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }

        // Keep the captions clear of the lowered energy ring.
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InterlacedRingLabel("Músculos", muscular, RingColors[0])
            InterlacedRingLabel("Energía", neural, RingColors[1])
            InterlacedRingLabel("Columna", spinal, RingColors[2])
        }

        Text(
            text = "Si no te representan los rings, presiona cada uno para ajustarlo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessAdjustCard(
    target: ReadinessAdjustTarget,
    neural: Int,
    spinal: Int,
    muscleAdjustments: Map<String, Int>,
    onNeuralChange: (Int) -> Unit,
    onSpinalChange: (Int) -> Unit,
    onMuscleChange: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, color) = when (target) {
        ReadinessAdjustTarget.MUSCULAR -> "Músculos" to RingColors[0]
        ReadinessAdjustTarget.ENERGY -> "Energía" to RingColors[1]
        ReadinessAdjustTarget.SPINE -> "Columna" to RingColors[2]
    }
    Surface(
        modifier = Modifier
            .width(264.dp)
            .heightIn(max = 340.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1F1F1F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        text = "Ajustar $title",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            when (target) {
                ReadinessAdjustTarget.ENERGY -> ReadinessSliderRow(
                    title = "Energía",
                    value = neural,
                    color = RingColors[1],
                    onValueChange = onNeuralChange,
                )
                ReadinessAdjustTarget.SPINE -> ReadinessSliderRow(
                    title = "Columna",
                    value = spinal,
                    color = RingColors[2],
                    onValueChange = onSpinalChange,
                )
                ReadinessAdjustTarget.MUSCULAR -> {
                    if (muscleAdjustments.isEmpty()) {
                        Text(
                            text = "No hay músculos detectados para esta sesión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Frescura por Músculo",
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
                                    MinimalMuscleSlider(
                                        modifier = Modifier.weight(1f),
                                        muscleLabel = muscleId,
                                        value = muscleAdjustments[muscleId] ?: 100,
                                        onValueChange = { updated ->
                                            onMuscleChange(muscleId, updated)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InterlacedRingLabel(
    label: String,
    value: Int,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Text(
            text = "$value%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReadinessSliderRow(
    title: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
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
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = color,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                activeTrackColor = color,
                thumbColor = color,
                inactiveTrackColor = color.copy(alpha = 0.15f),
            ),
        )
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
            "Arrastrar verticalmente",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
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
        val strokePx = 6.dp.toPx()
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

@Composable
private fun SessionVoiceReadinessCard(
    voiceSessionEnabled: Boolean,
    captureMode: com.example.kpkn.data.models.VoiceCaptureMode,
    onToggle: () -> Unit,
    onCaptureModeChange: (com.example.kpkn.data.models.VoiceCaptureMode) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var modeInfo by remember { mutableStateOf<com.example.kpkn.data.models.VoiceCaptureMode?>(null) }
    var cardHeight by remember { mutableIntStateOf(0) }
    var modeRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Surface(
        onClick = { showInfo = true },
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardHeight = it.height },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (voiceSessionEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Entrenar con Comandos de Voz",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (voiceSessionEnabled) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                    )
                }
                Switch(
                    checked = voiceSessionEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.scale(0.8f),
                )
            }

            if (voiceSessionEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { modeRowHeight = it.height },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Modo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val musicSelected = captureMode == com.example.kpkn.data.models.VoiceCaptureMode.MUSIC
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = !musicSelected,
                            onClick = {
                                if (musicSelected) {
                                    onCaptureModeChange(com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE)
                                }
                                modeInfo = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Manos libres", fontSize = 11.sp) }
                        SegmentedButton(
                            selected = musicSelected,
                            onClick = {
                                if (!musicSelected) {
                                    onCaptureModeChange(com.example.kpkn.data.models.VoiceCaptureMode.MUSIC)
                                }
                                modeInfo = com.example.kpkn.data.models.VoiceCaptureMode.MUSIC
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Música", fontSize = 11.sp) }
                    }
                }

                modeInfo?.let { mode ->
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(
                            0,
                            modeRowHeight + with(density) { 8.dp.toPx() }.toInt(),
                        ),
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = { modeInfo = null },
                    ) {
                        VoiceModeInfoCard(mode = mode, onDismiss = { modeInfo = null })
                    }
                }
            }
        }
    }

    if (showInfo) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                0,
                cardHeight + with(density) { 8.dp.toPx() }.toInt(),
            ),
            properties = PopupProperties(focusable = true),
            onDismissRequest = { showInfo = false },
        ) {
            Surface(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1F1F1F),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "¿Cómo funciona la voz?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = "Controla tu sesión hablando: registra series, " +
                            "cambia pesos y gestiona descansos con comandos de voz. " +
                            "La voz estará activa durante toda la sesión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Ejemplos: “2 series más”, “sube a 80 kg”, “pausa de 90 segundos”.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Button(
                        onClick = { showInfo = false },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("Entendido", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceModeInfoCard(
    mode: com.example.kpkn.data.models.VoiceCaptureMode,
    onDismiss: () -> Unit,
) {
    val (title, body) = when (mode) {
        com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE ->
            "Modo manos libres" to
                "Registra tus series con el micrófono de tus auriculares, incluso por bluetooth, " +
                "sin acercarte al celular. La contra: el audio suena con calidad de llamada " +
                "y degrada la música de fondo."
        com.example.kpkn.data.models.VoiceCaptureMode.MUSIC ->
            "Modo música" to
                "Usa el micrófono del celular, no el de tus auriculares: tu música se escucha " +
                "perfecta. Solo necesitas tener el teléfono cerca (sin desbloquearlo) para " +
                "registrar tus series por voz."
    }
    Surface(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1F1F1F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Entendido", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreWorkoutDiscomfortSelector(
    selectedDiscomforts: List<String>,
    onDiscomfortsChanged: (List<String>) -> Unit,
    sessionPillarMuscleIds: List<String> = emptyList(),
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(sessionPillarMuscleIds) {
        DiscomfortSuggestionEngine.suggestForMuscles(sessionPillarMuscleIds)
    }
    val searchResults = remember(query) {
        if (query.isBlank()) emptyList() else DiscomfortSuggestionEngine.search(query)
    }
    val isSearching = query.isNotBlank()
    val count = selectedDiscomforts.size

    fun toggle(item: DiscomfortCatalogEntry) {
        val newSelection = selectedDiscomforts.toMutableList()
        if (selectedDiscomforts.contains(item.id)) {
            newSelection.remove(item.id)
        } else {
            newSelection.add(item.id)
        }
        onDiscomfortsChanged(newSelection)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Molestias previas al entrenamiento",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (count == 0) "Ninguna reportada" else "$count seleccionada(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Plegar" else "Desplegar",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar molestia...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                    focusedLeadingIconColor = Color.White.copy(alpha = 0.5f),
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.5f),
                ),
            )

            if (isSearching) {
                Text(
                    text = "Resultados",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                if (searchResults.isEmpty()) {
                    Text(
                        text = "Sin resultados para \"$query\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        searchResults.forEach { item ->
                            DiscomfortFilterChip(
                                item = item,
                                isSelected = selectedDiscomforts.contains(item.id),
                                onClick = { toggle(item) },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Sugeridas para tu sesión",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                if (suggestions.isEmpty()) {
                    Text(
                        text = "No hay molestias típicas para los músculos de esta sesión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { item ->
                            DiscomfortFilterChip(
                                item = item,
                                isSelected = selectedDiscomforts.contains(item.id),
                                onClick = { toggle(item) },
                            )
                        }
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun DiscomfortFilterChip(
    item: DiscomfortCatalogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(item.label, fontSize = 11.sp) },
        colors = com.example.kpkn.ui.components.kpknSheetWhiteFilterChipColors(),
        border = null,
    )
}

private fun getSessionPillarMuscleIds(exercises: List<Exercise>): List<String> {
    val pillars = linkedSetOf<String>()
    fun collect(exercise: Exercise) {
        val info = resolveExercise(exercise.resolvedCanonicalExerciseId())
            ?: resolveExercise(exercise.exerciseDbId ?: exercise.exerciseId)
            ?: return
        SessionMuscleFilter.relevantMusclesFor(info)
            .forEach { involvement ->
                getAugeMusclePillarId(involvement.muscle, involvement.emphasis)
                    .takeIf { it.isNotBlank() }
                    ?.let(pillars::add)
            }
    }
    exercises.forEach(::collect)
    return pillars.toList()
}
