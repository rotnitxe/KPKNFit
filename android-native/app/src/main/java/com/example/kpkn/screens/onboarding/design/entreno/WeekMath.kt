package com.example.kpkn.screens.onboarding.design.entreno

import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.TrainingPlace
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Lógica pura de la semana y del tiempo de sesión del módulo de Entreno v2: qué día es cada número, cómo se
 * reordena la tira, qué lugar le toca a cada día y cómo se pasa de un ángulo del dial a minutos (y de vuelta).
 *
 * Sin Compose ni Android: todo es determinista y se prueba en JVM (`WeekMathTest`, `SessionDialMathTest`).
 * Los días siguen la convención del repo: 1 = lunes … 7 = domingo.
 */

/** Días de la semana. */
const val WEEK_DAY_COUNT = 7

private val DAY_INITIALS = listOf("L", "M", "Mi", "J", "V", "S", "D")
private val DAY_SHORT_NAMES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
private val DAY_FULL_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/** Verdadero si [day] es un día válido (1 = lunes … 7 = domingo). */
fun isWeekDay(day: Int): Boolean = day in 1..WEEK_DAY_COUNT

/**
 * Los siete días en el orden de una semana que arranca en [startDay]: con jueves (4) sale 4, 5, 6, 7, 1, 2, 3.
 * Un inicio fuera de 1..7 (un borrador roto) cuenta como lunes: la tira nunca queda vacía.
 */
fun orderedWeek(startDay: Int): List<Int> {
    val start = if (isWeekDay(startDay)) startDay else 1
    return List(WEEK_DAY_COUNT) { (start - 1 + it) % WEEK_DAY_COUNT + 1 }
}

/** Ranura (0..6) que ocupa [day] en una semana que arranca en [startDay]. Un día inválido da −1. */
fun weekSlotOf(day: Int, startDay: Int): Int = orderedWeek(startDay).indexOf(day)

/**
 * Inicial de un día: L M Mi J V S D. El miércoles es «Mi» (no «X»: una X suelta, sin su nombre al lado, no se lee como un día) y así no
 * se confunde con el martes. Un día inválido da «».
 */
fun dayInitial(day: Int): String = DAY_INITIALS.getOrElse(day - 1) { "" }

/** Nombre corto de tres letras: Lun Mar Mié Jue Vie Sáb Dom. Un día inválido da «». */
fun dayShortName(day: Int): String = DAY_SHORT_NAMES.getOrElse(day - 1) { "" }

/** Nombre completo con mayúscula inicial («Miércoles»). Para una frase se pasa a minúsculas. Un día inválido da «». */
fun dayFullName(day: Int): String = DAY_FULL_NAMES.getOrElse(day - 1) { "" }

/** Unidad del contador de días: «día por semana» con uno, «días por semana» con cualquier otra cantidad (también 0). */
fun daysPerWeekUnit(count: Int): String = if (count == 1) "día por semana" else "días por semana"

/**
 * La cifra grande del contador. Sin días se ve un guion: el cero de Syne es casi redondo y «O días por semana» se lee como una
 * letra, no como una cantidad. (Para TalkBack el contador sigue diciendo «0 días por semana».)
 */
fun weekCounterText(count: Int): String = if (count > 0) count.toString() else "–"

/** Frase del selector de inicio de semana: «La semana empieza el jueves». */
fun weekStartLabel(startDay: Int): String =
    "La semana empieza el ${dayFullName(if (isWeekDay(startDay)) startDay else 1).lowercase()}"

/**
 * Texto de accesibilidad de un día de entreno o de energía: «Lunes, elegido» / «Lunes, sin elegir». Con [strongest] (el día
 * de la sesión más fuerte, el del sol) añade «, tu sesión más fuerte»: así quien no ve el sol también lo sabe.
 */
fun dayChoiceDescription(day: Int, chosen: Boolean, strongest: Boolean = false): String =
    dayFullName(day) + ", " + (if (chosen) "elegido" else "sin elegir") + if (strongest) ", tu sesión más fuerte" else ""

// ─── Reordenar la tira (la «cinta» de la semana) ─────────────────────────────────────────────────────────────

/**
 * Desplazamiento más corto de [from] a [to] sobre un círculo de [period] (por defecto la semana): el resultado cae en
 * `(−period/2, period/2]`. Sirve para animar el inicio de semana por el camino corto (de lunes a jueves son +3 huecos y de
 * lunes a sábado −2, en vez de cinco) aunque la animación anterior no hubiera terminado: la posición es continua.
 */
