package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.reps.CountSeries
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.PreparedTrack
import com.example.kpkn.domain.biomechanics.reps.RepConfidence
import com.example.kpkn.domain.biomechanics.reps.RepCycle
import com.example.kpkn.domain.biomechanics.reps.RepFlags
import com.example.kpkn.domain.biomechanics.reps.RepPhases
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import com.example.kpkn.domain.biomechanics.vbt.CalibratedVelocity
import com.example.kpkn.domain.biomechanics.vbt.PlateScale
import com.example.kpkn.domain.biomechanics.vbt.PlateScaleResult
import kotlin.math.max

/**
 * Lo que el motor necesita de un vídeo (§3.6): la traza de pose, la del disco si la hay, el perfil que lo mide, y los datos de la
 * serie. [loadKg] y [userHeightCm] los usa VT3b (m/s y carga-velocidad); [setHints] son los nombres de `SetTechniqueV2`
 * (`REST_PAUSE`, `PARTIALS`…) que ayudan a contar.
 */
data class TechniqueAnalysisInput(
    val pose: PoseTrackV2,
    val bar: BarTrack?,
    val profile: AnalysisProfile,
    val loadKg: Double?,
    val userHeightCm: Double?,
    val setHints: Set<String>,
    val tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
)

/**
 * El motor del analizador de técnica (§3.6): del vídeo ya convertido en trazas al [SetTechniqueReport].
 *
 * Secuencia: preparación de la traza (cruces izquierda/derecha, geometría con el aspecto y el roll, escala, lado cercano, vista),
 * señal de conteo, segmentación, confianza, fases/TUT/tempo y, por último, las métricas (ROM, profundidad y bloqueos, tronco,
 * trayectoria y velocidad). Cada métrica solo se marca disponible si el perfil y la vista lo permiten.
 *
 * **Disco calibrado (VT3b).** Con un `BarTrack` de diámetro conocido, `PlateScale` da la escala (con la elipse y el paralaje) y sus
 * avisos (PLATE_ELLIPSE, PLATE_REJECTED, PARALLAX_SUPPRESSED, y PLATE_SCALE_MISMATCH si el muslo según el disco no cuadra con el de
 * los puntos «world», VT6a). Si además el disco es la señal de conteo (`source == "plate"`):
 * - la velocidad sale en m/s (`VelocityUnit.MPS`, claves VELOCITY_MPS y, con el disco a ≥ 50 fps, PEAK_VELOCITY);
 * - la trayectoria sale en cm (`barDeviationUnit = "cm"`, clave PATH_ABSOLUTE); el desvío respecto del medio pie solo si el paralaje
 *   se pudo corregir o es pequeño (si no, PARALLAX_SUPPRESSED y queda sin dato; la deriva y la excursión siguen).
 * Sin disco calibrado nada va en m/s ni en cm: las velocidades son relativas (BH/s o °/s, mostradas solo en %) y la trayectoria
 * va en % del recorrido (ROM_v). PEAK_VELOCITY es siempre el pico en m/s: el pico relativo (BH/s) no se marca disponible.
 *
 * Las observaciones de técnica (`Observations`) se calculan al final, sobre el informe ya armado.
 */
object TechniqueAnalysisEngine {
    const val ENGINE_VERSION = 1

    /** Unidad de las cifras de trayectoria sin calibrar: porcentaje del recorrido vertical de la barra (ROM_v). */
    const val PERCENT_ROM = "%ROM"

    /** Unidad de las cifras de trayectoria con el disco calibrado: centímetros. */
    const val PATH_CM = "cm"

