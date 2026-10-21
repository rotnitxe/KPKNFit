package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/*
 * Fase 2: parciales SEMI-sintéticas sobre trazas REALES (herramienta de medición opcional; no cambia el motor).
 *
 * Se parte de clips reales del paquete de desarrollo (`C:\kw\vt-tune`) cuyo conteo ya es limpio (todas completas y el motor coincide
 * con la etiqueta) y se CONVIERTEN repeticiones concretas en parciales deformando la traza de pose, sin añadir ruido:
 *
 *   pose'(t) = pose(t) + (s'(t) − s(t)) · ΔP
 *
 * donde s(t) ∈ [0, 1] es el progreso de la repetición en la señal de conteo REAL del motor (reposo = 0, giro = 1), ΔP es la
 * diferencia de postura entre el giro y el reposo de esa repetición (puntos de imagen y «world», con las visibilidades intactas)
 * y s'(t) la trayectoria nueva:
 *  - acortada (sin llegar al giro, por el lado del reposo): s' = f·s; la repetición recorre el f de su recorrido y vuelve al reposo;
 *  - en estiramiento (cerca del giro, sin volver al reposo): antes del giro s' = lb + (1 − lb)·s y después s' = 1 − (1 − la)(1 − s);
 *    el atleta acaba cada repetición en el nivel la, y la siguiente sale de ahí. Solo se aplica a la cola de la serie (o a toda
 *    ella), porque el atleta se queda en el valle hasta el final del vídeo.
 * El ruido y el temblor originales (el residuo de pose(t) respecto de la trayectoria suave) se conservan, y las velocidades se
 * escalan con el recorrido.
 *
 * Verdad: igual que en la fase 1, a = min(subida, bajada) en fracción de la amplitud de la repetición COMPLETA (la amplitud
 * original de cada repetición, `amplitudeFraction` del motor sobre la traza intacta): FULL a ≥ 0,70, PARTIAL 0,30 ≤ a < 0,70.
 */

internal enum class SemiScenario(val label: String, val description: String) {
    CONTROL("control_sin_tocar", "la traza real sin modificar (control)"),
    FINAL_ACORTADAS("final_acortadas", "las últimas 1-3 repeticiones, acortadas (sin llegar al giro) al 35-50 %"),
    FINAL_ESTIRAMIENTO("final_estiramiento", "las últimas 1-3 repeticiones, en estiramiento (cerca del giro, sin volver) al 35-50 %"),
    INTERCALADAS("intercaladas", "1-3 repeticiones acortadas al 35-62 % entre completas"),
    TODAS_ACORTADAS("todas_acortadas", "serie entera de parciales acortadas al 35-45 %"),
    TODAS_ESTIRAMIENTO("todas_estiramiento", "serie entera de parciales en estiramiento al 35-45 %"),
    UMBRAL_ACORTADAS("umbral_acortadas", "las últimas 1-3 repeticiones acortadas al 55-69 %"),
    UMBRAL_ESTIRAMIENTO("umbral_estiramiento", "las últimas 1-3 repeticiones en estiramiento al 55-69 %"),
}

internal enum class SemiMode { SHORT, STRETCH }

/**
 * Cómo se transforma la repetición [rep]: [f] = recorrido de DISEÑO relativo a la suya completa; [lb] y [la] = niveles de partida y
 * de llegada del diseño. Los valores `…Applied` son los que se aplican a la pose: la señal de conteo no es lineal en la pose, así
 * que se corrigen hasta que el recorrido REALIZADO en la señal del motor coincida con el de diseño (la verdad usa el de diseño).
 */
internal class SemiRepPlan(
    val rep: Int, val mode: SemiMode, val f: Double, val lb: Double, val la: Double,
    val fApplied: Double = f, val lbApplied: Double = lb, val laApplied: Double = la,
    val realized: Double = Double.NaN,
) {
    fun with(fNew: Double, lbNew: Double, realizedNew: Double) =
        SemiRepPlan(rep, mode, f, lb, la, fNew, lbNew, 1.0 - fNew, realizedNew)
}

internal class SemiRep(
    val index: Int,
    val engine: EngineRep,
    val amplitude: Double,
    val bStartUs: Long,
    val bEndUs: Long,
    val tPeakUs: Long,
    val rBefore: Double,
    val rAfter: Double,
    val rPeak: Double,
    val deltaImg: FloatArray,
    val deltaWld: FloatArray,
)

