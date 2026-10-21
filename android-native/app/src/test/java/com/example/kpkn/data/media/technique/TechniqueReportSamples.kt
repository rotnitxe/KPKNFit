package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.DepthClass
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.RepPhaseTimes
import com.example.kpkn.domain.biomechanics.metrics.RepReport
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueObservation
import com.example.kpkn.domain.biomechanics.metrics.TempoSummary
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier

/** Informes y referencias de ejemplo para las pruebas de persistencia (sidecars, resumen y repositorio). */
internal object TechniqueReportSamples {
    private fun fullRep(index: Int) = RepReport(
        index = index,
        kind = RepKind.FULL,
        side = "left",
        phases = RepPhaseTimes(departUs = 1_000_000L + index, turnStartUs = 2_500_000L, turnEndUs = 3_000_000L, arriveUs = 4_250_000L),
        eccentricMs = 1500,
        turnPauseMs = 500,
        concentricMs = 1250,
        restPauseMs = 250,
        amplitudeFraction = 0.983,
        romDeg = 101.5,
        depthClass = DepthClass.PARALLEL,
        depthDeg = 1.5,
        criteria = mapOf("lockout" to true, "shallower" to false),
        mcv = 0.4,
        peakVelocity = 0.9,
        pathOffsetMean = 3.2,
        pathDrift = -1.1,
        pathExcursionFraction = 0.12,
        pathDeviationRms = 2.5,
        trunkAtTurnDeg = 28.0,
        trunkRangeDeg = 30.5,
        trunkEarlyDeltaDeg = -3.0,
        trunkMaxDeg = 31.0,
    )

    private fun partialRep(index: Int) = RepReport(
        index = index,
        kind = RepKind.PARTIAL,
        partialKind = PartialKind.FAR_END,
        phases = RepPhaseTimes(5_000_000L, 5_500_000L, 5_500_000L, 6_000_000L),
        eccentricMs = null,
        turnPauseMs = 0,
        concentricMs = null,
        restPauseMs = null,
        amplitudeFraction = 0.55,
        romDeg = null,
    )

    val observations = listOf(
        TechniqueObservation("tronco_inclinado", "El tronco se inclinó 9° más en la última repetición que en la primera.", listOf(1, 2), 3_000_000L, 9.0, 0.8),
        TechniqueObservation("sin_instante", "Texto", emptyList(), null, 0.0, 0.0),
    )

    /** Una sentadilla de 2 repeticiones completas y 1 parcial, con disco calibrado y todas las medidas. */
    fun fullReport() = SetTechniqueReport(
        engineVersion = TechniqueAnalysisEngine.ENGINE_VERSION,
        profileId = AnalysisProfileId.SQUAT,
        tier = ProfileTier.A,
        view = ViewKind.SIDE,
        nearSide = "left",
        confidence = CountConfidence.ALTA,
        confidenceScore = 0.87,
        reps = listOf(fullRep(0), fullRep(1), partialRep(2)),
        repCount = 2,
        partialReps = 1,
        failedReps = 0,
        miniSets = 1,
        perSideCounts = mapOf("left" to 2, "right" to 0),
        tutMs = 12_500L,
        setDurationMs = 14_000L,
        restPositionMs = 750L,
        tempo = TempoSummary(1500, 500, 1250, 250),
        romMeanDeg = 101.2,
        romSdDeg = 1.8,
        romCvPct = 1.8,
        romTrendDeg = -4.5,
        velocityUnit = VelocityUnit.MPS,
        velocityLossPct = 12.5,
        mcvFirst = 0.41,
        mcvBest = 0.43,
        mcvLast = 0.376,
        peakBest = 0.92,
        scale = ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.00255, 0.97, 1.2),
        trunkMeanDeg = 28.5,
        barDeviation = 3.2,
        barDeviationUnit = "cm",
        observations = observations,
        available = setOf(MetricKey.REPS, MetricKey.PHASES, MetricKey.ROM, MetricKey.DEPTH, MetricKey.VELOCITY_MPS),
        flags = setOf(AnalysisFlag.MULTI_PERSON, AnalysisFlag.ROLL_CORRECTED, AnalysisFlag.PLATE_ELLIPSE),
        analyzedFps = 29.97,
        sourceFps = 59.94,
        noRepsReason = null,
        romSlopeDegPerRep = -1.5,
    )

    /** Un vídeo no concluyente: ninguna medida. */
    fun minimalReport() = SetTechniqueReport(
        engineVersion = 1,
        profileId = AnalysisProfileId.CALF,
        tier = ProfileTier.C,
        view = ViewKind.UNKNOWN,
        nearSide = null,
        confidence = CountConfidence.BAJA,
        confidenceScore = 0.0,
        reps = emptyList(),
        repCount = 0,
        partialReps = 0,
        failedReps = 0,
        miniSets = 0,
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
        scale = null,
        trunkMeanDeg = null,
        barDeviation = null,
        barDeviationUnit = null,
        observations = emptyList(),
        available = emptySet(),
        flags = emptySet(),
        analyzedFps = 0.0,
        sourceFps = null,
        noRepsReason = "no_pose",
    )

    fun exercise() = ExerciseRef(
        definitionId = "back_squat",
        configurationId = "back_squat__barbell",
        movementPatternId = "squat",
        equipmentId = "barbell",
        laterality = "BILATERAL",
        name = "Sentadilla trasera",
        isCustom = false,
        userProfileOverride = AnalysisProfileId.SQUAT,
        guessedMovementPatternId = null,
    )
}
