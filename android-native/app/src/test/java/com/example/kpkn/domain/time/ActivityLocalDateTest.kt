package com.example.kpkn.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ActivityLocalDateTest {

    @Test
    fun completionInstantMapsToLocalActivityDay_notUtcPrefix() {
        val santiago = ZoneId.of("America/Santiago")
        val instantIso = "2026-09-19T01:30:00Z"
        val local = ActivityLocalDate.fromInstantIso(instantIso, santiago)
        assertEquals(LocalDate.parse("2026-09-18"), local)
        assertEquals("2026-09-18", ActivityLocalDate.formatIsoDate(local))
    }

    @Test
    fun matchesActivityDay_usesActualDateWhenPresent() {
        val day = LocalDate.parse("2026-09-18")
        val logInstant = "2026-09-19T01:30:00Z"
        assertTrue(
            ActivityLocalDate.matchesActivityDay(
                storedDate = logInstant,
                activityDay = day,
                zoneId = ZoneId.of("America/Santiago"),
            ),
        )
    }
}