internal class SemiClip(
    val id: String,
    val source: String,
    val profileName: String,
    val profile: AnalysisProfile,
    val labelView: String,
    val supportedView: Boolean,
    val startsEccentric: Boolean,
    val track: PoseTrackV2,
    val t0Us: Long,
    val fs: Double,
    val r: DoubleArray,
    val reps: List<SemiRep>,
    val analyzedFps: Double?,
    val windowS: Pair<Double, Double>?,
) {
    /** La señal de conteo del motor en el instante [tUs] (interpolada). */
    fun rAt(tUs: Long): Double {
        val k = (tUs - t0Us) / 1e6 * fs
        if (k <= 0.0) return r.first()
        if (k >= r.size - 1) return r.last()
        val i = k.toInt()
        val w = k - i
        return r[i] * (1 - w) + r[i + 1] * w
    }

    fun indexOfUs(tUs: Long): Int = (((tUs - t0Us) / 1e6 * fs).roundToLong()).toInt().coerceIn(0, r.size - 1)
}

/** Construye un [SemiClip] desde una traza con conteo limpio, o dice por qué no sirve. */
internal object SemiClipBuilder {
    class Result(val clip: SemiClip?, val reason: String?)

    private const val PEAK_SEARCH_US = 400_000L
    private const val POSE_WINDOW_US = 100_000L

    fun build(
        id: String, source: String, profileName: String, profile: AnalysisProfile, labelView: String, supportedView: Boolean,
        track: PoseTrackV2, expectedTurnsUs: List<Long>?, windowS: Pair<Double, Double>?,
    ): Result {
        val report = try {
            TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(track, null, profile, null, null, emptySet()))
        } catch (e: Throwable) {
            return Result(null, "motor_lanza_excepcion")
        }
        if (MetricKey.REPS !in report.available) return Result(null, "sin_salida")
        if (report.partialReps != 0 || report.failedReps != 0) return Result(null, "el_motor_ve_parciales_o_fallidas")
        if (report.reps.any { it.kind != RepKind.FULL }) return Result(null, "el_motor_ve_parciales_o_fallidas")
        if (report.repCount < 2) return Result(null, "menos_de_2_repeticiones")
        if ("POLARITY_FLIPPED" in report.flags.map { it.name }) return Result(null, "polaridad_invertida")
        val engineReps = report.reps.map {
            EngineRep(it.kind.name, it.partialKind?.name, it.amplitudeFraction, it.phases.departUs,
                (it.phases.turnStartUs + it.phases.turnEndUs) / 2, it.phases.arriveUs)
        }
        if (expectedTurnsUs != null) {
            if (expectedTurnsUs.size != engineReps.size) return Result(null, "conteo_distinto_de_la_etiqueta")
            val a = expectedTurnsUs.sorted()
            val b = engineReps.map { it.turnCenterUs }.sorted()
            if (a.indices.any { abs(a[it] - b[it]) > 700_000L }) return Result(null, "giros_lejos_de_la_etiqueta")
        }
        val prepared = TrackPreparation.prepare(track)
        val built = CountSeriesBuilder.build(prepared, null, profile) as? CountSeriesResult.Ok ?: return Result(null, "sin_serie_de_conteo")
        val series = built.series
        val r = fill(series.r)
        if (r.isEmpty()) return Result(null, "serie_vacia")

