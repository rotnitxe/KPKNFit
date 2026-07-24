package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.SleepLogExtended
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SleepLogExtendedToSleepLogTest {

    @Test
    fun toSleepLogEmitsIso8601EndTimeNotBareClock() {
        val extended = SleepLogExtended(
            id = "s1",
            date = "2026-07-23",
            bedTime = "23:30",
            wakeTime = "07:00",
            duration = 7.5,
        )
        val basic = extended.toSleepLog()
        assertFalse("endTime must not be bare HH:mm", basic.endTime == "07:00")
        assertTrue("endTime should contain a date-time separator", basic.endTime.contains("T"))
        // Must be parseable as Instant (ISO-8601 with offset/Z) or at least Instant-compatible
        val parsed = runCatching { Instant.parse(basic.endTime) }.isSuccess ||
            basic.endTime.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}.*"""))
        assertTrue("endTime should be ISO-8601 parseable, was=${basic.endTime}", parsed)
    }
}
