package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Reloj y zona explícitos: las anclas son estado independiente de la fila diaria. */
class ManualAnchorStateTest {
    private val zone = ZoneId.of("UTC")
    private val hour = 3_600_000L
    private val t0 = Instant.parse("2026-10-01T22:00:00Z").toEpochMilli()
    private fun row(time: Long, neural: Int? = null, spinal: Int? = null, muscular: Int? = null) = DailyWellbeingLog(
        id = "fila-$time", date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toString(),
        manualNeuralBattery = neural, manualSpinalBattery = spinal, manualMuscularBattery = muscular,
        manualBatteryAnchorMs = time,
    )
    private fun merged(rows: List<DailyWellbeingLog>, now: Long) = effectiveManualAnchors(rows, now, zone)
    private fun energy(w: DailyWellbeingLog?, now: Long, history: List<WorkoutLog> = emptyList()) =
        AugeRecoveryEngine.calculateSystemicFatigue(history, w, Settings(), nowOverride = now, zone = zone).first
    private fun global(w: DailyWellbeingLog?, now: Long) =
        AugeRecoveryEngine.calculateGlobalBatteries(emptyList(), w, Settings(), nowOverrideMs = now, zone = zone)

    @Test fun continuityAtEighteenHoursMidnightAndTheNextRow() {
        val anchor = row(t0, neural = 60, spinal = 55, muscular = 60)
            .copy(manualMuscleBatteries = mapOf("Pectorales" to 50))
        val before = t0 + 18 * hour - 60_000L
        val after = t0 + 18 * hour + 60_000L
        assertTrue(abs(energy(merged(listOf(anchor), before), before) - energy(merged(listOf(anchor), after), after)) <= 1)
        fun scores(now: Long): List<Int> {
            val w = merged(listOf(anchor), now)
            val g = global(w, now)
            val local = AugeRecoveryEngine.calculateMuscleBattery("Pectorales", emptyList(), w, Settings(), nowOverride = now, zone = zone)
            return listOf(g.cnc, g.spinal, g.muscular, local.recoveryScore)
        }
        scores(t0 + 18 * hour - 6 * 60_000L).zip(scores(t0 + 18 * hour + 6 * 60_000L)).forEach { (a, b) ->
            assertTrue("17,9/18,1 h: $a → $b", abs(a - b) <= 1)
        }
        val midnight = t0 + 2 * hour
        assertTrue(abs(energy(merged(listOf(anchor), midnight - 1), midnight - 1) - energy(merged(listOf(anchor), midnight + 1), midnight + 1)) <= 1)
        val checkIn = row(t0 + 12 * hour).copy(stressLevel = 5, notes = "hoy", capturedFields = setOf("energy"))
        val withToday = requireNotNull(merged(listOf(anchor, checkIn), after))
        assertEquals(energy(merged(listOf(anchor), after), after), energy(withToday, after))
        assertEquals(checkIn.notes, withToday.notes)
        assertEquals(checkIn.capturedFields, withToday.capturedFields)
        val declared = anchor.copy(doms = 4, source = WellbeingSource.ONBOARDING_INITIAL, capturedFields = setOf("energy"))
        val carried = requireNotNull(merged(listOf(declared), midnight + 1))
        assertEquals(declared.doms, carried.doms)
        assertEquals(declared.source, carried.source)
        assertEquals(declared.capturedFields, carried.capturedFields)
        println("2.5 Energía 60 a las 22:00; 15:59/16:01 antes=${energy(anchor, before)}/100 después=${energy(withToday, before)}/${energy(withToday, after)}")
    }

    @Test fun latestAnchorOfEachChannelWinsAndAnEnergyEditDoesNotMoveTheOthers() {
        val older = row(t0, neural = 60, spinal = 50, muscular = 55)
        val newer = row(t0 + 12 * hour, neural = 40)
        val now = t0 + 13 * hour
        val fused = requireNotNull(merged(listOf(newer, older), now))
        assertEquals(40, fused.manualNeuralBattery)
        assertEquals(t0, fused.manualSpinalAnchorMs)
        assertEquals(t0, fused.manualMuscularAnchorMs)
        val previous = global(merged(listOf(older), now), now)
        val current = global(fused, now)
        assertTrue(current.cnc < previous.cnc)
        assertEquals(previous.spinal, current.spinal)
        assertEquals(previous.muscular, current.muscular)
        assertEquals(fused, merged(listOf(older, newer), now))
    }

