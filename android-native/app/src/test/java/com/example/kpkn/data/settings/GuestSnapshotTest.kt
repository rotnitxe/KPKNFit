package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.*
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.db.*
import com.example.kpkn.data.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34])
class GuestSnapshotTest {
    private lateinit var guest: Context
    private lateinit var target: Context
    private lateinit var base: Context
    @Before fun setup() {
        base = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(base); ProfileStorage.select(null, newGuest = true)
        KpknDatabase.closeInstance()
        guest = ProfileStorage.context(base)
        target = ProfileStorage.context(base, LocalProfile(uid = "snapshot-owner"))
    }
    @After fun cleanup() {
        KpknDatabase.closeInstance()
        listOf(guest, target).forEach { it.deleteDatabase("kpkn.db"); it.filesDir.deleteRecursively(); it.noBackupFilesDir.deleteRecursively() }
    }
    @Test fun failedLoginThenEditedOrDeletedGuestKeepsOriginalRowsFilesPreferences() = runBlocking(Dispatchers.IO) {
        val database = KpknDatabase.getInstance(guest)
        val file = File(guest.filesDir, "workout_media/photo.jpg").also { it.parentFile!!.mkdirs(); it.writeText("original-file") }
        database.openHelper.writableDatabase.execSQL("INSERT INTO workout_media(id,kind,filePath,createdAtMs,isPr) VALUES(?,?,?,?,?)",
            arrayOf("original-media", "PHOTO", file.absolutePath, 1L, 0))
        guest.getSharedPreferences("user-settings", 0).edit().putString("choice", "original-preference").commit()
        val imports = GuestImportCoordinator(base)
        val root = imports.prepare(guest, database)
        database.workoutMediaDao().delete("original-media")
        file.delete()
        guest.getSharedPreferences("user-settings", 0).edit().putString("choice", "changed-preference").commit()
        assertEquals(root, imports.prepare(guest, database))
        imports.bindAndCopy(root, "snapshot-owner")
        val destination = KpknDatabase.openIsolated(target, "kpkn.db")
        try {
            val row = requireNotNull(destination.workoutMediaDao().getById("original-media"))
            assertEquals("original-file", File(row.filePath).readText())
            assertTrue(File(row.filePath).canonicalPath.startsWith(target.filesDir.canonicalPath))
        } finally { destination.close() }
        assertEquals("original-preference", target.getSharedPreferences("user-settings", 0).getString("choice", null))
    }
    @Test fun interruptedCopyIsVerifiedOnRetryAndExistingAccountFilesSurvive() {
        val original = File(guest.filesDir, "profile/avatar.png").also { it.parentFile!!.mkdirs(); it.writeText("guest-avatar") }
        val existing = File(target.filesDir, "profile/avatar.png").also { it.parentFile!!.mkdirs(); it.writeText("account-avatar") }
        val root = File(guest.noBackupFilesDir, "fixture-${UUID.randomUUID()}")
        val entries = GuestSnapshotFiles.freeze(guest, root)
        val mapping = GuestSnapshotFiles.publish(root, target, "import-fixture", entries)
        val imported = File(mapping.getValue("profile/avatar.png"))
        imported.writeText("partial")
        original.writeText("edited-later")
        GuestSnapshotFiles.publish(root, target, "import-fixture", entries)
        assertEquals("guest-avatar", imported.readText())
        assertEquals("account-avatar", existing.readText())
    }
    @Test fun unavailableDestinationFailsWithoutPublishingPartialSuccessThenRetries() {
        File(guest.filesDir, "workout_media/photo.png").also { it.parentFile!!.mkdirs(); it.writeText("frozen") }
        val root = File(guest.noBackupFilesDir, "freeze")
        val entries = GuestSnapshotFiles.freeze(guest, root)
        val blocker = File(target.filesDir, "workout_media").also { it.writeText("not-a-directory") }
        assertThrows(Exception::class.java) { GuestSnapshotFiles.publish(root, target, "retry", entries) }
        blocker.delete()
        assertEquals("frozen", File(GuestSnapshotFiles.publish(root, target, "retry", entries).values.single()).readText())
    }
    @Test fun preferenceSnapshotExcludesSecretsAndCannotOverwriteExistingChoices() {
        guest.getSharedPreferences("user", 0).edit().putString("choice", "guest").putString("added", "frozen").putString("apiKey", "synthetic-secret").commit()
        val frozen = PortableBackup.preferences(guest)
        guest.getSharedPreferences("user", 0).edit().putString("added", "later").commit()
        target.getSharedPreferences("user", 0).edit().putString("choice", "account").commit()
        PortableBackup.copyMissingPreferences(frozen, target)
        assertEquals("account", target.getSharedPreferences("user", 0).getString("choice", null))
        assertEquals("frozen", target.getSharedPreferences("user", 0).getString("added", null))
        assertFalse(target.getSharedPreferences("user", 0).contains("apiKey"))
    }
    @Test fun legacyRecoveryRemainsInsidePortableFiles() {
        File(guest.noBackupFilesDir, "legacy-body-recovery.json").writeText("{\"retained\":true}")
        val root = File(guest.noBackupFilesDir, "recovery-fixture")
        val entries = GuestSnapshotFiles.freeze(guest, root)
        val saved = File(GuestSnapshotFiles.publish(root, target, "recovery", entries).values.single())
        assertEquals("{\"retained\":true}", saved.readText())
        assertEquals("profile", saved.relativeTo(target.filesDir).invariantSeparatorsPath.substringBefore('/'))
    }
}
