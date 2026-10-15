package com.example.kpkn.screens.technique

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.DepthClass
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.ObservationText
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.RepPhaseTimes
import com.example.kpkn.domain.biomechanics.metrics.RepReport
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.metrics.TechniqueObservation
import com.example.kpkn.domain.biomechanics.metrics.TempoSummary
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier
import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import com.example.kpkn.screens.technique.analysis.AnalysisPhase
import com.example.kpkn.screens.technique.analysis.AnalysisStatusUi
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisUiState
import com.example.kpkn.screens.technique.analysis.buildResults
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import com.example.kpkn.screens.technique.player.TechniqueRepMark

/**
 * Datos de ejemplo de las pantallas de técnica en vídeo (VT7a en adelante): hojas de render y pruebas. Todo sale del
 * `SyntheticLifter`, así que es determinista y no depende de ningún vídeo. Las pantallas siguientes (análisis, tendencias,
 * comparar) añaden sus muestras AL FINAL de este objeto.
 */
internal object TechniqueSamples {
    /** Tamaño del vídeo de ejemplo: vertical 9:16, como una grabación del móvil. */
    const val VIDEO_WIDTH = 720
    const val VIDEO_HEIGHT = 1280

    /** Cámara vertical de lado a 4,5 m con zoom: el atleta llena en torno al 55 % del alto y cabe entero a lo ancho. */
    val portraitCamera: SynthCamera = SynthCamera(
        yawDeg = 90.0,
        distanceM = 4.5,
        heightM = 0.9,
        hfovDeg = 22.0,
        widthPx = VIDEO_WIDTH,
        heightPx = VIDEO_HEIGHT,
    )

    /** Seis sentadillas con 1,8 s de bajada, 0,4 s abajo, 1,2 s de subida y 0,5 s arriba, entre dos reposos de 1,5 s. */
    val squatResult: SynthResult by lazy {
        SyntheticLifter.generate(
            SynthConfig(
                movement = SynthMovement.SQUAT,
                camera = portraitCamera,
                script = SynthTestSupport.repScript(6, eccS = 1.8, p1S = 0.4, conS = 1.2, p2S = 0.5, lead = 1.5, tail = 1.5),
            ),
        )
    }

    /** El informe del motor sobre [squatResult] (sin disco). */
    val squatReport: SetTechniqueReport by lazy {
        TechniqueAnalysisEngine.analyze(
            TechniqueAnalysisInput(
                pose = squatResult.track,
                bar = null,
                profile = AnalysisProfiles.of(AnalysisProfileId.SQUAT),
                loadKg = null,
                userHeightCm = null,
                setHints = emptySet(),
            ),
        )
    }

    /** Un disco sintético sobre el hombro cercano de [squatResult], todas las muestras de detector (cobertura completa). */
    val squatPlate: BarTrack by lazy { RepTestSupport.plateFromWrist(squatResult, PoseLandmark.LEFT_SHOULDER) }

