package com.example.kpkn.domain.biomechanics.vision

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Umbrales de la visión del disco (§8 puntos 1 a 3), con los valores de la especificación como defecto. Los comparten el
 * detector (`PlateDetector`) y el rastreador (`PlateTracker`); VT10 los ajusta aquí, junto con `PlateTrackerConfig`.
 */
data class PlateVisionConfig(
    // ---- Bordes (§8 p1) ----
    /** Reducción s ∈ {1..[maxReduction]} para que r/s quede en [[targetRadiusMinPx], [targetRadiusMaxPx]] (se toma la menor). */
    val targetRadiusMinPx: Double = 20.0,
    val targetRadiusMaxPx: Double = 45.0,
    val maxReduction: Int = 4,
    /** Bordes: |g| ≥ max(P[edgePercentile] de la ventana, [edgeFloor]), con |g| en unidades de Sobel crudo. */
    val edgePercentile: Double = 85.0,
    val edgeFloor: Double = 20.0,

    // ---- Hough de gradiente (§8 p2) ----
    /** Sectores de la cobertura (como mucho 62) y condición de apoyo: |cos| ≥ [coverageCos] a ±[coverageBandPx] del contorno. */
    val sectors: Int = 36,
    val coverageCos: Double = 0.9,
    val coverageBandPx: Double = 1.5,
    /** Al adquirir gana el pico de radio más externo con cobertura ≥ esto; si ninguno la alcanza, el de más cobertura si llega a [fallbackMinCoverage]. */
    val acquireMinCoverage: Double = 0.4,
    val fallbackMinCoverage: Double = 0.2,
    /** Histograma de radios: solo cuentan los bordes con |cos| ≥ esto, y un pico debe tener al menos esta fracción del mayor. */
    val radiusMinCos: Double = 0.7,
    val radiusPeakFraction: Double = 0.2,
    /** Picos del acumulador que se evalúan, y votos mínimos del pico (por píxel de circunferencia del radio menor). */
    val accumulatorPeaks: Int = 3,
    val minPeakVotesPerPx: Double = 0.25,

    // ---- Refino a resolución completa: Kåsa con inliers a ±[inlierBandPx] (§8 p2) ----
    val inlierBandPx: Double = 3.0,
    /** Primera pasada: banda de ±max([scanBandMinPx], [scanBandFraction]·r, 1,5·s) alrededor del modelo. */
    val scanBandFraction: Double = 0.12,
    val scanBandMinPx: Double = 3.0,
    /** Perfiles radiales: paso en el radio, longitud de arco de un sector, paso entre subrayos y sus límites. */
    val scanStepPx: Double = 0.5,
    val scanArcPx: Double = 5.0,
    val scanSubRayPx: Double = 1.0,
    val scanMaxSubRays: Int = 9,
    val scanMinSectors: Int = 48,
    val scanMaxSectors: Int = 96,
    /**
     * Un borde de un perfil: pendiente ≥ max([scanMinSlope], [scanMedianFraction]·mediana de los máximos de todos los perfiles,
     * [scanRelativeSlope]·máximo de su propio perfil). La mediana de los máximos es la fuerza típica del borde de este contorno: un
     * perfil sin borde (un oclusor, una zona lisa) tiene un máximo de ruido, muy por debajo. Estimar el ruido con un percentil de |d| no
     * sirve con bandas estrechas: los dos lóbulos del borde y del aro ocupan media banda.
     */
    val scanMinSlope: Double = 1.5,
    val scanMedianFraction: Double = 0.3,
    val scanRelativeSlope: Double = 0.4,
    /** Elipse: solo si los inliers cubren esta fracción de los sectores y a/b queda en [[ellipseMinRatio], 1/[ellipseMinRatio]]. */
    val ellipseMinCoverage: Double = 0.6,
    val ellipseMinRatio: Double = 0.6,
    /** El refino falla si quedan menos de esta fracción de los perfiles como inliers. */
    val minInlierFraction: Double = 0.35,
    val fitIterations: Int = 4,

    // ---- Elipses: hipótesis de a/b (el Hough vota en un espacio estirado; ver CircleHough.detect) ----
    /**
     * Sin un a/b medido se prueban las hipótesis 1 − k·[ratioGridStep], con k de 0 a [ratioGridSteps] − 1 (1; 0,94; 0,88; 0,82; 0,76):
     * dejan un residuo de ≤ 3 % con cualquier a/b entre 0,73 y 1, y con un residuo mayor el Hough da un contorno mixto (borde del
     * disco en unos sitios y aro en otros) con calidad alta pero centro y a/b equivocados. Gana la hipótesis de más acumulador: la
     * calidad q no distingue al contorno bueno del mixto, y el pico del acumulador es el más alto con el a/b exacto.
     */
    val ratioGridStep: Double = 0.06,
    val ratioGridSteps: Int = 5,
    /** Tras elegir la hipótesis se repite una vez con el a/b medido si difiere de ella en más de esto. */
    val ratioRefitDelta: Double = 0.02,
    /** Un a/b medido solo cuenta como medida del disco si queda a menos de esto de la hipótesis con la que se midió. */
    val ratioConsistency: Double = 0.05,
)

/** Cómo se elige el radio entre los picos del histograma (§8 punto 2). */
internal enum class RadiusChoice {
    /** Adquirir: el pico más externo con cobertura suficiente (gana el borde del disco y no el aro ni el buje). */
    OUTERMOST,