fun circularShortestDelta(from: Float, to: Float, period: Float = WEEK_DAY_COUNT.toFloat()): Float {
    if (!(period > 0f) || !from.isFinite() || !to.isFinite()) return 0f
    val forward = floatMod(to - from, period)
    return if (forward > period / 2f) forward - period else forward
}

/**
 * Posición (en ranuras) del día [day] cuando el inicio de semana va por [startPos] (un número continuo y circular: 4 = jueves).
 * Con [startPos] entero el día cae en una ranura entera de 0 a 6. Mientras se reordena, un día que sale por un borde
 * vuelve a entrar por el otro: la posición vive en `[−0,5; 6,5)` y la opacidad ([weekConveyorAlpha]) la oculta en el salto.
 */
fun weekConveyorSlot(day: Int, startPos: Float): Float =
    floatMod(day - startPos + 0.5f, WEEK_DAY_COUNT.toFloat()) - 0.5f

/** Opacidad de un día según su ranura: plena dentro de la tira y desvanecida en el medio hueco que pisa cada borde. */
fun weekConveyorAlpha(slot: Float): Float = when {
    slot.isNaN() -> 0f
    slot < 0f -> ((slot + 0.5f) / 0.5f).coerceIn(0f, 1f)
    slot > WEEK_DAY_COUNT - 1f -> ((WEEK_DAY_COUNT - 0.5f - slot) / 0.5f).coerceIn(0f, 1f)
    else -> 1f
}

private fun floatMod(a: Float, b: Float): Float {
    val m = a % b
    val r = if (m < 0f) m + b else m
    return if (r >= b) 0f else r
}

// ─── Lugar de cada día ───────────────────────────────────────────────────────────────────────────────────────

/** Los lugares elegidos en el orden fijo gimnasio, casa, espacios públicos (el orden del enum). */
fun orderedPlaces(places: Set<TrainingPlace>): List<TrainingPlace> = TrainingPlace.entries.filter { it in places }

/** El selector de lugar por día solo existe con dos o más lugares. */
fun showsDayPlaces(places: Set<TrainingPlace>): Boolean = places.size >= 2

/**
 * Lugar que se muestra para [day]: el asignado si sigue entre los [places] elegidos y, si no hay asignación (o quedó
 * obsoleta porque se quitó ese lugar), el primero en orden gimnasio, casa, espacios públicos. Sin lugares da null.
 */
fun placeOfDay(day: Int, places: Set<TrainingPlace>, dayPlaces: Map<Int, TrainingPlace>): TrainingPlace? {
    val ordered = orderedPlaces(places)
    if (ordered.isEmpty()) return null
    return dayPlaces[day]?.takeIf { it in places } ?: ordered.first()
}

/** El lugar siguiente en orden (y vuelta al primero tras el último). Si [current] no está entre [places], el primero. */
fun nextPlace(current: TrainingPlace?, places: Set<TrainingPlace>): TrainingPlace? {
    val ordered = orderedPlaces(places)
    if (ordered.isEmpty()) return null
    val index = ordered.indexOf(current)
    return if (index < 0) ordered.first() else ordered[(index + 1) % ordered.size]
}

// ─── Duración ────────────────────────────────────────────────────────────────────────────────────────────────

