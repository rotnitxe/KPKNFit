package com.example.kpkn.data.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.preferences.SharedPreferencesKeyValueStore
import com.example.kpkn.screens.sessioneditor.RuleTemplateStore
import com.example.kpkn.screens.sessioneditor.SessionEditorRuleDefaults
import com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class RetiredPersonalStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private suspend fun newSession() {
        DataSessionWriters.closeAdmission()
        DataSessionWriters.awaitStopped()
        DataSessionWriters.reopenAdmission()
    }
    @After fun cleanup(): Unit = runBlocking {
        RuleTemplateStore.closeForDataSession()
        TrainedSessionVersionStore.closeForDataSession()
        newSession()
    }

    @Test fun lateDurableAndAsynchronousPreferenceWritesCannotReplaceRestoredValue(): Unit = runBlocking {
        val old = SharedPreferencesKeyValueStore(context, "qa_retired_preferences")
        old.putString("value", "old")
        assertEquals("old", old.getString("value"))
        newSession()
        val restored = SharedPreferencesKeyValueStore(context, "qa_retired_preferences")
        assertTrue(restored.putStringDurably("value", "restored"))
        assertTrue(runCatching { old.putStringDurably("value", "late") }.isFailure)
        assertTrue(runCatching { old.putString("value", "late async") }.isFailure)
        assertEquals("restored", restored.getString("value"))
    }

    @Test fun retiredTemplateStoreCannotDeleteOrRenameNewTemplates(): Unit = runBlocking {
        val old = RuleTemplateStore(context)
        old.saveAsTemplate("Before restore", SessionEditorRuleDefaults())
        newSession()
        val fresh = RuleTemplateStore(context)
        val template = fresh.saveAsTemplate("Restored rule", SessionEditorRuleDefaults())
        val before = fresh.loadAll()
        assertTrue(runCatching { old.rename(template.id, "Stale rename") }.isFailure)
        assertTrue(runCatching { old.delete(template.id) }.isFailure)
        assertEquals(before, fresh.loadAll())
    }

    @Test fun retiredTrainedVersionStoreCannotAppendToRestoredHistory(): Unit = runBlocking {
        val old = TrainedSessionVersionStore(context)
        old.maybeAppendAfterTraining("qa-session", Session("qa-session", "Original"))
        newSession()
        val fresh = TrainedSessionVersionStore(context)
        val restored = fresh.maybeAppendAfterTraining("qa-session", Session("qa-session", "Restored"))
        assertTrue(runCatching { old.maybeAppendAfterTraining("qa-session", Session("qa-session", "Late version")) }.isFailure)
        assertEquals(restored, fresh.loadForSession("qa-session"))
    }
}
