package com.example.kpkn.services.nutrition

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SupplementReminderSchedulingTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase

    // Sábado 2026-10-03 13:05 en Santiago.
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 10, 3, 13, 5, 0, 0, ZoneId.of("America/Santiago"))

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = KpknDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
        ShadowAlarmManager.reset()
    }

    private fun alarms() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    private fun idsConMismoHashBajo(): Pair<String, String> {
        val seen = mutableMapOf<Int, String>()
        var i = 0
        while (true) {
            val candidate = "suplemento-$i"
            val low = candidate.hashCode() and 0x0FFF
            val previous = seen.putIfAbsent(low, candidate)
            if (previous != null) return previous to candidate
            i++
        }
    }

    @Suppress("DEPRECATION")
    private fun idOf(alarm: ShadowAlarmManager.ScheduledAlarm): String? =
        shadowOf(alarm.operation).savedIntent.getStringExtra(SupplementReminderManager.EXTRA_ID)

    private fun localTimeOf(alarm: ShadowAlarmManager.ScheduledAlarm): LocalTime =
        Instant.ofEpochMilli(alarm.triggerAtMs).atZone(ZoneId.systemDefault()).toLocalTime()

    @Test
    fun unRecordatorioActivoProgramaUnaAlarmaDiariaDeUnSoloUso() {
        val manager = SupplementReminderManager(context)
        val item = SupplementPilaItem("creatina", 5.0, reminder = "09:00")
        assertTrue(manager.sync(item, now))

        val alarm = alarms().single()
        assertEquals("creatina", idOf(alarm))
        assertEquals(0L, alarm.intervalMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(LocalTime.of(9, 0), localTimeOf(alarm))
        assertTrue(alarm.triggerAtMs > now.toInstant().toEpochMilli())
    }

    @Test
    fun desactivarElRecordatorioCancelaSuAlarma() {
        val manager = SupplementReminderManager(context)
        val active = SupplementPilaItem("magnesio", 300.0, reminder = "21:30")
        assertTrue(manager.sync(active, now))
        assertEquals(1, alarms().size)

        assertFalse(manager.sync(active.copy(reminder = null), now))
        assertNull(alarms().firstOrNull { idOf(it) == "magnesio" })
    }

    @Test
    fun dosIdsConElMismoHashBajoNoCompartenAlarmaNiSeCancelanEntreSi() {
        val ids = idsConMismoHashBajo()
        val manager = SupplementReminderManager(context)

        manager.scheduleNext(ids.first, 9, 0, now)
        manager.scheduleNext(ids.second, 10, 0, now)
        assertEquals(2, alarms().size)

        manager.cancel(ids.first)
        assertEquals(1, alarms().size)
        assertEquals(ids.second, idOf(alarms().single()))
    }

    @Test
    fun elArranqueRestauraLosRecordatoriosDeLaPilaGuardadaEnRoom() = runBlocking {
        db.settingsDao().upsert(
            Settings(
                supplementPila = listOf(
                    SupplementPilaItem("creatina", 5.0, reminder = "09:00"),
                    SupplementPilaItem("vitd", 10.0, reminder = "13:15"),
                    SupplementPilaItem("omega3", 1.0, reminder = null),
                ),
            ).toEntity(),
        )

        NutritionReminderBootReceiver.restoreReminders(context, db, now)

        assertEquals(setOf("creatina", "vitd"), alarms().mapNotNull(::idOf).toSet())
    }

    @Test
    fun syncAllProgramaLasActivasYNoDejaLaDeUnaPilaSinHora() {
        val manager = SupplementReminderManager(context)
        manager.syncAll(
            listOf(
                SupplementPilaItem("creatina", 5.0, reminder = "09:00"),
                SupplementPilaItem("omega3", 1.0, reminder = null),
                SupplementPilaItem("vitd", 10.0, reminder = "13:15"),
            ),
            now,
        )
        assertEquals(setOf("creatina", "vitd"), alarms().mapNotNull(::idOf).toSet())
    }
}
