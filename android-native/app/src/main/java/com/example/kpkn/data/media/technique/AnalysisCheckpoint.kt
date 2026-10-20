package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import kotlin.math.abs

/**
 * El punto de control de un análisis a medias (`<id>.pose.part`, §3.8): la traza de pose y la del disco hasta el último
 * fotograma procesado, más lo que hace falta para seguir desde ahí sin repetir lo hecho. El pipeline lo escribe cada 150
 * fotogramas y lo borra al terminar; si el trabajo se corta (el sistema lo mata, un fallo de E/S), el siguiente intento
 * decodifica desde [Header.lastPtsUs] en vez de empezar de cero.
 *
 * Solo se escribe cuando el disco ya no está en busca (ver [Header.plateStage]): el estado del rastreador se reduce entonces a la
 * última muestra fiable, con la que se arranca otro rastreador.
 */
internal class AnalysisCheckpoint(
    val header: Header,
    /** La traza de pose hasta [Header.lastPtsUs] (fotogramas con y sin atleta). */
    val pose: PoseTrackV2,
    /** La traza del disco hasta ese instante (hacia atrás ya resuelta); `null` si el perfil no usa el disco. */
    val bar: BarTrack?,
) {
    /**
     * @property poseVersion `TechniqueAnalysisPipeline.POSE_VERSION` con la que se escribió: otra versión descarta el punto de control.
     * @property videoBytes tamaño del vídeo; con [videoModifiedMs] dice que sigue siendo el mismo archivo.
     * @property usesPlate si el perfil seguía el disco.
     * @property poseStride 1 o 2: se conserva al reanudar si la cadencia de este vídeo mantiene el mínimo de pose.
     * @property timeScale µs reales por µs del contenedor (1 salvo en cámara lenta de galería).
     * @property lastPtsUs PTS del contenedor del último fotograma ya procesado: se reanuda desde el siguiente.
     * @property nextIndex índice (contando todos los fotogramas decodificados) que tendrá el primero tras reanudar.
     * @property plateStage `NONE` (sin disco), `TRACKING`, `PROPOSED` (semilla automática sin seguimiento) o `GAVE_UP`.
     * @property seedKey identidad de la semilla confirmada por el usuario con la que se hizo (`null` si no había).
     * @property poseErrors fotogramas cuya pose falló hasta ahora.
     */
    @Serializable
    data class Header(
        val format: Int = FORMAT,
        val mediaId: String,
        val poseVersion: Int,
        val videoBytes: Long,
        val videoModifiedMs: Long,
        val usesPlate: Boolean,
        val poseStride: Int,
        val timeScale: Double,
        val frameWidth: Int,
        val frameHeight: Int,
        val lastPtsUs: Long,
        val nextIndex: Int,
        val plateStage: String,
        val seedKey: String? = null,
        val poseErrors: Int = 0,
        // Optional diagnostic history; absent in old checkpoints. Never used to infer identity.
        val plateAttempts: Int = 0,
        val plateWaitingCounts: Map<String, Int> = emptyMap(),
        val plateLastWaitingReason: String? = null,
    )

    companion object {
        const val FORMAT = 1
        const val STAGE_NONE = "NONE"
        const val STAGE_TRACKING = "TRACKING"
        const val STAGE_GAVE_UP = "GAVE_UP"
        const val STAGE_PROPOSED = "PROPOSED"

        /** La identidad de una semilla confirmada: cambia si el usuario la mueve, y entonces el punto de control ya no vale. */
        fun seedKey(seed: PlateSeed?): String? = seed?.let { "${it.tUs}:${it.cx}:${it.cy}:${it.r}" }
    }
}

/** Lectura y escritura de [AnalysisCheckpoint]. E/S bloqueante: llamar desde `Dispatchers.IO`. */
internal object AnalysisCheckpoints {
    /** Stride máximo que admite la reanudación actual; el pipeline no produce uno mayor. */
    const val MAX_POSE_STRIDE = 2