    fun analyze(input: TechniqueAnalysisInput): SetTechniqueReport {
        val tuning = input.tuning
        val profile = input.profile
        val prepared = TrackPreparation.prepare(input.pose, tuning)
        val built = CountSeriesBuilder.build(prepared, input.bar, profile, tuning)
        val ok = when (built) {
            is CountSeriesResult.Ok -> built
            is CountSeriesResult.NoSignal -> return noSignalReport(input, prepared, built.reason)
        }
        val series = ok.series
        val seg = RepSegmenter.segment(series, profile, input.setHints, tuning)
        val countFlags = RepFlags.of(prepared, series, seg)
        val confidence = RepConfidence.score(series, seg, prepared.view, profile, countFlags, tuning)

        // Si el segmentador dio la vuelta a la señal, los índices de sus ciclos son los de la señal al revés: las fases y las
        // métricas deben ver la misma señal.
        val oriented = if (seg.polarityFlipped) negated(series) else series
        val phases = RepPhases.compute(oriented, seg, profile, tuning)
        val ctx = MetricContext(prepared, oriented, seg, phases, profile, tuning)

        // Escala por disco (VT3b): con un disco de diámetro conocido hay escala y avisos; los m/s y los cm, además, exigen que
        // el disco sea la señal de conteo (su traslación es la de la barra).
        val bar = input.bar
        val plate: PlateScaleResult? =
            if (bar != null && bar.plateDiameterMm != null) {
                PlateScale.estimate(bar, prepared.timebase.fs, input.pose.focalLengthPx, tuning)
            } else {
                null
            }
        // Cordura (VT6a): el muslo según la escala del disco, en el plano del atleta, debe cuadrar con el de los puntos «world».
        val thigh = if (plate != null && bar != null) PlateScale.thighSanity(plate, bar, prepared, tuning) else null
        val plateFlags: Set<AnalysisFlag> = when {
            plate == null -> emptySet()
            thigh?.mismatch == true -> plate.flags + AnalysisFlag.PLATE_SCALE_MISMATCH
            else -> plate.flags
        }
        val flags = if (plateFlags.isNotEmpty()) countFlags + plateFlags else countFlags
        val scale = plate?.scale
        val calibratedBar: BarTrack? = if (scale != null && series.source == CountSeriesBuilder.SOURCE_PLATE) bar else null
        val calibration = if (calibratedBar != null && plate != null) PathCalibration.of(plate, calibratedBar.frameHeight) else null

        val rom = RomMetrics.compute(ctx)
        val depth = DepthRules.compute(ctx)
        val trunk = TrunkMetrics.compute(ctx)
        val path = PathMetrics.compute(ctx, calibration)
        val relative = RelativeVelocity.compute(ctx)
        val mps = if (calibratedBar != null && scale != null) CalibratedVelocity.compute(ctx, calibratedBar, scale) else null
        val velocity = if (mps != null && mps.available) mps else relative
        val inMps = velocity === mps
        val cm = path.available && path.calibrated

        val reps = ctx.cycles.mapIndexed { k, cycle ->
            repReport(ctx, cycle, rom.perRepDeg[k], depth.reps[k], trunk.reps[k], path, path.reps[k], velocity.reps[k], cm)
        }
        val available = buildSet<MetricKey> {
            if (ctx.cycles.isNotEmpty()) add(MetricKey.REPS)
            if (phases.reps.isNotEmpty()) add(MetricKey.PHASES)
            if (phases.tutMs != null) add(MetricKey.TUT)
            if (phases.tempo != null) add(MetricKey.TEMPO)
            if (rom.available) add(MetricKey.ROM)
            if (depth.depthAvailable) add(MetricKey.DEPTH)
            if (depth.lockoutAvailable) add(MetricKey.LOCKOUT)
            if (path.available) add(MetricKey.PATH)
            if (cm) add(MetricKey.PATH_ABSOLUTE)
            if (trunk.available) add(MetricKey.TRUNK)
            if (inMps) {
                // Con el disco calibrado: m/s, pico en m/s si el disco se midió a ≥ 50 fps, y las barras relativas si la pérdida es fiable.
                add(MetricKey.VELOCITY_MPS)
                if (velocity.peakAvailable) add(MetricKey.PEAK_VELOCITY)
                if (velocity.lossReliable) add(MetricKey.VELOCITY_RELATIVE)
            } else if (velocity.available) {
                add(MetricKey.VELOCITY_RELATIVE)
            }
        }

        val analyzedFps = prepared.timebase.fs
        // Desvío de la barra: en cm con el disco calibrado (sin dato si el paralaje lo oculta, pero la unidad sigue siendo cm para la
        // deriva de cada repetición); si no, en % del recorrido vertical.
        val barDeviation = when {
            !path.available -> null
            cm -> if (path.offsetsHidden) null else round(path.barDeviationCm, 1)
            else -> round(path.barDeviationPctRom, 1)
        }
        val barDeviationUnit = when {
            !path.available -> null
            cm -> PATH_CM
            barDeviation != null -> PERCENT_ROM
            else -> null
        }
        val report = SetTechniqueReport(
            engineVersion = ENGINE_VERSION,
            profileId = profile.id,
            tier = profile.tier,
            view = prepared.view.kind,
            nearSide = prepared.near?.near?.name?.lowercase(),
            confidence = confidence.level,
            confidenceScore = round(confidence.score, 3) ?: 0.0,
            reps = reps,
            repCount = seg.fullCount,
            partialReps = seg.partialCount,
            failedReps = seg.failedCount,
            miniSets = seg.miniSets,
            perSideCounts = seg.perSideCounts.mapKeys { it.key.name.lowercase() },
            tutMs = phases.tutMs,
            setDurationMs = phases.setDurationMs,
            restPositionMs = phases.restPositionMs,
            tempo = phases.tempo,
            romMeanDeg = round(rom.meanDeg, 1),
            romSdDeg = round(rom.sdDeg, 1),
            romCvPct = round(rom.cvPct, 1),
            romTrendDeg = round(rom.trendDeg, 1),
            velocityUnit = if (velocity.available) velocity.unit else null,
            velocityLossPct = round(velocity.lossPct, 1),
            mcvFirst = round(velocity.first, 3),
            mcvBest = round(velocity.best, 3),
            mcvLast = round(velocity.last, 3),
            peakBest = round(velocity.peakBest, 3),
            scale = scale ?: ScaleInfo.NONE,
            trunkMeanDeg = round(trunk.meanDeg, 1),
            barDeviation = barDeviation,
            barDeviationUnit = barDeviationUnit,
            observations = emptyList(),
            available = available,
            flags = flags,
            analyzedFps = analyzedFps,
            sourceFps = sourceFps(input.pose, analyzedFps),
            noRepsReason = seg.noRepsReason,
            romSlopeDegPerRep = round(rom.slopeDegPerRep, 2),
        )
        // Las observaciones salen del informe ya armado (hechos con su cifra y su repetición, como mucho 3).
        val observations = Observations.evaluate(report, prepared, profile, report.scale ?: ScaleInfo.NONE, tuning, ctx, path, input.setHints)
        return if (observations.isEmpty()) report else report.copy(observations = observations)
    }

