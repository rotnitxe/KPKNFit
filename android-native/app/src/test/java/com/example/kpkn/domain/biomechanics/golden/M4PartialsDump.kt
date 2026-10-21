package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.reps.CountSeries
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.RepConfidence
import com.example.kpkn.domain.biomechanics.reps.RepFlags
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import com.example.kpkn.domain.biomechanics.signal.Autocorrelation
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import java.io.File
import java.util.Locale
import java.util.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * M4, solo diagnóstico: vuelca, serie a serie, lo que ve el segmentador en los bancos sintético y semi-sintético de parciales
 * (la señal de conteo, su versión suavizada, los ciclos, la traza y la verdad), y las series de referencia con las que se
 * mediría una referencia PERSONAL. No cambia el motor ni los bancos. Solo corre con KPKN_M4_DUMP_OUT=<carpeta externa>
 * (y KPKN_PARTIALS_SEMI_DIR para la parte semi-sintética).
 */
class M4PartialsDumpTest {
    @Test
    fun optionalM4PartialsDump() {
        val out = System.getenv("KPKN_M4_DUMP_OUT")
        assumeTrue("Volcado M4 opcional: define KPKN_M4_DUMP_OUT", !out.isNullOrBlank())
        val dir = File(requireNotNull(out)).apply { mkdirs() }
        M4PartialsDump.synthetic(File(dir, "synth.jsonl"))
        val semi = System.getenv("KPKN_PARTIALS_SEMI_DIR")
        if (!semi.isNullOrBlank()) M4PartialsDump.semi(File(semi), File(dir, "semi.jsonl"))
    }
}

internal object M4PartialsDump {
    private fun num(x: Double, digits: Int = 5): JsonPrimitive? =
        if (x.isFinite()) JsonPrimitive(String.format(Locale.ROOT, "%.${digits}g", x).toDouble()) else null

    private fun arr(x: DoubleArray?): JsonArray? = x?.let { v -> JsonArray(v.map { num(it) ?: JsonNull }) }

    /** rd del segmentador: paso bajo de conteo según el periodo de la autocorrelación (como `RepSegmenter.Analyzer`). */
    private fun rdOf(r: DoubleArray, fs: Double, t: AnalysisTuning): Pair<DoubleArray, Double?> {
        val rd0 = ZeroPhaseLowPass.filter(r, fs, t.acfLowPassHz)
        val t0 = Autocorrelation.firstPeakLagS(rd0, fs, t.periodMinS, t.periodMaxS, t.periodMinRho)
        val rd = if (t0 != null) {
            ZeroPhaseLowPass.filter(r, fs, (t.countLowPassCycles / t0).coerceIn(t.countLowPassMinHz, t.countLowPassMaxHz))
        } else rd0
        return rd to t0
    }

    fun truthJson(truth: List<PTruthRep>): JsonArray = buildJsonArray {
        for (r in truth) add(buildJsonObject {
            put("k", r.kind.name); put("z", r.zone.name); put("mz", r.muscleZone)
            put("f", num(r.fraction) ?: JsonNull); put("up", num(r.upFraction) ?: JsonNull); put("dn", num(r.downFraction) ?: JsonNull)
            put("hi", num(r.hi) ?: JsonNull); put("vb", num(r.valleyBefore) ?: JsonNull); put("va", num(r.valleyAfter) ?: JsonNull)
            put("d", r.departUs); put("ts", r.turnStartUs); put("te", r.turnEndUs); put("a", r.arriveUs)
        })
    }

