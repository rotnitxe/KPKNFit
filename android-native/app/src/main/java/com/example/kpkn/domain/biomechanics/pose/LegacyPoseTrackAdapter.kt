package com.example.kpkn.domain.biomechanics.pose

import com.example.kpkn.domain.biomechanics.PoseJoint
import com.example.kpkn.domain.biomechanics.PoseTrack

/**
 * Lleva una traza v1 (`PoseTrack` de ML Kit a ~10 Hz) al modelo v2 para que el motor la cuente.
 *
 * La traza v1 no guarda profundidad, «world» ni la forma del fotograma fiable, así que el resultado lleva
 * `aspectKnown = false`: solo sirve para contar repeticiones, nunca para medir ángulos ni distancias.
 */
object LegacyPoseTrackAdapter {
    private val LEGACY_ENGINE = PoseEngineInfo("mlkit-pose", "base", "CPU")

    /**
     * v1 → v2: vis = pres = `inFrameLikelihood`, z = 0, sin «world», `nominalFps = sampleFps`, `aspectKnown = false`.
     * `frameWidth/Height` salen de `videoWidth/Height` (1×1 si faltan) y el tiempo pasa de ms a µs.
     * Un fotograma v1 sin puntos queda sin pose.
     */
    fun toV2(v1: PoseTrack): PoseTrackV2 {
        val frames = v1.frames.map { frame ->
            val image = HashMap<PoseLandmark, LandmarkSample>(frame.points.size * 2)
            for ((joint, p) in frame.points) {
                image[landmarkOf(joint)] = LandmarkSample(p.x, p.y, 0f, p.inFrameLikelihood, p.inFrameLikelihood)
            }
            val hasPose = image.isNotEmpty()
            PoseFrameV2.of(
                tUs = frame.timestampMs * 1000L,
                posesDetected = if (hasPose) 1 else 0,
                image = if (hasPose) image else null,
                world = null,
            )
        }
        return PoseTrackV2(
            mediaId = v1.mediaId,
            engine = LEGACY_ENGINE,
            frameWidth = v1.videoWidth?.takeIf { it > 0 } ?: 1,
            frameHeight = v1.videoHeight?.takeIf { it > 0 } ?: 1,
            rotationApplied = 0,
            nominalFps = v1.sampleFps.toFloat(),
            poseStride = 1,
            vfr = false,
            aspectKnown = false,
            frames = frames,
        )
    }

    private fun landmarkOf(joint: PoseJoint): PoseLandmark = when (joint) {
        PoseJoint.NOSE -> PoseLandmark.NOSE
        PoseJoint.LEFT_SHOULDER -> PoseLandmark.LEFT_SHOULDER
        PoseJoint.RIGHT_SHOULDER -> PoseLandmark.RIGHT_SHOULDER
        PoseJoint.LEFT_ELBOW -> PoseLandmark.LEFT_ELBOW
        PoseJoint.RIGHT_ELBOW -> PoseLandmark.RIGHT_ELBOW
        PoseJoint.LEFT_WRIST -> PoseLandmark.LEFT_WRIST
        PoseJoint.RIGHT_WRIST -> PoseLandmark.RIGHT_WRIST
        PoseJoint.LEFT_HIP -> PoseLandmark.LEFT_HIP
        PoseJoint.RIGHT_HIP -> PoseLandmark.RIGHT_HIP
        PoseJoint.LEFT_KNEE -> PoseLandmark.LEFT_KNEE
        PoseJoint.RIGHT_KNEE -> PoseLandmark.RIGHT_KNEE
        PoseJoint.LEFT_ANKLE -> PoseLandmark.LEFT_ANKLE
        PoseJoint.RIGHT_ANKLE -> PoseLandmark.RIGHT_ANKLE
    }
}
