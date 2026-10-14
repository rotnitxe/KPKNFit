package com.example.kpkn.domain.biomechanics.pose

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Códec del sidecar `<id>.pose2.json.gz` (§3.8): JSON columnar cuantizado y comprimido con gzip.
 *
 * Cada canal es un array plano de enteros, fotograma a fotograma y punto a punto (índice = fotograma·K + columna):
 * - `x`, `y` = round(v·10⁴) con clamp a [−5000, 15000]; `z` = round(z·10³); `vis`, `pres` = round(p·100);
 * - `wx`, `wy`, `wz` en milímetros;
 * - `n` = poses detectadas por fotograma (0 = sin atleta, y entonces todo el fotograma va a 0);
 * - `t0` (µs) y `dt` (µs entre fotogramas consecutivos, frames − 1) llevan el tiempo.
 *
 * Un punto que falta dentro de un fotograma con pose se escribe con todos sus canales a 0 y vuelve como `null`
 * (un punto real con todo a 0 no aporta información). Un fotograma con pose y `posesDetected` = 0 vuelve con 1.
 */
object PoseTrackCodec {
    private const val FORMAT_VERSION = 2

    /** Tope de JSON descomprimido (un vídeo de 180 s a 60 fps ronda los 10 MB). Protege de ficheros corruptos. */
    private const val MAX_JSON_BYTES = 256 * 1024 * 1024

