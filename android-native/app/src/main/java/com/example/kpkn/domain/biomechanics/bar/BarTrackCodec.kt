package com.example.kpkn.domain.biomechanics.bar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Códec del sidecar `<id>.bar.json.gz` (§3.8): JSON columnar y gzip.
 *
 * Columnas, una entrada por muestra: `t` (µs), `cx`, `cy` (píxeles, exactos), `r` ×10, `ax` ×1000 (eje menor/mayor),
 * `q` ×100 y `src` (índice de [BarSampleSource]). Van aparte la cabecera, el diámetro del disco y la semilla.
 * La cuantización de `r`, `ax` y `q` es la propia de este formato: 0,1 px, 0,001 y 0,01.
 */
object BarTrackCodec {
    private const val FORMAT_VERSION = 2

    /** Tope de JSON descomprimido (una hora de vídeo a 60 fps ocuparía ~35 MB). Protege de ficheros corruptos. */
    private const val MAX_JSON_BYTES = 128 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    fun encode(t: BarTrack): ByteArray {
        val samples = t.samples
        val n = samples.size
        val time = LongArray(n)
        val cx = FloatArray(n)
        val cy = FloatArray(n)
        val r = IntArray(n)
        val ax = IntArray(n)
        val q = IntArray(n)
        val src = IntArray(n)
        for (i in 0 until n) {
            val s = samples[i]
            time[i] = s.tUs
            cx[i] = s.cx
            cy[i] = s.cy
            r[i] = quantize(s.r, 10.0, 0, Int.MAX_VALUE / 4)
            ax[i] = quantize(s.axisRatio, 1000.0, 0, Int.MAX_VALUE / 4)
            q[i] = quantize(s.quality, 100.0, 0, 100)
            src[i] = s.source.ordinal
        }
        val dto = BarTrackDto(
            v = FORMAT_VERSION,
            mediaId = t.mediaId,
            w = t.frameWidth,
            h = t.frameHeight,
            plateDiameterMm = t.plateDiameterMm,
            seed = t.seed?.let { SeedDto(it.tUs, it.cx, it.cy, it.r, it.userConfirmed) },
            t = time, cx = cx, cy = cy, r = r, ax = ax, q = q, src = src,
            trackingContinuity = t.trackingContinuity.name,
            continuityBreakUs = t.continuityBreakUs,
        )
        val text = json.encodeToString(BarTrackDto.serializer(), dto)
        val out = ByteArrayOutputStream(maxOf(256, text.length / 6))
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** Decodifica un `bar.json.gz`; `null` si no es gzip válido, no es JSON, `v` no soportada o las columnas no cuadran. V1 conserva datos con continuidad UNKNOWN. */
    fun decode(b: ByteArray): BarTrack? {
        return try {
            val raw = gunzip(b) ?: return null
            val dto = json.decodeFromString(BarTrackDto.serializer(), String(raw, Charsets.UTF_8))
            if (dto.v != 1 && dto.v != FORMAT_VERSION) return null
            toTrack(dto)
        } catch (e: Exception) {
            // Gzip corrupto, JSON mal formado o de otra forma, o columnas incoherentes: «no es un bar válido».
            null
        }
    }

    private fun toTrack(dto: BarTrackDto): BarTrack? {
        val n = dto.t.size
        if (dto.cx.size != n || dto.cy.size != n || dto.r.size != n || dto.ax.size != n ||
            dto.q.size != n || dto.src.size != n
        ) {
            return null
        }
        val sources = BarSampleSource.entries
        val samples = ArrayList<BarSample>(n)
        for (i in 0 until n) {
            val source = sources.getOrNull(dto.src[i]) ?: return null
            samples += BarSample(
                tUs = dto.t[i],
                cx = dto.cx[i],
                cy = dto.cy[i],
                r = dto.r[i] / 10f,
                axisRatio = dto.ax[i] / 1000f,
                quality = dto.q[i] / 100f,
                source = source,
            )
        }
        var continuity = if (dto.v == 1) PlateTrackingContinuity.UNKNOWN else
            PlateTrackingContinuity.entries.firstOrNull { it.name == dto.trackingContinuity } ?: PlateTrackingContinuity.UNKNOWN
        if (continuity == PlateTrackingContinuity.UNINTERRUPTED && dto.continuityBreakUs != null) continuity = PlateTrackingContinuity.UNKNOWN
        return BarTrack(
            mediaId = dto.mediaId,
            frameWidth = dto.w,
            frameHeight = dto.h,
            plateDiameterMm = dto.plateDiameterMm,
            seed = dto.seed?.let { PlateSeed(it.t, it.cx, it.cy, it.r, it.userConfirmed) },
            samples = samples,
            version = dto.v,
            trackingContinuity = continuity,
            continuityBreakUs = if (dto.v == 1) null else dto.continuityBreakUs,
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

    /** Descomprime gzip con tope de tamaño; `null` si lo excede. Lanza IOException si no es gzip válido. */
    private fun gunzip(bytes: ByteArray): ByteArray? {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
            val out = ByteArrayOutputStream(maxOf(256, minOf(bytes.size.toLong() * 8L, 1L shl 22).toInt()))
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = gz.read(buf)
                if (read < 0) break
                total += read
                if (total > MAX_JSON_BYTES) return null
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }
    }

    @Serializable
    private class SeedDto(val t: Long, val cx: Float, val cy: Float, val r: Float, val userConfirmed: Boolean)

    /** Columnas del sidecar; los nombres de campo son los del formato (§3.8). */
    @Serializable
    private class BarTrackDto(
        val v: Int,
        val mediaId: String,
        val w: Int,
        val h: Int,
        val plateDiameterMm: Int? = null,
        val seed: SeedDto? = null,
        val t: LongArray,
        val cx: FloatArray,
        val cy: FloatArray,
        val r: IntArray,
        val ax: IntArray,
        val q: IntArray,
        val src: IntArray,
        val trackingContinuity: String? = null,
        val continuityBreakUs: Long? = null,
    )
}