        val frames = track.frames
        val firstUs = frames.first().tUs
        val lastUs = frames.last().tUs
        val clipShell = SemiClip(
            id, source, profileName, profile, labelView, supportedView, profile.startPhase == StartPhase.ECCENTRIC_FIRST, track,
            series.t0Us, series.fs, r, emptyList(), prepared.timebase.fs, windowS,
        )
        val reps = ArrayList<SemiRep>()
        for ((j, e) in engineReps.withIndex()) {
            val startUs = if (j == 0) firstUs else (engineReps[j - 1].arriveUs + e.departUs) / 2
            val endUs = if (j == engineReps.size - 1) lastUs + 1 else (e.arriveUs + engineReps[j + 1].departUs) / 2
            // Pico de la señal en torno al giro del motor.
            val lo = clipShell.indexOfUs(e.turnCenterUs - PEAK_SEARCH_US)
            val hi = clipShell.indexOfUs(e.turnCenterUs + PEAK_SEARCH_US)
            var peakK = lo
            for (k in lo..hi) if (r[k] > r[peakK]) peakK = k
            val tPeak = series.t0Us + (peakK * 1e6 / series.fs).roundToLong()
            val bStartK = clipShell.indexOfUs(startUs)
            val bEndK = clipShell.indexOfUs(endUs)
            var vbK = min(bStartK, peakK)
            for (k in min(bStartK, peakK)..peakK) if (r[k] < r[vbK]) vbK = k
            var vaK = peakK
            for (k in peakK..max(bEndK, peakK)) if (r[k] < r[vaK]) vaK = k
            val rPeak = r[peakK]
            if (rPeak - max(r[vbK], r[vaK]) <= 1e-9) return Result(null, "senal_plana")
            val tVb = series.t0Us + (vbK * 1e6 / series.fs).roundToLong()
            val tVa = series.t0Us + (vaK * 1e6 / series.fs).roundToLong()
            val peakPose = meanPose(frames, tPeak)
            val rest1 = meanPose(frames, tVb)
            val rest2 = meanPose(frames, tVa)
            val dImg = FloatArray(peakPose.first.size) { i ->
                val d = peakPose.first[i] - 0.5f * (rest1.first[i] + rest2.first[i])
                if (d.isNaN()) 0f else d
            }
            val dWld = FloatArray(peakPose.second.size) { i ->
                val d = peakPose.second[i] - 0.5f * (rest1.second[i] + rest2.second[i])
                if (d.isNaN()) 0f else d
            }
            reps.add(SemiRep(j, e, e.amplitude, startUs, endUs, tPeak, r[vbK], r[vaK], rPeak, dImg, dWld))
        }
        return Result(
            SemiClip(
                id, source, profileName, profile, labelView, supportedView, clipShell.startsEccentric, track,
                series.t0Us, series.fs, r, reps, prepared.timebase.fs, windowS,
            ),
            null,
        )
    }

    /** Rellena los NaN con el valor válido más cercano (la señal ya viene con los huecos cortos interpolados). */
    private fun fill(src: DoubleArray): DoubleArray {
        val out = src.copyOf()
        var last = Double.NaN
        for (i in out.indices) if (out[i].isNaN()) out[i] = last else last = out[i]
        last = Double.NaN
        for (i in out.indices.reversed()) if (out[i].isNaN()) out[i] = last else last = out[i]
        return if (out.any { it.isNaN() }) DoubleArray(0) else out
    }

    /** Postura media (imagen x,y,z por punto; «world» x,y,z por punto) de los fotogramas con pose a ±0,1 s de [centerUs]. */
    fun meanPose(frames: List<PoseFrameV2>, centerUs: Long): Pair<FloatArray, FloatArray> {
        val nImg = PoseLandmark.KEPT_IMAGE.size
        val nWld = PoseLandmark.KEPT_WORLD.size
        var picked = frames.filter { it.hasPose && abs(it.tUs - centerUs) <= POSE_WINDOW_US }
        if (picked.size < 2) picked = frames.filter { it.hasPose }.sortedBy { abs(it.tUs - centerUs) }.take(2)
        val img = FloatArray(nImg * 3)
        val wld = FloatArray(nWld * 3)
        for (slot in 0 until nImg) for (c in 0 until 3) {
            var sum = 0.0
            var n = 0
            for (f in picked) {
                val v = f.imageData()!![slot * PoseFrameV2.IMAGE_STRIDE + c]
                if (!v.isNaN()) { sum += v; n++ }
            }
            img[slot * 3 + c] = if (n == 0) Float.NaN else (sum / n).toFloat()
        }
        for (slot in 0 until nWld) for (c in 0 until 3) {
            var sum = 0.0
            var n = 0
            for (f in picked) {
                val d = f.worldData() ?: continue
                val v = d[slot * PoseFrameV2.WORLD_STRIDE + c]
                if (!v.isNaN()) { sum += v; n++ }
            }
            wld[slot * 3 + c] = if (n == 0) Float.NaN else (sum / n).toFloat()
        }
        return img to wld
    }
}