    private const val XY_MIN = -5000
    private const val XY_MAX = 15000
    private const val Z_LIMIT = 32767
    private const val WORLD_LIMIT_MM = 32767

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    fun encode(track: PoseTrackV2): ByteArray {
        val frames = track.frames
        val count = frames.size
        val ki = PoseLandmark.KEPT_IMAGE.size
        val kw = PoseLandmark.KEPT_WORLD.size
        val n = IntArray(count)
        val x = IntArray(count * ki)
        val y = IntArray(count * ki)
        val z = IntArray(count * ki)
        val vis = IntArray(count * ki)
        val pres = IntArray(count * ki)
        val wx = IntArray(count * kw)
        val wy = IntArray(count * kw)
        val wz = IntArray(count * kw)
        val dt = LongArray(if (count > 1) count - 1 else 0)
        var t0 = 0L

        for (i in 0 until count) {
            val frame = frames[i]
            if (i == 0) t0 = frame.tUs else dt[i - 1] = frame.tUs - frames[i - 1].tUs
            val img = frame.imageData() ?: continue
            n[i] = maxOf(1, frame.posesDetected)
            for (s in 0 until ki) {
                val o = s * PoseFrameV2.IMAGE_STRIDE
                if (img[o].isNaN()) continue
                val idx = i * ki + s
                x[idx] = quantize(img[o], 1e4, XY_MIN, XY_MAX)
                y[idx] = quantize(img[o + 1], 1e4, XY_MIN, XY_MAX)
                z[idx] = quantize(img[o + 2], 1e3, -Z_LIMIT, Z_LIMIT)
                vis[idx] = quantize(img[o + 3], 100.0, 0, 100)
                pres[idx] = quantize(img[o + 4], 100.0, 0, 100)
            }
            val wld = frame.worldData() ?: continue
            for (s in 0 until kw) {
                val o = s * PoseFrameV2.WORLD_STRIDE
                if (wld[o].isNaN()) continue
                val idx = i * kw + s
                wx[idx] = quantize(wld[o], 1e3, -WORLD_LIMIT_MM, WORLD_LIMIT_MM)
                wy[idx] = quantize(wld[o + 1], 1e3, -WORLD_LIMIT_MM, WORLD_LIMIT_MM)
                wz[idx] = quantize(wld[o + 2], 1e3, -WORLD_LIMIT_MM, WORLD_LIMIT_MM)
            }
        }

        val dto = PoseTrackDto(
            v = FORMAT_VERSION,
            mediaId = track.mediaId,
            engine = EngineDto(track.engine.id, track.engine.model, track.engine.delegate),
            w = track.frameWidth,
            h = track.frameHeight,
            rot = track.rotationApplied,
            fps = track.nominalFps,
            stride = track.poseStride,
            vfr = track.vfr,
            captureFps = track.captureFps,
            fxPx = track.focalLengthPx,
            roll = track.gravityRollDeg,
            pitch = track.gravityPitchDeg,
            aspectKnown = track.aspectKnown,
            img = IntArray(ki) { PoseLandmark.KEPT_IMAGE[it].mp },
            wld = IntArray(kw) { PoseLandmark.KEPT_WORLD[it].mp },
            t0 = t0,
            dt = dt,
            n = n,
            x = x, y = y, z = z, vis = vis, pres = pres,
            wx = wx, wy = wy, wz = wz,
        )
        val text = json.encodeToString(PoseTrackDto.serializer(), dto)
        val out = ByteArrayOutputStream(maxOf(1024, text.length / 8))
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** Decodifica un `pose2.json.gz`; `null` si no es gzip válido, no es JSON, `v` ≠ 2 o las columnas no cuadran. */
    fun decode(bytes: ByteArray): PoseTrackV2? {
        return try {
            val raw = gunzip(bytes) ?: return null
            val dto = json.decodeFromString(PoseTrackDto.serializer(), String(raw, Charsets.UTF_8))
            if (dto.v != FORMAT_VERSION) return null
            toTrack(dto)
        } catch (e: Exception) {
            // Gzip corrupto (IOException), JSON mal formado o de otra forma (SerializationException) o columnas
            // incoherentes (IllegalArgumentException / IndexOutOfBounds): en todos los casos «no es un v2 válido».
            null
        }
    }

    private fun toTrack(dto: PoseTrackDto): PoseTrackV2? {
        val count = dto.n.size
        val ci = dto.img.size
        val cw = dto.wld.size
        if (dto.dt.size != maxOf(count - 1, 0)) return null
        val imageChannels = listOf(dto.x, dto.y, dto.z, dto.vis, dto.pres)
        if (imageChannels.any { it.size != count * ci }) return null
        val worldChannels = listOf(dto.wx, dto.wy, dto.wz)
        if (worldChannels.any { it.size != count * cw }) return null

        // Columna de la cabecera → ranura de este formato (−1 si el punto no se guarda aquí).
        val imageSlots = IntArray(ci) { c -> PoseLandmark.fromMp(dto.img[c])?.let { PoseLandmark.imageSlot(it) } ?: -1 }
        val worldSlots = IntArray(cw) { c -> PoseLandmark.fromMp(dto.wld[c])?.let { PoseLandmark.worldSlot(it) } ?: -1 }
        val si = PoseLandmark.KEPT_IMAGE.size * PoseFrameV2.IMAGE_STRIDE
        val sw = PoseLandmark.KEPT_WORLD.size * PoseFrameV2.WORLD_STRIDE

        val frames = ArrayList<PoseFrameV2>(count)
        var t = dto.t0
        for (i in 0 until count) {
            if (i > 0) t += dto.dt[i - 1]
            val poses = dto.n[i]
            if (poses <= 0) {
                frames += PoseFrameV2.fromArrays(t, 0, null, null)
                continue
            }
            val img = FloatArray(si) { Float.NaN }
            for (c in 0 until ci) {
                val slot = imageSlots[c]
                if (slot < 0) continue
                val idx = i * ci + c
                val xi = dto.x[idx]
                val yi = dto.y[idx]
                val zi = dto.z[idx]
                val vi = dto.vis[idx]
                val pi = dto.pres[idx]
                if (xi == 0 && yi == 0 && zi == 0 && vi == 0 && pi == 0) continue
                val o = slot * PoseFrameV2.IMAGE_STRIDE
                img[o] = xi / 10_000f
                img[o + 1] = yi / 10_000f
                img[o + 2] = zi / 1_000f
                img[o + 3] = vi / 100f
                img[o + 4] = pi / 100f
            }
            var wld: FloatArray? = null
            for (c in 0 until cw) {
                val slot = worldSlots[c]
                if (slot < 0) continue
                val idx = i * cw + c
                val xi = dto.wx[idx]
                val yi = dto.wy[idx]
                val zi = dto.wz[idx]
                if (xi == 0 && yi == 0 && zi == 0) continue
                val target = wld ?: FloatArray(sw) { Float.NaN }.also { wld = it }
                val o = slot * PoseFrameV2.WORLD_STRIDE
                target[o] = xi / 1_000f
                target[o + 1] = yi / 1_000f
                target[o + 2] = zi / 1_000f
            }
            frames += PoseFrameV2.fromArrays(t, poses, img, wld)
        }
        return PoseTrackV2(
            mediaId = dto.mediaId,
            engine = PoseEngineInfo(dto.engine.id, dto.engine.model, dto.engine.delegate),
            frameWidth = dto.w,
            frameHeight = dto.h,
            rotationApplied = dto.rot,
            nominalFps = dto.fps,
            poseStride = dto.stride,
            vfr = dto.vfr,
            captureFps = dto.captureFps,
            focalLengthPx = dto.fxPx,
            gravityRollDeg = dto.roll,
            gravityPitchDeg = dto.pitch,
            aspectKnown = dto.aspectKnown,
            frames = frames,
            version = FORMAT_VERSION,
        )
    }

    /** round(v·scale) con clamp a [lo, hi]; NaN → 0. */
    private fun quantize(v: Float, scale: Double, lo: Int, hi: Int): Int {
        if (v.isNaN()) return 0
        val d = v.toDouble() * scale
        return when {
            d >= hi -> hi
            d <= lo -> lo
            else -> Math.round(d).toInt().coerceIn(lo, hi)
        }
    }

    /** Descomprime gzip con tope de tamaño; `null` si excede el tope. Lanza IOException si no es gzip válido. */
    private fun gunzip(bytes: ByteArray): ByteArray? {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
            val out = ByteArrayOutputStream(maxOf(1024, minOf(bytes.size.toLong() * 8L, 1L shl 24).toInt()))
            val buf = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val r = gz.read(buf)
                if (r < 0) break
                total += r
                if (total > MAX_JSON_BYTES) return null
                out.write(buf, 0, r)
            }
            return out.toByteArray()
        }
    }

    @Serializable
    private class EngineDto(val id: String, val model: String, val delegate: String)

    /** Columnas del sidecar; los nombres de campo son los del formato (§3.8). */
    @Serializable
    private class PoseTrackDto(
        val v: Int,
        val mediaId: String,
        val engine: EngineDto,
        val w: Int,
        val h: Int,
        val rot: Int,
        val fps: Float,
        val stride: Int,
        val vfr: Boolean,
        val captureFps: Float? = null,
        val fxPx: Float? = null,
        val roll: Float? = null,
        val pitch: Float? = null,
        val aspectKnown: Boolean = true,
        val img: IntArray,
        val wld: IntArray = IntArray(0),
        val t0: Long,
        val dt: LongArray,
        val n: IntArray,
        val x: IntArray,
        val y: IntArray,
        val z: IntArray,
        val vis: IntArray,
        val pres: IntArray,
        val wx: IntArray = IntArray(0),
        val wy: IntArray = IntArray(0),
        val wz: IntArray = IntArray(0),
    )
}
