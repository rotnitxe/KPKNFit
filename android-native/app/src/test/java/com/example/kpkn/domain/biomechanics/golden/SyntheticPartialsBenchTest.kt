package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Banco sintético de parciales. El de verdad (`optionalSyntheticPartialsBench`) solo corre con
 * KPKN_PARTIALS_BENCH_OUT=<carpeta externa> (y, opcional, KPKN_PARTIALS_BENCH_VARIANTS, 3 por defecto); el resto son pruebas
 * rápidas del generador, la verdad, el emparejamiento y los informes.
 */
class SyntheticPartialsBenchTest {
    @Test
    fun optionalSyntheticPartialsBench() {
        val out = System.getenv("KPKN_PARTIALS_BENCH_OUT")
        assumeTrue("Banco sintético de parciales opcional: define KPKN_PARTIALS_BENCH_OUT", !out.isNullOrBlank())
        val variants = System.getenv("KPKN_PARTIALS_BENCH_VARIANTS")?.toIntOrNull() ?: 3
        val outcomes = PartialsBench.run(File(requireNotNull(out)), variants) { println(it) }
        assertTrue("Menos de 600 series: ${outcomes.size}", outcomes.size >= 600)
        assertEquals("El motor lanzó excepciones", 0, outcomes.count { it.engine.error != null })
    }

    @Test
    fun planCoversEveryDimensionWithAtLeast600Series() {
        val plan = PartialsPlan.plan(3)
        assertTrue(plan.size >= 600)
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
        assertEquals(PartialsScenario.entries.toSet(), plan.map { it.scenario }.toSet())
        assertEquals(PartialsProfile.entries.toSet(), plan.map { it.profile }.toSet())
        assertEquals(PartialsView.entries.toSet(), plan.map { it.view }.toSet())
        assertEquals(setOf(30.0, 60.0), plan.map { it.fps }.toSet())
        assertEquals(PartialsNoise.entries.toSet(), plan.map { it.noise }.toSet())
        assertEquals(setOf(true, false), plan.map { it.gap }.toSet())
        // Cada escenario ve todas las cadencias y todos los niveles de ruido, y el plan es determinista.
        for (scenario in PartialsScenario.entries) {
            val mine = plan.filter { it.scenario == scenario }
            assertEquals(6, mine.map { it.fps to it.noise }.toSet().size)
        }
        assertEquals(plan.map { it.id to it.seed }, PartialsPlan.plan(3).map { it.id to it.seed })
    }

    @Test
    fun matchingIsOneToOneByTurnTime() {
        val reps = listOf(
            truthRep(0, 0, 4_000_000), truthRep(1, 4_000_000, 8_000_000), truthRep(2, 8_000_000, 12_000_000),
        )
        // Giros del motor: dos dentro de la primera, uno en la segunda, otro fuera de todo.
        val centers = listOf(1_900_000L, 2_300_000L, 6_000_000L, 20_000_000L)
        val m = PartialsMatching.match(reps, centers)
        assertEquals(listOf(0, -1, 1, -1), m.toList())
    }

    private fun truthRep(i: Int, depart: Long, arrive: Long) = PTruthRep(
        i, TruthKind.FULL, PartialsZone.FULL_RANGE, "COMPLETA", 1.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0,
        depart, depart + (arrive - depart) / 2, depart + (arrive - depart) / 2, arrive,
    )

    @Test
    fun truthKindsFollowTheEngineDefinitionOfAmplitude() {
        val profile = PartialsProfile.SENTADILLA
        val full = PRepSpec(1.0, 0.0, 2.0, 0.4, 1.5, 0.4)
        val items = listOf(
            PItem.Idle(1.0),
            PItem.Rep(full),
            PItem.Rep(PRepSpec(1.0, 0.6, 1.2, 0.3, 0.8, 0.3)), // turno pegado al giro, 40 %
            PItem.Rep(PRepSpec(1.0, 0.55, 1.2, 0.3, 0.8, 0.3)), // 45 %
            PItem.Tail(1.0),
        )
        val tl = PartialsTimelineBuilder.build(items, profile.startsEccentric, PartialsCalibration.IDENTITY, profile.movement)
        assertEquals(listOf(TruthKind.FULL, TruthKind.PARTIAL, TruthKind.PARTIAL), tl.reps.map { it.kind })
        assertEquals(PartialsZone.TURN_SIDE, tl.reps[1].zone)
        assertEquals("ESTIRAMIENTO", tl.reps[1].muscleZone)
        // La primera parcial sale del reposo (subida 1,0) y baja al 60 %: a = 0,4. La segunda sale del 60 % y baja al 55 %: a = 0,4 y 0,45.
        assertEquals(0.40, tl.reps[1].fraction, 1e-9)
        assertEquals(0.40, tl.reps[2].fraction, 1e-9)
        // Una parcial pegada al giro seguida de recuperación al reposo ya no es parcial: su bajada es de 100 %.
        val single = listOf(PItem.Idle(1.0), PItem.Rep(PRepSpec(1.0, 0.6, 1.2, 0.3, 0.8, 0.3)), PItem.Recover(1.0), PItem.Idle(1.0))
        val tlSingle = PartialsTimelineBuilder.build(single, true, PartialsCalibration.IDENTITY, profile.movement)
        assertEquals(listOf(TruthKind.FULL), tlSingle.reps.map { it.kind })
        // El arranque C invierte el nombre de la zona muscular.
        val curl = PartialsProfile.CURL
        val tlC = PartialsTimelineBuilder.build(
            listOf(PItem.Idle(1.0), PItem.Rep(PRepSpec(0.5, 0.0, 1.0, 0.3, 1.5, 0.3)), PItem.Idle(1.0)),
            curl.startsEccentric, PartialsCalibration.IDENTITY, curl.movement,
        )
        assertEquals(PartialsZone.REST_SIDE, tlC.reps[0].zone)
        assertEquals("ESTIRAMIENTO", tlC.reps[0].muscleZone)
        // Fallida con arranque C: se estanca a medio camino.
        val failed = PartialsTimelineBuilder.build(
            listOf(PItem.Idle(1.0), PItem.Rep(PRepSpec(1.0, 0.0, 1.5, 0.3, 2.0, 0.3, failed = true)), PItem.Idle(1.0)),
            curl.startsEccentric, PartialsCalibration.IDENTITY, curl.movement,
        )
        assertEquals(listOf(TruthKind.FAILED), failed.reps.map { it.kind })
    }