internal object SemiTransform {
    /** Aplica el plan a la traza: pose' = pose + (s' − s)·ΔP dentro de la región de cada repetición modificada. */
    fun apply(clip: SemiClip, plan: List<SemiRepPlan>): PoseTrackV2 {
        if (plan.isEmpty()) return clip.track
        val byRep = plan.associateBy { it.rep }
        // ΔP común en un bloque en estiramiento (para que el valle de una repetición sea la salida de la siguiente).
        val stretch = plan.filter { it.mode == SemiMode.STRETCH }
        val blockImg = if (stretch.isEmpty()) null else mean(stretch.map { clip.reps[it.rep].deltaImg })
        val blockWld = if (stretch.isEmpty()) null else mean(stretch.map { clip.reps[it.rep].deltaWld })
        val out = ArrayList<PoseFrameV2>(clip.track.frames.size)
        var current = 0
        for (f in clip.track.frames) {
            if (!f.hasPose) { out.add(f); continue }
            val t = f.tUs
            while (current < clip.reps.size - 1 && t >= clip.reps[current].bEndUs) current++
            val rep = clip.reps[current]
            val p = byRep[current]
            if (p == null || t < rep.bStartUs) { out.add(f); continue }
            val rt = clip.rAt(t)
            val pre = t <= rep.tPeakUs
            val base = if (pre) rep.rBefore else rep.rAfter
            val s = ((rt - base) / (rep.rPeak - base)).coerceIn(0.0, 1.0)
            val sNew = when (p.mode) {
                SemiMode.SHORT -> p.fApplied * s
                SemiMode.STRETCH -> if (pre) p.lbApplied + (1 - p.lbApplied) * s else 1 - (1 - p.laApplied) * (1 - s)
            }
            val d = (sNew - s).toFloat()
            val dImg = if (p.mode == SemiMode.STRETCH) blockImg!! else rep.deltaImg
            val dWld = if (p.mode == SemiMode.STRETCH) blockWld!! else rep.deltaWld
            val img = f.imageData()!!.copyOf()
            for (slot in 0 until PoseLandmark.KEPT_IMAGE.size) for (c in 0 until 3) {
                val v = img[slot * PoseFrameV2.IMAGE_STRIDE + c]
                if (!v.isNaN()) img[slot * PoseFrameV2.IMAGE_STRIDE + c] = v + d * dImg[slot * 3 + c]
            }
            val wld = f.worldData()?.copyOf()
            if (wld != null) for (slot in 0 until PoseLandmark.KEPT_WORLD.size) for (c in 0 until 3) {
                val v = wld[slot * PoseFrameV2.WORLD_STRIDE + c]
                if (!v.isNaN()) wld[slot * PoseFrameV2.WORLD_STRIDE + c] = v + d * dWld[slot * 3 + c]
            }
            out.add(PoseFrameV2.fromArrays(f.tUs, f.posesDetected, img, wld))
        }
        return clip.track.copy(frames = out)
    }

    private fun mean(list: List<FloatArray>): FloatArray = FloatArray(list.first().size) { i -> list.map { it[i] }.average().toFloat() }
}

internal object SemiPlanner {
    private fun u(rng: Random, lo: Double, hi: Double) = lo + (hi - lo) * rng.nextDouble()

    /** El plan de la serie, o `null` si el clip no da para ese escenario (pocas repeticiones). */
    fun plan(clip: SemiClip, scenario: SemiScenario, rng: Random): List<SemiRepPlan>? {
        val n = clip.reps.size
        fun f(j: Int, target: Double) = (target / clip.reps[j].amplitude).coerceIn(0.15, 0.95)
        fun short(js: List<Int>, lo: Double, hi: Double) = js.map { SemiRepPlan(it, SemiMode.SHORT, f(it, u(rng, lo, hi)), 0.0, 0.0) }
        fun stretch(js: List<Int>, lo: Double, hi: Double): List<SemiRepPlan> {
            val out = ArrayList<SemiRepPlan>()
            var lb = 0.0
            for (j in js) {
                val fj = f(j, u(rng, lo, hi))
                out.add(SemiRepPlan(j, SemiMode.STRETCH, fj, lb, 1.0 - fj))
                lb = 1.0 - fj
            }
            return out
        }
        fun tail(maxK: Int): List<Int>? {
            if (n < 3) return null
            val k = 1 + rng.nextInt(min(maxK, n - 2))
            return (n - k until n).toList()
        }
        return when (scenario) {
            SemiScenario.CONTROL -> emptyList()
            SemiScenario.FINAL_ACORTADAS -> tail(3)?.let { short(it, 0.35, 0.50) }
            SemiScenario.FINAL_ESTIRAMIENTO -> tail(3)?.let { stretch(it, 0.35, 0.50) }
            SemiScenario.UMBRAL_ACORTADAS -> tail(3)?.let { short(it, 0.55, 0.69) }
            SemiScenario.UMBRAL_ESTIRAMIENTO -> tail(3)?.let { stretch(it, 0.55, 0.69) }
            SemiScenario.INTERCALADAS -> {
                if (n < 4) return null
                val m = 1 + rng.nextInt(min(3, n - 2))
                val js = (0 until n).shuffled(rng).take(m).sorted()
                short(js, 0.35, 0.62)
            }
            SemiScenario.TODAS_ACORTADAS -> short((0 until n).toList(), 0.35, 0.45)
            SemiScenario.TODAS_ESTIRAMIENTO -> stretch((0 until n).toList(), 0.35, 0.45)
        }
    }

