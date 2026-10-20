package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Denominator, integrity and partition regressions independent of external bank availability. */
class FullBankStatisticsTest {
    private fun row(id: String = "a", group: String = id, exam: Boolean = false, fold: Int = 0, category: String = "G1") =
        FullBankRow(buildJsonObject {
            put("id", id); put("source", "test"); put("groupKey", group); put("category", category)
            put("profile", if (category == "G3") "UNSUPPORTED" else "SQUAT"); put("benchProfile", "SQUAT")
            put("exam", exam); put("fold", fold); put("labelSha256", "a".repeat(64))
            put("equipmentId", "bodyweight"); put("forcedProfileProbe", category == "G3")
        })

    private fun truth(min: Int = 0, max: Int = 0, full: Int = min, people: Int? = 1, body: Boolean = true, view: String? = "SIDE", walking: Boolean = false) =
        FullBankTruth(full, 0, 0, min, max, view, people, body, false, walking)

    private fun emptyReport() = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(
        PoseTrackV2("synthetic", PoseEngineInfo("mediapipe-pose", "full", "CPU"), 640, 360, 0, 30f, 1, false,
            frames = (0..30).map { PoseFrameV2(it * 33_333L, 0, null, null) }),
        null, FullBankReport.resolve(row()), null, null, emptySet(),
    ))

    private fun manifest(vararg rows: FullBankRow) = buildJsonObject {
        put("validationOnly", true); put("expectedCount", rows.size); put("rows", JsonArray(rows.map { it.raw }))
    }

    @Test fun wilsonMatchesKnownBinomialBoundsAndEmptyIsNotZeroPrecision() {
        assertNull(FullBankReport.wilson(0, 0))
        val zero = FullBankReport.wilson(0, 10)!!
        assertEquals(0.0, zero.first, 1e-12)
        assertEquals(.2775327999, zero.second, 1e-9)
        val all = FullBankReport.wilson(10, 10)!!
        assertEquals(.7224672001, all.first, 1e-9)
        assertEquals(1.0, all.second, 1e-12)
    }

    @Test fun noOutputDoesNotBecomeAnExactZeroOrAnInRangeSuccess() {
        val result = FullBankResult(row(), truth(), "DONE", emptyReport())
        assertFalse(result.hasOutput)
        assertFalse(result.exact)
        assertFalse(result.inRange)
        assertEquals("SIN_SALIDA", result.level)
        val metrics = FullBankReport.metricsJson(listOf(result))
        assertEquals(0, metrics["inRange"]!!.jsonObject["denominator"]!!.jsonPrimitive.int)
        assertTrue(metrics["maeFull"] is JsonNull)
    }

    @Test fun mediaOffersANumberAndRangeToleranceIsNotExactness() {
        val report = emptyReport().copy(repCount = 8, available = setOf(MetricKey.REPS), confidence = CountConfidence.MEDIA)
        val tolerated = FullBankResult(row(), truth(7, 8, full = 7), "DONE", report)
        assertTrue(tolerated.offered)
        assertTrue(tolerated.inRange)
        assertFalse(tolerated.exact)
        val wrong = tolerated.copy(truth = truth(7, 7))
        val metrics = FullBankReport.metricsJson(listOf(tolerated, wrong))
        assertEquals(1, metrics["wrongOffered"]!!.jsonObject["numerator"]!!.jsonPrimitive.int)
        assertEquals(2, metrics["wrongAmongOffered"]!!.jsonObject["denominator"]!!.jsonPrimitive.int)
        assertEquals(1.0, metrics["maeFull"]!!.jsonPrimitive.double, 1e-12)
    }

    @Test fun bajaIsOutputButDoesNotOfferANumber() {
        val report = emptyReport().copy(repCount = 1, available = setOf(MetricKey.REPS), confidence = CountConfidence.BAJA)
        val result = FullBankResult(row(), truth(1, 1), "DONE", report)
        assertTrue(result.hasOutput)
        assertTrue(result.exact)
        assertFalse(result.offered)
    }

    @Test fun missingOrFailedExtractionIsNotSuccessfulAbstention() {
        val results = listOf(FullBankResult(row(), truth(), "MISSING_RECEIPT"), FullBankResult(row("b"), truth(), "EXTRACTION_FAILED"))
        val metrics = FullBankReport.metricsJson(results)
        assertEquals(2, metrics["missingOrFailed"]!!.jsonPrimitive.int)
        assertEquals(0, metrics["levels"]!!.jsonObject["SIN_SALIDA"]!!.jsonObject["distribution"]!!.jsonObject["denominator"]!!.jsonPrimitive.int)
    }

    @Test fun groupCannotCrossExamOrDevelopmentFolds() {
        assertThrows(IllegalArgumentException::class.java) { FullBankReport.rows(manifest(row("a", "same"), row("b", "same", exam = true))) }
        assertThrows(IllegalArgumentException::class.java) { FullBankReport.rows(manifest(row("a", "same"), row("b", "same", fold = 1))) }
        assertEquals(2, FullBankReport.rows(manifest(row("a", "same"), row("b", "same"))).size)
    }

    @Test fun defaultSelectionExcludesExamAndExplicitExamExcludesDevelopment() {
        val development = row("dev")
        val examination = row("exam", exam = true)
        val rows = FullBankReport.rows(manifest(development, examination))
        assertEquals(listOf(development), FullBankReport.select(rows, exam = false))
        assertEquals(listOf(examination), FullBankReport.select(rows, exam = true))
    }

    @Test fun duplicateIdsAndInvalidFoldsFailBeforeInputsAreOpened() {
        assertThrows(IllegalArgumentException::class.java) { FullBankReport.rows(manifest(row(), row())) }
        assertThrows(IllegalArgumentException::class.java) { FullBankReport.rows(manifest(row(fold = 5))) }
    }

    @Test fun goodFramingRequiresAdmittedLabelViewWholeBodyAndOnePerson() {
        val profile = FullBankReport.resolve(row())
        assertTrue(FullBankReport.goodFraming(truth(), profile))
        assertFalse(FullBankReport.goodFraming(truth(people = 2), profile))
        assertFalse(FullBankReport.goodFraming(truth(people = null), profile))
        assertFalse(FullBankReport.goodFraming(truth(body = false), profile))
        assertFalse(FullBankReport.goodFraming(truth(view = "BACK"), profile))
        assertFalse(FullBankReport.goodFraming(truth(view = null), profile))
    }

    @Test fun g3TransportProbeCannotValidateUnsupportedResolver() {
        val result = FullBankResult(row(category = "G3"), truth(), "DONE", emptyReport())
        val metrics = FullBankReport.metricsJson(listOf(result))
        assertFalse(metrics["g3ResolverValidated"]!!.jsonPrimitive.boolean)
        assertEquals(1, metrics["g3Offered"]!!.jsonObject["denominator"]!!.jsonPrimitive.int)
    }

    @Test fun walkingLungeRequiresNoRepsIncludingNoPartialOutput() {
        val base = FullBankResult(row(), truth(walking = true), "DONE", emptyReport())
        val partial = base.copy(report = emptyReport().copy(available = setOf(MetricKey.REPS), partialReps = 1))
        val stats = FullBankReport.metricsJson(listOf(base, partial))["walkingNoReps"]!!.jsonObject
        assertEquals(1, stats["numerator"]!!.jsonPrimitive.int)
        assertEquals(2, stats["denominator"]!!.jsonPrimitive.int)
    }

    @Test fun frameRateBandsKeepLowFpsAndUnknownSeparate() {
        assertEquals("unknown", FullBankReport.fpsBand(null))
        assertEquals("unknown", FullBankReport.fpsBand(Double.NaN))
        assertEquals("<20", FullBankReport.fpsBand(19.98))
        assertEquals("20-<30", FullBankReport.fpsBand(29.97))
        assertEquals("30-<50", FullBankReport.fpsBand(30.0))
        assertEquals(">=50", FullBankReport.fpsBand(59.94))
    }
}
