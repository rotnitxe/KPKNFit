package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val wheelItemHeight = 44.dp
private val wheelVisibleItems = 5

/** Atenuación de los vecinos por distancia al centro, como la referencia p3. */
private fun neighbourAlpha(distance: Int): Float = when (distance) {
    1 -> .58f
    2 -> .28f
    else -> .2f
}

/**
 * Rueda vertical de altura de las referencias (p.ej. `Workouts/p3.jpg`): **cinco
 * valores visibles** (pitch ≈ 113 px ≈ 44 dp medidos sobre el JPG 1080), con el
 * ítem central destacado entre dos líneas guía y los vecinos atenuados sin perder
 * contraste.
 *
 * La posición inicial no es una respuesta: el canónico (cm) solo se emite tras
 * una interacción real. Tocar el valor central confirma exactamente ese valor una
 * sola vez, sin haber movido la rueda; también existe la acción de accesibilidad
 * "Confirmar altura". Alternar cm/pies-pulgadas reexpresa el mismo valor sin
 * acumular error de conversión.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizardHeightWheel(
    unit: WizardHeightUnit,
    cm: Int?,
    onValueChange: (Int) -> Unit,
    onConfirm: (() -> Unit)? = null,
) {
    val reducedMotion = wizardReducedMotion()
    val stepCount = remember(unit) { WizardHeightScale.stepCount(unit) }
    val labels = remember(unit) { WizardHeightScale.labels(unit) }
    val initialIndex = remember(unit) {
        WizardHeightScale.nearestStepIndex(cm ?: (WizardHeightScale.MIN_CM + WizardHeightScale.MAX_CM) / 2, unit)
            .coerceIn(0, stepCount - 1)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }
    var programmaticScrolls by remember { mutableStateOf(0) }
    var lastEmittedCm by remember { mutableStateOf<Int?>(null) }
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

    fun emitCandidate(cm: Int) {
        // A settled swipe, `jumpTo` and a later center confirmation can carry the
        // same candidate; emit it only once while still allowing onConfirm.
        if (lastEmittedCm != cm) {
            lastEmittedCm = cm
            onValueChangeState.value(cm)
        }
    }

    val centerIndex by remember(state, stepCount, initialIndex) {
        derivedStateOf { centeredIndexNow() }
    }
    val currentCm = WizardHeightScale.cmForStepIndex(centerIndex, unit)

    /**
     * Confirmar la altura central explícitamente (tocar el valor central o la
     * acción de accesibilidad) **no exige haber movido la rueda**: la posición
     * inicial no es una respuesta, pero confirmarla sí lo es. Cada interacción
     * emite el valor exacto una sola vez.
     */
    fun confirm() {
        touched = true
        emitCandidate(currentCm)
        onConfirm?.invoke()
    }

    fun settle() {
        if (touched) {
            emitCandidate(WizardHeightScale.cmForStepIndex(centeredIndexNow(), unit))
        }
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

    // Solo re-centra cuando cambia la unidad. Dejar de observar `cm` evita que cada
    // `onValueChange` del usuario devuelva la rueda a la posición central.
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
                if (programmatic == 0) {
                    emitCandidate(WizardHeightScale.cmForStepIndex(candidateIndex, unit))
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rueda de altura en ${unit.label.lowercase()}"
                stateDescription = "${WizardHeightScale.format(currentCm, unit)}. Desliza para ajustar"
                role = Role.Button
                onClick("Confirmar altura") { confirm(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir altura") {
                        jumpTo((centerIndex - 1).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction("Aumentar altura") {
                        jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)); true
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(wheelItemHeight * wheelVisibleItems)) {
            LazyColumn(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(vertical = wheelItemHeight * ((wheelVisibleItems - 1) / 2)),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().height(wheelItemHeight * wheelVisibleItems),
            ) {
                itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                    val center = index == centerIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(wheelItemHeight)
                            // Solo el ítem central expone la etiqueta estable; al
                            // desplazarla el tag sigue al valor central, sin duplicados.
                            .then(if (center) Modifier.testTag("setup-height-value") else Modifier)
                            .clickable { if (center) confirm() else jumpTo(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = if (center) WizardTypography.wheelValue else WizardTypography.wheelValueNeighbour,
                            // Centro gris claro y vecinos con atenuación por
                            // distancia a ambos lados (referencia p3).
                            color = if (center) {
                                WizardColors.textMuted
                            } else {
                                WizardColors.textMuted.copy(alpha = neighbourAlpha(abs(index - centerIndex)))
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            // Dos líneas guía finas que enmarcan el ítem central.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -(wheelItemHeight / 2))
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WizardColors.cardBorder),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = wheelItemHeight / 2)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WizardColors.cardBorder),
            )
        }
        // Sin fila «−1/+1» visible (no está en la referencia p3): el incremento
        // sigue disponible por arrastre y por las acciones semánticas
        // «Disminuir/Aumentar altura» del propio control.
        if (!touched && cm == null) {
            Text(
                text = "Desliza la rueda para declarar tu altura. La posición inicial no es una respuesta.",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}
