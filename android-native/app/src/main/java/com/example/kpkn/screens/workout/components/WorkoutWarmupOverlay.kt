package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.kpknHazeEffect
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Full-screen DarkMica overlay for approximation (warm-up) sets in live sessions.
 * Features a circular progression ramp, prominent percentages, inline rest timers,
 * clean direct load input, effort rating, sticky bottom DarkMica actions,
 * and real-time auto-regulation via WarmupCalibrationEngine.
 */
data class WarmupExerciseGroup(
    val exercise: Exercise,
    val warmupSets: List<WorkoutWarmupDisplaySet>,
    val baseWorkingWeightKg: Double?,
)

data class FlattenedWarmupItem(
    val groupIndex: Int,
    val totalGroups: Int,
    val exercise: Exercise,
    val warmupIndex: Int,
    val totalWarmupsInGroup: Int,
    val warmup: WarmupSetDefinition,
    val warmupDisplaySet: WorkoutWarmupDisplaySet?,
    val baseWorkingWeightKg: Double?,
)

/**
 * Full-screen DarkMica overlay for approximation (warm-up) sets in live sessions.
 * Features a circular progression ramp, prominent percentages, inline rest timers,
 * clean direct load input, effort rating, sticky bottom DarkMica actions,
 * and real-time auto-regulation via WarmupCalibrationEngine.
 */
