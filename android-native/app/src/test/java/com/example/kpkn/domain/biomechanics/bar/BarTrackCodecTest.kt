package com.example.kpkn.domain.biomechanics.bar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs

class BarTrackCodecTest {
    @Test
    fun anUntrackedProposalRoundTripsItsSeedAndPtsWithoutManufacturingSamples() {
        val seed = PlateSeed(1_234_567L, 123.25f, 321.5f, 36.25f, false)
        val input = BarTrack("proposal", 406, 720, null, seed, emptyList(),
            trackingContinuity = PlateTrackingContinuity.UNKNOWN)
        val decoded = checkNotNull(BarTrackCodec.decode(BarTrackCodec.encode(input)))
        assertEquals(input, decoded)
        assertTrue(decoded.samples.isEmpty())
        org.junit.Assert.assertFalse(PlatePublicationPolicy.canUsePlate(decoded))
    }


    @Test
    fun continuityRoundTripAndLegacyDecodeFailClosedWithoutChangingHistoricalConfirmation() {
        val selected = syntheticBar(count = 30, diameterMm = 450, withSeed = true)
        for (state in PlateTrackingContinuity.entries) {
            val input = selected.copy(trackingContinuity = state,
                continuityBreakUs = if (state == PlateTrackingContinuity.INTERRUPTED) 2_500_000L else null)
            val decoded = checkNotNull(BarTrackCodec.decode(BarTrackCodec.encode(input)))
            assertEquals(state, decoded.trackingContinuity)
            assertEquals(input.continuityBreakUs, decoded.continuityBreakUs)
            assertTrue(checkNotNull(decoded.seed).userConfirmed)
            assertEquals(2, decoded.version)
        }
        val v2 = gunzip(BarTrackCodec.encode(selected.copy(trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED)))
        // A real old sidecar lacks these fields; even a stray future field in v1 cannot upgrade legacy evidence.
        val v1 = v2.replaceFirst("\"v\":2", "\"v\":1")
            .replace(",\"trackingContinuity\":\"UNINTERRUPTED\"", "").replace(",\"continuityBreakUs\":null", "")
        val legacy = checkNotNull(BarTrackCodec.decode(gzip(v1)))
        assertEquals(1, legacy.version)
        assertEquals(PlateTrackingContinuity.UNKNOWN, legacy.trackingContinuity)
        assertTrue(checkNotNull(legacy.seed).userConfirmed)
        org.junit.Assert.assertFalse(PlatePublicationPolicy.canUsePlate(legacy))
        val malformed = v2.replace("UNINTERRUPTED", "FUTURE_UNKNOWN_STATE")
        assertEquals(PlateTrackingContinuity.UNKNOWN, checkNotNull(BarTrackCodec.decode(gzip(malformed))).trackingContinuity)
    }

    @Test
    fun roundTripKeepsHeaderSeedAndQuantizedSamples() {
        val track = syntheticBar(count = 250, diameterMm = 450, withSeed = true)
        val decoded = BarTrackCodec.decode(BarTrackCodec.encode(track))
        assertNotNull(decoded)
        decoded!!

        assertEquals(track.mediaId, decoded.mediaId)
        assertEquals(track.frameWidth, decoded.frameWidth)
        assertEquals(track.frameHeight, decoded.frameHeight)
        assertEquals(450, decoded.plateDiameterMm)
        assertEquals(track.seed, decoded.seed)
        assertEquals(2, decoded.version)
        assertEquals(track.samples.size, decoded.samples.size)

        val sources = HashSet<BarSampleSource>()
        for (i in track.samples.indices) {
            val a = track.samples[i]
            val b = decoded.samples[i]
            sources += b.source
            assertEquals("t[$i]", a.tUs, b.tUs)
            assertEquals("cx[$i]", a.cx, b.cx, 0f) // los centros se guardan sin cuantizar
            assertEquals("cy[$i]", a.cy, b.cy, 0f)
            assertTrue("r[$i]", abs(a.r - b.r) <= 0.05f + 1e-4f) // r ×10 → 0,1 px
            assertTrue("ax[$i]", abs(a.axisRatio - b.axisRatio) <= 0.0005f + 1e-6f) // ax ×1000
            assertTrue("q[$i]", abs(a.quality - b.quality) <= 0.005f + 1e-6f) // q ×100
            assertEquals("src[$i]", a.source, b.source)
        }
        assertEquals("se ejercitan las cuatro fuentes", BarSampleSource.entries.toSet(), sources)
    }

    @Test
    fun uncalibratedTrackWithoutSeedRoundTrips() {
        val track = syntheticBar(count = 40, diameterMm = null, withSeed = false)
        val decoded = BarTrackCodec.decode(BarTrackCodec.encode(track))!!
        assertNull(decoded.plateDiameterMm)
        assertNull(decoded.seed)
        assertEquals(40, decoded.samples.size)
    }

    @Test
    fun emptyTrackRoundTrips() {
        val track = BarTrack("vacío", 720, 1280, null, null, emptyList())
        assertEquals(track, BarTrackCodec.decode(BarTrackCodec.encode(track)))
    }

