package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RecordFabHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.kpkn.screens.workout.inlineRestRemainingOrNull
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import java.util.Locale
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
    recordActionHolder: RecordActionHolder? = null,
    recordActionScopeOwner: Any? = null,
    recordActionPageKey: String? = null,
    recordFabHolder: RecordFabHolder? = null,
    isActivePage: Boolean = false,
) {
    val showExerciseBadge = items.map { it.exerciseId }.distinct().size > 1
    val recordActionInstanceOwner = remember { Any() }
    SideEffect {
        val actionPageKey = recordActionPageKey?.takeIf { it.isNotBlank() }
        val stepKey = items.firstOrNull { it.stepKey !in completedStepKeys }?.stepKey
            ?: items.firstOrNull()?.stepKey.orEmpty()
        if (
            isActivePage &&
            recordActionHolder != null &&
            recordActionScopeOwner != null &&
            actionPageKey != null &&
            stepKey.isNotBlank()
        ) {
            recordActionHolder.bind(
                scopeOwner = recordActionScopeOwner,
                instanceOwner = recordActionInstanceOwner,
                stepKey = stepKey,
                pageKey = actionPageKey,
                action = {
                val item = items.firstOrNull { it.stepKey !in completedStepKeys }
                runPrepLiveCardFabAction(
                    hasIncomplete = item != null,
                    completeNext = { item?.let { onToggleComplete(it, true) } },
                    advance = onContinue,
                )
                },
            )
            recordFabHolder?.isUpdateMode = false
        } else {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
        }
    }
    DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
        onDispose { recordActionHolder?.clearIfOwner(recordActionInstanceOwner) }
    }
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
            val remaining = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
            if (remaining != null) {
                PrepInlineRestBar(
                    remainingSeconds = remaining.coerceAtLeast(0),
                    totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                    sessionAccentColor = sessionAccentColor,
                    onSkip = onSkipInlineRest ?: {},
                )
            } else {
                PrepPhaseSkipBar(onSkip = onSkip)
            }
        },
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = KpknSurface.divider)
            }
            val mob = item.mobility
            val subtitle = buildList {
                if (showExerciseBadge && item.exerciseName.isNotBlank()) add(item.exerciseName)
                mob.reps?.takeIf { it.isNotBlank() }?.let { add("$it reps") }
                mob.durationSeconds?.takeIf { it > 0 }?.let { add("${it} s") }
            }.joinToString(" · ")
            ChecklistLine(
                title = mob.name,
                subtitle = subtitle,
                isCompleted = item.stepKey in completedStepKeys,
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
    recordActionHolder: RecordActionHolder? = null,
    recordActionScopeOwner: Any? = null,
    recordActionPageKey: String? = null,
    recordFabHolder: RecordFabHolder? = null,
    isActivePage: Boolean = false,
    onWeightDraft: ((row: WarmupPhaseRow, text: String) -> Unit)? = null,
) {
    val recordActionInstanceOwner = remember { Any() }
    SideEffect {
        val actionPageKey = recordActionPageKey?.takeIf { it.isNotBlank() }
        val stepKey = rows.firstOrNull { !it.isCompleted }?.let {
            WorkoutStepRules.warmupStepKey(it.exerciseId, it.warmup.id)
        } ?: rows.firstOrNull()?.let {
            WorkoutStepRules.warmupStepKey(it.exerciseId, it.warmup.id)
        }.orEmpty()
        if (
            isActivePage &&
            recordActionHolder != null &&
            recordActionScopeOwner != null &&
            actionPageKey != null &&
            stepKey.isNotBlank()
        ) {
            recordActionHolder.bind(
                scopeOwner = recordActionScopeOwner,
                instanceOwner = recordActionInstanceOwner,
                stepKey = stepKey,
                pageKey = actionPageKey,
                action = {
                val row = rows.firstOrNull { !it.isCompleted }
                runPrepLiveCardFabAction(
                    hasIncomplete = row != null,
                    completeNext = {
                        row?.let {
                            val kg = it.actualWeightKg ?: it.suggestedWeightKg ?: 0.0
                            onToggleComplete(it, true, kg)
                        }
                    },
                    advance = onContinue,
                )
                },
            )
            recordFabHolder?.isUpdateMode = false
        } else {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
        }
    }
    DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
        onDispose { recordActionHolder?.clearIfOwner(recordActionInstanceOwner) }
    }
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
                val remaining = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
                if (remaining != null) {
                    PrepInlineRestBar(
                        remainingSeconds = remaining.coerceAtLeast(0),
                        totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                        sessionAccentColor = sessionAccentColor,
                        onSkip = onSkipInlineRest ?: {},
                    )
                } else {
                    if (onAddSet != null) {
                        KpknSecondaryPill(
                            text = "Agregar serie",
                            onClick = onAddSet,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PrepPhaseSkipBar(onSkip = onSkip)
                }
            }
        },
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = KpknSurface.divider)
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
                onWeightDraft = onWeightDraft?.let { callback -> { text -> callback(row, text) } },
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
    lockToWorkingSetHeight: Boolean = false,
    publishWorkingSetHeight: Boolean = false,
    godModeActive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (godModeActive) {
        val scale = WorkoutUiTokens.GodModeCardScale
        val baseHeight = WorkoutUiTokens.LivePagerNormalExpandedBaseHeight
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val pageWidth = maxWidth
            Box(
                modifier = Modifier
                    .width(pageWidth * scale)
                    .height(baseHeight * scale),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredWidth(pageWidth)
                        .requiredHeight(baseHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin.Center
                        },
                    content = content,
                )
            }
        }
        return
    }

    val scale = WorkoutUiTokens.effectiveLivePagerCardScale()
    val policy = LocalLiveViewportPolicy.current
    val scaleX = LiveViewportPolicyMath.livePagerScaleX(
        scale * WorkoutUiTokens.LivePagerCardWidthSlimFactor,
        policy.cardPageFillX,
    )
    val scaleY = scale * WorkoutUiTokens.LivePagerCardHeightGrowFactor
    val slotHeight = WorkoutUiTokens.effectiveLivePagerStableHeight()
    val baseHeight = WorkoutUiTokens.LivePagerNormalExpandedBaseHeight
    val density = LocalDensity.current
    // Banda del relator (LR1g): la tarjeta empieza justo debajo y el margen sale de la misma fórmula que la banda.
    val relatorBand = WorkoutUiTokens.liveRelatorBandHeight(density)
    val topNudgeFraction = policy.topNudgeFraction
    val topNudge = slotHeight * topNudgeFraction + WorkoutUiTokens.LivePagerRelatorClearance
    if (!allowContentExpansion) {
        val widthFraction = if (scaleX >= 1f) (1f / scaleX).coerceIn(0.01f, 1f) else 1f
        // El borde inferior de la tarjeta se queda donde estaba: la tarjeta crece por arriba lo que ahorra la banda.
        val cardBottom = topNudge + baseHeight * scaleY
        val cardScaleY = ((cardBottom - relatorBand) / baseHeight).coerceAtLeast(0.01f)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(slotHeight + topNudge),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = relatorBand)
                    .fillMaxWidth(widthFraction)
                    .height(baseHeight)
                    .graphicsLayer {
                        this.scaleX = scaleX
                        this.scaleY = cardScaleY
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                content = content,
            )
        }
        return
    }

    val workingSetHeightHolder = LocalLivePagerWorkingSetVisualHeightPx.current
    val workingSetHeightPx = workingSetHeightHolder?.intValue ?: 0
    val lockHeightPx = if (lockToWorkingSetHeight) workingSetHeightPx else 0
    SubcomposeLayout(modifier = modifier.fillMaxWidth().wrapContentHeight()) { constraints ->
        val maxChildWidth = if (constraints.hasBoundedWidth) {
            if (scaleX >= 1f) {
                (constraints.maxWidth / scaleX).roundToInt().coerceAtLeast(0)
            } else {
                constraints.maxWidth
            }
        } else {
            Constraints.Infinity
        }
        val minChildWidth = if (scaleX >= 1f) {
            (constraints.minWidth / scaleX).roundToInt().coerceAtLeast(0)
        } else {
            0
        }
        val childMaxHeight = if (lockHeightPx > 0) {
            lockHeightPx
        } else {
            Constraints.Infinity
        }
        val childMinHeight = if (lockHeightPx > 0) lockHeightPx else 0
        val placeable = subcompose("expandable_live_pager_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (lockHeightPx > 0) {
                            Modifier
                                .height(with(density) { lockHeightPx.toDp() })
                                .clipToBounds()
                        } else {
                            Modifier.wrapContentHeight()
                        },
                    )
                    .then(
                        if (publishWorkingSetHeight) {
                            Modifier.onSizeChanged { size ->
                                publishLivePagerWorkingSetVisualHeight(
                                    workingSetHeightHolder,
                                    size.height,
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.TopCenter,
                content = content,
            )
        }.first().measure(
            Constraints(
                minWidth = minChildWidth.coerceAtMost(maxChildWidth),
                maxWidth = maxChildWidth,
                minHeight = childMinHeight,
                maxHeight = childMaxHeight,
            ),
        )
        val scaledWidth = (placeable.width * scaleX).roundToInt()
        val layoutWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            scaledWidth
        }
        val unscaledHeight = if (lockHeightPx > 0) lockHeightPx else placeable.height
        val scaledHeight = (unscaledHeight * scaleY).roundToInt()
        // Borde inferior de siempre (no se mueve): la tarjeta empieza bajo la banda del relator y crece por arriba
        // lo que ahorra (LR1g). Así el ✓ y el cajón quedan igual que antes, y el hueco es el mismo en serie y descanso.
        val topNudgePx = (scaledHeight * topNudgeFraction).roundToInt() +
            WorkoutUiTokens.LivePagerRelatorClearance.roundToPx()
        val footPx = scaledHeight + topNudgePx
        val bandPx = relatorBand.roundToPx()
        val cardPx = (footPx - bandPx).coerceAtLeast(1)
        val cardScaleY = if (unscaledHeight > 0) cardPx.toFloat() / unscaledHeight else scaleY
        val layoutHeight = footPx

        layout(layoutWidth, layoutHeight) {
            placeable.placeRelativeWithLayer(
                x = ((layoutWidth - scaledWidth) / 2f).roundToInt(),
                y = bandPx,
            ) {
                this.scaleX = scaleX
                this.scaleY = cardScaleY
                transformOrigin = TransformOrigin(0f, 0f)
                clip = lockToWorkingSetHeight
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
    val bodyScrollState = rememberScrollState()
    // Tarjeta de la página con la piel nueva (LR1b): módulo y su filete, como la tarjeta de la serie (LR1a).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(KpknSurface.module, KpknShape.module)
            .border(1.dp, KpknSurface.moduleBorder, KpknShape.module),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        title,
                        style = KpknType.eyebrow,
                        color = WizardColors.textMuted,
                    )
                    Text(
                        "$doneCount/$totalCount",
                        style = KpknType.chip.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                    )
                }
                headerExtra?.invoke()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .verticalScroll(bodyScrollState),
            ) {
                content()
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
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PrepCheckBox(checked = isCompleted, onCheckedChange = onToggle)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = KpknType.bodyStrong,
                color = if (isCompleted) WizardColors.textMuted else WizardColors.text,
                maxLines = 2,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Marca de hecho de la aproximación y la movilidad (LR1b): cuadro de 22 dp con anillo de 1,75 dp. Hecho, relleno de `ok` con el check
 * en negro. Solo la marca responde al toque, en una zona de 48 dp, como la casilla de antes.
 */
@Composable
private fun PrepCheckBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val corner = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(corner)
                .background(if (checked) WizardColors.done else Color.Transparent, corner)
                .border(1.75.dp, if (checked) WizardColors.done else WizardColors.textFaint, corner),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = WizardColors.ctaContent,
                )
            }
        }
    }
}

