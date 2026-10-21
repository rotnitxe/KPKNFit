package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import com.example.kpkn.domain.biomechanics.testing.Anthro
import com.example.kpkn.domain.biomechanics.testing.CameraModel
import com.example.kpkn.domain.biomechanics.testing.MotionState
import com.example.kpkn.domain.biomechanics.testing.Piece
import com.example.kpkn.domain.biomechanics.testing.SynthBody
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthMovements
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTimeline
import com.example.kpkn.domain.biomechanics.testing.V3
import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Generador de repeticiones PARCIALES para el banco sintético (herramienta de medición, no toca el motor).
 *
 * `SyntheticLifter` solo sabe hacer repeticiones con amplitud global y parciales de una forma fija (55 % desde el reposo o
 * 45 %–100 % alrededor del giro). Aquí cada repetición lleva su PROPIO techo (`hi`) y su PROPIO valle posterior (`lo`),
 * medidos en FRACCIÓN DE SEÑAL (0 = reposo, 1 = el giro de una repetición completa), no en el progreso b del generador: la
 * señal de conteo no es lineal en b (la altura de la cadera en la sentadilla baja mucho menos que el ángulo de rodilla), y
 * el motor mide el recorrido en la señal. [PartialsCalibration] mide esa relación sobre la traza limpia y la invierte.
 *
 * El cuerpo, la cámara y el ruido son los mismos del generador original ([CameraModel], [SynthMovements], [SynthBody]); lo que
 * se copia de `SyntheticLifter.generate` es solo el bucle de fotogramas, porque aquel compila el guion por dentro y no deja
 * inyectar una línea de tiempo propia (y no se toca por si lo edita otro agente).
 */

/** Los perfiles del banco: movimiento del generador + perfil del motor que lo mide. */
internal enum class PartialsProfile(val label: String, val movement: SynthMovement, val profileId: AnalysisProfileId) {
    SENTADILLA("sentadilla", SynthMovement.SQUAT, AnalysisProfileId.SQUAT),
    BANCA("banca", SynthMovement.BENCH, AnalysisProfileId.BENCH),
    CURL("curl", SynthMovement.CURL, AnalysisProfileId.CURL),
    JALON("jalon", SynthMovement.LAT_PULLDOWN, AnalysisProfileId.PULL_VERTICAL),
    PRESS_MILITAR("press_militar", SynthMovement.OHP, AnalysisProfileId.OHP),
    RUMANO("rumano", SynthMovement.RDL, AnalysisProfileId.HINGE),
    MAQUINA("maquina_extension_pierna", SynthMovement.LEG_EXTENSION, AnalysisProfileId.LEG_EXTENSION),
    ;

    val startsEccentric: Boolean get() = SynthMovements.def(movement).startsEccentric
    val profile get() = AnalysisProfiles.of(profileId)
}

/** La vista de la cámara: lateral (ψ = 90°), frontal (0°) y oblicua (45°). */
internal enum class PartialsView(val label: String, val yawDeg: Double, val kind: ViewKind) {
    LATERAL("lateral", 90.0, ViewKind.SIDE),
    FRONTAL("frontal", 0.0, ViewKind.FRONT),
    OBLICUA("oblicua", 45.0, ViewKind.OBLIQUE),
    ;

    val camera: SynthCamera get() = SynthCamera(yawDeg = yawDeg)
}

/** Clase de la repetición en la verdad (las del motor son `RepKind`). */
internal enum class TruthKind { FULL, PARTIAL, FAILED }

/**
 * Zona del recorrido de una repetición parcial: [TURN_SIDE] llega cerca del giro (≥ 85 % de la señal) y no vuelve al reposo
 * (en el motor, `PartialKind.FAR_END`); [REST_SIDE] sale del reposo y no alcanza el giro (`REST_END`). El motor aún no dice la
 * zona: se anota aquí para el paquete siguiente.
 */
internal enum class PartialsZone { FULL_RANGE, TURN_SIDE, REST_SIDE, FAILED }

/** Un paso del guion del banco. */
internal sealed interface PItem {
    /** Vuelve al reposo (1 s si no estaba) y se queda [s] segundos. */
    data class Idle(val s: Double) : PItem

    /** Se queda [s] segundos donde esté, SIN volver al reposo (el vídeo acaba con el atleta parado en un parcial). */
    data class Tail(val s: Double) : PItem

    /** Vuelve al reposo en [s] segundos, sin repetición. */
    data class Recover(val s: Double = 1.0) : PItem

