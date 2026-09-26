package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TICKS_PER_UNIT = 10
private val tickSlotWidth = 10.5.dp
private val ruleItemHeight = 110.dp
private val ruleTrackHeight = 90.dp

/**
 * Regla horizontal de peso como la referencia (`Workouts/p5.jpg`): cifra
 * centrada pequeña sobre marcas **regulares y tenues**, rótulos
 * de los enteros **encima** de las marcas, cursor verde fino y más alto, y
 * borrado lateral sobre el fondo antracita exacto.
 *
 * Solo los enteros llevan texto; las fracciones son marcas sin rótulo.
 * Deslizar ajusta de décima en décima y **no confirma**: la confirmación es un
 * toque explícito en el valor central. **La posición inicial de la regla no es
 * una respuesta**: `onValueChange` solo se emite tras una interacción real.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizardWeightRule(
    unit: WizardMassUnit,
    valueKg: Double?,
    onValueChange: (Double) -> Unit,
    onConfirm: (() -> Unit)? = null,
) {
    val reducedMotion = wizardReducedMotion()
    val isLb = unit == WizardMassUnit.LB
    val range = remember(unit) { WizardWeightScale.displayRange(unit) }
    val firstStep = remember(unit) { kotlin.math.round(range.start * TICKS_PER_UNIT).toInt() }
    val lastStep = remember(unit) { kotlin.math.round(range.endInclusive * TICKS_PER_UNIT).toInt() }
    val stepCount = lastStep - firstStep + 1

    val initialIndex = remember(unit) {
        val anchor = WizardWeightScale.toDisplay(valueKg ?: WizardWeightScale.MIN_KG * 3.5, unit)
        (kotlin.math.round(WizardWeightScale.clampDisplay(anchor, unit) * TICKS_PER_UNIT).toInt() - firstStep)
            .coerceIn(0, stepCount - 1)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }
    var programmaticScrolls by remember { mutableStateOf(0) }
    var lastEmittedKg by remember { mutableStateOf<Double?>(null) }
    val onValueChangeState = rememberUpdatedState(onValueChange)

    fun centeredIndexNow(): Int {
        val layoutInfo = state.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return initialIndex
        val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        return visible.minByOrNull { abs((it.offset + it.size / 2) - center) }
            ?.index?.coerceIn(0, stepCount - 1)
            ?: initialIndex
    }

    fun kgAt(index: Int): Double {
        val display = (firstStep + index) / TICKS_PER_UNIT.toDouble()
        return WizardWeightScale.toKg(display, unit)
    }

    fun emitCandidate(kg: Double) {
        // A settled swipe, `jumpTo` and a later center confirmation can carry the
        // same candidate; emit it only once while still allowing onConfirm.
        if (lastEmittedKg != kg) {
            lastEmittedKg = kg
            onValueChangeState.value(kg)
        }
    }

    val centerIndex by remember(state, stepCount, initialIndex) {
        derivedStateOf { centeredIndexNow() }
    }
    val currentDisplay = (firstStep + centerIndex) / TICKS_PER_UNIT.toDouble()
    val currentKg = WizardWeightScale.toKg(currentDisplay, unit)

    /**
     * Confirmar el peso central explícitamente (tocar el valor central o la
     * acción de accesibilidad) **no exige haber movido la regla**: la posición
     * inicial no es una respuesta, pero confirmarla sí lo es. Cada interacción
     * emite el valor exacto una sola vez.
     */
    fun confirm() {
        touched = true
        emitCandidate(currentKg)
        onConfirm?.invoke()
    }

    fun settle() {
        if (touched) emitCandidate(kgAt(centeredIndexNow()))
    }

    val jumpTo: (Int) -> Unit = { target ->
        touched = true
        scope.launch {
            programmaticScrolls += 1
            try {
                if (reducedMotion) state.scrollToItem(target) else state.animateScrollToItem(target)
            } finally {
                programmaticScrolls -= 1
            }
            settle()
        }
    }

    // Solo re-centra cuando cambia la unidad. Dejar de observar `valueKg` evita que
    // cada `onValueChange` del usuario devuelva la regla a la posición inicial.
    LaunchedEffect(unit) {
        programmaticScrolls += 1
        try {
            state.scrollToItem(initialIndex)
            withFrameNanos { }
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex }
                ?: return@LaunchedEffect
            val viewportCenter = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
            val delta = item.offset - (viewportCenter - item.size / 2)
            if (delta != 0) state.scroll { scrollBy(delta.toFloat()) }
        } finally {
            touched = false
            programmaticScrolls -= 1
        }
    }

    // Only a real user scroll emits at rest. Initial centering and jumpTo are
    // bracketed by programmaticScrolls; neither reaches the user-scroll branch.
    LaunchedEffect(state, unit) {
        var userScrollActive = false
        snapshotFlow { Triple(state.isScrollInProgress, programmaticScrolls, centerIndex) }
            .collect { (scrolling, programmatic, candidateIndex) ->
                if (scrolling) {
                    if (programmatic == 0) {
                        touched = true
                        userScrollActive = true
                    }
                    return@collect
                }
                if (!userScrollActive) return@collect
                userScrollActive = false
                if (programmatic == 0) emitCandidate(kgAt(candidateIndex))
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Regla de peso en ${if (isLb) "libras" else "kilogramos"}"
                stateDescription =
                    "${WizardWeightScale.format(currentDisplay)} ${unit.code}. Desliza para ajustar de décima en décima"
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir 0,1 ${unit.code}") {
                        jumpTo((centerIndex - 1).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction("Aumentar 0,1 ${unit.code}") {
                        jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)); true
                    },
                )
                onClick("Confirmar peso") { confirm(); true }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "${WizardWeightScale.format(currentDisplay)} ${unit.code}",
                style = WizardTypography.measure,
                color = if (touched) WizardColors.text else WizardColors.textMuted,
                modifier = Modifier
                    .clickable { confirm() }
                    // Valor central estable para pruebas: el texto grande que se
                    // toca para confirmar exactamente el valor mostrado.
                    .testTag("setup-weight-value")
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth(0.90f)
                .align(Alignment.CenterHorizontally)
                .height(ruleItemHeight),
        ) {
            val horizontalPadding = maxWidth / 2
            Box(Modifier.fillMaxWidth().height(ruleItemHeight)) {
                LazyRow(
                    state = state,
                    flingBehavior = fling,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(ruleTrackHeight),
                ) {
                    items(stepCount) { index ->
                        val step = firstStep + index
                        val value = step / TICKS_PER_UNIT.toDouble()
                        val isInteger = step % TICKS_PER_UNIT == 0
                        // Rótulos ARRIBA de las marcas; intervalos uniformes,
                        // trazos tenues y enteros destacados como en p5.
                        Box(
                            Modifier.width(tickSlotWidth).height(ruleItemHeight),
                        ) {
                            if (isInteger) {
                                Text(
                                    text = WizardWeightScale.format(value),
                                    color = WizardColors.textMuted.copy(alpha = .72f),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .requiredWidth(if (isLb) 32.dp else 28.dp)
                                        .clickable { jumpTo(index) }
                                        .padding(top = 2.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .width(1.5.dp)
                                    .height(if (isInteger) 50.dp else 35.dp)
                                    .background(
                                        WizardColors.textMuted.copy(alpha = .55f),
                                        RoundedCornerShape(1.dp),
                                    ),
                            )
                        }
                    }
                }
                // Borrado lateral neutro (fondo exacto #1F1F1F): los extremos
                // de la regla se desvanecen como la referencia, sin tintar de
                // verde la mitad derecha.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to WizardColors.background,
                                .16f to androidx.compose.ui.graphics.Color.Transparent,
                                .84f to androidx.compose.ui.graphics.Color.Transparent,
                                1f to WizardColors.background,
                            ),
                        ),
                )
                // Cursor central verde: fino y más alto que las marcas (p5).
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 5.dp)
                        .width(2.dp)
                        .height(95.dp)
                        .background(WizardColors.ruleCursor, RoundedCornerShape(1.dp)),
                )
            }
        }
        // Sin fila «−0,1/+0,1» visible (no está en las referencias): el
        // incremento sigue disponible por arrastre y por las acciones
        // semánticas «Disminuir/Aumentar 0,1» del propio control.
        if (!touched && valueKg == null) {
            Text(
                text = "Desliza la regla para declarar tu peso. La posición inicial no es una respuesta.",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}
