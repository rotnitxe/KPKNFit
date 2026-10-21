package com.example.kpkn.ui.components.rings

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.domain.auge.AugeParameters
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * Lógica pura del ajuste manual oficial de los anillos (LV10b): del toque al valor, los pasos de los botones, las palabras de cada
 * tramo, los tonos de un músculo y qué pilar es cada zona del cuerpo. No dibuja nada ni guarda nada: lo que se ajusta lo decide
 * quien monta los componentes (`RingAdjust.kt` y `RingAdjustBody.kt`). Todo se ve en la escala 0–100 de la pantalla.
 */

/** Valor máximo de un ajuste: toda la app los muestra de 0 a 100. */
internal const val RingAdjustMax = 100

/** Lo que suben o bajan los botones − y + de Energía y Columna. */
internal const val RingAdjustDialStep = 5

/** Lo que suben o bajan los botones − y + de un músculo. */
internal const val RingAdjustMuscleStep = 10

/** La háptica suena al cruzar cada múltiplo de 5 al arrastrar. */
internal const val RingAdjustTickEvery = 5

/** Cuánto hay que arrastrar a lo ancho de la fila de un músculo para mover un punto (en dp). */
internal const val RingAdjustFineDragDpPerPoint = 5f

/** Recorta un valor a 0–100. */
internal fun ringAdjustClamp(value: Int): Int = value.coerceIn(0, RingAdjustMax)

/** Sube o baja [value] en [delta] sin salirse de 0–100. */
internal fun ringAdjustStep(value: Int, delta: Int): Int = ringAdjustClamp(value + delta)

/**
 * Valor 0–100 de un toque en el anillo: el ángulo del punto ([dx], [dy] respecto al centro, con y hacia abajo) medido en sentido
 * horario desde arriba, a paso de 1. Arriba es 0, la derecha 25, abajo 50 y la izquierda 75; justo a la izquierda de arriba, 100.
 */
internal fun ringAdjustValueAt(dx: Float, dy: Float): Int {
    val turns = atan2(dx, -dy) / (2f * PI.toFloat())
    val unit = if (turns < 0f) turns + 1f else turns
    return ringAdjustClamp((unit * RingAdjustMax).roundToInt())
}

/** Ángulo del valor, en grados en sentido horario desde arriba: la inversa de [ringAdjustValueAt]. */
internal fun ringAdjustAngleDegrees(value: Int): Float = ringAdjustClamp(value) * 3.6f

/**
 * Valor al arrastrar: el dedo da la vuelta por arriba y el valor no salta de 100 a 0 (ni al revés). Si el valor crudo [raw] está a más
 * de media vuelta del anterior [previous], el arrastre ha cruzado la parte de arriba y el valor se queda en el tope que alcanzó.
 */
internal fun ringAdjustDragValue(previous: Int, raw: Int): Int =
    if (abs(raw - previous) > RingAdjustMax / 2) {
        if (previous > RingAdjustMax / 2) RingAdjustMax else 0
    } else {
        raw
    }

/** ¿Pasar de [old] a [new] cruza un múltiplo de 5? Entonces suena la háptica. */
internal fun ringAdjustCrossedTick(old: Int, new: Int): Boolean =
    old / RingAdjustTickEvery != new / RingAdjustTickEvery

/**
 * ¿El toque cae sobre el anillo? El punto ([dx], [dy] desde el centro) debe estar entre el 45 % y el 100 % de [halfSize] (la mitad del
 * lado del dial): el anillo y el «líquido» sí; el centro, donde van los textos, y las esquinas, no.
 */
internal fun ringAdjustIsOnBand(dx: Float, dy: Float, halfSize: Float): Boolean {
    val distance = hypot(dx, dy)
    return distance >= 0.45f * halfSize && distance <= halfSize
}

/** Valor tras arrastrar [dragPx] a lo ancho de la fila de un músculo, partiendo de [start], a [pxPerPoint] por punto. */
internal fun ringAdjustFineValue(start: Int, dragPx: Float, pxPerPoint: Float): Int =
    ringAdjustClamp(start + (dragPx / pxPerPoint.coerceAtLeast(1f)).toInt())

/** Palabra de Energía según las bandas compartidas. */
internal fun ringAdjustEnergyWord(value: Int): String = when (RecoveryBands.band(value)) {
    RecoveryBand.HIGH -> "Con mucha energía"
    RecoveryBand.NORMAL -> "Energía normal"
    RecoveryBand.MODERATE -> "Algo cansado"
    RecoveryBand.LOW -> "Energía baja"
    RecoveryBand.CRITICAL -> "Muy cansado"
}

