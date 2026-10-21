package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Round trip real por Room/JSON; todas las lecturas y borrados llevan instante y zona. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugeManualAnchorRepositoryTest {
    private lateinit var database: KpknDatabase
    private lateinit var repo: AugeRepository
    private val zone = ZoneId.of("UTC")
    private val t0 = Instant.parse("2026-10-01T22:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private val old = DailyWellbeingLog("ayer", "2026-10-01", manualNeuralBattery = 60,
        manualSpinalBattery = 55, manualMuscleBatteries = mapOf("Pecho" to 50), manualBatteryAnchorMs = t0)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = KpknDatabase.createInMemory(context)
        singleton(KpknDatabase::class.java, database)
        singleton(AugeRepository::class.java, null)
        repo = AugeRepository.getInstance(context)
    }
    @After fun tearDown() {
        singleton(AugeRepository::class.java, null)
        singleton(KpknDatabase::class.java, null)
        database.close()
    }
    private fun singleton(owner: Class<*>, value: Any?) {
        owner.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, value)
    }

    @Test fun readsEachChannelAndKeepsTodaysMetadata() = runBlocking {
        repo.saveWellbeingLog(old)
        val today = DailyWellbeingLog("hoy", "2026-10-02", manualSpinalBattery = 40,
            manualSpinalAnchorMs = t0 + 10 * hour, notes = "hoy", source = WellbeingSource.ONBOARDING_INITIAL,
            capturedFields = setOf("structure"))
        repo.saveWellbeingLog(today)
        val read = requireNotNull(repo.getEffectiveManualAnchors(t0 + 19 * hour, zone))
        assertEquals(60, read.manualNeuralBattery)
        assertEquals(t0, read.manualNeuralAnchorMs)
        assertEquals(40, read.manualSpinalBattery)
        assertEquals(today.notes, read.notes)
        assertEquals(today.source, read.source)
        assertEquals(today.capturedFields, read.capturedFields)
        assertEquals(old, repo.getWellbeingLogs().first { it.id == old.id })
    }

    @Test fun resetWithoutTodaysRowSurvivesAnotherDayAndDoesNotClearAnotherChannel() = runBlocking {
        repo.saveWellbeingLog(old)
        repo.clearManualBatteryOverrides(channel = RecoveryChannelId.SYSTEM, nowMs = t0 + 19 * hour, zone = zone)
        val reset = repo.getWellbeingLogs().single { it.date == "2026-10-02" }
        assertEquals(t0 + 19 * hour, reset.manualBatteryResetMs[RecoveryChannelId.SYSTEM])
        val nextDay = requireNotNull(repo.getEffectiveManualAnchors(t0 + 40 * hour, zone))
        assertNull(nextDay.manualNeuralBattery)
        assertEquals(55, nextDay.manualSpinalBattery)
        assertEquals(50, nextDay.manualMuscleBatteries["Pectorales"])
        assertEquals(old, repo.getWellbeingLogs().first { it.id == old.id })
    }

    @Test fun resetsOneMuscleThenAllAndANewAdjustmentCanBeSaved() = runBlocking {
        repo.saveWellbeingLog(old)
        repo.clearManualBatteryOverrides(muscle = "Pecho", nowMs = t0 + 19 * hour, zone = zone)
        val first = requireNotNull(repo.getEffectiveManualAnchors(t0 + 20 * hour, zone))
        assertTrue(first.manualMuscleBatteries.isEmpty())
        assertEquals(60, first.manualNeuralBattery)
        repo.clearManualBatteryOverrides(nowMs = t0 + 21 * hour, zone = zone)
        val cleared = requireNotNull(repo.getEffectiveManualAnchors(t0 + 22 * hour, zone))
        assertNull(cleared.manualNeuralBattery)
        assertNull(cleared.manualSpinalBattery)
        val stored = repo.getWellbeingLogs().single { it.date == "2026-10-02" }
        repo.saveWellbeingLog(stored.copy(manualNeuralBattery = 35, manualNeuralAnchorMs = t0 + 23 * hour))
        assertEquals(35, repo.getEffectiveManualAnchors(t0 + 24 * hour, zone)?.manualNeuralBattery)
    }
}
