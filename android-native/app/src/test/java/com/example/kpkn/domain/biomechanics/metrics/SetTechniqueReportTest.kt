package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El informe de la serie (§3.5) viaja como JSON en `<id>.analysis.json`: ida y vuelta sin perder nada. */
class SetTechniqueReportTest {
    /** Cómo lo escribe el sidecar: con los valores por defecto incluidos. */
    private val sidecar = Json { encodeDefaults = true }

    private fun roundTrip(report: SetTechniqueReport, json: Json = sidecar): SetTechniqueReport =
        json.decodeFromString(SetTechniqueReport.serializer(), json.encodeToString(SetTechniqueReport.serializer(), report))

    private fun fullRep(index: Int) = RepReport(
        index = index,
        kind = RepKind.FULL,
        partialKind = null,
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
        criteria = mapOf("chinOverBar" to true, "fullHang" to false, "lockout" to true, "pushUpFull" to true, "dipParallel" to false, "shallower" to false),
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
        side = null,
        phases = RepPhaseTimes(5_000_000L, 5_500_000L, 5_500_000L, 6_000_000L),
        eccentricMs = null,
        turnPauseMs = 0,
        concentricMs = null,
        restPauseMs = null,
        amplitudeFraction = 0.55,
        romDeg = null,
    )

    private fun fullReport() = SetTechniqueReport(
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
        velocityUnit = VelocityUnit.BODY_HEIGHTS_PER_S,
        velocityLossPct = 12.5,
        mcvFirst = 0.41,
        mcvBest = 0.43,
        mcvLast = 0.376,
        peakBest = 0.92,
        scale = ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.00255, 0.97, 1.2),
        trunkMeanDeg = 28.5,
        barDeviation = 3.2,
        barDeviationUnit = "%ROM",
        observations = listOf(
            TechniqueObservation("tronco_inclinado", "El tronco se inclinó 9° más en la última repetición que en la primera.", listOf(1, 2), 3_000_000L, 9.0, 0.8),
            TechniqueObservation("sin_instante", "Texto", emptyList(), null, 0.0, 0.0),
        ),
        available = setOf(MetricKey.REPS, MetricKey.PHASES, MetricKey.ROM, MetricKey.DEPTH, MetricKey.PATH_ABSOLUTE, MetricKey.VELOCITY_MPS, MetricKey.SYMMETRY),
        flags = setOf(AnalysisFlag.MULTI_PERSON, AnalysisFlag.ROLL_CORRECTED, AnalysisFlag.PLATE_ELLIPSE),
        analyzedFps = 29.97,
        sourceFps = 59.94,
        noRepsReason = "no_full_reps",
        romSlopeDegPerRep = -1.5,
    )

    private fun minimalReport() = SetTechniqueReport(
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
    )

    @Test
    fun aFullReportSurvivesTheRoundTrip() {
        val report = fullReport()
        assertEquals(report, roundTrip(report))
        // Con y sin los valores por defecto en el JSON.
        assertEquals(report, roundTrip(report, Json))
        assertEquals(report, roundTrip(report, Json { encodeDefaults = false; prettyPrint = true }))
    }

    @Test
    fun aMinimalReportSurvivesTheRoundTripAndKeepsItsDefaults() {
        val report = minimalReport()
        assertEquals(report, roundTrip(report))
        assertEquals(emptyMap<String, Int>(), report.perSideCounts)
        assertEquals(null, report.noRepsReason)
        assertEquals(null, report.romSlopeDegPerRep)
    }

    @Test
    fun enumsAreStoredByName() {
        val text = sidecar.encodeToString(SetTechniqueReport.serializer(), fullReport())
        for (name in listOf("\"SQUAT\"", "\"ALTA\"", "\"SIDE\"", "\"BODY_HEIGHTS_PER_S\"", "\"PARALLEL\"", "\"FAR_END\"", "\"PATH_ABSOLUTE\"", "\"PLATE_ELLIPSE\"", "\"PLATE\"")) {
            assertTrue("falta $name en $text", name in text)
        }
    }

    @Test
    fun anOldReportWithoutTheFieldsAddedByVt3aStillReads() {
        // Un informe sin los campos que VT3a añadió al final (noRepsReason, romSlopeDegPerRep y trunkMaxDeg) se lee con sus valores por defecto.
        val text = Json.encodeToString(SetTechniqueReport.serializer(), fullReport())
        val old = text
            .replace(Regex(",\"noRepsReason\":\"[^\"]*\""), "")
            .replace(Regex(",\"romSlopeDegPerRep\":[^,}]*"), "")
            .replace(Regex(",\"trunkMaxDeg\":[^,}]*"), "")
        assertFalse("noRepsReason" in old || "romSlopeDegPerRep" in old || "trunkMaxDeg" in old)
        val back = Json.decodeFromString(SetTechniqueReport.serializer(), old)
        assertEquals(null, back.noRepsReason)
        assertEquals(null, back.romSlopeDegPerRep)
        assertEquals(null, back.reps.first().trunkMaxDeg)
        assertEquals(fullReport().copy(noRepsReason = null, romSlopeDegPerRep = null, reps = fullReport().reps.map { it.copy(trunkMaxDeg = null) }), back)
    }

    @Test
    fun theTempoLabelAndThePhaseTimesKeepTheirValues() {
        val back = roundTrip(fullReport())
        assertEquals("2-1-1-0", back.tempo?.label())
        assertEquals(RepPhaseTimes(1_000_000L, 2_500_000L, 3_000_000L, 4_250_000L), back.reps.first().phases)
        assertEquals(mapOf("left" to 2, "right" to 0), back.perSideCounts)
        assertEquals(setOf(AnalysisFlag.MULTI_PERSON, AnalysisFlag.ROLL_CORRECTED, AnalysisFlag.PLATE_ELLIPSE), back.flags)
    }
}