    /** Verdad por repetición según el plan; las que quedan por debajo del 30 % no son repeticiones para el motor y se omiten. */
    fun truth(clip: SemiClip, plan: List<SemiRepPlan>): List<PTruthRep> {
        val byRep = plan.associateBy { it.rep }
        val out = ArrayList<PTruthRep>()
        for (rep in clip.reps) {
            val p = byRep[rep.index]
            val a = rep.amplitude
            val (hi, vb, va) = when {
                p == null -> Triple(a, 0.0, 0.0)
                p.mode == SemiMode.SHORT -> Triple(p.f * a, 0.0, 0.0)
                else -> Triple(a, p.lb * a, p.la * a)
            }
            val up = hi - vb
            val down = hi - va
            val frac = min(up, down)
            val kind = when {
                frac >= 0.70 -> TruthKind.FULL
                frac >= 0.30 -> TruthKind.PARTIAL
                else -> continue
            }
            val zone = when {
                kind == TruthKind.FULL -> PartialsZone.FULL_RANGE
                p?.mode == SemiMode.STRETCH -> PartialsZone.TURN_SIDE
                else -> PartialsZone.REST_SIDE
            }
            val muscle = when (zone) {
                PartialsZone.TURN_SIDE -> if (clip.startsEccentric) "ESTIRAMIENTO" else "ACORTADA"
                PartialsZone.REST_SIDE -> if (clip.startsEccentric) "ACORTADA" else "ESTIRAMIENTO"
                else -> "COMPLETA"
            }
            val e = rep.engine
            out.add(
                PTruthRep(
                    index = out.size, kind = kind, zone = zone, muscleZone = muscle, hi = hi, valleyBefore = vb, valleyAfter = va,
                    upFraction = up, downFraction = down, fraction = frac, angleFraction = Double.NaN,
                    departUs = e.departUs, turnStartUs = e.turnCenterUs, turnEndUs = e.turnCenterUs, arriveUs = e.arriveUs,
                ),
            )
        }
        return out
    }
}

/** Corrige el plan hasta que el recorrido realizado en la señal del motor coincida con el de diseño. */
internal object SemiCalibrator {
    private const val ITERATIONS = 2
    private const val PEAK_SEARCH_US = 400_000L

    /** Recorrido realizado de cada repetición modificada (SHORT: min(subida, bajada); STRETCH: la bajada), relativo a su recorrido completo. */
    fun realized(clip: SemiClip, track: PoseTrackV2, plan: List<SemiRepPlan>): Map<Int, Double>? {
        val built = CountSeriesBuilder.build(TrackPreparation.prepare(track), null, clip.profile) as? CountSeriesResult.Ok ?: return null
        val s = built.series
        if (s.r.size != clip.r.size || s.t0Us != clip.t0Us) return null
        val r = s.r
        val out = HashMap<Int, Double>()
        for (p in plan) {
            val rep = clip.reps[p.rep]
            val pk = clip.indexOfUs(rep.tPeakUs)
            val lo = clip.indexOfUs(rep.tPeakUs - PEAK_SEARCH_US)
            val hi = clip.indexOfUs(rep.tPeakUs + PEAK_SEARCH_US)
            var peak = Double.NEGATIVE_INFINITY
            for (k in lo..hi) if (!r[k].isNaN()) peak = max(peak, r[k])
            val bs = clip.indexOfUs(rep.bStartUs)
            val be = clip.indexOfUs(rep.bEndUs)
            var before = Double.POSITIVE_INFINITY
            for (k in min(bs, pk)..pk) if (!r[k].isNaN()) before = min(before, r[k])
            var after = Double.POSITIVE_INFINITY
            for (k in pk..max(be, pk)) if (!r[k].isNaN()) after = min(after, r[k])
            if (peak.isInfinite() || before.isInfinite() || after.isInfinite()) continue
            val up = (peak - before) / (rep.rPeak - rep.rBefore)
            val down = (peak - after) / (rep.rPeak - rep.rAfter)
            out[p.rep] = if (p.mode == SemiMode.SHORT) min(up, down) else down
        }
        return out
    }

