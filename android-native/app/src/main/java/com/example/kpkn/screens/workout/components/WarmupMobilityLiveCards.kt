package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RecordFabHolder
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.workout.inlineRestRemainingOrNull
import com.example.kpkn.screens.workout.design.PrepCheckState
import com.example.kpkn.screens.workout.design.PrepChecklistRow
import com.example.kpkn.screens.workout.design.PrepKgField
import com.example.kpkn.screens.workout.design.PrepPageHeader
import com.example.kpkn.screens.workout.design.PrepRestRow
import com.example.kpkn.screens.workout.design.PrepTimerRow
import com.example.kpkn.screens.workout.design.prepClock
import com.example.kpkn.screens.workout.design.prepMobilityDose
import com.example.kpkn.screens.workout.design.prepPercentText
import com.example.kpkn.screens.workout.design.workoutBottomFade
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import kotlin.math.roundToInt

/**
 * Página de movilidad del pager de la sesión (KPKN LS5b): el elemento actual en grande, el temporizador del bloque y el checklist.
 * Sin caja: sobre el negro, con el lenguaje del panel de la serie. Los datos, el temporizador y las llamadas son los de siempre.
 */
@Composable
internal fun MobilityPhaseLiveCard(
    items: List<WorkoutMobilityChecklistItem>,
    completedStepKeys: Set<String>,
    remainingSeconds: Int,
    isTimerRunning: Boolean,
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
    val reduced = wizardReducedMotion()
    val currentIndex = currentPrepIndex(items.map { it.stepKey in completedStepKeys })
    val restRemaining = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
    PrepPageLayout(
        modifier = modifier,
        header = {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = { prepContentTransition(reduced) },
                label = "prepMobilityHeader",
            ) { index ->
                val current = items.getOrNull(index)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrepPageHeader(
                        eyebrow = "Movilidad ${index + 1} de ${items.size.coerceAtLeast(1)}",
                        title = current?.mobility?.name?.takeIf { it.isNotBlank() } ?: "Movilidad",
                        dose = current?.mobility?.let { prepMobilityDose(it) },
                    )
                    PrepTimerRow(
                        clock = prepClock(remainingSeconds),
                        running = isTimerRunning,
                        onToggle = { if (isTimerRunning) onPauseTimer() else onStartTimer() },
                        onAddSeconds = { onAddTimerSeconds(30) },
                        onReset = onResetTimer,
                    )
                }
            }
        },
        footer = {
            if (restRemaining != null) {
                PrepRestRow(
                    remainingSeconds = restRemaining.coerceAtLeast(0),
                    totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                    onSkip = onSkipInlineRest ?: {},
                )
            } else {
                PrepPhaseSkipBar(label = "Saltar movilidad", onSkip = onSkip)
            }
        },
        content = {
            items.forEachIndexed { index, item ->
                if (index > 0) PrepRowDivider()
                val state = when {
                    item.stepKey in completedStepKeys -> PrepCheckState.DONE
                    index == currentIndex -> PrepCheckState.CURRENT
                    else -> PrepCheckState.PENDING
                }
                PrepChecklistRow(
                    title = item.mobility.name,
                    state = state,
                    onToggle = { onToggleComplete(item, state != PrepCheckState.DONE) },
                    caption = listOfNotNull(
                        item.exerciseName.takeIf { showExerciseBadge && it.isNotBlank() },
                        prepMobilityDose(item.mobility),
                    ).joinToString(" · ").takeIf { it.isNotBlank() },
                )
            }
        },
    )
}

/**
 * Página de aproximación del pager de la sesión (KPKN LS5b): la aproximación actual en grande, con su peso y sus repeticiones, y el
 * checklist de todas. El peso de cada fila se escribe ahí mismo y llega a [onWeightDraft] (y al relator), como antes.
 */
