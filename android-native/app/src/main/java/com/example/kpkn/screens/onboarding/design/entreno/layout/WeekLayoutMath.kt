package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * La parte pura del tablero de la semana: orden de las ranuras, qué ranura queda bajo un punto, cómo cambia la
 * colocación al mover una sesión y cuánto se desplaza la tira al arrastrar cerca de sus bordes. Todo es determinista
 * y no toca Compose más allá de `Rect`, así que se prueba sin dibujar.
 *
 * Convención del repositorio: los días van de 1 (lunes) a 7 (domingo).
 */

/** Días de una semana. */
internal const val WEEK_DAY_COUNT = 7

/**
 * Los siete días en el orden de la tira: [weekStartDay] primero y el resto a continuación, dando la vuelta
 * (con inicio en jueves: 4, 5, 6, 7, 1, 2, 3). Un inicio fuera de 1..7 cuenta como lunes.
 */
fun slotOrder(weekStartDay: Int): List<Int> {
    val start = if (weekStartDay in 1..WEEK_DAY_COUNT) weekStartDay else 1
    return List(WEEK_DAY_COUNT) { index -> (start - 1 + index) % WEEK_DAY_COUNT + 1 }
}

/**
 * La ranura (día) que queda bajo el punto `(x, y)`, o null si ninguna.
 *
 * [slopX] y [slopY] ensanchan cada ranura por los cuatro lados (tolerancia del dedo y huecos entre columnas). Si el
 * punto cae en varias ranuras ensanchadas gana la de centro más cercano; con empate, el día menor. Un punto que no
 * es un número o ranuras sin área no cuentan.
 */
fun hitSlot(
    x: Float,
    y: Float,
    slotRects: Map<Int, Rect>,
    slopX: Float = 0f,
    slopY: Float = slopX,
): Int? {
    if (x.isNaN() || y.isNaN()) return null
    val padX = max(0f, slopX)
    val padY = max(0f, slopY)
    var best: Int? = null
    var bestScore = Float.POSITIVE_INFINITY
    for ((day, rect) in slotRects) {
        if (rect.width <= 0f || rect.height <= 0f) continue
        if (x < rect.left - padX || x > rect.right + padX || y < rect.top - padY || y > rect.bottom + padY) continue
        val dx = x - rect.center.x
        val dy = y - rect.center.y
        val score = dx * dx + dy * dy
        val current = best
        if (score < bestScore || (score == bestScore && current != null && day < current)) {
            best = day
            bestScore = score
        }
    }
    return best
}

/**
 * La colocación (día → id de sesión) tras llevar [sessionId] a [toDay]:
 *  - a un día libre: la sesión cambia de día y el suyo queda libre;
 *  - a un día ocupado: las dos sesiones se **intercambian**;
 *  - al mismo día en que ya está: no cambia nada (devuelve el mismo mapa).
 *
 * Una sesión que no está colocada o un día fuera de 1..7 tampoco cambian nada. Nunca modifica [assignment].
 */
fun swapAssignment(assignment: Map<Int, String>, sessionId: String, toDay: Int): Map<Int, String> {
    if (toDay !in 1..WEEK_DAY_COUNT) return assignment
    val fromDay = assignment.entries.firstOrNull { it.value == sessionId }?.key ?: return assignment
    if (fromDay == toDay) return assignment
    val occupant = assignment[toDay]
    val result = LinkedHashMap<Int, String>(assignment)
    result.remove(fromDay)
    result[toDay] = sessionId
    if (occupant != null) result[fromDay] = occupant
    return result
}

/**
 * La parte de la colocación que el tablero puede dibujar: solo días 1..7, solo sesiones conocidas ([knownIds]) y cada
 * sesión una única vez (si venía repetida, se queda con el primer día en el orden de la tira [order]).
 */
internal fun sanitizeAssignment(assignment: Map<Int, String>, knownIds: Set<String>, order: List<Int>): Map<Int, String> {
    val result = LinkedHashMap<Int, String>()
    val used = HashSet<String>()
    for (day in order) {
        val id = assignment[day] ?: continue
        if (id in knownIds && used.add(id)) result[day] = id
    }
    return result
}

/**
 * Cuánto empuja el borde: de 0 (el dedo fuera de las zonas de borde) a −1 (pegado al inicio o más allá) o 1 (pegado al
 * final o más allá), con rampa lineal dentro de la zona de borde de ancho [edge]. Si el borde no cabe dos veces en el
 * ancho visible [viewport], se acorta a la mitad para que las dos zonas nunca se solapen. Entradas inválidas dan 0.
 */
