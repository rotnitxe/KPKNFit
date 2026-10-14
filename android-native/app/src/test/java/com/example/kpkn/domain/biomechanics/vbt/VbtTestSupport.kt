package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.tan

/** Ayudas de las pruebas de VBT: la geometría de la cámara sintética y discos sintéticos con la escala conocida. */
internal object VbtTestSupport {
    /** Focal en píxeles de la cámara sintética (el campo de visión horizontal va a lo largo del ancho del fotograma). */
    fun focalPx(camera: SynthCamera = SynthCamera()): Double = (camera.widthPx / 2.0) / tan(Math.toRadians(camera.hfovDeg / 2.0))

    /** Radio en píxeles de un disco de [diameterMm] a [depthM] metros de la cámara. */
    fun radiusPx(diameterMm: Int, depthM: Double, camera: SynthCamera = SynthCamera()): Float =
        (focalPx(camera) * (diameterMm / 2000.0) / depthM).toFloat()

    /** Profundidad (m) de la muñeca izquierda de un atleta de lado (ψ = 90°) con la cámara a [distanceM]: el hombro queda a 0,2266 m del plano medio. */
    fun leftWristDepthM(camera: SynthCamera = SynthCamera(), heightM: Double = 1.75): Double =
        camera.distanceM - 0.259 * heightM / 2.0

    /**
     * Un disco que sigue a una muñeca de la verdad (la traza limpia) con la escala del plano de esa muñeca: el radio en
     * píxeles es el de un disco de [diameterMm] a [depthM]. [noisePx] es el ruido gaussiano de la posición (el centro de un
     * Hough bueno tiene ~0,5 px). Con [diameterMm] = null la traza va sin calibrar.
     */
    fun plateFollowing(
        result: SynthResult,
        wrist: PoseLandmark,
        diameterMm: Int?,
        radiusPx: Float,
        axisRatio: Float = 1f,
        noisePx: Double = 0.0,
        seed: Long = 7L,
        source: BarSampleSource = BarSampleSource.HOUGH,
    ): BarTrack {
        val track = result.cleanTrack
        val w = track.frameWidth.toFloat()
        val h = track.frameHeight.toFloat()
        val rng = Random(seed)
        val samples = track.frames.map { f ->
            BarSample(
                tUs = f.tUs,
                cx = f.x(wrist) * w + (noisePx * rng.nextGaussian()).toFloat(),
                cy = f.y(wrist) * h + (noisePx * rng.nextGaussian()).toFloat(),
                r = radiusPx,
                axisRatio = axisRatio,
                quality = 0.9f,
                source = source,
            )
        }
        return BarTrack(track.mediaId, track.frameWidth, track.frameHeight, diameterMm, null, samples)
    }

    /**
     * Un disco que sube y baja solo, sin pose: Y(t) = [centreY] + [amplitudePx]·(½ − ½·cos(2πt/[periodS])), de [seconds] segundos a [fps].
     * Las velocidades son bajas junto a los extremos (hay fotogramas lentos para calibrar). El radio de un fotograma lo da
     * [radiusAt] (índice, velocidad en px/s); así se simula el desenfoque de movimiento.
     */
    fun oscillatingPlate(
        fps: Double = 30.0,
        seconds: Double = 8.0,
        diameterMm: Int? = 450,
        axisRatio: Float = 1f,
        centreX: Float = 360f,
        centreY: Float = 640f,
        amplitudePx: Float = 150f,
        periodS: Double = 3.0,
        quality: Float = 0.9f,
        source: BarSampleSource = BarSampleSource.HOUGH,
        width: Int = 720,
        height: Int = 1280,
        radiusAt: (index: Int, speedPxPerS: Double) -> Float = { _, _ -> 90f },
    ): BarTrack {
        val n = (seconds * fps).roundToLong().toInt() + 1
        val samples = ArrayList<BarSample>(n)
        for (i in 0 until n) {
            val t = i / fps
            val phase = 2.0 * PI * t / periodS
            val y = centreY + amplitudePx * (0.5 - 0.5 * cos(phase))
            val speed = amplitudePx * PI / periodS * kotlin.math.abs(kotlin.math.sin(phase))
            samples.add(
                BarSample(
                    tUs = (t * 1e6).roundToLong(),
                    cx = centreX,
                    cy = y.toFloat(),
                    r = radiusAt(i, speed),
                    axisRatio = axisRatio,
                    quality = quality,
                    source = source,
                ),
            )
        }
        return BarTrack("synthetic-plate", width, height, diameterMm, null, samples)
    }
}
