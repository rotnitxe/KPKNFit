package com.example.kpkn.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val TourVeil = Color(0xFF000000).copy(alpha = 0.72f)
private val TourCutoutCorner = 16.dp
private val TourCutoutMargin = 8.dp
private val TourBubbleShape = RoundedCornerShape(24.dp)
private val TourBubbleFallback = Color(0xFF141414).copy(alpha = 0.94f)
private val TourBubbleGap = 12.dp
private val TourArrowWidth = 16.dp
private val TourArrowHeight = 8.dp
private val TourArrowInset = 20.dp
private const val TourEnterMillis = 220
private val TourCutoutSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

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

/**
 * Overlay del recorrido, montado por el anfitrión sobre todo lo demás. Un velo negro al 72 % con un recorte
 * redondeado sobre el ancla y una burbuja con el paso. En pasos «Pruébalo» solo el recorte deja pasar los toques
 * (llegan al elemento); en el resto, nada llega a la app. Atrás = saltar el recorrido.
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
    val density = LocalDensity.current
    val haze = LocalHazeState.current
    // Los insets son @Composable: se leen aquí y la medición del Layout usa los valores.
    val insetTop = WindowInsets.statusBars.getTop(density)
    val insetBottom = WindowInsets.navigationBars.getBottom(density)
    val marginPx = with(density) { TourCutoutMargin.toPx() }
    val cornerPx = with(density) { TourCutoutCorner.toPx() }
    val target = step?.let { anchors.boundsOf(it.anchorId)?.inflate(marginPx) }

    // Recorte: se transforma de un ancla a la siguiente con resorte; con movimiento reducido salta.
    val cut = remember { TourCut() }
    LaunchedEffect(target, reduced) {
        val rect = target
        if (rect == null) {
            cut.placed = false
        } else if (!cut.placed || reduced) {
            cut.left.snapTo(rect.left)
            cut.top.snapTo(rect.top)
            cut.right.snapTo(rect.right)
            cut.bottom.snapTo(rect.bottom)
            cut.placed = true
        } else {
            coroutineScope {
                launch { cut.left.animateTo(rect.left, TourCutoutSpring) }
                launch { cut.top.animateTo(rect.top, TourCutoutSpring) }
                launch { cut.right.animateTo(rect.right, TourCutoutSpring) }
                launch { cut.bottom.animateTo(rect.bottom, TourCutoutSpring) }
            }
        }
    }

    // Entrada de la burbuja en cada paso: escala 0,92→1 y fundido; con movimiento reducido, cuadro final.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(ui.tourId, ui.index, reduced) {
        if (reduced) {
            enter.snapTo(1f)
        } else {
            enter.snapTo(0f)
            enter.animateTo(1f, tween(durationMillis = TourEnterMillis, easing = FastOutSlowInEasing))
        }
    }

    // Al cambiar de paso, el foco va al título para que TalkBack lo lea primero.
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(ui.tourId, ui.index) {
        runCatching { titleFocus.requestFocus() }
    }
    BackHandler(enabled = ui.isActive) { tour.skip() }

    val veil = remember { Path() }
    val cutShown = target != null && cut.placed

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (step == null) return@drawBehind
                veil.reset()
                veil.fillType = PathFillType.EvenOdd
                veil.addRect(Rect(0f, 0f, size.width, size.height))
                if (cutShown) {
                    veil.addRoundRect(
                        RoundRect(
                            cut.left.value,
                            cut.top.value,
                            cut.right.value,
                            cut.bottom.value,
                            CornerRadius(cornerPx, cornerPx),
                        ),
                    )
                }
                drawPath(veil, TourVeil)
            },
    ) {
        if (step != null) {
            TourTouchRegions(
                // «Pruébalo»: se bloquea todo menos el recorte, por donde el toque llega al elemento.
                outsideCutOnly = cutShown && step.kind == TourStepKind.TRY,
                cut = cut,
                cutShown = cutShown,
            )
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    TourBubble(
                        ui = ui,
                        step = step,
                        haze = haze,
                        titleFocus = titleFocus,
                        onNext = { tour.next() },
                        onSkip = { tour.skip() },
                        modifier = Modifier.graphicsLayer {
                            alpha = enter.value
                            val scale = if (reduced) 1f else 0.92f + 0.08f * enter.value
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                    TourArrow(pointsUp = true)
                    TourArrow(pointsUp = false)
                },
            ) { measurables, constraints ->
                val gutter = WizardSpacing.gutter.roundToPx()
                val gap = TourBubbleGap.roundToPx()
                val arrowWidth = TourArrowWidth.roundToPx()
                val arrowHeight = TourArrowHeight.roundToPx()
                val width = (constraints.maxWidth - 2 * gutter).coerceAtLeast(0)
                val bubble = measurables[0].measure(
                    Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight),
                )
                val anchor = target
                // Con ancla visible, la burbuja va debajo si cabe y si no, encima; sin ancla, centrada.
                val below = anchor != null && anchor.bottom + gap + bubble.height <= constraints.maxHeight - insetBottom
                val maxY = max(insetTop, constraints.maxHeight - bubble.height - insetBottom)
                val y = when {
                    anchor == null -> (constraints.maxHeight - bubble.height) / 2
                    below -> (anchor.bottom + gap).roundToInt()
                    else -> (anchor.top - gap - bubble.height).roundToInt()
                }.coerceIn(insetTop, maxY)
                val arrow = if (anchor == null) {
                    null
                } else {
                    measurables[if (below) 1 else 2].measure(Constraints.fixed(arrowWidth, arrowHeight))
                }
                val arrowLow = gutter + TourArrowInset.roundToPx()
                val arrowHigh = gutter + width - TourArrowInset.roundToPx() - arrowWidth
                val arrowX = if (anchor == null) {
                    0
                } else {
                    val centered = (anchor.center.x - arrowWidth / 2f).roundToInt()
                    if (arrowHigh < arrowLow) arrowLow else centered.coerceIn(arrowLow, arrowHigh)
                }
                val arrowY = if (below) y - arrowHeight + 1 else y + bubble.height - 1
                layout(constraints.maxWidth, constraints.maxHeight) {
                    bubble.place(gutter, y)
                    arrow?.place(arrowX, arrowY)
                }
            }
        }
    }
}

/** Recorte animado: cuatro valores que siguen al ancla con resorte. [placed] indica que ya hay un recorte colocado. */
private class TourCut {
    val left = Animatable(0f)
    val top = Animatable(0f)
    val right = Animatable(0f)
    val bottom = Animatable(0f)
    var placed by mutableStateOf(false)
}

