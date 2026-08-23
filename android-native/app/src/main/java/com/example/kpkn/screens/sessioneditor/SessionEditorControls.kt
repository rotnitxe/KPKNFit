package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import com.example.kpkn.screens.sessioneditor.components.InlineSetRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknDropdownMenu

@Composable
internal fun CompactRestBundleButton(
    primaryLabel: String,
    primarySeconds: Int,
    sideSeconds: Int?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    accentTinted: Boolean = false,
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
        color = if (accentTinted) accentColor.copy(alpha = 0.15f) else DarkEditorChip,
        border = if (accentTinted) BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)) else null,
    ) {
        Row(
            modifier = Modifier
                .then(if (accentTinted) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Timer, contentDescription = "Configurar descansos", tint = if (accentTinted) accentColor else Color.White, modifier = Modifier.size(18.dp))
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (accentTinted) accentColor else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (accentTinted) Modifier.weight(1f, fill = false) else Modifier,
            )
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
    com.example.kpkn.ui.components.KpknRestPickerChain(
        primaryTitle = primaryLabel,
        primarySeconds = initialPrimarySeconds,
        secondaryTitle = initialSideSeconds?.let { "Descanso entre lados" },
        secondarySeconds = initialSideSeconds,
        onConfirm = { primary, side -> onConfirm(primary, side) },
        onDismiss = onDismiss,
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
    modifier: Modifier = Modifier,
    accentTinted: Boolean = false,
    onModeSelected: (TrainingMode) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        Surface(
        modifier = Modifier
            .then(if (modifier !== Modifier) Modifier.fillMaxWidth() else Modifier)
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showMenu = true },
            color = if (accentTinted) accentColor.copy(alpha = 0.15f) else DarkEditorChip,
            border = if (accentTinted) BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)) else null,
        ) {
            Row(
                modifier = Modifier
                    .then(if (modifier !== Modifier) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trainingModeLabel(currentMode),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (accentTinted) accentColor else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (modifier !== Modifier) Modifier.weight(1f, fill = false) else Modifier.widthIn(max = 90.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (accentTinted) accentColor else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        
        KpknDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                onClick = { onOpenSheet?.invoke() ?: onToggle() },
                onLongClick = onToggle,
            ),
        color = if (isActive) DarkEditorChipSelected else DarkEditorChip,
        border = if (isActive) BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f)) else null,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Meta del ejercicio",
                tint = if (isActive) Color(0xFFFFB300) else Color.White,
                modifier = Modifier.size(18.dp),
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
        KpknDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
    backgroundAccentColor: Color = accentColor,
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
    var techniqueConfigExpandedBySetId by remember(exercise.id) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }
    val anyTechniqueConfigExpanded = techniqueConfigExpandedBySetId.values.any { it }

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
        val baseHeight = if (showUnilateralDualCards) 460.dp else 278.dp
        val expandedHeight = if (showUnilateralDualCards) 580.dp else 400.dp
        val density = LocalDensity.current
        var setCardTotalHeight by remember { mutableStateOf(220.dp) }
        // Carousel using LazyRow
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showUnilateralDualCards) {
                        Modifier.height(if (anyTechniqueConfigExpanded) expandedHeight else baseHeight)
                    } else {
                        Modifier
                    },
                ),
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
                            .width(264.dp)
                        .then(if (showUnilateralDualCards) Modifier.fillMaxHeight() else Modifier)
                        .onSizeChanged { size ->
                            if (!showUnilateralDualCards && size.height > 0) {
                                setCardTotalHeight = with(density) { size.height.toDp() }
                            }
                        },
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
                                                 backgroundAccentColor = backgroundAccentColor,
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
                                                 onTechniqueConfigExpandedChange = { expanded ->
                                                    techniqueConfigExpandedBySetId =
                                                        techniqueConfigExpandedBySetId + (set.id to expanded)
                                                },
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
                                                            targetRepsRange = s.targetRepsRange,
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
                                 backgroundAccentColor = backgroundAccentColor,
                                canMoveUp = index > 0,
                                canMoveDown = index < exercise.sets.size - 1,
                                isUnilateral = exercise.isEffectivelyUnilateral(),
                                fillHeight = false,
                                unilateralIntensityMode = exercise.unilateralIntensityMode,
                                 onUpdate = { updater -> onUpdateSet(set.id, updater) },
                                 onRemove = { onRemoveSet(set.id) },
                                 onMoveUp = { onMoveSet(set.id, -1) },
                                 onMoveDown = { onMoveSet(set.id, 1) },
                                 onTechniqueConfigExpandedChange = { expanded ->
                                    techniqueConfigExpandedBySetId =
                                        techniqueConfigExpandedBySetId + (set.id to expanded)
                                },
                            )
                        }
                    }
                }
            }
            item("add-set") {
                Box(
                    modifier = Modifier
                        .width(264.dp)
                        .then(if (showUnilateralDualCards) Modifier.fillMaxHeight() else Modifier.height(setCardTotalHeight))
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showUnilateralDualCards) {
                        AddSetGhostCard(onAddSet = { onAddSet(null) })
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Spacer(Modifier.height(33.dp))
                            AddSetGhostCard(
                                onAddSet = { onAddSet(null) },
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            )
                    }
                }
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
    SessionEstimatedRings(
        energyDrain = (100 - energy).coerceIn(0, 100),
        spineDrain = (100 - spine).coerceIn(0, 100),
        muscleDrain = 0,
    )
}