    data class Rep(val spec: PRepSpec) : PItem
}

/**
 * Una repetición. [hi] es el techo (fracción de señal del giro) y [lo] el valle al que baja DESPUÉS. La repetición sale del
 * punto en que acabó la anterior. Tiempos: [outS] el tramo que sale del reposo hacia el giro, [turnHoldS] la pausa del giro,
 * [retS] el que vuelve y [loHoldS] la pausa del valle. [failed]: la fase concéntrica llega al 50 % de su recorrido, se queda
 * quieta 0,8 s y vuelve al punto de partida de esa fase (como el generador original).
 */
internal data class PRepSpec(
    val hi: Double,
    val lo: Double,
    val outS: Double,
    val turnHoldS: Double,
    val retS: Double,
    val loHoldS: Double,
    val failed: Boolean = false,
)

/**
 * Verdad de una repetición. [fraction] = min([upFraction], [downFraction]), exactamente la `a` del segmentador pero con la
 * señal limpia y la amplitud completa de referencia (1,0). [angleFraction] es el mismo recorrido medido en el ángulo principal
 * del movimiento (para comparar con una cifra de «porcentaje de recorrido» que vea un entrenador).
 */
internal data class PTruthRep(
    val index: Int,
    val kind: TruthKind,
    val zone: PartialsZone,
    /** `ESTIRAMIENTO` (el músculo trabaja alargado), `ACORTADA` o `COMPLETA`; depende del arranque del movimiento. */
    val muscleZone: String,
    val hi: Double,
    val valleyBefore: Double,
    val valleyAfter: Double,
    val upFraction: Double,
    val downFraction: Double,
    val fraction: Double,
    val angleFraction: Double,
    val departUs: Long,
    val turnStartUs: Long,
    val turnEndUs: Long,
    val arriveUs: Long,
    /** Se ve entera (o lo bastante) en el vídeo recortado; si no, queda fuera de la verdad contada. */
    val visible: Boolean = true,
) {
    val turnCenterUs: Long get() = (turnStartUs + turnEndUs) / 2
}

/** La línea de tiempo compilada y la verdad de sus repeticiones (todas, también las que un recorte deje fuera). */
internal class PartialsTimeline(val timeline: SynthTimeline, val reps: List<PTruthRep>, val totalSeconds: Double)

