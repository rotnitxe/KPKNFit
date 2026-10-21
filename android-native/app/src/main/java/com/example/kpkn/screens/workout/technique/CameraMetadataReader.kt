package com.example.kpkn.screens.workout.technique

/** Sin dimensiones del vídeo finalizado no se inventa una focal; el motor conserva su FOV de respaldo. */
internal fun recordedFocalPx(focalMm: Float?, sensorLongEdgeMm: Float?, recordedLongEdgePx: Float?): Float? {
    return focalPx(focalMm ?: return null, sensorLongEdgeMm ?: return null, recordedLongEdgePx ?: return null)
}
