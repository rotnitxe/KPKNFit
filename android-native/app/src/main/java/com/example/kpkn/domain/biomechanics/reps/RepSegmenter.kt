package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RangeReference
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceSource
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceUse
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.signal.Autocorrelation
import com.example.kpkn.domain.biomechanics.signal.Derivative
import com.example.kpkn.domain.biomechanics.signal.Peak
import com.example.kpkn.domain.biomechanics.signal.PeakFinder
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Cómo se reparte el conteo entre los lados del cuerpo (§6, paso 9). */
enum class SideMode {
    /** Los dos lados a la vez (curl con barra, remo, elevaciones simétricas): se cuenta el lado de más confianza. */
    BOTH_SYNC,

    /** Uno y otro lado por turnos (curl alterno): se cuenta por lado y el total es la suma. */
    ALTERNATING,

    /** Un solo lado trabaja (la amplitud del otro es < 0,3×): se cuenta ese lado. */
    SINGLE,

    /** Perfil por lado con solo el lado cercano fiable: no se sabe qué lado trabaja (confianza máx. MEDIA). */
    NEAR_ONLY,

    /** El perfil no cuenta por lado, o la señal es el disco (una sola barra). */
    NOT_APPLICABLE,
}

/**
 * Una repetición detectada. [leftValley], [peak] y [rightValley] son índices de la serie: el reposo anterior, el giro
 * (extremo lejano) y el reposo posterior (`null` si la serie acaba antes de volver). [amplitudeFraction] es a =
 * min(aUp, aDn): el recorrido relativo a la amplitud de una repetición completa (la de la serie o, si es mayor, la de la
 * referencia de recorrido, M4). [miniSet] numera las mini-series (0 si no hay descansos largos entre repeticiones).
 * [zone] (M4): la zona muscular de una PARTIAL. [amplitude] (M4): el recorrido en la unidad de la señal (a por la amplitud
 * completa); NaN en las segmentaciones hechas a mano de las pruebas.
 */
data class RepCycle(
    val index: Int,
    val kind: RepKind,
    val partialKind: PartialKind?,
    val side: Side?,
    val leftValley: Int,
    val peak: Int,
    val rightValley: Int?,
    val amplitudeFraction: Double,
    val turnInGap: Boolean,
    val miniSet: Int = 0,
    val zone: PartialZone? = null,
    val amplitude: Double = Double.NaN,
)

/**
 * Un pico que NO es repetición. [reason]: `noise` (recorrido < 30 %), `nonrep_walk` (caminando), `nonrep_horizontal` (la
 * barra va en horizontal: sacarla, caminar con ella, recolocarla), `setup` (preparación o recogida), `rerack` (recolocar
 * la barra tras la serie), `setdown` (dejar la barra abajo al fallar o acabar) y `censored` (M4: un ciclo cortado por el
 * principio o el final del vídeo mientras la señal aún se movía; no se sabe cómo acabó y la confianza lo trata como borde
 * censurado).
 */
data class DiscardedCycle(val peak: Int, val reason: String)

/**
 * Por qué un ciclo cuenta como dudoso (n_amb, §6). M3 los separa porque no todos pueden cambiar el número de completas:
 * - [NEAR_FULL]: recorrido a cerca del 0,70; el ciclo puede pasar de completo a parcial o al revés (duda por amplitud).
 * - [NEAR_PARTIAL]: recorrido cerca del 0,30; solo puede pasar de ruido a parcial.
 * - [TURN_IN_GAP]: el giro cae junto a un hueco rellenado; un hueco largo puede esconder un valle o un pico.
 * - [PROMINENCE]: un pico con la prominencia a ±15 % del umbral de picos, aceptado o rechazado por poco.
 * - [SIDES]: los dos lados cuentan distinto.
 * - [SHORT_FULL] (M4): un ciclo más corto que el 0,70 que se cuenta como completo porque su caída no es clara (sin una
 *   referencia estable, o aislado entre dos completas y cerca del 0,70): puede ser una parcial.
 */
enum class AmbiguityKind { NEAR_FULL, NEAR_PARTIAL, TURN_IN_GAP, PROMINENCE, SIDES, SHORT_FULL }

/**
 * Un ciclo dudoso: su tipo, la altura de su pico sobre el reposo en fracciones de Afull (e = (rd[p] − Lrest)/Afull; NaN si
 * no tiene pico propio, como [AmbiguityKind.SIDES]) y, en un giro en hueco, la duración del tramo rellenado más largo que
 * toca la ventana del giro ([gapS], en segundos; 0 en los demás tipos).
 */
data class AmbiguityItem(val kind: AmbiguityKind, val height: Double, val gapS: Double = 0.0) {
    /**
     * Un dudoso que no puede cambiar el número de repeticiones completas (duda «por ruido»):
     * - un parcial cerca del 0,30 o un pico por poco cuyo pico queda a menos de [maxHeight]·Afull sobre el reposo: es un rizo
     *   del reposo, no un giro (una completa necesita a ≥ 0,70);
     * - un giro junto a un hueco de como mucho [maxGapS]: el ciclo se ve a los dos lados del hueco, y en tan poco tiempo no cabe
     *   la bajada y la subida de una repetición; a lo sumo recorta un poco el pico, y eso ya lo vigila [NEAR_FULL].
     * Un ciclo cerca del 0,70, un giro en un hueco largo o dos lados que no coinciden sí pueden cambiarlo.
     */
    fun isCountPreserving(maxHeight: Double, maxGapS: Double): Boolean = when (kind) {
        AmbiguityKind.NEAR_PARTIAL, AmbiguityKind.PROMINENCE -> height.isFinite() && height < maxHeight
        AmbiguityKind.TURN_IN_GAP -> gapS <= maxGapS
        AmbiguityKind.NEAR_FULL, AmbiguityKind.SIDES, AmbiguityKind.SHORT_FULL -> false
    }
}

/**
 * Lo que sostiene (o no) el número de parciales de una serie (M4), para su confianza propia (`RepConfidence.partialLevel`).
 *
 * @property stableReference la amplitud completa con la que se clasificó es estable: la de fuera (referencia de recorrido)
 *   o la de la serie con al menos `PartialsTuning.minStableFull` completas.
 * @property referenceApplied la referencia de fuera superó la amplitud de la serie y se clasificó contra ella.
 * @property externalSource el origen de esa referencia, si se aplicó.
 * @property margin el margen que se exigió a una parcial aislada entre dos completas (en fracción de la amplitud completa).
 * @property borderline parciales cerca de un umbral (en la franja de dudosos, `ambiguityMargin`, del 0,30 o del 0,70).
 * @property shortFull ciclos cortos contados como completos por no tener una caída clara (pueden ser parciales).
 * @property fullCycles completas de la serie en las que se apoya la referencia interna.
 */
data class PartialEvidence(
    val stableReference: Boolean,
    val referenceApplied: Boolean = false,
    val externalSource: RangeReferenceSource? = null,
    val margin: Double = Double.NaN,
    val borderline: Int = 0,
    val shortFull: Int = 0,
    val fullCycles: Int = 0,
) {
    operator fun plus(other: PartialEvidence) = PartialEvidence(
        stableReference = stableReference && other.stableReference,
        referenceApplied = referenceApplied || other.referenceApplied,
        externalSource = externalSource ?: other.externalSource,
        margin = if (margin.isNaN()) other.margin else if (other.margin.isNaN()) margin else max(margin, other.margin),
        borderline = borderline + other.borderline,
        shortFull = shortFull + other.shortFull,
        fullCycles = min(fullCycles, other.fullCycles),
    )

    companion object {
        /** Sin desglose (segmentaciones hechas a mano o series sin ciclos): no sostiene parciales por sí mismo. */
        val NONE = PartialEvidence(stableReference = false)
    }
}

/**
 * Resultado de segmentar una serie (§6).
 *
 * @property activeWindow de la salida de la primera FULL a la llegada de la última; `null` sin FULL.
 * @property miniSets número de mini-series (rest-pause): 1 si no hay descansos largos; 0 sin repeticiones.
 * @property periodS periodo T0 de la autocorrelación, si lo hay.
 * @property noiseSigma σn = 1,4826·MAD(r − LP(r, 5 Hz)), en la unidad de la señal.
 * @property aFull amplitud de una repetición completa (mediana de las más prominentes) y [lRest] el nivel de reposo.
 * @property perSideCounts repeticiones FULL por lado: las dos con BOTH_SYNC (si difieren, es un aviso), la del lado que
 *   trabaja con SINGLE, una por lado con ALTERNATING; vacío en los demás modos.
 * @property polarityFlipped la señal se dio la vuelta una vez (flag POLARITY_FLIPPED).
 * @property noRepsReason por qué no hay repeticiones (o no hay ninguna completa).
 * @property ambiguousCycles n_amb de la confianza: ciclos con a cerca de los umbrales (0,30 y 0,70), con prominencia a ±15 %
 *   del umbral de picos, o con el giro dentro de un hueco rellenado; más 1 si los dos lados cuentan distinto (BOTH_SYNC).
 * @property captureLimited una región requerida del cuerpo quedó fuera del encuadre, sin desplazamiento: los ciclos se
 *   conservan solo porque su evidencia sería ALTA, y la confianza no pasa de MEDIA.
 * @property ambiguity los ciclos dudosos uno a uno, con su tipo y su altura (M3). Vacía en las segmentaciones hechas a mano
 *   de las pruebas: entonces solo cuenta [ambiguousCycles].
 * @property rangeAmplitude (M4) la amplitud completa con la que se clasificó: [aFull], o la referencia de recorrido si es
 *   mayor. NaN en las segmentaciones hechas a mano: entonces vale [aFull].
 * @property reference (M4) la referencia de recorrido que se recibió y si se aplicó.
 * @property partialEvidence (M4) lo que sostiene el número de parciales.
 */
data class SegmentationResult(
    val cycles: List<RepCycle>,
    val discarded: List<DiscardedCycle>,
    val activeWindow: IntRange?,
    val miniSets: Int,
    val periodS: Double?,
    val noiseSigma: Double,
    val aFull: Double,
    val lRest: Double,
    val sideMode: SideMode,
    val perSideCounts: Map<Side, Int>,
    val polarityFlipped: Boolean,
    val noRepsReason: String?,
    val ambiguousCycles: Int = 0,
    val captureLimited: Boolean = false,
    val ambiguity: List<AmbiguityItem> = emptyList(),
    val rangeAmplitude: Double = Double.NaN,
    val reference: RangeReferenceUse? = null,
    val partialEvidence: PartialEvidence = PartialEvidence.NONE,
    /** Señal lateral que produjo los ciclos elegidos; null si se contó la señal agregada o ambos lados. */
    val selectedSignalSide: Side? = null,
) {
    val fullCount: Int get() = cycles.count { it.kind == RepKind.FULL }
    val partialCount: Int get() = cycles.count { it.kind == RepKind.PARTIAL }
    val failedCount: Int get() = cycles.count { it.kind == RepKind.FAILED }

    /** La amplitud completa con la que se clasificó (ver [rangeAmplitude]). */
    val classificationAmplitude: Double get() = if (rangeAmplitude.isFinite() && rangeAmplitude > 0.0) rangeAmplitude else aFull

    /** La referencia de fuera superó la amplitud de la serie y la clasificación se hizo contra ella. */
    val referenceApplied: Boolean get() = reference?.applied == true
}

/**
 * Tramos de movimiento ajustados con el perfil de jerk mínimo. Un tramo (subida o bajada del reposo al giro) se busca en dos
 * pasos: los cruces del 10 % y el 90 % del recorrido (robustos al ruido, porque ahí la pendiente es máxima) dan una primera
 * salida y llegada por extrapolación, y un ajuste por mínimos cuadrados del perfil s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ sobre todas las
 * muestras del tramo (Levenberg–Marquardt, con los niveles de reposo y de giro también libres) la afina: con SNR ≈ 15, los
 * cruces dan unos 100 ms de error RMS en la salida y la llegada de un tramo de 2 s y el ajuste unos 70 ms, a poco de la
 * cota de Cramér–Rao (59 ms), que ningún estimador baja.
 *
 * Por qué no con el umbral de velocidad del 10 % de la velocidad máxima: en un movimiento de jerk mínimo la velocidad
 * tarda 0,087·T en llegar a ese 10 %, así que la salida y la llegada se adelantarían 0,087·T cada una (0,17 s en una fase
 * de 2 s, cinco fotogramas) y una pausa de 0,2 s saldría de 0,46 s.
 */