    /** Seguir: el pico más cercano al radio del fotograma anterior. */
    NEAREST,
}

/** Un disco candidato, en píxeles del fotograma (coordenadas continuas del paquete). */
internal class HoughCandidate {
    var cx = 0.0
    var cy = 0.0

    /** Radio del círculo equivalente, √(a·b). */
    var r = 0.0
    var a = 0.0
    var b = 0.0

    /** a/b (semieje horizontal entre vertical). */
    var ratio = 1.0

    /** `true` si a/b salió del ajuste de elipse de esta imagen; si no, vale el que se pidió como referencia. */
    var ratioMeasured = false
    var coverage = 0.0
    var meanCos = 0.0

    /** Calidad q = cobertura × media|cos|. */
    var quality = 0.0

    /** Votos del pico del acumulador (suma 3×3) por píxel de circunferencia. */
    var accScore = 0.0

    /** `true` si el refino a resolución completa funcionó; si no, valen el centro y el radio gruesos del Hough. */
    var refined = false

    /** El a/b previsto con el que se votó (1 si se trató como un círculo). */
    var hypothesis = 1.0

    fun copyFrom(o: HoughCandidate) {
        cx = o.cx
        cy = o.cy
        r = o.r
        a = o.a
        b = o.b
        ratio = o.ratio
        ratioMeasured = o.ratioMeasured
        coverage = o.coverage
        meanCos = o.meanCos
        quality = o.quality
        accScore = o.accScore
        refined = o.refined
        hypothesis = o.hypothesis
    }
}

/**
 * Hough de gradiente sobre luma (§8 puntos 1 y 2) con refino a resolución completa.
 *
 * 1. [Gradients]: ventana reducida, Gauss de σ = 1 y Sobel; bordes con |g| ≥ max(P85, 20).
 * 2. Cada borde vota en p ± r·n para cada radio entero r del rango, con las dos polaridades; el acumulador se suaviza con una
 *    suma 3×3 y su máximo da el centro, con subpíxel cuadrático.
 * 3. Radio: histograma de |p − c| ponderado por |g|·|cos∠(n, p − c)|. Antes se recentra el pico con el pico más fuerte del
 *    histograma: el promedio de los centros que votan los bordes alineados de ese radio (±1,5 px) quita el error de 1–3 px que el
 *    máximo del acumulador tiene con poco contraste. Cobertura: fracción de los 36 sectores con un borde alineado (|cos| ≥ 0,9) a
 *    ±1,5 px del contorno. Se elige el pico más externo con cobertura ≥ 0,4 al adquirir y el más cercano al radio anterior al seguir.
 * 4. Refino: perfiles radiales a resolución completa (media a lo largo de un arco corto, para bajar el ruido) dan puntos de
 *    borde con subpíxel; se ajusta un círculo de Kåsa y la elipse alineada a ejes, quitando los puntos a más de 3 px. En dos
 *    pasadas: la primera, de banda ancha (al adquirir, con el borde más externo de cada perfil: el aro de dentro no puede dar un
 *    contorno mixto), y la segunda, de ±3 px con el borde más cercano. La calidad q = cobertura × media|cos| se mide al final sobre
 *    los bordes de la ventana con el contorno ya refinado.
 *
 * Es de un solo hilo y reutiliza sus buffers: tras la primera ventana de un tamaño dado no asigna nada.
 */
internal class CircleHough(private val cfg: PlateVisionConfig) {
    private val grad = Gradients()

    // Bordes de la ventana en el ESPACIO DEL MODELO (coordenadas de trabajo, estiradas si a/b ≠ 1: ver [detect]): posición, normal
    // unitaria del gradiente y módulo. Con a/b = 1 el espacio del modelo es el de la imagen reducida.
    private var ex = FloatArray(0)
    private var ey = FloatArray(0)
    private var enx = FloatArray(0)
    private var eny = FloatArray(0)
    private var emg = FloatArray(0)
    private var ne = 0

    // Estiramiento x e y de la ventana actual (1 y 1 si el contorno buscado es un círculo).
    private var modelSx = 1.0
    private var modelSy = 1.0

    // Acumulador, su copia intermedia y su versión suavizada (suma 3×3).
    private var acc = IntArray(0)
    private var accTmp = IntArray(0)
    private var accSmooth = IntArray(0)

    // Histograma de radios y sus picos.
    private val rawHist = FloatArray(HIST_MAX_BINS)
    private val smoothHist = FloatArray(HIST_MAX_BINS)
    private val peakR = DoubleArray(MAX_RADIUS_PEAKS)
    private val peakWeight = DoubleArray(MAX_RADIUS_PEAKS)
    private val peakCov = DoubleArray(MAX_RADIUS_PEAKS)
    private val recentred = DoubleArray(2)

    // Resultado de support().
    private var covOut = 0.0
    private var cosOut = 0.0

    // Refino.
    private val px = DoubleArray(MAX_SCAN)
    private val py = DoubleArray(MAX_SCAN)
    private val alive = BooleanArray(MAX_SCAN)
    private var prof = DoubleArray(0)
    private var der = DoubleArray(0)
    private val sectorMax = DoubleArray(MAX_SCAN)
    private val sectorSorted = DoubleArray(MAX_SCAN)
    private val circle = DoubleArray(3)
    private val ellipse = DoubleArray(4)
    private var scanSectors = 0
    private var fitCx = 0.0
    private var fitCy = 0.0
    private var fitA = 0.0
    private var fitB = 0.0
    private var fitTrusted = false

