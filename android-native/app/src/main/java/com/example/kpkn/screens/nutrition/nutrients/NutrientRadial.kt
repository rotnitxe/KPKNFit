package com.example.kpkn.screens.nutrition.nutrients

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * La huella radial de «Vitaminas y minerales» (Arreglos v1, `npRad` de scenes-nutricion.js): geometría y selección puras, sin Compose,
 * para probarlas en la JVM. El dibujo vive en NutrientFootprint.kt.
 */

/** Radio interior y exterior de los radios, como fracción del tamaño del dibujo (`r0 = .19`, `r1 = .37`). */
internal const val RADIAL_INNER = 0.19f
internal const val RADIAL_OUTER = 0.37f
/** Cada radio crece en 900 ms y entra 22 ms después del anterior. */
internal const val RADIAL_GROW_MS = 900
internal const val RADIAL_STAGGER_MS = 22
/** Los que más aportan llevan su código; en el día, el nivel decide la opacidad. */
internal const val RADIAL_TOP = 3
internal const val RADIAL_OPACITY_REST = 0.45f
internal const val RADIAL_OPACITY_RELATIVE = 0.6f
internal const val RADIAL_OPACITY_COVERED = 1f
internal const val RADIAL_OPACITY_NEAR = 0.6f
internal const val RADIAL_OPACITY_BELOW = 0.32f
/** Umbrales del día: «cubierto» desde el 100 %, «cerca» desde el 70 %. */
internal const val RADIAL_COVERED_FROM = 100.0
internal const val RADIAL_NEAR_FROM = 70.0

/** Hacia dónde se alinea el código de un radio respecto a su punta (`text-anchor` del lienzo). */
internal enum class RadialAlign { START, CENTER, END }

/**
 * Un radio de la huella. [fraction] es el largo relativo (0..1); null = sin dato. [ceiling] = tope (sodio, cafeína): se dibuja hueco
 * y, si [over] (pasa del 100 %), macizo en rojo. [labeled] = uno de los que más aportan, con su código.
 */
internal data class RadialSpoke(
    val index: Int,
    val sigla: String,
    val angleDegrees: Double,
    val fraction: Float?,
    val ceiling: Boolean,
    val over: Boolean,
    val opacity: Float,
    val labeled: Boolean,
) {
    val cosine: Double get() = cos(Math.toRadians(angleDegrees))
    val sine: Double get() = sin(Math.toRadians(angleDegrees))
}

/** Ángulo del radio [index] (grados, 0° = derecha): −90 + 6 + grupo · 12 + (índice + 0,5) · 13,5. Grupo 0 vitaminas, 1 minerales, 2 otros. */
internal fun radialAngleDegrees(index: Int, group: Int): Double = -90.0 + 6.0 + group * 12.0 + (index + 0.5) * 13.5

/** Opacidad por nivel del día: 1 desde el 100 %, 0,6 desde el 70 %, 0,32 por debajo (sin dato cuenta como 0, solo para la opacidad). */
internal fun radialDayOpacity(percent: Double?): Float {
    val p = percent ?: 0.0
    return when {
        p >= RADIAL_COVERED_FROM -> RADIAL_OPACITY_COVERED
        p >= RADIAL_NEAR_FROM -> RADIAL_OPACITY_NEAR
        else -> RADIAL_OPACITY_BELOW
    }
}

/** Opacidad de un radio: un tope pasado, siempre plena; sin referencia, uniforme; en el día, por nivel; en una comida, plena si es de los que más aportan. */
internal fun radialOpacity(percent: Double?, day: Boolean, relative: Boolean, top: Boolean, over: Boolean): Float = when {
    over -> 1f
    relative -> RADIAL_OPACITY_RELATIVE
    day -> radialDayOpacity(percent)
    top -> 1f
    else -> RADIAL_OPACITY_REST
}

/** Alineación del código según el coseno del ángulo: centro si |cos| < 0,35; izquierda si cos > 0; derecha si no. */
internal fun radialLabelAlign(cosine: Double): RadialAlign = when {
    abs(cosine) < 0.35 -> RadialAlign.CENTER
    cosine > 0 -> RadialAlign.START
    else -> RadialAlign.END
}

/** Largo dibujado: el relleno sobre una pista de [span], con un mínimo de [minLength] para que un valor pequeño se vea. */
internal fun radialLength(span: Float, minLength: Float, fraction: Float): Float = max(minLength, span * min(1f, fraction))

/** Duración total de la entrada: el último radio acaba 900 ms después de empezar, escalonado cada 22 ms. */
internal fun radialTotalMs(count: Int): Int = RADIAL_GROW_MS + RADIAL_STAGGER_MS * (count - 1).coerceAtLeast(0)

/** Avance lineal (0..1) del radio [index] cuando han pasado [elapsedMs]; el dibujo le aplica la curva (0,2, 0,8, 0,2, 1). */
internal fun radialProgress(elapsedMs: Float, index: Int): Float = ((elapsedMs - index * RADIAL_STAGGER_MS) / RADIAL_GROW_MS).coerceIn(0f, 1f)

/** Los 24 radios de la huella en el orden de la app. En el día no hay destacados; sin referencia, el largo es relativo y los topes no se distinguen. */
internal fun radialSpokes(model: NutrientFootprintModel): List<RadialSpoke> = model.bars.mapIndexed { i, bar ->
    val ceiling = bar.ceiling && !model.relative
    val over = ceiling && bar.over
    val top = bar.highlighted && !model.day
    RadialSpoke(
        index = i,
        sigla = bar.slot.sigla,
        angleDegrees = radialAngleDegrees(i, bar.slot.band.ordinal),
        fraction = bar.height?.coerceIn(0f, 1f),
        ceiling = ceiling,
        over = over,
        opacity = radialOpacity(bar.percent, model.day, model.relative, top, over),
        labeled = top,
    )
}

/**
 * Los que más aportan en una comida o ración: los tres de mayor % sin contar los topes, de mayor a menor. Es la misma elección que marca
 * `FootprintBar.highlighted`; aquí con nombre y porcentaje para «Lo que más aporta».
 */
internal fun radialTopRows(model: NutrientFootprintModel): List<NutrientHighlight> = model.highlights.filterNot { it.warn }.take(RADIAL_TOP)

/** La leyenda del día: recuento de cubiertos, cerca y por debajo; los topes pasados por nombre; y el total de objetivos («de N»). */
internal data class RadialLegend(val covered: Int, val near: Int, val below: Int, val overMax: List<String>, val goals: Int)

internal fun radialLegend(model: NutrientFootprintModel): RadialLegend {
    val counts = NutrientFootprints.dayCounts(model)
    return RadialLegend(
        covered = counts.onTrack,
        near = counts.near,
        below = counts.below,
        overMax = model.bars.filter { it.ceiling && it.over }.map { it.key.label },
        goals = model.bars.count { !it.ceiling },
    )
}
