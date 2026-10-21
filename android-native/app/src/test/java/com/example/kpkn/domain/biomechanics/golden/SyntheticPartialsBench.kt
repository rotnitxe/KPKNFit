package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Banco SINTÉTICO de repeticiones parciales (herramienta de medición opcional; no cambia el motor).
 *
 * Cada serie sale de [PartialsLifter] con una verdad exacta por repetición (tipo, zona, fracción de recorrido) y se pasa por
 * `TechniqueAnalysisEngine.analyze` igual que el evaluador del banco real (`FullBankReport`). Solo corre desde
 * `SyntheticPartialsBenchTest` con KPKN_PARTIALS_BENCH_OUT=<carpeta externa>; escribe `summary.json`, `RESULTADOS.md` y un JSON
 * por serie fuera del repositorio.
 *
 * Definición de la verdad (la misma que el segmentador, con la señal limpia): para cada giro, a = min(subida, bajada) en
 * fracción de la señal de una repetición completa de referencia (1,0); FULL si a ≥ 0,70, PARTIAL si 0,30 ≤ a < 0,70, FAILED si la
 * concéntrica se estanca. Los valles son los del recorrido diseñado (el reposo o el valle de un parcial encadenado).
 */

internal enum class PartialsScenario(val label: String, val description: String) {
    COMPLETAS("completas", "solo repeticiones completas (3 a 8)"),
    PARCIALES_ESTIRAMIENTO_FINAL("parciales_estiramiento_final", "completas y 1 a 3 parciales del 35-50 % pegadas al giro (abajo en sentadilla/banca/rumano), al final"),
    PARCIALES_ACORTADAS_FINAL("parciales_acortadas_final", "completas y 1 a 3 parciales del 35-50 % que salen del reposo (arriba en sentadilla/banca/rumano), al final"),
    PARCIALES_INTERCALADAS("parciales_intercaladas", "completas y parciales (35-62 %) mezcladas, algún par pegado al giro"),
    SOLO_PARCIALES("solo_parciales", "serie entera de parciales del 35-45 % que salen del reposo (cuarto de sentadilla)"),
    REST_PAUSE("rest_pause", "tres mini-series separadas por 8-14 s; la mitad acaba con parciales"),
    FALLIDA_FINAL("fallida_final", "completas y una fallida al final (la concéntrica se estanca)"),
    FATIGA("fatiga", "pérdida gradual de recorrido del 100 % al 75 %: todas deben seguir siendo completas"),
    REPETICION_PROFUNDA("repeticion_profunda", "repeticiones del 72-80 % con una o dos al 100 % (pueden inflar la amplitud completa)"),
    CICLO_CORTADO_BORDE("ciclo_cortado_borde", "el vídeo empieza o acaba en mitad de una repetición"),
    SINGLES_DOBLES("singles_dobles", "una completa; dos completas; dos completas y una parcial"),
    PARCIALES_CERCA_UMBRAL("parciales_cerca_umbral", "completas y 1 a 3 parciales del 55-69 %, cerca del umbral de 70 %"),
    COMPLETAS_CERCA_UMBRAL("completas_cerca_umbral", "completas del 71-78 % junto a otras del 100 %: deben seguir siendo completas"),
}

internal enum class PartialsNoise(val label: String) {
    BAJO("bajo"), MEDIO("medio"), ALTO("alto");

    fun noise(fps: Double, gapWindow: Pair<Double, Double>?): SynthNoise {
        val sixty = fps >= 50.0
        val base = when (this) {
            BAJO -> SynthNoise(sigmaFh = 0.002, outlierRate = 0.005, dropRate = 0.0, ptsJitterMs = if (sixty) 1.0 else 2.0, worldSigmaM = 0.01)
            MEDIO -> SynthNoise(sigmaFh = 0.004, outlierRate = 0.01, dropRate = 0.01, ptsJitterMs = if (sixty) 1.0 else 3.0)
            ALTO -> SynthNoise(sigmaFh = 0.008, outlierRate = 0.02, dropRate = 0.03, ptsJitterMs = if (sixty) 1.0 else 3.0, worldSigmaM = 0.03)
        }
        return if (gapWindow == null) base else base.copy(
            occlusionWindowsS = listOf(gapWindow),
            occludedLandmarks = PoseLandmark.KEPT_IMAGE.toSet(),
        )
    }
}

internal data class PartialsSeriesSpec(
    val id: String,
    val scenario: PartialsScenario,
    val profile: PartialsProfile,
    val view: PartialsView,
    val fps: Double,
    val noise: PartialsNoise,
    val gap: Boolean,
    val seed: Long,
    val variant: Int,
)

internal enum class CutKind { START, END }

/** Etiquetas de una serie para agrupar en el informe (clave de dimensión → valor) y campos propios para su JSON. */
internal class SeriesMeta(val id: String, val dims: Map<String, String>, val extra: JsonObject = JsonObject(emptyMap()))

internal fun PartialsSeriesSpec.supportedView(): Boolean = view.kind in profile.profile.countViews

internal fun PartialsSeriesSpec.meta() = SeriesMeta(
    id,
    linkedMapOf(
        "escenario" to scenario.label, "perfil" to profile.label, "vista" to view.label, "fps" to fps.toInt().toString(),
        "ruido" to noise.label, "hueco" to (if (gap) "con_hueco" else "sin_hueco"),
        "arranque" to (if (profile.startsEccentric) "excentrica_primero" else "concentrica_primero"),
        "vistaSoportada" to (if (supportedView()) "soportada" else "no_soportada"),
    ),
    buildJsonObject {
        put("movimiento", profile.movement.name); put("perfilMotor", profile.profileId.name); put("hueco", gap)
        put("semilla", seed); put("variante", variant)
    },
)

internal class ScriptBuild(val items: List<PItem>, val cut: CutKind?)

/** Los guiones de cada escenario. */
internal object PartialsScripts {
    private fun u(rng: Random, lo: Double, hi: Double) = lo + (hi - lo) * rng.nextDouble()

    private fun rep(rng: Random, ecc: Boolean, hi: Double, lo: Double, scale: Double = 1.0, failed: Boolean = false): PItem.Rep {
        val out = (if (ecc) u(rng, 1.4, 2.4) else u(rng, 1.0, 1.8)) * scale
        val ret = (if (ecc) u(rng, 1.0, 1.8) else u(rng, 1.4, 2.4)) * scale
        return PItem.Rep(PRepSpec(hi, lo, out, u(rng, 0.2, 0.7), ret, u(rng, 0.2, 0.7), failed))
    }

    private fun full(rng: Random, ecc: Boolean, hi: Double = 1.0 - 0.07 * rng.nextDouble()) = rep(rng, ecc, hi, 0.0)
    private fun scale(f: Double) = 0.55 + 0.45 * f

