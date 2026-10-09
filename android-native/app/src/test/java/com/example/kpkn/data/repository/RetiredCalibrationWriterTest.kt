package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.backup.DataSessionWriters
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.NutritionCalibrationProfile
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class RetiredCalibrationWriterTest {
    @Test fun callerHeldCalibrationCannotReplaceRowsAfterMaintenanceReopens(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = KpknDatabase.createInMemory(context)
        try {
            val old = NutritionCalibrationRepository.forDatabase(context, db)
            old.save(NutritionCalibrationProfile(updatedAtEpochMs = 1000L))
            DataSessionWriters.closeAdmission()
            DataSessionWriters.awaitStopped()
            DataSessionWriters.reopenAdmission()
            val fresh = NutritionCalibrationRepository.forDatabase(context, db)
            fresh.save(NutritionCalibrationProfile(updatedAtEpochMs = 2000L))
            val restored = db.nutritionDao().getCalibrationProfile()
            assertTrue(runCatching { old.update { it.copy(updatedAtEpochMs = 3000L) } }.isFailure)
            assertEquals(restored, db.nutritionDao().getCalibrationProfile())
            assertEquals(2000L, fresh.get()!!.updatedAtEpochMs)
        } finally {
            DataSessionWriters.closeAdmission()
            DataSessionWriters.awaitStopped()
            DataSessionWriters.reopenAdmission()
            db.close()
        }
    }
}
