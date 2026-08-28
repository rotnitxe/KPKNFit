package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.sessioneditor.contentOn
import com.example.kpkn.screens.workout.toTrimmedNumberString
import kotlin.math.roundToInt

/**
 * One mobility page in the set carousel: a single set-sized card with a flat checklist.
 */
@Composable
internal fun MobilityPhaseLiveCard(
    items: List<WorkoutMobilityChecklistItem>,
    completedStepKeys: Set<String>,
    remainingSeconds: Int,
    totalMinutes: Int,
    isTimerRunning: Boolean,
    sessionAccentColor: Color,
    onToggleComplete: (item: WorkoutMobilityChecklistItem, completed: Boolean) -> Unit,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onAddTimerSeconds: (Int) -> Unit,
    onResetTimer: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    inlineRestRemainingSeconds: Int? = null,
    inlineRestTotalSeconds: Int? = null,
    onSkipInlineRest: (() -> Unit)? = null,
) {
    val allDone = items.isNotEmpty() && items.all { it.stepKey in completedStepKeys }
    val showExerciseBadge = items.map { it.exerciseId }.distinct().size > 1
    val showInlineRest = inlineRestRemainingSeconds != null && (inlineRestTotalSeconds ?: 0) > 0
    PrepChecklistShell(
        title = "MOVILIDAD",
        doneCount = items.count { it.stepKey in completedStepKeys },
        totalCount = items.size,
        sessionAccentColor = sessionAccentColor,
        modifier = modifier,
        headerExtra = {
            CompactMobilityTimer(
                remainingSeconds = remainingSeconds,
                totalMinutes = totalMinutes,
                isRunning = isTimerRunning,
                sessionAccentColor = sessionAccentColor,
                onStart = onStartTimer,
                onPause = onPauseTimer,
                onAddSeconds = onAddTimerSeconds,
                onReset = onResetTimer,
            )
        },
        footer = {
            if (showInlineRest) {
                PrepInlineRestBar(
                    remainingSeconds = inlineRestRemainingSeconds!!.coerceAtLeast(0),
                    totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                    sessionAccentColor = sessionAccentColor,
                    onSkip = onSkipInlineRest ?: {},
                )
            } else {
                PrepPhaseActionBar(
                    allDone = allDone || items.isEmpty(),
                    continueLabel = "Continuar",
                    sessionAccentColor = sessionAccentColor,
                    onSkip = onSkip,
                    onContinue = onContinue,
                )
            }
        },
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
            val mob = item.mobility
            val subtitle = buildList {
                if (showExerciseBadge && item.exerciseName.isNotBlank()) add(item.exerciseName)
                mob.reps?.takeIf { it.isNotBlank() }?.let { add("$it reps") }
                mob.durationSeconds?.takeIf { it > 0 }?.let { add("${it}s") }
            }.joinToString(" · ")
            ChecklistLine(
                title = mob.name,
                subtitle = subtitle,
                isCompleted = item.stepKey in completedStepKeys,
                accent = sessionAccentColor,
                onToggle = { onToggleComplete(item, it) },
            )
        }
    }
}

/**
 * One approximation page: checklist with compact kg inputs + checks.
 */
