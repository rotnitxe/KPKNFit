package com.example.kpkn.data.media.technique

/** Coordenada normalizada `(0..1, 0..1)` dentro del fotograma de análisis. */
data class NormalizedPoint(val x: Float, val y: Float)

/** Muestra de altura vertical normalizada de la barra o cadera en un instante `[tFraction]` (`0..1`). */
data class LiveSignalSample(val tFraction: Float, val heightNormalized: Float)

/** Etapa actual mostrada en la vista fullscreen «Analizando» / «En cola» / «Actualizando». */
enum class LiveAnalysisPhase {
    QUEUED,
    POSE_AND_BAR,
    COUNTING,
    METRICS,
    DONE,
}

/** Telemetría viva emitida en memoria durante el análisis en curso (sin persistir en Room). */
data class LiveAnalysisTelemetry(
    val mediaId: String,
    val progressPct: Int,
    val phase: LiveAnalysisPhase,
    val currentPtsMs: Long = 0L,
    val durationMs: Long = 0L,
    val signalSamples: List<LiveSignalSample> = emptyList(),
    val barTrailNormalized: List<NormalizedPoint> = emptyList(),
    val poseSegmentsNormalized: List<Pair<NormalizedPoint, NormalizedPoint>> = emptyList(),
)