    fun calibrate(clip: SemiClip, plan: List<SemiRepPlan>): Pair<List<SemiRepPlan>, PoseTrackV2> {
        var current = plan
        var track = SemiTransform.apply(clip, current)
        if (plan.isEmpty()) return current to track
        repeat(ITERATIONS) {
            val real = realized(clip, track, current) ?: return current to track
            var lb = 0.0
            current = current.map { p ->
                val r = real[p.rep]
                var fNew = if (r != null && r > 0.02) (p.fApplied * p.f / r).coerceIn(0.08, 1.3) else p.fApplied
                if (p.mode == SemiMode.STRETCH) fNew = fNew.coerceAtMost(0.98)
                val lbNew = if (p.mode == SemiMode.STRETCH) lb else 0.0
                if (p.mode == SemiMode.STRETCH) lb = 1.0 - fNew
                p.with(fNew, lbNew, r ?: Double.NaN)
            }
            track = SemiTransform.apply(clip, current)
        }
        // Medida final del recorrido realizado (para el informe).
        val real = realized(clip, track, current)
        if (real != null) current = current.map { p -> p.with(p.fApplied, p.lbApplied, real[p.rep] ?: Double.NaN) }
        return current to track
    }
}

internal object SemiRunner {
    fun analyze(track: PoseTrackV2, profile: AnalysisProfile): EngineOut = try {
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(track, null, profile, null, null, emptySet()))
        EngineOut.of(report)
    } catch (e: Throwable) {
        EngineOut.failure(e.javaClass.simpleName + ": " + e.message)
    }

    fun run(clip: SemiClip, scenario: SemiScenario, variant: Int, seed: Long): PartialsOutcome? {
        val started = System.nanoTime()
        val designed = SemiPlanner.plan(clip, scenario, Random(seed)) ?: return null
        val (plan, track) = SemiCalibrator.calibrate(clip, designed)
        val engine = analyze(track, clip.profile)
        val truth = SemiPlanner.truth(clip, plan)
        val matches = PartialsMatching.match(truth, engine.reps.map { it.turnCenterUs })
        val id = String.format(Locale.ROOT, "%s_%s_v%d", clip.id, scenario.label, variant)
        val meta = SeriesMeta(
            id,
            linkedMapOf(
                "escenario" to scenario.label, "perfil" to clip.profileName, "vista" to clip.labelView.lowercase(Locale.ROOT),
                "fps" to FullBankReport.fpsBand(clip.analyzedFps), "fuente" to clip.source,
                "arranque" to (if (clip.startsEccentric) "excentrica_primero" else "concentrica_primero"),
                "vistaSoportada" to (if (clip.supportedView) "soportada" else "no_soportada"),
            ),
            buildJsonObject {
                put("clip", clip.id); put("semilla", seed); put("variante", variant)
                put("repsOriginales", clip.reps.size)
                put("ventanaS", clip.windowS?.let { JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second))) } ?: JsonArray(emptyList()))
                put("plan", buildJsonArray {
                    for (p in plan) add(buildJsonObject {
                        put("repeticion", p.rep); put("modo", p.mode.name); put("f", p.f); put("niveloPartida", p.lb); put("nivelLlegada", p.la)
                        put("fAplicada", p.fApplied); put("recorridoRealizado", if (p.realized.isNaN()) null else p.realized)
                        put("amplitudOriginal", clip.reps[p.rep].amplitude)
                    })
                })
            },
        )
        return PartialsOutcome(meta, truth, truth, null, engine, matches, BooleanArray(engine.reps.size), track.frames.size,
            (System.nanoTime() - started) / 1_000_000)
    }
}

internal object SemiBench {
    const val SEED = 20261021L

    class Loaded(val clips: List<SemiClip>, val stats: Map<String, Int>)