/** Palabra de Columna según las bandas compartidas. */
internal fun ringAdjustSpineWord(value: Int): String = when (RecoveryBands.band(value)) {
    RecoveryBand.HIGH -> "Espalda descansada"
    RecoveryBand.NORMAL -> "Carga normal"
    RecoveryBand.MODERATE -> "Algo cargada"
    RecoveryBand.LOW -> "Cargada"
    RecoveryBand.CRITICAL -> "Muy cargada"
}

/** Palabra del canal de un dial: solo Energía y Columna tienen. */
internal fun ringAdjustWord(channel: RingChannel, value: Int): String = when (channel) {
    RingChannel.ENERGIA -> ringAdjustEnergyWord(value)
    RingChannel.COLUMNA -> ringAdjustSpineWord(value)
    RingChannel.MUSCULOS -> ringAdjustMuscleTone(value).word
}

/** Estado muscular derivado de la misma banda que usa el motor. */
internal enum class RingAdjustMuscleTone(val word: String, val color: Color) {
    RECOVERED("recuperado", Color(0xFF43D18C)),
    NORMAL("en rango", Color(0xFF43D18C)),
    MID("algo cargado", Color(0xFFF7CF73)),
    LOADED("cargado", Color(0xFFF49A6E)),
    CRITICAL("muy cargado", Color(0xFFEF4444)),
}

internal fun ringAdjustMuscleTone(value: Int): RingAdjustMuscleTone = when (RecoveryBands.band(value)) {
    RecoveryBand.HIGH -> RingAdjustMuscleTone.RECOVERED
    RecoveryBand.NORMAL -> RingAdjustMuscleTone.NORMAL
    RecoveryBand.MODERATE -> RingAdjustMuscleTone.MID
    RecoveryBand.LOW -> RingAdjustMuscleTone.LOADED
    RecoveryBand.CRITICAL -> RingAdjustMuscleTone.CRITICAL
}

/** «72 %» con espacio inseparable antes del signo. */
internal fun ringAdjustPercent(value: Int): String = "$value\u00A0%"

/** Los degradados de un anillo de Energía o Columna: de su tono oscuro al claro (los mismos que los de la torre luminosa). */
internal fun ringAdjustGradient(channel: RingChannel): Pair<Color, Color> = when (channel) {
    RingChannel.ENERGIA -> Color(0xFFC9A04A) to Color(0xFFF7CF73)
    RingChannel.COLUMNA -> Color(0xFF5E7EC4) to Color(0xFF8FB2FF)
    RingChannel.MUSCULOS -> Color(0xFFC7704A) to Color(0xFFF49A6E)
}