    /** La capa de [squatResult] sin disco: la trayectoria sale del sustituto de pose (el hombro). */
    val squatOverlay: TechniqueOverlayData by lazy {
        TechniqueOverlayData.from(squatResult.track, null, squatReport, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    /** La capa de [squatResult] con el disco como punto de barra. */
    val squatPlateOverlay: TechniqueOverlayData by lazy {
        TechniqueOverlayData.from(squatResult.track, squatPlate, squatReport, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    /** Duración del vídeo de las marcas de ejemplo: 31 s. */
    const val MIXED_DURATION_US = 31_000_000L

    /**
     * Ocho repeticiones de una serie de 31 s: la 3.ª parcial, la 6.ª fallida y las demás completas. Salen cada 3,3 s y duran 2,9 s;
     * sirven para la línea de tiempo (con la 4.ª elegida) y para los chips («1 2 3·p 4 5 6·f 7 8»).
     */
    val mixedMarks: List<TechniqueRepMark> by lazy {
        val kinds = listOf(
            RepKind.FULL, RepKind.FULL, RepKind.PARTIAL, RepKind.FULL,
            RepKind.FULL, RepKind.FAILED, RepKind.FULL, RepKind.FULL,
        )
        kinds.mapIndexed { i, kind ->
            val start = 2_400_000L + i * 3_300_000L
            TechniqueRepMark(
                index = i,
                startUs = start,
                turnUs = start + 1_500_000L,
                endUs = start + 2_900_000L,
                kind = kind,
                partialKind = if (kind == RepKind.PARTIAL) PartialKind.REST_END else null,
            )
        }
    }

    /** Un disco con las muestras de [source] en vez de las de detector (para probar la cobertura). */
    fun plateWithSource(source: BarSampleSource): BarTrack {
        val plate = squatPlate
        return plate.copy(samples = plate.samples.map { s: BarSample -> s.copy(source = source) })
    }

    // ── Pantalla de análisis (VT7b) ──────────────────────────────────────────────────────────────────────────────────────────

    private const val ANALYSIS_REPS = 10
    private const val ANALYSIS_LEAD_S = 1.5
    private const val ANALYSIS_TURN_PAUSE_S = 1.0
    private const val ANALYSIS_TOP_PAUSE_S = 0.3

    // La bajada se alarga y la subida se frena con la fatiga: la serie cae de 0,62 a 0,50 m/s (−19 %).
    private fun analysisEccS(i: Int): Double = 2.8 + 0.08 * i
    private fun analysisConS(i: Int): Double = 0.9 + 0.025 * i

    /** La 3.ª repetición es parcial y la 10.ª fallida; las otras ocho, completas. */
    private fun analysisKind(i: Int): RepKind = when (i) {
        2 -> RepKind.PARTIAL
        9 -> RepKind.FAILED
        else -> RepKind.FULL
    }

    /** Segundo (desde el principio del vídeo) en el que sale la repetición [i] del reposo. */
    private fun analysisStartS(i: Int): Double =
        ANALYSIS_LEAD_S + (0 until i).sumOf { analysisEccS(it) + ANALYSIS_TURN_PAUSE_S + analysisConS(it) + ANALYSIS_TOP_PAUSE_S }

    private fun us(seconds: Double): Long = Math.round(seconds * 1_000_000.0)

    /** Diez sentadillas a tempo lento (3-1-1-0) de la pantalla de análisis, con la 3.ª parcial y la 10.ª fallida. */
    val analysisResult: SynthResult by lazy {
        val script = buildList<SynthSegment> {
            add(SynthSegment.Idle(ANALYSIS_LEAD_S))
            for (i in 0 until ANALYSIS_REPS) {
                add(
                    SynthSegment.Rep(
                        eccS = analysisEccS(i),
                        p1S = ANALYSIS_TURN_PAUSE_S,
                        conS = analysisConS(i),
                        p2S = ANALYSIS_TOP_PAUSE_S,
                        kind = when (analysisKind(i)) {
                            RepKind.FULL -> SynthRepKind.FULL
                            RepKind.PARTIAL -> SynthRepKind.PARTIAL_FAR
                            RepKind.FAILED -> SynthRepKind.FAILED
                        },
                    ),
                )
            }
            add(SynthSegment.Idle(2.0))
        }
        SyntheticLifter.generate(SynthConfig(movement = SynthMovement.SQUAT, camera = portraitCamera, script = script))
    }

    /** El disco sintético sobre el hombro cercano de [analysisResult], todas las muestras de detector, sin semilla ni diámetro. */
    val analysisPlate: BarTrack by lazy { RepTestSupport.plateFromWrist(analysisResult, PoseLandmark.LEFT_SHOULDER) }

    /** El disco que habría hallado el detector en el fondo de la 1.ª repetición, sin confirmar. */
    val analysisSeed: PlateSeed by lazy {
        val turnUs = us(analysisStartS(0) + analysisEccS(0) + ANALYSIS_TURN_PAUSE_S / 2.0)
        val sample: BarSample = analysisPlate.samples.minByOrNull { Math.abs(it.tUs - turnUs) }!!
        PlateSeed(tUs = sample.tUs, cx = sample.cx, cy = sample.cy, r = sample.r, userConfirmed = false)
    }

    private fun analysisRep(i: Int, unit: VelocityUnit?): RepReport {
        val kind = analysisKind(i)
        val full = kind == RepKind.FULL
        val start = analysisStartS(i)
        val turnStart = start + analysisEccS(i)
        val turnEnd = turnStart + ANALYSIS_TURN_PAUSE_S
        val arrive = turnEnd + analysisConS(i)
        // Velocidad media de la concéntrica: en m/s con el disco calibrado, en alturas corporales por segundo si no.
        val speed = (if (unit == VelocityUnit.MPS) 0.62 else 0.34) * 0.9 / analysisConS(i)
        return RepReport(
            index = i,
            kind = kind,
            partialKind = if (kind == RepKind.PARTIAL) PartialKind.REST_END else null,
            phases = RepPhaseTimes(us(start), us(turnStart), us(turnEnd), us(arrive)),
            eccentricMs = Math.round(analysisEccS(i) * 1000.0).toInt(),
            turnPauseMs = if (full) 1000 else 0,
            concentricMs = if (full) Math.round(analysisConS(i) * 1000.0).toInt() else null,
            restPauseMs = if (full) 300 else null,
            amplitudeFraction = when (kind) {
                RepKind.FULL -> 0.98
                RepKind.PARTIAL -> 0.55
                RepKind.FAILED -> 0.5
            },
            romDeg = if (full) 112.0 - 0.9 * i else null,
            depthClass = if (full) (if (i == 8) DepthClass.PARALLEL else DepthClass.BELOW) else null,
            depthDeg = if (full) 14.0 - i else null,
            mcv = if (full && unit != null) speed else null,
        )
    }

    private fun analysisObservations(): List<TechniqueObservation> = listOf(
        TechniqueObservation(
            code = "squat_depth_shallow_rep",
            text = ObservationText.squatDepthShallowRep(listOf(8), 6.0),
            repIndices = listOf(8),
            atUs = us(analysisStartS(8) + analysisEccS(8) + ANALYSIS_TURN_PAUSE_S / 2.0),
            magnitude = 6.0,
            rank = 0.9,
        ),
        TechniqueObservation(
            code = "squat_hips_first",
            text = ObservationText.squatHipsFirst(listOf(7, 8), 1.8, 9.0),
            repIndices = listOf(7, 8),
            atUs = us(analysisStartS(7) + analysisEccS(7) + ANALYSIS_TURN_PAUSE_S + 0.2),
            magnitude = 1.8,
            rank = 0.8,
        ),
        TechniqueObservation(
            code = "rom_trend",
            text = ObservationText.romTrend(RomAngle.KNEE, -7.0, 6.0),
            repIndices = listOf(0, 8),
            atUs = null,
            magnitude = 7.0,
            rank = 0.6,
        ),
    )

    /**
     * El informe de la serie de [analysisResult]: ocho completas, una parcial y una fallida, a 3-1-1-0, con el disco calibrado (m/s,
     * pérdida del 19 %), tres observaciones y confianza ALTA. Sin avisos.
     */
    val analysisReport: SetTechniqueReport by lazy { buildAnalysisReport(VelocityUnit.MPS) }

    /** El mismo informe sin calibrar el disco: la velocidad solo es relativa (alturas corporales por segundo), sin m/s. */
    val analysisReportRelative: SetTechniqueReport by lazy { buildAnalysisReport(VelocityUnit.BODY_HEIGHTS_PER_S) }

    private fun buildAnalysisReport(unit: VelocityUnit): SetTechniqueReport {
        val mps = unit == VelocityUnit.MPS
        val reps = (0 until ANALYSIS_REPS).map { analysisRep(it, unit) }
        val full = reps.filter { it.kind == RepKind.FULL }
        val speeds = full.mapNotNull { it.mcv }
        val available = buildSet {
            addAll(
                listOf(
                    MetricKey.REPS, MetricKey.PHASES, MetricKey.TUT, MetricKey.TEMPO, MetricKey.ROM, MetricKey.DEPTH,
                    MetricKey.PATH, MetricKey.TRUNK, MetricKey.VELOCITY_RELATIVE,
                ),
            )
            if (mps) addAll(listOf(MetricKey.PATH_ABSOLUTE, MetricKey.VELOCITY_MPS))
        }
        return SetTechniqueReport(
            engineVersion = TechniqueAnalysisEngine.ENGINE_VERSION,
            profileId = AnalysisProfileId.SQUAT,
            tier = ProfileTier.A,
            view = ViewKind.SIDE,
            nearSide = "left",
            confidence = CountConfidence.ALTA,
            confidenceScore = 0.93,
            reps = reps,
            repCount = full.size,
            partialReps = reps.count { it.kind == RepKind.PARTIAL },
            failedReps = reps.count { it.kind == RepKind.FAILED },
            miniSets = 1,
            tutMs = 41_170L,
            setDurationMs = us(analysisStartS(ANALYSIS_REPS)) / 1000L,
            restPositionMs = 1_500L,
            tempo = TempoSummary(3160, 1000, 1010, 300),
            romMeanDeg = 108.2,
            romSdDeg = 3.4,
            romCvPct = 3.1,
            romTrendDeg = -7.0,
            velocityUnit = unit,
            velocityLossPct = 18.9,
            mcvFirst = speeds.first(),
            mcvBest = speeds.max(),
            mcvLast = speeds.last(),
            peakBest = null,
            scale = if (mps) ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.0025, 0.98, null) else ScaleInfo.NONE,
            trunkMeanDeg = 31.0,
            barDeviation = if (mps) 3.2 else 6.0,
            barDeviationUnit = if (mps) "cm" else "%ROM",
            observations = analysisObservations(),
            available = available,
            flags = emptySet(),
            analyzedFps = 30.0,
            sourceFps = 30.0,
        )
    }

    /** Confianza MEDIA, vista oblicua y cuatro avisos: el conteo es una estimación (≈) y los consejos salen de los avisos. */
    val analysisReportMedium: SetTechniqueReport by lazy {
        analysisReportRelative.copy(
            view = ViewKind.OBLIQUE,
            confidence = CountConfidence.MEDIA,
            confidenceScore = 0.58,
            flags = setOf(AnalysisFlag.OBLIQUE_VIEW, AnalysisFlag.FAR_SIDE_WORKING, AnalysisFlag.MULTI_PERSON, AnalysisFlag.LOW_FPS),
        )
    }

    /** Barras relativas (VELOCITY_RELATIVE) pero sin pérdida medible: la pérdida es «sin dato fiable», nunca un 0 %. */
    val analysisReportLossUnknown: SetTechniqueReport by lazy { analysisReportRelative.copy(velocityLossPct = null) }

    /** La capa del reproductor de [analysisReport]: el esqueleto de [analysisResult], el disco como trayectoria y las diez marcas. */
    val analysisOverlay: TechniqueOverlayData by lazy {
        TechniqueOverlayData.from(analysisResult.track, analysisPlate, analysisReport, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    /** Un instante en la subida de la 4.ª repetición (la que la hoja deja elegida), para dibujar la capa. */
    val analysisPositionUs: Long by lazy { us(analysisStartS(3) + analysisEccS(3) + ANALYSIS_TURN_PAUSE_S + analysisConS(3) * 0.5) }

    /** Duración del vídeo de [analysisResult] (µs). */
    val analysisDurationUs: Long by lazy { us(analysisStartS(ANALYSIS_REPS) + 2.0) }

    /**
     * El estado de la pantalla con el análisis terminado de [report]: los módulos salen de `buildResults` como en la app. [bar] y
     * [overrides] deciden el estado del módulo del disco; [usesPlate] dice si el perfil lo usa.
     */
    fun analysisUiState(
        report: SetTechniqueReport = analysisReport,
        bar: BarTrack? = null,
        overrides: CaptureOverrides = CaptureOverrides(),
        usesPlate: Boolean = false,
    ): TechniqueAnalysisUiState {
        val overlay = TechniqueOverlayData.from(analysisResult.track, analysisPlate, report, VIDEO_WIDTH, VIDEO_HEIGHT)
        return TechniqueAnalysisUiState(
            phase = AnalysisPhase.READY,
            exerciseName = "Sentadilla trasera",
            setInfo = "Serie 3 · 80 kg × 8 · 13 oct",
            videoPath = "/muestra.mp4",
            videoAspect = overlay.videoAspect,
            overlay = overlay,
            status = AnalysisStatusUi.Done,
            results = buildResults(
                report = report,
                marks = overlay.reps,
                bar = bar,
                overrides = overrides,
                usesPlate = usesPlate,
                defaultDiameterMm = 450,
                frameWidth = VIDEO_WIDTH,
                frameHeight = VIDEO_HEIGHT,
            ),
            exerciseKey = "back_squat",
            profileId = report.profileId,
        )
    }

    /** El mismo estado sin análisis: [status] en lugar de los módulos. */
    fun statusUiState(status: AnalysisStatusUi): TechniqueAnalysisUiState = TechniqueAnalysisUiState(
        phase = AnalysisPhase.READY,
        exerciseName = "Sentadilla trasera",
        setInfo = "Serie 3 · 80 kg × 8 · 13 oct",
        videoPath = "/muestra.mp4",
        videoAspect = VIDEO_WIDTH.toFloat() / VIDEO_HEIGHT,
        status = status,
        exerciseKey = "back_squat",
    )

    /** Un fotograma de mentira del vídeo (gris degradado) con un disco oscuro de aro claro en el sitio de [seed]. */
    fun plateFrameImage(seed: PlateSeed): ImageBitmap {
        val width = VIDEO_WIDTH
        val height = VIDEO_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF6B7075.toInt(), 0xFF30353A.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        val disc = Paint().apply {
            isAntiAlias = true
            color = 0xFF16181A.toInt()
        }
        val rim = Paint().apply {
            isAntiAlias = true
            color = 0xFFB9BEC2.toInt()
            style = Paint.Style.STROKE
            strokeWidth = seed.r * 0.12f
        }
        canvas.drawCircle(seed.cx, seed.cy, seed.r, disc)
        canvas.drawCircle(seed.cx, seed.cy, seed.r * 0.94f, rim)
        canvas.drawCircle(seed.cx, seed.cy, seed.r * 0.22f, rim)
        return bitmap.asImageBitmap()
    }
}