    /** Los clips G1 de desarrollo con conteo limpio. */
    fun load(root: File, log: (String) -> Unit = {}): Loaded {
        val manifest = FullBankReport.readObject(File(root, "manifest.json"))
        val stats = java.util.TreeMap<String, Int>()
        fun count(key: String) { stats[key] = (stats[key] ?: 0) + 1 }
        val clips = ArrayList<SemiClip>()
        for (rowJson in manifest.getValue("rows").jsonArray.map { it.jsonObject }) {
            val row = FullBankRow(rowJson)
            if (row.category != "G1" || row.probe || row.exam) continue
            val labelFile = FullBankReport.path(root, rowJson.s("labelPath"))
            val poseFile = FullBankReport.path(root, rowJson.optional("posePath") ?: "traces/${row.id}.pose2.json.gz")
            if (!labelFile.isFile || !poseFile.isFile) { count("descartado:no_esta_en_el_paquete") ; continue }
            count("candidatos_G1")
            val label = FullBankReport.readObject(labelFile)
            val reps = label["reps"]?.jsonObject
            val window = label["activeWindowS"]?.takeIf { it is JsonArray }?.jsonArray?.map { it.jsonPrimitive.doubleOrNull ?: Double.NaN }
            val turns = label["repTimesS"]?.takeIf { it is JsonArray }?.jsonArray?.mapNotNull { it.jsonObject["turnaroundS"]?.jsonPrimitive?.doubleOrNull }
            val reason = when {
                label["usable"]?.jsonPrimitive?.booleanOrNull != true -> "etiqueta_no_usable"
                reps == null || reps["uncertain"]?.jsonPrimitive?.booleanOrNull == true -> "etiqueta_incierta"
                reps["partial"]?.jsonPrimitive?.intOrNull != 0 || reps["failed"]?.jsonPrimitive?.intOrNull != 0 -> "etiqueta_con_parciales_o_fallidas"
                (reps["full"]?.jsonPrimitive?.intOrNull ?: 0) < 2 -> "menos_de_2_completas"
                label["fullBodyVisible"]?.jsonPrimitive?.booleanOrNull != true || label["people"]?.jsonPrimitive?.intOrNull != 1 -> "cuerpo_no_entero_o_varias_personas"
                window == null || window.size != 2 || window.any { it.isNaN() } -> "sin_ventana_activa"
                turns == null || turns.size != reps["full"]?.jsonPrimitive?.intOrNull -> "giros_no_coinciden_con_el_conteo"
                else -> null
            }
            if (reason != null) { count("descartado:$reason"); continue }
            val profile = try { FullBankReport.resolve(row) } catch (e: Throwable) { count("descartado:perfil_no_soportado"); continue }
            val pose = try { PoseTrackCodec.decode(poseFile.readBytes()) } catch (e: Throwable) { null }
            if (pose == null || pose.frames.isEmpty()) { count("descartado:traza_ilegible"); continue }
            val start = (window!![0] * 1e6).roundToLong()
            val end = (window[1] * 1e6).roundToLong()
            val cropped = pose.copy(frames = pose.frames.filter { it.tUs in start..end })
            if (cropped.frames.size < 10) { count("descartado:ventana_vacia"); continue }
            val viewName = label["view"]?.jsonPrimitive?.content ?: "UNKNOWN"
            val supported = ViewKind.entries.any { it.name == viewName && it in profile.countViews }
            val res = SemiClipBuilder.build(
                row.id, row.source, row.benchProfile, profile, viewName, supported, cropped,
                turns!!.map { (it * 1e6).roundToLong() }, window[0] to window[1],
            )
            if (res.clip == null) { count("descartado:${res.reason}"); continue }
            count("limpios")
            clips.add(res.clip)
        }
        log("[semi] clips limpios: ${clips.size} de ${stats["candidatos_G1"] ?: 0} candidatos G1")
        return Loaded(clips, stats)
    }

