package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

/** Detector del disco (§8 punto 3) sobre el generador sintético de §10.4. */
class PlateDetectorTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    /** Una escena quieta con el disco en un sitio al azar y las dos muñecas: una junto al disco y otra más lejos. */
    private class StaticCase(
        val scene: PlateScene,
        val frame: LumaFrame,
        val cx: Double,
        val cy: Double,
        val radius: Double,
        val wrists: List<Pair<Float, Float>>,
        val expectedRadius: Float,
    )

    private fun staticCase(seed: Long, options: SceneOptions = SceneOptions()): StaticCase {
        val params = SyntheticPlateFrames.params(seed, options)
        val rng = Random(seed * 17L + 3L)
        val r = params.equivalentRadiusPx
        val margin = 4.0 * r + 10.0
        val cx = margin + rng.nextDouble() * (options.width - 2.0 * margin).coerceAtLeast(1.0)
        val cy = margin + rng.nextDouble() * (options.height - 2.0 * margin).coerceAtLeast(1.0)
        val scene = SyntheticPlateFrames.scene(params, StaticPath(cx, cy))
        val frame = scene.frame(0.0, 0)
        // Muñeca cercana: a ≤ 0,4R del centro. Muñeca lejana: la otra mano, a 1,5–2,5R en una dirección cualquiera.
        val near = Pair((cx + (rng.nextDouble() - 0.5) * 0.8 * r).toFloat(), (cy + (rng.nextDouble() - 0.5) * 0.8 * r).toFloat())
        val angle = rng.nextDouble() * 2.0 * Math.PI
        val farDistance = (1.5 + rng.nextDouble()) * r
        val far = Pair((cx + farDistance * Math.cos(angle)).toFloat(), (cy + farDistance * Math.sin(angle)).toFloat())
        val expected = (r * (0.85 + rng.nextDouble() * 0.3)).toFloat()
        return StaticCase(scene, frame, cx, cy, r, listOf(near, far), expected)
    }

    /** Para depurar una semilla fallida: lo que da el Hough con cada hipótesis de a/b en la ventana de cada muñeca. */
    private fun debugCase(case: StaticCase): String {
        val hough = CircleHough(PlateVisionConfig())
        val out = Array(3) { HoughCandidate() }
        val prior = case.expectedRadius.toDouble()
        val sb = StringBuilder()
        for ((w, wrist) in case.wrists.withIndex()) {
            val half = 4.0 * prior
            for (k in 0 until 5) {
                val h = 1.0 - k * 0.06
                val n = hough.detect(
                    case.frame, (wrist.first - half).toInt(), (wrist.second - half).toInt(), (wrist.first + half).toInt() + 1, (wrist.second + half).toInt() + 1,
                    0.75 * prior, 1.7 * prior, RadiusChoice.OUTERMOST, prior, h, out, 3,
                )
                for (i in 0 until n) {
                    val c = out[i]
                    sb.append(
                        "      muñeca $w h=${fmt(h)} #$i: (${fmt(c.cx)}, ${fmt(c.cy)}) r=${fmt(c.r)} a/b=${fmt(c.ratio)} medido=${c.ratioMeasured} q=${fmt(c.quality)} cob=${fmt(c.coverage)} " +
                            "acc=${fmt(c.accScore)} refinado=${c.refined} error=${fmt(hypot(c.cx - case.cx, c.cy - case.cy))} px\n",
                    )
                }
            }
        }
        return sb.toString()
    }

    private fun dumpIfAsked(name: String, frame: LumaFrame) {
        val dir = System.getenv("KPKN_VT6A_DUMP_DIR") ?: return
        SyntheticPlateFrames.writePng(frame, File(dir, "$name.png"))
    }

    @Test
    fun theBestOfFiftySyntheticScenesIsTheRealPlateAndNeverTheDistractor() {
        val lines = ArrayList<String>()
        val centreErrors = ArrayList<Double>()
        val radiusErrors = ArrayList<Double>()
        var good = 0
        var distractorPicks = 0
        val total = 50
        for (seed in 1L..total.toLong()) {
            val case = staticCase(seed)
            if (seed <= 3L) dumpIfAsked("detector-$seed", case.frame)
            val d = 2.0 * case.radius
            val found = PlateDetector.propose(case.frame, case.wrists, case.expectedRadius)
            val top = found.firstOrNull()
            val scene = case.scene
            val errPct = if (top == null) Double.NaN else hypot(top.cx - case.cx, top.cy - case.cy) / d * 100.0
            val onDistractor = top != null && scene.distractorR > 0.0 &&
                hypot(top.cx - scene.distractorX, top.cy - scene.distractorY) < 0.7 * scene.distractorR
            if (onDistractor) distractorPicks++
            if (top != null && errPct < 2.0 && !onDistractor) good++ else {
                lines.add(
                    "semilla $seed: R=${fmt(case.radius)} contraste ${fmt(scene.params.contrast)} polaridad ${scene.params.polarity} ruido ${fmt(scene.params.noiseSigma)} " +
                        "→ ${if (top == null) "sin candidatos" else "candidato en (${fmt(top.cx.toDouble())}, ${fmt(top.cy.toDouble())}) r=${fmt(top.r.toDouble())} error ${fmt(errPct)} % de D, cobertura ${fmt(top.coverage.toDouble())}"} " +
                        "(verdad (${fmt(case.cx)}, ${fmt(case.cy)})); ${found.size} candidatos${if (onDistractor) ", ES EL DISTRACTOR" else ""}\n" + debugCase(case),
                )
            }
            if (top != null) {
                centreErrors.add(errPct)
                radiusErrors.add(abs(top.r - case.radius) / case.radius * 100.0)
            }
        }
        val centres = centreErrors.toDoubleArray()
        val radii = radiusErrors.toDoubleArray()
        println(
            "[detector] $good de $total con el candidato n.º 1 a < 2 % de D del centro real (distractor elegido: $distractorPicks) | error del centro (% de D) " +
                "mediana ${fmt(TrackRun.median(centres))} P95 ${fmt(TrackRun.percentile(centres, 95.0))} | error del radio (%) mediana ${fmt(TrackRun.median(radii))} P95 ${fmt(TrackRun.percentile(radii, 95.0))}",
        )
        if (lines.isNotEmpty()) println("[detector] semillas fallidas:\n" + lines.joinToString("\n"))
        assertEquals("el distractor no debe elegirse nunca", 0, distractorPicks)
        assertTrue("$good de $total aciertos (≥ 95 % = 48)\n" + lines.joinToString("\n"), good >= 48)
    }

    @Test
    fun theOutermostEdgeWinsOverTheRingAndTheHub() {
        // El radio del candidato es el del borde del disco (R) y no el del aro (≈ 0,88R): en ≥ 90 % de 30 semillas, a menos de un 4 %.
        var close = 0
        val total = 30
        val errors = ArrayList<Double>()
        for (seed in 101L..100L + total) {
            val case = staticCase(seed)
            val top = PlateDetector.propose(case.frame, case.wrists, case.expectedRadius).firstOrNull() ?: continue
            val err = abs(top.r - case.radius) / case.radius * 100.0
            errors.add(err)
            if (err < 4.0) close++
        }
        println("[detector] radio del borde exterior: $close de $total a < 4 % | errores ${errors.sorted().map { fmt(it) }}")
        assertTrue("$close de $total con el radio del borde exterior", close >= (0.9 * total).toInt())
    }

    @Test
    fun ellipticalPlatesAreFoundToo() {
        val fails = RepTestSupport.Failures()
        for (ratio in listOf(0.94, 0.85, 0.78)) {
            var good = 0
            val total = 12
            for (seed in 201L..200L + total) {
                val case = staticCase(seed, SceneOptions(axisRatio = ratio))
                val top = PlateDetector.propose(case.frame, case.wrists, case.expectedRadius).firstOrNull()
                val err = if (top == null) Double.NaN else hypot(top.cx - case.cx, top.cy - case.cy) / (2.0 * case.radius) * 100.0
                if (top != null && err < 2.0) good++
            }
            println("[detector] a/b = $ratio: $good de $total a < 2 % de D")
            fails.check(good >= (0.9 * total).toInt()) { "a/b $ratio: $good de $total aciertos" }
        }
        fails.assertNone()
    }

    @Test
    fun candidatesAreNotDuplicatesAndComeInDescendingScore() {
        val case = staticCase(7L)
        val found = PlateDetector.propose(case.frame, case.wrists, case.expectedRadius, max = 3)
        println("[detector] candidatos: ${found.map { "(${fmt(it.cx.toDouble())}, ${fmt(it.cy.toDouble())}) r=${fmt(it.r.toDouble())} puntos=${fmt(it.score.toDouble())} cob=${fmt(it.coverage.toDouble())}" }}")
        assertTrue(found.size <= 3)
        for (i in found.indices) {
            for (j in i + 1 until found.size) {
                val d = hypot((found[i].cx - found[j].cx).toDouble(), (found[i].cy - found[j].cy).toDouble())
                assertTrue("centros a ${fmt(d)} px: duplicados", d >= 0.5 * found[i].r)
            }
            if (i > 0) assertTrue("puntuación no descendente", found[i].score <= found[i - 1].score)
        }
        assertTrue(found.isNotEmpty())
    }

    @Test
    fun aFlatFrameOrNoWristsGiveNoCandidatesOrSearchTheWholeFrame() {
        val flat = LumaFrame(320, 240, ByteArray(320 * 240) { 100 })
        assertTrue(PlateDetector.propose(flat, listOf(Pair(160f, 120f)), 40f).isEmpty())
        assertTrue(PlateDetector.propose(flat, emptyList(), 40f).isEmpty())
        assertTrue(PlateDetector.propose(flat, listOf(Pair(160f, 120f)), 0f).isEmpty())
        // Sin muñecas busca en todo el fotograma.
        val case = staticCase(11L)
        val found = PlateDetector.propose(case.frame, emptyList(), case.expectedRadius)
        val top = found.firstOrNull()
        println("[detector] sin muñecas: ${top?.let { "(${fmt(it.cx.toDouble())}, ${fmt(it.cy.toDouble())}) r=${fmt(it.r.toDouble())}" }} (verdad (${fmt(case.cx)}, ${fmt(case.cy)}))")
        assertTrue("sin muñecas debe encontrar el disco", top != null && hypot(top.cx - case.cx, top.cy - case.cy) < 0.06 * case.radius)
    }
}