    @Test fun legacyAndV2CompeteByMuscleAndPreserveTheSupervisedBaseline() {
        val old = row(t0).copy(manualMuscleBatteries = mapOf("Pecho" to 50, "Dorsales" to 45))
        val override = ManualMuscleBatteryOverride(60, t0 + 2 * hour, "sesión", 87)
        val v2 = row(t0 + 2 * hour).copy(manualMuscleOverridesV2 = mapOf("Pectorales" to override))
        val newerLegacy = row(t0 + 3 * hour).copy(manualMuscleBatteries = mapOf("Pecho" to 70))
        val middle = requireNotNull(merged(listOf(old, v2), t0 + 4 * hour))
        assertEquals(override, middle.manualMuscleOverridesV2["Pectorales"])
        assertEquals(mapOf("Dorsales" to 45), middle.manualMuscleBatteries)
        val latest = requireNotNull(merged(listOf(old, v2, newerLegacy), t0 + 4 * hour))
        assertTrue(latest.manualMuscleOverridesV2.isEmpty())
        assertEquals(mapOf("Pectorales" to 70, "Dorsales" to 45), latest.manualMuscleBatteries)
        assertEquals(t0, latest.manualMuscleAnchorMs["Dorsales"])
    }

    @Test fun aFutureAnchorCannotHideAnEarlierOneEvenOnAnotherChannel() {
        val old = row(t0, neural = 60, spinal = 55)
        val future = row(t0 + hour, neural = 10).copy(manualSpinalBattery = 40, manualSpinalAnchorMs = t0 - hour)
        val w = requireNotNull(merged(listOf(old, future), t0))
        assertEquals(60, w.manualNeuralBattery)
        assertEquals(55, w.manualSpinalBattery)
        val mixed = old.copy(manualNeuralAnchorMs = t0 + hour, manualSpinalAnchorMs = t0)
        assertEquals(100, global(mixed, t0).cnc)
        assertEquals(global(old, t0).spinal, global(mixed, t0).spinal)
    }

    @Test fun anAnchorOlderThanTenDaysIsIgnored() {
        val old = row(t0, neural = 60, spinal = 55, muscular = 50)
        val now = t0 + 10 * 24 * hour + 1
        assertNull(merged(listOf(old), now))
        assertEquals(global(null, now), global(old, now).copy(sourceLabel = "Sin calibrar"))
    }

    @Test fun resetWinsIncludingOnATieAndANewAdjustmentCanFollowIt() {
        val old = row(t0, neural = 60, spinal = 55).copy(manualMuscleBatteries = mapOf("Pecho" to 40))
        val reset = row(t0 + 12 * hour).copy(
            manualBatteryResetMs = mapOf(RecoveryChannelId.SYSTEM to t0 + 12 * hour),
            manualMuscleResetMs = mapOf("Pectorales" to t0 + 12 * hour),
        )
        val now = t0 + 13 * hour
        val w = requireNotNull(merged(listOf(old, reset), now))
        assertNull(w.manualNeuralBattery)
        assertEquals(55, w.manualSpinalBattery)
        assertTrue(w.manualMuscleBatteries.isEmpty())
        assertNull(merged(listOf(old, reset, row(t0 + 12 * hour, neural = 30)), now)?.manualNeuralBattery)
        assertEquals(30, merged(listOf(old, reset, row(t0 + 12 * hour + 1, neural = 30)), now)?.manualNeuralBattery)
    }

    @Test fun aPriorSessionIsAbsorbedAndAPosteriorSessionAddsFatigueOnTop() {
        val w = row(t0, neural = 60, spinal = 55).copy(manualMuscleBatteries = mapOf("Pectorales" to 50))
        fun log(time: Long) = WorkoutLog("sesión-$time", "p", "s", "entreno", Instant.ofEpochMilli(time).toString(), 60,
            completedExercises = AugeRealSessionFixtures.completedExercises)
        val now = t0 + hour
        val before = listOf(log(t0 - 1))
        val after = listOf(log(t0 + 1))
        assertEquals(energy(w, now), energy(w, now, before))
        assertTrue(energy(w, now, after) < energy(w, now))
        fun spinal(h: List<WorkoutLog>) = AugeRecoveryEngine.calculateSpinalBattery(h, w, Settings(),
            exerciseDb = AugeRealSessionFixtures.exerciseDb, nowOverride = now, zone = zone)
        fun muscle(h: List<WorkoutLog>) = AugeRecoveryEngine.calculateMuscleBattery("Pectorales", h, w, Settings(),
            exerciseDb = AugeRealSessionFixtures.exerciseDb, precomputedCapacity = 1000.0, nowOverride = now, zone = zone).recoveryScore
        assertEquals(spinal(emptyList()), spinal(before))
        assertTrue(spinal(after) < spinal(emptyList()))
        assertEquals(muscle(emptyList()), muscle(before))
        assertTrue(muscle(after) < muscle(emptyList()))
    }

