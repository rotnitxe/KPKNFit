package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import android.app.Application
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.video.VideoDecodeException
import com.example.kpkn.data.media.video.VideoDecodePath
import com.example.kpkn.data.media.video.VideoFrameSource
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.vbt.VbtTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.vision.SampledPath
import com.example.kpkn.domain.biomechanics.vision.SceneOptions
import com.example.kpkn.domain.biomechanics.vision.SyntheticPlateFrames
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El pipeline del analizador (VT4c) con un vídeo y un estimador de pose FALSOS que reparten los huecos como el pool real:
 * una sentadilla sintética pasa por la decodificación solapada con la pose, los sidecars, los puntos de control, la cancelación,
 * la pausa de cámara y el disco (dibujado, con las muñecas de la sentadilla).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueAnalysisPipelineTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val set = PipelineTestKit.squat()
    private val times = set.track.frames.map { it.tUs }
    private val sources = CopyOnWriteArrayList<FakeFrameSource>()
    private val estimators = CopyOnWriteArrayList<FakePoseEstimator>()
    private val stats = CopyOnWriteArrayList<PipelineRunStats>()

    private class Result(val outcome: TechniqueAnalysisOutcome, val progress: List<Int>)

    private fun source(
        path: VideoDecodePath = VideoDecodePath.GL,
        failAfter: Int = -1,
        failure: Throwable? = null,
        hdr: Boolean = false,
    ): FakeFrameSource =
        FakeFrameSource(PipelineTestKit.videoInfo(406, 720, times.last(), hdr = hdr), times, path, null, failAfter, failure).also { sources.add(it) }

    private fun estimator(failEvery: Int = 0, failAlways: Boolean = false): FakePoseEstimator =
        FakePoseEstimator(set.track.frames, failEvery, failAlways).also { estimators.add(it) }

    private fun pipeline(
        open: (File) -> VideoFrameSource = { source() },
        pose: () -> PoseEstimator = { estimator() },
        tuning: PipelineTuning = PipelineTuning(),
        gateIsClosed: () -> Boolean = { false },
        awaitGate: suspend () -> Unit = {},
        sdkInt: Int = 34,
        recommendedStride: Int = 1,
    ) = TechniqueAnalysisPipeline(
        PipelineTestKit.deps(open, pose, gateIsClosed = gateIsClosed, awaitGate = awaitGate, onStats = { stats.add(it) }, sdkInt = sdkInt)
            .copy(recommendedPoseStride = { recommendedStride }),
        tuning,
    )

    private fun analyze(pipeline: TechniqueAnalysisPipeline, request: TechniqueAnalysisRequest): Result = runBlocking {
        val progress = CopyOnWriteArrayList<Int>()
        val outcome = withTimeout(TIMEOUT_MS) { pipeline.analyze(request) { progress.add(it) } }
        Result(outcome, progress.toList())
    }

    private fun done(outcome: TechniqueAnalysisOutcome): TechniqueAnalysisOutcome.Done =
        outcome as? TechniqueAnalysisOutcome.Done ?: error("esperaba Done y fue $outcome")

    private fun failed(outcome: TechniqueAnalysisOutcome): TechniqueAnalysisOutcome.Failed =
        outcome as? TechniqueAnalysisOutcome.Failed ?: error("esperaba Failed y fue $outcome")

    private fun storedPose(video: File): PoseTrackV2 =
        checkNotNull(PoseTrackCodec.decode(TechniqueSidecars.poseFile(video).readBytes())) { "pose2 ilegible" }

    private fun writeStrideTwoCheckpoint(video: File, track: PoseTrackV2, ptsScale: Long = 1L): AnalysisCheckpoint {
        val nextIndex = 101
        val partial = track.copy(
            mediaId = video.nameWithoutExtension,
            nominalFps = track.nominalFps / 2f,
            poseStride = 2,
            frames = track.frames.take(nextIndex).filterIndexed { index, _ -> index % 2 == 0 },
        )
        val header = AnalysisCheckpoint.Header(
            mediaId = video.nameWithoutExtension, poseVersion = TechniqueAnalysisPipeline.POSE_VERSION,
            videoBytes = video.length(), videoModifiedMs = video.lastModified(), usesPlate = false,
            poseStride = 2, timeScale = 1.0 / ptsScale, frameWidth = track.frameWidth, frameHeight = track.frameHeight,
            lastPtsUs = track.frames[nextIndex - 1].tUs * ptsScale, nextIndex = nextIndex, plateStage = AnalysisCheckpoint.STAGE_NONE,
        )
        return AnalysisCheckpoint(header, partial, null).also { AnalysisCheckpoints.write(TechniqueSidecars.partFile(video), it) }
    }

    private fun profile(equipment: String? = null): AnalysisProfile =
        (AnalysisProfileResolver.resolve(PipelineTestKit.exercise(equipmentId = equipment)) as ProfileResolution.Supported).profile

    private fun engineOn(track: PoseTrackV2, bar: BarTrack? = null, equipment: String? = null) =
        TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(track, bar, profile(equipment), null, null, emptySet()))

    // ---- FULL ----------------------------------------------------------------------------------------------------------

    @Test
    fun degradedDevicePreservesEveryPoseAndPhasesAt25And30Fps() {
        for (fps in listOf(25.0, 30.0)) {
            val synthetic = PipelineTestKit.squat(reps = 3, fps = fps, noise = SynthNoise.NONE)
            val pts = synthetic.track.frames.map { it.tUs }
            val video = PipelineTestKit.videoFile(folder.root, "degraded-${fps.toInt()}.mp4")
            val src = FakeFrameSource(PipelineTestKit.videoInfo(406, 720, pts.last(), fps = fps.toFloat()), pts)
                .also { sources.add(it) }
            val est = FakePoseEstimator(synthetic.track.frames).also { estimators.add(it) }
            val report = done(analyze(pipeline(open = { src }, pose = { est }, recommendedStride = 2), PipelineTestKit.request(video)).outcome).report

            assertEquals("stride a $fps fps", 1, stats.last().poseStride)
            assertEquals(pts, storedPose(video).frames.map { it.tUs })
            assertEquals(pts.size, est.calls.get())
            assertEquals(fps, report.analyzedFps, 0.01)
            assertFalse(AnalysisFlag.LOW_FPS in report.flags)
            assertEquals(3, report.repCount)
            assertNotNull(report.tempo)
            assertTrue(checkNotNull(report.tutMs) > 0L)
        }
    }

    @Test
    fun degradedHighFrameRatesKeepStrideWithinCheckpointSupport() {
        for (fps in listOf(60.0, 120.0)) {
            val synthetic = PipelineTestKit.squat(reps = 3, fps = fps, noise = SynthNoise.NONE)
            val pts = synthetic.track.frames.map { it.tUs }
            val video = PipelineTestKit.videoFile(folder.root, "degraded-${fps.toInt()}.mp4")
            val src = FakeFrameSource(PipelineTestKit.videoInfo(406, 720, pts.last(), fps = fps.toFloat()), pts)
                .also { sources.add(it) }
            val est = FakePoseEstimator(synthetic.track.frames).also { estimators.add(it) }
            val report = done(analyze(pipeline(open = { src }, pose = { est }, recommendedStride = 2), PipelineTestKit.request(video)).outcome).report

            assertEquals(2, stats.last().poseStride)
            assertEquals(pts.filterIndexed { index, _ -> index % 2 == 0 }, storedPose(video).frames.map { it.tUs })
            assertEquals(fps / 2.0, report.analyzedFps, 0.01)
            assertFalse(AnalysisFlag.LOW_FPS in report.flags)
            assertEquals(3, report.repCount)
        }
    }

    @Test
    fun aFullAnalysisCountsTheTruthRepsAndLeavesItsSidecars() {
        val video = PipelineTestKit.videoFile(folder.root)
        val result = analyze(pipeline(), PipelineTestKit.request(video))
        val doneResult = done(result.outcome)
        val report = doneResult.report
        assertEquals(TechniqueAnalysisPipeline.POSE_VERSION, doneResult.poseVersion)
        assertEquals("fake/full/CPU", doneResult.poseEngine)
        assertNotNull(doneResult.exercise)

        assertEquals(set.reps.count { it.kind == SynthRepKind.FULL }, report.repCount)
        assertEquals(5, report.reps.size)
        // El progreso sube sin repetir ni retroceder, de 0 a 100.
        assertEquals(0, result.progress.first())
        assertEquals(100, result.progress.last())
        assertTrue("progreso ${result.progress}", result.progress.zipWithNext().all { (a, b) -> b > a })
        assertTrue("el 95 % es el cambio de la decodificación al motor", 95 in result.progress)

        // Sidecars: la pose, sin disco ni punto de control.
        assertTrue(TechniqueSidecars.poseFile(video).isFile)
        assertFalse(TechniqueSidecars.barFile(video).exists())
        assertFalse(TechniqueSidecars.partFile(video).exists())
        val stored = storedPose(video)
        assertEquals(times.size, stored.frames.size)
        for (i in times.indices) {
            assertEquals(times[i], stored.frames[i].tUs)
            assertEquals(set.track.frames[i].hasPose, stored.frames[i].hasPose)
        }
        assertEquals(1, stored.poseStride)
        assertEquals(406, stored.frameWidth)
        assertEquals(720, stored.frameHeight)
        assertEquals("CPU", stored.engine.delegate)
        assertEquals(30f, stored.nominalFps, 0.6f)

        // El pipeline mide exactamente lo que dejó escrito.
        assertEquals(engineOn(stored), report)

        // Decodificación y pose solapadas sin pisarse: cada hueco se leyó antes de que el decodificador lo reutilizara.
        val estimator = estimators.single()
        assertTrue("huecos reutilizados antes de tiempo: ${estimator.staleReads.take(3)}", estimator.staleReads.isEmpty())
        assertTrue("tiempos que no crecen: ${estimator.notIncreasing.take(3)}", estimator.notIncreasing.isEmpty())
        assertEquals(times.size, estimator.calls.get())
        assertTrue(estimator.closed)
        val src = sources.single()
        assertTrue(src.closed)
        assertFalse("close() con decode en marcha", src.closedWhileDecoding)
        assertFalse(src.decoding)
        assertEquals(times.size, src.delivered.get())

        val run = stats.single()
        assertEquals(times.size, run.framesDecoded)
        assertEquals(times.size, run.framesPosed)
        assertEquals(VideoDecodePath.GL, run.decodePath)
        assertEquals("CPU", run.poseDelegate)
        assertFalse(run.usesPlate)
        assertNull(run.resumedFromPtsUs)
    }

    @Test
    fun recomputeGivesTheSameReportWithoutOpeningTheVideo() {
        val video = PipelineTestKit.videoFile(folder.root)
        val first = done(analyze(pipeline(), PipelineTestKit.request(video)).outcome).report

        val opened = java.util.concurrent.atomic.AtomicInteger()
        val created = java.util.concurrent.atomic.AtomicInteger()
        val again = pipeline(
            open = { opened.incrementAndGet(); error("RECOMPUTE no abre el vídeo") },
            pose = { created.incrementAndGet(); error("RECOMPUTE no crea el estimador") },
        )
        val second = analyze(again, PipelineTestKit.request(video, AnalysisMode.RECOMPUTE))
        assertEquals(first, done(second.outcome).report)
        assertEquals(0, opened.get())
        assertEquals(0, created.get())
        assertEquals(100, second.progress.last())
    }

    @Test
    fun recomputeWithoutSidecarsFallsBackToAFullAnalysis() {
        val video = PipelineTestKit.videoFile(folder.root)
        val result = analyze(pipeline(), PipelineTestKit.request(video, AnalysisMode.RECOMPUTE))
        assertEquals(5, done(result.outcome).report.repCount)
        assertEquals(1, sources.size)
        assertTrue(TechniqueSidecars.poseFile(video).isFile)
    }

    @Test
    fun anUnsupportedOrUnchosenProfileNeverOpensTheVideo() {
        val video = PipelineTestKit.videoFile(folder.root)
        val opened = java.util.concurrent.atomic.AtomicInteger()
        val guarded = pipeline(open = { opened.incrementAndGet(); source() }, pose = { estimator() })

        val isometric = ExerciseRef("wall_sit", null, "knee_dominant", null, null, "Sentada en la pared", isCustom = false)
        val unsupported = analyze(guarded, PipelineTestKit.request(video, exercise = isometric)).outcome
        assertEquals(TechniqueAnalysisOutcome.Unsupported("isometric"), unsupported)

        val needsProfile = analyze(guarded, PipelineTestKit.request(video, exercise = PipelineTestKit.exercise(null, name = "Press banca con pausa"))).outcome
        assertEquals(TechniqueAnalysisOutcome.NeedsProfile(AnalysisProfileId.BENCH), needsProfile)

        assertEquals("no se abrió el vídeo", 0, opened.get())
        assertTrue(estimators.isEmpty())
        assertFalse(TechniqueSidecars.poseFile(video).exists())
    }

    // ---- Puntos de control ---------------------------------------------------------------------------------------------

    @Test
    fun aStrideTwoCheckpointAt30FpsIsRemovedBeforeStartingAgain() {
        val video = PipelineTestKit.videoFile(folder.root)
        writeStrideTwoCheckpoint(video, set.track)
        val part = TechniqueSidecars.partFile(video)
        val partExistedAtPoseCreation = AtomicBoolean()
        val src = source()
        val est = estimator()
        val result = analyze(pipeline(
            open = { src },
            pose = { partExistedAtPoseCreation.set(part.exists()); est },
            recommendedStride = 2,
        ), PipelineTestKit.request(video))

        done(result.outcome)
        assertFalse("el .part de 15 Hz se borra antes de estimar", partExistedAtPoseCreation.get())
        assertEquals(listOf(0L), src.decodeStarts.toList())
        assertNull(stats.last().resumedFromPtsUs)
        assertEquals(1, stats.last().poseStride)
        assertEquals(times, storedPose(video).frames.map { it.tUs })
        assertEquals(times.size, est.calls.get())
    }

    @Test
    fun aStrideTwoCheckpointAt60FpsCanResumeOnAHealthyDevice() {
        val synthetic = PipelineTestKit.squat(reps = 3, fps = 60.0)
        val pts = synthetic.track.frames.map { it.tUs }
        val video = PipelineTestKit.videoFile(folder.root)
        val checkpoint = writeStrideTwoCheckpoint(video, synthetic.track)
        val src = FakeFrameSource(PipelineTestKit.videoInfo(406, 720, pts.last(), fps = 60f), pts).also { sources.add(it) }
        val est = FakePoseEstimator(synthetic.track.frames).also { estimators.add(it) }
        val result = analyze(pipeline(open = { src }, pose = { est }), PipelineTestKit.request(video))

        done(result.outcome)
        assertEquals(listOf(checkpoint.header.lastPtsUs + 1L), src.decodeStarts.toList())
        assertEquals(checkpoint.header.lastPtsUs, stats.last().resumedFromPtsUs)
        assertEquals(2, stats.last().poseStride)
        assertEquals(pts.filterIndexed { index, _ -> index % 2 == 0 }, storedPose(video).frames.map { it.tUs })
        assertEquals((pts.size + 1) / 2 - checkpoint.pose.frames.size, est.calls.get())
        assertFalse(TechniqueSidecars.partFile(video).exists())
    }

    @Test
    fun aSlowMotionCheckpointUsesTheRealCaptureCadenceForReuse() {
        val synthetic = PipelineTestKit.squat(reps = 3, fps = 60.0, noise = SynthNoise.NONE)
        val real = synthetic.track.frames.map { it.tUs }
        val container = real.map { it * 2L }
        val video = PipelineTestKit.videoFile(folder.root)
        val checkpoint = writeStrideTwoCheckpoint(video, synthetic.track, ptsScale = 2L)
        val info = PipelineTestKit.videoInfo(406, 720, container.last(), fps = 30f, captureFps = 60f)
        val src = FakeFrameSource(info, container).also { sources.add(it) }
        val est = FakePoseEstimator(synthetic.track.frames, ptsScale = 2L).also { estimators.add(it) }
        val result = analyze(pipeline(open = { src }, pose = { est }, recommendedStride = 2), PipelineTestKit.request(video))
        val report = done(result.outcome).report

        assertEquals(listOf(checkpoint.header.lastPtsUs + 1L), src.decodeStarts.toList())
        assertEquals(checkpoint.header.lastPtsUs, stats.last().resumedFromPtsUs)
        assertEquals(real.filterIndexed { index, _ -> index % 2 == 0 }, storedPose(video).frames.map { it.tUs })
        assertEquals(30.0, report.analyzedFps, 0.01)
        assertFalse(AnalysisFlag.LOW_FPS in report.flags)
        assertTrue(est.staleReads.isEmpty())
        assertTrue(est.notIncreasing.isEmpty())
    }

    @Test
    fun aCheckpointInTheMiddleMakesTheNextAttemptResumeWithTheSameResult() {
        // La serie entera de un tirón: la referencia.
        val reference = PipelineTestKit.videoFile(folder.root, "ref.mp4")
        val referenceReport = done(analyze(pipeline(), PipelineTestKit.request(reference)).outcome).report
        val referencePose = storedPose(reference)

        // Un fallo transitorio de E/S a los 400 fotogramas deja el punto de control de los 300 (uno cada 150).
        val video = PipelineTestKit.videoFile(folder.root, "cut.mp4")
        val interrupted = pipeline(open = { source(failAfter = 400, failure = IOException("el disco")) })
        val failure = failed(analyze(interrupted, PipelineTestKit.request(video)).outcome)
        assertEquals(TechniqueAnalysisPipeline.ERROR_IO, failure.errorCode)
        assertTrue(failure.transient)
        val part = TechniqueSidecars.partFile(video)
        assertTrue("el fallo transitorio deja el punto de control", part.isFile)
        val checkpoint = checkNotNull(AnalysisCheckpoints.read(part))
        assertEquals(300, checkpoint.header.nextIndex)
        assertEquals(times[299], checkpoint.header.lastPtsUs)
        assertEquals(300, checkpoint.pose.frames.size)
        assertFalse("sin pose2 hasta terminar", TechniqueSidecars.poseFile(video).exists())

        // El siguiente intento sigue desde el último PTS, sin repetir lo hecho, y da el mismo resultado.
        val resumedSource = source()
        val resumedEstimator = estimator()
        val resumed = analyze(pipeline(open = { resumedSource }, pose = { resumedEstimator }), PipelineTestKit.request(video))
        assertEquals(referenceReport, done(resumed.outcome).report)
        assertEquals(listOf(times[299] + 1), resumedSource.decodeStarts.toList())
        assertEquals(times.size - 300, resumedEstimator.calls.get())
        assertTrue(resumedEstimator.staleReads.isEmpty())
        assertFalse("se borra al terminar", part.exists())
        assertEquals(referencePose.frames, storedPose(video).frames)
        assertEquals(times[299], stats.last().resumedFromPtsUs)
    }

    @Test
    fun aCheckpointFromOtherVideoOrVersionIsDiscarded() {
        val video = PipelineTestKit.videoFile(folder.root)
        // Un punto de control de un vídeo de otro tamaño: no sirve y se empieza de cero.
        val fakeHeader = AnalysisCheckpoint.Header(
            mediaId = "otro", poseVersion = TechniqueAnalysisPipeline.POSE_VERSION, videoBytes = 1L, videoModifiedMs = 1L, usesPlate = false,
            poseStride = 1, timeScale = 1.0, frameWidth = 406, frameHeight = 720, lastPtsUs = times[100], nextIndex = 101,
            plateStage = AnalysisCheckpoint.STAGE_NONE,
        )
        val partial = set.track.copy(frames = set.track.frames.take(101), mediaId = "otro")
        AnalysisCheckpoints.write(TechniqueSidecars.partFile(video), AnalysisCheckpoint(fakeHeader, partial, null))

        val src = source()
        val result = analyze(pipeline(open = { src }), PipelineTestKit.request(video))
        assertEquals(5, done(result.outcome).report.repCount)
        assertEquals(listOf(0L), src.decodeStarts.toList())
        assertNull(stats.last().resumedFromPtsUs)
    }

    // ---- Cancelación y pausa -------------------------------------------------------------------------------------------

    @Test
    fun cancellingWaitsForTheDecoderBeforeClosingAndKeepsTheCheckpoint() = runBlocking {
        val video = PipelineTestKit.videoFile(folder.root)
        val src = source()
        val est = estimator()
        val pipeline = pipeline(open = { src }, pose = { est })
        val cancelled = AtomicBoolean(false)
        val holder = AtomicReference<Job?>(null)
        val job = launch(Dispatchers.Default) {
            try {
                pipeline.analyze(PipelineTestKit.request(video)) { percent -> if (percent >= 30) holder.get()?.cancel() }
            } catch (expected: CancellationException) {
                cancelled.set(true)
                throw expected
            }
        }
        holder.set(job)
        withTimeout(TIMEOUT_MS) { job.join() }

        assertTrue("la cancelación llegó hasta fuera", cancelled.get())
        assertTrue(src.closed)
        assertFalse("close() con decode en marcha", src.closedWhileDecoding)
        assertFalse("decode sigue vivo tras cancelar", src.decoding)
        assertTrue(est.closed)
        assertTrue(est.staleReads.isEmpty())
        assertFalse("sin sidecar de pose a medias", TechniqueSidecars.poseFile(video).exists())
        assertTrue("la cancelación deja el punto de control", TechniqueSidecars.partFile(video).isFile)
    }

    @Test
    fun theCameraGatePausesTheAnalysisUntilItIsFree() = runBlocking {
        val video = PipelineTestKit.videoFile(folder.root)
        val busy = MutableStateFlow(true)
        val src = source()
        val pipeline = pipeline(open = { src }, gateIsClosed = { busy.value }, awaitGate = { busy.first { free -> !free } })
        val job = async(Dispatchers.Default) { pipeline.analyze(PipelineTestKit.request(video)) {} }

        var last = -1
        var stable = 0
        withTimeout(TIMEOUT_MS) {
            while (stable < 4) {
                delay(100L)
                val now = src.delivered.get()
                if (now == last) stable++ else {
                    stable = 0
                    last = now
                }
            }
        }
        // 30 fotogramas procesados + los 2 de la cola: el productor espera en `send`.
        assertTrue("se paró en $last de ${times.size}", last in 30..40)
        assertTrue(job.isActive)

        busy.value = false
        val outcome = withTimeout(TIMEOUT_MS) { job.await() }
        assertEquals(5, done(outcome).report.repCount)
        assertEquals(times.size, src.delivered.get())
    }

    // ---- Camino de decodificación, cámara lenta y fallos -----------------------------------------------------------------

    @Test
    fun theRetrieverPathAddsTheFlagAndCapsTheConfidenceAtMedium() {
        val video = PipelineTestKit.videoFile(folder.root)
        val result = analyze(pipeline(open = { source(path = VideoDecodePath.RETRIEVER) }), PipelineTestKit.request(video))
        val report = done(result.outcome).report
        assertTrue(AnalysisFlag.DECODE_FALLBACK in report.flags)
        val plain = engineOn(storedPose(video))
        val expected = if (plain.confidence == CountConfidence.ALTA) CountConfidence.MEDIA else plain.confidence
        assertEquals(expected, report.confidence)
        assertTrue(report.confidence != CountConfidence.ALTA)
        assertEquals(VideoDecodePath.RETRIEVER, stats.single().decodePath)

        // Un RECOMPUTE hereda el aviso del informe anterior (que el Worker deja en <id>.analysis.json).
        TechniqueSidecars.writeAnalysis(
            TechniqueSidecars.analysisFile(video), report, PipelineTestKit.exercise(), null, null, analyzedAtMs = 1L,
        )
        val again = done(analyze(pipeline(), PipelineTestKit.request(video, AnalysisMode.RECOMPUTE)).outcome).report
        assertEquals(report, again)
    }

    @Test
    fun anHdrVideoCarriesItsFlagOnlyWhereTheToneMapRequestDoesNotExist() {
        val before = PipelineTestKit.videoFile(folder.root, "hdr30.mp4")
        val withoutRequest = done(analyze(pipeline(open = { source(hdr = true) }, sdkInt = 30), PipelineTestKit.request(before)).outcome).report
        assertTrue(AnalysisFlag.HDR_SOURCE in withoutRequest.flags)

        val after = PipelineTestKit.videoFile(folder.root, "hdr34.mp4")
        val withRequest = done(analyze(pipeline(open = { source(hdr = true) }, sdkInt = 34), PipelineTestKit.request(after)).outcome).report
        assertFalse(AnalysisFlag.HDR_SOURCE in withRequest.flags)

        // Un vídeo que no es HDR nunca lo lleva.
        val sdr = PipelineTestKit.videoFile(folder.root, "sdr.mp4")
        assertFalse(AnalysisFlag.HDR_SOURCE in done(analyze(pipeline(sdkInt = 30), PipelineTestKit.request(sdr)).outcome).report.flags)
    }

    @Test
    fun aSlowMotionVideoIsAnalyzedInRealTimeWithTheStrideOfItsCaptureRate() {
        // Capturado a 60 fps y reproducido a 30: el PTS del contenedor va al doble que el tiempo real.
        val slow = PipelineTestKit.squat(reps = 3, noise = SynthNoise(ptsJitterMs = 0.5), fps = 60.0, lead = 1.5, tail = 1.5)
        val real = slow.track.frames.map { it.tUs }
        val container = real.map { it * 2L }
        val info = PipelineTestKit.videoInfo(406, 720, container.last(), fps = 30f, captureFps = 60f)
        val video = PipelineTestKit.videoFile(folder.root)
        val src = FakeFrameSource(info, container).also { sources.add(it) }
        val est = FakePoseEstimator(slow.track.frames, ptsScale = 2L).also { estimators.add(it) }
        val result = analyze(pipeline(open = { src }, pose = { est }), PipelineTestKit.request(video))
        val report = done(result.outcome).report

        val stored = storedPose(video)
        // 60 fps reales: pose en uno de cada dos fotogramas, con los tiempos REALES.
        assertEquals(2, stored.poseStride)
        assertEquals(60f, checkNotNull(stored.captureFps), 0.01f)
        assertEquals(real.indices.filter { it % 2 == 0 }.map { real[it] }, stored.frames.map { it.tUs })
        assertEquals(30.0, report.analyzedFps, 1.0)
        assertEquals(60.0, checkNotNull(report.sourceFps), 0.01)
        assertEquals(3, report.repCount)
        assertTrue(est.staleReads.isEmpty())
        assertTrue(est.notIncreasing.isEmpty())
    }

    @Test
    fun errorsAreReturnedNeverThrownAndOnlyTransientOnesKeepTheCheckpoint() {
        val video = PipelineTestKit.videoFile(folder.root)

        // Un fallo del decodificador que no es de E/S: definitivo.
        val broken = failed(analyze(pipeline(open = { source(failAfter = 200, failure = VideoDecodeException("el códec murió")) }), PipelineTestKit.request(video)).outcome)
        assertEquals(TechniqueAnalysisPipeline.ERROR_DECODE, broken.errorCode)
        assertFalse(broken.transient)
        assertFalse("un fallo definitivo borra el punto de control", TechniqueSidecars.partFile(video).exists())
        assertTrue(sources.last().closed)
        assertFalse(sources.last().closedWhileDecoding)
        assertTrue(estimators.last().closed)

        // El mismo, pero con una causa de E/S: transitorio.
        val io = failed(analyze(pipeline(open = { source(failAfter = 10, failure = VideoDecodeException("lectura", IOException("sin espacio"))) }), PipelineTestKit.request(video)).outcome)
        assertEquals(TechniqueAnalysisPipeline.ERROR_DECODE, io.errorCode)
        assertTrue(io.transient)

        // No se puede abrir el vídeo.
        val unreadable = failed(analyze(pipeline(open = { throw VideoDecodeException("no existe") }), PipelineTestKit.request(video)).outcome)
        assertEquals(TechniqueAnalysisPipeline.ERROR_VIDEO_UNREADABLE, unreadable.errorCode)
        assertFalse(unreadable.transient)

        // No se puede crear el estimador (la librería nativa falta): definitivo, y la fuente se cierra.
        val noPose = failed(analyze(pipeline(pose = { throw UnsatisfiedLinkError("libmediapipe") }), PipelineTestKit.request(video)).outcome)
        assertEquals(TechniqueAnalysisPipeline.ERROR_POSE_UNAVAILABLE, noPose.errorCode)
        assertFalse(noPose.transient)
        assertTrue(sources.last().closed)

        // La pose falla en cada fotograma: tras 20 errores se rinde.
        val failing = failed(analyze(pipeline(pose = { estimator(failAlways = true) }), PipelineTestKit.request(video)).outcome)
        assertEquals(TechniqueAnalysisPipeline.ERROR_POSE_FAILED, failing.errorCode)
        assertFalse(failing.transient)
        assertEquals(PipelineTuning().maxPoseErrors + 1, estimators.last().calls.get())
        assertTrue(estimators.last().closed)
        assertTrue(sources.last().closed)
    }

    @Test
    fun anOccasionalPoseErrorCostsOnlyThatFrame() {
        val video = PipelineTestKit.videoFile(folder.root)
        val result = analyze(pipeline(pose = { estimator(failEvery = 97) }), PipelineTestKit.request(video))
        assertEquals(5, done(result.outcome).report.repCount)
        val stored = storedPose(video)
        assertEquals(times.size, stored.frames.size)
        // Las llamadas 97, 194… devolvieron «sin atleta»: el fotograma está, sin pose.
        assertFalse(stored.frames[96].hasPose)
        assertFalse(stored.frames[193].hasPose)
    }

    // ---- Disco ---------------------------------------------------------------------------------------------------------

    /** Una sentadilla limpia de 720×1280 con un disco DIBUJADO que sigue la muñeca izquierda, y lo necesario para pasarlo por el pipeline. */
    private inner class PlateCase(val reps: Int = 3, val occlusion: ClosedFloatingPointRange<Double>? = null, val fps: Double = 30.0) {
        val camera = SynthCamera()
        val lifter = PipelineTestKit.squat(
            reps = reps, width = camera.widthPx, height = camera.heightPx, noise = SynthNoise.NONE,
            lead = 1.5, tail = 1.0, eccS = 1.2, p1S = 0.3, conS = 1.2, p2S = 0.3,
            fps = fps,
        )
        val frames = lifter.track.frames
        val pts = frames.map { it.tUs }
        val xs = DoubleArray(frames.size) { frames[it].x(PoseLandmark.LEFT_WRIST) * camera.widthPx.toDouble() }
        val ys = DoubleArray(frames.size) { frames[it].y(PoseLandmark.LEFT_WRIST) * camera.heightPx.toDouble() }
        val radius = VbtTestSupport.radiusPx(450, VbtTestSupport.leftWristDepthM(camera), camera).toDouble()
        val scene = SyntheticPlateFrames.scene(
            SyntheticPlateFrames.params(
                11L,
                SceneOptions(
                    width = camera.widthPx, height = camera.heightPx, radiusPx = radius, contrast = 45.0, polarity = 1, noiseSigma = 3.0,
                    exposureS = 1.0 / 90.0, rackBars = false, distractor = false, fullOcclusion = occlusion,
                ),
            ),
            SampledPath(DoubleArray(frames.size) { pts[it] / 1e6 }, xs, ys),
        )
        val tuning = PipelineTuning(targetLongEdge = camera.heightPx)

        fun newSource(failAfter: Int = -1, failure: Throwable? = null) = FakeFrameSource(
            PipelineTestKit.videoInfo(camera.widthPx, camera.heightPx, pts.last(), fps = fps.toFloat()), pts, VideoDecodePath.GL,
            painter = { tUs, out -> scene.render(tUs / 1e6, (tUs / 1000L).toInt(), out) },
            failAfterDelivered = failAfter,
            failure = failure,
        ).also { sources.add(it) }

        fun newEstimator() = FakePoseEstimator(frames).also { estimators.add(it) }

        fun truthAt(tUs: Long): Pair<Double, Double> {
            val k = pts.indexOfFirst { it >= tUs }.let { if (it < 0) pts.size - 1 else it }
            return xs[k] to ys[k]
        }

        fun pipeline(failAfter: Int = -1, failure: Throwable? = null, noPose: Boolean = false) = TechniqueAnalysisPipeline(
            PipelineTestKit.deps({ newSource(failAfter, failure) }, { if (noPose) FakePoseEstimator(frames, failAlways = true).also { estimators.add(it) } else newEstimator() }, onStats = { stats.add(it) }),
            tuning,
        )

        /** Cuánto se aleja de la verdad cada medida del disco (HOUGH o NCC), ordenado. */
        fun errorsOf(samples: List<com.example.kpkn.domain.biomechanics.bar.BarSample>): List<Double> =
            samples.filter { it.source == BarSampleSource.HOUGH || it.source == BarSampleSource.NCC }
                .map { hypot(it.cx - truthAt(it.tUs).first, it.cy - truthAt(it.tUs).second) }.sorted()

        fun request(video: File, mode: AnalysisMode = AnalysisMode.FULL, seed: PlateSeed? = null) = PipelineTestKit.request(
            video, mode, PipelineTestKit.exercise(AnalysisProfileId.SQUAT, equipmentId = "barbell"), seed, 450,
        )
    }

    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    @Test
    fun missingWristsExplainZeroAcquisitionAttemptsWithoutBlamingScaleOrTheDetector() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        analyze(case.pipeline(noPose = true), case.request(video))
        val run = stats.single()
        assertEquals(0, run.plateAttempts)
        assertEquals("none", run.plateSeedState)
        assertTrue((run.plateWaitingCounts["no_wrist"] ?: 0) > 0)
        assertEquals(setOf("no_wrist"), run.plateWaitingCounts.keys)
    }

    @Test
    fun automaticProposalDefersTrackingThenExplicitSelectionKeepsForwardBackwardAccuracy() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        val result = analyze(case.pipeline(), case.request(video))
        val automaticReport = done(result.outcome).report

        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes())) { "bar ilegible" }
        val seed = checkNotNull(bar.seed) { "sin semilla automática" }
        assertFalse("la semilla automática no está confirmada", seed.userConfirmed)
        assertNull("sin diámetro hasta que el usuario confirme", bar.plateDiameterMm)
        assertEquals(720, bar.frameWidth)
        assertEquals(1280, bar.frameHeight)

        val proposalStats = stats.single()
        assertEquals("PROPOSED", proposalStats.plateStage)
        assertTrue(proposalStats.plateTrackingDeferred)
        assertEquals(0, proposalStats.plateTrackingFrames)
        assertTrue(bar.samples.isEmpty())
        assertEquals(PlateTrackingContinuity.UNKNOWN, bar.trackingContinuity)
        assertEquals(engineOn(storedPose(video), equipment = "barbell"), automaticReport)
        assertNotNull(proposalStats.autoSeed)
        assertEquals("provisional", proposalStats.plateSeedState)
        assertTrue((proposalStats.plateWaitingCounts["no_still"] ?: 0) > 0)
        val selected = done(analyze(case.pipeline(), case.request(video, AnalysisMode.PLATE_ONLY, seed)).outcome).report
        val selectedBar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))

        // Una muestra por fotograma, en orden de tiempo, desde el primero (el búfer cubre lo anterior a la semilla).
        val samples = selectedBar.samples
        assertTrue("muestras ${samples.size} de ${case.pts.size}", samples.size >= case.pts.size - 2)
        assertTrue(samples.zipWithNext().all { (a, b) -> b.tUs > a.tUs })
        assertEquals(case.pts.first(), samples.first().tUs)
        assertTrue("la semilla llegó al empezar la quietud", seed.tUs in 400_000L..1_000_000L)
        assertTrue("hay muestras de antes de la semilla (hacia atrás)", samples.any { it.tUs < seed.tUs })
        assertTrue("y de después (hacia delante)", samples.any { it.tUs > seed.tUs })

        // El disco medido sigue a la verdad, a ambos lados de la semilla.
        val detector = samples.filter { it.source == BarSampleSource.HOUGH || it.source == BarSampleSource.NCC }
        assertTrue("solo ${detector.size} medidas del disco de ${samples.size}", detector.size >= samples.size * 8 / 10)
        val errors = detector.map { hypot(it.cx - case.truthAt(it.tUs).first, it.cy - case.truthAt(it.tUs).second) }.sorted()
        val median = errors[errors.size / 2]
        val p95 = errors[(errors.size * 95 / 100).coerceAtMost(errors.size - 1)]
        println("[pipeline/disco] semilla ${seed.tUs} µs r=${fmt(seed.r.toDouble())} (verdad ${fmt(case.radius)}) | ${samples.size} muestras, ${detector.size} medidas | error mediana ${fmt(median)} px P95 ${fmt(p95)} px | ${selected.repCount} reps ${selected.confidence}")
        assertTrue("error mediano ${fmt(median)} px", median <= 3.0)
        assertTrue("P95 ${fmt(p95)} px", p95 <= 10.0)
        val before = detector.filter { it.tUs < seed.tUs }.map { hypot(it.cx - case.truthAt(it.tUs).first, it.cy - case.truthAt(it.tUs).second) }
        assertTrue("las muestras hacia atrás también miden el disco", before.isNotEmpty() && before.max() <= 6.0)

        val run = stats.last()
        assertTrue(run.usesPlate)
        assertEquals("TRACKING", run.plateStage)
        assertNull(run.autoSeed)
        assertEquals("selected", run.plateSeedState)
        assertEquals(samples.size, run.barSamples)
        assertEquals(samples.size, run.plateTrackingFrames)
        assertFalse(run.plateTrackingDeferred)
        assertEquals(case.reps, selected.repCount)
        assertEquals(engineOn(storedPose(video), selectedBar, equipment = "barbell"), selected)
        assertTrue(estimators.single().staleReads.isEmpty())
        assertFalse(TechniqueSidecars.partFile(video).exists())
    }

    @Test
    fun selectConfirmRecomputeReusesOnlyTheSameSeedAndRetracksAChangedSelection() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        done(analyze(case.pipeline(), case.request(video)).outcome)
        val poseBefore = TechniqueSidecars.poseFile(video).readBytes()
        val provisionalBefore = TechniqueSidecars.barFile(video).readBytes()
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.RECOMPUTE)).outcome)
        assertTrue(provisionalBefore.contentEquals(TechniqueSidecars.barFile(video).readBytes()))
        assertFalse(checkNotNull(checkNotNull(BarTrackCodec.decode(provisionalBefore)).seed).userConfirmed)

        // The same circle with no samples is only a proposal: selection must run PLATE_ONLY.
        val sameSeed = checkNotNull(checkNotNull(BarTrackCodec.decode(provisionalBefore)).seed)
        val sourcesBeforeSameSeed = sources.size
        val sameSelectedReport = done(analyze(case.pipeline(), case.request(video, AnalysisMode.RECOMPUTE, sameSeed)).outcome).report
        assertEquals(sourcesBeforeSameSeed + 1, sources.size)
        val sameSelected = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertTrue(checkNotNull(sameSelected.seed).userConfirmed)
        assertEquals(450, sameSelected.plateDiameterMm)
        assertEquals(sameSeed.copy(userConfirmed = true), sameSelected.seed)
        assertTrue(sameSelected.samples.isNotEmpty())
        assertTrue(stats.last().plateTrackingFrames > 0)
        val selectedBytesForSameSeed = TechniqueSidecars.barFile(video).readBytes()
        val estimatorsBeforeDirect = estimators.size
        val directSelectedReport = done(analyze(case.pipeline(), case.request(video, AnalysisMode.PLATE_ONLY, sameSeed)).outcome).report
        // Both routes execute the same selected PLATE_ONLY pipeline on the same cached pose/PTS.
        assertTrue(selectedBytesForSameSeed.contentEquals(TechniqueSidecars.barFile(video).readBytes()))
        assertEquals(sameSelectedReport, directSelectedReport)
        assertEquals(estimatorsBeforeDirect, estimators.size)
        val sourcesBeforeSelectedRecompute = sources.size
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.RECOMPUTE, sameSeed)).outcome)
        assertEquals(sourcesBeforeSelectedRecompute, sources.size)
        assertTrue(poseBefore.contentEquals(TechniqueSidecars.poseFile(video).readBytes()))

        // El usuario fija el disco en un instante cualquiera de la 1.ª repetición.
        val seedAt = case.pts.first { it >= 3_000_000L }
        val (sx, sy) = case.truthAt(seedAt)
        val seed = PlateSeed(seedAt, sx.toFloat(), sy.toFloat(), case.radius.toFloat(), userConfirmed = true)
        val sourcesBefore = sources.size
        val estimatorsBefore = estimators.size
        val result = analyze(case.pipeline(), case.request(video, AnalysisMode.RECOMPUTE, seed))
        val report = done(result.outcome).report

        // Solo luma, desde 3 s antes de la semilla.
        val src = sources[sourcesBefore]
        assertEquals(listOf(max(0L, seedAt - 3_000_000L)), src.decodeStarts.toList())
        assertEquals("PLATE_ONLY no estima la pose", estimatorsBefore, estimators.size)
        assertTrue(poseBefore.contentEquals(TechniqueSidecars.poseFile(video).readBytes()))
        assertEquals(100, result.progress.last())

        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertTrue(checkNotNull(bar.seed).userConfirmed)
        assertEquals(seedAt, checkNotNull(bar.seed).tUs)
        assertEquals(450, bar.plateDiameterMm)
        val samples = bar.samples
        assertTrue(samples.zipWithNext().all { (a, b) -> b.tUs > a.tUs })
        assertTrue("hay muestras de antes de la semilla (el búfer es de ≤ 3 s)", samples.first().tUs < seedAt - 1_000_000L)
        assertTrue(samples.first().tUs >= seedAt - 3_000_000L - 1L)
        assertEquals(case.pts.last(), samples.last().tUs)
        val errors = samples.filter { it.source == BarSampleSource.HOUGH || it.source == BarSampleSource.NCC }
            .map { hypot(it.cx - case.truthAt(it.tUs).first, it.cy - case.truthAt(it.tUs).second) }.sorted()
        assertTrue("pocas medidas: ${errors.size} de ${samples.size}", errors.size >= samples.size * 8 / 10)
        println("[pipeline/disco] PLATE_ONLY: ${samples.size} muestras, error mediana ${fmt(errors[errors.size / 2])} px | escala ${report.scale?.source} | ${report.repCount} reps ${report.confidence}")
        assertTrue(errors[errors.size / 2] <= 3.0)
        assertEquals(case.reps, report.repCount)
        assertEquals(VideoDecodePath.GL, stats.last().decodePath)
        assertTrue(abs(stats.last().barSamples - samples.size) == 0)
        assertEquals("selected", stats.last().plateSeedState)
        val selectedBytes = TechniqueSidecars.barFile(video).readBytes()
        val sourcesBeforeRecompute = sources.size
        val recomputed = done(analyze(case.pipeline(), case.request(video, AnalysisMode.RECOMPUTE)).outcome).report
        assertEquals(sourcesBeforeRecompute, sources.size)
        assertTrue(selectedBytes.contentEquals(TechniqueSidecars.barFile(video).readBytes()))
        assertTrue(checkNotNull(checkNotNull(BarTrackCodec.decode(selectedBytes)).seed).userConfirmed)
        assertEquals(report, recomputed)
    }

    @Test
    fun aConfirmedSeedMakesAFullAnalysisTrackFromItsInstantAndBackward() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        val seedAt = case.pts.first { it >= 3_000_000L }
        val (sx, sy) = case.truthAt(seedAt)
        val seed = PlateSeed(seedAt, sx.toFloat(), sy.toFloat(), case.radius.toFloat(), userConfirmed = true)
        val result = analyze(case.pipeline(), case.request(video, AnalysisMode.FULL, seed))
        val report = done(result.outcome).report

        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertTrue(checkNotNull(bar.seed).userConfirmed)
        assertEquals(seedAt, checkNotNull(bar.seed).tUs)
        assertEquals("el diámetro confirmado", 450, bar.plateDiameterMm)
        assertTrue("hay muestras de antes de la semilla (el búfer)", bar.samples.first().tUs < seedAt)
        assertEquals(case.pts.last(), bar.samples.last().tUs)
        assertTrue(bar.samples.zipWithNext().all { (a, b) -> b.tUs > a.tUs })
        val errors = case.errorsOf(bar.samples)
        assertTrue("pocas medidas: ${errors.size} de ${bar.samples.size}", errors.size >= bar.samples.size * 8 / 10)
        assertTrue(errors[errors.size / 2] <= 3.0)

        val run = stats.single()
        assertEquals("TRACKING", run.plateStage)
        assertNull("no hubo semilla automática", run.autoSeed)
        assertEquals("sin adquisición: la semilla es la del usuario", 0, run.plateAttempts)
        assertEquals(case.reps, report.repCount)
    }

    @Test
    fun interruptedConfirmedTrackStaysInterruptedThroughRecomputeAndCanOnlyBeReassessedByPlateOnly() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        val (x, y) = case.truthAt(0L)
        val seed = PlateSeed(0L, x.toFloat(), y.toFloat(), case.radius.toFloat(), true)
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.FULL, seed)).outcome)
        val file = TechniqueSidecars.barFile(video)
        val measured = checkNotNull(BarTrackCodec.decode(file.readBytes()))
        val interrupted = measured.copy(trackingContinuity = PlateTrackingContinuity.INTERRUPTED, continuityBreakUs = 4_000_000L)
        TechniqueSidecars.writeAtomically(file, BarTrackCodec.encode(interrupted))
        val sourcesBefore = sources.size
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.RECOMPUTE, seed)).outcome)
        assertEquals(sourcesBefore, sources.size)
        val kept = checkNotNull(BarTrackCodec.decode(file.readBytes()))
        assertTrue(checkNotNull(kept.seed).userConfirmed)
        assertEquals(PlateTrackingContinuity.INTERRUPTED, kept.trackingContinuity)
        assertEquals(4_000_000L, kept.continuityBreakUs)
        assertFalse(com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy.canUsePlate(kept))
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.PLATE_ONLY, seed)).outcome)
        val reassessed = checkNotNull(BarTrackCodec.decode(file.readBytes()))
        assertTrue(checkNotNull(reassessed.seed).userConfirmed)
        assertEquals(PlateTrackingContinuity.UNINTERRUPTED, reassessed.trackingContinuity)
        assertNull(reassessed.continuityBreakUs)
    }

    @Test
    fun confirmedCheckpointPreservesHistoryAndResumesWithUnknownAssociationState() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        val (x, y) = case.truthAt(0L)
        val seed = PlateSeed(0L, x.toFloat(), y.toFloat(), case.radius.toFloat(), true)
        failed(analyze(case.pipeline(failAfter = 260, failure = IOException("checkpoint")), case.request(video, AnalysisMode.FULL, seed)).outcome)
        val checkpoint = checkNotNull(AnalysisCheckpoints.read(TechniqueSidecars.partFile(video)))
        assertTrue(checkNotNull(checkNotNull(checkpoint.bar).seed).userConfirmed)
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.FULL, seed)).outcome)
        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertTrue(checkNotNull(bar.seed).userConfirmed)
        assertEquals(PlateTrackingContinuity.UNKNOWN, bar.trackingContinuity)
        assertFalse(com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy.canUsePlate(bar))
    }

    @Test
    fun plateOnlyThatLosesSupportAgainCannotRecoverPublicationByReconfirmingTheSeed() {
        val case = PlateCase(occlusion = 3.0..3.5)
        val video = PipelineTestKit.videoFile(folder.root)
        val (x, y) = case.truthAt(0L)
        val seed = PlateSeed(0L, x.toFloat(), y.toFloat(), case.radius.toFloat(), true)
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.FULL, seed)).outcome)
        done(analyze(case.pipeline(), case.request(video, AnalysisMode.PLATE_ONLY, seed)).outcome)
        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertTrue(checkNotNull(bar.seed).userConfirmed)
        assertEquals(PlateTrackingContinuity.INTERRUPTED, bar.trackingContinuity)
        assertNotNull(bar.continuityBreakUs)
        assertTrue(bar.samples.any { it.tUs > 4_000_000L && it.source == BarSampleSource.HOUGH })
        assertFalse(com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy.canUsePlate(bar))
    }

    @Test
    fun aPlateCheckpointResumesTrackingFromTheLastReliableSample() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        val seedAt = 500_000L
        assertTrue(case.pts.contains(seedAt))
        val (seedX, seedY) = case.truthAt(seedAt)
        val selectedSeed = PlateSeed(seedAt, seedX.toFloat(), seedY.toFloat(), case.radius.toFloat(), true)
        // Un fallo transitorio de E/S a los 260 fotogramas deja el punto de control de los 150, con el disco ya siguiéndose.
        val failure = failed(analyze(case.pipeline(failAfter = 260, failure = IOException("el disco")), case.request(video, AnalysisMode.FULL, selectedSeed)).outcome)
        assertTrue(failure.transient)
        val checkpoint = checkNotNull(AnalysisCheckpoints.read(TechniqueSidecars.partFile(video)))
        assertEquals(150, checkpoint.header.nextIndex)
        assertEquals(AnalysisCheckpoint.STAGE_TRACKING, checkpoint.header.plateStage)
        assertEquals(AnalysisCheckpoint.seedKey(selectedSeed), checkpoint.header.seedKey)
        assertEquals(selectedSeed, checkNotNull(checkpoint.bar).seed)
        assertTrue(checkNotNull(checkNotNull(checkpoint.bar).seed).userConfirmed)
        assertEquals("una muestra por fotograma procesado, hacia atrás incluidas", 150, checkNotNull(checkpoint.bar).samples.size)

        val resumed = analyze(case.pipeline(), case.request(video, AnalysisMode.FULL, selectedSeed))
        val report = done(resumed.outcome).report
        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertEquals("ningún fotograma repetido ni perdido", case.pts.size, bar.samples.size)
        assertEquals(case.pts, bar.samples.map { it.tUs })
        val errors = case.errorsOf(bar.samples)
        assertTrue("pocas medidas: ${errors.size} de ${bar.samples.size}", errors.size >= bar.samples.size * 9 / 10)
        assertTrue("error mediano ${fmt(errors[errors.size / 2])} px", errors[errors.size / 2] <= 3.0)
        assertEquals(case.pts[149], stats.last().resumedFromPtsUs)
        assertEquals("la selección explícita histórica se conserva", selectedSeed, bar.seed)
        assertEquals(500_000L, checkNotNull(bar.seed).tUs)
        assertTrue(checkNotNull(bar.seed).userConfirmed)
        val expectedContinuity = if (checkNotNull(checkpoint.bar).trackingContinuity == PlateTrackingContinuity.INTERRUPTED)
            PlateTrackingContinuity.INTERRUPTED else PlateTrackingContinuity.UNKNOWN
        assertEquals(expectedContinuity, bar.trackingContinuity)
        assertEquals(checkNotNull(checkpoint.bar).continuityBreakUs, bar.continuityBreakUs)
        assertFalse(com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy.canUsePlate(bar))
        assertEquals(case.reps, report.repCount)
        assertFalse(TechniqueSidecars.partFile(video).exists())
    }

    private companion object {
        const val TIMEOUT_MS = 180_000L
    }
    @Test
    fun proposedCheckpointResumesWithoutTrackingOrLosingSeedPtsAndAcquisitionDiagnostics() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        failed(analyze(case.pipeline(failAfter = 260, failure = IOException("checkpoint")), case.request(video)).outcome)
        val checkpoint = checkNotNull(AnalysisCheckpoints.read(TechniqueSidecars.partFile(video)))
        assertEquals(AnalysisCheckpoint.STAGE_PROPOSED, checkpoint.header.plateStage)
        val proposal = checkNotNull(checkpoint.bar)
        assertNotNull(proposal.seed)
        assertTrue(proposal.samples.isEmpty())
        assertTrue(checkpoint.header.plateAttempts > 0)
        val report = done(analyze(case.pipeline(), case.request(video)).outcome).report
        val back = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
        assertEquals(proposal.seed, back.seed)
        assertTrue(back.samples.isEmpty())
        assertEquals(0, stats.last().plateTrackingFrames)
        assertTrue(stats.last().plateTrackingDeferred)
        assertEquals(checkpoint.header.plateAttempts, stats.last().plateAttempts)
        assertEquals(checkpoint.header.plateWaitingCounts, stats.last().plateWaitingCounts)
        assertNotNull(stats.last().resumedFromPtsUs)
        assertEquals(engineOn(storedPose(video), equipment = "barbell"), report)
    }

    @Test
    fun legacyAutomaticCheckpointRetainsProposalButDiscardsOnlyItsUnpublishedAssociations() {
        val case = PlateCase()
        val video = PipelineTestKit.videoFile(folder.root)
        failed(analyze(case.pipeline(failAfter = 260, failure = IOException("checkpoint")), case.request(video)).outcome)
        val part = TechniqueSidecars.partFile(video)
        val checkpoint = checkNotNull(AnalysisCheckpoints.read(part))
        val proposal = checkNotNull(checkpoint.bar)
        val seed = checkNotNull(proposal.seed)
        val historical = com.example.kpkn.domain.biomechanics.bar.BarSample(seed.tUs, seed.cx, seed.cy,
            seed.r, 1f, 0.9f, BarSampleSource.HOUGH)
        for (stage in listOf(AnalysisCheckpoint.STAGE_TRACKING, AnalysisCheckpoint.STAGE_GAVE_UP)) {
            AnalysisCheckpoints.write(part, AnalysisCheckpoint(checkpoint.header.copy(plateStage = stage),
                checkpoint.pose, proposal.copy(samples = listOf(historical))))
            done(analyze(case.pipeline(), case.request(video)).outcome)
            val back = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(video).readBytes()))
            assertEquals(seed, back.seed)
            assertTrue(back.samples.isEmpty())
            assertEquals(PlateTrackingContinuity.UNKNOWN, back.trackingContinuity)
            assertEquals(0, stats.last().plateTrackingFrames)
            assertEquals("PROPOSED", stats.last().plateStage)
        }
    }


    @Test
    fun proposedStrideTwoSkipCheckpointRestoresAllPosePtsAndGlobalIndexParity() {
        // Stride 2 is safe at 60 fps; a degraded 30 fps source must now keep every pose.
        val case = PlateCase(fps = 60.0)
        val whole = PipelineTestKit.videoFile(folder.newFolder("whole-stride2"))
        val cut = PipelineTestKit.videoFile(folder.newFolder("cut-stride2"))
        val wholeNeeds = CopyOnWriteArrayList<Triple<Int, Long, com.example.kpkn.data.media.video.FrameNeeds>>()
        val cutNeeds = CopyOnWriteArrayList<Triple<Int, Long, com.example.kpkn.data.media.video.FrameNeeds>>()
        val resumeNeeds = CopyOnWriteArrayList<Triple<Int, Long, com.example.kpkn.data.media.video.FrameNeeds>>()

        fun observedPipeline(
            log: MutableList<Triple<Int, Long, com.example.kpkn.data.media.video.FrameNeeds>>,
            failAfter: Int = -1,
            recommendedStride: Int = 2,
        ): TechniqueAnalysisPipeline {
            val deps = PipelineTestKit.deps(
                open = {
                    val delegate = case.newSource(failAfter, if (failAfter >= 0) IOException("stride2 checkpoint") else null)
                    object : VideoFrameSource, com.example.kpkn.data.media.video.VideoDecodeReport {
                        override val info get() = delegate.info
                        override val path get() = delegate.path
                        override val details get() = delegate.details
                        override val fallbackReason get() = delegate.fallbackReason
                        override suspend fun decode(
                            fromUs: Long,
                            targetLongEdge: Int,
                            needs: (Int, Long) -> com.example.kpkn.data.media.video.FrameNeeds,
                            onFrame: suspend (com.example.kpkn.data.media.video.DecodedFrame) -> Unit,
                        ) = delegate.decode(fromUs, targetLongEdge, { index, ptsUs ->
                            needs(index, ptsUs).also { log.add(Triple(index, ptsUs, it)) }
                        }, onFrame)
                        override fun close() = delegate.close()
                    }
                },
                pose = { case.newEstimator() },
                onStats = { stats.add(it) },
            ).copy(recommendedPoseStride = { recommendedStride })
            return TechniqueAnalysisPipeline(deps, case.tuning)
        }

        done(analyze(observedPipeline(wholeNeeds), case.request(whole)).outcome)
        val wholePose = storedPose(whole)
        val expectedPosePts = case.pts.filterIndexed { index, _ -> index % 2 == 0 }
        assertEquals(expectedPosePts, wholePose.frames.map { it.tUs })
        assertEquals(case.pts.indices.toList(), wholeNeeds.map { it.first })
        assertEquals(case.pts, wholeNeeds.map { it.second })
        assertTrue(wholeNeeds.any { it.third == com.example.kpkn.data.media.video.FrameNeeds.SKIP })
        assertEquals(0, stats.last().plateTrackingFrames)
        assertEquals("PROPOSED", stats.last().plateStage)
        assertFalse(TechniqueSidecars.partFile(whole).exists())

        // 160 delivered callbacks, not source indices: leaves the 150-callback checkpoint after SKIP began.
        val failure = failed(analyze(observedPipeline(cutNeeds, failAfter = 160), case.request(cut)).outcome)
        assertTrue(failure.transient)
        val part = TechniqueSidecars.partFile(cut)
        val checkpoint = checkNotNull(AnalysisCheckpoints.read(part))
        val header = checkpoint.header
        assertEquals(2, header.poseStride)
        assertEquals(AnalysisCheckpoint.STAGE_PROPOSED, header.plateStage)
        assertTrue(header.nextIndex > 150) // SKIP consumes an index without a callback.
        assertEquals(1, header.nextIndex % 2) // checkpoint callback is an even, pose-due global frame.
        assertEquals(case.pts[header.nextIndex - 1], header.lastPtsUs)
        val checkpointRequest = cutNeeds.single { it.second == header.lastPtsUs }
        assertEquals(header.nextIndex, checkpointRequest.first + 1)
        assertEquals(case.pts.take(header.nextIndex).filterIndexed { index, _ -> index % 2 == 0 },
            checkpoint.pose.frames.map { it.tUs })
        assertTrue(cutNeeds.any { it.third == com.example.kpkn.data.media.video.FrameNeeds.SKIP })
        assertEquals(0, stats.last().plateTrackingFrames)
        assertTrue(checkNotNull(checkpoint.bar).samples.isEmpty())

        // A different recommendation must not replace the stride preserved by this checkpoint.
        done(analyze(observedPipeline(resumeNeeds, recommendedStride = 1), case.request(cut)).outcome)
        assertEquals(2, stats.last().poseStride)
        assertEquals(listOf(header.lastPtsUs + 1L), sources.last().decodeStarts.toList())
        assertEquals(header.lastPtsUs, stats.last().resumedFromPtsUs)
        assertEquals(0, resumeNeeds.first().first)
        assertEquals(case.pts[header.nextIndex], resumeNeeds.first().second)
        assertEquals(com.example.kpkn.data.media.video.FrameNeeds.SKIP, resumeNeeds.first().third)
        assertEquals(case.pts.drop(header.nextIndex), resumeNeeds.map { it.second })
        for ((localIndex, ptsUs, need) in resumeNeeds) {
            val globalIndex = header.nextIndex + localIndex
            assertEquals(case.pts[globalIndex], ptsUs)
            assertEquals(if (globalIndex % 2 == 0) com.example.kpkn.data.media.video.FrameNeeds.RGBA
                else com.example.kpkn.data.media.video.FrameNeeds.SKIP, need)
        }
        val resumedPose = storedPose(cut)
        assertEquals(expectedPosePts, resumedPose.frames.map { it.tUs })
        assertEquals(wholePose.frames.map { it.tUs }, resumedPose.frames.map { it.tUs })
        assertEquals(expectedPosePts.size, resumedPose.frames.map { it.tUs }.distinct().size)
        val bar = checkNotNull(BarTrackCodec.decode(TechniqueSidecars.barFile(cut).readBytes()))
        assertEquals(checkNotNull(checkpoint.bar).seed, bar.seed)
        assertFalse(checkNotNull(bar.seed).userConfirmed)
        assertTrue(bar.samples.isEmpty())
        assertEquals(0, stats.last().plateTrackingFrames)
        assertTrue(stats.last().plateTrackingDeferred)
        assertFalse(part.exists())
        assertTrue(estimators.all { it.staleReads.isEmpty() && it.notIncreasing.isEmpty() })
    }


}