/**
 * Bloqueo de toques y recorte medible. Cada hijo se mide y se coloca con `place` en su rectángulo, así que sus límites y
 * su zona de toque son los reales (un `offset` solo movería el contenido). Los límites se leen al medir: el recorte sigue
 * la animación sin recomponer cada cuadro.
 */
@Composable
private fun TourTouchRegions(
    outsideCutOnly: Boolean,
    cut: TourCut,
    cutShown: Boolean,
) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // Hijos 0–3: bloqueos (arriba, abajo, izquierda y derecha del recorte, o uno a pantalla completa). Hijo 4: recorte.
            repeat(4) { Box(Modifier.tourBlockTouches()) {} }
            Box(Modifier.testTag("tour_cutout")) {}
        },
    ) { measurables, constraints ->
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        val left = cut.left.value.roundToInt().coerceIn(0, screenW)
        val top = cut.top.value.roundToInt().coerceIn(0, screenH)
        val right = cut.right.value.roundToInt().coerceIn(left, screenW)
        val bottom = cut.bottom.value.roundToInt().coerceIn(top, screenH)
        val regions = buildList<Pair<Int, IntRect>> {
            if (outsideCutOnly) {
                add(0 to IntRect(0, 0, screenW, top))
                add(1 to IntRect(0, bottom, screenW, screenH))
                add(2 to IntRect(0, top, left, bottom))
                add(3 to IntRect(right, top, screenW, bottom))
            } else {
                add(0 to IntRect(0, 0, screenW, screenH))
            }
            if (cutShown) add(4 to IntRect(left, top, right, bottom))
        }
        val placed = regions.map { (index, rect) ->
            measurables[index].measure(Constraints.fixed(rect.width, rect.height)) to rect
        }
        layout(screenW, screenH) {
            placed.forEach { (placeable, rect) -> placeable.place(rect.left, rect.top) }
        }
    }
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
private fun TourBubble(
    ui: TourUiState,
    step: TourStep,
    haze: HazeState?,
    titleFocus: FocusRequester,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vidrio del anfitrión si hay desenfoque; si no, #141414 al 94 %.
    val surface = if (haze != null) {
        Modifier.kpknGlass(haze, TourBubbleShape, withBorder = false)
    } else {
        Modifier.clip(TourBubbleShape).background(TourBubbleFallback)
    }
    Column(
        modifier = modifier
            .then(surface)
            .border(1.dp, WizardColors.glassBorder, TourBubbleShape)
            .padding(20.dp)
            .semantics { paneTitle = step.title }
            .testTag("tour_bubble"),
    ) {
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
        Text(text = step.body, style = TourBodyStyle, color = WizardColors.textMuted)
        Spacer(Modifier.height(14.dp))
        TourDots(count = ui.steps.size, current = ui.index)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TourAction(label = "Saltar recorrido", color = WizardColors.textMuted, tag = "tour_skip", onClick = onSkip)
            if (step.kind == TourStepKind.NEXT) {
                TourAction(label = "Siguiente", color = WizardColors.text, tag = "tour_next", onClick = onNext)
            } else {
                Text(
                    text = "Pruébalo",
                    style = WizardTypography.controlLabel,
                    color = WizardColors.text,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TourDots(count: Int, current: Int) {
    Row(
        modifier = Modifier.semantics { contentDescription = "Paso ${current + 1} de $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (index == current) WizardColors.text else WizardColors.textFaint, CircleShape),
            ) {}
        }
    }
}

@Composable
private fun TourAction(label: String, color: Color, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = WizardTypography.controlLabel, color = color)
    }
}

/** Flecha pequeña hacia el ancla: apunta arriba si la burbuja va debajo, y abajo si va encima. */
@Composable
private fun TourArrow(pointsUp: Boolean) {
    val path = remember { Path() }
    val border = WizardColors.glassBorder
    Canvas(Modifier.size(TourArrowWidth, TourArrowHeight)) {
        path.reset()
        if (pointsUp) {
            path.moveTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width / 2f, size.height)
        }
        path.close()
        drawPath(path, TourBubbleFallback)
        drawPath(path, border, style = Stroke(width = 1.dp.toPx()))
    }
}