    @Test fun oldJsonAndASingleTodaysAnchorKeepTheirMeaningAndNewStateRoundTrips() {
        val raw = """{"id":"antiguo","date":"2026-10-01","manualNeuralBattery":60,"manualSpinalBattery":55,"manualMuscleBatteries":{"Pectorales":50},"manualBatteryAnchorMs":$t0}"""
        val old = Json.decodeFromString<DailyWellbeingLog>(raw)
        for (hours in listOf(0, 1)) {
            val now = t0 + hours * hour
            val effective = requireNotNull(merged(listOf(old), now))
            assertEquals(global(old, now), global(effective, now))
            assertEquals(old.manualBatteryAnchorMs, effective.manualNeuralAnchorMs)
        }
        val noStamp = Json.decodeFromString<DailyWellbeingLog>("""{"id":"sin-marca","date":"2026-10-01","manualNeuralBattery":60}""")
        assertEquals(global(noStamp, t0), global(merged(listOf(noStamp), t0), t0))
        val state = old.copy(manualNeuralAnchorMs = t0, manualSpinalAnchorMs = t0 - hour,
            manualMuscleAnchorMs = mapOf("Pectorales" to t0), manualBatteryResetMs = mapOf(RecoveryChannelId.MUSCULAR to t0))
        assertEquals(state, Json.decodeFromString<DailyWellbeingLog>(Json.encodeToString(state)))
    }

    @Test fun savingAnEnergyEditKeepsTheClocksOfTheOtherLegacyChannels() {
        val old = row(t0, neural = 60, spinal = 55, muscular = 50).copy(manualMuscleBatteries = mapOf("Dorsales" to 40))
        val saved = mergeWellbeingForSave(old, row(t0 + hour, neural = 30), zone)
        assertEquals(t0 + hour, saved.manualNeuralAnchorMs)
        assertEquals(t0, saved.manualSpinalAnchorMs)
        assertEquals(t0, saved.manualMuscularAnchorMs)
        assertEquals(t0, saved.manualMuscleAnchorMs["Dorsales"])
    }

    @Test fun anEnergyEditDoesNotRewriteSpinalLoadFromAnEarlierSessionOrItsGuard() {
        val old = row(t0, spinal = 55).copy(doms = 4)
        val saved = mergeWellbeingForSave(old, row(t0 + hour, neural = 40).copy(doms = 4), zone)
        val history = listOf(WorkoutLog("entre-ajustes", "p", "s", "entreno",
            Instant.ofEpochMilli(t0 + hour / 2).toString(), 60, completedExercises = AugeRealSessionFixtures.completedExercises))
        fun spinal(w: DailyWellbeingLog) = AugeRecoveryEngine.calculateSpinalBattery(history, w, Settings(),
            exerciseDb = AugeRealSessionFixtures.exerciseDb, nowOverride = t0 + 2 * hour, zone = zone)
        assertEquals(spinal(old), spinal(saved))
    }

    @Test fun aStaleCopyCannotUndoAResetAndResurrectAnOlderAnchor() {
        val reset = row(t0 + hour).copy(
            manualBatteryResetMs = mapOf(RecoveryChannelId.SYSTEM to t0 + hour),
            manualMuscleResetMs = mapOf("Pectorales" to t0 + hour),
        )
        val copy = reset.copy(
            manualBatteryResetMs = mapOf(RecoveryChannelId.SYSTEM to t0),
            manualMuscleResetMs = mapOf("Pectorales" to t0),
        )
        val saved = mergeWellbeingForSave(reset, copy, zone)
        assertEquals(reset.manualBatteryResetMs, saved.manualBatteryResetMs)
        assertEquals(reset.manualMuscleResetMs, saved.manualMuscleResetMs)
        val old = row(t0 + hour / 2, neural = 60).copy(manualMuscleBatteries = mapOf("Pectorales" to 50))
        // Después de medianoche no hay fila nueva ni anclas vigentes: no se inventa bienestar.
        assertNull(merged(listOf(old, saved), t0 + 2 * hour))
    }
}
