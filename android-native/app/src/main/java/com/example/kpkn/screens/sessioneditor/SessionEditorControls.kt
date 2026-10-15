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
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.TowerRingValues
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
import com.example.kpkn.screens.sessioneditor.components.ExerciseSetList
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
            .height(48.dp)
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
            .height(48.dp)
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
            .height(48.dp)
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
            .height(48.dp)
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
                .height(48.dp)
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
            .height(48.dp)
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

/**
 * Editor de series del ejercicio (ER1c1): la lista vertical de [ExerciseSetList]. Conserva su nombre y su firma porque la usa también la
 * sesión en vivo («Editar series»), que no pasa acciones a nivel de ejercicio (allí no aparece «Duplicar»).
 */
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
    ExerciseSetList(
        exercise = exercise,
        reference1RM = reference1RM,
        trainingMode = trainingMode,
        customUnit = customUnit,
        predictedMetrics = predictedMetrics,
        accentColor = accentColor,
        backgroundAccentColor = backgroundAccentColor,
        modifier = modifier,
        onAddSet = onAddSet,
        onUpdateSet = onUpdateSet,
        onRemoveSet = onRemoveSet,
        onMoveSet = onMoveSet,
    )
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
 * Drenaje estimado de la sesión en la torre de tres anillos del logo (mismos canales que los anillos en vivo).
 * El relleno es el coste previsto de cada canal; sin animación, porque cambia en cada edición de la sesión.
 */
@Composable
internal fun SessionEstimatedRings(
    energyDrain: Int,
    spineDrain: Int,
    muscleDrain: Int,
    modifier: Modifier = Modifier,
) {
    val muscleColor = KpknRingColors.musculos
    val energyColor = KpknRingColors.energia
    val spineColor = KpknRingColors.columna

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
            KpknTowerRings(
                values = TowerRingValues(
                    columna = spineDrain.coerceIn(0, 100),
                    musculos = muscleDrain.coerceIn(0, 100),
                    energia = energyDrain.coerceIn(0, 100),
                ),
                modifier = Modifier.fillMaxSize(),
                animate = false,
            )
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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                focusManager.clearFocus(force = true)
                onAddSet()
            },
        color = DarkEditorSurfaceSoft,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