    /** Informe de un vídeo sin señal: 0 repeticiones, confianza BAJA, nada disponible y el motivo en `noRepsReason`. */
    private fun noSignalReport(input: TechniqueAnalysisInput, prepared: PreparedTrack, reason: String): SetTechniqueReport {
        val analyzedFps = prepared.timebase.fs
        return SetTechniqueReport(
            engineVersion = ENGINE_VERSION,
            profileId = input.profile.id,
            tier = input.profile.tier,
            view = prepared.view.kind,
            nearSide = prepared.near?.near?.name?.lowercase(),
            confidence = CountConfidence.BAJA,
            confidenceScore = 0.0,
            reps = emptyList(),
            repCount = 0,
            partialReps = 0,
            failedReps = 0,
            miniSets = 0,
            perSideCounts = emptyMap(),
            tutMs = null,
            setDurationMs = null,
            restPositionMs = null,
            tempo = null,
            romMeanDeg = null,
            romSdDeg = null,
            romCvPct = null,
            romTrendDeg = null,
            velocityUnit = null,
            velocityLossPct = null,
            mcvFirst = null,
            mcvBest = null,
            mcvLast = null,
            peakBest = null,
            scale = ScaleInfo.NONE,
            trunkMeanDeg = null,
            barDeviation = null,
            barDeviationUnit = null,
            observations = emptyList(),
            available = emptySet(),
            flags = prepared.flags,
            analyzedFps = analyzedFps,
            sourceFps = sourceFps(input.pose, analyzedFps),
            noRepsReason = reason,
        )
    }