    /** Parcial que sale del reposo y no llega al giro: techo f, valle 0. */
    private fun restPartial(rng: Random, ecc: Boolean, f: Double) = rep(rng, ecc, f, 0.0, scale(f))

    /** Parcial pegado al giro: techo ≈ 1, valle 1 − f (encadenado con el siguiente). */
    private fun turnPartial(rng: Random, ecc: Boolean, f: Double): PItem.Rep {
        val hi = 0.97 + 0.03 * rng.nextDouble()
        return rep(rng, ecc, hi, hi - f, scale(f))
    }

    fun build(spec: PartialsSeriesSpec): ScriptBuild {
        val ecc = spec.profile.startsEccentric
        val rng = Random(spec.seed)
        val mix = spec.variant + spec.profile.ordinal + spec.view.ordinal
        val items = ArrayList<PItem>()
        items.add(PItem.Idle(u(rng, 1.5, 2.5)))
        var tailAtLo = false
        var cut: CutKind? = null
        fun fulls(n: Int) = repeat(n) { items.add(full(rng, ecc)) }
        fun between(lo: Int, hi: Int) = lo + rng.nextInt(hi - lo + 1)

        when (spec.scenario) {
            PartialsScenario.COMPLETAS -> fulls(between(3, 8))
            PartialsScenario.PARCIALES_ESTIRAMIENTO_FINAL -> {
                fulls(between(4, 7))
                repeat(between(1, 3)) { items.add(turnPartial(rng, ecc, u(rng, 0.35, 0.50))) }
                tailAtLo = true
            }
            PartialsScenario.PARCIALES_ACORTADAS_FINAL -> {
                fulls(between(4, 7))
                repeat(between(1, 3)) { items.add(restPartial(rng, ecc, u(rng, 0.35, 0.50))) }
            }
            PartialsScenario.PARCIALES_INTERCALADAS -> {
                val total = between(6, 9)
                var partials = 0
                var i = 0
                while (i < total) {
                    val r = rng.nextDouble()
                    when {
                        i < 2 -> items.add(full(rng, ecc))
                        i == total - 1 && partials == 0 -> { items.add(restPartial(rng, ecc, u(rng, 0.35, 0.62))); partials++ }
                        r < 0.55 -> items.add(full(rng, ecc))
                        r < 0.85 -> { items.add(restPartial(rng, ecc, u(rng, 0.35, 0.62))); partials++ }
                        else -> {
                            items.add(turnPartial(rng, ecc, u(rng, 0.35, 0.55)))
                            items.add(turnPartial(rng, ecc, u(rng, 0.35, 0.55)))
                            items.add(PItem.Recover(1.0))
                            partials += 2
                            i++
                        }
                    }
                    i++
                }
            }
            PartialsScenario.SOLO_PARCIALES -> repeat(between(4, 6)) { items.add(restPartial(rng, ecc, u(rng, 0.35, 0.45))) }
            PartialsScenario.REST_PAUSE -> {
                val sizes = listOf(between(3, 4), between(2, 3), between(1, 2))
                for ((m, size) in sizes.withIndex()) {
                    if (m > 0) items.add(PItem.Idle(u(rng, 8.0, 14.0)))
                    fulls(size)
                }
                if (mix % 2 == 1) repeat(between(1, 2)) { items.add(restPartial(rng, ecc, u(rng, 0.35, 0.50))) }
            }
            PartialsScenario.FALLIDA_FINAL -> {
                fulls(between(3, 6))
                items.add(rep(rng, ecc, 1.0, 0.0, failed = true))
            }
            PartialsScenario.FATIGA -> {
                val n = between(6, 10)
                for (k in 0 until n) {
                    val hi = (1.0 - 0.25 * k / (n - 1) + u(rng, -0.015, 0.015)).coerceIn(0.73, 1.0)
                    items.add(rep(rng, ecc, hi, 0.0))
                }
            }
            PartialsScenario.REPETICION_PROFUNDA -> {
                val n = between(5, 7)
                val deep = HashSet<Int>()
                repeat(between(1, 2)) { deep.add(1 + rng.nextInt(n - 2)) }
                for (k in 0 until n) items.add(rep(rng, ecc, if (k in deep) 1.0 else u(rng, 0.72, 0.80), 0.0))
            }
            PartialsScenario.CICLO_CORTADO_BORDE -> {
                fulls(between(4, 6))
                cut = if (mix % 2 == 0) CutKind.END else CutKind.START
            }
            PartialsScenario.SINGLES_DOBLES -> when (mix % 3) {
                0 -> fulls(1)
                1 -> fulls(2)
                else -> { fulls(2); items.add(restPartial(rng, ecc, u(rng, 0.40, 0.50))) }
            }
            PartialsScenario.PARCIALES_CERCA_UMBRAL -> {
                fulls(between(4, 6))
                val k = between(1, 3)
                if (mix % 2 == 0) {
                    repeat(k) { items.add(restPartial(rng, ecc, u(rng, 0.55, 0.69))) }
                } else {
                    repeat(k) { items.add(turnPartial(rng, ecc, u(rng, 0.55, 0.69))) }
                    tailAtLo = true
                }
            }
            PartialsScenario.COMPLETAS_CERCA_UMBRAL -> {
                fulls(between(2, 3))
                repeat(between(2, 4)) { items.add(rep(rng, ecc, u(rng, 0.71, 0.78), 0.0)) }
            }
        }
        items.add(if (tailAtLo) PItem.Tail(2.0) else PItem.Idle(2.0))
        return ScriptBuild(items, cut)
    }
}

internal object PartialsPlan {
    const val SEED = 20261021L
    private val COMBOS = listOf(
        30.0 to PartialsNoise.BAJO, 30.0 to PartialsNoise.MEDIO, 30.0 to PartialsNoise.ALTO,
        60.0 to PartialsNoise.BAJO, 60.0 to PartialsNoise.MEDIO, 60.0 to PartialsNoise.ALTO,
    )

