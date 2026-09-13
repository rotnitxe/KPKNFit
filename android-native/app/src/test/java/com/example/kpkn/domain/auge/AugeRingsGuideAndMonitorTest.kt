package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.LoadAdvisory
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RingStartSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBandsSingleSourceTest {
    @Test
    fun labelsAndColorsComeFromTheSameThresholds() {
        assertEquals(RecoveryBand.HIGH, RecoveryBands.band(85))
        assertEquals(RecoveryBand.NORMAL, RecoveryBands.band(70))
        assertEquals(RecoveryBand.MODERATE, RecoveryBands.band(50))
        assertEquals(RecoveryBand.LOW, RecoveryBands.band(35))
        assertEquals(RecoveryBand.CRITICAL, RecoveryBands.band(34))
        assertEquals("Óptimo", ExerciseReadinessEngine.readinessLabel(90))
        assertEquals("Bueno", ExerciseReadinessEngine.readinessLabel(70))
        assertEquals(RecoveryBands.NORMAL_MIN, ExerciseReadinessEngine.ADJUSTMENT_THRESHOLD)
        assertEquals(RecoveryBands.colorArgb(90), RecoveryBands.colorArgb(70))
        assertTrue(RecoveryBands.colorArgb(40) != RecoveryBands.colorArgb(90))
    }
}

class RingProjectionTest {
    @Test
    fun hoursUntilNormalIsZeroAtOrAbove70AndPositiveBelow() {
        assertEquals(0, RecoveryBands.hoursUntilNormal(80, 36.0))
        val hours = RecoveryBands.hoursUntilNormal(50, 36.0)
        assertTrue("hours=$hours", hours != null && hours > 0)
    }
}

class PersonalBaselineTest {
    @Test
    fun p25p75RequiresFourSnapshots() {
        val snaps = (60..67).map {
            RingStartSnapshot("d$it", muscular = it, energy = it, structure = it)
        }
        assertNull(AxialLoadMonitor.personalBaseline(snaps.take(3), RecoveryChannelId.SYSTEM))
        val range = AxialLoadMonitor.personalBaseline(snaps, RecoveryChannelId.SYSTEM)!!
        assertTrue(range.first <= 62)
        assertTrue(range.last >= 65)
    }
}

class LoadAdvisoryHysteresisTest {
    @Test
    fun dropsAtMostOneLevelPerDayAndKeepsHysteresisBand() {
        val fromUnload = LoadAdvisoryEngine.applyHysteresis(
            raw = LoadAdvisoryLevel.WATCH,
            previous = LoadAdvisoryLevel.UNLOAD,
            allowDrop = true,
            acwr = 1.50,
        )
        assertEquals(LoadAdvisoryLevel.UNLOAD, fromUnload)
        val drop = LoadAdvisoryEngine.applyHysteresis(
            raw = LoadAdvisoryLevel.WATCH,
            previous = LoadAdvisoryLevel.UNLOAD,
            allowDrop = true,
            acwr = 1.20,
        )
        assertEquals(LoadAdvisoryLevel.ADJUST, drop)
        val sameDay = LoadAdvisoryEngine.applyHysteresis(
            raw = LoadAdvisoryLevel.NONE,
            previous = LoadAdvisoryLevel.ADJUST,
            allowDrop = false,
            acwr = 1.0,
        )
        assertEquals(LoadAdvisoryLevel.ADJUST, sameDay)
    }
}

class AdvisoryDismissalTest {
    @Test
    fun dismissedKeysAreFiltered() {
        val report = AxialLoadReport(
            insufficientData = false,
            acwr = 1.5,
            axialDaysIn7 = 4,
            axialDaysIn4 = 3,
            axialDaysIn6 = 4,
            consecutiveAxialDays = 3,
            heavySessions7 = 2,
            startTrend = listOf(55, 54),
        )
        val systemic = SystemicLoadReport(insufficientData = true, acwr = null, sessionsIn7 = 2, startTrend = emptyList())
        val cache = AugeAdaptiveCache()
        val first = LoadAdvisoryEngine.evaluate(report, systemic, cache, nowMs = 1_778_000_000_000L)
        assertTrue(first.advisories.isNotEmpty())
        val dismissed = first.advisories.map { it.id }.toSet()
        val second = LoadAdvisoryEngine.evaluate(report, systemic, cache, nowMs = 1_778_000_000_000L, dismissed = dismissed)
        assertTrue(second.advisories.none { it.id in dismissed })
    }
}

class LoadAdvisorySessionContextTest {
    @Test
    fun namesTheTwoMostAxialLiftsAndOptionalReplacement() {
        val base = LoadAdvisory(
            id = "STRUCTURE|2026-W37|ADJUST",
            channel = RecoveryChannelId.STRUCTURE,
            level = LoadAdvisoryLevel.ADJUST,
            title = "Columna pide un respiro",
            body = "Llevas 4 días axiales en 7. Hoy baja 10–15 % en sentadilla o peso muerto, o cambia uno por una variante menos axial.",
            weekKey = "2026-W37",
        )
        val upcoming = listOf(
            AxialSessionExercise("Press Banca", axial = 0.0, replacementGroup = "horizontal_press"),
            AxialSessionExercise("Sentadilla alta", axial = 1.0, replacementGroup = "squat"),
            AxialSessionExercise("Peso muerto", axial = 0.9, replacementGroup = "hinge"),
            AxialSessionExercise("Curl", axial = 0.0, replacementGroup = "curl"),
        )
        val catalog = listOf(
            AxialSessionExercise("Hack squat", axial = 0.2, replacementGroup = "squat"),
        )
        val adjusted = LoadAdvisoryEngine.contextualize(base, upcoming, catalog)
        assertTrue(adjusted.body.contains("Sentadilla alta"))
        assertTrue(adjusted.body.contains("Peso muerto"))
        assertTrue(adjusted.body.startsWith("Llevas 4 días axiales en 7."))
        assertTrue(!adjusted.body.contains("sentadilla o peso muerto"))
        assertTrue(adjusted.body.contains("Hack squat"))
        assertEquals("Columna: hoy −10 % en Sentadilla alta", LoadAdvisoryEngine.badgeLabel(adjusted, upcoming))
    }

    @Test
    fun watchAndEnergyAdvisoriesStayGeneric() {
        val watch = LoadAdvisory(
            id = "STRUCTURE|2026-W37|WATCH",
            channel = RecoveryChannelId.STRUCTURE,
            level = LoadAdvisoryLevel.WATCH,
            title = "Columna",
            body = "Vigílala",
            weekKey = "2026-W37",
        )
        val upcoming = listOf(AxialSessionExercise("Sentadilla", axial = 1.0, replacementGroup = "squat"))
        assertEquals("Vigílala", LoadAdvisoryEngine.contextualize(watch, upcoming).body)
        val energy = LoadAdvisory(
            id = "SYSTEM|2026-W37|ADJUST",
            channel = RecoveryChannelId.SYSTEM,
            level = LoadAdvisoryLevel.ADJUST,
            title = "Energía acumulada",
            body = "Deja 1–2 repeticiones en reserva.",
            weekKey = "2026-W37",
        )
        assertEquals(energy.body, LoadAdvisoryEngine.contextualize(energy, upcoming).body)
    }
}
