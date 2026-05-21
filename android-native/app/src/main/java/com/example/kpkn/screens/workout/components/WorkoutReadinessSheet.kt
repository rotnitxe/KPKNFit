package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kpkn.data.models.Gender
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutReadinessSheet(
    showReadinessSheet: Boolean,
    gender: Gender?,
    sessionMuscleStartingBatteries: Map<String, Int>,
    readinessNeuralStart: Int,
    readinessSpinalStart: Int,
    hazeState: HazeState,
    onSave: (neural: Int, muscular: Int?, spinal: Int, perMuscle: Map<String, Int>) -> Unit,
    onDismissWithoutVerify: () -> Unit
) {
    if (!showReadinessSheet) return

    var neural by rememberSaveable { mutableIntStateOf(readinessNeuralStart) }
    var spinal by rememberSaveable { mutableIntStateOf(readinessSpinalStart) }
    val muscleAdjustments = remember { mutableStateMapOf<String, Int>() }
    var userEditedNeural by rememberSaveable { mutableStateOf(false) }
    var userEditedSpinal by rememberSaveable { mutableStateOf(false) }
    val userEditedMuscles = remember { mutableStateMapOf<String, Boolean>() }
    var initialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialized, sessionMuscleStartingBatteries) {
        if (!initialized) {
            neural = readinessNeuralStart
            spinal = readinessSpinalStart
            muscleAdjustments.clear()
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                muscleAdjustments[muscleId] = value.coerceIn(0, 100)
            }
            initialized = true
        }
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

    var showDismissConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { showDismissConfirmDialog = true },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        val hasMuscles = muscleAdjustments.isNotEmpty()
        val preparedWord = when (gender) {
            Gender.FEMALE -> "preparada"
            Gender.MALE -> "preparado"
            else -> "preparado(a)"
        }

        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            blurRadius = 24.dp,
                            tint = HazeTint(Color.Black.copy(alpha = 0.45f)),
                            backgroundColor = Color(0xFF0A0A0A).copy(alpha = 0.75f),
                            noiseFactor = 0.03f,
                        ),
                    )
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Drag handle visual indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                )

                Text(
                    text = "PRE-FLIGHT CHECK",
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

                // 1. CARDS RESUMEN / ESTADO INICIAL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val avgMuscle = if (muscleAdjustments.isEmpty()) 100 else muscleAdjustments.values.average().toInt()
                    ReadinessSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Sistemas",
                        value = neural,
                        color = Color(0xFF448AFF)
                    )
                    ReadinessSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Estructura",
                        value = spinal,
                        color = Color(0xFFFFD740)
                    )
                    ReadinessSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Muscular",
                        value = avgMuscle,
                        color = Color(0xFF66BB6A)
                    )
                }

                // 2. ACCORDEÓN COLAPSABLE
                var isAdjustExpanded by rememberSaveable { mutableStateOf(false) }
                Surface(
                    onClick = { isAdjustExpanded = !isAdjustExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Ajustar manualmente",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Modificar porcentajes del sistema RINGS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isAdjustExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAdjustExpanded) "Contraer" else "Expandir",
                            tint = Color.White
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isAdjustExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Explicación opcional rápida
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Arrastra verticalmente sobre los RINGS para modificar Energía (Neural) y Columna (Spinal). Desliza horizontalmente sobre las barras para ajustar la frescura de tus músculos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Justify
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
                                ringSize = 120,
                                onValueChange = { neural = it; userEditedNeural = true },
                            )
                            AdjustableRingCompact(
                                modifier = Modifier.weight(1f),
                                title = "Columna",
                                value = spinal,
                                ringColor = Color(0xFFFFD740),
                                ringSize = 120,
                                onValueChange = { spinal = it; userEditedSpinal = true },
                            )
                        }

                        if (hasMuscles) {
                            Text(
                                text = "Frescura por Músculo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                muscleAdjustments.keys.sorted().forEach { muscleId ->
                                    val value = muscleAdjustments[muscleId] ?: 100
                                    MuscleSliderChip(
                                        modifier = Modifier.weight(1f),
                                        muscleLabel = muscleId,
                                        value = value,
                                        onValueChange = { updated ->
                                            muscleAdjustments[muscleId] = updated
                                            userEditedMuscles[muscleId] = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. BOTÓN PRINCIPAL
                Button(
                    onClick = {
                        val derivedMuscular = if (muscleAdjustments.isEmpty()) {
                            null
                        } else {
                            muscleAdjustments.values.average().toInt().coerceIn(0, 100)
                        }
                        onSave(neural, derivedMuscular, spinal, muscleAdjustments.toMap())
                        allowSheetDismiss = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Confirmar y Entrenar",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    if (showDismissConfirmDialog) {
        Dialog(
            onDismissRequest = { showDismissConfirmDialog = false },
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF161616),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "¿Empezar sin verificar RINGS?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Puedes ajustar tu energía, columna y músculos antes de empezar, o saltar este paso y comenzar directamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                allowSheetDismiss = true
                                onDismissWithoutVerify()
                                showDismissConfirmDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Iniciar sin verificar", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showDismissConfirmDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Volver", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                CircularProgressVisual(value = value, color = color)
                Text(
                    text = "$value%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = color,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CircularProgressVisual(
    value: Int,
    color: Color
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "circularProgressVisual",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 4.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.1f),
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
    val clamped = value.coerceIn(0, 100)
    val accent = when {
        clamped >= 80 -> Color(0xFF66BB6A)
        clamped >= 55 -> Color(0xFFFFD740)
        else -> Color(0xFFEF5350)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    muscleLabel,
                    style = MaterialTheme.typography.labelSmall,
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
                    fontSize = 11.sp,
                )
            }

            // Compact slider-like bar with tap and drag
            var barSize by remember { mutableStateOf(IntSize(1, 1)) }
            var dragStartValue by remember { mutableIntStateOf(clamped) }
            var dragAccumulator by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .onSizeChanged { barSize = it }
                    .semantics {
                        contentDescription = muscleLabel
                        stateDescription = "$clamped por ciento"
                        progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..100f, 100)
                        setProgress { target ->
                            onValueChange(target.toInt().coerceIn(0, 100))
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / barSize.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onValueChange((ratio * 100f).toInt().coerceIn(0, 100))
                        }
                    }
                    .pointerInput(Unit) {
                        val widthPx = { barSize.width.toFloat().coerceAtLeast(1f) }
                        detectDragGestures(
                            onDragStart = {
                                dragStartValue = clamped
                                dragAccumulator = 0f
                            },
                            onDragEnd = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount.x
                                val delta = ((dragAccumulator / widthPx()) * 100f).toInt()
                                onValueChange((dragStartValue + delta).coerceIn(0, 100))
                            },
                        )
                    },
            ) {
                // Track background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                )
                // Filled portion
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(clamped / 100f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent),
                )
            }
        }
    }
}
