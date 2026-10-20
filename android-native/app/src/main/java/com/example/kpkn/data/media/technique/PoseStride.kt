package com.example.kpkn.data.media.technique

/**
 * Stride por cadencia real: conserva la regla normal y solo añade un paso por carga con un margen del 20 % sobre el mínimo.
 * El pipeline limita después el resultado a los strides que admite la reanudación.
 */
internal fun poseStrideFor(realFps: Double, maxPoseFps: Double, minPoseFps: Double, degraded: Boolean): Int {
    if (!realFps.isFinite() || realFps <= 0.0) return 1
    val normal = if (realFps > maxPoseFps) 2 else 1
    val candidate = normal + 1
    return if (degraded && realFps / candidate >= minPoseFps * 1.2) candidate else normal
}