fun autoScrollFraction(pointer: Float, viewport: Float, edge: Float): Float {
    if (pointer.isNaN() || !(viewport > 0f) || !(edge > 0f) || viewport.isInfinite()) return 0f
    val zone = min(edge, viewport / 2f)
    return when {
        pointer < zone -> -((zone - pointer) / zone).coerceIn(0f, 1f)
        pointer > viewport - zone -> ((pointer - (viewport - zone)) / zone).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** El mayor salto de tiempo que cuenta un fotograma: tras un tirón del sistema no se desplaza «de golpe». */
private const val MAX_FRAME_SECONDS = 0.05f

/**
 * Desplazamiento de la tira en este fotograma mientras se arrastra una ficha: negativo hacia el inicio, positivo hacia
 * el final y 0 con el dedo lejos de los bordes. [pointer] es la posición del dedo dentro del ancho visible [viewport]
 * (0 = borde izquierdo), [edge] el ancho de la zona de borde, [maxSpeed] la velocidad máxima (px por segundo) y
 * [dtSeconds] lo que dura el fotograma.
 */
fun autoScrollDelta(pointer: Float, viewport: Float, edge: Float, maxSpeed: Float, dtSeconds: Float): Float {
    if (!(maxSpeed > 0f) || !(dtSeconds > 0f) || maxSpeed.isInfinite() || dtSeconds.isNaN()) return 0f
    return autoScrollFraction(pointer, viewport, edge) * maxSpeed * min(dtSeconds, MAX_FRAME_SECONDS)
}

// ---------------------------------------------------------------- mini-semana de un reparto

/** Índices de ranura (0 = primer día de la semana) de los días de entreno cuando el reparto no coincide con los días actuales. */
private val SPREAD_PATTERNS: List<List<Int>> = listOf(
    emptyList(),
    listOf(0),
    listOf(0, 3),
    listOf(0, 2, 4),
    listOf(0, 1, 3, 4),
    listOf(0, 1, 2, 4, 5),
    listOf(0, 1, 2, 3, 4, 5),
    listOf(0, 1, 2, 3, 4, 5, 6),
)

/**
 * Los días de la semana (1..7, en orden de la tira) en que cae cada título de un reparto de [dayCount] días. Si los
 * días de entreno actuales ([currentDays]) son justo tantos como el reparto, son esos mismos (se ve cómo caería en su
 * semana); si no, se reparten con un patrón típico (3 días → 1.º, 3.º y 5.º). Más de siete días se recortan a siete.
 */
internal fun splitPatternDays(dayCount: Int, weekStartDay: Int, currentDays: Collection<Int>): List<Int> {
    val count = dayCount.coerceIn(0, WEEK_DAY_COUNT)
    if (count == 0) return emptyList()
    val order = slotOrder(weekStartDay)
    val current = order.filter { it in currentDays }
    if (current.size == count) return current
    return SPREAD_PATTERNS[count].map { order[it] }
}

/**
 * Un índice de color por cada título de un reparto: el mismo título lleva siempre el mismo índice y los títulos nuevos
 * toman el siguiente en orden de aparición (`Torso, Pierna, Torso, Pierna` → 0, 1, 0, 1). Se comparan sin distinguir
 * mayúsculas ni espacios de más. El índice es de la paleta de [paletteSize] colores y da la vuelta si hay más títulos.
 */
internal fun splitTitleColorIndexes(titles: List<String>, paletteSize: Int): List<Int> {
    if (paletteSize <= 0) return titles.map { 0 }
    val seen = LinkedHashMap<String, Int>()
    return titles.map { title ->
        val key = title.trim().lowercase()
        seen.getOrPut(key) { seen.size } % paletteSize
    }
}

// ---------------------------------------------------------------- ancho de las columnas

/** Lo más que crece una columna cuando las siete caben (más ancho que esto la tira quedaría desproporcionada). */
private const val ALL_FIT_MAX_GROWTH = 1.35f

/** La siguiente columna debe asomar al menos esta fracción; si asoma menos, se estrechan un poco para que asome [PEEK_TARGET]. */
private const val PEEK_MIN = 0.35f
private const val PEEK_TARGET = 0.4f

/** Cuánto pueden estrecharse como mucho para conseguir ese asomo (nunca por debajo de este tanto del ancho base). */
private const val PEEK_MAX_SHRINK = 0.92f

/**
 * Ancho de una columna de la tira, con [viewport] de ancho visible, [base] de ancho cómodo, [gap] entre columnas y [count]
 * columnas (las medidas pueden ir en dp o en lo que se quiera: solo importan sus proporciones):
 *  - si las [count] caben con su ancho base, se reparten el ancho (sin crecer más de un 35 %): la tira no se desplaza;
 *  - si no, y la última columna que se ve asoma muy poco (menos de un 35 %), las columnas se estrechan un pelo (como mucho
 *    un 8 %) para que la siguiente asome casi la mitad: es la pista de que la tira sigue, sin barra de desplazamiento;
 *  - en cualquier otro caso, el ancho base.
 */
internal fun stripColumnWidth(viewport: Float, base: Float, gap: Float, count: Int): Float {
    if (count <= 0 || !(viewport > 0f) || !(base > 0f) || viewport.isInfinite()) return base
    val safeGap = max(0f, gap)
    val fit = (viewport - safeGap * (count - 1)) / count
    if (fit >= base) return min(fit, base * ALL_FIT_MAX_GROWTH)
    val visible = (viewport + safeGap) / (base + safeGap)
    val full = floor(visible)
    if (full < 1f || visible - full >= PEEK_MIN) return base
    return max((viewport + safeGap) / (full + PEEK_TARGET) - safeGap, base * PEEK_MAX_SHRINK)
}
