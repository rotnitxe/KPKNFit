package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy
import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Run with KPKN_FULL_BANK_DIR + KPKN_FULL_BANK_OUT, using ONLY the optional FullBank* filter.
 * EXAM=1 selects the final examination only and consumes a durable one-shot marker.
 * Never select the old GoldenRepCount/Timing final-holdout tests for VT11a validation.
 */
class FullBankReportTest {
    @Test
    fun optionalFullBankReport() {
        val directory = System.getenv("KPKN_FULL_BANK_DIR")
        val output = System.getenv("KPKN_FULL_BANK_OUT")
        assumeTrue("Banco completo opcional: configura ambas rutas externas", !directory.isNullOrBlank() && !output.isNullOrBlank())
        val results = FullBankReport.run(File(requireNotNull(directory)), File(requireNotNull(output)), System.getenv("KPKN_FULL_BANK_EXAM") == "1")
        assertTrue("No se procesó ninguna entrada", results.any { it.processed })
        // Historical validation must be complete; missing traces cannot improve coverage/precision.
        if (FullBankReport.readObject(File(directory, "manifest.json")).b("validationOnly")) {
            assertEquals(475, results.size)
            assertTrue(results.all { it.processed })
            assertEquals(59, results.count { it.row.raw.optional("validationSet") == "vt10-tuning" })
            assertEquals(416, results.count { it.row.raw.optional("validationSet") == "vt10-external" })
        }
    }

    /** Explain the sole external VT10 count difference without changing any product policy.
     * A temporary in-memory confirmation is a causal counterfactual, not real provenance.
     */
    @Test
    fun historicalAutomaticPlateIsNowRejectedAndPoseExplainsTheDifference() {
        val directory = System.getenv("KPKN_FULL_BANK_DIR")
        assumeTrue(!directory.isNullOrBlank())
        val root = File(requireNotNull(directory))
        val manifest = FullBankReport.readObject(File(root, "manifest.json"))
        assumeTrue("Este control solo usa el manifiesto histórico autorizado", manifest.b("validationOnly"))
        val row = FullBankRow(manifest.getValue("rows").jsonArray.map { it.jsonObject }.single { it.s("id") == "kaggle_romanian_deadlift_14" })
        fun verified(kind: String): File = FullBankReport.path(root, row.raw.s(kind + "Path")).also {
            assertEquals(row.raw.s(kind + "Sha256"), FullBankReport.sha256(it))
        }
        val pose = requireNotNull(PoseTrackCodec.decode(verified("pose").readBytes()))
        val bar = requireNotNull(BarTrackCodec.decode(verified("bar").readBytes()))
        val croppedPose = pose.copy(frames = pose.frames.filter { it.tUs in 0L..13_100_000L })
        val croppedBar = bar.copy(samples = bar.samples.filter { it.tUs in 0L..13_100_000L })
        val profile = FullBankReport.resolve(row)
        val prepared = TrackPreparation.prepare(croppedPose)
        assertFalse(PlatePublicationPolicy.canUsePlate(croppedBar))
        assertFalse(requireNotNull(croppedBar.seed).userConfirmed)
        assertEquals(PlateTrackingContinuity.UNKNOWN, croppedBar.trackingContinuity)
        val current = CountSeriesBuilder.build(prepared, croppedBar, profile) as CountSeriesResult.Ok
        assertEquals(CountSeriesBuilder.SOURCE_POSE, current.series.source)
        val currentCounts = RepSegmenter.segment(current.series, profile, emptySet())
        assertEquals(1, currentCounts.fullCount)
        assertEquals(1, currentCounts.partialCount)
        // Same samples, timestamps and profile. Only remove the NEW eligibility veto in memory.
        val simulatedEligible = croppedBar.copy(seed = croppedBar.seed!!.copy(userConfirmed = true),
            trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED, continuityBreakUs = null)
        val counterfactual = CountSeriesBuilder.build(prepared, simulatedEligible, profile) as CountSeriesResult.Ok
        assertEquals(CountSeriesBuilder.SOURCE_PLATE, counterfactual.series.source)
        val legacyCounts = RepSegmenter.segment(counterfactual.series, profile, emptySet())
        assertEquals(2, legacyCounts.fullCount)
        assertEquals(0, legacyCounts.partialCount)
    }
}
