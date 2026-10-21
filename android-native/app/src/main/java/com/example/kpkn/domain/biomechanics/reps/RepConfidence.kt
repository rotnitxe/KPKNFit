package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Desglose de la confianza del conteo (§6): los cinco factores, el número de ciclos dudosos, la puntuación, el nivel y, si
 * se forzó BAJA, por qué.
 */
data class ConfidenceBreakdown(
    val cTrack: Double,
    val cSnr: Double,
    val cAmp: Double,
    val cRhy: Double,
    val cView: Double,
    val nAmbiguous: Int,
    val score: Double,
    val level: CountConfidence,
    val forcedLowReason: String?,
)

/**
 * Los avisos que salen del conteo, sumados a los de la preparación de la traza. Quien arma el informe (VT3a) se los pasa a
 * [RepConfidence]: POLARITY_FLIPPED si el segmentador dio la vuelta a la señal; SIDE_UNKNOWN si en un perfil por lado solo
 * hay el lado cercano (no se sabe qué brazo o pierna trabaja); FAR_SIDE_WORKING si el lado que trabaja es el lejano.
 */
object RepFlags {
    fun of(prepared: PreparedTrack, series: CountSeries, seg: SegmentationResult): Set<AnalysisFlag> = buildSet {
        addAll(prepared.flags)
        if (seg.polarityFlipped) add(AnalysisFlag.POLARITY_FLIPPED)
        val near = prepared.near?.near
        val working = series.workSide
        if (working != null && near != null && working != near) {
            add(AnalysisFlag.FAR_SIDE_WORKING)
        } else if (seg.sideMode == SideMode.NEAR_ONLY) {
            add(AnalysisFlag.SIDE_UNKNOWN)
        }
    }
}

/**
 * Confianza del conteo (§6, «Confianza del conteo»), con publicación conservadora M2:
 * ```
 * c_track = clamp((fracción válida observada − 0,70)/0,25, 0, 1)
 * c_snr   = clamp(log10(Afull/σn/5)/log10(6), 0, 1)
 * c_amp   = clamp(1 − (CV_a(FULL) − 0,10)/0,30, 0, 1)
 * c_rhy   = clamp(1 − (CV_duración(FULL) − 0,30)/0,50, 0, 1)
 * c_view  = 1 si la vista ∈ countViews; 0,7 si OBLIQUE; 0,4 en otro caso
 * score   = √c_track · c_view · (0,45·c_snr + 0,30·c_amp + 0,25·c_rhy) · 0,8^n_amb
 * ALTA ⇔ score ≥ 0,75 ∧ n_amb = 0 ∧ ¬legado ∧ ¬MULTI_PERSON ∧ ¬POLARITY_FLIPPED;  MEDIA ⇔ score ≥ 0,45;  BAJA si no.
 * BAJA forzada: sin FULL, fracción válida < 0,6 o fs < 20.
 * ```
 * Además, con solo el lado cercano (SIDE_UNKNOWN) o con el lado que trabaja lejos de la cámara (FAR_SIDE_WORKING) el
 * máximo es MEDIA (§4.11 y §6 p9). Con menos de 3 FULL el CV no es de fiar y c_amp y c_rhy valen 0,5.
 * M2 fuerza BAJA con varias personas, vista no admitida, ciclos ambiguos o censura en el borde. Un single/doble
 * completamente observado y rodeado por reposo medido puede usar tracking y SNR sin imputar regularidad entre reps.
 */