/** Relación entre el progreso b del generador y la fracción de la señal de conteo, para un movimiento y una vista. */
internal class PartialsCalibration(
    val calibrated: Boolean,
    private val bGrid: DoubleArray,
    private val gGrid: DoubleArray,
    /** Rango de la señal r entre el reposo y el giro (en su unidad), si se pudo medir. */
    val span: Double,
    val restLevel: Double,
) {
    /** El progreso b que da la fracción de señal [fraction] (0..1). */
    fun bOf(fraction: Double): Double {
        val f = fraction.coerceIn(0.0, 1.0)
        if (!calibrated) return f
        var lo = 0
        var hi = gGrid.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (gGrid[mid] < f) lo = mid + 1 else hi = mid
        }
        if (lo == 0) return bGrid[0]
        val g0 = gGrid[lo - 1]
        val g1 = gGrid[lo]
        val w = if (g1 - g0 < 1e-12) 1.0 else (f - g0) / (g1 - g0)
        return bGrid[lo - 1] + w * (bGrid[lo] - bGrid[lo - 1])
    }

    /** La fracción de señal que da el progreso [b]. */
    fun fractionOf(b: Double): Double {
        val x = b.coerceIn(0.0, 1.0)
        if (!calibrated) return x
        val k = (x * (bGrid.size - 1)).coerceIn(0.0, (bGrid.size - 1).toDouble())
        val i = min(floor(k).toInt(), bGrid.size - 2)
        val w = k - i
        return gGrid[i] + w * (gGrid[i + 1] - gGrid[i])
    }

    companion object {
        private val cache = HashMap<String, PartialsCalibration>()
        private const val GRID = 201

        /** Identidad (sin calibrar): para las pruebas que no necesitan la señal. */
        val IDENTITY = PartialsCalibration(false, DoubleArray(0), DoubleArray(0), Double.NaN, Double.NaN)

        @Synchronized
        fun of(profile: PartialsProfile, view: PartialsView): PartialsCalibration =
            cache.getOrPut(profile.name + "/" + view.name) { measure(profile, view) }

        /**
         * Una repetición lentísima de 0 a 1 de b sobre la traza limpia: la señal r(b) del motor da la fracción
         * g(b) = (r(b) − r(0)) / (r(1) − r(0)); se toma su envolvente creciente y se remuestrea en una rejilla de b.
         */
        private fun measure(profile: PartialsProfile, view: PartialsView): PartialsCalibration {
            val items = listOf(
                PItem.Idle(2.0),
                PItem.Rep(PRepSpec(1.0, 0.0, 12.0, 1.5, 6.0, 0.5)),
                PItem.Idle(2.0),
            )
            val tl = PartialsTimelineBuilder.build(items, profile.startsEccentric, IDENTITY, profile.movement)
            val config = SynthConfig(
                movement = profile.movement, camera = view.camera, fps = 30.0, script = emptyList<SynthSegment>(),
                noise = SynthNoise.NONE, seed = 1L,
            )
            val result = PartialsLifter.generate(config, tl.timeline)
            val prepared = TrackPreparation.prepare(result.cleanTrack)
            val built = CountSeriesBuilder.build(prepared, null, profile.profile)
            val series = (built as? CountSeriesResult.Ok)?.series ?: return IDENTITY
            // Pares (b, r) en el tramo de ida (b creciente).
            val bs = ArrayList<Double>()
            val rs = ArrayList<Double>()
            var restSum = 0.0
            var restN = 0
            var topSum = 0.0
            var topN = 0
            for (k in 0 until series.size) {
                val r = series.r[k]
                if (r.isNaN()) continue
                val t = series.t0Us / 1e6 + k / series.fs
                val b = tl.timeline.stateAt(t).bL
                if (b < 1e-6) {
                    restSum += r; restN++
                } else if (b > 1.0 - 1e-6) {
                    topSum += r; topN++
                }
                if (t <= tl.reps[0].turnStartUs / 1e6) {
                    bs.add(b); rs.add(r)
                }
            }
            if (restN < 5 || topN < 5) return IDENTITY
            val rest = restSum / restN
            val top = topSum / topN
            val span = top - rest
            if (abs(span) < 1e-9) return IDENTITY
            // g(b) en una rejilla uniforme de b, envolvente creciente y acotada a 0..1.
            val order = bs.indices.sortedBy { bs[it] }
            val gRaw = DoubleArray(GRID)
            var cursor = 0
            var lastG = 0.0
            for (i in 0 until GRID) {
                val b = i.toDouble() / (GRID - 1)
                // Media de las muestras en una ventana ±0,5 % de b.
                var sum = 0.0
                var n = 0
                var j = cursor
                while (j > 0 && bs[order[j - 1]] >= b - 0.005) j--
                while (j < order.size && bs[order[j]] <= b + 0.005) {
                    sum += (rs[order[j]] - rest) / span
                    n++
                    j++
                }
                cursor = max(0, j - 1)
                val g = if (n > 0) sum / n else lastG
                lastG = g
                gRaw[i] = g
            }
            val gGrid = DoubleArray(GRID)
            var running = 0.0
            for (i in 0 until GRID) {
                running = max(running, gRaw[i].coerceIn(0.0, 1.0))
                gGrid[i] = running
            }
            gGrid[0] = 0.0
            gGrid[GRID - 1] = 1.0
            // Hace estrictamente creciente la rejilla para poder invertirla.
            for (i in 1 until GRID) if (gGrid[i] <= gGrid[i - 1]) gGrid[i] = min(1.0, gGrid[i - 1] + 1e-6)
            gGrid[GRID - 1] = 1.0
            val bGrid = DoubleArray(GRID) { it.toDouble() / (GRID - 1) }
            return PartialsCalibration(true, bGrid, gGrid, span, rest)
        }
    }
}

/** Compila los pasos del guion en una línea de tiempo continua y calcula la verdad de cada repetición. */
internal object PartialsTimelineBuilder {
    private const val STALL_S = 0.8
    private const val FAILED_FRACTION = 0.5
    private const val FULL_AT = 0.70
    private const val PARTIAL_AT = 0.30
    private const val FAR_END = 0.85