/**
 * Estimated session drain rings (same channels as live workout rings).
 * Fill = predicted drain cost. Olympic-style zigzag overlap:
 * Músculos (left-up) → Energía (center-down) → Columna (right-up).
 */
@Composable
internal fun SessionEstimatedRings(
    energyDrain: Int,
    spineDrain: Int,
    muscleDrain: Int,
    modifier: Modifier = Modifier,
) {
    val muscleProgress = muscleDrain.coerceIn(0, 100) / 100f
    val energyProgress = energyDrain.coerceIn(0, 100) / 100f
    val spineProgress = spineDrain.coerceIn(0, 100) / 100f
    val muscleColor = com.example.kpkn.ui.theme.RingRed
    val energyColor = com.example.kpkn.ui.theme.RingBlue
    val spineColor = com.example.kpkn.ui.theme.RingYellow

    val muscleAnim by animateFloatAsState(muscleProgress, label = "muscleRing")
    val energyAnim by animateFloatAsState(energyProgress, label = "energyRing")
    val spineAnim by animateFloatAsState(spineProgress, label = "spineRing")

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val r = min(size.width / 5.4f, size.height * 0.36f)
                val cx = size.width / 2f
                val cy = size.height / 2f
                // Balanced spacing + zigzag stagger (Olympic)
                val dx = r * 1.28f
                val dy = r * 0.48f
                val centers = listOf(
                    Offset(cx - dx, cy - dy),  // Músculos (left-up)
                    Offset(cx, cy + dy),       // Energía (center-down)
                    Offset(cx + dx, cy - dy),  // Columna (right-up)
                )
                val colors = listOf(muscleColor, energyColor, spineColor)
                val progresses = listOf(muscleAnim, energyAnim, spineAnim)

                // Pass 1: visible track circles
                centers.forEachIndexed { i, c ->
                    drawCircle(
                        color = colors[i].copy(alpha = 0.28f),
                        radius = r,
                        center = c,
                        style = Stroke(width = stroke),
                    )
                }
                // Pass 2: soft glow + progress (menos capas = abrir Asistente más fluido)
                centers.forEachIndexed { i, c ->
                    val color = colors[i]
                    val sweep = 360f * progresses[i].coerceIn(0f, 1f)
                    val topLeft = Offset(c.x - r, c.y - r)
                    val arcSize = Size(r * 2, r * 2)
                    drawArc(
                        color = color.copy(alpha = 0.28f),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RingCaption("MÚSCULOS", muscleDrain.coerceIn(0, 100), muscleColor)
            RingCaption("ENERGÍA", energyDrain.coerceIn(0, 100), energyColor)
            RingCaption("COLUMNA", spineDrain.coerceIn(0, 100), spineColor)
        }
    }
}

@Composable
private fun RingCaption(label: String, pct: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color)
        Text("$pct%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
internal fun AddSetGhostCard(onAddSet: () -> Unit, modifier: Modifier = Modifier) {
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
            // Default suave: pausa corta entre lados si no había valor.
            restBetweenSidesSeconds = restBetweenSidesSeconds ?: 15,
            sets = sets.map { set ->
                val target = UnilateralTarget(
                    weight = set.weight,
                    targetReps = set.targetReps,
                    targetRepsRange = set.targetRepsRange,
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
                        targetRepsRange = source.targetRepsRange,
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
