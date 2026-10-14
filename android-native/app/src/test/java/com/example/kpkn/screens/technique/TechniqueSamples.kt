package com.example.kpkn.screens.technique

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
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
}
