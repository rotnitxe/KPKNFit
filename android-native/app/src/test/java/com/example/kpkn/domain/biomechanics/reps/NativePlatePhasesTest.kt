package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.bar.PlateSeed

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.MetricContext
import com.example.kpkn.domain.biomechanics.metrics.MetricSeriesBuilder
import com.example.kpkn.domain.biomechanics.metrics.NativePlateRelativeVelocity
import com.example.kpkn.domain.biomechanics.metrics.RelativeVelocity
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.vbt.CalibratedVelocity
import com.example.kpkn.domain.biomechanics.vbt.PlateVerticalSeries
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Analytic motion written independently of SyntheticLifter, corpus labels and MovementPieces. */
class NativePlatePhasesTest {
    private val origin = 12_345_000L
    private val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
    private val truths = listOf(Truth(1.113), Truth(4.617))

    private data class Truth(val depart: Double) {
        val turnStart = depart + 1.3
        val turnEnd = turnStart + 0.417
        val arrive = turnEnd + 1.1
    }

    private fun ease(t: Double): Double {
        val u = t.coerceIn(0.0, 1.0)
        return 10 * u * u * u - 15 * u * u * u * u + 6 * u * u * u * u * u
    }

    private fun progress(t: Double): Double {
        for (truth in truths) {
            if (t < truth.depart || t > truth.arrive) continue
            return when {
                t < truth.turnStart -> ease((t - truth.depart) / 1.3)
                t < truth.turnEnd -> 1.0
                else -> 1.0 - ease((t - truth.turnEnd) / 1.1)
            }
        }
        return 0.0
    }

    private fun bar(fps: Double = 60.0): BarTrack = BarTrack(
        mediaId = "independent-native-clock", frameWidth = 720, frameHeight = 1280,
        plateDiameterMm = 450, seed = PlateSeed(origin, 300f, 500f, 60f, true),
        trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED,
        samples = (0..(9.0 * fps).roundToInt()).map { k ->
            val seconds = k / fps
            BarSample(origin + (seconds * 1e6).roundToLong(), 300f,
                (500.0 - 220.0 * progress(seconds)).toFloat(), 60f, 1f, 0.99f, BarSampleSource.HOUGH)
        },
    )

    private fun series(fps: Double, source: String = CountSeriesBuilder.SOURCE_PLATE): CountSeries {
        val r = DoubleArray((9.0 * fps).roundToInt() + 1) { progress(it / fps) }
        return CountSeries(origin, fps, r, BooleanArray(r.size), BooleanArray(r.size),
            BooleanArray(r.size), null, null, null, source, SignalUnit.RATIO, 1.0)
    }

    // Existing coarse cycle identities are fixture inputs; native refinement must not segment again.
    private fun segmentation(fps: Double, shiftFrames: Int = 0): SegmentationResult {
        fun index(s: Double) = (s * fps).roundToInt() + shiftFrames
        return SegmentationResult(
            cycles = truths.mapIndexed { i, truth ->
                RepCycle(41 + i, RepKind.FULL, null, null,
                    index(if (i == 0) 0.5 else 4.25), index(truth.turnStart + 0.2),
                    index(if (i == 0) 4.25 else 8.5), 1.0, false)
            }, discarded = emptyList(), activeWindow = index(0.5)..index(8.5), miniSets = 1,
            periodS = 3.504, noiseSigma = 0.0, aFull = 1.0, lRest = 0.0,
            sideMode = SideMode.NOT_APPLICABLE, perSideCounts = emptyMap(),
            polarityFlipped = false, noRepsReason = null,
        )
    }

    private fun assertKnownPhases(summary: PhaseSummary, toleranceUs: Long = 18_667L) {
        assertEquals(2, summary.reps.size)
        for ((i, event) in summary.reps.withIndex()) {
            val truth = truths[i]
            assertEquals(41 + i, event.cycleIndex)
            for ((actual, expected) in listOf(event.departUs to truth.depart,
                event.turnStartUs to truth.turnStart, event.turnEndUs to truth.turnEnd,
                event.arriveUs to truth.arrive)) {
                assertTrue("event=$actual expected=${origin + (expected * 1e6).roundToLong()}",
                    abs(actual - origin - (expected * 1e6).roundToLong()) <= toleranceUs)
            }
            assertEquals(1300.0, event.concentricMs!!.toDouble(), 2 * toleranceUs / 1000.0)
            assertEquals(1100.0, event.eccentricMs!!.toDouble(), 2 * toleranceUs / 1000.0)
            assertEquals(417.0, event.turnPauseMs!!.toDouble(), 2 * toleranceUs / 1000.0)
        }
        assertEquals(5634.0, summary.tutMs!!.toDouble(), 8 * toleranceUs / 1000.0)
    }

