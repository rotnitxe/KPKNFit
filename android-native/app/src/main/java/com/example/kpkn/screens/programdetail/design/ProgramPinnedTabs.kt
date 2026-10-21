package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.islandShownAfter

/** Alto del control segmentado dentro de la isla pegada: el de la pista de 44 dp más su zona táctil de 48 dp. */
internal val ProgramPinnedTabsHeight = 56.dp

/**
 * Las pestañas «Semana · Estructura · Volumen» pegadas bajo la isla superior al hacer scroll (KPKN_UI_V2 §3, P1): una isla de vidrio
 * con el control segmentado. Entra y sale con un fundido y un leve desplazamiento; con movimiento reducido, solo el fundido.
 * [topOffset] es la distancia desde la barra de estado hasta su borde superior.
 */
@Composable
internal fun ProgramPinnedTabs(
    visible: Boolean,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    topOffset: Dp,
) {
    val appear = remember { MutableTransitionState(false) }
    SideEffect { appear.targetState = visible }
    // Mientras la salida no ha terminado, la capa sigue montada; al acabar, se libera y no queda nada tocable.
    if (visible || appear.currentState) {
        KpknPortal {
            PinnedTabsFrame(appear = appear, options = options, selectedIndex = selectedIndex, onSelect = onSelect, topOffset = topOffset)
        }
    }
}

@Composable
private fun PinnedTabsFrame(
    appear: MutableTransitionState<Boolean>,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    topOffset: Dp,
) {
    val reduced = wizardReducedMotion()
    val hazeState = LocalHazeState.current
    val density = LocalDensity.current
    val slidePx = with(density) { 8.dp.roundToPx() }
    val enter = if (reduced) {
        fadeIn(animationSpec = tween(durationMillis = 120))
    } else {
        fadeIn(animationSpec = tween(durationMillis = 160)) +
            slideInVertically(animationSpec = tween(durationMillis = 180)) { -slidePx }
    }
    val exit = if (reduced) {
        fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        fadeOut(animationSpec = tween(durationMillis = 140)) +
            slideOutVertically(animationSpec = tween(durationMillis = 140)) { -slidePx }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = topOffset, end = 12.dp)
            .height(ProgramPinnedTabsHeight),
    ) {
        AnimatedVisibility(
            visibleState = appear,
            modifier = Modifier.fillMaxSize(),
            enter = enter,
            exit = exit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .kpknGlassOrFallback(hazeState, KpknShape.island, variant = KpknGlassVariant.ISLAND),
                contentAlignment = Alignment.Center,
            ) {
                KpknSegmentedControl(
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * ¿Ya ha subido el borde de un elemento hasta la línea [lineRootPx] (coordenadas de la raíz)? [markerRootY] lo actualiza quien lo
 * mide, con `positionInRoot`. Con la misma banda de histéresis de la isla (24 dp), no parpadea al rebotar el scroll. Así se pegan las
 * pestañas bajo la isla y aparece la isla cuando la cabecera sale de la vista.
 */
@Composable
internal fun rememberPassedLine(markerRootY: MutableFloatState, lineRootPx: Float): Boolean {
    val bandPx = with(LocalDensity.current) { 24.dp.toPx() }
    return remember(markerRootY, lineRootPx, bandPx) {
        var shown = false
        derivedStateOf {
            shown = islandShownAfter(previous = shown, past = lineRootPx - markerRootY.floatValue, bandPx = bandPx)
            shown
        }
    }.value
}

/** Las pestañas de Program Detail, en el orden de [StructureSubTab]: Semana, Estructura y Volumen. */
internal val programDetailTabsOptions: List<String> = listOf("Semana", "Estructura", "Volumen")

/** Altura de la isla superior desde la barra de estado: 12 dp de margen y 56 dp de alto. */
internal val ProgramIslandBottom = 68.dp

/** Distancia desde la barra de estado hasta la píldora de pestañas pegada: bajo la isla, con 8 dp de aire. */
internal val ProgramPinnedTopOffset = 76.dp
