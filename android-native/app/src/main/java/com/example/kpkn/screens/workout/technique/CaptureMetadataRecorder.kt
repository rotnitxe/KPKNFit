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
    if (!focalMm.isFinite() || !sensorLongEdgeMm.isFinite() || !videoLongEdgePx.isFinite() || focalMm <= 0f || sensorLongEdgeMm <= 0f || videoLongEdgePx <= 0f) return null
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
    private val gravitySource: CaptureGravitySource = AndroidCaptureGravitySource(context),
) : java.io.Closeable {

    private var previewStarted = false
    private var collecting = false
    private var closed = false
    private var frozenSamples: CaptureMetadataSamples? = null
    private var latestGravity: FloatArray? = null

    private val rollSamples = mutableListOf<Float>()
    private val pitchSamples = mutableListOf<Float>()

    @Volatile
    private var activeTargetRotation: Int = Surface.ROTATION_0

    @Volatile
    private var recordingStartedAtMs: Long? = null

    val currentRollDeg: Float?
        get() = synchronized(this) { latestGravity?.let { rollForFrameDeg(it[0], it[1], activeTargetRotation) } }

    /** La inclinación se mide desde la vista previa; las medianas solo incluyen la grabación. */
    fun startPreview(targetRotation: Int = Surface.ROTATION_0) {
        synchronized(this) {
            if (closed) return
            activeTargetRotation = targetRotation
            if (previewStarted) return
            previewStarted = true
        }
        gravitySource.start(::onGravity)
    }

    fun updateTargetRotation(targetRotation: Int) {
        synchronized(this) { activeTargetRotation = targetRotation }
    }

    fun start(targetRotation: Int = Surface.ROTATION_0) {
        startPreview(targetRotation)
        synchronized(this) {
            if (closed) return
            frozenSamples = null
            rollSamples.clear()
            pitchSamples.clear()
            collecting = true
            recordingStartedAtMs = System.currentTimeMillis()
        }
    }

    /** Solo congela memoria; Camera2, las medianas y el archivo se resuelven después en IO. */
    internal fun stopCollecting(): CaptureMetadataSamples = synchronized(this) {
        collecting = false
        frozenSamples ?: CaptureMetadataSamples(
            rollSamples.toList(), pitchSamples.toList(), recordingStartedAtMs,
        ).also { frozenSamples = it }
    }

    fun stop(videoLongEdgePx: Float? = null): CaptureMetadata = metadata(stopCollecting(), videoLongEdgePx)

    private fun metadata(samples: CaptureMetadataSamples, videoLongEdgePx: Float?): CaptureMetadata {
        val medianRoll = median(samples.roll)
        val medianPitch = median(samples.pitch)
        val startedAt = samples.startedAtMs

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
        return save(videoFile, stopCollecting())
    }

    internal fun save(videoFile: File, samples: CaptureMetadataSamples): CaptureMetadata {
        val longEdge = readVideoLongEdgePx(videoFile)
        val metadata = metadata(samples, longEdge)
        val captureFile = TechniqueSidecars.captureFile(videoFile)
        runCatching {
            CaptureMetadataFile.write(captureFile, metadata)
        }
        return metadata
    }

    private fun onGravity(gx: Float, gy: Float, gz: Float) {
        synchronized(this) {
            if (!previewStarted) return
            val gravity = latestGravity ?: FloatArray(3).also { latestGravity = it }
            gravity[0] = gx
            gravity[1] = gy
            gravity[2] = gz
            if (!collecting) return
            rollForFrameDeg(gx, gy, activeTargetRotation)?.let(rollSamples::add)
            val pitch = Math.toDegrees(atan2(sqrt((gx * gx + gy * gy).toDouble()), gz.toDouble())).toFloat()
            pitchSamples.add(pitch)
        }
    }

    override fun close() {
        stopCollecting()
        synchronized(this) {
            closed = true
            previewStarted = false
            latestGravity = null
        }
        gravitySource.close()
    }

    private fun calculateFocalLengthPx(recordedLongEdge: Float? = null): Float? {
        val info = cameraInfo ?: return null
        return try {
            val c2 = Camera2CameraInfo.from(info)
            val focals = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalMm = focals?.firstOrNull() ?: return null
            val sensorSize = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val longEdge = recordedLongEdge ?: return null
            val sensorLongEdge = maxOf(sensorSize.width, sensorSize.height)
            recordedFocalPx(focalMm, sensorLongEdge, longEdge)
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

/** Fuente sustituible para comprobar la vista previa sin un sensor físico. */
interface CaptureGravitySource : java.io.Closeable {
    fun start(onGravity: (Float, Float, Float) -> Unit)
}

private class AndroidCaptureGravitySource(context: Context) : CaptureGravitySource, SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private var listener: ((Float, Float, Float) -> Unit)? = null
    override fun start(onGravity: (Float, Float, Float) -> Unit) {
        listener = onGravity
        sensor?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY && event.values.size >= 3) {
            listener?.invoke(event.values[0], event.values[1], event.values[2])
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun close() {
        manager?.unregisterListener(this)
        listener = null
    }
}

internal data class CaptureMetadataSamples(
    val roll: List<Float>,
    val pitch: List<Float>,
    val startedAtMs: Long?,
)

/** Cada intento posee su congelación: un Start tardío tras cancelar nunca vuelve a muestrear. */
internal class TechniqueCaptureMetadataInterval(
    private val recorder: CaptureMetadataRecorder?,
    private val rotation: Int,
) {
    private var started = false
    private var stopped = false
    private var samples: CaptureMetadataSamples? = null

    @Synchronized fun onStarted(): Boolean {
        if (stopped) return false
        if (!started) {
            started = true
            recorder?.start(rotation)
        }
        return true
    }

    @Synchronized fun stopCollecting() {
        if (stopped) return
        stopped = true
        samples = if (started) recorder?.stopCollecting() else CaptureMetadataSamples(emptyList(), emptyList(), null)
    }

    fun save(videoFile: File) {
        stopCollecting()
        val frozen = synchronized(this) { samples } ?: return
        recorder?.save(videoFile, frozen)
    }
}
