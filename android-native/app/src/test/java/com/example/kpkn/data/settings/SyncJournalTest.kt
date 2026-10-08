package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class SyncJournalTest {
    @Test fun editingNormalizedObservationRetainsFutureFieldsAndCanClearKnownOptionalFields() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        val sql = db.openHelper.writableDatabase
        val codec = com.example.kpkn.data.sync.PersonalRecordCodec(context)
        val remote = kotlinx.serialization.json.Json.parseToJsonElement("""{
            "id":"future-body","metric":"WEIGHT","valueSi":70.0,"unitSi":"kg","sessionId":"old-session",
            "timestampEpochMs":1000,"zoneId":"UTC","source":"MANUAL","method":"SCALE","quality":"DECLARED","externalId":null,
            "futurePrecision":{"version":2,"sensor":"future-device"}}
        """).let { it as kotlinx.serialization.json.JsonObject }
        codec.apply(sql, "body_observation", "future-body", remote, false)
        db.syncJournalDao().confirm(SyncShadowEntity("body_observations", "future-body", 1, remote.toString(), false))
        sql.execSQL("UPDATE body_observations SET valueSi=71.0,sessionId=NULL WHERE id='future-body'")
        val next = requireNotNull(codec.snapshot(sql, "body_observations", "future-body"))
        assertEquals(remote["futurePrecision"], next["futurePrecision"])
        assertEquals(kotlinx.serialization.json.JsonPrimitive(71.0), next["valueSi"])
        assertEquals(kotlinx.serialization.json.JsonNull, next["sessionId"])
    }
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun setup() {
        KpknDatabase.closeInstance()
        com.example.kpkn.data.account.ProfileStorage.initialize(context)
        context.deleteDatabase("kpkn.db")
    }
    @After fun cleanup() { KpknDatabase.closeInstance(); context.deleteDatabase("kpkn.db") }

    @Test fun everyPersonalTableParticipatesAndCatalogsDoNot() = runBlocking(Dispatchers.IO) {
        val expected = BackupTableManifest.tableCategory.filterValues { it == BackupTableManifest.Category.USER_DATA }.keys
        assertEquals(expected, SyncJournalSchema.keys.keys)
        val db = KpknDatabase.getInstance(context)
        assertEquals(KpknDatabase.DATABASE_VERSION, db.openHelper.readableDatabase.version)
    }
    @Test fun mutationAndPendingMarkerRollBackTogether() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        db.syncJournalDao().configure(SyncConfigurationEntity(enabled = true, deviceId = "device-a", accountGeneration = 1))
        try {
            db.runInTransaction {
                db.openHelper.writableDatabase.execSQL("INSERT INTO programs(id,name,data) VALUES('rolled-back','test','{}')")
                error("interrupted write")
            }
            fail("Failure was concealed")
        } catch (_: IllegalStateException) { }
        assertEquals(0, db.syncJournalDao().pendingCount())
        assertTrue(db.programDao().getAllHeaders().isEmpty())
        db.programDao().upsert(Program(id = "durable", name = "Durable").toEntity())
        assertEquals(1, db.syncJournalDao().pendingCount())
        KpknDatabase.closeInstance()
        val restored = KpknDatabase.getInstance(context)
        assertEquals("durable", restored.programDao().getAllHeaders().single().id)
        assertEquals("durable", restored.syncJournalDao().pending().single().rawKey)
    }
    @Test fun remoteApplicationDoesNotEchoAndChangesDuringSendingRemainPending() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        val journal = db.syncJournalDao()
        journal.configure(SyncConfigurationEntity(enabled = true, deviceId = "device-a", accountGeneration = 1))
        db.programDao().upsert(Program(id = "local", name = "First").toEntity())
        val first = journal.pending().single()
        journal.upsert(first.copy(operationId = "stable-op", operationJson = "immutable-operation", sentVersion = first.dirtyVersion, status = "SENDING"))
        db.programDao().upsert(Program(id = "local", name = "Second").toEntity())
        val later = journal.pending().single()
        assertTrue(later.dirtyVersion > later.sentVersion)
        assertEquals("immutable-operation", later.operationJson)
        journal.configure(journal.configuration()!!.copy(applyingRemote = true))
        db.programDao().upsert(Program(id = "remote", name = "Remote").toEntity())
        assertEquals(1, journal.pendingCount())
        journal.configure(journal.configuration()!!.copy(applyingRemote = false))
        db.programDao().delete("local")
        assertTrue(journal.pending().single().deleted)
    }
    @Test fun dirtyEditKeepsItsOriginalBaseWhenANewerShadowIsPulled() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        val journal = db.syncJournalDao()
        journal.configure(SyncConfigurationEntity(enabled = true, deviceId = "device-a", accountGeneration = 1))
        journal.confirm(SyncShadowEntity("programs", "edited", 4, "{}", false))
        db.programDao().upsert(Program(id = "edited", name = "Offline edit").toEntity())
        assertEquals(4L, journal.pending().single().baseRevision)
        journal.confirm(SyncShadowEntity("programs", "edited", 5, "{}", false))
        db.programDao().upsert(Program(id = "edited", name = "Second offline edit").toEntity())
        assertEquals(4L, journal.pending().single().baseRevision)
    }
    @Test fun paidQueueDoesNotBlockFreeDataAndDeletionCanLeaveTheWait() = runBlocking(Dispatchers.IO) {
        val db = KpknDatabase.getInstance(context)
        val journal = db.syncJournalDao()
        journal.configure(SyncConfigurationEntity(enabled = true, deviceId = "device-a", accountGeneration = 1))
        db.programDao().upsert(Program(id = "paid", name = "Preserved").toEntity())
        val paid = journal.pending().single()
        journal.upsert(paid.copy(status = "CAPABILITY_WAIT", operationId = "paid-op", operationJson = "unchanged-operation"))
        db.programDao().upsert(Program(id = "free", name = "Ordinary edit").toEntity())
        assertEquals(listOf("free"), journal.pending().map { it.rawKey })
        assertEquals(1, journal.waitingForProCount())
        db.programDao().delete("paid")
        assertTrue(journal.pending().first { it.rawKey == "paid" }.deleted)
        assertEquals("unchanged-operation", journal.mutation("programs", "paid")!!.operationJson)
        journal.releaseCapabilityWait()
        assertEquals(0, journal.waitingForProCount())
        assertEquals("unchanged-operation", journal.mutation("programs", "paid")!!.operationJson)
    }
}
