package com.example.kpkn.screens.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class StoryShareDirectoryTest {
    @Test fun removesOnlyOldOwnedFilesAndPreservesRecentAndUnrelatedFiles() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = storyShareDirectory(context)
        val old = File.createTempFile("workout-story-test-", ".png", dir)
        val recent = File.createTempFile("workout-story-test-", ".png", dir)
        val unrelated = File.createTempFile("unrelated-", ".png", dir)
        try {
            val oldTime = System.currentTimeMillis() - 48L * 60 * 60 * 1000
            assertTrue(old.setLastModified(oldTime))
            assertTrue(unrelated.setLastModified(oldTime))
            storyShareDirectory(context)
            assertFalse(old.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
        } finally { old.delete(); recent.delete(); unrelated.delete() }
    }
}
