package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La escala de esfuerzo de una serie (LV8): los valores que el slider de la tarjeta ofrece, en el mismo orden que daba el carrusel de
 * siempre (`buildIntensityCarouselItems`: RIR, RPE, fallo planificado o SOLO_RM con «—»). [planIndex] es la marca del objetivo del plan
 * sobre la pista: solo se ve, NUNCA se preselecciona. Es nulo cuando la serie no tiene objetivo de esfuerzo.
 */
internal data class EffortScale(
    val items: List<IntensityCarouselItem>,
    val planIndex: Int?,
)

private const val EPS = 0.001

/** Construye la escala con la lista exacta que daba el carrusel para [plannedIntensityMode] y [reportedIntensityMode]. */
internal fun buildEffortScale(
    plannedIntensityMode: IntensityMode?,
    reportedIntensityMode: IntensityMode?,
    targetRir: Int?,
    targetRpe: Double?,
): EffortScale {
    val items = buildIntensityCarouselItems(
        plannedIntensityMode = plannedIntensityMode,
        reportedIntensityMode = reportedIntensityMode,
        targetRir = targetRir,
        targetRpe = targetRpe,
    )
    return EffortScale(items, effortPlanIndex(items, plannedIntensityMode, targetRir, targetRpe))
}

/**
 * Índice del objetivo del plan en [items], o nulo si no hay objetivo. Un plan a FALLO marca «Fallo». Un objetivo en la otra unidad de la
 * escala se convierte (RIR 2 es RPE 8): así el plan se ve aunque la lista esté en RPE.
 */
internal fun effortPlanIndex(
    items: List<IntensityCarouselItem>,
    plannedIntensityMode: IntensityMode?,
    targetRir: Int?,
    targetRpe: Double?,
): Int? {
    if (items.isEmpty()) return null
    if (plannedIntensityMode == IntensityMode.FAILURE) {
        return items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }
    }
    val planned: Pair<IntensityMode, Double> = when {
        plannedIntensityMode == IntensityMode.RIR && targetRir != null -> IntensityMode.RIR to targetRir.toDouble()
        plannedIntensityMode == IntensityMode.RPE && targetRpe != null -> IntensityMode.RPE to targetRpe
        targetRir != null -> IntensityMode.RIR to targetRir.toDouble()
        targetRpe != null -> IntensityMode.RPE to targetRpe
        else -> return null
    }
    val (mode, value) = planned
    // 1) La misma unidad que muestra la escala.
    items.indexOfFirst { item ->
        !item.isFailure && item.mode == mode && item.numericValue != null && abs(item.numericValue - value) < EPS
    }.takeIf { it >= 0 }?.let { return it }
    // 2) La otra unidad: RIR r equivale a RPE 10 − r.
    val equivalent = if (mode == IntensityMode.RIR) 10.0 - value else value
    return items.indexOfFirst { item ->
        !item.isFailure && effortRpeEquivalent(item)?.let { abs(it - equivalent) < EPS } == true
    }.takeIf { it >= 0 }
}

/** Esfuerzo de una marca en RPE: RIR r es RPE 10 − r, el fallo es 10 y la marca «—» no tiene valor. */
internal fun effortRpeEquivalent(item: IntensityCarouselItem): Double? = when {
    item.isFailure -> 10.0
    item.numericValue == null -> null
    item.mode == IntensityMode.RIR -> 10.0 - item.numericValue
    else -> item.numericValue
}

/**
 * Nivel de fuego de una marca, de 0 (gris, sin partículas) a 5 (el fallo, ascua y llama). Va de 0 en RPE ≤ 6 (RIR 4) a 4 en RPE 10
 * (RIR 0): los seis niveles del prototipo, interpolados entre medias.
 */
internal fun effortFireLevel(item: IntensityCarouselItem): Float {
    if (item.isFailure) return 5f
    val equivalent = effortRpeEquivalent(item) ?: return 0f
    return (equivalent - 6.0).coerceIn(0.0, 4.0).toFloat()
}

/** La palabra de la marca: cómodo, firme, exigente, muy duro, al límite y todo (el fallo). Nulo en «—». */
internal fun effortWord(item: IntensityCarouselItem): String? {
    if (item.isFailure) return "todo"
    val equivalent = effortRpeEquivalent(item) ?: return null
    return when {
        equivalent < 6.5 -> "cómodo"
        equivalent < 7.5 -> "firme"
        equivalent < 8.5 -> "exigente"
        equivalent < 9.5 -> "muy duro"
        else -> "al límite"
    }
}

