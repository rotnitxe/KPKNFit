package com.example.kpkn.data.media.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** El respaldo de GL con fuentes de mentira: nada de Android, solo la lógica de reanudación. */
class FallbackVideoFrameSourceTest {
    private val info = VideoInfo(320, 240, 0, 320, 240, 3_000_000L, "video/avc", 30f, null, false)
    private val pts = List(10) { it * 33_333L }

    /**
     * Entrega los [times] que sean ≥ `fromUs`, numerados desde `startIndex`. Con [failAfter] ≥ 0, tras entregar esos fotogramas
     * lanza [failure] (un `VideoDecodeException` lleva hasta dónde llegó, como el de GL).
     */
    private class FakeSource(
        override val info: VideoInfo,
        private val times: List<Long>,
        private val failAfter: Int = -1,
        private val failure: Throwable? = null,
    ) : ResumableVideoFrameSource {
        val calls = ArrayList<String>()
        var closed = false

        override suspend fun decode(
            fromUs: Long,
            targetLongEdge: Int,
            needs: (index: Int, ptsUs: Long) -> FrameNeeds,
            onFrame: suspend (DecodedFrame) -> Unit,
        ) = decodeFrom(fromUs, targetLongEdge, needs, onFrame, 0)

        override suspend fun decodeFrom(
            fromUs: Long,
            targetLongEdge: Int,
            needs: (index: Int, ptsUs: Long) -> FrameNeeds,
            onFrame: suspend (DecodedFrame) -> Unit,
            startIndex: Int,
        ) {
            calls.add("from=$fromUs start=$startIndex")
            var index = startIndex
            var delivered = 0
            var lastConsumed = Long.MIN_VALUE
            for (t in times) {
                if (t < fromUs) continue
                if (failAfter >= 0 && delivered == failAfter) {
                    val error = checkNotNull(failure)
                    if (error is VideoDecodeException) {
                        error.consumedPtsUs = lastConsumed
                        error.nextIndex = index
                    }
                    throw error
                }
                val need = needs(index, t)
                if (need != FrameNeeds.SKIP) onFrame(DecodedFrame(index, t, 2, 2, null, null))
                lastConsumed = t
                index++
                delivered++
            }
        }

        override fun close() {
            closed = true
        }
    }

    private class Run(val source: FallbackVideoFrameSource, val backups: List<FakeSource>) {
        val seen = ArrayList<Pair<Int, Long>>()
        suspend fun decode(fromUs: Long = 0L, onFrame: suspend (DecodedFrame) -> Unit = { seen.add(it.index to it.ptsUs) }) {
            source.decode(fromUs, 720, { _, _ -> FrameNeeds.LUMA }, onFrame)
        }
    }

    private fun build(primary: FakeSource, backupTimes: List<Long> = pts): Run {
        val backups = ArrayList<FakeSource>()
        val source = FallbackVideoFrameSource(primary, info) { _ -> FakeSource(info, backupTimes).also { backups.add(it) } }
        return Run(source, backups)
    }

    @Test
    fun aPrimaryThatSucceedsNeverCreatesTheBackup() = runBlocking {
        val run = build(FakeSource(info, pts))
        run.decode()
        assertEquals(10, run.seen.size)
        assertTrue(run.backups.isEmpty())
        assertEquals(VideoDecodePath.GL, run.source.path)
        assertNull(run.source.fallbackReason)
    }

    @Test
    fun aFailureMidwayContinuesWithTheBackupFromTheNextPtsAndIndex() = runBlocking {
        val failure = VideoDecodeException("el códec murió")
        val run = build(FakeSource(info, pts, failAfter = 4, failure = failure))
        run.decode()
        // Ni un fotograma repetido ni uno perdido, con la numeración seguida.
        assertEquals(List(10) { it to pts[it] }, run.seen)
        assertEquals(1, run.backups.size)
        assertEquals(listOf("from=${pts[3] + 1} start=4"), run.backups.single().calls)
        assertEquals(VideoDecodePath.RETRIEVER, run.source.path)
        assertEquals("el códec murió", run.source.fallbackReason)
    }

    @Test
    fun aFailureBeforeAnyFrameRestartsFromTheRequestedInstant() = runBlocking {
        val run = build(FakeSource(info, pts, failAfter = 0, failure = VideoDecodeException("sin salida")))
        run.decode(fromUs = pts[2])
        assertEquals(List(8) { it to pts[it + 2] }, run.seen)
        assertEquals(listOf("from=${pts[2]} start=0"), run.backups.single().calls)
    }

    @Test
    fun theResumeInstantNeverGoesBeforeTheRequestedOne() = runBlocking {
        val failure = VideoDecodeException("tarde")
        failure.consumedPtsUs = 10L
        val failing = object : ResumableVideoFrameSource by FakeSource(info, pts) {
            override suspend fun decode(
                fromUs: Long,
                targetLongEdge: Int,
                needs: (index: Int, ptsUs: Long) -> FrameNeeds,
                onFrame: suspend (DecodedFrame) -> Unit,
            ): Unit = throw failure
        }
        val backups = ArrayList<FakeSource>()
        val source = FallbackVideoFrameSource(failing, info) { _ -> FakeSource(info, pts).also { backups.add(it) } }
        source.decode(fromUs = 100_000L, targetLongEdge = 720, needs = { _, _ -> FrameNeeds.SKIP }, onFrame = {})
        assertEquals(listOf("from=100000 start=0"), backups.single().calls)
    }

    @Test
    fun consumerFailuresPropagateAndDoNotActivateTheBackup() = runBlocking {
        val run = build(FakeSource(info, pts))
        try {
            run.decode { frame -> if (frame.index == 2) throw IllegalStateException("la pose falló") }
            fail("debía propagar la excepción del consumidor")
        } catch (expected: IllegalStateException) {
            assertEquals("la pose falló", expected.message)
        }
        assertTrue(run.backups.isEmpty())
        assertEquals(VideoDecodePath.GL, run.source.path)
    }

    @Test
    fun cancellationPropagatesUntouched() = runBlocking {
        val cancellation = CancellationException("cancelado")
        val run = build(FakeSource(info, pts, failAfter = 3, failure = cancellation))
        try {
            run.decode()
            fail("debía propagar la cancelación")
        } catch (expected: CancellationException) {
            assertSame(cancellation, expected)
        }
        assertTrue(run.backups.isEmpty())
        assertEquals(VideoDecodePath.GL, run.source.path)
    }

    @Test
    fun laterCallsGoStraightToTheBackupAndKeepTheReason() = runBlocking {
        val run = build(FakeSource(info, pts, failAfter = 2, failure = VideoDecodeException("primera")))
        run.decode()
        run.seen.clear()
        run.decode()
        assertEquals(List(10) { it to pts[it] }, run.seen)
        assertEquals(1, run.backups.size)
        assertEquals(listOf("from=${pts[1] + 1} start=2", "from=0 start=0"), run.backups.single().calls)
        assertEquals("primera", run.source.fallbackReason)
    }

    @Test
    fun closeClosesBothSources() = runBlocking {
        val primary = FakeSource(info, pts, failAfter = 1, failure = VideoDecodeException("x"))
        val run = build(primary)
        run.decode()
        run.source.close()
        assertTrue(primary.closed)
        assertTrue(run.backups.single().closed)
        assertFalse(run.backups.isEmpty())
    }
}
