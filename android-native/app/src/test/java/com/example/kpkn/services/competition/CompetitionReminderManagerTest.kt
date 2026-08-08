package com.example.kpkn.services.competition

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.CompetitionRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompetitionReminderManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    private fun futureRecord(id: String = "record-1") = CompetitionRecord(
        id = id,
        title = "Copa KPKN",
        eventDate = LocalDate.now().plusDays(30).toString(),
        startTime = "10:00",
        reminderStartEnabled = true,
    )

    @Test
    fun schedule_creates_week_48h_start_and_post_result_alarms() {
        CompetitionReminderManager(context).schedule(futureRecord())

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(4, alarms.size)
    }

    @Test
    fun boot_reschedule_rebuilds_alarms_from_persisted_records() = runBlocking {
        val database = KpknDatabase.createInMemory(context)
        try {
            database.competitionRecordDao().upsert(futureRecord("persisted").toEntity())

            CompetitionReminderBootReceiver.reschedulePersistedRecords(context, database)

            val alarmManager = context.getSystemService(AlarmManager::class.java)
            assertEquals(4, shadowOf(alarmManager).scheduledAlarms.size)
        } finally {
            database.close()
        }
    }
}
