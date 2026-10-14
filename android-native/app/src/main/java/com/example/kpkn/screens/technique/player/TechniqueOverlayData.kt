package com.example.kpkn.screens.technique.player

import androidx.compose.runtime.Immutable
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.kinematics.NearSideSelector
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.profiles.BarPointRule
import com.example.kpkn.domain.biomechanics.profiles.PathRule
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Los puntos del esqueleto que guarda la capa por fotograma (17 de los 27 de la traza), en este orden. Los de la cara se reducen
 * a la nariz; las manos, a las muñecas (el centro de la mano solo sirve para el punto de barra).
 */
internal object OverlayJoint {
    const val NOSE = 0
    const val LEFT_SHOULDER = 1
    const val RIGHT_SHOULDER = 2
    const val LEFT_ELBOW = 3
    const val RIGHT_ELBOW = 4
    const val LEFT_WRIST = 5
    const val RIGHT_WRIST = 6
    const val LEFT_HIP = 7
    const val RIGHT_HIP = 8
    const val LEFT_KNEE = 9
    const val RIGHT_KNEE = 10
    const val LEFT_ANKLE = 11
    const val RIGHT_ANKLE = 12
    const val LEFT_HEEL = 13
    const val RIGHT_HEEL = 14
    const val LEFT_FOOT = 15
    const val RIGHT_FOOT = 16

    /** Cuántos puntos hay por fotograma. */
    const val COUNT = 17

    /** El punto de la traza que corresponde a cada posición. */
    val LANDMARKS: Array<PoseLandmark> = arrayOf(
        PoseLandmark.NOSE,
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX,
    )
}

/** De dónde sale el punto de barra de la trayectoria: el propio disco o el sustituto de pose del perfil (muñecas, hombro o cadera). */
enum class TechniqueTrailSource { PLATE, POSE }

/**
 * Una repetición en la línea de tiempo: [startUs] es la salida del reposo, [turnUs] el centro del giro y [endUs] la llegada de
 * vuelta, todo en µs sobre la base de tiempos del vídeo. [index] es el de `RepReport.index`; el número que ve el usuario es la
 * posición en la lista + 1.
 */
@Immutable
data class TechniqueRepMark(
    val index: Int,
    val startUs: Long,
    val turnUs: Long,
    val endUs: Long,
    val kind: RepKind,
    val partialKind: PartialKind? = null,
    /** `"left"` o `"right"` en el conteo por lado; null si cuentan los dos o no se sabe. */
    val side: String? = null,
) {
    /** `true` si dura lo bastante para repetirla en bucle (una repetición de menos de 0,3 s es ruido). */
    val isLoopable: Boolean get() = endUs - startUs >= MIN_LOOP_US

    companion object {
        const val MIN_LOOP_US = 300_000L
    }
}

/**
 * El punto de barra de un vídeo, fotograma a fotograma: [x] e [y] normalizadas (0..1) sobre el fotograma girado, ya suavizadas, y
 * [speed] relativa (0..1) a SU repetición. [repFrom] y [repTo] son, por repetición (o uno solo si no las hay), el rango de
 * índices `[desde, hasta)` de sus puntos.
 */
internal class TechniqueTrail(
    val source: TechniqueTrailSource,
    val timesUs: LongArray,
    val x: FloatArray,
    val y: FloatArray,
    val speed: FloatArray,
    val repFrom: IntArray,
    val repTo: IntArray,
)