    /** Reducción que se usaría para un disco de radio [rPx]: la menor s con r/s ≤ 45 (como mucho 4). */
    fun chooseScale(rPx: Double): Int {
        var s = 1
        while (s < cfg.maxReduction && rPx / s > cfg.targetRadiusMaxPx) s++
        return s
    }

    /**
     * Busca discos en la ventana [x0, x1)×[y0, y1) (píxeles del fotograma, se recorta al fotograma) con radio equivalente √(a·b)
     * entre [rMin] y [rMax]. [rRef] es el radio esperado (fija la reducción y, con [RadiusChoice.NEAREST], el pico elegido) y
     * [ratioRef] el a/b previsto (1 si no se conoce). Rellena hasta [maxOut] candidatos de [out] y devuelve cuántos son, por orden de
     * pico del acumulador (el primero es el de más votos).
     *
     * **Elipses.** Un círculo de Hough pone el pico de una elipse en las cúspides de su evoluta, a ≈ b·(1 − (a/b)²) del centro (un
     * 28 % del radio con a/b = 0,85), y no en el centro. Con [ratioRef] ≠ 1 se vota en el ESPACIO DEL MODELO: la ventana se estira
     * (x por 1/√ratio, y por √ratio, sin cambiar el área) para que la elipse prevista sea un círculo de radio equivalente. Con un a/b
     * previsto a ±3 % del real el pico vuelve a caer en el centro; a ±6 %, el refino todavía puede dar un contorno mixto.
     */
    fun detect(
        frame: LumaFrame,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        rMin: Double,
        rMax: Double,
        choice: RadiusChoice,
        rRef: Double,
        ratioRef: Double,
        out: Array<HoughCandidate>,
        maxOut: Int,
    ): Int {
        if (!(rRef > 0.0) || !(rMax >= rMin) || maxOut <= 0) return 0
        val s = chooseScale(rRef)
        if (!grad.compute(frame, x0, y0, x1, y1, s)) return 0
        val w = grad.width
        val h = grad.height
        val ratio = if (ratioRef.isFinite() && ratioRef > MIN_MODEL_RATIO && abs(ratioRef - 1.0) > MODEL_RATIO_EPS) ratioRef else 1.0
        val root = sqrt(ratio)
        modelSx = 1.0 / root
        modelSy = root
        // Dimensiones del acumulador (espacio del modelo): con a/b = 1 son las de la ventana.
        val wA = ceil(w * modelSx - 1e-9).toInt()
        val hA = ceil(h * modelSy - 1e-9).toInt()
        if (wA < Gradients.MIN_SIDE || hA < Gradients.MIN_SIDE) return 0
        val threshold = max(grad.percentile(cfg.edgePercentile).toDouble(), cfg.edgeFloor).toFloat()
        buildEdges(threshold, modelSx.toFloat(), modelSy.toFloat())
        if (ne < MIN_EDGES) return 0

        val rMinW = max(1.0, rMin / s)
        val rMaxW = max(rMinW, rMax / s)
        val rLo = max(1, ceil(rMinW).toInt())
        val rHi = max(rLo, floor(rMaxW).toInt())
        vote(wA, hA, rLo, rHi)
        smoothAccumulator(wA, hA)

        val minVotes = cfg.minPeakVotesPerPx * 2.0 * PI * rMinW
        val suppress = max(3, (0.6 * rMinW).toInt())
        var found = 0
        for (k in 0 until cfg.accumulatorPeaks) {
            if (found >= maxOut) break
            val idx = argMax(wA, hA)
            if (idx < 0) break
            val votes = accSmooth[idx]
            if (votes < minVotes) break
            val ix = idx % wA
            val iy = idx / wA
            var cxw = ix.toDouble()
            var cyw = iy.toDouble()
            val centre = votes.toDouble()
            val denX = accSmooth[idx - 1] - 2.0 * centre + accSmooth[idx + 1]
            if (denX < -1e-9) cxw += (0.5 * (accSmooth[idx - 1] - accSmooth[idx + 1]) / denX).coerceIn(-0.5, 0.5)
            val denY = accSmooth[idx - wA] - 2.0 * centre + accSmooth[idx + wA]
            if (denY < -1e-9) cyw += (0.5 * (accSmooth[idx - wA] - accSmooth[idx + wA]) / denY).coerceIn(-0.5, 0.5)
            if (evaluate(frame, cxw, cyw, votes, rMinW, rMaxW, choice, rRef, ratio, out[found])) found++
            suppressPeak(wA, hA, ix, iy, suppress)
        }
        return found
    }

