package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.*
import com.example.kpkn.data.backup.PersonalDataErasure
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.sync.GuestImportPlan
import com.example.kpkn.data.sync.GuestSourceLedger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PersonalDataErasureTest {
    @Before fun dispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun releaseDispatcher() { KpknDatabase.closeInstance(); Dispatchers.resetMain() }
    private val base: Context = ApplicationProvider.getApplicationContext()
    private fun initialize(): Unit { ProfileStorage.initialize(base); KpknDatabase.closeInstance() }
    private fun seed(context: Context): File {
        context.getDatabasePath("kpkn.db").also { it.parentFile!!.mkdirs(); it.writeText("synthetic-database") }
        context.getSharedPreferences("personal", 0).edit().putString("entry", "synthetic-value").commit()
        File(context.cacheDir, "pending.bin").writeText("synthetic-cache")
        File(context.noBackupFilesDir, "pending.json").writeText("synthetic-pending")
        File(context.noBackupFilesDir, "legacy-body-recovery.json").writeText("synthetic-recovery")
        return File(context.filesDir, "workout_media/clip.mp4").also { it.parentFile!!.mkdirs(); it.writeText("synthetic-media") }
    }
    @Test fun eraseKeepsOtherProfilesAndPlatformPreferences() = runBlocking {
        initialize()
        val a = ProfileStorage.context(base, LocalProfile(uid = "erase-a"))
        val b = ProfileStorage.context(base, LocalProfile(uid = "erase-b"))
        seed(a); val other = seed(b)
        a.getSharedPreferences("firebase.sdk", 0).edit().putString("identity", "synthetic-platform").commit()
        PersonalDataErasure.erase(a)
        assertFalse(PersonalDataErasure.pending())
        assertFalse(a.getDatabasePath("kpkn.db").exists())
        assertFalse(File(a.noBackupFilesDir, "legacy-body-recovery.json").exists())
        assertTrue(a.getSharedPreferences("personal", 0).all.isEmpty())
        assertTrue(other.exists())
        assertEquals("synthetic-value", b.getSharedPreferences("personal", 0).getString("entry", null))
        assertEquals("synthetic-platform", a.getSharedPreferences("firebase.sdk", 0).getString("identity", null))
    }
    @Test fun interruptionRetainsJournalAndOwnedGuestProofAfterItsPlanIsRemoved() = runBlocking {
        initialize()
        val owner = ProfileStorage.context(base, LocalProfile(uid = "erase-owner")) as ProfileContext
        val guestProfile = LocalProfile(guestId = "erase-guest")
        val guest = ProfileStorage.context(base, guestProfile)
        seed(owner); seed(guest)
        val plan = GuestImportPlan("import-erasure", "guest-proof", guestProfile, "test-device", emptyList(), "a".repeat(64), ownerUid = owner.profile.uid)
        File(guest.noBackupFilesDir, "guest-import/plan.json").also { it.parentFile!!.mkdirs(); it.writeText(Json.encodeToString(GuestImportPlan.serializer(), plan)) }
        GuestSourceLedger.record(owner, requireNotNull(owner.profile.uid), plan.id, guestProfile)
        PersonalDataErasure.begin(owner)
        try { PersonalDataErasure.finishStores(owner) { if (it == 0) error("simulated process interruption") }; fail("No interruption") }
        catch (_: IllegalStateException) { assertTrue(PersonalDataErasure.pending()) }
        assertFalse(File(guest.noBackupFilesDir, "guest-import/plan.json").exists())
        assertTrue(owner.getDatabasePath("kpkn.db").exists())
        // Reading the durable record again models a fresh process; no in-memory plan is used.
        assertEquals(guestProfile.key, PersonalDataErasure.read().ownedGuestSources.single().key)
        PersonalDataErasure.finishStores(base)
        assertFalse(PersonalDataErasure.pending())
        assertFalse(owner.getDatabasePath("kpkn.db").exists())
        assertTrue(owner.getSharedPreferences("personal", 0).all.isEmpty())
    }
    @Test fun anotherProfileCannotReplaceAnUnfinishedErasure() = runBlocking {
        initialize()
        val a = ProfileStorage.context(base, LocalProfile(uid = "pending-a"))
        val b = ProfileStorage.context(base, LocalProfile(uid = "pending-b"))
        seed(a); val other = seed(b)
        PersonalDataErasure.begin(a)
        assertThrows(IllegalStateException::class.java) { PersonalDataErasure.begin(b) }
        assertEquals((a as ProfileContext).profile.key, PersonalDataErasure.read().owner.key)
        PersonalDataErasure.finishStores(a)
        assertTrue(other.exists())
    }
}