/**
 * Todo lo que la capa del reproductor necesita para pintar, preparado una vez y listo para leerse en la fase de dibujo sin
 * asignaciones (VT7a): arrays planos, nada de mapas ni de objetos por fotograma.
 *
 * - **Esqueleto:** por fotograma de la traza de pose, [OverlayJoint.COUNT] pares (x, y) normalizados sobre el fotograma girado,
 *   que es el mismo espacio que el vídeo en pantalla. Un punto con visibilidad baja o sin dato queda en NaN y no se pinta.
 * - **Trayectoria:** el punto de barra de la regla del perfil (`BarPointRule`): el disco si el [BarTrack] lo cubre (como en el
 *   motor, con ≥ 85 % de muestras de detector) y, si no, el sustituto de pose (hombro, mano o cadera del lado cercano).
 *   Cada punto lleva su velocidad relativa (0..1) dentro de su repetición.
 * - **Guía:** [guideX], la x del medio pie, solo si el perfil mide verticalidad (`PathRule.MIDFOOT_VERTICAL`) y el informe tiene la
 *   trayectoria disponible (los pies estaban quietos).
 * - **Marcas:** [reps], las repeticiones del informe en orden.
 * - **Paso a paso:** los PTS de los fotogramas de la traza ([stepPtsUs]). Con `poseStride = 2` solo están los analizados.
 *
 * Una instancia vacía ([EMPTY]) es válida: no pinta nada y el paso a paso cae a un paso nominal.
 */