@Composable
internal fun WarmupPhaseLiveCard(
    rows: List<WarmupPhaseRow>,
    sessionAccentColor: Color,
    onToggleComplete: (row: WarmupPhaseRow, completed: Boolean, weightKg: Double) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onAddSet: (() -> Unit)? = null,
    inlineRestRemainingSeconds: Int? = null,
    inlineRestTotalSeconds: Int? = null,
    onSkipInlineRest: (() -> Unit)? = null,
) {
    val allDone = rows.isNotEmpty() && rows.all { it.isCompleted }
    val showInlineRest = inlineRestRemainingSeconds != null && (inlineRestTotalSeconds ?: 0) > 0
    PrepChecklistShell(
        title = "APROXIMACIÓN",
        doneCount = rows.count { it.isCompleted },
        totalCount = rows.size,
        sessionAccentColor = sessionAccentColor,
        modifier = modifier,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showInlineRest) {
                    PrepInlineRestBar(
                        remainingSeconds = inlineRestRemainingSeconds!!.coerceAtLeast(0),
                        totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                        sessionAccentColor = sessionAccentColor,
                        onSkip = onSkipInlineRest ?: {},
                    )
                } else {
                    if (onAddSet != null) {
                        OutlinedButton(
                            onClick = onAddSet,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = sessionAccentColor,
                            ),
                        ) {
                            Text(
                                text = "Agregar serie",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    PrepPhaseActionBar(
                        allDone = allDone || rows.isEmpty(),
                        continueLabel = "Comenzar 1ª serie",
                        sessionAccentColor = sessionAccentColor,
                        onSkip = onSkip,
                        onContinue = onContinue,
                    )
                }
            }
        },
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
            val pct = formatLiveWarmupPercent(row.warmup.percentageOfWorkingWeight)
            val subtitle = buildList {
                row.exerciseBadge?.takeIf { it.isNotBlank() }?.let { add(it) }
                add(pct)
                add("${row.warmup.targetReps} reps")
            }.joinToString(" · ")
            WarmupChecklistLine(
                title = "Aprox ${row.index + 1}",
                subtitle = subtitle,
                suggestedWeightKg = row.suggestedWeightKg,
                actualWeightKg = row.actualWeightKg,
                isCompleted = row.isCompleted,
                accent = sessionAccentColor,
                onToggle = { completed, kg -> onToggleComplete(row, completed, kg) },
            )
        }
    }
}

internal data class WarmupPhaseRow(
    val exerciseId: String,
    val exerciseBadge: String?,
    val index: Int,
    val warmup: WarmupSetDefinition,
    val suggestedWeightKg: Double?,
    val actualWeightKg: Double?,
    val isCompleted: Boolean,
)

/**
 * Transparent sizing slot for every live pager page.
 *
 * The normal set card deliberately has no outer gray Surface: only its
 * Planificado/Reportar inner sections provide containers. Prep cards add one
 * undivided Surface inside this slot, while the slot keeps every page aligned.
 * The inner frame scales the previous base bounds uniformly so all card
 * content grows without spilling into the neighboring pager page. Normal
 * set cards can opt into measured height expansion so plan/technique panels
 * are laid out below the card instead of being hidden by the fixed slot.
 */
@Composable
internal fun LivePagerCardFrame(
    modifier: Modifier = Modifier,
    allowContentExpansion: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scale = WorkoutUiTokens.effectiveLivePagerCardScale()
    val slotHeight = WorkoutUiTokens.effectiveLivePagerSlotHeight()
    if (!allowContentExpansion) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(slotHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f / scale)
                    .height(WorkoutUiTokens.LivePagerBaseHeight)
                    .scale(scale),
                content = content,
            )
        }
        return
    }

    SubcomposeLayout(modifier = modifier.fillMaxWidth().wrapContentHeight()) { constraints ->
        val baseHeightPx = WorkoutUiTokens.LivePagerBaseHeight.roundToPx()
        val slotHeightPx = slotHeight.roundToPx()
        val maxChildWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth / scale).roundToInt().coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val minChildWidth = (constraints.minWidth / scale).roundToInt().coerceAtLeast(0)
        val placeable = subcompose("expandable_live_pager_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(min = WorkoutUiTokens.LivePagerBaseHeight),
                contentAlignment = Alignment.TopCenter,
                content = content,
            )
        }.first().measure(
            Constraints(
                minWidth = minChildWidth.coerceAtMost(maxChildWidth),
                maxWidth = maxChildWidth,
                minHeight = baseHeightPx,
                maxHeight = Constraints.Infinity,
            ),
        )
        val scaledWidth = (placeable.width * scale).roundToInt()
        val width = scaledWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = (placeable.height * scale).roundToInt().coerceAtLeast(slotHeightPx)

        layout(width, layoutHeight) {
            placeable.placeRelativeWithLayer(
                x = ((width - scaledWidth) / 2f).roundToInt().coerceAtLeast(0),
                y = 0,
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun PrepChecklistShell(
    title: String,
    doneCount: Int,
    totalCount: Int,
    sessionAccentColor: Color,
    modifier: Modifier = Modifier,
    headerExtra: (@Composable () -> Unit)? = null,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = WorkoutUiTokens.CardShape,
        color = WorkoutUiTokens.setCardColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = sessionAccentColor,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "$doneCount/$totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                headerExtra?.invoke()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
            footer()
        }
    }
}

