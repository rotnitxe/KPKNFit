package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class PortableBackupTest {
    @Test fun recoveryFile() = runBlocking(Dispatchers.IO) {
        val recovery = File(context.noBackupFilesDir, "legacy-body-recovery.json").also { it.writeText("{\"unreadableLegacyRow\":true}") }
        File(context.noBackupFilesDir, "sync-media").mkdirs()
        File(context.noBackupFilesDir, "sync-media/private-token").writeText("synthetic-operational")
        val output = ByteArrayOutputStream()
        PortableBackup.export(context, output)
        recovery.delete()
        PortableBackup.restore(context, ByteArrayInputStream(output.toByteArray()))
        assertEquals("{\"unreadableLegacyRow\":true}", recovery.readText())
        assertFalse(File(context.filesDir, "legacy-body-recovery.json").exists())
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            val names = mutableListOf<String>()
            while (true) { val entry = zip.nextEntry ?: break; names += entry.name }
            assertTrue("files/legacy-body-recovery.json" in names)
            assertTrue(names.none { it.contains("private-token") })
        }
    }
    private lateinit var context: Context
    @Before fun setup() {
        KpknDatabase.closeInstance()
        val base: Context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(base)
        context = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
    }
    @After fun cleanup() { KpknDatabase.closeInstance(); context.deleteDatabase("kpkn.db"); context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively() }

    @Test fun roundTrip() = runBlocking(Dispatchers.IO) {
        val database = KpknDatabase.getInstance(context)
        val large = "é".repeat(2_500_000)
        val program = Program(id = "large-program", name = large)
        database.programDao().upsert(program.toEntity())
        File(context.filesDir, "profile/avatar.jpg").also { it.parentFile!!.mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3)) }
        File(context.filesDir, "workout_media/photo.bin").also { it.parentFile!!.mkdirs(); it.writeText("photo") }
        database.openHelper.writableDatabase.execSQL("INSERT INTO workout_media (id,kind,filePath,createdAtMs,isPr) VALUES (?,?,?,?,?)",
            arrayOf("portable-photo", "PHOTO", File(context.filesDir, "workout_media/photo.bin").absolutePath, 1234L, 0))
        context.getSharedPreferences("variant_preferences", 0).edit().putString("selected", "original").putFloat("weight", 3.5f).commit()
        val output = ByteArrayOutputStream(); PortableBackup.export(context, output)
        database.programDao().deleteAll()
        File(context.filesDir, "profile/avatar.jpg").writeText("changed")
        context.getSharedPreferences("variant_preferences", 0).edit().putString("selected", "changed").commit()
        PortableBackup.restore(context, ByteArrayInputStream(output.toByteArray()))
        val row = BoundedSqlRows.read(database.openHelper.readableDatabase, "programs", "id", program.id)!!
        assertTrue((row["data"] as String).contains(large))
        assertArrayEquals(byteArrayOf(1, 2, 3), File(context.filesDir, "profile/avatar.jpg").readBytes())
        assertEquals("photo", File(context.filesDir, "workout_media/photo.bin").readText())
        assertEquals(File(context.filesDir, "workout_media/photo.bin").absolutePath, database.workoutMediaDao().getById("portable-photo")!!.filePath)
        assertEquals("original", context.getSharedPreferences("variant_preferences", 0).getString("selected", null))
        assertEquals(3.5f, context.getSharedPreferences("variant_preferences", 0).getFloat("weight", 0f))
    }
    @Test fun rollback() = runBlocking(Dispatchers.IO) {
        val database = KpknDatabase.getInstance(context)
        database.programDao().upsert(Program(id = "retained", name = "Retained").toEntity())
        val photo = File(context.filesDir, "profile/avatar.jpg").also { it.parentFile!!.mkdirs(); it.writeText("retained") }
        val preferences = context.getSharedPreferences("variant_preferences", 0)
        preferences.edit().putString("selected", "retained").commit()
        try {
            PortableBackup.transaction(context) {
                database.programDao().deleteAll(); photo.writeText("changed"); preferences.edit().clear().commit()
                throw IOException("simulated full storage")
            }
            fail("Failure was concealed")
        } catch (_: IOException) {
            assertEquals("retained", database.programDao().getAll().single().id)
            assertEquals("retained", photo.readText())
            assertEquals("retained", preferences.getString("selected", null))
        }
    }
    @Test fun rejectInvalid() = runBlocking(Dispatchers.IO) {
        val database = KpknDatabase.getInstance(context)
        database.programDao().upsert(Program(id = "retained", name = "Retained").toEntity())
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> zip.putNextEntry(ZipEntry("../outside")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        try { PortableBackup.restore(context, ByteArrayInputStream(output.toByteArray())); fail("Invalid archive accepted") }
        catch (_: Exception) { assertEquals("retained", database.programDao().getAll().single().id) }
        assertFalse(File(context.noBackupFilesDir, "outside").exists())
    }
    @Test fun recoverInterrupted() = runBlocking(Dispatchers.IO) {
        val database = KpknDatabase.getInstance(context)
        database.programDao().upsert(Program(id = "before", name = "Before").toEntity())
        val photo = File(context.filesDir, "profile/avatar.jpg").also { it.parentFile!!.mkdirs(); it.writeText("before") }
        val root = File(context.noBackupFilesDir, "maintenance").also { it.mkdirs() }
        File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context)}").copyTo(File(root, "rollback.db"))
        File(root, "preferences.json").writeText("{}")
        val saved = File(root, "rollback-files/profile/avatar.jpg").also { it.parentFile!!.mkdirs() }
        photo.copyTo(saved)
        File(root, "state").writeText("PREPARED")
        database.programDao().deleteAll(); photo.writeText("interrupted")
        KpknDatabase.closeInstance()
        PortableBackup.recover(context) // A new process must recover before opening personal readers.
        PortableBackup.recover(context) // A repeated startup must not reapply or discard recovered rows.
        assertEquals("before", KpknDatabase.getInstance(context).programDao().getAll().single().id)
        assertEquals("before", photo.readText())
        assertFalse(root.exists())
    }
    @Test fun pendingPreferencesAndConsent() = runBlocking(Dispatchers.IO) {
        KpknDatabase.getInstance(context)
        context.getSharedPreferences("just_opened", 0).edit().putString("value", "retained").apply()
        context.getSharedPreferences("diagnostic_consent", 0).edit().putBoolean("crash_reporting", true).commit()
        val output = ByteArrayOutputStream(); PortableBackup.export(context, output)
        context.getSharedPreferences("just_opened", 0).edit().clear().commit()
        PortableBackup.restore(context, ByteArrayInputStream(output.toByteArray()))
        assertEquals("retained", context.getSharedPreferences("just_opened", 0).getString("value", null))
        assertFalse(context.getSharedPreferences("diagnostic_consent", 0).getBoolean("crash_reporting", false))
    }
}