internal object MovementPieces {
    /** Fracción del recorrido en la que se mide cada extremo del ajuste por cruces. */
    private const val LOW = 0.10
    private const val HIGH = 0.90
    private const val MID = 0.50

    /** Muestras mínimas del tramo para afinarlo por mínimos cuadrados, iteraciones como mucho y holguras de la ventana. */
    private const val MIN_REFINE_SAMPLES = 8
    private const val MAX_ITERATIONS = 40
    private const val REFINE_LEAD = 0.4
    private const val REFINE_TRAIL = 1.4

    /** El reposo de un lado es lo que queda a menos de esta fracción del recorrido sobre su nivel (con las colas de los vecinos). */
    private const val REST_BAND = 0.15

    /** Instante normalizado τ en el que s(τ) = [LOW]: s(τ) = 10τ³ − 15τ⁴ + 6τ⁵. */
    private val TAU_LOW: Double = solveMinJerk(LOW)
    private val TAU_HIGH: Double = solveMinJerk(HIGH)

    private fun solveMinJerk(level: Double): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(60) {
            val mid = 0.5 * (lo + hi)
            if (smooth(mid) < level) lo = mid else hi = mid
        }
        return 0.5 * (lo + hi)
    }

    /** s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ con τ recortado a [0, 1]. */
    private fun smooth(tau: Double): Double {
        val t = if (tau < 0.0) 0.0 else if (tau > 1.0) 1.0 else tau
        return t * t * t * (10.0 - 15.0 * t + 6.0 * t * t)
    }

    /** s'(τ) = 30τ²(1 − τ)², nula fuera de (0, 1). */
    private fun slope(tau: Double): Double {
        if (tau <= 0.0 || tau >= 1.0) return 0.0
        val u = tau * (1.0 - tau)
        return 30.0 * u * u
    }

    /** Un tramo en índices FRACCIONARIOS de la serie: sale en [start], llega en [end] y pasa por el 50 % en [mid]. */
    class Piece(val start: Double, val end: Double, val mid: Double)

    /**
     * Ajusta el tramo que va del valor [v0] (en el índice [from]) al valor [v1] (en el índice [to]) por los cruces del 10 %,
     * el 50 % y el 90 %; [from] < [to] y vale tanto una subida como una bajada. `null` si el recorrido es casi nulo o la
     * señal no lo cruza.
     */
    fun fit(x: DoubleArray, from: Int, to: Int, v0: Double, v1: Double): Piece? {
        val span = v1 - v0
        if (from < 0 || to >= x.size || to <= from || !(kotlin.math.abs(span) > 1e-12)) return null
        // Progreso 0..1 del tramo: crece de v0 a v1, sea subida o bajada.
        fun p(i: Int): Double = (x[i] - v0) / span

        // 50 %: se busca hacia atrás desde el final, así que es el último cruce antes de llegar (el de la pendiente máxima).
        var k = to - 1
        while (k >= from) {
            val pk = p(k)
            if (pk.isNaN()) return null
            if (pk < MID) break
            k--
        }
        if (k < from) return null
        val pk = p(k)
        val pk1 = p(k + 1)
        if (pk1.isNaN() || pk1 <= pk) return null
        val c50 = k + (MID - pk) / (pk1 - pk)

        // 10 %: hacia atrás desde el 50 %.
        var m = k
        while (m >= from && p(m) >= LOW) m--
        val c10 = if (m < from) from.toDouble() else m + (LOW - p(m)) / (p(m + 1) - p(m))

        // 90 %: hacia delante desde el 50 %.
        var q = k + 1
        while (q <= to && p(q) < HIGH) q++
        val c90 = if (q > to) to.toDouble() else (q - 1) + (HIGH - p(q - 1)) / (p(q) - p(q - 1))

        if (!(c90 > c10)) return null
        val length = (c90 - c10) / (TAU_HIGH - TAU_LOW)
        val start = c10 - TAU_LOW * length
        return Piece(start, start + length, c50)
    }

    /** Suma de cuadrados de lo que el modelo [th] = (v0, v1, a, L) deja sin explicar en x[lo..hi]. */
    private fun cost(x: DoubleArray, lo: Int, hi: Int, th: DoubleArray): Double {
        var c = 0.0
        for (i in lo..hi) {
            val xi = x[i]
            if (xi.isNaN()) continue
            val e = xi - (th[0] + (th[1] - th[0]) * smooth((i - th[2]) / th[3]))
            c += e * e
        }
        return c
    }

    /**
     * Afina un tramo ya ajustado por cruces ([start0], [length0], con los niveles [v0] antes y [v1] después) por mínimos
     * cuadrados (Levenberg–Marquardt) con m(i) = v0 + (v1 − v0)·s((i − a)/L). Los cuatro parámetros (los dos niveles, la
     * salida a y la duración L) son libres, así que un nivel sesgado por el ruido no desplaza el tramo. La ventana va de
     * 0,4·L antes de la salida a 1,4·L después, sin salirse de [from, to]. `null` si hay pocas muestras o el ajuste se va de
     * lo razonable (entonces vale el de los cruces).
     */
    private fun refine(x: DoubleArray, from: Int, to: Int, v0: Double, v1: Double, start0: Double, length0: Double): Piece? {
        val lo = max(from, floor(start0 - REFINE_LEAD * length0).toInt())
        val hi = min(to, ceil(start0 + REFINE_TRAIL * length0).toInt())
        if (lo < 0 || hi >= x.size || hi - lo + 1 < MIN_REFINE_SAMPLES || !(length0 > 2.0)) return null
        val th = doubleArrayOf(v0, v1, start0, length0)
        val trial = DoubleArray(4)
        var best = cost(x, lo, hi, th)
        var lambda = 1e-3
        val jtj = Array(4) { DoubleArray(4) }
        val jtr = DoubleArray(4)
        val j = DoubleArray(4)
        var iteration = 0
        while (iteration < MAX_ITERATIONS) {
            iteration++
            for (p in 0 until 4) {
                jtr[p] = 0.0
                java.util.Arrays.fill(jtj[p], 0.0)
            }
            for (i in lo..hi) {
                val xi = x[i]
                if (xi.isNaN()) continue
                val tau = (i - th[2]) / th[3]
                val s = smooth(tau)
                val residual = xi - (th[0] + (th[1] - th[0]) * s)
                j[0] = 1.0 - s
                j[1] = s
                j[2] = -(th[1] - th[0]) * slope(tau) / th[3]
                j[3] = j[2] * tau
                for (p in 0 until 4) {
                    jtr[p] += j[p] * residual
                    for (q in 0 until 4) jtj[p][q] += j[p] * j[q]
                }
            }
            var moved = false
            var attempts = 0
            while (attempts < 10 && !moved) {
                attempts++
                val step = solveDamped(jtj, jtr, lambda)
                if (step == null) {
                    lambda *= 10.0
                    continue
                }
                for (p in 0 until 4) trial[p] = th[p] + step[p]
                val sane = trial[3] > 0.4 * length0 && trial[3] < 3.0 * length0 && kotlin.math.abs(trial[2] - start0) <= length0
                val c = if (sane) cost(x, lo, hi, trial) else Double.POSITIVE_INFINITY
                if (c < best) {
                    best = c
                    for (p in 0 until 4) th[p] = trial[p]
                    lambda = max(lambda * 0.3, 1e-9)
                    moved = kotlin.math.abs(step[2]) > 1e-4 || kotlin.math.abs(step[3]) > 1e-4
                    if (!moved) iteration = MAX_ITERATIONS
                    break
                }
                lambda *= 10.0
            }
            if (!moved) break
        }
        // A transition whose midpoint is outside the observed fitting window is not identified
        // by these samples. Its unconstrained terminal level/duration can otherwise extrapolate
        // past the entire return of the cycle. Reject it and let cycle() consider the crossing fit.
        val midpoint = th[2] + 0.5 * th[3]
        val end = th[2] + th[3]
        if (!th.all { it.isFinite() } || !end.isFinite() || !(th[3] > 0.0) ||
            midpoint < lo.toDouble() || midpoint > hi.toDouble()) return null
        return Piece(th[2], end, midpoint)
    }

    /** Resuelve (JᵀJ + λ·diag(JᵀJ))·δ = Jᵀr por eliminación gaussiana con pivote; `null` si es singular. */
    private fun solveDamped(jtj: Array<DoubleArray>, jtr: DoubleArray, lambda: Double): DoubleArray? {
        val n = 4
        val a = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) jtj[r][c] + (if (r == c) lambda * (jtj[r][r] + 1e-12) else 0.0) else jtr[r] } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (kotlin.math.abs(a[r][col]) > kotlin.math.abs(a[pivot][col])) pivot = r
            if (kotlin.math.abs(a[pivot][col]) < 1e-14) return null
            val tmp = a[col]
            a[col] = a[pivot]
            a[pivot] = tmp
            for (r in col + 1 until n) {
                val f = a[r][col] / a[col][col]
                for (c in col until n + 1) a[r][c] -= f * a[col][c]
            }
        }
        val out = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var sum = a[r][n]
            for (c in r + 1 until n) sum -= a[r][c] * out[c]
            out[r] = sum / a[r][r]
        }
        return out
    }

    /** Los dos tramos de un ciclo: la subida del reposo al giro y la bajada del giro al reposo (o `null` si no se ajustan). */
    class CyclePieces(val rise: Piece?, val fall: Piece?)

    /**
     * Ajusta la subida y la bajada de un ciclo con valles en [valleyLeft] y [valleyRight] (−1 si faltan) y giro cerca de
     * [peak] (el máximo de la señal suavizada del segmentador, que en una cima plana y con ruido puede caer a un segundo del
     * centro de la pausa). Los pasos:
     *
     * 1. Niveles robustos: el de reposo de cada lado es el mínimo de la media móvil de ±[half] junto a su valle, y el de la
     *    cima, el máximo de esa media móvil cerca del pico. Con el mínimo y el máximo a secas, el ruido ensancha el
     *    recorrido varias desviaciones y los cruces del 10 % y el 90 % salen donde no es.
     * 2. Centro del ciclo: el punto medio de los cruces del 50 % de la subida y de la bajada. Parte los dos tramos aunque
     *    [peak] no caiga en la pausa: la subida se ajusta con lo que hay hasta el centro y la bajada con lo que hay desde él.
     * 3. Ventanas: cada tramo usa también el reposo de su lado, hasta el centro de la región que queda a menos de
     *    [REST_BAND] del recorrido sobre el nivel de reposo. Con la ventana cortada en el valle (que cae casi siempre al
     *    final de la bajada), el nivel de reposo queda sin datos y el ajuste ensancha el tramo para compensarlo.
     * 4. Cada tramo se ajusta por cruces y se afina por mínimos cuadrados.
     */
    fun cycle(x: DoubleArray, valleyLeft: Int, peak: Int, valleyRight: Int, half: Int): CyclePieces {
        val n = x.size
        if (peak < 0 || peak >= n) return CyclePieces(null, null)
        val hasLeft = valleyLeft in 0 until peak
        val hasRight = valleyRight > peak && valleyRight < n
        val lo0 = if (hasLeft) lowLevel(x, valleyLeft, half) else Double.NaN
        val lo1 = if (hasRight) lowLevel(x, valleyRight, half) else Double.NaN
        if (lo0.isNaN() && lo1.isNaN()) return CyclePieces(null, null)
        val leftBound = if (hasLeft) valleyLeft else 0
        val rightBound = if (hasRight) valleyRight else n - 1
        val hi = highLevel(x, peak, half, leftBound, rightBound)
        if (hi.isNaN()) return CyclePieces(null, null)
        val h0 = if (lo0.isNaN()) Double.NaN else hi - lo0
        val h1 = if (lo1.isNaN()) Double.NaN else hi - lo1
        val reference = when {
            h0.isNaN() -> h1
            h1.isNaN() -> h0
            else -> min(h0, h1)
        }
        if (!(reference > 0.0)) return CyclePieces(null, null)

        // Centro: mitad entre el cruce del 50 % de la subida (hacia atrás desde el pico) y el de la bajada (hacia delante).
        val tr = if (h0 > 0.0) crossingBefore(x, peak, leftBound, lo0 + MID * h0) else Double.NaN
        val tf = if (h1 > 0.0) crossingAfter(x, peak, rightBound, lo1 + MID * h1) else Double.NaN
        val centre = if (!tr.isNaN() && !tf.isNaN()) 0.5 * (tr + tf) else peak.toDouble()
        val centreIndex = centre.roundToInt().coerceIn(leftBound, rightBound)

        val roughRise = if (!tr.isNaN() && h0 > 0.0 && centreIndex > valleyLeft) fit(x, valleyLeft, centreIndex, lo0, hi) else null
        val roughFall = if (!tf.isNaN() && h1 > 0.0 && valleyRight > centreIndex) fit(x, centreIndex, valleyRight, hi, lo1) else null

        // Ventanas: cada tramo llega hasta el centro del reposo que lo separa del tramo vecino. El valle (el mínimo de la señal
        // suavizada) cae casi siempre al final de la bajada, y una ventana cortada ahí deja el nivel de reposo sin datos: el
        // ajuste ensancha el tramo para compensarlo. Pasar del centro metería la cola del tramo vecino.
        val margin = max(1, half / 3)
        val rise = roughRise?.let {
            var left = floor(restCentre(x, valleyLeft, lo0 + REST_BAND * h0)).toInt()
            left = min(left, floor(it.start).toInt() - margin)
            left = max(0, min(left, valleyLeft))
            refine(x, left, centreIndex, lo0, hi, it.start, it.end - it.start) ?: it
        }
        val fall = roughFall?.let {
            var right = ceil(restCentre(x, valleyRight, lo1 + REST_BAND * h1)).toInt()
            right = max(right, ceil(it.end).toInt() + margin)
            right = min(n - 1, max(right, valleyRight))
            refine(x, centreIndex, right, hi, lo1, it.start, it.end - it.start) ?: it
        }
        return CyclePieces(rise, fall)
    }

    /**
     * Centro de la región que rodea al valle [valley] y no pasa del nivel [limit] (el reposo y las colas de los dos tramos
     * vecinos). Si los dos vecinos tienen colas parecidas, cae en mitad del reposo.
     */
    private fun restCentre(x: DoubleArray, valley: Int, limit: Double): Double {
        var a = valley
        while (a - 1 >= 0 && x[a - 1] <= limit) a--
        var b = valley
        while (b + 1 < x.size && x[b + 1] <= limit) b++
        return 0.5 * (a + b)
    }

    /** Media de [x] en [centre] ± [half] sin los NaN; NaN si no hay datos. */
    private fun meanAround(x: DoubleArray, centre: Int, half: Int): Double {
        var sum = 0.0
        var count = 0
        for (i in max(0, centre - half)..min(x.size - 1, centre + half)) {
            val v = x[i]
            if (v.isNaN()) continue
            sum += v
            count++
        }
        return if (count == 0) Double.NaN else sum / count
    }

    /** Nivel de reposo junto al valle [valley]: el mínimo de la media móvil de ±[half] en [valley] ± [half]. */
    private fun lowLevel(x: DoubleArray, valley: Int, half: Int): Double {
        var best = Double.NaN
        for (c in max(0, valley - half)..min(x.size - 1, valley + half)) {
            val m = meanAround(x, c, half)
            if (!m.isNaN() && (best.isNaN() || m < best)) best = m
        }
        return best
    }

    /** Nivel de la cima: el máximo de la media móvil de ±[half] en [peak] ± 2·[half], dentro de [lowest, highest]. */
    private fun highLevel(x: DoubleArray, peak: Int, half: Int, lowest: Int, highest: Int): Double {
        var best = Double.NaN
        for (c in max(lowest, peak - 2 * half)..min(highest, peak + 2 * half)) {
            val m = meanAround(x, c, half)
            if (!m.isNaN() && (best.isNaN() || m > best)) best = m
        }
        return best
    }

    /**
     * Índice fraccionario del último cruce ascendente de [level] antes de [from] (yendo hacia atrás hasta [lowest]); NaN si
     * no hay (la señal ya estaba por encima en [lowest]) o se topa con un dato que falta.
     */
    private fun crossingBefore(x: DoubleArray, from: Int, lowest: Int, level: Double): Double {
        var k = from
        while (k - 1 >= lowest && !(x[k] < level)) {
            if (x[k].isNaN()) return Double.NaN
            k--
        }
        if (!(x[k] < level) || k + 1 >= x.size || x[k + 1].isNaN()) return Double.NaN
        val d = x[k + 1] - x[k]
        return if (d > 0.0) k + (level - x[k]) / d else k.toDouble()
    }

    /** Índice fraccionario del primer cruce descendente de [level] desde [from] (hacia delante hasta [highest]); NaN si no hay. */
    private fun crossingAfter(x: DoubleArray, from: Int, highest: Int, level: Double): Double {
        var k = from
        while (k + 1 <= highest && !(x[k] < level)) {
            if (x[k].isNaN()) return Double.NaN
            k++
        }
        if (!(x[k] < level) || k < 1 || x[k - 1].isNaN()) return Double.NaN
        val d = x[k - 1] - x[k]
        return if (d > 0.0) (k - 1) + (x[k - 1] - level) / d else k.toDouble()
    }
}