@Composable
private fun ChecklistLine(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    accent: Color,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF38BDF8),
                uncheckedColor = Color.White.copy(alpha = 0.30f),
                checkmarkColor = Color.Black,
            ),
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) Color(0xFF38BDF8) else Color.White,
                maxLines = 2,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WarmupChecklistLine(
    title: String,
    subtitle: String,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    isCompleted: Boolean,
    accent: Color,
    onToggle: (completed: Boolean, weightKg: Double) -> Unit,
) {
    var textValue by remember(title, actualWeightKg, suggestedWeightKg) {
        val initial = actualWeightKg ?: suggestedWeightKg
        mutableStateOf(initial?.toTrimmedNumberString() ?: "")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { checked ->
                val kg = textValue.toDoubleOrNull()
                    ?: suggestedWeightKg
                    ?: 0.0
                onToggle(checked, kg)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF38BDF8),
                uncheckedColor = Color.White.copy(alpha = 0.30f),
                checkmarkColor = Color.Black,
            ),
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) Color(0xFF38BDF8) else Color.White,
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier.height(34.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    modifier = Modifier.widthIn(min = 36.dp, max = 56.dp),
                )
                Text("kg", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun CompactMobilityTimer(
    remainingSeconds: Int,
    totalMinutes: Int,
    isRunning: Boolean,
    sessionAccentColor: Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onAddSeconds: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Timer, null, tint = sessionAccentColor, modifier = Modifier.size(14.dp))
            Text(
                "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (remainingSeconds in 1..10) Color(0xFFFF5252) else Color.White,
            )
            Text(
                "/ ${totalMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.40f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (isRunning) onPause() else onStart() }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = sessionAccentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            TinyChip("+30s") { onAddSeconds(30) }
            TinyChip("Reset", onReset)
        }
    }
}

@Composable
private fun TinyChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.75f),
    )
}

/**
 * Inline prep rest countdown on MOV/APR cards (RestTimerKind.WARMUP).
 */
@Composable
private fun PrepInlineRestBar(
    remainingSeconds: Int,
    totalSeconds: Int,
    sessionAccentColor: Color,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Descanso %02d:%02d".format(mins, secs),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Button(
                onClick = onSkip,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = sessionAccentColor,
                    contentColor = contentOn(sessionAccentColor),
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text("Saltar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(sessionAccentColor.copy(alpha = 0.85f)),
            )
        }
    }
}

@Composable
internal fun PrepPhaseActionBar(
    allDone: Boolean,
    continueLabel: String,
    sessionAccentColor: Color,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSkip,
            modifier = Modifier.weight(1f).height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1B232E).copy(alpha = 0.90f),
                contentColor = Color.White,
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            Text("Omitir", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onContinue,
            enabled = allDone,
            modifier = Modifier.weight(1.35f).height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = sessionAccentColor,
                contentColor = contentOn(sessionAccentColor),
                disabledContainerColor = Color(0xFF151B24).copy(alpha = 0.85f),
                disabledContentColor = Color.White.copy(alpha = 0.38f),
            ),
        ) {
            Text(
                if (allDone) continueLabel else "Completa los pasos",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

private fun formatLiveWarmupPercent(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()}%"
}
