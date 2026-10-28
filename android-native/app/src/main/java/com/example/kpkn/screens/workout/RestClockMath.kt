package com.example.kpkn.screens.workout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Las tres formas del reloj de descanso (LV6, F1): la Torre de RINGS (tres elipses que se llenan de abajo arriba), la Pista (un
 * estadio) y la Espiral (seis semivueltas desde el centro). Todas se llenan con el mismo progreso del descanso.
 */
enum class RestClockShape(val label: String) {
    TOWER("Torre"),
    TRACK("Pista"),
    SPIRAL("Espiral"),
}

/** Un anillo de la Torre, en las unidades del dibujo (caja de 300 × 280): centro vertical, radios y grosor del trazo. */
internal data class RestTowerRing(val cy: Float, val rx: Float, val ry: Float, val stroke: Float)

/** Dónde está el corredor sobre la forma (unidades del dibujo) y hacia dónde mira: +1 a la derecha, −1 a la izquierda. */
internal data class RestRunnerPoint(val x: Float, val y: Float, val facing: Float)

/** La parte del lienzo de 300 × 280 que ocupa una forma, con margen para su halo y para el corredor: es lo que se escala a la caja. */
internal data class RestViewBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Geometría y cuentas del reloj de descanso. Todo puro (sin Android ni Compose) para poder probarlo: el llenado de cada forma, la
 * posición del corredor y el total del descanso. El tiempo SIEMPRE sale del restante del controlador; aquí solo se convierte en
 * dibujo.
 */
internal object RestClockMath {
    /** Caja del dibujo (la de los prototipos F1 y F3). */
    const val VIEW_W = 300f
    const val VIEW_H = 280f
    const val CENTER_X = 150f

    /** Los tres anillos de la Torre, de abajo arriba: Columna, Músculos y Energía. */
    val towerRings: List<RestTowerRing> = listOf(
        RestTowerRing(cy = 220f, rx = 130f, ry = 38f, stroke = 18f),
        RestTowerRing(cy = 150f, rx = 98f, ry = 28f, stroke = 16f),
        RestTowerRing(cy = 88f, rx = 66f, ry = 20f, stroke = 14f),
    )

    /** Pista: dos rectas de 140 y dos semicírculos de radio 70, de (80, 70) en sentido horario. */
    const val TRACK_EDGE = 140f
    const val TRACK_RADIUS = 70f

    /** Radios de las seis semivueltas de la Espiral (el k-ésimo mide 12 · (k + 1)). */
    const val SPIRAL_ARCS = 6
    const val SPIRAL_STEP = 12f

    /** Lo que sobresale el corredor, a escala 1, por encima de su punto (la cabeza incluida) y a cada lado, en unidades del dibujo. */
    const val RUNNER_UP = 30f
    const val RUNNER_SIDE = 8f

    /**
     * Caja de cada forma dentro del lienzo de 300 × 280 (los prototipos F1 y F3 las dibujan en ese lienzo, con margen de sobra):
     * recortada a lo que la forma ocupa de verdad, con su halo y el corredor, para que la forma use el sitio que tiene. Las tres
     * quedan centradas en horizontal sobre x = 150. Con el corredor más grande que el de F1 ([runnerScale] mayor que 1, el de la
     * tarjeta) la caja se agranda lo que él sobresale de más, por arriba y a los lados, para que no se le corte la cabeza.
     */
    fun viewBox(shape: RestClockShape, runnerScale: Float = 1f): RestViewBox {
        val base = when (shape) {
            RestClockShape.TOWER -> RestViewBox(left = 0f, top = 30f, width = 300f, height = 244f)
            RestClockShape.TRACK -> RestViewBox(left = -8f, top = 36f, width = 316f, height = 192f)
            RestClockShape.SPIRAL -> RestViewBox(left = 64f, top = 22f, width = 172f, height = 190f)
        }
        val extra = (runnerScale - 1f).coerceAtLeast(0f)
        return RestViewBox(
            left = base.left - RUNNER_SIDE * extra,
            top = base.top - RUNNER_UP * extra,
            width = base.width + 2f * RUNNER_SIDE * extra,
            height = base.height + RUNNER_UP * extra,
        )
    }

