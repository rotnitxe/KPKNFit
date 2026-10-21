package com.example.kpkn.ui.design

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Banda de histéresis de la isla (KPKN_UI_V2 §3): aparece al cruzar el umbral y se va solo tras alejarse 24 dp. */
internal val KpknIslandHysteresis = 24.dp

/**
 * Decide si la isla se ve, dada la distancia [past] al umbral en px (≥ 0 = ya pasó). Dentro de la banda de [bandPx] conserva
 * [previous], así no parpadea cuando el scroll rebota en el borde.
 */
internal fun islandShownAfter(previous: Boolean, past: Float, bandPx: Float): Boolean = when {
    past >= 0f -> true
    past < -bandPx -> false
    else -> previous
}

/**
 * `true` cuando el elemento [afterItemIndex] de la lista ya salió por arriba (su borde inferior cruzó el inicio de la vista), con
 * histéresis de 24 dp. Úsalo como `visible` de [KpknTopIsland].
 */
@Composable
fun rememberIslandVisible(listState: LazyListState, afterItemIndex: Int = 0): Boolean {
    val bandPx = with(LocalDensity.current) { KpknIslandHysteresis.toPx() }
    return remember(listState, afterItemIndex, bandPx) {
        var shown = false
        derivedStateOf {
            shown = islandShownAfter(previous = shown, past = listState.pastPx(afterItemIndex), bandPx = bandPx)
            shown
        }
    }.value
}

/**
 * Variante para una columna con [ScrollState]: `true` cuando el desplazamiento llega a [thresholdPx] (la altura de la cabecera de la
 * página), con la misma histéresis de 24 dp.
 */
@Composable
fun rememberIslandVisible(scrollState: ScrollState, thresholdPx: Int): Boolean {
    val bandPx = with(LocalDensity.current) { KpknIslandHysteresis.toPx() }
    return remember(scrollState, thresholdPx, bandPx) {
        var shown = false
        derivedStateOf {
            val past = (scrollState.value - thresholdPx).toFloat()
            shown = islandShownAfter(previous = shown, past = past, bandPx = bandPx)
            shown
        }
    }.value
}

/** Distancia en px de [index] al inicio de la vista: positiva si ya salió por arriba, negativa si todavía no ha llegado. */
private fun LazyListState.pastPx(index: Int): Float {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    return when {
        item != null -> -(item.offset + item.size).toFloat()
        index < firstVisibleItemIndex -> Float.MAX_VALUE
        else -> -Float.MAX_VALUE
    }
}
