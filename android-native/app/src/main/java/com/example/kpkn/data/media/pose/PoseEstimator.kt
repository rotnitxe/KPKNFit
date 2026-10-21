package com.example.kpkn.data.media.pose

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Un fotograma para el estimador (§3.7): RGBA_8888 en un buffer DIRECTO de `width * height * 4` bytes, con `stride = width * 4`
 * y las filas de arriba abajo, ya girado. Es el `DecodedFrame.rgba` del decodificador tal cual, así que solo vale mientras dura
 * la llamada a [PoseEstimator.estimate].
 */
class RgbaFrame(val width: Int, val height: Int, val buffer: ByteBuffer)

/** Una pose: 33 puntos de imagen y 33 «world», en el orden de MediaPipe (`PoseLandmark.mp`). */
data class DetectedPose(val image: List<LandmarkSample>, val world: List<WorldSample>)

/**
 * Un motor de pose (§3.7): convierte fotogramas RGBA en las poses que ve el modelo, hasta `numPoses` por fotograma.
 * [estimate] bloquea hasta tener el resultado: nunca desde el hilo principal, y una llamada a la vez. En modo vídeo,
 * `timestampMs` ha de crecer estrictamente de un fotograma al siguiente.
 */
interface PoseEstimator : Closeable {
    /** Motor, modelo y delegado que producen las poses ahora mismo (el delegado puede pasar de GPU a CPU a mitad de camino). */
    val engine: PoseEngineInfo

    fun estimate(frame: RgbaFrame, timestampMs: Long): List<DetectedPose>
}

/**
 * Extensión opcional de [PoseEstimator] (no es parte del contrato de §3.7), como `VideoDecodeReport`: por qué el estimador no
 * usa la GPU. Se lee con `estimator as? PoseEstimatorReport`.
 */
interface PoseEstimatorReport {
    /** Motivo de que el delegado sea CPU habiéndose pedido GPU (GLES < 3.1, GPU vetada, excepción…); `null` si no hubo ninguno. */
    val delegateNote: String?
}
