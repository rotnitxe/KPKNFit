package com.example.kpkn.data.media.video

import org.junit.Assert.*
import org.junit.Test

class IndexedPresentationTimesTest {
    private fun verified(times: LongArray): IndexedPresentationTimes =
        (IndexedPresentationTimes.verify(times, times.size, 1, mp4Compatible = true) as IndexedPtsValidation.Verified).times

    @Test fun vfrKeepsTheContainerClockAndResumesAtTheNextPhysicalPresentation() {
        val pts = longArrayOf(0, 25_000, 66_000, 91_000, 132_000, 157_000)
        val table = verified(pts)
        assertArrayEquals(pts, LongArray(table.size) { table.ptsUs(it) })
        assertEquals(0, table.firstIndexAtOrAfter(0))
        assertEquals(1, table.firstIndexAtOrAfter(25_000))
        assertEquals(2, table.firstIndexAtOrAfter(25_001))
        assertEquals(5, table.firstIndexAtOrAfter(150_000))
        assertEquals(6, table.firstIndexAtOrAfter(157_001))
        // Resume after a consumed frame must neither repeat it nor pick an fps-derived index.
        val resume = table.firstIndexAtOrAfter(table.ptsUs(2) + 1)
        assertEquals(3, resume)
        assertEquals(91_000L, table.ptsUs(resume))
    }

    @Test fun cfrDoesNotAcquireRoundingErrorAndNonzeroPtsAreNotRebased() {
        val cfr = longArrayOf(0, 33_333, 66_666, 100_000, 133_333, 166_666)
        assertArrayEquals(cfr, LongArray(cfr.size) { verified(cfr).ptsUs(it) })
        val shifted = cfr.map { it + 12_345_000L }.toLongArray()
        val table = verified(shifted)
        assertEquals(12_411_666L, table.ptsUs(2))
        assertEquals(0, table.firstIndexAtOrAfter(0L))
        // Mutation of the extraction scratch array must not change a validated clock.
        shifted[2] = Long.MAX_VALUE
        assertEquals(12_411_666L, table.ptsUs(2))
    }

    @Test fun mp4BFramesUseExplicitPresentationOrderWhileUnknownContainersAreRejected() {
        val bFrames = longArrayOf(0, 66_666, 33_333, 100_000)
        val result = IndexedPresentationTimes.verify(bFrames, 4, 1, mp4Compatible = true)
        val table = (result as IndexedPtsValidation.Verified).times
        assertArrayEquals(longArrayOf(0, 33_333, 66_666, 100_000), LongArray(4) { table.ptsUs(it) })
        assertTrue(table.reorderedForMp4Presentation)
        assertTrue(IndexedPresentationTimes.verify(bFrames, 4, 1, mp4Compatible = false) is IndexedPtsValidation.Rejected)
        assertArrayEquals(longArrayOf(0, 66_666, 33_333, 100_000), bFrames)
    }

    @Test fun ambiguousCountsTracksDuplicatePtsAndOversizedTablesAreRejected() {
        val pts = longArrayOf(0, 25_000, 66_000)
        assertTrue(IndexedPresentationTimes.verify(pts, null, 1, true) is IndexedPtsValidation.Rejected)
        assertTrue(IndexedPresentationTimes.verify(pts, 4, 1, true) is IndexedPtsValidation.Rejected)
        assertTrue(IndexedPresentationTimes.verify(pts, 3, 2, true) is IndexedPtsValidation.Rejected)
        assertTrue(IndexedPresentationTimes.verify(longArrayOf(0, 0, 66_000), 3, 1, true) is IndexedPtsValidation.Rejected)
        assertTrue(IndexedPresentationTimes.verify(longArrayOf(-2, 25_000), 2, 1, true) is IndexedPtsValidation.Rejected)
        assertTrue(IndexedPresentationTimes.verify(pts, IndexedPresentationTimes.MAX_ENTRIES + 1, 1, true) is IndexedPtsValidation.Rejected)
    }
}