    @Test fun plate60Pose30AndPlate60Pose15KeepNativeAbsoluteEventsAndPoseIndices() {
        val disk = bar()
        val samplesBefore = disk.samples.toList()
        for (fps in listOf(30.0, 15.0)) {
            val count = series(fps)
            val before = count.r.copyOf()
            val seg = segmentation(fps)
            val cyclesBefore = seg.cycles.toList()
            val phases = NativePlatePhases.compute(count, seg, profile, disk, 0.0)
            assertKnownPhases(phases)
            assertArrayEquals(before, count.r, 0.0)
            assertEquals(fps, count.fs, 0.0)
            assertEquals(cyclesBefore, seg.cycles)
            assertEquals(samplesBefore, disk.samples)
            // Without native refinement, the pose15 path intentionally has no phases.
            if (fps == 15.0) assertTrue(RepPhases.compute(count, seg, profile).reps.isEmpty())
        }
    }

    @Test fun nativeClockIsNotShiftedToMatchTheHandClock() {
        for (fps in listOf(30.0, 15.0)) for (lag in listOf(-3, 0, 3)) {
            assertKnownPhases(NativePlatePhases.compute(series(fps), segmentation(fps, lag), profile, bar(), 0.0))
        }
    }

    @Test fun reversedPolarityAndEccentricFirstKeepTheirPhysicalPhaseAssignment() {
        val count = series(30.0)
        val disk = bar()
        val flipped = segmentation(30.0).copy(polarityFlipped = true)
        // PLATE_DROP with flipped segmentation must orient the physical rising plate as before.
        val drop = profile.copy(plateSignal = com.example.kpkn.domain.biomechanics.profiles.CountSignal.PLATE_DROP,
            startPhase = StartPhase.ECCENTRIC_FIRST)
        val result = NativePlatePhases.compute(count, flipped, drop, disk, 0.0)
        assertEquals(2, result.reps.size)
        assertEquals(1300.0, result.reps.first().eccentricMs!!.toDouble(), 37.334)
        assertEquals(1100.0, result.reps.first().concentricMs!!.toDouble(), 37.334)
    }

    @Test fun longMissingPtsAndPredictedPositionsNeverCreateTimedCyclesOrPartialSetTut() {
        val original = bar()
        val from = origin + 1_400_000L
        val until = origin + 1_800_000L
        val missingPts = original.copy(samples = original.samples.filterNot { it.tUs in from..until })
        val predicted = original.copy(samples = original.samples.map {
            if (it.tUs in from..until) it.copy(source = BarSampleSource.PREDICTED) else it
        })
        val prior = predicted.copy(samples = predicted.samples.map {
            if (it.source == BarSampleSource.PREDICTED) it.copy(source = BarSampleSource.POSE_PRIOR) else it
        })
        for (disk in listOf(missingPts, predicted, prior)) {
            val before = disk.samples.toList()
            val phases = NativePlatePhases.compute(series(15.0), segmentation(15.0), profile, disk, 0.0)
            assertTrue(phases.reps.isEmpty())
            assertNull(phases.tutMs)
            assertNull(phases.tempo)
            assertEquals(before, disk.samples)
        }
    }

    @Test fun aShortGapUsesOnlyTheExistingPlateGapBudget() {
        val original = bar()
        val disk = original.copy(samples = original.samples.map {
            if (it.tUs in origin + 1_500_000L..origin + 1_533_334L)
                it.copy(source = BarSampleSource.PREDICTED) else it
        })
        assertKnownPhases(NativePlatePhases.compute(series(30.0), segmentation(30.0), profile, disk, 0.0), 50_000L)
        assertEquals(original.samples.size, disk.samples.size)
    }

