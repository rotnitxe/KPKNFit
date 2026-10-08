package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileContext
import com.example.kpkn.data.account.ProfileStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class ProfileStorageTest {
    private val base: Context = ApplicationProvider.getApplicationContext()
    @Test fun concurrentDirectoryCreation() {
        ProfileStorage.initialize(base)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(16)
        try {
            repeat(20) { iteration ->
                val profile = ProfileStorage.context(base, LocalProfile(uid = "concurrent-$iteration"))
                val start = java.util.concurrent.CyclicBarrier(16)
                val readers = (1..16).map { executor.submit<List<File>> {
                    start.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    listOf(profile.filesDir, profile.cacheDir, profile.noBackupFilesDir,
                        requireNotNull(profile.getDatabasePath("kpkn.db").parentFile))
                } }
                readers.forEach { result ->
                    result.get(15, java.util.concurrent.TimeUnit.SECONDS).forEach { assertTrue(it.isDirectory) }
                }
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        }
    }
    @Test fun aScopedDatabaseCannotBeReusedByAnotherProfile() {
        ProfileStorage.initialize(base)
        com.example.kpkn.data.db.KpknDatabase.closeInstance()
        val a = ProfileStorage.context(base, LocalProfile(uid = "database-owner-a"))
        val b = ProfileStorage.context(base, LocalProfile(uid = "database-owner-b"))
        try {
            val owned = com.example.kpkn.data.db.KpknDatabase.getInstance(a)
            assertSame(owned, com.example.kpkn.data.db.KpknDatabase.getInstance(a))
            assertThrows(IllegalStateException::class.java) { com.example.kpkn.data.db.KpknDatabase.getInstance(b) }
        } finally { com.example.kpkn.data.db.KpknDatabase.closeInstance(); a.deleteDatabase("kpkn.db"); b.deleteDatabase("kpkn.db") }
    }
    @Test fun separateProfilesAndDelayedContextsCannotSharePersonalData() {
        ProfileStorage.initialize(base)
        val a = ProfileStorage.context(base, LocalProfile(uid = "user-a")) as ProfileContext
        val b = ProfileStorage.context(base, LocalProfile(uid = "user-b")) as ProfileContext
        a.getSharedPreferences("body", 0).edit().putString("measurement", "private-a").commit()
        File(a.filesDir, "photo.jpg").writeText("private-photo-a")
        assertNull(b.getSharedPreferences("body", 0).getString("measurement", null))
        assertFalse(File(b.filesDir, "photo.jpg").exists())
        assertNotEquals(a.getDatabasePath("kpkn.db"), b.getDatabasePath("kpkn.db"))
        ProfileStorage.select("user-b")
        a.getSharedPreferences("body", 0).edit().putString("late", "still-a").commit()
        assertNull(b.getSharedPreferences("body", 0).getString("late", null))
        b.clearPersonalPreferences()
        assertEquals("private-a", a.getSharedPreferences("body", 0).getString("measurement", null))
        assertEquals("private-photo-a", File(a.filesDir, "photo.jpg").readText())
    }
    @Test fun legacyGuestAndPlatformStoresKeepTheirExistingLocations() {
        ProfileStorage.initialize(base)
        val guest = ProfileStorage.context(base, LocalProfile())
        assertEquals(base.filesDir, guest.filesDir)
        assertEquals(base.getDatabasePath("kpkn.db"), guest.getDatabasePath("kpkn.db"))
        base.getSharedPreferences("variant_preferences", 0).edit().putString("variant", "retained").commit()
        assertEquals("retained", guest.getSharedPreferences("variant_preferences", 0).getString("variant", null))
        val a = ProfileStorage.context(base, LocalProfile(uid = "user-a")) as ProfileContext
        val b = ProfileStorage.context(base, LocalProfile(uid = "user-b"))
        a.getSharedPreferences("com.google.firebase.auth.api.Store.test", 0).edit().putBoolean("sdk", true).commit()
        assertTrue(b.getSharedPreferences("com.google.firebase.auth.api.Store.test", 0).getBoolean("sdk", false))
        a.clearPersonalPreferences()
        assertTrue(b.getSharedPreferences("com.google.firebase.auth.api.Store.test", 0).getBoolean("sdk", false))
        assertEquals(base.getDatabasePath("androidx.work.workdb"), a.getDatabasePath("androidx.work.workdb"))
    }
}
