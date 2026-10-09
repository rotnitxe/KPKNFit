package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RetiredCompetitionWriterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: KpknDatabase? = null
    @After fun cleanup() { CompetitionRepository.closeInstance(); database?.close() }

    @Test fun retiredCallerCannotOverwriteRestoredRowsOrScheduleAlarms() = runBlocking {
        val old = CompetitionRepository.initForTests(context)
        withTimeout(10_000) { while (!old.isReady.value) delay(10) }
        val db = CompetitionRepository::class.java.getDeclaredField("db").apply { isAccessible = true }.get(old) as KpknDatabase
        database = db
        val original = CompetitionRecord("retained", "Before restore", status = CompetitionRecordStatus.COMPLETED)
        old.upsertNow(original)
        CompetitionRepository.closeForDataSession()
        val before = db.competitionRecordDao().getAll().single()
        val late = runCatching { old.upsertNow(original.copy(title = "Late old writer")) }
        assertTrue("A retired caller must reject its durable write", late.isFailure)
        assertEquals("The restored/live connection must remain unchanged", before, db.competitionRecordDao().getAll().single())
    }
}
