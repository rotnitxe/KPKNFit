package com.example.kpkn.data.migrations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LegacyLearnPreferencesCleanupTest {
    @Test
    fun clearsLegacyPrefsOnceAndLeavesMarkerForIdempotentRetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("learn_prefs", Context.MODE_PRIVATE)
            .edit().clear().putString("course_progress", "legacy").commit()
        context.getSharedPreferences("kpkn_migrations", Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertTrue(clearLegacyLearnPreferencesOnce(context))
        assertTrue(context.getSharedPreferences("learn_prefs", Context.MODE_PRIVATE).all.isEmpty())
        assertFalse(clearLegacyLearnPreferencesOnce(context))
    }
}