/** Número de una marca con coma decimal: «10», «0», «7,5». */
internal fun effortNumberText(value: Double): String =
    if (abs(value - value.roundToInt()) < EPS) value.roundToInt().toString() else String.format(Locale("es"), "%.1f", value)

/** Valor de una marca para la cabecera del slider: «RIR 2», «RPE 7,5», «Fallo» o «—». */
internal fun effortValueLabel(item: IntensityCarouselItem): String = when {
    item.isFailure -> "Fallo"
    item.numericValue == null -> "—"
    item.mode == IntensityMode.RIR -> "RIR ${effortNumberText(item.numericValue)}"
    else -> "RPE ${effortNumberText(item.numericValue)}"
}

/** Descripción hablada del slider: «Esfuerzo RIR 2, exigente», «Esfuerzo Fallo, todo» o «Esfuerzo sin marcar». */
internal fun effortSpokenDescription(item: IntensityCarouselItem?): String {
    if (item == null) return "Esfuerzo sin marcar"
    val word = effortWord(item)
    return "Esfuerzo ${effortValueLabel(item)}" + if (word != null) ", $word" else ""
}

/** Posición (0..1) del centro del pulgar en la marca [index] de [count]: la primera a la izquierda y la última a la derecha. */
internal fun effortThumbFraction(index: Int, count: Int): Float =
    if (count <= 1) 0f else (index.coerceIn(0, count - 1).toFloat() / (count - 1).toFloat())

/** Centro del pulgar en px dentro de la pista: [insetPx] de margen a cada lado (el radio del pulgar más su holgura). */
internal fun effortCenterPx(index: Int, count: Int, trackWidthPx: Float, insetPx: Float): Float =
    insetPx + effortThumbFraction(index, count) * max(0f, trackWidthPx - 2f * insetPx)

/** Marca más cercana a la posición [xPx] de un toque o de un arrastre sobre la pista. Fuera de la pista cae en la primera o la última. */
internal fun effortIndexAtX(xPx: Float, trackWidthPx: Float, insetPx: Float, count: Int): Int {
    if (count <= 1) return 0
    val span = max(1f, trackWidthPx - 2f * insetPx)
    val fraction = ((xPx - insetPx) / span).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/**
 * Marca que debe mostrar el slider al reabrir una serie ya registrada o con borrador: el fallo, o el valor escrito (con coma o con
 * punto, y de la misma unidad que [mode] si la lista mezcla RPE y RIR). Nulo si no hay nada que mostrar: la serie sigue sin marcar.
 */
internal fun restoreEffortIndex(
    items: List<IntensityCarouselItem>,
    reachedFailure: Boolean,
    intensityText: String,
    mode: IntensityMode?,
): Int? {
    if (items.isEmpty()) return null
    if (reachedFailure) return items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }
    val numeric = intensityText.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val hint = mode?.takeIf { it == IntensityMode.RIR || it == IntensityMode.RPE }
    val sameMode = items.indexOfFirst { item ->
        !item.isFailure && item.numericValue != null && abs(item.numericValue - numeric) < EPS && (hint == null || item.mode == hint)
    }
    if (sameMode >= 0) return sameMode
    return items.indexOfFirst { item ->
        !item.isFailure && item.numericValue != null && abs(item.numericValue - numeric) < EPS
    }.takeIf { it >= 0 }
}

/** Cómo guarda una serie AMRAP la marca elegida: llegar al fallo o las repeticiones de reserva. */
internal data class AmrapEffort(val reachedFailure: Boolean, val reserveReps: Int?)

internal fun amrapEffortFor(item: IntensityCarouselItem): AmrapEffort = when {
    item.isFailure -> AmrapEffort(reachedFailure = true, reserveReps = null)
    else -> AmrapEffort(
        reachedFailure = false,
        reserveReps = effortRpeEquivalent(item)?.let { (10.0 - it).roundToInt().coerceAtLeast(0) },
    )
}

/** Número de partículas de fuego de un nivel: 0 en lo cómodo y 16 en el fallo, con los seis niveles del prototipo interpolados. */
internal fun effortParticleCount(level: Float): Int {
    val counts = intArrayOf(0, 0, 2, 6, 11, 16)
    val clamped = level.coerceIn(0f, 5f)
    val low = clamped.toInt().coerceAtMost(4)
    val fraction = clamped - low
    return (counts[low] + (counts[low + 1] - counts[low]) * fraction).roundToInt()
}

