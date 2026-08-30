package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SleepLog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Overnight muscular recovery should feel like a real night (~+10–20 pts)
 * from the sigmoidal curve alone — sleep hours are not a recovery bonus.
 */
class OvernightRecoverySensationTest {

    @Test
    fun mondayNight_to_tuesdayMorning_mediumMuscle_recoversIntoBand() {
        val monday2100 = Instant.parse("2026-07-20T21:00:00Z").toEpochMilli()
        val tuesday0700 = Instant.parse("2026-07-21T07:00:00Z").toEpochMilli() // +10h
        val wellbeing = DailyWellbeingLog(
            id = "wb",
            date = "2026-07-20",
            sleepHours = 7.5,
            sleepQuality = 4,
            manualMuscleBatteries = mapOf("Pectorales" to 40),
            manualBatteryAnchorMs = monday2100,
        )
        val sleepLogs = listOf(
            SleepLog(
                id = "sleep-1",
                date = "2026-07-21",
                endTime = "2026-07-21T07:00:00Z",
                duration = 7.5,
            ),
        )

        val status = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            sleepLogs = sleepLogs,
            nowOverride = tuesday0700,
        )

        assertTrue(
            "Overnight from 40% should land ~50–62 without a sleep-habit bonus, got ${status.recoveryScore}",
            status.recoveryScore in 50..62,
        )
    }

    @Test
    fun sigmoidalHours_tenHours_isHalfLinearNotFifteenPercent() {
        val effective = AugeUtils.getSigmoidalHours(10.0)
        assertTrue("10h should count ~5.0 effective hours, got $effective", effective in 4.5..5.5)
    }
}