    /** Perímetro del estadio: 2 · 140 + 2π · 70. */
    val trackLength: Float = (2.0 * TRACK_EDGE + 2.0 * PI * TRACK_RADIUS).toFloat()

    /** Largo de la Espiral: π · (12 + 24 + 36 + 48 + 60 + 72). */
    val spiralLength: Float = (PI * (12 + 24 + 36 + 48 + 60 + 72)).toFloat()

    /** Progreso del descanso: (total − restante) / total, entre 0 y 1. Un total de 0 se cuenta como 1 s. */
    fun progress(totalSeconds: Int, remainingSeconds: Int): Float {
        val total = totalSeconds.coerceAtLeast(1)
        val remaining = remainingSeconds.coerceIn(0, total)
        return ((total - remaining).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Relleno (0–1) del anillo [ring] de la Torre con el progreso [progress]: cada uno se lleva un tercio del descanso. */
    fun towerFill(progress: Float, ring: Int): Float =
        (3f * progress.coerceIn(0f, 1f) - ring).coerceIn(0f, 1f)

    /** El anillo que se está llenando: el primero que aún no está lleno (el de arriba si ya están todos). */
    fun activeRing(progress: Float): Int = when {
        towerFill(progress, 0) < 1f -> 0
        towerFill(progress, 1) < 1f -> 1
        else -> 2
    }

    /** Perímetro de una elipse (aproximación de Ramanujan, la de los prototipos). */
    fun ellipsePerimeter(rx: Float, ry: Float): Float {
        val a = rx.toDouble()
        val b = ry.toDouble()
        return (PI * (3.0 * (a + b) - sqrt((3.0 * a + b) * (a + 3.0 * b)))).toFloat()
    }

    /** Relleno (0–1) de las formas de un solo trazo (Pista y Espiral): el propio progreso. */
    fun singleFill(progress: Float): Float = progress.coerceIn(0f, 1f)

    /**
     * Total del descanso en segundos: lo que dura el descanso entero, ajustes de ±15 s incluidos. Sale de cuándo empezó y de cuándo
     * acaba. Sin el inicio guardado (descansos anteriores a este campo, pruebas) se usa [activeSeconds]. Nunca baja de 1.
     */
    fun totalSeconds(startedAtMs: Long, endsAtMs: Long, activeSeconds: Int): Int {
        val fromClock = if (startedAtMs > 0L && endsAtMs > startedAtMs) {
            ((endsAtMs - startedAtMs + 500L) / 1000L).toInt()
        } else {
            0
        }
        return (if (fromClock > 0) fromClock else activeSeconds).coerceAtLeast(1)
    }

    /** Lo que lee TalkBack: «Descanso: quedan 2 minutos y 41 segundos». Sin minutos o sin segundos, solo lo que hay. */
    fun spokenRemaining(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        val minutes = if (m == 1) "1 minuto" else "$m minutos"
        val secs = if (s == 1) "1 segundo" else "$s segundos"
        return when {
            m == 0 -> "Descanso: quedan $secs"
            s == 0 -> "Descanso: quedan $minutes"
            else -> "Descanso: quedan $minutes y $secs"
        }
    }

    /** Fracción de vuelta (0 ≤ u < 1) de cualquier número. */
    fun wrap01(u: Float): Float = u - floor(u)

    /**
     * Posición del corredor sobre la Torre. [ringPos] elige el anillo (0 abajo, 2 arriba) y, con decimales, se mueve entre dos
     * mientras salta de uno al siguiente. [u] es la fracción de vuelta, en sentido horario desde el extremo derecho.
     */
    fun towerPoint(ringPos: Float, u: Float): RestRunnerPoint {
        val clamped = ringPos.coerceIn(0f, towerRings.lastIndex.toFloat())
        val i0 = floor(clamped).toInt()
        val i1 = min(i0 + 1, towerRings.lastIndex)
        val f = clamped - i0
        val a = towerRings[i0]
        val b = towerRings[i1]
        val cy = a.cy + (b.cy - a.cy) * f
        val rx = a.rx + (b.rx - a.rx) * f
        val ry = a.ry + (b.ry - a.ry) * f
        val theta = 2.0 * PI * wrap01(u)
        val s = sin(theta)
        return RestRunnerPoint(
            x = (CENTER_X + rx * cos(theta)).toFloat(),
            y = (cy + ry * s).toFloat(),
            facing = if (s < 0.0) 1f else -1f,
        )
    }

    /** Posición del corredor sobre la Pista, con [u] como fracción del perímetro desde (80, 70) en sentido horario. */
    fun trackPoint(u: Float): RestRunnerPoint {
        val s = wrap01(u) * trackLength
        val r = TRACK_RADIUS
        val half = (PI * r).toFloat()
        return when {
            s < TRACK_EDGE -> RestRunnerPoint(80f + s, 70f, 1f)
            s < TRACK_EDGE + half -> {
                val phi = -PI / 2.0 + (s - TRACK_EDGE) / r
                RestRunnerPoint(
                    x = (220.0 + r * cos(phi)).toFloat(),
                    y = (140.0 + r * sin(phi)).toFloat(),
                    facing = if (sin(phi) < 0.0) 1f else -1f,
                )
            }
            s < 2f * TRACK_EDGE + half -> RestRunnerPoint(220f - (s - TRACK_EDGE - half), 210f, -1f)
            else -> {
                val phi = PI / 2.0 + (s - 2f * TRACK_EDGE - half) / r
                RestRunnerPoint(
                    x = (80.0 + r * cos(phi)).toFloat(),
                    y = (140.0 + r * sin(phi)).toFloat(),
                    facing = if (sin(phi) < 0.0) 1f else -1f,
                )
            }
        }
    }

    /**
     * Posición del corredor sobre la Espiral, con [u] como fracción de su largo desde el centro. Las semivueltas pares giran
     * alrededor de (150, 140), de arriba a abajo; las impares alrededor de (150, 128), de abajo a arriba.
     */
    fun spiralPoint(u: Float): RestRunnerPoint {
        var s = wrap01(u) * spiralLength
        var k = 0
        while (k < SPIRAL_ARCS) {
            val r = SPIRAL_STEP * (k + 1)
            val arc = (PI * r).toFloat()
            if (s <= arc || k == SPIRAL_ARCS - 1) {
                val even = k % 2 == 0
                val cy = if (even) 140.0 else 128.0
                val start = if (even) -PI / 2.0 else PI / 2.0
                val phi = start + min(s, arc) / r
                return RestRunnerPoint(
                    x = (150.0 + r * cos(phi)).toFloat(),
                    y = (cy + r * sin(phi)).toFloat(),
                    facing = if (sin(phi) < 0.0) 1f else -1f,
                )
            }
            s -= arc
            k++
        }
        return RestRunnerPoint(150f, 128f, 1f)
    }

    /**
     * Forma de un descanso: la que la persona eligió con las pestañas o, si no eligió, la que toca por su orden en la sesión
     * (Torre, Pista, Espiral y otra vez). [restOrdinal] es el número de series de trabajo hechas, que sube con cada descanso.
     */
    fun shapeFor(restOrdinal: Int, chosen: RestClockShape?): RestClockShape =
        chosen ?: RestClockShape.entries[(restOrdinal - 1).coerceAtLeast(0) % RestClockShape.entries.size]

    /**
     * Segundos que quedan al aplicar un descanso sugerido: el tiempo ya transcurrido se conserva (no se reinicia) y el total pasa a
     * ser el sugerido. Si el descanso ya superó la sugerencia, queda en 0.
     */
    fun remainingAfterSuggestion(totalSeconds: Int, remainingSeconds: Int, suggestedSeconds: Int): Int {
        val elapsed = (totalSeconds - remainingSeconds).coerceAtLeast(0)
        return (suggestedSeconds - elapsed).coerceAtLeast(0)
    }
}

/** Total del descanso (con los ajustes de ±15 s) tal como lo guarda el estado del descanso. */
internal fun WorkoutRestModalState.totalRestSeconds(): Int =
    RestClockMath.totalSeconds(startedAtMs = startedAtMs, endsAtMs = endsAtMs, activeSeconds = activeSeconds)