object RepConfidence {
    fun score(
        series: CountSeries,
        seg: SegmentationResult,
        view: ViewEstimate,
        profile: AnalysisProfile,
        flags: Set<AnalysisFlag>,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): ConfidenceBreakdown {
        val cTrack = clamp01((series.validFraction - tuning.trackBase) / tuning.trackSpan)
        val cSnr = if (seg.noiseSigma > 0.0) {
            clamp01(log10(seg.aFull / seg.noiseSigma / tuning.snrFloor) / log10(tuning.snrSpanRatio))
        } else if (seg.aFull > 0.0) {
            1.0
        } else {
            0.0
        }

        val fulls = seg.cycles.filter { it.kind == RepKind.FULL }
        val cAmp = if (fulls.size >= tuning.minCvReps) {
            clamp01(1.0 - (coefficientOfVariation(fulls.map { it.amplitudeFraction }) - tuning.ampCvBase) / tuning.ampCvSpan)
        } else {
            tuning.neutralScore
        }
        val intervals = ArrayList<Double>()
        for (i in 1 until fulls.size) {
            if (fulls[i].miniSet != fulls[i - 1].miniSet) continue
            intervals.add((fulls[i].peak - fulls[i - 1].peak) / series.fs)
        }
        val cRhy = if (fulls.size >= tuning.minCvReps && intervals.size >= tuning.minCvReps - 1) {
            clamp01(1.0 - (coefficientOfVariation(intervals) - tuning.rhythmCvBase) / tuning.rhythmCvSpan)
        } else {
            tuning.neutralScore
        }

        val cView = when {
            view.kind in profile.countViews -> 1.0
            view.kind == ViewKind.OBLIQUE -> tuning.viewOblique
            else -> tuning.viewOther
        }
        val nAmb = seg.ambiguousCycles
        val evidence = countEvidence(series, seg, tuning)
        val repeatedScore = sqrt(cTrack) * cView *
            (tuning.snrWeight * cSnr + tuning.ampWeight * cAmp + tuning.rhythmWeight * cRhy) *
            tuning.ambiguityPenalty.pow(nAmb)
        // For a single/double, do not invent a CV. Complete observed excursions bounded by measured rest use
        // tracking and SNR directly; the neutral cAmp/cRhy remain explicit in the diagnostic breakdown.
        val score = if (evidence.closedShortSet && cTrack == 1.0 && cSnr == 1.0) sqrt(cTrack) * cView * cSnr else repeatedScore

        val forced = when {
            fulls.isEmpty() -> "no_full_reps"
            series.validFraction < tuning.minValidFraction -> "low_valid_fraction"
            series.fs < tuning.lowFpsBelow -> "low_fps"
            AnalysisFlag.MULTI_PERSON in flags -> "multiple_people"
            view.kind !in profile.countViews -> "unsupported_view"
            evidence.censoredStart -> "censored_start"
            evidence.censoredReturn -> "censored_return"
            nAmb > tuning.metrics.countReliability.maxOfferedAmbiguousCycles -> "ambiguous_cycles"
            fulls.size < tuning.minCvReps && seg.cycles.any { it.kind != RepKind.FULL } -> "uncertain_partial_cycles"
            else -> null
        }
        val polarityFlipped = seg.polarityFlipped || AnalysisFlag.POLARITY_FLIPPED in flags
        val sideCap = seg.sideMode == SideMode.NEAR_ONLY || AnalysisFlag.SIDE_UNKNOWN in flags ||
            AnalysisFlag.FAR_SIDE_WORKING in flags
        val altaAllowed = nAmb == 0 && AnalysisFlag.LEGACY_TRACK !in flags && AnalysisFlag.MULTI_PERSON !in flags &&
            !polarityFlipped && !sideCap
        val level = when {
            forced != null -> CountConfidence.BAJA
            score >= tuning.altaScore && altaAllowed -> CountConfidence.ALTA
            score >= tuning.mediaScore -> CountConfidence.MEDIA
            else -> CountConfidence.BAJA
        }
        return ConfidenceBreakdown(cTrack, cSnr, cAmp, cRhy, cView, nAmb, score, level, forced)
    }

    private fun clamp01(x: Double): Double = if (x.isNaN()) 0.0 else x.coerceIn(0.0, 1.0)

    /** Desviación típica muestral entre la media; 0 si la media es 0. */
    private fun coefficientOfVariation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (!(mean > 0.0)) return 0.0
        var s = 0.0
        for (v in values) s += (v - mean) * (v - mean)
        return sqrt(s / (values.size - 1)) / mean
    }
}
