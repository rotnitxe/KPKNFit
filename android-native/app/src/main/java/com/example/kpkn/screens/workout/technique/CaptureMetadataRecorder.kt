package com.example.kpkn.screens.workout.technique

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import com.example.kpkn.data.media.technique.CaptureMetadata
import com.example.kpkn.data.media.technique.CaptureMetadataFile
import com.example.kpkn.data.media.technique.TechniqueSidecars
import java.io.File
import kotlin.math.atan2
import kotlin.math.sqrt

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
    private var recordingStartedAtMs: Long? = null

    val currentRollDeg: Float?
        get() = synchronized(this) { rollSamples.lastOrNull() }

    fun start() {
        synchronized(this) {
            rollSamples.clear()
            pitchSamples.clear()
            recordingStartedAtMs = System.currentTimeMillis()
        }
        gravitySensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop(): CaptureMetadata {
        sensorManager?.unregisterListener(this)
        val (medianRoll, medianPitch, startedAt) = synchronized(this) {
            Triple(median(rollSamples), median(pitchSamples), recordingStartedAtMs)
        }

        val focalPx = calculateFocalLengthPx()
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
        val metadata = stop()
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

        // Roll: atan2(gx, -gy) en grados (VT1b: roll > 0 en sentido horario al inclinar la parte superior a la derecha)
        val rollDeg = Math.toDegrees(atan2(gx.toDouble(), -gy.toDouble())).toFloat()
        // Pitch: atan2(sqrt(gx^2 + gy^2), gz) en grados (~90° en vertical, 0° horizontal pantalla arriba)
        val pitchDeg = Math.toDegrees(atan2(sqrt((gx * gx + gy * gy).toDouble()), gz.toDouble())).toFloat()

        synchronized(this) {
            rollSamples.add(rollDeg)
            pitchSamples.add(pitchDeg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateFocalLengthPx(): Float? {
        val info = cameraInfo ?: return null
        return try {
            val c2 = Camera2CameraInfo.from(info)
            val focals = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalMm = focals?.firstOrNull() ?: return null
            val sensorSize = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val recordedLongEdge = 1920f
            val sensorLongEdge = maxOf(sensorSize.width, sensorSize.height)
            if (sensorLongEdge <= 0f) return null
            (focalMm * recordedLongEdge) / sensorLongEdge
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