/**
 * Segmentador de repeticiones (§6, pasos 1 a 10) sobre la serie de conteo, orientada con el reposo en el mínimo.
 *
 * 1. σn de la señal; 2. periodo T0 por autocorrelación y suavizado de conteo; 3. picos por prominencia relativa;
 * 4. valles, nivel de reposo y amplitud completa; 5. clase de cada ciclo (FULL, PARTIAL, FAILED, ruido, no-repetición);
 * 6. ventana activa y parciales de preparación o de re-rack; 7. polaridad; 8. mini-series; 9. conteo por lado;
 * 10. salida.
 */
object RepSegmenter {
    /**
     * Traza de depuración para las pruebas y el ajuste de umbrales: recibe, línea a línea, los números con los que decide el
     * segmentador (amplitudes, clases, tramos y paradas). Apagada (`null`) no cuesta nada.
     */
    internal var trace: ((String) -> Unit)? = null

    private inline fun traceLine(message: () -> String) {
        trace?.invoke(message())
    }

    /**
     * Segmenta la serie. [reference] (M4) es la referencia de recorrido, si la hay: se usa si se midió en la misma señal (y en
     * la misma vista, si la dice) y su amplitud supera la de la propia serie.
     */
    fun segment(
        series: CountSeries,
        profile: AnalysisProfile,
        hints: Set<String>,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
        prepared: PreparedTrack? = null,
        reference: RangeReference? = null,
    ): SegmentationResult {
        val range = reference?.let { rangeInputOf(it, series, profile, prepared, tuning) }
        val result = segmentSignal(series, profile, hints, tuning, range)
        if (result.activeWindow == null) return result
        if (prepared == null) return result // Hand-built signal tests have no capture geometry.
        // Walkout/setup before the first repetition does not establish camera motion during the set.
        val capture = captureCountEvidence(prepared, profile, tuning, captureWindowUs(series, result, tuning))
        if (!capture.unsafe) return result
        // M2 (iteración 2): una región del cuerpo fuera del encuadre, sin desplazamiento, no borra ciclos ya observados; cuesta
        // un nivel. Solo un conteo que sin ese problema sería ALTA se conserva, y como mucho en MEDIA. Lo demás se abstiene.
        if (!capture.travelled) {
            val unlimited = RepConfidence.score(series, result, prepared.view, profile, RepFlags.of(prepared, series, result), tuning)
            if (unlimited.level == CountConfidence.ALTA) return result.copy(captureLimited = true)
        }
        return result.copy(cycles = emptyList(),
            discarded = result.discarded + result.cycles.map { DiscardedCycle(it.peak, "poor_capture") },
            activeWindow = null, miniSets = 0, perSideCounts = emptyMap(), noRepsReason = "poor_capture")
    }

    /**
     * La referencia de recorrido comprobada para esta serie (M4). No se usa si su amplitud no es válida (`invalid`), si se
     * midió con otra señal de conteo (`signal`: otra unidad, o el disco frente a la pose) o, si la señal es un ángulo o un
     * cociente, en otra vista (`view`: en 2D cambian con la vista; un desplazamiento en alturas de cuerpo, no). Una referencia
     * del perfil (PROFILE) cuenta por `PartialsTuning.profileReferenceTrust` de su amplitud: es de la población, no de la
     * persona.
     */
    private fun rangeInputOf(
        reference: RangeReference,
        series: CountSeries,
        profile: AnalysisProfile,
        prepared: PreparedTrack?,
        tuning: AnalysisTuning,
    ): RangeInput {
        val signal = if (series.source == CountSeriesBuilder.SOURCE_PLATE) profile.plateSignal else profile.poseSignal
        val reason = when {
            !(reference.amplitude.isFinite() && reference.amplitude > 0.0) -> "invalid"
            reference.signal != signal -> "signal"
            reference.view != null && prepared != null && series.unit != SignalUnit.BODY_HEIGHTS &&
                reference.view != prepared.view.kind -> "view"
            else -> null
        }
        val trust = if (reference.source == RangeReferenceSource.PERSONAL) 1.0 else tuning.metrics.partials.profileReferenceTrust
        return RangeInput(if (reason == null) reference.amplitude * trust else Double.NaN, reference, reason)
    }

    private fun segmentSignal(
        series: CountSeries,
        profile: AnalysisProfile,
        hints: Set<String>,
        tuning: AnalysisTuning,
        range: RangeInput?,
    ): SegmentationResult {
        val sides = series.perSide
        val canSplit = profile.perSide && series.source == CountSeriesBuilder.SOURCE_POSE &&
            sides != null && sides.containsKey(Side.LEFT) && sides.containsKey(Side.RIGHT)
        if (canSplit) return segmentBySide(series, sides!!.getValue(Side.LEFT), sides.getValue(Side.RIGHT), profile, hints, tuning, range)

        val pass = analyzeWithPolarity(series, series.r, profile, hints, tuning, range)
        val mode = if (profile.perSide && series.source == CountSeriesBuilder.SOURCE_POSE) SideMode.NEAR_ONLY else SideMode.NOT_APPLICABLE
        return pass.toResult(mode, emptyMap(), null, pass.cuts)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Paso 9: conteo por lado
    // ------------------------------------------------------------------------------------------------------------

    private fun segmentBySide(
        series: CountSeries,
        left: DoubleArray,
        right: DoubleArray,
        profile: AnalysisProfile,
        hints: Set<String>,
        tuning: AnalysisTuning,
        range: RangeInput?,
    ): SegmentationResult {
        val pl = analyzeWithPolarity(series, left, profile, hints, tuning, range)
        val pr = analyzeWithPolarity(series, right, profile, hints, tuning, range)
        val main = analyzeWithPolarity(series, series.r, profile, hints, tuning, range)
        val fullL = pl.countOf(RepKind.FULL)
        val fullR = pr.countOf(RepKind.FULL)
        val counts = mapOf(Side.LEFT to fullL, Side.RIGHT to fullR)

        fun single(side: Side, pass: Pass) = pass.toResult(SideMode.SINGLE, mapOf(side to pass.countOf(RepKind.FULL)), side, pass.cuts,
            selectedSignalSide = side)

        val hasL = pl.cycles.isNotEmpty()
        val hasR = pr.cycles.isNotEmpty()
        if (!hasL && !hasR) return main.toResult(SideMode.BOTH_SYNC, emptyMap(), null, main.cuts)
        if (hasL != hasR) return if (hasL) single(Side.LEFT, pl) else single(Side.RIGHT, pr)
        if (min(pl.aFull, pr.aFull) < tuning.singleSideAmplitudeRatio * max(pl.aFull, pr.aFull)) {
            return if (pl.aFull >= pr.aFull) single(Side.LEFT, pl) else single(Side.RIGHT, pr)
        }

        val corr = correlation(left, right)
        val alternate = peaksAlternate(pl, pr, tuning)
        traceLine { "lados: corr=%.2f alternan=%s centros izq=%s der=%s".format(corr, alternate, pl.cycles.map { it.centre.roundToInt() }, pr.cycles.map { it.centre.roundToInt() }) }
        if (corr < tuning.alternatingCorrelation || alternate) {
            // Por turnos: cada lado cuenta lo suyo y el total es la suma. Las mini-series salen de la señal conjunta.
            val merged = ArrayList<Cand>(pl.cycles.size + pr.cycles.size)
            for (c in pl.cycles) merged.add(c.withSide(Side.LEFT))
            for (c in pr.cycles) merged.add(c.withSide(Side.RIGHT))
            merged.sortBy { it.peak }
            val discarded = (pl.discarded + pr.discarded).sortedBy { it.peak }
            val combined = Pass(
                cycles = merged,
                discarded = discarded,
                window = main.window ?: pl.window ?: pr.window,
                miniSets = main.miniSets,
                t0 = main.t0 ?: pl.t0,
                sigma = main.sigma,
                aFull = main.aFull,
                lRest = main.lRest,
                reason = null,
                flipped = pl.flipped || pr.flipped,
                ambiguous = pl.ambiguous + pr.ambiguous,
                cuts = main.cuts,
                ambiguity = pl.ambiguity + pr.ambiguity,
                aRange = main.aRange,
                range = range,
                referenceApplied = pl.referenceApplied || pr.referenceApplied,
                evidence = pl.evidence + pr.evidence,
            )
            return combined.toResult(SideMode.ALTERNATING, counts, null, main.cuts)
        }
        // Simétrico, o sin una pauta clara: se cuenta el lado de más confianza y la diferencia entre lados avisa.
        val snrL = if (pl.sigma > 0.0) pl.aFull / pl.sigma else Double.MAX_VALUE
        val snrR = if (pr.sigma > 0.0) pr.aFull / pr.sigma else Double.MAX_VALUE
        val bestSide = if (snrL >= snrR) Side.LEFT else Side.RIGHT
        val best = if (bestSide == Side.LEFT) pl else pr
        val extra = if (fullL != fullR) 1 else 0
        return best.toResult(SideMode.BOTH_SYNC, counts, null, best.cuts, extraAmbiguous = extra,
            selectedSignalSide = bestSide)
    }

    /** Correlación de Pearson de dos señales sobre las muestras válidas en las dos; 0 si no hay datos o alguna es constante. */
    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        var n = 0
        var sa = 0.0
        var sb = 0.0
        for (k in a.indices) {
            if (a[k].isNaN() || b[k].isNaN()) continue
            n++
            sa += a[k]
            sb += b[k]
        }
        if (n < 2) return 0.0
        val ma = sa / n
        val mb = sb / n
        var saa = 0.0
        var sbb = 0.0
        var sab = 0.0
        for (k in a.indices) {
            if (a[k].isNaN() || b[k].isNaN()) continue
            saa += (a[k] - ma) * (a[k] - ma)
            sbb += (b[k] - mb) * (b[k] - mb)
            sab += (a[k] - ma) * (b[k] - mb)
        }
        val d = sqrt(saa * sbb)
        return if (d > 0.0) sab / d else 0.0
    }