    private fun repReport(
        ctx: MetricContext,
        cycle: RepCycle,
        romDeg: Double,
        depth: RepDepth,
        trunk: RepTrunk,
        path: PathSummary,
        pathRep: RepPath,
        velocity: RepVelocity,
        cm: Boolean,
    ): RepReport {
        val ph = ctx.phasesOf(cycle)
        val times = if (ph != null) {
            RepPhaseTimes(ph.departUs, ph.turnStartUs, ph.turnEndUs, ph.arriveUs)
        } else {
            // Sin fases (fs < 20): solo los instantes del segmentador, sin duraciones.
            val turn = usOf(ctx, cycle.peak)
            RepPhaseTimes(usOf(ctx, cycle.leftValley), turn, turn, usOf(ctx, cycle.rightValley ?: cycle.peak))
        }
        val romV = pathRep.romVBh
        return RepReport(
            index = cycle.index,
            kind = cycle.kind,
            partialKind = cycle.partialKind,
            side = cycle.side?.name?.lowercase(),
            phases = times,
            eccentricMs = ph?.eccentricMs,
            turnPauseMs = ph?.turnPauseMs,
            concentricMs = ph?.concentricMs,
            restPauseMs = ph?.restPauseMs,
            amplitudeFraction = round(cycle.amplitudeFraction, 3) ?: 0.0,
            romDeg = round(romDeg, 1),
            depthClass = depth.depthClass,
            depthDeg = round(depth.depthDeg, 1),
            criteria = depth.criteria.toMap(),
            mcv = round(velocity.mcv, 3),
            peakVelocity = round(velocity.peak, 3),
            // Con el disco calibrado, en cm (el desvío respecto del medio pie es NaN si el paralaje lo oculta); si no, en % de ROM_v.
            pathOffsetMean = if (cm) round(pathRep.offsetMeanCm, 1) else round(percentOf(pathRep.offsetMeanBh, romV), 1),
            pathDrift = if (cm) round(pathRep.driftCm, 1) else round(percentOf(pathRep.driftBh, romV), 1),
            pathExcursionFraction = round(pathRep.excursionFraction, 3),
            pathDeviationRms = if (cm) {
                round(pathRep.deviationRmsCm, 1)
            } else {
                round(percentOf(pathRep.deviationRmsBh, path.medianRomVBh ?: Double.NaN), 1)
            },
            trunkAtTurnDeg = round(trunk.atTurnDeg, 1),
            trunkRangeDeg = round(trunk.rangeDeg, 1),
            trunkEarlyDeltaDeg = round(trunk.earlyDeltaDeg, 1),
            trunkMaxDeg = round(trunk.maxDeg, 1),
        )
    }

    /** `value` como porcentaje de `reference`; `null` si falta alguno. */
    private fun percentOf(value: Double, reference: Double): Double? =
        if (value.isNaN() || !(reference > 0.0)) null else value / reference * 100.0

    /** [x] redondeado a [decimals] decimales; `null` si falta o no es finito (nada de NaN en el informe). */
    private fun round(x: Double?, decimals: Int): Double? = if (x == null) null else SeriesMath.roundedOrNull(x, decimals)

    private fun usOf(ctx: MetricContext, index: Int): Long =
        ctx.series.t0Us + Math.round(index / ctx.series.fs * SeriesMath.MICROS)

    /** Cadencia de la grabación: la de captura (cámara lenta) o la analizada por el salto de fotogramas. */
    private fun sourceFps(pose: PoseTrackV2, analyzedFps: Double): Double? {
        val capture = pose.captureFps
        if (capture != null && capture > 0f) return capture.toDouble()
        return if (analyzedFps > 0.0) analyzedFps * max(1, pose.poseStride) else null
    }

    /** La serie con el signo cambiado (r y r por lado), para las fases cuando el segmentador dio la vuelta a la señal. */
    private fun negated(series: CountSeries): CountSeries = CountSeries(
        t0Us = series.t0Us,
        fs = series.fs,
        r = DoubleArray(series.r.size) { -series.r[it] },
        filled = series.filled,
        weak = series.weak,
        walk = series.walk,
        barX = series.barX,
        barY = series.barY,
        perSide = series.perSide?.mapValues { (_, values) -> DoubleArray(values.size) { -values[it] } },
        source = series.source,
        unit = series.unit,
        validFraction = series.validFraction,
        workSide = series.workSide,
    )
}
