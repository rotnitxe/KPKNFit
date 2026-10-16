package com.example.kpkn.screens.workout.technique

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import com.example.kpkn.data.media.technique.CaptureMetadata
import com.example.kpkn.data.media.technique.CaptureMetadataFile
import com.example.kpkn.data.media.technique.TechniqueSidecars
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Calcula el ángulo de balanceo (roll) del móvil respecto al fotograma GRABADO (§4 «Convenios VT1b»).
 *
 * Fórmula: `wrap(atan2(gx, gy) - grados(targetRotation))` con convención VT1b:
 * positivo si el contenido se ve girado en sentido horario (móvil inclinado en sentido antihorario).
 * Si |roll| > 45°, devuelve null (otra orientación, no una mera inclinación).
 */
fun rollForFrameDeg(gx: Float, gy: Float, targetRotation: Int): Float? {
    val targetDeg = when (targetRotation) {
        Surface.ROTATION_0 -> 0.0
        Surface.ROTATION_90 -> 90.0
        Surface.ROTATION_180 -> 180.0
        Surface.ROTATION_270 -> 270.0
        90 -> 90.0
        180 -> 180.0
        270 -> 270.0
        else -> 0.0
    }
    val rawDeg = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble()))
    val diff = wrap180(rawDeg - targetDeg)
    if (abs(diff) > 45.0) return null
    return diff.toFloat()
}

/**
 * Normaliza un ángulo a (-180, 180].
 */
fun wrap180(degrees: Double): Double {
    var d = degrees
    while (d <= -180.0) d += 360.0
    while (d > 180.0) d -= 360.0
    return d
}

/**
 * Calcula la distancia focal en píxeles del fotograma grabado:
 * `f_px = (focalMm * videoLongEdgePx) / sensorLongEdgeMm`.
 * Devuelve null si cualquiera de las dimensiones o la focal es <= 0.
 */
fun focalPx(focalMm: Float, sensorLongEdgeMm: Float, videoLongEdgePx: Float): Float? {
    if (focalMm <= 0f || sensorLongEdgeMm <= 0f || videoLongEdgePx <= 0f) return null
    return (focalMm * videoLongEdgePx) / sensorLongEdgeMm
}

/**
 * Lee el lado largo (max(width, height)) del vídeo grabado directamente del archivo finalizado.
 */
fun readVideoLongEdgePx(videoFile: File): Float? {
    if (!videoFile.exists() || videoFile.length() == 0L) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(videoFile.absolutePath)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: return null
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: return null
        maxOf(w, h).takeIf { it > 0f }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Registra los metadatos de captura (§3.8 y §9 punto 6) durante la grabación:
 * - Gravedad (TYPE_GRAVITY) → mediana de roll y pitch (convenios de VT1b);
 * - Focal en píxeles del vídeo grabado desde Camera2CameraInfo;
 * - Fps pedidos y concedidos;
 * - Lente y modelo del dispositivo;
 * - Escribe el resultado en `<id>.capture.json`.
 */
class CaptureMetadataRecorder(
    private val context: Context,
    private val cameraInfo: CameraInfo?,
    private val requestedFps: Float?,
    private val grantedFps: Float?,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val rollSamples = mutableListOf<Float>()
    private val pitchSamples = mutableListOf<Float>()

    @Volatile
    private var activeTargetRotation: Int = Surface.ROTATION_0

    @Volatile
    private var recordingStartedAtMs: Long? = null

    val currentRollDeg: Float?
        get() = synchronized(this) { rollSamples.lastOrNull() }

    fun start(targetRotation: Int = Surface.ROTATION_0) {
        synchronized(this) {
            activeTargetRotation = targetRotation
            rollSamples.clear()
            pitchSamples.clear()
            recordingStartedAtMs = System.currentTimeMillis()
        }
        gravitySensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop(videoLongEdgePx: Float? = null): CaptureMetadata {
        sensorManager?.unregisterListener(this)
        val (medianRoll, medianPitch, startedAt) = synchronized(this) {
            Triple(median(rollSamples), median(pitchSamples), recordingStartedAtMs)
        }

        val focalPx = calculateFocalLengthPx(videoLongEdgePx)
        val lensName = cameraInfo?.let { getLensName(it) } ?: "main"
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        return CaptureMetadata(
            requestedFps = requestedFps,
            grantedFps = grantedFps,
            gravityRollDeg = medianRoll,
            gravityPitchDeg = medianPitch,
            focalLengthPx = focalPx,
            lens = lensName,
            deviceModel = model,
            recordedAtMs = startedAt,
        )
    }

    fun save(videoFile: File): CaptureMetadata {
        val longEdge = readVideoLongEdgePx(videoFile)
        val metadata = stop(longEdge)
        val captureFile = TechniqueSidecars.captureFile(videoFile)
        runCatching {
            CaptureMetadataFile.write(captureFile, metadata)
        }
        return metadata
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GRAVITY) return
        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]

        val targetRot = synchronized(this) { activeTargetRotation }
        val rollDeg = rollForFrameDeg(gx, gy, targetRot)
        // Pitch: atan2(sqrt(gx^2 + gy^2), gz) en grados (~90° en vertical, 0° horizontal pantalla arriba)
        val pitchDeg = Math.toDegrees(atan2(sqrt((gx * gx + gy * gy).toDouble()), gz.toDouble())).toFloat()

        synchronized(this) {
            if (rollDeg != null) {
                rollSamples.add(rollDeg)
            }
            pitchSamples.add(pitchDeg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateFocalLengthPx(recordedLongEdge: Float? = null): Float? {
        val info = cameraInfo ?: return null
        return try {
            val c2 = Camera2CameraInfo.from(info)
            val focals = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalMm = focals?.firstOrNull() ?: return null
            val sensorSize = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val longEdge = recordedLongEdge ?: 1920f
            val sensorLongEdge = maxOf(sensorSize.width, sensorSize.height)
            focalPx(focalMm, sensorLongEdge, longEdge)
        } catch (_: Exception) {
            null
        }
    }

    private fun getLensName(info: CameraInfo): String = try {
        val c2 = Camera2CameraInfo.from(info)
        val facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
        when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_BACK -> "main"
            else -> "main"
        }
    } catch (_: Exception) {
        "main"
    }

    private fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }
}