    @Test fun plateVerticalSeriesNeverMeasuresAcrossOmittedTimestampSpansOverPointOneSeconds() {
        val original = bar()
        // Removing six 60-Hz slots gives >0.1 s between finite neighbours; slot-count filling
        // must not erase that elapsed-time cut. Also cover the original 0.15-0.45 s defect.
        for (gapUs in listOf(100_000L, 150_000L, 300_000L, 450_000L)) {
            val from = origin + 1_400_000L
            val disk = original.copy(samples = original.samples.filterNot { it.tUs in from until from + gapUs })
            val before = disk.samples.toList()
            val native = PlateVerticalSeries.of(disk, 0.0)!!
            assertTrue(native.valueAt(from + gapUs / 2).isNaN())
            assertTrue(native.meanSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025, 0.1).isNaN())
            assertTrue(native.peakSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025).isNaN())
            assertEquals(before, disk.samples)
        }
    }

    @Test fun plateVerticalSeriesNeverMeasuresAcrossPredictedOrPosePriorSpansOverPointOneSeconds() {
        val original = bar()
        for (source in listOf(BarSampleSource.PREDICTED, BarSampleSource.POSE_PRIOR)) {
            val disk = original.copy(samples = original.samples.map {
                if (it.tUs in origin + 1_400_000L until origin + 1_700_000L) it.copy(source = source) else it
            })
            val before = disk.samples.toList()
            val native = PlateVerticalSeries.of(disk, 0.0)!!
            assertTrue(native.valueAt(origin + 1_550_000L).isNaN())
            assertTrue(native.meanSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025, 0.1).isNaN())
            assertTrue(native.peakSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025).isNaN())
            assertEquals(before, disk.samples)
        }
    }

    @Test fun plateVerticalSeriesAllowsShortOmittedPtsAndShortDetectorGapsWithoutChangingSamples() {
        val original = bar()
        val from = origin + 1_500_000L
        val to = origin + 1_533_334L
        val missing = original.copy(samples = original.samples.filterNot { it.tUs in from..to })
        val predicted = original.copy(samples = original.samples.map {
            if (it.tUs in from..to) it.copy(source = BarSampleSource.PREDICTED) else it
        })
        val prior = original.copy(samples = original.samples.map {
            if (it.tUs in from..to) it.copy(source = BarSampleSource.POSE_PRIOR) else it
        })
        for (disk in listOf(missing, predicted, prior)) {
            val before = disk.samples.toList()
            val native = PlateVerticalSeries.of(disk, 0.0)!!
            assertTrue(native.valueAt(origin + 1_516_667L).isFinite())
            assertEquals(220.0 * 0.0025 / 1.3,
                native.meanSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025, 0.1), 0.02)
            assertTrue(native.peakSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025).isFinite())
            assertEquals(before, disk.samples)
        }
    }

    @Test fun invalidGeometryAndNonFiniteConversionsAbstainInsteadOfReturningInfinity() {
        val original = bar()
        assertNull(PlateVerticalSeries.of(original.copy(frameWidth = 0), 0.0))
        assertNull(PlateVerticalSeries.of(original.copy(frameHeight = -1), 0.0))
        assertNull(PlateVerticalSeries.of(original, Double.NaN))
        assertNull(PlateVerticalSeries.of(original, Double.POSITIVE_INFINITY))
        val native = PlateVerticalSeries.of(original, 0.0)!!
        for (factor in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            assertTrue(native.meanSpeed(origin + 1_113_000L, origin + 2_413_000L, factor, 0.1).isNaN())
            assertTrue(native.peakSpeed(origin + 1_113_000L, origin + 2_413_000L, factor).isNaN())
        }
        val invalidCoordinates = original.copy(samples = original.samples.map {
            if (it.tUs in origin + 1_400_000L until origin + 1_700_000L)
                it.copy(cx = Float.POSITIVE_INFINITY) else it
        })
        assertTrue(PlateVerticalSeries.of(invalidCoordinates, 0.0)!!
            .meanSpeed(origin + 1_113_000L, origin + 2_413_000L, 0.0025, 0.1).isNaN())
    }

    @Test fun aTruncatedReturnOrUnsupportedNativeCadenceAbstainsWithoutFallbackTimings() {
        val seg = segmentation(30.0)
        val truncated = seg.copy(cycles = seg.cycles.mapIndexed { i, c -> if (i == 1) c.copy(rightValley = null) else c })
        for ((s, b) in listOf(truncated to bar(), seg to bar(15.0))) {
            val phases = NativePlatePhases.compute(series(30.0), s, profile, b, 0.0)
            assertTrue(phases.reps.isEmpty())
            assertNull(phases.tutMs)
        }
    }

    @Test fun withoutPlateThePoseOnlyPathIsExactlyTheExistingEstimator() {
        for (fps in listOf(30.0, 15.0)) {
            val count = series(fps, CountSeriesBuilder.SOURCE_POSE)
            val seg = segmentation(fps)
            assertEquals(RepPhases.compute(count, seg, profile),
                NativePlatePhases.compute(count, seg, profile, null, 0.0))
        }
    }

    @Test fun metricConsumersContinueConvertingNativePtsToThePoseGrid() {
        for (fps in listOf(30.0, 15.0)) {
            val synthetic = SyntheticLifter.generate(SynthConfig(SynthMovement.DEADLIFT, fps = fps,
                noise = SynthNoise.NONE, script = listOf(SynthSegment.Idle(9.0))))
            val track = synthetic.track.copy(frames = synthetic.track.frames.map { frame ->
                // Preserve every point and world coordinate; only translate its timestamp.
                com.example.kpkn.domain.biomechanics.pose.PoseFrameV2(frame.tUs + origin, frame.posesDetected,
                    com.example.kpkn.domain.biomechanics.pose.PoseLandmark.KEPT_IMAGE.mapNotNull { lm -> frame.point(lm)?.let { lm to it } }.toMap(),
                    com.example.kpkn.domain.biomechanics.pose.PoseLandmark.KEPT_WORLD.mapNotNull { lm -> frame.world(lm)?.let { lm to it } }.toMap())
            })
            val prepared = TrackPreparation.prepare(track)
            val count = series(fps)
            val seg = segmentation(fps)
            val phases = NativePlatePhases.compute(count, seg, profile, bar(), 0.0)
            val ctx = MetricContext(prepared, count, seg, phases, profile)
            val poseValues = DoubleArray(track.frames.size) { (track.frames[it].tUs - origin) / 1e6 }
            val metric = MetricSeriesBuilder.fromFrames(prepared, poseValues, AnalysisTuning.DEFAULT)
            assertEquals(fps, metric.fs, 0.01)
            for ((i, cycle) in seg.cycles.withIndex()) {
                val event = phases.reps[i]
                assertSame(event, ctx.phasesOf(cycle))
                assertEquals((event.turnStartUs - origin) / 1e6 * fps, ctx.indexOf(event.turnStartUs), 1e-6)
                val window = ctx.turnWindow(cycle)!!
                assertTrue(window.last < metric.size)
                assertTrue(ctx.indexOf(event.turnStartUs) >= window.first)
                assertTrue(ctx.indexOf(event.turnEndUs) <= window.last)
                assertEquals((event.turnStartUs - origin) / 1e6, metric.valueAt(event.turnStartUs), 0.03)
                val con = ctx.concentric(cycle)!!
                assertEquals(event.departUs, con.fromUs)
                assertEquals(event.turnStartUs, con.toUs)
            }
            // Native plate timing must not accidentally enable 15 Hz POSE velocity.
            if (fps == 15.0) assertFalse(RelativeVelocity.compute(ctx).available)
            val relative = NativePlateRelativeVelocity.compute(ctx, bar().copy(plateDiameterMm = null))
            assertTrue(relative.available)
            assertEquals(VelocityUnit.BODY_HEIGHTS_PER_S, relative.unit)
            assertFalse(relative.peakAvailable)
            assertNull(relative.lossPct) // Two FULL: preserve the existing three-FULL VL guard.
            val expectedBhSpeed = 220.0 / (1280.0 * ctx.bodyHeightFh!!) / 1.3
            assertEquals(expectedBhSpeed, relative.reps.first().mcv, 0.02)
            val calibrated = CalibratedVelocity.compute(ctx, bar(),
                ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.0025, 1.0, 0.0))
            assertTrue(calibrated.available)
            assertEquals(VelocityUnit.MPS, calibrated.unit)
            assertEquals(220.0 * 0.0025 / 1.3, calibrated.reps.first().mcv, 0.02)
            // Both velocity paths use the native clock, including when pose remains at 15 Hz.
            assertEquals(relative.reps.first().mcv / expectedBhSpeed,
                calibrated.reps.first().mcv / (220.0 * 0.0025 / 1.3), 0.01)
            for (invalidDimension in listOf(bar().copy(frameWidth = 0), bar().copy(frameHeight = 0))) {
                assertFalse(NativePlateRelativeVelocity.compute(ctx, invalidDimension).available)
                assertFalse(CalibratedVelocity.compute(ctx, invalidDimension,
                    ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.0025, 1.0, 0.0)).available)
            }
            assertFalse(CalibratedVelocity.compute(ctx, bar(),
                ScaleInfo(ScaleSource.PLATE, 450, 0.0025, Double.POSITIVE_INFINITY, 1.0, 0.0)).available)
            val baseScale = prepared.scale!!
            for (badHeight in listOf(Double.POSITIVE_INFINITY, Double.NaN, 0.0)) {
                val invalidPrepared = PreparedTrack(prepared.track, prepared.geo, prepared.timebase,
                    baseScale.copy(bodyHeightFh = badHeight), prepared.near, prepared.facing, prepared.view, prepared.flags)
                val invalidCtx = MetricContext(invalidPrepared, count, seg, phases, profile)
                assertFalse(NativePlateRelativeVelocity.compute(invalidCtx, bar()).available)
            }
            // A preserved coarse/native phase fixture cannot make the velocity path bridge missing PTS.
            val missing = bar().copy(samples = bar().samples.filterNot {
                it.tUs in origin + 1_400_000L until origin + 1_700_000L
            })
            val relativeMissing = NativePlateRelativeVelocity.compute(ctx, missing)
            val calibratedMissing = CalibratedVelocity.compute(ctx, missing,
                ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.0025, 1.0, 0.0))
            assertTrue(relativeMissing.reps.first().mcv.isNaN())
            assertTrue(calibratedMissing.reps.first().mcv.isNaN())
        }
    }
}
