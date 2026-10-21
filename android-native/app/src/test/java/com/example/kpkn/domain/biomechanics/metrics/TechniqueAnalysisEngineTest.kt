package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.LegacyPoseTrackAdapter
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** El motor de punta a punta (§3.6): del vídeo sintético al informe. */
class TechniqueAnalysisEngineTest {
    private fun input(r: SynthResult, profile: AnalysisProfile, bar: BarTrack? = null, hints: Set<String> = emptySet()) =
        TechniqueAnalysisInput(
            pose = r.track,
            bar = bar,
            profile = profile,
            loadKg = null,
            userHeightCm = null,
            setHints = hints,
        )

    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.2f", x)

    private val tempoLabel = Regex("""\d+-\d+-(\d+|X)-\d+""")

    @Test
    fun fiveCleanSquatsGiveAHighConfidenceReportWithEverythingMeasured() {
        val fails = RepTestSupport.Failures()
        // Las mismas cinco sentadillas limpias (ruido típico del generador) que la prueba de confianza ALTA.
        val r = SyntheticLifter.generate(
            SynthConfig(SynthMovement.SQUAT, script = repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0)),
        )
        val report = TechniqueAnalysisEngine.analyze(input(r, RepTestSupport.profile(AnalysisProfileId.SQUAT)))
        println(
            "[motor] sentadillas: ${report.repCount} reps ${report.confidence} (${fmt(report.confidenceScore)}) vista ${report.view} lado ${report.nearSide} " +
                "tempo ${report.tempo?.label()} TUT ${report.tutMs} ms serie ${report.setDurationMs} ms reposo ${report.restPositionMs} ms | disponible ${report.available.sorted()} | " +
                "ROM ${fmt(report.romMeanDeg)}±${fmt(report.romSdDeg)} (CV ${fmt(report.romCvPct)} %) tronco ${fmt(report.trunkMeanDeg)}° desvío ${fmt(report.barDeviation)} ${report.barDeviationUnit} " +
                "| velocidad ${report.velocityUnit} MCV ${fmt(report.mcvFirst)}/${fmt(report.mcvBest)}/${fmt(report.mcvLast)} pérdida ${fmt(report.velocityLossPct)} % | avisos ${report.flags}",
        )
        for (rep in report.reps) {
            println(
                "[motor]   rep ${rep.index} ${rep.kind} E ${rep.eccentricMs} P ${rep.turnPauseMs} C ${rep.concentricMs} R ${rep.restPauseMs} | ROM ${fmt(rep.romDeg)}° " +
                    "profundidad ${rep.depthClass} ${fmt(rep.depthDeg)}° | MCV ${fmt(rep.mcv)} | desvío ${fmt(rep.pathOffsetMean)} deriva ${fmt(rep.pathDrift)} exc ${fmt(rep.pathExcursionFraction)} | " +
                    "τ giro ${fmt(rep.trunkAtTurnDeg)} máx ${fmt(rep.trunkMaxDeg)} Δ ${fmt(rep.trunkEarlyDeltaDeg)}",
            )
        }
        fails.check(report.engineVersion == TechniqueAnalysisEngine.ENGINE_VERSION && TechniqueAnalysisEngine.ENGINE_VERSION == 7) { "versión ${report.engineVersion}" }
        fails.check(report.profileId == AnalysisProfileId.SQUAT && report.tier == ProfileTier.A) { "perfil ${report.profileId}/${report.tier}" }
        fails.check(report.repCount == 5 && report.partialReps == 0 && report.failedReps == 0) { "conteo ${report.repCount}/${report.partialReps}/${report.failedReps}" }
        fails.check(report.reps.size == 5 && report.reps.all { it.kind == RepKind.FULL }) { "repeticiones ${report.reps.map { it.kind }}" }
        fails.check(report.reps.withIndex().all { (i, rep) -> rep.index == i }) { "índices ${report.reps.map { it.index }}" }
        fails.check(report.confidence == CountConfidence.ALTA) { "confianza ${report.confidence} (${report.confidenceScore})" }
        fails.check(report.view == ViewKind.SIDE && report.nearSide == "left") { "vista ${report.view}, lado ${report.nearSide}" }
        val tempo = report.tempo
        fails.check(tempo != null && tempo.eccentricMs != null && tempo.pauseAfterEccentricMs != null && tempo.concentricMs != null && tempo.pauseAfterConcentricMs != null) {
            "tempo con cuatro cifras: $tempo"
        }
        fails.check(tempo != null && tempoLabel.matches(tempo.label())) { "etiqueta de tempo ${tempo?.label()}" }
        fails.check((report.tutMs ?: 0L) > 0L) { "TUT ${report.tutMs}" }
        val expected = setOf(
            MetricKey.REPS, MetricKey.PHASES, MetricKey.TUT, MetricKey.TEMPO, MetricKey.ROM, MetricKey.DEPTH,
            MetricKey.TRUNK, MetricKey.PATH, MetricKey.VELOCITY_RELATIVE,
        )
        fails.check(report.available.containsAll(expected)) { "disponible ${report.available.sorted()}; faltan ${(expected - report.available).sorted()}" }
        fails.check(MetricKey.PATH_ABSOLUTE !in report.available && MetricKey.VELOCITY_MPS !in report.available && MetricKey.SYMMETRY !in report.available) {
            "no debería haber m/s, cm ni simetría: ${report.available.sorted()}"
        }
        // Sin disco calibrado: sin escala, sin observaciones y velocidades relativas.
        fails.check(report.scale == ScaleInfo.NONE && report.scale?.source == ScaleSource.NONE) { "escala ${report.scale}" }
        fails.check(report.observations.isEmpty()) { "observaciones ${report.observations}" }
        fails.check(report.velocityUnit == VelocityUnit.BODY_HEIGHTS_PER_S) { "unidad de velocidad ${report.velocityUnit}" }
        fails.check(report.barDeviationUnit == "%ROM" && report.barDeviation != null) { "desvío ${report.barDeviation} ${report.barDeviationUnit}" }
        val sourceFps = report.sourceFps
        fails.check(abs(report.analyzedFps - 30.0) <= 0.5 && sourceFps != null && abs(sourceFps - 30.0) <= 0.5) {
            "cadencia ${report.analyzedFps} / $sourceFps"
        }
        // El ROM de rodilla de la serie (5 × 121°) con el ruido típico: unos grados de sesgo, no más.
        val truthRom = r.reps.first().romDeg
        val romMean = report.romMeanDeg
        fails.check(romMean != null && abs(romMean - truthRom) <= 15.0) { "ROM medio $romMean (verdad $truthRom ± 15)" }
        fails.check(report.reps.all { it.depthClass == null || it.depthDeg != null }) { "una clase de profundidad sin su cifra" }
        fails.check(report.reps.all { it.amplitudeFraction in 0.5..1.5 }) { "amplitudes ${report.reps.map { it.amplitudeFraction }}" }
        // Los instantes de cada repetición salen de las fases y están en orden.
        for (rep in report.reps) {
            val p = rep.phases
            fails.check(p.departUs <= p.turnStartUs && p.turnStartUs <= p.turnEndUs && p.turnEndUs <= p.arriveUs) { "fases desordenadas en la repetición ${rep.index}: $p" }
        }
        // Nada de NaN ni infinitos: el informe se escribe en JSON.
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(SetTechniqueReport.serializer(), report)
        fails.check("NaN" !in text && "Infinity" !in text) { "el JSON del informe lleva NaN o infinitos" }
        assertEquals(report, json.decodeFromString(SetTechniqueReport.serializer(), text))
        fails.assertNone()
    }

    @Test
    fun aTrackWithoutAnyPoseGivesAnEmptyLowConfidenceReport() {
        val frames = List(120) { PoseFrameV2.of(it * 33_333L, 0, null, null) }
        val track = PoseTrackV2(
            mediaId = "sin-atleta",
            engine = PoseEngineInfo("synthetic", "lifter", "CPU"),
            frameWidth = 720,
            frameHeight = 1280,
            rotationApplied = 0,
            nominalFps = 30f,
            poseStride = 1,
            vfr = false,
            frames = frames,
        )
        val report = TechniqueAnalysisEngine.analyze(
            TechniqueAnalysisInput(track, null, RepTestSupport.profile(AnalysisProfileId.SQUAT), null, null, emptySet()),
        )
        println("[motor] sin pose: ${report.repCount} reps ${report.confidence} motivo=${report.noRepsReason} disponible=${report.available} avisos=${report.flags}")
        assertEquals(0, report.repCount)
        assertEquals(0, report.partialReps)
        assertEquals(0, report.failedReps)
        assertEquals(CountConfidence.BAJA, report.confidence)
        assertEquals(emptySet<MetricKey>(), report.available)
        assertEquals(emptyList<RepReport>(), report.reps)
        assertEquals("no_pose", report.noRepsReason)
        assertEquals(ScaleInfo.NONE, report.scale)
        assertEquals(null, report.tempo)
        assertEquals(null, report.tutMs)
        assertEquals(AnalysisProfileId.SQUAT, report.profileId)
        // Y se puede guardar.
        val json = Json { encodeDefaults = true }
        assertEquals(report, json.decodeFromString(SetTechniqueReport.serializer(), json.encodeToString(SetTechniqueReport.serializer(), report)))
    }

    @Test
    fun aFrontViewKeepsTheCountAndDropsTheAbsoluteMetrics() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), camera = SynthCamera(yawDeg = 0.0))
        val report = TechniqueAnalysisEngine.analyze(input(r, RepTestSupport.profile(AnalysisProfileId.SQUAT)))
        println("[motor] sentadillas de frente: vista ${report.view} ${report.repCount} reps ${report.confidence} | disponible ${report.available.sorted()}")
        fails.check(report.view == ViewKind.FRONT) { "vista ${report.view}" }
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(MetricKey.REPS in report.available && MetricKey.PHASES in report.available && MetricKey.TUT in report.available) { "disponible ${report.available.sorted()}" }
        for (key in listOf(MetricKey.ROM, MetricKey.DEPTH, MetricKey.LOCKOUT, MetricKey.TRUNK, MetricKey.PATH)) {
            fails.check(key !in report.available) { "$key no debería estar disponible de frente" }
        }
        fails.check(report.romMeanDeg == null && report.trunkMeanDeg == null && report.barDeviation == null && report.barDeviationUnit == null) { "cifras absolutas de frente" }
        fails.check(report.reps.all { it.romDeg == null && it.depthClass == null && it.trunkAtTurnDeg == null && it.pathOffsetMean == null }) { "cifras por repetición de frente" }
        fails.assertNone()
    }

    @Test
    fun aDeadliftReportsTheLockoutAndTheBarPath() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(5) { 1.0 }))
        val report = TechniqueAnalysisEngine.analyze(input(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT)))
        println(
            "[motor] peso muerto: ${report.repCount} reps ${report.confidence} | disponible ${report.available.sorted()} | bloqueo ${report.reps.map { it.criteria }} | " +
                "ROM ${fmt(report.romMeanDeg)}° tronco ${fmt(report.trunkMeanDeg)}° desvío ${fmt(report.barDeviation)} ${report.barDeviationUnit} | tempo ${report.tempo?.label()}",
        )
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(MetricKey.LOCKOUT in report.available && MetricKey.DEPTH !in report.available) { "disponible ${report.available.sorted()}" }
        fails.check(report.reps.all { it.criteria["lockout"] == true }) { "bloqueo ${report.reps.map { it.criteria }}" }
        fails.check(MetricKey.ROM in report.available && MetricKey.TRUNK in report.available && MetricKey.PATH in report.available && MetricKey.VELOCITY_RELATIVE in report.available) {
            "disponible ${report.available.sorted()}"
        }
        val deviation = report.barDeviation
        fails.check(deviation != null && deviation in 0.0..12.0) { "desvío de la barra $deviation %ROM" }
        // El peso muerto arranca con la concéntrica: el tempo se lee excéntrica-pausa-concéntrica-pausa igualmente.
        fails.check(report.tempo?.concentricMs != null) { "tempo ${report.tempo}" }
        fails.assertNone()
    }

    @Test
    fun aBenchWithThePlateReportsTheConsistencyAndTheShorterRep() {
        val fails = RepTestSupport.Failures()
        val amplitudes = listOf(1.0, 1.0, 1.0, 1.0, 0.85)
        val r = MetricsTestSupport.gen(SynthMovement.BENCH, MetricsTestSupport.script(amplitudes, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
        val bar = RepTestSupport.plateFromWrist(r)
        val report = TechniqueAnalysisEngine.analyze(input(r, RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true), bar))
        println(
            "[motor] banca con disco: ${report.repCount} reps ${report.confidence} vista ${report.view} | disponible ${report.available.sorted()} | " +
                "PD ${report.reps.map { fmt(it.pathDeviationRms) }} | más corta ${report.reps.map { it.criteria["shallower"] }} | desvío ${fmt(report.barDeviation)} ${report.barDeviationUnit}",
        )
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(report.view == ViewKind.LYING) { "vista ${report.view}" }
        fails.check(MetricKey.PATH in report.available && MetricKey.DEPTH in report.available && MetricKey.TRUNK in report.available) { "disponible ${report.available.sorted()}" }
        fails.check(report.reps.all { it.pathDeviationRms != null }) { "PD por repetición ${report.reps.map { it.pathDeviationRms }}" }
        fails.check(report.reps.map { it.criteria["shallower"] } == listOf(false, false, false, false, true)) { "shallower ${report.reps.map { it.criteria["shallower"] }}" }
        fails.check(report.barDeviationUnit == "%ROM") { "unidad del desvío ${report.barDeviationUnit}" }
        fails.assertNone()
    }

    @Test
    fun aSeriesThatRestsAtTheTopIsFlippedAndTheTimesStillComeOutRight() {
        val fails = RepTestSupport.Failures()
        // Flexiones contadas con −Y del hombro: con los brazos extendidos (el reposo) la señal está en su MÁXIMO. Con 3 s de reposo
        // arriba y sin pausa abajo, el segmentador le da la vuelta (POLARITY_FLIPPED) y las fases deben salir de la señal al revés.
        val inverted = AnalysisProfiles.of(AnalysisProfileId.PUSH_UP).copy(poseSignal = CountSignal.RISE_SHOULDER_NEAR)
        val script = listOf<SynthSegment>(SynthSegment.Idle(3.0)) + List(5) { SynthSegment.Rep(1.5, 0.0, 1.5, 3.0) } + SynthSegment.Idle(3.0)
        val r = MetricsTestSupport.gen(SynthMovement.PUSH_UP, script)
        val report = TechniqueAnalysisEngine.analyze(input(r, inverted))
        val tempo = report.tempo
        val ecc = tempo?.eccentricMs
        val con = tempo?.concentricMs
        val rest = tempo?.pauseAfterConcentricMs
        println(
            "[motor] señal al revés: ${report.repCount} reps ${report.confidence} avisos ${report.flags} | tempo ${tempo?.detailLabel()} TUT ${report.tutMs} | " +
                "fases ${report.reps.map { "${it.eccentricMs}/${it.turnPauseMs}/${it.concentricMs}/${it.restPauseMs}" }}",
        )
        fails.check(AnalysisFlag.POLARITY_FLIPPED in report.flags) { "falta POLARITY_FLIPPED: ${report.flags}" }
        fails.check(report.repCount == 5) { "conteo ${report.repCount} (${report.noRepsReason})" }
        fails.check(report.confidence != CountConfidence.ALTA) { "con la polaridad dada la vuelta no puede ser ALTA" }
        fails.check(MetricKey.PHASES in report.available) { "disponible ${report.available.sorted()}" }
        // Excéntrica 1,5 s, concéntrica 1,5 s, reposo arriba 3 s.
        fails.check(ecc != null && abs(ecc - 1500) <= 300) { "excéntrica $ecc ms (esperada 1500 ± 300)" }
        fails.check(con != null && abs(con - 1500) <= 300) { "concéntrica $con ms (esperada 1500 ± 300)" }
        fails.check(rest != null && abs(rest - 3000) <= 600) { "reposo $rest ms (esperado 3000 ± 600)" }
        val tut = report.tutMs
        fails.check(tut != null && abs(tut - 15_000L) <= 1_500L) { "TUT $tut ms (esperado 15000 ± 1500)" }
        fails.assertNone()
    }

    @Test
    fun aLegacyV1TrackOnlyCountsAndNeverMeasuresGeometry() {
        val fails = RepTestSupport.Failures()
        val r = SyntheticLifter.generate(
            SynthConfig(SynthMovement.SQUAT, script = repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0)),
        )
        // La traza de ML Kit a ~10 Hz, sin aspecto fiable (aspectKnown = false): solo sirve para contar.
        val legacy = LegacyPoseTrackAdapter.toV2(RepTestSupport.legacyV1(r))
        val report = TechniqueAnalysisEngine.analyze(
            TechniqueAnalysisInput(legacy, null, RepTestSupport.profile(AnalysisProfileId.SQUAT), null, null, emptySet()),
        )
        println("[motor] traza v1: ${report.repCount} reps ${report.confidence} vista ${report.view} | disponible ${report.available.sorted()} avisos ${report.flags}")
        fails.check(AnalysisFlag.LEGACY_TRACK in report.flags && AnalysisFlag.LOW_FPS in report.flags) { "avisos ${report.flags}" }
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(report.confidence == CountConfidence.BAJA) { "confianza ${report.confidence}" }
        fails.check(report.available == setOf(MetricKey.REPS)) { "disponible ${report.available.sorted()}: una traza v1 solo cuenta" }
        fails.check(report.romMeanDeg == null && report.trunkMeanDeg == null && report.barDeviation == null && report.velocityUnit == null) { "cifras de geometría con una traza v1" }
        fails.check(report.reps.all { it.romDeg == null && it.depthClass == null && it.trunkAtTurnDeg == null && it.pathOffsetMean == null && it.mcv == null }) {
            "cifras por repetición con una traza v1"
        }
        fails.assertNone()
    }

    @Test
    fun belowTwentyFpsTheReportHasTheCountButNoPhasesNorVelocity() {
        val fails = RepTestSupport.Failures()
        val r = SyntheticLifter.generate(
            SynthConfig(SynthMovement.SQUAT, fps = 15.0, script = repScript(5, 2.0, 1.0, 1.0, 0.5, lead = 2.0, tail = 2.0)),
        )
        val report = TechniqueAnalysisEngine.analyze(input(r, RepTestSupport.profile(AnalysisProfileId.SQUAT)))
        println("[motor] 15 fps: ${report.repCount} reps ${report.confidence} | disponible ${report.available.sorted()} avisos ${report.flags}")
        fails.check(report.reps.isNotEmpty() && MetricKey.REPS in report.available) { "sin repeticiones a 15 fps: ${report.noRepsReason}, disponible ${report.available.sorted()}" }
        fails.check(AnalysisFlag.LOW_FPS in report.flags && report.confidence == CountConfidence.BAJA) { "avisos ${report.flags}, confianza ${report.confidence}" }
        for (key in listOf(MetricKey.PHASES, MetricKey.TUT, MetricKey.TEMPO, MetricKey.VELOCITY_RELATIVE, MetricKey.PEAK_VELOCITY)) {
            fails.check(key !in report.available) { "$key no debería estar disponible a 15 fps" }
        }
        fails.check(report.tutMs == null && report.tempo == null && report.mcvBest == null && report.velocityUnit == null) { "cifras con fases a 15 fps" }
        fails.check(report.reps.all { it.eccentricMs == null && it.concentricMs == null && it.mcv == null }) { "cifras por repetición a 15 fps" }
        // Los instantes salen del segmentador, en orden.
        fails.check(report.reps.all { it.phases.departUs <= it.phases.turnStartUs && it.phases.turnStartUs <= it.phases.arriveUs }) { "instantes ${report.reps.map { it.phases }}" }
        fails.assertNone()
    }
}
