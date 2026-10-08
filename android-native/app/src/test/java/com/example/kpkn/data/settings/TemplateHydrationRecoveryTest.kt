package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.SessionTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class TemplateHydrationRecoveryTest {
    @Test fun unreadableStoreRequiresRecovery() = runBlocking(Dispatchers.IO) {
        val context: Context = ApplicationProvider.getApplicationContext()
        SessionTemplateRepository.resetForTests()
        KpknDatabase.closeInstance()
        val database = KpknDatabase.createInMemory(context)
        // A closed in-memory helper can reopen in Robolectric. Remove a required
        // table to produce an actual DAO failure without depending on that behavior.
        database.openHelper.writableDatabase.execSQL("DROP TABLE session_templates")
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, database)
        try {
            val templates = SessionTemplateRepository.getInstance(context)
            withTimeout(10_000) { templates.requiresRecovery.first { it } }
            assertFalse(templates.isReady.value)
            val failure = runCatching { withTimeout(2_000) { templates.getByIdAfterReady("missing") } }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertNotNull(failure?.cause)
        } finally {
            SessionTemplateRepository.resetForTests()
            KpknDatabase.closeInstance()
        }
    }
}