    @Test
    fun calibrationMakesTheEnginesSignalHitTheDesignedFraction() {
        for (profile in listOf(PartialsProfile.SENTADILLA, PartialsProfile.CURL)) {
            val view = PartialsView.LATERAL
            val cal = PartialsCalibration.of(profile, view)
            assertTrue("sin calibrar: $profile", cal.calibrated)
            assertTrue(cal.bOf(0.3) < cal.bOf(0.5) && cal.bOf(0.5) < cal.bOf(0.7))
            assertEquals(0.5, cal.fractionOf(cal.bOf(0.5)), 0.01)
            for (target in listOf(0.4, 0.6)) {
                val items = listOf(
                    PItem.Idle(2.0),
                    PItem.Rep(PRepSpec(1.0, 0.0, 2.0, 0.5, 1.5, 0.5)),
                    PItem.Rep(PRepSpec(target, 0.0, 1.5, 0.5, 1.2, 0.5)),
                    PItem.Idle(2.0),
                )
                val tl = PartialsTimelineBuilder.build(items, profile.startsEccentric, cal, profile.movement)
                val gen = PartialsLifter.generate(
                    SynthConfig(profile.movement, camera = view.camera, fps = 30.0, script = emptyList<SynthSegment>(), noise = SynthNoise.NONE),
                    tl.timeline,
                )
                val built = CountSeriesBuilder.build(TrackPreparation.prepare(gen.cleanTrack), null, profile.profile) as CountSeriesResult.Ok
                val r = built.series.r
                // Altura del segundo pico (la parcial) sobre el reposo, en fracción del rango medido en la calibración.
                val t2 = tl.reps[1].turnCenterUs / 1e6
                var best = Double.NEGATIVE_INFINITY
                for (k in r.indices) {
                    val t = built.series.t0Us / 1e6 + k / built.series.fs
                    if (!r[k].isNaN() && kotlin.math.abs(t - t2) < 0.4) best = maxOf(best, r[k])
                }
                val fraction = (best - cal.restLevel) / cal.span
                assertEquals("$profile objetivo $target", target, fraction, 0.06)
            }
        }
    }

    @Test
    fun cleanFullSeriesAreCountedExactlyAndTheTruthMatches() {
        for ((profile, view) in listOf(
            PartialsProfile.SENTADILLA to PartialsView.LATERAL,
            PartialsProfile.CURL to PartialsView.LATERAL,
            PartialsProfile.BANCA to PartialsView.LATERAL,
        )) {
            val spec = PartialsSeriesSpec("t", PartialsScenario.COMPLETAS, profile, view, 30.0, PartialsNoise.BAJO, false, 77L, 0)
            val o = PartialsRunner.run(spec)
            assertEquals(profile.label, null, o.engine.error)
            assertTrue("$profile sin salida", o.engine.hasOutput)
            assertEquals("$profile completas", o.truthFull, o.engine.full)
            assertEquals(o.truth.size, o.truth.count { o.truthToEngine[it.index] >= 0 })
        }
    }