    fun run(root: File, outDir: File, minSeries: Int, log: (String) -> Unit = {}): List<PartialsOutcome> {
        val loaded = load(root, log)
        val clips = loaded.clips
        // Menor número de variantes con el que salen al menos [minSeries] series (control solo en la variante 0).
        fun total(v: Int): Int = clips.sumOf { clip ->
            SemiScenario.entries.sumOf { sc ->
                val applicable = SemiPlanner.plan(clip, sc, Random(1L)) != null
                if (!applicable) 0 else if (sc == SemiScenario.CONTROL) 1 else v
            }
        }
        var variants = 1
        while (variants < 10 && total(variants) < minSeries) variants++
        val started = System.nanoTime()
        val outs = ArrayList<PartialsOutcome>()
        var serial = 0
        for (clip in clips) for (sc in SemiScenario.entries) for (v in 0 until variants) {
            if (sc == SemiScenario.CONTROL && v > 0) continue
            serial++
            val seed = SEED + 104729L * serial
            val o = SemiRunner.run(clip, sc, v, seed) ?: continue
            outs.add(o)
            if (outs.size % 100 == 0) log("[semi] ${outs.size} series")
        }
        val elapsed = (System.nanoTime() - started) / 1e9
        val errs = ArrayList<Double>()
        for (o in outs) (o.meta.extra["plan"] as? JsonArray)?.forEach {
            val j = it.jsonObject
            val real = j["recorridoRealizado"]?.jsonPrimitive?.doubleOrNull
            val design = j["f"]?.jsonPrimitive?.doubleOrNull
            if (real != null && design != null) errs.add(real - design)
        }
        PartialsReport.write(outDir, outs, semiDef(loaded, variants, errs), elapsed)
        return outs
    }

    fun semiDef(loaded: Loaded, variants: Int, errs: List<Double> = emptyList()) = ReportDef(
        title = "Parciales semi-sintéticas sobre trazas reales (fase 2)",
        intro = "Clips reales del paquete de desarrollo (G1) con el conteo limpio (todas completas y el motor igual a la etiqueta, sin disco), " +
            "en los que se convierten repeticiones concretas en parciales deformando la traza de pose real (puntos de imagen y «world», visibilidades intactas). " +
            "${loaded.clips.size} clips limpios, $variants variantes por escenario, semilla $SEED.",
        dims = listOf(
            DimDef("perfil", "porPerfil", "Por perfil"), DimDef("vista", "porVista", "Por vista de la etiqueta"),
            DimDef("fps", "porFps", "Por cadencia analizada (fps)"), DimDef("fuente", "porFuente", "Por fuente del clip"),
            DimDef("arranque", "porArranque", "Por arranque del movimiento"), DimDef("vistaSoportada", "porVistaSoportada", "Por vista soportada o no por el perfil"),
        ),
        notes = listOf(
            "Transformación: pose'(t) = pose(t) + (s'(t) − s(t))·ΔP con s el progreso de la repetición en la señal de conteo real del motor y ΔP la diferencia de postura giro − reposo; el residuo (ruido y temblor reales) y las visibilidades se conservan, y las velocidades se escalan con el recorrido.",
            "Acortada: s' = f·s (sale del reposo y no llega al giro). En estiramiento: la repetición llega al giro y baja solo hasta un valle intermedio, donde empieza la siguiente; solo en la cola de la serie o en toda ella (el atleta se queda en el valle hasta el final del vídeo).",
            "Verdad: a = min(subida, bajada) relativa a la amplitud completa de cada repetición (la que mide el motor sobre la traza intacta). El ΔP de un bloque en estiramiento es el medio del bloque, y las repeticiones sin modificar quedan como estaban.",
            "El disco no se usa (ni en la selección ni en la medida): el motor cuenta solo con la pose. Los clips son los 'G1' con etiqueta usable, una persona, cuerpo entero, sin parciales ni fallidas en la etiqueta y con el conteo y los giros del motor iguales a los etiquetados.",
            "Escenarios: " + SemiScenario.entries.joinToString("; ") { "`${it.label}` (${it.description})" } + ".",
            if (errs.isEmpty()) "Sin medida del recorrido realizado." else {
                val sorted = errs.sorted()
                String.format(
                    Locale.forLanguageTag("es"),
                    "Recorrido realizado en la señal del motor tras la corrección frente al de diseño (%d repeticiones modificadas): error medio %+.3f, mediana %+.3f, %.0f %% a menos de 0,10 y %.0f %% a menos de 0,15.",
                    errs.size, errs.average(), sorted[sorted.size / 2], 100.0 * errs.count { abs(it) < 0.10 } / errs.size,
                    100.0 * errs.count { abs(it) < 0.15 } / errs.size,
                )
            },
            "Selección de clips: " + loaded.stats.entries.joinToString(", ") { "${it.key}=${it.value}" } + ".",
        ),
        methodText = "",
        metaJson = buildJsonObject {
            put("semilla", SEED); put("variantes", variants); put("clipsLimpios", loaded.clips.size)
            put("seleccion", buildJsonObject { for ((k, v) in loaded.stats) put(k, v) })
        },
    )
}