@Composable
internal fun WarmupPhaseLiveCard(
    rows: List<WarmupPhaseRow>,
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
    weightDrafts: Map<String, String> = emptyMap(),
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
    val reduced = wizardReducedMotion()
    val currentIndex = currentPrepIndex(rows.map { it.isCompleted })
    val restRemaining = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
    PrepPageLayout(
        modifier = modifier,
        header = {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = { prepContentTransition(reduced) },
                label = "prepWarmupHeader",
            ) { index ->
                val row = rows.getOrNull(index)
                // El peso que se ve arriba es el que se escribe en la fila (si hay borrador), o el registrado, o la sugerencia.
                val typedKg = row?.let { weightDrafts[warmupDraftKey(it)] }?.replace(',', '.')?.toDoubleOrNull()
                val kg = typedKg ?: row?.actualWeightKg ?: row?.suggestedWeightKg
                val estimate = kg != null && typedKg == null && row?.actualWeightKg == null
                PrepPageHeader(
                    eyebrow = "Aproximación ${index + 1} de ${rows.size.coerceAtLeast(1)}",
                    title = row?.exerciseName?.takeIf { it.isNotBlank() } ?: "Calentamiento",
                    dose = when {
                        row == null -> null
                        kg != null -> kg.toTrimmedNumberString()
                        else -> prepPercentText(row.warmup.percentageOfWorkingWeight)
                    },
                    doseUnit = if (kg != null) "kg" else null,
                    doseLead = if (estimate) "≈" else null,
                    caption = row?.let { warmupCaption(it, hasKg = kg != null, estimate = estimate) },
                )
            }
        },
        footer = {
            if (restRemaining != null) {
                PrepRestRow(
                    remainingSeconds = restRemaining.coerceAtLeast(0),
                    totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                    onSkip = onSkipInlineRest ?: {},
                )
            } else {
                PrepPhaseSkipBar(label = "Saltar calentamiento", onSkip = onSkip)
            }
        },
        content = {
            rows.forEachIndexed { index, row ->
                if (index > 0) PrepRowDivider()
                val state = when {
                    row.isCompleted -> PrepCheckState.DONE
                    index == currentIndex -> PrepCheckState.CURRENT
                    else -> PrepCheckState.PENDING
                }
                WarmupChecklistLine(
                    title = "Aproximación ${index + 1}",
                    caption = listOfNotNull(
                        row.exerciseBadge?.takeIf { it.isNotBlank() },
                        prepPercentText(row.warmup.percentageOfWorkingWeight),
                        "${row.warmup.targetReps} repeticiones",
                    ).joinToString(" · "),
                    state = state,
                    suggestedWeightKg = row.suggestedWeightKg,
                    actualWeightKg = row.actualWeightKg,
                    onToggle = { completed, kg -> onToggleComplete(row, completed, kg) },
                    onWeightDraft = onWeightDraft?.let { callback -> { text -> callback(row, text) } },
                )
            }
            if (onAddSet != null) {
                KpknTextAction(
                    text = "Añadir aproximación",
                    onClick = onAddSet,
                    leadingIcon = KpknLineIcons.Plus,
                )
            }
        },
    )
}

internal data class WarmupPhaseRow(
    val exerciseId: String,
    val exerciseBadge: String?,
    val index: Int,
    val warmup: WarmupSetDefinition,
    val suggestedWeightKg: Double?,
    val actualWeightKg: Double?,
    val isCompleted: Boolean,
    /** Nombre del ejercicio del calentamiento: es el título de la página (KPKN LS5b). */
    val exerciseName: String = "",
)

/**
 * Transparent sizing slot for every live pager page.
 *
 * The normal set card deliberately has no outer gray Surface: only its
 * Planificado/Reportar inner sections provide containers. Prep pages (LS5b) sit
 * on the black canvas too, with no box. The slot keeps every page aligned: the
 * inner frame scales the previous base bounds uniformly so all card content
 * grows without spilling into the neighboring pager page. Normal set cards can
 * opt into measured height expansion so plan/technique panels are laid out below
 * the card instead of being hidden by the fixed slot. Prep pages lock to the
 * working-set height and clip to it, so their checklist scrolls inside.
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
    val topNudgeFraction = policy.topNudgeFraction
    val topNudge = slotHeight * topNudgeFraction + WorkoutUiTokens.LivePagerRelatorClearance
    if (!allowContentExpansion) {
        val widthFraction = if (scaleX >= 1f) (1f / scaleX).coerceIn(0.01f, 1f) else 1f
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(slotHeight + topNudge),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = topNudge)
                    .fillMaxWidth(widthFraction)
                    .height(baseHeight)
                    .then(
                        if (kotlin.math.abs(scaleX - scaleY) < 0.001f) {
                            Modifier.scale(scaleX)
                        } else {
                            Modifier.graphicsLayer {
                                this.scaleX = scaleX
                                this.scaleY = scaleY
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                        },
                    ),
                content = content,
            )
        }
        return
    }

    val density = LocalDensity.current
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
        val topNudgePx = (scaledHeight * topNudgeFraction).roundToInt() +
            WorkoutUiTokens.LivePagerRelatorClearance.roundToPx()
        val layoutHeight = scaledHeight + topNudgePx

        layout(layoutWidth, layoutHeight) {
            placeable.placeRelativeWithLayer(
                x = ((layoutWidth - scaledWidth) / 2f).roundToInt(),
                y = topNudgePx,
            ) {
                this.scaleX = scaleX
                this.scaleY = scaleY
                transformOrigin = TransformOrigin(0f, 0f)
                clip = lockToWorkingSetHeight
            }
        }
    }
}

/**
 * Esqueleto de una página de prep (KPKN LS5b): la cabecera fija arriba, el checklist con scroll en medio (ocupa lo que sobra) y el pie
 * fijo abajo. Sin caja: sobre el negro.
 */
