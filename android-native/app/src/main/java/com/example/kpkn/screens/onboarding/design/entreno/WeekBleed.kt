package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.example.kpkn.screens.onboarding.design.WizardSpacing

/*
 * Las filas de siete celdas (los días de la energía, del calendario y del inicio de semana) necesitan 7 × 48 = 336 dp para que
 * cada celda sea un objetivo táctil de 48 dp. Una página de 360 dp deja 312 dp entre los márgenes de 24 dp, así que la fila
 * «sangra»: se sale hacia los márgenes lo justo (12 dp por lado a 360 dp) para que cada día mida 48 dp, sin cambiar lo que
 * ocupa en la página ni empujar nada. Si ni a todo el ancho de la pantalla caben (320 dp: 45,7 dp por día) se sale lo que
 * permite el margen y las celdas miden lo máximo posible.
 */

/** Ancho mínimo de una celda de día: el objetivo táctil de 48 dp. */
internal val WeekMinCell: Dp = WizardSpacing.touchTarget

/** Lo máximo que una fila puede salirse por cada lado: el margen lateral de la página larga. */
internal val WeekMaxBleed: Dp = WizardSpacing.gutter

/**
 * Píxeles que debe salirse por CADA lado una fila de [cells] celdas de al menos [minCell] píxeles cuando dispone de [available]
 * píxeles de ancho: lo justo para que quepan, sin pasar de [maxBleed] y nunca negativo (si caben de sobra, 0).
 */
internal fun weekBleedPerSide(available: Int, cells: Int, minCell: Int, maxBleed: Int): Int {
    if (available <= 0 || cells <= 0 || minCell <= 0 || maxBleed <= 0) return 0
    val missing = cells * minCell - available
    if (missing <= 0) return 0
    return ((missing + 1) / 2).coerceAtMost(maxBleed)
}

/**
 * Mide su contenido más ancho que el espacio que le dan, [weekBleedPerSide] por cada lado, y lo coloca centrado: el contenido
 * se sale hacia los márgenes pero la fila sigue ocupando lo mismo en la página. Sin ancho acotado no hace nada.
 *
 * Los toques de la parte que se sale llegan igual (el contenido no recorta); lo único que la recorta es la capa fuera de pantalla
 * del paso mientras llega (0,5 s con el siguiente asomando), y entonces solo se ven los discos del extremo sin elegir.
 */
internal fun Modifier.weekBleed(cells: Int = WEEK_DAY_COUNT, minCell: Dp = WeekMinCell, maxBleed: Dp = WeekMaxBleed): Modifier =
    layout { measurable, constraints ->
        val side = if (constraints.hasBoundedWidth) {
            weekBleedPerSide(constraints.maxWidth, cells, minCell.roundToPx(), maxBleed.roundToPx())
        } else {
            0
        }
        val width = if (side > 0) constraints.maxWidth + 2 * side else constraints.maxWidth
        val placeable = measurable.measure(
            if (side > 0) constraints.copy(minWidth = width, maxWidth = width) else constraints,
        )
        layout((placeable.width - 2 * side).coerceIn(constraints.minWidth, constraints.maxWidth), placeable.height) {
            placeable.place(-side, 0)
        }
    }
