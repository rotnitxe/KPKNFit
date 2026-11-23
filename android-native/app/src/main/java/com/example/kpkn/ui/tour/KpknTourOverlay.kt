package com.example.kpkn.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val TourVeil = Color(0xFF000000).copy(alpha = 0.75f)
private val TourCutoutMargin = 8.dp
private val TourTryCutoutMargin = 6.dp
private val TourCardShape = RoundedCornerShape(24.dp)
private val TourCardFallback = Color(0xFF121212).copy(alpha = 0.93f)
private val TourCardGap = 14.dp
private const val TourEnterMillis = 220
private const val TourMaxTouchBlockers = 24
private val TourCutoutSpring = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

private val TourEyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.9.sp,
)
private val TourTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
)
private val TourBodyStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
private val TourHintStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)
private val TourChipLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/**
 * Overlay del recorrido guiado montado por el anfitrión sobre todo lo demás.
 *
 * - Velo negro con recorte de resorte (`spring`) y radio adaptativo al componente.
 * - Anillo de foco nítido en pasos `NEXT` y halo + beacon pulsante en pasos `TRY`.
 * - Tarjeta flotante glass en la zona segura opuesta al ancla (sin `TourArrow`).
 * - Durante `isScrollingToAnchor` o sin ancla visible no pinta una pantalla negra vacía.
 */