@Immutable
class TechniqueOverlayData internal constructor(
    /** Proporción ancho/alto del vídeo girado tal como se ve; 0 si no se conoce. */
    val videoAspect: Float,
    /** PTS (µs) de cada fotograma de pose, estrictamente creciente. */
    internal val frameTimesUs: LongArray,
    /** Puntos del esqueleto: `frameTimesUs.size × OverlayJoint.COUNT × 2` floats (x, y); NaN donde no hay dato. */
    internal val joints: FloatArray,
    /** Lado cercano a la cámara (se pinta con más fuerza); null si no se sabe. */
    internal val nearSide: Side?,
    /** PTS (µs) de los fotogramas a los que salta el paso a paso. */
    internal val stepPtsUs: LongArray,
    internal val trail: TechniqueTrail?,
    /** x normalizada del medio pie para la guía vertical; NaN si el perfil no la pide o no hay dato. */
    val guideX: Float,
    val reps: List<TechniqueRepMark>,
    /** Hueco máximo entre dos fotogramas vecinos para seguir dibujando entre ellos (µs). */
    internal val maxGapUs: Long,
) {
    /** `true` si hay esqueleto que pintar. */
    val hasSkeleton: Boolean get() = frameTimesUs.isNotEmpty()

    /** `true` si hay trayectoria que pintar. */
    val hasTrail: Boolean get() = trail != null

    /** De dónde sale la trayectoria; null si no hay. */
    val trailSource: TechniqueTrailSource? get() = trail?.source

    /** `true` si hay guía vertical. */
    val hasGuide: Boolean get() = !guideX.isNaN()

    /** PTS (µs) del último fotograma de pose, o 0 si no hay ninguno. */
    val lastFrameUs: Long get() = if (frameTimesUs.isEmpty()) 0L else frameTimesUs[frameTimesUs.size - 1]

    /**
     * Índice del fotograma de pose que corresponde a [positionUs]: el último cuyo PTS no pasa de la posición. −1 si la posición es
     * anterior al primero o si el fotograma queda a más de [maxGapUs] (sin dato reciente no se pinta nada, ni el último conocido).
     */
    fun frameIndexAt(positionUs: Long): Int {
        val i = floorIndex(frameTimesUs, positionUs)
        if (i < 0) return -1
        return if (positionUs - frameTimesUs[i] > maxGapUs) -1 else i
    }

    /** Posición en [reps] de la repetición que contiene [positionUs] (de su salida a su llegada), o −1 si cae fuera de todas. */
    fun repIndexAt(positionUs: Long): Int {
        for (i in reps.indices) {
            val r = reps[i]
            if (positionUs < r.startUs) return -1
            if (positionUs <= r.endUs) return i
        }
        return -1
    }

    /** La repetición que contiene [positionUs] o, si cae entre dos, la siguiente; −1 si ya pasaron todas o no hay ninguna. */
    fun repIndexNear(positionUs: Long): Int {
        for (i in reps.indices) if (positionUs <= reps[i].endUs) return i
        return -1
    }

    /** Índice en [stepPtsUs] del fotograma que se ve en [positionUs]: el último cuyo PTS no pasa de la posición; −1 si no hay. */
    fun stepIndexAt(positionUs: Long): Int = floorIndex(stepPtsUs, positionUs)

    /** Posición de búsqueda, en ms, de un fotograma del paso a paso: `floor(PTS / 1000)`, nunca hacia arriba. */
    fun stepSeekMs(index: Int): Long = Math.floorDiv(stepPtsUs[index], 1000L)

    /**
     * Repetición cuya trayectoria se pinta en [positionUs]: la última que ya salió (se queda a la vista mientras se descansa hasta
     * que sale la siguiente). Con la serie sin repeticiones, el único tramo (0). −1 si aún no salió ninguna o no hay trayectoria.
     */
    internal fun trailSegmentAt(positionUs: Long): Int {
        val t = trail ?: return -1
        if (reps.isEmpty()) return if (t.repFrom.isNotEmpty()) 0 else -1
        var found = -1
        for (i in reps.indices) {
            if (positionUs + PTS_EPS_US < reps[i].startUs) break
            found = i
        }
        return found
    }

    /** Índice del punto de la trayectoria que se ve en [positionUs] (el último con tiempo ≤ posición), o −1 si no hay ninguno reciente. */
    internal fun trailIndexAt(positionUs: Long): Int {
        val t = trail ?: return -1
        val i = floorIndex(t.timesUs, positionUs)
        if (i < 0) return -1
        return if (positionUs - t.timesUs[i] > maxGapUs) -1 else i
    }

    companion object {
        /** Datos vacíos: no pinta nada. */
        val EMPTY: TechniqueOverlayData = TechniqueOverlayData(
            videoAspect = 0f,
            frameTimesUs = LongArray(0),
            joints = FloatArray(0),
            nearSide = null,
            stepPtsUs = LongArray(0),
            trail = null,
            guideX = Float.NaN,
            reps = emptyList(),
            maxGapUs = DEFAULT_MAX_GAP_US,
        )

        /** Visibilidad mínima de un punto del esqueleto para pintarlo. */
        const val MIN_VISIBILITY = 0.25f

        /** Tolerancia al comparar el PTS del reproductor con el de la traza (µs): son el mismo reloj, a veces con 1 µs de redondeo. */
        const val PTS_EPS_US = 500L

        private const val DEFAULT_MAX_GAP_US = 150_000L

        /** Fracción mínima de muestras del disco que vienen de un detector para usarlo como punto de barra (la del motor). */
        private const val PLATE_MIN_COVERAGE = 0.85

        /** Visibilidad mínima de un punto para el centro de la mano y para la guía de medio pie. */
        private const val HAND_VISIBILITY = 0.5f
        private const val FOOT_VISIBILITY = 0.6f

        private const val MIN_FOOT_SAMPLES = 10

        /**
         * Prepara la capa de un vídeo. Puro: se llama fuera del hilo principal. [videoWidth] y [videoHeight] son los del vídeo ya
         * girado, como se ve en pantalla (0 si no se conocen: entonces manda el tamaño de la traza).
         *
         * Todo es opcional: sin traza no hay esqueleto, sin informe no hay marcas, guía ni sustituto de pose, y sin disco ni
         * informe no hay trayectoria. Nunca se inventa un dato: lo que falta no se pinta.
         */
        fun from(
            track: PoseTrackV2?,
            bar: BarTrack?,
            report: SetTechniqueReport?,
            videoWidth: Int,
            videoHeight: Int,
        ): TechniqueOverlayData {
            val aspect = aspectOf(videoWidth, videoHeight, track, bar)
            val marks = marksOf(report)
            val profile = report?.let { AnalysisProfiles.of(it.profileId) }

            // Fotogramas con el tiempo estrictamente creciente (la traza ya lo garantiza; aquí se protege el dibujo).
            val frames = ArrayList<PoseFrameV2>(track?.frames?.size ?: 0)
            var lastT = Long.MIN_VALUE
            for (f in track?.frames.orEmpty()) {
                if (f.tUs <= lastT) continue
                lastT = f.tUs
                frames.add(f)
            }
            val n = frames.size
            val times = LongArray(n) { frames[it].tUs }
            val joints = FloatArray(n * OverlayJoint.COUNT * 2) { Float.NaN }
            val lms = OverlayJoint.LANDMARKS
            for (k in 0 until n) {
                val f = frames[k]
                if (!f.hasPose) continue
                val o = k * OverlayJoint.COUNT * 2
                for (j in 0 until OverlayJoint.COUNT) {
                    val lm = lms[j]
                    val x = f.x(lm)
                    val y = f.y(lm)
                    val v = f.vis(lm)
                    if (x.isNaN() || y.isNaN() || (!v.isNaN() && v < MIN_VISIBILITY)) continue
                    joints[o + 2 * j] = x
                    joints[o + 2 * j + 1] = y
                }
            }

            val maxGap = maxGapOf(times)
            val near = nearSideOf(track, report)
            val trail = if (profile != null || bar != null) buildTrail(frames, bar, profile, near, aspect, marks, times, joints, maxGap) else null
            val guide = guideXOf(profile, report, near, frames, marks)

            return TechniqueOverlayData(
                videoAspect = aspect,
                frameTimesUs = times,
                joints = joints,
                nearSide = near,
                stepPtsUs = times.copyOf(),
                trail = trail,
                guideX = guide,
                reps = marks,
                maxGapUs = maxGap,
            )
        }

        // ------------------------------------------------------------------------------------------------------------
        // Marcas, lado cercano y proporción
        // ------------------------------------------------------------------------------------------------------------

        private fun marksOf(report: SetTechniqueReport?): List<TechniqueRepMark> {
            if (report == null) return emptyList()
            return report.reps
                .map { r ->
                    TechniqueRepMark(
                        index = r.index,
                        startUs = r.phases.departUs,
                        turnUs = (r.phases.turnStartUs + r.phases.turnEndUs) / 2,
                        endUs = r.phases.arriveUs,
                        kind = r.kind,
                        partialKind = r.partialKind,
                        side = r.side,
                    )
                }
                .filter { it.endUs > it.startUs }
                .sortedBy { it.startUs }
        }

        private fun nearSideOf(track: PoseTrackV2?, report: SetTechniqueReport?): Side? = when (report?.nearSide) {
            "left" -> Side.LEFT
            "right" -> Side.RIGHT
            else -> if (track != null && track.frames.isNotEmpty()) NearSideSelector.select(track, track.frames.indices)?.near else null
        }

        private fun aspectOf(videoWidth: Int, videoHeight: Int, track: PoseTrackV2?, bar: BarTrack?): Float = when {
            videoWidth > 0 && videoHeight > 0 -> videoWidth.toFloat() / videoHeight
            track != null && track.frameWidth > 0 && track.frameHeight > 0 -> track.frameWidth.toFloat() / track.frameHeight
            bar != null && bar.frameWidth > 0 && bar.frameHeight > 0 -> bar.frameWidth.toFloat() / bar.frameHeight
            else -> 0f
        }

        /** Hueco máximo entre fotogramas: 2,5 veces la mediana de sus separaciones, y nunca menos de 150 ms. */
        private fun maxGapOf(times: LongArray): Long {
            if (times.size < 2) return DEFAULT_MAX_GAP_US
            val gaps = LongArray(times.size - 1) { times[it + 1] - times[it] }
            gaps.sort()
            val median = gaps[gaps.size / 2]
            return max(DEFAULT_MAX_GAP_US, (median * 2.5).toLong())
        }

        // ------------------------------------------------------------------------------------------------------------
        // Trayectoria
        // ------------------------------------------------------------------------------------------------------------

        private fun buildTrail(
            frames: List<PoseFrameV2>,
            bar: BarTrack?,
            profile: AnalysisProfile?,
            near: Side?,
            aspect: Float,
            marks: List<TechniqueRepMark>,
            poseTimes: LongArray,
            joints: FloatArray,
            maxGap: Long,
        ): TechniqueTrail? {
            val rule = profile?.barPoint
            if (rule == BarPointRule.NONE) return null
            val plateRule = rule == null || rule == BarPointRule.PLATE_THEN_SHOULDER ||
                rule == BarPointRule.PLATE_THEN_HAND || rule == BarPointRule.PLATE_THEN_HIP

            val times: LongArray
            val rawX: FloatArray
            val rawY: FloatArray
            val source: TechniqueTrailSource
            if (bar != null && plateRule && plateCovers(bar)) {
                val samples = bar.samples.sortedBy { it.tUs }
                val w = bar.frameWidth.toFloat()
                val h = bar.frameHeight.toFloat()
                times = LongArray(samples.size) { samples[it].tUs }
                rawX = FloatArray(samples.size) { samples[it].cx / w }
                rawY = FloatArray(samples.size) { samples[it].cy / h }
                source = TechniqueTrailSource.PLATE
            } else {
                if (profile == null || rule == null || near == null || poseTimes.isEmpty()) return null
                times = poseTimes
                rawX = FloatArray(poseTimes.size) { Float.NaN }
                rawY = FloatArray(poseTimes.size) { Float.NaN }
                val xy = FloatArray(2)
                for (i in poseTimes.indices) {
                    if (substitutePoint(profile, rule, near, frames[i], i, joints, xy)) {
                        rawX[i] = xy[0]
                        rawY[i] = xy[1]
                    }
                }
                source = TechniqueTrailSource.POSE
            }
            if (times.size < 2) return null

            val half = smoothingHalfWindow(times)
            val x = smooth(rawX, half)
            val y = smooth(rawY, half)
            val speed = FloatArray(times.size) { Float.NaN }
            val sx = if (aspect > 0f) aspect else 1f
            for (i in times.indices) {
                if (x[i].isNaN()) continue
                val a = if (i > 0 && !x[i - 1].isNaN() && times[i] - times[i - 1] <= maxGap) i - 1 else i
                val b = if (i < times.size - 1 && !x[i + 1].isNaN() && times[i + 1] - times[i] <= maxGap) i + 1 else i
                if (a == b) continue
                val dt = (times[b] - times[a]) / 1_000_000f
                speed[i] = hypot((x[b] - x[a]) * sx, y[b] - y[a]) / dt
            }

            // Tramos por repetición (o uno solo si no hay) y velocidad relativa dentro de cada uno.
            val segments = max(1, marks.size)
            val from = IntArray(segments)
            val to = IntArray(segments)
            if (marks.isEmpty()) {
                from[0] = 0
                to[0] = times.size
            } else {
                for (k in marks.indices) {
                    from[k] = lowerBound(times, marks[k].startUs)
                    to[k] = upperBound(times, marks[k].endUs)
                }
            }
            val relative = FloatArray(times.size) { Float.NaN }
            for (k in 0 until segments) normalize(speed, from[k], to[k], relative)
            return TechniqueTrail(source, times, x, y, relative, from, to)
        }

        /** El disco sirve de punto de barra si al menos el 85 % de sus muestras vienen de un detector (HOUGH o NCC). */
        private fun plateCovers(bar: BarTrack): Boolean {
            if (bar.samples.size < 2 || bar.frameWidth <= 0 || bar.frameHeight <= 0) return false
            var strong = 0
            for (s in bar.samples) if (s.source == BarSampleSource.HOUGH || s.source == BarSampleSource.NCC) strong++
            return strong >= PLATE_MIN_COVERAGE * bar.samples.size
        }

        /**
         * El sustituto de pose del punto de barra en el fotograma [i] (los mismos puntos que usa el motor): hombro, centro de la mano
         * (muñeca con los dedos si se ven) o cadera del lado cercano, o el medio de los hombros. Lo escribe en [out]; `false` si falta.
         */
        private fun substitutePoint(
            profile: AnalysisProfile,
            rule: BarPointRule,
            near: Side,
            frame: PoseFrameV2,
            i: Int,
            joints: FloatArray,
            out: FloatArray,
        ): Boolean {
            val left = near == Side.LEFT
            val shoulder = if (left) OverlayJoint.LEFT_SHOULDER else OverlayJoint.RIGHT_SHOULDER
            val elbow = if (left) OverlayJoint.LEFT_ELBOW else OverlayJoint.RIGHT_ELBOW
            val wrist = if (left) OverlayJoint.LEFT_WRIST else OverlayJoint.RIGHT_WRIST
            val hip = if (left) OverlayJoint.LEFT_HIP else OverlayJoint.RIGHT_HIP
            return when (rule) {
                BarPointRule.PLATE_THEN_SHOULDER -> joint(joints, i, shoulder, out)
                BarPointRule.PLATE_THEN_HIP -> joint(joints, i, hip, out)
                BarPointRule.SHOULDER_MID -> {
                    val a = joint(joints, i, OverlayJoint.LEFT_SHOULDER, out)
                    val ax = out[0]
                    val ay = out[1]
                    val b = joint(joints, i, OverlayJoint.RIGHT_SHOULDER, out)
                    when {
                        a && b -> {
                            out[0] = 0.5f * (ax + out[0])
                            out[1] = 0.5f * (ay + out[1])
                            true
                        }
                        a -> {
                            out[0] = ax
                            out[1] = ay
                            true
                        }
                        else -> b
                    }
                }
                BarPointRule.HAND -> handCenter(frame, near, out) || joint(joints, i, wrist, out)
                BarPointRule.PLATE_THEN_HAND ->
                    if (profile.id == AnalysisProfileId.DEADLIFT) {
                        // Peso muerto: la mano solo si la muñeca se ve bien (el disco la tapa); si no, el hombro.
                        val wristLm = if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
                        if (frame.vis(wristLm) >= HAND_VISIBILITY && handCenter(frame, near, out)) true else joint(joints, i, shoulder, out)
                    } else {
                        handCenter(frame, near, out) || joint(joints, i, elbow, out)
                    }
                BarPointRule.NONE -> false
            }
        }

        private fun joint(joints: FloatArray, frame: Int, joint: Int, out: FloatArray): Boolean {
            val o = (frame * OverlayJoint.COUNT + joint) * 2
            val x = joints[o]
            if (x.isNaN()) return false
            out[0] = x
            out[1] = joints[o + 1]
            return true
        }

        /** Centro de la mano ½·muñeca + ¼·(índice + meñique); solo la muñeca si los dedos no se ven. `false` si la muñeca falta. */
        private fun handCenter(f: PoseFrameV2, near: Side, out: FloatArray): Boolean {
            if (!f.hasPose) return false
            val left = near == Side.LEFT
            val wrist = if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
            val index = if (left) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX
            val pinky = if (left) PoseLandmark.LEFT_PINKY else PoseLandmark.RIGHT_PINKY
            if (!(f.vis(wrist) >= HAND_VISIBILITY)) return false
            val wx = f.x(wrist)
            val wy = f.y(wrist)
            if (wx.isNaN() || wy.isNaN()) return false
            val fingers = f.vis(index) >= HAND_VISIBILITY && f.vis(pinky) >= HAND_VISIBILITY
            if (!fingers) {
                out[0] = wx
                out[1] = wy
                return true
            }
            out[0] = 0.5f * wx + 0.25f * (f.x(index) + f.x(pinky))
            out[1] = 0.5f * wy + 0.25f * (f.y(index) + f.y(pinky))
            return true
        }

        /** Mitad de la ventana de la media móvil: ≈ 0,065 s a cada lado (5 fotogramas a 30 fps), y al menos un fotograma. */
        private fun smoothingHalfWindow(times: LongArray): Int {
            val span = times[times.size - 1] - times[0]
            if (span <= 0L) return 1
            val fps = (times.size - 1) * 1_000_000.0 / span
            return max(1, (0.065 * fps).roundToInt())
        }

        /** Media móvil que ignora los NaN y no inventa puntos: donde el centro no tiene dato, sigue sin tenerlo. */
        private fun smooth(v: FloatArray, half: Int): FloatArray {
            val out = FloatArray(v.size) { Float.NaN }
            for (i in v.indices) {
                if (v[i].isNaN()) continue
                var sum = 0f
                var count = 0
                for (k in max(0, i - half)..min(v.size - 1, i + half)) {
                    val q = v[k]
                    if (q.isNaN()) continue
                    sum += q
                    count++
                }
                out[i] = sum / count
            }
            return out
        }

        /**
         * Escribe en [out], de [from] a [to], la velocidad de [speed] relativa a ese tramo: 0 en la más lenta y 1 en la más rápida
         * (percentiles 5 y 95 si hay muestras de sobra, para que un punto suelto no estire la escala). Con una velocidad constante
         * todo es 0,5.
         */
        private fun normalize(speed: FloatArray, from: Int, to: Int, out: FloatArray) {
            val lo = max(0, from)
            val hi = min(speed.size, to)
            if (lo >= hi) return
            val values = FloatArray(hi - lo)
            var count = 0
            for (i in lo until hi) if (!speed[i].isNaN()) values[count++] = speed[i]
            if (count == 0) return
            values.sort(0, count)
            val low: Float
            val high: Float
            if (count < MIN_FOR_PERCENTILES) {
                low = values[0]
                high = values[count - 1]
            } else {
                low = values[((count - 1) * 0.05f).toInt()]
                high = values[Math.ceil((count - 1) * 0.95).toInt()]
            }
            val range = high - low
            for (i in lo until hi) {
                val s = speed[i]
                if (s.isNaN()) continue
                out[i] = if (range > 1e-9f) ((s - low) / range).coerceIn(0f, 1f) else 0.5f
            }
        }

        private const val MIN_FOR_PERCENTILES = 20

        // ------------------------------------------------------------------------------------------------------------
        // Guía vertical
        // ------------------------------------------------------------------------------------------------------------

        /**
         * x normalizada del medio pie del lado cercano (mediana de ½·(talón + punta) con los dos a ≥ 0,6 de visibilidad, dentro de la
         * ventana de las repeticiones). NaN si el perfil no mide verticalidad, el informe no tiene la trayectoria (los pies se
         * movían) o hay menos de 10 muestras.
         */
        private fun guideXOf(
            profile: AnalysisProfile?,
            report: SetTechniqueReport?,
            near: Side?,
            frames: List<PoseFrameV2>,
            marks: List<TechniqueRepMark>,
        ): Float {
            if (profile == null || report == null || near == null) return Float.NaN
            if (profile.pathRule != PathRule.MIDFOOT_VERTICAL || MetricKey.PATH !in report.available) return Float.NaN
            val left = near == Side.LEFT
            val heel = if (left) PoseLandmark.LEFT_HEEL else PoseLandmark.RIGHT_HEEL
            val toe = if (left) PoseLandmark.LEFT_FOOT_INDEX else PoseLandmark.RIGHT_FOOT_INDEX
            val from = if (marks.isEmpty()) Long.MIN_VALUE else marks.first().startUs
            val to = if (marks.isEmpty()) Long.MAX_VALUE else marks.last().endUs
            val xs = FloatArray(frames.size)
            var count = 0
            for (f in frames) {
                if (f.tUs < from || f.tUs > to) continue
                if (!(f.vis(heel) >= FOOT_VISIBILITY && f.vis(toe) >= FOOT_VISIBILITY)) continue
                val x = 0.5f * (f.x(heel) + f.x(toe))
                if (x.isNaN()) continue
                xs[count++] = x
            }
            if (count < MIN_FOOT_SAMPLES) return Float.NaN
            xs.sort(0, count)
            return if (count % 2 == 1) xs[count / 2] else 0.5f * (xs[count / 2 - 1] + xs[count / 2])
        }

        // ------------------------------------------------------------------------------------------------------------
        // Búsquedas en arrays ordenados
        // ------------------------------------------------------------------------------------------------------------

        /** Índice del último elemento con `valor ≤ target + PTS_EPS_US`, o −1 si todos son mayores. */
        internal fun floorIndex(values: LongArray, target: Long): Int {
            var lo = 0
            var hi = values.size - 1
            var answer = -1
            val limit = target + PTS_EPS_US
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (values[mid] <= limit) {
                    answer = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return answer
        }

        /** Primer índice con `valor ≥ target` (o `values.size`). */
        private fun lowerBound(values: LongArray, target: Long): Int {
            var lo = 0
            var hi = values.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (values[mid] < target) lo = mid + 1 else hi = mid
            }
            return lo
        }

        /** Primer índice con `valor > target` (o `values.size`). */
        private fun upperBound(values: LongArray, target: Long): Int {
            var lo = 0
            var hi = values.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (values[mid] <= target) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
