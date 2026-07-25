package com.example.kpkn.screens.sessioneditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.home.SingleRingCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.example.kpkn.screens.sessioneditor.components.InlineSetRow
import com.example.kpkn.screens.sessioneditor.components.SupersetRestWheelRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun CompactRestBundleButton(
    primaryLabel: String,
    primarySeconds: Int,
    sideSeconds: Int?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int, Int?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val summary = if (sideSeconds != null) {
        "$primaryLabel ${formatRestSummary(primarySeconds)} · Lados ${formatRestSummary(sideSeconds)}"
    } else {
        "$primaryLabel ${formatRestSummary(primarySeconds)}"
    }
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showPicker = true },
        shape = RoundedCornerShape(999.dp),
        color = DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Timer, contentDescription = "Configurar descansos", tint = Color.White, modifier = Modifier.size(18.dp))
            Text(summary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }

    if (showPicker) {
        RestBundleDialog(
            primaryLabel = primaryLabel,
            initialPrimarySeconds = primarySeconds,
            initialSideSeconds = sideSeconds,
            accentColor = accentColor,
            onDismiss = { showPicker = false },
            onConfirm = { primary, side ->
                onConfirm(primary, side)
                showPicker = false
            },
        )
    }
}

@Composable
internal fun RestBundleDialog(
    primaryLabel: String,
    initialPrimarySeconds: Int,
    initialSideSeconds: Int?,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int?) -> Unit,
) {
    var primaryMinutes by rememberSaveable(initialPrimarySeconds) { mutableStateOf((initialPrimarySeconds / 60).coerceIn(0, 59)) }
    var primarySeconds by rememberSaveable(initialPrimarySeconds) { mutableStateOf((initialPrimarySeconds % 60).coerceIn(0, 59)) }
    var sideMinutes by rememberSaveable(initialSideSeconds) { mutableStateOf(((initialSideSeconds ?: 0) / 60).coerceIn(0, 59)) }
    var sideSeconds by rememberSaveable(initialSideSeconds) { mutableStateOf(((initialSideSeconds ?: 0) % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descansos", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SupersetRestWheelRow(primaryLabel, primaryMinutes, primarySeconds, accentColor, { primaryMinutes = it }, { primarySeconds = it })
                if (initialSideSeconds != null) {
                    SupersetRestWheelRow("Entre lados", sideMinutes, sideSeconds, accentColor, { sideMinutes = it }, { sideSeconds = it })
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        primaryMinutes * 60 + primarySeconds,
                        initialSideSeconds?.let { sideMinutes * 60 + sideSeconds },
                    )
                },
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun CompactRestPickerButton(
    label: String,
    totalSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showPicker = true },
        color = DarkEditorChip,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$label ${formatRestSummary(totalSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
    
    if (showPicker) {
        DurationPickerDialog(
            initialTotalSeconds = totalSeconds,
            accentColor = accentColor,
            onDismiss = { showPicker = false },
            onConfirm = {
                onConfirm(it)
                showPicker = false
            },
        )
    }
}

@Composable
internal fun CompactModeSelector(
    currentMode: TrainingMode,
    accentColor: Color,
    onModeSelected: (TrainingMode) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box {
        Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showMenu = true },
            color = DarkEditorChip,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trainingModeLabel(currentMode),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 90.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            listOf(
                TrainingMode.REPS to "Reps",
                TrainingMode.AMRAP to "AMRAP",
                TrainingMode.RM to "RM",
                TrainingMode.SOLO_RPE to "Solo RPE",
                TrainingMode.TIME to "Tiempo",
                TrainingMode.DISTANCE to "Distancia",
                TrainingMode.CUSTOM to "Personalizado",
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        showMenu = false
                        onModeSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun CompactGoalTrackingButton(
    isActive: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onOpenSheet: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .combinedClickable(
                onClick = { if (onOpenSheet != null) onOpenSheet() else onToggle() },
                onLongClick = { onOpenSheet?.invoke() },
            ),
        color = if (isActive) DarkEditorChipSelected else DarkEditorChip,
        border = if (isActive) BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Seguimiento de metas",
                tint = if (isActive) Color(0xFFFFB300) else Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Meta",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFFFFB300) else Color.White,
            )
        }
    }
}

@Composable
internal fun UnilateralModeSelector(
    mode: UnilateralMode,
    accentColor: Color,
    onToggleUnilateral: () -> Unit,
) {
    val isUnilateral = mode != UnilateralMode.BILATERAL
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { showMenu = true },
            color = if (isUnilateral) DarkEditorChipSelected else DarkEditorChip,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = if (isUnilateral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (isUnilateral) "Unilateral" else "Bilateral",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnilateral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = if (isUnilateral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Bilateral") }, onClick = {
                showMenu = false
                if (isUnilateral) onToggleUnilateral()
            })
            DropdownMenuItem(text = { Text("Unilateral") }, onClick = {
                showMenu = false
                if (!isUnilateral) onToggleUnilateral()
            })
        }
    }
}

@Composable
internal fun SideOrderChip(
    sideOrder: UnilateralSideOrder,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    val label = when (sideOrder) {
        UnilateralSideOrder.LEFT_RIGHT -> "Orden L/R"
        UnilateralSideOrder.RIGHT_LEFT -> "Orden R/L"
    }
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onToggle() },
        color = DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = "Cambiar orden unilateral",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun ExerciseSetsCarousel(
    exercise: Exercise,
    reference1RM: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    predictedMetrics: Map<String, Double?>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onAddSet: (String?) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
) {
    if (exercise.sets.isEmpty()) {
        // Empty state
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "No hay series añadidas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { onAddSet(null) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(48.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Añadir serie",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        return
    }

    var currentSetIndex by remember(exercise.id) { mutableStateOf(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentSetIndex)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(exercise.sets.size) {
        val lastIndex = (exercise.sets.size - 1).coerceAtLeast(0)
        currentSetIndex = currentSetIndex.coerceIn(0, lastIndex)
    }
    LaunchedEffect(listState, exercise.sets.size) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val items = layoutInfo.visibleItemsInfo
                if (items.isEmpty()) return@collect
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = items.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                } ?: return@collect
                currentSetIndex = closest.index.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0))
            }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val showUnilateralDualCards = exercise.isEffectivelyUnilateral()
        val orderedSides = when (exercise.unilateralSideOrder) {
            UnilateralSideOrder.LEFT_RIGHT -> listOf("L", "R")
            UnilateralSideOrder.RIGHT_LEFT -> listOf("R", "L")
        }
        // Carousel using LazyRow
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showUnilateralDualCards) 392.dp else 214.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            state = listState,
        ) {
            itemsIndexed(exercise.sets) { index, set ->
                key(set.id) {
                    val predictedWeight = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM, set) {
                        calculateSuggestedLoad(exercise, set)
                    }
                    val estimatedMetric = predictedMetrics[set.id]

                    Box(
                        modifier = Modifier
                            .width(292.dp)
                            .fillMaxHeight(),
                    ) {
                        if (showUnilateralDualCards) {
                            val showLeftCard = set.leftTarget != null
                            val showRightCard = set.rightTarget != null
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                orderedSides.forEach { side ->
                                    val isLeft = side == "L"
                                    val showCard = if (isLeft) showLeftCard else showRightCard
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (showCard) {
                                            val isFirstVisible = orderedSides.takeWhile { it != side }.none { prior ->
                                                if (prior == "L") showLeftCard else showRightCard
                                            }
                                            InlineSetRow(
                                                set = set,
                                                index = index,
                                                reference1RM = reference1RM,
                                                predictedWeight = predictedWeight,
                                                estimatedMetric = estimatedMetric,
                                                trainingMode = trainingMode,
                                                customUnit = customUnit,
                                                accentColor = if (isLeft) Color(0xFF2196F3) else Color(0xFFFF5252),
                                                canMoveUp = isFirstVisible && index > 0,
                                                canMoveDown = isFirstVisible && index < exercise.sets.size - 1,
                                                isUnilateral = true,
                                                fixedUnilateralSide = side,
                                                showSetActions = isFirstVisible,
                                                unilateralIntensityMode = exercise.unilateralIntensityMode,
                                                onUpdate = { updater -> onUpdateSet(set.id, updater) },
                                                onRemove = { onRemoveSet(set.id) },
                                                onMoveUp = { onMoveSet(set.id, -1) },
                                                onMoveDown = { onMoveSet(set.id, 1) },
                                            )
                                        } else {
                                            UnilateralAddGhostCard(
                                                side = side,
                                                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(),
                                                onClick = {
                                                    onUpdateSet(set.id) { s ->
                                                        val default = UnilateralTarget(
                                                            weight = s.weight,
                                                            targetReps = s.targetReps,
                                                            targetDuration = s.targetDuration,
                                                            targetValue = s.plannedTargetV2,
                                                            targetRPE = s.targetRPE,
                                                            targetRIR = s.targetRIR,
                                                            intensityMode = s.intensityMode,
                                                        )
                                                        if (side == "L") {
                                                            s.copy(leftTarget = s.leftTarget ?: default)
                                                        } else {
                                                            s.copy(rightTarget = s.rightTarget ?: default)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            InlineSetRow(
                                set = set,
                                index = index,
                                reference1RM = reference1RM,
                                predictedWeight = predictedWeight,
                                estimatedMetric = estimatedMetric,
                                trainingMode = trainingMode,
                                customUnit = customUnit,
                                accentColor = accentColor,
                                canMoveUp = index > 0,
                                canMoveDown = index < exercise.sets.size - 1,
                                isUnilateral = exercise.isEffectivelyUnilateral(),
                                unilateralIntensityMode = exercise.unilateralIntensityMode,
                                onUpdate = { updater -> onUpdateSet(set.id, updater) },
                                onRemove = { onRemoveSet(set.id) },
                                onMoveUp = { onMoveSet(set.id, -1) },
                                onMoveDown = { onMoveSet(set.id, 1) },
                            )
                        }
                    }
                }
            }
            item("add-set") {
                Box(
                    modifier = Modifier
                        .width(292.dp)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AddSetGhostCard(onAddSet = { onAddSet(null) })
                }
            }
        }

        // Dot stepper indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.sets.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentSetIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentSetIndex) accentColor else accentColor.copy(alpha = 0.35f),
                            )
                            .clickable {
                                currentSetIndex = index
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                    )
                }
            }
            Text(
                "${currentSetIndex + 1}/${exercise.sets.size}",
                modifier = Modifier.align(Alignment.CenterEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun UnilateralAddGhostCard(
    side: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = DarkEditorSurfaceSoft,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Añadir lado $side", tint = accentColor, modifier = Modifier.size(14.dp))
                Text(
                    "Añadir $side",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }
        }
    }
}

@Composable
internal fun EstimatedRingsRow(
    energy: Int,
    spine: Int,
) {
    val energyProgress = (energy.coerceIn(0, 100) / 100f)
    val spineProgress = (spine.coerceIn(0, 100) / 100f)
    val ringColors = listOf(Color(0xFF448AFF), Color(0xFFFFD740))
    val ringLabels = listOf("ENERGÍA", "COLUMNA")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(energyProgress to ringLabels[0], spineProgress to ringLabels[1]).forEachIndexed { index, entry ->
            val (progress, label) = entry
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SingleRingCanvas(
                    value = progress,
                    color = ringColors[index],
                    ringDiameter = 120f,
                    strokeWidth = 8f,
                )
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = ringColors[index])
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AddSetGhostCard(onAddSet: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAddSet() },
        color = DarkEditorSurfaceSoft,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Añadir serie",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Agregar serie",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun Exercise.toggledBilateralUnilateral(): Exercise {
    val nextUnilateral = !isEffectivelyUnilateral()
    return if (nextUnilateral) {
        copy(
            isUnilateral = true,
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            sets = sets.map { set ->
                val target = UnilateralTarget(
                    weight = set.weight,
                    targetReps = set.targetReps,
                    targetDuration = set.targetDuration,
                    targetValue = set.plannedTargetV2,
                    targetRPE = set.targetRPE,
                    targetRIR = set.targetRIR,
                    intensityMode = set.intensityMode,
                )
                set.copy(
                    leftTarget = target,
                    rightTarget = target,
                )
            }
        )
    } else {
        copy(
            isUnilateral = false,
            unilateralMode = UnilateralMode.BILATERAL,
            restBetweenSidesSeconds = null,
            sets = sets.map { set ->
                val source = set.leftTarget ?: set.rightTarget
                if (source != null) {
                    set.copy(
                        weight = source.weight,
                        targetReps = source.targetReps,
                        targetDuration = source.targetDuration,
                        plannedTargetV2 = source.targetValue,
                        targetRPE = source.targetRPE,
                        targetRIR = source.targetRIR,
                        intensityMode = source.intensityMode,
                        restBetweenSides = null,
                        leftTarget = null,
                        rightTarget = null,
                    )
                } else {
                    set.copy(
                        restBetweenSides = null,
                        leftTarget = null,
                        rightTarget = null,
                    )
                }
            },
        )
    }
}