@Composable
fun KpknTourOverlay(
    tour: TourController,
    anchors: TourAnchorRegistry,
    modifier: Modifier = Modifier,
) {
    val ui by tour.state.collectAsState()
    val step = ui.current
    val reduced = wizardReducedMotion()
    SideEffect { tour.reducedMotion = reduced }

    val density = LocalDensity.current
    val haze = LocalHazeState.current
    val insetTop = WindowInsets.statusBars.getTop(density)
    val insetBottom = WindowInsets.navigationBars.getBottom(density)
    val marginPx = with(density) { TourCutoutMargin.toPx() }
    val tryMarginPx = with(density) { TourTryCutoutMargin.toPx() }

    val target = if (step != null && !ui.isScrollingToAnchor) {
        anchors.boundsOf(step.anchorId)?.inflate(marginPx)
    } else {
        null
    }
    val rawTryBounds = if (step != null && !ui.isScrollingToAnchor) {
        step.tryAnchorId?.let { anchors.boundsOf(it) }
    } else {
        null
    }
    val disjointTryTarget = if (target != null && rawTryBounds != null && !target.overlaps(rawTryBounds)) {
        rawTryBounds.inflate(tryMarginPx)
    } else {
        null
    }
    val targetCornerPx = with(density) { (step?.cutoutCornerDp ?: 22f).dp.toPx() }
    val tryCornerPx = with(density) { 24.dp.toPx() }

    val cut = remember { TourCut() }
    LaunchedEffect(target, targetCornerPx, reduced) {
        animateCut(cut, target, targetCornerPx, reduced)
    }

    val tryCut = remember { TourCut() }
    LaunchedEffect(disjointTryTarget, tryCornerPx, reduced) {
        animateCut(tryCut, disjointTryTarget, tryCornerPx, reduced)
    }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(ui.tourId, ui.index, reduced, target != null) {
        if (target == null) {
            enter.snapTo(0f)
        } else if (reduced) {
            enter.snapTo(1f)
        } else {
            enter.snapTo(0f)
            enter.animateTo(1f, tween(durationMillis = TourEnterMillis, easing = FastOutSlowInEasing))
        }
    }

    val pulsePhase: Float
    val beaconPhase: Float
    if (step?.kind == TourStepKind.TRY && !reduced) {
        val infinite = rememberInfiniteTransition(label = "tour-try-pulse")
        pulsePhase = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "halo-phase",
        ).value
        beaconPhase = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "beacon-phase",
        ).value
    } else {
        pulsePhase = 0f
        beaconPhase = 0f
    }

    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(ui.tourId, ui.index, target != null) {
        if (target != null) runCatching { titleFocus.requestFocus() }
    }
    BackHandler(enabled = ui.isActive) { tour.skip() }

    val veil = remember { Path() }
    val cutShown = step != null && !ui.isScrollingToAnchor && target != null && cut.placed
    val tryCutShown = cutShown && disjointTryTarget != null && tryCut.placed

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (!cutShown) return@drawBehind
                val activeStep = step ?: return@drawBehind
                val mainRect = cut.toRect()
                val mainCorner = cut.clampedCorner(mainRect)

                veil.reset()
                veil.fillType = PathFillType.EvenOdd
                veil.addRect(Rect(0f, 0f, size.width, size.height))
                veil.addRoundRect(RoundRect(mainRect, CornerRadius(mainCorner, mainCorner)))
                if (tryCutShown) {
                    val tryRect = tryCut.toRect()
                    if (!mainRect.overlaps(tryRect)) {
                        val tryCorner = tryCut.clampedCorner(tryRect)
                        veil.addRoundRect(RoundRect(tryRect, CornerRadius(tryCorner, tryCorner)))
                    }
                }
                drawPath(veil, TourVeil)

                drawFocusRing(
                    rect = mainRect,
                    corner = mainCorner,
                    isTry = activeStep.kind == TourStepKind.TRY,
                    pulsePhase = pulsePhase,
                )
                if (tryCutShown) {
                    val tryRect = tryCut.toRect()
                    drawFocusRing(
                        rect = tryRect,
                        corner = tryCut.clampedCorner(tryRect),
                        isTry = true,
                        pulsePhase = pulsePhase,
                    )
                }
                if (activeStep.kind == TourStepKind.TRY && !reduced && !ui.trySuccessPulse && rawTryBounds != null) {
                    drawTryBeacon(center = rawTryBounds.center, phase = beaconPhase)
                }
            },
    ) {
        if (cutShown) {
            val activeStep = step!!
            val anchor = target!!
            TourTouchRegions(
                outsideCutOnly = activeStep.kind == TourStepKind.TRY,
                cut = cut,
                tryCut = if (tryCutShown) tryCut else null,
            )
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    KpknTourCard(
                        ui = ui,
                        step = activeStep,
                        haze = haze,
                        reduced = reduced,
                        titleFocus = titleFocus,
                        onPrev = { tour.previous() },
                        onNext = { tour.next() },
                        onSkip = { tour.skip() },
                        modifier = Modifier.graphicsLayer {
                            alpha = enter.value
                            val scale = if (reduced) 1f else 0.94f + 0.06f * enter.value
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                },
            ) { measurables, constraints ->
                val gutter = WizardSpacing.gutter.roundToPx()
                val gap = TourCardGap.roundToPx()
                val safeMargin = 12.dp.roundToPx()
                val safeTop = insetTop + safeMargin
                val safeBottom = constraints.maxHeight - insetBottom - safeMargin
                val width = (constraints.maxWidth - 2 * gutter).coerceAtLeast(0)
                val card = measurables[0].measure(
                    Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight),
                )
                val maxY = max(safeTop, safeBottom - card.height)
                val belowY = (anchor.bottom + gap).roundToInt()
                val aboveY = (anchor.top - gap - card.height).roundToInt()
                val tryBelow = disjointTryTarget != null && disjointTryTarget.center.y > anchor.center.y
                val fitsBelow = !tryBelow && belowY + card.height <= safeBottom
                val fitsAbove = aboveY >= safeTop
                val y = when {
                    tryBelow && fitsAbove -> aboveY
                    anchor.center.y > constraints.maxHeight * 0.55f && fitsAbove -> aboveY
                    fitsBelow -> belowY
                    fitsAbove -> aboveY
                    else -> {
                        val spaceAbove = anchor.top - safeTop
                        val spaceBelow = safeBottom - anchor.bottom
                        if (spaceAbove >= spaceBelow) aboveY else belowY
                    }
                }.coerceIn(safeTop, maxY)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    card.place(gutter, y)
                }
            }
        }
    }
}