    fun build(items: List<PItem>, startsEccentric: Boolean, cal: PartialsCalibration, movement: SynthMovement): PartialsTimeline {
        val pieces = ArrayList<Piece>()
        var t = 0.0
        var b = 0.0

        fun move(duration: Double, to: Double) {
            if (duration <= 1e-9) {
                b = to
                return
            }
            pieces.add(Piece(t, t + duration, b, to, b, to, 0.0, 0.0, 0.0, 0.0))
            t += duration
            b = to
        }

        fun hold(duration: Double) = move(duration, b)
        fun ensureRest() {
            if (abs(b) > 1e-9) move(1.0, 0.0)
        }

        class Raw(
            val spec: PRepSpec, val departS: Double, val turnStartS: Double, val turnEndS: Double, val arriveS: Double,
            val valleyBefore: Double, var valleyAfter: Double,
        )

        val raws = ArrayList<Raw>()
        // Valle con el que acabó la repetición anterior (fracción de señal) o 0 si hubo reposo de por medio.
        var valleyCarry = 0.0
        for ((i, item) in items.withIndex()) {
            when (item) {
                is PItem.Idle -> {
                    ensureRest(); hold(item.s); valleyCarry = 0.0
                }
                is PItem.Recover -> {
                    move(item.s, 0.0); valleyCarry = 0.0
                }
                is PItem.Tail -> hold(item.s)
                is PItem.Rep -> {
                    val s = item.spec
                    val bHi = cal.bOf(s.hi)
                    val bLo = cal.bOf(s.lo)
                    val tDepart = t
                    val tTurnStart: Double
                    val tTurnEnd: Double
                    val tArrive: Double
                    if (!s.failed) {
                        move(s.outS, bHi); tTurnStart = t
                        hold(s.turnHoldS); tTurnEnd = t
                        move(s.retS, bLo); tArrive = t
                        hold(s.loHoldS)
                    } else if (startsEccentric) {
                        // Baja entera; en la subida llega al 50 % de su recorrido, se queda quieta y vuelve abajo.
                        move(s.outS, bHi); tTurnStart = t
                        hold(s.turnHoldS)
                        move(s.retS, cal.bOf(s.hi * (1.0 - FAILED_FRACTION)))
                        hold(STALL_S); tTurnEnd = t
                        move(s.outS, bHi); tArrive = t
                    } else {
                        // La concéntrica llega al 50 %, se queda quieta y baja de nuevo al reposo.
                        move(s.outS, cal.bOf(s.hi * FAILED_FRACTION)); tTurnStart = t
                        hold(STALL_S); tTurnEnd = t
                        move(s.retS, bLo); tArrive = t
                        hold(s.loHoldS)
                    }
                    val before = valleyCarry
                    // El valle que verá el motor después: el de la repetición siguiente si la hay; 0 si hay reposo o recuperación
                    // (el valle intermedio queda en una meseta monótona); el propio si el vídeo acaba parado ahí.
                    val next = items.getOrNull(i + 1)
                    val after = when {
                        s.failed -> if (startsEccentric) s.hi else 0.0
                        next is PItem.Idle || next is PItem.Recover -> 0.0
                        else -> s.lo
                    }
                    valleyCarry = if (s.failed) 0.0 else s.lo
                    raws.add(Raw(s, tDepart, tTurnStart, tTurnEnd, tArrive, before, after))
                }
            }
        }
        val total = t
        val start = MotionState(0.0, 0.0, 0.0, 0.0)
        val timeline = SynthTimeline(pieces, emptyList(), emptyList(), Side.LEFT, total, start)

        val anthro = Anthro(1.75)
        fun angle(bv: Double): Double {
            val post = SynthMovements.posture(movement, MotionState(bv, bv), anthro, Side.LEFT)
            return SynthMovements.primaryAngleDeg(movement, SynthBody.joints(post, anthro), Side.LEFT, anthro)
        }
        val angleSpan = abs(angle(1.0) - angle(0.0)).coerceAtLeast(1e-6)

        val truth = ArrayList<PTruthRep>()
        for (r in raws) {
            val s = r.spec
            val up = s.hi - r.valleyBefore
            val down = s.hi - r.valleyAfter
            val a = if (s.failed) FAILED_FRACTION else min(up, down)
            val kind = when {
                s.failed -> TruthKind.FAILED
                a >= FULL_AT -> TruthKind.FULL
                a >= PARTIAL_AT -> TruthKind.PARTIAL
                else -> null
            } ?: continue // por debajo del 30 % es ruido para el motor: no es una repetición de la verdad
            val zone = when (kind) {
                TruthKind.FULL -> PartialsZone.FULL_RANGE
                TruthKind.FAILED -> PartialsZone.FAILED
                TruthKind.PARTIAL -> if (s.hi >= FAR_END) PartialsZone.TURN_SIDE else PartialsZone.REST_SIDE
            }
            val muscle = when (zone) {
                PartialsZone.TURN_SIDE -> if (startsEccentric) "ESTIRAMIENTO" else "ACORTADA"
                PartialsZone.REST_SIDE -> if (startsEccentric) "ACORTADA" else "ESTIRAMIENTO"
                else -> "COMPLETA"
            }
            val bHi = cal.bOf(s.hi)
            val bFrom = cal.bOf(r.valleyBefore)
            val bTo = cal.bOf(r.valleyAfter)
            val angleFraction = min(abs(angle(bHi) - angle(bFrom)), abs(angle(bHi) - angle(bTo))) / angleSpan
            truth.add(
                PTruthRep(
                    index = truth.size, kind = kind, zone = zone, muscleZone = muscle, hi = s.hi,
                    valleyBefore = r.valleyBefore, valleyAfter = r.valleyAfter, upFraction = up, downFraction = down,
                    fraction = a, angleFraction = angleFraction,
                    departUs = (r.departS * 1e6).roundToLong(), turnStartUs = (r.turnStartS * 1e6).roundToLong(),
                    turnEndUs = (r.turnEndS * 1e6).roundToLong(), arriveUs = (r.arriveS * 1e6).roundToLong(),
                ),
            )
        }
        return PartialsTimeline(timeline, truth, total)
    }
}