    /** Todas las series: 13 escenarios × 7 perfiles × 3 vistas × [variants], con fps y ruido rotando de forma equilibrada. */
    fun plan(variants: Int): List<PartialsSeriesSpec> {
        val out = ArrayList<PartialsSeriesSpec>()
        var i = 0
        for (scenario in PartialsScenario.entries) for (profile in PartialsProfile.entries) for (view in PartialsView.entries) {
            for (v in 0 until variants) {
                val (fps, noise) = COMBOS[i % COMBOS.size]
                val gap = (i / COMBOS.size) % 3 == 0
                val id = String.format(Locale.ROOT, "%03d_%s_%s_%s_%d_%s_%d", i, scenario.label, profile.label, view.label, fps.toInt(), noise.label, v)
                out.add(PartialsSeriesSpec(id, scenario, profile, view, fps, noise, gap, SEED + 7919L * (i + 1), v))
                i++
            }
        }
        return out
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Emparejamiento verdad ↔ motor
// ------------------------------------------------------------------------------------------------------------------------------

internal object PartialsMatching {
    /** Tolerancia alrededor de [departUs, arriveUs] de cada repetición de la verdad. */
    const val TOLERANCE_US = 400_000L

    /** Para cada repetición del motor (por el centro de su giro): el índice de la verdad que le toca, o −1 (sobrante). Uno a uno. */
    fun match(truth: List<PTruthRep>, engineCentersUs: List<Long>): IntArray {
        val out = IntArray(engineCentersUs.size) { -1 }
        class Cand(val e: Int, val t: Int, val d: Long)
        val cands = ArrayList<Cand>()
        for ((e, c) in engineCentersUs.withIndex()) for ((t, r) in truth.withIndex()) {
            if (c >= r.departUs - TOLERANCE_US && c <= r.arriveUs + TOLERANCE_US) cands.add(Cand(e, t, abs(c - r.turnCenterUs)))
        }
        cands.sortBy { it.d }
        val usedTruth = BooleanArray(truth.size)
        for (c in cands) {
            if (out[c.e] != -1 || usedTruth[c.t]) continue
            out[c.e] = c.t
            usedTruth[c.t] = true
        }
        return out
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Resultado de una serie
// ------------------------------------------------------------------------------------------------------------------------------

internal class EngineRep(
    val kind: String,
    val partialKind: String?,
    val amplitude: Double,
    val departUs: Long,
    val turnCenterUs: Long,
    val arriveUs: Long,
)

internal class EngineOut(
    val hasOutput: Boolean,
    val level: String,
    val confidenceScore: Double,
    val full: Int,
    val partial: Int,
    val failed: Int,
    val reps: List<EngineRep>,
    val view: String?,
    val analyzedFps: Double?,
    val noRepsReason: String?,
    val flags: List<String>,
    val error: String? = null,
) {
    val offered: Boolean get() = hasOutput && (level == "ALTA" || level == "MEDIA")

    companion object {
        fun of(report: SetTechniqueReport): EngineOut {
            val reps = report.reps.map {
                EngineRep(it.kind.name, it.partialKind?.name, it.amplitudeFraction, it.phases.departUs,
                    (it.phases.turnStartUs + it.phases.turnEndUs) / 2, it.phases.arriveUs)
            }
            val has = MetricKey.REPS in report.available
            return EngineOut(has, if (has) report.confidence.name else "SIN_SALIDA", report.confidenceScore, report.repCount,
                report.partialReps, report.failedReps, reps, report.view.name, report.analyzedFps, report.noRepsReason,
                report.flags.map { it.name }.sorted())
        }

        fun failure(message: String) = EngineOut(false, "SIN_SALIDA", 0.0, 0, 0, 0, emptyList(), null, null, null, emptyList(), message)
    }
}

internal class PartialsOutcome(
    val meta: SeriesMeta,
    val truthAll: List<PTruthRep>,
    /** Las repeticiones de la verdad que cuentan (visibles en el vídeo recortado). */
    val truth: List<PTruthRep>,
    val cutWindowS: Pair<Double, Double>?,
    val engine: EngineOut,
    val engineToTruth: IntArray,
    /** Repeticiones sobrantes del motor a menos de 1 s del borde recortado (no cuentan como fantasmas). */
    val borderExtra: BooleanArray,
    val frames: Int,
    val elapsedMs: Long,
) {
    val truthFull = truth.count { it.kind == TruthKind.FULL }
    val truthPartial = truth.count { it.kind == TruthKind.PARTIAL }
    val truthFailed = truth.count { it.kind == TruthKind.FAILED }
    val fullExact get() = engine.hasOutput && engine.full == truthFull
    val partialExact get() = engine.hasOutput && engine.partial == truthPartial
    val failedExact get() = engine.hasOutput && engine.failed == truthFailed
    val seriesExact get() = fullExact && partialExact && failedExact
    val truthToEngine: IntArray by lazy {
        IntArray(truth.size) { -1 }.also { arr -> engineToTruth.forEachIndexed { e, t -> if (t >= 0) arr[t] = e } }
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Ejecución
// ------------------------------------------------------------------------------------------------------------------------------

internal object PartialsRunner {
    private const val BORDER_US = 1_000_000L

    fun run(spec: PartialsSeriesSpec): PartialsOutcome {
        val started = System.nanoTime()
        val profile = spec.profile
        val ecc = profile.startsEccentric
        val cal = PartialsCalibration.of(profile, spec.view)
        val script = PartialsScripts.build(spec)
        val built = PartialsTimelineBuilder.build(script.items, ecc, cal, profile.movement)
        val rng = Random(spec.seed xor 0x5DEECE66DL)

        // Ventana del vídeo recortado.
        val all = built.reps
        var t0 = 0.0
        var t1 = built.totalSeconds
        when (script.cut) {
            CutKind.START -> all.firstOrNull()?.let { r ->
                t0 = (r.departUs + (0.25 + 0.55 * rng.nextDouble()) * (r.arriveUs - r.departUs)) / 1e6
            }
            CutKind.END -> all.lastOrNull()?.let { r ->
                t1 = (r.departUs + (0.40 + 0.50 * rng.nextDouble()) * (r.arriveUs - r.departUs)) / 1e6
            }
            null -> Unit
        }
        val cut = script.cut != null
        val visible = all.map { r -> if (cut) applyCut(r, t0, t1, ecc) else r }.filter { it.visible }.mapIndexed { i, r -> r.copy(index = i) }

        // Hueco de puntos: 0,45 s con todos los puntos de mala calidad, dentro de lo que se ve.
        val gap: Pair<Double, Double>? = if (spec.gap && t1 - t0 > 5.0) {
            val a = t0 + 1.0 + (t1 - t0 - 3.0) * rng.nextDouble()
            a to a + 0.45
        } else null
        val config = SynthConfig(
            movement = profile.movement, camera = spec.view.camera, fps = spec.fps, script = emptyList<SynthSegment>(),
            noise = spec.noise.noise(spec.fps, gap), seed = spec.seed,
        )
        val generated = PartialsLifter.generate(config, built.timeline)
        val track: PoseTrackV2 = generated.track.let { tr ->
            if (!cut) tr else tr.copy(frames = tr.frames.filter { it.tUs >= (t0 * 1e6).toLong() && it.tUs <= (t1 * 1e6).toLong() })
        }
        val engine = try {
            val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(track, null, profile.profile, null, null, emptySet()))
            require(report.repCount == report.reps.count { it.kind == RepKind.FULL })
            require(report.partialReps == report.reps.count { it.kind == RepKind.PARTIAL })
            require(report.failedReps == report.reps.count { it.kind == RepKind.FAILED })
            EngineOut.of(report)
        } catch (e: Throwable) {
            EngineOut.failure(e.javaClass.simpleName + ": " + e.message)
        }
        val matches = PartialsMatching.match(visible, engine.reps.map { it.turnCenterUs })
        val border = BooleanArray(engine.reps.size) { e ->
            val c = engine.reps[e].turnCenterUs
            cut && matches[e] < 0 && (c - (t0 * 1e6).toLong() <= BORDER_US || (t1 * 1e6).toLong() - c <= BORDER_US)
        }
        return PartialsOutcome(spec.meta(), all, visible, if (cut) t0 to t1 else null, engine, matches, border, track.frames.size,
            (System.nanoTime() - started) / 1_000_000)
    }

    /**
     * Qué queda de una repetición en un vídeo recortado a [t0, t1]: entera → cuenta; si el vídeo empieza después de su salida,
     * o acaba antes de verse el giro, o (con arranque E) antes de volver, no es una repetición que se pueda contar. Con arranque
     * C basta ver el giro sostenido 0,3 s: cuenta como lo que subió.
     */
    fun applyCut(r: PTruthRep, t0: Double, t1: Double, ecc: Boolean): PTruthRep {
        val startUs = (t0 * 1e6).toLong()
        val endUs = (t1 * 1e6).toLong()
        if (r.departUs < startUs) return r.copy(visible = false)
        if (r.arriveUs <= endUs) return r
        if (r.kind == TruthKind.FAILED) return r.copy(visible = false)
        if (ecc) return r.copy(visible = false)
        if (r.turnStartUs + 300_000L > endUs) return r.copy(visible = false)
        val up = r.upFraction
        return when {
            up >= 0.70 -> r.copy(kind = TruthKind.FULL, zone = PartialsZone.FULL_RANGE, muscleZone = "COMPLETA", fraction = up, downFraction = Double.NaN)
            up >= 0.30 -> r.copy(kind = TruthKind.PARTIAL, fraction = up, downFraction = Double.NaN)
            else -> r.copy(visible = false)
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Agregación
// ------------------------------------------------------------------------------------------------------------------------------

internal class Tally {
    var n = 0
    var withOutput = 0
    var fullExact = 0
    var partialExact = 0
    var failedExact = 0
    var seriesExact = 0
    var fullExactOut = 0
    var seriesExactOut = 0
    var truthReps = 0
    var repsCorrect = 0

    /** verdad (FULL, PARTIAL, FAILED) × motor (FULL, PARTIAL, FAILED, OMITIDA). */
    val confusion = Array(3) { IntArray(4) }

    /** Repeticiones sobrantes del motor, por clase, y las que caen en el borde recortado. */
    val extra = IntArray(3)
    var borderExtra = 0
    var engineReps = 0

    var truthPartialSeries = 0
    var phantomSeriesDen = 0
    var phantomSeriesNum = 0
    var seriesPartialExact = 0

    /** Zona de la verdad de las parciales: total, acertadas como parcial, vistas como completa, omitidas, otra. */
    val zone = HashMap<String, IntArray>()
    var kindAgree = 0
    var kindTotal = 0

    val level = HashMap<String, IntArray>() // n, serieExacta, fullExact, partialExact, parcialesOk
    var offered = 0
    var offeredWrong = 0
    var errors = 0

    fun add(o: PartialsOutcome) {
        n++
        if (o.engine.error != null) errors++
        if (o.engine.hasOutput) withOutput++
        if (o.fullExact) fullExact++
        if (o.partialExact) partialExact++
        if (o.failedExact) failedExact++
        if (o.seriesExact) seriesExact++
        if (o.engine.hasOutput && o.fullExact) fullExactOut++
        if (o.engine.hasOutput && o.seriesExact) seriesExactOut++
        if (o.truthPartial > 0) truthPartialSeries++ else {
            phantomSeriesDen++
            if (o.engine.partial > 0) phantomSeriesNum++
        }
        if (o.truthPartial > 0 && o.partialExact) seriesPartialExact++
        truthReps += o.truth.size
        engineReps += o.engine.reps.size
        for ((t, r) in o.truth.withIndex()) {
            val e = o.truthToEngine[t]
            val col = if (e < 0) 3 else KINDS.indexOf(o.engine.reps[e].kind)
            confusion[r.kind.ordinal][col]++
            if (col == r.kind.ordinal) repsCorrect++
            if (r.kind == TruthKind.PARTIAL) {
                val z = zone.getOrPut(r.zone.name) { IntArray(5) }
                z[0]++
                when (col) {
                    1 -> {
                        z[1]++
                        val pk = o.engine.reps[e].partialKind
                        if (pk != null) {
                            kindTotal++
                            val expected = if (r.zone == PartialsZone.TURN_SIDE) "FAR_END" else "REST_END"
                            if (pk == expected) kindAgree++
                        }
                    }
                    0 -> z[2]++
                    3 -> z[3]++
                    else -> z[4]++
                }
            }
        }
        for ((e, rep) in o.engine.reps.withIndex()) {
            if (o.engineToTruth[e] >= 0) continue
            if (o.borderExtra[e]) borderExtra++ else extra[KINDS.indexOf(rep.kind)]++
        }
        val l = level.getOrPut(o.engine.level) { IntArray(5) }
        l[0]++
        if (o.seriesExact) l[1]++
        if (o.fullExact) l[2]++
        if (o.partialExact) l[3]++
        if (o.engine.offered) {
            offered++
            if (!o.seriesExact) offeredWrong++
        }
    }

    val partialTruthTotal get() = confusion[1].sum()
    val partialHits get() = confusion[1][1]
    val ghostExtra get() = extra[1]
    val fullAsPartial get() = confusion[0][1]
    val failedAsPartial get() = confusion[2][1]

    companion object {
        val KINDS = listOf("FULL", "PARTIAL", "FAILED")
    }
}

internal class DimDef(val key: String, val jsonName: String, val title: String)

internal class ReportDef(
    val title: String,
    val intro: String,
    val dims: List<DimDef>,
    val notes: List<String>,
    val methodText: String,
    val metaJson: JsonObject,
)

internal object PartialsReport {
    private val pretty = Json { prettyPrint = true }

    private fun tally(outs: List<PartialsOutcome>) = Tally().also { t -> outs.forEach { t.add(it) } }

    private fun prop(num: Int, den: Int): JsonObject = buildJsonObject {
        put("numerador", num); put("denominador", den)
        put("valor", if (den == 0) JsonNull else JsonPrimitive(num.toDouble() / den))
        val w = if (den == 0) null else FullBankReport.wilson(num, den)
        put("wilson95", if (w == null) JsonNull else JsonArray(listOf(JsonPrimitive(w.first), JsonPrimitive(w.second))))
    }

    private fun ratio(num: Int, den: Int): Double = if (den == 0) Double.NaN else num.toDouble() / den

    private fun num(x: Double): JsonElement = if (x.isNaN() || x.isInfinite()) JsonNull else JsonPrimitive(x)

    /** Precisión: de las parciales del motor, cuántas son parciales de la verdad. Exhaustividad: de las de la verdad, cuántas ve. */
    private fun partialsJson(t: Tally): JsonObject = buildJsonObject {
        put("verdad", t.partialTruthTotal)
        put("motor", t.confusion.sumOf { it[1] } + t.extra[1])
        put("aciertos", t.partialHits)
        put("precision", prop(t.partialHits, t.confusion.sumOf { it[1] } + t.extra[1]))
        put("exhaustividad", prop(t.partialHits, t.partialTruthTotal))
        put("fantasmas", buildJsonObject {
            put("sobrantes", t.ghostExtra)
            put("completasVistasComoParcial", t.fullAsPartial)
            put("fallidasVistasComoParcial", t.failedAsPartial)
            put("seriesSinParcialesConParcialesDelMotor", prop(t.phantomSeriesNum, t.phantomSeriesDen))
        })
        put("zona", buildJsonObject {
            for ((z, a) in t.zone.toSortedMap()) put(z, buildJsonObject {
                put("n", a[0]); put("comoParcial", a[1]); put("comoCompleta", a[2]); put("omitidas", a[3]); put("comoFallida", a[4])
            })
            put("partialKindCoincideConZona", prop(t.kindAgree, t.kindTotal))
        })
    }

    fun tallyJson(t: Tally): JsonObject = buildJsonObject {
        put("series", t.n); put("conSalida", t.withOutput); put("errores", t.errors)
        put("fullExacto", prop(t.fullExact, t.n))
        put("partialExacto", prop(t.partialExact, t.n))
        put("failedExacto", prop(t.failedExact, t.n))
        put("serieExacta", prop(t.seriesExact, t.n))
        put("fullExactoConSalida", prop(t.fullExactOut, t.withOutput))
        put("serieExactaConSalida", prop(t.seriesExactOut, t.withOutput))
        put("repeticionesVerdad", t.truthReps)
        put("repeticionesAcertadas", prop(t.repsCorrect, t.truthReps))
        put("matrizRepeticiones", buildJsonObject {
            for ((i, k) in Tally.KINDS.withIndex()) put(k, buildJsonObject {
                put("FULL", t.confusion[i][0]); put("PARTIAL", t.confusion[i][1]); put("FAILED", t.confusion[i][2]); put("OMITIDA", t.confusion[i][3])
            })
            put("SOBRANTE", buildJsonObject {
                put("FULL", t.extra[0]); put("PARTIAL", t.extra[1]); put("FAILED", t.extra[2]); put("enBordeRecortado", t.borderExtra)
            })
        })
        put("parciales", partialsJson(t))
        put("seriesConParcialesEnLaVerdad", t.truthPartialSeries)
        put("partialExactoEnSeriesConParciales", prop(t.seriesPartialExact, t.truthPartialSeries))
        put("confianza", buildJsonObject {
            for (lvl in listOf("ALTA", "MEDIA", "BAJA", "SIN_SALIDA")) {
                val a = t.level[lvl] ?: IntArray(5)
                put(lvl, buildJsonObject {
                    put("series", a[0]); put("serieExacta", prop(a[1], a[0])); put("fullExacto", prop(a[2], a[0])); put("partialExacto", prop(a[3], a[0]))
                })
            }
            put("ofrecida", prop(t.offered, t.n))
            put("ofrecidaConSerieErronea", prop(t.offeredWrong, t.offered))
        })
    }

    private fun compactJson(t: Tally): JsonObject = buildJsonObject {
        put("series", t.n)
        put("fullExacto", num(ratio(t.fullExact, t.n)))
        put("partialExacto", num(ratio(t.partialExact, t.n)))
        put("failedExacto", num(ratio(t.failedExact, t.n)))
        put("serieExacta", num(ratio(t.seriesExact, t.n)))
        put("sinSalida", num(ratio(t.n - t.withOutput, t.n)))
        put("repsAcertadas", num(ratio(t.repsCorrect, t.truthReps)))
        put("precisionParciales", num(ratio(t.partialHits, t.confusion.sumOf { it[1] } + t.extra[1])))
        put("exhaustividadParciales", num(ratio(t.partialHits, t.partialTruthTotal)))
        put("fantasmasSobrantes", t.ghostExtra)
        put("completasComoParcial", t.fullAsPartial)
        put("ofrecida", num(ratio(t.offered, t.n)))
        put("ofrecidaErronea", num(ratio(t.offeredWrong, t.offered)))
    }

    private fun groups(outs: List<PartialsOutcome>, key: (PartialsOutcome) -> String): Map<String, Tally> =
        outs.groupBy(key).toSortedMap().mapValues { tally(it.value) }

    /** Recuerdo de las parciales y falsas parciales de las completas por tramo de fracción de recorrido. */
    private fun bands(outs: List<PartialsOutcome>): JsonObject = buildJsonObject {
        data class Row(var n: Int = 0, var partial: Int = 0, var full: Int = 0, var omitted: Int = 0, var failed: Int = 0)

        fun bucket(kind: TruthKind, zone: String?, lo: Double, hi: Double): Row {
            val row = Row()
            for (o in outs) for ((t, r) in o.truth.withIndex()) {
                if (r.kind != kind || r.fraction < lo || r.fraction >= hi) continue
                if (zone != null && r.zone.name != zone) continue
                row.n++
                val e = o.truthToEngine[t]
                when {
                    e < 0 -> row.omitted++
                    o.engine.reps[e].kind == "PARTIAL" -> row.partial++
                    o.engine.reps[e].kind == "FULL" -> row.full++
                    else -> row.failed++
                }
            }
            return row
        }

        fun rowJson(r: Row) = buildJsonObject {
            put("n", r.n); put("comoParcial", r.partial); put("comoCompleta", r.full); put("omitidas", r.omitted); put("comoFallida", r.failed)
        }
        put("parciales", buildJsonObject {
            for ((lo, hi) in listOf(0.30 to 0.40, 0.40 to 0.50, 0.50 to 0.60, 0.60 to 0.70)) {
                for (zone in listOf(null, "TURN_SIDE", "REST_SIDE")) {
                    val key = String.format(Locale.ROOT, "%d-%d%%_%s", (lo * 100).toInt(), (hi * 100).toInt(), zone ?: "TODAS")
                    put(key, rowJson(bucket(TruthKind.PARTIAL, zone, lo, hi)))
                }
            }
        })
        put("completas", buildJsonObject {
            for ((lo, hi) in listOf(0.70 to 0.80, 0.80 to 0.90, 0.90 to 1.01)) {
                put(String.format(Locale.ROOT, "%d-%d%%", (lo * 100).toInt(), minOf(100, (hi * 100).toInt())), rowJson(bucket(TruthKind.FULL, null, lo, hi)))
            }
        })
    }

    private fun dim(o: PartialsOutcome, key: String): String = o.meta.dims[key] ?: "?"

    private fun supported(outs: List<PartialsOutcome>) = outs.filter { dim(it, "vistaSoportada") == "soportada" }

    fun summaryJson(outs: List<PartialsOutcome>, def: ReportDef, elapsedS: Double): JsonObject = buildJsonObject {
        put("generado", Instant.now().toString())
        put("engineVersion", TechniqueAnalysisEngine.ENGINE_VERSION)
        put("fase", def.title)
        for ((k, v) in def.metaJson) put(k, v)
        put("series", outs.size)
        put("segundos", elapsedS)
        put("global", tallyJson(tally(outs)))
        put("globalVistasSoportadas", tallyJson(tally(supported(outs))))
        for (d in def.dims) put(d.jsonName, buildJsonObject { for ((k, t) in groups(outs) { dim(it, d.key) }) put(k, compactJson(t)) })
        put("porEscenarioDetalle", buildJsonObject { for ((k, t) in groups(outs) { dim(it, "escenario") }) put(k, tallyJson(t)) })
        put("bandasDeFraccion", bands(outs))
        val withTruthPartials = outs.filter { it.truthPartial > 0 }
        val without = outs.filter { it.truthPartial == 0 }
        put("seriesConParcialesEnLaVerdad", tallyJson(tally(withTruthPartials)))
        put("seriesSinParcialesEnLaVerdad", tallyJson(tally(without)))
        put("peoresEscenarios", JsonArray(worst(outs).map { (k, t) -> buildJsonObject { put("escenario", k); put("detalle", compactJson(t)) } }))
        put("notas", JsonArray((COMMON_NOTES + def.notes).map { JsonPrimitive(it) }))
    }

    /** Los cinco escenarios con peor serie exacta, solo con las series de vista soportada (si no hay, con todas). */
    private fun worst(outs: List<PartialsOutcome>): List<Pair<String, Tally>> =
        groups(supported(outs).ifEmpty { outs }) { dim(it, "escenario") }.entries.sortedBy { ratio(it.value.seriesExact, it.value.n) }.take(5).map { it.key to it.value }

    private val COMMON_NOTES = listOf(
        "Verdad por repetición: a = min(subida, bajada) en fracción de la señal de conteo limpia, relativa a una repetición completa de referencia (1,0). FULL si a >= 0,70, PARTIAL si 0,30 <= a < 0,70, FAILED si la concentrica se estanca.",
        "La fracción de recorrido es de SEÑAL (la que mide el motor), calibrada por perfil y vista; la fracción en el ÁNGULO principal va aparte en cada serie (angleFraction).",
        "Zona de una parcial: TURN_SIDE si su techo llega al 85 % o más de la señal (cerca del giro, sin volver al reposo), REST_SIDE si sale del reposo y no lo alcanza. Estiramiento/acortada depende del arranque (E: giro = estiramiento; C: reposo = estiramiento).",
        "Los parciales pegados al giro al final del vídeo acaban con el atleta parado en el valle (sin vuelta al reposo): si volviera al reposo, su bajada sería de 100 % y el motor vería una completa.",
        "Una serie hecha solo de parciales no tiene referencia de repetición completa dentro del vídeo: el motor la normaliza contra sí misma.",
        "«Vista soportada»: la vista de la cámara está entre las `countViews` del perfil del motor; en las demás el motor no promete contar, y sus fallos no son un defecto del conteo de parciales.",
        "Emparejamiento: un giro del motor corresponde a la repetición de la verdad más cercana cuyo intervalo salida-llegada (± 0,4 s) lo contiene; los giros sin pareja son sobrantes (a menos de 1 s del borde de un vídeo recortado se cuentan aparte).",
    )

    // --- Markdown -----------------------------------------------------------------------------------------------------------

    private fun pct(x: Double): String = if (x.isNaN()) "—" else String.format(Locale.ROOT, "%.1f %%", 100 * x).replace('.', ',')
    private fun pctOf(num: Int, den: Int) = pct(ratio(num, den))

    private fun table(headers: List<String>, rows: List<List<String>>): String = buildString {
        append("| ").append(headers.joinToString(" | ")).append(" |\n")
        append("|").append(headers.joinToString("|") { "---" }).append("|\n")
        for (r in rows) append("| ").append(r.joinToString(" | ")).append(" |\n")
    }

    private fun compactRow(label: String, t: Tally): List<String> = listOf(
        label, t.n.toString(), pctOf(t.fullExact, t.n), pctOf(t.partialExact, t.n), pctOf(t.failedExact, t.n), pctOf(t.seriesExact, t.n),
        pctOf(t.repsCorrect, t.truthReps), pctOf(t.partialHits, t.confusion.sumOf { it[1] } + t.extra[1]), pctOf(t.partialHits, t.partialTruthTotal),
        t.ghostExtra.toString(), t.fullAsPartial.toString(), pctOf(t.n - t.withOutput, t.n),
    )

    private val COMPACT_HEADERS = listOf(
        "grupo", "series", "FULL exacto", "PARTIAL exacto", "FAILED exacto", "serie exacta", "reps acertadas",
        "precisión parciales", "exhaustividad parciales", "fantasmas", "completa→parcial", "sin salida",
    )

    fun markdown(outs: List<PartialsOutcome>, def: ReportDef, elapsedS: Double): String = buildString {
        val all = tally(outs)
        append("# ").append(def.title).append("\n\n")
        append("Motor: `TechniqueAnalysisEngine` versión ${TechniqueAnalysisEngine.ENGINE_VERSION}, ${outs.size} series, ")
        append("${String.format(Locale.ROOT, "%.0f", elapsedS)} s de corrida.\n\n")
        append(def.intro).append("\n\n")
        append("Cifras sobre TODAS las series, con «sin salida» contado como fallo, salvo donde se diga lo contrario.\n\n")

        append("## 1. Cifras globales\n\n")
        append(table(listOf("medida", "valor"), listOf(
            listOf("series", all.n.toString()),
            listOf("series con salida del motor", pctOf(all.withOutput, all.n)),
            listOf("FULL exacto (nº de completas)", pctOf(all.fullExact, all.n)),
            listOf("PARTIAL exacto (nº de parciales)", pctOf(all.partialExact, all.n)),
            listOf("FAILED exacto (nº de fallidas)", pctOf(all.failedExact, all.n)),
            listOf("serie entera exacta (las tres)", pctOf(all.seriesExact, all.n)),
            listOf("FULL exacto, solo con salida", pctOf(all.fullExactOut, all.withOutput)),
            listOf("serie exacta, solo con salida", pctOf(all.seriesExactOut, all.withOutput)),
            listOf("repeticiones de la verdad bien clasificadas", pctOf(all.repsCorrect, all.truthReps)),
            listOf("errores del motor (excepciones)", all.errors.toString()),
        )))
        append("\n")
        append("### Según la vista sea una de las que el perfil soporta\n\n")
        val sup = outs.filter { dim(it, "vistaSoportada") == "soportada" }
        val nosup = outs.filter { dim(it, "vistaSoportada") != "soportada" }
        append(table(COMPACT_HEADERS, listOf(compactRow("todas", all), compactRow("vista soportada", tally(sup)), compactRow("vista NO soportada", tally(nosup)))))
        append("\n")

        append("## 2. Matriz de confusión por repetición\n\n")
        append("Filas: verdad. Columnas: lo que dice el motor para la repetición emparejada; «omitida» si el motor no vio ninguna.\n\n")
        val matrixRows = Tally.KINDS.mapIndexed { i, k ->
            val total = all.confusion[i].sum()
            listOf(k, total.toString()) + (0..3).map { j -> "${all.confusion[i][j]} (${pctOf(all.confusion[i][j], total)})" }
        } + listOf(listOf("sobrante (sin pareja)", (all.extra.sum() + all.borderExtra).toString(),
            "${all.extra[0]}", "${all.extra[1]}", "${all.extra[2]}", "borde recortado: ${all.borderExtra}"))
        append(table(listOf("verdad \\ motor", "n", "FULL", "PARTIAL", "FAILED", "omitida"), matrixRows))
        append("\n")

        append("## 3. Parciales\n\n")
        val engineP = all.confusion.sumOf { it[1] } + all.extra[1]
        append(table(listOf("medida", "valor"), listOf(
            listOf("parciales en la verdad", all.partialTruthTotal.toString()),
            listOf("parciales del motor", engineP.toString()),
            listOf("aciertos (parcial con parcial)", all.partialHits.toString()),
            listOf("precisión", pctOf(all.partialHits, engineP)),
            listOf("exhaustividad", pctOf(all.partialHits, all.partialTruthTotal)),
            listOf("fantasmas: parciales del motor sin repetición en la verdad", all.ghostExtra.toString()),
            listOf("fantasmas: completas de la verdad vistas como parcial", all.fullAsPartial.toString()),
            listOf("fantasmas: fallidas vistas como parcial", all.failedAsPartial.toString()),
            listOf("series sin parciales en la verdad donde el motor da alguna", "${all.phantomSeriesNum} de ${all.phantomSeriesDen} (${pctOf(all.phantomSeriesNum, all.phantomSeriesDen)})"),
            listOf("`partialKind` coincide con la zona (solo parcial con parcial)", pctOf(all.kindAgree, all.kindTotal)),
        )))
        append("\n### Por zona de la verdad\n\n")
        append(table(listOf("zona", "n", "como parcial", "como completa", "omitidas", "como fallida"),
            all.zone.toSortedMap().map { (z, a) -> listOf(z, a[0].toString(), pctOf(a[1], a[0]), pctOf(a[2], a[0]), pctOf(a[3], a[0]), pctOf(a[4], a[0])) }))
        append("\nLa zona no existe hoy en el informe del motor (solo `partialKind`); queda anotada en la verdad de cada serie (`zone`, `muscleZone`).\n\n")

        append("## 4. Por escenario\n\n")
        append(table(COMPACT_HEADERS, groups(outs) { dim(it, "escenario") }.map { (k, t) -> compactRow(k, t) }))
        append("\n")
        append("### Los cinco escenarios donde peor sale la serie exacta\n\n")
        append(table(COMPACT_HEADERS, worst(outs).map { (k, t) -> compactRow(k, t) }))
        append("\n")
        var section = 5
        for (d in def.dims) {
            append("## ${section++}. ${d.title}\n\n")
            append(table(COMPACT_HEADERS, groups(outs) { dim(it, d.key) }.map { (k, t) -> compactRow(k, t) }))
            append("\n")
        }

        append("## ${section++}. Confianza frente a acierto\n\n")
        append(table(listOf("nivel", "series", "serie exacta", "FULL exacto", "PARTIAL exacto"),
            listOf("ALTA", "MEDIA", "BAJA", "SIN_SALIDA").map { lvl ->
                val a = all.level[lvl] ?: IntArray(5)
                listOf(lvl, a[0].toString(), pctOf(a[1], a[0]), pctOf(a[2], a[0]), pctOf(a[3], a[0]))
            }))
        append("\nOfrecidas (ALTA o MEDIA): ${pctOf(all.offered, all.n)} de las series; de ellas, con la serie mal contada: ${pctOf(all.offeredWrong, all.offered)}.\n\n")

        append("## ${section++}. Parciales por tramo de recorrido y zona\n\n")
        append("Recorrido de la parcial = a de la verdad (fracción de la señal). Se cuentan solo las repeticiones de la verdad.\n\n")
        val partialRows = ArrayList<List<String>>()
        for ((lo, hi) in listOf(0.30 to 0.40, 0.40 to 0.50, 0.50 to 0.60, 0.60 to 0.70)) {
            for (zone in listOf(null, "TURN_SIDE", "REST_SIDE")) {
                var n = 0; var asPartial = 0; var asFull = 0; var omitted = 0; var asFailed = 0
                for (o in outs) for ((t, r) in o.truth.withIndex()) {
                    if (r.kind != TruthKind.PARTIAL || r.fraction < lo || r.fraction >= hi) continue
                    if (zone != null && r.zone.name != zone) continue
                    n++
                    val e = o.truthToEngine[t]
                    when {
                        e < 0 -> omitted++
                        o.engine.reps[e].kind == "PARTIAL" -> asPartial++
                        o.engine.reps[e].kind == "FULL" -> asFull++
                        else -> asFailed++
                    }
                }
                partialRows.add(listOf("${(lo * 100).toInt()}-${(hi * 100).toInt()} %", zone ?: "todas", n.toString(), pctOf(asPartial, n), pctOf(asFull, n), pctOf(omitted, n), pctOf(asFailed, n)))
            }
        }
        append(table(listOf("recorrido", "zona", "n", "como parcial", "como completa", "omitidas", "como fallida"), partialRows))
        append("\n")
        val fullRows = ArrayList<List<String>>()
        for ((lo, hi) in listOf(0.70 to 0.80, 0.80 to 0.90, 0.90 to 1.01)) {
            var n = 0; var asPartial = 0; var asFull = 0; var omitted = 0; var asFailed = 0
            for (o in outs) for ((t, r) in o.truth.withIndex()) {
                if (r.kind != TruthKind.FULL || r.fraction < lo || r.fraction >= hi) continue
                n++
                val e = o.truthToEngine[t]
                when {
                    e < 0 -> omitted++
                    o.engine.reps[e].kind == "PARTIAL" -> asPartial++
                    o.engine.reps[e].kind == "FULL" -> asFull++
                    else -> asFailed++
                }
            }
            fullRows.add(listOf("${(lo * 100).toInt()}-${minOf(100, (hi * 100).toInt())} %", n.toString(), pctOf(asFull, n), pctOf(asPartial, n), pctOf(omitted, n), pctOf(asFailed, n)))
        }
        append("Completas de la verdad por tramo de recorrido:\n\n")
        append(table(listOf("recorrido", "n", "como completa", "como parcial", "omitidas", "como fallida"), fullRows))
        append("\n")

        append("## ${section}. Método y límites\n\n")
        for (n in COMMON_NOTES + def.notes) append("- ").append(n).append('\n')
        append(def.methodText)
    }

    fun synthDef(variants: Int) = ReportDef(
        title = "Banco sintético de repeticiones parciales (fase 1)",
        intro = "${PartialsPlan.plan(variants).size} series (13 escenarios × 7 perfiles × 3 vistas × $variants variantes, semilla ${PartialsPlan.SEED}). " +
            "Cada serie sale del generador sintético con la verdad por repetición (tipo, zona, fracción de recorrido) y se pasa por el motor como el evaluador del banco real.",
        dims = listOf(
            DimDef("perfil", "porPerfil", "Por perfil"), DimDef("vista", "porVista", "Por vista"), DimDef("fps", "porFps", "Por cadencia (fps)"),
            DimDef("ruido", "porRuido", "Por nivel de ruido"), DimDef("hueco", "porHueco", "Con y sin hueco de puntos"),
            DimDef("arranque", "porArranque", "Por arranque del movimiento (excéntrica primero: sentadilla, banca, rumano; concéntrica primero: curl, jalón, press militar, máquina)"),
            DimDef("vistaSoportada", "porVistaSoportada", "Por vista soportada o no por el perfil"),
        ),
        notes = listOf(
            "Escenarios: " + PartialsScenario.entries.joinToString("; ") { "`${it.label}` (${it.description})" } + ".",
            "Perfiles: " + PartialsProfile.entries.joinToString(", ") { "${it.label} (${it.movement.name} → ${it.profileId.name})" } +
                ". Vistas: lateral ψ = 90°, frontal 0°, oblicua 45°. Ruido: bajo (σ 0,002 FH), medio (0,004), alto (0,008, 3 % de fotogramas perdidos); hueco: 0,45 s con todos los puntos de mala calidad en un tercio de las series.",
        ),
        methodText = "",
        metaJson = buildJsonObject { put("semilla", PartialsPlan.SEED); put("variantes", variants) },
    )

    fun write(outDir: File, outs: List<PartialsOutcome>, def: ReportDef, elapsedS: Double) {
        outDir.mkdirs()
        File(outDir, "summary.json").writeText(pretty.encodeToString(JsonElement.serializer(), summaryJson(outs, def, elapsedS)), Charsets.UTF_8)
        File(outDir, "RESULTADOS.md").writeText(markdown(outs, def, elapsedS), Charsets.UTF_8)
        val seriesDir = File(outDir, "series")
        seriesDir.mkdirs()
        for (o in outs) File(seriesDir, o.meta.id + ".json").writeText(seriesJson(o).toString(), Charsets.UTF_8)
    }

    fun seriesJson(o: PartialsOutcome): JsonObject = buildJsonObject {
        put("id", o.meta.id)
        put("etiquetas", buildJsonObject { for ((k, v) in o.meta.dims) put(k, v) })
        for ((k, v) in o.meta.extra) put(k, v)
        put("fotogramas", o.frames)
        put("recorteS", o.cutWindowS?.let { JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second))) } ?: JsonNull)
        put("verdad", buildJsonObject {
            put("full", o.truthFull); put("partial", o.truthPartial); put("failed", o.truthFailed)
            put("repeticiones", buildJsonArray {
                for (r in o.truthAll) add(buildJsonObject {
                    put("indice", r.index); put("visible", r.visible); put("tipo", r.kind.name); put("zona", r.zone.name)
                    put("zonaMuscular", r.muscleZone); put("techo", num(r.hi)); put("vallePrevio", r.valleyBefore); put("valleSiguiente", r.valleyAfter)
                    put("subida", r.upFraction); put("bajada", num(r.downFraction)); put("fraccionDeRecorrido", r.fraction)
                    put("fraccionEnAngulo", num(r.angleFraction))
                    put("salidaUs", r.departUs); put("giroInicioUs", r.turnStartUs); put("giroFinUs", r.turnEndUs); put("llegadaUs", r.arriveUs)
                })
            })
        })
        put("motor", buildJsonObject {
            val e = o.engine
            put("conSalida", e.hasOutput); put("confianza", e.level); put("puntuacion", e.confidenceScore)
            put("full", e.full); put("partial", e.partial); put("failed", e.failed)
            put("vista", e.view?.let { JsonPrimitive(it) } ?: JsonNull)
            put("fpsAnalizados", e.analyzedFps?.let { JsonPrimitive(it) } ?: JsonNull)
            put("sinRepeticionesPorque", e.noRepsReason?.let { JsonPrimitive(it) } ?: JsonNull)
            put("error", e.error?.let { JsonPrimitive(it) } ?: JsonNull)
            put("avisos", JsonArray(e.flags.map { JsonPrimitive(it) }))
            put("repeticiones", buildJsonArray {
                for ((i, r) in e.reps.withIndex()) add(buildJsonObject {
                    put("tipo", r.kind); put("partialKind", r.partialKind?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("amplitud", r.amplitude); put("parejaVerdad", o.engineToTruth[i]); put("sobranteEnBorde", o.borderExtra[i])
                    put("salidaUs", r.departUs); put("giroUs", r.turnCenterUs); put("llegadaUs", r.arriveUs)
                })
            })
        })
        put("resultado", buildJsonObject {
            put("fullExacto", o.fullExact); put("partialExacto", o.partialExact); put("failedExacto", o.failedExact); put("serieExacta", o.seriesExact)
        })
        put("milisegundos", o.elapsedMs)
    }
}

/** Punto de entrada: planifica, corre las series y escribe el informe. */
internal object PartialsBench {
    fun run(outDir: File, variants: Int, log: (String) -> Unit = {}): List<PartialsOutcome> {
        val plan = PartialsPlan.plan(variants)
        val started = System.nanoTime()
        val outs = ArrayList<PartialsOutcome>(plan.size)
        for ((i, spec) in plan.withIndex()) {
            outs.add(PartialsRunner.run(spec))
            if (i % 100 == 99) log("[parciales] ${i + 1}/${plan.size}")
        }
        val elapsed = (System.nanoTime() - started) / 1e9
        PartialsReport.write(outDir, outs, PartialsReport.synthDef(variants), elapsed)
        return outs
    }
}