    @Test
    fun valuesOutOfRangeAreClampedNotWrapped() {
        val track = BarTrack(
            "clamp", 100, 100, 450, null,
            listOf(BarSample(0L, 1f, 2f, -3f, -1f, 5f, BarSampleSource.PREDICTED)),
        )
        val s = BarTrackCodec.decode(BarTrackCodec.encode(track))!!.samples.single()
        assertEquals(0f, s.r, 0f)
        assertEquals(0f, s.axisRatio, 0f)
        assertEquals(1f, s.quality, 1e-6f)
    }

    @Test
    fun encodingIsDeterministic() {
        val track = syntheticBar(count = 100, diameterMm = 450, withSeed = true)
        assertArrayEquals(BarTrackCodec.encode(track), BarTrackCodec.encode(track))
    }

    @Test
    fun garbageAndWrongVersionDecodeToNull() {
        assertNull(BarTrackCodec.decode(ByteArray(0)))
        assertNull(BarTrackCodec.decode("no es gzip".toByteArray()))
        assertNull(BarTrackCodec.decode(ByteArray(300).also { Random(3).nextBytes(it) }))
        assertNull(BarTrackCodec.decode(gzip("no es JSON")))
        assertNull(BarTrackCodec.decode(gzip("{}")))
        val json = gunzip(BarTrackCodec.encode(syntheticBar(count = 10, diameterMm = 450, withSeed = true)))
        assertTrue(json.startsWith("{\"v\":2,"))
        assertNotNull(BarTrackCodec.decode(gzip(json)))
        assertNull("v = 3", BarTrackCodec.decode(gzip(json.replaceFirst("\"v\":2", "\"v\":3"))))
        assertNull("v = 0", BarTrackCodec.decode(gzip(json.replaceFirst("\"v\":2", "\"v\":0"))))
    }

    @Test
    fun inconsistentColumnsOrUnknownSourceDecodeToNull() {
        val json = gunzip(BarTrackCodec.encode(syntheticBar(count = 10, diameterMm = 450, withSeed = false)))
        // Una columna con un valor de más.
        assertNull(BarTrackCodec.decode(gzip(json.replaceFirst("\"q\":[", "\"q\":[1,"))))
        // Una muestra más en TODAS las columnas es coherente, salvo que su fuente esté fuera del enum.
        assertNotNull(BarTrackCodec.decode(gzip(prependSample(json, src = 3))))
        assertNull(BarTrackCodec.decode(gzip(prependSample(json, src = 99))))
    }

    /** Antepone una muestra a cada columna del JSON de un sidecar. */
    private fun prependSample(json: String, src: Int): String = json
        .replaceFirst("\"t\":[", "\"t\":[7,")
        .replaceFirst("\"cx\":[", "\"cx\":[1.0,")
        .replaceFirst("\"cy\":[", "\"cy\":[1.0,")
        .replaceFirst("\"r\":[", "\"r\":[1,")
        .replaceFirst("\"ax\":[", "\"ax\":[1,")
        .replaceFirst("\"q\":[", "\"q\":[1,")
        .replaceFirst("\"src\":[", "\"src\":[$src,")

    @Test
    fun sourceIsStoredAsEnumIndex() {
        val json = gunzip(
            BarTrackCodec.encode(
                BarTrack(
                    "src", 10, 10, null, null,
                    BarSampleSource.entries.mapIndexed { i, s -> BarSample(i * 1000L, 5f, 6f, 40f, 1f, 0.9f, s) },
                ),
            ),
        )
        assertTrue(json, json.contains("\"src\":[0,1,2,3]"))
        assertTrue(json, json.contains("\"r\":[400,400,400,400]"))
        assertTrue(json, json.contains("\"ax\":[1000,1000,1000,1000]"))
        assertTrue(json, json.contains("\"q\":[90,90,90,90]"))
    }

    // ---- utilidades ----

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { String(it.readBytes(), Charsets.UTF_8) }

    private fun syntheticBar(count: Int, diameterMm: Int?, withSeed: Boolean): BarTrack {
        val rnd = Random(99)
        val sources = BarSampleSource.entries
        val samples = (0 until count).map { i ->
            BarSample(
                tUs = 2_000_000L + i * 33_333L,
                cx = 360f + 20f * Math.sin(i * 0.2).toFloat() + rnd.nextFloat() * 0.3f,
                cy = 700f - 250f * (0.5f + 0.5f * Math.sin(i * 0.15).toFloat()),
                r = 95f + rnd.nextFloat() * 2f,
                axisRatio = 0.9f + rnd.nextFloat() * 0.1f,
                quality = rnd.nextFloat(),
                source = sources[i % sources.size],
            )
        }
        return BarTrack(
            mediaId = "bar-1",
            frameWidth = 720,
            frameHeight = 1280,
            plateDiameterMm = diameterMm,
            seed = if (withSeed) PlateSeed(2_000_000L, 360f, 700f, 95f, userConfirmed = true) else null,
            samples = samples,
        )
    }
}