@Composable
private fun PrepPageLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        header()
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .verticalScroll(scrollState)
                .workoutBottomFade(scrollState),
        ) {
            content()
        }
        footer()
    }
}

/** Índice del elemento que toca: el primero sin hacer, o el último si ya están todos hechos. */
private fun currentPrepIndex(done: List<Boolean>): Int {
    val first = done.indexOfFirst { !it }
    return if (first >= 0) first else (done.size - 1).coerceAtLeast(0)
}

/** Cambio de elemento: la cabecera sube con un fundido corto; con movimiento reducido, de golpe. */
private fun prepContentTransition(reduced: Boolean): ContentTransform =
    if (reduced) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(tween(durationMillis = 220)) + slideInVertically(tween(durationMillis = 220)) { it / 8 }) togetherWith fadeOut(tween(durationMillis = 140))
    }

/** Clave del borrador de peso de una aproximación: la misma que usa la página para el relator (`exerciseId_warmupId`). */
private fun warmupDraftKey(row: WarmupPhaseRow): String = "${row.exerciseId}_${row.warmup.id}"

/** Leyenda de la aproximación actual: si el peso es una sugerencia, lo dice; sin peso, lo dice también. */
private fun warmupCaption(row: WarmupPhaseRow, hasKg: Boolean, estimate: Boolean): String {
    val pct = prepPercentText(row.warmup.percentageOfWorkingWeight)
    val reps = "${row.warmup.targetReps} repeticiones"
    return when {
        !hasKg -> "Peso pendiente · $reps · $pct"
        estimate -> "Sugerido · $reps · $pct"
        else -> "$reps · $pct"
    }
}

@Composable
private fun PrepRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    ) {}
}

/** Una aproximación del checklist: el peso va a la derecha, fuera de la zona que marca la fila. */
@Composable
private fun WarmupChecklistLine(
    title: String,
    caption: String,
    state: PrepCheckState,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    onToggle: (completed: Boolean, weightKg: Double) -> Unit,
    onWeightDraft: ((String) -> Unit)? = null,
) {
    var textValue by remember(title, actualWeightKg, suggestedWeightKg) {
        val initial = actualWeightKg ?: suggestedWeightKg
        mutableStateOf(initial?.toTrimmedNumberString() ?: "")
    }
    val suggestedText = suggestedWeightKg?.toTrimmedNumberString().orEmpty()
    PrepChecklistRow(
        title = title,
        state = state,
        caption = caption,
        onToggle = {
            // El teclado decimal escribe la coma: se lee como punto antes de convertir.
            val kg = textValue.replace(',', '.').toDoubleOrNull() ?: suggestedWeightKg ?: 0.0
            onToggle(state != PrepCheckState.DONE, kg)
        },
        trailing = {
            PrepKgField(
                value = textValue,
                onValueChange = { typed ->
                    textValue = typed
                    onWeightDraft?.invoke(typed)
                },
                suggested = actualWeightKg == null && textValue == suggestedText,
            )
        },
    )
}

/** «Saltar calentamiento» o «Saltar movilidad»: una acción de texto en el pie de la página (KPKN LS5b). */
@Composable
internal fun PrepPhaseSkipBar(
    label: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknTextAction(text = label, onClick = onSkip, modifier = modifier)
}