/** El bucle de fotogramas de `SyntheticLifter.generate` con una línea de tiempo ya compilada (sin glitches ni segunda persona). */
internal object PartialsLifter {
    private const val NEAR_VISIBILITY = 0.95
    private const val MIN_FACE_VISIBILITY = 0.1
    private const val PRESENCE = 0.99f

    fun generate(config: SynthConfig, timeline: SynthTimeline): SynthResult {
        val anthro = Anthro(config.heightM)
        val cam = CameraModel(config.camera)
        val noise = config.noise
        val fps = config.fps
        val n = floor(timeline.totalSeconds * fps + 1e-9).toInt() + 1
        val aspect = cam.widthPx / cam.heightPx
        val sinPsi = kotlin.math.sin(Math.toRadians(config.camera.yawDeg))

        val rngPts = rng(config.seed, 1)
        val rngNoise = rng(config.seed, 2)
        val rngWorld = rng(config.seed, 3)
        val rngDrop = rng(config.seed, 4)

        val frames = ArrayList<PoseFrameV2>(n)
        val clean = ArrayList<PoseFrameV2>(n)
        val dropped = BooleanArray(n)
        val tmp = DoubleArray(3)
        val keptImage = PoseLandmark.KEPT_IMAGE
        val keptWorld = PoseLandmark.KEPT_WORLD
        val maxJitterS = noise.ptsJitterMs / 1000.0

        for (k in 0 until n) {
            val jitter = if (k == 0 || maxJitterS <= 0.0) 0.0 else (rngPts.nextDouble() + rngPts.nextDouble() - 1.0) * maxJitterS
            val t = max(0.0, k / fps + jitter)
            val tUs = (t * 1e6).roundToLong()

            val st = timeline.stateAt(t)
            val post = SynthMovements.posture(config.movement, st, anthro, timeline.frontSide)
            val local = SynthBody.joints(post, anthro)
            val joints = local.translated(st.rootX)
            val hipMid = V3(post.hip.x + st.rootX, post.hip.y, 0.0)
            val zHip = max(cam.depth(hipMid), 0.05)

            val face = SynthBody.faceDirection(post)
            val nose = joints[PoseLandmark.NOSE]
            val toCamera = (cam.position - nose).unit()
            val faceVisibilityFactor = faceFactor(face.dot(toCamera))

            val imgClean = FloatArray(keptImage.size * 5)
            for ((slot, lm) in keptImage.withIndex()) {
                cam.project(joints[lm], tmp)
                val o = slot * 5
                imgClean[o] = tmp[0].toFloat()
                imgClean[o + 1] = tmp[1].toFloat()
                imgClean[o + 2] = ((tmp[2] - zHip) * (cam.focalPx / zHip) / cam.widthPx).toFloat()
                imgClean[o + 3] = visibility(lm, sinPsi, noise.farSideVisibility, faceVisibilityFactor).toFloat()
                imgClean[o + 4] = PRESENCE
            }
            val wldClean = FloatArray(keptWorld.size * 3)
            for ((slot, lm) in keptWorld.withIndex()) {
                cam.worldOf(joints[lm], hipMid, tmp)
                val o = slot * 3
                wldClean[o] = tmp[0].toFloat()
                wldClean[o + 1] = tmp[1].toFloat()
                wldClean[o + 2] = tmp[2].toFloat()
            }
            clean.add(PoseFrameV2.fromArrays(tUs, 1, imgClean, wldClean))

            val img = imgClean.copyOf()
            val wld = wldClean.copyOf()
            for ((slot, lm) in keptImage.withIndex()) {
                val gx = rngNoise.nextGaussian()
                val gy = rngNoise.nextGaussian()
                val gz = rngNoise.nextGaussian()
                val outlier = rngNoise.nextDouble() < noise.outlierRate
                var sigma = noise.sigmaFh * (if (outlier) 10.0 else 1.0)
                val o = slot * 5
                if (isOccluded(noise, lm, t)) {
                    sigma = max(sigma, 5.0 * max(noise.sigmaFh, 0.002))
                    img[o + 3] = 0.1f
                }
                img[o] += (sigma * gx / aspect).toFloat()
                img[o + 1] += (sigma * gy).toFloat()
                img[o + 2] += (sigma * gz / aspect).toFloat()
            }
            for (i in wld.indices) wld[i] += (noise.worldSigmaM * rngWorld.nextGaussian()).toFloat()

            val drop = rngDrop.nextDouble() < noise.dropRate
            if (drop) {
                dropped[k] = true
                frames.add(PoseFrameV2.fromArrays(tUs, 0, null, null))
            } else {
                frames.add(PoseFrameV2.fromArrays(tUs, 1, img, wld))
            }
        }

        val header = { list: List<PoseFrameV2> ->
            PoseTrackV2(
                mediaId = "synthetic-partials-${config.movement.name.lowercase()}",
                engine = PoseEngineInfo("synthetic", "lifter", "CPU"),
                frameWidth = config.camera.widthPx,
                frameHeight = config.camera.heightPx,
                rotationApplied = 0,
                nominalFps = fps.toFloat(),
                poseStride = 1,
                vfr = false,
                captureFps = null,
                focalLengthPx = cam.focalPx.toFloat(),
                gravityRollDeg = config.camera.rollDeg.toFloat(),
                gravityPitchDeg = config.camera.pitchDeg.toFloat(),
                aspectKnown = true,
                frames = list,
            )
        }
        return SynthResult(
            track = header(frames),
            reps = emptyList(),
            primaryAngleDeg = DoubleArray(n),
            cleanTrack = header(clean),
            droppedFrames = dropped,
        )
    }

