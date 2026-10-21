package com.example.kpkn.data.media.pose

import android.graphics.Bitmap
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.TimeUnit

/**
 * SOLO DEPURACIÓN (no se integra): el ML Kit Pose de siempre (el de `PoseTrajectoryAnalyzer`) tras [PoseEstimator], para
 * compararlo en el banco con MediaPipe. Va en `STREAM_MODE`, que sigue la pose de un fotograma al siguiente, y por eso los
 * fotogramas han de llegar en orden (el tiempo no se usa).
 *
 * Una sola pose por fotograma. Los 33 puntos de ML Kit coinciden en índice con los de MediaPipe: `inFrameLikelihood` pasa a
 * visibilidad Y presencia, `x`/`y` se normalizan con el ancho y el alto del fotograma y `z` (misma escala que `x` en píxeles)
 * con el ancho, como hace MediaPipe. ML Kit no da «world»: va a ceros, y el [PoseTrackAssembler] lo trata como ausente.
 */
class MlKitPoseEstimator(
    detectorFactory: () -> PoseDetector = {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    },
) : PoseEstimator {
    private val detector: PoseDetector = detectorFactory()
    private var bitmap: Bitmap? = null

    override val engine: PoseEngineInfo = PoseEngineInfo(ENGINE_ID, MODEL, DELEGATE)

    override fun estimate(frame: RgbaFrame, timestampMs: Long): List<DetectedPose> {
        require(frame.buffer.capacity() >= frame.width * frame.height * 4) { "Buffer RGBA demasiado pequeño" }
        val current = bitmap
        val target = if (current != null && current.width == frame.width && current.height == frame.height) {
            current
        } else {
            current?.recycle()
            Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).also { bitmap = it }
        }
        frame.buffer.rewind()
        target.copyPixelsFromBuffer(frame.buffer)
        frame.buffer.rewind()
        val pose = Tasks.await(detector.process(InputImage.fromBitmap(target, 0)), AWAIT_SECONDS, TimeUnit.SECONDS)
        if (pose.allPoseLandmarks.isEmpty()) return emptyList()
        val width = frame.width.toFloat()
        val height = frame.height.toFloat()
        val image = ArrayList<LandmarkSample>(LANDMARKS)
        val world = ArrayList<WorldSample>(LANDMARKS)
        for (type in 0 until LANDMARKS) {
            val landmark = pose.getPoseLandmark(type)
            if (landmark == null) {
                // Un punto que ML Kit no da: x e y a NaN, que el ensamblador ignora.
                image.add(LandmarkSample(Float.NaN, Float.NaN, 0f, 0f, 0f))
            } else {
                val likelihood = landmark.inFrameLikelihood
                image.add(
                    LandmarkSample(
                        landmark.position.x / width,
                        landmark.position.y / height,
                        landmark.position3D.z / width,
                        likelihood,
                        likelihood,
                    ),
                )
            }
            world.add(WorldSample(0f, 0f, 0f))
        }
        return listOf(DetectedPose(image, world))
    }

    override fun close() {
        runCatching { detector.close() }
        bitmap?.recycle()
        bitmap = null
    }

    private companion object {
        const val ENGINE_ID = "mlkit-pose"
        const val MODEL = "base"
        const val DELEGATE = "CPU"
        const val LANDMARKS = 33
        const val AWAIT_SECONDS = 10L
    }
}
