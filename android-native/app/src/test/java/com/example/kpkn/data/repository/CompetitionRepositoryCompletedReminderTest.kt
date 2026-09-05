package com.example.kpkn.data.repository

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import kotlinx.coroutines.delay
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
class CompetitionRepositoryCompletedReminderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        CompetitionRepository.closeInstance()
    }

    @After
    fun tearDown() {
        CompetitionRepository.closeInstance()
        ShadowAlarmManager.reset()
    }

    @Test
    fun upsert_completed_cancels_reminders() = runBlocking {
        val repo = CompetitionRepository.initForTests(context)
        while (!repo.isReady.value) delay(25)
        val planned = CompetitionRecord(
            id = "comp-1",
            title = "Open",
            eventDate = LocalDate.now().plusDays(30).toString(),
            startTime = "10:00",
            status = CompetitionRecordStatus.PLANNED,
            reminderStartEnabled = true,
        )
        repo.upsertNow(planned)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(4, shadowOf(alarmManager).scheduledAlarms.size)

        repo.upsertNow(planned.copy(status = CompetitionRecordStatus.COMPLETED))
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