    private fun rng(seed: Long, salt: Int): Random = Random(seed * -7046029254386353131L + salt * 1442695040888963407L)

    private fun sideOf(lm: PoseLandmark): Side? = when (lm) {
        PoseLandmark.NOSE, PoseLandmark.MOUTH_LEFT, PoseLandmark.MOUTH_RIGHT -> null
        else -> when {
            lm.name.startsWith("LEFT_") -> Side.LEFT
            lm.name.startsWith("RIGHT_") -> Side.RIGHT
            else -> null
        }
    }

    private fun isFace(lm: PoseLandmark) = lm == PoseLandmark.NOSE || lm == PoseLandmark.LEFT_EAR ||
        lm == PoseLandmark.RIGHT_EAR || lm == PoseLandmark.MOUTH_LEFT || lm == PoseLandmark.MOUTH_RIGHT

    private fun visibility(lm: PoseLandmark, sinPsi: Double, farVisibility: Float, faceFactor: Double): Double {
        val side = sideOf(lm)
        var v = NEAR_VISIBILITY
        if (side != null) {
            val near = (side == Side.LEFT && sinPsi >= 0.0) || (side == Side.RIGHT && sinPsi <= 0.0)
            if (!near) v = NEAR_VISIBILITY - (NEAR_VISIBILITY - farVisibility) * abs(sinPsi)
        }
        if (isFace(lm)) v *= faceFactor
        return v
    }

    private fun faceFactor(dot: Double): Double {
        val floorRatio = MIN_FACE_VISIBILITY / NEAR_VISIBILITY
        return when {
            dot >= -0.1 -> 1.0
            dot <= -0.6 -> floorRatio
            else -> 1.0 + (dot + 0.1) / 0.5 * (1.0 - floorRatio)
        }
    }

    private fun isOccluded(noise: SynthNoise, lm: PoseLandmark, t: Double): Boolean {
        if (lm !in noise.occludedLandmarks) return false
        for ((a, b) in noise.occlusionWindowsS) if (t >= a && t <= b) return true
        return false
    }
}
