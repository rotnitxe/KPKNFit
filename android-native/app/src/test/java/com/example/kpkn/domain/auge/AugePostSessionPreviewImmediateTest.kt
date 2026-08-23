package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AugePostSessionPreviewImmediateTest {
    @Test
    fun completionInstant_isPreserved_andHoursAreZeroAtFinish() {
        val completion = ZonedDateTime.of(2026, 8, 23, 17, 25, 0, 0, ZoneId.of("America/Santiago")).toInstant()
        val iso = completion.toString()
        val impact = MuscularSessionImpactEngine.evaluate(
            completedExercises = AugeRealSessionFixtures.completedExercises,
            completionInstantIso = iso,
            exerciseDb = AugeRealSessionFixtures.exerciseDb,
            settings = Settings(),
        )

        assertEquals(iso, impact.completionInstantIso)
        assertTrue(AugeUtils.parseIsoMs(impact.completionInstantIso) == completion.toEpochMilli())
        assertTrue(impact.involvedVolumeMuscles.contains("Pectorales"))
        assertTrue(impact.perMuscle.values.all { it.immediateDrainPct >= 0.0 })
    }

    @Test
    fun boundariesAndDst_parseAsTheSameInstant() {
        val values = listOf("2026-08-23T00:01:00-04:00", "2026-08-23T17:25:00-04:00", "2026-08-23T23:59:00-04:00")
        assertTrue(values.zipWithNext().all { (left, right) -> Instant.parse(left).isBefore(Instant.parse(right)) })
        val dst = ZonedDateTime.of(2026, 9, 6, 3, 0, 0, 0, ZoneId.of("America/Santiago"))
        assertEquals(dst.toInstant().toEpochMilli(), AugeUtils.parseIsoMs(dst.toString()))
    }
}