/** Segundos que tarda una partícula en subir: 2,4 s en el nivel 2 y 0,8 s en el fallo (más esfuerzo, más rápido). */
internal fun effortParticlePeriodSeconds(level: Float): Float {
    val clamped = level.coerceIn(2f, 5f)
    val periods = floatArrayOf(2.4f, 1.6f, 1.2f, 0.8f)
    val low = (clamped - 2f).toInt().coerceAtMost(2)
    val fraction = (clamped - 2f) - low
    return periods[low] + (periods[low + 1] - periods[low]) * fraction
}

/** Una etiqueta candidata bajo la pista: la marca [index] con su texto y su prioridad (mayor, más importante). */
internal data class EffortLabelCandidate(val index: Int, val text: String, val priority: Int)

/** Una etiqueta ya colocada: empieza en [startPx] de la pista. */
internal data class EffortLabelPlacement(val index: Int, val text: String, val startPx: Float, val widthPx: Float)

/**
 * Las etiquetas que puede llevar la escala: el plan (siempre que exista), «Fallo», el mínimo y el máximo. El plan que coincide con otra
 * de ellas la funde («2 · plan», «Fallo · plan»). Solo la primera y las que cambian de unidad llevan «RIR» o «RPE» delante.
 *
 * [cardLegend] es la leyenda de la tarjeta de Tarjetas (Refinamiento 02): mínimo, plan con su unidad («RIR 2 · plan») y «Fallo», sin el máximo.
 */
internal fun effortLabelCandidates(scale: EffortScale, cardLegend: Boolean = false): List<EffortLabelCandidate> {
    val items = scale.items
    if (items.isEmpty()) return emptyList()
    val failure = items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }
    val top = items.filter { !it.isFailure }.mapNotNull { effortRpeEquivalent(it) }.maxOrNull()
    val maxIndex = top?.let { value ->
        items.indices.firstOrNull { i -> !items[i].isFailure && effortRpeEquivalent(items[i])?.let { abs(it - value) < EPS } == true }
    }
    val firstMode = items.first().mode

    fun body(index: Int, withUnit: Boolean): String {
        val item = items[index]
        return when {
            item.isFailure -> "Fallo"
            item.numericValue == null -> "—"
            withUnit || item.mode != firstMode -> effortValueLabel(item)
            else -> effortNumberText(item.numericValue)
        }
    }

    val plan = scale.planIndex
    val byIndex = linkedMapOf<Int, EffortLabelCandidate>()
    byIndex[0] = EffortLabelCandidate(0, body(0, withUnit = true), priority = 2)
    if (!cardLegend && maxIndex != null && maxIndex != 0) byIndex[maxIndex] = EffortLabelCandidate(maxIndex, body(maxIndex, withUnit = false), priority = 1)
    if (failure != null && failure != 0) byIndex[failure] = EffortLabelCandidate(failure, "Fallo", priority = 3)
    if (plan != null) {
        val existing = byIndex[plan]
        val text = (existing?.text ?: body(plan, withUnit = cardLegend)) + " · plan"
        byIndex[plan] = EffortLabelCandidate(plan, text, priority = 4)
    }
    return byIndex.values.toList()
}

/**
 * Coloca las etiquetas bajo la pista sin que se pisen: cada una, centrada en su marca y sin salirse de la pista; si choca con otra de
 * más prioridad, se descarta (a 360 dp ni al 130 % de letra se amontonan).
 */
internal fun placeEffortLabels(
    candidates: List<EffortLabelCandidate>,
    widthsPx: Map<Int, Float>,
    centersPx: Map<Int, Float>,
    trackWidthPx: Float,
    gapPx: Float,
): List<EffortLabelPlacement> {
    val placed = mutableListOf<EffortLabelPlacement>()
    candidates.sortedByDescending { it.priority }.forEach { candidate ->
        val width = widthsPx[candidate.index] ?: return@forEach
        val center = centersPx[candidate.index] ?: return@forEach
        val start = (center - width / 2f).coerceIn(0f, max(0f, trackWidthPx - width))
        val collides = placed.any { other -> start < other.startPx + other.widthPx + gapPx && other.startPx < start + width + gapPx }
        if (!collides) placed += EffortLabelPlacement(candidate.index, candidate.text, start, width)
    }
    return placed.sortedBy { it.startPx }
}