/** Nombre en español de un canal. */
internal fun ringAdjustChannelName(channel: RingChannel): String = when (channel) {
    RingChannel.ENERGIA -> "Energía"
    RingChannel.COLUMNA -> "Columna"
    RingChannel.MUSCULOS -> "Músculos"
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// El cuerpo
// ──────────────────────────────────────────────────────────────────────────────────────────────

/** De qué lado se ve el cuerpo. */
internal enum class RingAdjustBodyView { FRONT, BACK }

/** Los 13 pilares del motor, los que tienen zona en el cuerpo. */
internal val RingAdjustBodyPillars: List<String> = AugeParameters.PILLARS

/** Nombre de un pilar para leerlo en pantalla: solo la primera letra en mayúscula («Erectores espinales»). */
internal fun ringAdjustPillarLabel(pillar: String): String =
    pillar.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Pilar del cuerpo al que corresponde un músculo, o `null` si el músculo no tiene zona (Antebrazo, Aductores, Cuello…). Acepta el
 * pilar tal cual, que es lo que usa el motor, y también los nombres de pantalla de otras listas («Pecho», «Hombros»).
 */
internal fun ringAdjustBodyPillarOf(muscleId: String): String? =
    getAugeMusclePillarId(muscleId).takeIf { it in RingAdjustBodyPillars }

/** Ancho y alto del dibujo del cuerpo, en las unidades de sus formas. */
internal const val RingAdjustBodyWidth = 220f
internal const val RingAdjustBodyHeight = 360f

/**
 * Una zona del cuerpo: el [pillar] que mueve y sus [shapes], cada una como una lista plana x0, y0, x1, y1… del lado DERECHO del dibujo.
 * El dibujo las refleja en el lado izquierdo (en el cuerpo todos los pilares son pares o centrales).
 */
internal class RingAdjustBodyZone(
    val pillar: String,
    val shapes: List<FloatArray>,
)

/** Las formas de un lado ([shapes]) más sus reflejos, que es lo que se pinta y se toca. */
internal fun ringAdjustWithMirrors(shapes: List<FloatArray>): List<FloatArray> =
    shapes + shapes.map { ringAdjustMirror(it) }

/** Refleja una forma respecto al eje vertical del cuerpo. */
internal fun ringAdjustMirror(shape: FloatArray): FloatArray =
    FloatArray(shape.size) { i -> if (i % 2 == 0) RingAdjustBodyWidth - shape[i] else shape[i] }

/**
 * Cierra un contorno dado solo por su lado derecho: lo recorre y sigue por el reflejo en sentido contrario. Con [dropFirst] y
 * [dropLast] se omiten los puntos del eje que el reflejo repetiría.
 */
internal fun ringAdjustJoinMirrored(right: FloatArray, dropFirst: Boolean, dropLast: Boolean): FloatArray {
    val mirrored = ringAdjustMirror(right)
    val count = right.size / 2
    val first = if (dropFirst) 1 else 0
    val last = if (dropLast) count - 1 else count
    val out = ArrayList<Float>(right.size * 2)
    right.forEach { out.add(it) }
    for (i in count - 1 downTo 0) {
        val fromEnd = count - 1 - i
        if (fromEnd < first || fromEnd >= last) continue
        out.add(mirrored[i * 2])
        out.add(mirrored[i * 2 + 1])
    }
    return out.toFloatArray()
}

/** ¿Está el punto (x, y) dentro del polígono dado por [shape] (x0, y0, x1, y1…)? */
internal fun ringAdjustContains(shape: FloatArray, x: Float, y: Float): Boolean {
    val count = shape.size / 2
    var inside = false
    var j = count - 1
    for (i in 0 until count) {
        val xi = shape[i * 2]
        val yi = shape[i * 2 + 1]
        val xj = shape[j * 2]
        val yj = shape[j * 2 + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** Distancia del punto (x, y) al polígono [shape]: 0 si está dentro; si no, la del borde más cercano. */
internal fun ringAdjustDistance(shape: FloatArray, x: Float, y: Float): Float {
    if (ringAdjustContains(shape, x, y)) return 0f
    val count = shape.size / 2
    var best = Float.MAX_VALUE
    var j = count - 1
    for (i in 0 until count) {
        val d = segmentDistance(x, y, shape[j * 2], shape[j * 2 + 1], shape[i * 2], shape[i * 2 + 1])
        if (d < best) best = d
        j = i
    }
    return best
}

private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val abx = bx - ax
    val aby = by - ay
    val lengthSquared = abx * abx + aby * aby
    val t = if (lengthSquared == 0f) 0f else (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (ax + t * abx), py - (ay + t * aby))
}

/** Área del polígono (para desempatar: ante dos zonas a la misma distancia gana la más pequeña, que es la más concreta). */
internal fun ringAdjustArea(shape: FloatArray): Float {
    val count = shape.size / 2
    var sum = 0f
    var j = count - 1
    for (i in 0 until count) {
        sum += shape[j * 2] * shape[i * 2 + 1] - shape[i * 2] * shape[j * 2 + 1]
        j = i
    }
    return abs(sum) / 2f
}

/**
 * Pilar que recibe un toque en (x, y) (en unidades del dibujo del cuerpo): el de la zona que contiene el punto o, si cae fuera de
 * todas, el de la más cercana a menos de [slop] unidades (las zonas pequeñas amplían así su área táctil). Con las zonas a la misma
 * distancia gana la más pequeña. Fuera de [slop] de cualquier zona, `null`.
 */
internal fun ringAdjustBodyZoneAt(zones: List<RingAdjustBodyZone>, x: Float, y: Float, slop: Float): String? {
    var bestPillar: String? = null
    var bestDistance = Float.MAX_VALUE
    var bestArea = Float.MAX_VALUE
    for (zone in zones) {
        for (shape in ringAdjustWithMirrors(zone.shapes)) {
            val distance = ringAdjustDistance(shape, x, y)
            if (distance > slop) continue
            val area = ringAdjustArea(shape)
            val closer = distance < bestDistance - 0.01f
            val tie = abs(distance - bestDistance) <= 0.01f && area < bestArea
            if (closer || tie) {
                bestPillar = zone.pillar
                bestDistance = distance
                bestArea = area
            }
        }
    }
    return bestPillar
}

/** Centro y caja de la forma de la derecha de una zona: dónde se pone su nodo de accesibilidad. */
internal fun ringAdjustZoneBounds(zone: RingAdjustBodyZone): FloatArray {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    zone.shapes.forEach { shape ->
        for (i in 0 until shape.size / 2) {
            val x = shape[i * 2]
            val y = shape[i * 2 + 1]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}

/** Lado del cuerpo en el que está un pilar: Trapecio, Dorsales, Tríceps, Erectores, Glúteos e Isquiosurales, por detrás; el resto, por delante. */
internal fun ringAdjustPreferredView(pillar: String): RingAdjustBodyView =
    if (BackOnlyPillars.contains(pillar)) RingAdjustBodyView.BACK else RingAdjustBodyView.FRONT

private val BackOnlyPillars = setOf("Trapecio", "Dorsales", "Tríceps", "Erectores Espinales", "Glúteos", "Isquiosurales")
