package com.example.kpkn.domain.biomechanics.bar

/** De dónde sale la muestra del disco. El índice del enum (`ordinal`) es lo que guarda el sidecar: no reordenar. */
enum class BarSampleSource { HOUGH, NCC, POSE_PRIOR, PREDICTED }

/**
 * Una muestra del disco por fotograma, en PÍXELES del mismo fotograma girado de análisis que la pose.
 * [axisRatio] = eje menor/eje mayor de la elipse del disco (1 = de frente); [quality] va de 0 a 1.
 */
data class BarSample(
    val tUs: Long,
    val cx: Float,
    val cy: Float,
    val r: Float,
    val axisRatio: Float,
    val quality: Float,
    val source: BarSampleSource,
)

/** Disco de partida que fija el detector o el usuario (círculo arrastrado y pellizcado en la pantalla de calibrar). */
data class PlateSeed(
    val tUs: Long,
    val cx: Float,
    val cy: Float,
    val r: Float,
    val userConfirmed: Boolean,
)

/** Trayectoria del disco de un vídeo (§3.2). */
data class BarTrack(
    val mediaId: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val plateDiameterMm: Int?,          // null = sin calibrar (sirve como señal, sin m/s)
    val seed: PlateSeed?,
    val samples: List<BarSample>,
    val version: Int = 1,
)
