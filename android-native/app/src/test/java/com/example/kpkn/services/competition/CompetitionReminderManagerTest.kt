package com.example.kpkn.services.competition

import android.app.AlarmManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.CompetitionRecordEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
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

    @get:Rule
    val roomRule = RoomTestRule()

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
    fun schedule_completed_cancels_and_does_not_create_alarms() {
        val manager = CompetitionReminderManager(context)
        val planned = futureRecord()
        manager.schedule(planned)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(4, shadowOf(alarmManager).scheduledAlarms.size)

        manager.schedule(planned.copy(status = com.example.kpkn.data.models.CompetitionRecordStatus.COMPLETED))
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
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

    @Test
    fun boot_reschedule_skips_corrupted_record_and_preserves_valid_alarms() = runBlocking {
        val database = KpknDatabase.createInMemory(context)
        try {
            database.competitionRecordDao().upsert(
                CompetitionRecordEntity(
                    id = "corrupt-1",
                    title = "Corrupt",
                    eventDate = "2099-01-01",
                    status = "PLANNED",
                    sportType = "POWERLIFTING",
                    plannedSessionId = "",
                    updatedAtMs = 0L,
                    data = "{not-valid-json",
                ),
            )
            database.competitionRecordDao().upsert(futureRecord("valid-1").toEntity())

            CompetitionReminderBootReceiver.reschedulePersistedRecords(context, database)

            val alarmManager = context.getSystemService(AlarmManager::class.java)
            assertEquals(4, shadowOf(alarmManager).scheduledAlarms.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun post_result_receiver_handles_intent_without_blocking_or_throwing() {
        val intent = Intent(context, CompetitionReminderReceiver::class.java).apply {
            putExtra(CompetitionReminderManager.EXTRA_RECORD_ID, "missing-id")
            putExtra(CompetitionReminderManager.EXTRA_TYPE, CompetitionReminderManager.TYPE_POST_RESULT)
            putExtra(CompetitionReminderManager.EXTRA_TITLE, "Copa KPKN")
        }
        CompetitionReminderReceiver().onReceive(context, intent)
    }
}