@Composable
fun WorkoutWarmupOverlay(
    exercise: Exercise? = null,
    warmupSets: List<WorkoutWarmupDisplaySet> = emptyList(),
    baseWorkingWeightKg: Double? = null,
    warmupGroups: List<WarmupExerciseGroup> = if (exercise != null) listOf(WarmupExerciseGroup(exercise, warmupSets, baseWorkingWeightKg)) else emptyList(),
    completedKeys: Set<String>,
    completedSets: Map<String, CompletedSet>,
    onToggleSet: (warmupSetId: String, completed: Boolean) -> Unit,
    onRecordWarmupWeight: (warmupSetId: String, weightKg: Double) -> Unit,
    onRecordWarmupHeaviness: (warmupSetId: String, effort: WarmupEffort) -> Unit,
    onToggleSetForExercise: ((exerciseId: String, warmupSetId: String, completed: Boolean) -> Unit)? = null,
    onRecordWarmupWeightForExercise: ((exerciseId: String, warmupSetId: String, weightKg: Double) -> Unit)? = null,
    onRecordWarmupHeavinessForExercise: ((exerciseId: String, warmupSetId: String, effort: WarmupEffort) -> Unit)? = null,
    onAddWarmupSet: () -> Unit = {},
    onSetTargetWorkingWeight: (Double) -> Unit = {},
    onSetTargetWorkingWeightForExercise: ((exerciseId: String, weight: Double) -> Unit)? = null,
    onClose: () -> Unit,
    onSkip: () -> Unit = onClose,
    onContinue: () -> Unit = onClose,
    hazeState: HazeState,
    sessionAccentColor: Color = Color(0xFFFFB300),
    embedded: Boolean = false,
) {
    val activeGroups = remember(warmupGroups, exercise, warmupSets, baseWorkingWeightKg) {
        if (warmupGroups.isNotEmpty()) warmupGroups else if (exercise != null) listOf(WarmupExerciseGroup(exercise, warmupSets, baseWorkingWeightKg)) else emptyList()
    }
    if (activeGroups.isEmpty()) return

    val scrollState = rememberScrollState()

    // Flatten all warmup sets across all groups in strict sequence:
    // First all warmups of Exercise 1, then all warmups of Exercise 2
    val allFlattenedItems = remember(activeGroups) {
        activeGroups.flatMapIndexed { gIdx, group ->
            group.exercise.warmupSets.mapIndexed { wIdx, warmup ->
                val displaySet = group.warmupSets.getOrNull(wIdx)
                FlattenedWarmupItem(
                    groupIndex = gIdx,
                    totalGroups = activeGroups.size,
                    exercise = group.exercise,
                    warmupIndex = wIdx,
                    totalWarmupsInGroup = group.exercise.warmupSets.size,
                    warmup = warmup,
                    warmupDisplaySet = displaySet,
                    baseWorkingWeightKg = group.baseWorkingWeightKg,
                )
            }
        }
    }

    val totalWarmups = allFlattenedItems.size

    val allDone = allFlattenedItems.isNotEmpty() && allFlattenedItems.all { item ->
        val key = "${item.exercise.id}_warmup_${item.warmup.id}"
        item.exercise.id in completedKeys || key in completedKeys
    }

    var activeItemIndex by remember(activeGroups.map { it.exercise.id }) {
        val firstUncompleted = allFlattenedItems.indexOfFirst { item ->
            val key = "${item.exercise.id}_warmup_${item.warmup.id}"
            key !in completedKeys && item.exercise.id !in completedKeys
        }
        mutableIntStateOf(if (firstUncompleted >= 0) firstUncompleted else 0)
    }

    val safeActiveIndex = activeItemIndex.coerceIn(0, (totalWarmups - 1).coerceAtLeast(0))
    val currentItem = allFlattenedItems.getOrNull(safeActiveIndex)

    var manualTargetWeight by remember(currentItem?.exercise?.id, currentItem?.baseWorkingWeightKg) {
        mutableStateOf(currentItem?.baseWorkingWeightKg?.takeIf { it > 0.0 })
    }

    val activeBaseLoad = manualTargetWeight ?: currentItem?.baseWorkingWeightKg

    // Active inline rest timer state
    var inlineRestActiveSetId by remember { mutableStateOf<String?>(null) }
    var inlineRestRemainingSeconds by remember { mutableStateOf(0) }
    var inlineRestTotalSeconds by remember { mutableStateOf(60) }
    var inlineRestIsPaused by remember { mutableStateOf(false) }

    LaunchedEffect(inlineRestActiveSetId, inlineRestIsPaused) {
        if (inlineRestActiveSetId != null && !inlineRestIsPaused) {
            while (inlineRestRemainingSeconds > 0) {
                delay(1000L)
                inlineRestRemainingSeconds -= 1
            }
            if (inlineRestRemainingSeconds <= 0) {
                inlineRestActiveSetId = null
            }
        }
    }

    @Composable
    fun WarmupBodyContent(contentModifier: Modifier) {
        Surface(
            modifier = contentModifier,
            shape = WorkoutUiTokens.CardShape,
            color = WorkoutUiTokens.setCardColor(),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ─── 1. Cabecera y Contador ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "SERIES DE APROXIMACIÓN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = sessionAccentColor,
                        letterSpacing = 1.1.sp,
                    )

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (allDone) sessionAccentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                    ) {
                        val completedCount = allFlattenedItems.count { item ->
                            val key = "${item.exercise.id}_warmup_${item.warmup.id}"
                            item.exercise.id in completedKeys || key in completedKeys
                        }
                        Text(
                            text = "$completedCount/$totalWarmups",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Black,
                            color = if (allDone) sessionAccentColor else Color.White.copy(alpha = 0.80f),
                        )
                    }
                }

                // ─── 2. Selector Guiado de Carga Efectiva si no existe ───
                val completedCount = allFlattenedItems.count { item ->
                    val key = "${item.exercise.id}_warmup_${item.warmup.id}"
                    item.exercise.id in completedKeys || key in completedKeys
                }
                if ((activeBaseLoad == null || activeBaseLoad <= 0.0) && completedCount == 0 && currentItem != null) {
                    TargetWeightGuidanceCard(
                        sessionAccentColor = sessionAccentColor,
                        onSetWeight = { weight ->
                            manualTargetWeight = weight
                            onSetTargetWorkingWeightForExercise?.invoke(currentItem.exercise.id, weight)
                                ?: onSetTargetWorkingWeight(weight)
                        },
                    )
                }

                // ─── 3. Stepper de Aproximaciones (Píldoras Compactas de Selección con División por Ejercicio) ───
                if (totalWarmups > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        activeGroups.forEachIndexed { gIdx, group ->
                            if (gIdx > 0) {
                                Box(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(1.5.dp)
                                        .background(Color.White.copy(alpha = 0.20f))
                                )
                            }
                            if (activeGroups.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                ) {
                                    Text(
                                        text = "Ej ${gIdx + 1}: ${group.exercise.name.take(12)}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.70f),
                                    )
                                }
                            }
                            group.exercise.warmupSets.forEachIndexed { wIdx, warmup ->
                                val flatIdx = allFlattenedItems.indexOfFirst {
                                    it.exercise.id == group.exercise.id && it.warmup.id == warmup.id
                                }
                                val key = "${group.exercise.id}_warmup_${warmup.id}"
                                val isCompleted = group.exercise.id in completedKeys || key in completedKeys
                                val isCurrent = flatIdx == safeActiveIndex
                                val pctText = formatWarmupPercent(warmup.percentageOfWorkingWeight)

                                Surface(
                                    onClick = { if (flatIdx >= 0) activeItemIndex = flatIdx },
                                    shape = RoundedCornerShape(999.dp),
                                    color = when {
                                        isCurrent -> sessionAccentColor.copy(alpha = 0.18f)
                                        isCompleted -> sessionAccentColor.copy(alpha = 0.42f)
                                        else -> Color.White.copy(alpha = 0.04f)
                                    },
                                    border = BorderStroke(
                                        width = if (isCurrent) 1.5.dp else 1.dp,
                                        color = when {
                                            isCurrent -> sessionAccentColor
                                            isCompleted -> sessionAccentColor
                                            else -> Color.White.copy(alpha = 0.10f)
                                        },
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = "A${wIdx + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                            fontWeight = if (isCurrent || isCompleted) FontWeight.Black else FontWeight.Bold,
                                            color = when {
                                                isCompleted -> sessionAccentColor
                                                isCurrent -> Color.White
                                                else -> Color.White.copy(alpha = 0.65f)
                                            },
                                        )
                                        Text(
                                            text = "· $pctText",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCurrent) sessionAccentColor else Color.White.copy(alpha = 0.45f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 4. Tarjeta Enfocada del Set Activo (Continuidad 1:1 con SetExecutionCard) ───
                if (currentItem != null) {
                    val key = "${currentItem.exercise.id}_warmup_${currentItem.warmup.id}"
                    val isSetDone = currentItem.exercise.id in completedKeys || key in completedKeys
                    val currentCompleted = completedSets[key] ?: completedSets[currentItem.exercise.id]
                    val loggedWeight = currentCompleted?.weight
                    val loggedRpe = currentCompleted?.rpe
                    val loggedEffort = loggedRpe?.let { r ->
                        when {
                            r <= 5.0 -> WarmupEffort.LIGHT
                            r >= 8.5 -> WarmupEffort.HEAVY
                            else -> WarmupEffort.NORMAL
                        }
                    }
                    val suggestedKg = currentItem.baseWorkingWeightKg?.let { base: Double ->
                        (base * currentItem.warmup.percentageOfWorkingWeight / 100.0).roundToStep(2.5)
                    }

                    val exerciseBadgeText = if (activeGroups.size > 1) {
                        "EJERCICIO ${currentItem.groupIndex + 1} DE ${activeGroups.size} · ${currentItem.exercise.name.uppercase()}"
                    } else null

                    WarmupSetDetailedCard(
                        index = currentItem.warmupIndex,
                        warmup = currentItem.warmup,
                        exerciseBadge = exerciseBadgeText,
                        suggestedWeightKg = suggestedKg,
                        actualWeightKg = loggedWeight,
                        isCompleted = isSetDone,
                        currentEffort = loggedEffort,
                        sessionAccentColor = sessionAccentColor,
                        isInlineRestActive = inlineRestActiveSetId == currentItem.warmup.id,
                        inlineRestRemainingSeconds = inlineRestRemainingSeconds,
                        inlineRestTotalSeconds = inlineRestTotalSeconds,
                        inlineRestIsPaused = inlineRestIsPaused,
                        onTogglePauseInlineRest = { inlineRestIsPaused = !inlineRestIsPaused },
                        onSkipInlineRest = { inlineRestActiveSetId = null },
                        onWeightChanged = { weight ->
                            onRecordWarmupWeightForExercise?.invoke(currentItem.exercise.id, currentItem.warmup.id, weight)
                                ?: onRecordWarmupWeight(currentItem.warmup.id, weight)
                        },
                        onEffortSelected = { effort ->
                            onRecordWarmupHeavinessForExercise?.invoke(currentItem.exercise.id, currentItem.warmup.id, effort)
                                ?: onRecordWarmupHeaviness(currentItem.warmup.id, effort)
                        },
                        onRegisterSet = { weight, effort ->
                            onRecordWarmupWeightForExercise?.invoke(currentItem.exercise.id, currentItem.warmup.id, weight)
                                ?: onRecordWarmupWeight(currentItem.warmup.id, weight)
                            onRecordWarmupHeavinessForExercise?.invoke(currentItem.exercise.id, currentItem.warmup.id, effort)
                                ?: onRecordWarmupHeaviness(currentItem.warmup.id, effort)
                            onToggleSetForExercise?.invoke(currentItem.exercise.id, currentItem.warmup.id, true)
                                ?: onToggleSet(currentItem.warmup.id, true)

                            inlineRestActiveSetId = currentItem.warmup.id
                            inlineRestTotalSeconds = (currentItem.warmup.restBetween ?: 60).coerceAtLeast(30)
                            inlineRestRemainingSeconds = inlineRestTotalSeconds
                            inlineRestIsPaused = false

                            if (safeActiveIndex < allFlattenedItems.lastIndex) {
                                activeItemIndex = safeActiveIndex + 1
                            } else {
                                onContinue()
                            }
                        },
                    )
                }

                // ─── 5. Botones de Acción (Integrados en la tarjeta) ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B232E).copy(alpha = 0.90f),
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    ) {
                        Text(
                            "Saltar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Button(
                        onClick = onContinue,
                        enabled = allDone || totalWarmups == 0,
                        modifier = Modifier.weight(1.4f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = sessionAccentColor,
                            contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                            disabledContainerColor = Color(0xFF151B24).copy(alpha = 0.85f),
                            disabledContentColor = Color.White.copy(alpha = 0.38f),
                        ),
                        border = if (!allDone && totalWarmups > 0) BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                    ) {
                        Text(
                            if (allDone || totalWarmups == 0) "Comenzar 1ª serie" else "Completa las series",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }

    if (embedded) {
        WarmupBodyContent(
            contentModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 8.dp, bottom = 16.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(6f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .kpknHazeEffect(hazeState),
            )
            WarmupBodyContent(
                contentModifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                    .padding(top = 20.dp, bottom = 110.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .kpknGlass(hazeState, WorkoutUiTokens.DockShape)
                    .navigationBarsPadding()
                    .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                    .padding(top = 16.dp, bottom = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B232E).copy(alpha = 0.90f),
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    ) {
                        Text(
                            "Saltar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Button(
                        onClick = onContinue,
                        enabled = allDone,
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = sessionAccentColor,
                            contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                            disabledContainerColor = Color(0xFF151B24).copy(alpha = 0.85f),
                            disabledContentColor = Color.White.copy(alpha = 0.38f),
                        ),
                        border = if (!allDone) BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                    ) {
                        Text(
                            if (allDone) "Comenzar 1ª serie" else "Completa las series",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detailed card for one warm-up set with prominent percentage, direct load input,
 * effort selector and "Registrar aproximación" button.
 */
@Composable
private fun WarmupSetDetailedCard(
    index: Int,
    warmup: WarmupSetDefinition,
    exerciseBadge: String? = null,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    isCompleted: Boolean,
    currentEffort: WarmupEffort?,
    sessionAccentColor: Color,
    onWeightChanged: (Double) -> Unit,
    onEffortSelected: (WarmupEffort) -> Unit,
    onRegisterSet: (weight: Double, effort: WarmupEffort) -> Unit,
    isInlineRestActive: Boolean,
    inlineRestRemainingSeconds: Int,
    inlineRestTotalSeconds: Int,
    inlineRestIsPaused: Boolean,
    onTogglePauseInlineRest: () -> Unit,
    onSkipInlineRest: () -> Unit,
) {
    var textValue by remember(warmup.id, actualWeightKg, suggestedWeightKg) {
        val initial = actualWeightKg ?: suggestedWeightKg
        mutableStateOf(initial?.toTrimmedNumberString() ?: "")
    }

    var selectedEffort by remember(warmup.id, currentEffort) {
        mutableStateOf(currentEffort ?: WarmupEffort.NORMAL)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isCompleted) sessionAccentColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isCompleted) sessionAccentColor.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header del Set con Porcentaje Prominente
            if (exerciseBadge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        text = exerciseBadge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        fontWeight = FontWeight.Black,
                        color = sessionAccentColor,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isCompleted) sessionAccentColor.copy(alpha = 0.18f) else sessionAccentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "A${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isCompleted) sessionAccentColor else sessionAccentColor,
                            )
                        }
                    }

                    Text(
                        // Keep the legacy accent-free label used by saved/automated flows.
                        "Aproximacion ${index + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                // Porcentaje Prominente + Reps
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sessionAccentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            formatWarmupPercent(warmup.percentageOfWorkingWeight),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = sessionAccentColor,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.06f),
                    ) {
                        Text(
                            "${warmup.targetReps} reps",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Input Directo de Carga con Steppers y Chips Rápidos
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Carga utilizada:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.height(42.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Surface(
                                onClick = {
                                    val current = textValue.toDoubleOrNull() ?: suggestedWeightKg ?: 0.0
                                    val next = (current - 2.5).coerceAtLeast(0.0)
                                    textValue = next.toTrimmedNumberString()
                                    onWeightChanged(next)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 4.dp),
                            ) {
                                BasicTextField(
                                    value = textValue,
                                    onValueChange = { input ->
                                        textValue = input
                                        input.toDoubleOrNull()?.let { onWeightChanged(it) }
                                    },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    cursorBrush = SolidColor(sessionAccentColor),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done,
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.widthIn(min = 36.dp),
                                )
                                Text(
                                    "kg",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.60f),
                                )
                            }

                            Surface(
                                onClick = {
                                    val current = textValue.toDoubleOrNull() ?: suggestedWeightKg ?: 0.0
                                    val next = current + 2.5
                                    textValue = next.toTrimmedNumberString()
                                    onWeightChanged(next)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "Más", tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Quick load adjustment chips
                if (suggestedWeightKg != null && suggestedWeightKg > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            onClick = {
                                textValue = suggestedWeightKg.toTrimmedNumberString()
                                onWeightChanged(suggestedWeightKg)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = sessionAccentColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.30f)),
                            modifier = Modifier.weight(1.3f).height(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "Sugerido: ${suggestedWeightKg.toTrimmedNumberString()} kg",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = sessionAccentColor,
                                )
                            }
                        }

                        listOf(-2.5, 2.5, 5.0).forEach { delta ->
                            val label = if (delta > 0) "+${delta.toTrimmedNumberString()}" else delta.toTrimmedNumberString()
                            Surface(
                                onClick = {
                                    val current = textValue.toDoubleOrNull() ?: suggestedWeightKg
                                    val next = (current + delta).coerceAtLeast(0.0)
                                    textValue = next.toTrimmedNumberString()
                                    onWeightChanged(next)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                modifier = Modifier.weight(1f).height(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selector de Percepción / Esfuerzo de Carga
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "¿Cómo sentiste el movimiento?",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.65f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val efforts = listOf(
                        Triple(WarmupEffort.LIGHT, "Ligero", Color(0xFF66BB6A)),
                        Triple(WarmupEffort.NORMAL, "Moderado", Color(0xFF448AFF)),
                        Triple(WarmupEffort.HEAVY, "Pesado", Color(0xFFFF5252)),
                    )

                    efforts.forEach { (effort, label, accent) ->
                        val isSelected = selectedEffort == effort
                        Surface(
                            onClick = {
                                selectedEffort = effort
                                onEffortSelected(effort)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                            ),
                            modifier = Modifier.weight(1f).height(36.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }

            // Botón "Registrar aproximación"
            Button(
                onClick = {
                    val finalWeight = textValue.toDoubleOrNull() ?: suggestedWeightKg ?: 0.0
                    onRegisterSet(finalWeight, selectedEffort)
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = sessionAccentColor,
                    contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                ),
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isCompleted) "Aproximación registrada" else "Registrar aproximación",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                )
            }

            // Descanso Inline si está activo para esta aproximación
            if (isInlineRestActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = sessionAccentColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Descanso: %02d:%02d".format(inlineRestRemainingSeconds / 60, inlineRestRemainingSeconds % 60),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = onTogglePauseInlineRest,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.80f)),
                            ) {
                                Text(if (inlineRestIsPaused) "Reanudar" else "Pausar", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = onSkipInlineRest,
                                colors = ButtonDefaults.textButtonColors(contentColor = sessionAccentColor),
                            ) {
                                Text("Saltar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Guidance card to pick/set the target effective working weight when no baseline is available.
 */
@Composable
private fun TargetWeightGuidanceCard(
    sessionAccentColor: Color,
    onSetWeight: (Double) -> Unit,
) {
    var customText by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = sessionAccentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = sessionAccentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Define la Carga de tu 1ª Serie Efectiva",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }

            Text(
                "Ingresa el peso objetivo de tu primera serie para calcular automáticamente la rampa de aproximaciones.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f),
            )

            // Preset Chips rápidos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(20.0, 40.0, 60.0, 80.0, 100.0, 120.0).forEach { weight ->
                    Surface(
                        onClick = { onSetWeight(weight) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text(
                            "${weight.toTrimmedNumberString()} kg",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            // Input manual libre
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f).height(38.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    ) {
                        if (customText.isEmpty()) {
                            Text(
                                "Otro peso (ej. 75)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.40f),
                            )
                        }
                        BasicTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            cursorBrush = SolidColor(sessionAccentColor),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Button(
                    onClick = { customText.toDoubleOrNull()?.let { onSetWeight(it) } },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text("Aplicar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun formatWarmupPercent(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()}%"
}

private fun Double.roundToStep(step: Double): Double = (this / step).roundToInt() * step