    /**
     * ≥ 80 % de los picos alternan entre los dos lados, y de verdad: cada ciclo cae lejos de los del otro lado (fase ≥ 0,25
     * del periodo propio). Dos lados que suben a la vez dan ciclos casi juntos, que ordenados también «alternan», pero con
     * fase cero. «Cuándo» ocurre un ciclo es su centro (la mitad del recorrido, ver `Cand.centre`) y no el máximo de rd:
     * en una cima plana y con ruido el máximo cae donde le toque, hasta a un segundo del otro lado, y haría pasar por
     * alternos dos brazos que suben a la vez.
     */
    private fun peaksAlternate(a: Pass, b: Pass, tuning: AnalysisTuning): Boolean {
        val pa = a.cycles.map { it.centre }
        val pb = b.cycles.map { it.centre }
        if (pa.size < 2 || pb.size < 2) return false
        val merged = (pa.map { it to 0 } + pb.map { it to 1 }).sortedBy { it.first }
        var alternating = 0
        for (i in 1 until merged.size) if (merged[i].second != merged[i - 1].second) alternating++
        val fraction = alternating.toDouble() / (merged.size - 1)
        val ownPeriod = min(medianGap(pa), medianGap(pb))
        if (!(ownPeriod > 0.0)) return false
        val phases = DoubleArray(pa.size + pb.size)
        var k = 0
        for (p in pa) phases[k++] = pb.minOf { abs(it - p) } / ownPeriod
        for (p in pb) phases[k++] = pa.minOf { abs(it - p) } / ownPeriod
        return fraction >= tuning.alternatingPeaksFraction && RobustStats.median(phases) >= tuning.alternatingMinPhase
    }