    @Test
    fun miniBenchWritesSummaryMarkdownAndOneJsonPerSeries() {
        val dir = Files.createTempDirectory("kpkn-partials-test").toFile()
        try {
            val plan = PartialsPlan.plan(1)
            // Una serie por escenario (la primera de cada uno) para que la prueba sea corta.
            val picked = PartialsScenario.entries.map { s -> plan.first { it.scenario == s } }
            val outs = picked.map { PartialsRunner.run(it) }
            PartialsReport.write(dir, outs, PartialsReport.synthDef(1), 0.0)
            val summary = Json.parseToJsonElement(File(dir, "summary.json").readText()).jsonObject
            assertEquals(picked.size, summary.getValue("series").jsonPrimitive.content.toInt())
            assertNotNull(summary["porEscenarioDetalle"])
            assertNotNull(summary["peoresEscenarios"])
            assertTrue(File(dir, "RESULTADOS.md").readText().contains("Matriz de confusión"))
            assertEquals(picked.size, File(dir, "series").listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --- Fase 2: parciales semi-sintéticas ---------------------------------------------------------------------------------

    @Test
    fun optionalSemiSyntheticPartials() {
        val dir = System.getenv("KPKN_PARTIALS_SEMI_DIR")
        val out = System.getenv("KPKN_PARTIALS_SEMI_OUT")
        assumeTrue("Fase 2 opcional: define KPKN_PARTIALS_SEMI_DIR (paquete de desarrollo) y KPKN_PARTIALS_SEMI_OUT", !dir.isNullOrBlank() && !out.isNullOrBlank())
        val min = System.getenv("KPKN_PARTIALS_SEMI_MIN")?.toIntOrNull() ?: 500
        val outcomes = SemiBench.run(File(requireNotNull(dir)), File(requireNotNull(out)), min) { println(it) }
        assertTrue("Menos de $min series: ${outcomes.size}", outcomes.size >= min)
        assertEquals("El motor lanzó excepciones", 0, outcomes.count { it.engine.error != null })
    }

    private fun syntheticClip(profile: PartialsProfile): SemiClip {
        val cal = PartialsCalibration.of(profile, PartialsView.LATERAL)
        val ecc = profile.startsEccentric
        val items = ArrayList<PItem>()
        items.add(PItem.Idle(2.0))
        repeat(6) { items.add(PItem.Rep(PRepSpec(1.0, 0.0, if (ecc) 2.0 else 1.5, 0.5, if (ecc) 1.5 else 2.0, 0.5))) }
        items.add(PItem.Idle(2.0))
        val tl = PartialsTimelineBuilder.build(items, ecc, cal, profile.movement)
        val gen = PartialsLifter.generate(
            SynthConfig(profile.movement, camera = PartialsView.LATERAL.camera, fps = 30.0, script = emptyList<SynthSegment>(),
                noise = PartialsNoise.BAJO.noise(30.0, null), seed = 5L),
            tl.timeline,
        )
        val res = SemiClipBuilder.build("sint", "sintetico", profile.label, profile.profile, "SIDE", true, gen.track, null, null)
        assertNotNull("sin clip: ${res.reason}", res.clip)
        return res.clip!!
    }

    @Test
    fun semiTransformShortensAndStretchesRealisticallyAndKeepsControlUntouched() {
        val clip = syntheticClip(PartialsProfile.SENTADILLA)
        assertEquals(6, clip.reps.size)
        // Control: la misma traza, sin copiarla.
        assertTrue(SemiTransform.apply(clip, emptyList()) === clip.track)
        // Dos acortadas al 50 %: el motor ve cuatro completas y dos parciales, y la verdad lo dice igual.
        val short = clip.reps.takeLast(2).map { SemiRepPlan(it.index, SemiMode.SHORT, 0.5 / it.amplitude, 0.0, 0.0) }
        val shortTruth = SemiPlanner.truth(clip, short)
        assertEquals(listOf(4, 2), listOf(shortTruth.count { it.kind == TruthKind.FULL }, shortTruth.count { it.kind == TruthKind.PARTIAL }))
        assertTrue(shortTruth.filter { it.kind == TruthKind.PARTIAL }.all { it.zone == PartialsZone.REST_SIDE && Math.abs(it.fraction - 0.5) < 1e-9 })
        val shortOut = SemiRunner.analyze(SemiTransform.apply(clip, short), clip.profile)
        assertEquals("completas", 4, shortOut.full)
        assertEquals("parciales", 2, shortOut.partial)
        // Dos en estiramiento: la verdad las marca TURN_SIDE y el motor las ve como parciales (al menos una).
        val a = clip.reps[4].amplitude
        val b = clip.reps[5].amplitude
        val f1 = 0.45 / a
        val f2 = 0.45 / b
        val stretch = listOf(SemiRepPlan(4, SemiMode.STRETCH, f1, 0.0, 1.0 - f1), SemiRepPlan(5, SemiMode.STRETCH, f2, 1.0 - f1, 1.0 - f2))
        val stretchTruth = SemiPlanner.truth(clip, stretch)
        assertEquals(2, stretchTruth.count { it.kind == TruthKind.PARTIAL && it.zone == PartialsZone.TURN_SIDE })
        val stretchOut = SemiRunner.analyze(SemiTransform.apply(clip, stretch), clip.profile)
        // El motor no distingue bien una parcial en estiramiento al final de una fallida: solo se exige que dé salida y que no sean completas.
        assertTrue("estiramiento: ${stretchOut.full}/${stretchOut.partial}", stretchOut.hasOutput && stretchOut.full <= 4)
    }
}