@Composable
internal fun WarmupChecklistLine(
    title: String,
    subtitle: String,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    isCompleted: Boolean,
    accent: Color,
    onToggle: (completed: Boolean, weightKg: Double) -> Unit,
    onWeightDraft: ((String) -> Unit)? = null,
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PrepCheckBox(
            checked = isCompleted,
            onCheckedChange = { checked ->
                // El teclado decimal escribe la coma: se lee como punto antes de convertir (LR0).
                val kg = textValue.replace(',', '.').toDoubleOrNull()
                    ?: suggestedWeightKg
                    ?: 0.0
                onToggle(checked, kg)
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = KpknType.bodyStrong,
                color = if (isCompleted) WizardColors.textMuted else WizardColors.text,
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 2,
                )
            }
        }
        // Celda de peso `inset`: la cifra en Syne y la unidad junto a ella, con coma decimal (LR1b).
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(KpknSurface.inset)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onWeightDraft?.invoke(it)
                },
                textStyle = KpknType.metricM.copy(color = WizardColors.text, textAlign = TextAlign.End),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                modifier = Modifier.widthIn(min = 36.dp, max = 56.dp),
            )
            Text("kg", style = KpknType.unit, color = WizardColors.textMuted)
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
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Fila 1: el reloj a la izquierda y iniciar/pausar a la derecha. Las acciones de texto van debajo: en una sola fila no caben a 130 %.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(KpknLineIcons.Clock, null, tint = sessionAccentColor, modifier = Modifier.size(16.dp))
                // Reloj en Syne con cifras tabulares, como el del descanso (LR1b). Los últimos 10 s, en `danger`.
                Text(
                    "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                    style = KpknType.metricM,
                    color = if (remainingSeconds in 1..10) WizardColors.danger else WizardColors.text,
                )
                Text(
                    "/ ${totalMinutes} m",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            KpknIconButton(
                icon = if (isRunning) KpknLineIcons.Pause else KpknLineIcons.PlayFilled,
                contentDescription = if (isRunning) "Pausar temporizador" else "Iniciar temporizador",
                onClick = { if (isRunning) onPause() else onStart() },
                tint = sessionAccentColor,
            )
        }
        // Fila 2: «+30 s» y «Reset», a la derecha de la fila.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        ) {
            KpknTextAction(text = "+30 s", onClick = { onAddSeconds(30) })
            KpknTextAction(text = "Reset", onClick = onReset)
        }
    }
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
            Column {
                Text(
                    text = "Descanso".uppercase(Locale.forLanguageTag("es")),
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                Text(
                    text = "%02d:%02d".format(mins, secs),
                    style = KpknType.metricM,
                    color = WizardColors.text,
                )
            }
            // «Saltar» es la acción principal del descanso, en tinta, como el «Saltar descanso» de la serie (LR1a).
            KpknPrimaryPill(text = "Saltar", onClick = onSkip)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KpknSurface.inset),
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
internal fun PrepPhaseSkipBar(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknSecondaryPill(
        text = "Omitir",
        onClick = onSkip,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun formatLiveWarmupPercent(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()} %"
}