    /** Lo que ve el segmentador sobre una traza: señal, rd, ciclos, descartes, dudosos, confianza y la traza de depuración. */
    fun analyze(track: PoseTrackV2, profile: AnalysisProfile): JsonObject = buildJsonObject {
        val tuning = AnalysisTuning.DEFAULT
        val prepared = TrackPreparation.prepare(track, tuning)
        val built = CountSeriesBuilder.build(prepared, null, profile, tuning)
        if (built !is CountSeriesResult.Ok) {
            put("noSignal", (built as CountSeriesResult.NoSignal).reason)
            return@buildJsonObject
        }
        val s: CountSeries = built.series
        val lines = ArrayList<String>()
        val previous = RepSegmenter.trace
        RepSegmenter.trace = { lines.add(it) }
        val seg = try {
            RepSegmenter.segment(s, profile, emptySet(), tuning, prepared)
        } finally {
            RepSegmenter.trace = previous
        }
        val conf = RepConfidence.score(s, seg, prepared.view, profile, RepFlags.of(prepared, s, seg), tuning)
        put("t0Us", s.t0Us); put("fs", s.fs); put("n", s.size); put("unit", s.unit.name); put("source", s.source)
        put("view", prepared.view.kind.name); put("validFraction", num(s.validFraction) ?: JsonNull)
        put("r", arr(s.r) ?: JsonNull)
        val (rd, t0) = rdOf(s.r, s.fs, tuning)
        put("rd", arr(rd) ?: JsonNull); put("acfT0", t0?.let { num(it) } ?: JsonNull)
        put("pieces", arr(ZeroPhaseLowPass.filter(s.r, s.fs, tuning.noiseLowPassHz)) ?: JsonNull)
        put("filled", JsonArray(s.filled.indices.filter { s.filled[it] }.map { JsonPrimitive(it) }))
        put("weak", JsonArray(s.weak.indices.filter { s.weak[it] }.map { JsonPrimitive(it) }))
        s.perSide?.let { sides ->
            for ((side, values) in sides) {
                val tag = if (side == Side.LEFT) "L" else "R"
                put("r$tag", arr(values) ?: JsonNull)
                put("rd$tag", arr(rdOf(values, s.fs, tuning).first) ?: JsonNull)
            }
        }
        put("seg", buildJsonObject {
            put("aFull", num(seg.aFull) ?: JsonNull); put("lRest", num(seg.lRest) ?: JsonNull)
            put("sigma", num(seg.noiseSigma) ?: JsonNull); put("periodS", seg.periodS?.let { num(it) } ?: JsonNull)
            put("flipped", seg.polarityFlipped); put("sideMode", seg.sideMode.name); put("nAmb", seg.ambiguousCycles)
            put("captureLimited", seg.captureLimited)
            put("reason", seg.noRepsReason?.let(::JsonPrimitive) ?: JsonNull)
            put("amb", JsonArray(seg.ambiguity.map { buildJsonObject { put("k", it.kind.name); put("h", num(it.height) ?: JsonNull); put("g", num(it.gapS) ?: JsonNull) } }))
            put("cycles", JsonArray(seg.cycles.map { c ->
                buildJsonObject {
                    put("k", c.kind.name); put("pk", c.partialKind?.name?.let(::JsonPrimitive) ?: JsonNull)
                    put("side", c.side?.name?.let(::JsonPrimitive) ?: JsonNull)
                    put("vL", c.leftValley); put("p", c.peak); put("vR", c.rightValley?.let(::JsonPrimitive) ?: JsonNull)
                    put("a", num(c.amplitudeFraction) ?: JsonNull); put("gap", c.turnInGap); put("mini", c.miniSet)
                }
            }))
            put("disc", JsonArray(seg.discarded.map { buildJsonObject { put("p", it.peak); put("why", it.reason) } }))
        })
        put("conf", buildJsonObject {
            put("level", conf.level.name); put("score", num(conf.score) ?: JsonNull)
            put("forced", conf.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
            put("cTrack", num(conf.cTrack) ?: JsonNull); put("cSnr", num(conf.cSnr) ?: JsonNull)
            put("cAmp", num(conf.cAmp) ?: JsonNull); put("cRhy", num(conf.cRhy) ?: JsonNull); put("cView", num(conf.cView) ?: JsonNull)
        })
        put("trace", JsonArray(lines.map { JsonPrimitive(it) }))
    }

    /** La traza de una serie del banco sintético, como `PartialsRunner.run` (mismo guion, mismo recorte y mismo ruido). */
    fun syntheticTrack(spec: PartialsSeriesSpec): Triple<PoseTrackV2, List<PTruthRep>, Pair<Double, Double>?> {
        val profile = spec.profile
        val ecc = profile.startsEccentric
        val cal = PartialsCalibration.of(profile, spec.view)
        val script = PartialsScripts.build(spec)
        val built = PartialsTimelineBuilder.build(script.items, ecc, cal, profile.movement)
        val rng = Random(spec.seed xor 0x5DEECE66DL)
        val all = built.reps
        var t0 = 0.0
        var t1 = built.totalSeconds
        when (script.cut) {
            CutKind.START -> all.firstOrNull()?.let { r -> t0 = (r.departUs + (0.25 + 0.55 * rng.nextDouble()) * (r.arriveUs - r.departUs)) / 1e6 }
            CutKind.END -> all.lastOrNull()?.let { r -> t1 = (r.departUs + (0.40 + 0.50 * rng.nextDouble()) * (r.arriveUs - r.departUs)) / 1e6 }
            null -> Unit
        }
        val cut = script.cut != null
        val visible = all.map { r -> if (cut) PartialsRunner.applyCut(r, t0, t1, ecc) else r }.filter { it.visible }.mapIndexed { i, r -> r.copy(index = i) }
        val gap: Pair<Double, Double>? = if (spec.gap && t1 - t0 > 5.0) {
            val a = t0 + 1.0 + (t1 - t0 - 3.0) * rng.nextDouble()
            a to a + 0.45
        } else null
        val config = SynthConfig(
            movement = profile.movement, camera = spec.view.camera, fps = spec.fps, script = emptyList<SynthSegment>(),
            noise = spec.noise.noise(spec.fps, gap), seed = spec.seed,
        )
        val generated = PartialsLifter.generate(config, built.timeline)
        val track = generated.track.let { tr ->
            if (!cut) tr else tr.copy(frames = tr.frames.filter { it.tUs >= (t0 * 1e6).toLong() && it.tUs <= (t1 * 1e6).toLong() })
        }
        return Triple(track, visible, if (cut) t0 to t1 else null)
    }

    fun synthetic(out: File) {
        val plan = PartialsPlan.plan(3)
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            // Series de referencia: dos series de completas por perfil, vista, fps y ruido (como las series anteriores del usuario).
            val combos = plan.map { Triple(it.profile, it.view, it.fps to it.noise) }.distinct()
            for ((profile, view, fn) in combos) for (k in 0 until 2) {
                val (fps, noise) = fn
                val id = String.format(Locale.ROOT, "ref_%s_%s_%d_%s_%d", profile.label, view.label, fps.toInt(), noise.label, k)
                val seed = PartialsPlan.SEED + 1_000_003L * (id.hashCode().toLong() and 0xffffL) + k
                val spec = PartialsSeriesSpec(id, PartialsScenario.COMPLETAS, profile, view, fps, noise, false, seed, k)
                val (track, truth, _) = syntheticTrack(spec)
                w.write(buildJsonObject {
                    put("bench", "synthRef"); put("id", id); put("profile", profile.label); put("view", view.label)
                    put("fps", fps.toInt()); put("noise", noise.label); put("k", k)
                    put("truth", truthJson(truth)); put("eng", analyze(track, profile.profile))
                }.toString())
                w.newLine()
            }
            for (spec in plan) {
                val (track, truth, cut) = syntheticTrack(spec)
                w.write(buildJsonObject {
                    put("bench", "synth"); put("id", spec.id)
                    put("dims", buildJsonObject { for ((k, v) in spec.meta().dims) put(k, v) })
                    put("ecc", spec.profile.startsEccentric)
                    put("cut", cut?.let { JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second))) } ?: JsonNull)
                    put("truth", truthJson(truth)); put("eng", analyze(track, spec.profile.profile))
                }.toString())
                w.newLine()
            }
        }
    }

    fun semi(root: File, out: File) {
        val loaded = SemiBench.load(root)
        val clips = loaded.clips
        fun total(v: Int): Int = clips.sumOf { clip ->
            SemiScenario.entries.sumOf { sc ->
                val applicable = SemiPlanner.plan(clip, sc, Random(1L)) != null
                if (!applicable) 0 else if (sc == SemiScenario.CONTROL) 1 else v
            }
        }
        var variants = 1
        while (variants < 10 && total(variants) < 500) variants++
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            for (clip in clips) {
                w.write(buildJsonObject {
                    put("bench", "semiRef"); put("clip", clip.id); put("profile", clip.profileName)
                    put("reps", JsonArray(clip.reps.map { r ->
                        buildJsonObject {
                            put("amp", num(r.amplitude) ?: JsonNull); put("rPeak", num(r.rPeak) ?: JsonNull)
                            put("rBefore", num(r.rBefore) ?: JsonNull); put("rAfter", num(r.rAfter) ?: JsonNull)
                        }
                    }))
                    put("eng", analyze(clip.track, clip.profile))
                }.toString())
                w.newLine()
            }
            var serial = 0
            for (clip in clips) for (sc in SemiScenario.entries) for (v in 0 until variants) {
                if (sc == SemiScenario.CONTROL && v > 0) continue
                serial++
                val seed = SemiBench.SEED + 104729L * serial
                val designed = SemiPlanner.plan(clip, sc, Random(seed)) ?: continue
                val (plan, track) = SemiCalibrator.calibrate(clip, designed)
                val truth = SemiPlanner.truth(clip, plan)
                val id = String.format(Locale.ROOT, "%s_%s_v%d", clip.id, sc.label, v)
                w.write(buildJsonObject {
                    put("bench", "semi"); put("id", id); put("clip", clip.id)
                    put("dims", buildJsonObject {
                        put("escenario", sc.label); put("perfil", clip.profileName); put("vista", clip.labelView.lowercase(Locale.ROOT))
                        put("vistaSoportada", if (clip.supportedView) "soportada" else "no_soportada")
                        put("fold", JsonNull)
                    })
                    put("ecc", clip.startsEccentric)
                    put("plan", JsonArray(plan.map { p -> buildJsonObject { put("rep", p.rep); put("mode", p.mode.name); put("f", num(p.f) ?: JsonNull) } }))
                    put("truth", truthJson(truth)); put("eng", analyze(track, clip.profile))
                }.toString())
                w.newLine()
            }
        }
    }
}