    private fun medianGap(centres: List<Double>): Double {
        if (centres.size < 2) return 0.0
        val gaps = DoubleArray(centres.size - 1) { centres[it + 1] - centres[it] }
        return RobustStats.median(gaps)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Paso 7: polaridad (una sola vez)
    // ------------------------------------------------------------------------------------------------------------

    private fun analyzeWithPolarity(
        series: CountSeries,
        r: DoubleArray,
        profile: AnalysisProfile,
        hints: Set<String>,
        tuning: AnalysisTuning,
        range: RangeInput?,
    ): Pass {
        val first = Analyzer(series, r, profile, hints, tuning, range, flipped = false).run()
        if (!first.flipSuggested) return first
        val flipped = DoubleArray(r.size) { -r[it] }
        val second = Analyzer(series, flipped, profile, hints, tuning, range, flipped = true).run()
        // Darle la vuelta solo vale si deja repeticiones completas: si no, el reposo estaba bien y era una serie con pausas largas.
        return if (second.countOf(RepKind.FULL) > 0) second.copy(flipped = true) else first
    }

    // ------------------------------------------------------------------------------------------------------------
    // Estructuras internas
    // ------------------------------------------------------------------------------------------------------------

    /** La referencia de recorrido comprobada (M4): [trusted] es la amplitud que se usa (NaN si no vale) y [reason] por qué no. */
    private class RangeInput(val trusted: Double, val reference: RangeReference, val reason: String?) {
        fun use(applied: Boolean) = RangeReferenceUse(reference.source, reference.amplitude, reference.seriesCount, applied, reason)
    }

    /** Un ciclo candidato: un pico con sus valles, su clase y por qué se descarta, si se descarta. */
    private class Cand(val peak: Int, val prominence: Double, var vL: Int, var vR: Int) {
        var aUp = Double.NaN
        var aDn = Double.NaN
        var a = Double.NaN
        var e = Double.NaN
        var kind: RepKind? = null
        var partial: PartialKind? = null
        var discard: String? = null
        var turnInGap = false
        var miniSet = 0
        var side: Side? = null

        /** M4: zona muscular (solo PARTIAL), recorrido en la unidad de la señal, y si es una completa dudosa (SHORT_FULL). */
        var zone: PartialZone? = null
        var amplitude = Double.NaN
        var shortFull = false

        /** M4: apartado como ruido por estrecho; si su giro cae junto a un hueco, conserva la duda TURN_IN_GAP. */
        var narrow = false

        /** Centro temporal del ciclo (índice fraccionario): el punto medio de lo que rd supera de la mitad del recorrido. */
        var centre = Double.NaN

        val alive: Boolean get() = discard == null

        fun drop(reason: String) {
            discard = reason
            kind = null
            partial = null
        }

        fun withSide(s: Side): Cand = copy().also { it.side = s }

        fun copy(): Cand {
            val c = Cand(peak, prominence, vL, vR)
            c.aUp = aUp; c.aDn = aDn; c.a = a; c.e = e
            c.kind = kind; c.partial = partial; c.discard = discard
            c.turnInGap = turnInGap; c.miniSet = miniSet; c.side = side; c.centre = centre
            c.zone = zone; c.amplitude = amplitude; c.shortFull = shortFull; c.narrow = narrow
            return c
        }
    }

    /** Lo que sale de analizar una señal (una pasada, con la polaridad ya decidida). */
    private data class Pass(
        val cycles: List<Cand>,
        val discarded: List<DiscardedCycle>,
        val window: IntRange?,
        val miniSets: Int,
        val t0: Double?,
        val sigma: Double,
        val aFull: Double,
        val lRest: Double,
        val reason: String?,
        val flipped: Boolean = false,
        val ambiguous: Int = 0,
        val cuts: IntArray = IntArray(0),
        val flipSuggested: Boolean = false,
        val ambiguity: List<AmbiguityItem> = emptyList(),
        val aRange: Double = Double.NaN,
        val range: RangeInput? = null,
        val referenceApplied: Boolean = false,
        val evidence: PartialEvidence = PartialEvidence.NONE,
    ) {
        fun countOf(kind: RepKind): Int = cycles.count { it.kind == kind }

        fun toResult(
            mode: SideMode,
            perSideCounts: Map<Side, Int>,
            sideTag: Side?,
            cuts: IntArray,
            extraAmbiguous: Int = 0,
            selectedSignalSide: Side? = null,
        ): SegmentationResult {
            val out = ArrayList<RepCycle>(cycles.size)
            for ((i, c) in cycles.withIndex()) {
                val side = c.side ?: sideTag
                val mini = cuts.count { it < c.peak }
                out.add(
                    RepCycle(
                        index = i,
                        kind = c.kind!!,
                        partialKind = c.partial,
                        side = side,
                        leftValley = if (c.vL >= 0) c.vL else c.peak,
                        peak = c.peak,
                        rightValley = if (c.vR >= 0) c.vR else null,
                        amplitudeFraction = c.a,
                        turnInGap = c.turnInGap,
                        miniSet = mini,
                        zone = if (c.kind == RepKind.PARTIAL) c.zone else null,
                        amplitude = c.amplitude,
                    ),
                )
            }
            val hasReps = out.isNotEmpty()
            // M4: una serie solo de parciales sostenida por la referencia de fuera tiene repeticiones aunque ninguna sea completa.
            val hasFull = out.any { it.kind == RepKind.FULL } || (referenceApplied && out.any { it.kind == RepKind.PARTIAL })
            return SegmentationResult(
                cycles = out,
                discarded = discarded,
                activeWindow = window,
                miniSets = if (hasReps) max(1, cuts.size + 1) else 0,
                periodS = t0,
                noiseSigma = sigma,
                aFull = aFull,
                lRest = lRest,
                sideMode = mode,
                perSideCounts = perSideCounts,
                polarityFlipped = flipped,
                noRepsReason = when {
                    !hasReps -> reason ?: "no_cycles"
                    !hasFull -> "no_full_reps"
                    else -> null
                },
                ambiguousCycles = ambiguous + extraAmbiguous,
                ambiguity = ambiguity + List(extraAmbiguous) { AmbiguityItem(AmbiguityKind.SIDES, Double.NaN) },
                rangeAmplitude = aRange,
                reference = range?.use(referenceApplied),
                partialEvidence = evidence,
                selectedSignalSide = selectedSignalSide,
            )
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Pasos 1 a 8 sobre una señal
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Pasos 1 a 8 sobre una señal. [range] (M4) es la referencia de recorrido comprobada; [flipped] dice si [r] es la señal
     * al revés (la zona muscular de una parcial se da en el sentido del perfil, no en el de la señal analizada).
     */
    private class Analyzer(
        private val series: CountSeries,
        private val r: DoubleArray,
        private val profile: AnalysisProfile,
        private val hints: Set<String>,
        private val tuning: AnalysisTuning,
        private val range: RangeInput? = null,
        private val flipped: Boolean = false,
    ) {
        private val fs = series.fs
        private val n = r.size
        private val startsEccentric = profile.startPhase == StartPhase.ECCENTRIC_FIRST
        private val partials = tuning.metrics.partials
        private var rd = DoubleArray(0)
        private var pieces = DoubleArray(0)
        private var aFull = 0.0
        private var lRest = 0.0
        private var sigma = 0.0
        private var restBand = 0.0

        /** M4: la amplitud completa con la que se clasifica (la de la serie o la de la referencia, la mayor) y el reposo de la zona. */
        private var aRange = 0.0
        private var referenceApplied = false
        private var anchor = 0.0
        private var evidence = PartialEvidence.NONE

        fun run(): Pass {
            var valid = 0
            for (x in r) if (!x.isNaN()) valid++
            if (valid < tuning.minSeriesS * fs) return empty("too_short")

            // 1 · σn = 1,4826·MAD(r − LP(r, 5 Hz)); la propia LP de 5 Hz sirve para ajustar los tramos de movimiento.
            val lp5 = ZeroPhaseLowPass.filter(r, fs, tuning.noiseLowPassHz)
            pieces = lp5
            val residual = DoubleArray(n) { r[it] - lp5[it] }
            sigma = RobustStats.mad(residual).let { if (it.isNaN()) 0.0 else it }

            // 2 · periodo por la primera cresta de la autocorrelación de rd (2,5 Hz) y suavizado de conteo.
            val rd0 = ZeroPhaseLowPass.filter(r, fs, tuning.acfLowPassHz)
            val t0 = Autocorrelation.firstPeakLagS(rd0, fs, tuning.periodMinS, tuning.periodMaxS, tuning.periodMinRho)
            rd = if (t0 != null) {
                ZeroPhaseLowPass.filter(r, fs, (tuning.countLowPassCycles / t0).coerceIn(tuning.countLowPassMinHz, tuning.countLowPassMaxHz))
            } else {
                rd0
            }
            val minDistanceS = if (t0 != null) {
                (tuning.minDistanceFactor * t0).coerceIn(tuning.minDistanceMinS, tuning.minDistanceMaxS)
            } else {
                tuning.minDistanceDefaultS
            }
            val windowS = max(tuning.windowPeriods * (t0 ?: 0.0), tuning.windowMinS)
            val minDistance = max(1, (minDistanceS * fs).roundToInt())
            val window = (windowS * fs).roundToInt()

            // 3 · picos: una primera pasada fija la amplitud de referencia y la segunda la usa de umbral.
            val low = RobustStats.percentile(rd, tuning.spanLowPercentile)
            val span = RobustStats.percentile(rd, tuning.spanHighPercentile) - low
            if (!(span > 0.0)) return empty("no_peaks", t0)
            val rdPeaks = withVirtualDescent(rd, low, span)
            val firstPass = findPeaks(rdPeaks, tuning.firstPassProminence * span, minDistance, window, relaxBelow = 0.0)
            if (firstPass.isEmpty()) return empty("no_peaks", t0)
            val sorted = referenceProminences(firstPass.map { it.prominence }).sortedDescending()
            val aRef = if (sorted.size >= tuning.refPeakCount) {
                RobustStats.median(DoubleArray(tuning.refPeakCount) { sorted[it] })
            } else {
                sorted.first()
            }
            val threshold = tuning.secondPassProminence * aRef
            val relaxBelow = tuning.closePeakRelaxFraction * aRef
            val peaks = findPeaks(rdPeaks, threshold, minDistance, window, relaxBelow)
            if (peaks.isEmpty()) return empty("no_peaks", t0)
            val extended = findPeaks(rdPeaks, tuning.borderlineLow * threshold, minDistance, window, relaxBelow)
            log {
                "picos (prominencia/Aref): " + peaks.joinToString { "%d(%.2f)".format(it.index, it.prominence / aRef) } +
                    " | extendidos: " + extended.joinToString { "%d(%.2f)".format(it.index, it.prominence / aRef) } +
                    " | primera pasada: " + firstPass.joinToString { "%d(%.2f)".format(it.index, it.prominence / aRef) } +
                    " | dmin=%d ventana=%d".format(minDistance, window)
            }

            // 4 · valles de cada pico, nivel de reposo y amplitud completa.
            val m = peaks.size
            val spacing = if (m >= 2) {
                RobustStats.median(DoubleArray(m - 1) { (peaks[it + 1].index - peaks[it].index) / fs })
            } else {
                Double.NaN
            }
            val tRef = t0 ?: spacing.takeIf { !it.isNaN() } ?: tuning.referencePeriodS
            val edge = max(1, (tuning.edgeSearchPeriods * tRef * fs).roundToInt())
            val cands = ArrayList<Cand>(m)
            for (i in 0 until m) {
                val p = peaks[i].index
                val leftFrom = if (i == 0) max(0, p - edge) else peaks[i - 1].index + 1
                val rightTo = if (i == m - 1) min(n, p + 1 + edge) else peaks[i + 1].index
                cands.add(Cand(p, peaks[i].prominence, argMin(leftFrom, p), argMin(p + 1, rightTo)))
            }
            val restValues = ArrayList<Double>(2 * m)
            val topHeights = ArrayList<Double>(m)
            for (c in cands) {
                if (c.prominence < tuning.topProminence * aRef) continue
                if (c.vL >= 0) restValues.add(rd[c.vL])
                if (c.vR >= 0) restValues.add(rd[c.vR])
            }
            lRest = if (restValues.isEmpty()) Double.NaN else RobustStats.median(restValues.toDoubleArray())
            for (c in cands) if (c.prominence >= tuning.topProminence * aRef) topHeights.add(rd[c.peak] - lRest)
            aFull = if (topHeights.isEmpty()) Double.NaN else RobustStats.median(topHeights.toDoubleArray())
            if (!(aFull > 0.0) || lRest.isNaN()) return empty("low_amplitude", t0)
            // M4 · referencia de recorrido: la amplitud de una repetición completa es la mayor entre la de la propia serie y la
            // de fuera (con la confianza de su origen), si la serie se queda claramente corta frente a ella (`referenceGate`). Así
            // una serie hecha solo de parciales no se mide contra sí misma, y una de completas algo cortas no cambia.
            val trusted = range?.trusted ?: Double.NaN
            aRange = if (trusted.isFinite() && trusted > aFull && aFull < partials.referenceGate * trusted) trusted else aFull
            referenceApplied = aRange > aFull
            val minAmplitude = if (series.source == CountSeriesBuilder.SOURCE_PLATE) {
                profile.plateMinAmplitude ?: profile.minAmplitude
            } else {
                profile.minAmplitude
            }
            // La amplitud mínima del perfil es la de una completa: con la referencia aplicada se mide en ella (las parciales de
            // una serie solo de parciales pueden quedar por debajo); el movimiento de la serie sigue teniendo que salir del ruido.
            if (aRange < minAmplitude) return empty("low_amplitude", t0, keepAmplitude = true)
            if (aFull < tuning.minAmplitudeSigmas * sigma) return empty("low_snr", t0, keepAmplitude = true)
            restBand = min(max(tuning.restBandFraction * aFull, tuning.restBandSigmas * sigma), tuning.restBandMaxFraction * aFull)
            log { "aRef=%.4f aFull=%.4f lRest=%.4f sigma=%.4f t0=%s picos=%d banda=%.4f".format(aRef, aFull, lRest, sigma, t0, m, restBand) }
            if (range != null) log { "referencia: amplitud=%.4f aplicada=%s motivo=%s".format(range.trusted, referenceApplied, range.reason) }

            // 5 · clase de cada ciclo (a y e en fracciones de la amplitud completa con la que se clasifica).
            for (c in cands) {
                c.aUp = if (c.vL >= 0) (rd[c.peak] - rd[c.vL]) / aRange else Double.NaN
                c.aDn = if (c.vR >= 0) (rd[c.peak] - rd[c.vR]) / aRange else Double.NaN
                c.a = when {
                    !c.aUp.isNaN() && !c.aDn.isNaN() -> min(c.aUp, c.aDn)
                    !c.aUp.isNaN() -> c.aUp
                    else -> c.aDn
                }
                c.turnInGap = filledNear(c.peak)
            }
            markNonReps(cands)
            // Con la referencia aplicada, el reposo de la zona es el nivel más bajo de los valles: en una serie de parciales en
            // estiramiento los valles quedan lejos del reposo y su mediana (Lrest) no lo es. Sin ella, Lrest como siempre.
            anchor = if (referenceApplied) min(lRest, lowestValley(cands)) else lRest
            for (c in cands) c.e = (rd[c.peak] - anchor) / aRange
            val lastIndex = cands.indexOfLast { it.alive || it.discard == "noise" }
            classify(cands, lastIndex)
            for (c in cands) c.centre = centreOf(c)
            log {
                cands.joinToString(" | ") {
                    "p=%d vL=%d vR=%d aUp=%.2f aDn=%.2f a=%.2f e=%.2f %s".format(it.peak, it.vL, it.vR, it.aUp, it.aDn, it.a, it.e, it.kind ?: it.discard)
                }
            }

            // 6 · ventana activa, preparación y re-rack.
            val tMed = medianFullPeriodS(cands) ?: t0 ?: tuning.referencePeriodS
            dropOversizedEdges(cands)
            applyWindow(cands, tMed)

            // 6b · M4: parciales que no se sostienen (bordes censurados, referencia estable y parciales aisladas), ya sin la
            // preparación ni el re-rack.
            gatePartials(cands)

            // 8 · mini-series.
            val cuts = findCuts(cands)

            // 7 · polaridad: tiempo quieto abajo frente a arriba.
            val flipSuggested = stillTimeSuggestsFlip()

            val kept = cands.filter { it.alive }.sortedBy { it.peak }
            val discarded = cands.filter { !it.alive }.map { DiscardedCycle(it.peak, it.discard!!) }
            // M4: la zona de cada parcial y el recorrido de cada ciclo en la unidad de la señal.
            for (c in kept) {
                c.amplitude = if (c.a.isFinite()) c.a * aRange else Double.NaN
                if (c.kind == RepKind.PARTIAL) assignZone(c) else c.zone = null
            }
            val fulls = kept.filter { it.kind == RepKind.FULL }
            // Sin completas, la ventana activa es la de las parciales si la referencia de fuera las sostiene (una serie solo de
            // parciales); si no, no hay ventana.
            val windowCycles = if (fulls.isNotEmpty()) fulls else if (referenceApplied) kept.filter { it.kind == RepKind.PARTIAL } else emptyList()
            val activeWindow = if (windowCycles.isEmpty()) {
                null
            } else {
                val first = windowCycles.first()
                val last = windowCycles.last()
                (if (first.vL >= 0) first.vL else first.peak)..(if (last.vR >= 0) last.vR else last.peak)
            }
            val ambiguity = countAmbiguous(cands, peaks.map { it.index to it.prominence }, extended.map { it.index to it.prominence }, threshold)
            log { "dudosos: " + ambiguity.joinToString { "%s(%.2f;%.2f)".format(java.util.Locale.ROOT, it.kind, it.height, it.gapS) } }
            return Pass(
                cycles = kept,
                discarded = discarded,
                window = activeWindow,
                miniSets = if (kept.isEmpty()) 0 else cuts.size + 1,
                t0 = t0,
                sigma = sigma,
                aFull = aFull,
                lRest = lRest,
                reason = if (kept.isEmpty()) "no_valid_cycles" else null,
                ambiguous = ambiguity.size,
                cuts = cuts,
                flipSuggested = flipSuggested,
                ambiguity = ambiguity,
                aRange = aRange,
                range = range,
                referenceApplied = referenceApplied,
                evidence = evidence,
            )
        }

        /**
         * Las prominencias de las que sale Aref. Si la primera o la última son mucho mayores (> 1,35x) que las del interior
         * (recoger la barra del suelo, dejarla), se dejan fuera: con dos así entre las tres mayores, la mediana sería la de ellas.
         */
        private fun referenceProminences(all: List<Double>): List<Double> {
            if (all.size < tuning.edgeMinFull + 2) return all
            val interior = all.subList(1, all.size - 1)
            val biggest = interior.max()
            val strong = interior.filter { it >= 0.5 * biggest }
            val median = RobustStats.median(strong.toDoubleArray())
            val from = if (all.first() > tuning.edgeOversizeFactor * median) 1 else 0
            val to = if (all.last() > tuning.edgeOversizeFactor * median) all.size - 1 else all.size
            return all.subList(from, to)
        }

        /**
         * Los picos con prominencia ≥ [minProminence] y la distancia mínima del §6 ([minDistance] muestras), con un matiz
         * para los pequeños: un pico más bajo y demasiado cerca de otro SE CONSERVA si el otro tiene una prominencia menor que
         * [relaxBelow] (no es de tamaño completo) y la suya es al menos [AnalysisTuning.closePeakProminenceRatio] veces la de
         * ese otro (no es un rizo del ruido). Son los rebotes de las parciales, que van al doble de cadencia que las
         * completas: sus máximos, en cimas planas y con ruido, caen hasta diez muestras fuera de su sitio y, con los 54 de
         * distancia mínima, un rebote se perdía de cada tres. Dos picos de tamaño completo tan juntos siguen siendo uno
         * (son los bordes de una meseta o de un hundimiento). Con [relaxBelow] = 0 es la regla del §6 tal cual.
         */
        private fun findPeaks(x: DoubleArray, minProminence: Double, minDistance: Int, window: Int, relaxBelow: Double): List<Peak> {
            val all = PeakFinder.find(x, minProminence, 1, window)
            if (minDistance <= 1 || all.size < 2) return all
            val keep = BooleanArray(all.size) { true }
            val byHeight = all.indices.sortedByDescending { all[it].value }
            val ratio = tuning.closePeakProminenceRatio
            fun eliminates(higher: Peak, lower: Peak): Boolean = higher.prominence >= relaxBelow || lower.prominence < ratio * higher.prominence
            for (a in byHeight) {
                if (!keep[a]) continue
                var b = a - 1
                while (b >= 0 && all[a].index - all[b].index < minDistance) {
                    if (eliminates(all[a], all[b])) keep[b] = false
                    b--
                }
                b = a + 1
                while (b < all.size && all[b].index - all[a].index < minDistance) {
                    if (eliminates(all[a], all[b])) keep[b] = false
                    b++
                }
            }
            return all.filterIndexed { i, _ -> keep[i] }
        }

        /**
         * rd con una bajada virtual detrás del último dato si la serie acaba en la mitad alta de su recorrido. Sin ella, la
         * última repetición, cortada por el final (arranque C que acaba en el bloqueo, arranque E que acaba abajo con la barra
         * dejada), no tiene prominencia, porque su valle de la derecha sería el propio final de la serie. Con la bajada es un
         * pico más, sin valle a su derecha, y el clasificador decide qué es. Si la serie acaba abajo no se toca nada.
         */
        private fun withVirtualDescent(x: DoubleArray, low: Double, span: Double): DoubleArray {
            var last = x.size - 1
            while (last >= 0 && x[last].isNaN()) last--
            if (last < 1) return x
            val window = max(1, (tuning.endLevelWindowS * fs).roundToInt())
            val values = DoubleArray(window)
            var count = 0
            var k = last
            while (k >= 0 && count < window) {
                if (!x[k].isNaN()) values[count++] = x[k]
                k--
            }
            val level = RobustStats.median(values.copyOf(count))
            if (level < low + tuning.endHighFraction * span) return x
            val ramp = max(2, (tuning.endDescentS * fs).roundToInt())
            val out = DoubleArray(max(x.size, last + 1 + ramp)) { if (it < x.size) x[it] else Double.NaN }
            val bottom = low - 0.5 * span
            for (i in 1..ramp) out[last + i] = x[last] + (bottom - x[last]) * (i.toDouble() / ramp)
            return out
        }

        /**
         * Centro temporal de un ciclo, en índices fraccionarios: el punto medio del tramo en que rd supera la mitad del
         * recorrido del ciclo (entre su pico y el más alto de sus dos valles). Es mucho más estable que el máximo de rd, que
         * en una cima plana y con ruido cae en cualquier punto de ella.
         */
        private fun centreOf(c: Cand): Double {
            val base = when {
                c.vL >= 0 && c.vR >= 0 -> max(rd[c.vL], rd[c.vR])
                c.vL >= 0 -> rd[c.vL]
                c.vR >= 0 -> rd[c.vR]
                else -> return c.peak.toDouble()
            }
            val top = rd[c.peak]
            if (top.isNaN() || base.isNaN() || !(top > base)) return c.peak.toDouble()
            val level = base + 0.5 * (top - base)
            val from = if (c.vL >= 0) c.vL else 0
            val to = if (c.vR >= 0) c.vR else n - 1
            var lo = c.peak
            while (lo - 1 >= from && !rd[lo - 1].isNaN() && rd[lo - 1] >= level) lo--
            var hi = c.peak
            while (hi + 1 <= to && !rd[hi + 1].isNaN() && rd[hi + 1] >= level) hi++
            return 0.5 * (lo + hi)
        }

        private inline fun log(message: () -> String) {
            RepSegmenter.trace?.invoke(message())
        }

        private fun empty(reason: String, t0: Double? = null, keepAmplitude: Boolean = false): Pass = Pass(
            cycles = emptyList(),
            discarded = emptyList(),
            window = null,
            miniSets = 0,
            t0 = t0,
            sigma = sigma,
            aFull = if (keepAmplitude) aFull else 0.0,
            lRest = if (keepAmplitude) lRest else 0.0,
            reason = reason,
            range = range,
        )

        /** Índice del mínimo de rd en [from, to) ignorando NaN; −1 si no hay muestras válidas. */
        private fun argMin(from: Int, to: Int): Int {
            var best = -1
            var bestValue = Double.POSITIVE_INFINITY
            for (k in max(0, from) until min(n, to)) {
                val v = rd[k]
                if (v.isNaN()) continue
                if (v < bestValue) {
                    bestValue = v
                    best = k
                }
            }
            return best
        }

        /** El giro cae en (o junto a) un hueco rellenado: la señal ahí no es una medida. */
        private fun filledNear(peak: Int): Boolean {
            val w = max(1, (tuning.turnGapWindowS * fs).roundToInt())
            for (k in max(0, peak - w)..min(n - 1, peak + w)) if (series.filled[k]) return true
            return false
        }

        /** Segundos del tramo rellenado contiguo más largo que toca la ventana del giro de [peak] (0 si no hay ninguno). */
        private fun gapAroundTurn(peak: Int): Double {
            val filled = series.filled
            val last = min(n, filled.size) - 1
            val w = max(1, (tuning.turnGapWindowS * fs).roundToInt())
            var longest = 0
            var k = max(0, peak - w)
            val hi = min(last, peak + w)
            while (k <= hi) {
                if (!filled[k]) {
                    k++
                    continue
                }
                var a = k
                while (a - 1 >= 0 && filled[a - 1]) a--
                var b = k
                while (b + 1 <= last && filled[b + 1]) b++
                longest = max(longest, b - a + 1)
                k = b + 1
            }
            return longest / fs
        }

        // ---- paso 5: no-repeticiones ----

        private fun markNonReps(cands: List<Cand>) {
            val horizontalProfile = series.source == CountSeriesBuilder.SOURCE_PLATE &&
                profile.plateSignal != null && profile.id != AnalysisProfileId.ROW
            val bx = series.barX
            val by = series.barY
            for (c in cands) {
                val lo = if (c.vL >= 0) c.vL else c.peak
                val hi = if (c.vR >= 0) c.vR else c.peak
                if (hi > lo) {
                    var walking = 0
                    for (k in lo..hi) if (series.walk[k]) walking++
                    if (walking > tuning.walkOverlapFraction * (hi - lo + 1)) {
                        c.drop("nonrep_walk")
                        continue
                    }
                    if (horizontalProfile && bx != null && by != null) {
                        val dx = range(bx, lo, hi)
                        val dy = range(by, lo, hi)
                        if (!dx.isNaN() && !dy.isNaN() && dx > tuning.horizontalPathRatio * dy) c.drop("nonrep_horizontal")
                    }
                }
            }
        }

        private fun range(x: DoubleArray, lo: Int, hi: Int): Double {
            var mn = Double.POSITIVE_INFINITY
            var mx = Double.NEGATIVE_INFINITY
            for (k in lo..hi) {
                val v = x[k]
                if (v.isNaN()) continue
                if (v < mn) mn = v
                if (v > mx) mx = v
            }
            return if (mn > mx) Double.NaN else mx - mn
        }

        // ---- paso 5: FULL, PARTIAL, FAILED y ruido ----

        private fun classify(cands: List<Cand>, lastIndex: Int) {
            val fullAt = tuning.fullFraction
            val partialAt = tuning.partialFraction

            // Clase por recorrido: a ≥ 0,70 FULL; 0,30 ≤ a < 0,70 PARTIAL; menos, ruido.
            for (c in cands) {
                if (!c.alive) continue
                when {
                    c.a.isNaN() -> c.drop("noise")
                    c.a >= fullAt -> c.kind = RepKind.FULL
                    c.a >= partialAt -> {
                        c.kind = RepKind.PARTIAL
                        c.partial = if (c.e >= tuning.farEndFraction) PartialKind.FAR_END else PartialKind.REST_END
                    }
                    else -> c.drop("noise")
                }
            }

            if (startsEccentric) {
                classifyEccentricStart(cands, lastIndex)
            } else {
                classifyConcentricStart(cands, lastIndex)
            }
        }

        /**
         * Arranque C: un intento a medio camino que se para es FAILED; el último ciclo llega a FULL con subir y sostener. M4: no si
         * volvió entre el 30 y el 70 % ([returnedPartway]): no acabó en el bloqueo, y se juzga por su recorrido.
         */
        private fun classifyConcentricStart(cands: List<Cand>, lastIndex: Int) {
            val fullRise = medianFullRiseSamples(cands)
            for ((i, c) in cands.withIndex()) {
                if (!c.alive && c.discard != "noise") continue
                // Último ciclo: subió lo suficiente y se sostuvo arriba (o la serie acaba antes de volver) → FULL.
                if (i == lastIndex && c.kind != RepKind.FULL && !c.aUp.isNaN() && c.aUp >= tuning.fullFraction &&
                    (c.vR < 0 || sustainedTop(c.peak)) && !returnedPartway(c)
                ) {
                    c.kind = RepKind.FULL
                    c.partial = null
                    c.discard = null
                    c.a = c.aUp
                    continue
                }
                // FAILED: sube entre el 30 % y el 70 %, lo hace despacio (> 1,5× una FULL) y se queda parado antes de volver.
                if (!c.aUp.isNaN() && c.aUp >= tuning.partialFraction && c.aUp < tuning.fullFraction && fullRise != null) {
                    val rise = piecesOf(c).rise
                    val stall = stallAtPeakSamples(c)
                    log { "FAILED(C)? p=%d subida=%s (FULL %.1f) parada=%s".format(c.peak, rise?.let { it.end - it.start }, fullRise, stall) }
                    if (rise != null && stall != null &&
                        rise.end - rise.start > tuning.failedConcentricFactor * fullRise &&
                        stall >= tuning.failedStallS * fs
                    ) {
                        c.discard = null
                        c.kind = RepKind.FAILED
                        c.partial = null
                        if (c.a.isNaN()) c.a = c.aUp
                    }
                }
            }
        }

        /**
         * Arranque E: el fallo solo se ve al final. Si el ciclo llega al extremo lejano (aUp ≥ 0,70) y no vuelve (aDn < 0,70)
         * pero lo intenta (≥ 0,15·A) y el intento se queda parado ≥ 0,5 s, es FAILED; la bajada que sigue es la dejada de la
         * barra y se ignora (SETDOWN). Si es el último ciclo y no vuelve ni a la mitad, también se ignora.
         *
         * M4: una fallida acaba la serie ([failureEndsTheSet]): un rebote parado que sigue con otros se juzga por su recorrido.
         * Y el último ciclo que vuelve al menos el 30 % ([returnedPartway]) no es una dejada: se juzga por su recorrido.
         */
        private fun classifyEccentricStart(cands: List<Cand>, lastIndex: Int) {
            for ((i, c) in cands.withIndex()) {
                if (!c.alive && c.discard != "noise") continue
                if (c.aUp.isNaN() || c.aUp < tuning.fullFraction) continue
                val back = c.aDn
                if (!back.isNaN() && back >= tuning.failedReturnBelow) continue
                val attempted = !back.isNaN() && back >= tuning.failedAttemptFraction
                val valleyStall = if (attempted) valleyStallSamples(c, cands.getOrNull(i + 1)) else 0.0
                log { "FAILED(E)? p=%d vuelta=%.2f parada=%.1f".format(c.peak, back, valleyStall) }
                if (attempted && valleyStall >= tuning.failedValleyStallS * fs && failureEndsTheSet(cands, i)) {
                    c.discard = null
                    c.kind = RepKind.FAILED
                    c.partial = null
                    c.a = back
                    cands.getOrNull(i + 1)?.let { next -> if (next.alive || next.discard == "noise") next.drop("setdown") }
                } else if (i == lastIndex && (back.isNaN() || back < tuning.setdownReturnBelow) && !returnedPartway(c)) {
                    c.drop("setdown")
                }
            }
        }

        /**
         * M4: una fallida con arranque E acaba la serie. Si la serie acaba en la parada, es una fallida (§6). Si la sigue un
         * ciclo, ese ciclo es la dejada: no vuelve solo a medias (eso sería otro rebote de un tren de parciales en
         * estiramiento) y tras él no queda ninguna repetición viva. Si no, la parada era la de un rebote y se juzga por su
         * recorrido.
         */
        private fun failureEndsTheSet(cands: List<Cand>, i: Int): Boolean {
            if (!partials.failedNeedsSetdown) return true
            val next = cands.getOrNull(i + 1) ?: return true
            val nextBack = next.aDn
            if (!nextBack.isNaN() && nextBack >= tuning.partialFraction && nextBack < tuning.fullFraction) return false
            for (k in i + 2 until cands.size) if (cands[k].alive) return false
            return true
        }

        /**
         * M4: el último ciclo [c] volvió entre el 30 y el 70 %: no acaba en el giro (un bloqueo con arranque C) ni sin intentar
         * volver (una dejada con arranque E), así que se juzga por su recorrido. Si la serie se corta mientras aún volvía, lo
         * decide el borde censurado (`gatePartials`).
         */
        private fun returnedPartway(c: Cand): Boolean =
            partials.lastReturnByAmplitude && c.vR >= 0 && !c.aDn.isNaN() &&
                c.aDn >= tuning.partialFraction && c.aDn < tuning.fullFraction

        // ---- paso 5b (M4): parciales que no se sostienen ----

        /**
         * M4: tras la clase por recorrido, una PARTIAL solo queda si su caída es clara:
         * 1. Borde censurado ([censorEdges]): el primer o el último ciclo, cortado por el vídeo mientras se movía, no se juzga por
         *    el lado cortado.
         * 1b. Parcial estrecha ([dropNarrowPartials]): un bache de ruido, no cuenta.
         * 2. Referencia estable: sin referencia de recorrido aplicada, hacen falta al menos `minStableFull` completas; con menos,
         *    la amplitud completa sale de un solo ciclo (una preparación, un pico raro) y ninguna parcial se sostiene.
         * 3. Parcial aislada entre dos completas: necesita a ≤ fullFraction − `sandwichMargin`. Una repetición algo más corta en
         *    mitad de una serie de completas suele ser una medida (un giro mal visto, un cambio de postura), no una parcial.
         * Lo que no se sostiene cuenta como completa dudosa (SHORT_FULL): la confianza lo sabe.
         */
        private fun gatePartials(cands: List<Cand>) {
            val alive = cands.filter { it.alive && it.kind != null }
            if (alive.isEmpty()) {
                evidence = PartialEvidence(stableReference = referenceApplied, referenceApplied = referenceApplied,
                    externalSource = if (referenceApplied) range?.reference?.source else null)
                return
            }
            if (partials.censorEdges) censorEdges(alive)
            if (partials.minPartialWidth > 0.0) dropNarrowPartials(alive.filter { it.alive })
            val live = alive.filter { it.alive }
            val fulls = live.count { it.kind == RepKind.FULL }
            val stable = referenceApplied || fulls >= partials.minStableFull
            if (!stable) for (c in live) if (c.kind == RepKind.PARTIAL) toShortFull(c)
            for ((i, c) in live.withIndex()) {
                if (c.kind != RepKind.PARTIAL) continue
                val before = live.getOrNull(i - 1)
                val after = live.getOrNull(i + 1)
                val sandwiched = before != null && after != null && before.kind == RepKind.FULL && after.kind == RepKind.FULL &&
                    !before.shortFull && !after.shortFull
                if (sandwiched && c.a > tuning.fullFraction - partials.sandwichMargin) toShortFull(c)
            }
            val partialCycles = live.filter { it.kind == RepKind.PARTIAL }
            val borderline = partialCycles.count {
                it.a > tuning.fullFraction - tuning.ambiguityMargin || it.a < tuning.partialFraction + tuning.ambiguityMargin
            }
            evidence = PartialEvidence(
                stableReference = stable,
                referenceApplied = referenceApplied,
                externalSource = if (referenceApplied) range?.reference?.source else null,
                margin = partials.sandwichMargin,
                borderline = borderline,
                shortFull = live.count { it.shortFull },
                fullCycles = fulls,
            )
            log {
                "parciales M4: estable=%s completas=%d dudosas=%s limite=%d".format(
                    stable, fulls, live.filter { it.shortFull }.map { it.peak }, borderline,
                )
            }
        }

        /**
         * M4 · parcial estrecha: más estrecha a media altura que `minPartialWidth` por la anchura mediana de las completas (o de
         * todos los ciclos vivos, si hay menos de dos completas y al menos tres ciclos) es un bache de ruido, no una repetición.
         * Los ciclos con el giro junto a un hueco rellenado no entran en la referencia (la forma del pico la pone el relleno); si
         * uno de ellos se aparta por estrecho, conserva su duda (TURN_IN_GAP): puede ser una repetición real mal vista, y la
         * confianza no debe subir por haberlo apartado.
         */
        private fun dropNarrowPartials(live: List<Cand>) {
            val measurable = live.filter { it.alive && !it.turnInGap }
            var widths = measurable.filter { it.kind == RepKind.FULL && !it.shortFull }.map(::halfHeightWidth).filter { !it.isNaN() }
            if (widths.size < 2) {
                widths = measurable.map(::halfHeightWidth).filter { !it.isNaN() }
                if (widths.size < 3) return
            }
            val reference = RobustStats.median(widths.toDoubleArray())
            for (c in live) {
                if (!c.alive || c.kind != RepKind.PARTIAL) continue
                val width = halfHeightWidth(c)
                if (!width.isNaN() && width < partials.minPartialWidth * reference) {
                    c.drop("noise")
                    c.narrow = true
                }
            }
        }

        /** Anchura de [c] a media altura entre su valle más alto y el pico, en muestras, sin pasar de sus valles; NaN sin medida. */
        private fun halfHeightWidth(c: Cand): Double {
            val top = rd[c.peak]
            val left = if (c.vL >= 0) rd[c.vL] else Double.NaN
            val right = if (c.vR >= 0) rd[c.vR] else Double.NaN
            val valley = when {
                left.isNaN() -> right
                right.isNaN() -> left
                else -> max(left, right)
            }
            if (top.isNaN() || valley.isNaN()) return Double.NaN
            val level = valley + 0.5 * (top - valley)
            var lo = c.peak
            while (lo - 1 >= 0 && !rd[lo - 1].isNaN() && rd[lo - 1] >= level && (c.vL < 0 || lo - 1 >= c.vL)) lo--
            var hi = c.peak
            while (hi + 1 < n && !rd[hi + 1].isNaN() && rd[hi + 1] >= level && (c.vR < 0 || hi + 1 <= c.vR)) hi++
            return (hi - lo + 1).toDouble()
        }

        /** Una parcial que no se sostiene pasa a completa dudosa (SHORT_FULL): cuenta como repetición y la confianza lo sabe. */
        private fun toShortFull(c: Cand) {
            c.kind = RepKind.FULL
            c.partial = null
            c.zone = null
            c.shortFull = true
        }

        /**
         * M4 · borde censurado. El último ciclo vivo es PARCIAL solo por su vuelta (aDn < aUp), su valle está en el final de la
         * serie (a ≤ `censorEdgeS`) y la señal aún bajaba: la velocidad media de los últimos `censorWindowS` es al menos
         * `censorSpeedRatio` de la máxima de la vuelta. El vídeo cortó la vuelta y no se sabe cómo acababa:
         * - con arranque C y la subida completa (aUp ≥ 0,70), cuenta como completa (la concéntrica se vio entera);
         * - con arranque E y la bajada completa (aUp ≥ 0,70), no cuenta: le falta la concéntrica, que es la vuelta. Queda
         *   descartado como `censored` y la confianza lo trata como borde censurado;
         * - si la ida ya era corta (aUp < 0,70), se juzga por ella.
         * Lo mismo al principio, con la ida cortada (aUp < aDn) y la señal ya subiendo: no cuenta (su salida no se vio).
         * Si el vecino es otra parcial (un tren de parciales), sigue la pauta del vecino y se juzga por su recorrido.
         */
        private fun censorEdges(alive: List<Cand>) {
            val window = max(1, (partials.censorWindowS * fs).roundToInt())
            val edge = max(1, (partials.censorEdgeS * fs).roundToInt())
            val first = alive.first()
            if (first.kind == RepKind.PARTIAL && first.vL >= 0 && !first.aUp.isNaN() && (first.aDn.isNaN() || first.aUp < first.aDn)) {
                val firstValid = firstValidIndex()
                val neighbour = alive.getOrNull(1)
                val train = neighbour != null && neighbour.kind == RepKind.PARTIAL
                if (!train && first.vL - firstValid <= edge && departureSpeedRatio(first, firstValid, window) >= partials.censorSpeedRatio) {
                    first.drop("censored")
                }
            }
            val stillAlive = alive.filter { it.alive }
            val last = stillAlive.lastOrNull() ?: return
            if (last.kind != RepKind.PARTIAL || last.vR < 0 || last.aDn.isNaN() || (!last.aUp.isNaN() && last.aDn >= last.aUp)) return
            val neighbour = stillAlive.getOrNull(stillAlive.size - 2)
            if (neighbour != null && neighbour.kind == RepKind.PARTIAL) return
            val lastValid = lastValidIndex()
            if (lastValid - last.vR > edge || !(returnSpeedRatio(last, lastValid, window) >= partials.censorSpeedRatio)) return
            when {
                !last.aUp.isNaN() && last.aUp >= tuning.fullFraction -> if (startsEccentric) {
                    last.drop("censored")
                } else {
                    last.kind = RepKind.FULL
                    last.partial = null
                    last.a = last.aUp
                }
                !last.aUp.isNaN() && last.aUp >= tuning.partialFraction -> last.a = last.aUp
                else -> last.drop("noise")
            }
        }

        /** Velocidad media de bajada de rd en los últimos [window] datos frente a la máxima de la vuelta de [c]; NaN sin vuelta. */
        private fun returnSpeedRatio(c: Cand, lastValid: Int, window: Int): Double {
            var peakSpeed = 0.0
            var endSum = 0.0
            var endCount = 0
            for (k in c.peak until lastValid) {
                val a = rd[k]
                val b = rd[k + 1]
                if (a.isNaN() || b.isNaN()) continue
                val v = (a - b) * fs
                if (v > peakSpeed) peakSpeed = v
                if (k >= lastValid - window) { endSum += v; endCount++ }
            }
            return if (peakSpeed > 0.0 && endCount > 0) endSum / endCount / peakSpeed else Double.NaN
        }

        /** Velocidad media de subida de rd en los primeros [window] datos frente a la máxima de la ida de [c]; NaN sin ida. */
        private fun departureSpeedRatio(c: Cand, firstValid: Int, window: Int): Double {
            var peakSpeed = 0.0
            var startSum = 0.0
            var startCount = 0
            for (k in firstValid until c.peak) {
                val a = rd[k]
                val b = rd[k + 1]
                if (a.isNaN() || b.isNaN()) continue
                val v = (b - a) * fs
                if (v > peakSpeed) peakSpeed = v
                if (k < firstValid + window) { startSum += v; startCount++ }
            }
            return if (peakSpeed > 0.0 && startCount > 0) startSum / startCount / peakSpeed else Double.NaN
        }

        private fun firstValidIndex(): Int {
            var k = 0
            while (k < n - 1 && rd[k].isNaN()) k++
            return k
        }

        /** El nivel más bajo de los valles de los ciclos vivos (el reposo de la zona con la referencia aplicada). */
        private fun lowestValley(cands: List<Cand>): Double {
            var low = Double.POSITIVE_INFINITY
            for (c in cands) {
                if (!c.alive) continue
                if (c.vL >= 0 && rd[c.vL] < low) low = rd[c.vL]
                if (c.vR >= 0 && rd[c.vR] < low) low = rd[c.vR]
            }
            return if (low.isFinite()) low else lRest
        }

        /**
         * M4 · zona de una parcial: sale del extremo que no alcanza. turnGap = lo que le falta al pico para llegar al giro y
         * restGap = lo que el valle de su lado corto queda por encima del reposo, los dos en fracciones de la amplitud completa.
         * Si se queda más lejos del reposo que del giro, está del lado del giro (FAR_END); si no, del lado del reposo (REST_END).
         * Con la señal al revés, el giro de la señal es el reposo del perfil. La excéntrica acaba en el estiramiento: con
         * arranque E el giro del perfil es la zona estirada y el reposo la acortada; con arranque C, al revés.
         */
        private fun assignZone(c: Cand) {
            val top = rd[c.peak]
            val left = if (c.vL >= 0) rd[c.vL] else Double.NaN
            val right = if (c.vR >= 0) rd[c.vR] else Double.NaN
            val valley = when {
                left.isNaN() -> right
                right.isNaN() -> left
                else -> max(left, right)
            }
            val turnGap = (anchor + aRange - top) / aRange
            val restGap = if (valley.isNaN()) 0.0 else (valley - anchor) / aRange
            val turnSide = restGap > turnGap
            c.partial = if (turnSide) PartialKind.FAR_END else PartialKind.REST_END
            val profileTurnSide = if (flipped) !turnSide else turnSide
            c.zone = if (profileTurnSide == startsEccentric) PartialZone.STRETCHED else PartialZone.SHORTENED
        }

        /** Sube y se sostiene arriba (≥ 70 % de la amplitud) al menos 0,3 s alrededor del giro. */
        private fun sustainedTop(peak: Int): Boolean {
            val level = lRest + tuning.lastSustainedLevel * aFull
            var count = 1
            var k = peak - 1
            while (k >= 0 && !rd[k].isNaN() && rd[k] >= level) { count++; k-- }
            k = peak + 1
            while (k < n && !rd[k].isNaN() && rd[k] >= level) { count++; k++ }
            return count >= tuning.lastSustainedS * fs
        }

        private fun piecesOf(c: Cand): MovementPieces.CyclePieces =
            MovementPieces.cycle(pieces, c.vL, c.peak, c.vR, max(1, (tuning.pieceLevelWindowS * fs).roundToInt()))

        /** Mediana, en muestras, de la duración de la subida de las FULL; `null` si no hay ninguna medible. */
        private fun medianFullRiseSamples(cands: List<Cand>): Double? {
            val values = ArrayList<Double>()
            for (c in cands) {
                if (!c.alive || c.kind != RepKind.FULL) continue
                val piece = piecesOf(c).rise ?: continue
                values.add(piece.end - piece.start)
            }
            return if (values.isEmpty()) null else RobustStats.median(values.toDoubleArray())
        }

        /** Muestras que el ciclo está parado en el giro antes de volver; `null` si no se puede medir. */
        private fun stallAtPeakSamples(c: Cand): Double? {
            val tramos = piecesOf(c)
            val rise = tramos.rise ?: return null
            val end = tramos.fall?.start ?: lastValidIndex().toDouble()
            val stall = end - rise.end
            return if (stall > 0.0 && isStill(rise.end, end)) stall else 0.0
        }

        /**
         * Muestras que el ciclo se queda parado en el valle de después del giro: de que acaba la bajada del giro a que empieza
         * la subida al pico siguiente ([next]) o, sin él, hasta el último dato. Una parada es un tramo QUIETO: si entre una
         * cosa y otra la señal se mueve (un rebote que el buscador de picos no separó), no es una parada y vale 0.
         */
        private fun valleyStallSamples(c: Cand, next: Cand?): Double {
            val fall = piecesOf(c).fall ?: return 0.0
            if (c.vR < 0) return 0.0
            val end = if (next == null || next.peak <= c.vR) {
                lastValidIndex().toDouble()
            } else {
                piecesOf(next).rise?.start ?: return 0.0
            }
            val stall = end - fall.end
            return if (stall > 0.0 && isStill(fall.end, end)) stall else 0.0
        }

        /** La señal LP de 5 Hz no varía más de [AnalysisTuning.stillBandFraction]·Afull entre los índices [from] y [to]. */
        private fun isStill(from: Double, to: Double): Boolean {
            val a = max(0, ceil(from).toInt())
            val b = min(n - 1, floor(to).toInt())
            if (b <= a) return true
            var lo = Double.POSITIVE_INFINITY
            var hi = Double.NEGATIVE_INFINITY
            for (k in a..b) {
                val v = pieces[k]
                if (v.isNaN()) continue
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            return !(hi > lo) || hi - lo <= tuning.stillBandFraction * aFull
        }

        private fun lastValidIndex(): Int {
            var k = n - 1
            while (k > 0 && rd[k].isNaN()) k--
            return k
        }

        // ---- paso 6: ventana activa, parciales de preparación y de re-rack ----

        private fun medianFullPeriodS(cands: List<Cand>): Double? {
            val fulls = cands.filter { it.alive && it.kind == RepKind.FULL }
            if (fulls.size < 2) return null
            val gaps = DoubleArray(fulls.size - 1) { (fulls[it + 1].peak - fulls[it].peak) / fs }
            return RobustStats.median(gaps)
        }

        /**
         * La primera o la última FULL con mucho más recorrido que el resto (recoger la barra del suelo, dejarla) no son
         * repeticiones: son preparación y dejada. Solo con tres FULL o más, para que la mediana sea de fiar.
         */
        private fun dropOversizedEdges(cands: List<Cand>) {
            val fulls = cands.filter { it.alive && it.kind == RepKind.FULL }
            if (fulls.size < tuning.edgeMinFull) return
            val first = fulls.first()
            if (first.e > tuning.edgeOversizeFactor) first.drop("setup")
            val last = fulls.last()
            if (last !== first && last.e > tuning.edgeOversizeFactor) last.drop("setdown")
        }

        private fun applyWindow(cands: List<Cand>, tMed: Double) {
            val alive = cands.filter { it.alive }
            val firstFull = alive.indexOfFirst { it.kind == RepKind.FULL }
            val lastFull = alive.indexOfLast { it.kind == RepKind.FULL }
            if (firstFull < 0) return
            val partialsHint = "PARTIALS" in hints
            val restPause = "REST_PAUSE" in hints
            val afterLimitS = if (restPause) tuning.miniSetGapS else tuning.partialGapPeriods * tMed
            val beforeLimitS = tuning.partialGapPeriods * tMed

            // Antes de la primera FULL: preparación, salvo parciales pegados a ella cuando se anuncian PARTIALS.
            var linked = partialsHint
            for (i in firstFull - 1 downTo 0) {
                val c = alive[i]
                if (linked) linked = dwellBetween(c.peak, alive[i + 1].peak) <= beforeLimitS
                if (!linked) c.drop("setup")
            }
            // Después de la última FULL: parciales seguidos se conservan; tras un descanso largo es el re-rack.
            linked = true
            for (i in lastFull + 1 until alive.size) {
                val c = alive[i]
                if (linked) linked = dwellBetween(alive[i - 1].peak, c.peak) <= afterLimitS
                if (!linked) c.drop("rerack")
            }
        }

        private fun valleyBetween(a: Int, b: Int): Int = argMin(a + 1, b)

        /**
         * Segundos de reposo entre dos picos consecutivos: las muestras que quedan dentro de la banda de reposo sobre el nivel
         * bajo (percentil 10) del tramo. No se mide desde el mínimo (un valle suelto, más bajo que el resto del reposo, dejaría
         * fuera media pausa) ni se exige que sean contiguas. Un corte de datos entre los dos picos cuenta como reposo largo.
         */
        private fun dwellBetween(a: Int, b: Int): Double {
            if (b <= a + 1) return 0.0
            val values = DoubleArray(b - a - 1)
            var count = 0
            for (k in a + 1 until b) {
                val v = rd[k]
                if (!v.isNaN()) values[count++] = v
            }
            if (count == 0) return Double.POSITIVE_INFINITY
            val limit = RobustStats.percentile(values.copyOf(count), tuning.restReferencePercentile) + restBand
            var inBand = 0
            for (i in 0 until count) if (values[i] <= limit) inBand++
            return inBand / fs
        }

        // ---- paso 8: mini-series ----

        /** Valles donde el reposo dura ≥ max(8 s, 3× la mediana): ahí empieza otra mini-serie. */
        private fun findCuts(cands: List<Cand>): IntArray {
            val kept = cands.filter { it.alive }.sortedBy { it.peak }
            if (kept.size < 2) return IntArray(0)
            val valleys = IntArray(kept.size - 1) { valleyBetween(kept[it].peak, kept[it + 1].peak) }
            val rests = DoubleArray(valleys.size) { dwellBetween(kept[it].peak, kept[it + 1].peak) }
            val finite = rests.filter { it.isFinite() }
            val median = if (finite.isEmpty()) 0.0 else RobustStats.median(finite.toDoubleArray())
            val limit = max(tuning.miniSetRestMinS, tuning.miniSetRestFactor * median)
            val cuts = ArrayList<Int>()
            for (i in valleys.indices) if (rests[i] >= limit) cuts.add(valleys[i])
            return cuts.toIntArray()
        }

        // ---- paso 7: polaridad ----

        /** Quieto arriba > 2× quieto abajo y ≥ 2 s: el reposo estaba arriba y la señal está al revés. */
        private fun stillTimeSuggestsFlip(): Boolean {
            val v = Derivative.central(rd, fs)
            val rest = ArrayList<Double>()
            val absV = DoubleArray(n) { abs(v[it]) }
            for (k in 0 until n) {
                if (!v[k].isNaN() && !rd[k].isNaN() && rd[k] <= lRest + tuning.restSampleFraction * aFull) rest.add(v[k])
            }
            val sigmaV = if (rest.size >= 3) RobustStats.mad(rest.toDoubleArray()) else 0.0
            val vMax = RobustStats.percentile(absV, 99.0)
            val vTh = max(tuning.speedThresholdFraction * vMax, tuning.speedThresholdSigmas * sigmaV)
            val mid = lRest + 0.5 * aFull
            var down = 0
            var up = 0
            for (k in 0 until n) {
                if (rd[k].isNaN() || v[k].isNaN() || abs(v[k]) > vTh) continue
                if (rd[k] < mid) down++ else up++
            }
            val upS = up / fs
            val downS = down / fs
            if (!(upS > tuning.polarityStillRatio * downS && upS >= tuning.polarityMinStillS)) return false
            // Si la serie empieza y acaba abajo (en reposo), la polaridad es la buena aunque haya pausas largas arriba.
            val start = edgeLevel(fromStart = true)
            val end = edgeLevel(fromStart = false)
            return !(!start.isNaN() && !end.isNaN() && start < mid && end < mid)
        }

        /** Mediana de rd en el primer (o el último) segundo con datos de la serie; NaN si no hay. */
        private fun edgeLevel(fromStart: Boolean): Double {
            val size = max(1, (tuning.polarityEdgeS * fs).roundToInt())
            val values = DoubleArray(size)
            var count = 0
            var k = if (fromStart) 0 else n - 1
            val step = if (fromStart) 1 else -1
            while (k in 0 until n && count < size) {
                if (!rd[k].isNaN()) values[count++] = rd[k]
                k += step
            }
            return if (count == 0) Double.NaN else RobustStats.median(values.copyOf(count))
        }

        // ---- n_amb ----

        /**
         * Ciclos dudosos: a cerca de los umbrales (en las franjas [0,22; 0,38] y [0,62; 0,78]), giro en un hueco rellenado, o
         * un pico cuya prominencia queda a ±15 % del umbral de picos (aceptado o rechazado por poco). Cada uno con su tipo, la
         * altura de su pico sobre el reposo y, en un giro en hueco, la duración del hueco (M3); el número de dudosos es el de
         * siempre.
         */
        private fun countAmbiguous(
            cands: List<Cand>,
            accepted: List<Pair<Int, Double>>,
            extended: List<Pair<Int, Double>>,
            threshold: Double,
        ): List<AmbiguityItem> {
            val margin = tuning.ambiguityMargin
            val items = ArrayList<AmbiguityItem>()
            val counted = HashSet<Int>()
            for (c in cands) {
                if (!c.alive && c.discard != "noise") continue
                if (c.a.isNaN()) continue
                val nearPartial = abs(c.a - tuning.partialFraction) <= margin
                val nearFull = abs(c.a - tuning.fullFraction) <= margin
                val kind = when {
                    c.alive && c.shortFull -> AmbiguityKind.SHORT_FULL
                    nearFull -> AmbiguityKind.NEAR_FULL
                    (c.alive || c.narrow) && c.turnInGap -> AmbiguityKind.TURN_IN_GAP
                    nearPartial -> AmbiguityKind.NEAR_PARTIAL
                    else -> null
                }
                if (kind != null) {
                    val gap = if (kind == AmbiguityKind.TURN_IN_GAP) gapAroundTurn(c.peak) else 0.0
                    items.add(AmbiguityItem(kind, c.e, gap))
                    counted.add(c.peak)
                }
            }
            val low = threshold * (1.0 - tuning.prominenceBorderline)
            val high = threshold * (1.0 + tuning.prominenceBorderline)
            for ((peak, prominence) in accepted + extended) {
                if (prominence < low || prominence > high) continue
                if (!counted.add(peak)) continue
                val height = if (peak in rd.indices && aFull > 0.0) (rd[peak] - lRest) / aFull else Double.NaN
                items.add(AmbiguityItem(AmbiguityKind.PROMINENCE, height))
            }
            return items
        }
    }

}