    // ------------------------------------------------------------------------------------------------------------
    // Bordes y votos
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Lista de bordes (|g| ≥ [threshold]) en el espacio del modelo: la posición estirada por ([sx], [sy]) y la normal del gradiente
     * transformada con la inversa (un gradiente se transforma con T⁻ᵀ: (gx/sx, gy/sy)), de nuevo unitaria.
     */
    private fun buildEdges(threshold: Float, sx: Float, sy: Float) {
        val w = grad.width
        val h = grad.height
        val cap = w * h
        if (ex.size < cap) {
            ex = FloatArray(cap)
            ey = FloatArray(cap)
            enx = FloatArray(cap)
            eny = FloatArray(cap)
            emg = FloatArray(cap)
        }
        val gx = grad.gx
        val gy = grad.gy
        val mag = grad.mag
        val stretched = sx != 1f || sy != 1f
        var k = 0
        for (j in 1 until h - 1) {
            var idx = j * w + 1
            for (i in 1 until w - 1) {
                val m = mag[idx]
                if (m >= threshold && m > 0f) {
                    ex[k] = i * sx
                    ey[k] = j * sy
                    if (stretched) {
                        val tx = gx[idx] / sx
                        val ty = gy[idx] / sy
                        val inv = 1f / sqrt(tx * tx + ty * ty)
                        enx[k] = tx * inv
                        eny[k] = ty * inv
                    } else {
                        val inv = 1f / m
                        enx[k] = gx[idx] * inv
                        eny[k] = gy[idx] * inv
                    }
                    emg[k] = m
                    k++
                }
                idx++
            }
        }
        ne = k
    }

    private fun vote(w: Int, h: Int, rLo: Int, rHi: Int) {
        val n = w * h
        if (acc.size < n) {
            acc = IntArray(n)
            accTmp = IntArray(n)
            accSmooth = IntArray(n)
        } else {
            acc.fill(0, 0, n)
        }
        for (e in 0 until ne) {
            val nx = enx[e]
            val ny = eny[e]
            val x = ex[e]
            val y = ey[e]
            var pxp = x + rLo * nx
            var pyp = y + rLo * ny
            var pxm = x - rLo * nx
            var pym = y - rLo * ny
            for (rr in rLo..rHi) {
                val fx = pxp + 0.5f
                val fy = pyp + 0.5f
                if (fx >= 0f && fy >= 0f) {
                    val ix = fx.toInt()
                    val iy = fy.toInt()
                    if (ix < w && iy < h) acc[iy * w + ix]++
                }
                val gx = pxm + 0.5f
                val gy = pym + 0.5f
                if (gx >= 0f && gy >= 0f) {
                    val ix = gx.toInt()
                    val iy = gy.toInt()
                    if (ix < w && iy < h) acc[iy * w + ix]++
                }
                pxp += nx
                pyp += ny
                pxm -= nx
                pym -= ny
            }
        }
    }

    /** Suma 3×3 del acumulador (separable: filas a `accTmp`, columnas a `accSmooth`). */
    private fun smoothAccumulator(w: Int, h: Int) {
        for (j in 0 until h) {
            val row = j * w
            for (i in 0 until w) {
                var sum = acc[row + i]
                if (i > 0) sum += acc[row + i - 1]
                if (i < w - 1) sum += acc[row + i + 1]
                accTmp[row + i] = sum
            }
        }
        for (j in 0 until h) {
            val row = j * w
            for (i in 0 until w) {
                var sum = accTmp[row + i]
                if (j > 0) sum += accTmp[row + i - w]
                if (j < h - 1) sum += accTmp[row + i + w]
                accSmooth[row + i] = sum
            }
        }
    }

    /** Índice del máximo del acumulador suavizado en el interior de la ventana, o −1 si es todo ceros. */
    private fun argMax(w: Int, h: Int): Int {
        var best = -1
        var bestValue = 0
        for (j in 1 until h - 1) {
            var idx = j * w + 1
            for (i in 1 until w - 1) {
                val v = accSmooth[idx]
                if (v > bestValue) {
                    bestValue = v
                    best = idx
                }
                idx++
            }
        }
        return best
    }