private suspend fun animateCut(
    cut: TourCut,
    rect: Rect?,
    cornerPx: Float,
    reduced: Boolean,
) {
    if (rect == null) {
        cut.placed = false
    } else if (!cut.placed || reduced) {
        cut.left.snapTo(rect.left)
        cut.top.snapTo(rect.top)
        cut.right.snapTo(rect.right)
        cut.bottom.snapTo(rect.bottom)
        cut.corner.snapTo(cornerPx)
        cut.placed = true
    } else {
        coroutineScope {
            launch { cut.left.animateTo(rect.left, TourCutoutSpring) }
            launch { cut.top.animateTo(rect.top, TourCutoutSpring) }
            launch { cut.right.animateTo(rect.right, TourCutoutSpring) }
            launch { cut.bottom.animateTo(rect.bottom, TourCutoutSpring) }
            launch { cut.corner.animateTo(cornerPx, TourCutoutSpring) }
        }
    }
}

private fun DrawScope.drawFocusRing(
    rect: Rect,
    corner: Float,
    isTry: Boolean,
    pulsePhase: Float,
) {
    val topLeft = Offset(rect.left, rect.top)
    val rectSize = Size(rect.width, rect.height)
    val cr = CornerRadius(corner, corner)
    if (isTry) {
        val outerExpand = (4f + 5f * pulsePhase).dp.toPx()
        val outerAlpha = 0.28f * (1f - 0.65f * pulsePhase)
        drawRoundRect(
            color = SymbolPalette.musculo.copy(alpha = outerAlpha),
            topLeft = Offset(rect.left - outerExpand / 2f, rect.top - outerExpand / 2f),
            size = Size(rect.width + outerExpand, rect.height + outerExpand),
            cornerRadius = CornerRadius(corner + outerExpand / 2f, corner + outerExpand / 2f),
            style = Stroke(width = outerExpand),
        )
        drawRoundRect(
            color = SymbolPalette.musculo.copy(alpha = 0.95f),
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = cr,
            style = Stroke(width = 2.dp.toPx()),
        )
    } else {
        drawRoundRect(
            color = WizardColors.text.copy(alpha = 0.08f),
            topLeft = Offset(rect.left - 2.dp.toPx(), rect.top - 2.dp.toPx()),
            size = Size(rect.width + 4.dp.toPx(), rect.height + 4.dp.toPx()),
            cornerRadius = CornerRadius(corner + 2.dp.toPx(), corner + 2.dp.toPx()),
            style = Stroke(width = 4.dp.toPx()),
        )
        drawRoundRect(
            color = WizardColors.text.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = cr,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

private fun DrawScope.drawTryBeacon(center: Offset, phase: Float) {
    val rippleRadius = (10f + 16f * phase).dp.toPx()
    val rippleAlpha = (1f - phase).coerceIn(0f, 1f)
    drawCircle(
        color = SymbolPalette.musculo.copy(alpha = 0.28f * rippleAlpha),
        radius = rippleRadius,
        center = center,
    )
    drawCircle(
        color = SymbolPalette.musculo.copy(alpha = 0.85f * rippleAlpha),
        radius = rippleRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawCircle(
        color = SymbolPalette.musculo,
        radius = 6.dp.toPx(),
        center = center,
    )
}

/** Recorte animado con física de resorte y radio adaptativo. */
private class TourCut {
    val left = Animatable(0f)
    val top = Animatable(0f)
    val right = Animatable(0f)
    val bottom = Animatable(0f)
    val corner = Animatable(0f)
    var placed by mutableStateOf(false)

    fun toRect(): Rect = Rect(
        left = min(left.value, right.value),
        top = min(top.value, bottom.value),
        right = max(left.value, right.value),
        bottom = max(top.value, bottom.value),
    )

    fun clampedCorner(rect: Rect): Float {
        val maxCorner = (min(rect.width, rect.height) / 2f).coerceAtLeast(0f)
        return corner.value.coerceIn(0f, maxCorner)
    }

    fun toIntRect(screenW: Int, screenH: Int): IntRect {
        val safeW = screenW.coerceAtLeast(0)
        val safeH = screenH.coerceAtLeast(0)
        val minL = min(left.value, right.value)
        val maxR = max(left.value, right.value)
        val minT = min(top.value, bottom.value)
        val maxB = max(top.value, bottom.value)
        val l = minL.roundToInt().coerceIn(0, safeW)
        val t = minT.roundToInt().coerceIn(0, safeH)
        val r = maxR.roundToInt().coerceIn(l, safeW)
        val b = maxB.roundToInt().coerceIn(t, safeH)
        return IntRect(l, t, r, b)
    }
}

/**
 * Bloqueo de toques y recorte medible. Deja libres las áreas de [cut] (y [tryCut] si es disjunto)
 * en pasos `TRY`, y bloquea toda la pantalla en pasos `NEXT`.
 */
@Composable
private fun TourTouchRegions(
    outsideCutOnly: Boolean,
    cut: TourCut,
    tryCut: TourCut?,
) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            repeat(TourMaxTouchBlockers) { Box(Modifier.tourBlockTouches()) {} }
            Box(Modifier.testTag("tour_cutout")) {}
        },
    ) { measurables, constraints ->
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        val mainRect = cut.toIntRect(screenW, screenH)
        val extraRect = tryCut?.toIntRect(screenW, screenH)
        val blockerRects = if (!outsideCutOnly) {
            listOf(IntRect(0, 0, screenW, screenH))
        } else if (extraRect == null) {
            listOf(
                IntRect(0, 0, screenW, mainRect.top),
                IntRect(0, mainRect.bottom, screenW, screenH),
                IntRect(0, mainRect.top, mainRect.left, mainRect.bottom),
                IntRect(mainRect.right, mainRect.top, screenW, mainRect.bottom),
            ).filter { it.width > 0 && it.height > 0 }
        } else {
            computeComplementRects(screenW, screenH, listOf(mainRect, extraRect))
        }

        val placedBlockers = blockerRects.take(TourMaxTouchBlockers).mapIndexed { idx, rect ->
            measurables[idx].measure(Constraints.fixed(rect.width, rect.height)) to rect
        }
        val cutoutPlaceable = measurables[TourMaxTouchBlockers].measure(
            Constraints.fixed(mainRect.width, mainRect.height),
        )
        layout(screenW, screenH) {
            placedBlockers.forEach { (placeable, rect) -> placeable.place(rect.left, rect.top) }
            cutoutPlaceable.place(mainRect.left, mainRect.top)
        }
    }
}

private fun computeComplementRects(
    screenW: Int,
    screenH: Int,
    holes: List<IntRect>,
): List<IntRect> {
    val xs = (listOf(0, screenW) + holes.flatMap { listOf(it.left, it.right) })
        .map { it.coerceIn(0, screenW) }
        .distinct()
        .sorted()
    val ys = (listOf(0, screenH) + holes.flatMap { listOf(it.top, it.bottom) })
        .map { it.coerceIn(0, screenH) }
        .distinct()
        .sorted()
    val out = ArrayList<IntRect>(16)
    for (i in 0 until xs.lastIndex) {
        for (j in 0 until ys.lastIndex) {
            val cell = IntRect(xs[i], ys[j], xs[i + 1], ys[j + 1])
            if (cell.width <= 0 || cell.height <= 0) continue
            val cx = cell.left + cell.width / 2
            val cy = cell.top + cell.height / 2
            val insideHole = holes.any { h -> cx >= h.left && cx < h.right && cy >= h.top && cy < h.bottom }
            if (!insideHole) out += cell
        }
    }
    return out
}

/** Se come todos los toques de la región: nada llega a la app que hay debajo. */
private fun Modifier.tourBlockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun KpknTourCard(
    ui: TourUiState,
    step: TourStep,
    haze: HazeState?,
    reduced: Boolean,
    titleFocus: FocusRequester,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = if (haze != null) {
        Modifier.kpknGlass(haze, TourCardShape, withBorder = false)
    } else {
        Modifier.clip(TourCardShape).background(TourCardFallback)
    }
    val eyebrowText = step.eyebrow.ifBlank { "PASO ${ui.index + 1} DE ${ui.steps.size}" }
    val isLastStep = ui.index == ui.steps.lastIndex

    Column(
        modifier = modifier
            .then(surface)
            .border(1.dp, WizardColors.glassBorder, TourCardShape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { paneTitle = step.title }
            .testTag("tour_bubble"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = eyebrowText,
                style = TourEyebrowStyle,
                color = SymbolPalette.musculo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .testTag("tour_skip")
                    .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = 36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Saltar",
                    style = TourChipLabelStyle,
                    color = WizardColors.textMuted,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TourProgressPills(count = ui.steps.size, current = ui.index, reduced = reduced)
        Spacer(Modifier.height(12.dp))

        if (reduced) {
            TourCardCopy(step = step, titleFocus = titleFocus)
        } else {
            AnimatedContent(
                targetState = ui.index to step,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "tour-card-copy",
            ) { (_, targetStep) ->
                TourCardCopy(step = targetStep, titleFocus = titleFocus)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (ui.canGoBack) {
                TourPillButton(
                    label = "← Atrás",
                    containerColor = Color.White.copy(alpha = 0.07f),
                    contentColor = WizardColors.textMuted,
                    tag = "tour_prev",
                    onClick = onPrev,
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }

            when {
                ui.trySuccessPulse -> {
                    Row(
                        modifier = Modifier
                            .testTag("tour_try_success")
                            .clip(CircleShape)
                            .background(WizardColors.done.copy(alpha = 0.20f))
                            .border(1.dp, WizardColors.done, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = KpknLineIcons.Check,
                            contentDescription = null,
                            tint = WizardColors.done,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "¡Listo!",
                            style = TourChipLabelStyle,
                            color = WizardColors.done,
                        )
                    }
                }
                step.kind == TourStepKind.TRY -> {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val hint = step.tryHint
                        if (!hint.isNullOrBlank()) {
                            Text(
                                text = hint,
                                style = TourHintStyle,
                                color = SymbolPalette.musculo,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        TourPillButton(
                            label = "Continuar",
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = WizardColors.text,
                            borderColor = Color.White.copy(alpha = 0.16f),
                            tag = "tour_next",
                            onClick = onNext,
                        )
                    }
                }
                else -> {
                    TourPillButton(
                        label = if (isLastStep) "Listo" else "Siguiente",
                        containerColor = WizardColors.text,
                        contentColor = Color(0xFF0B0B0B),
                        tag = "tour_next",
                        onClick = onNext,
                    )
                }
            }
        }
    }
}

@Composable
private fun TourCardCopy(
    step: TourStep,
    titleFocus: FocusRequester,
) {
    Column {
        Text(
            text = step.title,
            style = TourTitleStyle,
            color = WizardColors.text,
            modifier = Modifier
                .semantics { heading() }
                .focusRequester(titleFocus)
                .focusable(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = step.body,
            style = TourBodyStyle,
            color = WizardColors.textMuted,
        )
    }
}

@Composable
private fun TourProgressPills(
    count: Int,
    current: Int,
    reduced: Boolean,
) {
    Row(
        modifier = Modifier.semantics { contentDescription = "Paso ${current + 1} de $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val targetWidth = when {
                index == current -> 28.dp
                index < current -> 12.dp
                else -> 10.dp
            }
            val width by if (reduced) {
                remember(targetWidth) { mutableStateOf(targetWidth) }
            } else {
                animateDpAsState(targetValue = targetWidth, label = "tour-pill-width")
            }
            val color = when {
                index == current -> WizardColors.text
                index < current -> WizardColors.done
                else -> Color.White.copy(alpha = 0.20f)
            }
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun TourPillButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    tag: String,
    onClick: () -> Unit,
    borderColor: Color? = null,
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = 40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = TourChipLabelStyle, color = contentColor, maxLines = 1)
    }
}