/** «45 min», «1 h», «1 h 15 min», «3 h». Lo negativo se lee como 0. */
fun formatDuration(minutes: Int): String {
    val total = minutes.coerceAtLeast(0)
    val hours = total / 60
    val rest = total % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

/** La misma duración para decirla en voz alta: «45 minutos», «1 hora», «1 hora y 15 minutos», «3 horas». */
fun spokenDuration(minutes: Int): String {
    val total = minutes.coerceAtLeast(0)
    val hours = total / 60
    val rest = total % 60
    fun h() = if (hours == 1) "1 hora" else "$hours horas"
    fun m() = if (rest == 1) "1 minuto" else "$rest minutos"
    return when {
        hours == 0 -> m()
        rest == 0 -> h()
        else -> "${h()} y ${m()}"
    }
}

// ─── Dial de reloj ───────────────────────────────────────────────────────────────────────────────────────────

/** Rango del dial del alta: el del paso (de 30 a 180 minutos), para que el reloj y el borrador nunca discrepen. */
val SESSION_DIAL_RANGE: IntRange = EntrenoStepValues.SESSION_MINUTES_MIN..EntrenoStepValues.SESSION_MINUTES_MAX

/** El valor salta de 5 en 5 minutos. */
const val SESSION_DIAL_STEP = EntrenoStepValues.SESSION_MINUTES_STEP

/** Dónde empieza el arco (el mínimo del rango): ángulo de reloj en grados desde las 12 en sentido horario; 210° = abajo a la izquierda. */
const val DIAL_START_DEG = 210f

/** Lo que barre el arco de mínimo a máximo (30 a 180 minutos); los 60° que faltan (abajo) son el hueco. */
const val DIAL_SWEEP_DEG = 300f

private const val FULL_TURN_DEG = 360f

/** Ángulo de reloj normalizado a `[0, 360)`: 0 = las 12, 90 = las 3, 180 = las 6. Lo que no es un número da 0. */
fun normalizeDialAngle(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    return floatMod(degrees, FULL_TURN_DEG)
}

/**
 * Ángulo de reloj de un vector `(dx, dy)` en pantalla (x a la derecha, y hacia ABAJO) respecto al centro del dial:
 * arriba = 0°, derecha = 90°, abajo = 180°, izquierda = 270°.
 */
fun dialAngleOf(dx: Float, dy: Float): Float =
    normalizeDialAngle(Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat())

/** Posición de [minutes] sobre el arco, de 0 (el valor mínimo) a 1 (el máximo). Fuera de rango se acota. */
fun dialFraction(minutes: Float, range: IntRange = SESSION_DIAL_RANGE): Float {
    val span = (range.last - range.first).toFloat()
    if (span <= 0f || minutes.isNaN()) return 0f
    return ((minutes - range.first) / span).coerceIn(0f, 1f)
}

/**
 * Ángulo de reloj (grados, `[0, 360)`) en el que cae [minutes]: con el rango del alta, 30 min a 210° (abajo a la izquierda),
 * 105 min a 0° (arriba) y 180 min a 150° (abajo a la derecha). Fuera de [range] se acota al extremo.
 */
fun angleForMinutes(minutes: Float, range: IntRange = SESSION_DIAL_RANGE): Float =
    normalizeDialAngle(DIAL_START_DEG + dialFraction(minutes, range) * DIAL_SWEEP_DEG)

/** [angleForMinutes] para minutos enteros. */
fun angleForMinutes(minutes: Int, range: IntRange = SESSION_DIAL_RANGE): Float = angleForMinutes(minutes.toFloat(), range)

/**
 * Ajusta [minutes] a la muesca más cercana: múltiplos de [step] contados desde el mínimo del rango, dentro de él. A mitad
 * de camino entre dos muescas gana la de arriba. Con un [step] menor que 1 se usa 1.
 */
fun snapMinutes(minutes: Float, range: IntRange = SESSION_DIAL_RANGE, step: Int = SESSION_DIAL_STEP): Int {
    if (minutes.isNaN()) return range.first
    val size = step.coerceAtLeast(1)
    val clamped = minutes.coerceIn(range.first.toFloat(), range.last.toFloat())
    val notch = ((clamped - range.first) / size).roundToInt()
    return (range.first + notch * size).coerceIn(range.first, range.last)
}

/**
 * Minutos de un ángulo de reloj (el de un toque sobre la esfera): dentro del arco se reparte de forma lineal y se ajusta a
 * [step]; en el hueco de abajo se **satura** en el extremo más cercano (la mitad de la derecha da el máximo y la de la
 * izquierda, el mínimo; el centro exacto del hueco, el mínimo). Así nunca se sale del rango ni da la vuelta de golpe en
 * cuanto el dedo pisa el hueco. Para arrastrar usa [DialDragTracker], que además no salta de un extremo al otro.
 */
fun minutesForAngle(
    angleDeg: Float,
    range: IntRange = SESSION_DIAL_RANGE,
    step: Int = SESSION_DIAL_STEP,
): Int {
    if (!angleDeg.isFinite()) return snapMinutes(range.first.toFloat(), range, step)
    val offset = normalizeDialAngle(angleDeg - DIAL_START_DEG)
    val fraction = when {
        offset <= DIAL_SWEEP_DEG -> offset / DIAL_SWEEP_DEG
        // Hueco: grados pasados del final del arco; menos de la mitad del hueco = más cerca del máximo.
        offset - DIAL_SWEEP_DEG < (FULL_TURN_DEG - DIAL_SWEEP_DEG) / 2f -> 1f
        else -> 0f
    }
    val span = (range.last - range.first).toFloat()
    return snapMinutes(range.first + fraction * span, range, step)
}

/**
 * La muesca siguiente ([direction] > 0) o anterior (< 0) a [current], dentro de [range]. Si [current] no está en una
 * muesca (62 con paso 5) salta a la contigua en esa dirección (65 hacia arriba, 60 hacia abajo). Sirve para las acciones
 * de accesibilidad «Aumentar / Disminuir».
 */
fun stepMinutes(
    current: Int,
    direction: Int,
    range: IntRange = SESSION_DIAL_RANGE,
    step: Int = SESSION_DIAL_STEP,
): Int {
    val size = step.coerceAtLeast(1)
    val clamped = current.coerceIn(range.first, range.last)
    val position = (clamped - range.first).toFloat() / size
    val notch = when {
        direction > 0 -> floor(position).toInt() + 1
        direction < 0 -> ceil(position).toInt() - 1
        else -> position.roundToInt()
    }
    return (range.first + notch * size).coerceIn(range.first, range.last)
}

/** Los atajos de texto bajo el dial: 30 · 45 · 60 · 90 · 120, los que caen en una muesca del rango. */
fun sessionShortcuts(range: IntRange = SESSION_DIAL_RANGE, step: Int = SESSION_DIAL_STEP): List<Int> {
    val size = step.coerceAtLeast(1)
    return listOf(30, 45, 60, 90, 120).filter { it in range && (it - range.first) % size == 0 }
}

/**
 * Seguimiento de UN arrastre sobre el dial. No convierte cada punto en un valor por separado: acumula cuánto ha girado el
 * dedo (diferencias cortas entre puntos consecutivos) y lleva ese giro, **sin acotar**, a lo largo del arco. El valor es el
 * de esa posición acotada al arco. Así:
 *  - el pomo sigue al dedo mientras este va por el arco y se queda en el extremo mientras el dedo pisa el hueco o da la
 *    vuelta por el otro lado (nunca salta de 180 a 20 ni al revés);
 *  - al volver por donde vino, el pomo reengancha justo cuando el dedo vuelve al extremo del arco.
 *
 * El primer punto ([begin]) sí es absoluto: tocar la esfera pone el valor de ese ángulo (en el hueco, el extremo más cercano).
 * Quien llama debe ignorar los puntos pegados al centro del dial, donde el ángulo es inestable.
 */
class DialDragTracker(
    private val range: IntRange = SESSION_DIAL_RANGE,
    private val step: Int = SESSION_DIAL_STEP,
) {
    private var lastAngle = 0f
    private var position = 0f

    /** Primer contacto en [angleDeg] (ángulo de reloj): devuelve los minutos de ese punto. */
    fun begin(angleDeg: Float): Int {
        lastAngle = normalizeDialAngle(angleDeg)
        val offset = normalizeDialAngle(lastAngle - DIAL_START_DEG)
        // Posición continua: dentro del arco es el propio desplazamiento; en el hueco se prolonga por el extremo más cercano
        // (pasado el final = mayor que el barrido; antes del inicio = negativa), que es lo que mantiene el giro coherente.
        position = when {
            offset <= DIAL_SWEEP_DEG -> offset
            offset - DIAL_SWEEP_DEG < (FULL_TURN_DEG - DIAL_SWEEP_DEG) / 2f -> offset
            else -> offset - FULL_TURN_DEG
        }
        return minutesAtPosition()
    }

    /** El dedo pasa a [angleDeg]: suma el giro corto desde el punto anterior y devuelve los minutos de la posición acotada. */
    fun move(angleDeg: Float): Int {
        val angle = normalizeDialAngle(angleDeg)
        position += circularShortestDelta(lastAngle, angle, FULL_TURN_DEG)
        lastAngle = angle
        return minutesAtPosition()
    }

    private fun minutesAtPosition(): Int {
        val fraction = (position / DIAL_SWEEP_DEG).coerceIn(0f, 1f)
        val span = (range.last - range.first).toFloat()
        return snapMinutes(range.first + fraction * span, range, step)
    }

    /** Posición actual en grados desde el inicio del arco, sin acotar (para pruebas). */
    internal fun rawPosition(): Float = position
}

/** Distancia angular absoluta (0..180) entre dos ángulos de reloj; útil para atenuar lo que queda bajo la aguja. */
fun dialAngularDistance(a: Float, b: Float): Float = abs(circularShortestDelta(a, b, FULL_TURN_DEG))