    private fun suppressPeak(w: Int, h: Int, cx: Int, cy: Int, radius: Int) {
        val r2 = radius * radius
        val j0 = if (cy - radius < 0) 0 else cy - radius
        val j1 = if (cy + radius > h - 1) h - 1 else cy + radius
        val i0 = if (cx - radius < 0) 0 else cx - radius
        val i1 = if (cx + radius > w - 1) w - 1 else cx + radius
        for (j in j0..j1) {
            for (i in i0..i1) {
                val dx = i - cx
                val dy = j - cy
                if (dx * dx + dy * dy <= r2) accSmooth[j * w + i] = 0
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Radio, cobertura y refino de un pico
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Evalúa el pico del acumulador en ([cxw], [cyw]) (coordenadas de trabajo): elige el radio, refina a resolución completa y
     * mide la calidad. `false` si no hay un radio plausible.
     */
    private fun evaluate(
        frame: LumaFrame,
        cxw: Double,
        cyw: Double,
        votes: Int,
        rMinW: Double,
        rMaxW: Double,
        choice: RadiusChoice,
        rRef: Double,
        ratioRef: Double,
        out: HoughCandidate,
    ): Boolean {
        val s = grad.scale
        val rRefW = rRef / s
        var n = radiusPeaks(cxw, cyw, rMinW, rMaxW)
        // Recentrado: con la gradiente ruidosa (poco contraste) los votos se reparten en una mancha de varios píxeles y su máximo cae a
        // 1–3 px del centro, más de la mitad de lo que separa el borde del disco de su aro. Con el pico más fuerte del histograma se
        // vuelve a centrar con el promedio de los centros que votan los bordes alineados de ESE radio (a ±1,5 px: el aro no entra), que
        // promedia el ruido de cientos de bordes; y se repite el histograma en el centro nuevo, ya más afilado.
        var centreX = cxw
        var centreY = cyw
        if (n > 0) {
            var strongest = 0
            for (i in 1 until n) if (peakWeight[i] > peakWeight[strongest]) strongest = i
            if (recentre(cxw, cyw, peakR[strongest])) {
                centreX = recentred[0]
                centreY = recentred[1]
                n = radiusPeaks(centreX, centreY, rMinW, rMaxW)
            }
        }
        for (i in 0 until n) {
            support(centreX, centreY, peakR[i], peakR[i])
            peakCov[i] = covOut
        }
        var rW: Double
        if (choice == RadiusChoice.OUTERMOST) {
            var pick = -1
            for (i in n - 1 downTo 0) {
                if (peakCov[i] >= cfg.acquireMinCoverage) {
                    pick = i
                    break
                }
            }
            if (pick < 0) {
                var bestCoverage = cfg.fallbackMinCoverage
                for (i in 0 until n) {
                    if (peakCov[i] >= bestCoverage) {
                        bestCoverage = peakCov[i]
                        pick = i
                    }
                }
            }
            if (pick < 0) return false
            rW = peakR[pick]
        } else {
            var pick = -1
            var bestDistance = Double.MAX_VALUE
            for (i in 0 until n) {
                val d = abs(peakR[i] - rRefW)
                if (d < bestDistance) {
                    bestDistance = d
                    pick = i
                }
            }
            rW = if (pick >= 0) peakR[pick] else rRefW
        }
        if (!(rW > 0.0)) return false

        // Centro y radio gruesos en píxeles del fotograma (el centro del espacio del modelo se desestira), y el modelo (a, b) con el
        // a/b previsto. El radio es el equivalente √(a·b): la transformación del modelo conserva el área.
        val cxF = grad.originX + (centreX / modelSx + 0.5) * s
        val cyF = grad.originY + (centreY / modelSy + 0.5) * s
        val rF = rW * s
        val rootRatio = sqrt(ratioRef)
        val a0 = rF * rootRatio
        val b0 = rF / rootRatio

        val refined = refine(frame, cxF, cyF, a0, b0, choice == RadiusChoice.OUTERMOST)
        val cx: Double
        val cy: Double
        val a: Double
        val b: Double
        if (refined) {
            cx = fitCx
            cy = fitCy
            a = fitA
            b = fitB
        } else {
            cx = cxF
            cy = cyF
            a = a0
            b = b0
        }
        // Calidad final: sobre los bordes de la ventana (en el espacio del modelo), con el contorno refinado.
        support(((cx - grad.originX) / s - 0.5) * modelSx, ((cy - grad.originY) / s - 0.5) * modelSy, a / s * modelSx, b / s * modelSy)
        out.cx = cx
        out.cy = cy
        out.a = a
        out.b = b
        out.r = sqrt(a * b)
        out.refined = refined
        out.ratioMeasured = refined && fitTrusted
        out.ratio = if (out.ratioMeasured) a / b else ratioRef
        out.coverage = covOut
        out.meanCos = cosOut
        out.quality = covOut * cosOut
        out.accScore = votes / (2.0 * PI * rW)
        out.hypothesis = ratioRef
        return true
    }

    /**
     * Histograma de |p − c| de los bordes (ponderado por |g|·|cos|) y sus picos: rellena [peakR] con los radios (de menor a mayor,
     * en píxeles de trabajo) y devuelve cuántos son.
     */
    private fun radiusPeaks(cxw: Double, cyw: Double, rMinW: Double, rMaxW: Double): Int {
        val lo = max(1.0, rMinW - 1.5)
        val hi = rMaxW + 1.5
        var nb = ceil((hi - lo) / RADIUS_BIN).toInt() + 1
        if (nb > HIST_MAX_BINS) nb = HIST_MAX_BINS
        if (nb < 5) return 0
        for (i in 0 until nb) rawHist[i] = 0f
        val lo2 = lo * lo
        val hi2 = hi * hi
        val minCos = cfg.radiusMinCos
        for (e in 0 until ne) {
            val dx = ex[e] - cxw
            val dy = ey[e] - cyw
            val d2 = dx * dx + dy * dy
            if (d2 < lo2 || d2 >= hi2) continue
            val d = sqrt(d2)
            val cosv = abs(enx[e] * dx + eny[e] * dy) / d
            if (cosv < minCos) continue
            val bin = ((d - lo) / RADIUS_BIN).toInt()
            if (bin in 0 until nb) rawHist[bin] += (emg[e] * cosv).toFloat()
        }
        var peak = 0f
        smoothHist[0] = rawHist[0]
        smoothHist[nb - 1] = rawHist[nb - 1]
        for (i in 1 until nb - 1) {
            val v = 0.25f * (rawHist[i - 1] + 2f * rawHist[i] + rawHist[i + 1])
            smoothHist[i] = v
            if (v > peak) peak = v
        }
        if (peak <= 0f) return 0
        val minPeak = (cfg.radiusPeakFraction * peak).toFloat()
        var count = 0
        for (i in 1 until nb - 1) {
            val v = smoothHist[i]
            if (v < minPeak || v < smoothHist[i - 1] || v <= smoothHist[i + 1]) continue
            val den = smoothHist[i - 1] - 2f * v + smoothHist[i + 1]
            val off = if (den < -1e-9f) (0.5f * (smoothHist[i - 1] - smoothHist[i + 1]) / den).toDouble().coerceIn(-0.5, 0.5) else 0.0
            if (count < MAX_RADIUS_PEAKS) {
                peakWeight[count] = v.toDouble()
                peakR[count++] = lo + (i + off + 0.5) * RADIUS_BIN
            }
        }
        return count
    }

    /**
     * Recentra ([cxw], [cyw]) con los bordes del radio [rW]: cada borde alineado (|cos| ≥ 0,9) a ±1,5 px de [rW] del centro actual vota
     * un centro a [rW] de él, a lo largo de su normal y en el sentido que apunta al centro; el nuevo centro es el promedio de esos
     * votos, y se repite una vez con el centro nuevo. Deja el resultado en [recentred]; `false` si hay pocos bordes o el centro se
     * movería más de 0,35·[rW] (en ese caso el pico del acumulador era de otra cosa).
     */
    private fun recentre(cxw: Double, cyw: Double, rW: Double): Boolean {
        var cx = cxw
        var cy = cyw
        val band = cfg.coverageBandPx
        val minCos = cfg.coverageCos
        for (iteration in 0 until RECENTRE_ITERATIONS) {
            var sumX = 0.0
            var sumY = 0.0
            var count = 0
            for (e in 0 until ne) {
                val dx = ex[e] - cx
                val dy = ey[e] - cy
                if (dx > rW + band || dx < -rW - band || dy > rW + band || dy < -rW - band) continue
                val d = sqrt(dx * dx + dy * dy)
                if (d < 1e-6 || abs(d - rW) > band) continue
                val cosv = (enx[e] * dx + eny[e] * dy) / d
                if (cosv < minCos && cosv > -minCos) continue
                val outward = if (cosv > 0.0) 1.0 else -1.0
                sumX += ex[e] - outward * rW * enx[e]
                sumY += ey[e] - outward * rW * eny[e]
                count++
            }
            if (count < MIN_RECENTRE_EDGES) return false
            cx = sumX / count
            cy = sumY / count
        }
        val shift = sqrt((cx - cxw) * (cx - cxw) + (cy - cyw) * (cy - cyw))
        if (shift > MAX_RECENTRE_SHIFT * rW) return false
        recentred[0] = cx
        recentred[1] = cy
        return true
    }

    /**
     * Cobertura y media de |cos| del contorno (elipse alineada a ejes de centro ([cx], [cy]) y semiejes [a] y [b], en coordenadas
     * de trabajo) sobre los bordes de la ventana. Los resultados quedan en [covOut] y [cosOut].
     *
     * - Apoyo: borde a menos de [PlateVisionConfig.coverageBandPx] del contorno (distancia de primer orden a la elipse).
     * - Cobertura: fracción de los sectores con al menos un apoyo alineado (|cos| ≥ 0,9 con la normal del contorno).
     * - Media de |cos|: sobre todos los apoyos de la banda, alineados o no, así que los bordes que cruzan el contorno la bajan.
     */
    private fun support(cx: Double, cy: Double, a: Double, b: Double) {
        covOut = 0.0
        cosOut = 0.0
        if (!(a > 0.5) || !(b > 0.5)) return
        val band = cfg.coverageBandPx
        val a2 = a * a
        val b2 = b * b
        val sectors = cfg.sectors
        val reach = max(a, b) + band + 1.0
        var mask = 0L
        var sumCos = 0.0
        var count = 0
        val twoPi = 2.0 * PI
        for (e in 0 until ne) {
            val dx = ex[e] - cx
            val dy = ey[e] - cy
            if (dx > reach || dx < -reach || dy > reach || dy < -reach) continue
            val gxn = dx / a2
            val gyn = dy / b2
            val rho = sqrt(dx * gxn + dy * gyn)
            val gn = sqrt(gxn * gxn + gyn * gyn)
            if (gn < 1e-12) continue
            val d = (rho - 1.0) * rho / gn
            if (d > band || d < -band) continue
            val cosv = abs(enx[e] * gxn + eny[e] * gyn) / gn
            sumCos += cosv
            count++
            if (cosv >= cfg.coverageCos) {
                var sector = ((atan2(dy, dx) + PI) / twoPi * sectors).toInt()
                if (sector >= sectors) sector = sectors - 1
                if (sector < 0) sector = 0
                mask = mask or (1L shl sector)
            }
        }
        covOut = java.lang.Long.bitCount(mask).toDouble() / sectors
        cosOut = if (count > 0) sumCos / count else 0.0
    }

    // ------------------------------------------------------------------------------------------------------------
    // Refino a resolución completa
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Refina el contorno a resolución completa en dos pasadas: una de banda ancha alrededor del modelo ([cx0], [cy0], [a0], [b0]) y
     * otra de ±3 px alrededor del resultado de la primera. En cada una se toman los perfiles radiales, se ajusta (Kåsa y elipse) y
     * se quitan los puntos fuera de la banda. Deja el resultado en [fitCx], [fitCy], [fitA], [fitB] y [fitTrusted]; `false` si no
     * hay puntos de borde suficientes.
     *
     * Con [outermostFirst] (adquirir) la primera pasada toma en cada perfil el borde fuerte MÁS EXTERNO y no el más cercano al modelo:
     * el centro y el radio del modelo todavía tienen un par de píxeles de error, y con un aro a 3 o 4 px del borde («más cercano»)
     * elige el aro en un lado del disco y el borde en el otro, y da un contorno mixto (radio de menos y centro desplazado) con la
     * calidad q altísima. Detrás del borde exterior de un disco no hay más disco, así que el más externo es el borde; lo que haya
     * fuera (una barra del rack) son unos pocos perfiles que el ajuste robusto quita. La segunda pasada (±3 px) es siempre «más cercano».
     */
    private fun refine(frame: LumaFrame, cx0: Double, cy0: Double, a0: Double, b0: Double, outermostFirst: Boolean): Boolean {
        val s = grad.scale
        val rEq = sqrt(a0 * b0)
        val band1 = max(cfg.scanBandMinPx, max(cfg.scanBandFraction * rEq, 1.5 * s))
        val n1 = scan(frame, cx0, cy0, a0, b0, band1, outermostFirst)
        val min1 = max(MIN_FIT_POINTS, (cfg.minInlierFraction * scanSectors).toInt())
        if (n1 < min1) return false
        if (!robustFit(n1, band1, max(cfg.inlierBandPx, 0.5 * band1), min1)) return false
        val p1x = fitCx
        val p1y = fitCy
        val p1a = fitA
        val p1b = fitB
        val p1trusted = fitTrusted

        val band2 = cfg.inlierBandPx
        val n2 = scan(frame, p1x, p1y, p1a, p1b, band2, false)
        val min2 = max(MIN_FIT_POINTS, (cfg.minInlierFraction * scanSectors).toInt())
        if (n2 >= min2 && robustFit(n2, band2, band2, min2)) return true
        fitCx = p1x
        fitCy = p1y
        fitA = p1a
        fitB = p1b
        fitTrusted = p1trusted
        return true
    }

    /**
     * Puntos de borde con subpíxel sobre perfiles radiales alrededor de la elipse ([a], [b]) de centro ([cx], [cy]), a ±[band] px
     * del contorno. Cada perfil promedia varios subrayos de un sector angular (baja el ruido de la luma) y su derivada (±1 px)
     * da el borde: el más cercano al contorno del modelo entre los que superan el umbral, o con [outermost] el más externo.
     * Rellena [px] y [py]; devuelve cuántos.
     */
    private fun scan(frame: LumaFrame, cx: Double, cy: Double, a: Double, b: Double, band: Double, outermost: Boolean): Int {
        val rEq = sqrt(a * b)
        var nSec = Math.round(2.0 * PI * rEq / cfg.scanArcPx).toInt()
        if (nSec < cfg.scanMinSectors) nSec = cfg.scanMinSectors
        if (nSec > cfg.scanMaxSectors) nSec = cfg.scanMaxSectors
        if (nSec > MAX_SCAN) nSec = MAX_SCAN
        scanSectors = nSec
        val dt = cfg.scanStepPx
        val np = (2.0 * band / dt).toInt() + 1
        if (np < 9) return 0
        val need = nSec * np
        if (prof.size < need) {
            prof = DoubleArray(need)
            der = DoubleArray(need)
        }
        val twoPi = 2.0 * PI
        val a2 = a * a
        val b2 = b * b
        for (k in 0 until nSec) {
            val phi = twoPi * (k + 0.5) / nSec
            val cphi = cos(phi)
            val sphi = sin(phi)
            val t0 = 1.0 / sqrt(cphi * cphi / a2 + sphi * sphi / b2)
            val arc = twoPi * t0 / nSec
            var m = Math.round(arc / cfg.scanSubRayPx).toInt()
            if (m < 1) m = 1
            if (m > cfg.scanMaxSubRays) m = cfg.scanMaxSubRays
            val dphi = arc / m / t0
            val base = k * np
            for (i in 0 until np) prof[base + i] = 0.0
            for (j in 0 until m) {
                val phij = phi + (j - (m - 1) * 0.5) * dphi
                val cj = cos(phij)
                val sj = sin(phij)
                val t0j = 1.0 / sqrt(cj * cj / a2 + sj * sj / b2)
                var t = t0j - band
                for (i in 0 until np) {
                    prof[base + i] += frame.sample(cx + t * cj, cy + t * sj)
                    t += dt
                }
            }
            val inv = 1.0 / m
            for (i in 0 until np) prof[base + i] *= inv
        }

        // Derivada (diferencia sobre ±1 px) y máximo de |d| de cada perfil.
        val invSpan = 1.0 / (4.0 * dt)
        for (k in 0 until nSec) {
            val base = k * np
            der[base] = 0.0
            der[base + 1] = 0.0
            der[base + np - 2] = 0.0
            der[base + np - 1] = 0.0
            var maxAbs = 0.0
            for (i in 2 until np - 2) {
                val d = (prof[base + i + 2] - prof[base + i - 2]) * invSpan
                der[base + i] = d
                val v = abs(d)
                if (v > maxAbs) maxAbs = v
            }
            sectorMax[k] = maxAbs
        }
        val slopeMin = max(cfg.scanMinSlope, cfg.scanMedianFraction * median(sectorMax, sectorSorted, nSec))

        var n = 0
        for (k in 0 until nSec) {
            val base = k * np
            val maxAbs = sectorMax[k]
            if (maxAbs < slopeMin) continue
            val accept = max(slopeMin, cfg.scanRelativeSlope * maxAbs)
            var pick = -1
            var pickOffset = Double.MAX_VALUE
            for (i in 3 until np - 3) {
                val v = abs(der[base + i])
                if (v < accept || v < abs(der[base + i - 1]) || v <= abs(der[base + i + 1])) continue
                if (outermost) {
                    pick = i
                } else {
                    val offset = abs(i * dt - band)
                    if (offset < pickOffset) {
                        pickOffset = offset
                        pick = i
                    }
                }
            }
            if (pick < 0) continue
            val vm = abs(der[base + pick - 1])
            val v0 = abs(der[base + pick])
            val vp = abs(der[base + pick + 1])
            val den = vm - 2.0 * v0 + vp
            val delta = if (den < -1e-9) (0.5 * (vm - vp) / den).coerceIn(-0.5, 0.5) else 0.0
            val phi = twoPi * (k + 0.5) / nSec
            val cphi = cos(phi)
            val sphi = sin(phi)
            val t0 = 1.0 / sqrt(cphi * cphi / a2 + sphi * sphi / b2)
            val t = t0 - band + (pick + delta) * dt
            px[n] = cx + t * cphi
            py[n] = cy + t * sphi
            n++
        }
        return n
    }

    /**
     * Ajuste robusto de los [n] puntos de [px] y [py]: ajusta (Kåsa y, si hay cobertura suficiente, elipse), quita los puntos a más
     * de [bandPx] del contorno y repite; la banda baja linealmente de [startBand] a [endBand]. Deja el resultado en [fitCx], [fitCy],
     * [fitA], [fitB] y [fitTrusted] (`true` si el contorno es una elipse ajustada y no el círculo de Kåsa). `false` si quedan
     * menos de [minPoints] puntos.
     */
    private fun robustFit(n: Int, startBand: Double, endBand: Double, minPoints: Int): Boolean {
        for (i in 0 until n) alive[i] = true
        val iterations = cfg.fitIterations
        for (iteration in 0..iterations) {
            if (!fitOnce(n, minPoints)) return false
            if (iteration == iterations) break
            val t = if (iterations <= 1) endBand else startBand + (endBand - startBand) * (iteration + 1).toDouble() / iterations
            for (i in 0 until n) {
                val d = CircleFit.ellipseDistance(px[i], py[i], fitCx, fitCy, fitA, fitB)
                alive[i] = abs(d) <= t
            }
        }
        return true
    }

    /** Un ajuste con los puntos vivos: deja el modelo en [fitCx], [fitCy], [fitA], [fitB] y [fitTrusted]. */
    private fun fitOnce(n: Int, minPoints: Int): Boolean {
        var m = 0
        for (i in 0 until n) if (alive[i]) m++
        if (m < minPoints) return false
        if (!CircleFit.kasa(px, py, alive, n, circle)) return false
        val kx = circle[0]
        val ky = circle[1]
        val kr = circle[2]
        if (!kx.isFinite() || !ky.isFinite() || !(kr > 1.0)) return false
        fitCx = kx
        fitCy = ky
        fitA = kr
        fitB = kr
        fitTrusted = false
        if (sectorCoverage(n, kx, ky) >= cfg.ellipseMinCoverage && CircleFit.ellipseFit(px, py, alive, n, kx, ky, ELLIPSE_CENTER_ITERATIONS, ellipse)) {
            val ea = ellipse[2]
            val eb = ellipse[3]
            val ratio = ea / eb
            if (ea.isFinite() && eb.isFinite() && ratio >= cfg.ellipseMinRatio && ratio <= 1.0 / cfg.ellipseMinRatio &&
                abs(ellipse[0] - kx) < 0.25 * kr && abs(ellipse[1] - ky) < 0.25 * kr
            ) {
                fitCx = ellipse[0]
                fitCy = ellipse[1]
                fitA = ea
                fitB = eb
                fitTrusted = true
            }
        }
        return true
    }

    /** Mediana de los primeros [n] valores de [values] (ordena una copia en [scratch], por inserción: n ≤ 128). */
    private fun median(values: DoubleArray, scratch: DoubleArray, n: Int): Double {
        if (n <= 0) return 0.0
        for (i in 0 until n) {
            val v = values[i]
            var j = i - 1
            while (j >= 0 && scratch[j] > v) {
                scratch[j + 1] = scratch[j]
                j--
            }
            scratch[j + 1] = v
        }
        val mid = n / 2
        return if (n % 2 == 1) scratch[mid] else 0.5 * (scratch[mid - 1] + scratch[mid])
    }

    /** Fracción de los sectores angulares (respecto de ([cx], [cy])) con algún punto vivo. */
    private fun sectorCoverage(n: Int, cx: Double, cy: Double): Double {
        val sectors = cfg.sectors
        val twoPi = 2.0 * PI
        var mask = 0L
        for (i in 0 until n) {
            if (!alive[i]) continue
            var sector = ((atan2(py[i] - cy, px[i] - cx) + PI) / twoPi * sectors).toInt()
            if (sector >= sectors) sector = sectors - 1
            if (sector < 0) sector = 0
            mask = mask or (1L shl sector)
        }
        return java.lang.Long.bitCount(mask).toDouble() / sectors
    }

    private companion object {
        const val MIN_EDGES = 30
        const val MIN_FIT_POINTS = 12
        const val MAX_SCAN = 128
        const val HIST_MAX_BINS = 1024
        const val MAX_RADIUS_PEAKS = 24
        const val RADIUS_BIN = 0.5
        const val ELLIPSE_CENTER_ITERATIONS = 3
        const val RECENTRE_ITERATIONS = 2
        const val MIN_RECENTRE_EDGES = 12
        const val MAX_RECENTRE_SHIFT = 0.35

        /** Un a/b previsto a menos de esto de 1 se trata como círculo; por debajo de [MIN_MODEL_RATIO] no se estira. */
        const val MODEL_RATIO_EPS = 0.02
        const val MIN_MODEL_RATIO = 0.3
    }
}