    private const val MAGIC = 0x4B504350 // «KPCP»
    private const val MAX_HEADER_BYTES = 1 shl 16
    private const val TIME_SCALE_EPSILON = 1e-9

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * El contenido de `<id>.pose.part`: la marca y el formato, el encabezado (JSON), la traza de pose y la del disco (cada una con
     * su códec de sidecar, ya comprimida) y, al final, el CRC32 de todo lo anterior. Un archivo a medias o corrupto no lo cumple.
     */
    fun encode(checkpoint: AnalysisCheckpoint): ByteArray {
        val header = json.encodeToString(AnalysisCheckpoint.Header.serializer(), checkpoint.header).toByteArray(Charsets.UTF_8)
        val pose = PoseTrackCodec.encode(checkpoint.pose)
        val bar = checkpoint.bar?.let { BarTrackCodec.encode(it) } ?: ByteArray(0)
        val payload = ByteArrayOutputStream(header.size + pose.size + bar.size + 32)
        DataOutputStream(payload).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(AnalysisCheckpoint.FORMAT)
            out.writeInt(header.size)
            out.write(header)
            out.writeInt(pose.size)
            out.write(pose)
            out.writeInt(bar.size)
            out.write(bar)
        }
        val body = payload.toByteArray()
        val crc = CRC32().apply { update(body) }.value
        val framed = ByteArrayOutputStream(body.size + Long.SIZE_BYTES)
        DataOutputStream(framed).use { out ->
            out.write(body)
            out.writeLong(crc)
        }
        return framed.toByteArray()
    }

    /** El punto de control de [bytes], o `null` si no es uno válido (marca, formato, CRC o trazas que no cuadran). Nunca lanza. */
    fun decode(bytes: ByteArray): AnalysisCheckpoint? {
        if (bytes.size < MIN_BYTES) return null
        val bodySize = bytes.size - Long.SIZE_BYTES
        val stored = DataInputStream(ByteArrayInputStream(bytes, bodySize, Long.SIZE_BYTES)).use { it.readLong() }
        val actual = CRC32().apply { update(bytes, 0, bodySize) }.value
        if (stored != actual) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes, 0, bodySize)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != AnalysisCheckpoint.FORMAT) return null
                val headerSize = input.readInt()
                if (headerSize !in 1..MAX_HEADER_BYTES || headerSize > input.available()) return null
                val headerBytes = ByteArray(headerSize).also { input.readFully(it) }
                val header = json.decodeFromString(AnalysisCheckpoint.Header.serializer(), String(headerBytes, Charsets.UTF_8))
                val poseSize = input.readInt()
                if (poseSize !in 1..input.available()) return null
                val poseBytes = ByteArray(poseSize).also { input.readFully(it) }
                val barSize = input.readInt()
                if (barSize !in 0..input.available()) return null
                val barBytes = ByteArray(barSize).also { input.readFully(it) }
                val pose = PoseTrackCodec.decode(poseBytes) ?: return null
                val bar = if (barSize == 0) null else BarTrackCodec.decode(barBytes) ?: return null
                AnalysisCheckpoint(header, pose, bar)
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // SerializationException hereda de IllegalArgumentException: el encabezado no se entiende.
            null
        }
    }

    /** Escribe [checkpoint] en [file] de forma atómica: un corte a medias deja el punto de control anterior entero. */
    fun write(file: File, checkpoint: AnalysisCheckpoint) {
        TechniqueSidecars.writeAtomically(file, encode(checkpoint))
    }

    /** Lee [file]; `null` si falta o no es válido. */
    fun read(file: File): AnalysisCheckpoint? {
        if (!file.isFile) return null
        val bytes = try {
            file.readBytes()
        } catch (_: IOException) {
            return null
        }
        return decode(bytes)
    }

    /**
     * Si el punto de control [checkpoint] sirve para este análisis: el mismo vídeo (tamaño y fecha), la misma versión de pose, el mismo
     * disco (si lo usa, con la misma semilla confirmada), el mismo tamaño de fotograma y la misma escala de tiempo, y unas trazas
     * coherentes con el último PTS procesado. [realFps] es la cadencia real de ESTE vídeo: con el stride guardado debe mantener
     * al menos [minPoseFps] Hz de pose. Si no, se descarta y se empieza de cero.
     */
    fun isUsable(
        checkpoint: AnalysisCheckpoint,
        mediaId: String,
        videoBytes: Long,
        videoModifiedMs: Long,
        poseVersion: Int,
        usesPlate: Boolean,
        frameWidth: Int,
        frameHeight: Int,
        timeScale: Double,
        seedKey: String?,
        realFps: Double,
        minPoseFps: Double,
    ): Boolean {
        val h = checkpoint.header
        if (h.format != AnalysisCheckpoint.FORMAT || h.mediaId != mediaId || h.poseVersion != poseVersion) return false
        if (h.videoBytes != videoBytes || h.videoModifiedMs != videoModifiedMs) return false
        if (h.usesPlate != usesPlate || h.seedKey != seedKey) return false
        if (h.frameWidth != frameWidth || h.frameHeight != frameHeight) return false
        if (abs(h.timeScale - timeScale) > TIME_SCALE_EPSILON) return false
        if (h.poseStride !in 1..MAX_POSE_STRIDE || h.nextIndex <= 0 || h.lastPtsUs < 0L) return false
        if (!realFps.isFinite() || realFps <= 0.0 || realFps / h.poseStride < minPoseFps) return false
        if (h.plateAttempts < 0 || h.plateWaitingCounts.values.any { it < 0 }) return false
        val expectedStage = if (usesPlate) h.plateStage == AnalysisCheckpoint.STAGE_TRACKING ||
            h.plateStage == AnalysisCheckpoint.STAGE_GAVE_UP ||
            h.plateStage == AnalysisCheckpoint.STAGE_PROPOSED else h.plateStage == AnalysisCheckpoint.STAGE_NONE
        if (!expectedStage) return false
        val pose = checkpoint.pose
        if (pose.mediaId != mediaId || pose.frameWidth != frameWidth || pose.frameHeight != frameHeight || pose.poseStride != h.poseStride) return false
        if (pose.frames.isEmpty()) return false
        if (usesPlate && h.plateStage == AnalysisCheckpoint.STAGE_TRACKING && checkpoint.bar?.samples.isNullOrEmpty()) return false
        if ((checkpoint.bar != null) != usesPlate) return false
        val bar = checkpoint.bar
        val automaticSeed = bar?.seed?.takeIf { !it.userConfirmed }
        if (usesPlate && h.seedKey == null && automaticSeed != null &&
            (bar == null || bar.mediaId != mediaId || bar.frameWidth != frameWidth || bar.frameHeight != frameHeight ||
                !validProposalSeed(automaticSeed, h))) return false
        if (h.plateStage == AnalysisCheckpoint.STAGE_PROPOSED) {
            if (bar == null || automaticSeed == null || h.seedKey != null || bar.samples.isNotEmpty() ||
                bar.plateDiameterMm != null || bar.trackingContinuity != PlateTrackingContinuity.UNKNOWN ||
                bar.continuityBreakUs != null || !validProposalSeed(automaticSeed, h)) return false
        }
        return true
    }

    private fun validProposalSeed(seed: PlateSeed, header: AnalysisCheckpoint.Header): Boolean =
        seed.tUs >= 0L && seed.cx.isFinite() && seed.cy.isFinite() && seed.r.isFinite() && seed.r > 0f &&
            header.timeScale.isFinite() && header.timeScale > 0.0 &&
            seed.tUs <= Math.round(header.lastPtsUs * header.timeScale)

    // Marca, formato, tres longitudes y el CRC: lo mínimo que puede medir un archivo con algo dentro.
    private const val MIN_BYTES = 4 + 4 + 4 + 1 + 4 + 1 + 4 + Long.SIZE_BYTES
}
