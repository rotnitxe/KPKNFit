package com.example.kpkn.domain.training.schedule

import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class InactivityDetectorTest {
    private val today = LocalDate.of(2026,10,25)
    private val zone = ZoneId.of("America/Santiago")
    private fun log(date: String) = WorkoutLog("id","p","s","S",date,30)
    @Test fun thresholdsDescribeOriginalRecordGapOnly() {
        assertNull(InactivityDetector.detect(emptyList(),"p",today,zone))
        assertNull(InactivityDetector.detect(listOf(log("2026-10-20")),"p",today,zone))
        val seven = InactivityDetector.detect(listOf(log("2026-10-18")),"p",today,zone)!!
        assertEquals(7,seven.daysSinceLastLog)
        assertEquals(1,seven.level)
        assertTrue(seven.message.contains("no indica si entrenaste"))
        assertEquals(2,InactivityDetector.detect(listOf(log("2026-10-04")),"p",today,zone)!!.level)
    }
    @Test fun futureInvalidOtherProgramAndActualDateAreHandledWithoutInventingEvidence() {
        assertNull(InactivityDetector.detect(listOf(log("bad"),log("2026-11-01"),log("2026-10-01").copy(programId = "other")),"p",today,zone))
        assertNull(InactivityDetector.detect(listOf(log("2026-10-01").copy(actualDate = "2026-10-24")),"p",today,zone))
        val result = InactivityDetector.detect(listOf(log("2026-10-19T01:00:00Z")),"p",today,zone)!!
        assertEquals(7,result.daysSinceLastLog)
    }
}
